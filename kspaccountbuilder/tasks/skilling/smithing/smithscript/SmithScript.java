/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.inject.Singleton
 *  net.runelite.api.Skill
 *  net.runelite.api.coords.WorldPoint
 *  net.runelite.client.plugins.microbot.Microbot
 *  net.runelite.client.plugins.microbot.Script
 *  net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable
 *  net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel
 *  net.runelite.client.plugins.microbot.util.bank.Rs2Bank
 *  net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory
 *  net.runelite.client.plugins.microbot.util.player.Rs2Player
 *  net.runelite.client.plugins.microbot.util.walker.Rs2Walker
 *  net.runelite.client.plugins.microbot.util.widget.Rs2Widget
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithscript;

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
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.recipes.SmithRecipe;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smitharea.SmithArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithlevels.SmithLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.tool.SmithTool;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SmithScript
extends Script {
    private static final Logger log = LoggerFactory.getLogger(SmithScript.class);
    private static final int INVENTORY_SLOTS = 28;
    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3000;
    private static final int ANVIL_INTERACT_COOLDOWN_MS = 2000;
    private static final int SMITH_START_GRACE_MS = 2500;
    private static final int SMITH_ANIMATION_COOLDOWN_MS = 1800;
    private static final int ANVIL_SEARCH_RADIUS = 12;
    private static final int ANVIL_APPROACH_DISTANCE = 6;
    private static final int SMITHING_WIDGET_GROUP_ID = 312;
    private static final int SMITHING_WIDGET_CONTAINER_CHILD_ID = 1;
    private static final int SMITHING_ALL_BUTTON_CHILD_ID = 7;
    private static final int ANVIL_MAKE_VARBIT_PLAYER = 2224;
    private static final WorldPoint VARROCK_WEST_ANVIL_APPROACH_POINT = new WorldPoint(3188, 3424, 0);
    private static final String TARGET_AREA_WALK_KEY = "Smithing:target-area";
    private static final String ANVIL_WALK_KEY = "Smithing:anvil";
    private long lastWebWalkAtMs;
    private long lastAnvilInteractAtMs;
    private long awaitingSmithStartAtMs;
    private long lastSmithAnimationAtMs;
    private WorldPoint lastWalkTarget;
    private boolean expectingSmithXpDrop;
    private boolean debugLogging;
    private boolean walkingToTargetArea;
    private SmithArea targetArea = SmithArea.SMITH_AREA_VARROCK_WEST_ANVIL;
    private SmithRecipe targetRecipe = SmithRecipe.BRONZE_DAGGER;
    private SmithLevels forcedSmithLevel;

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public boolean run(SmithArea area) {
        return this.run(area, null);
    }

    public boolean run(SmithArea area, SmithLevels forcedSmithLevel) {
        this.shutdown();
        this.targetArea = area;
        this.forcedSmithLevel = forcedSmithLevel;
        this.targetRecipe = forcedSmithLevel != null
                ? this.resolveRecipe(forcedSmithLevel)
                : SmithRecipe.BRONZE_DAGGER;
        this.expectingSmithXpDrop = false;
        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn()) {
                return;
            }
            this.selectTargetRecipe();
            KspTaskDebug.throttled(log, this.debugLogging, "Smithing", "loop", 5_000L,
                    "loop | recipe={} area={} player={} moving={} animating={} interacting={} bankOpen={} smithWidgetOpen={} bars={} awaitingStart={}",
                    this.targetRecipe != null ? this.targetRecipe.getDisplayName() : "none",
                    this.targetArea.getDisplayName(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Bank.isOpen(),
                    this.isSmithingWidgetOpen(),
                    this.targetRecipe != null ? Rs2Inventory.count((String)this.getBarName(this.targetRecipe)) : 0,
                    this.awaitingSmithStartAtMs != 0L);
            if (this.targetRecipe == null) {
                this.debug("No smithing recipe available for the current level", new Object[0]);
                return;
            }
            if (!this.ensureToolAndBarsForTargetRecipe(this.targetRecipe)) {
                this.debug("Unable to prepare smithing inventory for {}", this.targetRecipe.getDisplayName());
                return;
            }
            if (!this.ensureInTargetArea()) {
                return;
            }
            this.smithAtAnvil(this.targetRecipe);
            this.debug("SmithScript active | area={} | recipe={}", this.targetArea.name(), this.targetRecipe.name());
        }, 0L, 600L, TimeUnit.MILLISECONDS);
        return true;
    }

    private void selectTargetRecipe() {
        if (this.forcedSmithLevel != null) {
            this.targetRecipe = this.resolveRecipe(this.forcedSmithLevel);
            return;
        }

        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        SmithRecipe bestRecipe = this.resolveBestCraftableRecipe(smithingLevel);
        if (bestRecipe == null) {
            bestRecipe = this.resolveBestUnlockedRecipe(smithingLevel);
        }
        if (bestRecipe != null && bestRecipe != this.targetRecipe) {
            this.targetRecipe = bestRecipe;
            this.debug("Selected smithing recipe {} at smithing level {}", this.targetRecipe.getDisplayName(), smithingLevel);
        }
    }

    private SmithRecipe resolveBestCraftableRecipe(int smithingLevel) {
        SmithLevels[] levels = SmithLevels.values();
        for (int i = levels.length - 1; i >= 0; --i) {
            SmithRecipe recipe;
            SmithLevels smithLevel = levels[i];
            if (smithingLevel < smithLevel.getRequiredLevel() || (recipe = this.resolveRecipe(smithLevel)) == null || !this.hasRequiredBarsInInventory(recipe) && this.getCraftableProductsFromBank(recipe) <= 0) continue;
            return recipe;
        }
        return null;
    }

    private SmithRecipe resolveBestUnlockedRecipe(int smithingLevel) {
        SmithLevels[] levels = SmithLevels.values();
        for (int i = levels.length - 1; i >= 0; --i) {
            SmithRecipe recipe;
            SmithLevels smithLevel = levels[i];
            if (smithingLevel < smithLevel.getRequiredLevel() || (recipe = this.resolveRecipe(smithLevel)) == null) continue;
            return recipe;
        }
        return null;
    }

    private SmithRecipe resolveRecipe(SmithLevels smithLevel) {
        try {
            return SmithRecipe.valueOf(smithLevel.name());
        }
        catch (IllegalArgumentException ex) {
            this.debug("No SmithRecipe entry found for {}", smithLevel.name());
            return null;
        }
    }

    private boolean ensureToolAndBarsForTargetRecipe(SmithRecipe recipe) {
        if (this.hasRequiredInventory(recipe)) {
            return true;
        }
        this.expectingSmithXpDrop = false;
        this.awaitingSmithStartAtMs = 0L;
        if (Rs2Bank.isOpen()) {
            return this.prepareAndCloseBank(recipe);
        }
        if (Rs2Player.isMoving() || Rs2Player.isInteracting()) {
            return false;
        }
        if (Rs2Bank.openBank()) {
            SmithScript.sleepUntil(Rs2Bank::isOpen, (int)3000);
            return false;
        }
        if (Rs2Bank.walkToBankAndUseBank()) {
            return false;
        }
        return false;
    }

    private boolean prepareAndCloseBank(SmithRecipe recipe) {
        if (!Rs2Bank.isOpen()) {
            return false;
        }
        if (!this.prepareSmithingInventory(recipe)) {
            return false;
        }
        if (!this.hasRequiredInventory(recipe)) {
            return false;
        }
        Rs2Bank.closeBank();
        SmithScript.sleepUntil(() -> !Rs2Bank.isOpen(), (int)1500);
        return false;
    }

    private boolean prepareSmithingInventory(SmithRecipe recipe) {
        int productsToWithdraw;
        String hammerName = SmithTool.HAMMER.getDisplayName();
        String barName = this.getBarName(recipe);
        int hammerItemId = SmithTool.HAMMER.getItemId();
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait()) {
            return false;
        }
        if (!KspBankMode.ensureWithdrawAsItem()) {
            this.debug("Waiting for withdraw-as-item mode before preparing smithing inventory");
            return false;
        }
        if (this.hasHammerInInventory()) {
            Rs2Bank.depositAllExcept((Integer[])new Integer[]{hammerItemId});
        } else {
            Rs2Bank.depositAll();
        }
        SmithScript.sleep((int)200);
        if (!this.hasHammerInInventory()) {
            if (!this.hasHammerInBank()) {
                this.debug("Hammer not found in bank", new Object[0]);
                return false;
            }
            if (!Rs2Bank.withdrawOne((String)hammerName)) {
                return false;
            }
            SmithScript.sleepUntil(this::hasHammerInInventory, (int)2000);
        }
        if ((productsToWithdraw = this.getProductsToWithdraw(recipe)) <= 0) {
            this.debug("Not enough bars in bank for {}", recipe.getDisplayName());
            return false;
        }
        int barsToWithdraw = productsToWithdraw * recipe.getBarRequirement();
        if (!Rs2Bank.withdrawX((String)barName, (int)barsToWithdraw)) {
            return false;
        }
        SmithScript.sleepUntil(() -> Rs2Inventory.count((String)barName) == barsToWithdraw, (int)2000);
        return Rs2Inventory.count((String)barName) == barsToWithdraw;
    }

    private int getProductsToWithdraw(SmithRecipe recipe) {
        String barName = this.getBarName(recipe);
        int slotsReserved = 1;
        int maxBarsForInventory = 28 - slotsReserved;
        int maxProductsForInventory = maxBarsForInventory / recipe.getBarRequirement();
        int maxProductsFromBank = Math.max(0, Rs2Bank.count((String)barName)) / recipe.getBarRequirement();
        return Math.min(maxProductsForInventory, maxProductsFromBank);
    }

    private boolean hasRequiredInventory(SmithRecipe recipe) {
        return this.hasHammerInInventory() && this.hasRequiredBarsInInventory(recipe);
    }

    private boolean hasHammerInInventory() {
        return Rs2Inventory.all().stream()
                .anyMatch(item -> item != null && item.getId() == SmithTool.HAMMER.getItemId());
    }

    private boolean hasHammerInBank() {
        return Rs2Bank.bankItems().stream()
                .anyMatch(item -> item != null && item.getId() == SmithTool.HAMMER.getItemId());
    }

    private boolean hasRequiredBarsInInventory(SmithRecipe recipe) {
        return Rs2Inventory.count((String)this.getBarName(recipe)) >= recipe.getBarRequirement();
    }

    private int getCraftableProductsFromBank(SmithRecipe recipe) {
        return Math.max(0, Rs2Bank.count((String)this.getBarName(recipe))) / recipe.getBarRequirement();
    }

    private String getBarName(SmithRecipe recipe) {
        String[] words = recipe.getDisplayName().split(" ");
        return words[0] + " bar";
    }

    private boolean ensureInTargetArea() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null) {
            this.debug("Cannot verify smithing area; player location is null");
            return false;
        }

        if (this.targetArea.toWorldArea().contains(playerLocation)) {
            this.clearTargetAreaWalkIfNeeded();
            return true;
        }

        this.markExistingTargetAreaWalkIfActive();

        if (Rs2Player.isMoving()) {
            return false;
        }

        if (KspWalkerGuard.walkToDestination(
                TARGET_AREA_WALK_KEY,
                this::getTargetAreaWalkPoint,
                this.targetArea.toWorldArea()::contains,
                2,
                WEB_WALK_COOLDOWN_MS)) {
            this.lastWebWalkAtMs = System.currentTimeMillis();
            this.lastWalkTarget = Rs2Walker.getCurrentTarget();
            this.walkingToTargetArea = true;
            this.debug("Requested smithing area walk | player={} walkerTarget={} area={}",
                    Rs2Player.getWorldLocation(),
                    this.lastWalkTarget,
                    this.targetArea.getDisplayName());
        } else {
            this.markExistingTargetAreaWalkIfActive();
        }
        return false;
    }


    private WorldPoint getTargetAreaWalkPoint() {
        if (this.targetArea == SmithArea.SMITH_AREA_VARROCK_WEST_ANVIL) {
            return VARROCK_WEST_ANVIL_APPROACH_POINT;
        }

        return this.targetArea.getRandomPoint();
    }

    private void markExistingTargetAreaWalkIfActive() {
        WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();

        if (this.isTargetAreaWalkerTarget(walkerTarget)) {
            this.lastWalkTarget = walkerTarget;
            this.walkingToTargetArea = true;
        }
    }

    private void clearTargetAreaWalkIfNeeded() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null || !this.targetArea.toWorldArea().contains(playerLocation)) {
            return;
        }

        WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();
        boolean shouldClearWalkerRoute = this.walkingToTargetArea
                || this.isSameDestination(walkerTarget, this.lastWalkTarget, 2);

        if (shouldClearWalkerRoute) {
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_smithing_reached_area");

            this.debug("Cleared smithing target-area walk | player={} area={} oldWalkerTarget={} lastWalkTarget={} walkingToTargetArea={}",
                    playerLocation,
                    this.targetArea.getDisplayName(),
                    walkerTarget,
                    this.lastWalkTarget,
                    this.walkingToTargetArea);
        }

        KspWalkerGuard.clear(TARGET_AREA_WALK_KEY);
        this.lastWalkTarget = null;
        this.lastWebWalkAtMs = 0L;
        this.walkingToTargetArea = false;
    }

    private boolean isTargetAreaWalkerTarget(WorldPoint walkerTarget) {
        return walkerTarget != null && this.targetArea.toWorldArea().contains(walkerTarget);
    }

    private boolean isSameDestination(WorldPoint first, WorldPoint second, int distance) {
        return first != null
                && second != null
                && first.getPlane() == second.getPlane()
                && first.distanceTo(second) <= distance;
    }

    private void smithAtAnvil(SmithRecipe recipe) {
        if (this.expectingSmithXpDrop && Rs2Player.waitForXpDrop((Skill)Skill.SMITHING, (int)7500)) {
            this.lastSmithAnimationAtMs = System.currentTimeMillis();
            return;
        }
        if (this.isSmithingWidgetOpen()) {
            this.handleSmithingSelection(recipe);
            return;
        }
        if (Rs2Player.isAnimating()) {
            this.lastSmithAnimationAtMs = System.currentTimeMillis();
            return;
        }
        if (this.isWaitingForSmithStart()) {
            return;
        }
        if (Rs2Player.isMoving()) {
            return;
        }
        long sinceLastSmithAnimation = System.currentTimeMillis() - this.lastSmithAnimationAtMs;
        if (sinceLastSmithAnimation < 1800L) {
            return;
        }
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return;
        }
        if (this.handleSmithingSelection(recipe)) {
            return;
        }
        if (!this.isIdleInTargetArea()) {
            return;
        }
        Rs2TileObjectModel anvil = this.findNearbyAnvilInTargetArea();
        if (anvil == null) {
            this.debug("No reachable anvil found inside {}", this.targetArea.getDisplayName());
            return;
        }
        if (Rs2Player.getWorldLocation().distanceTo(anvil.getWorldLocation()) > 6) {
            KspWalkerGuard.walkFastCanvasToPoint(
                    ANVIL_WALK_KEY,
                    (WorldPoint) anvil.getWorldLocation(),
                    6,
                    WEB_WALK_COOLDOWN_MS);
            return;
        }
        if (!this.isIdleInTargetArea()) {
            KspTaskDebug.throttled(log, this.debugLogging, "Smithing", "not-idle", 2_000L,
                    "waiting for idle before anvil | player={} moving={} animating={} interacting={} area={}",
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    this.targetArea.getDisplayName());
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastAnvilInteractAtMs < 2000L) {
            return;
        }
        this.debug("Attempting anvil interaction | recipe={} anvilName={} id={} loc={} reachable={} player={} distance={}",
                recipe.getDisplayName(),
                anvil.getName(),
                anvil.getId(),
                anvil.getWorldLocation(),
                anvil.isReachable(),
                Rs2Player.getWorldLocation(),
                Rs2Player.getWorldLocation() != null ? Rs2Player.getWorldLocation().distanceTo(anvil.getWorldLocation()) : -1);
        boolean interacted = anvil.click("Smith");
        this.debug("Anvil interaction result | clicked={} recipe={} loc={} player={} moving={} animating={} interacting={} smithWidgetOpen={}",
                interacted,
                recipe.getDisplayName(),
                anvil.getWorldLocation(),
                Rs2Player.getWorldLocation(),
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting(),
                this.isSmithingWidgetOpen());
        if (!interacted) {
            return;
        }
        boolean widgetOpened = SmithScript.sleepUntil(() -> this.isSmithingWidgetOpen() || Rs2Player.isMoving() || Rs2Player.isAnimating(), (int)2500);
        this.debug("Anvil post-click wait | widgetOpened={} moving={} animating={} interacting={} smithWidgetOpen={}",
                widgetOpened,
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting(),
                this.isSmithingWidgetOpen());
        if (!widgetOpened) {
            return;
        }
        this.lastAnvilInteractAtMs = now;
        this.debug("Interacted with anvil to smith {}", recipe.getDisplayName());
    }

    private boolean isIdleInTargetArea() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation != null
                && this.targetArea.toWorldArea().contains(playerLocation)
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating()
                && !Rs2Player.isInteracting();
    }

    private boolean handleSmithingSelection(SmithRecipe recipe) {
        if (!this.isSmithingWidgetOpen()) {
            return false;
        }

        this.awaitingSmithStartAtMs = 0L;

        int inventoryBarCount = Rs2Inventory.count((String)this.getBarName(recipe));
        int productCount = inventoryBarCount / recipe.getBarRequirement();
        if (productCount <= 0) {
            this.debug("No bars available for smithing {}", recipe.getDisplayName());
            return true;
        }
        if (Microbot.getVarbitPlayerValue((int)2224) < productCount) {
            this.debug("Selecting smith quantity all | currentVarbit={} productCount={}", Microbot.getVarbitPlayerValue((int)2224), productCount);
            Rs2Widget.clickWidget((int)312, (int)7);
            SmithScript.sleep((int)150);
        }

        if (!this.selectSmithingRecipe(recipe)) {
            this.debug("Failed to select smithing menu option {}", recipe.getDisplayName());
            return true;
        }
        SmithScript.sleep((int)150);
        this.awaitingSmithStartAtMs = System.currentTimeMillis();
        this.expectingSmithXpDrop = true;
        SmithScript.sleepUntil(() -> Rs2Player.isAnimating() || !this.isSmithingWidgetOpen(), (int)2500);
        return true;
    }

    private boolean selectSmithingRecipe(SmithRecipe recipe) {
        boolean selectedRecipe = Rs2Widget.clickWidget((int)312, (int)this.getSmithingChildId(recipe));
        this.debug("Smith recipe child-click | recipe={} childId={} selected={}", recipe.getDisplayName(), this.getSmithingChildId(recipe), selectedRecipe);

        if (!selectedRecipe) {
            String productName = this.getSmithingWidgetProductName(recipe);
            selectedRecipe = Rs2Widget.clickWidget((String)productName, (boolean)true)
                    || Rs2Widget.clickWidget((String)productName, (boolean)false)
                    || Rs2Widget.clickWidget((String)recipe.getDisplayName(), (boolean)true)
                    || Rs2Widget.clickWidget((String)recipe.getDisplayName(), (boolean)false);
            this.debug("Smith recipe text fallback | recipe={} productName={} selected={}", recipe.getDisplayName(), productName, selectedRecipe);
        }

        if (selectedRecipe) {
            SmithScript.sleep((int)150);
            if (this.isSmithingWidgetOpen()) {
                Rs2Keyboard.keyPress((int)32);
                SmithScript.sleep((int)150);
            }
        }

        return selectedRecipe;
    }

    private String getSmithingWidgetProductName(SmithRecipe recipe) {
        String displayName = recipe.getDisplayName();
        int firstSpace = displayName.indexOf(' ');

        if (firstSpace < 0 || firstSpace >= displayName.length() - 1) {
            return displayName;
        }

        String productName = displayName.substring(firstSpace + 1);
        return Character.toUpperCase(productName.charAt(0)) + productName.substring(1);
    }

    private boolean isSmithingWidgetOpen() {
        return Rs2Widget.isSmithingWidgetOpen() || Rs2Widget.getWidget((int)312, (int)1) != null;
    }

    private int getSmithingChildId(SmithRecipe recipe) {
        switch (recipe) {
            case BRONZE_DAGGER: {
                return 9;
            }
            case BRONZE_SCIMITAR: 
            case IRON_SCIMITAR: 
            case STEEL_SCIMITAR: {
                return 11;
            }
            case BRONZE_WARHAMMER: 
            case IRON_WARHAMMER: 
            case STEEL_WARHAMMER: {
                return 16;
            }
            case BRONZE_PLATEBODY: 
            case IRON_PLATEBODY: 
            case STEEL_PLATEBODY: {
                return 22;
            }
        }
        return 9;
    }

    private boolean isWaitingForSmithStart() {
        if (this.awaitingSmithStartAtMs == 0L) {
            return false;
        }
        if (Rs2Player.isAnimating()) {
            return true;
        }
        if (this.isSmithingWidgetOpen()) {
            this.awaitingSmithStartAtMs = 0L;
            return false;
        }
        long elapsed = System.currentTimeMillis() - this.awaitingSmithStartAtMs;
        if (elapsed < 2500L) {
            return true;
        }
        this.awaitingSmithStartAtMs = 0L;
        return false;
    }

    private Rs2TileObjectModel findNearbyAnvilInTargetArea() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }
        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withName("Anvil")
                .within(playerLocation, 12)
                .where(obj -> obj.getWorldLocation() != null && this.targetArea.toWorldArea().contains(obj.getWorldLocation()))
                .nearestReachable(12));
    }

    private void debug(String message, Object ... args) {
        if (this.debugLogging) {
            KspTaskDebug.info(log, true, "Smithing", message, args);
        }
    }

    public void shutdown() {
        this.awaitingSmithStartAtMs = 0L;
        this.lastSmithAnimationAtMs = 0L;
        this.lastAnvilInteractAtMs = 0L;
        this.lastWebWalkAtMs = 0L;
        this.lastWalkTarget = null;
        this.expectingSmithXpDrop = false;
        this.walkingToTargetArea = false;
        KspWalkerGuard.clear(TARGET_AREA_WALK_KEY);
        KspWalkerGuard.clear(ANVIL_WALK_KEY);
        super.shutdown();
    }

    public SmithArea getTargetArea() {
        return this.targetArea;
    }

    public SmithRecipe getTargetRecipe() {
        return this.targetRecipe;
    }
}
