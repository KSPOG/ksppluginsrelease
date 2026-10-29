package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.util.Arrays;
import java.util.Collections;

public final class KspHalloweenChemistDialogueTest {
    private static void check(int expected, String... options) {
        int actual = KspHalloweenChemistDialogue.findOptionIndex(Arrays.asList(options));
        if (expected != actual)
            throw new AssertionError("expected " + expected + " got " + actual
                    + " options=" + Arrays.asList(options));
    }

    public static void main(String[] args) {
        // Screenshot: "What do you want to talk about? Halloween. Something else."
        check(0, "Halloween.", "Something else.");
        check(1, "Something else.", "<col=ff0000>Halloween.</col>");
        check(0, "92", "89", "93", "Something else");
        check(2, "89", "98", "92.");
        check(-1, "Something else.", "No thanks.");
        check(-1, "192", "920", "I have 92 coins");
        check(-1, "Chemistry", "Halloween decorations");
        check(-1);
        if (KspHalloweenChemistDialogue.findOptionIndex(null) != -1)
            throw new AssertionError("null options must be rejected");
        if (KspHalloweenChemistDialogue.findOptionIndex(Collections.singletonList(null)) != -1)
            throw new AssertionError("null widget option must be rejected");
        System.out.println("PASS: Chemist Halloween topic and COFFIN 92 only");
    }
}
