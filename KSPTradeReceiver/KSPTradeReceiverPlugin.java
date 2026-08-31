package net.runelite.client.plugins.microbot.KSPTradeReceiver;

import com.google.inject.Provides;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "Trade Receiver",
        description = "Localhost-coordinated mule receiver with automatic account-name discovery, queued worker trades, banking and idle logout.",
        tags = {"trade", "receiver", "mule", "localhost", "bank", "microbot", "ksp"},
        version = KSPTradeReceiverPlugin.VERSION,
        minClientVersion = "2.6.18",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KSPTradeReceiverPlugin extends Plugin
{
    public static final String VERSION = "0.2.2";

    @Inject private KSPTradeReceiverConfig config;
    @Inject private KSPTradeReceiverScript script;
    @Inject private KspLocalMuleCoordinatorService muleCoordinator;
    @Inject private KSPTradeReceiverOverlay overlay;
    @Inject private OverlayManager overlayManager;

    @Provides
    KSPTradeReceiverConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KSPTradeReceiverConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        muleCoordinator.start(config);
        script.run(config);
    }

    @Override
    protected void shutDown()
    {
        muleCoordinator.shutdown();
        script.shutdown();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        script.onChatMessage(event);
    }
}
