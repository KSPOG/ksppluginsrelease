package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.impcatcher.impscript;

import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.impcatcher.reqs.ImpRequirements;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.QuestRequirement;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.SimpleQuestScript;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Singleton
public class ImpScript extends SimpleQuestScript
{
    public ImpScript() { super(Quest.IMP_CATCHER, "Imp Catcher"); }
    @Override protected QuestRequirement[] requirements() { return ImpRequirements.beads(); }
    @Override protected String[] dialogueOptions() { return new String[] { "Give me a quest please.", "Yes.", "Up", "Climb up the stairs." }; }
    @Override protected void progressQuest()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;
        if (player.getPlane() == 2) talkTo("Wizard Mizgog", new WorldPoint(3103, 3163, 2));
        else if (player.getPlane() == 1)
            interactObject(ObjectID.FAI_WIZTOWER_SPIRALSTAIRS_MIDDLE, "Climb-up", new WorldPoint(3103, 3159, 1));
        else interactObject(ObjectID.FAI_WIZTOWER_SPIRALSTAIRS, "Climb-up", new WorldPoint(3103, 3159, 0));
    }
}
