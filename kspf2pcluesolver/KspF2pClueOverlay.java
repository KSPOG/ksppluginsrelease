package net.runelite.client.plugins.microbot.kspf2pcluesolver;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public final class KspF2pClueOverlay extends OverlayPanel
{
    private final KspF2pCluePlugin plugin;
    @Inject KspF2pClueOverlay(KspF2pCluePlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(270, 0));
        KspF2pClueScript s = plugin.getScript();
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("KSP F2P Clue Solver v" + KspF2pCluePlugin.VERSION)
            .color(Color.CYAN).build());
        line("Status", s.getStatus());
        line("Clue", s.getClueName());
        line("Needed", s.getNeededItem());
        line("GE committed", s.getCommittedGp() + " GP");
        line("Completed", Long.toString(s.getCompleted()));
        long seconds = s.getRuntimeMs() / 1000L;
        line("Runtime", String.format("%02d:%02d:%02d", seconds/3600, seconds/60%60, seconds%60));
        return super.render(graphics);
    }
    private void line(String a, String b)
    { panelComponent.getChildren().add(LineComponent.builder().left(a).right(b == null ? "-" : b).build()); }
}