package net.runelite.client.plugins.microbot.KSPAutoMiner;

public enum KSPAutoMinerRock {
    CLAY("Clay rocks", 1),
    COPPER_TIN("Copper & Tin rocks", 1),
    IRON("Iron rocks", 15),
    SILVER("Silver rocks", 20),
    COAL("Coal rocks", 30),
    GOLD("Gold rocks", 40),
    MITHRIL("Mithril rocks", 55),
    ADAMANT("Adamantite rocks", 70),
    RUNE("Runite rocks", 85);

    private final String rockName;
    private final int miningLevel;

    KSPAutoMinerRock(String rockName, int miningLevel) {
        this.rockName = rockName;
        this.miningLevel = miningLevel;
    }

    public String getRockName() {
        return rockName;
    }

    public int getMiningLevel() {
        return miningLevel;
    }

    @Override
    public String toString() {
        return rockName.replace(" rocks", "");
    }
}