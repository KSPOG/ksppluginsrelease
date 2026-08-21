package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspTileObjectSupport;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
@RequiredArgsConstructor
public class KspRootEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin;

    @Override
    public boolean validate() {
        try {
            if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;

            var special = findRoot(ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL);
            if (special != null && KspTileObjectSupport.hasAction(special, "Chop down")) {
                return true;
            }

            var normal = findRoot(ObjectID.GATHERING_EVENT_RISING_ROOTS);
            return normal != null && KspTileObjectSupport.hasAction(normal, "Chop down");
        } catch (Exception ex) {
            log.error("Rising Roots validation failed", ex);
            return false;
        }
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.RISING_ROOTS);

        while (validate()) {
            var root = findRoot(ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL);
            if (root == null) {
                root = findRoot(ObjectID.GATHERING_EVENT_RISING_ROOTS);
            }
            if (root == null) {
                continue;
            }

            if (!plugin.canStartForestryInteraction(root.getHash(), "Chop down")) {
                sleepUntil(() -> false, 150);
                continue;
            }

            root.click("Chop down");
            plugin.markForestryInteraction(root.getHash(), "Chop down");
            Rs2Player.waitForAnimation(5000);
            sleepUntil(() -> !Rs2Player.isInteracting(), 40000);
        }

        plugin.completeForestryEvent(KspForestryEvent.RISING_ROOTS);
        return true;
    }

    private net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel findRoot(int id) {
        return plugin.rs2TileObjectCache.query().where(x -> x.getId() == id).nearest();
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
