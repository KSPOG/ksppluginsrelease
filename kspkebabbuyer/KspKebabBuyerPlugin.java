package net.runelite.client.plugins.microbot.kspkebabbuyer;

import net.runelite.client.plugins.microbot.kspsupport.KspBreakService;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>KSP</font>] Kebab Buyer",
        description = "Buys kebabs from Karim in Al Kharid and banks them automatically.",
        tags = {"ksp", "kebab", "karim", "al kharid", "money making", "f2p"},
        authors = {"KSP"},
        version = KspKebabBuyerPlugin.VERSION,
        minClientVersion = "2.6.19",
        enabledByDefault = false,
        isExternal = true
)
public class KspKebabBuyerPlugin extends Plugin
{
    private final KspBreakService breaks = new KspBreakService();

    @Inject private KspKebabBuyerConfig config;
    @Provides KspKebabBuyerConfig provideConfig(ConfigManager manager) { return manager.getConfig(KspKebabBuyerConfig.class); }

    public static final String VERSION = "0.0.7";

    @Inject
    private KspKebabBuyerScript script;

    @Inject
    private KspKebabBuyerOverlay overlay;

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

    KspKebabBuyerScript getScript()
    {
        return script;
    }
    public void prepareHotUnload() throws Exception
    {
        shutDown();
    }
}
