package net.runelite.client.plugins.microbot.KSPGELooter;

import net.runelite.api.coords.WorldPoint;

import java.awt.Polygon;
import java.util.HashSet;
import java.util.Set;

/**
 * Hard area guard for KSP GE Looter.
 *
 * The points below are the user's supplied perimeter, translated from Position
 * to RuneLite WorldPoint. All looting targets must be inside this polygon.
 */
public final class KSPGELooterArea
{
    private static final int PLANE = 0;

    public static final WorldPoint[] PATH = {
            new WorldPoint(3159, 3506, 0),
            new WorldPoint(3170, 3506, 0),
            new WorldPoint(3171, 3505, 0),
            new WorldPoint(3172, 3505, 0),
            new WorldPoint(3173, 3504, 0),
            new WorldPoint(3174, 3504, 0),
            new WorldPoint(3174, 3503, 0),
            new WorldPoint(3176, 3503, 0),
            new WorldPoint(3177, 3502, 0),
            new WorldPoint(3178, 3501, 0),
            new WorldPoint(3178, 3500, 0),
            new WorldPoint(3178, 3499, 0),
            new WorldPoint(3179, 3499, 0),
            new WorldPoint(3179, 3498, 0),
            new WorldPoint(3180, 3497, 0),
            new WorldPoint(3180, 3496, 0),
            new WorldPoint(3181, 3495, 0),
            new WorldPoint(3181, 3484, 0),
            new WorldPoint(3180, 3483, 0),
            new WorldPoint(3180, 3482, 0),
            new WorldPoint(3179, 3481, 0),
            new WorldPoint(3179, 3480, 0),
            new WorldPoint(3178, 3480, 0),
            new WorldPoint(3178, 3478, 0),
            new WorldPoint(3177, 3477, 0),
            new WorldPoint(3176, 3476, 0),
            new WorldPoint(3175, 3476, 0),
            new WorldPoint(3174, 3475, 0),
            new WorldPoint(3173, 3475, 0),
            new WorldPoint(3172, 3474, 0),
            new WorldPoint(3171, 3474, 0),
            new WorldPoint(3170, 3474, 0),
            new WorldPoint(3170, 3473, 0),
            new WorldPoint(3160, 3473, 0),
            new WorldPoint(3159, 3473, 0),
            new WorldPoint(3158, 3474, 0),
            new WorldPoint(3157, 3474, 0),
            new WorldPoint(3156, 3475, 0),
            new WorldPoint(3155, 3475, 0),
            new WorldPoint(3155, 3476, 0),
            new WorldPoint(3153, 3476, 0),
            new WorldPoint(3152, 3477, 0),
            new WorldPoint(3151, 3478, 0),
            new WorldPoint(3151, 3479, 0),
            new WorldPoint(3150, 3480, 0),
            new WorldPoint(3150, 3482, 0),
            new WorldPoint(3149, 3482, 0),
            new WorldPoint(3149, 3484, 0),
            new WorldPoint(3148, 3484, 0),
            new WorldPoint(3148, 3495, 0),
            new WorldPoint(3149, 3496, 0),
            new WorldPoint(3149, 3497, 0),
            new WorldPoint(3150, 3498, 0),
            new WorldPoint(3150, 3499, 0),
            new WorldPoint(3151, 3500, 0),
            new WorldPoint(3151, 3501, 0),
            new WorldPoint(3152, 3502, 0),
            new WorldPoint(3153, 3503, 0),
            new WorldPoint(3154, 3503, 0),
            new WorldPoint(3155, 3504, 0),
            new WorldPoint(3156, 3504, 0),
            new WorldPoint(3157, 3505, 0),
            new WorldPoint(3158, 3505, 0),
            new WorldPoint(3159, 3506, 0)
    };

    private static final Polygon POLYGON = new Polygon();
    private static final Set<Long> BOUNDARY_TILES = new HashSet<>();

    static
    {
        for (WorldPoint point : PATH)
        {
            POLYGON.addPoint(point.getX(), point.getY());
            BOUNDARY_TILES.add(tileKey(point.getX(), point.getY()));
        }
    }

    private KSPGELooterArea()
    {
    }

    public static boolean contains(WorldPoint point)
    {
        if (point == null || point.getPlane() != PLANE)
        {
            return false;
        }

        // Explicitly include every supplied boundary tile. Polygon.contains()
        // can otherwise treat points exactly on an edge as outside.
        if (BOUNDARY_TILES.contains(tileKey(point.getX(), point.getY())))
        {
            return true;
        }

        return POLYGON.contains(point.getX() + 0.5D, point.getY() + 0.5D);
    }

    private static long tileKey(int x, int y)
    {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
