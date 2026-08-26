package net.runelite.client.plugins.microbot.kspgeflipper;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;

public class KspGEFlipperOverlay extends OverlayPanel {
    @Inject
    KspGEFlipperOverlay(KspGEFlipperPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(305, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP GE Flipper v" + KspGEFlipperPlugin.VERSION).color(Color.ORANGE).build());

        Duration runtime = KspGEFlipperScript.runtime();
        line("Status", KspGEFlipperScript.status);
        line("Account", KspGEFlipperScript.members ? "Members" : "F2P");
        line("Runtime", time(runtime));
        line("Cash", gp(KspGEFlipperScript.cash));
        line("Capital in flips", gp(KspGEFlipperScript.capitalUsed));
        line("Active flips", KspGEFlipperScript.activeFlips + "  (B " + KspGEFlipperScript.buyingFlips + " / S " + KspGEFlipperScript.sellingFlips + ")");

        line("Candidate", KspGEFlipperScript.bestCandidate);
        if (!"-".equals(KspGEFlipperScript.bestCandidate)) {
            line("Type", KspGEFlipperScript.candidateType);
            line("Buy -> Sell", gp(KspGEFlipperScript.candidateBuy) + " -> " + gp(KspGEFlipperScript.candidateSell));
            line("Quantity", Integer.toString(KspGEFlipperScript.candidateQty));
            line("Net ROI", String.format("%.2f%%", KspGEFlipperScript.candidateRoi));
            line("Est. profit", gp(KspGEFlipperScript.candidateProfit));
            line("Est. duration", KspGEFlipperScript.candidateExpectedMinutes + "m");
            line("Expected GP/h", gp(KspGEFlipperScript.candidateGpPerHour));
            line("Confidence", String.format("%.0f%%", KspGEFlipperScript.candidateConfidence * 100.0));
            line("1h matched volume", Integer.toString(KspGEFlipperScript.candidateVolume));
        }

        line("Realized profit", gp(KspGEFlipperScript.profit));
        line("Realized profit / h", gp(perHour(KspGEFlipperScript.profit, runtime)));
        line("Completed flips", Integer.toString(KspGEFlipperScript.completedFlips));
        line("Market items", Integer.toString(KspGEFlipperScript.marketItems));
        return super.render(graphics);
    }

    private void line(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right == null ? "-" : right).build());
    }

    private static long perHour(long value, Duration duration) {
        return duration.getSeconds() < 1 ? 0 : Math.round(value * 3600.0 / duration.getSeconds());
    }

    private static String time(Duration duration) {
        long seconds = duration.getSeconds();
        return String.format("%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private static String gp(long value) {
        long absolute = Math.abs(value);
        if (absolute >= 1_000_000_000L) return String.format("%.2fB", value / 1_000_000_000d);
        if (absolute >= 1_000_000L) return String.format("%.2fM", value / 1_000_000d);
        if (absolute >= 1_000L) return String.format("%.1fK", value / 1_000d);
        return Long.toString(value);
    }
}
