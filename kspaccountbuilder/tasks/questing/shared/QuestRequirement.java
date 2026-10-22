package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared;

/** A tradeable, unnoted item required by the current quest stage. */
public final class QuestRequirement
{
    public final int itemId;
    public final String name;
    public final int quantity;

    public QuestRequirement(int itemId, String name, int quantity)
    {
        this.itemId = itemId;
        this.name = name;
        this.quantity = quantity;
    }
}
