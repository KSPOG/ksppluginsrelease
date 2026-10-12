package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public enum Areas
{
    TIN_COPPER_VARROCK_EAST(
            "Varrock East Tin & Copper",
            new WorldArea(3281, 3360, 11, 6, 0),
            AreaType.TIN_COPPER
    ),
    TIN_COPPER_RIMMINGTON(
            "Rimmington Tin & Copper",
            new WorldArea(2963, 3226, 31, 27, 0),
            AreaType.TIN_COPPER
    ),
    TIN_COPPER_LUMBRIDGE_SWAMP(
            "Lumbridge Swamp Tin & Copper",
            new WorldArea(3219, 3143, 14, 10, 0),
            AreaType.TIN_COPPER
    ),
    TIN_COPPER_DWARVEN_NORTH(
            "Dwarven Mine North Tin & Copper",
            new WorldArea(3028, 9821, 8, 10, 0),
            AreaType.TIN_COPPER
    ),
    TIN_COPPER_DWARVEN_SOUTH(
            "Dwarven Mine South Tin & Copper",
            new WorldArea(3035, 9777, 23, 9, 0),
            AreaType.TIN_COPPER
    ),

    IRON_VARROCK_EAST(
            "Varrock East Iron",
            new WorldArea(3284, 3367, 6, 4, 0),
            AreaType.IRON
    ),
    IRON_DWARVEN_WEST(
            "Dwarven Mine West Iron",
            new WorldArea(3028, 9821, 8, 9, 0),
            AreaType.IRON
    ),
    IRON_DWARVEN_EAST(
            "Dwarven Mine East Iron",
            new WorldArea(3051, 9821, 6, 8, 0),
            AreaType.IRON
    ),
    IRON_DWARVEN_SOUTH(
            "Dwarven Mine South Iron",
            new WorldArea(3032, 9764, 17, 15, 0),
            AreaType.IRON
    ),
    IRON_RIMMINGTON(
            "Rimmington Iron",
            new WorldArea(2966, 3231, 20, 12, 0),
            AreaType.IRON
    ),
    IRON_VARROCK_WEST(
            "Varrock West Iron",
            new WorldArea(3171, 3364, 13, 12, 0),
            AreaType.IRON
    ),

    CLAY_VARROCK_WEST(
            "Varrock West Clay",
            new WorldArea(3179, 3370, 6, 8, 0),
            AreaType.OTHER
    ),
    SILVER_VARROCK_WEST(
            "Varrock West Silver",
            new WorldArea(3171, 3363, 10, 8, 0),
            AreaType.OTHER
    ),
    COAL_BARBARIAN_VILLAGE(
            "Barbarian Village Coal",
            new WorldArea(3079, 3418, 7, 5, 0),
            AreaType.OTHER
    ),
    GOLD_RIMMINGTON(
            "Rimmington Gold Mine",
            new WorldArea(2973, 3231, 7, 5, 0),
            AreaType.OTHER
    ),
    PORT_SARIM_DEPOSIT_BOX(
            "Port Sarim Deposit Box",
            new WorldArea(3044, 3234, 9, 4, 0),
            AreaType.OTHER
    );

    public enum AreaType
    {
        TIN_COPPER,
        IRON,
        OTHER
    }

    private final String displayName;
    private final WorldArea worldArea;
    private final AreaType areaType;

    Areas(String displayName, WorldArea worldArea, AreaType areaType)
    {
        this.displayName = displayName;
        this.worldArea = worldArea;
        this.areaType = areaType;
    }

    public static Areas randomTinCopperArea()
    {
        return randomArea(AreaType.TIN_COPPER);
    }

    public static Areas randomIronArea()
    {
        return randomArea(AreaType.IRON);
    }

    private static Areas randomArea(AreaType type)
    {
        Areas[] matching = Arrays.stream(values())
                .filter(area -> area.areaType == type)
                .toArray(Areas[]::new);
        return matching[ThreadLocalRandom.current().nextInt(matching.length)];
    }

    public boolean isTinCopperArea()
    {
        return areaType == AreaType.TIN_COPPER;
    }

    public boolean isIronArea()
    {
        return areaType == AreaType.IRON;
    }

    public String getDisplayName() { return displayName; }

    public WorldPoint getSouthWest()
    {
        return new WorldPoint(worldArea.getX(), worldArea.getY(), worldArea.getPlane());
    }

    public WorldPoint getNorthEast()
    {
        return new WorldPoint(
                worldArea.getX() + worldArea.getWidth() - 1,
                worldArea.getY() + worldArea.getHeight() - 1,
                worldArea.getPlane());
    }

    public WorldArea toWorldArea()
    {
        return worldArea;
    }

    public WorldPoint getRandomPoint()
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(worldArea.getX(), worldArea.getX() + worldArea.getWidth());
        int y = random.nextInt(worldArea.getY(), worldArea.getY() + worldArea.getHeight());
        return new WorldPoint(x, y, worldArea.getPlane());
    }
}
