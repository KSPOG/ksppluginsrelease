package net.runelite.client.plugins.microbot.kspaccountbuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.randomevents.KspRandomEventSolver;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.concurrent.ThreadLocalRandom;

@PluginDescriptor(
        name = PluginConstants.KSP + "Account Builder",
        description = "Builds a F2P main account with automated account progression",
        tags = {"microbot", "ksp", "account", "builder", "hotreload"},
        authors = {"KSP"},
        version = KspAccountBuilderPlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = true,
        isExternal = true
)
@Slf4j
@SuppressWarnings("unused")
public class KspAccountBuilderPlugin extends Plugin
{
    public static final String VERSION = "1.9.14";

    private static final long SKILLING_MOUSE_ANTIBAN_MIN_DELAY_MS = 12_000L;
    private static final long SKILLING_MOUSE_ANTIBAN_MAX_DELAY_MS = 35_000L;
    private static final int SKILLING_MOUSE_OFFSCREEN_CHANCE_PERCENT = 40;

    @Inject
    private KspAccountBuilderScript script;

    @Inject
    private KspAccountBuilderConfig config;

    @Inject
    private KSPAccountBuilderOverlay overlay;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    private KspRandomEventSolver randomEventSolver;
    private boolean runtimeActive;
    private String antibanDecisionKey;
    private boolean antibanEnabledForCurrentTask;
    private long nextSkillingMouseAntibanAtMillis;

