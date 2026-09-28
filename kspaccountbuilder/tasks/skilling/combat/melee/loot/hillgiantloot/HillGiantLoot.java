/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.loot.hillgiantloot;

public enum HillGiantLoot {
    BIG_BONES("Big bones"),
    GIANT_KEY("Giant key"),
    STEEL_SCIMITAR("Steel scimitar"),
    STEEL_LONGSWORD("Steel longsword"),
    BRONZE_ARROW("Bronze arrow"),
    IRON_ARROW("Iron arrow"),
    STEEL_ARROW("Steel arrow"),
    MITHRIL_ARROW("Mithril arrow"),
    ADAMANT_ARROW("Adamant arrow"),
    FIRE_RUNE("Fire rune"),
    WATER_RUNE("Water rune"),
    LAW_RUNE("Law rune"),
    MIND_RUNE("Mind rune"),
    COSMIC_RUNE("Cosmic rune"),
    NATURE_RUNE("Nature rune"),
    CHAOS_RUNE("Chaos rune"),
    DEATH_RUNE("Death rune"),
    BODY_TALISMAN("Body talisman"),
    COINS("Coins"),
    LIMPWURT_ROOT("Limpwurt root"),
    UNCUT_SAPPHIRE("Uncut sapphire"),
    UNCUT_EMERALD("Uncut emerald"),
    UNCUT_RUBY("Uncut ruby"),
    UNCUT_DIAMOND("Uncut diamond"),
    CLUE_SCROLL_BEGINNER("Clue scroll (beginner)"),
    SCROLL_BOX_BEGINNER("Scroll box (beginner)");

    private final String displayName;

    public String getDisplayName() {
        return this.displayName;
    }

    private HillGiantLoot(String displayName) {
        this.displayName = displayName;
    }
}

