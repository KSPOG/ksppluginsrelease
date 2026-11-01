package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.util.Locale;

/**
 * Recognizes the 2026 Halloween Chemist's free-text answer field.
 * Dialogue options ("Halloween." and a selectable "92") are handled
 * separately; this classifier only accepts an actual chatbox input.
 */
final class KspHalloweenChemistInput {
    private static final int MODE_NONE = 0;
    private static final int MODE_PRIVATE_MESSAGE = 6;
    private static final int MODE_SEARCH = 11;

    private KspHalloweenChemistInput() { }

    /** {@code inputMode} is VarClientID.MESLAYERMODE: any mode but none, a private message or a search is a typed answer. */
    static boolean isAnswerPrompt(int inputMode, boolean titleVisible, boolean fullInputVisible,
                                  boolean inputVisible, String titleText, String bodyText) {
        if (unrelated(titleVisible ? titleText : "", fullInputVisible ? bodyText : "")) return false;
        if (inputMode == MODE_PRIVATE_MESSAGE || inputMode == MODE_SEARCH) return false;
        if (inputMode > MODE_NONE) return true;
        return isAnswerPrompt(titleVisible, fullInputVisible, inputVisible, titleText, bodyText);
    }

    static boolean isAnswerPrompt(boolean titleVisible, boolean fullInputVisible,
                                  boolean inputVisible, String titleText, String bodyText) {
        if (!fullInputVisible && !inputVisible) return false;

        String title = normalize(titleVisible ? titleText : "");
        String body = normalize(fullInputVisible ? bodyText : "");
        String prompt = title + " " + body;

        if (unrelated(title, body)) return false;

        boolean answerQuestion = prompt.contains("answer") || prompt.contains("riddle")
                || prompt.contains("coffin") || prompt.contains("atomic")
                || prompt.contains("number") || prompt.contains("enter")
                || prompt.contains("type");
        // A visible full input in the Chemist conversation is enough; the chat input alone needs a question cue.
        return fullInputVisible || answerQuestion;
    }

    /** Do not type into unrelated chatbox entry forms if they appear. */
    private static boolean unrelated(String titleText, String bodyText) {
        String prompt = normalize(titleText) + " " + normalize(bodyText);
        return prompt.contains("bank pin") || prompt.contains("search")
                || prompt.contains("how much") || prompt.contains("quantity")
                || prompt.contains("password") || prompt.contains("chat message");
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
