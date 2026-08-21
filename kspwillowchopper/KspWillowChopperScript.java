package net.runelite.client.plugins.microbot.kspwillowchopper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Singleton
public class KspWillowChopperScript extends Script {
    private static final int TINDERBOX_ID = ItemID.TINDERBOX;
    private static final int BURN_INTERFACE_WIDGET = 17694735;
    private static final int FIRE_ID = ObjectID.FIRE;
    private static final int FIRE_ID_ALT = 49927;

    private final KspWillowChopperPlugin plugin;

    private volatile String status = "Idle";
    private volatile boolean campfireNearby;
    private volatile boolean burningActive;
    private volatile boolean sessionStarted;
    private volatile KspTree activeTree = KspTree.WILLOW;

    // Tracks the exact scene object we most recently clicked. When RuneLite reports
    // that object despawning/morphing to another ID, normal movement/animation
    // throttles are bypassed and a replacement target is selected immediately.
    private volatile WorldPoint activeTargetLocation;
    private volatile int activeTargetObjectId = -1;
    private volatile boolean immediateRetargetRequested;
    private volatile int immediateRetargetAttempts;
    private final AtomicBoolean immediateRetargetQueued = new AtomicBoolean(false);
    private final Object targetInteractionLock = new Object();

    // Same event-driven invalidation for the campfire target. A Forester's
    // Campfire can morph to a different object ID or disappear while logs are
    // being processed. Do not wait for the five-second burn-progress timeout:
    // reacquire the replacement immediately and repeat the interaction.
    private volatile WorldPoint activeCampfireLocation;
    private volatile int activeCampfireObjectId = -1;
    private volatile WorldPoint invalidatedCampfireLocation;
    private volatile int invalidatedCampfireObjectId = -1;
    private volatile boolean immediateCampfireRetargetRequested;
    // True only when the active campfire is confirmed gone. While set, stale
    // burn timers/animations are ignored and recovery must reacquire or create
    // a fire before the remaining logs can continue.
    private volatile boolean campfireLostConfirmed;
    private volatile int immediateCampfireRetargetAttempts;
    private volatile boolean burnModeEnabled;
    private volatile boolean burnCycleActive;
    private volatile boolean treeInteractionIssued;
    private volatile boolean campfireInteractionIssued;
    private final AtomicBoolean immediateCampfireRetargetQueued = new AtomicBoolean(false);
    private final Object campfireInteractionLock = new Object();

    private long startTimeMillis;
    private int startWoodcuttingXp;
    private int startFiremakingXp;
    private int startWoodcuttingLevel;
    private int startFiremakingLevel;
    private int resourcesChopped;
    private int resourcesBanked;
    private int resourcesBurned;
    private int campfiresLit;

    private int lastBurnResourceCount = -1;
    private int lastObservedResourceCount = -1;
    private int suppressedResourceLoss = 0;
    private long lastBurnProgressMillis = 0L;
    private long lastTreeClickMillis = 0L;
    // This timestamp is both the no-progress recovery guard and the observable
    // postcondition of a successful chop. The immediate-retarget task runs on
    // the same scheduler as the main worker, so retain its latest value across
    // those task boundaries.
    private volatile long lastTreeProgressMillis = 0L;
    private long lastCampfireInteractionMillis = 0L;
    private long lastCampfireCreateAttemptMillis = 0L;

