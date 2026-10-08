package net.runelite.client.plugins.microbot.kspmugfiller;

import net.runelite.client.plugins.microbot.kspsupport.KspBreakService;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "Mug Filler",
        description = "Banks for empty beer glasses and fills them one-by-one from the beer barrel at 3099,3281.",
        tags = {"ksp", "mug", "beer", "glass", "barrel", "bank", "f2p"},
        authors = {"KSP"},
        version = KspMugFillerPlugin.VERSION,
        minClientVersion = "2.6.19",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspMugFillerPlugin extends Plugin
{
    private final KspBreakService breaks = new KspBreakService();

    @Inject private KspMugFillerConfig config;
    @Provides KspMugFillerConfig provideConfig(ConfigManager manager) { return manager.getConfig(KspMugFillerConfig.class); }

    public static final String VERSION = "0.0.3";

    @Inject
    private KspMugFillerScript script;

    @Inject
    private KspMugFillerOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Override
    protected void startUp()
    {
        breaks.start(config);
        overlayManager.add(overlay);
        script.run();
    }

    @Override
    protected void shutDown()
    {
        try
        {
            script.shutdown();
            overlayManager.remove(overlay);
        }
        finally
        {
            breaks.shutdown();
        }
    }

    KspMugFillerScript getScript()
    {
        return script;
    }
    public void prepareHotUnload() throws Exception
    {
        shutDown();
    }
}
