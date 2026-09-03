package net.runelite.client.plugins.microbot.kspwillowchopper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic Chopper runtime.
 *
 * Chopping is object driven. After a tree is clicked, that exact object remains
 * locked as the active target until its object id changes/despawns/replaces.
 * Inventory gains and animation changes never select another tree while the
 * active object is unchanged. If the interaction itself stops unexpectedly, the
 * same locked tree may be re-clicked; a different tree is never selected first.
 *
 * Firemaking is also object driven. The exact campfire being used is tracked by
 * id/hash/tile. If that object disappears or changes id while a burn batch still
 * has logs, a replacement campfire is started immediately instead of waiting for
 * the normal fire/burn grace timers.
 */
@Slf4j
@Singleton
public class KspWillowChopperScript extends Script {
    private static final int TINDERBOX_ID = ItemID.TINDERBOX;
    private static final int BURN_INTERFACE_WIDGET = 17694735;
    private static final int FIRE_ID = ObjectID.FIRE;
    private static final int REGULAR_FIRE_ID = 26185;
    private static final int REGULAR_FIRE_BLUE_ID = 26576;
    private static final int REGULAR_FIRE_GREEN_ID = 26575;
    private static final int REGULAR_FIRE_PURPLE_ID = 20001;
    private static final int REGULAR_FIRE_RED_ID = 26186;
    private static final int REGULAR_FIRE_WHITE_ID = 20000;
    private static final int FORESTER_CAMPFIRE_MIN_ID = 49927;
    private static final int FORESTER_CAMPFIRE_MAX_ID = 49932;
    // Chopper banking is intentionally restricted to this exact booth.
    // Never fall back to bankers, alternate booth IDs, deposit boxes or WebWalker.
    private static final int BANK_BOOTH_ID = 10583;
    private static final String BANK_BOOTH_NAME = "Bank Booth";

    private static final long LOOP_MS = 300L;
    private static final long TREE_RETRY_MS = 6_000L;
    private static final long BANK_RETRY_MS = 4_000L;
    private static final long TINDERBOX_RETRY_MS = 4_000L;
    private static final long FIRE_RETRY_MS = 3_000L;
    private static final int BURN_START_GRACE_MS = 3_000;
    private static final int BURN_PROGRESS_GRACE_MS = 4_500;
    private static final int FIRE_OBJECT_APPEAR_TIMEOUT_MS = 3_000;
    private static final long FIRE_MORPH_SETTLE_MS = 175L;

    public enum RuntimeState {
        IDLE,
        CHOPPING,
        BANKING,
        GETTING_TINDERBOX,
        CREATING_FIRE,
        BURNING,
        FORESTRY,
        PAUSED,
        ERROR
    }

    private final KspWillowChopperPlugin plugin;

    @Inject
    private EventBus eventBus;

    private volatile RuntimeState state = RuntimeState.IDLE;
    private volatile String status = "Idle";
    private volatile boolean sessionStarted;
    private volatile KspTree activeTree = KspTree.WILLOW;
    private volatile boolean campfireNearby;
    private volatile boolean burningActive;
    private volatile boolean burnBatchActive;
    private volatile boolean burnCommandIssued;
    private volatile boolean fireBurnedOutSignal;
    private volatile KspWillowChopperConfig runtimeConfig;

    private volatile int activeTreeObjectId = -1;
    private volatile long activeTreeObjectHash = Long.MIN_VALUE;
    private volatile WorldPoint activeTreeObjectLocation;
    private volatile WorldPoint lastChangedTreeLocation;
    private volatile long activeTreeMissingSinceMillis;
    private final AtomicBoolean immediateRetargetQueued = new AtomicBoolean(false);

    private volatile int activeCampfireObjectId = -1;
    private volatile long activeCampfireObjectHash = Long.MIN_VALUE;
    private volatile WorldPoint activeCampfireObjectLocation;
    private final AtomicBoolean immediateCampfireQueued = new AtomicBoolean(false);

    private volatile boolean eventBusRegistered;

    private long startTimeMillis;
    private int startWoodcuttingXp;
    private int startFiremakingXp;
    private int startWoodcuttingLevel;
    private int startFiremakingLevel;

    private int resourcesChopped;
    private int resourcesBanked;
    private int resourcesBurned;
    private int campfiresLit;
    private int lastResourceCount = -1;
    private int burnCommandResourceCount = -1;
    private volatile boolean promotingFireToCampfire;

    private long lastTreeClickMillis;
    private long lastTreeProgressMillis;
    private long lastBankAttemptMillis;
    private long lastTinderboxAttemptMillis;
    private long lastFireAttemptMillis;
    private long burnCommandMillis;
    private long lastBurnProgressMillis;

