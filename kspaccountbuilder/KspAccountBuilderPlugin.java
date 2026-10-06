package net.runelite.client.plugins.microbot.kspaccountbuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspLevelUpDialogueEvent;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.randomevents.KspRandomEventSolver;
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
    public static final String VERSION = "1.9.0";

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
    private KspLevelUpDialogueEvent levelUpDialogueEvent;
    private boolean runtimeActive;

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

        migrateSingleSkillTargetDefaults();
        overlayManager.add(overlay);

        randomEventSolver = new KspRandomEventSolver();
        Microbot.getBlockingEventManager().add(randomEventSolver);

        levelUpDialogueEvent = new KspLevelUpDialogueEvent();
        Microbot.getBlockingEventManager().add(levelUpDialogueEvent);

        runtimeActive = true;
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

    private void cleanupRuntime(String reason)
    {
        if (!runtimeActive && randomEventSolver == null && levelUpDialogueEvent == null)
        {
            // Still stop the script in case Source Loader calls hot-unload before
            // the normal plugin lifecycle flag was established.
            if (script != null)
            {
                script.shutdown();
            }
            return;
        }

        log.info("Stopping KSP Account Builder runtime | reason={}", reason);
        runtimeActive = false;

        if (script != null)
        {
            script.shutdown();
        }

        KspWalkerGuard.clearActiveWalker("ksp_account_builder_" + reason);
        KspWalkerGuard.clear("Woodcutting:target-area");

        if (randomEventSolver != null)
        {
            Microbot.getBlockingEventManager().remove(randomEventSolver);
            randomEventSolver = null;
        }

        if (levelUpDialogueEvent != null)
        {
            Microbot.getBlockingEventManager().remove(levelUpDialogueEvent);
            levelUpDialogueEvent = null;
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
