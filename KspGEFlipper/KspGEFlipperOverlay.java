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
        panelComponent.setPreferredSize(new Dimension(285, 0));
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
            line("Buy -> Sell", gp(KspGEFlipperScript.candidateBuy) + " -> " + gp(KspGEFlipperScript.candidateSell));
            line("Quantity", Integer.toString(KspGEFlipperScript.candidateQty));
            line("Net ROI", String.format("%.2f%%", KspGEFlipperScript.candidateRoi));
            line("Est. profit", gp(KspGEFlipperScript.candidateProfit));
            line("1h two-side volume", Integer.toString(KspGEFlipperScript.candidateVolume));
        }

        line("Realized profit", gp(KspGEFlipperScript.profit));
        line("Profit / h", gp(perHour(KspGEFlipperScript.profit, runtime)));
        line("Completed flips", Integer.toString(KspGEFlipperScript.completedFlips));
        line("Market items", Integer.toString(KspGEFlipperScript.marketItems));
        return super.render(graphics);
    }

    private void line(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right == null ? "-" : right).build());
    }

    private static long perHour(long value, Duration d) {
        return d.getSeconds() < 1 ? 0 : Math.round(value * 3600.0 / d.getSeconds());
    }

    private static String time(Duration d) {
        long s = d.getSeconds();
        return String.format("%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    private static String gp(long v) {
        long a = Math.abs(v);
        if (a >= 1_000_000_000L) return String.format("%.2fB", v / 1_000_000_000d);
        if (a >= 1_000_000L) return String.format("%.2fM", v / 1_000_000d);
        if (a >= 1_000L) return String.format("%.1fK", v / 1_000d);
        return Long.toString(v);
    }
}
