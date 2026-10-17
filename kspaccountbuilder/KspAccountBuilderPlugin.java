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
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
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
    public static final String VERSION = "1.9.15";

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
    private final KspAccountBuilderSkillingAntiban skillingAntiban = new KspAccountBuilderSkillingAntiban();
    private boolean runtimeActive;
    private String antibanDecisionKey;
    private boolean antibanEnabledForCurrentTask;

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

        stopStandaloneBreakHandler();
        migrateSingleSkillTargetDefaults();
        overlayManager.add(overlay);

        randomEventSolver = new KspRandomEventSolver();
        Microbot.getBlockingEventManager().add(randomEventSolver);

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

    public boolean isAntibanEnabledForCurrentTask()
    {
        return antibanEnabledForCurrentTask;
    }

    public int getAntibanChancePercent()
    {
        return config == null ? 0 : Math.max(0, Math.min(100, config.antibanChancePercent()));
    }

    public long getAntibanTimeUntilNextActionSeconds()
    {
        return skillingAntiban.getTimeUntilNextActionSeconds();
    }

    public String getAntibanActivity()
    {
        if (config == null || !config.useAntiban())
        {
            return "Disabled";
        }
        if (!antibanEnabledForCurrentTask)
        {
            return "Skipped by chance roll";
        }
        return skillingAntiban.getActivity();
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
            skillingAntiban.reset();
            log.info("Account Builder antiban decision | task={} enabled={} chance={}%%",
                    taskName, antibanEnabledForCurrentTask, chance);
        }

        Rs2AntibanSettings.antibanEnabled = antibanEnabledForCurrentTask;
        skillingAntiban.tick(taskName, antibanEnabledForCurrentTask);
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

    private void resetAntibanChanceDecision()
    {
        antibanDecisionKey = null;
        antibanEnabledForCurrentTask = false;
        skillingAntiban.reset();
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
