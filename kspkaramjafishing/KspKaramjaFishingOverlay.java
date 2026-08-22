package net.runelite.client.plugins.microbot.kspkaramjafishing;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class KspKaramjaFishingOverlay extends OverlayPanel
{
    private final KspKaramjaFishingPlugin plugin;
    private final KspKaramjaFishingConfig config;

    @Inject
    KspKaramjaFishingOverlay(KspKaramjaFishingPlugin plugin, KspKaramjaFishingConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        KspKaramjaFishingScript s = plugin.getScript();
        panelComponent.setPreferredSize(new Dimension(245, 205));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 175));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Karamja Fishing v" + KspKaramjaFishingPlugin.VERSION)
                .color(Color.CYAN).build());

        line("Mode", config.mode().toString());
        line("Status", s.getStatus());
        line("Runtime", plugin.runtime());
        line("Caught", n(s.getCaught()));
        line("Inventory fish", String.valueOf(s.fishCount()));
        line("Trips", String.valueOf(s.getTrips()));
        line("Fishing XP", n(plugin.xp()));
        line("Fishing XP/h", n(plugin.xpHour()));
        line("Coins", n(s.coins()));
        line(config.mode() == KspKaramjaFishingConfig.Mode.LOBSTER ? "Lobster pot" : "Harpoon",
                s.isToolReady() ? "Ready" : "Missing");

        return super.render(graphics);
    }

    private void line(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(left + ":").right(right).build());
    }

    private String n(int value)
    {
        return String.format("%,d", value);
    }
}
