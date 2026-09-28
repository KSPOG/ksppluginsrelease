/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.runelite.api.coords.WorldArea
 *  net.runelite.api.coords.WorldPoint
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public enum TreeAreas {
    REGULAR_TREE_VARROCK_WEST("Regular Tree (Varrock West)", new WorldPoint[]{
            new WorldPoint(3151, 3465, 0),
            new WorldPoint(3172, 3455, 0),
            new WorldPoint(3172, 3449, 0),
            new WorldPoint(3158, 3449, 0),
            new WorldPoint(3152, 3446, 0),
            new WorldPoint(3149, 3464, 0),
            new WorldPoint(3151, 3465, 0)
    }),
    OAK_TREE_DRAYNOR("Oak Tree (Draynor)", new WorldPoint(3109, 3250, 0), new WorldPoint(3098, 3237, 0)),
    VCASTLE_OAKS("VCastleOaks", new WorldPoint(3187, 3464, 0), new WorldPoint(3195, 3456, 0)),
    VWEST_OAKS("VWestOaks", new WorldPoint(3160, 3421, 0), new WorldPoint(3172, 3411, 0)),
    VEAST_OAKS("VEastOaks", new WorldPoint(3274, 3438, 0), new WorldPoint(3284, 3414, 0)),
    WILLOW_TREES_DRAYNOR("Willows (Draynor)", new WorldPoint(3081, 3226, 0), new WorldPoint(3091, 3238, 0)),
    YEW_TREE_VARROCK_PALACE("Yew Tree (Varrock Palace)", new WorldPoint(3085, 3468, 0), new WorldPoint(3088, 3482, 0));

    private final String displayName;
    private final WorldPoint southWest;
    private final WorldPoint northEast;
    private final WorldPoint[] polygon;

    private TreeAreas(String displayName, WorldPoint firstCorner, WorldPoint secondCorner) {
        this(displayName, null, firstCorner, secondCorner);
    }

    private TreeAreas(String displayName, WorldPoint[] polygon) {
        this(displayName, polygon, getSouthWestCorner(polygon), getNorthEastCorner(polygon));
    }

    private TreeAreas(String displayName, WorldPoint[] polygon, WorldPoint firstCorner, WorldPoint secondCorner) {
        this.displayName = displayName;
        this.polygon = polygon;
        int minX = Math.min(firstCorner.getX(), secondCorner.getX());
        int minY = Math.min(firstCorner.getY(), secondCorner.getY());
        int maxX = Math.max(firstCorner.getX(), secondCorner.getX());
        int maxY = Math.max(firstCorner.getY(), secondCorner.getY());
        this.southWest = new WorldPoint(minX, minY, firstCorner.getPlane());
        this.northEast = new WorldPoint(maxX, maxY, firstCorner.getPlane());
    }

    public WorldArea toWorldArea() {
        int width = this.northEast.getX() - this.southWest.getX() + 1;
        int height = this.northEast.getY() - this.southWest.getY() + 1;
        return new WorldArea(this.southWest.getX(), this.southWest.getY(), width, height, this.southWest.getPlane());
    }

    public WorldPoint getRandomPoint() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < 50; attempt++) {
            WorldPoint point = randomPointInBounds(random);
            if (this.contains(point)) {
                return point;
            }
        }

        for (int x = this.southWest.getX(); x <= this.northEast.getX(); x++) {
            for (int y = this.southWest.getY(); y <= this.northEast.getY(); y++) {
                WorldPoint point = new WorldPoint(x, y, this.southWest.getPlane());
                if (this.contains(point)) {
                    return point;
                }
            }
        }

        return randomPointInBounds(random);
    }

    public boolean contains(WorldPoint point) {
        if (point == null || point.getPlane() != this.southWest.getPlane()) {
            return false;
        }

        if (this.polygon == null || this.polygon.length < 3) {
            return this.toWorldArea().contains(point);
        }

        if (!this.toWorldArea().contains(point)) {
            return false;
        }

        int x = point.getX();
        int y = point.getY();
        boolean inside = false;

        for (int i = 0, j = this.polygon.length - 1; i < this.polygon.length; j = i++) {
            WorldPoint current = this.polygon[i];
            WorldPoint previous = this.polygon[j];

            if (isOnSegment(point, previous, current)) {
                return true;
            }

            boolean intersects = current.getY() > y != previous.getY() > y
                    && x < (double) (previous.getX() - current.getX()) * (y - current.getY())
                    / (previous.getY() - current.getY()) + current.getX();

            if (intersects) {
                inside = !inside;
            }
        }

        return inside;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public WorldPoint getSouthWest() {
        return this.southWest;
    }

    public WorldPoint getNorthEast() {
        return this.northEast;
    }

    private static WorldPoint getSouthWestCorner(WorldPoint[] points) {
        WorldPoint firstPoint = points[0];
        int minX = firstPoint.getX();
        int minY = firstPoint.getY();

        for (WorldPoint point : points) {
            minX = Math.min(minX, point.getX());
            minY = Math.min(minY, point.getY());
        }

        return new WorldPoint(minX, minY, firstPoint.getPlane());
    }

    private static WorldPoint getNorthEastCorner(WorldPoint[] points) {
        WorldPoint firstPoint = points[0];
        int maxX = firstPoint.getX();
        int maxY = firstPoint.getY();

        for (WorldPoint point : points) {
            maxX = Math.max(maxX, point.getX());
            maxY = Math.max(maxY, point.getY());
        }

        return new WorldPoint(maxX, maxY, firstPoint.getPlane());
    }

    private static boolean isOnSegment(WorldPoint point, WorldPoint start, WorldPoint end) {
        int crossProduct = (point.getY() - start.getY()) * (end.getX() - start.getX())
                - (point.getX() - start.getX()) * (end.getY() - start.getY());

        if (crossProduct != 0) {
            return false;
        }

        return point.getX() >= Math.min(start.getX(), end.getX())
                && point.getX() <= Math.max(start.getX(), end.getX())
                && point.getY() >= Math.min(start.getY(), end.getY())
                && point.getY() <= Math.max(start.getY(), end.getY());
    }

    private WorldPoint randomPointInBounds(ThreadLocalRandom random) {
        int x = random.nextInt(this.southWest.getX(), this.northEast.getX() + 1);
        int y = random.nextInt(this.southWest.getY(), this.northEast.getY() + 1);
        return new WorldPoint(x, y, this.southWest.getPlane());
    }
}
