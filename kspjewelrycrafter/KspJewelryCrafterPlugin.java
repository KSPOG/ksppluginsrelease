package net.runelite.client.plugins.microbot.kspjewelrycrafter;

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
    name = PluginConstants.KSP + "Jewelry Crafter",
    description = "Profit-aware jewellery crafting with F2P/P2P eligibility and automatic GE restocking.",
    tags = {"ksp", "crafting", "jewelry", "jewellery", "grand exchange", "profit", "f2p", "members"},
    authors = {"KSP"},
    version = KspJewelryCrafterPlugin.VERSION,
    minClientVersion = "1.9.8",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class KspJewelryCrafterPlugin extends Plugin
{
    private final KspBreakService breaks = new KspBreakService();

    public static final String VERSION = "0.1.30";

    @Inject private KspJewelryCrafterConfig config;
    @Inject private KspJewelryCrafterScript script;
    @Inject private KspJewelryCrafterOverlay overlay;
    @Inject private OverlayManager overlayManager;
    private final KspMuleWorkerService muleService = new KspMuleWorkerService("Jewelry Crafter");

    @Provides
    KspJewelryCrafterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspJewelryCrafterConfig.class);
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
