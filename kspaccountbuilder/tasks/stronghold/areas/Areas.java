package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.areas;

public enum Areas
{
    GIFT_OF_PEACE("Gift of Peace", new Area(1901, 5225, 1915, 5218)),
    GRAIN_OF_PLENTY("Grain of Plenty", new Area(2016, 5220, 2027, 5208)),
    BOX_OF_HEALTH("Box of Health", new Area(2140, 5286, 2151, 5275));

    private final String displayName;
    private final Area area;

    Areas(String displayName, Area area)
    {
        this.displayName = displayName;
        this.area = area;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public Area getArea()
    {
        return area;
    }
}
