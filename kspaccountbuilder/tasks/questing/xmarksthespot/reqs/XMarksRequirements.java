package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.xmarksthespot.reqs;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.QuestRequirement;

public final class XMarksRequirements
{
    private XMarksRequirements() {}
    public static QuestRequirement[] spade() { return new QuestRequirement[] { new QuestRequirement(ItemID.SPADE, "Spade", 1) }; }
}
