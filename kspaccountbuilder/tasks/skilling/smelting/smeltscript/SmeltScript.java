/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  javax.inject.Singleton
 *  net.runelite.api.Skill
 *  net.runelite.api.coords.WorldPoint
 *  net.runelite.client.plugins.microbot.Microbot
 *  net.runelite.client.plugins.microbot.Script
 *  net.runelite.client.plugins.microbot.util.bank.Rs2Bank
 *  net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory
 *  net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard
 *  net.runelite.client.plugins.microbot.util.player.Rs2Player
 *  net.runelite.client.plugins.microbot.util.walker.Rs2Walker
 *  net.runelite.client.plugins.microbot.util.widget.Rs2Widget
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltscript;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.oresreq.ReqOres;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea.SmeltArea;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SmeltScript
extends Script {
    private static final Logger log = LoggerFactory.getLogger(SmeltScript.class);
    private static final int LOOP_DELAY_MS = 100;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final int EDGEVILLE_FURNACE_ID = 16469;
    private static final WorldPoint EDGEVILLE_FURNACE_POINT = new WorldPoint(3108, 3499, 0);
    private static final WorldPoint EDGEVILLE_BANK_POINT = new WorldPoint(3096, 3491, 0);
    private static final long TARGET_INTERACTION_TIMEOUT_MS = 8_000L;
    private static final int PRODUCTION_ACTION_START_TIMEOUT_MS = 1_500;
    private static final long SMELTING_BATCH_STALL_TIMEOUT_MS = 5_000L;
    private long lastWebWalkAtMs;
    private long bankInteractionSentAt;
    private long furnaceInteractionSentAt;
    private long lastSmeltAnimationAtMs;
    private long pendingProductionActionAtMs;
    private String pendingProductionActionKey;
    private SmeltArea targetArea = SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE;
    private BarLevels targetBar = BarLevels.BRONZE;
    private boolean debugLogging;
    private boolean walkingToTargetArea;
    private boolean progressiveSmelting = true;
    private boolean smeltingBatchActive;
    private int smeltingBatchRemainingCycles;
    private long smeltingBatchLastProgressAtMs;

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public boolean run(SmeltArea area, BarLevels fallbackBarLevel) {
        return this.run(area, fallbackBarLevel, true);
    }

    public boolean run(SmeltArea area, BarLevels fallbackBarLevel, boolean progressiveSmelting) {
        this.shutdown();
        this.targetArea = area;
        this.targetBar = fallbackBarLevel;
        this.progressiveSmelting = progressiveSmelting;
        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn()) {
                return;
            }
            if (this.isSmeltingBatchActive(this.targetBar)) {
                Microbot.status = "Smelting " + this.targetBar.getDisplayName();
                return;
            }

            this.selectTargetBar(fallbackBarLevel);
            KspTaskDebug.throttled(log, this.debugLogging, "Smelting", "loop", 5_000L,
                    "loop | targetBar={} area={} player={} moving={} animating={} interacting={} bankOpen={} productionOpen={} awaitingStart={}",
                    this.targetBar != null ? this.targetBar.getDisplayName() : "none",
                    this.targetArea.getDisplayName(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Bank.isOpen(),
                    Rs2Widget.isProductionWidgetOpen(),
                    this.furnaceInteractionSentAt != 0L);
            if (!this.ensureOreInventoryForTargetBar(this.targetBar)) {
                this.debug("Unable to prepare ore inventory for {} yet", this.targetBar.getDisplayName());
                return;
            }
            if (!this.ensureInTargetArea()) {
                return;
            }
            this.smeltAtFurnace(this.targetBar);
            this.debug("SmeltScript active | area={} | targetBar={}", this.targetArea.name(), this.targetBar.name());
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void selectTargetBar(BarLevels fallbackBarLevel) {
        if (!this.progressiveSmelting) {
            this.targetBar = fallbackBarLevel;
            return;
        }

        BarLevels inventoryBar = this.resolveInventorySmeltableBar();
        if (inventoryBar != null) {
            if (this.targetBar != inventoryBar) {
                this.targetBar = inventoryBar;
                this.debug("Keeping inventory-selected bar {}", this.targetBar.getDisplayName());
            }
            return;
        }
        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        BarLevels bestBar = this.resolveBestSmeltableBar(smithingLevel);
        if (bestBar != null) {
            if (this.targetBar != bestBar) {
                this.targetBar = bestBar;
                this.debug("Selected best smeltable bar {} at smithing level {}", this.targetBar.getDisplayName(), smithingLevel);
            }
            return;
        }
        this.targetBar = fallbackBarLevel;
        this.debug("No smeltable bar found from bank ores at smithing level {}, using fallback {}", smithingLevel, fallbackBarLevel.getDisplayName());
    }

    private BarLevels resolveInventorySmeltableBar() {
        BarLevels[] bars = BarLevels.values();
        for (int i = bars.length - 1; i >= 0; --i) {
            BarLevels bar = bars[i];
            ReqOres req = ReqOres.valueOf(bar.name());
            if (!this.hasBalancedOreInventory(req)) continue;
            return bar;
        }
        return null;
    }

    private BarLevels resolveBestSmeltableBar(int smithingLevel) {
        BarLevels[] bars = BarLevels.values();
        for (int i = bars.length - 1; i >= 0; --i) {
            BarLevels bar = bars[i];
            if (smithingLevel < bar.getRequiredSmithingLevel() || this.getCraftableBarsFromBank(bar) <= 0) continue;
            return bar;
        }
        return null;
    }

    private int getCraftableBarsFromBank(BarLevels bar) {
        ReqOres req = ReqOres.valueOf(bar.name());
        int primaryCount = Math.max(0, Rs2Bank.count((String)req.getPrimaryOreName()));
        int primaryBars = primaryCount / req.getPrimaryOreAmount();
        if (!req.hasSecondaryOre()) {
            return primaryBars;
        }
        int secondaryCount = Math.max(0, Rs2Bank.count((String)req.getSecondaryOreName()));
        int secondaryBars = secondaryCount / req.getSecondaryOreAmount();
        return Math.min(primaryBars, secondaryBars);
    }

    private boolean ensureOreInventoryForTargetBar(BarLevels bar) {
        ReqOres req = ReqOres.valueOf(bar.name());

        if (hasRequiredOresInInventory(req)) {
            bankInteractionSentAt = 0L;
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
                return false;
            }
            return true;
        }

        if (!openWorkBank()) {
            return false;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return false;

        Microbot.status = "Preparing " + bar.getDisplayName();
        if (!Rs2Bank.setWithdrawAsItem()) {
            Microbot.status = "Setting bank item withdrawal mode";
            return false;
        }

        if (!Rs2Inventory.isEmpty()) {
            if (!Rs2Bank.depositAll()) {
                Microbot.status = "Depositing previous smelting inventory";
                return false;
            }
            if (!sleepUntil(Rs2Inventory::isEmpty, 3_000)) {
                Microbot.status = "Waiting for previous smelting inventory to bank";
                return false;
            }
        }

        int cycles = getBarsToWithdrawForInventory(req);
        if (cycles <= 0) {
            Microbot.status = "Out of ores for " + bar.getDisplayName();
            return false;
        }

        int primaryTarget = cycles * req.getPrimaryOreAmount();
        int secondaryTarget = req.hasSecondaryOre() ? cycles * req.getSecondaryOreAmount() : 0;

        if (!withdrawAndWait(req.getPrimaryOreName(), primaryTarget)) {
            return false;
        }
        if (req.hasSecondaryOre() && !withdrawAndWait(req.getSecondaryOreName(), secondaryTarget)) {
            return false;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);

        if (!hasExactOreInventory(req, primaryTarget, secondaryTarget)) {
            Microbot.status = "Smelting inventory setup failed";
            return false;
        }

        bankInteractionSentAt = 0L;
        furnaceInteractionSentAt = 0L;
        return true;
    }

    private boolean openWorkBank() {
        if (Rs2Bank.isOpen()) {
            bankInteractionSentAt = 0L;
            return true;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return false;

        if (player.distanceTo(EDGEVILLE_BANK_POINT) > 20
                && player.distanceTo(EDGEVILLE_FURNACE_POINT) > 20) {
            bankInteractionSentAt = 0L;
            Microbot.status = "Walking to Edgeville bank";
            if (!Rs2Player.isMoving()) {
                Rs2Walker.walkTo(EDGEVILLE_BANK_POINT, 4);
            }
            return false;
        }

        long now = System.currentTimeMillis();
        if (bankInteractionSentAt > 0L
                && (Rs2Player.isMoving() || now - bankInteractionSentAt < TARGET_INTERACTION_TIMEOUT_MS)) {
            Microbot.status = Rs2Player.isMoving()
                    ? "Approaching Edgeville bank"
                    : "Waiting for Edgeville bank";
            return false;
        }

        bankInteractionSentAt = 0L;
        GameObject bank = Rs2GameObject.get("Bank booth", true);
        if (bank == null) {
            Microbot.status = "Finding nearby Edgeville bank booth";
            return false;
        }

        Microbot.status = "Opening Edgeville bank";
        bankInteractionSentAt = now;
        if (!interactGameObjectWithoutCamera(bank, "Bank")) {
            bankInteractionSentAt = 0L;
            Microbot.status = "Edgeville bank target not ready";
            return false;
        }

        if (sleepUntil(Rs2Bank::isOpen, 5_000)) {
            bankInteractionSentAt = 0L;
            return true;
        }

        Microbot.status = Rs2Player.isMoving()
                ? "Approaching Edgeville bank"
                : "Waiting for Edgeville bank";
        return false;
    }

    private boolean withdrawAndWait(String oreName, int wanted) {
        if (oreName == null || wanted <= 0) return true;

        int current = Rs2Inventory.count(oreName);
        if (current == wanted) return true;
        if (current > wanted) {
            Microbot.status = "Resetting incorrect " + oreName + " amount";
            return false;
        }

        if (!Rs2Bank.withdrawX(oreName, wanted - current)) {
            Microbot.status = "Failed withdrawing " + oreName;
            return false;
        }

        if (!sleepUntil(() -> Rs2Inventory.count(oreName) >= wanted, 3_000)) {
            Microbot.status = "Waiting for " + oreName + " withdrawal";
            return false;
        }

        return Rs2Inventory.count(oreName) == wanted;
    }

    private boolean hasBalancedOreInventory(ReqOres req) {
        if (!this.hasRequiredOresInInventory(req)) {
            return false;
        }
        if (!req.hasSecondaryOre()) {
            return true;
        }
        int primaryCount = Rs2Inventory.count((String)req.getPrimaryOreName());
        int secondaryCount = Rs2Inventory.count((String)req.getSecondaryOreName());
        return primaryCount * req.getSecondaryOreAmount() == secondaryCount * req.getPrimaryOreAmount();
    }

    private boolean hasExactOreInventory(ReqOres req, int primaryTargetAmount, int secondaryTargetAmount) {
        int currentPrimary = Rs2Inventory.count((String)req.getPrimaryOreName());
        if (currentPrimary != primaryTargetAmount) {
            return false;
        }
        if (!req.hasSecondaryOre()) {
            return true;
        }
        return Rs2Inventory.count((String)req.getSecondaryOreName()) == secondaryTargetAmount;
    }

    private int getBarsToWithdrawForInventory(ReqOres req) {
        int maxBarsFromPrimary = Math.max(0, Rs2Bank.count((String)req.getPrimaryOreName())) / req.getPrimaryOreAmount();
        if (maxBarsFromPrimary <= 0) {
            return 0;
        }
        int maxBarsFromBank = maxBarsFromPrimary;
        if (req.hasSecondaryOre()) {
            int maxBarsFromSecondary = Math.max(0, Rs2Bank.count((String)req.getSecondaryOreName())) / req.getSecondaryOreAmount();
            maxBarsFromBank = Math.min(maxBarsFromPrimary, maxBarsFromSecondary);
        }
        int oresPerBar = req.getPrimaryOreAmount() + (req.hasSecondaryOre() ? req.getSecondaryOreAmount() : 0);
        int maxBarsFromInventory = 28 / oresPerBar;
        return Math.min(maxBarsFromBank, maxBarsFromInventory);
    }

    private boolean hasRequiredOresInInventory(ReqOres req) {
        int primaryCount = Rs2Inventory.count((String)req.getPrimaryOreName());
        if (primaryCount < req.getPrimaryOreAmount()) {
            return false;
        }
        if (!req.hasSecondaryOre()) {
            return true;
        }
        int secondaryCount = Rs2Inventory.count((String)req.getSecondaryOreName());
        return secondaryCount >= req.getSecondaryOreAmount();
    }

    private boolean ensureInTargetArea() {
        if (this.targetArea.toWorldArea().contains(Rs2Player.getWorldLocation())) {
            this.clearTargetAreaWalkIfNeeded();
            return true;
        }
        if (KspWalkerGuard.walkToDestination(
                "Smelting:target-area",
                this::getAreaCenter,
                this.targetArea.toWorldArea()::contains,
                2,
                WEB_WALK_COOLDOWN_MS)) {
            this.lastWebWalkAtMs = System.currentTimeMillis();
            this.walkingToTargetArea = true;
            this.debug("Requested smelting area walk | player={} walkerTarget={} area={}",
                    Rs2Player.getWorldLocation(),
                    Rs2Walker.getCurrentTarget(),
                    this.targetArea.getDisplayName());
        }
        return false;
    }

    private void clearTargetAreaWalkIfNeeded() {
        if (!this.walkingToTargetArea) {
            return;
        }
        KspWalkerGuard.clearActiveWalker("ksp_account_builder_smelting_reached_area");
        KspWalkerGuard.clear("Smelting:target-area");
        this.walkingToTargetArea = false;
    }

    private WorldPoint getAreaCenter() {
        int centerX = (this.targetArea.getSouthWest().getX() + this.targetArea.getNorthEast().getX()) / 2;
        int centerY = (this.targetArea.getSouthWest().getY() + this.targetArea.getNorthEast().getY()) / 2;
        int plane = this.targetArea.getSouthWest().getPlane();
        return new WorldPoint(centerX, centerY, plane);
    }

    private void smeltAtFurnace(BarLevels bar) {
        if (Rs2Player.isAnimating()) {
            lastSmeltAnimationAtMs = System.currentTimeMillis();
            return;
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }

        if (handleSmeltSelection(bar) || handleProductionWidget(bar)) {
            furnaceInteractionSentAt = 0L;
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || !targetArea.toWorldArea().contains(player)) return;

        clearTargetAreaWalkIfNeeded();

        long now = System.currentTimeMillis();
        if (furnaceInteractionSentAt > 0L
                && now - furnaceInteractionSentAt < TARGET_INTERACTION_TIMEOUT_MS) {
            Microbot.status = Rs2Player.isMoving()
                    ? "Approaching furnace"
                    : "Waiting for smelting interface";
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isInteracting()) return;

        furnaceInteractionSentAt = 0L;

        TileObject furnace = Rs2GameObject.findObjectById(EDGEVILLE_FURNACE_ID);
        if (furnace == null) {
            Rs2TileObjectModel fallback = findNearbyFurnaceInTargetArea();
            if (fallback == null) return;

            Microbot.status = "Opening furnace interface";
            if (fallback.click("Smelt")) {
                furnaceInteractionSentAt = now;
            }
            return;
        }

        Microbot.status = "Opening Edgeville furnace interface";
        if (interactGameObjectWithoutCamera(furnace, "Smelt")) {
            furnaceInteractionSentAt = now;
        }
    }

    private Rs2TileObjectModel findNearbyFurnaceInTargetArea() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }
        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withName("Furnace")
                .within(playerLocation, 12)
                .where(obj -> obj.getWorldLocation() != null && this.targetArea.toWorldArea().contains(obj.getWorldLocation()))
                .nearestReachable(12));
    }

    private boolean handleSmeltSelection(BarLevels bar) {
        if (Rs2Widget.findWidget("What would you like to smelt?", null, false) == null) {
            clearProductionActionIfWidgetClosed();
            return false;
        }

        if (isProductionActionPending("smelt-selection:" + bar.name())) return true;

        if (Rs2Widget.clickWidget(bar.getDisplayName())) {
            markProductionAction("smelt-selection:" + bar.name());
        }
        return true;
    }

    private boolean handleProductionWidget(BarLevels bar) {
        if (!Rs2Widget.isProductionWidgetOpen()) {
            clearProductionActionIfWidgetClosed();
            return false;
        }

        String key = "production:" + bar.name();
        if (isProductionActionPending(key)) return true;

        if (!selectProductionBar(bar)) return true;

        markProductionAction(key);
        Rs2Keyboard.keyPress(32);
        furnaceInteractionSentAt = 0L;
        beginSmeltingBatch(bar);
        return true;
    }

    private void beginSmeltingBatch(BarLevels bar) {
        smeltingBatchActive = true;
        smeltingBatchRemainingCycles = getCraftableBarsFromInventory(bar);
        smeltingBatchLastProgressAtMs = System.currentTimeMillis();
        lastSmeltAnimationAtMs = 0L;
        debug("Smelting batch started | bar={} cycles={}", bar.getDisplayName(), smeltingBatchRemainingCycles);
    }

    private boolean isSmeltingBatchActive(BarLevels bar) {
        if (!smeltingBatchActive || bar == null) return false;

        int remainingCycles = getCraftableBarsFromInventory(bar);
        long now = System.currentTimeMillis();

        if (Rs2Player.isAnimating()) {
            lastSmeltAnimationAtMs = now;
            smeltingBatchLastProgressAtMs = now;
        }

        if (remainingCycles < smeltingBatchRemainingCycles) {
            smeltingBatchRemainingCycles = remainingCycles;
            smeltingBatchLastProgressAtMs = now;
            debug("Smelting batch progressed | bar={} remainingCycles={}",
                    bar.getDisplayName(), remainingCycles);
        }

        if (remainingCycles <= 0) {
            clearSmeltingBatch();
            return false;
        }

        if (now - smeltingBatchLastProgressAtMs < SMELTING_BATCH_STALL_TIMEOUT_MS) {
            return true;
        }

        debug("Smelting batch stalled | bar={} remainingCycles={} lastAnimationAgo={}ms",
                bar.getDisplayName(),
                remainingCycles,
                lastSmeltAnimationAtMs == 0L ? -1L : now - lastSmeltAnimationAtMs);
        clearSmeltingBatch();
        return false;
    }

    private int getCraftableBarsFromInventory(BarLevels bar) {
        if (bar == null) return 0;
        ReqOres req = ReqOres.valueOf(bar.name());
        int primaryCycles = Rs2Inventory.count(req.getPrimaryOreName()) / req.getPrimaryOreAmount();
        if (!req.hasSecondaryOre()) return Math.max(0, primaryCycles);

        int secondaryCycles = Rs2Inventory.count(req.getSecondaryOreName()) / req.getSecondaryOreAmount();
        return Math.max(0, Math.min(primaryCycles, secondaryCycles));
    }

    private void clearSmeltingBatch() {
        smeltingBatchActive = false;
        smeltingBatchRemainingCycles = 0;
        smeltingBatchLastProgressAtMs = 0L;
        lastSmeltAnimationAtMs = 0L;
    }

    private boolean isProductionActionPending(String key) {
        if (pendingProductionActionAtMs == 0L || pendingProductionActionKey == null) return false;

        long elapsed = System.currentTimeMillis() - pendingProductionActionAtMs;
        if (!pendingProductionActionKey.equals(key)) {
            if (elapsed >= PRODUCTION_ACTION_START_TIMEOUT_MS) clearPendingProductionAction();
            return false;
        }

        if (Rs2Player.isAnimating()) {
            clearPendingProductionAction();
            return true;
        }

        if (elapsed < PRODUCTION_ACTION_START_TIMEOUT_MS) return true;

        clearPendingProductionAction();
        return false;
    }

    private void markProductionAction(String key) {
        pendingProductionActionKey = key;
        pendingProductionActionAtMs = System.currentTimeMillis();
    }

    private void clearProductionActionIfWidgetClosed() {
        if (!Rs2Widget.isProductionWidgetOpen()
                && Rs2Widget.findWidget("What would you like to smelt?", null, false) == null) {
            clearPendingProductionAction();
        }
    }

    private void clearPendingProductionAction() {
        pendingProductionActionAtMs = 0L;
        pendingProductionActionKey = null;
    }

    private boolean selectProductionBar(BarLevels bar) {
        boolean selected = Rs2Widget.clickWidget(bar.getDisplayName(), Optional.of(270), 13, false);
        if (!selected) {
            selected = Rs2Widget.clickWidget(bar.getDisplayName(), true)
                    || Rs2Widget.clickWidget(bar.getDisplayName(), false);
        }
        return selected;
    }

    private boolean interactGameObjectWithoutCamera(TileObject tileObject, String action) {
        if (!(tileObject instanceof GameObject) || action == null || action.isBlank()) {
            return false;
        }

        GameObject object = (GameObject) tileObject;
        ObjectComposition composition = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ObjectComposition resolved = Microbot.getClient().getObjectDefinition(object.getId());
            if (resolved != null && resolved.getImpostorIds() != null && resolved.getImpostor() != null) {
                resolved = resolved.getImpostor();
            }
            return resolved;
        }).orElse(null);
        if (composition == null) {
            return false;
        }

        String[] actions = composition.getActions();
        if (actions == null) {
            return false;
        }

        int actionIndex = -1;
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && action.equalsIgnoreCase(actions[i])) {
                actionIndex = i;
                break;
            }
        }
        if (actionIndex < 0) {
            return false;
        }

        MenuAction menuAction = gameObjectMenuAction(actionIndex);
        if (menuAction == null) {
            return false;
        }

        int sceneX = object.getLocalLocation().getSceneX();
        int sceneY = object.getLocalLocation().getSceneY();
        if (object.sizeX() > 1) {
            sceneX -= object.sizeX() / 2;
        }
        if (object.sizeY() > 1) {
            sceneY -= object.sizeY() / 2;
        }

        NewMenuEntry entry = new NewMenuEntry()
                .param0(sceneX)
                .param1(sceneY)
                .opcode(menuAction.getId())
                .identifier(object.getId())
                .itemId(-1)
                .option(actions[actionIndex])
                .target(composition.getName())
                .gameObject(object);

        Microbot.doInvoke(entry, Rs2UiHelper.getObjectClickbox(object));
        return true;
    }

    private MenuAction gameObjectMenuAction(int actionIndex) {
        switch (actionIndex) {
            case 0: return MenuAction.GAME_OBJECT_FIRST_OPTION;
            case 1: return MenuAction.GAME_OBJECT_SECOND_OPTION;
            case 2: return MenuAction.GAME_OBJECT_THIRD_OPTION;
            case 3: return MenuAction.GAME_OBJECT_FOURTH_OPTION;
            case 4: return MenuAction.GAME_OBJECT_FIFTH_OPTION;
            default: return null;
        }
    }

    private void debug(String message, Object ... args) {
        if (this.debugLogging) {
            KspTaskDebug.info(log, true, "Smelting", message, args);
        }
    }

    public void shutdown() {
        this.lastWebWalkAtMs = 0L;
        this.bankInteractionSentAt = 0L;
        this.furnaceInteractionSentAt = 0L;
        this.lastSmeltAnimationAtMs = 0L;
        this.clearSmeltingBatch();
        this.clearPendingProductionAction();
        this.walkingToTargetArea = false;
        KspWalkerGuard.clear("Smelting:target-area");
        super.shutdown();
    }

    public SmeltArea getTargetArea() {
        return this.targetArea;
    }

    public BarLevels getTargetBar() {
        return this.targetBar;
    }
}
