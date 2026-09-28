package net.runelite.client.plugins.microbot.kspf2paccountbuilder;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;

public class KspF2pAccountBuilderOverlay extends OverlayPanel
{
    private final KspF2pAccountBuilderPlugin plugin;

    @Inject
    public KspF2pAccountBuilderOverlay(KspF2pAccountBuilderPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        KspF2pAccountBuilderScript script = plugin.getScript();
        if (script == null)
        {
            return super.render(graphics);
        }

        F2pTradeRestrictionTracker.Snapshot gate = script.getRestriction();

        panelComponent.setPreferredSize(new Dimension(285, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP F2P Account Builder v" + KspF2pAccountBuilderPlugin.VERSION)
                .build());
        panelComponent.getChildren().add(LineComponent.builder().left("Phase").right(script.getPhase().name()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Status").right(script.getStatus()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Module").right(script.getActiveModule()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Quest").right(script.getCurrentQuest()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Account playtime").right(gate.formatTradePlaytime()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Quest points")
                .right(gate.getQuestPoints() + " / " + F2pTradeRestrictionTracker.REQUIRED_QUEST_POINTS)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Total level")
                .right(gate.getTotalLevel() + " / " + F2pTradeRestrictionTracker.REQUIRED_TOTAL_LEVEL)
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("GE trade gate")
                .right(gate.isTradeUnlocked() ? "UNLOCKED" : "LOCKED")
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Liquid GP")
                .right(String.format("%,d", script.getLiquidCoins()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Next upgrade")
                .right(script.getNextUpgrade())
                .build());

        Instant started = plugin.getStarted();
        if (started != null)
        {
            long seconds = Duration.between(started, Instant.now()).getSeconds();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Builder runtime")
                    .right(String.format("%02d:%02d:%02d",
                            seconds / 3600,
                            (seconds % 3600) / 60,
                            seconds % 60))
                    .build());
        }

        return super.render(graphics);
    }
}
