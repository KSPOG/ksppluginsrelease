package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.meleescript;

import java.util.function.BooleanSupplier;

/** Acknowledge direct bank opening and fallback attempts, even on failure. */
final class MeleeBankOpenDispatcher
{
    private MeleeBankOpenDispatcher() {}

    static void dispatch(BooleanSupplier directOpen, Runnable walkFallback, Runnable acknowledge)
    {
        try
        {
            if (!directOpen.getAsBoolean())
            {
                walkFallback.run();
            }
        }
        finally
        {
            acknowledge.run();
        }
    }
}
