package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspRootEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin;

    public KspRootEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try {
            if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;

            var special = plugin.rs2TileObjectCache.query()
                    .where(x -> x.getId() == ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL)
                    .nearest();

            if (special != null && Rs2GameObject.hasAction(special.getObjectComposition(), "Chop down")) {
                return true;
            }

            var normal = plugin.rs2TileObjectCache.query()
                    .where(x -> x.getId() == ObjectID.GATHERING_EVENT_RISING_ROOTS)
                    .nearest();

            return normal != null && Rs2GameObject.hasAction(normal.getObjectComposition(), "Chop down");
        } catch (Exception ex) {
            log.error("Rising Roots validation failed", ex);
            return false;
        }
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.RISING_ROOTS);

        while (validate()) {
            var root = plugin.rs2TileObjectCache.query()
                    .where(x -> x.getId() == ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL)
                    .nearest();

            if (root == null) {
                root = plugin.rs2TileObjectCache.query()
                        .where(x -> x.getId() == ObjectID.GATHERING_EVENT_RISING_ROOTS)
                        .nearest();
            }

            if (root == null) {
                continue;
            }

            if (Rs2Player.isInteracting() && Rs2Player.getInteracting() != null) {
                Actor interacting = Microbot.getClient().getLocalPlayer().getInteracting();
                if (interacting != null && interacting.getWorldLocation().equals(root.getWorldLocation())) {
                    continue;
                }
            }

            root.click("Chop down");
            Rs2Player.waitForAnimation(5000);
            sleepUntil(() -> !Rs2Player.isInteracting(), 40000);
        }

        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
