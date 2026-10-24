package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public enum SmeltArea
{
    SMELT_AREA_EDGEVILLE_FURNACE("Edgeville Furnace", new WorldArea(3105, 3496, 6, 6, 0)),
    SMELT_AREA_AL_KHARID("Al Kharid Furnace", new WorldArea(3272, 3184, 8, 5, 0)),
    SMELT_AREA_LUMBRIDGE("Lumbridge Furnace", new WorldArea(3222, 3252, 7, 6, 0)),
    SMELT_AREA_FALADOR("Falador Furnace", new WorldArea(2970, 3368, 8, 6, 0));

    private final String displayName;
    private final WorldArea worldArea;

    SmeltArea(String displayName, WorldArea worldArea)
    {
        this.displayName = displayName;
        this.worldArea = worldArea;
    }

    public static SmeltArea randomArea()
    {
        SmeltArea[] areas = values();
        return areas[ThreadLocalRandom.current().nextInt(areas.length)];
    }

    /**
     * Select the furnace with the shortest approximate initial walking distance.
     * Preserve the existing randomArea() API for callers that require randomness.
     * This is a coordinate-distance heuristic, not a WebWalker route estimate.
     */
    public static SmeltArea nearestArea(WorldPoint playerLocation)
    {
        if (playerLocation == null)
        {
            return SMELT_AREA_EDGEVILLE_FURNACE;
        }

        SmeltArea nearest = SMELT_AREA_EDGEVILLE_FURNACE;
        long shortestDistance = Long.MAX_VALUE;
        for (SmeltArea candidate : values())
        {
            WorldArea bounds = candidate.worldArea;
            long centerX = (long) bounds.getX() + bounds.getWidth() / 2;
            long centerY = (long) bounds.getY() + bounds.getHeight() / 2;
            long distance = Math.abs((long) playerLocation.getX() - centerX)
                    + Math.abs((long) playerLocation.getY() - centerY);
            if (distance < shortestDistance)
            {
                shortestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
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

    public String getDisplayName()
    {
        return displayName;
    }

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
}
