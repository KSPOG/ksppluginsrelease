package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeoareas;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public final class Area
{
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private int plane;

    public Area(int firstX, int firstY, int secondX, int secondY)
    {
        minX = Math.min(firstX, secondX);
        minY = Math.min(firstY, secondY);
        maxX = Math.max(firstX, secondX);
        maxY = Math.max(firstY, secondY);
    }

    public Area setPlane(int plane)
    {
        this.plane = plane;
        return this;
    }

    public WorldArea toWorldArea()
    {
        return new WorldArea(
                minX,
                minY,
                maxX - minX + 1,
                maxY - minY + 1,
                plane);
    }

    public WorldPoint getRandomPoint()
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new WorldPoint(
                random.nextInt(minX, maxX + 1),
                random.nextInt(minY, maxY + 1),
                plane);
    }

    public boolean contains(WorldPoint point)
    {
        return point != null && toWorldArea().contains(point);
    }
}
