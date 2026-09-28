package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeinv;

import net.runelite.api.ItemID;

public enum Inv
{
    CADAVA_BERRIES("Cadava berries", ItemID.CADAVA_BERRIES, 1),
    MESSAGE("Message", 755, 1),
    CADAVA_POTION("Cadava potion", 756, 1);

    private final String displayName;
    private final int itemId;
    private final int quantity;

    Inv(String displayName, int itemId, int quantity)
    {
        this.displayName = displayName;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public int getItemId()
    {
        return itemId;
    }

    public int getQuantity()
    {
        return quantity;
    }
}
