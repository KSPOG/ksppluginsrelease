package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

public class KspFoxEvent implements BlockingEvent {
    private static final long MAX_EVENT_MS = 90_000L;

    private final KspWillowChopperPlugin plugin;

    public KspFoxEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!plugin.isForestryEventEnabled(KspForestryEvent.POACHERS) || !Microbot.isLoggedIn()) {
            return false;
        }
        return findFox() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.POACHERS);
        plugin.ensureInventorySpace(1);
        long deadline = System.currentTimeMillis() + MAX_EVENT_MS;

        while (plugin.isForestryEventEnabled(KspForestryEvent.POACHERS)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            if (findFox() == null) {
                break;
            }

            Rs2NpcModel trap = Microbot.getRs2NpcCache().query()
                    .withId(NpcID.GATHERING_EVENT_POACHERS_TRAP)
                    .nearest();
            if (trap == null) {
                sleep(250);
                continue;
            }

            if (!plugin.canStartForestryInteraction(trap.getHash(), "Disarm")) {
                sleep(120);
                continue;
            }

            if (trap.click("Disarm")) {
                plugin.markForestryInteraction(trap.getHash(), "Disarm");
                Rs2Player.waitForAnimation(1_500);
            } else {
                sleep(300);
            }
        }

        if (!validate()) {
            plugin.completeForestryEvent(KspForestryEvent.POACHERS);
        }
        return true;
    }

    private Rs2NpcModel findFox() {
        Rs2NpcModel fox = Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_POACHERS_FOX_OUTDOORS)
                .nearest();
        if (fox != null) {
            return fox;
        }
        return Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_POACHERS_FOX_INDOORS)
                .nearest();
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
