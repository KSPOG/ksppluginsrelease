package net.runelite.client.plugins.microbot.KSPTradeReceiver;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;

public class KSPTradeReceiverOverlay extends OverlayPanel
{
    private final KSPTradeReceiverConfig config;

    @Inject
    KSPTradeReceiverOverlay(KSPTradeReceiverPlugin plugin, KSPTradeReceiverConfig config)
    {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(300, 315));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Trade Receiver v" + KSPTradeReceiverPlugin.VERSION)
                .color(Color.ORANGE)
                .build());

        addLine("Status:", KspLocalMuleCoordinatorService.status);
        addLine("Runtime:", formatDuration(KSPTradeReceiverScript.getRuntime()));
        addLine("Coordinator:", KspLocalMuleCoordinatorService.coordinatorOnline
                ? "127.0.0.1:" + KspLocalMuleCoordinatorService.localPort
                : "Offline");
        addLine("Pending jobs:", Integer.toString(KspLocalMuleCoordinatorService.pendingWorkers));
        addLine("Queued jobs:", Integer.toString(KspLocalMuleCoordinatorService.queuedWorkers));
        addLine("Active worker:", KspLocalMuleCoordinatorService.activeWorker);
        addLine("Expected coins:", formatGp(KspLocalMuleCoordinatorService.activeCoins));
        addLine("Trade status:", KSPTradeReceiverScript.status);
        addLine("Trader:", KSPTradeReceiverScript.configuredTrader);
        addLine("Pending:", KSPTradeReceiverScript.pendingTrader);
        addLine("Inventory:", KSPTradeReceiverScript.inventorySlots + "/28");
        addLine("Trade tile:", KSPTradeReceiverScript.savedTradeTile);
        addLine("Own offer:", KSPTradeReceiverScript.ownOfferSafe ? "Safe / empty" : "BLOCKED");
        addLine("First accepts:", Integer.toString(KSPTradeReceiverScript.acceptedFirstScreens));
        addLine("Final accepts:", Integer.toString(KSPTradeReceiverScript.acceptedConfirmations));
        addLine("Bank trips:", Integer.toString(KSPTradeReceiverScript.bankTrips));
        addLine("Ignored requests:", Integer.toString(KSPTradeReceiverScript.ignoredTradeRequests));

        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right == null ? "-" : right)
                .build());
    }

    private static String formatGp(long value)
    {
        if (value <= 0L) return "-";
        if (value >= 1_000_000_000L) return String.format("%.2fB", value / 1_000_000_000.0);
        if (value >= 1_000_000L) return String.format("%.2fM", value / 1_000_000.0);
        if (value >= 1_000L) return String.format("%.1fK", value / 1_000.0);
        return Long.toString(value);
    }

    private static String formatDuration(Duration duration)
    {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
