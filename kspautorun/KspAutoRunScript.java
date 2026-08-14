package net.runelite.client.plugins.microbot.kspautorun;

import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.util.concurrent.TimeUnit;

public class KspAutoRunScript extends Script
{
    private static final long CHECK_INTERVAL_MS = 250L;
    private static final long INVOKE_COOLDOWN_MS = 600L;

    private Boolean previousMicrobotAutoRun;
    private long lastInvokeAt = 0L;

    public boolean run(KspAutoRunConfig config)
    {
        /*
         * Microbot's base Script.run() contains its own auto-run handler, which
         * currently calls Rs2Player.toggleRunEnergy(true). That Microbot helper
         * uses a natural mouse click on the run orb.
         *
         * Disable the global handler while this plugin is active so that this
         * plugin is the only code responsible for enabling Run.
         */
        if (previousMicrobotAutoRun == null)
        {
            previousMicrobotAutoRun = Microbot.enableAutoRunOn;
        }
        setMicrobotAutoRun(false);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                /*
                 * Keep the global handler disabled before super.run() executes,
                 * because super.run() checks Microbot.enableAutoRunOn.
                 */
                if (Microbot.enableAutoRunOn)
                {
                    setMicrobotAutoRun(false);
                }

                if (Microbot.pauseAllScripts.get() || Thread.currentThread().isInterrupted())
                {
                    return;
                }

                if (!Microbot.isLoggedIn())
                {
                    return;
                }

                enableRunIfThresholdReached(config);
            }
            catch (Exception ex)
            {
                System.err.println("KSP Auto Run: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void enableRunIfThresholdReached(KspAutoRunConfig config)
    {
        if (Rs2Player.isRunEnabled())
        {
            return;
        }

        final int threshold = Math.max(1, Math.min(100, config.runThreshold()));
        final int runEnergy = Rs2Player.getRunEnergy();

        if (runEnergy < threshold)
        {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - lastInvokeAt < INVOKE_COOLDOWN_MS)
        {
            return;
        }

        if (invokeRunOrb())
        {
            lastInvokeAt = now;
        }
    }

    /**
     * Enables Run by invoking the run-orb widget directly.
     *
     * This deliberately does NOT call:
     * - Microbot.getMouse().click(...)
     * - Rs2Player.toggleRunEnergy(true)
     *
     * The latter currently performs a natural mouse click internally.
     */
    private boolean invokeRunOrb()
    {
        final int packedWidgetId = WidgetInfo.MINIMAP_TOGGLE_RUN_ORB.getId();
        final Widget widget = Rs2Widget.getWidget(packedWidgetId);

        if (widget == null)
        {
            return false;
        }

        final Rectangle bounds = Microbot.getClientThread()
                .runOnClientThreadOptional(() ->
                {
                    final Widget currentWidget = Microbot.getClient().getWidget(packedWidgetId);
                    return currentWidget != null ? currentWidget.getBounds() : null;
                })
                .orElse(null);

        final Rectangle invokeRectangle =
                bounds != null && Rs2UiHelper.isRectangleWithinCanvas(bounds)
                        ? bounds
                        : Rs2UiHelper.getDefaultRectangle();

        final NewMenuEntry menuEntry = new NewMenuEntry()
                .option("Toggle Run")
                .target("")
                .identifier(1)
                .type(MenuAction.CC_OP)
                .param0(-1)
                .param1(widget.getId())
                .forceLeftClick(false);

        Microbot.doInvoke(menuEntry, invokeRectangle);
        return true;
    }

    private void setMicrobotAutoRun(boolean enabled)
    {
        Microbot.enableAutoRunOn = enabled;
        Microbot.getConfigManager().setConfiguration(
                MicrobotConfig.configGroup,
                MicrobotConfig.keyEnableAutoRunOn,
                enabled
        );
    }

    @Override
    public void shutdown()
    {
        super.shutdown();

        if (previousMicrobotAutoRun != null)
        {
            setMicrobotAutoRun(previousMicrobotAutoRun);
            previousMicrobotAutoRun = null;
        }

        lastInvokeAt = 0L;
    }
}
