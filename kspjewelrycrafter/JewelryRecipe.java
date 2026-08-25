package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Furnace-crafted jewellery only.
 *
 * membersOnly is a hard eligibility flag. An F2P account can never select a
 * members-only recipe regardless of profitability.
 */
public enum JewelryRecipe
{
    // Plain gold jewellery
    GOLD_RING("Gold ring", 5, false, "Gold bar", null, "Ring mould", 15.0),
    GOLD_NECKLACE("Gold necklace", 6, false, "Gold bar", null, "Necklace mould", 20.0),
    GOLD_BRACELET("Gold bracelet", 7, true, "Gold bar", null, "Bracelet mould", 25.0),
    GOLD_AMULET("Gold amulet (u)", 8, false, "Gold bar", null, "Amulet mould", 30.0),

    // Sapphire
    SAPPHIRE_RING("Sapphire ring", 20, false, "Gold bar", "Sapphire", "Ring mould", 40.0),
    SAPPHIRE_NECKLACE("Sapphire necklace", 22, false, "Gold bar", "Sapphire", "Necklace mould", 55.0),
    SAPPHIRE_BRACELET("Sapphire bracelet", 23, true, "Gold bar", "Sapphire", "Bracelet mould", 60.0),
    SAPPHIRE_AMULET("Sapphire amulet (u)", 24, false, "Gold bar", "Sapphire", "Amulet mould", 65.0),

    // Emerald
    EMERALD_RING("Emerald ring", 27, false, "Gold bar", "Emerald", "Ring mould", 55.0),
    EMERALD_NECKLACE("Emerald necklace", 29, false, "Gold bar", "Emerald", "Necklace mould", 60.0),
    EMERALD_BRACELET("Emerald bracelet", 30, true, "Gold bar", "Emerald", "Bracelet mould", 65.0),
    EMERALD_AMULET("Emerald amulet (u)", 31, false, "Gold bar", "Emerald", "Amulet mould", 70.0),

    // Ruby
    RUBY_RING("Ruby ring", 34, false, "Gold bar", "Ruby", "Ring mould", 70.0),
    RUBY_NECKLACE("Ruby necklace", 40, false, "Gold bar", "Ruby", "Necklace mould", 75.0),
    RUBY_BRACELET("Ruby bracelet", 42, true, "Gold bar", "Ruby", "Bracelet mould", 80.0),
    RUBY_AMULET("Ruby amulet (u)", 50, false, "Gold bar", "Ruby", "Amulet mould", 85.0),

    // Diamond
    DIAMOND_RING("Diamond ring", 43, false, "Gold bar", "Diamond", "Ring mould", 85.0),
    DIAMOND_NECKLACE("Diamond necklace", 56, false, "Gold bar", "Diamond", "Necklace mould", 90.0),
    DIAMOND_BRACELET("Diamond bracelet", 58, true, "Gold bar", "Diamond", "Bracelet mould", 95.0),
    DIAMOND_AMULET("Diamond amulet (u)", 70, false, "Gold bar", "Diamond", "Amulet mould", 100.0),

    // Members-only silver jewellery
    OPAL_RING("Opal ring", 1, true, "Silver bar", "Opal", "Ring mould", 10.0),
    OPAL_NECKLACE("Opal necklace", 16, true, "Silver bar", "Opal", "Necklace mould", 35.0),
    OPAL_BRACELET("Opal bracelet", 22, true, "Silver bar", "Opal", "Bracelet mould", 45.0),
    OPAL_AMULET("Opal amulet (u)", 27, true, "Silver bar", "Opal", "Amulet mould", 55.0),

    JADE_RING("Jade ring", 13, true, "Silver bar", "Jade", "Ring mould", 32.0),
    JADE_NECKLACE("Jade necklace", 25, true, "Silver bar", "Jade", "Necklace mould", 54.0),
    JADE_BRACELET("Jade bracelet", 29, true, "Silver bar", "Jade", "Bracelet mould", 60.0),
    JADE_AMULET("Jade amulet (u)", 34, true, "Silver bar", "Jade", "Amulet mould", 70.0),

