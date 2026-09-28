package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.woodcuttingscript;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspBankMode;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.equiplevels.AxeEquip;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.levelreqwc.WoodCuttingReq;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas.TreeAreas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treelevel.TreeLevel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WoodCuttingScript extends Script {
    private static final Logger log = LoggerFactory.getLogger(WoodCuttingScript.class);

    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3000;
    private static final int OBJECT_INTERACTION_COOLDOWN_MS = 2_500;
    private static final int TREE_SEARCH_PADDING_TILES = 8;
    private static final int OUT_OF_AREA_TREE_FALLBACK_RADIUS = 4;
    private static final int MID_TIER_RANDOM_MAX_LEVEL = 60;
    private static final int WILLOW_SAFE_COMBAT_LEVEL = 16;
    private static final int DRAYNOR_OAK_MIN_COMBAT_LEVEL = 53;

    private static final List<String> AXE_NAMES = Buy.AXE_NAME_LIST;
    private static final List<TreeAreas> OAK_AREAS = Arrays.asList(
            TreeAreas.OAK_TREE_DRAYNOR,
            TreeAreas.VCASTLE_OAKS,
            TreeAreas.VWEST_OAKS,
            TreeAreas.VEAST_OAKS
    );
    private static final List<TreeAreas> LOW_COMBAT_OAK_AREAS = Arrays.asList(
            TreeAreas.VCASTLE_OAKS,
            TreeAreas.VWEST_OAKS,
            TreeAreas.VEAST_OAKS
    );

    private TreeAreas targetArea = TreeAreas.REGULAR_TREE_VARROCK_WEST;
    private boolean startingTargetTreeInitialized;
    private TreeLevel randomMidTierTree;
    private TreeAreas randomOakArea;
    private boolean debugLogging;
    private boolean progressiveWoodcutting = true;
    private long lastWebWalkAtMs;
    private long lastObjectInteractionAtMs;
    private boolean walkingToTargetArea;

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public void setProgressiveWoodcutting(boolean progressiveWoodcutting) {
        this.progressiveWoodcutting = progressiveWoodcutting;
    }

    public boolean run(TreeAreas area) {
        this.shutdown();

        this.targetArea = area;
        this.startingTargetTreeInitialized = false;
        this.randomMidTierTree = null;
        this.randomOakArea = null;

        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn()) {
                return;
            }

            int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
            int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);

            this.initializeStartingTargetTree(woodcuttingLevel);

            TreeAreas desiredArea = this.progressiveWoodcutting
                    ? this.resolveTargetArea(woodcuttingLevel)
                    : this.targetArea;
            if (desiredArea != this.targetArea) {
                this.targetArea = desiredArea;
                this.clearTargetAreaWalkIfNeeded();
                this.debug("Switching woodcutting area to {} for woodcutting level {}",
                        this.targetArea.getDisplayName(),
                        woodcuttingLevel);
            }

            TreeLevel targetTree = this.getTargetTreeLevel(woodcuttingLevel);

            KspTaskDebug.throttled(log, this.debugLogging, "Woodcutting", "loop", 5_000L,
                    "loop | level={} attack={} area={} targetTree={} player={} moving={} animating={} interacting={} invFull={} bankOpen={} walkerTarget={}",
                    woodcuttingLevel,
                    attackLevel,
                    this.targetArea.getDisplayName(),
                    targetTree != null ? targetTree.getDisplayName() : "none",
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Inventory.isFull(),
                    Rs2Bank.isOpen(),
                    Rs2Walker.getCurrentTarget());

            if (Rs2Inventory.isFull()) {
                this.debug("Inventory full; banking logs | player={} area={} equippedAxeInInv={}",
                        Rs2Player.getWorldLocation(),
                        this.targetArea.getDisplayName(),
                        this.resolveInventoryAxeToKeep(woodcuttingLevel));
                this.bankLogsOnly(woodcuttingLevel);
                return;
            }

            if (!this.upgradeAxe(woodcuttingLevel, attackLevel)) {
                return;
            }

            if (!this.hasAnyAxeEquippedOrInInventory()) {
                this.debug("No axe available in inventory or equipment, waiting before proceeding");
                return;
            }

            if (!this.ensureInTargetArea()) {
                return;
            }

            if (!this.isIdleInTargetArea()) {
                return;
            }

            this.chopForCurrentLevel(woodcuttingLevel);

        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean upgradeAxe(int woodcuttingLevel, int attackLevel) {
        WoodCuttingReq bestWoodcuttingReq = WoodCuttingReq.bestForWoodcuttingLevel(woodcuttingLevel);
        String targetAxeName = this.resolveDesiredAxe(bestWoodcuttingReq);

        if (targetAxeName == null) {
            return true;
        }

        String activeAxeName = this.resolveBestOwnedAxeName(targetAxeName);

        if (activeAxeName == null) {
            this.ensureInventoryTabOpen();

            if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank()) {
                return false;
            }

            activeAxeName = this.resolveBestOwnedAxeName(targetAxeName);

            if (activeAxeName == null) {
                this.debug("No eligible axe available up to target {}", targetAxeName);
                Rs2Bank.closeBank();
                return true;
            }
        }

        WoodCuttingReq activeAxeReq = this.resolveWoodcuttingReq(activeAxeName);
        boolean canEquipActiveAxe = activeAxeReq != null && this.canEquipDesiredAxe(activeAxeReq, attackLevel);

        if (!Rs2Equipment.isWearing(activeAxeName) && !Rs2Inventory.hasItem(activeAxeName)) {
            if (!Rs2Bank.isOpen()) {
                this.ensureInventoryTabOpen();

                if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank()) {
                    return false;
                }
            }

            if (Rs2Bank.isOpen() && Rs2Bank.count(activeAxeName) > 0) {
                if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
                    return false;
                }

                if (!KspBankMode.ensureWithdrawAsItem()) {
                    this.debug("Waiting for withdraw-as-item mode before withdrawing {}", activeAxeName);
                    return false;
                }

                String axeToWithdraw = activeAxeName;
                Rs2Bank.withdrawOne(activeAxeName);
                WoodCuttingScript.sleepUntil(() -> Rs2Inventory.hasItem(axeToWithdraw), 3000);
            }
        }

        if (canEquipActiveAxe
                && Rs2Inventory.hasItem(activeAxeName)
                && !Rs2Equipment.isWearing(activeAxeName)) {

            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                return false;
            }

            String axeToWield = activeAxeName;
            Rs2Inventory.wield(activeAxeName);
            WoodCuttingScript.sleepUntil(() -> Rs2Equipment.isWearing(axeToWield), 2000);
        }

        if (Rs2Bank.isOpen()) {
            if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
                return false;
            }

            this.depositOutdatedAxes(activeAxeName);

            if (!this.hasOutdatedAxeInInventory(activeAxeName)) {
                Rs2Bank.closeBank();
            }

            return false;
        }

        return Rs2Equipment.isWearing(activeAxeName) || Rs2Inventory.hasItem(activeAxeName);
    }

    private void depositOutdatedAxes(String desiredAxeName) {
        for (String axeName : AXE_NAMES) {
            if (axeName.equalsIgnoreCase(desiredAxeName)) {
                continue;
            }

            if (!Rs2Inventory.hasItem(axeName)) {
                continue;
            }

            Rs2Bank.depositAll(axeName);
        }
    }

    private boolean hasOutdatedAxeInInventory(String desiredAxeName) {
        for (String axeName : AXE_NAMES) {
            if (axeName.equalsIgnoreCase(desiredAxeName)) {
                continue;
            }

            if (Rs2Inventory.hasItem(axeName)) {
                return true;
            }
        }

        return false;
    }

    private String resolveDesiredAxe(WoodCuttingReq woodCuttingReq) {
        return woodCuttingReq.getDisplayName();
    }

    private String resolveBestOwnedAxeName(String targetAxeName) {
        int targetIndex = AXE_NAMES.indexOf(targetAxeName);

        if (targetIndex < 0) {
            return null;
        }

        for (int index = targetIndex; index >= 0; index--) {
            String axeName = AXE_NAMES.get(index);

            if (Rs2Equipment.isWearing(axeName)
                    || Rs2Inventory.hasItem(axeName)
                    || Rs2Bank.isOpen() && Rs2Bank.count(axeName) > 0) {
                return axeName;
            }
        }

        return null;
    }

    private WoodCuttingReq resolveWoodcuttingReq(String axeName) {
        if (axeName == null) {
            return null;
        }

        return Arrays.stream(WoodCuttingReq.values())
                .filter(req -> axeName.equalsIgnoreCase(req.getDisplayName()))
                .findFirst()
                .orElse(null);
    }

    private boolean hasAnyAxeEquippedOrInInventory() {
        for (String axeName : AXE_NAMES) {
            if (Rs2Equipment.isWearing(axeName) || Rs2Inventory.hasItem(axeName)) {
                return true;
            }
        }

        return false;
    }

    private boolean canEquipDesiredAxe(WoodCuttingReq woodCuttingReq, int attackLevel) {
        AxeEquip equipRequirement = AxeEquip.valueOf(woodCuttingReq.name());
        return attackLevel >= equipRequirement.getRequiredAttackLevel();
    }

    private boolean ensureInTargetArea() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null) {
            this.debug("Cannot verify woodcutting area; player location is null");
            return false;
        }

        /*
         * Important fix:
         * If the player is already inside the current task area,
         * clear any leftover walker route immediately and allow object interaction.
         */
        if (this.targetArea.contains(playerLocation)) {
            this.clearTargetAreaWalkIfNeeded();
            Microbot.status = "Inside woodcutting area";
            return true;
        }

        /*
         * Only keep waiting for movement if we are actually outside the task area.
         * This prevents the script from walking again while already near valid task objects.
         */
        if (Rs2Player.isMoving()) {
            KspTaskDebug.throttled(log, this.debugLogging, "Woodcutting", "walk-moving", 3_000L,
                    "waiting for walker | player={} targetArea={} walkerTarget={}",
                    playerLocation,
                    this.targetArea.getDisplayName(),
                    Rs2Walker.getCurrentTarget());
            return false;
        }

        Microbot.status = "Walking to woodcutting area";

        if (KspWalkerGuard.walkToDestination(
                "Woodcutting:target-area",
                this::getAreaCenter,
                this.targetArea::contains,
                1,
                WEB_WALK_COOLDOWN_MS)) {
            this.lastWebWalkAtMs = System.currentTimeMillis();
            this.walkingToTargetArea = true;

            this.debug("Requested woodcutting area walk | player={} walkerTarget={} area={}",
                    playerLocation,
                    Rs2Walker.getCurrentTarget(),
                    this.targetArea.getDisplayName());
        }
        return false;
    }

    private void clearTargetAreaWalkIfNeeded() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null) {
            return;
        }

        if (!this.targetArea.contains(playerLocation)) {
            return;
        }

        WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();

        if (walkerTarget != null || this.walkingToTargetArea) {
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_woodcutting_already_in_area");
            KspWalkerGuard.clear("Woodcutting:target-area");

            this.debug("Cleared woodcutting walker route because player is already inside task area | player={} area={} oldWalkerTarget={}",
                    playerLocation,
                    this.targetArea.getDisplayName(),
                    walkerTarget);
        }

        this.walkingToTargetArea = false;
        KspWalkerGuard.clear("Woodcutting:target-area");
        this.lastWebWalkAtMs = 0L;
    }

    private WorldPoint getAreaCenter() {
        int centerX = (this.targetArea.getSouthWest().getX() + this.targetArea.getNorthEast().getX()) / 2;
        int centerY = (this.targetArea.getSouthWest().getY() + this.targetArea.getNorthEast().getY()) / 2;
        int plane = this.targetArea.getSouthWest().getPlane();

        return new WorldPoint(centerX, centerY, plane);
    }

    private void ensureInventoryTabOpen() {
        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY) {
            Rs2Tab.switchTo(InterfaceTab.INVENTORY);
            WoodCuttingScript.sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 1200);
        }
    }

    private void bankLogsOnly(int woodcuttingLevel) {
        this.ensureInventoryTabOpen();

        boolean useLocalWillowBank = this.targetArea == TreeAreas.WILLOW_TREES_DRAYNOR
                && this.targetArea.contains(Rs2Player.getWorldLocation());

        if (useLocalWillowBank) {
            KspWalkerGuard.clearReachedDestination(
                    "Woodcutting:target-area",
                    "ksp_woodcutting_willow_local_bank");
            KspWalkerGuard.clearActiveWalker("ksp_woodcutting_willow_local_bank");
        }

        boolean bankOpened = useLocalWillowBank
                ? Rs2Bank.openBank()
                : Rs2Bank.walkToBankAndUseBank() || Rs2Bank.openBank();

        if (!bankOpened) {
            return;
        }

        if (Rs2Bank.isOpen()) {
            if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
                return;
            }

            String axeToKeep = this.resolveInventoryAxeToKeep(woodcuttingLevel);

            if (axeToKeep != null) {
                Rs2Bank.depositAllExcept(axeToKeep);
            } else {
                Rs2Bank.depositAll();
            }

            WoodCuttingScript.sleep(300);
            Rs2Bank.closeBank();
            WoodCuttingScript.sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);

            if (useLocalWillowBank) {
                this.interactWithBestWillowAfterBank();
            }
        }
    }

    private void interactWithBestWillowAfterBank() {
        if (Rs2Bank.isOpen() || Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return;
        }

        int searchRadius = this.getAreaSearchRadius() + TREE_SEARCH_PADDING_TILES;
        Rs2TileObjectModel willow = this.findMatchingTree(
                playerLocation,
                searchRadius,
                TreeLevel.WILLOW,
                false);

        if (willow == null) {
            this.debug("No reachable Willow found after local Draynor bank deposit | player={}",
                    playerLocation);
            return;
        }

        this.lastObjectInteractionAtMs = System.currentTimeMillis();
        Microbot.status = "Chopping " + willow.getName();
        boolean clicked = willow.click("Chop down");
        this.debug("Direct Willow interaction after banking | clicked={} tree={} id={} loc={} player={}",
                clicked,
                willow.getName(),
                willow.getId(),
                willow.getWorldLocation(),
                playerLocation);

        if (clicked) {
            WoodCuttingScript.sleepUntil(
                    () -> Rs2Player.isMoving()
                            || Rs2Player.isAnimating()
                            || Rs2Player.isInteracting(),
                    1_200);
        }
    }

    private String resolveInventoryAxeToKeep(int woodcuttingLevel) {
        String targetAxeName = this.resolveDesiredAxe(WoodCuttingReq.bestForWoodcuttingLevel(woodcuttingLevel));
        int targetIndex = AXE_NAMES.indexOf(targetAxeName);

        if (targetIndex < 0) {
            return null;
        }

        for (int index = targetIndex; index >= 0; index--) {
            String axeName = AXE_NAMES.get(index);

            if (Rs2Equipment.isWearing(axeName)) {
                return null;
            }
        }

        for (int index = targetIndex; index >= 0; index--) {
            String axeName = AXE_NAMES.get(index);

            if (Rs2Inventory.hasItem(axeName)) {
                return axeName;
            }
        }

        return null;
    }

    private void chopForCurrentLevel(int woodcuttingLevel) {
        if (!this.isIdleInTargetArea()) {
            KspTaskDebug.throttled(log, this.debugLogging, "Woodcutting", "not-idle", 2_000L,
                    "waiting for idle before chopping | player={} moving={} animating={} interacting={} area={}",
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    this.targetArea.getDisplayName());
            return;
        }

        if (System.currentTimeMillis() - this.lastObjectInteractionAtMs < OBJECT_INTERACTION_COOLDOWN_MS) {
            KspTaskDebug.throttled(log, this.debugLogging, "Woodcutting", "interaction-cooldown", 2_000L,
                    "interaction cooldown active | elapsed={}ms cooldown={}ms",
                    System.currentTimeMillis() - this.lastObjectInteractionAtMs,
                    OBJECT_INTERACTION_COOLDOWN_MS);
            return;
        }

        Rs2TileObjectModel targetTree = this.findNearestTreeInTargetArea(woodcuttingLevel);

        if (targetTree == null) {
            Microbot.status = "No reachable tree found";
            return;
        }

        if (!this.isIdleInTargetArea()) {
            this.debug("Tree candidate found but player stopped being idle | tree={} id={} loc={} moving={} animating={} interacting={}",
                    targetTree.getName(),
                    targetTree.getId(),
                    targetTree.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting());
            return;
        }

        this.lastObjectInteractionAtMs = System.currentTimeMillis();

        this.debug("Attempting tree interaction | expected={} objectName={} id={} loc={} reachable={} player={} distance={} hasChopAction={} insideArea={}",
                this.getTargetTreeLevel(woodcuttingLevel).getDisplayName(),
                targetTree.getName(),
                targetTree.getId(),
                targetTree.getWorldLocation(),
                targetTree.isReachable(),
                Rs2Player.getWorldLocation(),
                Rs2Player.getWorldLocation() != null
                        ? Rs2Player.getWorldLocation().distanceTo(targetTree.getWorldLocation())
                        : -1,
                hasObjectAction(targetTree, "Chop down"),
                this.targetArea.contains(targetTree.getWorldLocation()));

        boolean interactionStarted = targetTree.click("Chop down");

        this.debug("Tree interaction result | clicked={} objectName={} id={} loc={} player={} moving={} animating={} interacting={}",
                interactionStarted,
                targetTree.getName(),
                targetTree.getId(),
                targetTree.getWorldLocation(),
                Rs2Player.getWorldLocation(),
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting());

        if (interactionStarted) {
            Microbot.status = "Chopping " + targetTree.getName();

            boolean activityStarted = WoodCuttingScript.sleepUntil(
                    () -> Rs2Player.isAnimating() || Rs2Player.isInteracting(),
                    1200
            );

            this.debug("Tree post-click wait | activityStarted={} moving={} animating={} interacting={} player={}",
                    activityStarted,
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Player.getWorldLocation());
        } else {
            this.debug("Tree interaction was not accepted by tile object API | objectName={} id={} loc={}",
                    targetTree.getName(),
                    targetTree.getId(),
                    targetTree.getWorldLocation());
        }
    }

    private boolean isIdleInTargetArea() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        return playerLocation != null
                && this.targetArea.contains(playerLocation)
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating()
                && !Rs2Player.isInteracting();
    }

    private Rs2TileObjectModel findNearestTreeInTargetArea(int woodcuttingLevel) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint searchCenter = this.getAreaCenter();

        if (playerLocation == null || searchCenter == null) {
            return null;
        }

        TreeLevel treeLevel = this.getTargetTreeLevel(woodcuttingLevel);
        int searchRadius = this.getAreaSearchRadius() + TREE_SEARCH_PADDING_TILES;

        /*
         * Prefer objects inside the actual current task area first.
         */
        Rs2TileObjectModel tree = this.findMatchingTree(searchCenter, searchRadius, treeLevel, true);

        if (tree != null) {
            this.debug("Tree candidate selected | expected={} objectName={} id={} loc={} reachable={} insideArea={} searchCenter={} radius={}",
                    treeLevel.getDisplayName(),
                    tree.getName(),
                    tree.getId(),
                    tree.getWorldLocation(),
                    tree.isReachable(),
                    this.targetArea.contains(tree.getWorldLocation()),
                    searchCenter,
                    searchRadius);
            return tree;
        }

        /*
         * Fallback: allow nearby objects only if no inside-area object was found.
         */
        tree = this.findMatchingTree(searchCenter, searchRadius, treeLevel, false);

        if (tree == null) {
            this.debug("No reachable {} found in or near {}",
                    treeLevel.getObjectCompositionName(),
                    this.targetArea.getDisplayName());
        }

        return tree;
    }

    private Rs2TileObjectModel findMatchingTree(
            WorldPoint searchCenter,
            int searchRadius,
            TreeLevel treeLevel,
            boolean mustBeInsideArea
    ) {
        return Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .within(searchCenter, searchRadius)
                .where(candidate -> candidate != null
                        && candidate.getWorldLocation() != null
                        && (mustBeInsideArea
                        ? this.targetArea.contains(candidate.getWorldLocation())
                        : this.isNearTargetArea(candidate.getWorldLocation(), OUT_OF_AREA_TREE_FALLBACK_RADIUS))
                        && this.isTargetTree(candidate, treeLevel)
                        && candidate.isReachable()
                        && hasObjectAction(candidate, "Chop down"))
                .nearestOnClientThread();
    }

    private boolean isNearTargetArea(WorldPoint point, int radius) {
        if (point == null || point.getPlane() != this.targetArea.getSouthWest().getPlane()) {
            return false;
        }

        if (this.targetArea.contains(point)) {
            return true;
        }

        int minX = this.targetArea.getSouthWest().getX() - radius;
        int maxX = this.targetArea.getNorthEast().getX() + radius;
        int minY = this.targetArea.getSouthWest().getY() - radius;
        int maxY = this.targetArea.getNorthEast().getY() + radius;

        return point.getX() >= minX
                && point.getX() <= maxX
                && point.getY() >= minY
                && point.getY() <= maxY;
    }

    private boolean isTargetTree(Rs2TileObjectModel tree, TreeLevel treeLevel) {
        if (tree == null || treeLevel == null || tree.getName() == null) {
            return false;
        }

        String treeName = tree.getName().toLowerCase(Locale.ENGLISH);
        String objectName = treeLevel.getObjectCompositionName().toLowerCase(Locale.ENGLISH);
        String displayName = treeLevel.getDisplayName().toLowerCase(Locale.ENGLISH);

        if (treeLevel == TreeLevel.TREE) {
            return treeName.equals(objectName) || treeName.equals(displayName);
        }

        return treeName.equals(objectName)
                || treeName.equals(displayName)
                || treeName.equals(displayName + " tree");
    }

    private static boolean hasObjectAction(Rs2TileObjectModel object, String expectedAction) {
        if (object == null || expectedAction == null) {
            return false;
        }

        ObjectComposition composition = object.getObjectComposition();

        if (composition == null || composition.getActions() == null) {
            return false;
        }

        for (String rawAction : composition.getActions()) {
            if (rawAction == null) {
                continue;
            }

            String action = Rs2UiHelper.stripColTags(rawAction);

            if (expectedAction.equalsIgnoreCase(action)) {
                return true;
            }
        }

        return false;
    }

    private int getAreaSearchRadius() {
        int width = Math.abs(this.targetArea.getNorthEast().getX() - this.targetArea.getSouthWest().getX());
        int height = Math.abs(this.targetArea.getNorthEast().getY() - this.targetArea.getSouthWest().getY());

        return Math.max(width, height) + 2;
    }

    private TreeAreas resolveTargetArea(int woodcuttingLevel) {
        TreeLevel treeLevel = this.getTargetTreeLevel(woodcuttingLevel);

        if (treeLevel == TreeLevel.YEW) {
            this.randomOakArea = null;
            return TreeAreas.YEW_TREE_VARROCK_PALACE;
        }

        if (treeLevel == TreeLevel.WILLOW) {
            this.randomOakArea = null;
            return TreeAreas.WILLOW_TREES_DRAYNOR;
        }

        if (treeLevel == TreeLevel.OAK) {
            return this.resolveRandomOakArea();
        }

        this.randomOakArea = null;
        return TreeAreas.REGULAR_TREE_VARROCK_WEST;
    }

    private TreeAreas resolveRandomOakArea() {
        int combatLevel = this.getCombatLevel();
        if (this.randomOakArea == TreeAreas.OAK_TREE_DRAYNOR
                && combatLevel < DRAYNOR_OAK_MIN_COMBAT_LEVEL) {
            this.randomOakArea = null;
        }

        if (this.randomOakArea == null) {
            List<TreeAreas> eligibleAreas = combatLevel >= DRAYNOR_OAK_MIN_COMBAT_LEVEL
                    ? OAK_AREAS
                    : LOW_COMBAT_OAK_AREAS;
            this.randomOakArea = eligibleAreas.get(
                    ThreadLocalRandom.current().nextInt(eligibleAreas.size()));

            this.debug("Selected oak woodcutting area {} at combat level {}",
                    this.randomOakArea.getDisplayName(),
                    combatLevel);
        }

        return this.randomOakArea;
    }

    private TreeLevel getTargetTreeLevel(int woodcuttingLevel) {
        if (!this.progressiveWoodcutting) {
            if (this.targetArea == TreeAreas.YEW_TREE_VARROCK_PALACE) {
                return TreeLevel.YEW;
            }
            if (this.targetArea == TreeAreas.WILLOW_TREES_DRAYNOR) {
                return TreeLevel.WILLOW;
            }
            if (OAK_AREAS.contains(this.targetArea)) {
                return TreeLevel.OAK;
            }
            return TreeLevel.TREE;
        }

        if (this.startingTargetTreeInitialized && this.randomMidTierTree != null) {
            return this.randomMidTierTree;
        }

        if (woodcuttingLevel >= TreeLevel.YEW.getRequiredWoodcuttingLevel()) {
            List<TreeLevel> levelSixtyOptions = this.canCutWillowsSafely()
                    ? Arrays.asList(TreeLevel.WILLOW, TreeLevel.YEW)
                    : Arrays.asList(TreeLevel.YEW);
            return levelSixtyOptions.get(ThreadLocalRandom.current().nextInt(levelSixtyOptions.size()));
        }

        if (woodcuttingLevel >= TreeLevel.WILLOW.getRequiredWoodcuttingLevel()
                && this.canCutWillowsSafely()) {
            return TreeLevel.WILLOW;
        }

        if (woodcuttingLevel >= TreeLevel.OAK.getRequiredWoodcuttingLevel()) {
            return TreeLevel.OAK;
        }

        return TreeLevel.TREE;
    }

    private boolean shouldRandomizeMidTierTree(int woodcuttingLevel) {
        return woodcuttingLevel >= TreeLevel.WILLOW.getRequiredWoodcuttingLevel()
                && woodcuttingLevel < MID_TIER_RANDOM_MAX_LEVEL
                && this.canCutWillowsSafely();
    }

    private boolean canCutWillowsSafely() {
        int combatLevel = this.getCombatLevel();
        boolean safe = combatLevel >= WILLOW_SAFE_COMBAT_LEVEL;

        if (!safe) {
            KspTaskDebug.throttled(log, this.debugLogging, "Woodcutting", "willow-combat-gate", 10_000L,
                    "skipping willows until combat level {} | currentCombat={}",
                    WILLOW_SAFE_COMBAT_LEVEL,
                    combatLevel);
        }

        return safe;
    }

    private int getCombatLevel() {
        if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
            return 0;
        }

        return Microbot.getClient().getLocalPlayer().getCombatLevel();
    }

    private void initializeStartingTargetTree(int woodcuttingLevel) {
        if (this.startingTargetTreeInitialized) {
            return;
        }

        if (this.shouldRandomizeMidTierTree(woodcuttingLevel)) {
            List<TreeLevel> randomOptions = Arrays.asList(TreeLevel.OAK, TreeLevel.WILLOW);
            this.randomMidTierTree = randomOptions.get(ThreadLocalRandom.current().nextInt(randomOptions.size()));

            this.debug("Selected starting woodcutting tree {} for woodcutting level {}",
                    this.randomMidTierTree.getDisplayName(),
                    woodcuttingLevel);
        } else {
            this.randomMidTierTree = null;
        }

        this.startingTargetTreeInitialized = true;
    }

    private void debug(String message, Object... args) {
        if (this.debugLogging) {
            KspTaskDebug.info(log, true, "Woodcutting", message, args);
        }
    }

    public void shutdown() {
        this.startingTargetTreeInitialized = false;
        this.randomMidTierTree = null;
        this.randomOakArea = null;
        this.lastWebWalkAtMs = 0L;
        this.lastObjectInteractionAtMs = 0L;
        this.walkingToTargetArea = false;
        KspWalkerGuard.clear("Woodcutting:target-area");

        super.shutdown();
    }

    public TreeAreas getTargetArea() {
        return this.targetArea;
    }
}