    @Inject
    public KspWillowChopperScript(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run(KspWillowChopperConfig config) {
        startTimeMillis = System.currentTimeMillis();
        startWoodcuttingXp = skillXp(Skill.WOODCUTTING);
        startFiremakingXp = skillXp(Skill.FIREMAKING);
        startWoodcuttingLevel = skillLevel(Skill.WOODCUTTING);
        startFiremakingLevel = skillLevel(Skill.FIREMAKING);
        resourcesChopped = 0;
        resourcesBanked = 0;
        resourcesBurned = 0;
        campfiresLit = 0;
        lastBurnResourceCount = -1;
        lastObservedResourceCount = -1;
        suppressedResourceLoss = 0;
        burningActive = false;
        campfireNearby = false;
        activeTree = safeTree(config.tree());
        activeTargetLocation = null;
        activeTargetObjectId = -1;
        immediateRetargetRequested = false;
        immediateRetargetAttempts = 0;
        immediateRetargetQueued.set(false);
        activeCampfireLocation = null;
        activeCampfireObjectId = -1;
        invalidatedCampfireLocation = null;
        invalidatedCampfireObjectId = -1;
        immediateCampfireRetargetRequested = false;
        campfireLostConfirmed = false;
        immediateCampfireRetargetAttempts = 0;
        immediateCampfireRetargetQueued.set(false);
        burnModeEnabled = !config.bankLogs() && activeTree.isCampfireBurnable();
        burnCycleActive = false;
        treeInteractionIssued = false;
        campfireInteractionIssued = false;
        lastTreeProgressMillis = System.currentTimeMillis();
        lastCampfireInteractionMillis = 0L;
        lastCampfireCreateAttemptMillis = 0L;
        sessionStarted = true;
        status = "Starting";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    status = "Waiting for login";
                    return;
                }

                KspTree selectedTree = safeTree(config.tree());
                syncSelectedTree(selectedTree);

                if (!super.run()) {
                    syncResourceBaseline();
                    if (plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
                        status = "Forestry: " + plugin.getCurrentForestryEvent();
                    }
                    return;
                }

                if (plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
                    plugin.setCurrentForestryEvent(KspForestryEvent.NONE);
                    syncResourceBaseline();
                }

                trackResourceInventoryChanges(!config.bankLogs());

                // Event notifications are the fastest retarget path, but some larger
                // or transformed tree objects report a different raw GameObject anchor
                // than Rs2TileObjectModel. Independently verify that the exact clicked
                // object is still live so every supported tree type gets the same
                // immediate replacement behavior.
                validateActiveTreeTargetLiveness();

                int wcLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
                if (wcLevel < activeTree.getWoodcuttingLevel()) {
                    status = "Need " + activeTree.getWoodcuttingLevel() + " Woodcutting";
                    return;
                }

                if (!hasAxe()) {
                    status = "No axe equipped/in inventory";
                    return;
                }

                burnModeEnabled = !config.bankLogs() && activeTree.isCampfireBurnable();

                if (config.bankLogs()) {
                    burningActive = false;
                    burnCycleActive = false;
                    campfireInteractionIssued = false;
                    lastBurnResourceCount = -1;
                    suppressedResourceLoss = 0;
                    campfireNearby = false;
                    clearActiveCampfireTarget();

                    if (Rs2Inventory.isFull()) {
                        handleDirectBanking();
                    } else {
                        clickSelectedTreeDirect();
                    }
                } else {
                    if (!activeTree.isCampfireBurnable()) {
                        burningActive = false;
                        campfireNearby = false;
                        status = activeTree.getResourceName() + " cannot be burned; enable Bank resources";
                        return;
                    }
                    handleBurnMode();
                }
            } catch (Exception ex) {
                status = "Error - check logs";
                Microbot.logStackTrace("KspWillowChopperScript", ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        sessionStarted = false;
        burningActive = false;
        campfireNearby = false;
        lastObservedResourceCount = -1;
        suppressedResourceLoss = 0;
        activeTargetLocation = null;
        activeTargetObjectId = -1;
        immediateRetargetRequested = false;
        immediateRetargetAttempts = 0;
        immediateRetargetQueued.set(false);
        burnModeEnabled = false;
        burnCycleActive = false;
        treeInteractionIssued = false;
        campfireInteractionIssued = false;
        clearActiveCampfireTarget();
        invalidatedCampfireLocation = null;
        invalidatedCampfireObjectId = -1;
        immediateCampfireRetargetRequested = false;
        campfireLostConfirmed = false;
        immediateCampfireRetargetAttempts = 0;
        immediateCampfireRetargetQueued.set(false);
        status = "Idle";
    }

    private KspTree safeTree(KspTree tree) {
        return tree == null ? KspTree.WILLOW : tree;
    }

    private void syncSelectedTree(KspTree selectedTree) {
        if (selectedTree == activeTree) {
            return;
        }

        activeTree = selectedTree;
        burningActive = false;
        burnCycleActive = false;
        treeInteractionIssued = false;
        campfireInteractionIssued = false;
        campfireNearby = false;
        lastBurnResourceCount = -1;
        suppressedResourceLoss = 0;
        lastObservedResourceCount = Rs2Inventory.count(activeTree.getResourceId());
        lastTreeClickMillis = 0L;
        activeTargetLocation = null;
        activeTargetObjectId = -1;
        immediateRetargetRequested = false;
        immediateRetargetAttempts = 0;
        clearActiveCampfireTarget();
        invalidatedCampfireLocation = null;
        invalidatedCampfireObjectId = -1;
        immediateCampfireRetargetRequested = false;
        campfireLostConfirmed = false;
        immediateCampfireRetargetAttempts = 0;
        status = "Tree changed to " + activeTree;
    }

    private void syncResourceBaseline() {
        lastObservedResourceCount = Rs2Inventory.count(activeTree.getResourceId());
        lastBurnResourceCount = lastObservedResourceCount;
        suppressedResourceLoss = 0;
    }

    private boolean hasAxe() {
        return Rs2Inventory.hasItem("axe") || Rs2Equipment.isWearing("axe");
    }

    private void handleDirectBanking() {
        int resourceId = activeTree.getResourceId();
        int before = Rs2Inventory.count(resourceId);
        status = "Opening bank directly";

        if (!Rs2Bank.openBank()) {
            status = "Nearby bank not clickable";
            return;
        }

        if (!sleepUntil(Rs2Bank::isOpen, 5000)) {
            status = "Waiting for bank";
            return;
        }

        status = "Banking " + activeTree.getResourceName();
        Rs2Bank.depositAll(resourceId);
        sleepUntil(() -> Rs2Inventory.count(resourceId) < before, 3000);

        int after = Rs2Inventory.count(resourceId);
        resourcesBanked += Math.max(0, before - after);
        lastObservedResourceCount = after;

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);

        status = "Clicking " + activeTree;
        clickSelectedTreeDirect();
    }

