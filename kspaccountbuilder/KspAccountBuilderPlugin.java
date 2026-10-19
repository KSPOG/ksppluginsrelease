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
    public static final String VERSION = "1.9.30";

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
        return -1L;
    }

    public String getAntibanActivity()
    {
        if (config == null || !config.useAntiban())
        {
            return "Disabled";
        }
        return "Task profile active; custom mouse OFF";
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

        // The extra GameTick mouse controller has been suspended because it
        // races banking/GE/skilling input. The builder's native antiban profiles
        // and break handling remain configured by KspAccountBuilderScript.
        // Never override global Rs2AntibanSettings on every GameTick:
        // that fights script-specific state changes during task handoffs.
        skillingAntiban.reset();
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
        // Disable callbacks first, even if child shutdown fails.
        boolean wasActive = runtimeActive;
        runtimeActive = false;
        if (wasActive || randomEventSolver != null)
        {
            log.info("Stopping KSP Account Builder runtime | reason={}", reason);
        }

        try
        {
            if (script != null)
            {
                script.shutdown();
            }
        }
        catch (Exception ex)
        {
            log.error("Account Builder script shutdown failed | reason={}", reason, ex);
        }
        finally
        {
            try { resetAntibanState(); }
            catch (Exception ex) { log.warn("Unable to reset antiban during shutdown", ex); }

            try
            {
                KspWalkerGuard.clearActiveWalker("ksp_account_builder_" + reason);
                KspWalkerGuard.clear("Woodcutting:target-area");
            }
            catch (Exception ex) { log.warn("Unable to clear walker during shutdown", ex); }

            KspRandomEventSolver solver = randomEventSolver;
            randomEventSolver = null;
            if (solver != null)
            {
                try { Microbot.getBlockingEventManager().remove(solver); }
                catch (Exception ex) { log.warn("Unable to remove random event solver", ex); }
            }
            if (overlayManager != null && overlay != null)
            {
                try { overlayManager.remove(overlay); }
                catch (Exception ex) { log.warn("Unable to remove Account Builder overlay", ex); }
            }
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
