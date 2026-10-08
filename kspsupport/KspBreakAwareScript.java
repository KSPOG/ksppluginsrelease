package net.runelite.client.plugins.microbot.kspsupport;

import net.runelite.client.plugins.microbot.Script;

/** Keep Microbot's normal run checks and additionally respect KSP logout breaks. */
public abstract class KspBreakAwareScript extends Script
{
    @Override
    public boolean run()
    {
        return !KspBreakService.shouldPause() && super.run();
    }
}
