package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.awt.Polygon;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspLeprechaunEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin;

    public KspLeprechaunEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;

        return Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN)
                .nearest() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.LEPRECHAUN);

        /*
         * The verified behavior is to use End of Rainbow tiles for the temporary boost.
         * A stable Microbot NPC deposit action string could not be confirmed, so no guessed
         * menu action is hardcoded here.
         */
        while (validate()) {
            var rainbow = Microbot.getRs2TileObjectCache().query()
                    .withId(ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_RAINBOW)
                    .nearest();

            if (rainbow == null) {
                sleepUntil(() -> false, 300);
                continue;
            }

            if (!Rs2Player.getWorldLocation().equals(rainbow.getWorldLocation())) {
                if (rainbow.getMinimapLocation() != null) {
                    Microbot.getMouse().click(rainbow.getMinimapLocation());
                } else {
                    Polygon poly = rainbow.getCanvasTilePoly();
                    if (poly != null) {
                        Microbot.getMouse().click(poly.getBounds());
                    }
                }
                sleepUntil(() -> Rs2Player.getWorldLocation().equals(rainbow.getWorldLocation()) || !validate(), 2500);
            } else {
                sleepUntil(() -> false, 350);
            }
        }

        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
