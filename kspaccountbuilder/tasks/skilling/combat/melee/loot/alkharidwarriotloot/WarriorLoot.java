/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.loot.alkharidwarriotloot;

public enum WarriorLoot {
    BONES("Bones"),
    EARTH_RUNE("Earth rune"),
    FIRE_RUNE("Fire rune"),
    MIND_RUNE("Mind rune"),
    CHAOS_RUNE("Chaos rune"),
    BRONZE_ARROW("Bronze arrow"),
    IRON_ARROW("Iron arrow"),
    STEEL_ARROW("Steel arrow"),
    MITHRIL_ARROW("Mithril arrow"),
    ADAMANT_ARROW("Adamant arrow"),
    EARTH_TALISMAN("Earth talisman"),
    COINS("Coins"),
    CLUE_SCROLL_BEGINNER("Clue scroll (beginner)"),
    SCROLL_BOX_BEGINNER("Scroll box (beginner)");

    private final String displayName;

    public String getDisplayName() {
        return this.displayName;
    }

    private WarriorLoot(String displayName) {
        this.displayName = displayName;
    }
}

