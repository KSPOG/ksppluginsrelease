package net.runelite.client.plugins.microbot.kspseasonalevents;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = PluginConstants.KSP + "Seasonal Events",
        description = "Independent seasonal event workflows; Halloween 2026 staged wiki walkthrough",
        tags = {"ksp","holiday","seasonal","halloween","event","f2p"},
        authors = {"KSP"},
        version = KspSeasonalEventsPlugin.VERSION,
        minClientVersion = "2.6.19",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL)
public final class KspSeasonalEventsPlugin extends Plugin {
    public static final String VERSION = "0.2.7";
    @Inject private ClientToolbar toolbar;
    @Inject private OverlayManager overlays;
    @Inject private KspSeasonalEventsOverlay overlay;
    @Inject private KspSeasonalEventsConfig config;
    @Inject private KspHalloween2026Script script;
    private KspSeasonalEventsPanel panel;
    private NavigationButton navigation;

    @Provides KspSeasonalEventsConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(KspSeasonalEventsConfig.class);
    }

    @Override protected void startUp() {
        // Loading the plugin never starts an event or puts an overlay on screen.
        panel = new KspSeasonalEventsPanel(this, script);
        navigation = NavigationButton.builder().tooltip("KSP Seasonal Events")
                .priority(10).panel(panel).icon(createIcon()).build();
        toolbar.addNavigation(navigation);
    }

    @Override protected void shutDown() {
        stopEvent();
        if (navigation != null) toolbar.removeNavigation(navigation);
        navigation = null;
        if (panel != null) panel.dispose();
        panel = null;
    }

    void startEvent() {
        if (script.isActive()) return;
        if (config.event() != KspSeasonalEvent.HALLOWEEN_2026) return;
        if (script.start(config)) overlays.add(overlay);
    }

    void togglePause() {
        if (script.isActive()) script.setPaused(!script.isPaused());
    }

    void stopEvent() {
        script.shutdown();
        overlays.remove(overlay);
    }

    @Subscribe public void onChatMessage(ChatMessage event) {
        if (event == null || !script.isActive()) return;
        // Public, clan and private chat are not evidence of game objectives.
        ChatMessageType type = event.getType();
        if (type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM
                || type == ChatMessageType.ENGINE || type == ChatMessageType.MESBOX)
            script.onChatMessage(event.getMessage());
    }

    public void prepareHotUnload() { shutDown(); }

    private static BufferedImage createIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(241, 139, 51));
            g.fillOval(2, 4, 12, 10);
            g.setColor(new Color(59, 106, 54));
            g.fillRect(7, 2, 2, 4);
            g.setColor(Color.BLACK);
            g.fillOval(5, 8, 2, 2);
            g.fillOval(10, 8, 2, 2);
            g.drawLine(6, 11, 10, 11);
        } finally {
            g.dispose();
        }
        return image;
    }
}
