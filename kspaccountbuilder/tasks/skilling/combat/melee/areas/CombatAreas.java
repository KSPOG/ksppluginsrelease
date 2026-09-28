/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.runelite.api.coords.WorldArea
 *  net.runelite.api.coords.WorldPoint
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.areas;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public enum CombatAreas {
    COWPEN("Cowpen", new WorldPoint(3253, 3255, 0), new WorldPoint(3265, 3296, 0)),
    CHICKENS("Chickens", new WorldPoint(3186, 3304, 0), new WorldPoint(3169, 3288, 0)),
    AL_KHARID_WARRIOR("Al Kharid warrior", new WorldPoint(3287, 3177, 0), new WorldPoint(3297, 3167, 0)),
    HILL_GIANTS("Hill Giants", Arrays.asList(new WorldPoint(3119, 9853, 0), new WorldPoint(3121, 9850, 0), new WorldPoint(3123, 9849, 0), new WorldPoint(3124, 9846, 0), new WorldPoint(3124, 9844, 0), new WorldPoint(3123, 9841, 0), new WorldPoint(3123, 9837, 0), new WorldPoint(3123, 9835, 0), new WorldPoint(3120, 9831, 0), new WorldPoint(3117, 9828, 0), new WorldPoint(3113, 9828, 0), new WorldPoint(3111, 9826, 0), new WorldPoint(3110, 9824, 0), new WorldPoint(3106, 9823, 0), new WorldPoint(3104, 9824, 0), new WorldPoint(3102, 9824, 0), new WorldPoint(3098, 9827, 0), new WorldPoint(3095, 9830, 0), new WorldPoint(3095, 9835, 0), new WorldPoint(3098, 9838, 0), new WorldPoint(3100, 9838, 0), new WorldPoint(3101, 9839, 0), new WorldPoint(3104, 9839, 0), new WorldPoint(3105, 9839, 0), new WorldPoint(3106, 9841, 0), new WorldPoint(3108, 9843, 0), new WorldPoint(3108, 9846, 0), new WorldPoint(3109, 9847, 0), new WorldPoint(3109, 9849, 0), new WorldPoint(3110, 9850, 0), new WorldPoint(3113, 9850, 0), new WorldPoint(3115, 9853, 0), new WorldPoint(3119, 9853, 0))),
    MOSS_GIANTS("Moss Giants", Arrays.asList(new WorldPoint(3159, 9896, 0), new WorldPoint(3158, 9896, 0), new WorldPoint(3157, 9899, 0), new WorldPoint(3156, 9901, 0), new WorldPoint(3154, 9902, 0), new WorldPoint(3153, 9903, 0), new WorldPoint(3153, 9907, 0), new WorldPoint(3154, 9908, 0), new WorldPoint(3158, 9908, 0), new WorldPoint(3160, 9906, 0), new WorldPoint(3163, 9906, 0), new WorldPoint(3163, 9901, 0), new WorldPoint(3159, 9898, 0), new WorldPoint(3159, 9896, 0)));

    private final String displayName;
    private final WorldPoint southWest;
    private final WorldPoint northEast;
    private final List<WorldPoint> polygon;

    private CombatAreas(String displayName, WorldPoint firstCorner, WorldPoint secondCorner) {
        this.displayName = displayName;
        this.polygon = Collections.emptyList();
        int minX = Math.min(firstCorner.getX(), secondCorner.getX());
        int minY = Math.min(firstCorner.getY(), secondCorner.getY());
        int maxX = Math.max(firstCorner.getX(), secondCorner.getX());
        int maxY = Math.max(firstCorner.getY(), secondCorner.getY());
        this.southWest = new WorldPoint(minX, minY, firstCorner.getPlane());
        this.northEast = new WorldPoint(maxX, maxY, firstCorner.getPlane());
    }

    private CombatAreas(String displayName, List<WorldPoint> polygon) {
        this.displayName = displayName;
        this.polygon = polygon;
        int minX = polygon.stream().mapToInt(WorldPoint::getX).min().orElse(0);
        int minY = polygon.stream().mapToInt(WorldPoint::getY).min().orElse(0);
        int maxX = polygon.stream().mapToInt(WorldPoint::getX).max().orElse(0);
        int maxY = polygon.stream().mapToInt(WorldPoint::getY).max().orElse(0);
        int plane = polygon.isEmpty() ? 0 : polygon.get(0).getPlane();
        this.southWest = new WorldPoint(minX, minY, plane);
        this.northEast = new WorldPoint(maxX, maxY, plane);
    }

    public WorldArea toWorldArea() {
        int width = this.northEast.getX() - this.southWest.getX() + 1;
        int height = this.northEast.getY() - this.southWest.getY() + 1;
        return new WorldArea(this.southWest.getX(), this.southWest.getY(), width, height, this.southWest.getPlane());
    }

    public boolean contains(WorldPoint point) {
        if (point == null || point.getPlane() != this.southWest.getPlane()) {
            return false;
        }
        if (this.polygon == null || this.polygon.isEmpty()) {
            return this.toWorldArea().contains(point);
        }
        if (!this.toWorldArea().contains(point)) {
            return false;
        }
        boolean inside = false;
        int pointCount = this.polygon.size();
        int current = 0;
        int previous = pointCount - 1;
        while (current < pointCount) {
            boolean intersects;
            WorldPoint currentPoint = this.polygon.get(current);
            WorldPoint previousPoint = this.polygon.get(previous);
            if (this.isPointOnSegment(point, previousPoint, currentPoint)) {
                return true;
            }
            boolean bl = intersects = currentPoint.getY() > point.getY() != previousPoint.getY() > point.getY() && (double)point.getX() < (double)(previousPoint.getX() - currentPoint.getX()) * (double)(point.getY() - currentPoint.getY()) / (double)(previousPoint.getY() - currentPoint.getY()) + (double)currentPoint.getX();
            if (intersects) {
                inside = !inside;
            }
            previous = current++;
        }
        return inside;
    }

    public WorldPoint getRandomPoint() {
        int plane = this.southWest.getPlane();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempts = 0; attempts < 100; ++attempts) {
            int y;
            int x = random.nextInt(this.southWest.getX(), this.northEast.getX() + 1);
            WorldPoint point = new WorldPoint(x, y = random.nextInt(this.southWest.getY(), this.northEast.getY() + 1), plane);
            if (!this.contains(point)) continue;
            return point;
        }
        return new WorldPoint((this.southWest.getX() + this.northEast.getX()) / 2, (this.southWest.getY() + this.northEast.getY()) / 2, plane);
    }

    private boolean isPointOnSegment(WorldPoint point, WorldPoint segmentStart, WorldPoint segmentEnd) {
        long crossProduct = (long)(point.getY() - segmentStart.getY()) * (long)(segmentEnd.getX() - segmentStart.getX()) - (long)(point.getX() - segmentStart.getX()) * (long)(segmentEnd.getY() - segmentStart.getY());
        if (crossProduct != 0L) {
            return false;
        }
        return point.getX() >= Math.min(segmentStart.getX(), segmentEnd.getX()) && point.getX() <= Math.max(segmentStart.getX(), segmentEnd.getX()) && point.getY() >= Math.min(segmentStart.getY(), segmentEnd.getY()) && point.getY() <= Math.max(segmentStart.getY(), segmentEnd.getY());
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

    public List<WorldPoint> getPolygon() {
        return this.polygon;
    }
}
