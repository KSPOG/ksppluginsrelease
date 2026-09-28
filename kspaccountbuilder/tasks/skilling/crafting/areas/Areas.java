package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.areas;

public enum Areas
{
    EDGE_BANK("Edgeville bank", new Area(3098, 3499, 3090, 3488)),
    EDGE_FURNACE("Edgeville furnace", new Area(3105, 3501, 3109, 3496));

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
