/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.npc;

public enum NPC {
    COW("Cow"),
    COW_CALF("Cow calf"),
    CHICKEN("Chicken"),
    AL_KHARID_WARRIOR("Al Kharid warrior"),
    HILL_GIANT("Hill Giant"),
    MOSS_GIANT("Moss Giant");

    private final String displayName;

    public String getDisplayName() {
        return this.displayName;
    }

    private NPC(String displayName) {
        this.displayName = displayName;
    }
}
