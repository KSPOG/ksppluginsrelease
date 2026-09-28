package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.tutorialisland.areas;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public class TutAreas
{
    public static final WorldArea TUT_OVERWORLD_AREA = new WorldArea(3041, 3008, 112, 133, 0);
    public static final WorldArea TUTORIAL_ISLAND_UNDERGROUND_AREA = new WorldArea(3071, 9491, 49, 45, 0);
    public static final WorldArea START_AREA = new WorldArea(3092, 3100, 8, 13, 0);
    public static final WorldArea SURVIVAL_AREA = new WorldArea(3098, 3089, 8, 11, 0);
    public static final WorldArea COOKING_AREA = new WorldArea(3073, 3083, 6, 5, 0);
    public static final WorldArea QUEST_TUT_AREA = new WorldArea(3083, 3119, 7, 7, 0);
    public static final WorldArea MINING_SMITHING_AREA = new WorldArea(3073, 9494, 15, 15, 0);
    public static final WorldArea COMBAT_INSTRUCTOR_AREA = new WorldArea(3103, 9507, 6, 3, 0);
    public static final WorldArea RAT_PIT_AREA = new WorldArea(3097, 9514, 12, 9, 0);
    public static final WorldArea TUT_ISLAND_BANK_AREA = new WorldArea(3120, 3118, 12, 10, 0);
    public static final WorldArea BOND_INFO_AREA = new WorldArea(3125, 3123, 5, 3, 0);
    public static final WorldArea CHURCH_AREA = new WorldArea(3120, 3103, 14, 14, 0);
    public static final WorldArea TUTORIAL_END_AREA = new WorldArea(3134, 3084, 14, 14, 0);

    private static final WorldArea[] ALL_AREAS = {
            START_AREA,
            SURVIVAL_AREA,
            COOKING_AREA,
            QUEST_TUT_AREA,
            MINING_SMITHING_AREA,
            COMBAT_INSTRUCTOR_AREA,
            RAT_PIT_AREA,
            TUT_ISLAND_BANK_AREA,
            BOND_INFO_AREA,
            CHURCH_AREA,
            TUTORIAL_END_AREA
    };

    private TutAreas()
    {
    }

    public static boolean contains(WorldPoint point)
    {
        if (point == null)
        {
            return false;
        }

        if (TUT_OVERWORLD_AREA.contains(point))
        {
            return true;
        }

        if (TUTORIAL_ISLAND_UNDERGROUND_AREA.contains(point))
        {
            return true;
        }

        for (WorldArea area : ALL_AREAS)
        {
            if (area.contains(point))
            {
                return true;
            }
        }

        return false;
    }
}
