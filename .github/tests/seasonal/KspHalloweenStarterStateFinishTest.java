package net.runelite.client.plugins.microbot.kspseasonalevents;

import net.runelite.client.plugins.microbot.kspseasonalevents.KspHalloweenStarterState.Objective;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KspHalloweenStarterStateFinishTest {

    @Test
    void afterTheEventTheStarterSaysItIsAlreadyDone() {
        // Wiki: said by Angel/Henja in any session after the event.
        assertEquals(Objective.ALREADY_COMPLETE,
                KspHalloweenStarterState.classify("Thanks again for your help! You're such a nice stranger."));
    }

    @Test
    void theRewardLineIsTheRewardStep() {
        assertEquals(Objective.CLAIM_REWARD, KspHalloweenStarterState.classify(
                "Thanks for your help! Here, you can take our dad's Halloween costume. He won't be needing it any time soon!"));
    }

    @Test
    void reportingTheChildrenLeadsToTheCandyPlan() {
        // Transcript lines 96-99: the report reply ("Plan B") is the adult candy step, not more scares.
        assertEquals(Objective.ADULT_CANDY, KspHalloweenStarterState.classify(
                "There's a box of old stinky onions by the back door. We just need some sugar!"
                        + " The chemist down the road should have some."));
    }

    @Test
    void theChildInstructionIsStillTheChildStep() {
        // Transcript line 55.
        assertEquals(Objective.SCARE_CHILDREN, KspHalloweenStarterState.classify(
                "Get yourself a Halloween costume and scare off the trick-or-treaters!"
                        + " Diango in Draynor Village should have something."));
    }
}
