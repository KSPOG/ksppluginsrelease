package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.util.Locale;

/**
 * Classifies Angel/Henja dialogue into the current 2026 Halloween objective.
 *
 * This deliberately does not inspect inventory, saved stages or local click
 * counters. Those can decide how to satisfy an objective, but the event
 * objective itself comes from the starter conversation.
 */
final class KspHalloweenStarterState {
    enum Objective {
        SMASH_LANTERNS,
        SCARE_CHILDREN,
        ADULT_CANDY,
        CLAIM_REWARD,
        COMPLETE,
        UNKNOWN
    }

    private KspHalloweenStarterState() { }

    static Objective classify(String rawDialogue) {
        if (rawDialogue == null || rawDialogue.trim().isEmpty())
            return Objective.UNKNOWN;

        String text = normal(rawDialogue);

        if (text.contains("completed the halloween event")
                || text.contains("completed this halloween event")
                || text.contains("event is complete"))
            return Objective.COMPLETE;

        // Final reward language must be explicit. Generic thanks/congratulations
        // are not enough to change stage.
        boolean reward = text.contains("vampyre")
                || text.contains("your reward") || text.contains("your rewards")
                || text.contains("here's your reward") || text.contains("here are your rewards");
        boolean finalLanguage = text.contains("we're done") || text.contains("we are done")
                || text.contains("all done") || text.contains("finished")
                || text.contains("that's everything") || text.contains("that is everything");
        if (reward && finalLanguage)
            return Objective.CLAIM_REWARD;

        // The adults step is considered authoritative only when the dialogue
        // names the actual solution chain. Merely saying adults ignore a
        // costume is explanation, not enough evidence to leave child scares.
        if (text.contains("chemist") || text.contains("molten sugar")
                || text.contains("candy onion") || text.contains("old onion")
                || (text.contains("sugar") && text.contains("onion")))
            return Objective.ADULT_CANDY;

        boolean trickOrTreater = text.contains("trick-or-treater")
                || text.contains("trick or treater")
                || text.contains("children") || text.contains("kids");
        boolean scare = text.contains("scare") || text.contains("spook")
                || text.contains("frighten");
        boolean costume = text.contains("costume") || text.contains("diango")
                || text.contains("dress up") || text.contains("dressing up")
                || (text.contains("wear") && text.contains("halloween"));
        if ((trickOrTreater && scare) || (costume && (trickOrTreater || scare)))
            return Objective.SCARE_CHILDREN;

        boolean lantern = text.contains("lantern") || text.contains("jack-o-lantern")
                || text.contains("jack o lantern") || text.contains("pumpkin");
        boolean smash = text.contains("smash") || text.contains("break")
                || text.contains("destroy");
        boolean unfinished = text.contains("still") || text.contains("more")
                || text.contains("remaining") || text.contains("haven't")
                || text.contains("not all") || text.contains("need to")
                || text.contains("have to");
        if (lantern && (smash || unfinished))
            return Objective.SMASH_LANTERNS;

        return Objective.UNKNOWN;
    }

    private static String normal(String raw) {
        return raw.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim().toLowerCase(Locale.ROOT);
    }
}
