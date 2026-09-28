/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.armour;

public enum Armour {
    BRONZE_FULL_HELM("Bronze full helm", 1),
    BRONZE_KITESHIELD("Bronze kiteshield", 1),
    BRONZE_PLATELEGS("Bronze platelegs", 1),
    BRONZE_PLATEBODY("Bronze platebody", 1),
    IRON_FULL_HELM("Iron full helm", 1),
    IRON_KITESHIELD("Iron kiteshield", 1),
    IRON_PLATELEGS("Iron platelegs", 1),
    IRON_PLATEBODY("Iron platebody", 1),
    STEEL_FULL_HELM("Steel full helm", 5),
    STEEL_KITESHIELD("Steel kiteshield", 5),
    STEEL_PLATELEGS("Steel platelegs", 5),
    STEEL_PLATEBODY("Steel platebody", 5),
    BLACK_FULL_HELM("Black full helm", 10),
    BLACK_KITESHIELD("Black kiteshield", 10),
    BLACK_PLATELEGS("Black platelegs", 10),
    BLACK_PLATEBODY("Black platebody", 10),
    MITHRIL_FULL_HELM("Mithril full helm", 20),
    MITHRIL_KITESHIELD("Mithril kiteshield", 20),
    MITHRIL_PLATELEGS("Mithril platelegs", 20),
    MITHRIL_PLATEBODY("Mithril platebody", 20),
    ADAMANT_FULL_HELM("Adamant full helm", 30),
    ADAMANT_KITESHIELD("Adamant kiteshield", 30),
    ADAMANT_PLATELEGS("Adamant platelegs", 30),
    ADAMANT_PLATEBODY("Adamant platebody", 30),
    RUNE_FULL_HELM("Rune full helm", 40),
    RUNE_KITESHIELD("Rune kiteshield", 40),
    RUNE_CHAINBODY("Rune chainbody", 40),
    RUNE_PLATELEGS("Rune platelegs", 40),
    RUNE_PLATEBODY("Rune platebody", 40);

    private final String displayName;
    private final int requiredDefenceLevel;

    public String getDisplayName() {
        return this.displayName;
    }

    public int getRequiredDefenceLevel() {
        return this.requiredDefenceLevel;
    }

    private Armour(String displayName, int requiredDefenceLevel) {
        this.displayName = displayName;
        this.requiredDefenceLevel = requiredDefenceLevel;
    }
}

