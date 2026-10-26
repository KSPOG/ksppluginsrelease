package net.runelite.client.plugins.microbot.kspwillowchopper;

/**
 * Standalone regression checks for the progression decision table.
 * Run as a Java main when compiling this package with the Microbot dependencies.
 */
public final class KspProgressiveTreeSelectionTest {
    private KspProgressiveTreeSelectionTest() {}

    public static void main(String[] args) {
        check(1, 1, false, false, KspTree.TREE);
        check(15, 1, false, false, KspTree.OAK);
        check(29, 99, false, false, KspTree.OAK);
        check(30, 99, false, false, KspTree.WILLOW);
        check(45, 99, false, false, KspTree.WILLOW);
        check(60, 1, false, false, KspTree.YEW);
        check(99, 99, false, false, KspTree.YEW);

        check(44, 99, true, false, KspTree.WILLOW);
        check(45, 99, true, false, KspTree.MAPLE);
        check(60, 99, true, false, KspTree.YEW);
        check(75, 99, true, false, KspTree.MAGIC);
        check(90, 99, true, false, KspTree.REDWOOD);

        // Progressive Burn must never choose logs above the current FM level.
        check(90, 1, true, true, KspTree.TREE);
        check(90, 15, true, true, KspTree.OAK);
        check(90, 30, true, true, KspTree.WILLOW);
        check(90, 45, true, true, KspTree.MAPLE);
        check(90, 60, true, true, KspTree.YEW);
        check(90, 75, true, true, KspTree.MAGIC);
        check(90, 90, true, true, KspTree.REDWOOD);
        check(99, 45, false, true, KspTree.WILLOW);

        for (KspChopperMode mode : KspChopperMode.values()) {
            if (mode.isProgressive() == (mode == KspChopperMode.CHOP_BANK
                    || mode == KspChopperMode.CHOP_BURN
                    || mode == KspChopperMode.CHOP_DROP)) {
                throw new AssertionError("Wrong progressive flag for " + mode);
            }
            int strategies = (mode.isBank() ? 1 : 0)
                    + (mode.isBurn() ? 1 : 0)
                    + (mode.isDrop() ? 1 : 0);
            if (strategies != 1) {
                throw new AssertionError("Mode must map to exactly one strategy: " + mode);
            }
        }

        for (KspTree tree : new KspTree[] { KspTree.TREE, KspTree.OAK,
                KspTree.WILLOW, KspTree.MAPLE, KspTree.YEW, KspTree.MAGIC,
                KspTree.REDWOOD }) {
            if (KspProgressiveTreeSelection.anchor(tree) == null) {
                throw new AssertionError("No travel anchor for " + tree);
            }
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
