package net.runelite.client.plugins.microbot.kspdirectfishing;

import net.runelite.client.game.FishingSpot;

import java.util.List;

public enum KspDirectFishingMode
{
    SHRIMP_ANCHOVIES(
            "Shrimp / Anchovies",
            "Small Net",
            List.of("Small fishing net"),
            List.of("Raw shrimps", "Raw anchovies"),
            FishingSpot.SHRIMP.getIds()
    ),

    SARDINE_HERRING(
            "Sardine / Herring",
            "Bait",
            List.of("Fishing rod", "Fishing bait"),
            List.of("Raw sardine", "Raw herring"),
            FishingSpot.SHRIMP.getIds()
    );

    private final String displayName;
    private final String primaryAction;
    private final List<String> requiredItems;
    private final List<String> rawFish;
    private final int[] fishingSpotIds;

    KspDirectFishingMode(
            String displayName,
            String primaryAction,
            List<String> requiredItems,
            List<String> rawFish,
            int[] fishingSpotIds)
    {
        this.displayName = displayName;
        this.primaryAction = primaryAction;
        this.requiredItems = requiredItems;
        this.rawFish = rawFish;
        this.fishingSpotIds = fishingSpotIds;
    }

    public String getPrimaryAction()
    {
        return primaryAction;
    }

    public List<String> getRequiredItems()
    {
        return requiredItems;
    }

    public List<String> getRawFish()
    {
        return rawFish;
    }

    public int[] getFishingSpotIds()
    {
        return fishingSpotIds;
    }

    public boolean usesBait()
    {
        return this == SARDINE_HERRING;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
