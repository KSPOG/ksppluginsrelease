package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Handles skilling level-up Continue dialogues without blocking the Account Builder thread.
 * The widget disappearing is the completion signal; elapsed time is only used to allow a
 * recovery re-click when the first interaction was not accepted by the client.
 */
public class KspLevelUpDialogueEvent implements BlockingEvent
{
    private static final int LEVEL_UP_CONTINUE_WIDGET = 15269891;
    private static final long RETRY_TIMEOUT_MS = 1_500L;

    private long lastClickAtMs;

    @Override
    public boolean validate()
    {
        return Microbot.isLoggedIn() && Rs2Widget.isWidgetVisible(LEVEL_UP_CONTINUE_WIDGET);
    }

    @Override
    public boolean execute()
    {
        if (!validate())
        {
            lastClickAtMs = 0L;
            return true;
        }

        long now = System.currentTimeMillis();
        if (lastClickAtMs == 0L || now - lastClickAtMs >= RETRY_TIMEOUT_MS)
        {
            if (Rs2Widget.clickWidget(LEVEL_UP_CONTINUE_WIDGET))
            {
                lastClickAtMs = now;
            }
        }

        // Stay blocking only while the level-up widget is actually present. No sleep/poll loop.
        return !validate();
    }

    @Override
    public BlockingEventPriority priority()
    {
        return BlockingEventPriority.HIGHEST;
    }
}
