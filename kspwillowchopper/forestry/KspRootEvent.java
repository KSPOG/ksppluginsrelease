package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspTileObjectSupport;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspRootEvent implements BlockingEvent {
    private static final long MAX_EVENT_MS = 90_000L;

    private final KspWillowChopperPlugin plugin;

    public KspRootEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try {
            if (!plugin.isForestryEventEnabled(KspForestryEvent.RISING_ROOTS) || !Microbot.isLoggedIn()) {
                return false;
            }
            return findBestRoot() != null;
        } catch (Exception ex) {
            log.debug("Rising Roots validation failed", ex);
            return false;
        }
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.RISING_ROOTS);
        long deadline = System.currentTimeMillis() + MAX_EVENT_MS;

        while (plugin.isForestryEventEnabled(KspForestryEvent.RISING_ROOTS)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            Rs2TileObjectModel root = findBestRoot();
            if (root == null) {
                break;
            }

            if (!plugin.canStartForestryInteraction(root.getHash(), "Chop down")) {
                sleep(120);
                continue;
            }

            if (!root.click("Chop down")) {
                sleep(250);
                continue;
            }

            plugin.markForestryInteraction(root.getHash(), "Chop down");
            Rs2Player.waitForAnimation(4_000);
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 8_000);
        }

        if (!validate()) {
            plugin.completeForestryEvent(KspForestryEvent.RISING_ROOTS);
        }
        return true;
    }

    private Rs2TileObjectModel findBestRoot() {
        Rs2TileObjectModel special = findRoot(ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL);
        if (special != null && KspTileObjectSupport.hasAction(special, "Chop down")) {
            return special;
        }

        Rs2TileObjectModel normal = findRoot(ObjectID.GATHERING_EVENT_RISING_ROOTS);
        return normal != null && KspTileObjectSupport.hasAction(normal, "Chop down") ? normal : null;
    }

    private Rs2TileObjectModel findRoot(int id) {
        return plugin.rs2TileObjectCache.query().where(object -> object.getId() == id).nearest();
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
