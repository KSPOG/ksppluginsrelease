package net.runelite.client.plugins.microbot.kspseasonalevents;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KspHalloweenChemistInputModeTest {

    @Test
    void anOpenInputLayerIsTheAnswerBoxWhateverItsWording() {
        // The box's own text was never captured live; the input layer being open is enough.
        assertTrue(KspHalloweenChemistInput.isAnswerPrompt(7, false, false, false, "", ""));
        assertTrue(KspHalloweenChemistInput.isAnswerPrompt(2, true, true, false, "", "*"));
    }

    @Test
    void noInputLayerFallsBackToTheTextChecks() {
        assertFalse(KspHalloweenChemistInput.isAnswerPrompt(0, false, false, true, "", ""));
        assertTrue(KspHalloweenChemistInput.isAnswerPrompt(0, true, false, true, "Enter your answer:", ""));
    }

    @Test
    void privateMessagesSearchesAndBankPinsAreNeverAnswered() {
        assertFalse(KspHalloweenChemistInput.isAnswerPrompt(6, true, true, false, "Enter message to Bob", ""));
        assertFalse(KspHalloweenChemistInput.isAnswerPrompt(11, true, true, false, "Search for an item", ""));
        assertFalse(KspHalloweenChemistInput.isAnswerPrompt(7, true, true, false, "Enter your bank PIN", ""));
        assertFalse(KspHalloweenChemistInput.isAnswerPrompt(7, true, true, false, "How much?", ""));
    }
}
