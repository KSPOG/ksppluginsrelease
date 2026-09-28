package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.needed;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum Inventory
{
    SHRIMP("Shrimp", "Small fishing net"),
    SARDINE("Sardine", "Fishing rod", "Fishing bait"),
    HERRING("Herring", "Fishing rod", "Fishing bait"),
    TROUT("Trout", "Fly fishing rod", "Feather"),
    SALMON("Salmon", "Fly fishing rod", "Feather"),
    TUNA("Tuna", "Harpoon", "Coins"),
    LOBSTER("Lobster", "Lobster pot", "Coins"),
    SWORDFISH("Swordfish", "Harpoon", "Coins");

    private final String displayName;
    private final List<String> requiredItems;

    Inventory(String displayName, String... requiredItems)
    {
        this.displayName = displayName;
        this.requiredItems = Collections.unmodifiableList(Arrays.asList(requiredItems));
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public List<String> getRequiredItems()
    {
        return requiredItems;
    }
}
