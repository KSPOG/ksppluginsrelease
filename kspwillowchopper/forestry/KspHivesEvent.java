package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperScript;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspHivesEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin;
    private final Set<Integer> completed = new HashSet<>();

    public KspHivesEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;

        boolean hiveExists = !Microbot.getRs2NpcCache().query()
                .where(x -> x.getId() == NpcID.GATHERING_EVENT_BEES_BEEBOX_1
                        || x.getId() == NpcID.GATHERING_EVENT_BEES_BEEBOX_2)
                .toList()
                .isEmpty();

        return hiveExists && Rs2Inventory.count(KspWillowChopperScript.WILLOW_LOG_ID) > 1;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.BEEHIVE);
        completed.clear();
        Rs2Walker.setTarget(null);

        while (validate()) {
            if (Rs2Widget.findWidget("How many logs would you like to add", null, false) != null) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isAnimating(), 6000);
                continue;
            }

            var hive = Microbot.getRs2NpcCache().query()
                    .where(x -> (x.getId() == NpcID.GATHERING_EVENT_BEES_BEEBOX_1
                            || x.getId() == NpcID.GATHERING_EVENT_BEES_BEEBOX_2)
                            && !completed.contains(x.getIndex()))
                    .nearest();

            if (hive == null) break;

            int before = Rs2Inventory.count(KspWillowChopperScript.WILLOW_LOG_ID);
            if (before <= 1) break;

            if (hive.click("Build")) {
                sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isAnimating(), 3000);
                sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isAnimating(), 15000);

                if (Microbot.getRs2NpcCache().query()
                        .where(x -> x.getIndex() == hive.getIndex())
                        .count() == 0) {
                    completed.add(hive.getIndex());
                }
            } else {
                sleepUntil(() -> false, 1000);
            }
        }

        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
