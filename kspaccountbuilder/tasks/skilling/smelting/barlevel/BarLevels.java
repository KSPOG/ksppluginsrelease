/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel;

public enum BarLevels {
    BRONZE("Bronze bar", 1),
    IRON("Iron bar", 15),
    SILVER("Silver bar", 20),
    STEEL("Steel bar", 30),
    GOLD("Gold bar", 40),
    MITHRIL("Mithril bar", 50),
    ADAMANT("Adamant bar", 70),
    RUNE("Rune bar", 85);

    private final String displayName;
    private final int requiredSmithingLevel;

    private BarLevels(String displayName, int requiredSmithingLevel) {
        this.displayName = displayName;
        this.requiredSmithingLevel = requiredSmithingLevel;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getRequiredSmithingLevel() {
        return this.requiredSmithingLevel;
    }
}

