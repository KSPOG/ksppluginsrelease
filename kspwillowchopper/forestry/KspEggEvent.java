package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspEggEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin;

    public KspEggEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;
        return Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_PHEASANT_FORESTER)
                .nearest() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.PHEASANT);
        Rs2Walker.setTarget(null);

        if (Rs2Inventory.isFull()) {
            if (!plugin.ensureInventorySpace(1)) {
                return false;
            }
            sleepUntil(() -> !Rs2Inventory.isFull(), 3000);
        }

        while (validate()) {
            var forester = Microbot.getRs2NpcCache().query()
                    .withId(NpcID.GATHERING_EVENT_PHEASANT_FORESTER)
                    .nearest();

            if (forester == null) break;

            if (Rs2Inventory.contains("Pheasant egg")) {
                forester.click("Talk-to");
                sleepUntil(Rs2Dialogue::isInDialogue, 5000);
                while (Rs2Dialogue.isInDialogue()) {
                    Rs2Dialogue.clickContinue();
                }
                continue;
            }

            var nests = Microbot.getRs2TileObjectCache().query()
                    .where(obj -> obj.getId() == ObjectID.GATHERING_EVENT_PHEASANT_NEST02)
                    .toList();

            var pheasants = Microbot.getRs2NpcCache().query()
                    .withId(NpcID.GATHERING_EVENT_PHEASANT)
                    .toList();

            var emptyNests = nests.stream()
                    .filter(nest -> pheasants.stream()
                            .noneMatch(pheasant -> pheasant.getWorldLocation().equals(nest.getWorldLocation())))
                    .collect(Collectors.toList());

            var target = emptyNests.stream()
                    .min(Comparator.comparingInt(nest ->
                            nest.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                    .orElse(null);

            if (target != null) {
                target.click();
                Rs2Player.waitForAnimation();
            } else {
                sleepUntil(() -> false, 300);
            }
        }

        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
