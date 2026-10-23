/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.loot.cowloot;

public enum CowLoot {
    COWHIDE("Cowhide"),
    FEATHER("Feather"),
    BONES("Bones"),
    BRONZE_ARROW("Bronze arrow"),
    IRON_ARROW("Iron arrow"),
    STEEL_ARROW("Steel arrow"),
    MITHRIL_ARROW("Mithril arrow"),
    ADAMANT_ARROW("Adamant arrow"),
    CLUE_SCROLL_BEGINNER("Clue scroll (beginner)"),
    SCROLL_BOX_BEGINNER("Scroll box (beginner)");

    private final String displayName;

    public String getDisplayName() {
        return this.displayName;
    }

    private CowLoot(String displayName) {
        this.displayName = displayName;
    }
}
