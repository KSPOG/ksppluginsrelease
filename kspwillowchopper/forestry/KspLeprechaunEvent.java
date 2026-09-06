package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspLeprechaunEvent implements BlockingEvent {
    private static final long MAX_EVENT_MS = 90_000L;

    private final KspWillowChopperPlugin plugin;

    public KspLeprechaunEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        return plugin.isForestryEventEnabled(KspForestryEvent.LEPRECHAUN)
                && Microbot.isLoggedIn()
                && findLeprechaun() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.LEPRECHAUN);
        long deadline = System.currentTimeMillis() + MAX_EVENT_MS;

        while (plugin.isForestryEventEnabled(KspForestryEvent.LEPRECHAUN)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            if (findLeprechaun() == null) {
                break;
            }

            Rs2TileObjectModel rainbow = Microbot.getRs2TileObjectCache().query()
                    .withId(ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_RAINBOW)
                    .nearest();
            if (rainbow == null) {
                sleep(250);
                continue;
            }

            if (Rs2Player.getWorldLocation().equals(rainbow.getWorldLocation())) {
                sleep(250);
                continue;
            }

            if (!plugin.moveDirectlyToForestryTarget(
                    rainbow.getHash(),
                    rainbow.getWorldLocation(),
                    rainbow.getMinimapLocation(),
                    rainbow.getCanvasTilePoly())) {
                sleep(120);
                continue;
            }

            sleepUntil(() -> Rs2Player.getWorldLocation().equals(rainbow.getWorldLocation())
                    || findLeprechaun() == null, 3_500);
        }

        if (!validate()) {
            plugin.completeForestryEvent(KspForestryEvent.LEPRECHAUN);
        }
        return true;
    }

    private Rs2NpcModel findLeprechaun() {
        return Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN)
                .nearest();
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
