package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.levels;

import net.runelite.api.gameval.ItemID;

public enum CookLevels
{
    SHRIMP("Raw shrimps", ItemID.RAW_SHRIMP, "Shrimps", 1),
    SARDINE("Raw sardine", ItemID.RAW_SARDINE, "Sardine", 1),
    HERRING("Raw herring", ItemID.RAW_HERRING, "Herring", 5),
    TROUT("Raw trout", ItemID.RAW_TROUT, "Trout", 15),
    SALMON("Raw salmon", ItemID.RAW_SALMON, "Salmon", 25),
    TUNA("Raw tuna", ItemID.RAW_TUNA, "Tuna", 30),
    LOBSTER("Raw lobster", ItemID.RAW_LOBSTER, "Lobster", 40),
    SWORDFISH("Raw swordfish", ItemID.RAW_SWORDFISH, "Swordfish", 45);

    private final String rawItemName;
    private final int rawItemId;
    private final String cookedItemName;
    private final int requiredLevel;

    CookLevels(String rawItemName, int rawItemId, String cookedItemName, int requiredLevel)
    {
        this.rawItemName = rawItemName;
        this.rawItemId = rawItemId;
        this.cookedItemName = cookedItemName;
        this.requiredLevel = requiredLevel;
    }

    public String getRawItemName()
    {
        return rawItemName;
    }

    public int getRawItemId()
    {
        return rawItemId;
    }

    public String getCookedItemName()
    {
        return cookedItemName;
    }

    public int getRequiredLevel()
    {
        return requiredLevel;
    }
}
