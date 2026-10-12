package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.rocklevel.RockLevel;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public enum Areas
{
    TIN_COPPER_VARROCK_EAST(
            "Varrock East Tin & Copper",
            new WorldArea(3281, 3360, 11, 6, 0),
            RockLevel.COPPER, RockLevel.TIN
    ),

    RIMMINGTON_MINE(
            "Rimmington Mine",
            new WorldArea(2966, 3231, 20, 12, 0),
            RockLevel.CLAY, RockLevel.COPPER, RockLevel.TIN, RockLevel.IRON, RockLevel.GOLD
    ),

    LUMBRIDGE_SWAMP_EAST(
            "Lumbridge Swamp East Mine",
            new WorldArea(3219, 3143, 14, 10, 0),
            RockLevel.COPPER, RockLevel.TIN
    ),

    // RuneLite MiningSiteLocation: DWARVEN_WEST_TOP @ 3031,9828
    // 3 copper, 2 tin, 2 iron. This replaces the old duplicate
    // TIN_COPPER_DWARVEN_NORTH / IRON_DWARVEN_WEST entries.
    DWARVEN_WEST_TOP(
            "Dwarven Mine West Top",
            new WorldArea(3028, 9821, 8, 10, 0),
            RockLevel.COPPER, RockLevel.TIN, RockLevel.IRON
    ),

    // RuneLite MiningSiteLocation: DWARVEN_EAST_TOP @ 3051,9820
    // 2 clay, 3 tin, 2 iron.
    DWARVEN_EAST_TOP(
            "Dwarven Mine East Top",
            new WorldArea(3051, 9821, 6, 8, 0),
            RockLevel.CLAY, RockLevel.TIN, RockLevel.IRON
    ),

    // RuneLite MiningSiteLocation: DWARVEN_EAST_BOTTOM @ 3039,9763
    // 5 tin, 2 iron, 8 coal, 2 gold, 1 adamantite.
    // Bounds are the physical bottom site, not a separate rectangle per ore.
    DWARVEN_EAST_BOTTOM(
            "Dwarven Mine East Bottom",
            new WorldArea(3034, 9759, 22, 11, 0),
            RockLevel.TIN, RockLevel.IRON, RockLevel.COAL, RockLevel.GOLD, RockLevel.ADAMANT
    ),

    // RuneLite MiningSiteLocation: DWARVEN_EAST_MIDDLE @ 3037,9775
    // 4 copper, 3 iron, 3 coal, 2 mithril, 2 adamantite.
    DWARVEN_EAST_MIDDLE(
            "Dwarven Mine East Middle",
            new WorldArea(3033, 9770, 21, 8, 0),
            RockLevel.COPPER, RockLevel.IRON, RockLevel.COAL, RockLevel.MITHRIL, RockLevel.ADAMANT
    ),

    // Separate coal cluster supplied from the live game. It is not merged into
    // the East Bottom/Middle sites because it is spatially distinct.
    DWARVEN_COAL_NORTH(
            "Dwarven Mine North Coal Cluster",
            new WorldArea(3036, 9797, 5, 4, 0),
            RockLevel.COAL
    ),

    IRON_VARROCK_EAST(
            "Varrock East Iron",
            new WorldArea(3284, 3367, 6, 4, 0),
            RockLevel.IRON
    ),

    IRON_VARROCK_WEST(
            "Varrock West Iron",
            new WorldArea(3171, 3364, 13, 12, 0),
            RockLevel.IRON
    ),

    CLAY_VARROCK_WEST(
            "Varrock West Clay",
            new WorldArea(3179, 3370, 6, 8, 0),
            RockLevel.CLAY
    ),

    SILVER_VARROCK_WEST(
            "Varrock West Silver",
            new WorldArea(3171, 3363, 10, 8, 0),
            RockLevel.SILVER
    ),

    // RuneLite MiningSiteLocation: BARBARIAN_VILLAGE @ 3078,3421
    // 5 tin, 4 coal. Keep this coal target narrow around the actual site.
    COAL_BARBARIAN_VILLAGE(
            "Barbarian Village Coal",
            new WorldArea(3076, 3418, 10, 7, 0),
            RockLevel.COAL
    ),

    // RuneLite MiningSiteLocation: LUMBRIDGE_SWAMP_WEST @ 3148,3149
    // 7 coal, 5 mithril, 2 adamantite. The old implementation created separate
    // overlapping Coal and Mithril areas for the same physical mining site.
    LUMBRIDGE_SWAMP_WEST(
            "Lumbridge Swamp West Mine",
            new WorldArea(3141, 3143, 10, 11, 0),
            RockLevel.COAL, RockLevel.MITHRIL, RockLevel.ADAMANT
    ),

    PORT_SARIM_DEPOSIT_BOX(
            "Port Sarim Deposit Box",
            new WorldArea(3044, 3234, 9, 4, 0)
    );

    private final String displayName;
    private final WorldArea worldArea;
    private final EnumSet<RockLevel> supportedRocks;

    Areas(String displayName, WorldArea worldArea, RockLevel... supportedRocks)
    {
        this.displayName = displayName;
        this.worldArea = worldArea;
        this.supportedRocks = supportedRocks.length == 0
                ? EnumSet.noneOf(RockLevel.class)
                : EnumSet.copyOf(Arrays.asList(supportedRocks));
    }

    public static Areas randomTinCopperArea()
    {
        Areas[] matching = Arrays.stream(values())
                .filter(area -> area.supportsRock(RockLevel.TIN) && area.supportsRock(RockLevel.COPPER))
                .toArray(Areas[]::new);
        return randomFrom(matching);
    }

    public static Areas randomIronArea()
    {
        return randomAreaForRock(RockLevel.IRON);
    }

    public static Areas randomCoalArea()
    {
        return randomAreaForRock(RockLevel.COAL);
    }

    public static Areas randomGoldArea()
    {
        return randomAreaForRock(RockLevel.GOLD);
    }

    public static Areas randomMithrilArea()
    {
        return randomAreaForRock(RockLevel.MITHRIL);
    }

    public static Areas randomAdamantArea()
    {
        return randomAreaForRock(RockLevel.ADAMANT);
    }

    private static Areas randomAreaForRock(RockLevel rock)
    {
        Areas[] matching = Arrays.stream(values())
                .filter(area -> area.supportsRock(rock))
                .toArray(Areas[]::new);
        return randomFrom(matching);
    }

    private static Areas randomFrom(Areas[] matching)
    {
        if (matching.length == 0)
        {
            throw new IllegalStateException("No mining areas matched the requested rock");
        }
        return matching[ThreadLocalRandom.current().nextInt(matching.length)];
    }

    public boolean supportsRock(RockLevel rock)
    {
        return rock != null && supportedRocks.contains(rock);
    }

    public boolean isTinCopperArea()
    {
        return supportsRock(RockLevel.TIN) && supportsRock(RockLevel.COPPER);
    }

    public boolean isIronArea()
    {
        return supportsRock(RockLevel.IRON);
    }

    public boolean isCoalArea()
    {
        return supportsRock(RockLevel.COAL);
    }

    public boolean isGoldArea()
    {
        return supportsRock(RockLevel.GOLD);
    }

    public boolean isMithrilArea()
    {
        return supportsRock(RockLevel.MITHRIL);
    }

    public boolean isAdamantArea()
    {
        return supportsRock(RockLevel.ADAMANT);
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
