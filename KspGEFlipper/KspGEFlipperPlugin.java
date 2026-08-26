package net.runelite.client.plugins.microbot.kspgeflipper;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "GE Flipper",
        description = "Automatic tax-aware Grand Exchange flipping using live OSRS Wiki prices",
        tags = {"ge", "flip", "flipping", "money", "ksp", "microbot"},
        version = KspGEFlipperPlugin.VERSION,
        minClientVersion = "0.0.3",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspGEFlipperPlugin extends Plugin {
    public static final String VERSION = "0.1.1";

    @Inject private KspGEFlipperConfig config;
    @Inject private KspGEFlipperScript script;
    @Inject private KspGEFlipperOverlay overlay;
    @Inject private OverlayManager overlayManager;

    @Provides
    KspGEFlipperConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(KspGEFlipperConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
