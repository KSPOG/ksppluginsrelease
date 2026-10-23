package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.randomevents;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

/** Random-event handling that advances from NPC/dialogue state instead of blocking sleeps. */
public class KspRandomEventSolver implements BlockingEvent
{
    private static final long INTERACTION_RECOVERY_MS = 1_500L;

    private int pendingNpcIndex = -1;
    private long pendingInteractionAtMs;

    private Rs2NpcModel getRandomEventNpc()
    {
        var oldModel = Rs2Npc.getRandomEventNPC();
        if (oldModel == null) return null;

        return Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .where(npc -> npc.getNpc().equals(oldModel.getRuneliteNpc()))
                .nearest();
    }

    @Override
    public boolean validate()
    {
        Rs2NpcModel randomEventNpc = getRandomEventNpc();
        return randomEventNpc != null && randomEventNpc.hasLineOfSight();
    }

    @Override
    public boolean execute()
    {
        Rs2NpcModel npc = getRandomEventNpc();
        if (npc == null || npc.getName() == null)
        {
            clearPending();
            return true;
        }

        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            return false;
        }

        long now = System.currentTimeMillis();
        if (pendingNpcIndex == npc.getIndex()
                && pendingInteractionAtMs != 0L
                && now - pendingInteractionAtMs < INTERACTION_RECOVERY_MS)
        {
            return false;
        }

        boolean sent;
        if ("Count Check".equals(npc.getName()) || "Genie".equals(npc.getName()))
        {
            sent = npc.click("Talk-to");
        }
        else
        {
            sent = npc.click("Dismiss");
        }

        if (sent)
        {
            pendingNpcIndex = npc.getIndex();
            pendingInteractionAtMs = now;
        }
        return !validate();
    }

    private void clearPending()
    {
        pendingNpcIndex = -1;
        pendingInteractionAtMs = 0L;
    }

    @Override
    public BlockingEventPriority priority()
    {
        return BlockingEventPriority.LOWEST;
    }
}
