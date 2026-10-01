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

    private static final int LOOP_DELAY_MS = 100;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final int OBJECT_INTERACTION_COOLDOWN_MS = 100;
    private static final int OBJECT_INTERACTION_START_TIMEOUT_MS = 1_500;
    private static final long BANK_ACTION_COOLDOWN_MS = 500L;
    private static final long BANK_INVENTORY_ACTION_TIMEOUT_MS = 1_500L;
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
    private long pendingObjectInteractionAtMs;
    private boolean walkingToTargetArea;
    private long lastBankActionAtMs;
    private long pendingBankInventoryActionAtMs;
    private int pendingBankInventoryEmptySlots = -1;
    private final AccountBuilderForestryHandler forestryHandler = new AccountBuilderForestryHandler();

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

            if (targetTree != null && this.forestryHandler.runIfNeeded(targetTree)) {
                this.pendingObjectInteractionAtMs = 0L;
                this.walkingToTargetArea = false;
                KspWalkerGuard.clearActiveWalker("ksp_account_builder_woodcutting_forestry");
                KspWalkerGuard.clear("Woodcutting:target-area");
                return;
            }

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

            this.chopForCurrentLevel(woodcuttingLevel);

        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean upgradeAxe(int woodcuttingLevel, int attackLevel) {
        WoodCuttingReq best = WoodCuttingReq.bestForWoodcuttingLevel(woodcuttingLevel);
        String targetAxe = resolveDesiredAxe(best);
        if (targetAxe == null) return true;

        String activeAxe = resolveBestOwnedAxeName(targetAxe);
        if (activeAxe == null) {
            if (!Rs2Bank.isOpen()) {
                if (!ensureInventoryTabOpen() || !bankActionReady()) return false;
                if (Rs2Bank.openBank() || Rs2Bank.walkToBankAndUseBank()) markBankAction();
                return false;
            }

            activeAxe = resolveBestOwnedAxeName(targetAxe);
            if (activeAxe == null) {
                Microbot.status = "No usable axe available";
                return false;
            }
        }

        WoodCuttingReq req = resolveWoodcuttingReq(activeAxe);
        boolean canEquip = req != null && canEquipDesiredAxe(req, attackLevel);

        if (!Rs2Equipment.isWearing(activeAxe) && !Rs2Inventory.hasItem(activeAxe)) {
            if (!Rs2Bank.isOpen()) {
                if (!ensureInventoryTabOpen() || !bankActionReady()) return false;
                if (Rs2Bank.openBank() || Rs2Bank.walkToBankAndUseBank()) markBankAction();
                return false;
            }

            if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) return false;
            if (!KspBankMode.ensureWithdrawAsItem()) return false;
            if (!bankActionReady()) return false;

            if (bankInventoryActionPending()) return false;
            int before = Rs2Inventory.emptySlotCount();
            if (Rs2Bank.withdrawOne(activeAxe)) markBankInventoryAction(before);
            markBankAction();
            return false;
        }

        if (canEquip && Rs2Inventory.hasItem(activeAxe) && !Rs2Equipment.isWearing(activeAxe)) {
            if (Rs2Bank.isOpen()) {
                if (!bankActionReady()) return false;
                Rs2Bank.closeBank();
                markBankAction();
                return false;
            }

            Rs2Inventory.wield(activeAxe);
            return false;
        }

        if (Rs2Bank.isOpen()) {
            if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) return false;
            if (!bankActionReady()) return false;

            if (bankInventoryActionPending()) return false;
            if (depositOneOutdatedAxe(activeAxe)) {
                markBankAction();
                return false;
            }
            if (!hasOutdatedAxeInInventory(activeAxe)) {
                Rs2Bank.closeBank();
            }
            markBankAction();
            return false;
        }

        return Rs2Equipment.isWearing(activeAxe) || Rs2Inventory.hasItem(activeAxe);
    }

    private boolean depositOneOutdatedAxe(String desiredAxeName) {
        for (String axeName : AXE_NAMES) {
            if (axeName.equalsIgnoreCase(desiredAxeName) || !Rs2Inventory.hasItem(axeName)) continue;
            int before = Rs2Inventory.emptySlotCount();
            if (Rs2Bank.depositAll(axeName)) {
                markBankInventoryAction(before);
                return true;
            }
        }
        return false;
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
        this.lastBankActionAtMs = 0L;
        KspWalkerGuard.clear("Woodcutting:target-area");
        this.lastWebWalkAtMs = 0L;
    }

    private WorldPoint getAreaCenter() {
        int centerX = (this.targetArea.getSouthWest().getX() + this.targetArea.getNorthEast().getX()) / 2;
        int centerY = (this.targetArea.getSouthWest().getY() + this.targetArea.getNorthEast().getY()) / 2;
        int plane = this.targetArea.getSouthWest().getPlane();

        return new WorldPoint(centerX, centerY, plane);
    }

    private boolean ensureInventoryTabOpen() {
        if (Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY) return true;
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        return false;
    }

    private void bankLogsOnly(int woodcuttingLevel) {
        if (!ensureInventoryTabOpen()) return;

        boolean localWillowBank = targetArea == TreeAreas.WILLOW_TREES_DRAYNOR
                && targetArea.contains(Rs2Player.getWorldLocation());

        if (localWillowBank) {
            KspWalkerGuard.clearReachedDestination("Woodcutting:target-area", "ksp_woodcutting_willow_local_bank");
            KspWalkerGuard.clearActiveWalker("ksp_woodcutting_willow_local_bank");
        }

        if (!Rs2Bank.isOpen()) {
            if (!bankActionReady()) return;
            boolean opened = localWillowBank
                    ? Rs2Bank.openBank()
                    : Rs2Bank.openBank() || Rs2Bank.walkToBankAndUseBank();
            if (opened) markBankAction();
            return;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) return;
        if (!bankActionReady() || bankInventoryActionPending()) return;

        String keep = resolveInventoryAxeToKeep(woodcuttingLevel);
        int before = Rs2Inventory.emptySlotCount();
        boolean deposited = keep != null ? Rs2Bank.depositAllExcept(keep) : Rs2Bank.depositAll();
        if (deposited) markBankInventoryAction(before);
        markBankAction();
    }

    private void interactWithBestWillowAfterBank() {
        long now = System.currentTimeMillis();
        if (Rs2Bank.isOpen() || !canDispatchObjectInteraction(now)) return;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;

        Rs2TileObjectModel willow = findMatchingTree(
                player,
                getAreaSearchRadius() + TREE_SEARCH_PADDING_TILES,
                TreeLevel.WILLOW,
                false);

        if (willow == null) return;

        lastObjectInteractionAtMs = now;
        Microbot.status = "Chopping " + willow.getName();
        if (willow.click("Chop down")) {
            pendingObjectInteractionAtMs = now;
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
        long now = System.currentTimeMillis();
        if (!canStartChopInTargetArea(now)) return;
        if (now - lastObjectInteractionAtMs < OBJECT_INTERACTION_COOLDOWN_MS) return;

        Rs2TileObjectModel tree = findNearestTreeInTargetArea(woodcuttingLevel);
        if (tree == null) {
            Microbot.status = "No reachable tree found";
            return;
        }

        lastObjectInteractionAtMs = now;
        if (tree.click("Chop down")) {
            pendingObjectInteractionAtMs = now;
            Microbot.status = "Chopping " + tree.getName();
            debug("Tree interaction accepted | tree={} id={} loc={} player={}",
                    tree.getName(), tree.getId(), tree.getWorldLocation(), Rs2Player.getWorldLocation());
        } else {
            debug("Tree interaction rejected | tree={} id={} loc={}",
                    tree.getName(), tree.getId(), tree.getWorldLocation());
        }
    }

    private boolean canStartChopInTargetArea(long now) {
        WorldPoint player = Rs2Player.getWorldLocation();
        return player != null
                && targetArea.contains(player)
                && canDispatchObjectInteraction(now);
    }

    private boolean canDispatchObjectInteraction(long now) {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            pendingObjectInteractionAtMs = 0L;
            return false;
        }

        if (pendingObjectInteractionAtMs > 0L) {
            if (now - pendingObjectInteractionAtMs < OBJECT_INTERACTION_START_TIMEOUT_MS) return false;
            pendingObjectInteractionAtMs = 0L;
        }

        return true;
    }

    private boolean bankInventoryActionPending() {
        if (pendingBankInventoryActionAtMs == 0L) return false;
        if (Rs2Inventory.emptySlotCount() != pendingBankInventoryEmptySlots) {
            pendingBankInventoryActionAtMs = 0L;
            pendingBankInventoryEmptySlots = -1;
            return false;
        }
        if (System.currentTimeMillis() - pendingBankInventoryActionAtMs < BANK_INVENTORY_ACTION_TIMEOUT_MS) return true;
        pendingBankInventoryActionAtMs = 0L;
        pendingBankInventoryEmptySlots = -1;
        return false;
    }

    private void markBankInventoryAction(int beforeEmptySlots) {
        pendingBankInventoryEmptySlots = beforeEmptySlots;
        pendingBankInventoryActionAtMs = System.currentTimeMillis();
    }

    private boolean bankActionReady() {
        return System.currentTimeMillis() - lastBankActionAtMs >= BANK_ACTION_COOLDOWN_MS;
    }

    private void markBankAction() {
        lastBankActionAtMs = System.currentTimeMillis();
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
        lastBankActionAtMs = 0L;
        pendingBankInventoryActionAtMs = 0L;
        pendingBankInventoryEmptySlots = -1;
        this.startingTargetTreeInitialized = false;
        this.randomMidTierTree = null;
        this.randomOakArea = null;
        this.lastWebWalkAtMs = 0L;
        this.lastObjectInteractionAtMs = 0L;
        this.pendingObjectInteractionAtMs = 0L;
        this.walkingToTargetArea = false;
        this.forestryHandler.reset();
        KspWalkerGuard.clear("Woodcutting:target-area");

        super.shutdown();
    }

    public TreeAreas getTargetArea() {
        return this.targetArea;
    }
}
