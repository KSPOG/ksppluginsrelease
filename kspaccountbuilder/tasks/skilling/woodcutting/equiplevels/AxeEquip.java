/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.equiplevels;

public enum AxeEquip {
    BRONZE("Bronze axe", 1),
    IRON("Iron axe", 1),
    STEEL("Steel axe", 5),
    BLACK("Black axe", 10),
    MITHRIL("Mithril axe", 20),
    ADAMANT("Adamant axe", 30),
    RUNE("Rune axe", 40);

    private final String displayName;
    private final int requiredAttackLevel;

    private AxeEquip(String displayName, int requiredAttackLevel) {
        this.displayName = displayName;
        this.requiredAttackLevel = requiredAttackLevel;
    }

    public static AxeEquip bestForAttackLevel(int attackLevel) {
        AxeEquip best = BRONZE;
        for (AxeEquip axe : AxeEquip.values()) {
            if (attackLevel < axe.requiredAttackLevel) continue;
            best = axe;
        }
        return best;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getRequiredAttackLevel() {
        return this.requiredAttackLevel;
    }
}

