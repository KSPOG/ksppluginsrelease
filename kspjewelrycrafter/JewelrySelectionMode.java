package net.runelite.client.plugins.microbot.kspjewelrycrafter;

public enum JewelrySelectionMode
{
    BEST_PROFIT("Best profit / item"),
    BEST_ROI("Best ROI"),
    HIGHEST_LEVEL_PROFITABLE("Highest level profitable"),
    FIXED_RECIPE("Fixed recipe");

    private final String label;

    JewelrySelectionMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
