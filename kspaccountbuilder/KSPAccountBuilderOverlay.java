package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.concurrent.TimeUnit;

public class KSPAccountBuilderOverlay extends OverlayPanel
{
    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;

    @Inject
    public KSPAccountBuilderOverlay(KspAccountBuilderPlugin plugin, KspAccountBuilderScript script, KspAccountBuilderConfig config)
    {
        super(plugin);
        this.script = script;
        this.config = config;

        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(260, 0));
        String displayStatus = normalizeStatus(Microbot.status);

        String title = "KSP Account Builder V" + KspAccountBuilderPlugin.VERSION;
        if (script.isAnyBreakActive())
        {
            title = title + (script.isBreakActive()
                    ? " | Break " + formatDuration(script.getBreakTimeRemainingSeconds())
                    : " | Break");
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(title)
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Task:")
                .right(script.getCurrentTaskName())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(displayStatus)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running:")
                .right(formatDuration(script.getRuntimeSeconds()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Account Play Time:")
                .right(formatDuration(script.getAccountPlayTimeSeconds()))
                .build());

        if (config.doBreaks())
        {
            if (script.isBreakActive())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Break Remaining:")
                        .right(formatDuration(script.getBreakTimeRemainingSeconds()))
                        .build());
            }
            else
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Time till break:")
                        .right(formatDuration(script.getTimeUntilBreakSeconds()))
                        .build());
            }
        }

        if (config.enableActivitySwitchRandomization()
                && !config.trainSingleSkill()
                && !config.runSingleQuest())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time till activity change:")
                    .right(formatDuration(script.getTimeUntilActivitySwitchSeconds()))
                    .build());
        }

        return super.render(graphics);
    }

    private String formatDuration(long totalSeconds)
    {
        if (totalSeconds < 0)
        {
            return "--";
        }

        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        if (hours > 0)
        {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String normalizeStatus(String status)
    {
        if (status == null || status.isEmpty())
        {
            return "Idle";
        }

        String normalizedStatus = status.trim();
        if (normalizedStatus.toLowerCase().startsWith("walking to closest bank"))
        {
            return "Walking to Bank";
        }

        String lowerCaseStatus = normalizedStatus.toLowerCase();
        if (lowerCaseStatus.startsWith("chopping down "))
        {
            String treeName = normalizedStatus.substring("Chopping Down ".length()).trim();
            if (!treeName.isEmpty())
            {
                return "Choppin " + treeName;
            }
        }

        return normalizedStatus;
    }
}
