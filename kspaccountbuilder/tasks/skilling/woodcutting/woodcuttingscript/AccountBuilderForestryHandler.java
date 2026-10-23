package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.woodcuttingscript;

import net.runelite.api.Point;
import java.awt.Polygon;
import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treelevel.TreeLevel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Local Forestry priority handler for Account Builder Woodcutting.
 *
 * This deliberately mirrors KSP Chopper's local Forestry model: Forestry owns
 * the Woodcutting loop while an event is present, interactions are throttled
 * and target/action deduped, and nothing is registered in Microbot's global
 * BlockingEventManager.
 */
final class AccountBuilderForestryHandler {
    private static final long INTERACTION_COOLDOWN_MS = 900L;
    private static final long SAME_ACTION_RETRY_MS = 2_500L;
    private static final int EVENT_DISTANCE = 15;
    private static final String LOG_AMOUNT_PROMPT = "How many logs would you like to add";

    private long lastInteractionAtMs;
    private long lastInteractionKey = Long.MIN_VALUE;
    private final Map<Integer, Integer> entlingPhaseByIndex = new HashMap<>();
    @SuppressWarnings("unchecked")
    private final Set<Integer>[] saplingTriedByStage = new Set[]{
            new HashSet<>(), new HashSet<>(), new HashSet<>()
    };

    boolean runIfNeeded(TreeLevel targetTree) {
        if (!Microbot.isLoggedIn() || Rs2Player.getWorldLocation() == null) return false;

        if (handleRoots()) return true;
        if (handleSapling(targetTree)) return true;
        if (handleEntlings(targetTree)) return true;
        if (handleHives(targetTree)) return true;
        if (handlePheasant(targetTree)) return true;
        if (handleFox()) return true;
        if (handleRitual(targetTree)) return true;
        if (handleLeprechaun()) return true;
        return handleFlowers();
    }

    void reset() {
        lastInteractionAtMs = 0L;
        lastInteractionKey = Long.MIN_VALUE;
        entlingPhaseByIndex.clear();
        for (Set<Integer> tried : saplingTriedByStage) tried.clear();
    }

    private boolean handleRoots() {
        Rs2TileObjectModel root = nearestObject(ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL, "Chop down");
        if (root == null) root = nearestObject(ObjectID.GATHERING_EVENT_RISING_ROOTS, "Chop down");
        if (root == null) return false;

        Microbot.status = "Forestry: Rising Roots";
        clickObject(root, "Chop down");
        return true;
    }

    private boolean handleSapling(TreeLevel tree) {
        Rs2TileObjectModel sapling = Microbot.getRs2TileObjectCache().query()
                .withName("Struggling sapling")
                .toListOnClientThread().stream()
                .filter(x -> x != null && near(x.getWorldLocation()))
                .filter(x -> hasAction(x, "Add-mulch"))
                .findFirst().orElse(null);
        if (sapling == null) return false;

        Microbot.status = "Forestry: Struggling Sapling";
        ensureInventorySpace(tree, 5);

        if (Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE3)) {
            clickObject(sapling, "Add-mulch");
            return true;
        }

