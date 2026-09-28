/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.amulet;

public enum Amulet {
    AMULET_OF_POWER("Amulet of power");

    private final String displayName;

    public String getDisplayName() {
        return this.displayName;
    }

    private Amulet(String displayName) {
        this.displayName = displayName;
    }
}

