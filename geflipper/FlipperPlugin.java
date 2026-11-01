package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

@PluginDescriptor(
    name = PluginConstants.KSP + "Flipper",
    description = "Follows Flipping Copilot's highlighted Grand Exchange actions",
    tags = {"flip", "flipping", "copilot", "ge", "grand-exchange"},
    authors = {"Choken", "afss0"},
    version = FlipperPlugin.VERSION,
    minClientVersion = "2.6.16",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public final class FlipperPlugin extends Plugin
{
    public static final String VERSION = "1.0.5";

    @Inject
    private FlipperScript script;

    @Provides
    FlipperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FlipperConfig.class);
    }

    @Override
    protected void startUp()
    {
        script.run();
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
    }
}
