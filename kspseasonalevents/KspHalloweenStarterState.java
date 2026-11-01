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
        /** Said after the event: "Thanks again for your help! You're such a nice stranger." */
        ALREADY_COMPLETE,
        UNKNOWN
    }

    private KspHalloweenStarterState() { }

    static Objective classify(String rawDialogue) {
        if (rawDialogue == null || rawDialogue.trim().isEmpty())
            return Objective.UNKNOWN;

        String text = normal(rawDialogue);

        if (text.contains("thanks again for your help") || text.contains("such a nice stranger"))
            return Objective.ALREADY_COMPLETE;
        if (text.contains("completed the halloween event")
                || text.contains("completed this halloween event")
                || text.contains("event is complete"))
            return Objective.COMPLETE;

        // Reward language must be explicit; generic thanks is not enough.
        boolean reward = text.contains("vampyre")
                || text.contains("your reward") || text.contains("your rewards")
                || text.contains("here's your reward") || text.contains("here are your rewards");
        boolean finalLanguage = text.contains("we're done") || text.contains("we are done")
                || text.contains("all done") || text.contains("finished")
                || text.contains("that's everything") || text.contains("that is everything");
        if ((reward && finalLanguage) || text.contains("take our dad's halloween costume"))
            return Objective.CLAIM_REWARD;

        // The adults step only counts when the dialogue names the candy chain.
        if (text.contains("chemist") || text.contains("molten sugar")
                || text.contains("candy onion") || text.contains("old onion")
                || (text.contains("sugar") && text.contains("onion")))
            return Objective.ADULT_CANDY;

        boolean trickOrTreater = text.contains("trick-or-treater")
                || text.contains("trick or treater")
                || text.contains("children") || text.contains("kids");
        boolean adultsOnly = (text.contains("adult") || text.contains("grown-up")
                || text.contains("grown up")) && !trickOrTreater;
        boolean scare = text.contains("scare") || text.contains("spook")
                || text.contains("frighten");
        boolean costume = text.contains("costume") || text.contains("diango")
                || text.contains("dress up") || text.contains("dressing up")
                || (text.contains("wear") && text.contains("halloween"));

        if (adultsOnly)
            return Objective.UNKNOWN;
        if ((trickOrTreater && scare) || (costume && trickOrTreater))
            return Objective.SCARE_CHILDREN;

        boolean lantern = text.contains("lantern") || text.contains("jack-o-lantern")
                || text.contains("jack o lantern") || text.contains("pumpkin");
        boolean smash = text.contains("smash") || text.contains("break")
                || text.contains("destroy");
        boolean unfinished = text.contains("still") || text.contains("more")
                || text.contains("remaining") || text.contains("haven't")
                || text.contains("not all") || text.contains("need to")
                || text.contains("have to");
        boolean completedLanternStatement = lantern && !unfinished
                && (text.contains("you've smashed all")
                    || text.contains("you have smashed all")
                    || text.contains("all the lanterns are smashed")
                    || text.contains("all eight") || text.contains("all 8"));
        if (completedLanternStatement)
            return Objective.UNKNOWN;
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
