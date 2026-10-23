package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.sheepshearer.reqs;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.QuestRequirement;

public final class SheepRequirements
{
    private SheepRequirements() {}
    public static QuestRequirement[] remaining(int stage)
    {
        int quantity = stage > 1 ? Math.max(0, 21 - stage) : 20;
        return new QuestRequirement[] { new QuestRequirement(ItemID.BALL_OF_WOOL, "Ball of wool", quantity) };
    }
}
