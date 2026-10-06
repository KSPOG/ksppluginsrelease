package net.runelite.client.plugins.microbot.kspsupport;

import javax.inject.Inject;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.LinkBrowser;

/**
 * Hidden always-on listener for shared KSP support features.
 *
 * Besides handling the Support button, this plugin owns the centralized runtime
 * diagnostics monitor so every source-loaded KSP plugin gets consistent lifecycle,
 * state and health logging without adding noisy per-tick logging to each script.
 */
@PluginDescriptor(
        name = "KSP Support",
        description = "Shared KSP support-link and runtime diagnostics handler.",
        tags = {"ksp", "support", "discord", "debug"},
        authors = {"KSP"},
        version = "1.0.1",
        enabledByDefault = true,
        alwaysOn = true,
        hidden = true,
        isExternal = true
)
public class KspSupportPlugin extends Plugin
{
    @Inject private PluginManager pluginManager;
    private KspRuntimeDebugMonitor debugMonitor;

    @Override
    protected void startUp()
    {
        stopDebugMonitor();
        debugMonitor = new KspRuntimeDebugMonitor(pluginManager);
        debugMonitor.start();
    }

    @Override
    protected void shutDown()
    {
        stopDebugMonitor();
    }

    /** Source Loader hot-refresh hook. */
    public void prepareHotUnload()
    {
        stopDebugMonitor();
    }

    private synchronized void stopDebugMonitor()
    {
        if (debugMonitor == null) return;
        debugMonitor.stop();
        debugMonitor = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (event != null && KspSupportConfig.SUPPORT_KEY.equals(event.getKey()))
        {
            LinkBrowser.browse(KspSupportConfig.SUPPORT_URL);
        }
    }
}
