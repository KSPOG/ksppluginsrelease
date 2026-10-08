package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import net.runelite.client.plugins.microbot.kspsupport.KspBreakService;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.kspmule.KspMuleWorkerService;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;

@PluginDescriptor(
    name = PluginConstants.KSP + "Smart Superheat",
    description = "Profit-aware Superheat Item with Jewellery-style Grand Exchange restocking",
    tags = {"superheat", "smithing", "magic", "grand exchange", "money making", "profit"},
    authors = {"KSP"}, version = KspSmartSuperheatPlugin.VERSION, minClientVersion = "2.6.16",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED, isExternal = PluginConstants.IS_EXTERNAL)
@Slf4j
public class KspSmartSuperheatPlugin extends Plugin
{
    private final KspBreakService breaks = new KspBreakService();

    public static final String VERSION = "0.1.8";
    @Inject private KspSmartSuperheatConfig config;
    @Inject private KspSmartSuperheatScript script;
    @Inject private KspSmartSuperheatOverlay overlay;
    @Inject private OverlayManager overlays;
    private final KspMuleWorkerService muleService = new KspMuleWorkerService("Smart Superheat");

    @Provides KspSmartSuperheatConfig provideConfig(ConfigManager manager) { return manager.getConfig(KspSmartSuperheatConfig.class); }
    KspSmartSuperheatScript getScript() { return script; }

    @Override protected void startUp() {
        breaks.start(config); muleService.start(config); overlays.add(overlay); script.run(config); log.info("KSP Smart Superheat v{} started", VERSION); }
    @Override protected void shutDown() {
        try
        {
            muleService.shutdown();
            script.stopScript();
            overlays.remove(overlay);
            log.info("KSP Smart Superheat stopped");
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
