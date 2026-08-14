package net.runelite.client.plugins.microbot.kspbankorganizer;

final class PlannedItem
{
    private final int itemId;
    private final String name;
    private final ItemCategory category;
    private final int targetTab;

    PlannedItem(int itemId, String name, ItemCategory category, int targetTab)
    {
        this.itemId = itemId;
        this.name = name;
        this.category = category;
        this.targetTab = targetTab;
    }

    int itemId() { return itemId; }
    String name() { return name; }
    ItemCategory category() { return category; }
    int targetTab() { return targetTab; }
}
