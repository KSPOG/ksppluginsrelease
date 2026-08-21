package net.runelite.client.plugins.microbot.kspdirectfishing;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KspDirectFishingOverlay extends OverlayPanel
{
    private final KspDirectFishingPlugin plugin;
    private final KspDirectFishingConfig config;

    @Inject
    KspDirectFishingOverlay(
            KspDirectFishingPlugin plugin,
            KspDirectFishingConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;

        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    private KspDirectFishingScript script()
    {
        return plugin.getScript();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.setPreferredSize(new Dimension(255, 250));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 175));

        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text("KSP Fishing v" + KspDirectFishingPlugin.VERSION)
                        .color(Color.BLUE)
                        .build()
        );

        addLine("Mode", config.fishingMode().toString());
        addLine("State", prettyState());
        addLine("Action", script().getStatus());

        panelComponent.getChildren().add(LineComponent.builder().build());

        addLine("Fishing XP", formatNumber(plugin.getFishingXpGained()));
        addLine("Fishing XP/h", formatNumber(plugin.getFishingXpPerHour()));
        addLine("Cooking XP", formatNumber(plugin.getCookingXpGained()));
        addLine("Cooking XP/h", formatNumber(plugin.getCookingXpPerHour()));

        panelComponent.getChildren().add(LineComponent.builder().build());

        addLine("Raw fish", String.valueOf(script().getRawFishCount()));

        if (config.fishingMode().usesBait())
        {
            addLine("Fishing bait", formatNumber(script().getBaitCount()));
        }
        else
        {
            addLine("Small net", Rs2Inventory.hasItem("Small fishing net") ? "Ready" : "Missing");
        }

        addLine("Fire", fireStatus());
        addLine("Runtime", plugin.getFormattedRuntime());

        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(left + ":")
                        .right(right == null ? "-" : right)
                        .build()
        );
    }

    private String prettyState()
    {
        KspDirectFishingState state = script().getState();
        if (state == null)
        {
            return "-";
        }

        String raw = state.name().replace('_', ' ').toLowerCase();
        StringBuilder result = new StringBuilder(raw.length());
        boolean upperNext = true;

        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);

            if (upperNext && Character.isLetter(c))
            {
                result.append(Character.toUpperCase(c));
                upperNext = false;
            }
            else
            {
                result.append(c);
            }

            if (c == ' ')
            {
                upperNext = true;
            }
        }

        return result.toString();
    }

    private String fireStatus()
    {
        return script().isFireAvailable() ? "Found" : "Not Found";
    }

    private String formatNumber(int value)
    {
        return String.format("%,d", value);
    }
}
