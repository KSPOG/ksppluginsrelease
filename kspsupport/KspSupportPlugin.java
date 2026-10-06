package net.runelite.client.plugins.microbot.kspsupport;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.LinkBrowser;

/**
 * Hidden always-on listener for shared KSP support features.
 *
 * Besides handling the Support button, this plugin owns the centralized runtime
 * diagnostics monitor and its KSP-only toolbar console.
 */
@PluginDescriptor(
        name = "KSP Support",
        description = "Shared KSP support-link and runtime diagnostics handler.",
        tags = {"ksp", "support", "discord", "debug"},
        authors = {"KSP"},
        version = "1.1.0",
        enabledByDefault = true,
        alwaysOn = true,
        hidden = true,
        isExternal = true
)
public class KspSupportPlugin extends Plugin
{
    @Inject private PluginManager pluginManager;
    @Inject private ClientToolbar clientToolbar;

    private KspRuntimeDebugMonitor debugMonitor;
    private KspDebugPanel debugPanel;
    private NavigationButton debugNavigation;

    @Override
    protected void startUp()
    {
        stopDebugMonitor();
        removeDebugNavigation();

        debugPanel = new KspDebugPanel();
        debugNavigation = NavigationButton.builder()
                .tooltip("KSP Debug")
                .priority(99)
                .icon(createDebugIcon())
                .panel(debugPanel)
                .build();
        clientToolbar.addNavigation(debugNavigation);

        debugMonitor = new KspRuntimeDebugMonitor(pluginManager, debugPanel);
        debugMonitor.start();
    }

    @Override
    protected void shutDown()
    {
        stopDebugMonitor();
        removeDebugNavigation();
        debugPanel = null;
    }

    /** Source Loader hot-refresh hook. */
    public void prepareHotUnload()
    {
        stopDebugMonitor();
        removeDebugNavigation();
        debugPanel = null;
    }

    private synchronized void stopDebugMonitor()
    {
        if (debugMonitor == null) return;
        debugMonitor.stop();
        debugMonitor = null;
    }

    private synchronized void removeDebugNavigation()
    {
        if (debugNavigation == null) return;
        clientToolbar.removeNavigation(debugNavigation);
        debugNavigation = null;
    }

    private static BufferedImage createDebugIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(35, 35, 35, 255));
            g.fillRoundRect(0, 0, 16, 16, 4, 4);
            g.setColor(new Color(120, 220, 140));
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
            g.drawString("K", 5, 11);
        }
        finally
        {
            g.dispose();
        }
        return image;
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
