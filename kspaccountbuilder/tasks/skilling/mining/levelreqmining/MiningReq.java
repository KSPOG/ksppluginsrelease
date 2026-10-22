/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.levelreqmining;

public enum MiningReq {
    BRONZE("Bronze pickaxe", 1),
    IRON("Iron pickaxe", 1),
    STEEL("Steel pickaxe", 6),
    BLACK("Black pickaxe", 11),
    MITHRIL("Mithril pickaxe", 21),
    ADAMANT("Adamant pickaxe", 31),
    RUNE("Rune pickaxe", 41);

    private final String displayName;
    private final int requiredMiningLevel;

    private MiningReq(String displayName, int requiredMiningLevel) {
        this.displayName = displayName;
        this.requiredMiningLevel = requiredMiningLevel;
    }

    public static MiningReq bestForMiningLevel(int miningLevel) {
        MiningReq best = BRONZE;
        for (MiningReq pickaxe : MiningReq.values()) {
            if (miningLevel < pickaxe.requiredMiningLevel) continue;
            best = pickaxe;
        }
        return best;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getRequiredMiningLevel() {
        return this.requiredMiningLevel;
    }
}

