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
    private static final int LOOP_DELAY_MS = 100;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final int FURNACE_INTERACT_COOLDOWN_MS = 300;
    private static final int SMELT_START_GRACE_MS = 900;
    private long lastWebWalkAtMs;
    private long lastFurnaceInteractAtMs;
    private long awaitingSmeltStartAtMs;
    private long lastSmeltAnimationAtMs;
    private SmeltArea targetArea = SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE;
    private BarLevels targetBar = BarLevels.BRONZE;
    private boolean debugLogging;
    private boolean walkingToTargetArea;
    private boolean progressiveSmelting = true;
    private boolean bankInventoryReset;

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
        if (hasBalancedOreInventory(req)) {
            bankInventoryReset = false;
            if (Rs2Bank.isOpen()) {
                Rs2Bank.closeBank();
                return false;
            }
            return true;
        }

        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank()) Rs2Bank.walkToBankAndUseBank();
            return false;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return false;
        if (!KspBankMode.ensureWithdrawAsItem()) return false;

        if (!bankInventoryReset) {
            Rs2Bank.depositAll();
            bankInventoryReset = true;
            return false;
        }

        int bars = getBarsToWithdrawForInventory(req);
        if (bars <= 0) return false;

        int primaryTarget = bars * req.getPrimaryOreAmount();
        int secondaryTarget = req.hasSecondaryOre() ? bars * req.getSecondaryOreAmount() : 0;

        int primaryCurrent = Rs2Inventory.count(req.getPrimaryOreName());
        if (primaryCurrent < primaryTarget) {
            Rs2Bank.withdrawX(req.getPrimaryOreName(), primaryTarget - primaryCurrent);
            return false;
        }

        if (req.hasSecondaryOre()) {
            int secondaryCurrent = Rs2Inventory.count(req.getSecondaryOreName());
            if (secondaryCurrent < secondaryTarget) {
                Rs2Bank.withdrawX(req.getSecondaryOreName(), secondaryTarget - secondaryCurrent);
                return false;
            }
        }

        if (!hasExactOreInventory(req, primaryTarget, secondaryTarget)) return false;

        bankInventoryReset = false;
        Rs2Bank.closeBank();
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
        return hasExactOreInventory(req, primaryTargetAmount, secondaryTargetAmount);
    }

    private boolean withdrawExactOreAmount(String oreName, int targetAmount) {
        if (targetAmount <= 0) return true;
        int current = Rs2Inventory.count(oreName);
        if (current >= targetAmount) return true;
        return Rs2Bank.withdrawX(oreName, targetAmount - current);
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
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            lastSmeltAnimationAtMs = System.currentTimeMillis();
            return;
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }

        if (handleSmeltSelection(bar) || handleProductionWidget(bar)) return;
        if (isWaitingForSmeltStart()) return;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || !targetArea.toWorldArea().contains(player)) return;

        clearTargetAreaWalkIfNeeded();

        long now = System.currentTimeMillis();
        if (now - lastFurnaceInteractAtMs < FURNACE_INTERACT_COOLDOWN_MS) return;

        Rs2TileObjectModel furnace = findNearbyFurnaceInTargetArea();
        if (furnace == null) return;

        if (furnace.click("Smelt")) {
            lastFurnaceInteractAtMs = now;
            awaitingSmeltStartAtMs = now;
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
        if (Rs2Widget.findWidget("What would you like to smelt?", null, false) == null) return false;

        Rs2Widget.clickWidget(bar.getDisplayName());
        return true;
    }

    private boolean handleProductionWidget(BarLevels bar) {
        if (!Rs2Widget.isProductionWidgetOpen()) return false;

        if (!selectProductionBar(bar)) return true;

        Rs2Keyboard.keyPress(32);
        awaitingSmeltStartAtMs = System.currentTimeMillis();
        return true;
    }

    private boolean selectProductionBar(BarLevels bar) {
        boolean selected = Rs2Widget.clickWidget(bar.getDisplayName(), Optional.of(270), 13, false);
        if (!selected) {
            selected = Rs2Widget.clickWidget(bar.getDisplayName(), true)
                    || Rs2Widget.clickWidget(bar.getDisplayName(), false);
        }
        return selected;
    }

    private boolean isWaitingForSmeltStart() {
        if (this.awaitingSmeltStartAtMs == 0L) {
            return false;
        }
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) {
            return true;
        }
        long elapsed = System.currentTimeMillis() - this.awaitingSmeltStartAtMs;
        if (elapsed < SMELT_START_GRACE_MS) {
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
        this.bankInventoryReset = false;
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
