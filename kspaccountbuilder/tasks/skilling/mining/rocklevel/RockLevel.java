/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.rocklevel;

public enum RockLevel {
    COPPER("Copper", 1),
    TIN("Tin", 1),
    CLAY("Clay", 1),
    RUNE_ESSENCE("Rune essence", 1),
    IRON("Iron", 15),
    SILVER("Silver", 20),
    COAL("Coal", 30),
    GOLD("Gold", 40);

    private final String displayName;
    private final int requiredMiningLevel;

    private RockLevel(String displayName, int requiredMiningLevel) {
        this.displayName = displayName;
        this.requiredMiningLevel = requiredMiningLevel;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getRequiredMiningLevel() {
        return this.requiredMiningLevel;
    }
}
