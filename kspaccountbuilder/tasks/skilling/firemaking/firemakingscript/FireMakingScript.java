package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.firemakingscript;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspBankMode;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.fmarea.FireArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.loglevels.LogsLvl;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FireMakingScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(FireMakingScript.class);

    private static final int LOOP_DELAY_MS = 100;
    private static final int WEB_WALK_COOLDOWN_MS = 1_000;
    private static final int FIRE_INTERACT_COOLDOWN_MS = 100;
    private static final int FIRE_START_GRACE_MS = 600;
    private static final int BURN_PROMPT_ACTION_COOLDOWN_MS = 100;
    private static final int BANK_ACTION_TIMEOUT_MS = 1_500;
    private static final int CAMPFIRE_DISTANCE = 6;
    private static final int NEARBY_CAMPFIRE_SCAN_RADIUS = 12;

    private static final int NORMAL_FIRE_ID = 26185;
    private static final int FORESTERS_CAMPFIRE_ID = 49927;

    private static final String TINDERBOX_NAME = Buy.TINDERBOX_NAME;
    private static final String BURN_QUANTITY_PROMPT_TEXT = "How many would you like to burn?";
    private static final String BURN_PRODUCT_PROMPT_TEXT = "What would you like to burn?";
    private static final int PRODUCTION_WIDGET_GROUP = 270;
    private static final int PRODUCTION_TITLE_CHILD = 5;

    private FireArea targetArea = FireArea.FM_AREA_DRAYNOR_BANK;

    private long lastWebWalkAtMs;
    private long lastFireInteractAtMs;
    private long awaitingFireStartAtMs;
    private long lastBurnPromptActionAtMs;
    private long pendingBankActionAtMs;
    private int pendingBankEmptySlots = -1;

    private boolean expectingFiremakingXpDrop;
    private boolean tendingForestersCampfire;
    private boolean debugLogging;
    private boolean walkingToTargetArea;

    public void setDebugLogging(boolean debugLogging) { this.debugLogging = debugLogging; }

    public boolean run(FireArea area)
    {
        shutdown();

        this.targetArea = area;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            String targetLogName = resolveTargetLogName();

            if (targetLogName == null)
            {
                debug("No firemaking log target available for current level");
                return;
            }

            WorldPoint fireLocation = findUsableFireLocation();
            if (tendingForestersCampfire)
            {
                Rs2TileObjectModel activeCampfire = findNearestForestersCampfireInTargetArea();
                if (activeCampfire != null)
                {
                    fireLocation = activeCampfire.getWorldLocation();
                    KspTaskDebug.throttled(log, debugLogging, "Firemaking", "tending-campfire-scan", 3_000L,
                            "Tending scan found active Forester's Campfire | id={} location={} player={}",
                            activeCampfire.getId(),
                            activeCampfire.getWorldLocation(),
                            Rs2Player.getWorldLocation());
                }
                else if (!isBurnInterfaceOpen(targetLogName, getTargetLogId(targetLogName)))
                {
                    resetFireInteractionState("Forester's Campfire disappeared during tending");
                    fireLocation = findUsableFireLocation();
                }
            }
            else if (expectingFiremakingXpDrop
                    && fireLocation == null
                    && !isBurnInterfaceOpen(targetLogName, getTargetLogId(targetLogName))
                    && !Rs2Player.isAnimating())
            {
                resetFireInteractionState("active fire disappeared");
            }

            // Do not block the Firemaking loop waiting several seconds for a *future*
            // XP drop. The first log can already have produced its XP before the next
            // scheduler pass, which previously made waitForXpDrop(...) sit for the
            // full timeout while a usable fire was already on the ground.
            if (expectingFiremakingXpDrop
                    && fireLocation == null
                    && Rs2Player.isAnimating())
            {
                debug("Firemaking action still active with {}", targetLogName);
                return;
            }

            int targetLogId = getTargetLogId(targetLogName);
            KspTaskDebug.throttled(log, debugLogging, "Firemaking", "loop", 5_000L,
                    "loop | targetLog={} targetId={} area={} player={} moving={} animating={} interacting={} invLogs={} bankOpen={} burnPromptOpen={} awaitingStart={}",
                    targetLogName,
                    targetLogId,
                    targetArea.getDisplayName(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Inventory.count(targetLogName),
                    Rs2Bank.isOpen(),
                    isBurnInterfaceOpen(targetLogName, targetLogId),
                    awaitingFireStartAtMs != 0L);

            if (handleBurnPrompt(targetLogName, targetLogId))
            {
                return;
            }

            if (!ensureSupplies(targetLogName, fireLocation != null))
            {
                return;
            }

            fireLocation = findUsableFireLocation();

            if (fireLocation != null)
            {
                useCampfire(targetLogId, fireLocation);
                return;
            }

            if (!ensureInTargetArea())
            {
                return;
            }

            if (!isIdleInTargetArea())
            {
                return;
            }

            buildFire(targetLogName);

        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private String resolveTargetLogName()
    {
        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        LogsLvl bestMatch = null;

        for (LogsLvl logsLvl : LogsLvl.values())
        {
            if (firemakingLevel >= logsLvl.getRequiredLevel())
            {
                bestMatch = logsLvl;
            }
        }

        if (bestMatch == null)
        {
            return null;
        }

        if (hasLogsAvailable(bestMatch.getDisplayName()))
        {
            return bestMatch.getDisplayName();
        }

        for (int i = bestMatch.ordinal() - 1; i >= 0; i--)
        {
            LogsLvl fallbackLogs = LogsLvl.values()[i];
            if (firemakingLevel >= LogsLvl.WILLOW_LOGS.getRequiredLevel())
            {
                continue;
            }

            if (firemakingLevel >= LogsLvl.OAK_LOGS.getRequiredLevel()
                    && fallbackLogs == LogsLvl.LOGS)
            {
                continue;
            }

            String fallbackLogName = fallbackLogs.getDisplayName();

            if (hasLogsAvailable(fallbackLogName))
            {
                return fallbackLogName;
            }
        }

        return bestMatch.getDisplayName();
    }

    private boolean ensureSupplies(String targetLogName, boolean hasActiveFire)
    {
        if (hasLogsForCurrentTarget(targetLogName)
                && (hasActiveFire || Rs2Inventory.hasItem(TINDERBOX_NAME)))
        {
            if (Rs2Bank.isOpen())
            {
                Rs2Bank.closeBank();
                return false;
            }
            return true;
        }

        awaitingFireStartAtMs = 0L;
        expectingFiremakingXpDrop = false;

        if (Rs2Bank.isOpen())
        {
            return prepareSuppliesFromBank(targetLogName, hasActiveFire);
        }

        if (Rs2Bank.walkToBankAndUseBank() || Rs2Bank.openBank())
        {
            return false;
        }

        Microbot.status = "Walking to nearest bank";
        Rs2Bank.walkToBankAndUseBank();
        return false;
    }

    private boolean prepareSuppliesFromBank(String targetLogName, boolean hasActiveFire)
    {
        if (!Rs2Bank.isOpen()) return false;
        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return false;

        if (isBankActionPending()) return false;

        boolean hasItemsToDeposit = Rs2Inventory.all().stream()
                .anyMatch(item -> item != null
                        && item.getName() != null
                        && !TINDERBOX_NAME.equalsIgnoreCase(item.getName()));
        if (hasItemsToDeposit)
        {
            int beforeDeposit = Rs2Inventory.emptySlotCount();
            if (Rs2Bank.depositAllExcept(TINDERBOX_NAME)) markBankAction(beforeDeposit);
            return false;
        }

        if (!Rs2Inventory.hasItem(TINDERBOX_NAME))
        {
            if (Rs2Bank.count(TINDERBOX_NAME) <= 0) return false;
            if (!KspBankMode.ensureWithdrawAsItem()) return false;
            int before = Rs2Inventory.emptySlotCount();
            if (Rs2Bank.withdrawOne(TINDERBOX_NAME)) markBankAction(before);
            return false;
        }

        if (!Rs2Inventory.hasItem(targetLogName))
        {
            if (Rs2Bank.count(targetLogName) <= 0) return false;
            if (!KspBankMode.ensureWithdrawAsItem()) return false;
            int before = Rs2Inventory.emptySlotCount();
            if (Rs2Bank.withdrawAll(targetLogName)) markBankAction(before);
            return false;
        }

        Rs2Bank.closeBank();
        return false;
    }

    private boolean isBankActionPending()
    {
        if (pendingBankActionAtMs == 0L) return false;
        if (Rs2Inventory.emptySlotCount() != pendingBankEmptySlots) {
            pendingBankActionAtMs = 0L;
            pendingBankEmptySlots = -1;
            return false;
        }
        if (System.currentTimeMillis() - pendingBankActionAtMs < BANK_ACTION_TIMEOUT_MS) return true;
        pendingBankActionAtMs = 0L;
        pendingBankEmptySlots = -1;
        return false;
    }

    private void markBankAction(int beforeEmptySlots)
    {
        pendingBankEmptySlots = beforeEmptySlots;
        pendingBankActionAtMs = System.currentTimeMillis();
    }

    private boolean hasLogsForCurrentTarget(String targetLogName) { return Rs2Inventory.hasItem(targetLogName); }

    private boolean hasLogsAvailable(String targetLogName) { return Rs2Inventory.hasItem(targetLogName) || Rs2Bank.count(targetLogName) > 0; }

    private boolean ensureInTargetArea()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null)
        {
            return false;
        }

        WorldArea area = targetArea.toWorldArea();

        if (area.contains(playerLocation))
        {
            clearTargetAreaWalkIfNeeded();
            return true;
        }

        Microbot.status = "Walking to firemaking area";

        if (KspWalkerGuard.walkToDestination(
                "Firemaking:target-area",
                targetArea::getRandomPoint,
                area::contains,
                2,
                WEB_WALK_COOLDOWN_MS))
        {
            lastWebWalkAtMs = System.currentTimeMillis();
            walkingToTargetArea = true;
            debug("Requested firemaking area walk | player={} walkerTarget={} area={}",
                    playerLocation,
                    Rs2Walker.getCurrentTarget(),
                    targetArea.getDisplayName());
        }

        return false;
    }

    private void clearTargetAreaWalkIfNeeded()
    {
        if (!walkingToTargetArea)
        {
            return;
        }

        KspWalkerGuard.clearActiveWalker("ksp_account_builder_firemaking_reached_area");
        KspWalkerGuard.clear("Firemaking:target-area");
        walkingToTargetArea = false;
    }

    private void useCampfire(int targetLogId, WorldPoint fireLocation)
    {
        if (fireLocation == null || !isInTargetArea(fireLocation)) return;
        if (isBurnInterfaceOpen(null, targetLogId)) return;

        if (Rs2Dialogue.isInDialogue())
        {
            handleForesterCampfireDialogue();
            return;
        }

        if (Rs2Player.distanceTo(fireLocation) > CAMPFIRE_DISTANCE)
        {
            Microbot.status = "Walking to campfire";
            KspWalkerGuard.walkToPoint("Firemaking:campfire", fireLocation, CAMPFIRE_DISTANCE, WEB_WALK_COOLDOWN_MS);
            return;
        }

        Rs2TileObjectModel fire = findFireObjectAtLocation(fireLocation);
        if (fire == null || !isValidFireId(fire.getId()))
        {
            resetFireInteractionState("selected fire disappeared");
            return;
        }

        // A visible fire is the authoritative completion signal for the build step.
        awaitingFireStartAtMs = 0L;

        if (!isIdleNearCampfire(fireLocation)) return;

        long now = System.currentTimeMillis();
        if (now - lastFireInteractAtMs < FIRE_INTERACT_COOLDOWN_MS) return;

        boolean interacted;
        if (fire.getId() == FORESTERS_CAMPFIRE_ID)
        {
            interacted = fire.click("Tend-to");
            tendingForestersCampfire = interacted;
        }
        else
        {
            tendingForestersCampfire = false;
            interacted = Rs2Inventory.useItemOnObject(targetLogId, fire.getId());
        }

        if (!interacted) return;

        lastFireInteractAtMs = now;
        awaitingFireStartAtMs = now;
    }

    private void buildFire(String targetLogName)
    {
        if (!Rs2Inventory.hasItem(TINDERBOX_NAME) || isWaitingForFireStart()) return;

        WorldPoint player = Rs2Player.getWorldLocation();
        WorldArea area = targetArea.toWorldArea();
        if (player == null) return;

        if (!area.contains(player))
        {
            KspWalkerGuard.walkToDestination(
                    "Firemaking:target-area",
                    targetArea::getRandomPoint,
                    area::contains,
                    2,
                    WEB_WALK_COOLDOWN_MS);
            return;
        }

        if (Rs2Player.isAnimating()) return;

        long now = System.currentTimeMillis();
        if (now - lastFireInteractAtMs < FIRE_INTERACT_COOLDOWN_MS) return;

        if (!Rs2Inventory.combine(TINDERBOX_NAME, targetLogName)) return;

        lastFireInteractAtMs = now;
        awaitingFireStartAtMs = now;
        expectingFiremakingXpDrop = true;
        tendingForestersCampfire = false;
    }

    private boolean handleBurnPrompt(String targetLogName, int targetLogId)
    {
        if (!isBurnInterfaceOpen(targetLogName, targetLogId))
        {
            lastBurnPromptActionAtMs = 0L;
            return false;
        }

        if (isBurnPromptActionCoolingDown()) return true;

        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        lastBurnPromptActionAtMs = System.currentTimeMillis();
        awaitingFireStartAtMs = lastBurnPromptActionAtMs;
        expectingFiremakingXpDrop = true;
        return true;
    }

    private boolean isBurnPromptActionCoolingDown()
    {
        return lastBurnPromptActionAtMs != 0L
                && System.currentTimeMillis() - lastBurnPromptActionAtMs < BURN_PROMPT_ACTION_COOLDOWN_MS;
    }

    private Widget findProductionWidgetByItemId(int itemId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget productionRoot = Rs2Widget.getWidget(270, 0);

            if (productionRoot == null)
            {
                return null;
            }

            return findChildWidgetByItemId(productionRoot, itemId);
        }).orElse(null);
    }

    private Widget findChildWidgetByItemId(Widget widget, int itemId)
    {
        if (widget == null)
        {
            return null;
        }

        if (!widget.isHidden() && widget.getItemId() == itemId)
        {
            return widget;
        }

        Widget found = findChildWidgetByItemId(widget.getDynamicChildren(), itemId);
        if (found != null)
        {
            return found;
        }

        found = findChildWidgetByItemId(widget.getStaticChildren(), itemId);
        if (found != null)
        {
            return found;
        }

        return findChildWidgetByItemId(widget.getNestedChildren(), itemId);
    }

    private Widget findChildWidgetByItemId(Widget[] widgets, int itemId)
    {
        if (widgets == null)
        {
            return null;
        }

        for (Widget widget : widgets)
        {
            Widget found = findChildWidgetByItemId(widget, itemId);
            if (found != null)
            {
                return found;
            }
        }

        return null;
    }

    private boolean isBurnInterfaceOpen(String targetLogName, int targetLogId)
    {
        // Robust: search all widgets for the prompt text (case-insensitive, partial match)
        if (Rs2Widget.findWidget(BURN_QUANTITY_PROMPT_TEXT, null, false) != null
                || Rs2Widget.findWidget(BURN_PRODUCT_PROMPT_TEXT, null, false) != null)
        {
            return true;
        }

        // Fallback: search all visible widgets for the prompt text
        if (Rs2Widget.hasWidgetText("What would you like to burn?", 270, false)) {
            return true;
        }

        // Fallback: look for the quantity buttons (1, 5, 10, X, All) in group 270
        for (int child = 0; child <= 10; child++) {
            String txt = Rs2Widget.getChildWidgetText(270, child);
            if (txt != null && (txt.equals("1") || txt.equals("5") || txt.equals("10") || txt.equals("X") || txt.equals("All"))) {
                return true;
            }
        }

        Widget productWidget = findProductionWidgetByItemId(targetLogId);
        if (productWidget != null)
        {
            return true;
        }

        if (targetLogName != null && findProductionWidgetByText(targetLogName) != null)
        {
            return true;
        }

        Widget productionTitle = Rs2Widget.getWidget(PRODUCTION_WIDGET_GROUP, PRODUCTION_TITLE_CHILD);
        if (isBurnPromptTitle(productionTitle))
        {
            return true;
        }

        Widget productionRoot = Rs2Widget.getWidget(PRODUCTION_WIDGET_GROUP, 0);
        return findBurnPromptTitle(productionRoot) != null;
    }

    private Widget findProductionWidgetByText(String text)
    {
        if (text == null || text.isEmpty())
        {
            return null;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget productionRoot = Rs2Widget.getWidget(PRODUCTION_WIDGET_GROUP, 0);
            return findChildWidgetByText(productionRoot, text);
        }).orElse(null);
    }

    private Widget findChildWidgetByText(Widget widget, String text)
    {
        if (widget == null)
        {
            return null;
        }

        if (!widget.isHidden() && widget.getText() != null && widget.getText().toLowerCase().contains(text.toLowerCase()))
        {
            return widget;
        }

        Widget found = findChildWidgetByText(widget.getDynamicChildren(), text);
        if (found != null)
        {
            return found;
        }

        found = findChildWidgetByText(widget.getStaticChildren(), text);
        if (found != null)
        {
            return found;
        }

        return findChildWidgetByText(widget.getNestedChildren(), text);
    }

    private Widget findChildWidgetByText(Widget[] widgets, String text)
    {
        if (widgets == null)
        {
            return null;
        }

        for (Widget widget : widgets)
        {
            Widget found = findChildWidgetByText(widget, text);
            if (found != null)
            {
                return found;
            }
        }

        return null;
    }

    private Widget findBurnPromptTitle(Widget widget)
    {
        if (widget == null)
        {
            return null;
        }

        if (isBurnPromptTitle(widget))
        {
            return widget;
        }

        Widget found = findBurnPromptTitle(widget.getDynamicChildren());
        if (found != null)
        {
            return found;
        }

        found = findBurnPromptTitle(widget.getStaticChildren());
        if (found != null)
        {
            return found;
        }

        return findBurnPromptTitle(widget.getNestedChildren());
    }

    private Widget findBurnPromptTitle(Widget[] widgets)
    {
        if (widgets == null)
        {
            return null;
        }

        for (Widget widget : widgets)
        {
            Widget found = findBurnPromptTitle(widget);
            if (found != null)
            {
                return found;
            }
        }

        return null;
    }

    private boolean isBurnPromptTitle(Widget widget)
    {
        if (widget == null || widget.isHidden() || widget.getText() == null)
        {
            return false;
        }

        String titleText = widget.getText().toLowerCase();
        return titleText.contains("what would you like to burn")
                || titleText.contains("how many would you like to burn");
    }

    private boolean isWaitingForFireStart()
    {
        if (awaitingFireStartAtMs == 0L)
        {
            return false;
        }

        if (Rs2Player.isAnimating())
        {
            return true;
        }

        long elapsed = System.currentTimeMillis() - awaitingFireStartAtMs;

        if (elapsed < FIRE_START_GRACE_MS)
        {
            return true;
        }

        awaitingFireStartAtMs = 0L;
        return false;
    }

    private void resetFireInteractionState(String reason)
    {
        debug("Resetting fire interaction state | reason={} player={}", reason, Rs2Player.getWorldLocation());
        awaitingFireStartAtMs = 0L;
        expectingFiremakingXpDrop = false;
        tendingForestersCampfire = false;
        lastBurnPromptActionAtMs = 0L;
        KspWalkerGuard.clear("Firemaking:campfire");
    }

    private void handleForesterCampfireDialogue()
    {
        if (!Rs2Dialogue.isInDialogue()) return;
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            return;
        }
        if (Rs2Dialogue.hasSelectAnOption()) Rs2Dialogue.keyPressForDialogueOption(1);
    }

    private boolean isIdleInTargetArea()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        return player != null
                && targetArea.toWorldArea().contains(player)
                && !Rs2Player.isAnimating();
    }

    private boolean isIdleNearCampfire(WorldPoint fireLocation)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        return player != null
                && fireLocation != null
                && isInTargetArea(fireLocation)
                && Rs2Player.distanceTo(fireLocation) <= CAMPFIRE_DISTANCE
                && !Rs2Player.isAnimating();
    }

    private WorldPoint findUsableFireLocation()
    {
        Rs2TileObjectModel nearbyForestersCampfire = findNearestFireObjectNearPlayer(true);
        if (nearbyForestersCampfire != null)
        {
            debug("Found nearby Forester campfire | loc={} player={} distance={}",
                    nearbyForestersCampfire.getWorldLocation(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.distanceTo(nearbyForestersCampfire.getWorldLocation()));
            return nearbyForestersCampfire.getWorldLocation();
        }

        Rs2TileObjectModel nearbyFire = findNearestFireObjectNearPlayer(false);
        if (nearbyFire != null)
        {
            debug("Found nearby fire | id={} loc={} player={} distance={}",
                    nearbyFire.getId(),
                    nearbyFire.getWorldLocation(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.distanceTo(nearbyFire.getWorldLocation()));
            return nearbyFire.getWorldLocation();
        }

        Rs2TileObjectModel areaFire = findNearestFireObjectInTargetArea();
        return areaFire != null ? areaFire.getWorldLocation() : null;
    }

    private Rs2TileObjectModel findNearestFireObjectNearPlayer(boolean forestersOnly)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (playerLocation == null)
        {
            return null;
        }

        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .within(playerLocation, NEARBY_CAMPFIRE_SCAN_RADIUS)
                .where(object -> object.getWorldLocation() != null
                        && isValidFireId(object.getId())
                        && isInTargetArea(object.getWorldLocation())
                        && (!forestersOnly || object.getId() == FORESTERS_CAMPFIRE_ID))
                .nearest(playerLocation, NEARBY_CAMPFIRE_SCAN_RADIUS));
    }

    private Rs2TileObjectModel findNearestFireObjectInTargetArea()
    {
        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .within(getAreaCenter(), getAreaSearchRadius())
                .where(object -> object.getWorldLocation() != null
                        && isInTargetArea(object.getWorldLocation())
                        && isValidFireId(object.getId()))
                .nearest(getAreaCenter(), getAreaSearchRadius()));
    }

    private Rs2TileObjectModel findNearestForestersCampfireInTargetArea()
    {
        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .within(getAreaCenter(), getAreaSearchRadius())
                .where(object -> object.getWorldLocation() != null
                        && isInTargetArea(object.getWorldLocation())
                        && object.getId() == FORESTERS_CAMPFIRE_ID)
                .nearest(getAreaCenter(), getAreaSearchRadius()));
    }

    private Rs2TileObjectModel findFireObjectAtLocation(WorldPoint location)
    {
        if (location == null)
        {
            return null;
        }

        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .where(object -> object.getWorldLocation() != null
                        && location.equals(object.getWorldLocation())
                        && isInTargetArea(object.getWorldLocation())
                        && isValidFireId(object.getId()))
                .first());
    }

    private boolean isInTargetArea(WorldPoint point) { return point != null && targetArea.toWorldArea().contains(point); }

    private boolean isValidFireId(int objectId) { return objectId == NORMAL_FIRE_ID || objectId == FORESTERS_CAMPFIRE_ID; }

    private WorldPoint getAreaCenter()
    {
        int centerX = (targetArea.getSouthWest().getX() + targetArea.getNorthEast().getX()) / 2;
        int centerY = (targetArea.getSouthWest().getY() + targetArea.getNorthEast().getY()) / 2;
        int plane = targetArea.getSouthWest().getPlane();

        return new WorldPoint(centerX, centerY, plane);
    }

    private int getAreaSearchRadius()
    {
        return Math.max(
                Math.abs(targetArea.getNorthEast().getX() - targetArea.getSouthWest().getX()),
                Math.abs(targetArea.getNorthEast().getY() - targetArea.getSouthWest().getY())) + 4;
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            KspTaskDebug.info(log, true, "Firemaking", message, args);
        }
    }

    private int getTargetLogId(String targetLogName)
    {
        if (LogsLvl.OAK_LOGS.getDisplayName().equalsIgnoreCase(targetLogName))
        {
            return 1521;
        }

        if (LogsLvl.WILLOW_LOGS.getDisplayName().equalsIgnoreCase(targetLogName))
        {
            return 1519;
        }

        return 1511;
    }

    @Override
    public void shutdown()
    {
        lastWebWalkAtMs = 0L;
        lastFireInteractAtMs = 0L;
        pendingBankActionAtMs = 0L;
        pendingBankEmptySlots = -1;
        awaitingFireStartAtMs = 0L;
        lastBurnPromptActionAtMs = 0L;
        expectingFiremakingXpDrop = false;
        tendingForestersCampfire = false;
        walkingToTargetArea = false;
        KspWalkerGuard.clear("Firemaking:target-area");
        KspWalkerGuard.clear("Firemaking:campfire");

        super.shutdown();
    }

    public FireArea getTargetArea() { return targetArea; }
}
