package net.runelite.client.plugins.microbot.KspBoneAshPlugin;


import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class KspBoneAshScript extends Script
{
    private static final long LOOP_DELAY_MS = 25L;
    private static final long CONSUME_CONFIRM_TIMEOUT_MS = 1_800L;
    private static final long WITHDRAW_CONFIRM_TIMEOUT_MS = 2_500L;
    private static final long BANK_RETRY_DELAY_MS = 650L;

    private final Random random = new Random();
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private KspBoneAshConfig config;
    private Plugin ownerPlugin;

    private boolean awaitingConsumption;
    private int countBeforeConsumption;
    private long consumptionIssuedAt;

    private boolean withdrawalPending;
    private long withdrawalIssuedAt;
    private long nextBankAttemptAt;

    private long nextInventoryInteractionAt;
    private int lastSlot = -1;
    private int traversalDirection = 1;

    public boolean run(KspBoneAshConfig config, Plugin ownerPlugin)
    {
        this.config = config;
        this.ownerPlugin = ownerPlugin;
        resetState();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn())
                {
                    return;
                }

                if (!super.run() || stopRequested.get())
                {
                    return;
                }

                process();
            }
            catch (Exception ex)
            {
                log.error("[KSP Bone & Ash] Unhandled script error", ex);
                requestStop("Bone & Ash stopped because an unexpected error occurred.");
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void process()
    {
        final String itemName = configuredItemName();
        if (itemName.isEmpty())
        {
            requestStop("Bone & Ash stopped: configure a bone or ash item name first.");
            return;
        }

        final int inventoryCount = trackedInventoryCount(itemName);
        if (inventoryCount > 0)
        {
            handleInventory(itemName, inventoryCount);
            return;
        }

        awaitingConsumption = false;
        handleBank(itemName);
    }

    private void handleInventory(String itemName, int inventoryCount)
    {
        final long now = System.currentTimeMillis();

        if (Rs2Bank.isOpen())
        {
            Microbot.status = "Closing bank";
            Rs2Bank.closeBank();
            withdrawalPending = false;
            nextInventoryInteractionAt = Math.max(nextInventoryInteractionAt, now + randomInteractionDelay());
            return;
        }

        if (awaitingConsumption)
        {
            if (inventoryCount < countBeforeConsumption)
            {
                awaitingConsumption = false;
                nextInventoryInteractionAt = now + randomInteractionDelay();
            }
            else if (now - consumptionIssuedAt >= CONSUME_CONFIRM_TIMEOUT_MS)
            {
                awaitingConsumption = false;
                nextInventoryInteractionAt = now + randomBetween(40, 110);
            }
            else
            {
                return;
            }
        }

        if (now < nextInventoryInteractionAt)
        {
            return;
        }

        final List<Rs2ItemModel> candidates = matchingInventoryItems(itemName);
        if (candidates.isEmpty())
        {
            return;
        }

        final Rs2ItemModel target = selectRandomizedTarget(candidates);
        final String action = resolvePrayerAction(target);

        if (action == null)
        {
            requestStop("Bone & Ash stopped: '" + itemName + "' has no Bury or Scatter inventory action.");
            return;
        }

        Microbot.status = action + " " + itemName;

        if (Rs2Inventory.interact(target, action))
        {
            lastSlot = target.getSlot();
            maybeFlipTraversalDirection();
            awaitingConsumption = true;
            countBeforeConsumption = inventoryCount;
            consumptionIssuedAt = now;
        }
        else
        {
            nextInventoryInteractionAt = now + randomBetween(70, 160);
        }
    }

    private void handleBank(String itemName)
    {
        final long now = System.currentTimeMillis();

        if (!Rs2Bank.isOpen())
        {
            withdrawalPending = false;

            if (now < nextBankAttemptAt)
            {
                return;
            }

            Microbot.status = "Opening bank";
            final boolean opened = KspVerifiedBank.openBank();
            nextBankAttemptAt = now + BANK_RETRY_DELAY_MS;

            if (!opened)
            {
                return;
            }
        }

        if (trackedInventoryCount(itemName) > 0)
        {
            Microbot.status = "Closing bank";
            Rs2Bank.closeBank();
            withdrawalPending = false;
            nextInventoryInteractionAt = System.currentTimeMillis() + randomInteractionDelay();
            return;
        }

        if (withdrawalPending)
        {
            if (now - withdrawalIssuedAt < WITHDRAW_CONFIRM_TIMEOUT_MS)
            {
                return;
            }
            withdrawalPending = false;
        }

        if (!Rs2Bank.hasBankItem(itemName, true))
        {
            requestStop("Bone & Ash complete: no '" + itemName + "' remains in inventory or bank.");
            return;
        }

        Microbot.status = "Withdraw-all " + itemName;

        if (Rs2Bank.withdrawAll(itemName, true))
        {
            withdrawalPending = true;
            withdrawalIssuedAt = now;
        }
        else
        {
            nextBankAttemptAt = now + BANK_RETRY_DELAY_MS;
        }
    }

    private List<Rs2ItemModel> matchingInventoryItems(String itemName)
    {
        return Rs2Inventory.getList(item -> item != null && item.getName().equalsIgnoreCase(itemName));
    }

    private int trackedInventoryCount(String itemName)
    {
        return Rs2Inventory.itemQuantity(itemName, true);
    }

    private Rs2ItemModel selectRandomizedTarget(List<Rs2ItemModel> source)
    {
        final List<Rs2ItemModel> candidates = new ArrayList<>(source);
        candidates.sort(Comparator.comparingInt(Rs2ItemModel::getSlot));

        if (candidates.size() == 1 || lastSlot < 0)
        {
            return candidates.get(random.nextInt(candidates.size()));
        }

        final int randomSlotChance = clampPercent(config.randomSlotChance());
        if (random.nextInt(100) < randomSlotChance)
        {
            return candidates.get(random.nextInt(candidates.size()));
        }

        Rs2ItemModel best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Rs2ItemModel candidate : candidates)
        {
            int distance;
            if (traversalDirection > 0)
            {
                distance = candidate.getSlot() > lastSlot
                        ? candidate.getSlot() - lastSlot
                        : (28 - lastSlot) + candidate.getSlot();
            }
            else
            {
                distance = candidate.getSlot() < lastSlot
                        ? lastSlot - candidate.getSlot()
                        : lastSlot + (28 - candidate.getSlot());
            }

            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = candidate;
            }
        }

        return best != null ? best : candidates.get(random.nextInt(candidates.size()));
    }

    private String resolvePrayerAction(Rs2ItemModel item)
    {
        final String[] actions = item.getInventoryActions();
        if (actions == null)
        {
            return null;
        }

        for (String action : actions)
        {
            if (action != null && action.equalsIgnoreCase("Bury"))
            {
                return "Bury";
            }
        }

        for (String action : actions)
        {
            if (action != null && action.equalsIgnoreCase("Scatter"))
            {
                return "Scatter";
            }
        }

        return null;
    }

    private long randomInteractionDelay()
    {
        final int min = Math.max(0, Math.min(config.minInteractionDelay(), config.maxInteractionDelay()));
        final int max = Math.max(min, Math.max(config.minInteractionDelay(), config.maxInteractionDelay()));
        long delay = randomBetween(min, max);

        if (random.nextInt(100) < clampPercent(config.hesitationChance()))
        {
            final int hesitationMin = Math.max(0, Math.min(config.hesitationMin(), config.hesitationMax()));
            final int hesitationMax = Math.max(hesitationMin, Math.max(config.hesitationMin(), config.hesitationMax()));
            delay += randomBetween(hesitationMin, hesitationMax);
        }

        return delay;
    }

    private void maybeFlipTraversalDirection()
    {
        if (random.nextInt(100) < clampPercent(config.directionFlipChance()))
        {
            traversalDirection *= -1;
        }
    }

    private int randomBetween(int min, int max)
    {
        if (max <= min)
        {
            return min;
        }
        return min + random.nextInt((max - min) + 1);
    }

    private int clampPercent(int value)
    {
        return Math.max(0, Math.min(100, value));
    }

    private String configuredItemName()
    {
        final String configured = config != null ? config.itemName() : null;
        return configured == null ? "" : configured.trim();
    }

    private void requestStop(String reason)
    {
        if (!stopRequested.compareAndSet(false, true))
        {
            return;
        }

        Microbot.status = reason;
        Microbot.log(reason);

        if (ownerPlugin != null)
        {
            Microbot.stopPlugin(ownerPlugin);
        }
        else
        {
            shutdown();
        }
    }

    private void resetState()
    {
        stopRequested.set(false);
        awaitingConsumption = false;
        countBeforeConsumption = 0;
        consumptionIssuedAt = 0L;
        withdrawalPending = false;
        withdrawalIssuedAt = 0L;
        nextBankAttemptAt = 0L;
        nextInventoryInteractionAt = 0L;
        lastSlot = -1;
        traversalDirection = random.nextBoolean() ? 1 : -1;
    }
}
