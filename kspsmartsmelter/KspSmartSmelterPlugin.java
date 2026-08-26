package net.runelite.client.plugins.microbot.kspsmartsmelter;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@PluginDescriptor(
        name = "KSP Smart Smelter",
        description = "Automatically selects profitable furnace smelting and cannonball processing routes.",
        authors = {"KSP"},
        version = KspSmartSmelterPlugin.VERSION,
        minClientVersion = "1.9.9.1",
        tags = {"ksp", "smithing", "smelting", "money making", "grand exchange"},
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspSmartSmelterPlugin extends Plugin {
    public static final String VERSION = "0.0.3";

    @Inject
    private KspSmartSmelterConfig config;

    @Inject
    private KspSmartSmelterScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KspSmartSmelterOverlay overlay;

    @Provides
    KspSmartSmelterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KspSmartSmelterConfig.class);
    }

    @Provides
    @Singleton
    KspSmartSmelterScript provideScript(KspSmartSmelterConfig config) {
        return new KspSmartSmelterScript(this, config);
    }

    @Override
    protected void startUp() {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        overlayManager.add(overlay);
        script.run();
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
