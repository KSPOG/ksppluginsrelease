package net.runelite.client.plugins.microbot.kspwinegrabber;

import com.google.inject.Provides;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.kspsupport.KspBreakService;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = PluginConstants.KSP + "Wine of Zamorak Grabber",
        description = "Walks to the upstairs Chaos Temple wine, telegrabs, and walks back to bank.",
        tags = {"ksp", "wine of zamorak", "telekinetic grab", "magic", "f2p", "banking"},
        authors = {"KSP"},
        version = KspWineGrabberPlugin.VERSION,
        minClientVersion = "2.6.19",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspWineGrabberPlugin extends Plugin
{
    public static final String VERSION = "0.1.1";

    @Inject private PluginManager pluginManager;
    @Inject private OverlayManager overlayManager;
    @Inject private KspWineGrabberScript script;
    @Inject private KspWineGrabberOverlay overlay;
    @Inject private KspWineGrabberConfig config;

    private final KspBreakService breaks = new KspBreakService();
    private final AtomicBoolean failureReported = new AtomicBoolean();

    @Provides
    KspWineGrabberConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(KspWineGrabberConfig.class);
    }

    @Override
    protected void startUp()
    {
        failureReported.set(false);
        breaks.start(config);
        overlayManager.add(overlay);
        script.run();
    }

    @Override
    protected void shutDown()
    {
        try
        {
            script.shutdown();
            overlayManager.remove(overlay);
        }
        finally
        {
            breaks.shutdown();
        }
    }

    /**
     * Immediately stop automation before posting the required error dialog.
     * The modal dialog is always shown on the Swing event dispatch thread.
     */
    public void stopWithError(String reason)
    {
        if (!failureReported.compareAndSet(false, true))
        {
            return;
        }

        script.stopNow(reason);
        SwingUtilities.invokeLater(() ->
        {
            try
            {
                pluginManager.setPluginEnabled(this, false);
                if (pluginManager.isActive(this))
                {
                    pluginManager.stopPlugin(this);
                }
            }
            catch (Exception ex)
            {
                // The script was already stopped; the error must still reach the user.
                net.runelite.client.plugins.microbot.Microbot.log(
                        "KSP Wine Grabber could not disable the plugin: " + ex.getMessage());
            }
            JOptionPane.showMessageDialog(
                    null,
                    reason,
                    "KSP Wine of Zamorak Grabber - Stopped",
                    JOptionPane.ERROR_MESSAGE);
        });
    }

    KspWineGrabberScript getScript()
    {
        return script;
    }

    public void prepareHotUnload() throws Exception
    {
        shutDown();
    }
}
