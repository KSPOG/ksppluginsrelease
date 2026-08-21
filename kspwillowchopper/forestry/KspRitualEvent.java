package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;


import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspRitualEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin;

    public KspRitualEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;

        return Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD)
                .nearest() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.ENCHANTMENT_RITUAL);
        plugin.ensureInventorySpace(1);

        while (validate()) {
            Rs2NpcModel target = solveCircles(plugin.ritualCircles);
            if (target == null) {
                sleepUntil(() -> false, 350);
                continue;
            }

            if (!Rs2Player.getWorldLocation().equals(target.getWorldLocation())) {
                if (!plugin.moveDirectlyToForestryTarget(
                        target.getHash(),
                        target.getWorldLocation(),
                        target.getMinimapLocation(),
                        target.getCanvasTilePoly())) {
                    sleepUntil(() -> false, 150);
                    continue;
                }
                sleepUntil(() -> Rs2Player.getWorldLocation().equals(target.getWorldLocation()) || !validate(), 2500);
            } else {
                sleepUntil(() -> false, 400);
            }
        }

        plugin.completeForestryEvent(KspForestryEvent.ENCHANTMENT_RITUAL);
        return true;
    }

    private Rs2NpcModel solveCircles(List<Rs2NpcModel> circles) {
        if (circles.size() != 5) return null;

        int xor = 0;
        for (Rs2NpcModel npc : circles) {
            int offset = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
            int shape = offset / 4;
            int color = offset % 4;
            int value = (16 << shape) | (1 << color);
            xor ^= value;
        }

        for (Rs2NpcModel npc : circles) {
            int offset = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
            int shape = offset / 4;
            int color = offset % 4;
            int value = (16 << shape) | (1 << color);
            if ((value & xor) == value) return npc;
        }

        return null;
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