    @Inject
    public KspWillowChopperScript(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean run(KspWillowChopperConfig config) {
        shutdown();

        runtimeConfig = config;
        activeTree = safeTree(config.tree());
        state = RuntimeState.CHOPPING;
        status = "Starting";
        sessionStarted = true;
        campfireNearby = false;
        burningActive = false;
        burnBatchActive = false;
        burnCommandIssued = false;
        fireBurnedOutSignal = false;
        clearActiveTreeTarget();
        clearActiveCampfireTarget();
        lastChangedTreeLocation = null;
        immediateRetargetQueued.set(false);
        immediateCampfireQueued.set(false);

        if (!eventBusRegistered && eventBus != null) {
            eventBus.register(this);
            eventBusRegistered = true;
        }

        startTimeMillis = System.currentTimeMillis();
        startWoodcuttingXp = skillXp(Skill.WOODCUTTING);
        startFiremakingXp = skillXp(Skill.FIREMAKING);
        startWoodcuttingLevel = skillLevel(Skill.WOODCUTTING);
        startFiremakingLevel = skillLevel(Skill.FIREMAKING);

        resourcesChopped = 0;
        resourcesBanked = 0;
        resourcesBurned = 0;
        campfiresLit = 0;
        lastResourceCount = Rs2Inventory.count(activeTree.getResourceId());
        burnCommandResourceCount = -1;
        promotingFireToCampfire = false;

        lastTreeClickMillis = 0L;
        lastTreeProgressMillis = 0L;
        lastBankAttemptMillis = 0L;
        lastTinderboxAttemptMillis = 0L;
        lastFireAttemptMillis = 0L;
        burnCommandMillis = 0L;
        lastBurnProgressMillis = 0L;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                () -> tick(config), 0L, LOOP_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick(KspWillowChopperConfig config) {
        try {
            if (!sessionStarted) return;

            if (!Microbot.isLoggedIn()) {
                state = RuntimeState.PAUSED;
                status = "Waiting for login";
                return;
            }

            if (Microbot.pauseAllScripts.get()) {
                state = RuntimeState.PAUSED;
                status = "Paused globally";
                return;
            }

            if (Thread.currentThread().isInterrupted()) return;

            KspTree selected = safeTree(config.tree());
            if (selected != activeTree) switchTree(selected);

            trackResourceChanges();

            if (config.enableForestry() && plugin.runForestryIfNeeded()) {
                state = RuntimeState.FORESTRY;
                status = "Forestry complete";
                clearActiveTreeTarget();
                clearActiveCampfireTarget();
                syncResourceBaseline();
                resetActionLatches();
                return;
            }

            if (plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
                state = RuntimeState.FORESTRY;
                status = "Forestry: " + plugin.getCurrentForestryEvent();
                clearActiveTreeTarget();
                clearActiveCampfireTarget();
                return;
            }

            int woodcuttingLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
            if (woodcuttingLevel < activeTree.getWoodcuttingLevel()) {
                state = RuntimeState.PAUSED;
                status = "Need " + activeTree.getWoodcuttingLevel() + " Woodcutting";
                clearActiveTreeTarget();
                return;
            }

            if (!hasAxe()) {
                state = RuntimeState.PAUSED;
                status = "No axe equipped/in inventory";
                clearActiveTreeTarget();
                return;
            }

            if (config.bankLogs()) tickBankMode();
            else tickBurnMode();
        } catch (Exception ex) {
            state = RuntimeState.ERROR;
            status = "Error - check logs";
            Microbot.logStackTrace("KspWillowChopperScript", ex);
        }
    }

    private void tickBankMode() {
        burningActive = false;
        burnBatchActive = false;
        burnCommandIssued = false;
        campfireNearby = false;
        fireBurnedOutSignal = false;
        clearActiveCampfireTarget();

        if (Rs2Inventory.isFull()) {
            clearActiveTreeTarget();
            bankResource();
            return;
        }

        state = RuntimeState.CHOPPING;
        chopSelectedTree(false, null);
    }

    private void tickBurnMode() {
        if (!activeTree.isCampfireBurnable()) {
            state = RuntimeState.PAUSED;
            status = activeTree.getResourceName() + " cannot be burned; enable Bank resources";
            clearActiveTreeTarget();
            return;
        }

        int resourceId = activeTree.getResourceId();
        int resourceCount = Rs2Inventory.count(resourceId);

        if (resourceCount <= 0) {
            finishBurnBatch();
            state = RuntimeState.CHOPPING;
            chopSelectedTree(false, null);
            return;
        }

        if (!burnBatchActive && !Rs2Inventory.isFull()) {
            state = RuntimeState.CHOPPING;
            chopSelectedTree(false, null);
            return;
        }

        clearActiveTreeTarget();
        burnBatchActive = true;

        if (burnCommandIssued) {
            long now = System.currentTimeMillis();
            if (resourceCount <= 0) {
                finishBurnBatch();
                return;
            }

            // Firemaking progress is inventory-driven. Animation/interacting state
            // must never keep this branch alive forever when no logs are consumed.
            if (burnCommandResourceCount < 0) burnCommandResourceCount = resourceCount;
            if (resourceCount < burnCommandResourceCount) {
                burnCommandResourceCount = resourceCount;
                lastBurnProgressMillis = now;
            }

            boolean starting = now - burnCommandMillis < BURN_START_GRACE_MS;
            boolean progressRecent = now - lastBurnProgressMillis < BURN_PROGRESS_GRACE_MS;
            if (!fireBurnedOutSignal && (starting || progressRecent)) {
                state = RuntimeState.BURNING;
                burningActive = true;
                status = "Burning " + activeTree.getResourceName();
                return;
            }

            burnCommandIssued = false;
            burnCommandResourceCount = -1;
            burningActive = false;
            status = "Burn stalled - retrying campfire";
        }

        Rs2TileObjectModel campfire = findCampfire(Rs2Player.getWorldLocation(), 15);
        campfireNearby = campfire != null;
        fireBurnedOutSignal = false;

        if (campfire == null) {
            clearActiveCampfireTarget();
            if (!Rs2Inventory.hasItem(TINDERBOX_ID)) {
                obtainTinderbox();
                return;
            }
            createFire(false);
            return;
        }

        rememberActiveCampfireTarget(campfire);
        startBulkBurn(campfire);
    }

    private void chopSelectedTree(boolean force, WorldPoint avoidLocation) {
        if (!force && immediateRetargetQueued.get()) {
            status = "Tree object changed - selecting next " + activeTree;
            return;
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }

        // The active tree object is the authoritative interaction lock. Never
        // switch while that exact object id/tile is still alive. Poll the locked
        // tile before trusting animation/interacting state: RuneScape can leave a
        // stale chopping animation briefly after the tree has already morphed.
        if (activeTreeObjectLocation != null) {
            long lockedNow = System.currentTimeMillis();
            Rs2TileObjectModel lockedTree = findActiveTreeObject();

            if (lockedTree == null) {
                Rs2TileObjectModel replacement = findChangedObjectAtActiveTreeTile();
                if (replacement != null) {
                    WorldPoint changedLocation = activeTreeObjectLocation;
                    int previousId = activeTreeObjectId;
                    activeTreeMissingSinceMillis = 0L;
                    requestImmediateRetarget(changedLocation,
                            "Object " + previousId + " changed to " + replacement.getId());
                    return;
                }

                // A cache refresh can hide an object for one script pass. Give it
                // one 300ms loop before treating disappearance as an ID change.
                if (activeTreeMissingSinceMillis <= 0L) {
                    activeTreeMissingSinceMillis = lockedNow;
                    status = "Detected " + activeTree + " object update";
                    return;
                }
                if (lockedNow - activeTreeMissingSinceMillis >= LOOP_MS) {
                    WorldPoint changedLocation = activeTreeObjectLocation;
                    int previousId = activeTreeObjectId;
                    activeTreeMissingSinceMillis = 0L;
                    requestImmediateRetarget(changedLocation,
                            "Object " + previousId + " no longer active");
                    return;
                }
                status = "Waiting for active " + activeTree + " object update";
                return;
            }

            activeTreeMissingSinceMillis = 0L;

            if (isPlayerBusy()) {
                status = "Chopping " + activeTree + " - waiting for object ID change";
                return;
            }

            boolean recentLockedClick = lastTreeClickMillis > 0L
                    && lockedNow - lastTreeClickMillis < 1_500L;
            boolean recentLockedProgress = lastTreeProgressMillis > 0L
                    && lockedNow - lastTreeProgressMillis < 1_500L;
            if (recentLockedClick || recentLockedProgress) {
                status = "Chopping " + activeTree + " - waiting for object ID change";
                return;
            }

            status = "Woodcutting stopped - retrying same " + activeTree;
            if (lockedTree.click(activeTree.getAction())) {
                rememberActiveTreeTarget(lockedTree);
                lastTreeClickMillis = lockedNow;
                status = "Chopping " + activeTree + " - waiting for object ID change";
            } else {
                status = "Same " + activeTree + " retry failed - waiting";
            }
            return;
        }

        if (!force && isPlayerBusy()) {
            status = "Chopping " + activeTree;
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && lastTreeClickMillis > 0L) {
            boolean recentProgress = lastTreeProgressMillis > 0L
                    && now - lastTreeProgressMillis < TREE_RETRY_MS;
            boolean recentClick = now - lastTreeClickMillis < TREE_RETRY_MS;
            if (recentProgress || recentClick) {
                status = "Chopping " + activeTree;
                return;
            }
        }

        Rs2TileObjectModel tree = findNextChoppableTree(avoidLocation);
        if (tree == null && avoidLocation != null) {
            tree = findNextChoppableTree(null);
        }

        if (tree == null) {
            status = "No " + activeTree + " loaded";
            lastTreeClickMillis = 0L;
            clearActiveTreeTarget();
            return;
        }

        status = force ? "Selecting next " + activeTree : "Clicking " + activeTree;

        if (tree.click(activeTree.getAction())) {
            rememberActiveTreeTarget(tree);
            lastChangedTreeLocation = null;
            lastTreeClickMillis = now;
            status = "Chopping " + activeTree;
        } else {
            clearActiveTreeTarget();
            status = activeTree + " click failed - retrying";
        }
    }

    private Rs2TileObjectModel findNextChoppableTree(WorldPoint avoidLocation) {
        String objectName = activeTree.getObjectName();
        String action = activeTree.getAction();

        return Microbot.getRs2TileObjectCache()
                .query()
                .withName(objectName)
                .where(object -> object != null
                        && KspTileObjectSupport.hasAction(object, action)
                        && (avoidLocation == null || !avoidLocation.equals(object.getWorldLocation())))
                .nearestOnClientThread();
    }

    private Rs2TileObjectModel findActiveTreeObject() {
        WorldPoint location = activeTreeObjectLocation;
        int objectId = activeTreeObjectId;
        if (location == null || objectId < 0) return null;

        return Microbot.getRs2TileObjectCache()
                .query()
                .where(object -> object != null
                        && object.getId() == objectId
                        && location.equals(object.getWorldLocation())
                        && KspTileObjectSupport.hasAction(object, activeTree.getAction()))
                .nearestOnClientThread();
    }

    /**
     * Polling fallback for tree morphs when the client does not deliver a clean
     * GameObjectDespawned/GameObjectSpawned pair. Only consulted after the exact
     * locked object is already absent, so unrelated objects cannot break a live
     * tree lock.
     */
    private Rs2TileObjectModel findChangedObjectAtActiveTreeTile() {
        WorldPoint location = activeTreeObjectLocation;
        int previousId = activeTreeObjectId;
        if (location == null || previousId < 0) return null;

        return Microbot.getRs2TileObjectCache()
                .query()
                .where(object -> object != null
                        && location.equals(object.getWorldLocation())
                        && object.getId() != previousId)
                .nearestOnClientThread();
    }

    private void rememberActiveTreeTarget(Rs2TileObjectModel tree) {
        if (tree == null) {
            clearActiveTreeTarget();
            return;
        }
        activeTreeObjectId = tree.getId();
        activeTreeObjectHash = tree.getHash();
        activeTreeObjectLocation = tree.getWorldLocation();
        activeTreeMissingSinceMillis = 0L;
    }

    private void clearActiveTreeTarget() {
        activeTreeObjectId = -1;
        activeTreeObjectHash = Long.MIN_VALUE;
        activeTreeObjectLocation = null;
        activeTreeMissingSinceMillis = 0L;
    }

    private void rememberActiveCampfireTarget(Rs2TileObjectModel fire) {
        if (fire == null) {
            clearActiveCampfireTarget();
            return;
        }
        activeCampfireObjectId = fire.getId();
        activeCampfireObjectHash = fire.getHash();
        activeCampfireObjectLocation = fire.getWorldLocation();
        campfireNearby = true;
    }

    private void rememberActiveCampfireTarget(GameObject fire) {
        if (fire == null) {
            clearActiveCampfireTarget();
            return;
        }
        activeCampfireObjectId = fire.getId();
        activeCampfireObjectHash = fire.getHash();
        activeCampfireObjectLocation = fire.getWorldLocation();
        campfireNearby = true;
    }

    private void clearActiveCampfireTarget() {
        activeCampfireObjectId = -1;
        activeCampfireObjectHash = Long.MIN_VALUE;
        activeCampfireObjectLocation = null;
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject object = event.getGameObject();
        if (isActiveCampfireObject(object)) {
            // A regular Fire despawns when it is promoted into a Forester's
            // Campfire. That is expected and must not start a second fire.
            if (promotingFireToCampfire && isRegularFireId(object.getId())) {
                status = "Converting fire to campfire";
                return;
            }
            requestImmediateCampfireRecreate(object.getId());
            return;
        }
        if (isActiveChopObject(object)) {
            requestImmediateRetarget(object.getWorldLocation(), "Object " + object.getId() + " changed");
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject object = event.getGameObject();
        if (object == null || !sessionStarted) return;

        WorldPoint fireLocation = activeCampfireObjectLocation;
        if ((state == RuntimeState.BURNING || state == RuntimeState.CREATING_FIRE)
                && isUsableFireId(object.getId())
                && (fireLocation == null || fireLocation.equals(object.getWorldLocation()))) {
            // Regular Fire -> Forester's Campfire is a normal object-ID morph.
            // Adopt the replacement instead of treating it as a burnout.
            rememberActiveCampfireTarget(object);
            fireBurnedOutSignal = false;
            if (isForesterCampfireId(object.getId())) {
                promotingFireToCampfire = false;
                state = RuntimeState.BURNING;
                status = "Campfire ready - adding logs";
            }
            return;
        }

        if (fireLocation != null
                && fireLocation.equals(object.getWorldLocation())
                && object.getId() != activeCampfireObjectId
                && (state == RuntimeState.BURNING || state == RuntimeState.CREATING_FIRE)) {
            requestImmediateCampfireRecreate(activeCampfireObjectId);
            return;
        }

        WorldPoint treeLocation = activeTreeObjectLocation;
        if (state == RuntimeState.CHOPPING
                && treeLocation != null
                && treeLocation.equals(object.getWorldLocation())
                && object.getId() != activeTreeObjectId) {
            requestImmediateRetarget(treeLocation, "Object " + activeTreeObjectId + " changed");
        }
    }

    private boolean isActiveChopObject(GameObject object) {
        if (!sessionStarted
                || state != RuntimeState.CHOPPING
                || object == null
                || activeTreeObjectLocation == null) {
            return false;
        }
        if (!activeTreeObjectLocation.equals(object.getWorldLocation())) return false;
        return object.getHash() == activeTreeObjectHash || object.getId() == activeTreeObjectId;
    }

    private boolean isActiveCampfireObject(GameObject object) {
        if (!sessionStarted
                || object == null
                || activeCampfireObjectLocation == null
                || (state != RuntimeState.BURNING && state != RuntimeState.CREATING_FIRE)) {
            return false;
        }
        if (!activeCampfireObjectLocation.equals(object.getWorldLocation())) return false;
        return object.getHash() == activeCampfireObjectHash || object.getId() == activeCampfireObjectId;
    }

    private void requestImmediateRetarget(WorldPoint avoidLocation, String reason) {
        if (!canImmediateRetarget()) return;

        lastChangedTreeLocation = avoidLocation;
        clearActiveTreeTarget();
        lastTreeClickMillis = 0L;
        lastTreeProgressMillis = 0L;
        state = RuntimeState.CHOPPING;
        status = reason + " - selecting next " + activeTree;

        if (!immediateRetargetQueued.compareAndSet(false, true)) return;

        scheduledExecutorService.execute(() -> {
            try {
                if (!canImmediateRetarget()) return;

                KspWillowChopperConfig config = runtimeConfig;
                if (config != null) {
                    KspTree selected = safeTree(config.tree());
                    if (selected != activeTree) switchTree(selected);
                }

                chopSelectedTree(true, lastChangedTreeLocation);
            } catch (Exception ex) {
                Microbot.logStackTrace("KSP Chopper immediate retarget", ex);
            } finally {
                immediateRetargetQueued.set(false);
            }
        });
    }

    private boolean canImmediateRetarget() {
        if (!sessionStarted
                || !Microbot.isLoggedIn()
                || Microbot.pauseAllScripts.get()
                || state != RuntimeState.CHOPPING
                || plugin.getCurrentForestryEvent() != KspForestryEvent.NONE
                || Rs2Inventory.isFull()) {
            return false;
        }

        KspWillowChopperConfig config = runtimeConfig;
        if (config == null) return false;
        if (!config.bankLogs() && activeTree.isCampfireBurnable() && burnBatchActive) return false;

        return hasAxe()
                && Rs2Player.getRealSkillLevel(Skill.WOODCUTTING) >= activeTree.getWoodcuttingLevel();
    }

    private void requestImmediateCampfireRecreate(int previousObjectId) {
        if (!canImmediateCampfireRecreate()) return;

        campfireNearby = false;
        fireBurnedOutSignal = true;
        burnCommandIssued = false;
        burnCommandResourceCount = -1;
        burningActive = false;
        lastFireAttemptMillis = 0L;
        burnCommandMillis = 0L;
        lastBurnProgressMillis = 0L;
        state = RuntimeState.CREATING_FIRE;
        status = "Campfire " + previousObjectId + " disappeared - checking replacement";

        if (!immediateCampfireQueued.compareAndSet(false, true)) return;

        // Give same-tick regular-fire -> campfire object morphs a fraction of a
        // tick to arrive. This is still effectively immediate for a real burnout.
        scheduledExecutorService.schedule(() -> {
            try {
                if (!canImmediateCampfireRecreate()) return;

                Rs2TileObjectModel existing = findCampfire(Rs2Player.getWorldLocation(), 15);
                if (existing != null) {
                    rememberActiveCampfireTarget(existing);
                    fireBurnedOutSignal = false;
                    promotingFireToCampfire = false;
                    state = RuntimeState.BURNING;
                    status = "Using existing fire";
                    return;
                }

                clearActiveCampfireTarget();
                if (!Rs2Inventory.hasItem(TINDERBOX_ID)) {
                    obtainTinderbox();
                    return;
                }

                createFire(true);
            } catch (Exception ex) {
                Microbot.logStackTrace("KSP Chopper immediate campfire", ex);
            } finally {
                immediateCampfireQueued.set(false);
            }
        }, FIRE_MORPH_SETTLE_MS, TimeUnit.MILLISECONDS);
    }

    private boolean canImmediateCampfireRecreate() {
        KspWillowChopperConfig config = runtimeConfig;
        if (!sessionStarted
                || config == null
                || config.bankLogs()
                || !activeTree.isCampfireBurnable()
                || !burnBatchActive
                || !Microbot.isLoggedIn()
                || Microbot.pauseAllScripts.get()
                || plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
            return false;
        }
        return Rs2Inventory.count(activeTree.getResourceId()) > 0;
    }

    /**
     * Opens a bank without ever selecting the Cook's Guild bank booth/bankers
     * reported by the user. If no allowed target is currently loaded, walk to
     * the nearest usable bank location other than Cook's Guild.
     */
    private boolean openAllowedBank() {
        if (Rs2Bank.isOpen()) return true;

        Rs2TileObjectModel bankBooth = Microbot.getRs2TileObjectCache()
                .query()
                .where(object -> object != null
                        && object.getId() == BANK_BOOTH_ID
                        && BANK_BOOTH_NAME.equals(object.getName())
                        && KspTileObjectSupport.hasAction(object, "Bank"))
                .nearestOnClientThread();

        if (bankBooth == null) {
            status = "Bank Booth 10583 not loaded";
            return false;
        }

        status = "Opening Bank Booth 10583";
        if (!bankBooth.click("Bank")) {
            status = "Bank Booth interaction failed - retrying";
            return false;
        }

        sleepUntil(Rs2Bank::isOpen, 4_000);
        return Rs2Bank.isOpen();
    }

    private void bankResource() {
        state = RuntimeState.BANKING;
        long now = System.currentTimeMillis();

        if (!Rs2Bank.isOpen()) {
            if (now - lastBankAttemptMillis < BANK_RETRY_MS) {
                status = Rs2Player.isMoving() ? "Going to bank" : "Waiting for bank";
                return;
            }
            lastBankAttemptMillis = now;
            status = "Opening bank";
            if (!openAllowedBank() && !Rs2Bank.isOpen()) {
                status = "Need nearby bank";
                return;
            }
        }

        if (!Rs2Bank.isOpen()) {
            status = "Waiting for bank";
            return;
        }

        int resourceId = activeTree.getResourceId();
        int before = Rs2Inventory.count(resourceId);
        status = "Banking " + activeTree.getResourceName();
        Rs2Bank.depositAll(resourceId);
        sleepUntil(() -> Rs2Inventory.count(resourceId) == 0, 4_000);

        int after = Rs2Inventory.count(resourceId);
        resourcesBanked += Math.max(0, before - after);
        lastResourceCount = after;

        if (after > 0) {
            status = "Waiting for bank deposit";
            return;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2_500);
        lastBankAttemptMillis = 0L;
        lastTreeClickMillis = 0L;
        lastTreeProgressMillis = 0L;
        clearActiveTreeTarget();
        state = RuntimeState.CHOPPING;
        status = "Banked - resuming " + activeTree;
    }

    private void obtainTinderbox() {
        state = RuntimeState.GETTING_TINDERBOX;

        if (Rs2Inventory.hasItem(TINDERBOX_ID)) return;

        if (Rs2Inventory.emptySlotCount() <= 0) {
            int resourceId = activeTree.getResourceId();
            int before = Rs2Inventory.count(resourceId);
            status = "Making room for tinderbox";
            Rs2Inventory.drop(resourceId);
            sleepUntil(() -> Rs2Inventory.count(resourceId) < before, 2_500);
            lastResourceCount = Rs2Inventory.count(resourceId);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTinderboxAttemptMillis < TINDERBOX_RETRY_MS) {
            status = "Waiting to get tinderbox";
            return;
        }
        lastTinderboxAttemptMillis = now;

        if (!Rs2Bank.isOpen()) {
            status = "Getting tinderbox";
            if (!openAllowedBank() && !Rs2Bank.isOpen()) {
                status = "Need Tinderbox / nearby bank";
                return;
            }
        }

        if (!Rs2Bank.isOpen()) {
            status = "Waiting for bank";
            return;
        }

        Rs2Bank.withdrawItem(true, "Tinderbox");
        sleepUntil(() -> Rs2Inventory.hasItem(TINDERBOX_ID), 3_000);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2_500);

        if (Rs2Inventory.hasItem(TINDERBOX_ID)) {
            status = "Tinderbox ready";
            lastTinderboxAttemptMillis = 0L;
        } else {
            status = "Tinderbox not found in bank";
        }
    }

    private void createFire(boolean force) {
        state = RuntimeState.CREATING_FIRE;

        // Never light another fire while any usable regular/Forester fire is
        // already available. Existing fire always wins.
        Rs2TileObjectModel existing = findCampfire(Rs2Player.getWorldLocation(), 15);
        if (existing != null) {
            rememberActiveCampfireTarget(existing);
            fireBurnedOutSignal = false;
            burningActive = false;
            burnCommandIssued = false;
            burnCommandResourceCount = -1;
            state = RuntimeState.BURNING;
            status = isForesterCampfireId(existing.getId())
                    ? "Existing campfire found - adding logs"
                    : "Existing fire found - converting to campfire";
            return;
        }

        if (!force && isPlayerBusy()) {
            status = "Waiting to create fire";
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && now - lastFireAttemptMillis < FIRE_RETRY_MS) {
            status = "Creating fire";
            return;
        }

        int resourceId = activeTree.getResourceId();
        if (!Rs2Inventory.hasItem(TINDERBOX_ID) || !Rs2Inventory.hasItem(resourceId)) return;

        lastFireAttemptMillis = now;
        int before = Rs2Inventory.count(resourceId);
        status = force ? "Campfire gone - lighting replacement" : "Creating fire";
        Rs2Inventory.combine("Tinderbox", activeTree.getResourceName());

        // Inventory decrease is the authoritative success signal. If the log did
        // not leave inventory, the action failed and must be retried.
        sleepUntil(() -> Rs2Inventory.count(resourceId) < before, 6_000);
        int after = Rs2Inventory.count(resourceId);
        if (after >= before) {
            lastFireAttemptMillis = 0L;
            status = "No log consumed - retrying fire";
            return;
        }

        resourcesBurned += before - after;
        lastResourceCount = after;
        lastBurnProgressMillis = System.currentTimeMillis();
        campfiresLit++;

        // The first action creates a regular Fire. Wait for that object to enter
        // the scene cache, then the next burn step will use a log on it to create
        // the Forester's Campfire. Do not light a second regular fire.
        sleepUntil(() -> findCampfire(Rs2Player.getWorldLocation(), 15) != null,
                FIRE_OBJECT_APPEAR_TIMEOUT_MS);
        Rs2TileObjectModel created = findCampfire(Rs2Player.getWorldLocation(), 15);
        if (created != null) {
            rememberActiveCampfireTarget(created);
            fireBurnedOutSignal = false;
            burningActive = false;
            burnCommandIssued = false;
            burnCommandResourceCount = -1;
            state = RuntimeState.BURNING;
            status = isForesterCampfireId(created.getId())
                    ? "Campfire ready - adding logs"
                    : "Fire lit - converting to campfire";
            return;
        }

        // The log was consumed, so a second fire must not be attempted just
        // because the cache is one tick late. Reset the retry timestamp to now.
        lastFireAttemptMillis = System.currentTimeMillis();
        status = "Fire lit - locating fire";
    }

    private void startBulkBurn(Rs2TileObjectModel campfire) {
        state = RuntimeState.BURNING;

        if (campfire == null || isPlayerBusy()) {
            status = "Waiting to burn logs";
            return;
        }

        rememberActiveCampfireTarget(campfire);

        int resourceId = activeTree.getResourceId();
        if (!Rs2Inventory.hasItem(resourceId)) {
            finishBurnBatch();
            return;
        }

        final boolean promoting = !isForesterCampfireId(campfire.getId());
        promotingFireToCampfire = promoting;
        int before = Rs2Inventory.count(resourceId);
        status = promoting
                ? "Using log on existing fire"
                : "Adding " + activeTree.getResourceName() + " to campfire";

        if (!Rs2Inventory.isItemSelected()) {
            Rs2Inventory.use(resourceId);
            if (!sleepUntil(Rs2Inventory::isItemSelected, 2_000)) {
                promotingFireToCampfire = false;
                status = "Selecting " + activeTree.getResourceName();
                return;
            }
        }

        if (!campfire.click()) {
            promotingFireToCampfire = false;
            status = "Campfire click failed - retrying";
            return;
        }

        // A regular Fire promotion may consume a log before any burn dialog is
        // shown. A Forester campfire normally opens the make/burn interface.
        sleepUntil(() -> Rs2Inventory.count(resourceId) < before
                        || Rs2Widget.getWidget(BURN_INTERFACE_WIDGET) != null,
                BURN_START_GRACE_MS);

        if (Rs2Widget.getWidget(BURN_INTERFACE_WIDGET) != null) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }

        // Do not mark Firemaking as active merely because an animation/widget
        // happened. The selected log count must actually decrease.
        sleepUntil(() -> Rs2Inventory.count(resourceId) < before, BURN_PROGRESS_GRACE_MS);
        int after = Rs2Inventory.count(resourceId);
        if (after >= before) {
            promotingFireToCampfire = false;
            burnCommandIssued = false;
            burnCommandResourceCount = -1;
            burningActive = false;
            lastBurnProgressMillis = 0L;
            status = "No logs consumed - retrying campfire";
            return;
        }

        resourcesBurned += before - after;
        lastResourceCount = after;
        lastBurnProgressMillis = System.currentTimeMillis();

        if (promoting) {
            // Using a log on a regular Fire replaces it with object 49927-49932.
            // Wait for that expected ID morph and then use the new campfire; never
            // interpret the regular Fire despawn as a reason to light fire #2.
            sleepUntil(() -> {
                Rs2TileObjectModel current = findCampfire(Rs2Player.getWorldLocation(), 15);
                return current != null && isForesterCampfireId(current.getId());
            }, FIRE_OBJECT_APPEAR_TIMEOUT_MS);

            Rs2TileObjectModel promoted = findForesterCampfire(Rs2Player.getWorldLocation(), 15);
            if (promoted != null) rememberActiveCampfireTarget(promoted);
            promotingFireToCampfire = false;
            burnCommandIssued = false;
            burnCommandResourceCount = -1;
            burningActive = false;
            state = RuntimeState.BURNING;
            status = promoted != null
                    ? "Campfire ready - adding remaining logs"
                    : "Fire converted - locating campfire";
            return;
        }

        promotingFireToCampfire = false;
        burnCommandIssued = true;
        burnCommandMillis = System.currentTimeMillis();
        burnCommandResourceCount = after;
        burningActive = true;
        status = "Burning " + activeTree.getResourceName();
    }

    private Rs2TileObjectModel findCampfire(WorldPoint anchor, int radius) {
        if (anchor == null) return null;

        // Prefer a real Forester's Campfire. If none exists, use an existing
        // regular/coloured Fire and promote it instead of lighting another one.
        Rs2TileObjectModel forester = findForesterCampfire(anchor, radius);
        if (forester != null) return forester;

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache()
                        .query()
                        .where(object -> object != null && isRegularFireId(object.getId()))
                        .nearest(anchor, radius)
        ).orElse(null);
    }

