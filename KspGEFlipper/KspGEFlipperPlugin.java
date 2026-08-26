package net.runelite.client.plugins.microbot.kspgeflipper;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginConstants.KSP + "GE Flipper",
        description = "Embedded state-aware GE flipping with persistent forecasts, portfolio calibration, optional remote sharing and local fallback",
        tags = {"ge", "flip", "flipping", "money", "ksp", "microbot", "forecast", "portfolio"},
        version = KspGEFlipperPlugin.VERSION,
        minClientVersion = "0.0.3",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspGEFlipperPlugin extends Plugin {
    public static final String VERSION = "1.1.0";

    @Inject private KspGEFlipperConfig config;
    @Inject private KspGEFlipperRuntime runtime;
    @Inject private KspGEFlipperOverlay overlay;
    @Inject private OverlayManager overlayManager;
    @Inject private ClientToolbar clientToolbar;
    private KspGEFlipperPanel panel;
    private NavigationButton navButton;

    @Provides
    KspGEFlipperConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(KspGEFlipperConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        panel = new KspGEFlipperPanel(config, runtime);
        navButton = NavigationButton.builder()
                .tooltip("KSP GE Flipper")
                .icon(createIcon())
                .priority(7)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
        runtime.run(config);
    }

    @Override
    protected void shutDown() {
        runtime.shutdown();
        if (panel != null) {
            panel.shutdown();
            panel = null;
        }
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        overlayManager.remove(overlay);
    }

    private static BufferedImage createIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(255, 152, 0));
            g.fillOval(1, 1, 14, 14);
            g.setColor(Color.BLACK);
            g.drawString("G", 4, 12);
        } finally {
            g.dispose();
        }
        return image;
    }
}
