package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.runeessscript;

import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderPauseGate;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspManagedScript;

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

import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspLiveScene;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.inv.Equip;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EssenceMining extends KspManagedScript
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
    private static final long PORTAL_SCENE_WALK_COOLDOWN_MS = 1_600L;
    private static final long PORTAL_SCENE_SCAN_INTERVAL_MS = 700L;
    private static final int PORTAL_LOCAL_APPROACH_TILES = 12;
    private static final int PORTAL_SCENE_DISCOVERY_RADIUS = 104;
    private static final long PORTAL_EXIT_TIMEOUT_MS = 8_000L;
    private static final long WALK_REFIRE_COOLDOWN_MS = 1_000L;
    private static final long ACTION_COOLDOWN_MS = 100L;
    private static final long ACTION_DISPATCH_TIMEOUT_MS = 1_500L;
    private static final long AUBURY_TELEPORT_TIMEOUT_MS = 8_000L;
    private static final long MINING_PROGRESS_TIMEOUT_MS = 4_800L;
    private static final WorldPoint AUBURY_POSITION = new WorldPoint(3253, 3399, 0);
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
    private boolean bankCloseRequested;
    private long portalSearchStartedAtMs;
    private long lastPortalSceneWalkAtMs;
    private long lastPortalSceneScanAtMs;
    private boolean lastObservedInsideMine;
    private volatile EssenceState state = EssenceState.PREPARING;
    private volatile String status = "Idle";

    public boolean run()
    {
        shutdown();
        lastActionAtMs = 0L;
        pendingWorldActionAtMs = 0L;
        pendingWorldAction = null;
        pendingBankActionAtMs = 0L;
        pendingBankEmptySlots = -1;
        bankCloseRequested = false;
        state = EssenceState.PREPARING;
        portalSearchStartedAtMs = 0L;
        lastPortalSceneWalkAtMs = 0L;
        lastPortalSceneScanAtMs = 0L;
        lastObservedInsideMine = false;
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
        if (KspAccountBuilderPauseGate.isPaused() || !super.run() || !Microbot.isLoggedIn())
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
            lastObservedInsideMine = true;
            // The Essence mine is instanced. A route started against template
            // WorldPoints can silently select a surface teleport. Never allow
            // WebWalker to own a route while we're inside the mine.
            cancelInstanceWalkerRoute("ksp_rune_essence_inside_instance");
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

        if (lastObservedInsideMine)
        {
            // Leaving through a portal can change region mid-walk. Cancel any
            // stale instance destination before the banking path is started.
            cancelInstanceWalkerRoute("ksp_rune_essence_left_instance");
            Rs2Walker.clearWalkingRoute(WALK_KEY_PORTAL);
            lastObservedInsideMine = false;
        }
        clearEssenceMiningAction();
        resetPortalSearch();
        if (EssenceBankCompletionPolicy.shouldContinueBanking(state, Rs2Inventory.isFull(), hasPickaxe()))
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
            Rs2Walker.walkUntil(bank.getWorldPoint(), 6, () -> { WorldPoint point = Rs2Player.getWorldLocation(); return point != null && point.distanceTo(bankPoint) <= 8; });
            return;
        }

        Rs2Walker.clearWalkingRoute(WALK_KEY_BANK);
        if (bankCloseRequested)
        {
            if (Rs2Bank.isOpen())
            {
                if (isWorldActionPending("close-bank")) return;
                Rs2Bank.closeBank();
                markWorldAction("close-bank");
                return;
            }
            bankCloseRequested = false;
            state = EssenceState.PREPARING;
            status = "Rune essence banking complete";
            return;
        }
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
        boolean keepPickaxe = Rs2Inventory.hasItem(pickaxeToKeep);
        // Finish depositing before closing; one free slot is insufficient.
        if (EssenceBankCompletionPolicy.readyToClose(before, keepPickaxe))
        {
            bankCloseRequested = true;
            return;
        }

        boolean deposited = keepPickaxe
                ? Rs2Bank.depositAllExcept(pickaxeToKeep)
                : Rs2Bank.depositAll();
        if (deposited) markBankAction(before);
    }

    private void walkToAndTeleportWithAubury()
    {
        if (isAuburyTeleportPending())
        {
            state = EssenceState.TELEPORTING;
            status = "Waiting for Aubury teleport";
            Rs2Walker.clearWalkingRoute(WALK_KEY_AUBURY);
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        Rs2NpcModel aubury = KspLiveScene.nearestNpcModel(player, 35,
                npc -> "Aubury".equalsIgnoreCase(npc.getName()));

        if (player == null || aubury == null || aubury.getWorldLocation() == null
                || player.distanceTo(aubury.getWorldLocation()) > NPC_REACH_DISTANCE)
        {
            state = EssenceState.WALKING_TO_AUBURY;
            status = "Walking to Aubury";
            Rs2Walker.walkTo(AUBURY_POSITION, NPC_REACH_DISTANCE);
            return;
        }

        state = EssenceState.TELEPORTING;
        status = "Teleporting to rune essence mine";
        Rs2Walker.clearWalkingRoute(WALK_KEY_AUBURY);
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

        var essenceRock = KspLiveScene.nearestModel(Rs2Player.getWorldLocation(), 30,
                obj -> "Rune Essence".equalsIgnoreCase(obj.getName()));
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

    /**
     * All mine navigation is tied to live local scene coordinates. Fixed
     * world-point portal locations and WebWalker are unsafe in this instance:
     * the player's scene may be mapped to a different template WorldPoint,
     * and an apparently unreachable goal can trigger a surface teleport.
     */
    private void exitMine()
    {
        clearEssenceMiningAction();
        state = EssenceState.EXITING_MINE;
        cancelInstanceWalkerRoute("ksp_rune_essence_exit_scene_only");
        if (isPortalExitPending())
        {
            return;
        }
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting() || !canAct()) return;

        // Full-scene searches are expensive; never repeat both the object-ID
        // and name-based portal scans on every 100ms script tick.
        long scanAt = System.currentTimeMillis();
        if (lastPortalSceneScanAtMs > 0L
                && scanAt - lastPortalSceneScanAtMs < PORTAL_SCENE_SCAN_INTERVAL_MS)
            return;
        lastPortalSceneScanAtMs = scanAt;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;

        // NPC portals: match known IDs and recognizable live-name variants.
        // Their WorldPoints need not agree with the player's instanced map;
        // KspLiveScene compares LocalPoints in the same WorldView instead.
        Rs2NpcModel liveNpc = KspLiveScene.nearestPortalNpc(
                PORTAL_NPC_IDS, PORTAL_SCENE_DISCOVERY_RADIUS);
        if (liveNpc != null)
        {
            int distance = KspLiveScene.distanceFromPlayerInScene(liveNpc.getLocalLocation());
            if (distance >= 0)
            {
                if (distance > PORTAL_LOCAL_APPROACH_TILES)
                {
                    approachLivePortal(liveNpc.getLocalLocation(), "NPC", liveNpc.getId(), distance);
                    return;
                }
                if (Rs2Player.isMoving())
                {
                    status = "Approaching live Rune Essence NPC portal";
                    return;
                }
                try
                {
                    String action = liveNpc.getId() == 3088 ? "Exit" : "Use";
                    if (KspLiveScene.clickInstancePortal(liveNpc, action)
                            || KspLiveScene.clickInstancePortal(liveNpc,
                                    "Exit".equals(action) ? "Use" : "Exit"))
                    {
                        markPortalExit("live NPC", liveNpc.getId());
                        return;
                    }
                }
                catch (RuntimeException ex)
                {
                    KspTaskDebug.throttled(log, true, "Rune Essence", "portal-npc-live-failure", 5_000L,
                            "Local NPC portal interaction failed | id={} local={} error={}",
                            liveNpc.getId(), liveNpc.getLocalLocation(), ex.toString());
                }
            }
        }

        // Object portals: scan ALL live scene layers and resolve transformed
        // object IDs. In addition to the known variants, accept an object named
        // "Portal" with a real Exit/Use action (for newly morphed instances).
        TileObject liveObject = KspLiveScene.nearestTileObject(
                PORTAL_OBJECT_IDS, player, PORTAL_SCENE_DISCOVERY_RADIUS);
        boolean nameFallback = false;
        if (liveObject == null)
        {
            liveObject = KspLiveScene.nearestNamedPortal(player, PORTAL_SCENE_DISCOVERY_RADIUS);
            nameFallback = true;
        }
        if (liveObject != null)
        {
            int distance = KspLiveScene.distanceFromPlayerInScene(liveObject.getLocalLocation());
            if (distance >= 0)
            {
                int id = KspLiveScene.resolvedObjectId(liveObject);
                KspTaskDebug.throttled(log, debugLogging, "Rune Essence",
                        "portal-live-object-discovery", 5_000L,
                        "Live portal | sceneId={} activeId={} local={} distance={} nameFallback={}",
                        liveObject.getId(), id, liveObject.getLocalLocation(), distance, nameFallback);
                if (distance > PORTAL_LOCAL_APPROACH_TILES)
                {
                    approachLivePortal(liveObject.getLocalLocation(), "object", id, distance);
                    return;
                }
                if (Rs2Player.isMoving())
                {
                    status = "Approaching live Rune Essence portal";
                    return;
                }
                try
                {
                    net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel model =
                            new net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel(liveObject);
                    String preferred = portalObjectAction(id);
                    String alternate = "Exit".equals(preferred) ? "Use" : "Exit";
                    if ((model.hasAction(preferred) && KspLiveScene.clickInstancePortal(liveObject, preferred))
                            || (model.hasAction(alternate) && KspLiveScene.clickInstancePortal(liveObject, alternate)))
                    {
                        markPortalExit("live object", id);
                        return;
                    }
                    KspTaskDebug.throttled(log, true, "Rune Essence",
                            "portal-live-object-action-missing", 5_000L,
                            "Visible portal has no dispatched Exit/Use | id={} local={}",
                            id, liveObject.getLocalLocation());
                }
                catch (RuntimeException ex)
                {
                    KspTaskDebug.throttled(log, true, "Rune Essence", "portal-object-live-failure", 5_000L,
                            "Local object portal interaction failed | id={} local={} error={}",
                            id, liveObject.getLocalLocation(), ex.toString());
                }
            }
        }

        // Never use a cached entity from an old scene, or walk to fixed mine
        // WorldPoints. Retrying live discovery is safer than allowing the
        // pathfinder to leave the mine by an unrelated teleport.
        status = "Searching live Rune Essence portal";
        long now = System.currentTimeMillis();
        if (portalSearchStartedAtMs == 0L) portalSearchStartedAtMs = now;
        if (now - portalSearchStartedAtMs >= PORTAL_SEARCH_TIMEOUT_MS)
        {
            KspTaskDebug.throttled(log, true, "Rune Essence", "portal-instance-diagnostic", 10_000L,
                    "No actionable portal in current instance; holding position | player={} region={} "
                            + "npcFound={} objectFound={} moving={} walkerTarget={}",
                    player, getCurrentRegion(), liveNpc != null, liveObject != null,
                    Rs2Player.isMoving(), Rs2Walker.getCurrentTarget());
            portalSearchStartedAtMs = now;
        }
    }

    private void approachLivePortal(net.runelite.api.coords.LocalPoint local, String type,
                                    int id, int distance)
    {
        status = "Approaching live Rune Essence " + type + " portal";
        if (Rs2Player.isMoving()) return;
        long now = System.currentTimeMillis();
        if (now - lastPortalSceneWalkAtMs < PORTAL_SCENE_WALK_COOLDOWN_MS) return;
        lastPortalSceneWalkAtMs = now;
        boolean clicked = KspLiveScene.stepTowardSceneLocal(local);
        KspTaskDebug.throttled(log, debugLogging, "Rune Essence", "portal-scene-step", 3_000L,
                "Portal scene-only step | kind={} id={} local={} distance={} clicked={}",
                type, id, local, distance, clicked);
    }

    private void cancelInstanceWalkerRoute(String reason)
    {
        Rs2Walker.clearWalkingRoute(WALK_KEY_PORTAL);
        // The old fallback route can survive an instance transition despite
        // its Account Builder key being cleared, especially when WebWalker is
        // calculating/continuing a previous segment.
        if (Rs2Walker.getCurrentTarget() != null)
            Rs2Walker.clearWalkingRoute(reason);
    }

    private static String portalObjectAction(int id)
    {
        return id == 34775 || id == 34777 ? "Exit" : "Use";
    }

    private void markPortalExit(String source, int id)
    {
        resetPortalSearch();
        Rs2Walker.clearWalkingRoute(WALK_KEY_PORTAL);
        lastActionAtMs = System.currentTimeMillis();
        markWorldAction("portal");
        status = "Exiting rune essence mine via " + source + " (ID " + id + ")";
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

    private void resetPortalSearch()
    {
        portalSearchStartedAtMs = 0L;
        lastPortalSceneWalkAtMs = 0L;
        lastPortalSceneScanAtMs = 0L;
    }

    public boolean recoverFromEssenceMine()
    {
        if (!isInEssenceMine())
        {
            cancelInstanceWalkerRoute("ksp_rune_essence_recovery_left_instance");
            return false;
        }

        exitMine();
        return true;
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
        Rs2Walker.clearWalkingRoute(WALK_KEY_AUBURY);
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
        bankCloseRequested = false;
        pendingBankActionAtMs = 0L;
        pendingBankEmptySlots = -1;
        clearEssenceMiningAction();
        resetPortalSearch();
        clearAuburyTeleportTransition();
        Rs2Walker.clearWalkingRoute(WALK_KEY_BANK);
        Rs2Walker.clearWalkingRoute(WALK_KEY_PORTAL);
        lastObservedInsideMine = false;
        super.shutdown();
    }
}
