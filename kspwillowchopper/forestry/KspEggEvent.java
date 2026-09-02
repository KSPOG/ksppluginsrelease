package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Comparator;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspEggEvent implements BlockingEvent {
    private static final long MAX_EVENT_MS = 90_000L;

    private final KspWillowChopperPlugin plugin;

    public KspEggEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        return plugin.isForestryEventEnabled(KspForestryEvent.PHEASANT)
                && Microbot.isLoggedIn()
                && findForester() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.PHEASANT);
        long deadline = System.currentTimeMillis() + MAX_EVENT_MS;

        if (Rs2Inventory.isFull() && !plugin.ensureInventorySpace(1)) {
            return true;
        }

        while (plugin.isForestryEventEnabled(KspForestryEvent.PHEASANT)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            var forester = findForester();
            if (forester == null) {
                break;
            }

            if (Rs2Inventory.contains("Pheasant egg")) {
                if (!plugin.canStartForestryInteraction(forester.getHash(), "Talk-to")) {
                    sleep(120);
                    continue;
                }

                if (!forester.click("Talk-to")) {
                    sleep(300);
                    continue;
                }

                plugin.markForestryInteraction(forester.getHash(), "Talk-to");
                sleepUntil(Rs2Dialogue::isInDialogue, 4_000);
                long dialogueDeadline = System.currentTimeMillis() + 8_000L;
                while (Rs2Dialogue.isInDialogue() && System.currentTimeMillis() < dialogueDeadline) {
                    Rs2Dialogue.clickContinue();
                    sleep(120);
                }
                continue;
            }

            Rs2TileObjectModel targetNest = findUnoccupiedNest();
            if (targetNest == null) {
                sleep(250);
                continue;
            }

            if (!plugin.canStartForestryInteraction(targetNest.getHash(), "Nest")) {
                sleep(120);
                continue;
            }

            if (!targetNest.click()) {
                sleep(300);
                continue;
            }

            plugin.markForestryInteraction(targetNest.getHash(), "Nest");
            Rs2Player.waitForAnimation(2_000);
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5_000);
        }

        if (!validate()) {
            plugin.completeForestryEvent(KspForestryEvent.PHEASANT);
        }
        return true;
    }

    private net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel findForester() {
        return Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_PHEASANT_FORESTER)
                .nearest();
    }

    private Rs2TileObjectModel findUnoccupiedNest() {
        List<Rs2TileObjectModel> nests = Microbot.getRs2TileObjectCache().query()
                .where(object -> object.getId() == ObjectID.GATHERING_EVENT_PHEASANT_NEST02)
                .toList();
        var pheasants = Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_PHEASANT)
                .toList();

        return nests.stream()
                .filter(nest -> pheasants.stream().noneMatch(pheasant ->
                        pheasant.getWorldLocation().equals(nest.getWorldLocation())))
                .min(Comparator.comparingInt(nest ->
                        nest.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
