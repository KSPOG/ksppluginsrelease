package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

/** Full Halloween outfits Diango gives back, by item id. The children only run from a full outfit. Pure. */
final class KspHalloweenCostume {
    static final class Outfit {
        final String name;
        final int[] pieces;

        Outfit(String name, int... pieces) {
            this.name = name;
            this.pieces = pieces;
        }

        int held(IntPredicate has) {
            return (int) Arrays.stream(pieces).filter(has::test).count();
        }

        boolean all(IntPredicate has) {
            return held(has) == pieces.length;
        }
    }

    /** In order of preference: Skeleton sits at the top of Diango's list. */
    static final List<Outfit> OUTFITS = List.of(
            new Outfit("Skeleton", 9925, 9924, 9923, 9922, 9921),
            new Outfit("Gravedigger", 13283, 13284, 13285, 13287, 13286),
            new Outfit("Spooky", 24305, 24307, 24309, 24311, 24313),
            new Outfit("Spookier", 24315, 24317, 24319, 24321, 24323),
            new Outfit("Witch", 27473, 27475, 27477, 27479),
            new Outfit("Grim reaper", 12845, 31229, 31231, 31233));

    private KspHalloweenCostume() { }

    /**
     * The outfit to complete: the one with most pieces already held, ties to preference order, among outfits whose
     * every missing piece Diango lists. Null when none can be completed.
     */
    static Outfit choose(IntPredicate held, IntPredicate listed) {
        Outfit best = null;
        for (Outfit o : OUTFITS) {
            boolean completable = Arrays.stream(o.pieces).allMatch(id -> held.test(id) || listed.test(id));
            if (completable && (best == null || o.held(held) > best.held(held))) best = o;
        }
        return best;
    }

    static boolean anyWorn(IntPredicate wearing) {
        return OUTFITS.stream().anyMatch(o -> o.all(wearing));
    }
}
