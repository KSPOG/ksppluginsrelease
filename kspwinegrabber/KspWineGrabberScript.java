package net.runelite.client.plugins.microbot.kspwinegrabber;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.kspsupport.KspBreakAwareScript;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Upstairs-only wine collector for the Asgarnian Chaos Temple.
 *
 * User-selected standing tile: (2939, 3517, 1). Banking is Falador West
 * (2946, 3368, 0). The script never intentionally uses the ground-floor wine.
 */
@Slf4j
public class KspWineGrabberScript extends KspBreakAwareScript
{
    private static final int WINE_ID = 245;
    private static final int LAW_RUNE_ID = 563;
    private static final int STAFF_ID = 1381;
    private static final int ROBE_TOP_ID = 1035;
    private static final int ROBE_BOTTOM_ID = 1033;

    private static final WorldPoint STANDING_TILE = new WorldPoint(2939, 3517, 1);
    private static final WorldPoint TEMPLE_GROUND = new WorldPoint(2939, 3517, 0);
    private static final WorldPoint BANK_TILE = new WorldPoint(2946, 3368, 0);

    private static final int MIN_TOTAL_LEVEL = 500;
    private static final int MIN_MAGIC_LEVEL = 33;
    private static final int MAX_LADDER_FAILURES = 10;
    private static final long ACTION_INTERVAL_MS = 1400L;
    private static final long LOOP_INTERVAL_MS = 200L;
    private static final long CAST_CONFIRMATION_MS = 4500L;

    @Inject private KspWineGrabberPlugin plugin;

    private volatile State state = State.STARTING;
    private volatile String status = "Starting";
    private volatile long startedAtMs;
    private volatile long collected;
    private volatile boolean stopped;

    private long nextActionAt;
    private long pendingCastAt;
    private int beforeCastCount;
    private int ladderFailures;
    private int equipmentFailures;
    private int castFailures;
    private int runeWithdrawFailures;
    private boolean awaitingCast;

    private enum State
    {
        STARTING,
        BANKING,
        EQUIPPING,
        TO_TEMPLE,
        COLLECTING,
        TO_BANK,
        STOPPED
    }

    public boolean run()
    {
        startedAtMs = System.currentTimeMillis();
        collected = 0L;
        stopped = false;
        awaitingCast = false;
        nextActionAt = pendingCastAt = 0L;
        ladderFailures = equipmentFailures = castFailures = runeWithdrawFailures = 0;
        state = State.STARTING;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (stopped || !Microbot.isLoggedIn())
            {
                return;
            }

            try
            {
                if (!super.run())
                {
                    return;
                }

                process();
            }
            catch (Exception ex)
            {
                if (!stopped && !Thread.currentThread().isInterrupted())
                {
                    log.error("KSP Wine Grabber loop failed", ex);
                    fail("Unexpected error. See client.log: " + ex.getMessage());
                }
            }
        }, 0L, LOOP_INTERVAL_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void process()
    {
        if (awaitingCast)
        {
            confirmCast();
            return;
        }
        if (System.currentTimeMillis() < nextActionAt)
        {
            return;
        }

        if (state == State.STARTING)
        {
            int[] skills = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    new int[]{
                            Microbot.getClient().getTotalLevel(),
                            Microbot.getClient().getRealSkillLevel(Skill.MAGIC)
                    }).orElse(null);
            if (skills == null)
            {
                status = "Waiting for skill data";
                return;
            }
            if (skills[0] < MIN_TOTAL_LEVEL)
            {
                fail("500 total level is required. Current total: " + skills[0] + ".");
                return;
            }
            if (skills[1] < MIN_MAGIC_LEVEL)
            {
                fail("33 Magic is required to cast Telekinetic Grab. Current Magic: " + skills[1] + ".");
                return;
            }
            state = State.BANKING;
            status = "Checking inventory, equipment and bank";
            return;
        }

        if (state == State.BANKING)
        {
            bank();
            return;
        }
        if (state == State.EQUIPPING)
        {
            equip();
            return;
        }

        if (!gearEquipped())
        {
            state = anyGearInInventory() ? State.EQUIPPING : State.BANKING;
            status = "Restoring required equipment";
            return;
        }
        if (Rs2Inventory.itemQuantity(LAW_RUNE_ID) <= 0)
        {
            state = State.TO_BANK;
            status = "Out of law runes - walking to bank";
        }

