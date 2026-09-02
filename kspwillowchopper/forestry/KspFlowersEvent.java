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
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspFlowersEvent implements BlockingEvent {
    private static final long MAX_EVENT_MS = 90_000L;

    private final KspWillowChopperPlugin plugin;

    public KspFlowersEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!plugin.isForestryEventEnabled(KspForestryEvent.FLOWERING_TREE) || !Microbot.isLoggedIn()) {
            return false;
        }
        return findAnyBush() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.FLOWERING_TREE);
        long deadline = System.currentTimeMillis() + MAX_EVENT_MS;

        while (plugin.isForestryEventEnabled(KspForestryEvent.FLOWERING_TREE)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            Rs2NpcModel target = Microbot.getRs2NpcCache().query()
                    .where(npc -> npc.getName() != null
                            && isFloweringBush(npc.getId())
                            && npc.getAnimation() == -1)
                    .nearest();

            if (target == null) {
                if (findAnyBush() == null) {
                    break;
                }
                sleep(250);
                continue;
            }

            if (!plugin.canStartForestryInteraction(target.getHash(), "Tend-to")) {
                sleep(120);
                continue;
            }

            if (!target.click("Tend-to")) {
                sleep(250);
                continue;
            }

            plugin.markForestryInteraction(target.getHash(), "Tend-to");
            Rs2Player.waitForAnimation(2_000);
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5_000);
        }

        if (!validate()) {
            plugin.completeForestryEvent(KspForestryEvent.FLOWERING_TREE);
        }
        return true;
    }

    private Rs2NpcModel findAnyBush() {
        return Microbot.getRs2NpcCache().query()
                .where(npc -> npc.getName() != null && isFloweringBush(npc.getId()))
                .nearest();
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
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
