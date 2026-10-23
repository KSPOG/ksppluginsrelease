package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.impcatcher.reqs;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.QuestRequirement;

public final class ImpRequirements
{
    private ImpRequirements() {}
    public static QuestRequirement[] beads()
    {
        return new QuestRequirement[] {
            new QuestRequirement(ItemID.BLACK_BEAD, "Black bead", 1),
            new QuestRequirement(ItemID.WHITE_BEAD, "White bead", 1),
            new QuestRequirement(ItemID.RED_BEAD, "Red bead", 1),
            new QuestRequirement(ItemID.YELLOW_BEAD, "Yellow bead", 1)
        };
    }
}
