package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.util.Locale;

/**
 * Recognizes the 2026 Halloween Chemist's free-text answer field.
 * Dialogue options ("Halloween." and a selectable "92") are handled
 * separately; this classifier only accepts an actual chatbox input.
 */
final class KspHalloweenChemistInput {
    private KspHalloweenChemistInput() { }

    static boolean isAnswerPrompt(boolean titleVisible, boolean fullInputVisible,
                                  boolean inputVisible, String titleText, String bodyText) {
        if (!fullInputVisible && !inputVisible) return false;

        String title = normalize(titleVisible ? titleText : "");
        String body = normalize(fullInputVisible ? bodyText : "");
        String prompt = title + " " + body;

        // Do not type into unrelated chatbox entry forms if they appear.
        if (prompt.contains("bank pin") || prompt.contains("search")
                || prompt.contains("how much") || prompt.contains("quantity")
                || prompt.contains("password") || prompt.contains("chat message")) return false;

        boolean answerQuestion = prompt.contains("answer") || prompt.contains("riddle")
                || prompt.contains("coffin") || prompt.contains("atomic")
                || prompt.contains("number") || prompt.contains("enter")
                || prompt.contains("type");
        // RuneLite's full input widget may have no readable label. A visible
        // full input within an already confirmed Chemist conversation is
        // sufficient; generic chatbox input alone requires a question cue.
        return fullInputVisible || answerQuestion;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
