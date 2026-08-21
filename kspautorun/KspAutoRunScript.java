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

import java.awt.Rectangle;
import java.util.concurrent.TimeUnit;

public class KspAutoRunScript extends Script
{
    private static final long CHECK_INTERVAL_MS = 250L;
    private static final long RUN_TOGGLE_CONFIRMATION_TIMEOUT_MS = 3_000L;

    private long runToggleRequestedAt = 0L;
    private long runDisabledObservedAt = 0L;
    // Identifies the complete threshold lifecycle, including a submitted toggle
    // whose confirmation is missed or times out. This keeps state reporting tied
    // to the action lifecycle rather than only to a later client observation.
    private long energyCycle = 1L;
    private boolean awaitingEnergyReset = false;
    private boolean runEnabledObserved = false;
    private Boolean previousMicrobotAutoRun;
    // Kept locally so runtime state collection does not fall back to another plugin's shared Microbot.status.
    private volatile String state = "waiting to log in";

    public boolean run(KspAutoRunConfig config)
    {
        // The shared Microbot auto-run handler also toggles the Run orb. Toggle Run
        // is non-idempotent, so this plugin must be the sole owner while it is active.
        if (previousMicrobotAutoRun == null)
        {
            previousMicrobotAutoRun = Microbot.enableAutoRunOn;
        }
        setMicrobotAutoRun(false);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                // Keep the shared handler disabled if another component re-enables it
                // while this script owns Run toggles.
                if (Microbot.enableAutoRunOn)
                {
                    setMicrobotAutoRun(false);
                }

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
        final RunState runState = getRunState();

        // Keep energy and the Run flag from the same client-thread observation. A
        // mixed snapshot can falsely look like "Run disabled below threshold" and
        // prematurely re-arm this non-idempotent toggle cycle.
        if (runState == null)
        {
            state = "waiting for run state";
            return;
        }

        final int runEnergy = runState.runEnergy;
        if (runState.runEnabled)
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
            // Enabling Run is the postcondition of this cycle, not a terminal
            // state. The next valid action cannot occur until Run is disabled
            // below the threshold, so report that intentional wait explicitly.
            state = energyState("waiting for energy reset", runEnergy, threshold);
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
                state = energyState("waiting for next energy cycle", runEnergy, threshold);
                return;
            }

            // Energy can fall below the threshold before the client has published the
            // result of a submitted invoke. Keep the request intact until its
            // postcondition is observed (or it times out), otherwise the next energy
            // cycle may submit a second, non-idempotent toggle.
            state = energyState("waiting for run toggle", runEnergy, threshold);
            return;
        }

        if (runEnergy < threshold)
        {
            // Run state is updated asynchronously. After this script has observed a
            // completed toggle, do not treat one disabled read as a new energy cycle:
            // a stale read would clear the latch and allow a later high-energy read to
            // submit another non-idempotent Toggle Run. Match the confirmation used
            // before toggling and require the reset postcondition to persist.
            if (awaitingEnergyReset)
            {
                final long now = System.currentTimeMillis();
                if (runDisabledObservedAt == 0L)
                {
                    runDisabledObservedAt = now;
                    state = "confirming energy reset";
                    return;
                }

                if (now - runDisabledObservedAt < CHECK_INTERVAL_MS)
                {
                    state = "confirming energy reset";
                    return;
                }
            }

            // A request that did not reach its postcondition can be retried only after
            // this energy cycle ends. Toggle Run is non-idempotent, so retrying an
            // ambiguous request while the original invoke may still be queued can turn
            // Run back off.
            runToggleRequestedAt = 0L;
            runDisabledObservedAt = 0L;
            if (awaitingEnergyReset)
            {
                // A disabled, below-threshold observation completes the previous
                // lifecycle (whether its toggle was confirmed or timed out). The
                // next wait/enable sequence is therefore a distinct energy cycle.
                energyCycle++;
                awaitingEnergyReset = false;
            }
            state = energyState("waiting for energy", runEnergy, threshold);
            return;
        }

        if (awaitingEnergyReset)
        {
            // The reset postcondition is specifically "Run disabled while energy is
            // below the threshold". A later high-energy observation invalidates a
            // pending low-energy observation; retaining its timestamp would let a
            // future, single low-energy read clear the latch and reopen this
            // non-idempotent toggle cycle.
            runDisabledObservedAt = 0L;
            state = energyState("waiting for next energy cycle", runEnergy, threshold);
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

        state = energyState("enabling run", runEnergy, threshold);
        if (invokeRunOrb())
        {
            // Start the confirmation window only after the client-thread invocation has
            // been submitted. The earlier timestamp is for the disabled-state check and
            // must not consume time from the action postcondition.
            runToggleRequestedAt = System.currentTimeMillis();
            runDisabledObservedAt = 0L;
            // The invoke has been submitted; wait for the run-state postcondition rather
            // than continuing to report the completed action while the client updates.
            state = energyState("waiting for run toggle", runEnergy, threshold);
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

    private RunState getRunState()
    {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> new RunState(
                        Rs2Player.getRunEnergy(),
                        Rs2Player.isRunEnabled()
                ))
                .orElse(null);
    }

    private static final class RunState
    {
        private final int runEnergy;
        private final boolean runEnabled;

        private RunState(int runEnergy, boolean runEnabled)
        {
            this.runEnergy = runEnergy;
            this.runEnabled = runEnabled;
        }
    }

    private String energyState(String phase, int runEnergy, int threshold)
    {
        // The monitor retains the stable part of this state and omits detail in
        // parentheses. Include the threshold-cycle number in that stable part so
        // the complete wait -> invoke -> confirmation lifecycle remains
        // distinguishable from an actual stalled repetition.
        return "energy cycle " + energyCycle + ": " + phase
                + " (energy " + runEnergy + "/" + threshold + ")";
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

        runToggleRequestedAt = 0L;
        runDisabledObservedAt = 0L;
        energyCycle = 1L;
        awaitingEnergyReset = false;
        runEnabledObserved = false;
        state = "stopped";
    }
}
