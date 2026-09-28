/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.runelite.api.coords.WorldArea
 *  net.runelite.api.coords.WorldPoint
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public enum GEArea {
    GRAND_EXCHANGE("Grand Exchange", new WorldPoint(3159, 3497, 0), new WorldPoint(3171, 3484, 0));

    private final String displayName;
    private final WorldPoint southWest;
    private final WorldPoint northEast;

    private GEArea(String displayName, WorldPoint firstCorner, WorldPoint secondCorner) {
        this.displayName = displayName;
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
        int x = random.nextInt(this.southWest.getX(), this.northEast.getX() + 1);
        int y = random.nextInt(this.southWest.getY(), this.northEast.getY() + 1);
        return new WorldPoint(x, y, this.southWest.getPlane());
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
}
