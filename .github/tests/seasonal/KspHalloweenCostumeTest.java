package net.runelite.client.plugins.microbot.kspseasonalevents;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KspHalloweenCostumeTest {

    private static final Set<Integer> NONE = Set.of();

    @Test
    void aFreshAccountTakesTheSkeletonOutfit() {
        // Diango lists every outfit on a first event; Skeleton is first and at the top of his list.
        KspHalloweenCostume.Outfit o = KspHalloweenCostume.choose(NONE::contains, id -> true);
        assertEquals("Skeleton", o.name);
    }

    @Test
    void anOutfitAlreadyPartlyHeldIsFinishedInstead() {
        Set<Integer> held = Set.of(13283, 13284, 13285); // gravedigger mask, top, leggings
        assertEquals("Gravedigger", KspHalloweenCostume.choose(held::contains, id -> true).name);
    }

    @Test
    void anOutfitDiangoCannotCompleteIsSkipped() {
        // Skeleton gloves missing from his list: a full Skeleton outfit is impossible, take the next full one.
        KspHalloweenCostume.Outfit o = KspHalloweenCostume.choose(NONE::contains, id -> id != 9922);
        assertEquals("Gravedigger", o.name);
        assertNull(KspHalloweenCostume.choose(NONE::contains, id -> false));
    }

    @Test
    void onlyAWholeOutfitCountsAsWorn() {
        Set<Integer> skeleton = Set.of(9921, 9922, 9923, 9924, 9925);
        assertTrue(KspHalloweenCostume.anyWorn(skeleton::contains));
        Set<Integer> mixed = Set.of(9921, 9922, 9923, 9924, 13283); // four skeleton pieces and a gravedigger mask
        assertFalse(KspHalloweenCostume.anyWorn(mixed::contains));
    }
}
