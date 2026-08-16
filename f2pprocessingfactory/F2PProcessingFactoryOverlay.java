package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;

public class F2PProcessingFactoryOverlay extends OverlayPanel
{
    private final F2PProcessingFactoryPlugin plugin;

    @Inject
    F2PProcessingFactoryOverlay(F2PProcessingFactoryPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        F2PProcessingFactoryScript script = plugin.getScript();
        if (script == null)
        {
            return null;
        }

        panelComponent.setPreferredSize(new Dimension(285, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("KSP AIO Factory v" + F2PProcessingFactoryPlugin.VERSION)
            .color(Color.GREEN)
            .build());

        addLine("State", prettify(script.getState().name()));
        addLine("Status", script.getStatus());
        addLine("Runtime", script.getRuntimeText());
        addLine("Account", script.isMembersAccount()
                ? (script.isMemberWorld() ? "Members" : "Members (F2P world)")
                : "Free-to-play");
        addLine("Anti-ban", script.getAntibanStatus());
        String watchdogStatus = script.getProgressWatchdogStatus();
        addLine("Retry guard", watchdogStatus == null || watchdogStatus.equals("Idle")
            ? "Monitoring"
            : watchdogStatus);
        if (script.getAntibanPauseSeconds() > 0)
        {
            addLine("Anti-ban pause", script.getAntibanPauseSeconds() + "s");
        }
        addLine("Coins detected", formatCoins(script.getObservedCoinTotal()));
        addLine("Spendable", formatCoins(script.getObservedSpendableCoins()));

        FactoryRecipe recipe = script.getActiveRecipe();
        if (recipe != null)
        {
            addLine("Recipe", recipe.getDisplayName());
            addLine("Cycle", script.getCycleProcessedUnits() + " / " + script.getCycleTargetUnits());
        }

        ProfitQuote quote = script.getActiveQuote();
        if (quote != null && quote.isValid())
        {
            addLine("Profit / unit", formatCoins(quote.getProfitPerUnit()));
            addLine("ROI", String.format(Locale.ROOT, "%.2f%%", quote.getRoiPercent()));
            addLine("Est. GP / hour", formatCoins(quote.getEstimatedProfitPerHour()));
        }

        if (script.getState() == FactoryState.WAITING_FOR_LIMIT
            || script.getState() == FactoryState.WAITING_FOR_MARKET)
        {
            addLine("Recheck in", script.getWaitSeconds() + "s");
        }

        FactoryStats stats = script.getStats();
        addLine("Processed", formatNumber(stats.getUnitsProcessed()));
        addLine("Bought", formatNumber(stats.getItemsBought()));
        addLine("Sold", formatNumber(stats.getItemsSold()));
        addLine("Spent", formatCoins(stats.getCoinsSpent()));
        addLine("Gross sales", formatCoins(stats.getGrossRevenue()));
        addLine("Session cash flow", formatCoins(stats.getSessionCashFlow()));

        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
            .left(left + ":")
            .right(right == null ? "" : right)
            .build());
    }

    private static String prettify(String value)
    {
        if (value == null)
        {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String formatCoins(long value)
    {
        String sign = value < 0 ? "-" : "";
        double absolute = Math.abs((double) value);
        if (absolute >= 1_000_000_000)
        {
            return sign + String.format(Locale.ROOT, "%.2fB", absolute / 1_000_000_000.0);
        }
        if (absolute >= 1_000_000)
        {
            return sign + String.format(Locale.ROOT, "%.2fM", absolute / 1_000_000.0);
        }
        if (absolute >= 1_000)
        {
            return sign + String.format(Locale.ROOT, "%.1fK", absolute / 1_000.0);
        }
        return sign + String.format(Locale.ROOT, "%.0f", absolute);
    }

    private static String formatNumber(long value)
    {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