    private void handleBurnMode() {
        trackResourceInventoryChanges(true);

        int resourceId = activeTree.getResourceId();
        int resourceCount = Rs2Inventory.count(resourceId);
        Rs2TileObjectModel fire = findCampfire(Rs2Player.getWorldLocation(), 15);
        campfireNearby = fire != null;
        trackBurnProgress();

        // A burn cycle begins once the inventory fills. From that point onward,
        // never return to chopping until every selected log has been consumed.
        if (!burnCycleActive && Rs2Inventory.isFull()) {
            burnCycleActive = true;
            immediateRetargetRequested = false;
            treeInteractionIssued = false;
        }

        if (burnCycleActive) {
            if (resourceCount <= 0) {
                burningActive = false;
                burnCycleActive = false;
                campfireInteractionIssued = false;
                lastBurnResourceCount = -1;
                immediateCampfireRetargetRequested = false;
                status = "Burn complete - resuming " + activeTree;
                if (!isPlayerBusyForAction()) {
                    clickSelectedTreeDirect();
                }
                return;
            }

            // A confirmed burn-out/despawn is authoritative. Do not let stale
            // animation/progress state hold us in a fake burning state. If there is
            // no replacement fire, immediately start creating another one.
            if (campfireLostConfirmed) {
                fire = findCampfire(Rs2Player.getWorldLocation(), 15);
                campfireNearby = fire != null;
                if (fire == null) {
                    burningActive = false;
                    campfireInteractionIssued = false;
                    createCampfire();
                    return;
                }

                activeCampfireLocation = fire.getWorldLocation();
                activeCampfireObjectId = fire.getId();
                campfireLostConfirmed = false;
                immediateCampfireRetargetRequested = false;
                immediateCampfireRetargetAttempts = 0;
                startCampfireBurn(fire, true);
                return;
            }

            // A same-tile ID morph may have a valid replacement. Reacquire it, but
            // do not spam another Use action while the existing burn is still alive.
            if (immediateCampfireRetargetRequested) {
                queueImmediateCampfireRetarget(0L);
                return;
            }

            if (isCampfireProcessingActive()) {
                status = "Burning " + activeTree.getResourceName();
                return;
            }

            fire = findCampfire(Rs2Player.getWorldLocation(), 15);
            campfireNearby = fire != null;
            if (fire == null) {
                createCampfire();
                return;
            }

            startCampfireBurn(fire);
            return;
        }

        // Collection phase: keep chopping until full. A partially-filled inventory
        // must not trigger burning unless we were already in an active burn cycle.
        if (fire == null
                && !Rs2Inventory.hasItem(TINDERBOX_ID)
                && Rs2Inventory.emptySlotCount() <= 1) {
            ensureTinderbox();
            return;
        }

        clickSelectedTreeDirect();
    }

