package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Dependency-free entrypoint; compiled with the latest Microbot Hub in CI.
 * Covers all three Progressive modes, including fixed area enforcement.
 */
public final class KspProgressiveTreeSelectionTest {
    private KspProgressiveTreeSelectionTest() {}

    public static void main(String[] args) {
        for (boolean members : new boolean[] {false, true}) {
            check(1, 99, members, false, KspTree.TREE);
            check(14, 99, members, false, KspTree.TREE);
            check(15, 99, members, false, KspTree.OAK);
            check(29, 99, members, false, KspTree.OAK);
            check(30, 99, members, false, KspTree.WILLOW);
            check(59, 99, members, false, KspTree.WILLOW);
            check(60, 99, members, false, KspTree.YEW);
            check(99, 99, members, false, KspTree.YEW);

            // Progressive Burn must respect Firemaking and the same four zones.
            check(99, 1, members, true, KspTree.TREE);
            check(99, 15, members, true, KspTree.OAK);
            check(99, 30, members, true, KspTree.WILLOW);
            check(99, 60, members, true, KspTree.YEW);
            check(99, 99, members, true, KspTree.YEW);
        }

        area(KspTree.TREE, 3149, 3449, 23, 16);
        area(KspTree.OAK, 3159, 3409, 13, 12);
        area(KspTree.WILLOW, 3056, 3250, 8, 7);
        area(KspTree.YEW, 3085, 3468, 5, 15);

        for (KspChopperMode mode : KspChopperMode.values()) {
            if (mode.isProgressive() == (mode == KspChopperMode.CHOP_BANK
                    || mode == KspChopperMode.CHOP_BURN
                    || mode == KspChopperMode.CHOP_DROP)) {
                throw new AssertionError("Wrong progressive flag for " + mode);
            }
            int strategies = (mode.isBank() ? 1 : 0)
                    + (mode.isBurn() ? 1 : 0) + (mode.isDrop() ? 1 : 0);
            if (strategies != 1) {
                throw new AssertionError("Mode must map to exactly one strategy: " + mode);
            }
        }

        System.out.println("PASS: four progressive areas, exact boundaries, level tiers and modes");
    }

    private static void area(KspTree tree, int x, int y, int width, int height) {
        WorldArea area = KspProgressiveTreeSelection.area(tree);
        if (area.getX() != x || area.getY() != y || area.getWidth() != width
                || area.getHeight() != height || area.getPlane() != 0)
            throw new AssertionError("Invalid area for " + tree + ": " + area);
        for (WorldPoint inside : new WorldPoint[] {
                new WorldPoint(x, y, 0),
                new WorldPoint(x + width - 1, y + height - 1, 0),
                KspProgressiveTreeSelection.anchor(tree) }) {
            if (!KspProgressiveTreeSelection.isInAssignedArea(tree, inside))
                throw new AssertionError("Expected inside: " + tree + " " + inside);
        }
        for (WorldPoint outside : new WorldPoint[] {
                new WorldPoint(x - 1, y, 0),
                new WorldPoint(x, y - 1, 0),
                new WorldPoint(x + width, y, 0),
                new WorldPoint(x, y + height, 0),
                new WorldPoint(x, y, 1) }) {
            if (KspProgressiveTreeSelection.isInAssignedArea(tree, outside))
                throw new AssertionError("Expected outside: " + tree + " " + outside);
        }
    }

    private static void check(int wc, int fm, boolean members, boolean burn, KspTree expected) {
        KspTree actual = KspProgressiveTreeSelection.select(wc, fm, members, burn);
        if (actual != expected) {
            throw new AssertionError("wc=" + wc + " fm=" + fm + " members="
                    + members + " burn=" + burn + ": expected " + expected + ", got " + actual);
        }
    }
}
