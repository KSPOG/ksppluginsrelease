package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.areas;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public final class Area
{
    private final WorldPoint southWest;
    private final WorldPoint northEast;

    public Area(int firstX, int firstY, int secondX, int secondY)
    {
        int minX = Math.min(firstX, secondX);
        int minY = Math.min(firstY, secondY);
        int maxX = Math.max(firstX, secondX);
        int maxY = Math.max(firstY, secondY);

        southWest = new WorldPoint(minX, minY, 0);
        northEast = new WorldPoint(maxX, maxY, 0);
    }

    public WorldArea toWorldArea()
    {
        return new WorldArea(
                southWest.getX(),
                southWest.getY(),
                northEast.getX() - southWest.getX() + 1,
                northEast.getY() - southWest.getY() + 1,
                southWest.getPlane());
    }

    public WorldPoint getRandomPoint()
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new WorldPoint(
                random.nextInt(southWest.getX(), northEast.getX() + 1),
                random.nextInt(southWest.getY(), northEast.getY() + 1),
                southWest.getPlane());
    }

    public boolean contains(WorldPoint point)
    {
        return point != null && toWorldArea().contains(point);
    }
}
