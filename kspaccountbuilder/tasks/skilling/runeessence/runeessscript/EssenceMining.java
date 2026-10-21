package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.runeessscript;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspLiveScene;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.inv.Equip;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EssenceMining extends Script
{
    private static final Logger log = LoggerFactory.getLogger(EssenceMining.class);
    private static final int LOOP_DELAY_MS = 100;
    private static final int ESSENCE_MINE_REGION = 11595;
    // Rune Essence portals are randomized: two NPC variants and five object variants.
    private static final int[] PORTAL_NPC_IDS = {3086, 3088};
    private static final int[] PORTAL_OBJECT_IDS = {34774, 34775, 34776, 34777, 34778};
    private static final int NPC_REACH_DISTANCE = 5;
    private static final int PORTAL_APPROACH_DISTANCE = 3;
    private static final long PORTAL_SEARCH_TIMEOUT_MS = 10_000L;
    private static final long PORTAL_EXIT_TIMEOUT_MS = 8_000L;
    private static final long WALK_REFIRE_COOLDOWN_MS = 1_000L;
    private static final long ACTION_COOLDOWN_MS = 100L;
    private static final long ACTION_DISPATCH_TIMEOUT_MS = 1_500L;
    private static final long AUBURY_TELEPORT_TIMEOUT_MS = 8_000L;
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
    private long portalSearchStartedAtMs;
    private int portalFallbackIndex = -1;
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
        portalSearchStartedAtMs = 0L;
        portalFallbackIndex = -1;
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
            clearAuburyTeleportTransition();
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
        resetPortalSearch();
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
        if (isAuburyTeleportPending())
        {
            state = EssenceState.TELEPORTING;
            status = "Waiting for Aubury teleport";
            KspWalkerGuard.clear(WALK_KEY_AUBURY);
            return;
        }

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
        if (!canAct()) return;

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
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()
                || isPortalExitPending() || !canAct()) return;

        WorldPoint player = Rs2Player.getWorldLocation();
        // Resolve the actual world-view NPC first, without relying on the
        // cache or exact names (some portal names include colour tags).
        int liveNpcId = KspLiveScene.nearestNpcId(PORTAL_NPC_IDS, player, 100);
        if (liveNpcId > 0)
        {
            Rs2NpcModel liveNpc = Microbot.getRs2NpcCache().query()
                    .fromWorldView().withId(liveNpcId).nearestOnClientThread();
            if (liveNpc != null)
            {
                if (Rs2Player.isMoving())
                {
                    status = "Approaching live rune essence NPC portal";
                    return;
                }
                try
                {
                    String action = liveNpcId == 3088 ? "Exit" : "Use";
                    if (liveNpc.click(action))
                    {
                        markPortalExit("live NPC", liveNpcId);
                        return;
                    }
                }
                catch (RuntimeException ex)
                {
                    KspTaskDebug.throttled(log, true, "Rune Essence", "portal-npc-live-failure", 5_000L,
                            "Live NPC portal interaction failed | id={} error={}", liveNpcId, ex.toString());
                }
            }
        }

        // Live game objects are second: these IDs are NOT NPC IDs.
        for (int id : PORTAL_OBJECT_IDS)
        {
            TileObject liveObject = KspLiveScene.nearestGameObject(id, player, 100);
            if (liveObject == null) continue;
            if (Rs2Player.isMoving())
            {
                status = "Approaching live rune essence object portal";
                return;
            }
            try
            {
                String action = portalObjectAction(id);
                if (Rs2GameObject.interact(liveObject, action))
                {
                    markPortalExit("live object", id);
                    return;
                }
            }
            catch (RuntimeException ex)
            {
                KspTaskDebug.throttled(log, true, "Rune Essence", "portal-object-live-failure", 5_000L,
                        "Live object portal interaction failed | id={} error={}", id, ex.toString());
            }
        }

        // Cache is a fallback for missing or stale live scene entries.
        for (int id : PORTAL_NPC_IDS)
        {
            Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                    .fromWorldView().withId(id).nearestOnClientThread();
            if (npc == null) continue;
            if (Rs2Player.isMoving()) return;
            try
            {
                if (npc.click(id == 3088 ? "Exit" : "Use"))
                {
                    markPortalExit("cached NPC", id);
                    return;
                }
            }
            catch (RuntimeException ex)
            {
                KspTaskDebug.throttled(log, true, "Rune Essence", "portal-npc-cache-failure", 5_000L,
                        "Cached NPC portal interaction failed | id={} error={}", id, ex.toString());
            }
        }
        for (int id : PORTAL_OBJECT_IDS)
        {
            var portal = Microbot.getRs2TileObjectCache().query()
                    .fromWorldView().withId(id).nearestOnClientThread();
            if (portal == null) continue;
            if (Rs2Player.isMoving()) return;
            try
            {
                if (portal.click(portalObjectAction(id)))
                {
                    markPortalExit("cached object", id);
                    return;
                }
            }
            catch (RuntimeException ex)
            {
                KspTaskDebug.throttled(log, true, "Rune Essence", "portal-object-cache-failure", 5_000L,
                        "Cached object portal interaction failed | id={} error={}", id, ex.toString());
            }
        }

        // The portal can be missing from the tile-object cache immediately
        // after a mine scene rebuild. Previously reaching the expected portal
        // tile caused an infinite "Waiting for rune essence portal" state.
        long now = System.currentTimeMillis();
        if (portalFallbackIndex < 0)
        {
            portalFallbackIndex = nearestPortalIndex(player);
            portalSearchStartedAtMs = now;
        }
        if (now - portalSearchStartedAtMs >= PORTAL_SEARCH_TIMEOUT_MS)
        {
            // Do NOT cycle through other exits on a cache miss. The game can
            // render a portal before RuneLite's tile-object caches populate;
            // cycling makes the walker zigzag forever between four entrances.
            // Hold the original closest portal and keep looking for its object.
            portalSearchStartedAtMs = now;
            KspTaskDebug.throttled(log, true, "Rune Essence", "portal-discovery", 10_000L,
                    "Portal missing from scene/cache; holding nearest exit | player={} target={} index={} distance={} moving={}",
                    player, ESSENCE_MINE_PORTAL_LOCATIONS[portalFallbackIndex], portalFallbackIndex,
                    player == null ? -1 : player.distanceTo(ESSENCE_MINE_PORTAL_LOCATIONS[portalFallbackIndex]),
                    Rs2Player.isMoving());
        }

        WorldPoint portalLocation = ESSENCE_MINE_PORTAL_LOCATIONS[portalFallbackIndex];
        if (player != null && player.distanceTo(portalLocation) <= PORTAL_APPROACH_DISTANCE)
        {
            KspWalkerGuard.clear(WALK_KEY_PORTAL);
            status = "Searching for rune essence portal";
            return;
        }

        status = "Walking to rune essence portal";
        KspWalkerGuard.walkToPoint(
                WALK_KEY_PORTAL,
                portalLocation,
                PORTAL_APPROACH_DISTANCE,
                WALK_REFIRE_COOLDOWN_MS);
    }

    private static String portalObjectAction(int id)
    {
        return id == 34775 || id == 34777 ? "Exit" : "Use";
    }

    private void markPortalExit(String source, int id)
    {
        resetPortalSearch();
        KspWalkerGuard.clear(WALK_KEY_PORTAL);
        lastActionAtMs = System.currentTimeMillis();
        markWorldAction("portal");
        status = "Exiting rune essence mine via " + source;
        KspTaskDebug.throttled(log, true, "Rune Essence", "portal-dispatch", 1_000L,
                "Portal action dispatched | source={} id={} status={}", source, id, status);
    }

    private boolean isPortalExitPending()
    {
        if (!"portal".equals(pendingWorldAction) || pendingWorldActionAtMs <= 0L)
            return false;
        // runLoop() handles successful mine departure before exitMine().
        // Give the teleport time to load even when the walking/interaction
        // flags become false between animations and the region change.
        long elapsed = System.currentTimeMillis() - pendingWorldActionAtMs;
        if (elapsed < PORTAL_EXIT_TIMEOUT_MS)
        {
            status = "Waiting for rune essence portal teleport";
            return true;
        }
        KspTaskDebug.throttled(log, true, "Rune Essence", "portal-exit-timeout", 5_000L,
                "Portal exit was not confirmed; retrying nearby portal | elapsedMs={} player={} region={}",
                elapsed, Rs2Player.getWorldLocation(), getCurrentRegion());
        pendingWorldAction = null;
        pendingWorldActionAtMs = 0L;
        return false;
    }

    private int nearestPortalIndex(WorldPoint player)
    {
        int best = 0;
        if (player == null) return best;
        for (int i = 1; i < ESSENCE_MINE_PORTAL_LOCATIONS.length; i++)
        {
            if (player.distanceTo(ESSENCE_MINE_PORTAL_LOCATIONS[i])
                    < player.distanceTo(ESSENCE_MINE_PORTAL_LOCATIONS[best]))
            {
                best = i;
            }
        }
        return best;
    }

    private void resetPortalSearch()
    {
        portalSearchStartedAtMs = 0L;
        portalFallbackIndex = -1;
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

        // Select the mining tool independently of Attack. A stronger pickaxe
        // carried in inventory is usable even if it cannot be equipped.
        Equip bestAvailable = null;
        for (Equip pickaxe : Equip.values())
        {
            if (miningLevel < pickaxe.getRequiredMiningLevel()) continue;
            if (Rs2Equipment.isWearing(pickaxe.getDisplayName())
                    || Rs2Inventory.hasItem(pickaxe.getDisplayName()))
            {
                bestAvailable = pickaxe;
            }
        }
        if (bestAvailable == null) return;

        // Do not replace or downgrade a usable higher-tier inventory pickaxe
        // just to equip a weaker one.
        if (attackLevel < bestAvailable.getRequiredAttackLevel()) return;
        if (Rs2Equipment.isWearing(bestAvailable.getDisplayName())) return;
        if (Rs2Inventory.hasItem(bestAvailable.getDisplayName()))
        {
            Rs2Inventory.wield(bestAvailable.getDisplayName());
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

    private boolean isAuburyTeleportPending()
    {
        if (!"aubury".equals(pendingWorldAction) || pendingWorldActionAtMs == 0L)
        {
            return false;
        }

        if (isInEssenceMine())
        {
            clearAuburyTeleportTransition();
            return false;
        }

        long elapsed = System.currentTimeMillis() - pendingWorldActionAtMs;
        if (elapsed < AUBURY_TELEPORT_TIMEOUT_MS)
        {
            return true;
        }

        KspTaskDebug.throttled(log, true, "Rune Essence", "aubury-teleport-timeout", 5_000L,
                "Aubury teleport did not complete before timeout; allowing one retry | elapsedMs={} player={} region={}",
                elapsed, Rs2Player.getWorldLocation(), getCurrentRegion());
        pendingWorldActionAtMs = 0L;
        pendingWorldAction = null;
        return false;
    }

    private void clearAuburyTeleportTransition()
    {
        KspWalkerGuard.clear(WALK_KEY_AUBURY);
        if ("aubury".equals(pendingWorldAction))
        {
            pendingWorldActionAtMs = 0L;
            pendingWorldAction = null;
        }
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
        resetPortalSearch();
        clearAuburyTeleportTransition();
        KspWalkerGuard.clear(WALK_KEY_BANK);
        KspWalkerGuard.clear(WALK_KEY_PORTAL);
        super.shutdown();
    }
}
