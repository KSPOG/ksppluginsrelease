package net.runelite.client.plugins.microbot.kspautorun;

import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.awt.Rectangle;
import java.util.concurrent.TimeUnit;

public class KspAutoRunScript extends Script
{
    private static final long CHECK_INTERVAL_MS = 250L;
    private static final long RUN_TOGGLE_CONFIRMATION_TIMEOUT_MS = 3_000L;

    private long runToggleRequestedAt = 0L;
    private long runDisabledObservedAt = 0L;
    private boolean awaitingEnergyReset = false;
    private boolean runEnabledObserved = false;
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
        final int threshold = Math.max(1, Math.min(100, config.runThreshold()));
        final int runEnergy = Rs2Player.getRunEnergy();

        if (Rs2Player.isRunEnabled())
        {
            // A successful toggle is acknowledged only once. Keep the completed
            // energy cycle latched until Run is observed disabled below the
            // threshold. Clearing it while Run is still enabled would allow a
            // transient disabled-state update at high energy to submit a second,
            // non-idempotent toggle.
            if (!runEnabledObserved)
            {
                awaitingEnergyReset = true;
            }

            runEnabledObserved = true;
            runToggleRequestedAt = 0L;
            runDisabledObservedAt = 0L;
            state = "monitoring run energy";
            return;
        }

        runEnabledObserved = false;

        if (runToggleRequestedAt != 0L)
        {
            if (System.currentTimeMillis() - runToggleRequestedAt
                    >= RUN_TOGGLE_CONFIRMATION_TIMEOUT_MS)
            {
                // Toggle Run is not idempotent. If its postcondition was not observed,
                // do not send another toggle that could disable Run after a delayed client
                // update. Resume only after energy has fallen below the threshold.
                runToggleRequestedAt = 0L;
                runDisabledObservedAt = 0L;
                awaitingEnergyReset = true;
                state = "waiting for next energy cycle";
                return;
            }

            // Energy can fall below the threshold before the client has published the
            // result of a submitted invoke. Keep the request intact until its
            // postcondition is observed (or it times out), otherwise the next energy
            // cycle may submit a second, non-idempotent toggle.
            state = "waiting for run toggle";
            return;
        }

        if (runEnergy < threshold)
        {
            // A request that did not reach its postcondition can be retried only after
            // this energy cycle ends. Toggle Run is non-idempotent, so retrying an
            // ambiguous request while the original invoke may still be queued can turn
            // Run back off.
            runToggleRequestedAt = 0L;
            runDisabledObservedAt = 0L;
            awaitingEnergyReset = false;
            state = "waiting for energy";
            return;
        }

        if (awaitingEnergyReset)
        {
            state = "waiting for next energy cycle";
            return;
        }

        final long now = System.currentTimeMillis();

        // A different script can enable Run between polling iterations. Require the
        // disabled postcondition to remain true across two observations before
        // invoking the toggle, so a stale or interleaved read cannot turn Run off.
        if (runDisabledObservedAt == 0L)
        {
            runDisabledObservedAt = now;
            state = "confirming run disabled";
            return;
        }

        if (now - runDisabledObservedAt < CHECK_INTERVAL_MS)
        {
            state = "confirming run disabled";
            return;
        }

        // The run state may change after the confirmation above and before this
        // scheduled task submits the invoke. Toggle Run is not idempotent, so
        // invoking the orb after another client update has enabled it would turn
        // Run back off and restart the waiting-for-energy cycle.
        if (Rs2Player.isRunEnabled())
        {
            runToggleRequestedAt = 0L;
            runDisabledObservedAt = 0L;
            awaitingEnergyReset = false;
            state = "monitoring run energy";
            return;
        }

        state = "enabling run";
        if (invokeRunOrb())
        {
            // Start the confirmation window only after the client-thread invocation has
            // been submitted. The earlier timestamp is for the disabled-state check and
            // must not consume time from the action postcondition.
            runToggleRequestedAt = System.currentTimeMillis();
            runDisabledObservedAt = 0L;
            // The invoke has been submitted; wait for the run-state postcondition rather
            // than continuing to report the completed action while the client updates.
            state = "waiting for run toggle";
        }
        else
        {
            // The orb may have disappeared between the state confirmation and the
            // invoke attempt. Require a fresh disabled-state observation before a
            // later retry instead of carrying that stale confirmation forward.
            runDisabledObservedAt = 0L;
            state = "run orb unavailable";
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
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() ->
                {
                    // Keep the final postcondition check with the invoke itself. The
                    // scheduled thread can only make a best-effort observation; Run
                    // may be enabled by another script before this client action runs.
                    // Toggle Run is non-idempotent, so invoking in that case would
                    // disable it again.
                    if (Rs2Player.isRunEnabled())
                    {
                        return false;
                    }

                    final Widget currentWidget = Microbot.getClient().getWidget(packedWidgetId);
                    if (currentWidget == null || currentWidget.isHidden())
                    {
                        return false;
                    }

                    final Rectangle bounds = currentWidget.getBounds();
                    final Rectangle invokeRectangle =
                            Rs2UiHelper.isRectangleWithinCanvas(bounds)
                                    ? bounds
                                    : Rs2UiHelper.getDefaultRectangle();

                    final NewMenuEntry menuEntry = new NewMenuEntry()
                            .option("Toggle Run")
                            .target("")
                            .identifier(1)
                            .type(MenuAction.CC_OP)
                            .param0(-1)
                            .param1(packedWidgetId)
                            .forceLeftClick(false);

                    Microbot.doInvoke(menuEntry, invokeRectangle);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        runToggleRequestedAt = 0L;
        runDisabledObservedAt = 0L;
        awaitingEnergyReset = false;
        runEnabledObserved = false;
        state = "stopped";
    }
}
