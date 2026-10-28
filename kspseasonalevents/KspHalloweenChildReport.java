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
        boolean explicitlyIncomplete = refersToChildren
                && (message.contains("more") || message.contains("still")
                    || message.contains("haven't") || message.contains("not all")
                    || message.contains("not yet") || message.contains("keep trying"));
        boolean clearlyCompleted = refersToChildren
                && (message.contains("you've scared")
                    || message.contains("you have scared")
                    || message.contains("have been scared")
                    || message.contains("all scared off"));
        boolean nextObjective = message.contains("chemist")
                || message.contains("molten sugar") || message.contains("candy onion")
                || message.contains("old onion")
                || (message.contains("sugar")
                    && (message.contains("adult") || message.contains("onion")));

        if (explicitlyIncomplete)
            return Instruction.SCARE_CHILDREN;
        if (clearlyCompleted)
            return nextObjective ? Instruction.VISIT_CHEMIST : Instruction.UNKNOWN;
        if (refersToChildren && tellsUsToScare)
            return Instruction.SCARE_CHILDREN;

        // Merely mentioning adults is not evidence of progression. Require
        // the Chemist or the candy/sugar/onion task.
        if (nextObjective)
            return Instruction.VISIT_CHEMIST;

        return Instruction.UNKNOWN;
    }
}
