package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.api.gameval.ItemID;

public enum KspTree {
    TREE("Tree", "tree", "Logs", ItemID.LOGS, 1, "Chop down"),
    TIRANNWN_TREE("Tree (Tirannwn)", "tree", "Logs", ItemID.LOGS, 1, "Chop down"),
    DYING_TREE("Dying tree", "dying tree", "Logs", ItemID.LOGS, 1, "Chop down"),
    BURNT_TREE("Burnt tree", "burnt tree", "Charcoal", ItemID.CHARCOAL, 1, "Chop down"),
    JUNGLE_TREE("Jungle tree", "jungle tree", "Logs", ItemID.LOGS, 1, "Chop down"),
    ACHEY_TREE("Achey tree", "achey tree", "Achey tree logs", ItemID.ACHEY_TREE_LOGS, 1, "Chop down"),
    LIGHT_JUNGLE("Light jungle", "light jungle", "Thatch spar light", ItemID.THATCHING_SPAR_LIGHT, 10, "Chop down"),
    OAK("Oak", "oak tree", "Oak logs", ItemID.OAK_LOGS, 15, "Chop down"),
    MEDIUM_JUNGLE("Medium jungle", "medium jungle", "Thatch spar med", ItemID.THATCHING_SPAR_MED, 20, "Chop down"),
    WILLOW("Willow", "willow tree", "Willow logs", ItemID.WILLOW_LOGS, 30, "Chop down"),
    TEAK_TREE("Teak", "teak tree", "Teak logs", ItemID.TEAK_LOGS, 35, "Chop down"),
    DENSE_JUNGLE("Dense jungle", "dense jungle", "Thatch spar dense", ItemID.THATCHING_SPAR_DENSE, 35, "Chop down"),
    JATOBA_TREE("Jatoba", "jatoba tree", "Jatoba logs", ItemID.JATOBA_LOGS, 40, "Chop down"),
    MATURE_JUNIPER("Mature juniper", "mature juniper tree", "Juniper logs", ItemID.JUNIPER_LOGS, 42, "Chop down"),
    MAPLE("Maple", "maple tree", "Maple logs", ItemID.MAPLE_LOGS, 45, "Chop down"),
    HOLLOW_TREE("Hollow tree", "hollow tree", "Bark", ItemID.HOLLOW_BARK, 45, "Chop down"),
    MAHOGANY("Mahogany", "mahogany tree", "Mahogany logs", ItemID.MAHOGANY_LOGS, 50, "Chop down"),
    ARCTIC_PINE("Arctic pine", "arctic pine", "Arctic pine logs", ItemID.ARCTIC_PINE_LOG, 54, "Chop down"),
    YEW("Yew", "yew tree", "Yew logs", ItemID.YEW_LOGS, 60, "Chop down"),
    BLISTERWOOD("Blisterwood", "blisterwood tree", "Blisterwood logs", ItemID.BLISTERWOOD_LOGS, 62, "Chop"),
    SULLIUSCEP("Sulliuscep", "sulliuscep", "Sulliuscep cap", ItemID.FOSSIL_SULLIUSCEP_CAP, 65, "Chop down"),
    CAMPHOR_TREE("Camphor", "camphor tree", "Camphor logs", ItemID.CAMPHOR_LOGS, 66, "Chop down"),
    MAGIC("Magic", "magic tree", "Magic logs", ItemID.MAGIC_LOGS, 75, "Chop down"),
    IRONWOOD_TREE("Ironwood", "ironwood tree", "Ironwood logs", ItemID.IRONWOOD_LOGS, 80, "Chop down"),
    REDWOOD("Redwood", "redwood tree", "Redwood logs", ItemID.REDWOOD_LOGS, 90, "Cut"),
    ROSEWOOD_TREE("Rosewood", "rosewood tree", "Rosewood logs", ItemID.ROSEWOOD_LOGS, 92, "Chop down"),
    EVERGREEN_TREE("Evergreen", "evergreen tree", "Logs", ItemID.LOGS, 1, "Chop down"),
    DEAD_TREE("Dead tree", "dead tree", "Logs", ItemID.LOGS, 1, "Chop down"),
    INFECTED_ROOT("Infected root", "infected root", "Logs", ItemID.LOGS, 80, "Chop");

    private final String displayName;
    private final String objectName;
    private final String resourceName;
    private final int resourceId;
    private final int woodcuttingLevel;
    private final String action;

    KspTree(String displayName, String objectName, String resourceName, int resourceId, int woodcuttingLevel, String action) {
        this.displayName = displayName;
        this.objectName = objectName;
        this.resourceName = resourceName;
        this.resourceId = resourceId;
        this.woodcuttingLevel = woodcuttingLevel;
        this.action = action;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getResourceName() {
        return resourceName;
    }

    public int getResourceId() {
        return resourceId;
    }

    public int getWoodcuttingLevel() {
        return woodcuttingLevel;
    }

    public String getAction() {
        return action;
    }

    public boolean isCampfireBurnable() {
        return resourceName.toLowerCase().contains("log");
    }

    @Override
    public String toString() {
        return displayName;
    }
}
