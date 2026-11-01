package net.runelite.client.plugins.microbot.kspseasonalevents;

import net.runelite.client.plugins.microbot.kspseasonalevents.KspHalloween2026Script.Stage;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KspHalloweenProgressTest {

    private static KspHalloweenProgress read(int main, int lanterns, int children, int adults) {
        Map<Integer, Integer> vars = new HashMap<>();
        vars.put(KspHalloweenProgress.MAIN, main);
        for (int i = 0; i < lanterns; i++) vars.put(KspHalloweenProgress.LANTERN_BITS[i], 1);
        for (int i = 0; i < children; i++) vars.put(KspHalloweenProgress.CHILD_BITS[i], 1);
        for (int i = 0; i < adults; i++) vars.put(KspHalloweenProgress.ADULT_BITS[i], 1);
        return KspHalloweenProgress.read(id -> vars.getOrDefault(id, 0));
    }

    @Test
    void liveStuckStateLeavesTheChildStep() {
        // Read live 2026-11-01: hw26_main=20, every lantern and child bit set, the counters 0.
        KspHalloweenProgress p = read(20, 8, 5, 0);
        assertEquals(8, p.lanterns);
        assertEquals(5, p.children);
        assertTrue(p.childrenDone());
        assertEquals(Stage.REPORT_CHILDREN, p.advance(Stage.SPOOK_CHILDREN));
        assertEquals(Stage.REPORT_CHILDREN, p.advance(Stage.GET_COSTUME));
    }

    @Test
    void partialChildrenStayInTheChildStep() {
        assertEquals(Stage.SPOOK_CHILDREN, read(20, 8, 3, 0).advance(Stage.SPOOK_CHILDREN));
    }

    @Test
    void lanternsDoneSkipSmashingAndVerifying() {
        KspHalloweenProgress p = read(0, 8, 0, 0);
        assertEquals(Stage.REPORT_LANTERNS, p.advance(Stage.SMASH_LANTERNS));
        assertEquals(Stage.REPORT_LANTERNS, p.advance(Stage.VERIFY_LANTERNS));
        assertEquals(Stage.SMASH_LANTERNS, read(0, 7, 0, 0).advance(Stage.SMASH_LANTERNS));
    }

    @Test
    void adultsDoneGoStraightToTheFinalTalk() {
        KspHalloweenProgress p = read(20, 8, 5, 5);
        for (Stage candy : new Stage[] {Stage.CHEMIST_RIDDLE, Stage.COOK_SUGAR,
                Stage.COLLECT_ONIONS, Stage.COAT_ONIONS, Stage.GIVE_CANDY}) {
            assertEquals(Stage.FINAL_TALK, p.advance(candy), candy.name());
        }
        assertEquals(Stage.GIVE_CANDY, read(20, 8, 5, 4).advance(Stage.GIVE_CANDY));
    }

    @Test
    void reportAndTalkStagesAreNeverMoved() {
        // Bits say what is done, not whether it was reported: those stay with the Angel/Henja dialogue.
        KspHalloweenProgress p = read(20, 8, 5, 5);
        for (Stage talk : new Stage[] {Stage.INTRODUCTION, Stage.REPORT_LANTERNS, Stage.REPORT_CHILDREN,
                Stage.FINAL_TALK, Stage.WAIT_FOR_REWARD, Stage.COMPLETED, Stage.NEEDS_REVIEW}) {
            assertEquals(talk, p.advance(talk), talk.name());
        }
    }

    @Test
    void loggedOutZerosNeverMoveAnything() {
        // Microbot.getVarbitValue returns 0 before login: zeros must not advance or rewind.
        KspHalloweenProgress p = read(0, 0, 0, 0);
        for (Stage s : Stage.values()) assertEquals(s, p.advance(s), s.name());
        assertFalse(p.lanternsDone() || p.childrenDone() || p.adultsDone());
    }

    @Test
    void savedProgressThatDisagreesLosesToTheBits() {
        // A saved SPOOK_CHILDREN stage (the live profile) with every child bit set.
        assertEquals(Stage.REPORT_CHILDREN, read(20, 8, 5, 0).advance(Stage.valueOf("SPOOK_CHILDREN")));
    }

    @Test
    void startStageFollowsEveryObservedMainValue() {
        assertEquals(null, read(0, 0, 0, 0).startStage(false));
        assertEquals(Stage.SMASH_LANTERNS, read(5, 3, 0, 0).startStage(false));
        assertEquals(Stage.REPORT_LANTERNS, read(10, 8, 0, 0).startStage(false));
        assertEquals(Stage.GET_COSTUME, read(15, 8, 0, 0).startStage(false));
        assertEquals(Stage.SPOOK_CHILDREN, read(15, 8, 2, 0).startStage(true));
        assertEquals(Stage.REPORT_CHILDREN, read(20, 8, 5, 0).startStage(true));
        assertEquals(Stage.CHEMIST_RIDDLE, read(25, 8, 5, 0).startStage(false));
        assertEquals(Stage.CHEMIST_RIDDLE, read(30, 8, 5, 3).startStage(false));
        assertEquals(Stage.FINAL_TALK, read(35, 8, 5, 5).startStage(false));
        assertEquals(Stage.FINAL_TALK, read(40, 8, 5, 5).startStage(false));
        assertEquals(Stage.COMPLETED, read(45, 8, 5, 5).startStage(false));
    }

    @Test
    void outOfOrderBitsAreCountedNotIndexed() {
        Map<Integer, Integer> vars = new HashMap<>();
        vars.put(KspHalloweenProgress.CHILD_BITS[4], 1);
        vars.put(KspHalloweenProgress.CHILD_BITS[1], 1);
        assertEquals(2, KspHalloweenProgress.read(id -> vars.getOrDefault(id, 0)).children);
    }
}
