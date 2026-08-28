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
}