        switch (state)
        {
            case TO_TEMPLE:
                reachTemple();
                break;
            case COLLECTING:
                collectWine();
                break;
            case TO_BANK:
                returnToBank();
                break;
            default:
                break;
        }
    }

    private void bank()
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (!Rs2Bank.isOpen())
        {
            // Route back downstairs first. Never attempt a bank route directly
            // from plane 1 without interacting with the temple ladder.
            if (here != null && here.getPlane() == 1)
            {
                descendLadder();
                return;
            }

            if (here == null || here.distanceTo(BANK_TILE) > 7)
            {
                status = "Walking to Falador West bank";
                walkOnly(BANK_TILE, 3);
                retryAfter(ACTION_INTERVAL_MS);
                return;
            }

            status = "Opening bank";
            Rs2Bank.openBank();
            retryAfter(ACTION_INTERVAL_MS);
            return;
        }

        // Deposit ONLY accumulated wine; do not destroy the player's other items.
        if (Rs2Inventory.itemQuantity(WINE_ID) > 0)
        {
            status = "Depositing all Wine of Zamorak";
            Rs2Bank.depositAll(WINE_ID);
            retryAfter(600L);
            return;
        }

        if (!Rs2Bank.setWithdrawAsItem())
        {
            status = "Switching bank to unnoted items";
            retryAfter(700L);
            return;
        }

        if (!ensureBankGear(STAFF_ID, "Staff of air")
                || !ensureBankGear(ROBE_TOP_ID, "Zamorak monk top")
                || !ensureBankGear(ROBE_BOTTOM_ID, "Zamorak monk bottom"))
        {
            return;
        }

        int bankRunes = Rs2Bank.count(LAW_RUNE_ID);
        int inventoryRunes = Rs2Inventory.itemQuantity(LAW_RUNE_ID);
        if (bankRunes + inventoryRunes <= 0)
        {
            fail("Missing Law rune: none found in the bank or inventory.");
            return;
        }

        if (bankRunes > 0)
        {
            if (inventoryRunes == 0 && Rs2Inventory.emptySlotCount() == 0)
            {
                fail("No inventory space available to withdraw Law runes.");
                return;
            }
            status = "Withdrawing all Law runes";
            if (!Rs2Bank.withdrawAll(LAW_RUNE_ID))
            {
                if (++runeWithdrawFailures >= 6)
                {
                    fail("Repeatedly failed to withdraw Law runes from bank.");
                    return;
                }
            }
            else
            {
                runeWithdrawFailures = 0;
            }
            retryAfter(800L);
            return;
        }

        if (Rs2Inventory.itemQuantity(LAW_RUNE_ID) <= 0)
        {
            status = "Waiting for law rune withdrawal";
            retryAfter(750L);
            return;
        }

        status = "Closing bank and equipping robes / staff";
        if (Rs2Bank.closeBank())
        {
            state = State.EQUIPPING;
            ladderFailures = 0;
        }
        retryAfter(800L);
    }

    private boolean ensureBankGear(int itemId, String itemName)
    {
        if (Rs2Equipment.isWearing(itemId) || Rs2Inventory.contains(itemId))
        {
            return true;
        }

        // An open-bank query is authoritative: no stale cache or assumed supplies.
        if (Rs2Bank.count(itemId) <= 0)
        {
            fail("Missing " + itemName + ": checked bank, inventory and worn equipment.");
            return false;
        }

        if (Rs2Inventory.emptySlotCount() == 0)
        {
            fail("No free inventory slot to withdraw " + itemName + ".");
            return false;
        }

        status = "Withdrawing " + itemName;
        Rs2Bank.withdrawOne(itemId);
        retryAfter(800L);
        return false; // One bank action per tick; verify before withdrawing the next item.
    }

    private boolean anyGearInInventory()
    {
        return Rs2Inventory.contains(STAFF_ID)
                || Rs2Inventory.contains(ROBE_TOP_ID)
                || Rs2Inventory.contains(ROBE_BOTTOM_ID);
    }

    private boolean gearEquipped()
    {
        return Rs2Equipment.isWearing(STAFF_ID)
                && Rs2Equipment.isWearing(ROBE_TOP_ID)
                && Rs2Equipment.isWearing(ROBE_BOTTOM_ID);
    }

    private void equip()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            retryAfter(750L);
            return;
        }

        if (gearEquipped())
        {
            equipmentFailures = 0;
            state = State.TO_TEMPLE;
            status = "Equipment ready";
            return;
        }

        if (!wear(STAFF_ID, "Staff of air", "Wield")
                || !wear(ROBE_TOP_ID, "Zamorak monk top", "Wear")
                || !wear(ROBE_BOTTOM_ID, "Zamorak monk bottom", "Wear"))
        {
            return;
        }

        if (!gearEquipped())
        {
            retryAfter(700L);
        }
    }

    private boolean wear(int id, String name, String action)
    {
        if (Rs2Equipment.isWearing(id))
        {
            return true;
        }

        if (!Rs2Inventory.contains(id))
        {
            state = State.BANKING;
            status = "Returning to bank for " + name;
            return false;
        }

        status = "Equipping " + name;
        Rs2Inventory.interact(name, action, true);
        retryAfter(800L);
        if (++equipmentFailures > 12)
        {
            fail("Unable to equip required items. Check inventory slots, equipment requirements and game messages.");
        }
        return false;
    }

    private void reachTemple()
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null)
        {
            return;
        }

        if (here.getPlane() == 1)
        {
            if (here.distanceTo(STANDING_TILE) > 2)
            {
                status = "Walking to 2939, 3517, 1";
                walkOnly(STANDING_TILE, 1);
                retryAfter(ACTION_INTERVAL_MS);
            }
            else
            {
                ladderFailures = 0;
                state = State.COLLECTING;
                status = "Waiting for upstairs wine";
            }
            return;
        }

        if (here.distanceTo(TEMPLE_GROUND) > 6)
        {
            status = "Walking to Asgarnian Chaos Temple";
            walkOnly(TEMPLE_GROUND, 3);
            retryAfter(ACTION_INTERVAL_MS);
            return;
        }

        status = "Climbing upstairs ladder";
        if (Rs2GameObject.interact("Ladder", "Climb-up", true))
        {
            sleepUntil(() -> isOnPlane(1), 4500);
            ladderFailures = 0;
        }
        else if (++ladderFailures >= MAX_LADDER_FAILURES)
        {
            fail("Unable to locate/climb the Chaos Temple upstairs ladder.");
        }
        retryAfter(1500L);
    }

    private void collectWine()
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null || here.getPlane() != 1 || here.distanceTo(STANDING_TILE) > 3)
        {
            state = State.TO_TEMPLE;
            return;
        }
        if (Rs2Inventory.isFull())
        {
            state = State.TO_BANK;
            status = "Inventory full - walking to bank";
            return;
        }
        if (Rs2Inventory.itemQuantity(LAW_RUNE_ID) <= 0)
        {
            state = State.TO_BANK;
            return;
        }

        int boostedMagic = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getBoostedSkillLevel(Skill.MAGIC)).orElse(0);
        if (boostedMagic < MIN_MAGIC_LEVEL)
        {
            fail("Current Magic level is below 33. Cannot cast Telekinetic Grab.");
            return;
        }

        Rs2TileItemModel wine = Microbot.getRs2TileItemCache().query()
                .fromWorldView()
                .withId(WINE_ID)
                .within(STANDING_TILE, 12)
                .where(item -> item.getWorldLocation().getPlane() == 1)
                .nearestOnClientThread();

        if (wine == null)
        {
            status = "Waiting for upstairs wine respawn";
            retryAfter(850L);
            return;
        }

        status = "Casting Telekinetic Grab";
        if (!Rs2Magic.cast(MagicAction.TELEKINETIC_GRAB))
        {
            if (++castFailures >= 5)
            {
                fail("Unable to select Telekinetic Grab. Check spellbook and rune requirements.");
            }
            retryAfter(1200L);
            return;
        }

        if (!sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().isWidgetSelected()).orElse(false), 2000))
        {
            retryAfter(1000L);
            return;
        }

        // This is a SPELL-ON-GROUND-ITEM menu action. A regular "Take" option
        // would be wrong and can provoke the upstairs monk.
        LocalPoint local = Microbot.getClientThread().runOnClientThreadOptional(
                wine::getLocalLocation).orElse(null);
        if (local == null)
        {
            retryAfter(850L);
            return;
        }

        Polygon polygon = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Perspective.getCanvasTilePoly(Microbot.getClient(), local)).orElse(null);
        Rectangle bounds = polygon == null
                ? new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(),
                        Microbot.getClient().getCanvasHeight())
                : polygon.getBounds();

        beforeCastCount = Rs2Inventory.itemQuantity(WINE_ID);
        Microbot.doInvoke(new NewMenuEntry()
                        .option("Cast")
                        .target("<col=ff9040>Wine of Zamorak")
                        .identifier(WINE_ID)
                        .opcode(MenuAction.WIDGET_TARGET_ON_GROUND_ITEM.getId())
                        .param0(local.getSceneX())
                        .param1(local.getSceneY())
                        .itemId(-1)
                        .worldViewId(local.getWorldView()),
                bounds);

        awaitingCast = true;
        pendingCastAt = System.currentTimeMillis();
        status = "Waiting for wine pickup confirmation";
    }

    private void confirmCast()
    {
        int current = Rs2Inventory.itemQuantity(WINE_ID);
        if (current > beforeCastCount)
        {
            collected += current - beforeCastCount;
            castFailures = 0;
            awaitingCast = false;
            status = "Wine acquired";
            retryAfter(950L);
            if (Rs2Inventory.isFull())
            {
                state = State.TO_BANK;
            }
            return;
        }

        if (System.currentTimeMillis() - pendingCastAt < CAST_CONFIRMATION_MS)
        {
            return;
        }

        awaitingCast = false;
        status = "No wine received - waiting / retrying";
        retryAfter(1400L);
    }

    private void returnToBank()
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null)
        {
            return;
        }

        if (here.getPlane() == 1)
        {
            descendLadder();
            return;
        }

        if (here.distanceTo(BANK_TILE) > 7)
        {
            status = "Walking to Falador West bank (no teleport)";
            walkOnly(BANK_TILE, 3);
            retryAfter(ACTION_INTERVAL_MS);
            return;
        }

        status = "Banking gathered wines";
        state = State.BANKING;
    }

    private void descendLadder()
    {
        status = "Climbing down Chaos Temple ladder";
        if (Rs2GameObject.interact("Ladder", "Climb-down", true))
        {
            sleepUntil(() -> isOnPlane(0), 4500);
            ladderFailures = 0;
        }
        else if (++ladderFailures >= MAX_LADDER_FAILURES)
        {
            fail("Unable to climb down the Chaos Temple ladder.");
        }
        retryAfter(1500L);
    }

    private boolean isOnPlane(int plane)
    {
        WorldPoint here = Rs2Player.getWorldLocation();
        return here != null && here.getPlane() == plane;
    }

    /**
     * Rs2Walker is global. Restore its teleport setting after EVERY walking
     * operation, including exceptional/interrupt paths.
     */
    private void walkOnly(WorldPoint destination, int distance)
    {
        boolean previous = Rs2Walker.disableTeleports;
        try
        {
            Rs2Walker.disableTeleports = true;
            Rs2Walker.walkTo(destination, distance);
        }
        finally
        {
            Rs2Walker.disableTeleports = previous;
        }
    }

    private void retryAfter(long millis)
    {
        nextActionAt = System.currentTimeMillis() + millis;
    }

    private void fail(String message)
    {
        status = message;
        state = State.STOPPED;
        stopped = true;
        plugin.stopWithError(message);
    }

    void stopNow(String message)
    {
        status = message;
        state = State.STOPPED;
        stopped = true;
        super.shutdown();
    }

    @Override
    public void shutdown()
    {
        stopped = true;
        awaitingCast = false;
        state = State.STOPPED;
        status = "Stopped";
        super.shutdown();
    }

    public String getStatus()
    {
        return status;
    }

    public long getRuntimeMs()
    {
        return Math.max(0L, System.currentTimeMillis() - startedAtMs);
    }

    public long getCollected()
    {
        return collected;
    }

    public long getWinesPerHour()
    {
        long runtime = getRuntimeMs();
        return runtime <= 0 ? 0 : Math.round(collected * 3600000.0 / runtime);
    }
}
