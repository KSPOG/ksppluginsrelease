package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspRitualEvent implements BlockingEvent {
    private static final long MAX_EVENT_MS = 90_000L;

    private final KspWillowChopperPlugin plugin;

    public KspRitualEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        return plugin.isForestryEventEnabled(KspForestryEvent.ENCHANTMENT_RITUAL)
                && Microbot.isLoggedIn()
                && findDryad() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.ENCHANTMENT_RITUAL);
        plugin.ensureInventorySpace(1);
        long deadline = System.currentTimeMillis() + MAX_EVENT_MS;

        while (plugin.isForestryEventEnabled(KspForestryEvent.ENCHANTMENT_RITUAL)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            if (findDryad() == null) {
                break;
            }

            net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel target = solveCircles(plugin.ritualCircles);
            if (target == null) {
                sleep(250);
                continue;
            }

            if (Rs2Player.getWorldLocation().equals(target.getWorldLocation())) {
                sleep(250);
                continue;
            }

            if (!plugin.moveDirectlyToForestryTarget(
                    target.getHash(),
                    target.getWorldLocation(),
                    target.getMinimapLocation(),
                    target.getCanvasTilePoly())) {
                sleep(120);
                continue;
            }

            sleepUntil(() -> Rs2Player.getWorldLocation().equals(target.getWorldLocation())
                    || findDryad() == null, 3_500);
        }

        if (!validate()) {
            plugin.completeForestryEvent(KspForestryEvent.ENCHANTMENT_RITUAL);
        }
        return true;
    }

    private Rs2NpcModel findDryad() {
        return Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD)
                .nearest();
    }

    private net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel solveCircles(
            List<net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel> circles) {
        if (circles == null || circles.size() != 5) {
            return null;
        }

        int xor = 0;
        for (net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel circle : circles) {
            if (circle == null) return null;
            xor ^= ritualValue(circle);
        }

        for (net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel circle : circles) {
            int value = ritualValue(circle);
            if ((value & xor) == value) {
                return circle;
            }
        }
        return null;
    }

    private int ritualValue(net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel npc) {
        int offset = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
        return (16 << (offset / 4)) | (1 << (offset % 4));
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
