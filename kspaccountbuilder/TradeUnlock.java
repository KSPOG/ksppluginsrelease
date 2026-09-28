package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class TradeUnlock
{
    private TradeUnlock() {}

    /**
     * Reads Jagex's account-summary play-time varc.
     *
     * ACCOUNT_SUMMARY_PLAYTIME is expressed in whole minutes. The client read is
     * dispatched onto the client thread because raw varc reads are client state.
     * Returns -1 when no authoritative value can be read this attempt.
     */
    public static long readPlayTimeMillis()
    {
        if (!Microbot.isLoggedIn() || Microbot.getClient() == null || Microbot.getClientThread() == null)
        {
            return -1L;
        }

        Integer playTimeMinutes = Microbot.getClientThread()
                .runOnClientThreadOptional(() ->
                        Microbot.getClient().getVarcIntValue(VarClientID.ACCOUNT_SUMMARY_PLAYTIME))
                .orElse(null);

        if (playTimeMinutes == null || playTimeMinutes < 0)
        {
            log.info("[TradeUnlock] ACCOUNT_SUMMARY_PLAYTIME unavailable | value={}", playTimeMinutes);
            return -1L;
        }

        long playTimeMillis = TimeUnit.MINUTES.toMillis(playTimeMinutes.longValue());
        log.info("[TradeUnlock] ACCOUNT_SUMMARY_PLAYTIME | minutes={}", playTimeMinutes);
        return playTimeMillis;
    }

    public static int readPlayTimeMinutes()
    {
        long playTimeMillis = readPlayTimeMillis();
        if (playTimeMillis < 0L)
        {
            return -1;
        }

        return (int) Math.min(
                Integer.MAX_VALUE,
                TimeUnit.MILLISECONDS.toMinutes(playTimeMillis));
    }

    public static int readPlayTimeHours()
    {
        long playTimeMillis = readPlayTimeMillis();
        if (playTimeMillis < 0L)
        {
            return -1;
        }

        return (int) Math.min(
                Integer.MAX_VALUE,
                TimeUnit.MILLISECONDS.toHours(playTimeMillis));
    }
}
