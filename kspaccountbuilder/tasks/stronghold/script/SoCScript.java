package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.script;

import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.inventory.InvFood;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.StrongholdAnswer;

@Singleton
public class SoCScript extends Script
{
    private static final int LOOP_DELAY_MS = 600;
    private static final int WALK_REFIRE_MS = 3_000;
    private static final int HEAL_PERCENT = 55;
    private static final int GIFT_OF_PEACE_OBJECT_ID = 20_656;
    private static final int GRAIN_OF_PLENTY_OBJECT_ID = 1_900;
    private static final int BOX_OF_HEALTH_OBJECT_ID = 23_709;
    private static final WorldPoint GIFT_OF_PEACE_OBJECT = new WorldPoint(1907, 5222, 0);
    private static final WorldPoint GRAIN_OF_PLENTY_OBJECT = new WorldPoint(2021, 5215, 0);
    private static final WorldPoint BOX_OF_HEALTH_OBJECT = new WorldPoint(2144, 5280, 0);
    private static final WorldPoint GATE_OF_WAR_SOUTH_TILE = new WorldPoint(1859, 5238, 0);
    private static final WorldPoint GATE_OF_WAR_ADVANCE_TILE = new WorldPoint(1862, 5236, 0);
    private static final String WALK_KEY = "Stronghold of Security:destination";
    private static final String GATE_OF_WAR_WALK_KEY = "Stronghold of Security:Gate of War";
    private static final String[] REWARD_ACTIONS = {"Open", "Search", "Use"};

    private final InvFood invFood = new InvFood();
    private boolean giftOfPeaceComplete;
    private boolean grainOfPlentyComplete;
    private boolean boxOfHealthComplete;
    private boolean rewardDialogueSeen;
    private boolean rewardInteractionStarted;
    private boolean securityDoorDialogueActive;
    private boolean gateOfWarAdvancePending;
    private Areas pendingLadderArea;
    private boolean complete;

    public boolean run()
    {
        shutdown();
        Microbot.status = "Preparing Stronghold of Security";
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (!super.run() || !Microbot.isLoggedIn() || complete)
            {
                return;
            }

            updateProgressFromCurrentArea();

            if (handleGateOfWarCrossing())
            {
                return;
            }

            if (finishSecurityDoorDialogue())
            {
                return;
            }

            if (handleDialogue())
            {
                return;
            }

            if (Rs2Player.eatAt(HEAL_PERCENT))
            {
                return;
            }

            if (!invFood.hasRequiredFood() && !isInsideStrongholdRewardArea())
            {
                Microbot.status = "Preparing Stronghold food";
                invFood.prepare();
                return;
            }

            if (boxOfHealthComplete)
            {
                returnToBank();
                return;
            }

            if (pendingLadderArea != null)
            {
                handlePendingLadderDescent();
                return;
            }

            if (!giftOfPeaceComplete)
            {
                handleSegment(
                        Areas.GIFT_OF_PEACE,
                        GIFT_OF_PEACE_OBJECT_ID,
                        GIFT_OF_PEACE_OBJECT,
                        () -> giftOfPeaceComplete = true);
                return;
            }

            if (!grainOfPlentyComplete)
            {
                handleSegment(
                        Areas.GRAIN_OF_PLENTY,
                        GRAIN_OF_PLENTY_OBJECT_ID,
                        GRAIN_OF_PLENTY_OBJECT,
                        () -> grainOfPlentyComplete = true);
                return;
            }

            handleSegment(
                    Areas.BOX_OF_HEALTH,
                    BOX_OF_HEALTH_OBJECT_ID,
                    BOX_OF_HEALTH_OBJECT,
                    () -> boxOfHealthComplete = true);
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean handleGateOfWarCrossing()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        if (location == null)
        {
            return false;
        }

        if (!gateOfWarAdvancePending
                && location.getPlane() == GATE_OF_WAR_SOUTH_TILE.getPlane()
                && location.distanceTo(GATE_OF_WAR_SOUTH_TILE) <= 1
                && location.getY() <= GATE_OF_WAR_SOUTH_TILE.getY())
        {
            gateOfWarAdvancePending = true;
            KspWalkerGuard.clear(WALK_KEY);
            KspWalkerGuard.clearActiveWalker("ksp_stronghold_gate_of_war_crossed");
        }

        if (!gateOfWarAdvancePending)
        {
            return false;
        }

        if (location.distanceTo(GATE_OF_WAR_ADVANCE_TILE) <= 1)
        {
            gateOfWarAdvancePending = false;
            KspWalkerGuard.clearReachedDestination(
                    GATE_OF_WAR_WALK_KEY,
                    "ksp_stronghold_gate_of_war_advanced");
            return false;
        }

        Microbot.status = "Moving past Gate of War";
        KspWalkerGuard.walkFastCanvasToPoint(
                GATE_OF_WAR_WALK_KEY,
                GATE_OF_WAR_ADVANCE_TILE,
                1,
                WALK_REFIRE_MS);
        return true;
    }

