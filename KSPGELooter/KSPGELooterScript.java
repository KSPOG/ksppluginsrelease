package net.runelite.client.plugins.microbot.KSPGELooter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KSPGELooterScript extends Script
{
    // Stable OSRS item IDs; avoids ItemID package/name differences between Microbot branches.
    private static final int NATURE_RUNE_ID = 561;
    private static final int FIRE_RUNE_ID = 554;

    private static final long RUNE_PRICE_REFRESH_MS = 30_000L;
    private static final long HIGH_ALCH_COOLDOWN_MS = 3_000L;

    private static final String STAFF_OF_FIRE = "Staff of fire";

    public static volatile String status = "Idle";
    public static volatile String targetName = "-";
    public static volatile long targetGeValue = 0L;
    public static volatile long alchRuneCost = 0L;
    public static volatile int itemsLooted = 0;
    public static volatile int itemsAlched = 0;
    public static volatile long totalLootGeValue = 0L;
    public static volatile long totalAlchValue = 0L;
    public static volatile long totalAlchMargin = 0L;
    public static volatile int natureRuneGePrice = 0;
    public static volatile int fireRuneGePrice = 0;
    public static volatile int inventorySlotsUsed = 0;
    public static volatile int natureRunes = 0;
    public static volatile int fireRunes = 0;
    public static volatile boolean staffOfFireEquipped = false;
    public static volatile boolean insideArea = false;
    public static volatile boolean priorityTakeoverActive = false;
    public static volatile boolean priorityPauseOwned = false;

    private static long startTimeMs = 0L;

    private int natureRunePrice = 0;
    private int fireRunePrice = 0;
    private long lastRunePriceRefresh = 0L;
    private long lastAlchAt = 0L;
    private boolean ownsPriorityPause = false;


    public boolean run(KSPGELooterConfig config)
    {
        startTimeMs = System.currentTimeMillis();
        status = "Starting";
        targetName = "-";
        targetGeValue = 0L;
        alchRuneCost = 0L;
        itemsLooted = 0;
        itemsAlched = 0;
        totalLootGeValue = 0L;
        totalAlchValue = 0L;
        totalAlchMargin = 0L;
        natureRuneGePrice = 0;
        fireRuneGePrice = 0;
        inventorySlotsUsed = inventoryItemCount();
        natureRunes = 0;
        fireRunes = 0;
        staffOfFireEquipped = false;
        insideArea = false;
        priorityTakeoverActive = false;
        priorityPauseOwned = false;
        ownsPriorityPause = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                boolean baseCanRun = super.run();

                /*
                 * While this looter owns Priority Mode's global pause, Script.run()
                 * returns false because Microbot.pauseAllScripts is true. The looter
                 * must remain alive so it can finish the loot interrupt and release
                 * the exact pause it created.
                 */
                if (!baseCanRun && !ownsPriorityPause)
                {
                    return;
                }

                if (!Microbot.isLoggedIn())
                {
                    releasePriorityPause("Logged out");
                    return;
                }

                // If another controller explicitly cleared the shared pause, drop
                // stale ownership. A still-present loot target can acquire it again.
                if (ownsPriorityPause && !Microbot.pauseAllScripts.get())
                {
                    ownsPriorityPause = false;
                    priorityPauseOwned = false;
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
                    targetName = "-";
                    targetGeValue = 0L;

                    if (Rs2Bank.isOpen())
                    {
                        Rs2Bank.closeBank();
                    }
                    return;
                }

                refreshRunePricesIfNeeded();

                /*
                 * Detect eligible ground loot before inventory handling. This lets
                 * Priority Mode stop the other active script immediately, even when
                 * this looter first needs to alch/bank to create inventory space.
                 */
                Rs2TileItemModel lootTarget = findLootTarget(Math.max(0, config.minimumGeValue()));

                if (config.priorityMode())
                {
                    if (lootTarget != null)
                    {
                        beginPriorityTakeover();
                    }
                    else
                    {
                        releasePriorityPause("No eligible loot remains");

                        // Priority Mode is an interrupt-only mode: once loot is gone,
                        // immediately hand control back to the previously running script.
                        targetName = "-";
                        targetGeValue = 0L;
                        status = "Waiting for priority loot";
                        updateOverlayState();
                        return;
                    }
                }

                /*
                 * Full inventory rule when eligible loot needs space:
                 * 1) If High Alch is enabled and an actionable profitable alch exists, alch it.
                 * 2) Otherwise bank all non-rune items.
                 * Priority takeover remains active while qualifying ground loot still exists.
                 */
                if (Rs2Inventory.isFull() && lootTarget != null)
                {
                    if (config.highAlch() && tryHighAlch())
                    {
                        return;
                    }

                    bankNonRunes();
                    return;
                }

                if (lootTarget != null)
                {
                    spamLoot(lootTarget, config);
                    return;
                }

                // Normal mode retains the original idle-time High Alch behavior.
                if (config.highAlch() && tryHighAlch())
                {
                    return;
                }

                targetName = "-";
                targetGeValue = 0L;
                status = "Waiting for loot";
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

    @Override
    public void shutdown()
    {
        releasePriorityPause("Looter stopped");
        super.shutdown();
        status = "Stopped";
        targetName = "-";
        targetGeValue = 0L;
        alchRuneCost = 0L;
        insideArea = false;
        priorityTakeoverActive = false;
        priorityPauseOwned = false;
    }

    private void beginPriorityTakeover()
    {
        priorityTakeoverActive = true;

        if (ownsPriorityPause)
        {
            priorityPauseOwned = true;
            return;
        }

        /*
         * Only claim ownership when this looter actually changes the shared pause
         * flag from false -> true. If something else already paused scripts, the
         * looter must never clear that external pause later.
         */
        if (Microbot.pauseAllScripts.compareAndSet(false, true))
        {
            ownsPriorityPause = true;
            priorityPauseOwned = true;
            Microbot.log("KSP GE Looter Priority Mode: paused other scripts for loot");
        }
        else
        {
            priorityPauseOwned = false;
        }
    }

    private void releasePriorityPause(String reason)
    {
        priorityTakeoverActive = false;

        if (!ownsPriorityPause)
        {
            priorityPauseOwned = false;
            return;
        }

        /*
         * Resume only the pause state this looter owns. This preserves a pause that
         * already existed before Priority Mode attempted its takeover.
         */
        Microbot.pauseAllScripts.compareAndSet(true, false);
        ownsPriorityPause = false;
        priorityPauseOwned = false;
        Microbot.log("KSP GE Looter Priority Mode: resumed scripts - " + reason);
    }

    public static Duration getRuntime()
    {
        if (startTimeMs <= 0L)
        {
            return Duration.ZERO;
        }
        return Duration.ofMillis(Math.max(0L, System.currentTimeMillis() - startTimeMs));
    }

    private Rs2TileItemModel findLootTarget(int minimumGeValue)
    {
        List<Rs2TileItemModel> candidates = Microbot.getRs2TileItemCache()
                .query()
                .where(Rs2TileItemModel::isLootAble)
                .where(item -> !item.isDespawned())
                .where(item -> KSPGELooterArea.contains(item.getWorldLocation()))
                .where(item -> getGroundStackGeValue(item) >= minimumGeValue)
                .toList();

        if (candidates == null || candidates.isEmpty())
        {
            return null;
        }

        final WorldPoint player = Rs2Player.getWorldLocation();

        candidates.sort(
                Comparator.comparingLong(this::getGroundStackGeValue)
                        .reversed()
                        .thenComparingInt(item -> distance(player, item.getWorldLocation()))
        );

        return candidates.get(0);
    }

    private void spamLoot(Rs2TileItemModel item, KSPGELooterConfig config)
    {
        if (item == null || !KSPGELooterArea.contains(item.getWorldLocation()))
        {
            return;
        }

        int clicks = clamp(config.spamClicks(), 1, 12);
        int delay = clamp(config.spamDelayMs(), 30, 250);
        int itemId = item.getId();
        int beforeQuantity = Rs2Inventory.count(itemId);
        int unitGePrice = getGePrice(itemId);

        targetName = safeName(item);
        targetGeValue = getGroundStackGeValue(item);
        status = "Looting " + targetName;

        for (int i = 0; i < clicks; i++)
        {
            if (!Microbot.isLoggedIn())
            {
                return;
            }

            WorldPoint player = Rs2Player.getWorldLocation();
            if (!KSPGELooterArea.contains(player))
            {
                status = "OUTSIDE AREA - PAUSED";
                insideArea = false;
                return;
            }

            if (!KSPGELooterArea.contains(item.getWorldLocation()))
            {
                return;
            }

            item.pickup();

            if (i + 1 < clicks)
            {
                sleep(delay);
            }
        }

        // Confirm the actual quantity received so overlay loot statistics remain accurate.
        sleepUntil(() -> Rs2Inventory.count(itemId) > beforeQuantity, 900);
        int afterQuantity = Rs2Inventory.count(itemId);
        int gained = Math.max(0, afterQuantity - beforeQuantity);
        if (gained > 0)
        {
            itemsLooted += gained;
            totalLootGeValue += (long) unitGePrice * gained;
        }

        updateOverlayState();
    }

    private boolean tryHighAlch()
    {
        if (System.currentTimeMillis() - lastAlchAt < HIGH_ALCH_COOLDOWN_MS)
        {
            return false;
        }

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

        if (Rs2Inventory.count("Nature rune") < 1)
        {
            status = "No Nature runes";
            return false;
        }

        if (!fireStaff && Rs2Inventory.count("Fire rune") < 5)
        {
            status = "Need 5 Fire runes";
            return false;
        }

        Rs2ItemModel target = findBestHighAlchTarget(runeCost);
        if (target == null)
        {
            return false;
        }

        WorldPoint before = Rs2Player.getWorldLocation();
        if (!KSPGELooterArea.contains(before))
        {
            status = "OUTSIDE AREA - PAUSED";
            return false;
        }

        int beforeCount = Rs2Inventory.count(target.getId());
        String name = target.getName();
        int highAlchValue = getHighAlchValue(target.getId());
        long alchMargin = Math.max(0L, (long) highAlchValue - runeCost);

        status = "High Alching " + name;
        targetName = name;
        targetGeValue = 0L;

        lastAlchAt = System.currentTimeMillis();
        Rs2Magic.alch(target, 60, 120);

        boolean changed = sleepUntil(
                () -> Rs2Inventory.count(target.getId()) < beforeCount,
                2_500
        );

        if (changed)
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
        List<Rs2ItemModel> eligible = new ArrayList<>();

        Rs2Inventory.items().forEach(item -> {
            if (item == null)
            {
                return;
            }

            String name = item.getName();
            if (isRune(name) || "Coins".equalsIgnoreCase(name))
            {
                return;
            }

            int highAlchValue = getHighAlchValue(item.getId());
            if (highAlchValue > runeCost)
            {
                eligible.add(item);
            }
        });

        if (eligible.isEmpty())
        {
            return null;
        }

        eligible.sort((a, b) -> {
            long marginA = (long) getHighAlchValue(a.getId()) - runeCost;
            long marginB = (long) getHighAlchValue(b.getId()) - runeCost;
            return Long.compare(marginB, marginA);
        });

        return eligible.get(0);
    }

    private boolean bankNonRunes()
    {
        /*
         * A bank that was already open belongs to another script.  Never turn
         * that script's bank session into this looter's deposit transaction:
         * doing so can leave the other script to withdraw/process items that
         * this looter just deposited.  The second check closes the small race
         * between observing the bank and claiming the shared pause.
         */
        if (Rs2Bank.isOpen())
        {
            status = "Waiting for shared bank";
            return false;
        }

        /*
         * Banking changes the shared inventory and bank widget.  When another
         * script is active it can otherwise act on the open bank between this
         * script's deposit and close operations, undoing the space we just made.
         * Keep that short transaction exclusive, without taking ownership of an
         * existing pause created by another script.
         */
        boolean releaseBankPause = acquireBankPause();

        try
        {
            if (Rs2Bank.isOpen())
            {
                status = "Waiting for shared bank";
                return false;
            }

            WorldPoint before = Rs2Player.getWorldLocation();
            if (!KSPGELooterArea.contains(before))
            {
                status = "OUTSIDE AREA - PAUSED";
                return false;
            }

            status = "Opening GE bank";

            /*
             * Deliberately do NOT call walkToBank()/walkToBankAndUseBank().
             * openBank() interacts with a bank already available in the current
             * scene, and the area guard is checked before and after opening.
             */
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

            WorldPoint atBank = Rs2Player.getWorldLocation();
            if (!KSPGELooterArea.contains(atBank))
            {
                Rs2Bank.closeBank();
                status = "AREA GUARD - bank cancelled";
                return false;
            }

            boolean staffOfFireEquipped = hasFireRuneStaff();
            status = staffOfFireEquipped
                    ? "Depositing - keeping Nature runes"
                    : "Depositing - keeping Nature + Fire";

            if (staffOfFireEquipped)
            {
                Rs2Bank.depositAllExcept(NATURE_RUNE_ID);
            }
            else
            {
                Rs2Bank.depositAllExcept(NATURE_RUNE_ID, FIRE_RUNE_ID);
            }

            sleepUntil(() -> !Rs2Inventory.isFull(), 2_000);
            Rs2Bank.closeBank();

            if (!KSPGELooterArea.contains(Rs2Player.getWorldLocation()))
            {
                status = "OUTSIDE AREA - PAUSED";
                return false;
            }

            status = "Returning to looting";
            return true;
        }
        finally
        {
            if (releaseBankPause)
            {
                releasePriorityPause("Bank transaction complete");
            }
        }
    }

    /**
     * Acquires the same pause used by Priority Mode only for an inventory/bank
     * transaction.  The return value records whether this invocation owns the
     * pause and must release it.
     */
    private boolean acquireBankPause()
    {
        if (ownsPriorityPause)
        {
            return false;
        }

        if (Microbot.pauseAllScripts.compareAndSet(false, true))
        {
            ownsPriorityPause = true;
            priorityPauseOwned = true;
            return true;
        }

        return false;
    }

    private void refreshRunePricesIfNeeded()
    {
        long now = System.currentTimeMillis();
        if (now - lastRunePriceRefresh < RUNE_PRICE_REFRESH_MS
                && natureRunePrice > 0
                && fireRunePrice > 0)
        {
            return;
        }

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
        if (natureRunePrice <= 0)
        {
            return 0L;
        }

        if (fireStaff)
        {
            return natureRunePrice;
        }

        if (fireRunePrice <= 0)
        {
            return 0L;
        }

        // High Level Alchemy consumes 1 Nature rune + 5 Fire runes.
        return (long) natureRunePrice + (5L * fireRunePrice);
    }

    private boolean hasFireRuneStaff()
    {
        // User requirement is intentionally exact: only Staff of fire counts.
        return Rs2Equipment.isWearing(STAFF_OF_FIRE, true);
    }

    private int getHighAlchValue(int itemId)
    {
        ItemComposition composition = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(itemId))
                .orElse(null);

        return composition == null ? 0 : Math.max(0, composition.getHaPrice());
    }

    private int getGePrice(int itemId)
    {
        Integer price = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getItemManager().getItemPrice(itemId))
                .orElse(0);

        return price == null ? 0 : Math.max(0, price);
    }

    private long getGroundStackGeValue(Rs2TileItemModel item)
    {
        if (item == null)
        {
            return 0L;
        }

        int unitPrice = getGePrice(item.getId());
        return Math.max(0L, (long) unitPrice * Math.max(1, item.getQuantity()));
    }

    private static boolean isRune(String name)
    {
        if (name == null)
        {
            return false;
        }

        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(" rune") || normalized.endsWith(" runes");
    }

    private static String safeName(Rs2TileItemModel item)
    {
        if (item == null)
        {
            return "-";
        }

        String name = item.getName();
        return name == null || name.isEmpty() ? "Unknown item" : name;
    }

    private static int distance(WorldPoint a, WorldPoint b)
    {
        if (a == null || b == null || a.getPlane() != b.getPlane())
        {
            return Integer.MAX_VALUE;
        }
        return a.distanceTo(b);
    }

    private static int clamp(int value, int minimum, int maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int inventoryItemCount()
    {
        return (int) Rs2Inventory.items().count();
    }

    private void updateOverlayState()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        insideArea = KSPGELooterArea.contains(location);
        inventorySlotsUsed = inventoryItemCount();
        natureRunes = Rs2Inventory.count("Nature rune");
        fireRunes = Rs2Inventory.count("Fire rune");
        staffOfFireEquipped = hasFireRuneStaff();
    }
}
