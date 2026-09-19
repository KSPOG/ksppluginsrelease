package net.runelite.client.plugins.microbot.kspkebabbuyer;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KspKebabBuyerOverlay extends OverlayPanel
{
    private final KspKebabBuyerPlugin plugin;

    @Inject
    KspKebabBuyerOverlay(KspKebabBuyerPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        KspKebabBuyerScript script = plugin.getScript();
        if (script == null)
        {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(245, 205));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 175));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Kebab Buyer")
                .color(Color.WHITE)
                .build());

        addLine("Status:", shorten(script.getStatus(), 27));
        addLine("Time running:", formatDuration(script.getRuntimeMs()));
        addLine("Bought:", formatNumber(script.getKebabsBought())
                + " / " + formatNumber(script.getKebabsPerHour()) + " ph");
        addLine("Inventory:", script.getInventoryKebabs() + " kebabs");
        addLine("Bank trips:", formatNumber(script.getBankTrips()));
        addLine("Coins left:", formatNumber(script.getCoinsRemaining()));
        addLine("GP spent:", formatNumber(script.getGpSpent()));
        addLine("Kebab GE:", script.getKebabGePrice() > 0
                ? formatNumber(script.getKebabGePrice()) + " gp"
                : "Loading...");
        addLine("Est. profit:", formatGp(script.getEstimatedProfit())
                + " / " + formatGp(script.getEstimatedProfitPerHour()) + " ph");

        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right)
                .build());
    }

    private String formatDuration(long millis)
    {
        long totalSeconds = Math.max(0L, millis) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatNumber(long value)
    {
        return String.format("%,d", value);
    }

    private String formatGp(long value)
    {
        long abs = Math.abs(value);
        String sign = value < 0 ? "-" : "";

        if (abs >= 1_000_000_000L)
        {
            return sign + String.format("%.2fB", abs / 1_000_000_000.0D);
        }
        if (abs >= 1_000_000L)
        {
            return sign + String.format("%.2fM", abs / 1_000_000.0D);
        }
        if (abs >= 1_000L)
        {
            return sign + String.format("%.1fK", abs / 1_000.0D);
        }
        return Long.toString(value);
    }

    private String shorten(String value, int max)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }
}
