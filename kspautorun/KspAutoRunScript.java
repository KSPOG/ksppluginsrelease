package net.runelite.client.plugins.microbot.kspautorun;

import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
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

    private long lastInvokeAt = 0L;
    // Kept locally so runtime state collection does not fall back to another plugin's shared Microbot.status.
    private volatile String state = "waiting to log in";

    public boolean run(KspAutoRunConfig config)
    {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (Microbot.pauseAllScripts.get() || Thread.currentThread().isInterrupted())
                {
                    state = "paused";
                    return;
                }

                if (!Microbot.isLoggedIn())
                {
                    state = "waiting to log in";
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
            state = "run enabled";
            return;
        }

        final int threshold = Math.max(1, Math.min(100, config.runThreshold()));
        final int runEnergy = Rs2Player.getRunEnergy();

        if (runEnergy < threshold)
        {
            state = "waiting for energy";
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - lastInvokeAt < INVOKE_COOLDOWN_MS)
        {
            state = "waiting for run toggle";
            return;
        }

        state = "enabling run";
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

    @Override
    public void shutdown()
    {
        super.shutdown();
        lastInvokeAt = 0L;
        state = "stopped";
    }
}
