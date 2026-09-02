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
 * KSP Chopper runtime.
 *
 * Core cycle:
 * CHOPPING -> BANKING -> CHOPPING
 * CHOPPING -> GETTING_TINDERBOX -> CREATING_FIRE -> BURNING -> CHOPPING
 *
 * The active chopping object is tracked by id/hash/tile. When RuneLite reports
 * that exact object despawning/replacing (the normal signal for an object-id
 * transition), a forced retarget is queued immediately. Forced retargets bypass
 * the normal animation/retry suppression so the next valid loaded tree is
 * clicked as soon as the old object changes.
 */
@Slf4j
@Singleton
public class KspWillowChopperScript extends Script {
    private static final int TINDERBOX_ID = ItemID.TINDERBOX;
    private static final int BURN_INTERFACE_WIDGET = 17694735;
    private static final int FIRE_ID = ObjectID.FIRE;
    private static final int FIRE_ID_ALT = 49927;

    private static final long LOOP_MS = 300L;
    private static final long TREE_RETRY_MS = 6_000L;
    private static final long BANK_RETRY_MS = 4_000L;
    private static final long TINDERBOX_RETRY_MS = 4_000L;
    private static final long FIRE_RETRY_MS = 3_000L;
    private static final long BURN_START_GRACE_MS = 3_000L;
    private static final long BURN_PROGRESS_GRACE_MS = 4_500L;

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
    private final AtomicBoolean immediateRetargetQueued = new AtomicBoolean(false);
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
        lastChangedTreeLocation = null;
        immediateRetargetQueued.set(false);

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
                syncResourceBaseline();
                resetActionLatches();
                return;
            }

            if (plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
                state = RuntimeState.FORESTRY;
                status = "Forestry: " + plugin.getCurrentForestryEvent();
                clearActiveTreeTarget();
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

            if (!fireBurnedOutSignal
                    && (isPlayerBusy()
                    || now - burnCommandMillis < BURN_START_GRACE_MS
                    || now - lastBurnProgressMillis < BURN_PROGRESS_GRACE_MS)) {
                state = RuntimeState.BURNING;
                burningActive = true;
                status = "Burning " + activeTree.getResourceName();
                return;
            }

            burnCommandIssued = false;
            burningActive = false;
        }

        Rs2TileObjectModel campfire = findCampfire(Rs2Player.getWorldLocation(), 15);
        campfireNearby = campfire != null;
        fireBurnedOutSignal = false;

        if (campfire == null) {
            if (!Rs2Inventory.hasItem(TINDERBOX_ID)) {
                obtainTinderbox();
                return;
            }
            createFire();
            return;
        }

        startBulkBurn(campfire);
    }

    private void chopSelectedTree(boolean force, WorldPoint avoidLocation) {
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
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

        status = force
                ? "Object changed - clicking next " + activeTree
                : "Clicking " + activeTree;

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

    private void rememberActiveTreeTarget(Rs2TileObjectModel tree) {
        if (tree == null) {
            clearActiveTreeTarget();
            return;
        }
        activeTreeObjectId = tree.getId();
        activeTreeObjectHash = tree.getHash();
        activeTreeObjectLocation = tree.getWorldLocation();
    }

    private void clearActiveTreeTarget() {
        activeTreeObjectId = -1;
        activeTreeObjectHash = Long.MIN_VALUE;
        activeTreeObjectLocation = null;
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject object = event.getGameObject();
        if (!isActiveChopObject(object)) return;
        requestImmediateRetarget(object.getWorldLocation(), object.getId());
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject object = event.getGameObject();
        WorldPoint targetLocation = activeTreeObjectLocation;

        if (!sessionStarted
                || state != RuntimeState.CHOPPING
                || object == null
                || targetLocation == null
                || !targetLocation.equals(object.getWorldLocation())
                || object.getId() == activeTreeObjectId) {
            return;
        }

        requestImmediateRetarget(targetLocation, activeTreeObjectId);
    }

    private boolean isActiveChopObject(GameObject object) {
        if (!sessionStarted
                || state != RuntimeState.CHOPPING
                || object == null
                || activeTreeObjectLocation == null) {
            return false;
        }

        if (!activeTreeObjectLocation.equals(object.getWorldLocation())) return false;

        return object.getHash() == activeTreeObjectHash
                || object.getId() == activeTreeObjectId;
    }

    private void requestImmediateRetarget(WorldPoint changedLocation, int previousObjectId) {
        if (!canImmediateRetarget()) return;

        lastChangedTreeLocation = changedLocation;
        clearActiveTreeTarget();
        lastTreeClickMillis = 0L;
        lastTreeProgressMillis = 0L;
        state = RuntimeState.CHOPPING;
        status = "Object " + previousObjectId + " changed - selecting next " + activeTree;

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

        if (!config.bankLogs()
                && activeTree.isCampfireBurnable()
                && burnBatchActive) {
            return false;
        }

        return hasAxe()
                && Rs2Player.getRealSkillLevel(Skill.WOODCUTTING) >= activeTree.getWoodcuttingLevel();
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
            if (!Rs2Bank.openBank() && !Rs2Bank.isOpen()) {
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
        int deposited = Math.max(0, before - after);
        resourcesBanked += deposited;
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
            if (!Rs2Bank.openBank() && !Rs2Bank.isOpen()) {
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

    private void createFire() {
        state = RuntimeState.CREATING_FIRE;

        if (isPlayerBusy()) {
            status = "Waiting to create fire";
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastFireAttemptMillis < FIRE_RETRY_MS) {
            status = "Creating fire";
            return;
        }

        int resourceId = activeTree.getResourceId();
        if (!Rs2Inventory.hasItem(TINDERBOX_ID) || !Rs2Inventory.hasItem(resourceId)) return;

        lastFireAttemptMillis = now;
        int before = Rs2Inventory.count(resourceId);
        status = "Creating fire";
        Rs2Inventory.combine("Tinderbox", activeTree.getResourceName());

        boolean xpDrop = Rs2Player.waitForXpDrop(Skill.FIREMAKING, 6_000);
        int after = Rs2Inventory.count(resourceId);
        if (after < before) {
            resourcesBurned += before - after;
            lastResourceCount = after;
            lastBurnProgressMillis = System.currentTimeMillis();
        }

        Rs2TileObjectModel created = findCampfire(Rs2Player.getWorldLocation(), 15);
        if (xpDrop || created != null) {
            campfiresLit++;
            campfireNearby = created != null;
            burningActive = false;
            burnCommandIssued = false;
            fireBurnedOutSignal = false;
            status = "Fire ready";
            return;
        }

        status = "Could not create fire - retrying";
    }

    private void startBulkBurn(Rs2TileObjectModel campfire) {
        state = RuntimeState.BURNING;

        if (campfire == null || isPlayerBusy()) {
            status = "Waiting to burn logs";
            return;
        }

        int resourceId = activeTree.getResourceId();
        if (!Rs2Inventory.hasItem(resourceId)) {
            finishBurnBatch();
            return;
        }

        status = "Adding " + activeTree.getResourceName() + " to campfire";

        if (!Rs2Inventory.isItemSelected()) {
            Rs2Inventory.use(resourceId);
            if (!sleepUntil(Rs2Inventory::isItemSelected, 2_000)) {
                status = "Selecting " + activeTree.getResourceName();
                return;
            }
        }

        if (!campfire.click()) {
            status = "Campfire click failed - retrying";
            return;
        }

        if (!sleepUntil(() -> Rs2Widget.getWidget(BURN_INTERFACE_WIDGET) != null, 4_500)) {
            int current = Rs2Inventory.count(resourceId);
            if (!isPlayerBusy() && current >= lastResourceCount) {
                status = "Waiting for Burn dialog";
                return;
            }
        } else {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        }

        burnCommandIssued = true;
        burnCommandMillis = System.currentTimeMillis();
        lastBurnProgressMillis = burnCommandMillis;
        burningActive = true;
        status = "Burning " + activeTree.getResourceName();
    }

    private Rs2TileObjectModel findCampfire(WorldPoint anchor, int radius) {
        if (anchor == null) return null;

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Rs2TileObjectModel target = Microbot.getRs2TileObjectCache()
                    .query()
                    .withNameContains("ampfire")
                    .nearest(anchor, radius);

            if (target == null) {
                target = Microbot.getRs2TileObjectCache()
                        .query()
                        .where(object -> object.getId() == FIRE_ID || object.getId() == FIRE_ID_ALT)
                        .nearest(anchor, radius);
            }
            return target;
        }).orElse(null);
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
        fireBurnedOutSignal = false;
        burnCommandMillis = 0L;
        lastBurnProgressMillis = 0L;
        lastTreeClickMillis = 0L;
        lastTreeProgressMillis = 0L;
        clearActiveTreeTarget();
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
        burningActive = false;
        clearActiveTreeTarget();
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
        fireBurnedOutSignal = false;
        lastResourceCount = -1;
        lastChangedTreeLocation = null;
        immediateRetargetQueued.set(false);
        clearActiveTreeTarget();
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
