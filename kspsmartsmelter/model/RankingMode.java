package net.runelite.client.plugins.microbot.kspsmartsmelter.model;

public enum RankingMode {
    TRIP_PROFIT("Profit / inventory"),
    ROI("ROI"),
    PROFIT_PER_CYCLE("Profit / cycle");

    private final String displayName;

    RankingMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
