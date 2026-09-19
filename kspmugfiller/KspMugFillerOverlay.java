package net.runelite.client.plugins.microbot.kspmugfiller;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KspMugFillerOverlay extends OverlayPanel
{
    private final KspMugFillerPlugin plugin;

    @Inject
    KspMugFillerOverlay(KspMugFillerPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        KspMugFillerScript script = plugin.getScript();
        if (script == null)
        {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(230, 115));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 175));

        // TitleComponent is centered within the panel.
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Mug Filler")
                .color(Color.WHITE)
                .build());

        addLine("Time running:", formatDuration(script.getRuntimeMs()));
        addLine("Filled:", formatNumber(script.getFilledCount())
                + " / " + formatNumber(script.getFilledPerHour()) + " ph");
        addLine("GP:", formatGp(script.getGpMade())
                + " / " + formatGp(script.getGpPerHour()) + " ph");

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
}
