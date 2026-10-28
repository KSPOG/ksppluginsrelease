package net.runelite.client.plugins.microbot.kspseasonalevents;

/** Executable Java 11 regression checks for actual Halloween NPC dialogue. */
public final class KspHalloweenChildReportTest {
    private static void check(String dialogue, KspHalloweenChildReport.Instruction expected) {
        KspHalloweenChildReport.Instruction actual =
                KspHalloweenChildReport.classify(dialogue);
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " but received " + actual
                    + " for: " + dialogue);
        }
    }

    public static void main(String[] args) {
        check("Get yourself a Halloween costume and scare off the<br>"
                    + "trick-or-treaters! Diango in Draynor Village should have<br>something.",
                KspHalloweenChildReport.Instruction.SCARE_CHILDREN);
        check("Get yourself a Halloween costume and scare off the"
                    + " trick-or-treaters! Diango should have something.",
                KspHalloweenChildReport.Instruction.SCARE_CHILDREN);
        check("You still need to scare more trick-or-treaters.",
                KspHalloweenChildReport.Instruction.SCARE_CHILDREN);
        check("You haven't frightened all the children yet.",
                KspHalloweenChildReport.Instruction.SCARE_CHILDREN);
        check("Scare off the kids first!",
                KspHalloweenChildReport.Instruction.SCARE_CHILDREN);
        check("The adults won't be frightened by your costume.",
                KspHalloweenChildReport.Instruction.UNKNOWN);
        check("The trick-or-treaters are coming tonight!",
                KspHalloweenChildReport.Instruction.UNKNOWN);
        check("You've scared all the children! Now speak to the Chemist.",
                KspHalloweenChildReport.Instruction.VISIT_CHEMIST);
        check("You've scared all the children!",
                KspHalloweenChildReport.Instruction.UNKNOWN);
        check("You have scared the kids, now get molten sugar for the adults.",
                KspHalloweenChildReport.Instruction.VISIT_CHEMIST);
        check("Go speak to the Chemist west of Rimmington.",
                KspHalloweenChildReport.Instruction.VISIT_CHEMIST);
        check("Make molten sugar to coat old onions for the adults.",
                KspHalloweenChildReport.Instruction.VISIT_CHEMIST);
        check("Give candy onions to the adults.",
                KspHalloweenChildReport.Instruction.VISIT_CHEMIST);
        check("Hello, there!",
                KspHalloweenChildReport.Instruction.UNKNOWN);
        check("", KspHalloweenChildReport.Instruction.UNKNOWN);
        check(null, KspHalloweenChildReport.Instruction.UNKNOWN);
        System.out.println("PASS: exact 2026 child instruction, incomplete objective,"
                + " chemist progression, and unknown-dialogue safeguards");
    }
}
