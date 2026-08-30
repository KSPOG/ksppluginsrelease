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
    public static final String VERSION = "0.2.10";

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
    private KspRuneLiteMarketBackup runeLiteBackup;

    @Provides
    KspF2PHighAlchTraderConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspF2PHighAlchTraderConfig.class);
    }

    @Override
    protected void startUp()
    {
        // Source-loaded plugins start on Swing/EDT. Prime both fallback layers on
        // RuneLite's client thread before allowing the trader loop to begin:
        // 1) local RuneLite guide prices -> Microbot price cache
        // 2) local ItemComposition/ItemStats -> Microbot mapping/GE-limit cache
        KspHighAlchMarketCache cache = new KspHighAlchMarketCache(itemManager, clientThread);
        KspRuneLiteMarketBackup backup = new KspRuneLiteMarketBackup(itemManager, clientThread);
        marketCache = cache;
        runeLiteBackup = backup;
        overlayManager.add(overlay);

        cache.start(config, () ->
        {
            if (marketCache != cache || runeLiteBackup != backup)
            {
                return;
            }

            backup.start(config, () ->
            {
                // Ignore delayed callbacks from a cache that was already shut down/replaced.
                if (marketCache == cache && runeLiteBackup == backup)
                {
                    script.run(config);
                }
            });
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

        KspRuneLiteMarketBackup backup = runeLiteBackup;
        runeLiteBackup = null;
        if (backup != null)
        {
            backup.close();
        }

        script.shutdown();
        overlayManager.remove(overlay);
    }
}
