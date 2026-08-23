package net.runelite.client.plugins.microbot.kspbryophyta;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

@PluginDescriptor(
        name = PluginConstants.KSP + "Bryophyta",
        description = "Full Bryophyta cycle with custom per-strategy equipment, Varrock banking and altar prayer restore",
        tags = {"ksp", "microbot", "bryophyta", "boss", "f2p", "melee", "ranged", "magic", "equipment"},
        authors = {"KSP"},
        version = KspBryophytaPlugin.VERSION,
        minClientVersion = "2.0.13",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspBryophytaPlugin extends Plugin {
    public static final String VERSION = "0.1.9";

    @Inject
    private KspBryophytaConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private KspBryophytaScript script;

    @Inject
    private KspBryophytaOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private BryophytaEquipmentSettings equipmentSettings;

    @Inject
    private BryophytaEquipmentIndex equipmentIndex;

    @Inject
    private ItemManager itemManager;

    private KspBryophytaEquipmentPanel equipmentPanel;
    private NavigationButton navigationButton;

    @Provides
    KspBryophytaConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(KspBryophytaConfig.class);
    }

    @Override
    protected void startUp() {
        configManager.setDefaultConfiguration(config, false);
        overlayManager.add(overlay);
        addEquipmentPanel();
        script.setStopped("Ready - press Start in the Bryophyta side panel.");
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
        removeEquipmentPanel();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!script.isRunning() || event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String message = event.getMessage();
        if (message == null) {
            return;
        }

        String lower = message.toLowerCase();
        if (lower.contains("you climb down through the manhole")) {
            script.confirmManholeDescent();
        }

        if (config.shutdownAfterDeath()
                && lower.contains("oh dear, you are dead")) {
            script.setStopped("Stopped after death.");
            script.shutdown();
        }
    }

    private void addEquipmentPanel() {
        if (navigationButton != null) {
            return;
        }

        equipmentPanel = new KspBryophytaEquipmentPanel(
                equipmentSettings,
                equipmentIndex,
                itemManager,
                config.strategy(),
                script,
                config);

        BufferedImage source = BryophytaEquipmentAssets.loadEquipmentSlots();
        BufferedImage iconSource = source != null && source.getWidth() >= 170 && source.getHeight() >= 61
                ? source.getSubimage(117, 8, 53, 53)
                : source;
        BufferedImage icon = resize(iconSource, 16, 16);

        navigationButton = NavigationButton.builder()
                .tooltip("KSP Bryophyta Equipment")
                .priority(8)
                .icon(icon)
                .panel(equipmentPanel)
                .build();

        clientToolbar.addNavigation(navigationButton);
    }

    private void removeEquipmentPanel() {
        if (navigationButton != null) {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }
        if (equipmentPanel != null) {
            equipmentPanel.shutdownPanel();
        }
        equipmentPanel = null;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        if (source == null) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }
}
