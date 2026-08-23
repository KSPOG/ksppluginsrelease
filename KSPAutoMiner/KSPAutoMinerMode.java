package net.runelite.client.plugins.microbot.KSPAutoMiner;

public enum KSPAutoMinerMode {
    MINE_DROP("Mine & Drop"),
    MINE_BANK("Mine & Bank"),
    PROGRESSIVE_BANK("Progressive Mode & Bank"),
    PROGRESSIVE_DROP("Progressive Mode & Drop");

    private final String displayName;

    KSPAutoMinerMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public boolean isBankingMode() {
        return this == MINE_BANK || this == PROGRESSIVE_BANK;
    }

    public boolean isProgressiveMode() {
        return this == PROGRESSIVE_BANK || this == PROGRESSIVE_DROP;
    }
}