    TOPAZ_RING("Topaz ring", 16, true, "Silver bar", "Red topaz", "Ring mould", 35.0),
    TOPAZ_NECKLACE("Topaz necklace", 32, true, "Silver bar", "Red topaz", "Necklace mould", 70.0),
    TOPAZ_BRACELET("Topaz bracelet", 38, true, "Silver bar", "Red topaz", "Bracelet mould", 75.0),
    TOPAZ_AMULET("Topaz amulet (u)", 45, true, "Silver bar", "Red topaz", "Amulet mould", 80.0),

    // Members-only high-tier gold jewellery
    DRAGONSTONE_RING("Dragonstone ring", 55, true, "Gold bar", "Dragonstone", "Ring mould", 100.0),
    DRAGONSTONE_NECKLACE("Dragon necklace", 72, true, "Gold bar", "Dragonstone", "Necklace mould", 105.0),
    DRAGONSTONE_BRACELET("Dragonstone bracelet", 74, true, "Gold bar", "Dragonstone", "Bracelet mould", 110.0),
    DRAGONSTONE_AMULET("Dragonstone amulet (u)", 80, true, "Gold bar", "Dragonstone", "Amulet mould", 150.0),

    ONYX_RING("Onyx ring", 67, true, "Gold bar", "Onyx", "Ring mould", 115.0),
    ONYX_NECKLACE("Onyx necklace", 82, true, "Gold bar", "Onyx", "Necklace mould", 120.0),
    ONYX_BRACELET("Onyx bracelet", 84, true, "Gold bar", "Onyx", "Bracelet mould", 125.0),
    ONYX_AMULET("Onyx amulet (u)", 90, true, "Gold bar", "Onyx", "Amulet mould", 165.0),

    ZENYTE_RING("Zenyte ring", 89, true, "Gold bar", "Zenyte", "Ring mould", 150.0),
    ZENYTE_NECKLACE("Zenyte necklace", 92, true, "Gold bar", "Zenyte", "Necklace mould", 165.0),
    ZENYTE_BRACELET("Zenyte bracelet", 95, true, "Gold bar", "Zenyte", "Bracelet mould", 180.0),
    ZENYTE_AMULET("Zenyte amulet (u)", 98, true, "Gold bar", "Zenyte", "Amulet mould", 200.0);

    private final String outputName;
    private final int craftingLevel;
    private final boolean membersOnly;
    private final String barName;
    private final String gemName;
    private final String mouldName;
    private final double xp;

    JewelryRecipe(String outputName, int craftingLevel, boolean membersOnly,
                  String barName, String gemName, String mouldName, double xp)
    {
        this.outputName = outputName;
        this.craftingLevel = craftingLevel;
        this.membersOnly = membersOnly;
        this.barName = barName;
        this.gemName = gemName;
        this.mouldName = mouldName;
        this.xp = xp;
    }

    public boolean isEligible(int level, boolean memberAccount)
    {
        return level >= craftingLevel && (!membersOnly || memberAccount);
    }

    public boolean usesGem()
    {
        return gemName != null;
    }

    public String getOutputName() { return outputName; }
    public int getCraftingLevel() { return craftingLevel; }
    public boolean isMembersOnly() { return membersOnly; }
    public String getBarName() { return barName; }
    public String getGemName() { return gemName; }
    public String getMouldName() { return mouldName; }
    public double getXp() { return xp; }

    public static List<JewelryRecipe> eligible(int level, boolean memberAccount)
    {
        return Arrays.stream(values())
            .filter(r -> r.isEligible(level, memberAccount))
            .collect(Collectors.toList());
    }

    @Override
    public String toString()
    {
        return outputName + " (Lvl " + craftingLevel + (membersOnly ? ", P2P" : ", F2P") + ")";
    }
}
