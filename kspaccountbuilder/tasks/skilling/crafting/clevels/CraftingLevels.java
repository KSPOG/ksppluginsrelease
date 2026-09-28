package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.clevels;

public enum CraftingLevels
{
    LEATHER_GLOVES("Leather gloves", 1),
    GOLD_RING("Gold ring", 5),
    GOLD_NECKLACE("Gold necklace", 8),
    CUT_SAPPHIRE("Cut sapphire", 20),
    SAPPHIRE_RING("Sapphire ring", 20),
    SAPPHIRE_NECKLACE("Sapphire necklace", 22),
    TIARA("Tiara", 23),
    CUT_EMERALD("Cut emerald", 27),
    EMERALD_RING("Emerald ring", 27),
    EMERALD_NECKLACE("Emerald necklace", 29),
    CUT_RUBY("Cut ruby", 34),
    RUBY_RING("Ruby ring", 34),
    RUBY_NECKLACE("Ruby necklace", 40),
    CUT_DIAMOND("Cut diamond", 43),
    DIAMOND_RING("Diamond ring", 43),
    DIAMOND_NECKLACE("Diamond necklace", 56);

    private final String displayName;
    private final int requiredLevel;

    CraftingLevels(String displayName, int requiredLevel)
    {
        this.displayName = displayName;
        this.requiredLevel = requiredLevel;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public int getRequiredLevel()
    {
        return requiredLevel;
    }
}