        int stage = Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE2) ? 2
                : Rs2Inventory.contains(ItemID.GATHERING_EVENT_SAPLING_MULCH_STAGE1) ? 1 : 0;

        List<Integer> ids = List.of(
                ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1,
                ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2,
                ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3,
                ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A,
                ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B,
                ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C,
                ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5);

        Rs2TileObjectModel ingredient = Microbot.getRs2TileObjectCache().query()
                .where(x -> x != null && ids.contains(x.getId()) && near(x.getWorldLocation()) && hasAction(x, "Collect"))
                .toListOnClientThread().stream()
                .filter(x -> !saplingTriedByStage[stage].contains(x.getId()))
                .findFirst().orElse(null);

        if (ingredient == null) {
            saplingTriedByStage[stage].clear();
            ingredient = Microbot.getRs2TileObjectCache().query()
                    .where(x -> x != null && ids.contains(x.getId()) && near(x.getWorldLocation()) && hasAction(x, "Collect"))
                    .nearestOnClientThread();
        }

        if (ingredient != null && canInteract(ingredient.getHash(), "Collect")) {
            if (ingredient.click("Collect")) {
                saplingTriedByStage[stage].add(ingredient.getId());
                markInteraction(ingredient.getHash(), "Collect");
            }
        }
        return true;
    }

    private boolean handleEntlings(TreeLevel tree) {
        List<Rs2NpcModel> entlings = Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_ENTLINGS_NPC_01)
                .toList().stream().filter(x -> x != null && near(x.getWorldLocation())).collect(Collectors.toList());
        if (entlings.isEmpty()) return false;

        Microbot.status = "Forestry: Friendly Entlings";
        ensureInventorySpace(tree, 2);

        Rs2NpcModel target = entlings.stream()
                .min(Comparator.comparingInt(x -> x.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
        if (target == null) return true;

        String[] actions = entlingActions(normalizeEntlingRequest(target.getOverheadText()));
        if (actions.length == 0) return true;

        int phase = entlingPhaseByIndex.getOrDefault(target.getIndex(), 0);
        String action = actions[phase % actions.length];
        if (canInteract(target.getHash(), action) && target.click(action)) {
            entlingPhaseByIndex.put(target.getIndex(), phase + 1);
            markInteraction(target.getHash(), action);
        }
        return true;
    }

    private boolean handleHives(TreeLevel tree) {
        Rs2NpcModel hive = Microbot.getRs2NpcCache().query()
                .where(x -> x != null
                        && (x.getId() == NpcID.GATHERING_EVENT_BEES_BEEBOX_1
                        || x.getId() == NpcID.GATHERING_EVENT_BEES_BEEBOX_2)
                        && near(x.getWorldLocation()))
                .nearest();
        if (hive == null) return false;

        Microbot.status = "Forestry: Beehive";

        if (Rs2Widget.findWidget(LOG_AMOUNT_PROMPT, null, false) != null) {
            if (canInteract(0L, "Beehive-space")) {
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                markInteraction(0L, "Beehive-space");
            }
            return true;
        }

        if (countCurrentLogs(tree) <= 1) return true;
        if (canInteract(hive.getHash(), "Build") && hive.click("Build")) {
            markInteraction(hive.getHash(), "Build");
        }
        return true;
    }

    private boolean handlePheasant(TreeLevel tree) {
        Rs2NpcModel forester = Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_PHEASANT_FORESTER).nearest();
        if (forester == null || !near(forester.getWorldLocation())) return false;

        Microbot.status = "Forestry: Pheasant Control";
        ensureInventorySpace(tree, 1);

        if (Rs2Dialogue.isInDialogue()) {
            Rs2Dialogue.clickContinue();
            return true;
        }

        if (Rs2Inventory.contains("Pheasant egg")) {
            clickNpc(forester, "Talk-to");
            return true;
        }

        List<Rs2NpcModel> pheasants = Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_PHEASANT).toList();
        Rs2TileObjectModel nest = Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.GATHERING_EVENT_PHEASANT_NEST02)
                .toListOnClientThread().stream()
                .filter(x -> x != null && near(x.getWorldLocation()))
                .filter(x -> pheasants.stream().noneMatch(p -> p != null
                        && p.getWorldLocation() != null
                        && p.getWorldLocation().equals(x.getWorldLocation())))
                .min(Comparator.comparingInt(x -> x.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
        if (nest != null && canInteract(nest.getHash(), "Nest") && nest.click()) {
            markInteraction(nest.getHash(), "Nest");
        }
        return true;
    }

    private boolean handleFox() {
        Rs2NpcModel fox = Microbot.getRs2NpcCache().query()
                .where(x -> x != null
                        && (x.getId() == NpcID.GATHERING_EVENT_POACHERS_FOX_OUTDOORS
                        || x.getId() == NpcID.GATHERING_EVENT_POACHERS_FOX_INDOORS)
                        && near(x.getWorldLocation()))
                .nearest();
        if (fox == null) return false;

        Microbot.status = "Forestry: Poachers / Fox";
        Rs2NpcModel trap = Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_POACHERS_TRAP).nearest();
        if (trap != null && near(trap.getWorldLocation())) clickNpc(trap, "Disarm");
        return true;
    }

    private boolean handleRitual(TreeLevel tree) {
        Rs2NpcModel dryad = Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD).nearest();
        if (dryad == null || !near(dryad.getWorldLocation())) return false;

        Microbot.status = "Forestry: Enchantment Ritual";
        ensureInventorySpace(tree, 1);

        List<Rs2NpcModel> circles = Microbot.getRs2NpcCache().query()
                .where(x -> x != null
                        && x.getId() >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1
                        && x.getId() <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4
                        && near(x.getWorldLocation()))
                .toList();
        Rs2NpcModel target = solveRitual(circles);
        if (target != null && !target.getWorldLocation().equals(Rs2Player.getWorldLocation())) {
            moveDirect(target.getHash(), target.getWorldLocation(), target.getMinimapLocation(), target.getCanvasTilePoly());
        }
        return true;
    }

    private boolean handleLeprechaun() {
        Rs2NpcModel leprechaun = Microbot.getRs2NpcCache().query()
                .withId(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN).nearest();
        if (leprechaun == null || !near(leprechaun.getWorldLocation())) return false;

        Microbot.status = "Forestry: Woodcutting Leprechaun";
        Rs2TileObjectModel rainbow = Microbot.getRs2TileObjectCache().query()
                .withId(ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_RAINBOW)
                .nearestOnClientThread();
        if (rainbow != null && near(rainbow.getWorldLocation())
                && !rainbow.getWorldLocation().equals(Rs2Player.getWorldLocation())) {
            moveDirect(rainbow.getHash(), rainbow.getWorldLocation(), rainbow.getMinimapLocation(), rainbow.getCanvasTilePoly());
        }
        return true;
    }

    private boolean handleFlowers() {
        Rs2NpcModel bush = Microbot.getRs2NpcCache().query()
                .where(x -> x != null && isFloweringBush(x.getId()) && near(x.getWorldLocation()))
                .nearest();
        if (bush == null) return false;

        Microbot.status = "Forestry: Flowering Tree";
        Rs2NpcModel target = Microbot.getRs2NpcCache().query()
                .where(x -> x != null && isFloweringBush(x.getId())
                        && x.getAnimation() == -1 && near(x.getWorldLocation()))
                .nearest();
        if (target != null) clickNpc(target, "Tend-to");
        return true;
    }

    private boolean clickObject(Rs2TileObjectModel object, String action) {
        if (object == null || !canInteract(object.getHash(), action)) return false;
        if (!object.click(action)) return false;
        markInteraction(object.getHash(), action);
        return true;
    }

    private boolean clickNpc(Rs2NpcModel npc, String action) {
        if (npc == null || !canInteract(npc.getHash(), action)) return false;
        if (!npc.click(action)) return false;
        markInteraction(npc.getHash(), action);
        return true;
    }

    private boolean canInteract(long hash, String action) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastInteractionAtMs;
        long key = interactionKey(hash, action);
        return elapsed >= INTERACTION_COOLDOWN_MS
                && (key != lastInteractionKey || elapsed >= SAME_ACTION_RETRY_MS)
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating()
                && !Rs2Player.isInteracting();
    }

    private void markInteraction(long hash, String action) {
        lastInteractionKey = interactionKey(hash, action);
        lastInteractionAtMs = System.currentTimeMillis();
    }

    private long interactionKey(long hash, String action) {
        return hash * 31L ^ (long) (action == null ? 0 : action.toLowerCase(Locale.ROOT).hashCode());
    }

    private boolean moveDirect(long hash, net.runelite.api.coords.WorldPoint target,
                               Point minimap, Polygon canvas) {
        if (target == null || !canInteract(hash, "Move")) return false;
        if (minimap != null) Microbot.getMouse().click(minimap);
        else if (canvas != null) Microbot.getMouse().click(canvas.getBounds());
        else return false;
        markInteraction(hash, "Move");
        return true;
    }

    private Rs2TileObjectModel nearestObject(int id, String action) {
        return Microbot.getRs2TileObjectCache().query()
                .withId(id).toListOnClientThread().stream()
                .filter(x -> x != null && near(x.getWorldLocation()) && hasAction(x, action))
                .min(Comparator.comparingInt(x -> x.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
    }

    private boolean near(net.runelite.api.coords.WorldPoint point) {
        return point != null
                && point.getPlane() == Rs2Player.getWorldLocation().getPlane()
                && point.distanceTo(Rs2Player.getWorldLocation()) <= EVENT_DISTANCE;
    }

    private boolean ensureInventorySpace(TreeLevel tree, int slots) {
        int free = Rs2Inventory.emptySlotCount();
        if (free >= slots) return true;
        int amount = Math.min(slots - free, countCurrentLogs(tree));
        if (amount <= 0) return false;
        Rs2Inventory.dropAmount(logName(tree), amount, InteractOrder.EFFICIENT_ROW);
        return Rs2Inventory.emptySlotCount() >= slots;
    }

    private int countCurrentLogs(TreeLevel tree) {
        return Rs2Inventory.count(logName(tree));
    }

    private String logName(TreeLevel tree) {
        if (tree == TreeLevel.OAK) return "Oak logs";
        if (tree == TreeLevel.WILLOW) return "Willow logs";
        if (tree == TreeLevel.YEW) return "Yew logs";
        return "Logs";
    }

    private static boolean hasAction(Rs2TileObjectModel object, String action) {
        if (object == null || object.getObjectComposition() == null
                || object.getObjectComposition().getActions() == null) return false;
        for (String raw : object.getObjectComposition().getActions()) {
            if (raw != null && action.equalsIgnoreCase(raw.replaceAll("<[^>]*>", ""))) return true;
        }
        return false;
    }

    private String normalizeEntlingRequest(String request) {
        if (request == null) return "";
        String normalized = Rs2TextSanitizer.stripTagsToSpace(request).trim();
        String key = normalized.toLowerCase(Locale.ROOT);
        if ("breezy at the back!".equals(key) || "breezy on the back!".equals(key)) return "Breezy at the back!";
        if ("short on back and sides!".equals(key) || "short back and sides!".equals(key)) return "Short back and sides!";
        if ("a leafy mullet!".equals(key)) return "A leafy mullet!";
        if ("short on top!".equals(key)) return "Short on top!";
        return normalized;
    }

    private String[] entlingActions(String request) {
        if ("Breezy at the back!".equals(request)) return new String[]{"Prune-back"};
        if ("Short on top!".equals(request)) return new String[]{"Prune-top"};
        if ("A leafy mullet!".equals(request)) return new String[]{"Prune-top", "Prune-sides"};
        if ("Short back and sides!".equals(request)) return new String[]{"Prune-back", "Prune-sides"};
        return new String[0];
    }

    private Rs2NpcModel solveRitual(List<Rs2NpcModel> circles) {
        if (circles == null || circles.size() != 5) return null;
        int xor = 0;
        for (Rs2NpcModel circle : circles) xor ^= ritualValue(circle);
        for (Rs2NpcModel circle : circles) {
            int value = ritualValue(circle);
            if ((value & xor) == value) return circle;
        }
        return null;
    }

    private int ritualValue(Rs2NpcModel npc) {
        int offset = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
        return (16 << (offset / 4)) | (1 << (offset % 4));
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
}
