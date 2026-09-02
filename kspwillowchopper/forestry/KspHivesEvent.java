package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspHivesEvent implements BlockingEvent {
    private static final long MAX_EVENT_MS = 90_000L;
    private static final String LOG_AMOUNT_PROMPT = "How many logs would you like to add";

    private final KspWillowChopperPlugin plugin;
    private final Set<Integer> completed = new HashSet<>();

    public KspHivesEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        return plugin.isForestryEventEnabled(KspForestryEvent.BEEHIVE)
                && Microbot.isLoggedIn()
                && plugin.isSelectedResourceCampfireBurnable()
                && Rs2Inventory.count(plugin.getSelectedResourceId()) > 1
                && findHive() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.BEEHIVE);
        completed.clear();
        long deadline = System.currentTimeMillis() + MAX_EVENT_MS;

        while (plugin.isForestryEventEnabled(KspForestryEvent.BEEHIVE)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            if (Rs2Widget.findWidget(LOG_AMOUNT_PROMPT, null, false) != null) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                plugin.markForestryInteraction(0L, "Beehive-space");
                sleepUntil(() -> Rs2Widget.findWidget(LOG_AMOUNT_PROMPT, null, false) == null, 3_000);
                continue;
            }

            if (Rs2Inventory.count(plugin.getSelectedResourceId()) <= 1) {
                break;
            }

            Rs2NpcModel hive = Microbot.getRs2NpcCache().query()
                    .where(npc -> isHive(npc.getId()) && !completed.contains(npc.getIndex()))
                    .nearest();

            if (hive == null) {
                if (findHive() == null) {
                    break;
                }
                sleep(250);
                continue;
            }

            if (!plugin.canStartForestryInteraction(hive.getHash(), "Build")) {
                sleep(120);
                continue;
            }

            if (!hive.click("Build")) {
                sleep(350);
                continue;
            }

            plugin.markForestryInteraction(hive.getHash(), "Build");
            int hiveIndex = hive.getIndex();
            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isMoving(), 2_500);
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 10_000);

            if (Microbot.getRs2NpcCache().query().where(npc -> npc.getIndex() == hiveIndex).count() == 0) {
                completed.add(hiveIndex);
            }
        }

        if (findHive() == null) {
            plugin.completeForestryEvent(KspForestryEvent.BEEHIVE);
        }
        return true;
    }

    private Rs2NpcModel findHive() {
        return Microbot.getRs2NpcCache().query().where(npc -> isHive(npc.getId())).nearest();
    }

    private boolean isHive(int id) {
        return id == NpcID.GATHERING_EVENT_BEES_BEEBOX_1
                || id == NpcID.GATHERING_EVENT_BEES_BEEBOX_2;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
