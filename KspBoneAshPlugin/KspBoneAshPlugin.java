package net.runelite.client.plugins.microbot.KspBoneAshPlugin;

import com.google.inject.Provides;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "Bone & Ash Processor",
        description = "Automatically buries configured bones or scatters configured ashes using bank withdraw-all",
        tags = {"ksp", "prayer", "bones", "ashes", "bury", "scatter", "bank"},
        version = KspBoneAshPlugin.VERSION,
        cardUrl = "",
        iconUrl = "",
        minClientVersion = "2.6.19",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspBoneAshPlugin extends Plugin
{
    public static final String VERSION = "0.0.1";

    @Inject
    private KspBoneAshConfig config;

    @Inject
    private KspBoneAshScript script;

    @Provides
    KspBoneAshConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspBoneAshConfig.class);
    }

    @Override
    protected void startUp()
    {
        script.run(config, this);
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == InventoryID.INV)
        {
            script.onInventoryChanged();
        }
    }
}
