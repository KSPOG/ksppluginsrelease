package net.runelite.client.plugins.microbot.kspbossgear;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = PluginConstants.KSP + "Boss Gear",
    description = "OSRS Wiki boss and raid gear/inventory finder with live bank and inventory highlights.",
    tags = {"boss", "gear", "equipment", "bank", "inventory", "wiki", "ksp"},
    authors = {"KSP"},
    version = KspBossGearPlugin.VERSION,
    minClientVersion = "2.0.61",
    iconUrl = "",
    cardUrl = "",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KspBossGearPlugin extends Plugin
{
    public static final String VERSION = "1.1.2";

    @Inject private BossGearService gearService;
    @Inject private OverlayManager overlayManager;
    @Inject private KspBossGearBankOverlay bankOverlay;
    @Inject private KspBossGearInventoryOverlay inventoryOverlay;
    @Inject private ClientToolbar clientToolbar;

    private KspBossGearPanel panel;
    private NavigationButton navButton;

    @Provides
    KspBossGearConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspBossGearConfig.class);
    }

    @Override
    protected void startUp()
    {
        gearService.start();
        overlayManager.add(bankOverlay);
        overlayManager.add(inventoryOverlay);
        addSidePanel();
        log.info("KSP Boss Gear enabled: Wiki boss/raid loadouts can highlight bank and inventory items.");
    }

    @Override
    protected void shutDown()
    {
        removeSidePanel();
        overlayManager.remove(bankOverlay);
        overlayManager.remove(inventoryOverlay);
        gearService.shutdown();
        log.info("KSP Boss Gear disabled.");
    }

    private void addSidePanel()
    {
        SwingUtilities.invokeLater(() -> {
            if (panel != null) return;
            panel = new KspBossGearPanel(gearService);
            navButton = NavigationButton.builder()
                .tooltip("KSP Boss Gear")
                .icon(createSidebarIcon())
                .priority(6)
                .panel(panel)
                .build();
            clientToolbar.addNavigation(navButton);
        });
    }

    private void removeSidePanel()
    {
        SwingUtilities.invokeLater(() -> {
            if (navButton != null)
            {
                clientToolbar.removeNavigation(navButton);
                navButton = null;
            }
            if (panel != null)
            {
                panel.dispose();
                panel = null;
            }
        });
    }

    /** Small shield + magnifier icon, drawn at runtime so source-loader installs need no resource file. */
    private static BufferedImage createSidebarIcon()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.5f));

            g.setColor(new Color(80, 220, 120));
            int[] xs = {2, 8, 8, 2};
            int[] ys = {2, 4, 11, 8};
            g.drawPolygon(xs, ys, 4);

            g.setColor(new Color(225, 225, 225));
            g.drawOval(7, 4, 5, 5);
            g.drawLine(11, 9, 14, 13);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }
}
