package net.runelite.client.plugins.microbot.kspaccountbuilder;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class KspAccountPlayTimePolicyTest
{
    @Test
    void rejectsUninitializedAccountSummaryVarc()
    {
        assertFalse(KspAccountPlayTimePolicy.isVerifiedReading(-1L));
        assertFalse(KspAccountPlayTimePolicy.isVerifiedReading(0L));
        assertFalse(KspAccountPlayTimePolicy.isVerifiedReading(59_999L));
        assertTrue(KspAccountPlayTimePolicy.isVerifiedReading(60_000L));
    }

    @Test
    void repairsIncorrectTwoMinuteCacheFromRealSixtySixHourSample()
    {
        long screenshotPlayTime = TimeUnit.HOURS.toMillis(2 * 24 + 18);
        long incorrectCache = TimeUnit.MINUTES.toMillis(2);
        assertEquals(screenshotPlayTime, KspAccountPlayTimePolicy.reconcile(incorrectCache, screenshotPlayTime));
    }

    @Test
    void neverReplacesVerifiedAgeWithZeroOrMissingSample()
    {
        long sixtySixHours = TimeUnit.HOURS.toMillis(66);
        assertEquals(sixtySixHours, KspAccountPlayTimePolicy.reconcile(sixtySixHours, 0L));
        assertEquals(sixtySixHours, KspAccountPlayTimePolicy.reconcile(sixtySixHours, -1L));
    }

    @Test
    void doesNotRegressWhenNewServerReadingRoundsDownToMinute()
    {
        long cached = TimeUnit.HOURS.toMillis(66) + 58_900L;
        long wholeMinute = TimeUnit.HOURS.toMillis(66);
        assertEquals(cached, KspAccountPlayTimePolicy.reconcile(cached, wholeMinute));
    }

    @Test
    void verifiedSampleCanBootstrapEmptyAccountCache()
    {
        assertEquals(TimeUnit.MINUTES.toMillis(1), KspAccountPlayTimePolicy.reconcile(-1L, 60_000L));
    }
}
