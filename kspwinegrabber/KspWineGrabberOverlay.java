package net.runelite.client.plugins.microbot.kspwinegrabber;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class KspWineGrabberOverlay extends OverlayPanel
{
    private final KspWineGrabberPlugin plugin;

    @Inject
    KspWineGrabberOverlay(KspWineGrabberPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        KspWineGrabberScript script = plugin.getScript();
        if (script == null)
        {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(270, 120));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 185));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Wine Grabber")
                .color(Color.WHITE)
                .build());

        addLine("Status:", script.getStatus());
        addLine("Time:", formatTime(script.getRuntimeMs()));
        addLine("Collected:", String.format("%,d (%,d ph)",
                script.getCollected(), script.getWinesPerHour()));
        addLine("Profit:", script.hasPriceData()
                ? formatGp(script.getEstimatedProfit()) + " ("
                        + formatGp(script.getEstimatedProfitPerHour()) + " ph)"
                : "N/A (N/A ph)");
        return super.render(graphics);
    }

    private void addLine(String key, String value)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(key)
                .right(value)
                .build());
    }

    private String formatGp(long amount)
    {
        long abs = amount == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(amount);
        String sign = amount < 0L ? "-" : "";
        if (abs >= 1_000_000_000L)
        {
            return sign + String.format("%.2fB", abs / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L)
        {
            return sign + String.format("%.2fM", abs / 1_000_000.0);
        }
        if (abs >= 1_000L)
        {
            return sign + String.format("%.1fK", abs / 1_000.0);
        }
        return String.format("%,d", amount);
    }

    private String formatTime(long millis)
    {
        long seconds = Math.max(0L, millis) / 1000;
        return String.format("%02d:%02d:%02d",
                seconds / 3600,
                (seconds % 3600) / 60,
                seconds % 60);
    }
}
