/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.recipes;

public enum SmithRecipe {
    BRONZE_DAGGER("Bronze dagger", 1, 12.5),
    BRONZE_SCIMITAR("Bronze scimitar", 2, 25.0),
    BRONZE_WARHAMMER("Bronze warhammer", 3, 37.5),
    BRONZE_PLATEBODY("Bronze platebody", 5, 62.5),
    IRON_SCIMITAR("Iron scimitar", 2, 50.0),
    IRON_WARHAMMER("Iron warhammer", 3, 75.0),
    IRON_PLATEBODY("Iron platebody", 5, 125.0),
    STEEL_SCIMITAR("Steel scimitar", 2, 75.0),
    STEEL_WARHAMMER("Steel warhammer", 3, 112.5),
    STEEL_PLATEBODY("Steel platebody", 5, 187.5);

    private final String displayName;
    private final int barRequirement;
    private final double expGain;

    public String getDisplayName() {
        return this.displayName;
    }

    public int getBarRequirement() {
        return this.barRequirement;
    }

    public double getExpGain() {
        return this.expGain;
    }

    private SmithRecipe(String displayName, int barRequirement, double expGain) {
        this.displayName = displayName;
        this.barRequirement = barRequirement;
        this.expGain = expGain;
    }
}