    private void ensureTinderbox() {
        if (Rs2Inventory.hasItem(TINDERBOX_ID)) {
            return;
        }

        if (Rs2Inventory.emptySlotCount() <= 0) {
            status = "Need slot for tinderbox";
            return;
        }

        status = "Getting tinderbox";
        if (!Rs2Bank.openBank() || !sleepUntil(Rs2Bank::isOpen, 4000)) {
            status = "Need tinderbox / nearby bank";
            return;
        }

        Rs2Bank.withdrawItem(true, "Tinderbox");
        sleepUntil(() -> Rs2Inventory.hasItem(TINDERBOX_ID), 3000);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2500);
    }

    private void createCampfire() {
        int resourceId = activeTree.getResourceId();

        if (isPlayerBusyForAction()) {
            status = "Waiting for current interaction";
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastCampfireCreateAttemptMillis < 2500L) {
            return;
        }

        if (!Rs2Inventory.hasItem(TINDERBOX_ID)) {
            if (Rs2Inventory.emptySlotCount() > 0) {
                ensureTinderbox();
            } else {
                status = "Making room for tinderbox";
                int beforeDrop = Rs2Inventory.count(resourceId);
                suppressedResourceLoss++;
                Rs2Inventory.drop(resourceId);
                if (!sleepUntil(() -> Rs2Inventory.count(resourceId) < beforeDrop, 2500)) {
                    suppressedResourceLoss = Math.max(0, suppressedResourceLoss - 1);
                } else {
                    trackResourceInventoryChanges(true);
                }
            }
            return;
        }

        if (!Rs2Inventory.hasItem(resourceId)) {
            return;
        }

        lastCampfireCreateAttemptMillis = now;
        burnCycleActive = true;
        status = "Creating campfire";
        Rs2Inventory.combine("Tinderbox", activeTree.getResourceName());

        if (Rs2Player.waitForXpDrop(Skill.FIREMAKING, 5000)) {
            trackResourceInventoryChanges(true);
            campfiresLit++;
            campfireNearby = true;
            Rs2TileObjectModel createdFire = findCampfire(Rs2Player.getWorldLocation(), 15);
            if (createdFire != null) {
                activeCampfireLocation = createdFire.getWorldLocation();
                activeCampfireObjectId = createdFire.getId();
            }
            lastBurnResourceCount = Rs2Inventory.count(resourceId);
            lastBurnProgressMillis = System.currentTimeMillis();
            campfireInteractionIssued = false;
            campfireLostConfirmed = false;
            immediateCampfireRetargetRequested = false;
            immediateCampfireRetargetAttempts = 0;
            status = "Campfire created";
            return;
        }

        // No web walker/repositioning. Retry the direct firemaking action later.
        status = "Campfire creation failed - retrying";
    }

    private void startCampfireBurn(Rs2TileObjectModel fire) {
        startCampfireBurn(fire, false);
    }

    private boolean startCampfireBurn(Rs2TileObjectModel fire, boolean forceImmediate) {
        synchronized (campfireInteractionLock) {
            int resourceId = activeTree.getResourceId();
            if (fire == null || !Rs2Inventory.hasItem(resourceId) || !burnModeEnabled || !burnCycleActive) {
                return false;
            }

            if (isCampfireProcessingActive()) {
                status = "Already burning " + activeTree.getResourceName();
                return false;
            }

            // Never issue Use->campfire twice while the previous interaction is still
            // within its startup window, even if a scene object morph event fired.
            long now = System.currentTimeMillis();
            if (campfireInteractionIssued && now - lastCampfireInteractionMillis < 3000L) {
                status = "Waiting for campfire interaction";
                return false;
            }

            if (forceImmediate
                    && immediateCampfireRetargetAttempts <= 2
                    && invalidatedCampfireLocation != null
                    && invalidatedCampfireLocation.equals(fire.getWorldLocation())
                    && invalidatedCampfireObjectId == fire.getId()) {
                return false;
            }

            activeCampfireLocation = fire.getWorldLocation();
            activeCampfireObjectId = fire.getId();
            campfireNearby = true;

            int count = Rs2Inventory.count(resourceId);
            status = forceImmediate
                    ? "Campfire ready - resuming burn"
                    : "Adding " + activeTree.getResourceName() + " to campfire";

            // A selected item means an interaction is already being prepared; do not
            // select the same log again and spam Use actions.
            if (!Rs2Inventory.isItemSelected()) {
                Rs2Inventory.use(resourceId);
                if (!sleepUntil(Rs2Inventory::isItemSelected, 2000)) {
                    status = "Selecting " + activeTree.getResourceName();
                    return false;
                }
            }

            if (isPlayerBusyForAction()) {
                status = "Waiting for current interaction";
                return false;
            }

            Rs2GameObject.interact(fire);
            if (!sleepUntil(() -> Rs2Widget.getWidget(BURN_INTERFACE_WIDGET) != null, 5000)) {
                status = forceImmediate ? "Campfire changed - waiting" : "Waiting for Burn dialog";
                return false;
            }

            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            burnCycleActive = true;
            burningActive = true;
            campfireInteractionIssued = true;
            lastCampfireInteractionMillis = System.currentTimeMillis();
            lastBurnResourceCount = count;
            lastBurnProgressMillis = lastCampfireInteractionMillis;
            immediateCampfireRetargetRequested = false;
            campfireLostConfirmed = false;
            immediateCampfireRetargetAttempts = 0;
            invalidatedCampfireLocation = null;
            invalidatedCampfireObjectId = -1;
            status = "Burning " + activeTree.getResourceName();
            return true;
        }
    }

    private boolean isPlayerBusyForAction() {
        return Rs2Player.isMoving() || Rs2Player.isAnimating(1200) || Rs2Player.isInteracting();
    }

    private boolean isCampfireProcessingActive() {
        if (!burnCycleActive || !Rs2Inventory.hasItem(activeTree.getResourceId())) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (isPlayerBusyForAction()) {
            return true;
        }

        if (campfireInteractionIssued && now - lastCampfireInteractionMillis < 3000L) {
            return true;
        }

        return burningActive && lastBurnProgressMillis > 0L && now - lastBurnProgressMillis < 4500L;
    }

    private void trackBurnProgress() {
        if (!burningActive || lastBurnResourceCount < 0) {
            return;
        }

        int count = Rs2Inventory.count(activeTree.getResourceId());
        if (count < lastBurnResourceCount) {
            lastBurnResourceCount = count;
            lastBurnProgressMillis = System.currentTimeMillis();
        }
    }

    /**
     * Tracks resource gains from real inventory increases and burn consumption from
     * inventory decreases. Banking only updates the baseline; burn mode counts the
     * decrease as consumed unless it was deliberately suppressed (e.g. one dropped
     * resource to make room for a tinderbox).
     */
    private void trackResourceInventoryChanges(boolean countLossAsBurned) {
        int current = Rs2Inventory.count(activeTree.getResourceId());

        if (lastObservedResourceCount < 0) {
            lastObservedResourceCount = current;
            return;
        }

        if (current > lastObservedResourceCount) {
            resourcesChopped += current - lastObservedResourceCount;
            lastObservedResourceCount = current;
            lastTreeProgressMillis = System.currentTimeMillis();
            treeInteractionIssued = true;
            return;
        }

        if (current < lastObservedResourceCount) {
            int lost = lastObservedResourceCount - current;
            int ignored = Math.min(lost, suppressedResourceLoss);
            suppressedResourceLoss -= ignored;
            int countableLoss = lost - ignored;

            if (countLossAsBurned && countableLoss > 0) {
                resourcesBurned += countableLoss;
                burnCycleActive = true;
                burningActive = true;
                campfireInteractionIssued = true;
                lastBurnProgressMillis = System.currentTimeMillis();
            }

            lastObservedResourceCount = current;
        }
    }

    private void clickSelectedTreeDirect() {
        clickSelectedTreeDirect(false);
    }

    /**
     * Select/click the nearest currently-live object for the configured tree.
     * forceImmediate=true is used only when the previously clicked scene object
     * has despawned or morphed to another object ID. It intentionally bypasses
     * movement, animation and normal anti-spam click delays.
     */
    private boolean clickSelectedTreeDirect(boolean forceImmediate) {
        synchronized (targetInteractionLock) {
            if (Rs2Bank.isOpen()) {
                return false;
            }

            // A target transition owns the next click until it has either selected a
            // replacement or exhausted its guarded retries. Letting the regular
            // worker click while that handoff is pending races the queued retarget
            // and produces a target -> no-target -> target loop on normal tree
            // depletion.
            if (!forceImmediate && immediateRetargetRequested) {
                status = "Selecting next " + activeTree;
                return false;
            }

            // During an active burn cycle, chopping is forbidden until every log
            // from that cycle has been consumed.
            if (burnModeEnabled && burnCycleActive && Rs2Inventory.hasItem(activeTree.getResourceId())) {
                status = "Finishing " + activeTree.getResourceName() + " first";
                return false;
            }

            // forceImmediate means bypass the ordinary click cooldown after a target
            // morph. It must NOT bypass the player's actual interaction/animation.
            if (isPlayerBusyForAction()) {
                status = forceImmediate ? "Next " + activeTree + " ready" : "Chopping " + activeTree;
                return false;
            }

            long now = System.currentTimeMillis();
            if (treeInteractionIssued) {
                // Keep trusting the existing chop interaction while it is producing
                // resources. Only recover by re-clicking after a long idle/no-progress
                // window or after the target object is explicitly invalidated.
                if (!forceImmediate && now - lastTreeProgressMillis < 7000L) {
                    status = "Chopping " + activeTree;
                    return false;
                }
                treeInteractionIssued = false;
            }

            if (!forceImmediate && now - lastTreeClickMillis < 1800L) {
                return false;
            }

            Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache()
                    .query()
                    .withName(activeTree.getObjectName())
                    .nearestOnClientThread();

            if (tree == null) {
                status = "No " + activeTree + " loaded";
                return false;
            }

            if (forceImmediate
                    && activeTargetLocation != null
                    && activeTargetLocation.equals(tree.getWorldLocation())
                    && activeTargetObjectId == tree.getId()) {
                return false;
            }

            status = forceImmediate ? "Selecting next " + activeTree : "Clicking " + activeTree;

            if (tree.click(activeTree.getAction())) {
                activeTargetLocation = tree.getWorldLocation();
                activeTargetObjectId = tree.getId();
                immediateRetargetRequested = false;
                immediateRetargetAttempts = 0;
                treeInteractionIssued = true;
                lastTreeClickMillis = now;
                lastTreeProgressMillis = now;
                status = "Chopping " + activeTree;
                return true;
            }

            status = activeTree + " click failed";
            return false;
        }
    }

    public void notifyGameObjectSpawned(GameObject object) {
        if (object == null) {
            return;
        }
        WorldPoint normalizedLocation = normalizeGameObjectLocation(object);
        notifyTargetObjectTransition(normalizedLocation, object.getId(), false);
        notifyCampfireObjectTransition(normalizedLocation, object.getId(), false);
    }

    public void notifyGameObjectDespawned(GameObject object) {
        if (object == null) {
            return;
        }
        WorldPoint normalizedLocation = normalizeGameObjectLocation(object);
        notifyTargetObjectTransition(normalizedLocation, object.getId(), true);
        notifyCampfireObjectTransition(normalizedLocation, object.getId(), true);
    }

    /**
     * Rs2TileObjectModel normalizes multi-tile GameObjects to their scene-min tile.
     * The clicked target is stored using that model location, so event locations
     * must use the same normalization or large trees will never compare equal.
     */
    private WorldPoint normalizeGameObjectLocation(GameObject object) {
        if (object == null) {
            return null;
        }
        try {
            return new Rs2TileObjectModel(object).getWorldLocation();
        } catch (Exception ex) {
            return object.getWorldLocation();
        }
    }

    /**
     * Fallback for tree types whose depletion/morph does not arrive as a matching
     * spawn/despawn pair. If the exact ID+normalized-location object we clicked is
     * no longer in the tile-object cache, invalidate it immediately.
     */
    private void validateActiveTreeTargetLiveness() {
        if (!sessionStarted
                || activeTargetLocation == null
                || activeTargetObjectId < 0
                || immediateRetargetRequested
                || Rs2Bank.isOpen()
                || plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
            return;
        }

        // Do not start a new chop while burn mode owns the inventory.
        if (burnModeEnabled && burnCycleActive) {
            return;
        }

        try {
            Rs2TileObjectModel liveTarget = Microbot.getRs2TileObjectCache()
                    .query()
                    .where(o -> o.getId() == activeTargetObjectId)
                    .nearest(activeTargetLocation, 2);

            boolean exactTargetStillLive = liveTarget != null
                    && activeTargetObjectId == liveTarget.getId()
                    && activeTargetLocation.equals(liveTarget.getWorldLocation());

            if (!exactTargetStillLive) {
                immediateRetargetRequested = true;
                immediateRetargetAttempts = 0;
                treeInteractionIssued = false;
                lastTreeClickMillis = 0L;
                status = "Target gone - selecting next " + activeTree;
                queueImmediateRetarget(0L);
            }
        } catch (Exception ex) {
            // Cache reads are only a fallback. Event-driven retargeting remains active
            // if the cache is temporarily between scene updates.
        }
    }

    private void notifyTargetObjectTransition(WorldPoint location, int objectId, boolean despawned) {
        WorldPoint targetLocation = activeTargetLocation;
        int targetId = activeTargetObjectId;

        if (!sessionStarted
                || targetLocation == null
                || location == null
                || !targetLocation.equals(location)
                || targetId < 0) {
            return;
        }

        boolean currentTargetRemoved = despawned && objectId == targetId;
        boolean sameTileMorphedToDifferentId = !despawned && objectId != targetId;
        if (!currentTargetRemoved && !sameTileMorphedToDifferentId) {
            return;
        }

        // A spawn/despawn event is an authoritative postcondition for the click that
        // selected this tree: it is no longer a valid target. Keep its identity only
        // until the queued handoff can replace it; clearing it first exposes an
        // observable no-target state and lets the regular worker race the retarget.
        immediateRetargetRequested = true;
        immediateRetargetAttempts = 0;
        treeInteractionIssued = false;
        lastTreeClickMillis = 0L;
        status = "Target changed - selecting next " + activeTree;
        queueImmediateRetarget(0L);
    }

    private void queueImmediateRetarget(long delayMillis) {
        if (!sessionStarted || !immediateRetargetRequested) {
            return;
        }

        if (!immediateRetargetQueued.compareAndSet(false, true)) {
            return;
        }

        scheduledExecutorService.schedule(() -> {
            boolean retry = false;
            try {
                if (!sessionStarted || !immediateRetargetRequested || !Microbot.isLoggedIn()) {
                    return;
                }

                if (Rs2Bank.isOpen() || plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
                    return;
                }

                if (burnModeEnabled && (burnCycleActive || Rs2Inventory.isFull())) {
                    immediateRetargetRequested = false;
                    return;
                }

                // Select the replacement immediately, but do not click through an
                // existing player interaction. Busy time does not consume retries.
                if (isPlayerBusyForAction()) {
                    retry = true;
                } else {
                    immediateRetargetAttempts++;
                    boolean clicked = clickSelectedTreeDirect(true);
                    retry = !clicked && immediateRetargetRequested && immediateRetargetAttempts < 8;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace("KspWillowChopperScript immediate retarget", ex);
            } finally {
                immediateRetargetQueued.set(false);
                if (retry) {
                    queueImmediateRetarget(80L);
                } else if (immediateRetargetAttempts >= 8) {
                    immediateRetargetRequested = false;
                }
            }
        }, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
    }

    private void notifyCampfireObjectTransition(WorldPoint location, int objectId, boolean despawned) {
        WorldPoint targetLocation = activeCampfireLocation;
        int targetId = activeCampfireObjectId;

        if (!sessionStarted
                || !burnModeEnabled
                || !burnCycleActive
                || targetLocation == null
                || location == null
                || !targetLocation.equals(location)
                || targetId < 0) {
            return;
        }

        boolean currentCampfireRemoved = despawned && objectId == targetId;
        boolean sameTileMorphedToDifferentId = !despawned && objectId != targetId;
        if (!currentCampfireRemoved && !sameTileMorphedToDifferentId) {
            return;
        }

        invalidatedCampfireLocation = targetLocation;
        invalidatedCampfireObjectId = targetId;
        immediateCampfireRetargetRequested = true;
        immediateCampfireRetargetAttempts = 0;

        if (currentCampfireRemoved) {
            // The exact campfire we were feeding disappeared. Clear the stale
            // Make-X state now; otherwise recent XP/animation timestamps can keep
            // isCampfireProcessingActive() true after the fire is already gone.
            campfireLostConfirmed = true;
            burningActive = false;
            campfireInteractionIssued = false;
            campfireNearby = false;
            clearActiveCampfireTarget();
            status = "Campfire disappeared - recovering";
        } else {
            campfireLostConfirmed = false;
            status = "Campfire ID changed - checking replacement";
        }
        queueImmediateCampfireRetarget(20L);
    }

    private void queueImmediateCampfireRetarget(long delayMillis) {
        if (!sessionStarted || !burnModeEnabled || !burnCycleActive || !immediateCampfireRetargetRequested) {
            return;
        }

        if (!immediateCampfireRetargetQueued.compareAndSet(false, true)) {
            return;
        }

        scheduledExecutorService.schedule(() -> {
            boolean retry = false;
            try {
                int resourceId = activeTree.getResourceId();
                if (!sessionStarted
                        || !burnModeEnabled
                        || !burnCycleActive
                        || !immediateCampfireRetargetRequested
                        || !Microbot.isLoggedIn()) {
                    return;
                }

                if (Rs2Bank.isOpen() || plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
                    return;
                }

                if (!Rs2Inventory.hasItem(resourceId)) {
                    immediateCampfireRetargetRequested = false;
                    campfireLostConfirmed = false;
                    return;
                }

                // A confirmed despawn/burn-out must not wait on the old processing
                // timeout. Allow two very short scene-cache refresh attempts, then
                // fall back to creating a new fire.
                if (campfireLostConfirmed) {
                    Rs2TileObjectModel replacement = findCampfire(Rs2Player.getWorldLocation(), 15);
                    if (replacement != null) {
                        activeCampfireLocation = replacement.getWorldLocation();
                        activeCampfireObjectId = replacement.getId();
                        campfireNearby = true;
                        campfireLostConfirmed = false;
                        immediateCampfireRetargetRequested = false;
                        immediateCampfireRetargetAttempts = 0;
                        startCampfireBurn(replacement, true);
                    } else {
                        immediateCampfireRetargetAttempts++;
                        if (immediateCampfireRetargetAttempts < 3) {
                            retry = true;
                        } else {
                            immediateCampfireRetargetRequested = false;
                            burningActive = false;
                            campfireInteractionIssued = false;
                            clearActiveCampfireTarget();
                            status = "Campfire gone - creating another";
                            // Keep campfireLostConfirmed true. If the player is still
                            // finishing an animation, handleBurnMode retries creation
                            // on the next normal 300 ms cycle.
                            createCampfire();
                        }
                    }
                    return;
                }

                Rs2TileObjectModel replacement = findCampfire(Rs2Player.getWorldLocation(), 15);
                if (replacement != null
                        && !(invalidatedCampfireLocation != null
                        && invalidatedCampfireLocation.equals(replacement.getWorldLocation())
                        && invalidatedCampfireObjectId == replacement.getId())) {
                    activeCampfireLocation = replacement.getWorldLocation();
                    activeCampfireObjectId = replacement.getId();
                    campfireNearby = true;

                    if (isCampfireProcessingActive()) {
                        immediateCampfireRetargetRequested = false;
                        immediateCampfireRetargetAttempts = 0;
                        status = "Campfire changed - continuing burn";
                    } else {
                        immediateCampfireRetargetAttempts++;
                        boolean restarted = startCampfireBurn(replacement, true);
                        retry = !restarted && immediateCampfireRetargetRequested && immediateCampfireRetargetAttempts < 8;
                    }
                } else if (isCampfireProcessingActive()) {
                    // Existing Make-X is still consuming logs. Wait for it to finish
                    // or stall before deciding the campfire is truly gone.
                    retry = true;
                } else {
                    immediateCampfireRetargetAttempts++;
                    if (immediateCampfireRetargetAttempts < 6) {
                        retry = true;
                    } else {
                        immediateCampfireRetargetRequested = false;
                        clearActiveCampfireTarget();
                        burningActive = false;
                        campfireInteractionIssued = false;
                        status = "Campfire gone - creating another";
                        createCampfire();
                    }
                }
            } catch (Exception ex) {
                Microbot.logStackTrace("KspWillowChopperScript campfire retarget", ex);
            } finally {
                immediateCampfireRetargetQueued.set(false);
                if (retry) {
                    queueImmediateCampfireRetarget(100L);
                } else if (immediateCampfireRetargetAttempts >= 8) {
                    immediateCampfireRetargetRequested = false;
                }
            }
        }, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
    }

    /** Called when the game explicitly reports that the fire burned out. */
    public void notifyFireBurnedOut() {
        if (!sessionStarted || !burnModeEnabled || !burnCycleActive) {
            return;
        }

        campfireLostConfirmed = true;
        burningActive = false;
        campfireInteractionIssued = false;
        campfireNearby = false;
        immediateCampfireRetargetRequested = true;
        immediateCampfireRetargetAttempts = 0;
        if (activeCampfireLocation != null) {
            invalidatedCampfireLocation = activeCampfireLocation;
            invalidatedCampfireObjectId = activeCampfireObjectId;
        }
        clearActiveCampfireTarget();
        status = "Fire burned out - creating another";
        queueImmediateCampfireRetarget(0L);
    }

    private void clearActiveCampfireTarget() {
        activeCampfireLocation = null;
        activeCampfireObjectId = -1;
    }

    private Rs2TileObjectModel findCampfire(WorldPoint anchor, int radius) {
        if (anchor == null) {
            return null;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Rs2TileObjectModel target = Microbot.getRs2TileObjectCache()
                    .query()
                    .withNameContains("ampfire")
                    .nearest(anchor, radius);

            if (target == null) {
                target = Microbot.getRs2TileObjectCache()
                        .query()
                        .where(o -> o.getId() == FIRE_ID || o.getId() == FIRE_ID_ALT)
                        .nearest(anchor, radius);
            }

            return target;
        }).orElse(null);
    }

    private int skillXp(Skill skill) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getSkillExperience(skill)
        ).orElse(0);
    }

    private int skillLevel(Skill skill) {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().getRealSkillLevel(skill)
        ).orElse(0);
    }

    public String getStatus() { return status; }
    public KspTree getActiveTree() { return activeTree; }
    public boolean isCampfireNearby() { return campfireNearby; }
    public boolean isBurningActive() { return burningActive; }
    public boolean hasSessionStarted() { return sessionStarted && startTimeMillis > 0; }
    public long getRuntimeMillis() { return hasSessionStarted() ? Math.max(0L, System.currentTimeMillis() - startTimeMillis) : 0L; }
    public int getWoodcuttingXpGained() { return Math.max(0, skillXp(Skill.WOODCUTTING) - startWoodcuttingXp); }
    public int getFiremakingXpGained() { return Math.max(0, skillXp(Skill.FIREMAKING) - startFiremakingXp); }
    public int getWoodcuttingLevel() { return skillLevel(Skill.WOODCUTTING); }
    public int getFiremakingLevel() { return skillLevel(Skill.FIREMAKING); }
    public int getWoodcuttingLevelsGained() { return hasSessionStarted() ? Math.max(0, getWoodcuttingLevel() - startWoodcuttingLevel) : 0; }
    public int getFiremakingLevelsGained() { return hasSessionStarted() ? Math.max(0, getFiremakingLevel() - startFiremakingLevel) : 0; }
    public int getResourcesChopped() { return resourcesChopped; }
    public int getResourcesBanked() { return resourcesBanked; }
    public int getResourcesBurned() { return resourcesBurned; }
    public int getCampfiresLit() { return campfiresLit; }
    public int getCurrentResourceCount() { return Rs2Inventory.count(activeTree.getResourceId()); }
}
