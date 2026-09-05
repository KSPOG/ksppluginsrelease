package net.runelite.client.plugins.microbot.kspsmartsmelter;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RouteQuote;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;

public class KspSmartSmelterOverlay extends OverlayPanel {
    private static final int WIDTH = 285;

    private final KspSmartSmelterConfig config;
    private final KspSmartSmelterScript script;

    @Inject
    public KspSmartSmelterOverlay(
            KspSmartSmelterPlugin plugin,
            KspSmartSmelterConfig config,
            KspSmartSmelterScript script
    ) {
        super(plugin);
        this.config = config;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            return null;
        }

        try {
            panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
            panelComponent.getChildren().add(
                    TitleComponent.builder()
                            .text("KSP Smart Smelter v" + KspSmartSmelterPlugin.VERSION)
                            .build()
            );

            add("Status", shorten(Microbot.status, 31));
            add("Smithing", script.getSmithingLevel() + " / +" + script.getSmithingLevelsGained());
            add("Account", script.isMemberAccount() ? "Members" : "F2P");
            add("Anti-ban", shorten(script.getAntibanStatus(), 31));

            RouteQuote quote = script.getSelectedQuote();
            addSection("Route");
            if (quote == null) {
                add("Method", "None");
            } else {
                add("Method", quote.getRoute().getOutputName());
                add("Profit / cycle", signedGp(quote.getProfitPerCycle()));
                add("ROI", String.format("%.2f%%", quote.getRoiPercent()));
                add("Stock", script.getSelectedBankCycles() + " bank / "
                        + script.getSelectedInventoryCycles() + " inv");
            }

            boolean sessionStarted = script.getStartedAt() > 0;
            addSection("Session");
            add("Runtime", sessionStarted
                    ? formatDuration(System.currentTimeMillis() - script.getStartedAt())
                    : "00:00:00");
            add("Profit", signedGp(script.getExpectedSessionProfit()) + " / "
                    + signedGp(script.getExpectedProfitPerHour()) + "/h");
            add("Output", formatNumber(script.getOutputProduced()) + " / "
                    + formatNumber(script.getOutputPerHour()) + "/h");
            add("XP", (sessionStarted ? formatNumber(script.getSmithingXpGained()) : "0") + " / "
                    + (sessionStarted ? formatNumber(script.getSmithingXpPerHour()) : "0") + "/h");
            add("Trips", String.valueOf(script.getCompletedTrips()));
        } catch (Exception ex) {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
        }

        return super.render(graphics);
    }

    private void addSection(String title) {
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("— " + title + " —")
                        .build()
        );
    }

    private void add(String left, String right) {
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(left == null ? "" : left)
                        .right(right == null ? "" : right)
                        .build()
        );
    }

    private String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String signedGp(double gp) {
        return (gp > 0 ? "+" : "") + formatGp(gp);
    }

    private String formatGp(double gp) {
        double abs = Math.abs(gp);
        if (abs >= 1_000_000_000) return String.format("%.2fb", gp / 1_000_000_000.0);
        if (abs >= 1_000_000) return String.format("%.2fm", gp / 1_000_000.0);
        if (abs >= 1_000) return String.format("%.1fk", gp / 1_000.0);
        return String.format("%.0f", gp);
    }

    private String formatNumber(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000) return String.format("%.2fb", value / 1_000_000_000.0);
        if (abs >= 1_000_000) return String.format("%.2fm", value / 1_000_000.0);
        if (abs >= 1_000) return String.format("%.1fk", value / 1_000.0);
        return String.format("%.0f", value);
    }

    private String formatDuration(long millis) {
        Duration d = Duration.ofMillis(Math.max(0, millis));
        long hours = d.toHours();
        long minutes = d.minusHours(hours).toMinutes();
        long seconds = d.minusHours(hours).minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
