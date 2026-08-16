package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
    name = PluginConstants.KSP + "AIO Factory",
    description = "Membership-aware processing factory with automatic GE trading, profit filtering, buy-limit tracking and factory-aware anti-ban",
    tags = {"processing", "grand exchange", "money making", "bank standing", "herblore", "antiban"},
    authors = {"KSP"},
    version = F2PProcessingFactoryPlugin.VERSION,
    minClientVersion = "2.6.16",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class F2PProcessingFactoryPlugin extends Plugin
{
    public static final String VERSION = "1.0.43";

    @Inject
    private F2PProcessingFactoryConfig config;

    @Inject
    private F2PProcessingFactoryScript script;

    @Inject
    private F2PProcessingFactoryOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ConfigManager configManager;

    @Inject
    private Gson gson;

    @Provides
    F2PProcessingFactoryConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(F2PProcessingFactoryConfig.class);
    }

    public F2PProcessingFactoryScript getScript()
    {
        return script;
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        script.run(config, configManager, gson);
        log.info("KSP AIO Factory v{} started", VERSION);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
        log.info("KSP AIO Factory stopped");
    }
}
