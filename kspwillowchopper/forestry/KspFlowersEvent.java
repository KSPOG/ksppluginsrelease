package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspFlowersEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin;

    public KspFlowersEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;

        return !Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getName() != null && isFloweringBush(npc.getId()))
                .toList()
                .isEmpty();
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.FLOWERING_TREE);
        Rs2Walker.setTarget(null);
        plugin.ensureInventorySpace(3);

        while (validate()) {
            var flowers = Microbot.getRs2NpcCache().query()
                    .where(npc -> npc.getName() != null && isFloweringBush(npc.getId()))
                    .toList();

            var target = flowers.stream()
                    .filter(flower -> flower.getAnimation() == -1)
                    .findFirst()
                    .orElse(null);

            if (target == null) {
                sleepUntil(() -> false, 500);
                continue;
            }

            if (target.click("Tend-to")) {
                Rs2Player.waitForAnimation();
                sleepUntil(() -> !Rs2Player.isInteracting(), 8000);
            } else {
                sleepUntil(() -> false, 300);
            }
        }

        plugin.incrementForestryEventCompleted();
        return true;
    }

    private boolean isFloweringBush(int id) {
        return id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL01
                || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL02
                || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL03
                || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL04
                || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL05
                || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL06
                || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL07
                || id == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL08;
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
