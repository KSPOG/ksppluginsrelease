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

    private static final int LOOP_DELAY_MS = 600;
    private static final int WEB_WALK_COOLDOWN_MS = 3_000;
    private static final int FIRE_INTERACT_COOLDOWN_MS = 2_000;
    private static final int FIRE_START_GRACE_MS = 2_500;
    private static final int BURN_PROMPT_ACTION_COOLDOWN_MS = 3_000;
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

    private boolean expectingFiremakingXpDrop;
    private boolean tendingForestersCampfire;
    private boolean debugLogging;
    private boolean walkingToTargetArea;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

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
                    && !Rs2Player.isAnimating()
                    && !Rs2Player.isInteracting())
            {
                resetFireInteractionState("active fire disappeared");
            }

            if (expectingFiremakingXpDrop && Rs2Player.waitForXpDrop(Skill.FIREMAKING, 4500))
            {
                debug("Firemaking in progress with {}", targetLogName);
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
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        if (KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait())
        {
            return false;
        }

        Rs2Bank.depositAllExcept(TINDERBOX_NAME);

        sleep(200);

        if (!Rs2Inventory.hasItem(TINDERBOX_NAME))
        {
            if (Rs2Bank.count(TINDERBOX_NAME) <= 0)
            {
                debug("No tinderbox available in bank");
                return false;
            }

            if (!KspBankMode.ensureWithdrawAsItem())
            {
                debug("Waiting for withdraw-as-item mode before withdrawing {}", TINDERBOX_NAME);
                return false;
            }

            if (!Rs2Bank.withdrawOne(TINDERBOX_NAME))
            {
                return false;
            }

            sleepUntil(() -> Rs2Inventory.hasItem(TINDERBOX_NAME), 2_000);
        }

        if (Rs2Bank.count(targetLogName) <= 0)
        {
            debug("No {} available in bank", targetLogName);
            return false;
        }

        if (!KspBankMode.ensureWithdrawAsItem())
        {
            debug("Waiting for withdraw-as-item mode before withdrawing {}", targetLogName);
            return false;
        }

        if (!Rs2Bank.withdrawAll(targetLogName))
        {
            return false;
        }

        sleepUntil(() -> Rs2Inventory.hasItem(targetLogName), 2_000);

        if (!Rs2Inventory.hasItem(targetLogName))
        {
            debug("Failed to withdraw {}", targetLogName);
            return false;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 1_500);

        return false;
    }

    private boolean hasLogsForCurrentTarget(String targetLogName)
    {
        return Rs2Inventory.hasItem(targetLogName);
    }

    private boolean hasLogsAvailable(String targetLogName)
    {
        return Rs2Inventory.hasItem(targetLogName) || Rs2Bank.count(targetLogName) > 0;
    }

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

        if (Rs2Player.isMoving())
        {
            return false;
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
        if (fireLocation == null)
        {
            debug("Cannot use campfire; fire location is null");
            return;
        }

        if (!isInTargetArea(fireLocation))
        {
            debug("Skipping campfire outside target area | fire={} area={}", fireLocation, targetArea.getDisplayName());
            KspWalkerGuard.clear("Firemaking:campfire");
            return;
        }

        // Early return if burn prompt is already open - don't spam click
        if (isBurnInterfaceOpen(null, targetLogId))
        {
            debug("Burn prompt already open, skipping campfire click");
            return;
        }

        if (Rs2Dialogue.isInDialogue())
        {
            debug("Forester campfire dialogue is open; resolving it before retrying");
            handleForesterCampfireDialogue();
            return;
        }

        if (Rs2Player.distanceTo(fireLocation) > CAMPFIRE_DISTANCE)
        {
            Microbot.status = "Walking to campfire";
            debug("Walking to campfire | player={} fire={} distance={}", Rs2Player.getWorldLocation(), fireLocation, Rs2Player.distanceTo(fireLocation));
            KspWalkerGuard.walkToPoint("Firemaking:campfire", fireLocation, CAMPFIRE_DISTANCE, WEB_WALK_COOLDOWN_MS);
            return;
        }

        if (isWaitingForFireStart())
        {
            return;
        }

        if (!isIdleNearCampfire(fireLocation))
        {
            KspTaskDebug.throttled(log, debugLogging, "Firemaking", "not-idle", 2_000L,
                    "waiting for idle before fire interaction | player={} fire={} distance={} moving={} animating={} interacting={} area={}",
                    Rs2Player.getWorldLocation(),
                    fireLocation,
                    Rs2Player.distanceTo(fireLocation),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    targetArea.getDisplayName());
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastFireInteractAtMs < FIRE_INTERACT_COOLDOWN_MS)
        {
            return;
        }

        Rs2TileObjectModel fireTile = findFireObjectAtLocation(fireLocation);

        if (fireTile == null || !isValidFireId(fireTile.getId()))
        {
            debug("No valid fire tile at location | location={} tile={} targetLogId={}", fireLocation, fireTile, targetLogId);
            resetFireInteractionState("selected fire disappeared");
            return;
        }

        boolean interacted;

        if (fireTile.getId() == FORESTERS_CAMPFIRE_ID)
        {
            debug("Attempting Forester campfire interaction | id={} loc={} action=Tend-to player={} targetLogId={}",
                    fireTile.getId(),
                    fireLocation,
                    Rs2Player.getWorldLocation(),
                    targetLogId);
            interacted = fireTile.click("Tend-to");
            if (interacted)
            {
                tendingForestersCampfire = true;
            }
        }
        else
        {
            tendingForestersCampfire = false;
            debug("Attempting fire item-on-object | id={} loc={} targetLogId={} player={}",
                    fireTile.getId(),
                    fireLocation,
                    targetLogId,
                    Rs2Player.getWorldLocation());
            interacted = Rs2Inventory.interact(targetLogId, "Use");
            if (interacted)
            {
                sleep(150);
                interacted = fireTile.click("Use");
            }
        }

        debug("Fire interaction result | clicked={} fireId={} loc={} player={} moving={} animating={} interacting={} burnPromptOpen={}",
                interacted,
                fireTile.getId(),
                fireLocation,
                Rs2Player.getWorldLocation(),
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting(),
                isBurnInterfaceOpen(null, targetLogId));

        if (!interacted)
        {
            return;
        }

        lastFireInteractAtMs = now;
        awaitingFireStartAtMs = now;

        boolean promptOpened = sleepUntil(() -> !Rs2Player.isMoving() && isBurnInterfaceOpen(null, targetLogId), 5_000);
        debug("Fire post-click wait | promptOpened={} moving={} animating={} interacting={} burnPromptOpen={}",
                promptOpened,
                Rs2Player.isMoving(),
                Rs2Player.isAnimating(),
                Rs2Player.isInteracting(),
                isBurnInterfaceOpen(null, targetLogId));
    }

    private void buildFire(String targetLogName)
    {
        if (!Rs2Inventory.hasItem(TINDERBOX_NAME))
        {
            debug("Missing tinderbox for fallback firemaking");
            return;
        }

        if (isWaitingForFireStart())
        {
            return;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldArea area = targetArea.toWorldArea();

        if (playerLocation == null || !isIdleInTargetArea())
        {
            return;
        }

        if (!area.contains(playerLocation))
        {
            Microbot.status = "Walking to firemaking area";
            KspWalkerGuard.walkToDestination(
                    "Firemaking:target-area",
                    targetArea::getRandomPoint,
                    area::contains,
                    2,
                    WEB_WALK_COOLDOWN_MS);
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastFireInteractAtMs < FIRE_INTERACT_COOLDOWN_MS)
        {
            return;
        }

        Rs2Inventory.combine(TINDERBOX_NAME, targetLogName);
        debug("Attempting tinderbox/log combine | tinderbox={} log={} player={}", TINDERBOX_NAME, targetLogName, Rs2Player.getWorldLocation());

        lastFireInteractAtMs = now;
        awaitingFireStartAtMs = now;
        expectingFiremakingXpDrop = true;
        tendingForestersCampfire = false;

        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), FIRE_START_GRACE_MS);
    }

    private boolean handleBurnPrompt(String targetLogName, int targetLogId)
    {
        if (!isBurnInterfaceOpen(targetLogName, targetLogId))
        {
            lastBurnPromptActionAtMs = 0L;
            return false;
        }

        if (Rs2Player.isMoving())
        {
            return true;
        }

        if (isBurnPromptActionCoolingDown())
        {
            KspTaskDebug.throttled(log, debugLogging, "Firemaking", "burn-prompt-action-cooldown", 1_000L,
                    "waiting after burn prompt VK_SPACE | targetLog={} targetId={} elapsed={}ms promptOpen={}",
                    targetLogName,
                    targetLogId,
                    System.currentTimeMillis() - lastBurnPromptActionAtMs,
                    isBurnInterfaceOpen(targetLogName, targetLogId));
            return true;
        }

        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        lastBurnPromptActionAtMs = System.currentTimeMillis();
        debug("Burn prompt handled with VK_SPACE | targetLog={} targetId={} promptStillOpen={}",
                targetLogName,
                targetLogId,
                isBurnInterfaceOpen(targetLogName, targetLogId));

        awaitingFireStartAtMs = lastBurnPromptActionAtMs;
        expectingFiremakingXpDrop = true;

        sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting() || !isBurnInterfaceOpen(targetLogName, targetLogId), 2_000);

        if (!isBurnInterfaceOpen(targetLogName, targetLogId))
        {
            lastBurnPromptActionAtMs = 0L;
        }

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

        if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
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
        if (!Rs2Dialogue.isInDialogue())
        {
            return;
        }

        if (Rs2Dialogue.hasContinue())
        {
            debug("Clicking continue in Forester campfire dialogue");
            Rs2Dialogue.clickContinue();
            sleepUntil(() -> !Rs2Dialogue.isInDialogue(), 2_000);
            return;
        }

        if (Rs2Dialogue.hasSelectAnOption())
        {
            debug("Selecting first option in Forester campfire dialogue");
            Rs2Dialogue.keyPressForDialogueOption(1);
            sleepUntil(() -> !Rs2Dialogue.isInDialogue(), 2_000);
        }
    }

    private boolean isIdleInTargetArea()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation != null
                && targetArea.toWorldArea().contains(playerLocation)
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating()
                && !Rs2Player.isInteracting();
    }

    private boolean isIdleNearCampfire(WorldPoint fireLocation)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation != null
                && fireLocation != null
                && isInTargetArea(fireLocation)
                && Rs2Player.distanceTo(fireLocation) <= CAMPFIRE_DISTANCE
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating()
                && !Rs2Player.isInteracting();
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

    private boolean isInTargetArea(WorldPoint point)
    {
        return point != null && targetArea.toWorldArea().contains(point);
    }

    private boolean isValidFireId(int objectId)
    {
        return objectId == NORMAL_FIRE_ID || objectId == FORESTERS_CAMPFIRE_ID;
    }

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
        awaitingFireStartAtMs = 0L;
        lastBurnPromptActionAtMs = 0L;
        expectingFiremakingXpDrop = false;
        tendingForestersCampfire = false;
        walkingToTargetArea = false;
        KspWalkerGuard.clear("Firemaking:target-area");
        KspWalkerGuard.clear("Firemaking:campfire");

        super.shutdown();
    }

    public FireArea getTargetArea()
    {
        return targetArea;
    }
}
