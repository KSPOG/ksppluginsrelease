package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.sheepshearer.sheepscript;

import javax.inject.Singleton;
import net.runelite.api.Quest;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.QuestRequirement;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.SimpleQuestScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.sheepshearer.reqs.SheepRequirements;

@Singleton
public class SheepScript extends SimpleQuestScript
{
    public SheepScript() { super(Quest.SHEEP_SHEARER, "Sheep Shearer"); }
    @Override protected QuestRequirement[] requirements() { return SheepRequirements.remaining(varp(VarPlayerID.SHEEP)); }
    @Override protected String[] dialogueOptions()
    {
        return new String[] { "I'm looking for a quest.", "Yes, okay. I can do that.",
                "I need to talk to you about shearing these sheep!", "Yes." };
    }
    @Override protected void progressQuest() { talkTo("Fred the Farmer", new WorldPoint(3190, 3273, 0)); }
}
