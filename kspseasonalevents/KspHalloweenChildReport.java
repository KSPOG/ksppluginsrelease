package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.util.Locale;

/**
 * Classifies the objective Angel/Henja describe while reporting child scares.
 * This is dialogue *intent*, not evidence that a click was successful.
 * No RuneLite APIs are needed, so exact in-game strings can be tested.
 */
final class KspHalloweenChildReport {
    enum Instruction { SCARE_CHILDREN, VISIT_CHEMIST, UNKNOWN }

    private KspHalloweenChildReport() { }

    static Instruction classify(String rawDialogue) {
        if (rawDialogue == null || rawDialogue.trim().isEmpty())
            return Instruction.UNKNOWN;

        String message = rawDialogue.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim().toLowerCase(Locale.ROOT);

        boolean refersToChildren = message.contains("trick-or-treater")
                || message.contains("children") || message.contains("kids");
        boolean tellsUsToScare = message.contains("scare")
                || message.contains("spook") || message.contains("frighten");
        boolean childInstruction = refersToChildren && tellsUsToScare;
        boolean explicitlyIncomplete = refersToChildren
                && (message.contains("more") || message.contains("still")
                    || message.contains("haven't") || message.contains("not all")
                    || message.contains("not yet") || message.contains("keep trying"));
        if (childInstruction || explicitlyIncomplete)
            return Instruction.SCARE_CHILDREN;

        // Mentions of adults alone are not enough: an explanation about
        // adults ignoring costumes can occur during the child objective.
        if (message.contains("chemist") || message.contains("molten sugar")
                || message.contains("candy onion") || message.contains("old onion")
                || (message.contains("sugar")
                    && (message.contains("adult") || message.contains("onion"))))
            return Instruction.VISIT_CHEMIST;

        return Instruction.UNKNOWN;
    }
}
