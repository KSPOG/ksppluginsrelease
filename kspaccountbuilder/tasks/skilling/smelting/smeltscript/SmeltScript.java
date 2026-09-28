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
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspBankMode;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.oresreq.ReqOres;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea.SmeltArea;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SmeltScript
extends Script {
    private static final Logger log = LoggerFactory.getLogger(SmeltScript.class);
    private static final int INVENTORY_SLOTS = 28;
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3000;
    private static final int FURNACE_INTERACT_COOLDOWN_MS = 2000;
    private static final int SMELT_START_GRACE_MS = 2500;
    private static final int SMELT_ANIMATION_COOLDOWN_MS = 1800;
    private static final int PRODUCTION_WIDGET_GROUP_ID = 270;
    private static final int PRODUCTION_WIDGET_CONTAINER_CHILD_ID = 13;
    private long lastWebWalkAtMs;
    private long lastFurnaceInteractAtMs;
    private long awaitingSmeltStartAtMs;
    private long lastSmeltAnimationAtMs;
    private SmeltArea targetArea = SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE;
    private BarLevels targetBar = BarLevels.BRONZE;
    private boolean debugLogging;
    private boolean walkingToTargetArea;
    private boolean progressiveSmelting = true;

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
                    this.awaitingSmeltStartAtMs != 0L);
            if (!this.ensureOreInventoryForTargetBar(this.targetBar)) {
                this.debug("Unable to prepare ore inventory for {} yet", this.targetBar.getDisplayName());
                return;
            }
            if (!this.ensureInTargetArea()) {
                return;
            }
            this.smeltAtFurnace(this.targetBar);
            this.debug("SmeltScript active | area={} | targetBar={}", this.targetArea.name(), this.targetBar.name());
        }, 0L, 600L, TimeUnit.MILLISECONDS);
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
        int secondaryToWithdraw;
        ReqOres req = ReqOres.valueOf(bar.name());
        if (this.hasBalancedOreInventory(req)) {
            return true;
        }
        if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank()) {
            return false;
        }
        if (!Rs2Bank.isOpen()) {
            return false;
        }
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
            return false;
        }
        if (!KspBankMode.ensureWithdrawAsItem()) {
            this.debug("Waiting for withdraw-as-item mode before withdrawing ores for {}", bar.getDisplayName());
            return false;
        }
        Rs2Bank.depositAll();
        SmeltScript.sleep((int)250);
        int barsToWithdraw = this.getBarsToWithdrawForInventory(req);
        if (barsToWithdraw <= 0) {
            this.debug("Not enough ores in bank to withdraw for {}", bar.getDisplayName());
            return false;
        }
        int primaryToWithdraw = barsToWithdraw * req.getPrimaryOreAmount();
        int n = secondaryToWithdraw = req.hasSecondaryOre() ? barsToWithdraw * req.getSecondaryOreAmount() : 0;
        if (!this.prepareExactOreInventory(req, primaryToWithdraw, secondaryToWithdraw)) {
            this.debug("Failed to prepare exact ore amounts for {} (primary={}, secondary={})", bar.getDisplayName(), primaryToWithdraw, secondaryToWithdraw);
            return false;
        }
        if (!this.hasRequiredOresInInventory(req)) {
            return false;
        }
        Rs2Bank.closeBank();
        SmeltScript.sleepUntil(() -> !Rs2Bank.isOpen(), (int)1500);
        return false;
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

    private boolean prepareExactOreInventory(ReqOres req, int primaryTargetAmount, int secondaryTargetAmount) {
        for (int attempt = 0; attempt < 2; ++attempt) {
            if (attempt > 0) {
                Rs2Bank.depositAll();
                SmeltScript.sleep((int)150);
            }
            if (!this.withdrawExactOreAmount(req.getPrimaryOreName(), primaryTargetAmount) || req.hasSecondaryOre() && !this.withdrawExactOreAmount(req.getSecondaryOreName(), secondaryTargetAmount) || !this.hasExactOreInventory(req, primaryTargetAmount, secondaryTargetAmount)) continue;
            return true;
        }
        return false;
    }

    private boolean withdrawExactOreAmount(String oreName, int targetAmount) {
        if (targetAmount <= 0) {
            return true;
        }
        boolean withdrew = Rs2Bank.withdrawX((String)oreName, (int)targetAmount);
        if (!withdrew) {
            return false;
        }
        SmeltScript.sleepUntil(() -> Rs2Inventory.count((String)oreName) == targetAmount, (int)2000);
        return Rs2Inventory.count((String)oreName) == targetAmount;
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
        if (Rs2Player.isMoving()) {
            return false;
        }
        if (KspWalkerGuard.walkToDestination(
                "Smelting:target-area",
                this.targetArea::getRandomPoint,
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
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            this.lastSmeltAnimationAtMs = System.currentTimeMillis();
            return;
        }
        long sinceLastSmeltAnimation = System.currentTimeMillis() - this.lastSmeltAnimationAtMs;
        if (sinceLastSmeltAnimation < 1800L) {
            return;
        }
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }
        if (this.handleSmeltSelection(bar)) {
            return;
        }
        if (this.handleProductionWidget(bar)) {
            return;
        }
        if (this.isWaitingForSmeltStart()) {
            return;
        }
        if (!this.isIdleInTargetArea()) {
            KspTaskDebug.throttled(log, this.debugLogging, "Smelting", "not-idle", 2_000L,
                    "waiting for idle before furnace | player={} moving={} animating={} interacting={} area={}",
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    this.targetArea.getDisplayName());
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastFurnaceInteractAtMs < 2000L) {
            return;
        }
        Rs2TileObjectModel furnace = this.findNearbyFurnaceInTargetArea();
        if (furnace == null) {
            this.debug("No reachable furnace found | bar={} player={} area={}", bar.getDisplayName(), Rs2Player.getWorldLocation(), this.targetArea.getDisplayName());
            return;
        }
        this.lastFurnaceInteractAtMs = now;
        this.debug("Attempting furnace interaction | bar={} furnaceName={} id={} loc={} reachable={} player={} distance={} area={}",
                bar.getDisplayName(),
                furnace.getName(),
                furnace.getId(),
                furnace.getWorldLocation(),
                furnace.isReachable(),
                Rs2Player.getWorldLocation(),
                Rs2Player.getWorldLocation() != null ? Rs2Player.getWorldLocation().distanceTo(furnace.getWorldLocation()) : -1,
                this.targetArea.getDisplayName());
        boolean started = furnace.click("Smelt");
        this.debug("Furnace interaction result | clicked={} bar={} furnaceId={} furnaceLoc={} player={} moving={} animating={} interacting={} productionOpen={}",
                started,
                bar.getDisplayName(),
                furnace.getId(),
                furnace.getWorldLocation(),
                Rs2Player.getWorldLocation(),
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting(),
                Rs2Widget.isProductionWidgetOpen());
        if (!started) {
            return;
        }
        this.awaitingSmeltStartAtMs = System.currentTimeMillis();
        this.debug("Interacted with furnace to smelt {}", bar.getDisplayName());
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
        boolean smeltSelectionOpened = Rs2Widget.sleepUntilHasWidgetText((String)"What would you like to smelt?", (int)270, (int)5, (boolean)false, (int)1500);
        if (!smeltSelectionOpened) {
            return false;
        }
        boolean clickedBar = Rs2Widget.clickWidget((String)bar.getDisplayName());
        this.debug("Smelt selection widget | bar={} clicked={} productionOpen={}", bar.getDisplayName(), clickedBar, Rs2Widget.isProductionWidgetOpen());
        if (!clickedBar) {
            this.debug("Failed to click smelt option {}", bar.getDisplayName());
            return true;
        }
        Rs2Widget.sleepUntilHasNotWidgetText((String)"What would you like to smelt?", (int)270, (int)5, (boolean)false, (int)3000);
        this.handleProductionWidget(bar);
        return true;
    }

    private boolean handleProductionWidget(BarLevels bar) {
        boolean productionOpened;
        if (!Rs2Widget.isProductionWidgetOpen() && !(productionOpened = SmeltScript.sleepUntil(Rs2Widget::isProductionWidgetOpen, (int)1500))) {
            return false;
        }
        boolean selectedBar = this.selectProductionBar(bar);
        this.debug("Production widget selection | bar={} selected={} productionOpen={}", bar.getDisplayName(), selectedBar, Rs2Widget.isProductionWidgetOpen());
        if (!selectedBar) {
            this.debug("Failed to select production widget option {}", bar.getDisplayName());
            return true;
        }
        Rs2Keyboard.keyPress((int)32);
        this.awaitingSmeltStartAtMs = System.currentTimeMillis();
        SmeltScript.sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), (int)2500);
        return true;
    }

    private boolean selectProductionBar(BarLevels bar) {
        boolean selected = Rs2Widget.clickWidget((String)bar.getDisplayName(), Optional.of(270), (int)13, (boolean)false);
        if (!selected) {
            boolean bl = selected = Rs2Widget.clickWidget((String)bar.getDisplayName(), (boolean)true) || Rs2Widget.clickWidget((String)bar.getDisplayName(), (boolean)false);
        }
        if (selected) {
            SmeltScript.sleep((int)150);
        }
        return selected;
    }

    private boolean isIdleInTargetArea() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation != null
                && this.targetArea.toWorldArea().contains(playerLocation)
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating()
                && !Rs2Player.isInteracting();
    }

    private boolean isWaitingForSmeltStart() {
        if (this.awaitingSmeltStartAtMs == 0L) {
            return false;
        }
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return true;
        }
        long elapsed = System.currentTimeMillis() - this.awaitingSmeltStartAtMs;
        if (elapsed < 2500L) {
            return true;
        }
        this.awaitingSmeltStartAtMs = 0L;
        return false;
    }

    private void debug(String message, Object ... args) {
        if (this.debugLogging) {
            KspTaskDebug.info(log, true, "Smelting", message, args);
        }
    }

    public void shutdown() {
        this.lastWebWalkAtMs = 0L;
        this.lastFurnaceInteractAtMs = 0L;
        this.awaitingSmeltStartAtMs = 0L;
        this.lastSmeltAnimationAtMs = 0L;
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
