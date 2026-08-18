package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspStrugglingSaplingEvent implements BlockingEvent {
    private static final int FORESTRY_DISTANCE = 15;

    private static final List<Integer> INGREDIENT_IDS = List.of(
            ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1,
            ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2,
            ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3,
            ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A,
            ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B,
            ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C,
            ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5
    );

    private final KspWillowChopperPlugin plugin;
    private final int[] learnedIds = {-1, -1, -1};
    private final String[] learnedNames = {"?", "?", "?"};

    @SuppressWarnings("unchecked")
    private final Set<Integer>[] triedByStage = new Set[] {
            new HashSet<>(), new HashSet<>(), new HashSet<>()
    };

    public KspStrugglingSaplingEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try {
            if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) return false;

            return Microbot.getRs2TileObjectCache()
                    .query()
                    .withName("Struggling sapling")
                    .toListOnClientThread()
                    .stream()
                    .anyMatch(obj ->
                            Rs2GameObject.hasAction(obj.getObjectComposition(), "Add-mulch")
                                    && obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE);
        } catch (Exception ex) {
            log.error("Struggling Sapling validation failed", ex);
            return false;
        }
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.STRUGGLING_SAPLING);

        if (!plugin.ensureInventorySpace(5)) {
            return true;
        }

        while (validate()) {
            Rs2TileObjectModel sapling = findSapling();
            if (sapling == null) {
                sleepUntil(() -> false, 300);
                continue;
            }

            if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE3)) {
                sapling.click("Add-mulch");
                Rs2Player.waitForAnimation();
                sleepUntil(() -> !Rs2Player.isAnimating(), 5000);
                continue;
            }

            int stage = currentStage();
            List<Rs2TileObjectModel> ingredients = availableIngredients();

            if (ingredients.isEmpty()) {
                sleepUntil(() -> false, 300);
                continue;
            }

            int knownId = learnedIds[stage];

            if (knownId != -1) {
                // Once a correct stage ingredient is discovered, always reuse it.
                Rs2TileObjectModel known = ingredients.stream()
                        .filter(item -> item.getId() == knownId)
                        .findFirst()
                        .orElse(null);

                if (known == null) {
                    // Never degrade back to random once the optimal stage is known.
                    sleepUntil(() -> false, 400);
                    continue;
                }

                known.click("Collect");
                Rs2Player.waitForAnimation();
                sleepUntil(() -> currentStage() != stage || !validate(), 4000);
                continue;
            }

            Set<Integer> tried = triedByStage[stage];
            Rs2TileObjectModel candidate = ingredients.stream()
                    .filter(item -> !tried.contains(item.getId()))
                    .findFirst()
                    .orElse(null);

            if (candidate == null) {
                tried.clear();
                sleepUntil(() -> false, 300);
                continue;
            }

            tried.add(candidate.getId());
            candidate.click("Collect");
            Rs2Player.waitForAnimation();
            sleepUntil(() -> currentStage() != stage || learnedIds[stage] != -1 || !validate(), 4000);
        }

        plugin.incrementForestryEventCompleted();
        return true;
    }

    public void learnFromChatMessage(String message) {
        if (message == null) return;

        String lower = message.toLowerCase();
        int stage = lower.contains("first") ? 0
                : lower.contains("second") ? 1
                : lower.contains("third") ? 2
                : -1;

        if (stage < 0) return;

        for (GameObject object : plugin.saplingIngredients) {
            String name = plugin.getObjectName(object);
            if (name != null && lower.contains(name.toLowerCase())) {
                learnedIds[stage] = object.getId();
                learnedNames[stage] = name;
                triedByStage[stage].clear();
                log.info("Learned optimal sapling stage {}: {}", stage + 1, name);
                return;
            }
        }
    }

    public void resetLearnedCombination() {
        for (int i = 0; i < 3; i++) {
            learnedIds[i] = -1;
            learnedNames[i] = "?";
            triedByStage[i].clear();
        }
    }

    public boolean hasCompleteCombination() {
        return learnedIds[0] != -1 && learnedIds[1] != -1 && learnedIds[2] != -1;
    }

    public String[] getLearnedNames() {
        return new String[] {learnedNames[0], learnedNames[1], learnedNames[2]};
    }

    private int currentStage() {
        if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE2)) return 2;
        if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE1)) return 1;
        return 0;
    }

    private Rs2TileObjectModel findSapling() {
        return Microbot.getRs2TileObjectCache()
                .query()
                .withName("Struggling sapling")
                .toListOnClientThread()
                .stream()
                .filter(obj ->
                        Rs2GameObject.hasAction(obj.getObjectComposition(), "Add-mulch")
                                && obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE)
                .findFirst()
                .orElse(null);
    }

    private List<Rs2TileObjectModel> availableIngredients() {
        return Microbot.getRs2TileObjectCache()
                .query()
                .where(obj -> INGREDIENT_IDS.contains(obj.getId()))
                .toListOnClientThread()
                .stream()
                .filter(obj -> Rs2GameObject.hasAction(obj.getObjectComposition(), "Collect"))
                .collect(Collectors.toList());
    }

    @Override
    public BlockingEventPriority priority() { return BlockingEventPriority.NORMAL; }
}
