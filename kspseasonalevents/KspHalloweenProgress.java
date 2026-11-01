package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.util.function.IntUnaryOperator;
import net.runelite.client.plugins.microbot.kspseasonalevents.KspHalloween2026Script.Stage;

/**
 * The event's own progress from its varbits (not yet in RuneLite's gameval). The per-entity bits stay set for the rest
 * of the event; the hw26_*_smashed/_tricked counters read 0 after their step, so only the bits are counted.
 */
final class KspHalloweenProgress {
    static final int MAIN = 16579;
    static final int[] LANTERN_BITS = {16583, 16584, 16585, 16610, 16611, 16612, 16613, 16614};
    static final int[] CHILD_BITS = {16615, 16618, 16625, 16626, 16627};
    static final int[] ADULT_BITS = {16628, 16636, 16637, 16638, 16645};

    static final KspHalloweenProgress NONE = new KspHalloweenProgress(0, 0, 0, 0);

    final int main;
    final int lanterns;
    final int children;
    final int adults;

    private KspHalloweenProgress(int main, int lanterns, int children, int adults) {
        this.main = main;
        this.lanterns = lanterns;
        this.children = children;
        this.adults = adults;
    }

    static KspHalloweenProgress read(IntUnaryOperator varbit) {
        return new KspHalloweenProgress(varbit.applyAsInt(MAIN), count(varbit, LANTERN_BITS),
                count(varbit, CHILD_BITS), count(varbit, ADULT_BITS));
    }

    private static int count(IntUnaryOperator varbit, int[] ids) {
        int set = 0;
        for (int id : ids) if (varbit.applyAsInt(id) > 0) set++;
        return set;
    }

    boolean lanternsDone() { return lanterns >= LANTERN_BITS.length; }
    boolean childrenDone() { return children >= CHILD_BITS.length; }
    boolean adultsDone() { return adults >= ADULT_BITS.length; }

    /**
     * Where a (re)start picks up from hw26_main: 5 started, 10 lanterns smashed, 15 lanterns reported, 20 children
     * scared, 25 children reported, 30 riddle solved, 35 adults fed, 40 final talk, 45 complete. Null below 5 (ask
     * Angel/Henja). The candy steps return CHEMIST_RIDDLE; the caller refines them from the inventory. Pure.
     */
    Stage startStage(boolean costumeOn) {
        if (main >= 45) return Stage.COMPLETED;
        if (main >= 35) return Stage.FINAL_TALK;
        if (main >= 25) return Stage.CHEMIST_RIDDLE;
        if (main >= 20) return Stage.REPORT_CHILDREN;
        if (main >= 15) return costumeOn ? Stage.SPOOK_CHILDREN : Stage.GET_COSTUME;
        if (main >= 10) return Stage.REPORT_LANTERNS;
        if (main >= 5) return Stage.SMASH_LANTERNS;
        return null;
    }

    /** The stage to be in: {@code current}, or past it when the game says its work is already done. Pure. */
    Stage advance(Stage current) {
        switch (current) {
            case SMASH_LANTERNS:
            case VERIFY_LANTERNS:
                return lanternsDone() ? Stage.REPORT_LANTERNS : current;
            case GET_COSTUME:
            case SPOOK_CHILDREN:
                return childrenDone() ? Stage.REPORT_CHILDREN : current;
            case CHEMIST_RIDDLE:
            case COOK_SUGAR:
            case COLLECT_ONIONS:
            case COAT_ONIONS:
            case GIVE_CANDY:
                return adultsDone() ? Stage.FINAL_TALK : current;
            default:
                return current;
        }
    }

    @Override public String toString() {
        return "hw26_main=" + main + " lanterns=" + lanterns + "/8 children=" + children + "/5 adults=" + adults + "/5";
    }
}
