package net.runelite.client.plugins.microbot.KSPGELooter;

import net.runelite.client.plugins.microbot.kspsupport.KspBreakService;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.kspmule.KspMuleWorkerService;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "GE Looter",
        description = "Value-based Grand Exchange looter with optional profitable High Alchemy",
        tags = {"looter", "loot", "ge", "alchemy", "microbot", "ksp"},
        version = KSPGELooterPlugin.VERSION,
        minClientVersion = "0.0.3",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KSPGELooterPlugin extends Plugin
{
    private final KspBreakService breaks = new KspBreakService();

    public static final String VERSION = "0.1.15";

    @Inject private KSPGELooterConfig config;
    @Inject private KSPGELooterScript script;
    @Inject private KSPGELooterOverlay overlay;
    @Inject private OverlayManager overlayManager;
    private final KspMuleWorkerService muleService = new KspMuleWorkerService("GE Looter");

    @Provides
    KSPGELooterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KSPGELooterConfig.class);
    }

    @Override
    protected void startUp()
    {
        breaks.start(config);
        muleService.start(config);
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        try
        {
            muleService.shutdown();
            script.shutdown();
            overlayManager.remove(overlay);
        }
        finally
        {
            breaks.shutdown();
        }
    }
    public void prepareHotUnload() throws Exception
    {
        shutDown();
    }
}
