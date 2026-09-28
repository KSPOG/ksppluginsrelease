package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Handles the skilling level-up Continue dialogue before normal Account Builder
 * task logic resumes. Reconstructed from the supplied 1.5.200 plugin bytecode.
 */
public class KspLevelUpDialogueEvent implements BlockingEvent
{
    private static final int LEVEL_UP_CONTINUE_WIDGET = 15269891;

    @Override
    public boolean validate() { return Microbot.isLoggedIn() && Rs2Widget.isWidgetVisible(LEVEL_UP_CONTINUE_WIDGET); }

    @Override
    public boolean execute()
    {
        if (!validate())
        {
            return true;
        }

        if (!Rs2Widget.clickWidget(LEVEL_UP_CONTINUE_WIDGET))
        {
            return false;
        }

        sleepUntil(() -> !validate(), 3_000);
        return !validate();
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.HIGHEST; }
}
