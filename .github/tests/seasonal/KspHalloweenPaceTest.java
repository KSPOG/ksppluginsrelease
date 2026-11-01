package net.runelite.client.plugins.microbot.kspseasonalevents;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KspHalloweenPaceTest {

    private static int[] sample(KspHalloweenPace pace, int n, boolean reading, String text) {
        Random r = new Random(7);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = reading ? pace.readMs(text, r) : pace.reactionMs(r);
        Arrays.sort(out);
        return out;
    }

    @Test
    void reactionsAreHumanAndNeverInstant() {
        int[] ms = sample(KspHalloweenPace.draw(1), 5000, false, null);
        assertTrue(ms[0] >= KspHalloweenPace.REACTION_FLOOR_MS, "floor " + ms[0]);
        int median = ms[ms.length / 2];
        assertTrue(median >= 230 && median <= 340, "median " + median);
        assertTrue(ms[ms.length - 1] <= KspHalloweenPace.REACTION_CAP_MS, "cap " + ms[ms.length - 1]);
        // Right-skewed with a slow tail, not one fixed delay.
        assertTrue(ms[(int) (ms.length * 0.97)] > median * 2, "slow tail " + ms[(int) (ms.length * 0.97)]);
        // No pile-up on the floor value.
        long atFloor = Arrays.stream(ms).filter(v -> v == KspHalloweenPace.REACTION_FLOOR_MS).count();
        assertTrue(atFloor < ms.length * 0.03, "floor pile-up " + atFloor);
    }

    @Test
    void longerDialogueTakesLongerToRead() {
        KspHalloweenPace pace = KspHalloweenPace.draw(2);
        int shortRead = sample(pace, 2001, true, "Yep!")[1000];
        int longRead = sample(pace, 2001, true,
                "Six letters long, I hold no breath, Sealed with nails, I'm the box of death...")[1000];
        assertTrue(longRead > shortRead + 250, shortRead + " vs " + longRead);
        assertTrue(longRead < 1_600, "a skim, not a read: " + longRead);
        assertTrue(sample(pace, 2001, true, null)[2000] <= KspHalloweenPace.READ_CAP_MS);
    }

    @Test
    void sessionsDoNotShareOneRhythm() {
        int a = sample(KspHalloweenPace.draw(11), 2001, false, null)[1000];
        int b = sample(KspHalloweenPace.draw(99), 2001, false, null)[1000];
        assertNotEquals(a, b);
    }
}
