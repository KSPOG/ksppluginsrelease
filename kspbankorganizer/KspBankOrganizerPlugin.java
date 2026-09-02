package net.runelite.client.plugins.microbot.kspbankorganizer;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        name = PluginConstants.KSP + "Bank Organizer",
    description = "Automatically categorizes, moves and smart-sorts real OSRS bank tabs.",
    tags = {"bank", "organizer", "sort", "tabs", "ksp"},
    authors = {"KSP"},
    version = KspBankOrganizerPlugin.VERSION,
    minClientVersion = "2.0.61",
    iconUrl = "",
    cardUrl = "",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KspBankOrganizerPlugin extends Plugin
{
    public static final String VERSION = "1.1.36";

    @Inject private KspBankOrganizerConfig config;
    @Inject private BankOrganizerEngine engine;
    @Inject private OverlayManager overlayManager;
    @Inject private KspBankOrganizerOverlay overlay;
    @Inject private KspBankOrganizerItemOverlay itemOverlay;
    @Inject private ClientToolbar clientToolbar;

    private ExecutorService executor;
    private Future<?> task;
    private KspBankOrganizerPanel panel;
    private NavigationButton navButton;

    @Provides
    KspBankOrganizerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspBankOrganizerConfig.class);
    }

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        overlayManager.add(itemOverlay);
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ksp-bank-organizer");
            thread.setDaemon(true);
            return thread;
        });
        addSidePanel();
        log.info("KSP Bank Organizer enabled. Use the sidebar to preview or organize the bank.");
    }

    @Override
    protected void shutDown()
    {
        stopRun();
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
        removeSidePanel();
        overlayManager.remove(overlay);
        overlayManager.remove(itemOverlay);
        log.info("KSP Bank Organizer disabled.");
    }

    synchronized void startRun(OperationMode mode)
    {
        if (isRunActive())
        {
            log.warn("Bank Organizer run ignored because another run is already active.");
            return;
        }
        if (executor == null || executor.isShutdown())
        {
            log.warn("Bank Organizer executor is not available.");
            return;
        }

        log.info("KSP Bank Organizer run requested: {}", mode);
        task = executor.submit(() -> {
            BankOrganizerEngine.RunResult result = engine.run(config, mode);
            if (result.success()) log.info("KSP Bank Organizer completed: {}", result.message());
            else log.warn("KSP Bank Organizer stopped: {}", result.message());
        });
    }

    synchronized void stopRun()
    {
        if (task != null && !task.isDone())
        {
            task.cancel(true);
            engine.markStoppedByUser();
        }
        task = null;
    }

    synchronized boolean isRunActive() { return task != null && !task.isDone(); }

    private void addSidePanel()
    {
        SwingUtilities.invokeLater(() -> {
            if (panel != null) return;
            panel = new KspBankOrganizerPanel(this, engine, config);
            navButton = NavigationButton.builder()
                .tooltip("KSP Bank Organizer")
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

    private static BufferedImage createSidebarIcon()
    {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        try
        {
            g.setColor(new Color(80, 220, 120));
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 3; col++)
                    g.fillRoundRect(1 + col * 5, 1 + row * 5, 4, 4, 1, 1);
        }
        finally
        {
            g.dispose();
        }
        return icon;
    }
}