    private Rs2TileObjectModel findForesterCampfire(WorldPoint anchor, int radius) {
        if (anchor == null) return null;

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache()
                        .query()
                        .where(object -> object != null && isForesterCampfireId(object.getId()))
                        .nearest(anchor, radius)
        ).orElse(null);
    }

    private boolean isUsableFireId(int id) {
        return isForesterCampfireId(id) || isRegularFireId(id);
    }

    private boolean isForesterCampfireId(int id) {
        return id >= FORESTER_CAMPFIRE_MIN_ID && id <= FORESTER_CAMPFIRE_MAX_ID;
    }

    private boolean isRegularFireId(int id) {
        return id == FIRE_ID
                || id == REGULAR_FIRE_ID
                || id == REGULAR_FIRE_BLUE_ID
                || id == REGULAR_FIRE_GREEN_ID
                || id == REGULAR_FIRE_PURPLE_ID
                || id == REGULAR_FIRE_RED_ID
                || id == REGULAR_FIRE_WHITE_ID;
    }

    private void trackResourceChanges() {
        int current = Rs2Inventory.count(activeTree.getResourceId());
        if (lastResourceCount < 0) {
            lastResourceCount = current;
            return;
        }

        if (current > lastResourceCount) {
            resourcesChopped += current - lastResourceCount;
            lastTreeProgressMillis = System.currentTimeMillis();
        } else if (current < lastResourceCount) {
            int lost = lastResourceCount - current;
            if (state == RuntimeState.BURNING || state == RuntimeState.CREATING_FIRE) {
                resourcesBurned += lost;
                lastBurnProgressMillis = System.currentTimeMillis();
            }
        }

        lastResourceCount = current;
    }

    private void finishBurnBatch() {
        burningActive = false;
        burnBatchActive = false;
        burnCommandIssued = false;
        burnCommandResourceCount = -1;
        promotingFireToCampfire = false;
        fireBurnedOutSignal = false;
        burnCommandMillis = 0L;
        lastBurnProgressMillis = 0L;
        lastTreeClickMillis = 0L;
        lastTreeProgressMillis = 0L;
        clearActiveTreeTarget();
        clearActiveCampfireTarget();
        immediateCampfireQueued.set(false);
        status = "Burn complete - resuming " + activeTree;
    }

    private void switchTree(KspTree selected) {
        activeTree = selected;
        finishBurnBatch();
        campfireNearby = false;
        lastChangedTreeLocation = null;
        lastBankAttemptMillis = 0L;
        lastTinderboxAttemptMillis = 0L;
        lastFireAttemptMillis = 0L;
        lastResourceCount = Rs2Inventory.count(activeTree.getResourceId());
        state = RuntimeState.CHOPPING;
        status = "Tree changed to " + activeTree;
    }

    private void resetActionLatches() {
        lastTreeClickMillis = 0L;
        lastTreeProgressMillis = 0L;
        lastBankAttemptMillis = 0L;
        burnCommandIssued = false;
        burnCommandResourceCount = -1;
        promotingFireToCampfire = false;
        burningActive = false;
        clearActiveTreeTarget();
        clearActiveCampfireTarget();
        syncResourceBaseline();
    }

    private void syncResourceBaseline() {
        lastResourceCount = Rs2Inventory.count(activeTree.getResourceId());
    }

    private boolean hasAxe() {
        return Rs2Inventory.hasItem("axe") || Rs2Equipment.isWearing("axe");
    }

    private boolean isPlayerBusy() {
        return Rs2Player.isMoving() || Rs2Player.isAnimating(1200) || Rs2Player.isInteracting();
    }

    private KspTree safeTree(KspTree tree) {
        return tree == null ? KspTree.WILLOW : tree;
    }

    private int skillXp(Skill skill) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getSkillExperience(skill)).orElse(0);
    }

    private int skillLevel(Skill skill) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getRealSkillLevel(skill)).orElse(0);
    }

    public void notifyFireBurnedOut() {
        fireBurnedOutSignal = true;
        burnCommandIssued = false;
        burningActive = false;
        campfireNearby = false;
        int oldId = activeCampfireObjectId;
        if (canImmediateCampfireRecreate()) {
            requestImmediateCampfireRecreate(oldId);
        }
    }

    @Override
    public synchronized void shutdown() {
        super.shutdown();

        if (eventBusRegistered && eventBus != null) {
            try {
                eventBus.unregister(this);
            } catch (Exception ignored) {
            }
            eventBusRegistered = false;
        }

        runtimeConfig = null;
        sessionStarted = false;
        state = RuntimeState.IDLE;
        status = "Idle";
        campfireNearby = false;
        burningActive = false;
        burnBatchActive = false;
        burnCommandIssued = false;
        burnCommandResourceCount = -1;
        promotingFireToCampfire = false;
        fireBurnedOutSignal = false;
        lastResourceCount = -1;
        lastChangedTreeLocation = null;
        immediateRetargetQueued.set(false);
        immediateCampfireQueued.set(false);
        clearActiveTreeTarget();
        clearActiveCampfireTarget();
    }

    public RuntimeState getState() { return state; }
    public String getStatus() { return status; }
    public KspTree getActiveTree() { return activeTree; }
    public boolean isCampfireNearby() { return campfireNearby; }
    public boolean isBurningActive() { return burningActive; }
    public boolean hasSessionStarted() { return sessionStarted && startTimeMillis > 0L; }

    public long getRuntimeMillis() {
        return hasSessionStarted() ? Math.max(0L, System.currentTimeMillis() - startTimeMillis) : 0L;
    }

    public int getWoodcuttingXpGained() { return Math.max(0, skillXp(Skill.WOODCUTTING) - startWoodcuttingXp); }
    public int getFiremakingXpGained() { return Math.max(0, skillXp(Skill.FIREMAKING) - startFiremakingXp); }
    public int getWoodcuttingLevel() { return skillLevel(Skill.WOODCUTTING); }
    public int getFiremakingLevel() { return skillLevel(Skill.FIREMAKING); }
    public int getWoodcuttingLevelsGained() {
        return hasSessionStarted() ? Math.max(0, getWoodcuttingLevel() - startWoodcuttingLevel) : 0;
    }
    public int getFiremakingLevelsGained() {
        return hasSessionStarted() ? Math.max(0, getFiremakingLevel() - startFiremakingLevel) : 0;
    }
    public int getResourcesChopped() { return resourcesChopped; }
    public int getResourcesBanked() { return resourcesBanked; }
    public int getResourcesBurned() { return resourcesBurned; }
    public int getCampfiresLit() { return campfiresLit; }
    public int getCurrentResourceCount() { return Rs2Inventory.count(activeTree.getResourceId()); }
}
