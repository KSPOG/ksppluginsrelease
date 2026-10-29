package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic options for the Chemist section of the 2026 Halloween event.
 * The wiki-confirmed topic is "Halloween" and the COFFIN answer is "92".
 * Never use fuzzy numeric matching; that could select an unrelated answer.
 */
final class KspHalloweenChemistDialogue {
    private KspHalloweenChemistDialogue() { }

    static int findOptionIndex(List<String> options) {
        if (options == null) return -1;
        for (int i = 0; i < options.size(); i++) {
            String text = normalize(options.get(i));
            if ("halloween".equals(text) || "92".equals(text))
                return i;
        }
        return -1;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]*>", " ")
                .trim().replaceAll("[.!?]+$", "")
                .trim().toLowerCase(Locale.ROOT);
    }
}
