package net.runelite.client.plugins.microbot.kspautorun;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "KSP Auto Run",
        description = "Automatically enables Run at a configurable energy threshold using widget invokes instead of natural clicks.",
        tags = {"ksp", "run", "energy", "movement", "invoke", "microbot"},
        version = KspAutoRunPlugin.VERSION,
        minClientVersion = "2.6.16",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspAutoRunPlugin extends Plugin
{
    public static final String VERSION = "1.0.0";

    @Inject
    private KspAutoRunConfig config;

    @Inject
    private KspAutoRunScript script;

    @Override
    protected void startUp()
    {
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
    }

    @Provides
    KspAutoRunConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspAutoRunConfig.class);
    }
}
