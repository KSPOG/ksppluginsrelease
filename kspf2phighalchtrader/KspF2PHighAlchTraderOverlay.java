package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class KspF2PHighAlchTraderOverlay extends OverlayPanel {
    private static final int WIDTH = 255;

    private final KspF2PHighAlchTraderScript script;

    @Inject
    public KspF2PHighAlchTraderOverlay(
            KspF2PHighAlchTraderPlugin plugin,
            KspF2PHighAlchTraderScript script
    ) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        // Keep the status panel visible while Bank/GE and other game widgets are open.
        // UNDER_WIDGETS can make an otherwise healthy overlay appear to randomly vanish.
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGH);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        // Height 0 lets PanelComponent size itself from the rendered rows instead of reserving
        // an arbitrary vertical block. Only the width is fixed for predictable alignment.
        panelComponent.setPreferredSize(new Dimension(WIDTH, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP High Alch Trader v" + KspF2PHighAlchTraderPlugin.VERSION)
                .color(Color.ORANGE)
                .build());

        addLine("State", formatState(script.getState()));
        addLine("Status", shorten(script.getStatus(), 28));
        addLine("Runtime", formatDuration(script.getStartedAt()));
        addLine("Account", script.isAccountMembershipActive() ? "Members" : "F2P");
        addLine("World", script.isCurrentWorldMembers() ? "Members" : "F2P");
        addLine("Market mode", script.isMembersContentEnabled() ? "F2P + P2P" : "F2P only");

        String pending = script.getPendingPurchaseSummary();
        if (pending != null) {
            addLine("GE slot", shorten(pending, 28));
        }
        String slowCooldowns = script.getSlowBuyCooldownSummary();
        if (slowCooldowns != null) {
            addLine("Slow cooldowns", shorten(slowCooldowns, 28));
        }

        addSeparator();
        addLine("Coins", formatGp(script.getCoinQuantity()));
        addLine("Nature runes", formatNumber(script.getNatureRuneQuantity()));
        addLine("Alch stock", "Noted");
        addLine("Fire source", script.isUsingFireStaffMode()
                ? "Staff of fire"
                : formatNumber(script.getFireRuneQuantity()) + " runes");

        AlchOpportunity active = script.getActiveOpportunity();
        if (active != null) {
            addSeparator();
            addLine("Item", shorten(active.getItemName(), 24));
            addLine("Buy ceiling", formatGp(active.getInstantBuyPrice()));
            addLine("High alch", formatGp(active.getHighAlchValue()));
            addLine("Profit / alch", formatSignedGp(active.getProfitPerCast()));
            addLine("Projected GP/hr", formatSignedGp(active.getExpectedProfitPerHour()));
            if (active.getTradeLimitPer4Hours() > 0) {
                addLine("GE limit / 4h", formatNumber(active.getTradeLimitPer4Hours()));
            }
        }

        if (script.getCommittedStockRemaining() > 0) {
            addLine("Purchased stock", formatNumber(script.getCommittedStockRemaining()));
        }

        addSeparator();
        addLine("Casts", formatNumber(script.getCastsCompleted()));
        addLine("Profit", formatSignedGp(script.getProjectedProfit()));
        addLine("Session GP/hr", formatSignedGp(script.getProjectedProfitPerHourActual()));
        addLine("Anti-ban", shorten(script.getCustomAntibanSummary(), 26));
        if (script.getCustomAntibanBreakCount() > 0) {
            addLine("Anti-ban pauses", Integer.toString(script.getCustomAntibanBreakCount()));
        }

        List<AlchOpportunity> ranked = script.getRankedOpportunities();
        if (!ranked.isEmpty()) {
            addSeparator();
            int limit = Math.min(3, ranked.size());
            for (int i = 0; i < limit; i++) {
                AlchOpportunity opportunity = ranked.get(i);
                addLine("#" + (i + 1) + " " + shorten(opportunity.getItemName(), 15),
                        formatSignedGp(opportunity.getProfitPerCast()) + "/alch");
            }
        }

        return super.render(graphics);
    }

    private void addSeparator() {
        panelComponent.getChildren().add(LineComponent.builder().build());
    }

    private void addLine(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right == null ? "-" : right)
                .build());
    }

    private static String formatState(KspF2PHighAlchTraderScript.State state) {
        if (state == null) {
            return "-";
        }
        String value = state.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String shorten(String value, int maxLength) {
        if (value == null || value.isBlank()) return "-";
        if (value.length() <= maxLength) return value;
        return value.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    private static String formatDuration(long startedAt) {
        if (startedAt <= 0L) {
            return "00:00:00";
        }
        long milliseconds = Math.max(0L, System.currentTimeMillis() - startedAt);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private static String formatSignedGp(long value) {
        return (value >= 0 ? "+" : "-") + formatGp(Math.abs(value));
    }

    private static String formatNumber(long value) {
        if (value >= 1_000_000_000L) {
            return String.format("%.2fB", value / 1_000_000_000.0);
        }
        if (value >= 1_000_000L) {
            return String.format("%.2fM", value / 1_000_000.0);
        }
        if (value >= 1_000L) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }

    private static String formatGp(long value) {
        return formatNumber(value) + " gp";
    }
}
