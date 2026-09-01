package net.runelite.client.plugins.microbot.kspaiofighter;

final class KspAioFighterInventoryItem
{
    private final int id;
    private final int quantity;
    private final int slot;
    private final String name;
    private final boolean noted;

    KspAioFighterInventoryItem(int id, int quantity, int slot, String name, boolean noted)
    {
        this.id = id;
        this.quantity = Math.max(1, quantity);
        this.slot = Math.max(0, Math.min(27, slot));
        this.name = name == null ? "" : name.trim();
        this.noted = noted;
    }

    int getId()
    {
        return id;
    }

    int getQuantity()
    {
        return quantity;
    }

    int getSlot()
    {
        return slot;
    }

    String getName()
    {
        return name;
    }

    boolean isNoted()
    {
        return noted;
    }
}
