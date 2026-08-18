package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Comparator;

@Slf4j
public class KspEntlingsEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin;

    public KspEntlingsEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;
        return !Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_ENTLINGS_NPC_01)
                .toList()
                .isEmpty();
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.FRIENDLY_ENTLINGS);

        if (!plugin.ensureInventorySpace(2)) {
            return true;
        }

        while (validate()) {
            var entlings = Microbot.getRs2NpcCache().query()
                    .withId(NpcID.GATHERING_EVENT_ENTLINGS_NPC_01)
                    .toList();

            entlings.sort(Comparator.comparingInt(entling ->
                    entling.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())));

            for (Rs2NpcModel entling : entlings) {
                String request = entling.getOverheadText();
                if (request == null) continue;

                String action;
                switch (request) {
                    case "Breezy at the back!":
                    case "Short back and sides!":
                        action = "Prune-back";
                        break;
                    case "A leafy mullet!":
                    case "Short on top!":
                        action = "Prune-top";
                        break;
                    default:
                        continue;
                }

                entling.click(action);
                Rs2Player.waitForAnimation(1000);
            }
        }

        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
