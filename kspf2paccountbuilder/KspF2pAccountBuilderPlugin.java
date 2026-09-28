package net.runelite.client.plugins.microbot.kspf2paccountbuilder;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

@PluginDescriptor(
        name = PluginConstants.KSP + "F2P Account Builder",
        description = "Autonomous F2P progression, questing, restocking, selling and upgrades.",
        tags = {"ksp", "f2p", "account", "builder", "quest", "progression", "grand exchange"},
        authors = {"KSP"},
        version = KspF2pAccountBuilderPlugin.VERSION,
        minClientVersion = "2.1.32",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspF2pAccountBuilderPlugin extends Plugin
{
    public static final String VERSION = "0.1.0";

    @Inject private KspF2pAccountBuilderConfig config;
    @Inject private KspF2pAccountBuilderScript script;
    @Inject private KspF2pAccountBuilderOverlay overlay;
    @Inject private OverlayManager overlayManager;

    private Instant started;

    @Override
    protected void startUp()
    {
        started = Instant.now();
        if (config.showOverlay())
        {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
        started = null;
    }

    @Provides
    KspF2pAccountBuilderConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspF2pAccountBuilderConfig.class);
    }

    public KspF2pAccountBuilderScript getScript()
    {
        return script;
    }

    public Instant getStarted()
    {
        return started;
    }
}
