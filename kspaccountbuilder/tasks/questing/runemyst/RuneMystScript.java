package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.runemyst;

import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspTaskDebug;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RuneMystScript extends Script
{
    private static final Logger log = LoggerFactory.getLogger(RuneMystScript.class);
    private static final int LOOP_DELAY_MS = 600;
    private static final int NPC_REACH_DISTANCE = 4;
    private static final long WALK_REFIRE_COOLDOWN_MS = 3_500L;
    private static final long ACTION_COOLDOWN_MS = 1_200L;

    private static final int AIR_TALISMAN_ID = 1438;
    private static final int RESEARCH_PACKAGE_ID = 290;
    private static final int RESEARCH_NOTES_ID = 291;

    private static final WorldPoint DUKE_HORACIO_POSITION = new WorldPoint(3210, 3222, 1);
    private static final WorldPoint SEDRIDOR_POSITION = new WorldPoint(3104, 9571, 0);
    private static final WorldPoint AUBURY_POSITION = new WorldPoint(3253, 3401, 0);

    private static final String WALK_KEY_DUKE = "Rune Mysteries:duke-horacio";
    private static final String WALK_KEY_SEDRIDOR = "Rune Mysteries:sedridor";
    private static final String WALK_KEY_AUBURY = "Rune Mysteries:aubury";

    private boolean debugLogging;
    private boolean complete;
    private boolean finalSedridorDeliveryStarted;
    private long lastActionAtMs;
    private RuneMystState state = RuneMystState.PREPARING;
    private String status = "Idle";

    public boolean run()
    {
        shutdown();
        complete = false;
        finalSedridorDeliveryStarted = false;
        lastActionAtMs = 0L;
        state = RuneMystState.PREPARING;
        status = "Starting Rune Mysteries";

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
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void runLoop()
    {
        if (!super.run() || !Microbot.isLoggedIn())
        {
            return;
        }

        if (isQuestComplete())
        {
            markComplete();
            return;
        }

        if (finalSedridorDeliveryStarted
                && !Rs2Inventory.hasItem(RESEARCH_NOTES_ID)
                && !Rs2Dialogue.isInDialogue())
        {
            markComplete();
            return;
        }

        KspTaskDebug.throttled(log, debugLogging, "Rune Mysteries", "loop", 5_000L,
                "loop | state={} status={} player={} dialogue={} talisman={} package={} notes={}",
                state,
                status,
                Rs2Player.getWorldLocation(),
                Rs2Dialogue.isInDialogue(),
                Rs2Inventory.itemQuantity(AIR_TALISMAN_ID),
                Rs2Inventory.itemQuantity(RESEARCH_PACKAGE_ID),
                Rs2Inventory.itemQuantity(RESEARCH_NOTES_ID));

        if (handleDialogueOrCutscene())
        {
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating())
        {
            return;
        }

        if (Rs2Inventory.hasItem(RESEARCH_NOTES_ID))
        {
            finalSedridorDeliveryStarted = true;
            talkToQuestNpc(
                    "Archmage Sedridor",
                    SEDRIDOR_POSITION,
                    WALK_KEY_SEDRIDOR,
                    RuneMystState.RETURNING_TO_SEDRIDOR,
                    RuneMystState.TALKING_TO_SEDRIDOR,
                    "Returning to Archmage Sedridor");
            return;
        }

        if (Rs2Inventory.hasItem(RESEARCH_PACKAGE_ID))
        {
            talkToQuestNpc(
                    "Aubury",
                    AUBURY_POSITION,
                    WALK_KEY_AUBURY,
                    RuneMystState.WALKING_TO_AUBURY,
                    RuneMystState.TALKING_TO_AUBURY,
                    "Walking to Aubury");
            return;
        }

        if (Rs2Inventory.hasItem(AIR_TALISMAN_ID)
                || Rs2Player.getQuestState(Quest.RUNE_MYSTERIES) == QuestState.IN_PROGRESS)
        {
            talkToQuestNpc(
                    "Archmage Sedridor",
                    SEDRIDOR_POSITION,
                    WALK_KEY_SEDRIDOR,
                    RuneMystState.WALKING_TO_SEDRIDOR,
                    RuneMystState.TALKING_TO_SEDRIDOR,
                    "Walking to Archmage Sedridor");
            return;
        }

        talkToQuestNpc(
                "Duke Horacio",
                DUKE_HORACIO_POSITION,
                WALK_KEY_DUKE,
                RuneMystState.WALKING_TO_DUKE,
                RuneMystState.TALKING_TO_DUKE,
                "Walking to Duke Horacio");
    }

    private boolean handleDialogueOrCutscene()
    {
        if (Rs2Dialogue.isInCutScene())
        {
            KspWalkerGuard.clearActiveWalker("ksp_rune_mysteries_cutscene");
            status = "Waiting for Rune Mysteries cutscene";
            if (Rs2Dialogue.hasContinue())
            {
                Rs2Dialogue.clickContinue();
            }
            return true;
        }

        if (!Rs2Dialogue.isInDialogue())
        {
            return false;
        }

        KspWalkerGuard.clearActiveWalker("ksp_rune_mysteries_dialogue");
        status = "Handling Rune Mysteries dialogue";

        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            return true;
        }

        if (!Rs2Dialogue.hasSelectAnOption())
        {
            return true;
        }

        if (finalSedridorDeliveryStarted && !Rs2Inventory.hasItem(RESEARCH_NOTES_ID))
        {
            status = "Waiting for Rune Mysteries completion";
            return true;
        }

        String[] preferredOptions = {
                "Have you any quests for me?",
                "Sure, no problem.",
                "Yes.",
                "I have been sent here with a package for you.",
                "I have some research notes for you."
        };
        for (String option : preferredOptions)
        {
            if (Rs2Dialogue.clickOption(option, false))
            {
                return true;
            }
        }

        if (Rs2Dialogue.acceptQuestStartDialogue()
                || Rs2Dialogue.handleQuestOptionDialogueSelection())
        {
            return true;
        }

        return Rs2Dialogue.keyPressForDialogueOption(1);
    }

    private void talkToQuestNpc(
            String npcName,
            WorldPoint destination,
            String walkKey,
            RuneMystState walkingState,
            RuneMystState talkingState,
            String walkingStatus)
    {
        Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .withName(npcName)
                .nearestOnClientThread();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (npc == null || playerLocation == null || npc.getWorldLocation() == null
                || playerLocation.distanceTo(npc.getWorldLocation()) > NPC_REACH_DISTANCE)
        {
            state = walkingState;
            status = walkingStatus;
            KspWalkerGuard.walkToPoint(walkKey, destination, NPC_REACH_DISTANCE, WALK_REFIRE_COOLDOWN_MS);
            return;
        }

        state = talkingState;
        status = "Talking to " + npcName;
        KspWalkerGuard.clear(walkKey);
        if (System.currentTimeMillis() - lastActionAtMs < ACTION_COOLDOWN_MS)
        {
            return;
        }

        lastActionAtMs = System.currentTimeMillis();
        if (npc.click("Talk-to"))
        {
            sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
        }
    }

    private boolean isQuestComplete()
    {
        return Rs2Player.getQuestState(Quest.RUNE_MYSTERIES) == QuestState.FINISHED;
    }

    private void markComplete()
    {
        complete = true;
        state = RuneMystState.COMPLETE;
        status = "Rune Mysteries complete";
        shutdown();
    }

    public boolean isComplete()
    {
        return complete || isQuestComplete();
    }

    public RuneMystState getState()
    {
        return state;
    }

    public String getStatus()
    {
        return status;
    }

    public void setDebugLogging(boolean debugLogging)
    {
        this.debugLogging = debugLogging;
    }

    @Override
    public void shutdown()
    {
        KspWalkerGuard.clear(WALK_KEY_DUKE);
        KspWalkerGuard.clear(WALK_KEY_SEDRIDOR);
        KspWalkerGuard.clear(WALK_KEY_AUBURY);
        super.shutdown();
    }
}
