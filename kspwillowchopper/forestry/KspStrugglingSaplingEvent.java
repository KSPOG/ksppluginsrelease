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
import net.runelite.client.plugins.microbot.kspwillowchopper.KspTileObjectSupport;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspStrugglingSaplingEvent implements BlockingEvent {
    private static final int FORESTRY_DISTANCE = 15;
    private static final long MAX_EVENT_MS = 120_000L;

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
    private final Set<Integer>[] triedByStage = new Set[]{
            new HashSet<>(), new HashSet<>(), new HashSet<>()
    };

    public KspStrugglingSaplingEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!plugin.isForestryEventEnabled(KspForestryEvent.STRUGGLING_SAPLING)
                || !Microbot.isLoggedIn()) {
            return false;
        }
        return findSapling() != null;
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.STRUGGLING_SAPLING);
        if (!plugin.ensureInventorySpace(5)) {
            return true;
        }

        long deadline = System.currentTimeMillis() + MAX_EVENT_MS;
        while (plugin.isForestryEventEnabled(KspForestryEvent.STRUGGLING_SAPLING)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            Rs2TileObjectModel sapling = findSapling();
            if (sapling == null) {
                break;
            }

            if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE3)) {
                if (!plugin.canStartForestryInteraction(sapling.getHash(), "Add-mulch")) {
                    sleep(120);
                    continue;
                }

                if (!sapling.click("Add-mulch")) {
                    sleep(250);
                    continue;
                }

                plugin.markForestryInteraction(sapling.getHash(), "Add-mulch");
                Rs2Player.waitForAnimation(2_000);
                sleepUntil(() -> !Rs2Player.isAnimating() || !validate(), 4_000);
                continue;
            }

            int stage = currentStage();
            List<Rs2TileObjectModel> ingredients = availableIngredients();
            if (ingredients.isEmpty()) {
                sleep(250);
                continue;
            }

            Rs2TileObjectModel candidate = chooseIngredient(stage, ingredients);
            if (candidate == null) {
                sleep(250);
                continue;
            }

            if (!plugin.canStartForestryInteraction(candidate.getHash(), "Collect")) {
                sleep(120);
                continue;
            }

            if (!candidate.click("Collect")) {
                sleep(300);
                continue;
            }

            if (learnedIds[stage] == -1) {
                triedByStage[stage].add(candidate.getId());
            }
            plugin.markForestryInteraction(candidate.getHash(), "Collect");
            Rs2Player.waitForAnimation(2_000);
            sleepUntil(() -> currentStage() != stage
                    || learnedIds[stage] != -1
                    || !validate(), 4_000);
        }

        if (!validate()) {
            plugin.completeForestryEvent(KspForestryEvent.STRUGGLING_SAPLING);
        }
        return true;
    }

    private Rs2TileObjectModel chooseIngredient(int stage, List<Rs2TileObjectModel> ingredients) {
        int learnedId = learnedIds[stage];
        if (learnedId != -1) {
            return ingredients.stream()
                    .filter(item -> item.getId() == learnedId)
                    .findFirst()
                    .orElse(null);
        }

        Set<Integer> tried = triedByStage[stage];
        Rs2TileObjectModel candidate = ingredients.stream()
                .filter(item -> !tried.contains(item.getId()))
                .findFirst()
                .orElse(null);

        if (candidate == null) {
            tried.clear();
            candidate = ingredients.stream().findFirst().orElse(null);
        }
        return candidate;
    }

    public void learnFromChatMessage(String message) {
        if (message == null) {
            return;
        }

        String lower = message.toLowerCase();
        int stage = lower.contains("first") ? 0
                : lower.contains("second") ? 1
                : lower.contains("third") ? 2
                : -1;
        if (stage < 0) {
            return;
        }

        for (GameObject object : plugin.saplingIngredients) {
            String name = plugin.getObjectName(object);
            if (name != null && lower.contains(name.toLowerCase())) {
                learnedIds[stage] = object.getId();
                learnedNames[stage] = name;
                triedByStage[stage].clear();
                log.info("Learned Struggling Sapling stage {} ingredient: {}", stage + 1, name);
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
        return new String[]{learnedNames[0], learnedNames[1], learnedNames[2]};
    }

    private int currentStage() {
        if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE2)) {
            return 2;
        }
        if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE1)) {
            return 1;
        }
        return 0;
    }

    private Rs2TileObjectModel findSapling() {
        return Microbot.getRs2TileObjectCache().query()
                .withName("Struggling sapling")
                .toListOnClientThread()
                .stream()
                .filter(object -> KspTileObjectSupport.hasAction(object, "Add-mulch"))
                .filter(object -> object.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE)
                .findFirst()
                .orElse(null);
    }

    private List<Rs2TileObjectModel> availableIngredients() {
        return Microbot.getRs2TileObjectCache().query()
                .where(object -> INGREDIENT_IDS.contains(object.getId()))
                .toListOnClientThread()
                .stream()
                .filter(object -> KspTileObjectSupport.hasAction(object, "Collect"))
                .collect(Collectors.toList());
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
