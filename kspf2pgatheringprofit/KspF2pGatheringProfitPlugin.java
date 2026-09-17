package net.runelite.client.plugins.microbot.kspf2pgatheringprofit;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "F2P Gathering Profit",
        description = "F2P Mining, Woodcutting and Fishing profit/XP optimizer.",
        tags = {"ksp", "f2p", "skiller", "profit", "mining", "woodcutting", "fishing"},
        version = KspF2pGatheringProfitPlugin.VERSION,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspF2pGatheringProfitPlugin extends Plugin
{
    public static final String VERSION = "0.0.3";

    @Inject private KspF2pGatheringProfitConfig config;
    @Inject private KspF2pGatheringProfitScript script;
    @Inject private KspF2pGatheringProfitOverlay overlay;
    @Inject private OverlayManager overlayManager;

    private Instant started;

    @Override
    protected void startUp()
    {
        started = Instant.now();
        if (overlayManager != null && overlay != null)
        {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        if (script != null)
        {
            script.shutdown();
        }
        if (overlayManager != null && overlay != null)
        {
            overlayManager.remove(overlay);
        }
        started = null;
    }

    @Provides
    KspF2pGatheringProfitConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspF2pGatheringProfitConfig.class);
    }

    public KspF2pGatheringProfitScript getScript()
    {
        return script;
    }

    public Instant getStarted()
    {
        return started;
    }
}
