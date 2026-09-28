package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas;

import lombok.Getter;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public enum Areas
{
    TIN_COPPER_VARROCK_EAST(
            "Varrock East Tin & Copper",
            new WorldPoint(3281, 3360, 0),
            new WorldPoint(3291, 3365, 0)
    ),
    IRON_VARROCK_EAST(
            "Varrock East Iron",
            new WorldPoint(3284, 3367, 0),
            new WorldPoint(3289, 3370, 0)
    ),
    CLAY_VARROCK_WEST(
            "Varrock West Clay",
            new WorldPoint(3179, 3370, 0),
            new WorldPoint(3184, 3377, 0)
    ),
    SILVER_VARROCK_WEST(
            "Varrock West Silver",
            new WorldPoint(3180, 3363, 0),
            new WorldPoint(3171, 3370, 0)
    ),
    COAL_BARBARIAN_VILLAGE(
            "Barbarian Village Coal",
            new WorldPoint(3079, 3418, 0),
            new WorldPoint(3085, 3422, 0)
    ),
    GOLD_RIMMINGTON(
            "Rimmington Gold Mine",
            new WorldPoint(2973, 3235, 0),
            new WorldPoint(2979, 3231, 0)
    ),
    PORT_SARIM_DEPOSIT_BOX(
            "Port Sarim Deposit Box",
            new WorldPoint(3044, 3237, 0),
            new WorldPoint(3052, 3234, 0)
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
}
