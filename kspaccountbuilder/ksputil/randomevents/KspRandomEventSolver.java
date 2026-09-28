package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.randomevents;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

public class KspRandomEventSolver implements BlockingEvent {
    private Rs2NpcModel getRandomEventNpc() {
        var oldModel = Rs2Npc.getRandomEventNPC();
        if (oldModel == null) {
            return null;
        }

        return Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .where(npc -> npc.getNpc().equals(oldModel.getRuneliteNpc()))
                .nearest();
    }

    @Override
    public boolean validate() {
        Rs2NpcModel randomEventNpc = getRandomEventNpc();
        return randomEventNpc != null && randomEventNpc.hasLineOfSight();
    }

    @Override
    public boolean execute() {
        Rs2NpcModel npc = getRandomEventNpc();
        if (npc == null || npc.getName() == null) {
            return true;
        }

        if ("Count Check".equals(npc.getName()) || "Genie".equals(npc.getName())) {
            talkTo(npc);
        } else {
            dismiss(npc);
        }

        return !validate();
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.LOWEST;
    }

    private void talkTo(Rs2NpcModel npc) {
        npc.click("Talk-to");
        Rs2Dialogue.sleepUntilHasContinue();
        Rs2Dialogue.clickContinue();
    }

    private void dismiss(Rs2NpcModel npc) {
        npc.click("Dismiss");
        Global.sleepUntil(() -> getRandomEventNpc() == null);
    }
}
