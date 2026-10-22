package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.fmarea;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public enum FireArea
{
    FM_AREA_DRAYNOR_BANK("Draynor Bank", new WorldArea(3091, 3247, 4, 4, 0)),
    FM_AREA_VARROCK_WEST("Varrock West", new WorldArea(3180, 3429, 11, 4, 0)),
    FM_AREA_VARROCK_NORTHWEST("Varrock Northwest", new WorldArea(3160, 3485, 10, 9, 0)),
    FM_AREA_VARROCK_EAST("Varrock East", new WorldArea(3250, 3427, 8, 4, 0));

    private final String displayName;
    private final WorldArea worldArea;

    FireArea(String displayName, WorldArea worldArea)
    {
        this.displayName = displayName;
        this.worldArea = worldArea;
    }

    public static FireArea randomArea()
    {
        FireArea[] areas = values();
        return areas[ThreadLocalRandom.current().nextInt(areas.length)];
    }

    public WorldArea toWorldArea()
    {
        return worldArea;
    }

    public WorldPoint getRandomPoint()
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(worldArea.getX(), worldArea.getX() + worldArea.getWidth());
        int y = random.nextInt(worldArea.getY(), worldArea.getY() + worldArea.getHeight());
        return new WorldPoint(x, y, worldArea.getPlane());
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public WorldPoint getSouthWest()
    {
        return new WorldPoint(worldArea.getX(), worldArea.getY(), worldArea.getPlane());
    }

    public WorldPoint getNorthEast()
    {
        return new WorldPoint(
                worldArea.getX() + worldArea.getWidth() - 1,
                worldArea.getY() + worldArea.getHeight() - 1,
                worldArea.getPlane());
    }
}
