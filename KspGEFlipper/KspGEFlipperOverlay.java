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
        panelComponent.setPreferredSize(new Dimension(330, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP GE Flipper v" + KspGEFlipperPlugin.VERSION).color(Color.ORANGE).build());

        Duration runtime = KspGEFlipperRuntime.runtime();
        line("Engine", KspGEFlipperRuntime.engine);
        line("Backend", KspGEFlipperRuntime.backend);
        line("Status", KspGEFlipperScript.status);
        line("Account", KspGEFlipperScript.members ? "Members" : "F2P");
        line("Runtime", time(runtime));
        line("Cash", gp(KspGEFlipperScript.cash));
        line("Capital in flips", gp(KspGEFlipperScript.capitalUsed));
        line("Active flips", KspGEFlipperScript.activeFlips + "  (B " + KspGEFlipperScript.buyingFlips + " / S " + KspGEFlipperScript.sellingFlips + ")");

        line("Learning", KspGEFlipperScript.calibrationStatus);
        if (KspGEFlipperScript.calibrationSamples > 0) {
            line("Model samples", Integer.toString(KspGEFlipperScript.calibrationSamples));
            line("Duration correction", multiplier(KspGEFlipperScript.calibrationDurationMultiplier));
            line("Execution correction", multiplier(KspGEFlipperScript.calibrationExecutionMultiplier));
            line("Profit correction", multiplier(KspGEFlipperScript.calibrationProfitMultiplier));
            line("Modification rate", String.format("%.1f%%", KspGEFlipperScript.calibrationModificationRate * 100.0));
        }

        line("Candidate", KspGEFlipperScript.bestCandidate);
        if (!"-".equals(KspGEFlipperScript.bestCandidate)) {
            line("Type", KspGEFlipperScript.candidateType);
            if (KspGEFlipperScript.candidateBuy > 0 || KspGEFlipperScript.candidateSell > 0)
                line("Buy -> Sell", gp(KspGEFlipperScript.candidateBuy) + " -> " + gp(KspGEFlipperScript.candidateSell));
            line("Quantity", Integer.toString(KspGEFlipperScript.candidateQty));
            line("Net ROI", String.format("%.2f%%", KspGEFlipperScript.candidateRoi));
            line("Expected profit", gp(KspGEFlipperScript.candidateProfit));
            line("Expected duration", KspGEFlipperScript.candidateExpectedMinutes + "m");
            line("Expected GP/h", gp(KspGEFlipperScript.candidateGpPerHour));
            line("Confidence", String.format("%.0f%%", KspGEFlipperScript.candidateConfidence * 100.0));
            if (KspGEFlipperScript.candidateVolume > 0)
                line("1h two-side volume", Integer.toString(KspGEFlipperScript.candidateVolume));
            if (KspGEFlipperScript.candidateCalibrationSamples > 0) {
                line("Candidate samples", Integer.toString(KspGEFlipperScript.candidateCalibrationSamples));
                line("Candidate D/E/P", multiplier(KspGEFlipperScript.candidateDurationMultiplier) + " / "
                        + multiplier(KspGEFlipperScript.candidateExecutionMultiplier) + " / "
                        + multiplier(KspGEFlipperScript.candidateProfitMultiplier));
            }
        }

        if (!"Off".equals(KspGEFlipperRuntime.dump)) line("Dump stream", KspGEFlipperRuntime.dump);
        if (!"-".equals(KspGEFlipperRuntime.explanation)) line("Reason", truncate(KspGEFlipperRuntime.explanation, 42));
        line("Realized profit", gp(KspGEFlipperScript.profit));
        line("Profit / h", gp(perHour(KspGEFlipperScript.profit, runtime)));
        line("Completed flips", Integer.toString(KspGEFlipperScript.completedFlips));
        if (KspGEFlipperScript.marketItems > 0) line("Market items", Integer.toString(KspGEFlipperScript.marketItems));
        return super.render(graphics);
    }

    private void line(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right == null ? "-" : right).build());
    }

    private static String multiplier(double value) { return String.format("x%.2f", value); }
    private static long perHour(long value, Duration d) { return d.getSeconds() < 1 ? 0 : Math.round(value * 3600.0 / d.getSeconds()); }
    private static String time(Duration d) { long s=d.getSeconds(); return String.format("%02d:%02d:%02d",s/3600,s/60%60,s%60); }
    private static String truncate(String s,int max){return s==null?"-":s.length()<=max?s:s.substring(0,Math.max(1,max-1))+"…";}
    private static String gp(long v) {
        long a=Math.abs(v);
        if(a>=1_000_000_000L)return String.format("%.2fB",v/1_000_000_000d);
        if(a>=1_000_000L)return String.format("%.2fM",v/1_000_000d);
        if(a>=1_000L)return String.format("%.1fK",v/1_000d);
        return Long.toString(v);
    }
}
