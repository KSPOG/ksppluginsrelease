package net.runelite.client.plugins.microbot.kspsupport;

import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.LinkBrowser;

/**
 * Hidden always-on listener for the shared Support config button.
 * Keeping the listener independent means the Support button also works while the
 * plugin whose configuration is being viewed is disabled.
 */
@PluginDescriptor(
        name = "KSP Support",
        description = "Shared KSP Plugins support-link handler.",
        tags = {"ksp", "support", "discord"},
        authors = {"KSP"},
        version = "1.0.0",
        enabledByDefault = true,
        alwaysOn = true,
        hidden = true,
        isExternal = true
)
public class KspSupportPlugin extends Plugin
{
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (event != null && KspSupportConfig.SUPPORT_KEY.equals(event.getKey()))
        {
            LinkBrowser.browse(KspSupportConfig.SUPPORT_URL);
        }
    }
}
