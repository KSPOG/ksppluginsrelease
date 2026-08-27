package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import net.runelite.api.gameval.ItemID;

/**
 * Tradeable OSRS bars supported by Superheat Item.
 *
 * Non-tradeable/special bars are intentionally excluded because this plugin is a
 * profit-driven GE processor. Sailing-era Lead and Cupronickel are included.
 */
public enum SuperheatRecipe
{
    BRONZE(
        "Bronze bar", ItemID.BRONZE_BAR, 1, 6.2, false,
        "Copper ore", ItemID.COPPER_ORE, 1,
        "Tin ore", ItemID.TIN_ORE, 1,
        0
    ),
    IRON(
        "Iron bar", ItemID.IRON_BAR, 15, 12.5, false,
        "Iron ore", ItemID.IRON_ORE, 1,
        null, -1, 0,
        0
    ),
    SILVER(
        "Silver bar", ItemID.SILVER_BAR, 20, 13.7, false,
        "Silver ore", ItemID.SILVER_ORE, 1,
        null, -1, 0,
        0
    ),
    LEAD(
        "Lead bar", ItemID.LEAD_BAR, 25, 15.5, true,
        "Lead ore", ItemID.LEAD_ORE, 2,
        null, -1, 0,
        0
    ),
    STEEL(
        "Steel bar", ItemID.STEEL_BAR, 30, 17.5, false,
        "Iron ore", ItemID.IRON_ORE, 1,
        null, -1, 0,
        2
    ),
    GOLD(
        "Gold bar", ItemID.GOLD_BAR, 40, 22.5, false,
        "Gold ore", ItemID.GOLD_ORE, 1,
        null, -1, 0,
        0
    ),
    MITHRIL(
        "Mithril bar", ItemID.MITHRIL_BAR, 50, 30.0, false,
        "Mithril ore", ItemID.MITHRIL_ORE, 1,
        null, -1, 0,
        4
    ),
    ADAMANTITE(
        "Adamantite bar", ItemID.ADAMANTITE_BAR, 70, 37.5, false,
        "Adamantite ore", ItemID.ADAMANTITE_ORE, 1,
        null, -1, 0,
        6
    ),
    CUPRONICKEL(
        "Cupronickel bar", ItemID.CUPRONICKEL_BAR, 74, 42.0, true,
        "Nickel ore", ItemID.NICKEL_ORE, 1,
        "Copper ore", ItemID.COPPER_ORE, 2,
        0
    ),
    RUNITE(
        "Runite bar", ItemID.RUNITE_BAR, 85, 50.0, false,
        "Runite ore", ItemID.RUNITE_ORE, 1,
        null, -1, 0,
        8
    );

    private final String outputName;
    private final int outputId;
    private final int smithingLevel;
    private final double smithingXp;
    private final boolean membersOnly;
    private final String primaryOreName;
    private final int primaryOreId;
    private final int primaryOrePerBar;
    private final String secondaryOreName;
    private final int secondaryOreId;
    private final int secondaryOrePerBar;
    private final int coalPerBar;

    SuperheatRecipe(
        String outputName,
        int outputId,
        int smithingLevel,
        double smithingXp,
        boolean membersOnly,
        String primaryOreName,
        int primaryOreId,
        int primaryOrePerBar,
        String secondaryOreName,
        int secondaryOreId,
        int secondaryOrePerBar,
        int coalPerBar)
    {
        this.outputName = outputName;
        this.outputId = outputId;
        this.smithingLevel = smithingLevel;
        this.smithingXp = smithingXp;
        this.membersOnly = membersOnly;
        this.primaryOreName = primaryOreName;
        this.primaryOreId = primaryOreId;
        this.primaryOrePerBar = primaryOrePerBar;
        this.secondaryOreName = secondaryOreName;
        this.secondaryOreId = secondaryOreId;
        this.secondaryOrePerBar = secondaryOrePerBar;
        this.coalPerBar = coalPerBar;
    }

    public String getOutputName() { return outputName; }
    public int getOutputId() { return outputId; }
    public int getSmithingLevel() { return smithingLevel; }
    public double getSmithingXp() { return smithingXp; }
    public boolean isMembersOnly() { return membersOnly; }
    public String getPrimaryOreName() { return primaryOreName; }
    public int getPrimaryOreId() { return primaryOreId; }
    public int getPrimaryOrePerBar() { return primaryOrePerBar; }
    public boolean hasSecondaryOre() { return secondaryOreId > 0 && secondaryOrePerBar > 0; }
    public String getSecondaryOreName() { return secondaryOreName; }
    public int getSecondaryOreId() { return secondaryOreId; }
    public int getSecondaryOrePerBar() { return secondaryOrePerBar; }
    public int getCoalPerBar() { return coalPerBar; }

    public int getMaterialSlotsPerBar()
    {
        return primaryOrePerBar + secondaryOrePerBar + coalPerBar;
    }

    @Override
    public String toString()
    {
        return outputName;
    }
}
