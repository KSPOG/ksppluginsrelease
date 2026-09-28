package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeoareas;

public enum Areas
{
    JULIET("Juliet", new Area(3161, 3426, 3155, 3424).setPlane(1)),
    HALLWAY("Hallway", new Area(3157, 3436, 3164, 3432)),
    CADAVA_BUSH("Cadava bush", new Area(3276, 3375, 3279, 3373));

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
