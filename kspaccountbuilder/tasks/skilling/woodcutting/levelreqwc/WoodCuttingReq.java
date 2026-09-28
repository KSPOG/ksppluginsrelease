/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.levelreqwc;

public enum WoodCuttingReq {
    BRONZE("Bronze axe", 1),
    IRON("Iron axe", 1),
    STEEL("Steel axe", 6),
    BLACK("Black axe", 11),
    MITHRIL("Mithril axe", 21),
    ADAMANT("Adamant axe", 31),
    RUNE("Rune axe", 41);

    private final String displayName;
    private final int requiredWoodcuttingLevel;

    private WoodCuttingReq(String displayName, int requiredWoodcuttingLevel) {
        this.displayName = displayName;
        this.requiredWoodcuttingLevel = requiredWoodcuttingLevel;
    }

    public static WoodCuttingReq bestForWoodcuttingLevel(int woodcuttingLevel) {
        WoodCuttingReq best = BRONZE;
        for (WoodCuttingReq axe : WoodCuttingReq.values()) {
            if (woodcuttingLevel < axe.requiredWoodcuttingLevel) continue;
            best = axe;
        }
        return best;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getRequiredWoodcuttingLevel() {
        return this.requiredWoodcuttingLevel;
    }
}

