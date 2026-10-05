package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeoscript;

import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeinv.Inv;
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

    private static final int LOOP_DELAY_MS = 80;
    private static final int NPC_REACH_DISTANCE = 4;
    private static final long WALK_REFIRE_COOLDOWN_MS = 750L;
    private static final long ACTION_COOLDOWN_MS = 250L;
    private static final long INTERACTION_TIMEOUT_MS = 2_500L;

    private static final int STAGE_NOT_STARTED = 0;
    private static final int STAGE_JULIET = 10;
    private static final int STAGE_ROMEO_MESSAGE = 20;
    private static final int STAGE_LAWRENCE = 30;
    private static final int STAGE_APOTHECARY = 40;
    private static final int STAGE_JULIET_POTION = 50;
    private static final int STAGE_FINISH = 60;

    // Microbot's own RomeoAndJuliet quest logic uses this exact tile before
    // interacting with ObjectID.FAI_VARROCK_STAIRS_TALLER.
    private static final WorldPoint JULIET_STAIR_ORIGIN = new WorldPoint(3159, 3436, 0);
    private static final WorldPoint JULIET_TOP_STAIR = new WorldPoint(3156, 3435, 1);
    private static final WorldPoint JULIET_POSITION = new WorldPoint(3158, 3427, 1);
    private static final WorldPoint ROMEO_POSITION = new WorldPoint(3211, 3422, 0);
    private static final WorldPoint FATHER_LAWRENCE_POSITION = new WorldPoint(3254, 3483, 0);
    private static final WorldPoint APOTHECARY_POSITION = new WorldPoint(3195, 3405, 0);
    private static final WorldPoint CADAVA_BUSH_POSITION = new WorldPoint(3277, 3374, 0);

    private static final WorldPoint JULIET_ROOM_DOOR_POSITION = new WorldPoint(3158, 3427, 1);
    private static final WorldPoint JULIET_ROOM_INNER_POSITION = new WorldPoint(3158, 3426, 1);
    private static final int JULIET_DOOR_ID = 11773;

    private static final String WALK_ROMEO = "Romeo and Juliet:romeo";
    private static final String WALK_LAWRENCE = "Romeo and Juliet:lawrence";
    private static final String WALK_APOTHECARY = "Romeo and Juliet:apothecary";
    private static final String WALK_BERRIES = "Romeo and Juliet:berries";
    private static final String WALK_JULIET = "Romeo and Juliet:juliet";

    private boolean debugLogging;
    private boolean complete;
    private RomeoState state = RomeoState.PREPARING;
    private String status = "Idle";
    private long lastActionAtMs;
    private long pendingInteractionAtMs;
    private String pendingInteractionKey;

    public boolean run()
    {
        shutdown();
        complete = false;
        state = RomeoState.PREPARING;
        status = "Starting Romeo and Juliet";

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                tick();
            }
            catch (Exception ex)
            {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tick()
    {
        if (!super.run() || !Microbot.isLoggedIn()) return;

        QuestState questState = Rs2Player.getQuestState(Quest.ROMEO__JULIET);
        int stage = getQuestStage();
        if (questState == QuestState.FINISHED || stage > STAGE_FINISH)
        {
            complete = true;
            state = RomeoState.COMPLETE;
            status = "Romeo and Juliet complete";
            return;
        }

        KspTaskDebug.throttled(log, debugLogging, "Romeo and Juliet", "loop", 5_000L,
                "loop | state={} status={} player={} stage={} dialogue={} pending={}",
                state, status, Rs2Player.getWorldLocation(), stage,
                Rs2Dialogue.isInDialogue(), pendingInteractionKey);

        if (handleDialogue()) return;
        if (Rs2Dialogue.isInCutScene())
        {
            state = RomeoState.WAITING_FOR_CUTSCENE;
            status = "Waiting for cutscene";
            KspWalkerGuard.clearActiveWalker("ksp_romeo_cutscene");
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;

        // A staircase interaction owns the loop until the plane changes or the
        // short retry timeout expires. This prevents 10-clicks-per-second spam.
        if (pendingInteractionKey != null && pendingInteractionKey.startsWith("stairs-"))
        {
            if ((pendingInteractionKey.equals("stairs-up") && player.getPlane() == 1)
                    || (pendingInteractionKey.equals("stairs-down") && player.getPlane() == 0))
            {
                clearPendingInteraction();
            }
            else if (System.currentTimeMillis() - pendingInteractionAtMs < INTERACTION_TIMEOUT_MS)
            {
                status = pendingInteractionKey.equals("stairs-up")
                        ? "Waiting to reach Juliet upstairs"
                        : "Waiting to go downstairs";
                return;
            }
            else
            {
                clearPendingInteraction();
            }
        }

        switch (stage)
        {
            case STAGE_NOT_STARTED:
                if (!has(Inv.CADAVA_BERRIES)) gatherCadavaBerries();
                else talkToNpc("Romeo", ROMEO_POSITION, WALK_ROMEO,
                        RomeoState.WALKING_TO_ROMEO, RomeoState.TALKING_TO_ROMEO);
                break;

            case STAGE_JULIET:
                talkToJuliet(false);
                break;

            case STAGE_ROMEO_MESSAGE:
                if (has(Inv.MESSAGE))
                    talkToNpc("Romeo", ROMEO_POSITION, WALK_ROMEO,
                            RomeoState.RETURNING_MESSAGE_TO_ROMEO, RomeoState.TALKING_TO_ROMEO);
                else
                    talkToJuliet(false);
                break;

            case STAGE_LAWRENCE:
                talkToNpc("Father Lawrence", FATHER_LAWRENCE_POSITION, WALK_LAWRENCE,
                        RomeoState.WALKING_TO_FATHER_LAWRENCE, RomeoState.TALKING_TO_FATHER_LAWRENCE);
                break;

            case STAGE_APOTHECARY:
                talkToNpc("Apothecary", APOTHECARY_POSITION, WALK_APOTHECARY,
                        RomeoState.WALKING_TO_APOTHECARY, RomeoState.TALKING_TO_APOTHECARY);
                break;

            case STAGE_JULIET_POTION:
                if (has(Inv.CADAVA_POTION)) talkToJuliet(true);
                else talkToNpc("Apothecary", APOTHECARY_POSITION, WALK_APOTHECARY,
                        RomeoState.WALKING_TO_APOTHECARY, RomeoState.TALKING_TO_APOTHECARY);
                break;

            default:
                if (stage >= STAGE_FINISH)
                {
                    if (player.getPlane() == 1) leaveJulietHouse();
                    else talkToNpc("Romeo", ROMEO_POSITION, WALK_ROMEO,
                            RomeoState.RETURNING_TO_ROMEO, RomeoState.TALKING_TO_ROMEO_FINAL);
                }
                break;
        }
    }

    private boolean handleDialogue()
    {
        if (!Rs2Dialogue.isInDialogue()
                && !Rs2Dialogue.hasContinue()
                && !Rs2Dialogue.hasSelectAnOption()) return false;

        KspWalkerGuard.clearActiveWalker("ksp_romeo_dialogue");
        clearPendingInteraction();

        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            return true;
        }

        if (Rs2Dialogue.hasDialogueOption("Talk about something else.", true))
        {
            Rs2Dialogue.clickOption("Talk about something else.", true);
            return true;
        }
        if (Rs2Dialogue.hasDialogueOption("Talk about Romeo & Juliet.", true))
        {
            Rs2Dialogue.clickOption("Talk about Romeo & Juliet.", true);
            return true;
        }

        if (Rs2Dialogue.acceptQuestStartDialogue()) return true;
        if (Rs2Dialogue.handleQuestOptionDialogueSelection()) return true;

        if (Rs2Dialogue.hasSelectAnOption())
        {
            Rs2Dialogue.keyPressForDialogueOption(1);
            return true;
        }
        return true;
    }

    private void talkToJuliet(boolean potion)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;

        if (player.getPlane() == 0)
        {
            climbToJuliet(player);
            return;
        }

        if (player.getPlane() != 1) return;

        if (passJulietRoomDoor()) return;

        Rs2NpcModel juliet = findNpc("Juliet");
        if (juliet == null)
        {
            state = potion ? RomeoState.RETURNING_POTION_TO_JULIET : RomeoState.WALKING_TO_JULIET_HALLWAY;
            status = "Walking to Juliet";
            Rs2Walker.walkFastCanvas(JULIET_POSITION);
            return;
        }

        WorldPoint julietLocation = juliet.getWorldLocation();
        if (julietLocation != null && player.distanceTo(julietLocation) > NPC_REACH_DISTANCE)
        {
            status = "Walking to Juliet";
            Rs2Walker.walkFastCanvas(julietLocation);
            return;
        }

        if (!interactionReady()) return;
        state = RomeoState.TALKING_TO_JULIET;
        status = potion ? "Giving potion to Juliet" : "Talking to Juliet";
        if (juliet.click("Talk-to")) markInteraction("npc-juliet");
    }

    private void climbToJuliet(WorldPoint player)
    {
        state = RomeoState.CLIMBING_TO_JULIET;

        // Important: walk to the same origin used by Microbot's own built-in
        // RomeoAndJuliet logic. The old script walked/clicked 3157,3436 and
        // could repeatedly click the floor beside the staircase.
        if (player.distanceTo(JULIET_STAIR_ORIGIN) > 1)
        {
            status = "Walking to Juliet staircase";
            KspWalkerGuard.walkFastCanvasToPoint(
                    WALK_JULIET, JULIET_STAIR_ORIGIN, 1, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clear(WALK_JULIET);
        if (!interactionReady()) return;

        status = "Climbing staircase to Juliet";
        if (Rs2GameObject.interact(ObjectID.FAI_VARROCK_STAIRS_TALLER, "Climb-up"))
        {
            KspWalkerGuard.clearActiveWalker("ksp_romeo_using_juliet_staircase");
            markInteraction("stairs-up");
        }
    }

    private void leaveJulietHouse()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.getPlane() != 1) return;

        state = RomeoState.LEAVING_JULIET_HOUSE;
        if (player.distanceTo(JULIET_TOP_STAIR) > 4)
        {
            status = "Walking to Juliet staircase";
            Rs2Walker.walkFastCanvas(JULIET_TOP_STAIR);
            return;
        }

        if (!interactionReady()) return;
        status = "Climbing down from Juliet's house";
        if (Rs2GameObject.interact(ObjectID.FAI_VARROCK_STAIRS_TOP, "Climb-down"))
        {
            markInteraction("stairs-down");
        }
    }

    private boolean passJulietRoomDoor()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.getPlane() != 1 || player.getY() < JULIET_ROOM_DOOR_POSITION.getY()) return false;

        TileObject door = Rs2GameObject.findObjectByLocation(JULIET_ROOM_DOOR_POSITION);
        if (door != null && door.getId() == JULIET_DOOR_ID && Rs2GameObject.hasAction(door, "Open"))
        {
            state = RomeoState.OPENING_JULIET_DOORS;
            status = "Opening door to Juliet";
            if (interactionReady() && Rs2GameObject.interact(door, "Open")) markInteraction("juliet-door");
            return true;
        }

        if (player.distanceTo(JULIET_ROOM_INNER_POSITION) > 1)
        {
            Rs2Walker.walkFastCanvas(JULIET_ROOM_INNER_POSITION);
            return true;
        }
        return false;
    }

    private void talkToNpc(
            String name,
            WorldPoint destination,
            String walkKey,
            RomeoState walkingState,
            RomeoState talkingState)
    {
        Rs2NpcModel npc = findNpc(name);
        if (npc == null)
        {
            state = walkingState;
            status = "Walking to " + name;
            KspWalkerGuard.walkToPoint(walkKey, destination, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        WorldPoint npcLocation = npc.getWorldLocation();
        if (player == null || npcLocation == null) return;

        if (player.distanceTo(npcLocation) > NPC_REACH_DISTANCE)
        {
            state = walkingState;
            status = "Walking to " + name;
            KspWalkerGuard.walkFastCanvasToPoint(
                    walkKey, npcLocation, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clear(walkKey);
        if (!interactionReady()) return;

        state = talkingState;
        status = "Talking to " + name;
        if (npc.click("Talk-to")) markInteraction("npc-" + name);
    }

    private void gatherCadavaBerries()
    {
        state = RomeoState.WALKING_TO_CADAVA_BUSH;
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;

        if (player.distanceTo(CADAVA_BUSH_POSITION) > 3)
        {
            status = "Walking to Cadava berries";
            KspWalkerGuard.walkToPoint(WALK_BERRIES, CADAVA_BUSH_POSITION, 2, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clear(WALK_BERRIES);
        if (!interactionReady()) return;

        state = RomeoState.PICKING_CADAVA_BERRIES;
        status = "Picking Cadava berries";
        if (Rs2GameObject.interact(new int[] {
                ObjectID.FAI_VARROCK_CADAVABUSH_2,
                ObjectID.FAI_VARROCK_CADAVABUSH_1,
                ObjectID.FAI_VARROCK_CADAVABUSH_0 }, "take"))
        {
            markInteraction("cadava-bush");
        }
    }

    private Rs2NpcModel findNpc(String name)
    {
        return Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .withName(name)
                .nearestOnClientThread();
    }

    private boolean interactionReady()
    {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Player.isInteracting()) return false;

        if (pendingInteractionAtMs > 0L)
        {
            if (System.currentTimeMillis() - pendingInteractionAtMs < INTERACTION_TIMEOUT_MS) return false;
            clearPendingInteraction();
        }
        return System.currentTimeMillis() - lastActionAtMs >= ACTION_COOLDOWN_MS;
    }

    private void markInteraction(String key)
    {
        pendingInteractionKey = key;
        pendingInteractionAtMs = System.currentTimeMillis();
        lastActionAtMs = pendingInteractionAtMs;
    }

    private void clearPendingInteraction()
    {
        pendingInteractionKey = null;
        pendingInteractionAtMs = 0L;
    }

    private boolean has(Inv item)
    {
        return Rs2Inventory.itemQuantity(item.getItemId()) >= item.getQuantity();
    }

    private int getQuestStage()
    {
        return Microbot.getVarbitPlayerValue(QuestVarPlayer.QUEST_ROMEO_AND_JULIET.getId());
    }

    public boolean isComplete()
    {
        return complete
                || Rs2Player.getQuestState(Quest.ROMEO__JULIET) == QuestState.FINISHED
                || getQuestStage() > STAGE_FINISH;
    }

    public void setDebugLogging(boolean debugLogging) { this.debugLogging = debugLogging; }
    public RomeoState getState() { return state; }
    public String getStatus() { return status; }

    @Override
    public void shutdown()
    {
        KspWalkerGuard.clear(WALK_ROMEO);
        KspWalkerGuard.clear(WALK_LAWRENCE);
        KspWalkerGuard.clear(WALK_APOTHECARY);
        KspWalkerGuard.clear(WALK_BERRIES);
        KspWalkerGuard.clear(WALK_JULIET);
        clearPendingInteraction();
        lastActionAtMs = 0L;
        state = RomeoState.PREPARING;
        status = "Idle";
        super.shutdown();
    }
}
