package net.runelite.client.plugins.microbot.kspbankorganizer;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class PlannedItem
{
    private final int itemId;
    private final String name;
    private final ItemCategory category;
    private final int targetTab;

    int itemId() { return itemId; }
    String name() { return name; }
    ItemCategory category() { return category; }
    int targetTab() { return targetTab; }
}
