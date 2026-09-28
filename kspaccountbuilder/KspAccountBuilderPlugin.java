package net.runelite.client.plugins.microbot.kspaccountbuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.randomevents.KspRandomEventSolver;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "Account Builder",
        description = "Will automatically build a F2P Main",
        tags = {"microbot", "ksp", "account", "builder"},
        authors = {"KSP"},
        version = KspAccountBuilderPlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = true,
        isExternal = true
)
@Slf4j
@SuppressWarnings("unused") // Loaded dynamically by the hub build/plugin discovery process.
public class KspAccountBuilderPlugin extends Plugin
{
    public static final String VERSION = "1.5.155";

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

    @Provides
    KspAccountBuilderConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspAccountBuilderConfig.class);
    }

    @Override
    protected void startUp()
    {
        log.info("Starting KSP Account Builder plugin");
        migrateSingleSkillTargetDefaults();
        overlayManager.add(overlay);
        randomEventSolver = new KspRandomEventSolver();
        Microbot.getBlockingEventManager().add(randomEventSolver);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        log.info("Stopping KSP Account Builder plugin");
        script.shutdown();
        if (randomEventSolver != null)
        {
            Microbot.getBlockingEventManager().remove(randomEventSolver);
            randomEventSolver = null;
        }
        overlayManager.remove(overlay);
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
