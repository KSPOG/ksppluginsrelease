package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * All three Progressive modes use the same four user-assigned F2P training
 * zones, regardless of membership. Only matching live tree objects INSIDE
 * the selected zone may be targeted. The areas are 0-plane rectangles;
 * RuneLite's WorldArea constructor is (x, y, width, height, plane).
 */
final class KspProgressiveTreeSelection {
    private KspProgressiveTreeSelection() {}

    private static final WorldArea TREES = new WorldArea(3149, 3449, 23, 16, 0);
    private static final WorldArea OAKS = new WorldArea(3159, 3409, 13, 12, 0);
    private static final WorldArea WILLOWS = new WorldArea(3056, 3250, 8, 7, 0);
    private static final WorldArea YEWS = new WorldArea(3085, 3468, 5, 15, 0);

    static KspTree select(int woodcuttingLevel, int firemakingLevel,
                          boolean membersWorld, boolean burnMode) {
        // Deliberately no Maple/Magic/Redwood progression: those trees are
        // outside the four specifically assigned training areas. The manual
        // selector still provides those tree types.
        int level = burnMode ? Math.min(woodcuttingLevel, firemakingLevel) : woodcuttingLevel;
        if (level >= 60) return KspTree.YEW;
        if (level >= 30) return KspTree.WILLOW;
        if (level >= 15) return KspTree.OAK;
        return KspTree.TREE;
    }

    static WorldArea area(KspTree tree) {
        switch (tree) {
            case TREE: return TREES;
            case OAK: return OAKS;
            case WILLOW: return WILLOWS;
            case YEW: return YEWS;
            default: throw new IllegalArgumentException("Not an assigned progressive tree: " + tree);
        }
    }

    static boolean isInAssignedArea(KspTree tree, WorldPoint point) {
        return point != null && area(tree).contains(point);
    }

    static WorldPoint anchor(KspTree tree) {
        WorldArea zone = area(tree);
        return new WorldPoint(zone.getX() + zone.getWidth() / 2,
                zone.getY() + zone.getHeight() / 2, zone.getPlane());
    }
}
