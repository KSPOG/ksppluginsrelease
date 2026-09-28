package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.levelreqfishing;

public enum LevelReqs
{
    SHRIMP("Shrimp", 1),
    SARDINE("Sardine", 5),
    HERRING("Herring", 10),
    TROUT("Trout", 20),
    SALMON("Salmon", 30),
    TUNA("Tuna", 35),
    LOBSTER("Lobster", 40),
    SWORDFISH("Swordfish", 50);

    private final String displayName;
    private final int requiredFishingLevel;

    LevelReqs(String displayName, int requiredFishingLevel)
    {
        this.displayName = displayName;
        this.requiredFishingLevel = requiredFishingLevel;
    }

    public static LevelReqs bestForFishingLevel(int fishingLevel)
    {
        LevelReqs best = SHRIMP;
        for (LevelReqs fish : values())
        {
            if (fishingLevel >= fish.requiredFishingLevel)
            {
                best = fish;
            }
        }
        return best;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public int getRequiredFishingLevel()
    {
        return requiredFishingLevel;
    }
}
