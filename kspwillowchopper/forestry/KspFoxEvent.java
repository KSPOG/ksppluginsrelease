package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@RequiredArgsConstructor
public class KspFoxEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin;

    @Override
    public boolean validate() {
        if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;

        return Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_POACHERS_FOX_OUTDOORS)
                .nearest() != null
                || Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_POACHERS_FOX_INDOORS)
                .nearest() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.POACHERS);
        plugin.ensureInventorySpace(1);

        while (validate()) {
            var trap = Microbot.getRs2NpcCache().query()
                    .withId(NpcID.GATHERING_EVENT_POACHERS_TRAP)
                    .nearest();

            if (trap == null) {
                sleepUntil(() -> false, 250);
                continue;
            }

            if (!plugin.canStartForestryInteraction(trap.getHash(), "Disarm")) {
                sleepUntil(() -> false, 150);
                continue;
            }

            trap.click("Disarm");
            plugin.markForestryInteraction(trap.getHash(), "Disarm");
            Rs2Player.waitForAnimation(1000);
        }

        plugin.completeForestryEvent(KspForestryEvent.POACHERS);
        return true;
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