    private void handleSegment(Areas area, int objectId, WorldPoint objectLocation, Runnable markComplete)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (!area.getArea().contains(playerLocation))
        {
            resetRewardInteraction();
            Microbot.status = "Walking to " + area.getDisplayName();
            KspWalkerGuard.walkToDestination(
                    WALK_KEY,
                    area.getArea()::getRandomPoint,
                    area.getArea()::contains,
                    2,
                    WALK_REFIRE_MS);
            return;
        }

        KspWalkerGuard.clear(WALK_KEY);
        if (!rewardInteractionStarted)
        {
            Microbot.status = "Claiming " + area.getDisplayName();
            if (interactWithExactObject(objectId, objectLocation))
            {
                rewardInteractionStarted = true;
                sleepUntil(Rs2Dialogue::isInDialogue, 3_000);
            }
            return;
        }

        if (Rs2Dialogue.isInDialogue())
        {
            rewardDialogueSeen = true;
            handleDialogue();
            return;
        }

        if (!rewardDialogueSeen)
        {
            rewardInteractionStarted = false;
            return;
        }

        markComplete.run();
        resetRewardInteraction();
        if (area != Areas.BOX_OF_HEALTH)
        {
            pendingLadderArea = area;
        }
    }

    private boolean interactWithExactObject(int objectId, WorldPoint location)
    {
        TileObject object = Rs2GameObject.findObjectByLocation(location);
        if (object == null || object.getId() != objectId)
        {
            KspWalkerGuard.walkToPoint(WALK_KEY, location, 1, WALK_REFIRE_MS);
            return false;
        }

        for (String action : REWARD_ACTIONS)
        {
            if (Rs2GameObject.interact(object, action))
            {
                return true;
            }
        }
        return Rs2GameObject.interact(object);
    }

    private boolean handleDialogue()
    {
        if (!Rs2Dialogue.isInDialogue())
        {
            return false;
        }

        if (rewardInteractionStarted)
        {
            rewardDialogueSeen = true;
        }
        else
        {
            securityDoorDialogueActive = true;
        }

        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            sleep(150, 300);
            return true;
        }

        if (!Rs2Dialogue.hasSelectAnOption())
        {
            return true;
        }

        String question = Rs2Dialogue.getQuestion();
        String answer = question == null ? null : StrongholdAnswer.findAnswer(question);
        if (answer != null && Rs2Dialogue.clickOption(answer, true))
        {
            sleep(150, 300);
            return true;
        }

        Microbot.status = "Unknown Stronghold security question";
        return true;
    }

    private boolean finishSecurityDoorDialogue()
    {
        if (!securityDoorDialogueActive || Rs2Dialogue.isInDialogue())
        {
            return false;
        }

        securityDoorDialogueActive = false;
        KspWalkerGuard.clear(WALK_KEY);
        KspWalkerGuard.clearActiveWalker("ksp_stronghold_security_door_crossed");
        Microbot.status = "Continuing through Stronghold";
        return true;
    }

    private void handlePendingLadderDescent()
    {
        if (pendingLadderArea == null)
        {
            return;
        }

        if (!pendingLadderArea.getArea().contains(Rs2Player.getWorldLocation()))
        {
            pendingLadderArea = null;
            return;
        }

        Microbot.status = "Descending Stronghold ladder";
        boolean interacted = Microbot.getRs2TileObjectCache()
                .query()
                .fromWorldView()
                .withName("Ladder")
                .interact("Climb-down");
        if (interacted)
        {
            sleepUntil(() -> !pendingLadderArea.getArea().contains(Rs2Player.getWorldLocation()), 5_000);
        }
    }

    private void returnToBank()
    {
        if (Rs2Bank.isOpen())
        {
            complete = true;
            Microbot.status = "Stronghold of Security Complete";
            return;
        }

        Microbot.status = "Returning to bank";
        Rs2Bank.walkToBankAndUseBank();
    }

    private void updateProgressFromCurrentArea()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        if (Areas.BOX_OF_HEALTH.getArea().contains(location))
        {
            giftOfPeaceComplete = true;
            grainOfPlentyComplete = true;
        }
        else if (Areas.GRAIN_OF_PLENTY.getArea().contains(location))
        {
            giftOfPeaceComplete = true;
        }
    }

    private boolean isInsideStrongholdRewardArea()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        for (Areas area : Areas.values())
        {
            if (area.getArea().contains(location))
            {
                return true;
            }
        }
        return false;
    }

    private void resetRewardInteraction()
    {
        rewardDialogueSeen = false;
        rewardInteractionStarted = false;
    }

    @Override
    public void shutdown()
    {
        giftOfPeaceComplete = false;
        grainOfPlentyComplete = false;
        boxOfHealthComplete = false;
        pendingLadderArea = null;
        complete = false;
        securityDoorDialogueActive = false;
        gateOfWarAdvancePending = false;
        resetRewardInteraction();
        KspWalkerGuard.clear(WALK_KEY);
        KspWalkerGuard.clear(GATE_OF_WAR_WALK_KEY);
        if (Rs2Walker.getCurrentTarget() != null)
        {
            KspWalkerGuard.clearActiveWalker("ksp_stronghold_shutdown");
        }
        super.shutdown();
    }

    public boolean isComplete()
    {
        return complete;
    }
}
