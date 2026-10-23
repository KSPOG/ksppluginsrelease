package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeoscript;

import net.runelite.client.plugins.microbot.kspaccountbuilder.KspManagedScript;

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
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RomeoScript extends KspManagedScript
{
    private static final Logger log = LoggerFactory.getLogger(RomeoScript.class);

    private static final int LOOP_DELAY_MS = 80;
    private static final int NPC_REACH_DISTANCE = 6;
    private static final long WALK_REFIRE_COOLDOWN_MS = 900L;
    private static final long ACTION_COOLDOWN_MS = 200L;
    private static final long INTERACTION_TIMEOUT_MS = 2_500L;
    private static final long BANK_ACTION_COOLDOWN_MS = 900L;

    private static final int STAGE_NOT_STARTED = 0;
    private static final int STAGE_JULIET = 10;
    private static final int STAGE_ROMEO_MESSAGE = 20;
    private static final int STAGE_LAWRENCE = 30;
    private static final int STAGE_APOTHECARY = 40;
    private static final int STAGE_JULIET_POTION = 50;
    private static final int STAGE_FINISH = 60;

    // Matches Quest Helper's Romeo & Juliet route/object locations.
    private static final WorldPoint JULIET_STAIR_ORIGIN = new WorldPoint(3157, 3436, 0);
    // Reachable tile just east of the staircase. Stop the web walker here and
    // interact with the stairs directly instead of letting the walker own the
    // final 2-3 tiles inside Juliet's house.
    private static final WorldPoint JULIET_STAIR_APPROACH = new WorldPoint(3159, 3436, 0);
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
    private boolean cadavaBankChecked;
    private int lastStage = -1;
    private RomeoState state = RomeoState.PREPARING;
    private String status = "Idle";
    private long lastActionAtMs;
    private long lastBankActionAtMs;
    private long pendingInteractionAtMs;
    private String pendingInteractionKey;

    public boolean run()
    {
        shutdown();
        complete = false;
        cadavaBankChecked = false;
        lastStage = -1;
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
            KspWalkerGuard.clearActiveWalker("ksp_romeo_complete");
            return;
        }

        if (stage != lastStage)
        {
            KspWalkerGuard.clearActiveWalker("ksp_romeo_stage_changed_" + stage);
            clearPendingInteraction();
            lastStage = stage;
        }

        KspTaskDebug.throttled(log, debugLogging, "Romeo and Juliet", "loop", 5_000L,
                "loop | state={} status={} player={} stage={} dialogue={} pending={}",
                state, status, Rs2Player.getWorldLocation(), stage,
                Rs2Dialogue.isInDialogue(), pendingInteractionKey);

        if (handleDialogue(stage)) return;

        if (Rs2Dialogue.isInCutScene())
        {
            state = RomeoState.WAITING_FOR_CUTSCENE;
            status = "Waiting for cutscene";
            KspWalkerGuard.clearActiveWalker("ksp_romeo_cutscene");
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;

        // A previous task may hand Romeo & Juliet an already-open bank. Before
        // closing it, resolve Cadava berries once. More importantly, when
        // ensureCadavaBerries() opens a bank itself, the Cadava preparation owns
        // that bank until the lookup/withdrawal has completed; otherwise the
        // generic handoff cleanup closes it again on the next tick and loops.
        if (stage > STAGE_NOT_STARTED && Rs2Bank.isOpen())
        {
            boolean mayStillNeedCadava = stage <= STAGE_JULIET_POTION
                    && !cadavaBankChecked
                    && !has(Inv.CADAVA_BERRIES)
                    && !has(Inv.CADAVA_POTION);

            if (mayStillNeedCadava)
            {
                ensureCadavaBerries();
                return;
            }

            state = RomeoState.PREPARING;
            status = "Closing bank before quest route";
            KspWalkerGuard.clear(WALK_JULIET);
            if (System.currentTimeMillis() - lastBankActionAtMs >= BANK_ACTION_COOLDOWN_MS)
            {
                Rs2Bank.closeBank();
                lastBankActionAtMs = System.currentTimeMillis();
            }
            return;
        }

        if (pendingInteractionKey != null && pendingInteractionKey.startsWith("stairs-"))
        {
            boolean arrived = pendingInteractionKey.equals("stairs-up")
                    ? player.getPlane() == 1
                    : player.getPlane() == 0;
            if (arrived) clearPendingInteraction();
            else if (System.currentTimeMillis() - pendingInteractionAtMs < INTERACTION_TIMEOUT_MS)
            {
                status = pendingInteractionKey.equals("stairs-up")
                        ? "Waiting to reach Juliet upstairs"
                        : "Waiting to go downstairs";
                return;
            }
            else clearPendingInteraction();
        }

        switch (stage)
        {
            case STAGE_NOT_STARTED:
                if (!ensureCadavaBerries()) return;
                talkToNpc("Romeo", ROMEO_POSITION, WALK_ROMEO,
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
                if (!ensureCadavaBerries()) return;
                talkToNpc("Apothecary", APOTHECARY_POSITION, WALK_APOTHECARY,
                        RomeoState.WALKING_TO_APOTHECARY, RomeoState.TALKING_TO_APOTHECARY);
                break;

            case STAGE_JULIET_POTION:
                if (has(Inv.CADAVA_POTION))
                {
                    talkToJuliet(true);
                }
                else
                {
                    if (!has(Inv.CADAVA_BERRIES) && !ensureCadavaBerries()) return;
                    talkToNpc("Apothecary", APOTHECARY_POSITION, WALK_APOTHECARY,
                            RomeoState.WALKING_TO_APOTHECARY, RomeoState.TALKING_TO_APOTHECARY);
                }
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

    /**
     * Quest Helper-backed dialogue routing. Never presses option 1 blindly.
     */
    private boolean handleDialogue(int stage)
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

        if (!Rs2Dialogue.hasSelectAnOption()) return true;

        // Romeo can leave his informational menu open after the quest has already
        // advanced from stage 0. That stale menu blocks movement. Dismiss only
        // this known post-start Romeo menu; later quest dialogue still uses the
        // normal handlers below.
        if (stage > STAGE_NOT_STARTED && isStaleRomeoInfoMenu())
        {
            if (clickOptionPartial("Ok, thanks"))
            {
                status = "Closing finished Romeo dialogue";
                return true;
            }
        }

        // Apothecary quest branch must beat his normal potion menu.
        if (clickOption("Talk about something else.")) return true;
        if (clickOption("Talk about Romeo & Juliet.")) return true;

        // Exact Quest Helper start sequence.
        if (stage == STAGE_NOT_STARTED)
        {
            if (clickOption("Yes, I have seen her actually!")) return true;
            if (clickOption("Perhaps I could help to find her for you?")) return true;
            if (clickOption("Yes, ok, I'll let her know.")) return true;
            if (clickOption("Yes.")) return true;
        }

        // If Juliet's father intercepts us, take the non-hostile route.
        if (clickOption("I've just come to have a chat with Juliet.")) return true;

        // Once the quest has progressed, informational Romeo/Lawrence menus can
        // remain open even though movement should continue. Use a partial match
        // so punctuation/cache text differences cannot trap the route.
        if (stage > STAGE_NOT_STARTED && clickOptionPartial("Ok, thanks")) return true;

        // Do not guess. A wrong generic option can keep the quest on the same
        // stage forever; wait for a known quest option instead.
        status = "Waiting for Romeo & Juliet quest dialogue option";
        return true;
    }

    private boolean clickOption(String option)
    {
        if (!Rs2Dialogue.hasDialogueOption(option, true)) return false;
        Rs2Dialogue.clickOption(option, true);
        lastActionAtMs = System.currentTimeMillis();
        return true;
    }

    private boolean clickOptionPartial(String option)
    {
        if (!Rs2Dialogue.hasDialogueOption(option, false)) return false;
        Rs2Dialogue.clickOption(option, false);
        lastActionAtMs = System.currentTimeMillis();
        return true;
    }

    private boolean isStaleRomeoInfoMenu()
    {
        // Romeo keeps offering informational menus after the quest var has
        // already advanced. Those menus must not retain tick ownership because
        // they block the next route (Juliet/Father Lawrence).
        return Rs2Dialogue.hasDialogueOption("Where can I find Juliet", false)
                || Rs2Dialogue.hasDialogueOption("anything else you can tell me about Juliet", false)
                || Rs2Dialogue.hasDialogueOption("Where can I find Father Lawrence", false)
                || Rs2Dialogue.hasDialogueOption("Have you heard anything from Juliet", false)
                || Rs2Dialogue.hasDialogueOption("How are you", false);
    }

    private boolean ensureCadavaBerries()
    {
        if (has(Inv.CADAVA_BERRIES))
        {
            cadavaBankChecked = true;
            if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
            return true;
        }

        if (!cadavaBankChecked)
        {
            state = RomeoState.PREPARING;
            status = "Checking bank for Cadava berries";

            if (!Rs2Bank.isOpen())
            {
                if (!Rs2Bank.openBank()) Rs2Bank.walkToBankAndUseBank();
                return false;
            }

            int bankCount = Rs2Bank.count(Inv.CADAVA_BERRIES.getItemId());
            if (bankCount > 0)
            {
                if (System.currentTimeMillis() - lastBankActionAtMs >= BANK_ACTION_COOLDOWN_MS)
                {
                    int before = Rs2Inventory.itemQuantity(Inv.CADAVA_BERRIES.getItemId());
                    if (Rs2Bank.withdrawOne(Inv.CADAVA_BERRIES.getItemId()))
                    {
                        lastBankActionAtMs = System.currentTimeMillis();
                        status = "Withdrawing Cadava berries";
                        if (Rs2Inventory.itemQuantity(Inv.CADAVA_BERRIES.getItemId()) > before)
                        {
                            cadavaBankChecked = true;
                            Rs2Bank.closeBank();
                        }
                    }
                }
                return false;
            }

            cadavaBankChecked = true;
            Rs2Bank.closeBank();
            return false;
        }

        gatherCadavaBerries();
        return false;
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

        // A task switch may finish at an upstairs bank such as Lumbridge (plane 2).
        // Previously this method returned forever on every plane other than 0/1.
        // Let the web walker descend to Juliet's ground-floor staircase first.
        if (player.getPlane() != 1)
        {
            state = potion ? RomeoState.RETURNING_POTION_TO_JULIET : RomeoState.WALKING_TO_JULIET_HALLWAY;
            status = "Leaving upper floor for Juliet";
            KspWalkerGuard.walkToPoint(WALK_JULIET, JULIET_STAIR_ORIGIN, 2, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        Rs2NpcModel juliet = findNpc("Juliet");
        if (juliet != null && juliet.getWorldLocation() != null
                && player.getPlane() == juliet.getWorldLocation().getPlane()
                && player.distanceTo(juliet.getWorldLocation()) <= 8
                && juliet.isReachable())
        {
            KspWalkerGuard.clear(WALK_JULIET);
            KspWalkerGuard.clearActiveWalker("ksp_romeo_reachable_juliet");
            if (!Rs2Player.isMoving() && interactionReady())
            {
                state = RomeoState.TALKING_TO_JULIET;
                status = potion ? "Giving potion to Juliet" : "Talking to Juliet";
                if (juliet.click("Talk-to"))
                {
                    markInteraction("npc-juliet");
                    return;
                }
            }
        }

        if (passJulietRoomDoor()) return;

        if (player.distanceTo(JULIET_POSITION) > NPC_REACH_DISTANCE)
        {
            state = potion ? RomeoState.RETURNING_POTION_TO_JULIET : RomeoState.WALKING_TO_JULIET_HALLWAY;
            status = "Walking to Juliet's room";
            KspWalkerGuard.walkToPoint(WALK_JULIET, JULIET_POSITION, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clear(WALK_JULIET);
        if (juliet == null || !interactionReady()) return;

        state = RomeoState.TALKING_TO_JULIET;
        status = potion ? "Giving potion to Juliet" : "Talking to Juliet";
        if (juliet.click("Talk-to")) markInteraction("npc-juliet");
    }

    private void climbToJuliet(WorldPoint player)
    {
        state = RomeoState.CLIMBING_TO_JULIET;

        // Let the web walker handle the outside route and house door, but do not
        // make the staircase object itself its destination. The core walker can
        // otherwise keep a tiny final route alive at 3159,3436 and repeatedly
        // re-run door/path checks instead of yielding to the stair interaction.
        if (player.distanceTo(JULIET_STAIR_APPROACH) > 3)
        {
            status = "WebWalking to Juliet staircase";
            KspWalkerGuard.walkToPoint(WALK_JULIET, JULIET_STAIR_APPROACH, 3, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        // We are at the known reachable approach tile. Release walker ownership
        // immediately and let the quest object interaction take over.
        KspWalkerGuard.clear(WALK_JULIET);
        KspWalkerGuard.clearActiveWalker("ksp_romeo_at_juliet_staircase");
        if (Rs2Player.isMoving()) return;
        if (!interactionReady()) return;

        status = "Climbing staircase to Juliet";
        if (Rs2GameObject.interact(ObjectID.FAI_VARROCK_STAIRS_TALLER, "Climb-up"))
        {
            markInteraction("stairs-up");
        }
    }

    private void leaveJulietHouse()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.getPlane() != 1) return;

        state = RomeoState.LEAVING_JULIET_HOUSE;
        if (player.distanceTo(JULIET_TOP_STAIR) > 3)
        {
            status = "Walking to Juliet staircase";
            KspWalkerGuard.walkToPoint(WALK_JULIET, JULIET_TOP_STAIR, 3, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        KspWalkerGuard.clear(WALK_JULIET);
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
            KspWalkerGuard.walkToPoint(WALK_JULIET, JULIET_ROOM_INNER_POSITION, 1, WALK_REFIRE_COOLDOWN_MS);
            return true;
        }
        return false;
    }

    private void talkToNpc(String name, WorldPoint destination, String walkKey,
                           RomeoState walkingState, RomeoState talkingState)
    {
        Rs2NpcModel npc = findNpc(name);
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;

        // A visible/reachable quest NPC takes priority over the walker. This is
        // especially important for Father Lawrence and Romeo because their live
        // NPC tile can move while the web walker is still targeting the previous
        // location. Do not rebuild that route when a direct click is possible.
        if (npc != null && npc.getWorldLocation() != null
                && player.getPlane() == npc.getWorldLocation().getPlane()
                && player.distanceTo(npc.getWorldLocation()) <= 8
                && npc.isReachable())
        {
            KspWalkerGuard.clear(walkKey);
            KspWalkerGuard.clearActiveWalker("ksp_romeo_reachable_npc_" + name);
            if (!Rs2Player.isMoving() && interactionReady())
            {
                state = talkingState;
                status = "Talking to " + name;
                if (npc.click("Talk-to"))
                {
                    markInteraction("npc-" + name);
                    return;
                }
            }
        }

        if (player.getPlane() != destination.getPlane()
                || player.distanceTo(destination) > NPC_REACH_DISTANCE)
        {
            state = walkingState;
            status = "Walking near " + name;
            KspWalkerGuard.walkToPoint(walkKey, destination, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        if (npc == null || npc.getWorldLocation() == null)
        {
            KspWalkerGuard.clear(walkKey);
            status = "Searching for " + name;
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
            status = "WebWalking to Cadava berries";
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
        lastBankActionAtMs = 0L;
        lastStage = -1;
        cadavaBankChecked = false;
        state = RomeoState.PREPARING;
        status = "Idle";
        super.shutdown();
    }
}
