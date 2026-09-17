package net.runelite.client.plugins.microbot.kspf2pgatheringprofit;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

public enum GatheringMethod
{
    IRON(Skill.MINING, 15, "Iron rocks", "Mine", "Iron ore", 440, 900, 45_000, null,
            new WorldPoint(3284, 3363, 0), new WorldPoint(3175, 3368, 0)),
    COAL(Skill.MINING, 30, "Coal rocks", "Mine", "Coal", 453, 500, 25_000, null,
            new WorldPoint(3147, 3148, 0), new WorldPoint(3030, 9738, 0)),
    MITHRIL(Skill.MINING, 55, "Mithril rocks", "Mine", "Mithril ore", 447, 140, 11_000, null,
            new WorldPoint(3021, 9739, 0)),
    ADAMANTITE(Skill.MINING, 70, "Adamantite rocks", "Mine", "Adamantite ore", 449, 90, 8_500, null,
            new WorldPoint(3018, 9739, 0)),
    RUNITE(Skill.MINING, 85, "Runite rocks", "Mine", "Runite ore", 451, 35, 4_500, null,
            new WorldPoint(3061, 3882, 0)),

    OAK(Skill.WOODCUTTING, 15, "Oak", "Chop down", "Oak logs", 1521, 900, 32_000, null,
            new WorldPoint(3166, 3415, 0), new WorldPoint(3103, 3242, 0)),
    WILLOW(Skill.WOODCUTTING, 30, "Willow", "Chop down", "Willow logs", 1519, 950, 45_000, null,
            new WorldPoint(3087, 3234, 0), new WorldPoint(3059, 3252, 0)),
    YEW(Skill.WOODCUTTING, 60, "Yew", "Chop down", "Yew logs", 1515, 220, 36_000, null,
            new WorldPoint(3206, 3502, 0), new WorldPoint(3047, 3270, 0)),

    TROUT_SALMON(Skill.FISHING, 20, "Fishing spot", "Lure", "Raw salmon", 331, 700, 35_000, "Fly fishing rod",
            new WorldPoint(3104, 3424, 0)),
    LOBSTER(Skill.FISHING, 40, "Fishing spot", "Cage", "Raw lobster", 377, 220, 26_000, "Lobster pot",
            new WorldPoint(2924, 3178, 0)),
    TUNA_SWORDFISH(Skill.FISHING, 50, "Fishing spot", "Harpoon", "Raw swordfish", 371, 180, 28_000, "Harpoon",
            new WorldPoint(2924, 3178, 0));

    public final Skill skill;
    public final int level;
    public final String nodeName;
    public final String action;
    public final String productName;
    public final int productId;
    public final int unitsHour;
    public final int xpHour;
    public final String tool;
    public final WorldPoint[] locations;

    GatheringMethod(Skill skill, int level, String nodeName, String action, String productName,
                    int productId, int unitsHour, int xpHour, String tool, WorldPoint... locations)
    {
        this.skill = skill;
        this.level = level;
        this.nodeName = nodeName;
        this.action = action;
        this.productName = productName;
        this.productId = productId;
        this.unitsHour = unitsHour;
        this.xpHour = xpHour;
        this.tool = tool;
        this.locations = locations;
    }

    public boolean wilderness()
    {
        return this == RUNITE;
    }

    public WorldPoint closest(WorldPoint from)
    {
        WorldPoint best = locations[0];
        if (from == null)
        {
            return best;
        }

        int bestDistance = Integer.MAX_VALUE;
        for (WorldPoint point : locations)
        {
            int distance = from.distanceTo(point);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = point;
            }
        }
        return best;
    }
}
