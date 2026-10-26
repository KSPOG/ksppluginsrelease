package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.api.coords.WorldPoint;

/**
 * Predictable, quest-free Woodcutting progression for the three progressive modes.
 * Special/quest-locked resource trees remain available through the manual selector.
 * F2P uses the readily accessible mainland trees; the F2P maple grove in
 * Corsair Cove is deliberately not included because its transport/access differs.
 */
final class KspProgressiveTreeSelection {
    private KspProgressiveTreeSelection() {
    }

    static KspTree select(int woodcuttingLevel, int firemakingLevel,
                          boolean membersWorld, boolean burnMode) {
        // Firemaking must be able to consume every log that the selected tree yields.
        int level = burnMode ? Math.min(woodcuttingLevel, firemakingLevel) : woodcuttingLevel;
        if (membersWorld && level >= 90) return KspTree.REDWOOD;
        if (membersWorld && level >= 75) return KspTree.MAGIC;
        if (level >= 60) return KspTree.YEW;
        if (membersWorld && level >= 45) return KspTree.MAPLE;
        if (level >= 30) return KspTree.WILLOW;
        if (level >= 15) return KspTree.OAK;
        return KspTree.TREE;
    }

    /**
     * Training-area approach points, not exact tree tiles. The existing
     * scene-object query chooses an actual nearby choppable object on arrival.
     */
    static WorldPoint anchor(KspTree tree) {
        switch (tree) {
            case TREE:
                return new WorldPoint(3156, 3457, 0); // Varrock west
            case OAK:
                return new WorldPoint(3166, 3416, 0); // Varrock west
            case WILLOW:
                return new WorldPoint(3086, 3231, 0); // Draynor
            case MAPLE:
                return new WorldPoint(2726, 3501, 0); // Seers' Village (members)
            case YEW:
                return new WorldPoint(3087, 3475, 0); // Varrock Palace
            case MAGIC:
                return new WorldPoint(2706, 3397, 0); // Sorcerer's Tower (members)
            case REDWOOD:
                return new WorldPoint(1570, 3492, 1); // WC Guild scaffolding (members)
            default:
                throw new IllegalArgumentException("Not a progressive tree: " + tree);
        }
    }
}