    @Provides
    KspAccountBuilderConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspAccountBuilderConfig.class);
    }

    @Override
    protected synchronized void startUp()
    {
        cleanupRuntime("restart-before-start");
        log.info("Starting KSP Account Builder plugin");

        // Account Builder owns its own break schedule. The shared Microbot Break
        // Handler keeps its state in static fields and can remain BREAK_REQUESTED /
        // INGAME_BREAK_ACTIVE across hot reloads. KspAccountBuilderScript treats
        // that state as a hard pre-task gate, which can leave currentTask=null and
        // the overlay on IDLE forever even though the player is fully logged in.
        // Stop the standalone handler before starting the builder so its shutdown
        // resets/resumes the shared state and cannot block Account Builder startup.
        stopStandaloneBreakHandler();

        migrateSingleSkillTargetDefaults();
        overlayManager.add(overlay);

        randomEventSolver = new KspRandomEventSolver();
        Microbot.getBlockingEventManager().add(randomEventSolver);

        // Level-up Continue interfaces are intentionally not registered as a
        // blocking event. The next skilling interaction normally dismisses the
        // interface itself, so keep task execution running and let that action
        // advance immediately instead of spending a tick clicking Continue.

        runtimeActive = true;
        resetAntibanChanceDecision();
        script.run(config);
    }

    @Override
    protected synchronized void shutDown()
    {
        cleanupRuntime("plugin-stop");
    }

    public synchronized void prepareHotUnload()
    {
        cleanupRuntime("hot-unload");
    }

    public void afterHotReload()
    {
        log.info("KSP Account Builder hot reload completed | version={}", VERSION);
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!runtimeActive || script == null || config == null)
        {
            return;
        }

        String taskName = script.getCurrentTaskName();
        boolean useAntiban = config.useAntiban();
        int chance = Math.max(0, Math.min(100, config.antibanChancePercent()));
        String decisionKey = taskName + '|' + useAntiban + '|' + chance;

        if (!decisionKey.equals(antibanDecisionKey))
        {
            antibanDecisionKey = decisionKey;
            antibanEnabledForCurrentTask = rollAntibanForTask(taskName, useAntiban, chance);
            nextSkillingMouseAntibanAtMillis = 0L;
            log.info("Account Builder antiban decision | task={} enabled={} chance={}%%",
                    taskName, antibanEnabledForCurrentTask, chance);
        }

        // KspAccountBuilderScript applies the activity-specific antiban template
        // whenever a task starts. This master flag enforces the user's percentage
        // decision for the complete task and prevents a skipped roll from becoming
        // active merely because the task-specific template was reapplied.
        Rs2AntibanSettings.antibanEnabled = antibanEnabledForCurrentTask;
        handleSkillingMouseAntiban(taskName);
    }

    private boolean rollAntibanForTask(String taskName, boolean useAntiban, int chance)
    {
        if (!useAntiban || chance <= 0 || taskName == null || "IDLE".equals(taskName))
        {
            return false;
        }
        if (chance >= 100)
        {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(100) < chance;
    }

    private void handleSkillingMouseAntiban(String taskName)
    {
        if (!antibanEnabledForCurrentTask || !isSkillingTask(taskName) || !Microbot.isLoggedIn())
        {
            nextSkillingMouseAntibanAtMillis = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (nextSkillingMouseAntibanAtMillis <= 0L)
        {
            scheduleNextSkillingMouseAntiban(now);
            return;
        }
        if (now < nextSkillingMouseAntibanAtMillis)
        {
            return;
        }

        // Only move the mouse while the player is already occupied with a skill
        // action. This avoids stealing the cursor while a new interaction or walk
        // is about to be issued by the task script.
        if (Rs2Player.isMoving() || (!Rs2Player.isAnimating() && !Rs2Player.isInteracting()))
        {
            return;
        }

        try
        {
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll < SKILLING_MOUSE_OFFSCREEN_CHANCE_PERCENT)
            {
                Rs2Antiban.moveMouseOffScreen();
                log.debug("Account Builder skilling antiban moved mouse off-screen | task={}", taskName);
            }
            else
            {
                Rs2Antiban.moveMouseRandomly();
                log.debug("Account Builder skilling antiban moved mouse randomly | task={}", taskName);
            }
        }
        catch (Exception ex)
        {
            log.debug("Account Builder skilling mouse antiban skipped | task={} reason={}", taskName, ex.getMessage());
        }
        finally
        {
            scheduleNextSkillingMouseAntiban(now);
        }
    }

    private void scheduleNextSkillingMouseAntiban(long now)
    {
        long delay = ThreadLocalRandom.current().nextLong(
                SKILLING_MOUSE_ANTIBAN_MIN_DELAY_MS,
                SKILLING_MOUSE_ANTIBAN_MAX_DELAY_MS + 1L);
        nextSkillingMouseAntibanAtMillis = now + delay;
    }

    private boolean isSkillingTask(String taskName)
    {
        if (taskName == null)
        {
            return false;
        }
        switch (taskName)
        {
            case "RUNE_ESSENCE":
            case "MINING":
            case "WOODCUTTING":
            case "FIREMAKING":
            case "FISHING":
            case "COOKING":
            case "CRAFTING":
            case "MELEE":
            case "SMITHING":
            case "SMELTING":
                return true;
            default:
                return false;
        }
    }

    private void resetAntibanChanceDecision()
    {
        antibanDecisionKey = null;
        antibanEnabledForCurrentTask = false;
        nextSkillingMouseAntibanAtMillis = 0L;
        Rs2AntibanSettings.antibanEnabled = false;
    }

    private void stopStandaloneBreakHandler()
    {
        try
        {
            Plugin breakHandler = Microbot.getPlugin(BreakHandlerPlugin.class.getName());
            if (breakHandler == null || !Microbot.isPluginEnabled(breakHandler))
            {
                return;
            }

            boolean stopped = Microbot.stopPlugin(breakHandler);
            log.info("Stopped standalone Break Handler before Account Builder start | stopped={}", stopped);
        }
        catch (Exception ex)
        {
            log.warn("Unable to stop standalone Break Handler before Account Builder start", ex);
        }
    }

    private void cleanupRuntime(String reason)
    {
        if (!runtimeActive && randomEventSolver == null)
        {
            // Still stop the script in case Source Loader calls hot-unload before
            // the normal plugin lifecycle flag was established.
            if (script != null)
            {
                script.shutdown();
            }
            resetAntibanChanceDecision();
            return;
        }

        log.info("Stopping KSP Account Builder runtime | reason={}", reason);
        runtimeActive = false;

        if (script != null)
        {
            script.shutdown();
        }

        resetAntibanChanceDecision();
        KspWalkerGuard.clearActiveWalker("ksp_account_builder_" + reason);
        KspWalkerGuard.clear("Woodcutting:target-area");

        if (randomEventSolver != null)
        {
            Microbot.getBlockingEventManager().remove(randomEventSolver);
            randomEventSolver = null;
        }

        if (overlayManager != null && overlay != null)
        {
            overlayManager.remove(overlay);
        }
    }

    private void migrateSingleSkillTargetDefaults()
    {
        String[] progressiveKeys = {
                "singleSkillMiningTarget",
                "singleSkillWoodcuttingTarget",
                "singleSkillFishingTarget",
                "singleSkillSmithingTarget",
                "singleSkillSmeltingTarget"
        };

        for (String key : progressiveKeys)
        {
            if ("AUTOMATIC".equals(configManager.getConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, key)))
            {
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, key, "PROGRESSIVE");
            }
        }
    }
}
