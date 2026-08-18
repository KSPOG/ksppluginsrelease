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
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
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

                int wcLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
                if (wcLevel < activeTree.getWoodcuttingLevel()) {
                    status = "Need " + activeTree.getWoodcuttingLevel() + " Woodcutting";
                    return;
                }

                if (!hasAxe()) {
                    status = "No axe equipped/in inventory";
                    return;
                }

                if (config.bankLogs()) {
                    burningActive = false;
                    lastBurnResourceCount = -1;
                    suppressedResourceLoss = 0;
                    campfireNearby = false;

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
        campfireNearby = false;
        lastBurnResourceCount = -1;
        suppressedResourceLoss = 0;
        lastObservedResourceCount = Rs2Inventory.count(activeTree.getResourceId());
        lastTreeClickMillis = 0L;
        activeTargetLocation = null;
        activeTargetObjectId = -1;
        immediateRetargetRequested = false;
        immediateRetargetAttempts = 0;
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
        Rs2TileObjectModel fire = findCampfire(Rs2Player.getWorldLocation(), 15);
        campfireNearby = fire != null;
        trackBurnProgress();

        if (burningActive) {
            if (Rs2Inventory.count(resourceId) == 0) {
                burningActive = false;
                lastBurnResourceCount = -1;
                status = "Burn complete";
                return;
            }

            if (System.currentTimeMillis() - lastBurnProgressMillis < 5000) {
                status = "Burning " + activeTree.getResourceName();
                return;
            }

            fire = findCampfire(Rs2Player.getWorldLocation(), 15);
            if (fire != null) {
                startCampfireBurn(fire);
                return;
            }

            burningActive = false;
            lastBurnResourceCount = -1;
        }

        if (!Rs2Inventory.isFull()) {
            if (fire == null && !Rs2Inventory.hasItem(TINDERBOX_ID) && Rs2Inventory.emptySlotCount() <= 1) {
                ensureTinderbox();
                return;
            }
            clickSelectedTreeDirect();
            return;
        }

        fire = findCampfire(Rs2Player.getWorldLocation(), 15);
        campfireNearby = fire != null;

        if (fire == null) {
            createCampfire();
            return;
        }

        startCampfireBurn(fire);
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

        status = "Creating campfire";
        Rs2Inventory.combine("Tinderbox", activeTree.getResourceName());

        if (Rs2Player.waitForXpDrop(Skill.FIREMAKING, 5000)) {
            trackResourceInventoryChanges(true);
            campfiresLit++;
            campfireNearby = true;
            lastBurnResourceCount = Rs2Inventory.count(resourceId);
            lastBurnProgressMillis = System.currentTimeMillis();
            status = "Campfire created";
            return;
        }

        status = "Repositioning for fire";
        WorldPoint spot = findLightableTile(Rs2Player.getWorldLocation());
        if (spot != null) {
            Rs2Walker.walkTo(spot, 0);
            sleepUntil(() -> !Rs2Player.isMoving(), 3000);
        }
    }

    private void startCampfireBurn(Rs2TileObjectModel fire) {
        int resourceId = activeTree.getResourceId();
        if (fire == null || !Rs2Inventory.hasItem(resourceId)) {
            return;
        }

        int count = Rs2Inventory.count(resourceId);
        status = "Adding " + activeTree.getResourceName() + " to campfire";

        Rs2Inventory.use(resourceId);
        if (!sleepUntil(Rs2Inventory::isItemSelected, 2000)) {
            status = "Selecting " + activeTree.getResourceName();
            return;
        }

        Rs2GameObject.interact(fire);
        if (!sleepUntil(() -> Rs2Widget.getWidget(BURN_INTERFACE_WIDGET) != null, 5000)) {
            status = "Waiting for Burn dialog";
            return;
        }

        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        burningActive = true;
        lastBurnResourceCount = count;
        lastBurnProgressMillis = System.currentTimeMillis();
        status = "Burning " + activeTree.getResourceName();
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
            return;
        }

        if (current < lastObservedResourceCount) {
            int lost = lastObservedResourceCount - current;
            int ignored = Math.min(lost, suppressedResourceLoss);
            suppressedResourceLoss -= ignored;
            int countableLoss = lost - ignored;

            if (countLossAsBurned && countableLoss > 0) {
                resourcesBurned += countableLoss;
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

            if (!forceImmediate && (Rs2Player.isMoving() || Rs2Player.isAnimating())) {
                status = "Chopping " + activeTree;
                return false;
            }

            if (!forceImmediate && System.currentTimeMillis() - lastTreeClickMillis < 1800) {
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

            // During a morph/despawn callback the tile-object cache can lag the
            // client event by a few milliseconds. Never re-click that stale object;
            // the immediate retarget worker will retry against the refreshed cache.
            if (forceImmediate
                    && activeTargetLocation != null
                    && activeTargetLocation.equals(tree.getWorldLocation())
                    && activeTargetObjectId == tree.getId()) {
                return false;
            }

            status = forceImmediate
                    ? "Selecting next " + activeTree
                    : "Clicking " + activeTree;

            if (tree.click(activeTree.getAction())) {
                activeTargetLocation = tree.getWorldLocation();
                activeTargetObjectId = tree.getId();
                immediateRetargetRequested = false;
                immediateRetargetAttempts = 0;
                lastTreeClickMillis = System.currentTimeMillis();

                // Do not block waiting for animation here. The normal loop observes
                // animation state, while object morph events remain free to trigger
                // an immediate replacement click.
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
        notifyTargetObjectTransition(object.getWorldLocation(), object.getId(), false);
    }

    public void notifyGameObjectDespawned(GameObject object) {
        if (object == null) {
            return;
        }
        notifyTargetObjectTransition(object.getWorldLocation(), object.getId(), true);
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

        immediateRetargetRequested = true;
        immediateRetargetAttempts = 0;
        lastTreeClickMillis = 0L;
        status = "Target changed - selecting next " + activeTree;
        queueImmediateRetarget(20L);
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

                // Full inventory must still be handled by bank/burn mode rather than
                // blindly clicking another tree. Forestry blocking events also retain
                // priority over the normal chopping target.
                if (Rs2Bank.isOpen()
                        || Rs2Inventory.isFull()
                        || plugin.getCurrentForestryEvent() != KspForestryEvent.NONE) {
                    return;
                }

                immediateRetargetAttempts++;
                boolean clicked = clickSelectedTreeDirect(true);
                retry = !clicked && immediateRetargetRequested && immediateRetargetAttempts < 8;
            } catch (Exception ex) {
                Microbot.logStackTrace("KspWillowChopperScript immediate retarget", ex);
            } finally {
                immediateRetargetQueued.set(false);
                if (retry) {
                    queueImmediateRetarget(60L);
                } else if (immediateRetargetAttempts >= 8) {
                    // Hand control back to the normal 300 ms loop if no replacement
                    // object is actually loaded after the short immediate retry burst.
                    immediateRetargetRequested = false;
                }
            }
        }, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
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

    private WorldPoint findLightableTile(WorldPoint from) {
        if (from == null) {
            return null;
        }

        int[][] offsets = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1},
                {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
        };

        for (int[] offset : offsets) {
            WorldPoint candidate = new WorldPoint(
                    from.getX() + offset[0],
                    from.getY() + offset[1],
                    from.getPlane()
            );

            if (Rs2Tile.isWalkable(candidate)) {
                return candidate;
            }
        }

        return null;
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
