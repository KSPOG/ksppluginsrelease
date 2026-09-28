package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.areas;

public enum Areas
{
    EDGEVILLE_RANGE("Edgeville cooking range", new Area(3081, 3496, 3077, 3489)),
    LUMBRIDGE_KITCHEN("Lumbridge Kitchen", new Area(3212, 3212, 3206, 3216));

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
