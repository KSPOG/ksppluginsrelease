/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.weapon;

public enum Weapons {
    BRONZE_SWORD("Bronze sword", 1),
    BRONZE_SCIMITAR("Bronze scimitar", 1),
    IRON_SCIMITAR("Iron scimitar", 1),
    STEEL_SCIMITAR("Steel scimitar", 5),
    BLACK_SCIMITAR("Black scimitar", 10),
    MITHRIL_SCIMITAR("Mithril scimitar", 20),
    ADAMANT_SCIMITAR("Adamant scimitar", 30),
    RUNE_SCIMITAR("Rune scimitar", 40);

    private final String displayName;
    private final int requiredAttackLevel;

    public String getDisplayName() {
        return this.displayName;
    }

    public int getRequiredAttackLevel() {
        return this.requiredAttackLevel;
    }

    private Weapons(String displayName, int requiredAttackLevel) {
        this.displayName = displayName;
        this.requiredAttackLevel = requiredAttackLevel;
    }
}
