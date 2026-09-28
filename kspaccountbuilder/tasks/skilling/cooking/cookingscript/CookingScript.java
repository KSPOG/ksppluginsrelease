package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.cookingscript;

import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.levels.CookLevels;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CookingScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(CookingScript.class);
    private static final String WALK_KEY = "Cooking:target-area";
    private static final String EXIT_WALK_KEY = "Cooking:exit-area";
    private static final int LOOP_DELAY_MS = 600;
    private static final int WALK_COOLDOWN_MS = 3_000;
    private static final int EDGEVILLE_STOVE_ID = 12269;
    private static final int LUMBRIDGE_RANGE_ID = 114;
    private static final int COOKING_EXIT_DOOR_ID = 1535;
    private static final int PRODUCTION_WIDGET_GROUP = 270;
    private static final int PRODUCTION_WIDGET_CONTAINER_CHILD = 13;
    private static final WorldPoint EDGEVILLE_COOKING_TILE = new WorldPoint(3079, 3494, 0);
    private static final WorldPoint LUMBRIDGE_KITCHEN_ENTRY_TILE = new WorldPoint(3208, 3214, 0);
    private static final WorldPoint LUMBRIDGE_RANGE_LOCATION = new WorldPoint(3212, 3215, 0);
    private static final WorldPoint COOKING_EXIT_DOOR_POINT = new WorldPoint(3079, 3497, 0);
    private static final WorldPoint COOKING_EXIT_OUTSIDE_POINT = new WorldPoint(3080, 3498, 0);
    private static final long DOOR_INTERACTION_COOLDOWN_MS = 2_000L;

    private volatile Areas targetArea = Areas.EDGEVILLE_RANGE;
    private volatile CookingState state = CookingState.WAITING;
    private boolean debugLogging;
    private boolean expectingXpDrop;
    private long lastDoorInteractionAtMs;

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public boolean run(Areas area)
    {
        shutdown();
        targetArea = resolveCookingArea(area);
        state = CookingState.CHECKING_SUPPLIES;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }

            CookLevels fish = resolveBestAvailableFish();
            KspTaskDebug.throttled(log, debugLogging, "Cooking", "loop", 5_000L,
                    "loop | fish={} area={} player={} moving={} animating={} interacting={} bankOpen={}",
                    fish,
                    targetArea.getDisplayName(),
                    Rs2Player.getWorldLocation(),
                    Rs2Player.isMoving(),
                    Rs2Player.isAnimating(),
                    Rs2Player.isInteracting(),
                    Rs2Bank.isOpen());

            if (fish == null)
            {
                state = CookingState.WAITING;
                Microbot.status = "No raw fish available to cook";
                return;
            }

            if (!Rs2Inventory.hasItem(fish.getRawItemName()))
            {
                state = CookingState.BANKING;
                bankForFish(fish);
                return;
            }

            if (!ensureInCookingArea())
            {
                state = CookingState.WALKING_TO_AREA;
                return;
            }

            if (expectingXpDrop && Rs2Player.waitForXpDrop(Skill.COOKING, 4_500))
            {
                state = CookingState.COOKING;
                return;
            }

            if (Rs2Widget.findWidget("How many would you like to cook?", null, false) != null)
            {
                state = CookingState.OPENING_COOKING_INTERFACE;
                Microbot.status = "Selecting " + fish.getCookedItemName();
                if (selectProductionOption(fish.getCookedItemName()))
                {
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                    expectingXpDrop = true;
                    state = CookingState.COOKING;
                    sleepUntil(() -> Rs2Player.isAnimating()
                            || Rs2Widget.findWidget(
                                    "How many would you like to cook?", null, false) == null, 2_000);
                }
                return;
            }

            if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting())
            {
                return;
            }

            Rs2TileObjectModel stove = findStove();
            if (stove == null)
            {
                state = CookingState.WAITING;
                Microbot.status = "Waiting for cooking stove";
                return;
            }

            state = CookingState.OPENING_COOKING_INTERFACE;
            Microbot.status = "Cooking " + fish.getCookedItemName();
            if (stove.click("Cook"))
            {
                sleepUntil(() -> Rs2Widget.findWidget(
                        "How many would you like to cook?", null, false) != null, 3_000);
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private CookLevels resolveBestAvailableFish()
    {
        int cookingLevel = Microbot.getClient().getRealSkillLevel(Skill.COOKING);
        CookLevels[] levels = CookLevels.values();
        for (int i = levels.length - 1; i >= 0; i--)
        {
            CookLevels fish = levels[i];
            if (cookingLevel < fish.getRequiredLevel())
            {
                continue;
            }

            int available = Rs2Inventory.count(fish.getRawItemName())
                    + Math.max(0, Rs2Bank.count(fish.getRawItemName()));
            if (available > 0)
            {
                return fish;
            }
        }
        return null;
    }

    private void bankForFish(CookLevels fish)
    {
        if (openCookingAreaExitDoor())
        {
            return;
        }

        if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Bank.openBank())
        {
            return;
        }
        if (!Rs2Bank.isOpen() || KspBankWidgetHelper.closeBankTutorialOverlayIfOpenAndWait())
        {
            return;
        }

        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty, 2_000);
        Rs2Bank.withdrawAll(fish.getRawItemName());
        sleepUntil(() -> Rs2Inventory.hasItem(fish.getRawItemName()), 3_000);
        Rs2Bank.closeBank();
        expectingXpDrop = false;
    }

    private boolean openCookingAreaExitDoor()
    {
        if (targetArea != Areas.EDGEVILLE_RANGE)
        {
            KspWalkerGuard.clear(EXIT_WALK_KEY);
            lastDoorInteractionAtMs = 0L;
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (!targetArea.getArea().contains(playerLocation))
        {
            KspWalkerGuard.clear(EXIT_WALK_KEY);
            lastDoorInteractionAtMs = 0L;
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastDoorInteractionAtMs < DOOR_INTERACTION_COOLDOWN_MS)
        {
            walkThroughCookingAreaExit();
            return true;
        }

        Rs2TileObjectModel door = Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withId(COOKING_EXIT_DOOR_ID)
                .within(COOKING_EXIT_DOOR_POINT, 2)
                .nearestOnClientThread();

        if (door == null || !door.click("Open"))
        {
            walkThroughCookingAreaExit();
            return true;
        }

        lastDoorInteractionAtMs = now;
        KspWalkerGuard.clearActiveWalker("ksp_cooking_opening_exit_door");
        Microbot.status = "Opening cooking area door";
        debug("Opened cooking area exit door | doorId={} door={} player={}",
                COOKING_EXIT_DOOR_ID,
                COOKING_EXIT_DOOR_POINT,
                playerLocation);
        return true;
    }

    private void walkThroughCookingAreaExit()
    {
        if (Rs2Player.isMoving())
        {
            return;
        }

        Microbot.status = "Leaving cooking area";
        KspWalkerGuard.walkFastCanvasToPoint(
                EXIT_WALK_KEY,
                COOKING_EXIT_OUTSIDE_POINT,
                1,
                1_000L);
    }

    private boolean ensureInCookingArea()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (targetArea.getArea().contains(playerLocation))
        {
            KspWalkerGuard.clearReachedDestination(
                    WALK_KEY,
                    "ksp_cooking_reached_cooking_area");
            KspWalkerGuard.clearActiveWalker("ksp_cooking_inside_cooking_area");
            return true;
        }

        if (!Rs2Player.isMoving())
        {
            Microbot.status = "Walking to " + targetArea.getDisplayName();
            KspWalkerGuard.walkToDestination(
                    WALK_KEY,
                    this::getCookingTile,
                    targetArea.getArea()::contains,
                    1,
                    WALK_COOLDOWN_MS);
        }
        return false;
    }

    private Rs2TileObjectModel findStove()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return null;
        }

        if (targetArea == Areas.LUMBRIDGE_KITCHEN)
        {
            Rs2TileObjectModel range = Microbot.getRs2TileObjectCache().query()
                    .fromWorldView()
                    .withId(LUMBRIDGE_RANGE_ID)
                    .within(LUMBRIDGE_RANGE_LOCATION, 3)
                    .nearestOnClientThread();

            if (range == null)
            {
                KspTaskDebug.throttled(log, debugLogging, "Cooking", "lumbridge-range-not-found", 5_000L,
                        "Lumbridge range not found | expectedId={} expectedLocation={} player={}",
                        LUMBRIDGE_RANGE_ID,
                        LUMBRIDGE_RANGE_LOCATION,
                        playerLocation);
            }

            return range;
        }

        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .withId(EDGEVILLE_STOVE_ID)
                .within(playerLocation, 12)
                .where(object -> targetArea.getArea().contains(object.getWorldLocation()))
                .nearestReachable(12));
    }

    private Areas resolveCookingArea(Areas requestedArea)
    {
        if (Rs2Player.getQuestState(Quest.COOKS_ASSISTANT) == QuestState.FINISHED)
        {
            return Areas.LUMBRIDGE_KITCHEN;
        }
        return requestedArea != null ? requestedArea : Areas.EDGEVILLE_RANGE;
    }

    private WorldPoint getCookingTile()
    {
        return targetArea == Areas.LUMBRIDGE_KITCHEN
                ? LUMBRIDGE_KITCHEN_ENTRY_TILE
                : EDGEVILLE_COOKING_TILE;
    }

    private boolean selectProductionOption(String itemName)
    {
        boolean selected = Rs2Widget.clickWidget(
                itemName,
                Optional.of(PRODUCTION_WIDGET_GROUP),
                PRODUCTION_WIDGET_CONTAINER_CHILD,
                false);
        if (!selected)
        {
            selected = Rs2Widget.clickWidget(itemName, true)
                    || Rs2Widget.clickWidget(itemName, false);
        }

        if (selected)
        {
            sleep(150);
        }

        debug("Production widget selection | item={} selected={} productionOpen={}",
                itemName,
                selected,
                Rs2Widget.isProductionWidgetOpen());
        return selected;
    }

    public Areas getTargetArea()
    {
        return targetArea;
    }

    public CookingState getState()
    {
        return state;
    }

    private void debug(String message, Object... args)
    {
        if (debugLogging)
        {
            KspTaskDebug.info(log, true, "Cooking", message, args);
        }
    }

    @Override
    public void shutdown()
    {
        state = CookingState.WAITING;
        expectingXpDrop = false;
        lastDoorInteractionAtMs = 0L;
        KspWalkerGuard.clear(EXIT_WALK_KEY);
        KspWalkerGuard.clear(WALK_KEY);
        super.shutdown();
    }
}
