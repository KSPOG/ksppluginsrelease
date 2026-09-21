package net.runelite.client.plugins.microbot.kspbonestobananas;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>KSP</font>] Bones to Bananas",
        description = "Profit-aware Bones to Bananas with GE restocking, equipped-staff detection and anti-ban.",
        tags = {"bones to bananas", "magic", "grand exchange", "money making", "profit", "antiban"},
        authors = {"KSP"},
        version = KspBonesToBananasPlugin.VERSION,
        minClientVersion = "2.6.19",
        enabledByDefault = false,
        isExternal = true
)
@Slf4j
public class KspBonesToBananasPlugin extends Plugin
{
    public static final String VERSION = "0.0.1";

    @Inject private KspBonesToBananasConfig config;
    @Inject private KspBonesToBananasScript script;
    @Inject private KspBonesToBananasOverlay overlay;
    @Inject private OverlayManager overlayManager;

    @Provides
    KspBonesToBananasConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(KspBonesToBananasConfig.class);
    }

    KspBonesToBananasScript getScript() { return script; }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        script.run(config);
        log.info("KSP Bones to Bananas v{} started", VERSION);
    }

    @Override
    protected void shutDown()
    {
        script.stopScript();
        overlayManager.remove(overlay);
        log.info("KSP Bones to Bananas stopped");
    }
}
