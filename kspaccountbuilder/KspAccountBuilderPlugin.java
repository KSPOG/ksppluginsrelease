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
    public static final String VERSION = "1.9.19";

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
    private String antibanStateKey;

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
        resetAntibanState();
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
        return config != null && config.useAntiban();
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
        String stateKey = taskName + '|' + useAntiban + '|' + chance;

        if (!stateKey.equals(antibanStateKey))
        {
            antibanStateKey = stateKey;
            skillingAntiban.reset();
            log.info("Account Builder antiban state | task={} enabled={} actionChance={}%%",
                    taskName, useAntiban, chance);
        }

        // The master toggle controls whether antiban is active. The percentage is
        // intentionally applied to each eligible custom antiban opportunity rather
        // than rolling once and disabling antiban for an entire task.
        Rs2AntibanSettings.antibanEnabled = useAntiban;
        skillingAntiban.tick(taskName, useAntiban, chance);
    }

    private void resetAntibanState()
    {
        antibanStateKey = null;
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
            resetAntibanState();
            return;
        }

        log.info("Stopping KSP Account Builder runtime | reason={}", reason);
        runtimeActive = false;

        if (script != null)
        {
            script.shutdown();
        }

        resetAntibanState();
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
