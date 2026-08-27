package net.runelite.client.plugins.microbot.KSPGELooter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KSPGELooterScript extends Script
{
    private static final int NATURE_RUNE_ID = 561;
    private static final int FIRE_RUNE_ID = 554;
    private static final long RUNE_PRICE_REFRESH_MS = 30_000L;
    private static final long HIGH_ALCH_COOLDOWN_MS = 3_000L;
    private static final long PRIORITY_RELEASE_GRACE_MS = 1_200L;
    private static final String STAFF_OF_FIRE = "Staff of fire";

    public static volatile String status = "Idle";
    public static volatile String targetName = "-";
    public static volatile long targetGeValue;
    public static volatile long alchRuneCost;
    public static volatile int itemsLooted;
    public static volatile int itemsAlched;
    public static volatile int groundItemsSeen;
    public static volatile int eligibleGroundItems;
    public static volatile long totalLootGeValue;
    public static volatile long totalAlchValue;
    public static volatile long totalAlchMargin;
    public static volatile int natureRuneGePrice;
    public static volatile int fireRuneGePrice;
    public static volatile int inventorySlotsUsed;
    public static volatile int natureRunes;
    public static volatile int fireRunes;
    public static volatile boolean staffOfFireEquipped;
    public static volatile boolean insideArea;
    public static volatile boolean priorityTakeoverActive;
    public static volatile boolean priorityPauseOwned;

    private static long startTimeMs;
    private int natureRunePrice;
    private int fireRunePrice;
    private long lastRunePriceRefresh;
    private long lastAlchAt;
    private long priorityReleaseAt;
    private boolean ownsPriorityPause;

    public boolean run(KSPGELooterConfig config)
    {
        resetSessionState();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                boolean baseCanRun = super.run();
                boolean sharedPause = Microbot.pauseAllScripts.get();

                if (!Microbot.isLoggedIn())
                {
                    releasePriorityPause("Logged out");
                    return;
                }

                // A non-pause guard (blocking event/interruption) still wins. A shared pause does
                // not stop the looter from scanning, otherwise Priority Mode can never pre-empt it.
                if (!baseCanRun && !sharedPause && !ownsPriorityPause) return;

                if (ownsPriorityPause && !sharedPause)
                {
                    ownsPriorityPause = priorityPauseOwned = false;
                }
                if (!config.priorityMode() && ownsPriorityPause)
                {
                    releasePriorityPause("Priority Mode disabled");
                }

                updateOverlayState();
                if (!insideArea)
                {
                    releasePriorityPause("Outside GE area");
                    status = "OUTSIDE AREA - PAUSED";
                    groundItemsSeen = eligibleGroundItems = 0;
                    clearTarget();
                    if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
                    return;
                }

                refreshRunePricesIfNeeded();
                Rs2TileItemModel lootTarget = findLootTarget(Math.max(0, config.minimumGeValue()));

                if (config.priorityMode())
                {
                    if (lootTarget != null)
                    {
                        beginPriorityTakeover();
                        priorityReleaseAt = System.currentTimeMillis() + PRIORITY_RELEASE_GRACE_MS;
                    }
                    else if (priorityTakeoverActive)
                    {
                        if (System.currentTimeMillis() < priorityReleaseAt)
                        {
                            status = "Priority hold - confirming loot cleared";
                            return;
                        }
                        releasePriorityPause("No eligible loot remains");
                    }
                }

                // Respect an external/global pause unless Priority Mode has an eligible target.
                if (!baseCanRun && !priorityTakeoverActive) return;

                if (lootTarget == null)
                {
                    clearTarget();
                    if (config.highAlch() && tryHighAlch()) return;
                    status = groundItemsSeen > 0
                            ? "Ground items seen - none eligible"
                            : (config.priorityMode() ? "Waiting for priority loot" : "Waiting for loot");
                    return;
                }

                if (Rs2Inventory.isFull() && !canStackIntoInventory(lootTarget))
                {
                    if (config.highAlch() && tryHighAlch()) return;
                    bankNonRunes();
                    return;
                }

                spamLoot(lootTarget, config);
            }
            catch (Exception ex)
            {
                status = "Error - see log";
                Microbot.log("KSP GE Looter error: " + ex.getMessage());
                log.error("KSP GE Looter loop error", ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        return true;
    }

    private void resetSessionState()
    {
        startTimeMs = System.currentTimeMillis();
        status = "Starting";
        targetName = "-";
        targetGeValue = alchRuneCost = totalLootGeValue = totalAlchValue = totalAlchMargin = 0L;
        itemsLooted = itemsAlched = groundItemsSeen = eligibleGroundItems = 0;
        natureRuneGePrice = fireRuneGePrice = natureRunes = fireRunes = 0;
        inventorySlotsUsed = inventoryItemCount();
        priorityReleaseAt = lastAlchAt = lastRunePriceRefresh = 0L;
        staffOfFireEquipped = insideArea = priorityTakeoverActive = priorityPauseOwned = ownsPriorityPause = false;
    }

    @Override
    public void shutdown()
    {
        releasePriorityPause("Looter stopped");
        super.shutdown();
        status = "Stopped";
        clearTarget();
        alchRuneCost = 0L;
        groundItemsSeen = eligibleGroundItems = 0;
        insideArea = priorityTakeoverActive = priorityPauseOwned = false;
    }

    private void clearTarget()
    {
        targetName = "-";
        targetGeValue = 0L;
    }

    private void beginPriorityTakeover()
    {
        priorityTakeoverActive = true;
        if (ownsPriorityPause)
        {
            priorityPauseOwned = true;
            return;
        }

        if (Microbot.pauseAllScripts.compareAndSet(false, true))
        {
            ownsPriorityPause = priorityPauseOwned = true;
            Microbot.log("KSP GE Looter Priority Mode: paused other scripts for loot");
        }
        else
        {
            // Another component already owns the shared pause. Looting may still proceed, but this
            // plugin must never release a pause it did not acquire.
            priorityPauseOwned = false;
        }
    }

    private void releasePriorityPause(String reason)
    {
        priorityTakeoverActive = false;
        priorityPauseOwned = false;
        priorityReleaseAt = 0L;
        if (!ownsPriorityPause) return;

        Microbot.pauseAllScripts.compareAndSet(true, false);
        ownsPriorityPause = false;
        Microbot.log("KSP GE Looter Priority Mode: resumed scripts - " + reason);
    }

    public static Duration getRuntime()
    {
        return startTimeMs <= 0L
                ? Duration.ZERO
                : Duration.ofMillis(Math.max(0L, System.currentTimeMillis() - startTimeMs));
    }

    private Rs2TileItemModel findLootTarget(int minimumGeValue)
    {
        List<Rs2TileItemModel> sceneItems = Microbot.getRs2TileItemCache().getStream()
                .filter(item -> item != null && !item.isDespawned())
                .filter(item -> KSPGELooterArea.contains(item.getWorldLocation()))
                .collect(Collectors.toList());
        groundItemsSeen = sceneItems.size();

        int accountType = accountType();
        WorldPoint player = Rs2Player.getWorldLocation();
        List<Rs2TileItemModel> candidates = sceneItems.stream()
                // Mirror RuneLite's TAKEABLE ownership rule. Main accounts may take public items
                // whose original ownership is OTHER; the old isLootAble() filter rejected them.
                .filter(item -> item.getOwnership() != TileItem.OWNERSHIP_OTHER || accountType == 0)
                .filter(item -> getGroundStackGeValue(item) >= minimumGeValue)
                .sorted(Comparator.comparingLong(this::getGroundStackGeValue)
                        .reversed()
                        .thenComparingInt(item -> distance(player, item.getWorldLocation())))
                .collect(Collectors.toList());
        eligibleGroundItems = candidates.size();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private void spamLoot(Rs2TileItemModel item, KSPGELooterConfig config)
    {
        if (item == null || !KSPGELooterArea.contains(item.getWorldLocation())) return;
        if (!prepareLootUi()) return;

        int clicks = clamp(config.spamClicks(), 1, 12);
        int delay = clamp(config.spamDelayMs(), 30, 250);
        int itemId = item.getId();
        int beforeQuantity = Rs2Inventory.itemQuantity(itemId);
        int unitGePrice = getGePrice(itemId);

        targetName = safeName(item);
        targetGeValue = getGroundStackGeValue(item);
        status = "Looting " + targetName;

        for (int i = 0; i < clicks; i++)
        {
            if (!Microbot.isLoggedIn()) return;
            WorldPoint player = Rs2Player.getWorldLocation();
            if (!KSPGELooterArea.contains(player))
            {
                status = "OUTSIDE AREA - PAUSED";
                insideArea = false;
                return;
            }
            if (!KSPGELooterArea.contains(item.getWorldLocation())) return;

            item.pickup();
            if (i + 1 < clicks) sleep(delay);
        }

        sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) > beforeQuantity, 900);
        int gained = Math.max(0, Rs2Inventory.itemQuantity(itemId) - beforeQuantity);
        if (gained > 0)
        {
            itemsLooted += gained;
            totalLootGeValue += (long) unitGePrice * gained;
            priorityReleaseAt = System.currentTimeMillis() + PRIORITY_RELEASE_GRACE_MS;
        }
        updateOverlayState();
    }

    private boolean prepareLootUi()
    {
        if (Rs2Bank.isOpen())
        {
            status = "Closing bank for priority loot";
            Rs2Bank.closeBank();
            if (!sleepUntil(() -> !Rs2Bank.isOpen(), 1_500)) return false;
        }
        if (Rs2GrandExchange.isOpen())
        {
            status = "Closing GE for priority loot";
            Rs2GrandExchange.closeExchange();
            if (!sleepUntil(() -> !Rs2GrandExchange.isOpen(), 1_500)) return false;
        }
        return true;
    }

    private boolean canStackIntoInventory(Rs2TileItemModel item)
    {
        try
        {
            return item != null && item.isStackable() && Rs2Inventory.itemQuantity(item.getId()) > 0;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private boolean tryHighAlch()
    {
        if (System.currentTimeMillis() - lastAlchAt < HIGH_ALCH_COOLDOWN_MS) return false;
        if (Microbot.getClient().getRealSkillLevel(Skill.MAGIC) < 55)
        {
            status = "High Alch requires 55 Magic";
            return false;
        }

        refreshRunePricesIfNeeded();
        boolean fireStaff = hasFireRuneStaff();
        long runeCost = calculateHighAlchRuneCost(fireStaff);
        alchRuneCost = runeCost;
        if (runeCost <= 0L)
        {
            status = "Waiting for rune GE prices";
            return false;
        }
        if (Rs2Inventory.itemQuantity(NATURE_RUNE_ID) < 1)
        {
            status = "No Nature runes";
            return false;
        }
        if (!fireStaff && Rs2Inventory.itemQuantity(FIRE_RUNE_ID) < 5)
        {
            status = "Need 5 Fire runes";
            return false;
        }

        Rs2ItemModel target = findBestHighAlchTarget(runeCost);
        if (target == null) return false;
        if (!KSPGELooterArea.contains(Rs2Player.getWorldLocation()))
        {
            status = "OUTSIDE AREA - PAUSED";
            return false;
        }

        int beforeCount = Rs2Inventory.itemQuantity(target.getId());
        String name = target.getName();
        int highAlchValue = getHighAlchValue(target.getId());
        long alchMargin = Math.max(0L, (long) highAlchValue - runeCost);

        status = "High Alching " + name;
        targetName = name;
        targetGeValue = 0L;
        lastAlchAt = System.currentTimeMillis();
        Rs2Magic.alch(target, 60, 120);

        if (sleepUntil(() -> Rs2Inventory.itemQuantity(target.getId()) < beforeCount, 2_500))
        {
            itemsAlched++;
            totalAlchValue += highAlchValue;
            totalAlchMargin += alchMargin;
            updateOverlayState();
        }
        return true;
    }

    private Rs2ItemModel findBestHighAlchTarget(long runeCost)
    {
        return Rs2Inventory.items()
                .filter(item -> item != null
                        && !isRune(item.getName())
                        && !"Coins".equalsIgnoreCase(item.getName())
                        && getHighAlchValue(item.getId()) > runeCost)
                .max(Comparator.comparingInt(item -> getHighAlchValue(item.getId())))
                .orElse(null);
    }

    private boolean bankNonRunes()
    {
        boolean releaseBankPause = acquireBankPause();
        try
        {
            if (!KSPGELooterArea.contains(Rs2Player.getWorldLocation()))
            {
                status = "OUTSIDE AREA - PAUSED";
                return false;
            }
            if (Rs2GrandExchange.isOpen())
            {
                Rs2GrandExchange.closeExchange();
                if (!sleepUntil(() -> !Rs2GrandExchange.isOpen(), 1_500)) return false;
            }

            status = "Opening GE bank";
            if (!Rs2Bank.isOpen())
            {
                if (!Rs2Bank.openBank())
                {
                    status = "Unable to open GE bank";
                    return false;
                }
                if (!sleepUntil(Rs2Bank::isOpen, 3_000))
                {
                    status = "Waiting for GE bank";
                    return false;
                }
            }

            if (!KSPGELooterArea.contains(Rs2Player.getWorldLocation()))
            {
                Rs2Bank.closeBank();
                status = "AREA GUARD - bank cancelled";
                return false;
            }

            boolean fireStaff = hasFireRuneStaff();
            status = fireStaff ? "Depositing - keeping Nature runes" : "Depositing - keeping Nature + Fire";
            if (fireStaff) Rs2Bank.depositAllExcept(NATURE_RUNE_ID);
            else Rs2Bank.depositAllExcept(NATURE_RUNE_ID, FIRE_RUNE_ID);

            sleepUntil(() -> !Rs2Inventory.isFull(), 2_000);
            Rs2Bank.closeBank();
            status = "Returning to looting";
            return true;
        }
        finally
        {
            if (releaseBankPause) releasePriorityPause("Bank transaction complete");
        }
    }

    private boolean acquireBankPause()
    {
        if (ownsPriorityPause || !Microbot.pauseAllScripts.compareAndSet(false, true)) return false;
        ownsPriorityPause = priorityPauseOwned = true;
        return true;
    }

    private void refreshRunePricesIfNeeded()
    {
        long now = System.currentTimeMillis();
        if (now - lastRunePriceRefresh < RUNE_PRICE_REFRESH_MS && natureRunePrice > 0 && fireRunePrice > 0) return;

        natureRunePrice = getGePrice(NATURE_RUNE_ID);
        fireRunePrice = getGePrice(FIRE_RUNE_ID);
        natureRuneGePrice = natureRunePrice;
        fireRuneGePrice = fireRunePrice;
        lastRunePriceRefresh = now;
        staffOfFireEquipped = hasFireRuneStaff();
        alchRuneCost = calculateHighAlchRuneCost(staffOfFireEquipped);
    }

    private long calculateHighAlchRuneCost(boolean fireStaff)
    {
        if (natureRunePrice <= 0 || (!fireStaff && fireRunePrice <= 0)) return 0L;
        return fireStaff ? natureRunePrice : (long) natureRunePrice + (5L * fireRunePrice);
    }

    private boolean hasFireRuneStaff() { return Rs2Equipment.isWearing(STAFF_OF_FIRE, true); }

    private int getHighAlchValue(int itemId)
    {
        ItemComposition composition = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(itemId))
                .orElse(null);
        return composition == null ? 0 : Math.max(0, composition.getHaPrice());
    }

    private int getGePrice(int itemId)
    {
        return Math.max(0, Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemPrice(itemId))
                .orElse(0));
    }

    private long getGroundStackGeValue(Rs2TileItemModel item)
    {
        return item == null ? 0L : Math.max(0L, (long) getGePrice(item.getId()) * Math.max(1, item.getQuantity()));
    }

    private int accountType()
    {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getVarbitValue(VarbitID.IRONMAN))
                .orElse(0);
    }

    private static boolean isRune(String name)
    {
        if (name == null) return false;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(" rune") || normalized.endsWith(" runes");
    }

    private static String safeName(Rs2TileItemModel item)
    {
        if (item == null) return "-";
        String name = item.getName();
        return name == null || name.isEmpty() ? "Unknown item" : name;
    }

    private static int distance(WorldPoint a, WorldPoint b)
    {
        return a == null || b == null || a.getPlane() != b.getPlane() ? Integer.MAX_VALUE : a.distanceTo(b);
    }

    private static int clamp(int value, int minimum, int maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int inventoryItemCount() { return (int) Rs2Inventory.items().count(); }

    private void updateOverlayState()
    {
        insideArea = KSPGELooterArea.contains(Rs2Player.getWorldLocation());
        inventorySlotsUsed = inventoryItemCount();
        natureRunes = Rs2Inventory.itemQuantity(NATURE_RUNE_ID);
        fireRunes = Rs2Inventory.itemQuantity(FIRE_RUNE_ID);
        staffOfFireEquipped = hasFireRuneStaff();
    }
}
