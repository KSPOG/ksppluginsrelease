package net.runelite.client.plugins.microbot.KSPGELooter;

import net.runelite.api.coords.WorldPoint;

/** Exact hard guard for Area(3148, 3506, 3182, 3473). */
public final class KSPGELooterArea
{
    private static final int MIN_X = 3148, MAX_X = 3182, MIN_Y = 3473, MAX_Y = 3506, PLANE = 0;

    private KSPGELooterArea() {}

    public static boolean contains(WorldPoint point)
    {
        return point != null && point.getPlane() == PLANE
                && point.getX() >= MIN_X && point.getX() <= MAX_X
                && point.getY() >= MIN_Y && point.getY() <= MAX_Y;
    }

    /**
     * Nearest tile inside the configured GE looting rectangle.
     * Used to return the looter to its own defined area after banking/walking.
     */
    public static WorldPoint nearestPointInside(WorldPoint point)
    {
        if (point == null || point.getPlane() != PLANE)
        {
            return returnPoint();
        }

        int x = Math.max(MIN_X, Math.min(MAX_X, point.getX()));
        int y = Math.max(MIN_Y, Math.min(MAX_Y, point.getY()));
        return new WorldPoint(x, y, PLANE);
    }

    /** Stable interior destination used after banking/outbound movement. */
    public static WorldPoint returnPoint()
    {
        return new WorldPoint((MIN_X + MAX_X) / 2, (MIN_Y + MAX_Y) / 2, PLANE);
    }
}
