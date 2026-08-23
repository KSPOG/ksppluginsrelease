package net.runelite.client.plugins.microbot.KSPAutoMiner;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>KSP</font>] Auto Miner",
        description = "Progressive mining with banking or dropping",
        tags = {"mining", "microbot", "ksp"},
        version = KSPAutoMinerPlugin.version,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KSPAutoMinerPlugin extends Plugin {
    public static final String version = "0.1.4";

    @Inject
    private KSPAutoMinerConfig config;

    @Inject
    private KSPAutoMinerScript script;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private KSPAutoMinerOverlay overlay;

    @Provides
    KSPAutoMinerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KSPAutoMinerConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
