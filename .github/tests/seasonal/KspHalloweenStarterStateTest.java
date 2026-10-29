package net.runelite.client.plugins.microbot.kspseasonalevents;

/** Executable Java 11 regression checks for Angel/Henja state resolution. */
public final class KspHalloweenStarterStateTest {
    private static void check(String dialogue, KspHalloweenStarterState.Objective expected) {
        KspHalloweenStarterState.Objective actual =
                KspHalloweenStarterState.classify(dialogue);
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " but received " + actual
                    + " for: " + dialogue);
        }
    }

    public static void main(String[] args) {
        check("Smash the jack-o'-lanterns around Rimmington.",
                KspHalloweenStarterState.Objective.SMASH_LANTERNS);
        check("There are still more pumpkins to smash!",
                KspHalloweenStarterState.Objective.SMASH_LANTERNS);

        check("Get yourself a Halloween costume and scare off the<br>"
                        + "trick-or-treaters! Diango in Draynor Village should have<br>something.",
                KspHalloweenStarterState.Objective.SCARE_CHILDREN);
        check("You still need to spook more of the children.",
                KspHalloweenStarterState.Objective.SCARE_CHILDREN);

        check("Go speak to the Chemist west of Rimmington.",
                KspHalloweenStarterState.Objective.ADULT_CANDY);
        check("Make molten sugar and coat the old onions.",
                KspHalloweenStarterState.Objective.ADULT_CANDY);
        check("Hand the candy onions to the adults.",
                KspHalloweenStarterState.Objective.ADULT_CANDY);

        // Explanation alone is not enough to advance beyond children.
        check("The adults won't be frightened by your costume.",
                KspHalloweenStarterState.Objective.UNKNOWN);

        check("We're all done, here's your Vampyre reward!",
                KspHalloweenStarterState.Objective.CLAIM_REWARD);
        check("You have completed the Halloween event.",
                KspHalloweenStarterState.Objective.COMPLETE);

        check("You've smashed all the lanterns!",
                KspHalloweenStarterState.Objective.UNKNOWN);
        check("Hello there!",
                KspHalloweenStarterState.Objective.UNKNOWN);
        check("", KspHalloweenStarterState.Objective.UNKNOWN);
        check(null, KspHalloweenStarterState.Objective.UNKNOWN);

        System.out.println("PASS: Angel/Henja objectives resolve lanterns, children,"
                + " adult candy, final reward and completion without local-state inference");
    }
}
