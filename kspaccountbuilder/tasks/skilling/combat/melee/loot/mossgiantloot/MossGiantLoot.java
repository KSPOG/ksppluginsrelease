/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.loot.mossgiantloot;

public enum MossGiantLoot {
    BIG_BONES("Big bones"),
    MITHRIL_SWORD("Mithril sword"),
    STEEL_KITESHIELD("Steel kiteshield"),
    BLACK_SQ_SHIELD("Black sq shield"),
    BRONZE_ARROW("Bronze arrow"),
    IRON_ARROW("Iron arrow"),
    STEEL_ARROW("Steel arrow"),
    MITHRIL_ARROW("Mithril arrow"),
    ADAMANT_ARROW("Adamant arrow"),
    LAW_RUNE("Law rune"),
    AIR_RUNE("Air rune"),
    EARTH_RUNE("Earth rune"),
    NATURE_RUNE("Nature rune"),
    CHAOS_RUNE("Chaos rune"),
    COSMIC_RUNE("Cosmic rune"),
    DEATH_RUNE("Death rune"),
    COINS("Coins"),
    STEEL_BAR("Steel bar"),
    COAL("Coal"),
    SPINACH_ROLL("Spinach roll"),
    UNCUT_SAPPHIRE("Uncut sapphire"),
    UNCUT_EMERALD("Uncut emerald"),
    UNCUT_RUBY("Uncut ruby"),
    UNCUT_DIAMOND("Uncut diamond"),
    CLUE_SCROLL_BEGINNER("Clue scroll (beginner)"),
    SCROLL_BOX_BEGINNER("Scroll box (beginner)"),
    MOSSY_KEY("Mossy key");

    private final String displayName;

    public String getDisplayName() {
        return this.displayName;
    }

    private MossGiantLoot(String displayName) {
        this.displayName = displayName;
    }
}

