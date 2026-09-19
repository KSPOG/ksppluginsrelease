package net.runelite.client.plugins.microbot.kspmugfiller;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspMugFillerScript extends Script
{
    // Verified OSRS item IDs:
    // Beer = 1917, Beer glass = 1919.
    private static final int BEER_ID = 1917;
    private static final int BEER_GLASS_ID = 1919;

    private static final int BARREL_ID = 364;
    private static final WorldPoint FILL_TILE = new WorldPoint(3099, 3280, 0);
    private static final WorldPoint BARREL_TILE = new WorldPoint(3099, 3281, 0);

    private static final long LOOP_DELAY_MS = 80L;
    private static final long BANK_RETRY_DELAY_MS = 750L;
    private static final int INVENTORY_CHANGE_TIMEOUT_MS = 2_500;
    private static final long PRICE_REFRESH_MS = 30_000L;

    private final Random random = new Random();
    private final List<Integer> shuffledGlassSlots = new ArrayList<>();

    private volatile String status = "Starting";
    private volatile long startedAtMs;
    private volatile long filledCount;
    private volatile int beerGePrice;
    private volatile int beerGlassGePrice;

    private RuntimeState state = RuntimeState.BANKING;
    private boolean bankDepositComplete;
    private long nextBankAttemptAt;

    private int slotCursor;
    private boolean awaitingInventoryChange;
    private int pendingGlassCount;
    private long pendingInteractionAt;
    private long nextInventoryInteractionAt;
    private long lastPriceRefreshAt;

    public boolean run()
    {
        resetSession();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn() || !super.run())
                {
                    return;
                }

                refreshPricesIfNeeded();
                process();
            }
            catch (Exception ex)
            {
                status = "Error - check client log";
                log.error("KSP Mug Filler loop error", ex);
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void process()
    {
        if (awaitingInventoryChange)
        {
            handlePendingInventoryChange();
            return;
        }

        if (state == RuntimeState.BANKING)
        {
            handleBanking();
            return;
        }

        if (Rs2Bank.isOpen())
        {
            status = "Closing bank";
            Rs2Bank.closeBank();
            return;
        }

        if (glassCount() <= 0)
        {
            beginBankReset();
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (!FILL_TILE.equals(player))
        {
            state = RuntimeState.WALKING_TO_BARREL;
            status = "Walking to 3099, 3280, 0";
            if (!Rs2Player.isMoving())
            {
                Rs2Walker.walkTo(FILL_TILE, 0);
            }
            return;
        }

        state = RuntimeState.FILLING;
        fillNextGlass();
    }

    private void handleBanking()
    {
        long now = System.currentTimeMillis();

        if (!Rs2Bank.isOpen())
        {
            if (now < nextBankAttemptAt)
            {
                return;
            }

            status = "Walking to bank";
            boolean opened = KspVerifiedBank.walkToBankAndOpenBank();
            nextBankAttemptAt = now + BANK_RETRY_DELAY_MS;

            if (!opened || !Rs2Bank.isOpen())
            {
                return;
            }
        }

        if (!bankDepositComplete)
        {
            status = "Depositing inventory";

            if (!Rs2Inventory.isEmpty())
            {
                if (!Rs2Bank.depositAll())
                {
                    return;
                }

                if (!sleepUntil(Rs2Inventory::isEmpty, INVENTORY_CHANGE_TIMEOUT_MS))
                {
                    status = "Waiting for deposit";
                    return;
                }
            }

            bankDepositComplete = true;
        }

        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Setting unnoted withdraw mode";
            return;
        }

        if (!Rs2Bank.hasItem(BEER_GLASS_ID))
        {
            status = "No Beer glass in bank";
            return;
        }

        if (glassCount() <= 0)
        {
            status = "Withdrawing all Beer glass";
            if (!Rs2Bank.withdrawAll(BEER_GLASS_ID))
            {
                return;
            }

            if (!sleepUntil(() -> glassCount() > 0, INVENTORY_CHANGE_TIMEOUT_MS))
            {
                status = "Waiting for Beer glass withdrawal";
                return;
            }
        }

        buildShuffledSlotOrder();

        status = "Closing bank";
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 1_500);

        state = RuntimeState.WALKING_TO_BARREL;
        bankDepositComplete = false;
        nextBankAttemptAt = 0L;
        status = "Walking to 3099, 3280, 0";
    }

    private void fillNextGlass()
    {
        long now = System.currentTimeMillis();
        if (now < nextInventoryInteractionAt)
        {
            return;
        }

        if (shuffledGlassSlots.isEmpty())
        {
            buildShuffledSlotOrder();
        }

        while (slotCursor < shuffledGlassSlots.size()
                && !slotContainsBeerGlass(shuffledGlassSlots.get(slotCursor)))
        {
            slotCursor++;
        }

        if (slotCursor >= shuffledGlassSlots.size())
        {
            if (glassCount() <= 0)
            {
                beginBankReset();
            }
            else
            {
                // Inventory changed outside the expected cycle. Rebuild only from the
                // glasses that still exist, then reshuffle those remaining slots.
                buildShuffledSlotOrder();
            }
            return;
        }

        Rs2TileObjectModel barrel = findExactBarrel();
        if (barrel == null)
        {
            status = "Waiting for barrel 364 @ 3099, 3281, 0";
            return;
        }

        if (Rs2Inventory.isItemSelected())
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            if (!sleepUntil(() -> !Rs2Inventory.isItemSelected(), 500))
            {
                status = "Clearing previous item selection";
                return;
            }
        }

        int targetSlot = shuffledGlassSlots.get(slotCursor);
        int before = glassCount();

        status = "Using Beer glass on barrel";
        if (!Rs2Inventory.slotInteract(targetSlot, "Use"))
        {
            nextInventoryInteractionAt = now + 150L;
            return;
        }

        if (!sleepUntil(Rs2Inventory::isItemSelected, 1_000))
        {
            status = "Retrying Beer glass selection";
            nextInventoryInteractionAt = now + 150L;
            return;
        }

        if (!barrel.click())
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            nextInventoryInteractionAt = now + 200L;
            status = "Retrying barrel interaction";
            return;
        }

        // Critical sequencing rule:
        // do not advance to the next shuffled slot until this Beer glass has
        // actually disappeared from the Beer-glass count.
        awaitingInventoryChange = true;
        pendingGlassCount = before;
        pendingInteractionAt = now;
        state = RuntimeState.WAITING_FOR_INVENTORY_CHANGE;
        status = "Waiting for inventory change";
    }

    private void handlePendingInventoryChange()
    {
        int current = glassCount();

        if (current < pendingGlassCount)
        {
            int confirmed = pendingGlassCount - current;
            filledCount += confirmed;

            awaitingInventoryChange = false;
                pendingGlassCount = 0;
            pendingInteractionAt = 0L;
            slotCursor++;
            nextInventoryInteractionAt = System.currentTimeMillis() + 40L;
            state = RuntimeState.FILLING;
            status = current > 0 ? "Fill confirmed" : "Inventory complete - banking";

            if (current <= 0)
            {
                beginBankReset();
            }
            return;
        }

        if (System.currentTimeMillis() - pendingInteractionAt < INVENTORY_CHANGE_TIMEOUT_MS)
        {
            return;
        }

        // The expected Beer glass -> Beer inventory transition was not observed.
        // Keep the same slotCursor so the same glass is retried; never move to
        // the next randomized glass merely because the interaction timed out.
        if (Rs2Inventory.isItemSelected())
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }

        awaitingInventoryChange = false;
        pendingGlassCount = 0;
        pendingInteractionAt = 0L;
        nextInventoryInteractionAt = System.currentTimeMillis() + 200L;
        state = RuntimeState.FILLING;
        status = "No inventory change - retrying same glass";
    }

    private Rs2TileObjectModel findExactBarrel()
    {
        try
        {
            Rs2TileObjectModel barrel = Microbot.getRs2TileObjectCache()
                    .query()
                    .withId(BARREL_ID)
                    .within(BARREL_TILE, 1)
                    .nearestOnClientThread();

            if (barrel == null)
            {
                return null;
            }

            WorldPoint location = Microbot.getClientThread().invoke(barrel::getWorldLocation);
            return BARREL_TILE.equals(location) ? barrel : null;
        }
        catch (RuntimeException ex)
        {
            if (!Thread.currentThread().isInterrupted())
            {
                log.debug("KSP Mug Filler barrel lookup failed: {}", ex.getMessage());
            }
            return null;
        }
    }

    private void buildShuffledSlotOrder()
    {
        shuffledGlassSlots.clear();
        shuffledGlassSlots.addAll(Rs2Inventory.getList(item ->
                        item != null && item.getId() == BEER_GLASS_ID)
                .stream()
                .map(Rs2ItemModel::getSlot)
                .collect(Collectors.toList()));

        Collections.shuffle(shuffledGlassSlots, random);
        slotCursor = 0;
    }

    private boolean slotContainsBeerGlass(int slot)
    {
        return Rs2Inventory.items(item ->
                        item != null
                                && item.getId() == BEER_GLASS_ID
                                && item.getSlot() == slot)
                .findFirst()
                .isPresent();
    }

    private int glassCount()
    {
        return Rs2Inventory.itemQuantity(BEER_GLASS_ID);
    }

    private void beginBankReset()
    {
        state = RuntimeState.BANKING;
        bankDepositComplete = false;
        awaitingInventoryChange = false;
        pendingGlassCount = 0;
        pendingInteractionAt = 0L;
        nextInventoryInteractionAt = 0L;
        slotCursor = 0;
        shuffledGlassSlots.clear();
        status = "Bank reset";
    }

    private void refreshPricesIfNeeded()
    {
        long now = System.currentTimeMillis();
        if (now - lastPriceRefreshAt < PRICE_REFRESH_MS)
        {
            return;
        }

        beerGePrice = getGePrice(BEER_ID);
        beerGlassGePrice = getGePrice(BEER_GLASS_ID);
        lastPriceRefreshAt = now;
    }

    private int getGePrice(int itemId)
    {
        return Math.max(0, Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemPrice(itemId))
                .orElse(0));
    }

    private void resetSession()
    {
        startedAtMs = System.currentTimeMillis();
        filledCount = 0L;
        beerGePrice = 0;
        beerGlassGePrice = 0;
        lastPriceRefreshAt = 0L;

        state = RuntimeState.BANKING;
        bankDepositComplete = false;
        nextBankAttemptAt = 0L;

        shuffledGlassSlots.clear();
        slotCursor = 0;
        awaitingInventoryChange = false;
        pendingGlassCount = 0;
        pendingInteractionAt = 0L;
        nextInventoryInteractionAt = 0L;
        status = "Starting bank reset";
    }

    @Override
    public void shutdown()
    {
        super.shutdown();

        if (Rs2Inventory.isItemSelected())
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }

        status = "Stopped";
        shuffledGlassSlots.clear();
        awaitingInventoryChange = false;
    }

    public String getStatus()
    {
        return status;
    }

    public long getRuntimeMs()
    {
        return startedAtMs <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - startedAtMs);
    }

    public long getFilledCount()
    {
        return filledCount;
    }

    public long getFilledPerHour()
    {
        long runtime = getRuntimeMs();
        return runtime <= 0L ? 0L : Math.round(filledCount * 3_600_000.0D / runtime);
    }

    public long getGpMade()
    {
        if (beerGePrice <= 0 || beerGlassGePrice <= 0)
        {
            return 0L;
        }

        // "GP made" is the estimated value added by filling the glass:
        // current Beer GE price minus current empty Beer glass GE price.
        return filledCount * (long) (beerGePrice - beerGlassGePrice);
    }

    public long getGpPerHour()
    {
        long runtime = getRuntimeMs();
        return runtime <= 0L ? 0L : Math.round(getGpMade() * 3_600_000.0D / runtime);
    }

    private enum RuntimeState
    {
        BANKING,
        WALKING_TO_BARREL,
        FILLING,
        WAITING_FOR_INVENTORY_CHANGE
    }
}
