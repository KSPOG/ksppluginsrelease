package net.runelite.client.plugins.microbot.kspseasonalevents;

public final class KspHalloweenChemistInputTest {
    private static void check(boolean expected, boolean title, boolean full,
                              boolean input, String titleText, String body) {
        boolean got = KspHalloweenChemistInput.isAnswerPrompt(
                title, full, input, titleText, body);
        if (got != expected)
            throw new AssertionError("expected=" + expected + " actual=" + got
                    + " title=" + titleText + " body=" + body);
    }

    public static void main(String[] args) {
        check(true, true, true, true, "Enter your answer:", "");
        check(true, true, true, false, "Spell my name with atomic signs", "");
        check(true, false, true, true, "", "");
        check(true, true, false, true, "What is the answer to my riddle?", "");
        check(true, true, true, true, "<col=ffff00>Enter the number</col>", "92");
        check(false, true, false, false, "What is the answer to my riddle?", "");
        check(false, false, false, true, "", "");
        check(false, true, true, true, "Enter your bank PIN", "");
        check(false, true, true, true, "How much?", "");
        check(false, true, false, true, "Search for an item", "");
        System.out.println("PASS: Chemist text-input detection; no ordinary dialogue or unrelated prompts");
    }
}
