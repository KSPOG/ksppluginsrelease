package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeoscript;

import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeinv.Inv;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeoareas.Areas;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestVarPlayer;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RomeoScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(RomeoScript.class);
    private static final int LOOP_DELAY_MS = 600;
    private static final int NPC_REACH_DISTANCE = 4;
    private static final long WALK_REFIRE_COOLDOWN_MS = 3_500L;
    private static final long ACTION_COOLDOWN_MS = 1_200L;
    private static final long DIALOGUE_INTERACTION_TIMEOUT_MS = 10_000L;
    private static final long QUEST_STAGE_TRANSITION_TIMEOUT_MS = 45_000L;
    private static final int JULIET_STAIRCASE_ID = 11797;
    private static final int JULIET_STAIRCASE_INTERACTION_DISTANCE = 6;
    private static final int JULIET_UPSTAIRS_STAIRCASE_ID = 11799;
    private static final int JULIET_HALLWAY_DOOR_CLOSED_ID = 11773;
    private static final int JULIET_ROOM_DOOR_CLOSED_ID = 11773;
    private static final int JULIET_DOOR_INTERACTION_DISTANCE = 8;
    private static final int CADAVA_BUSH_OBJECT_ID = 23625;
    private static final int QUEST_STAGE_NOT_STARTED = 0;
    private static final int QUEST_STAGE_TALK_TO_JULIET = 10;
    private static final int QUEST_STAGE_GIVE_MESSAGE_TO_ROMEO = 20;
    private static final int QUEST_STAGE_TALK_TO_FATHER_LAWRENCE = 30;
    private static final int QUEST_STAGE_TALK_TO_APOTHECARY = 40;
    private static final int QUEST_STAGE_GIVE_POTION_TO_JULIET = 50;
    private static final int QUEST_STAGE_FINISH_WITH_ROMEO = 60;
    private static final String APOTHECARY_OTHER_DIALOGUE = "Talk about something else.";
    private static final String APOTHECARY_QUEST_DIALOGUE = "Talk about Romeo & Juliet.";
    private static final WorldPoint CADAVA_BUSH_POSITION = new WorldPoint(3277, 3374, 0);
    private static final WorldPoint ROMEO_POSITION = new WorldPoint(3211, 3424, 0);
    private static final WorldPoint FATHER_LAWRENCE_POSITION = new WorldPoint(3254, 3484, 0);
    private static final WorldPoint APOTHECARY_POSITION = new WorldPoint(3195, 3403, 0);
    private static final WorldPoint JULIET_HALLWAY_POSITION = new WorldPoint(3158, 3434, 0);
    private static final WorldPoint JULIET_STAIRCASE_POSITION = new WorldPoint(3157, 3436, 0);
    private static final WorldPoint JULIET_STAIRCASE_LEGACY_POSITION = new WorldPoint(3156, 3435, 0);
    private static final WorldPoint JULIET_UPSTAIRS_STAIRCASE_POSITION = new WorldPoint(3156, 3435, 1);
    private static final WorldPoint JULIET_HALLWAY_DOOR_POSITION = new WorldPoint(3157, 3430, 1);
    private static final WorldPoint JULIET_HALLWAY_INNER_POSITION = new WorldPoint(3157, 3429, 1);
    private static final WorldPoint JULIET_HALLWAY_OUTER_POSITION = new WorldPoint(3157, 3431, 1);
    private static final WorldPoint JULIET_ROOM_DOOR_POSITION = new WorldPoint(3158, 3427, 1);
    private static final WorldPoint JULIET_ROOM_INNER_POSITION = new WorldPoint(3158, 3426, 1);
    private static final WorldPoint JULIET_ROOM_OUTER_POSITION = new WorldPoint(3158, 3428, 1);
    private static final String WALK_KEY_CADAVA_BUSH = "Romeo and Juliet:cadava-bush";
    private static final String WALK_KEY_ROMEO = "Romeo and Juliet:romeo";
    private static final String WALK_KEY_FATHER_LAWRENCE = "Romeo and Juliet:father-lawrence";
    private static final String WALK_KEY_APOTHECARY = "Romeo and Juliet:apothecary";
    private static final String WALK_KEY_JULIET_HALLWAY = "Romeo and Juliet:juliet-hallway";
    private static final String WALK_KEY_JULIET = "Romeo and Juliet:juliet";

    private boolean debugLogging;
    private boolean complete;
    private boolean romeoDialogueSeen;
    private boolean romeoIntroComplete;
    private boolean julietDialogueSeen;
    private boolean deliveringMessageToRomeo;
    private boolean deliveringPotionToJuliet;
    private boolean awaitingPotionCutscene;
    private RomeoState dialogueState;
    private long lastActionAtMs;
    private long potionDialogueCompletedAtMs;
    private long finalRomeoDialogueCompletedAtMs;
    private RomeoState state = RomeoState.PREPARING;
    private String status = "Idle";

    public boolean run()
    {
        shutdown();
        complete = false;
        romeoDialogueSeen = false;
        romeoIntroComplete = false;
        julietDialogueSeen = false;
        deliveringMessageToRomeo = false;
        deliveringPotionToJuliet = false;
        awaitingPotionCutscene = false;
        dialogueState = null;
        lastActionAtMs = 0L;
        potionDialogueCompletedAtMs = 0L;
        finalRomeoDialogueCompletedAtMs = 0L;
        state = RomeoState.PREPARING;
        status = "Starting Romeo and Juliet";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                runLoop();
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void runLoop()
    {
        if (!super.run() || !Microbot.isLoggedIn())
        {
            return;
        }

        QuestState questState = Rs2Player.getQuestState(Quest.ROMEO__JULIET);
        int questStage = getQuestLogStage();
        if (questState == QuestState.FINISHED
                || questStage > QUEST_STAGE_FINISH_WITH_ROMEO)
        {
            markComplete();
            return;
        }

        KspTaskDebug.throttled(log, debugLogging, "Romeo and Juliet", "loop", 5_000L,
                "loop | state={} status={} player={} questState={} questStage={} moving={} dialogue={} cadavaBerries={}",
                state,
                status,
                Rs2Player.getWorldLocation(),
                questState,
                questStage,
                Rs2Player.isMoving(),
                Rs2Dialogue.isInDialogue(),
                Rs2Inventory.itemQuantity(Inv.CADAVA_BERRIES.getItemId()));

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (handleCutscene(playerLocation))
        {
            return;
        }

        if (handleDialogue(questState))
        {
            return;
        }

        if (awaitingPotionCutscene)
        {
            if (questStage > QUEST_STAGE_GIVE_POTION_TO_JULIET)
            {
                awaitingPotionCutscene = false;
            }
            else if (System.currentTimeMillis() - potionDialogueCompletedAtMs
                    < QUEST_STAGE_TRANSITION_TIMEOUT_MS)
            {
                state = RomeoState.WAITING_FOR_CUTSCENE;
                status = "Waiting for Juliet potion cutscene";
                return;
            }
            else
            {
                awaitingPotionCutscene = false;
            }
        }

        if (finalRomeoDialogueCompletedAtMs > 0L
                && questStage == QUEST_STAGE_FINISH_WITH_ROMEO
                && System.currentTimeMillis() - finalRomeoDialogueCompletedAtMs
                < QUEST_STAGE_TRANSITION_TIMEOUT_MS)
        {
            status = "Waiting for Romeo and Juliet completion";
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating())
        {
            return;
        }

        followQuestLogStage(questStage);
    }

    private boolean handleCutscene(WorldPoint playerLocation)
    {
        if (!Rs2Dialogue.isInCutScene() && !isJulietCutsceneMap(playerLocation))
        {
            return false;
        }

        state = RomeoState.WAITING_FOR_CUTSCENE;
        status = "Waiting for Romeo and Juliet cutscene";
        KspWalkerGuard.clearActiveWalker("ksp_romeo_cutscene");
        if (Rs2Dialogue.isInDialogue() && Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
        }
        return true;
    }

    private void markComplete()
    {
        shutdown();
        complete = true;
        state = RomeoState.COMPLETE;
        status = "Romeo and Juliet complete";
    }

    private void followQuestLogStage(int questStage)
    {
        switch (questStage)
        {
            case QUEST_STAGE_NOT_STARTED:
                if (!hasCadavaBerries())
                {
                    gatherCadavaBerries();
                    return;
                }
                startQuestWithRomeo();
                return;
            case QUEST_STAGE_TALK_TO_JULIET:
                talkToJuliet();
                return;
            case QUEST_STAGE_GIVE_MESSAGE_TO_ROMEO:
                if (hasMessage())
                {
                    returnMessageToRomeo();
                }
                else
                {
                    status = "Recovering Juliet's message";
                    talkToJuliet();
                }
                return;
            case QUEST_STAGE_TALK_TO_FATHER_LAWRENCE:
                talkToQuestNpc(
                        "Father Lawrence",
                        FATHER_LAWRENCE_POSITION,
                        WALK_KEY_FATHER_LAWRENCE,
                        RomeoState.WALKING_TO_FATHER_LAWRENCE,
                        RomeoState.TALKING_TO_FATHER_LAWRENCE,
                        "Walking to Father Lawrence",
                        "Talking to Father Lawrence");
                return;
            case QUEST_STAGE_TALK_TO_APOTHECARY:
                talkToApothecary();
                return;
            case QUEST_STAGE_GIVE_POTION_TO_JULIET:
                if (hasCadavaPotion())
                {
                    deliveringPotionToJuliet = true;
                    state = RomeoState.RETURNING_POTION_TO_JULIET;
                    talkToJuliet();
                }
                else
                {
                    status = "Recovering Cadava potion";
                    talkToApothecary();
                }
                return;
            default:
                if (questStage >= QUEST_STAGE_FINISH_WITH_ROMEO)
                {
                    WorldPoint playerLocation = Rs2Player.getWorldLocation();
                    if (isJulietCutsceneMap(playerLocation))
                    {
                        state = RomeoState.WAITING_FOR_CUTSCENE;
                        status = "Waiting for Juliet cutscene to end";
                        KspWalkerGuard.clearActiveWalker("ksp_romeo_cutscene_map");
                        return;
                    }

                    if (playerLocation != null && playerLocation.getPlane() == 1)
                    {
                        leaveJulietHouse(playerLocation);
                        return;
                    }

                    talkToQuestNpc(
                            "Romeo",
                            ROMEO_POSITION,
                            WALK_KEY_ROMEO,
                            RomeoState.RETURNING_TO_ROMEO,
                            RomeoState.TALKING_TO_ROMEO_FINAL,
                            "Returning to Romeo",
                            "Finishing Romeo and Juliet");
                    return;
                }

                status = "Waiting for Romeo and Juliet quest log";
        }
    }

    private boolean isJulietCutsceneMap(WorldPoint playerLocation)
    {
        return playerLocation != null
                && (playerLocation.getX() < 3100
                || playerLocation.getX() > 3300
                || playerLocation.getY() < 3300
                || playerLocation.getY() > 3500);
    }

    private void talkToApothecary()
    {
        talkToQuestNpc(
                "Apothecary",
                APOTHECARY_POSITION,
                WALK_KEY_APOTHECARY,
                RomeoState.WALKING_TO_APOTHECARY,
                RomeoState.TALKING_TO_APOTHECARY,
                "Walking to the Apothecary",
                "Talking to the Apothecary");
    }

    private int getQuestLogStage()
    {
        return Microbot.getVarbitPlayerValue(
                QuestVarPlayer.QUEST_ROMEO_AND_JULIET.getId());
    }

    private boolean handleDialogue(QuestState questState)
    {
        if (!Rs2Dialogue.isInDialogue())
        {
            if (romeoDialogueSeen)
            {
                if (deliveringMessageToRomeo)
                {
                    if (hasMessage())
                    {
                        status = "Waiting for Romeo to take message";
                        if (System.currentTimeMillis() - lastActionAtMs < DIALOGUE_INTERACTION_TIMEOUT_MS)
                        {
                            return true;
                        }

                        romeoDialogueSeen = false;
                        state = RomeoState.RETURNING_MESSAGE_TO_ROMEO;
                        return false;
                    }

                    romeoDialogueSeen = false;
                    deliveringMessageToRomeo = false;
                    state = RomeoState.MESSAGE_DELIVERED;
                    status = "Walking to Father Lawrence";
                    return false;
                }

                if (questState == QuestState.NOT_STARTED)
                {
                    status = "Waiting for Romeo dialogue";
                    if (System.currentTimeMillis() - lastActionAtMs < DIALOGUE_INTERACTION_TIMEOUT_MS)
                    {
                        return true;
                    }

                    romeoDialogueSeen = false;
                    state = RomeoState.WALKING_TO_ROMEO;
                    return false;
                }

                romeoDialogueSeen = false;
                romeoIntroComplete = true;
                state = RomeoState.WALKING_TO_JULIET_HALLWAY;
                status = "Walking to Juliet hallway";
            }
            else if (julietDialogueSeen)
            {
                julietDialogueSeen = false;
                if (deliveringPotionToJuliet)
                {
                    deliveringPotionToJuliet = false;
                    awaitingPotionCutscene = true;
                    potionDialogueCompletedAtMs = System.currentTimeMillis();
                    state = RomeoState.WAITING_FOR_CUTSCENE;
                    status = "Waiting for Juliet potion cutscene";
                }
                else
                {
                    state = RomeoState.RETURNING_MESSAGE_TO_ROMEO;
                    status = "Returning Juliet's message to Romeo";
                }
            }
            else if (dialogueState != null)
            {
                RomeoState completedDialogue = dialogueState;
                dialogueState = null;
                if (completedDialogue == RomeoState.TALKING_TO_FATHER_LAWRENCE)
                {
                    state = RomeoState.WALKING_TO_APOTHECARY;
                    status = "Walking to the Apothecary";
                }
                else if (completedDialogue == RomeoState.TALKING_TO_APOTHECARY)
                {
                    state = RomeoState.WALKING_TO_APOTHECARY;
                    status = "Waiting for Cadava potion";
                }
                else if (completedDialogue == RomeoState.TALKING_TO_ROMEO_FINAL)
                {
                    state = RomeoState.RETURNING_TO_ROMEO;
                    status = "Waiting for quest completion";
                    finalRomeoDialogueCompletedAtMs = System.currentTimeMillis();
                }
            }

            if (state == RomeoState.TALKING_TO_ROMEO
                    || state == RomeoState.TALKING_TO_JULIET)
            {
                if (System.currentTimeMillis() - lastActionAtMs < DIALOGUE_INTERACTION_TIMEOUT_MS)
                {
                    status = state == RomeoState.TALKING_TO_ROMEO
                            ? "Waiting for Romeo dialogue"
                            : "Waiting for Juliet dialogue";
                    return true;
                }

                state = state == RomeoState.TALKING_TO_ROMEO
                        ? RomeoState.WALKING_TO_ROMEO
                        : RomeoState.WALKING_TO_JULIET_HALLWAY;
            }
            return false;
        }

        if (state == RomeoState.TALKING_TO_ROMEO)
        {
            romeoDialogueSeen = true;
        }
        else if (state == RomeoState.TALKING_TO_JULIET)
        {
            julietDialogueSeen = true;
        }
        else if (state == RomeoState.TALKING_TO_FATHER_LAWRENCE
                || state == RomeoState.TALKING_TO_APOTHECARY
                || state == RomeoState.TALKING_TO_ROMEO_FINAL)
        {
            dialogueState = state;
        }

        status = state == RomeoState.TALKING_TO_APOTHECARY
                ? "Requesting the Cadava potion"
                : state == RomeoState.TALKING_TO_FATHER_LAWRENCE
                ? "Handling Father Lawrence dialogue"
                : state == RomeoState.TALKING_TO_JULIET
                ? "Handling Juliet dialogue"
                : "Handling Romeo dialogue";

        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            return true;
        }

        if (state == RomeoState.TALKING_TO_APOTHECARY
                && handleApothecaryDialogue())
        {
            return true;
        }

        if (Rs2Dialogue.acceptQuestStartDialogue())
        {
            return true;
        }

        if (Rs2Dialogue.handleQuestOptionDialogueSelection())
        {
            return true;
        }

        return Rs2Dialogue.hasSelectAnOption()
                && Rs2Dialogue.keyPressForDialogueOption(1);
    }

    private boolean handleApothecaryDialogue()
    {
        if (Rs2Dialogue.hasDialogueOption(APOTHECARY_OTHER_DIALOGUE, true))
        {
            return Rs2Dialogue.clickOption(APOTHECARY_OTHER_DIALOGUE, true);
        }

        if (Rs2Dialogue.hasDialogueOption(APOTHECARY_QUEST_DIALOGUE, true))
        {
            return Rs2Dialogue.clickOption(APOTHECARY_QUEST_DIALOGUE, true);
        }

        return false;
    }

    private void gatherCadavaBerries()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (!Areas.CADAVA_BUSH.getArea().contains(playerLocation))
        {
            state = RomeoState.WALKING_TO_CADAVA_BUSH;
            status = "Walking to Cadava bush";
            KspWalkerGuard.walkToDestination(
                    WALK_KEY_CADAVA_BUSH,
                    () -> CADAVA_BUSH_POSITION,
                    Areas.CADAVA_BUSH.getArea()::contains,
                    1,
                    WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clearReachedDestination(
                WALK_KEY_CADAVA_BUSH,
                "ksp_romeo_reached_cadava_bush");
        if (!actionCooldownElapsed())
        {
            return;
        }

        state = RomeoState.PICKING_CADAVA_BERRIES;
        status = "Picking Cadava berries";
        int previousQuantity = Rs2Inventory.itemQuantity(Inv.CADAVA_BERRIES.getItemId());
        TileObject bush = Rs2GameObject.findObjectByLocation(CADAVA_BUSH_POSITION);
        if (bush == null || bush.getId() != CADAVA_BUSH_OBJECT_ID)
        {
            status = "Locating Cadava bush";
            KspWalkerGuard.walkFastCanvasToPoint(
                    WALK_KEY_CADAVA_BUSH,
                    CADAVA_BUSH_POSITION,
                    1,
                    WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        lastActionAtMs = System.currentTimeMillis();
        if (Rs2GameObject.interact(bush, "Pick-from"))
        {
            sleepUntil(
                    () -> Rs2Inventory.itemQuantity(Inv.CADAVA_BERRIES.getItemId()) > previousQuantity,
                    4_000);
        }
    }

    private void startQuestWithRomeo()
    {
        Rs2NpcModel romeo = findNpc("Romeo");
        if (romeo == null)
        {
            state = RomeoState.WALKING_TO_ROMEO;
            status = "Walking to Romeo";
            KspWalkerGuard.walkToPoint(
                    WALK_KEY_ROMEO,
                    ROMEO_POSITION,
                    NPC_REACH_DISTANCE,
                    WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        if (!walkToNpc(WALK_KEY_ROMEO, romeo, "Romeo"))
        {
            return;
        }

        if (!actionCooldownElapsed())
        {
            return;
        }

        state = RomeoState.TALKING_TO_ROMEO;
        status = "Talking to Romeo";
        lastActionAtMs = System.currentTimeMillis();
        if (romeo.click("Talk-to"))
        {
            KspWalkerGuard.clear(WALK_KEY_ROMEO);
            sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
        }
    }

    private void returnMessageToRomeo()
    {
        Rs2NpcModel romeo = findNpc("Romeo");
        if (romeo == null)
        {
            state = RomeoState.RETURNING_MESSAGE_TO_ROMEO;
            status = "Returning Juliet's message to Romeo";
            KspWalkerGuard.walkToPoint(
                    WALK_KEY_ROMEO,
                    ROMEO_POSITION,
                    NPC_REACH_DISTANCE,
                    WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        if (!walkToNpc(WALK_KEY_ROMEO, romeo, "Romeo") || !actionCooldownElapsed())
        {
            return;
        }

        deliveringMessageToRomeo = true;
        state = RomeoState.TALKING_TO_ROMEO;
        status = "Giving Juliet's message to Romeo";
        lastActionAtMs = System.currentTimeMillis();
        if (romeo.click("Talk-to"))
        {
            KspWalkerGuard.clear(WALK_KEY_ROMEO);
            sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
        }
    }

    private void talkToQuestNpc(
            String npcName,
            WorldPoint destination,
            String walkKey,
            RomeoState walkingState,
            RomeoState talkingState,
            String walkingStatus,
            String talkingStatus)
    {
        Rs2NpcModel npc = findNpc(npcName);
        if (npc == null)
        {
            state = walkingState;
            status = walkingStatus;
            KspWalkerGuard.walkToPoint(
                    walkKey,
                    destination,
                    NPC_REACH_DISTANCE,
                    WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        if (!walkToNpc(walkKey, npc, npcName) || !actionCooldownElapsed())
        {
            return;
        }

        state = talkingState;
        status = talkingStatus;
        lastActionAtMs = System.currentTimeMillis();
        if (npc.click("Talk-to"))
        {
            KspWalkerGuard.clear(walkKey);
            sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
        }
    }

    private void talkToJuliet()
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null && playerLocation.getPlane() == 1)
        {
            clearHallwayRouteAfterClimb();
            if (enterJulietRoom(playerLocation))
            {
                return;
            }

            Rs2NpcModel juliet = findNpc("Juliet");
            if (juliet == null)
            {
                status = "Searching for Juliet";
                return;
            }

            if (!walkDirectlyToJuliet(playerLocation, juliet) || !actionCooldownElapsed())
            {
                return;
            }

            state = RomeoState.TALKING_TO_JULIET;
            status = "Talking to Juliet";
            lastActionAtMs = System.currentTimeMillis();
            if (juliet.click("Talk-to"))
            {
                KspWalkerGuard.clear(WALK_KEY_JULIET);
                sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
            }
            return;
        }

        if (!Areas.HALLWAY.getArea().contains(playerLocation))
        {
            state = RomeoState.WALKING_TO_JULIET_HALLWAY;
            status = "Walking to Juliet hallway";
            KspWalkerGuard.walkToDestination(
                    WALK_KEY_JULIET_HALLWAY,
                    () -> JULIET_HALLWAY_POSITION,
                    Areas.HALLWAY.getArea()::contains,
                    1,
                    WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clear(WALK_KEY_JULIET_HALLWAY);
        if (!actionCooldownElapsed())
        {
            return;
        }

        state = RomeoState.CLIMBING_TO_JULIET;
        status = "Climbing staircase to Juliet";
        lastActionAtMs = System.currentTimeMillis();

        if (interactWithJulietStaircase())
        {
            KspWalkerGuard.clearActiveWalker("ksp_romeo_using_juliet_staircase");
            sleepUntil(
                    () ->
                    {
                        WorldPoint location = Rs2Player.getWorldLocation();
                        return location != null && location.getPlane() == 1;
                    },
                    5_000);
            return;
        }

        status = "Finding Juliet staircase";
        Rs2Walker.walkFastCanvas(JULIET_STAIRCASE_POSITION);
    }

    private void clearHallwayRouteAfterClimb()
    {
        KspWalkerGuard.clear(WALK_KEY_JULIET_HALLWAY);
        WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();
        if (walkerTarget != null && walkerTarget.getPlane() != 1)
        {
            Rs2Walker.clearWalkingRoute("ksp_romeo_upstairs");
        }
    }

    private boolean enterJulietRoom(WorldPoint playerLocation)
    {
        if (playerLocation.getY() >= JULIET_HALLWAY_DOOR_POSITION.getY())
        {
            return passJulietDoor(
                    JULIET_HALLWAY_DOOR_POSITION,
                    JULIET_HALLWAY_DOOR_CLOSED_ID,
                    JULIET_HALLWAY_INNER_POSITION,
                    "Entering Juliet's hallway");
        }

        if (playerLocation.getY() >= JULIET_ROOM_DOOR_POSITION.getY())
        {
            return passJulietDoor(
                    JULIET_ROOM_DOOR_POSITION,
                    JULIET_ROOM_DOOR_CLOSED_ID,
                    JULIET_ROOM_INNER_POSITION,
                    "Entering Juliet's room");
        }

        return false;
    }

    private boolean exitJulietRoom(WorldPoint playerLocation)
    {
        if (playerLocation.getY() <= JULIET_ROOM_DOOR_POSITION.getY())
        {
            return passJulietDoor(
                    JULIET_ROOM_DOOR_POSITION,
                    JULIET_ROOM_DOOR_CLOSED_ID,
                    JULIET_ROOM_OUTER_POSITION,
                    "Leaving Juliet's room");
        }

        if (playerLocation.getY() <= JULIET_HALLWAY_DOOR_POSITION.getY())
        {
            return passJulietDoor(
                    JULIET_HALLWAY_DOOR_POSITION,
                    JULIET_HALLWAY_DOOR_CLOSED_ID,
                    JULIET_HALLWAY_OUTER_POSITION,
                    "Leaving Juliet's hallway");
        }

        return false;
    }

    private boolean passJulietDoor(
            WorldPoint doorPosition,
            int doorId,
            WorldPoint destination,
            String routeStatus)
    {
        state = RomeoState.OPENING_JULIET_DOORS;
        status = routeStatus;

        TileObject door = findClosedJulietDoorAt(doorPosition, doorId);
        if (door != null)
        {
            status = "Opening door to Juliet";
            if (!actionCooldownElapsed())
            {
                return true;
            }

            lastActionAtMs = System.currentTimeMillis();
            if (Rs2GameObject.interact(door, "Open"))
            {
                sleep(600);
            }
        }

        Rs2Walker.walkFastCanvas(destination);
        return true;
    }

    private TileObject findClosedJulietDoorAt(WorldPoint position, int doorId)
    {
        TileObject door = Rs2GameObject.findObjectByLocation(position);
        if (isClosedJulietDoor(door, doorId))
        {
            return door;
        }

        door = Rs2GameObject.getTileObject(
                doorId,
                position,
                JULIET_DOOR_INTERACTION_DISTANCE);
        return isClosedJulietDoor(door, doorId) ? door : null;
    }

    private boolean isClosedJulietDoor(TileObject door, int doorId)
    {
        return door != null
                && door.getId() == doorId
                && Rs2GameObject.hasAction(door, "Open");
    }

    private void leaveJulietHouse(WorldPoint playerLocation)
    {
        state = RomeoState.LEAVING_JULIET_HOUSE;
        status = "Leaving Juliet's house";
        KspWalkerGuard.clearActiveWalker("ksp_romeo_local_house_exit");

        if (exitJulietRoom(playerLocation) || !actionCooldownElapsed())
        {
            return;
        }

        TileObject staircase = Rs2GameObject.findObjectByLocation(JULIET_UPSTAIRS_STAIRCASE_POSITION);
        if (isJulietUpstairsStaircase(staircase))
        {
            lastActionAtMs = System.currentTimeMillis();
            if (Rs2GameObject.interact(staircase, "Climb-down"))
            {
                sleepUntil(
                        () ->
                        {
                            WorldPoint location = Rs2Player.getWorldLocation();
                            return location != null && location.getPlane() == 0;
                        },
                        5_000);
            }
            return;
        }

        staircase = Rs2GameObject.getTileObject(
                JULIET_UPSTAIRS_STAIRCASE_ID,
                JULIET_UPSTAIRS_STAIRCASE_POSITION,
                JULIET_STAIRCASE_INTERACTION_DISTANCE);
        if (isJulietUpstairsStaircase(staircase))
        {
            lastActionAtMs = System.currentTimeMillis();
            Rs2GameObject.interact(staircase, "Climb-down");
            return;
        }

        status = "Walking to Juliet's staircase";
        Rs2Walker.walkFastCanvas(JULIET_UPSTAIRS_STAIRCASE_POSITION);
    }

    private boolean isJulietUpstairsStaircase(TileObject staircase)
    {
        return staircase != null
                && staircase.getId() == JULIET_UPSTAIRS_STAIRCASE_ID
                && Rs2GameObject.hasAction(staircase, "Climb-down");
    }

    private boolean walkDirectlyToJuliet(WorldPoint playerLocation, Rs2NpcModel juliet)
    {
        WorldPoint julietLocation = juliet.getWorldLocation();
        if (julietLocation == null || julietLocation.getPlane() != playerLocation.getPlane())
        {
            status = "Locating Juliet upstairs";
            return false;
        }

        if (playerLocation.distanceTo(julietLocation) <= NPC_REACH_DISTANCE)
        {
            KspWalkerGuard.clear(WALK_KEY_JULIET);
            return true;
        }

        status = "Walking upstairs to Juliet";
        Rs2Walker.walkFastCanvas(julietLocation);
        return false;
    }

    private boolean interactWithJulietStaircase()
    {
        TileObject staircase = Rs2GameObject.findObjectByLocation(JULIET_STAIRCASE_POSITION);
        if (isJulietStaircase(staircase))
        {
            return Rs2GameObject.interact(staircase, "Climb-up");
        }

        staircase = Rs2GameObject.findObjectByLocation(JULIET_STAIRCASE_LEGACY_POSITION);
        if (isJulietStaircase(staircase))
        {
            return Rs2GameObject.interact(staircase, "Climb-up");
        }

        staircase = Rs2GameObject.getTileObject(
                JULIET_STAIRCASE_ID,
                JULIET_STAIRCASE_POSITION,
                JULIET_STAIRCASE_INTERACTION_DISTANCE);
        if (isJulietStaircase(staircase))
        {
            return Rs2GameObject.interact(staircase, "Climb-up");
        }

        return Microbot.getRs2TileObjectCache()
                .query()
                .fromWorldView()
                .withId(JULIET_STAIRCASE_ID)
                .within(JULIET_STAIRCASE_INTERACTION_DISTANCE)
                .interact("Climb-up");
    }

    private boolean isJulietStaircase(TileObject staircase)
    {
        return staircase != null
                && staircase.getId() == JULIET_STAIRCASE_ID
                && Rs2GameObject.hasAction(staircase, "Climb-up");
    }

    private boolean walkToNpc(String walkKey, Rs2NpcModel npc, String npcName)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint npcLocation = npc.getWorldLocation();
        if (playerLocation == null || npcLocation == null)
        {
            return false;
        }

        if (playerLocation.distanceTo(npcLocation) <= NPC_REACH_DISTANCE)
        {
            KspWalkerGuard.clearReachedDestination(
                    walkKey,
                    "ksp_romeo_reached_" + npcName.toLowerCase());
            return true;
        }

        status = "Walking to " + npcName;
        KspWalkerGuard.walkFastCanvasToPoint(
                walkKey,
                npcLocation,
                NPC_REACH_DISTANCE,
                WALK_REFIRE_COOLDOWN_MS);
        return false;
    }

    private Rs2NpcModel findNpc(String name)
    {
        return Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .withName(name)
                .nearestOnClientThread();
    }

    private boolean actionCooldownElapsed()
    {
        return System.currentTimeMillis() - lastActionAtMs >= ACTION_COOLDOWN_MS;
    }

    private boolean hasCadavaBerries()
    {
        return Rs2Inventory.itemQuantity(Inv.CADAVA_BERRIES.getItemId())
                >= Inv.CADAVA_BERRIES.getQuantity();
    }

    private boolean hasMessage()
    {
        return Rs2Inventory.itemQuantity(Inv.MESSAGE.getItemId()) >= Inv.MESSAGE.getQuantity();
    }

    private boolean hasCadavaPotion()
    {
        return Rs2Inventory.itemQuantity(Inv.CADAVA_POTION.getItemId())
                >= Inv.CADAVA_POTION.getQuantity();
    }

    public boolean isComplete()
    {
        return complete
                || Rs2Player.getQuestState(Quest.ROMEO__JULIET) == QuestState.FINISHED
                || getQuestLogStage() > QUEST_STAGE_FINISH_WITH_ROMEO;
    }

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    public RomeoState getState()
    {
        return state;
    }

    public String getStatus()
    {
        return status;
    }

    @Override
    public void shutdown()
    {
        KspWalkerGuard.clear(WALK_KEY_CADAVA_BUSH);
        KspWalkerGuard.clear(WALK_KEY_ROMEO);
        KspWalkerGuard.clear(WALK_KEY_FATHER_LAWRENCE);
        KspWalkerGuard.clear(WALK_KEY_APOTHECARY);
        KspWalkerGuard.clear(WALK_KEY_JULIET_HALLWAY);
        KspWalkerGuard.clear(WALK_KEY_JULIET);
        romeoDialogueSeen = false;
        romeoIntroComplete = false;
        julietDialogueSeen = false;
        deliveringMessageToRomeo = false;
        deliveringPotionToJuliet = false;
        awaitingPotionCutscene = false;
        dialogueState = null;
        lastActionAtMs = 0L;
        potionDialogueCompletedAtMs = 0L;
        finalRomeoDialogueCompletedAtMs = 0L;
        state = RomeoState.PREPARING;
        status = "Idle";
        super.shutdown();
    }
}
