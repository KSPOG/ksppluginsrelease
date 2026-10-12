package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.areas;

import java.util.concurrent.ThreadLocalRandom;

public enum Areas
{
    EDGE_BANK("Edgeville bank", new Area(3098, 3499, 3090, 3488), AreaType.LEATHER),
    FALADOR_WEST_LEATHER("Falador west leather", new Area(2944, 3366, 2949, 3369), AreaType.LEATHER),
    FALADOR_EAST_LEATHER("Falador east leather", new Area(3009, 3353, 3018, 3358), AreaType.LEATHER),
    VARROCK_WEST_LEATHER("Varrock west leather", new Area(3180, 3433, 3185, 3446), AreaType.LEATHER),
    VARROCK_NORTHWEST_LEATHER("Varrock northwest leather", new Area(3161, 3486, 3168, 3493), AreaType.LEATHER),
    VARROCK_EAST_LEATHER("Varrock east leather", new Area(3250, 3419, 3257, 3423), AreaType.LEATHER),
    AL_KHARID_LEATHER("Al Kharid leather", new Area(3269, 3161, 3272, 3173), AreaType.LEATHER),

    EDGE_FURNACE("Edgeville furnace", new Area(3105, 3501, 3109, 3496), AreaType.FURNACE),
    AL_KHARID_FURNACE("Al Kharid furnace", new Area(3272, 3184, 3279, 3188), AreaType.FURNACE),
    LUMBRIDGE_FURNACE("Lumbridge furnace", new Area(3222, 3252, 3228, 3257), AreaType.FURNACE),
    FALADOR_FURNACE("Falador furnace", new Area(2970, 3368, 2977, 3373), AreaType.FURNACE);

    public enum AreaType
    {
        LEATHER,
        FURNACE
    }

    private final String displayName;
    private final Area area;
    private final AreaType type;

    Areas(String displayName, Area area, AreaType type)
    {
        this.displayName = displayName;
        this.area = area;
        this.type = type;
    }

    public static Areas randomLeatherArea()
    {
        return randomArea(AreaType.LEATHER);
    }

    public static Areas randomFurnaceArea()
    {
        return randomArea(AreaType.FURNACE);
    }

    private static Areas randomArea(AreaType type)
    {
        Areas[] matching = java.util.Arrays.stream(values())
                .filter(area -> area.type == type)
                .toArray(Areas[]::new);
        return matching[ThreadLocalRandom.current().nextInt(matching.length)];
    }

    public String getDisplayName() { return displayName; }

    public Area getArea() { return area; }

    public AreaType getType() { return type; }
}
