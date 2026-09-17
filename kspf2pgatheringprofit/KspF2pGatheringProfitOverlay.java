package net.runelite.client.plugins.microbot.kspf2pgatheringprofit;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;

public class KspF2pGatheringProfitOverlay extends OverlayPanel
{
    private final KspF2pGatheringProfitPlugin plugin;

    @Inject
    public KspF2pGatheringProfitOverlay(KspF2pGatheringProfitPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        KspF2pGatheringProfitScript script = plugin.getScript();
        if (script == null)
        {
            return super.render(graphics);
        }

        panelComponent.setPreferredSize(new Dimension(260, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP F2P Gathering Profit v" + KspF2pGatheringProfitPlugin.VERSION)
                .build());
        panelComponent.getChildren().add(LineComponent.builder().left("Status").right(script.getStatus()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Method").right(script.getMethod() == null ? "-" : script.getMethod().name()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Est GP/h").right(String.format("%,d", script.getGpHour())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Measured GP/h").right(String.format("%,d", script.getRealGpHour())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Location").right(script.getLocation()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("World hops").right(Integer.toString(script.getHops())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Escape").right(script.getEscape().name()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Est XP/h").right(String.format("%,d", script.getXpHour())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Actual XP/h").right(String.format("%,d", script.getActualXpHour())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("XP gained").right(String.format("%,d", script.getXpGained())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Failures").right(Integer.toString(script.getFailures())).build());

        Instant started = plugin.getStarted();
        if (started != null)
        {
            long seconds = Duration.between(started, Instant.now()).getSeconds();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime")
                    .right(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60))
                    .build());
        }

        return super.render(graphics);
    }
}
