package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import com.google.inject.Provides;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "High Alch Trader",
        description = "Market-aware High Alchemy trader with automatic F2P/members candidate selection and Grand Exchange restocking.",
        tags = {"ksp", "f2p", "members", "highalch", "alchemy", "magic", "profit", "grandexchange", "ge"},
        authors = {"KSP"},
        version = KspF2PHighAlchTraderPlugin.VERSION,
        minClientVersion = "2.6.18",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspF2PHighAlchTraderPlugin extends Plugin
{
    public static final String VERSION = "0.2.9";

    @Inject
    private KspF2PHighAlchTraderConfig config;

    @Inject
    private KspF2PHighAlchTraderScript script;

    @Inject
    private KspF2PHighAlchTraderOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClientThread clientThread;

    private KspHighAlchMarketCache marketCache;

    @Provides
    KspF2PHighAlchTraderConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspF2PHighAlchTraderConfig.class);
    }

    @Override
    protected void startUp()
    {
        // Source-loaded plugins start on Swing/EDT. RuneLite ItemManager price reads reach
        // client item definitions, so prime the fallback on ClientThread before the trader
        // begins scanning and can fall through to Microbot's per-item network path.
        KspHighAlchMarketCache cache = new KspHighAlchMarketCache(itemManager, clientThread);
        marketCache = cache;
        overlayManager.add(overlay);

        cache.start(config, () ->
        {
            // Ignore a delayed callback from a cache that was already shut down/replaced.
            if (marketCache == cache)
            {
                script.run(config);
            }
        });
    }

    @Override
    protected void shutDown()
    {
        KspHighAlchMarketCache cache = marketCache;
        marketCache = null;
        if (cache != null)
        {
            cache.close();
        }

        script.shutdown();
        overlayManager.remove(overlay);
    }
}
