package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.runeessscript;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.inv.Equip;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EssenceMining extends Script
{
    private static final Logger log = LoggerFactory.getLogger(EssenceMining.class);
    private static final int LOOP_DELAY_MS = 100;
    private static final int ESSENCE_MINE_REGION = 11595;
    private static final int ESSENCE_MINE_PORTAL_ID = 3086;
    private static final int NPC_REACH_DISTANCE = 5;
    private static final int PORTAL_APPROACH_DISTANCE = 6;
    private static final long WALK_REFIRE_COOLDOWN_MS = 1_000L;
    private static final long ACTION_COOLDOWN_MS = 100L;
    private static final long ACTION_DISPATCH_TIMEOUT_MS = 1_500L;
    private static final long MINING_PROGRESS_TIMEOUT_MS = 4_800L;
    private static final WorldPoint AUBURY_POSITION = new WorldPoint(3253, 3399, 0);
    private static final WorldPoint[] ESSENCE_MINE_PORTAL_LOCATIONS = {
            new WorldPoint(2932, 4854, 0),
            new WorldPoint(2885, 4850, 0),
            new WorldPoint(2889, 4813, 0),
            new WorldPoint(2933, 4815, 0)
    };
    private static final String WALK_KEY_AUBURY = "Rune Essence:aubury";
    private static final String WALK_KEY_BANK = "Rune Essence:bank";
    private static final String WALK_KEY_PORTAL = "Rune Essence:portal";

    private boolean debugLogging;
    private long lastActionAtMs;
    private long pendingWorldActionAtMs;
    private String pendingWorldAction;
    private boolean essenceMiningPending;
    private int lastEssenceCount;
    private long lastMiningProgressAtMs;
    private long pendingBankActionAtMs;
    private int pendingBankEmptySlots = -1;
    private EssenceState state = EssenceState.PREPARING;
    private String status = "Idle";

    public boolean run()
    {
        shutdown();
        lastActionAtMs = 0L;
        pendingWorldActionAtMs = 0L;
        pendingWorldAction = null;
        pendingBankActionAtMs = 0L;
        pendingBankEmptySlots = -1;
        state = EssenceState.PREPARING;
        status = "Starting rune essence mining";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                runLoop();
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void runLoop()
    {
        if (!super.run() || !Microbot.isLoggedIn())
        {
            return;
        }

        if (Rs2Player.getQuestState(Quest.RUNE_MYSTERIES) != QuestState.FINISHED)
        {
            status = "Rune Mysteries must be completed";
            return;
        }

        KspTaskDebug.throttled(log, debugLogging, "Rune Essence", "loop", 5_000L,
                "loop | state={} status={} player={} region={} inventoryFull={} animating={}",
                state,
                status,
                Rs2Player.getWorldLocation(),
                getCurrentRegion(),
                Rs2Inventory.isFull(),
                Rs2Player.isAnimating());

        if (isInEssenceMine())
        {
            if (Rs2Inventory.isFull())
            {
                exitMine();
            }
            else
            {
                mineEssence();
            }
            return;
        }

        clearEssenceMiningAction();
        if (Rs2Inventory.isFull() || !hasPickaxe())
        {
            bankInventory();
            return;
        }

        equipBestInventoryPickaxe();
        walkToAndTeleportWithAubury();
    }

    private void bankInventory()
    {
        state = EssenceState.BANKING;
        status = "Banking rune essence";
        BankLocation bank = BankLocation.VARROCK_EAST;
        WorldPoint bankPoint = bank.getWorldPoint();

        if (!Rs2Bank.isNearBank(bank, 8))
        {
            KspWalkerGuard.walkToDestination(
                    WALK_KEY_BANK,
                    bank::getWorldPoint,
                    point -> point != null && point.distanceTo(bankPoint) <= 8,
                    6,
                    WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clear(WALK_KEY_BANK);
        if (!Rs2Bank.isOpen())
        {
            if (isWorldActionPending("open-bank")) return;
            if (Rs2Bank.openBank()) markWorldAction("open-bank");
            return;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen())
        {
            return;
        }

        String pickaxeToKeep = resolveBestOwnedPickaxeName();
        if (pickaxeToKeep == null)
        {
            status = "No pickaxe available";
            return;
        }

        if (!Rs2Inventory.hasItem(pickaxeToKeep) && !Rs2Equipment.isWearing(pickaxeToKeep))
        {
            if (!isBankActionPending()) {
                int before = Rs2Inventory.emptySlotCount();
                if (Rs2Bank.withdrawOne(pickaxeToKeep)) markBankAction(before);
            }
            return;
        }

        if (isBankActionPending()) return;

        int before = Rs2Inventory.emptySlotCount();
        boolean deposited;
        if (Rs2Inventory.hasItem(pickaxeToKeep))
        {
            deposited = Rs2Bank.depositAllExcept(pickaxeToKeep);
        }
        else
        {
            deposited = Rs2Bank.depositAll();
        }
        if (deposited) markBankAction(before);

        if (!Rs2Inventory.isFull())
        {
            Rs2Bank.closeBank();
        }
    }

    private void walkToAndTeleportWithAubury()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        Rs2NpcModel aubury = Microbot.getRs2NpcCache().query()
                .fromWorldView().withName("Aubury").nearestOnClientThread();

        if (player == null || aubury == null || aubury.getWorldLocation() == null
                || player.distanceTo(aubury.getWorldLocation()) > NPC_REACH_DISTANCE)
        {
            state = EssenceState.WALKING_TO_AUBURY;
            status = "Walking to Aubury";
            KspWalkerGuard.walkToPoint(
                    WALK_KEY_AUBURY, AUBURY_POSITION, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        state = EssenceState.TELEPORTING;
        status = "Teleporting to rune essence mine";
        KspWalkerGuard.clear(WALK_KEY_AUBURY);
        if (isWorldActionPending("aubury") || !canAct()) return;

        if (aubury.click("Teleport")) {
            lastActionAtMs = System.currentTimeMillis();
            markWorldAction("aubury");
        }
    }

    private void mineEssence()
    {
        state = EssenceState.MINING;
        status = "Mining rune essence";
        if (isEssenceMiningActionPending() || Rs2Player.isMoving()
                || Rs2Player.isAnimating() || Rs2Player.isInteracting() || !canAct())
        {
            return;
        }

        var essenceRock = Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withName("Rune Essence")
                .nearestOnClientThread();
        if (essenceRock != null && essenceRock.click("Mine"))
        {
            lastActionAtMs = System.currentTimeMillis();
            essenceMiningPending = true;
            lastEssenceCount = getEssenceCount();
            lastMiningProgressAtMs = lastActionAtMs;
        }
    }

    private boolean isEssenceMiningActionPending()
    {
        if (!essenceMiningPending) return false;

        long now = System.currentTimeMillis();
        int count = getEssenceCount();
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()
                || Rs2Player.isInteracting() || count > lastEssenceCount)
        {
            lastEssenceCount = count;
            lastMiningProgressAtMs = now;
            return true;
        }

        // Retain the click through dispatch and mining animation gaps.
        // Retry only after movement, animation and inventory progress have all stopped.
        if (now - lastMiningProgressAtMs < MINING_PROGRESS_TIMEOUT_MS) return true;
        clearEssenceMiningAction();
        return false;
    }

    private int getEssenceCount()
    {
        return Rs2Inventory.count(1436) + Rs2Inventory.count(7936);
    }

    private void clearEssenceMiningAction()
    {
        essenceMiningPending = false;
        lastEssenceCount = 0;
        lastMiningProgressAtMs = 0L;
    }

    private void exitMine()
    {
        clearEssenceMiningAction();
        state = EssenceState.EXITING_MINE;
        status = "Leaving rune essence mine";
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting() || isWorldActionPending("portal") || !canAct()) return;

        var portal = Microbot.getRs2TileObjectCache().query()
                .withId(ESSENCE_MINE_PORTAL_ID)
                .nearestOnClientThread();
        if (portal == null) {
            portal = Microbot.getRs2TileObjectCache().query()
                    .withName("Portal")
                    .nearestOnClientThread();
        }

        if (portal == null)
        {
            status = "Walking to rune essence portal";
            KspWalkerGuard.walkFastCanvasToPoint(
                    WALK_KEY_PORTAL,
                    getNearestPortalLocation(),
                    PORTAL_APPROACH_DISTANCE,
                    WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clear(WALK_KEY_PORTAL);
        if (portal.click()) {
            lastActionAtMs = System.currentTimeMillis();
            markWorldAction("portal");
        }
    }

    public boolean recoverFromEssenceMine()
    {
        if (!isInEssenceMine())
        {
            KspWalkerGuard.clear(WALK_KEY_PORTAL);
            return false;
        }

        exitMine();
        return true;
    }

    private WorldPoint getNearestPortalLocation()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint nearest = ESSENCE_MINE_PORTAL_LOCATIONS[0];

        if (playerLocation == null)
        {
            return nearest;
        }

        for (WorldPoint portalLocation : ESSENCE_MINE_PORTAL_LOCATIONS)
        {
            if (playerLocation.distanceTo(portalLocation) < playerLocation.distanceTo(nearest))
            {
                nearest = portalLocation;
            }
        }

        return nearest;
    }

    private void equipBestInventoryPickaxe()
    {
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        Equip best = Equip.bestForLevels(miningLevel, attackLevel);

        for (int index = best.ordinal(); index >= 0; index--)
        {
            String pickaxeName = Equip.values()[index].getDisplayName();
            if (Rs2Equipment.isWearing(pickaxeName))
            {
                return;
            }
            if (Rs2Inventory.hasItem(pickaxeName))
            {
                Rs2Inventory.wield(pickaxeName);
                return;
            }
        }
    }

    private String resolveBestOwnedPickaxeName()
    {
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        Equip bestMiningPickaxe = Arrays.stream(Equip.values())
                .filter(pickaxe -> miningLevel >= pickaxe.getRequiredMiningLevel())
                .reduce((first, second) -> second)
                .orElse(Equip.BRONZE);

        for (int index = bestMiningPickaxe.ordinal(); index >= 0; index--)
        {
            String pickaxeName = Equip.values()[index].getDisplayName();
            if (Rs2Equipment.isWearing(pickaxeName)
                    || Rs2Inventory.hasItem(pickaxeName)
                    || (Rs2Bank.isOpen() && Rs2Bank.count(pickaxeName) > 0))
            {
                return pickaxeName;
            }
        }
        return null;
    }

    private boolean hasPickaxe()
    {
        for (Equip pickaxe : Equip.values())
        {
            if (Rs2Equipment.isWearing(pickaxe.getDisplayName())
                    || Rs2Inventory.hasItem(pickaxe.getDisplayName()))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isWorldActionPending(String action)
    {
        if (pendingWorldActionAtMs == 0L || pendingWorldAction == null) return false;
        if (!pendingWorldAction.equals(action)) return false;
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) return true;
        if (System.currentTimeMillis() - pendingWorldActionAtMs < ACTION_DISPATCH_TIMEOUT_MS) return true;
        pendingWorldActionAtMs = 0L;
        pendingWorldAction = null;
        return false;
    }

    private void markWorldAction(String action)
    {
        pendingWorldAction = action;
        pendingWorldActionAtMs = System.currentTimeMillis();
    }

    private boolean isBankActionPending()
    {
        if (pendingBankActionAtMs == 0L) return false;
        if (Rs2Inventory.emptySlotCount() != pendingBankEmptySlots) {
            pendingBankActionAtMs = 0L;
            pendingBankEmptySlots = -1;
            return false;
        }
        if (System.currentTimeMillis() - pendingBankActionAtMs < ACTION_DISPATCH_TIMEOUT_MS) return true;
        pendingBankActionAtMs = 0L;
        pendingBankEmptySlots = -1;
        return false;
    }

    private void markBankAction(int beforeEmptySlots)
    {
        pendingBankEmptySlots = beforeEmptySlots;
        pendingBankActionAtMs = System.currentTimeMillis();
    }

    private boolean canAct() { return System.currentTimeMillis() - lastActionAtMs >= ACTION_COOLDOWN_MS; }

    private int getCurrentRegion()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location == null ? -1 : location.getRegionID();
    }

    public boolean isInEssenceMine() { return getCurrentRegion() == ESSENCE_MINE_REGION; }

    public boolean isInTaskArea()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return isInEssenceMine()
                || (location != null && (location.distanceTo(AUBURY_POSITION) <= 12
                || location.distanceTo(BankLocation.VARROCK_EAST.getWorldPoint()) <= 12));
    }

    public EssenceState getState() { return state; }

    public String getStatus() { return status; }

    public void setDebugLogging(boolean debugLogging) { this.debugLogging = debugLogging; }

    @Override
    public void shutdown()
    {
        clearEssenceMiningAction();
        KspWalkerGuard.clear(WALK_KEY_AUBURY);
        KspWalkerGuard.clear(WALK_KEY_BANK);
        KspWalkerGuard.clear(WALK_KEY_PORTAL);
        super.shutdown();
    }
}
