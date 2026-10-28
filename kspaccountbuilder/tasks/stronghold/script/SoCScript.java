package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.script;

import net.runelite.client.plugins.microbot.kspaccountbuilder.KspAccountBuilderPauseGate;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspManagedScript;

import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspLiveScene;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.inventory.InvFood;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.StrongholdAnswer;

@Singleton
public class SoCScript extends KspManagedScript
{
    private static final int LOOP_DELAY_MS = 100;
    private static final int WALK_REFIRE_MS = 1_000;
    private static final long INTERACTION_DISPATCH_TIMEOUT_MS = 1_500L;
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
    private long pendingInteractionAtMs;
    private String pendingInteractionKey;

    public boolean run()
    {
        shutdown();
        Microbot.status = "Preparing Stronghold of Security";
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            if (KspAccountBuilderPauseGate.isPaused() || !super.run() || !Microbot.isLoggedIn() || complete)
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
            Rs2Walker.clearWalkingRoute(WALK_KEY);
            Rs2Walker.clearWalkingRoute("ksp_stronghold_gate_of_war_crossed");
        }

        if (!gateOfWarAdvancePending)
        {
            return false;
        }

        if (location.distanceTo(GATE_OF_WAR_ADVANCE_TILE) <= 1)
        {
            gateOfWarAdvancePending = false;
            Rs2Walker.clearWalkingRoute("ksp_stronghold_gate_of_war_advanced");
            return false;
        }

        Microbot.status = "Moving past Gate of War";
        Rs2Walker.walkTo(GATE_OF_WAR_ADVANCE_TILE, 1);
        return true;
    }

    private void handleSegment(Areas area, int objectId, WorldPoint objectLocation, Runnable markComplete)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (!area.getArea().contains(playerLocation))
        {
            resetRewardInteraction();
            Microbot.status = "Walking to " + area.getDisplayName();
            Rs2Walker.walkUntil(area.getArea().getRandomPoint(), 2, () -> area.getArea().contains(Rs2Player.getWorldLocation()));
            return;
        }

        Rs2Walker.clearWalkingRoute(WALK_KEY);
        if (!rewardInteractionStarted)
        {
            Microbot.status = "Claiming " + area.getDisplayName();
            if (interactWithExactObject(objectId, objectLocation))
            {
                rewardInteractionStarted = true;
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
        pendingInteractionAtMs = 0L;
        pendingInteractionKey = null;
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
            Rs2Walker.walkTo(location, 1);
            return false;
        }

        for (String action : REWARD_ACTIONS)
        {
            String key = "reward:" + objectId + ":" + action;
            if (isInteractionPending(key)) return false;
            if (Rs2GameObject.interact(object, action))
            {
                markInteraction(key);
                return true;
            }
        }

        String key = "reward:" + objectId;
        if (isInteractionPending(key)) return false;
        boolean interacted = Rs2GameObject.interact(object);
        if (interacted) markInteraction(key);
        return interacted;
    }

    private boolean handleDialogue()
    {
        if (!Rs2Dialogue.isInDialogue()) return false;

        if (Rs2Dialogue.hasContinue())
        {
            if (isInteractionPending("dialogue-continue")) return true;
            Rs2Dialogue.clickContinue();
            markInteraction("dialogue-continue");
            return true;
        }

        if (!Rs2Dialogue.hasSelectAnOption()) return false;

        String question = Rs2Dialogue.getDialogueText();
        String answer = question == null ? null : StrongholdAnswer.findAnswer(question);
        if (answer != null && Rs2Dialogue.clickOption(answer, true)) return true;

        Microbot.status = "Unknown Stronghold security question";
        return false;
    }

    private boolean finishSecurityDoorDialogue()
    {
        if (!securityDoorDialogueActive || Rs2Dialogue.isInDialogue())
        {
            return false;
        }

        securityDoorDialogueActive = false;
        Rs2Walker.clearWalkingRoute(WALK_KEY);
        Rs2Walker.clearWalkingRoute("ksp_stronghold_security_door_crossed");
        Microbot.status = "Continuing through Stronghold";
        return true;
    }

    private void handlePendingLadderDescent()
    {
        if (pendingLadderArea == null) return;

        if (!pendingLadderArea.getArea().contains(Rs2Player.getWorldLocation()))
        {
            pendingLadderArea = null;
            return;
        }

        if (isInteractionPending("ladder-down")) return;
        boolean interacted = KspLiveScene.interact(
                "Ladder", Rs2Player.getWorldLocation(), 15, "Climb-down");
        if (interacted)
        {
            markInteraction("ladder-down");
            Microbot.status = "Descending Stronghold ladder";
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

    private boolean isInteractionPending(String key)
    {
        if (pendingInteractionAtMs == 0L || pendingInteractionKey == null) return false;
        if (!pendingInteractionKey.equals(key)) return false;
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) return true;
        if (System.currentTimeMillis() - pendingInteractionAtMs < INTERACTION_DISPATCH_TIMEOUT_MS) return true;
        pendingInteractionAtMs = 0L;
        pendingInteractionKey = null;
        return false;
    }

    private void markInteraction(String key)
    {
        pendingInteractionKey = key;
        pendingInteractionAtMs = System.currentTimeMillis();
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
        pendingInteractionAtMs = 0L;
        pendingInteractionKey = null;
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
        Rs2Walker.clearWalkingRoute(WALK_KEY);
        Rs2Walker.clearWalkingRoute(GATE_OF_WAR_WALK_KEY);
        if (Rs2Walker.getCurrentTarget() != null)
        {
            Rs2Walker.clearWalkingRoute("ksp_stronghold_shutdown");
        }
        super.shutdown();
    }

    public boolean isComplete() { return complete; }
}
