package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treelevel;
import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import javax.annotation.Nullable;
import java.util.Locale;
public enum TreeLevel {
    TREE("Tree", "Tree", 1),
    OAK("Oak", "Oak tree", 15),
    WILLOW("Willow", "Willow tree", 30),
    YEW("Yew", "Yew tree", 60);
    private final String displayName;
    /** Exact object composition name for {@code withName} (see wiki / object inspector). */
    private final String objectCompositionName;
    private final int requiredWoodcuttingLevel;
    TreeLevel(String displayName, String objectCompositionName, int requiredWoodcuttingLevel) {
        this.displayName = displayName;
        this.objectCompositionName = objectCompositionName;
        this.requiredWoodcuttingLevel = requiredWoodcuttingLevel;
    }
    public String getDisplayName() {
        return displayName;
    }
    public String getObjectCompositionName() {
        return objectCompositionName;
    }
    public int getRequiredWoodcuttingLevel() {
        return requiredWoodcuttingLevel;
    }
    /**
     * Nearest reachable object: same world view, within radius, exact {@link #objectCompositionName},
     * object definition exposes a menu action containing {@code "chop"} (e.g. {@code Chop down}).
     */
    @Nullable
    public Rs2TileObjectModel findNearestChoppable(int radiusTiles) {
        return Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .within(radiusTiles)
                .withName(objectCompositionName)
                .where(TreeLevel::objectHasChopAction)
                .nearestReachable(radiusTiles);
    }
    /** Same as {@link #findNearestChoppable(int)} but entire query runs on the client thread. */
    @Nullable
    public Rs2TileObjectModel findNearestChoppableOnClientThread(int radiusTiles) {
        return Microbot.getClientThread().invoke(() -> findNearestChoppable(radiusTiles));
    }
    /**
     * Finds nearest matching tree and invokes {@code Chop down} (standard Microbot / client string).
     *
     * @return {@code false} if none found or click failed
     */
    public boolean interactChopNearest(int radiusTiles) {
        Rs2TileObjectModel tree = findNearestChoppableOnClientThread(radiusTiles);
        return tree != null && tree.click("Chop down");
    }
    private static boolean objectHasChopAction(Rs2TileObjectModel obj) {
        ObjectComposition comp = obj.getObjectComposition();
        if (comp == null) {
            return false;
        }
        String[] actions = comp.getActions();
        if (actions == null) {
            return false;
        }
        for (String raw : actions) {
            if (raw == null) {
                continue;
            }
            String a = Rs2UiHelper.stripColTags(raw).toLowerCase(Locale.ROOT);
            if (a.contains("chop")) {
                return true;
            }
        }
        return false;
    }
}