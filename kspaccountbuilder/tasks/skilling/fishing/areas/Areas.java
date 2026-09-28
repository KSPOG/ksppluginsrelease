package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.concurrent.ThreadLocalRandom;

public enum Areas
{
    SHRIMP_ANCHOVIES(
            "Shrimp & Anchovies",
            new WorldPoint(3233, 3164, 0),
            new WorldPoint(3250, 3137, 0)
    ),
    SARDINE_HERRING(
            "Sardine & Herring",
            new WorldPoint(3233, 3164, 0),
            new WorldPoint(3250, 3137, 0)
    ),
    TROUT_SALMON(
            "Trout & Salmon",
            new WorldPoint(3103, 3420, 0),
            new WorldPoint(3113, 3436, 0)
    ),
    KARAMJA(
            "Karamja",
            new WorldPoint(2920, 3184, 0),
            new WorldPoint(2930, 3174, 0)
    );

    private final String displayName;
    private final WorldPoint southWest;
    private final WorldPoint northEast;

    Areas(String displayName, WorldPoint firstCorner, WorldPoint secondCorner)
    {
        this.displayName = displayName;

        int minX = Math.min(firstCorner.getX(), secondCorner.getX());
        int minY = Math.min(firstCorner.getY(), secondCorner.getY());
        int maxX = Math.max(firstCorner.getX(), secondCorner.getX());
        int maxY = Math.max(firstCorner.getY(), secondCorner.getY());

        this.southWest = new WorldPoint(minX, minY, firstCorner.getPlane());
        this.northEast = new WorldPoint(maxX, maxY, firstCorner.getPlane());
    }

    public WorldArea toWorldArea()
    {
        int width = (northEast.getX() - southWest.getX()) + 1;
        int height = (northEast.getY() - southWest.getY()) + 1;
        return new WorldArea(southWest.getX(), southWest.getY(), width, height, southWest.getPlane());
    }

    public WorldPoint getRandomPoint()
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(southWest.getX(), northEast.getX() + 1);
        int y = random.nextInt(southWest.getY(), northEast.getY() + 1);
        return new WorldPoint(x, y, southWest.getPlane());
    }

    public boolean contains(WorldPoint point)
    {
        return point != null && toWorldArea().contains(point);
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public WorldPoint getSouthWest()
    {
        return southWest;
    }

    public WorldPoint getNorthEast()
    {
        return northEast;
    }
}
