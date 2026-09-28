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
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    private static final long PRIORITY_HANDOFF_SETTLE_MS = 1_500L;
    private static final int FAILED_TARGET_REJECT_THRESHOLD = 2;
    private static final int MAX_IMMEDIATE_LOOT_HANDOFFS = 12;
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
    private long priorityHandoffReadyAt;
    private volatile boolean stopping;
    private boolean ownsPriorityPause;

    // Targets that repeatedly fail or are not collision-reachable are ignored
    // until that exact ground item disappears. This prevents a single bad
    // target from pinning Priority Mode in "Retrying Take" forever.
    private final Map<String, Integer> failedLootTargets = new HashMap<>();
    private final Set<String> rejectedLootTargets = new HashSet<>();

    public boolean run(KSPGELooterConfig config)
    {
        resetSessionState();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (stopping) return;
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
                    releasePriorityPause("Returning to defined GE area");
                    groundItemsSeen = eligibleGroundItems = 0;
                    clearTarget();
                    returnToDefinedArea();
                    return;
                }

                WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();
                if (walkerTarget != null && !KSPGELooterArea.contains(walkerTarget))
                {
                    Rs2Walker.clearWalkingRoute("ge-looter-hard-area-guard");
                    status = "AREA GUARD - blocked outbound walk";
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

                // Pause first, then let an already-running bank/GE call finish before closing its UI.
                if (priorityTakeoverActive
                        && System.currentTimeMillis() < priorityHandoffReadyAt
                        && (Rs2Bank.isOpen() || Rs2GrandExchange.isOpen()))
                {
                    status = "Priority handoff - waiting for active bank/GE action";
                    return;
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
        priorityReleaseAt = lastAlchAt = lastRunePriceRefresh = priorityHandoffReadyAt = 0L;
        stopping = false;
        failedLootTargets.clear();
        rejectedLootTargets.clear();
        staffOfFireEquipped = insideArea = priorityTakeoverActive = priorityPauseOwned = ownsPriorityPause = false;
    }

    @Override
    public void shutdown()
    {
        stopping = true;
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
        if (stopping) return;
        boolean firstTakeover = !priorityTakeoverActive;
        priorityTakeoverActive = true;

        // Cancel walking once, then give any in-flight bank/GE call time to complete cooperatively.
        if (firstTakeover)
        {
            Rs2Walker.clearWalkingRoute("ge-looter-priority-takeover");
            priorityHandoffReadyAt = System.currentTimeMillis() + PRIORITY_HANDOFF_SETTLE_MS;
        }

        if (ownsPriorityPause)
        {
            priorityPauseOwned = true;
            return;
        }

        if (Microbot.pauseAllScripts.compareAndSet(false, true))
        {
            ownsPriorityPause = priorityPauseOwned = true;
            Microbot.log("KSP GE Looter Priority Mode: paused other scripts; waiting for safe UI handoff");
        }
        else
        {
            // Another component already owns the shared pause. Never release a pause we did not acquire.
            priorityPauseOwned = false;
        }
    }

    private void releasePriorityPause(String reason)
    {
        priorityTakeoverActive = false;
        priorityPauseOwned = false;
        priorityReleaseAt = priorityHandoffReadyAt = 0L;
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

    private Rs2TileItemModel findLootTarget(int minimumGeValue) { return findLootTarget(minimumGeValue, null); }

    private Rs2TileItemModel findLootTarget(int minimumGeValue, String excludeKey)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (!KSPGELooterArea.contains(player))
        {
            groundItemsSeen = eligibleGroundItems = 0;
            return null;
        }

        // Busy GE scenes used to allocate two Lists and sort every 100 ms scan.
        // Select the best target in one pass instead: no candidate List, no sort,
        // and each item's loot key / GE value is computed at most once per scan.
        Set<WorldPoint> reachableTiles = Rs2Tile.getReachableTilesFromTile(player, 40).keySet();
        boolean pruneTracked = !rejectedLootTargets.isEmpty() || !failedLootTargets.isEmpty();
        Set<String> liveTrackedKeys = pruneTracked ? new HashSet<>() : null;
        Iterator<Rs2TileItemModel> iterator = Microbot.getRs2TileItemCache().getStream().iterator();

        Rs2TileItemModel best = null;
        long bestValue = Long.MIN_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        int seen = 0;
        int eligible = 0;
        int accountType = accountType();

        while (iterator.hasNext())
        {
            Rs2TileItemModel item = iterator.next();
            if (item == null || item.isDespawned()) continue;

            WorldPoint location = item.getWorldLocation();
            if (!KSPGELooterArea.contains(location)) continue;
            seen++;

            String key = null;
            if (pruneTracked || excludeKey != null)
            {
                key = lootKey(item);
                if (pruneTracked
                        && (rejectedLootTargets.contains(key) || failedLootTargets.containsKey(key)))
                {
                    liveTrackedKeys.add(key);
                }
            }

            if (item.getOwnership() == TileItem.OWNERSHIP_OTHER && accountType != 0) continue;
            if (key != null && rejectedLootTargets.contains(key)) continue;
            if (excludeKey != null && excludeKey.equals(key)) continue;
            if (!reachableTiles.contains(location)) continue;

            long value = getGroundStackGeValue(item);
            if (value < minimumGeValue) continue;

            eligible++;
            int itemDistance = distance(player, location);
            if (best == null || value > bestValue || (value == bestValue && itemDistance < bestDistance))
            {
                best = item;
                bestValue = value;
                bestDistance = itemDistance;
            }
        }

        if (pruneTracked)
        {
            rejectedLootTargets.retainAll(liveTrackedKeys);
            failedLootTargets.keySet().retainAll(liveTrackedKeys);
        }

        groundItemsSeen = seen;
        eligibleGroundItems = eligible;
        return best;
    }

    private void spamLoot(Rs2TileItemModel firstItem, KSPGELooterConfig config)
    {
        if (firstItem == null || !prepareLootUi()) return;

        Rs2TileItemModel item = firstItem;
        int minimumGeValue = Math.max(0, config.minimumGeValue());
        int attempts = clamp(config.spamClicks(), 1, 12);
        int delay = clamp(config.spamDelayMs(), 30, 250);

        /*
         * Fast handoff loop:
         * once the inventory changes OR the current ground item disappears,
         * immediately select and invoke Take on the next eligible item without
         * returning to the 100 ms scheduler first.
         */
        for (int handoff = 0;
             handoff < MAX_IMMEDIATE_LOOT_HANDOFFS && item != null && Microbot.isLoggedIn();
             handoff++)
        {
            WorldPoint tile = item.getWorldLocation();
            if (!isStrictlyAllowedLootTile(tile))
            {
                rejectLootTarget(item, "outside defined area or collision-unreachable");
                item = findLootTarget(minimumGeValue);
                continue;
            }

            Rs2TileItemModel live = findLiveGroundItem(item.getId(), tile);
            if (live == null)
            {
                // It disappeared before we clicked it. Move straight to the next item.
                failedLootTargets.remove(lootKey(item));
                rejectedLootTargets.remove(lootKey(item));
                item = findLootTarget(minimumGeValue);
                continue;
            }

            WorldPoint player = Rs2Player.getWorldLocation();
            if (!KSPGELooterArea.contains(player))
            {
                status = "AREA GUARD - loot cancelled";
                insideArea = false;
                Rs2Walker.clearWalkingRoute("ge-looter-left-area-during-loot");
                return;
            }

            if (Rs2Inventory.isFull() && !canStackIntoInventory(live))
            {
                return;
            }

            String key = lootKey(live);
            int itemId = live.getId();
            int beforeQuantity = Rs2Inventory.itemQuantity(itemId);
            int beforeSlots = inventoryItemCount();
            int unitGePrice = getGePrice(itemId);

            targetName = safeName(live);
            targetGeValue = getGroundStackGeValue(live);
            status = "Looting " + targetName;

            boolean dispatched = false;
            boolean changedOrGone = false;

            for (int attempt = 0; attempt < attempts && Microbot.isLoggedIn(); attempt++)
            {
                live = findLiveGroundItem(itemId, tile);
                if (live == null)
                {
                    changedOrGone = true;
                    break;
                }

                if (!isStrictlyAllowedLootTile(live.getWorldLocation()))
                {
                    rejectLootTarget(live, "failed live area/reachability guard");
                    changedOrGone = true;
                    break;
                }

                if (!clearSelectedWidget()) return;

                // Rs2TileItemModel.pickup()/click("Take") dispatches through
                // Microbot.doInvoke (GROUND_ITEM_* menu action); no mouse click.
                boolean invoked = invokeTake(live);
                if (!invoked)
                {
                    status = "Take invoke failed " + targetName;
                    if (attempt + 1 < attempts) sleep(delay);
                    continue;
                }
                dispatched = true;

                int distance = distance(Rs2Player.getWorldLocation(), tile);
                int wait = Math.min(2_500, Math.max(400, 400 + distance * 160));
                changedOrGone = sleepUntil(() ->
                        Rs2Inventory.itemQuantity(itemId) != beforeQuantity
                                || inventoryItemCount() != beforeSlots
                                || findLiveGroundItem(itemId, tile) == null
                                || !KSPGELooterArea.contains(Rs2Player.getWorldLocation()),
                        wait);

                if (changedOrGone)
                {
                    break; // no spam-delay here: hand off immediately below
                }
                if (attempt + 1 < attempts) sleep(delay);
            }

            if (!KSPGELooterArea.contains(Rs2Player.getWorldLocation()))
            {
                rejectLootTarget(item, "Take interaction attempted to leave defined area");
                status = "AREA GUARD - rejected loot target";
                Rs2Walker.clearWalkingRoute("ge-looter-rejected-outbound-loot");
                return;
            }

            int gained = Math.max(0, Rs2Inventory.itemQuantity(itemId) - beforeQuantity);
            boolean disappeared = findLiveGroundItem(itemId, tile) == null;
            boolean inventoryChanged = gained > 0 || inventoryItemCount() != beforeSlots;

            if (gained > 0)
            {
                itemsLooted += gained;
                totalLootGeValue += (long) unitGePrice * gained;
                failedLootTargets.remove(key);
                rejectedLootTargets.remove(key);
                priorityReleaseAt = System.currentTimeMillis() + PRIORITY_RELEASE_GRACE_MS;
            }
            else if (!inventoryChanged && !disappeared)
            {
                int failures = failedLootTargets.merge(key, 1, Integer::sum);
                if (!dispatched || failures >= FAILED_TARGET_REJECT_THRESHOLD)
                {
                    rejectLootTarget(item,
                            !dispatched
                                    ? "Take invoke could not be dispatched"
                                    : "Take was not confirmed after " + failures + " cycles");
                }
                else
                {
                    status = "Take not confirmed - one retry allowed";
                    updateOverlayState();
                    return;
                }
            }
            else
            {
                // Another player can make the target disappear. That is a successful
                // handoff condition, not a reason to retry the vanished target.
                failedLootTargets.remove(key);
                rejectedLootTargets.remove(key);
            }

            updateOverlayState();

            // Critical fast path: inventory changed or target vanished -> immediately
            // pick a DIFFERENT valid target and invoke it in this same scheduler pass.
            // Excluding the just-completed cache key prevents one-tick cache lag from
            // re-invoking Take on an item we already picked up.
            item = findLootTarget(minimumGeValue, key);
        }
    }

    private boolean invokeTake(Rs2TileItemModel item) { return item != null && item.pickup(); }

    private Rs2TileItemModel findLiveGroundItem(int itemId, WorldPoint tile)
    {
        if (!isStrictlyAllowedLootTile(tile)) return null;

        return Microbot.getRs2TileItemCache().getStream()
                .filter(i -> i != null && !i.isDespawned() && i.getId() == itemId)
                .filter(i -> tile.equals(i.getWorldLocation()))
                .filter(i -> isStrictlyAllowedLootTile(i.getWorldLocation()))
                .findFirst().orElse(null);
    }

    private boolean isStrictlyAllowedLootTile(WorldPoint tile)
    {
        return tile != null
                && KSPGELooterArea.contains(tile)
                && Rs2Tile.isTileReachable(tile);
    }

    private void rejectLootTarget(Rs2TileItemModel item, String reason)
    {
        if (item == null) return;
        String key = lootKey(item);
        rejectedLootTargets.add(key);
        failedLootTargets.remove(key);
        clearTarget();
        log.warn("KSP GE Looter: rejected loot target {} id={} at {} - {}",
                safeName(item), item.getId(), item.getWorldLocation(), reason);
        status = "Ignored unsafe/unreachable loot";
    }

    private String lootKey(Rs2TileItemModel item)
    {
        if (item == null || item.getWorldLocation() == null) return "invalid";
        WorldPoint p = item.getWorldLocation();
        return item.getId() + "@" + p.getX() + "," + p.getY() + "," + p.getPlane();
    }

    private boolean clearSelectedWidget()
    {
        if (!Microbot.getClient().isWidgetSelected()) return true;
        status = "Clearing stale selection for Take";
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        return sleepUntil(() -> !Microbot.getClient().isWidgetSelected(), 600);
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
            returnToDefinedArea();
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
                returnToDefinedArea();
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
                // Restore the original GE Looter banking path. Rs2Bank.openBank()
                // performs Microbot's native bank target discovery and uses the
                // invoke-backed object/NPC interaction internally.
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
            status = KSPGELooterArea.contains(Rs2Player.getWorldLocation())
                    ? "Returning to looting"
                    : "Returning to defined GE area";
            return true;
        }
        finally
        {
            if (releaseBankPause) releasePriorityPause("Bank transaction complete");
        }
    }

    /**
     * GE Looter owns its return-to-area behavior; it does not rely on a shared
     * bank helper for routing. Any accidental/outbound position is routed back
     * into the exact configured GE looting rectangle before scanning resumes.
     */
    private boolean returnToDefinedArea()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (KSPGELooterArea.contains(player))
        {
            insideArea = true;
            status = "Returning to looting";
            return true;
        }

        WorldPoint returnPoint = KSPGELooterArea.returnPoint();
        WorldPoint currentTarget = Rs2Walker.getCurrentTarget();
        if (currentTarget != null && !KSPGELooterArea.contains(currentTarget))
        {
            Rs2Walker.clearWalkingRoute("ge-looter-return-to-defined-area");
        }

        status = "Returning to defined GE area";
        boolean arrived = Rs2Walker.walkTo(returnPoint, 2);
        updateOverlayState();
        return arrived && insideArea;
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
        long price = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> (long) Microbot.getItemManager().getItemPrice(itemId))
                .orElse(0L);
        return price <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, price);
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

    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }

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
