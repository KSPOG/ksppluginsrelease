/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.oresreq;

public enum ReqOres {
    BRONZE("Bronze bar", "Copper ore", 1, "Tin ore", 1),
    IRON("Iron bar", "Iron ore", 1, null, 0),
    SILVER("Silver bar", "Silver ore", 1, null, 0),
    STEEL("Steel bar", "Iron ore", 1, "Coal", 2),
    GOLD("Gold bar", "Gold ore", 1, null, 0),
    MITHRIL("Mithril bar", "Mithril ore", 1, "Coal", 4),
    ADAMANT("Adamant bar", "Adamantite ore", 1, "Coal", 6),
    RUNE("Rune bar", "Runite ore", 1, "Coal", 8);

    private final String barName;
    private final String primaryOreName;
    private final int primaryOreAmount;
    private final String secondaryOreName;
    private final int secondaryOreAmount;

    private ReqOres(String barName, String primaryOreName, int primaryOreAmount, String secondaryOreName, int secondaryOreAmount) {
        this.barName = barName;
        this.primaryOreName = primaryOreName;
        this.primaryOreAmount = primaryOreAmount;
        this.secondaryOreName = secondaryOreName;
        this.secondaryOreAmount = secondaryOreAmount;
    }

    public boolean hasSecondaryOre() {
        return this.secondaryOreName != null && !this.secondaryOreName.isEmpty() && this.secondaryOreAmount > 0;
    }

    public String getBarName() {
        return this.barName;
    }

    public String getPrimaryOreName() {
        return this.primaryOreName;
    }

    public int getPrimaryOreAmount() {
        return this.primaryOreAmount;
    }

    public String getSecondaryOreName() {
        return this.secondaryOreName;
    }

    public int getSecondaryOreAmount() {
        return this.secondaryOreAmount;
    }
}

