package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.rocklevel.RockLevel;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public enum Areas
{
    TIN_COPPER_VARROCK_EAST("Varrock East Tin & Copper", new WorldArea(3281, 3360, 11, 6, 0), RockLevel.COPPER, RockLevel.TIN),

    TIN_COPPER_RIMMINGTON("Rimmington Tin & Copper", new WorldArea(2963, 3226, 31, 27, 0), RockLevel.COPPER, RockLevel.TIN),
    IRON_RIMMINGTON("Rimmington Iron", new WorldArea(2966, 3231, 20, 12, 0), RockLevel.IRON),
    GOLD_RIMMINGTON("Rimmington Gold", new WorldArea(2973, 3231, 7, 5, 0), RockLevel.GOLD),

    LUMBRIDGE_SWAMP_EAST("Lumbridge Swamp East Tin & Copper", new WorldArea(3219, 3143, 14, 10, 0), RockLevel.COPPER, RockLevel.TIN),

    TIN_COPPER_DWARVEN_NORTH("Dwarven Mine North Tin & Copper", new WorldArea(3028, 9821, 8, 10, 0), RockLevel.COPPER, RockLevel.TIN),
    TIN_COPPER_DWARVEN_SOUTH("Dwarven Mine South Tin & Copper", new WorldArea(3035, 9777, 23, 9, 0), RockLevel.COPPER, RockLevel.TIN),

    IRON_VARROCK_EAST("Varrock East Iron", new WorldArea(3284, 3367, 6, 4, 0), RockLevel.IRON),
    IRON_VARROCK_WEST("Varrock West Iron", new WorldArea(3171, 3364, 13, 12, 0), RockLevel.IRON),
    IRON_DWARVEN_WEST("Dwarven Mine West Iron", new WorldArea(3028, 9821, 8, 9, 0), RockLevel.IRON),
    IRON_DWARVEN_EAST("Dwarven Mine East Iron", new WorldArea(3051, 9821, 6, 8, 0), RockLevel.IRON),
    IRON_DWARVEN_SOUTH("Dwarven Mine South Iron", new WorldArea(3032, 9764, 17, 15, 0), RockLevel.IRON),

    CLAY_VARROCK_WEST("Varrock West Clay", new WorldArea(3179, 3370, 6, 8, 0), RockLevel.CLAY),
    SILVER_VARROCK_WEST("Varrock West Silver", new WorldArea(3171, 3363, 10, 8, 0), RockLevel.SILVER),

    COAL_BARBARIAN_VILLAGE("Barbarian Village Coal", new WorldArea(3076, 3418, 10, 7, 0), RockLevel.COAL),
    COAL_DWARVEN_SOUTH("Dwarven Mine South Coal", new WorldArea(3035, 9759, 21, 8, 0), RockLevel.COAL),
    COAL_DWARVEN_CENTRAL("Dwarven Mine Central Coal", new WorldArea(3047, 9774, 7, 4, 0), RockLevel.COAL),
    COAL_DWARVEN_NORTH("Dwarven Mine North Coal", new WorldArea(3036, 9797, 5, 4, 0), RockLevel.COAL),
    COAL_LUMBRIDGE_SWAMP("Lumbridge Swamp Coal", new WorldArea(3143, 3147, 6, 7, 0), RockLevel.COAL),

    GOLD_DWARVEN_MINE("Dwarven Mine Gold", new WorldArea(3048, 9759, 4, 2, 0), RockLevel.GOLD),

    MITHRIL_DWARVEN_MINE("Dwarven Mine Mithril", new WorldArea(3033, 9770, 7, 4, 0), RockLevel.MITHRIL),
    MITHRIL_LUMBRIDGE_SWAMP("Lumbridge Swamp Mithril", new WorldArea(3141, 3143, 10, 5, 0), RockLevel.MITHRIL),

    ADAMANT_DWARVEN_MINE("Dwarven Mine Adamantite", new WorldArea(3034, 9763, 10, 13, 0), RockLevel.ADAMANT),

    PORT_SARIM_DEPOSIT_BOX("Port Sarim Deposit Box", new WorldArea(3044, 3234, 9, 4, 0));

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
        return randomSurfaceAreaForRocks(RockLevel.TIN, RockLevel.COPPER);
    }

    public static Areas randomIronArea() { return randomSurfaceAreaForRock(RockLevel.IRON); }
    public static Areas randomCoalArea() { return randomSurfaceAreaForRock(RockLevel.COAL); }
    public static Areas randomGoldArea() { return randomSurfaceAreaForRock(RockLevel.GOLD); }
    public static Areas randomMithrilArea() { return randomSurfaceAreaForRock(RockLevel.MITHRIL); }
    public static Areas randomAdamantArea() { return randomAreaForRock(RockLevel.ADAMANT); }

    private static Areas randomSurfaceAreaForRocks(RockLevel... rocks)
    {
        Areas[] matching = Arrays.stream(values())
                .filter(area -> !area.isUnderground())
                .filter(area -> Arrays.stream(rocks).allMatch(area::supportsRock))
                .toArray(Areas[]::new);

        if (matching.length > 0)
        {
            return randomFrom(matching);
        }

        Areas[] fallback = Arrays.stream(values())
                .filter(area -> Arrays.stream(rocks).allMatch(area::supportsRock))
                .toArray(Areas[]::new);
        return randomFrom(fallback);
    }

    private static Areas randomSurfaceAreaForRock(RockLevel rock)
    {
        Areas[] matching = Arrays.stream(values())
                .filter(area -> !area.isUnderground())
                .filter(area -> area.supportsRock(rock))
                .toArray(Areas[]::new);

        if (matching.length > 0)
        {
            return randomFrom(matching);
        }

        return randomAreaForRock(rock);
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

    public boolean supportsRock(RockLevel rock) { return rock != null && supportedRocks.contains(rock); }
    public boolean isTinCopperArea() { return supportsRock(RockLevel.TIN) && supportsRock(RockLevel.COPPER); }
    public boolean isIronArea() { return supportsRock(RockLevel.IRON); }
    public boolean isCoalArea() { return supportsRock(RockLevel.COAL); }
    public boolean isGoldArea() { return supportsRock(RockLevel.GOLD); }
    public boolean isMithrilArea() { return supportsRock(RockLevel.MITHRIL); }
    public boolean isAdamantArea() { return supportsRock(RockLevel.ADAMANT); }

    /**
     * Underground OSRS map regions use the 9xxx Y-coordinate range. The generic
     * account-builder walker cannot directly path from a surface tile into those
     * regions without an explicit entrance/transport step, so progressive random
     * area selection must prefer surface alternatives when they exist.
     */
    public boolean isUnderground()
    {
        return worldArea.getY() >= 9_000;
    }

    public boolean isRimmingtonArea()
    {
        return this == TIN_COPPER_RIMMINGTON || this == IRON_RIMMINGTON || this == GOLD_RIMMINGTON;
    }

    public String getDisplayName() { return displayName; }
    public WorldArea toWorldArea() { return worldArea; }

    public WorldPoint getSouthWest()
    {
        return new WorldPoint(worldArea.getX(), worldArea.getY(), worldArea.getPlane());
    }

    public WorldPoint getNorthEast()
    {
        return new WorldPoint(worldArea.getX() + worldArea.getWidth() - 1,
                worldArea.getY() + worldArea.getHeight() - 1, worldArea.getPlane());
    }

    public WorldPoint getRandomPoint()
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(worldArea.getX(), worldArea.getX() + worldArea.getWidth());
        int y = random.nextInt(worldArea.getY(), worldArea.getY() + worldArea.getHeight());
        return new WorldPoint(x, y, worldArea.getPlane());
    }
}