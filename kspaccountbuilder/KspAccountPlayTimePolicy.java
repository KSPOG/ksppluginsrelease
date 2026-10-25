package net.runelite.client.plugins.microbot.kspaccountbuilder;

/**
 * A freshly logged-in client's ACCOUNT_SUMMARY_PLAYTIME varc can still be
 * zero/uninitialized until Account Summary updates it. Never treat that
 * placeholder as a verified age for a previously played account.
 *
 * Play time comes from Jagex in whole minutes, while the local cache advances
 * between reads. Keep the larger value to avoid moving backwards on a
 * minute-precision refresh.
 */
public final class KspAccountPlayTimePolicy
{
    private KspAccountPlayTimePolicy() {}

    public static boolean isVerifiedReading(long readingMillis)
    {
        return readingMillis >= 60_000L;
    }

    public static long reconcile(long existingMillis, long readingMillis)
    {
        if (!isVerifiedReading(readingMillis))
            return Math.max(0L, existingMillis);
        return Math.max(Math.max(0L, existingMillis), readingMillis);
    }
}
