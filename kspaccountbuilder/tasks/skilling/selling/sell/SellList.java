/*
 * Decompiled with CFR 0.152.
 */
package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sell;

public enum SellList {
    LOGS("Logs", false),
    OAK_LOGS("Oak logs", true),
    YEW_LOGS("Yew logs", true),
    COWHIDE("Cowhide", true),
    RAW_CHICKEN("Raw chicken"),
    LEATHER_GLOVES("Leather gloves"),
    GOLD_RING("Gold ring"),
    GOLD_NECKLACE("Gold necklace"),
    SAPPHIRE_RING("Sapphire ring"),
    SAPPHIRE_NECKLACE("Sapphire necklace"),
    TIARA("Tiara"),
    EMERALD_RING("Emerald ring"),
    EMERALD_NECKLACE("Emerald necklace"),
    RUBY_RING("Ruby ring"),
    RUBY_NECKLACE("Ruby necklace"),
    DIAMOND_RING("Diamond ring"),
    DIAMOND_NECKLACE("Diamond necklace"),
    SMALL_FISHING_NET("Small fishing net"),
    FISHING_ROD("Fishing rod"),
    FISHING_BAIT("Fishing bait", true),
    SILVER_ORE("Silver ore", true),
    SILVER_BAR("Silver bar"),
    BRONZE_DAGGER("Bronze dagger"),
    BRONZE_SCIMITAR("Bronze scimitar"),
    BRONZE_WARHAMMER("Bronze warhammer"),
    BRONZE_PLATEBODY("Bronze platebody"),
    IRON_SCIMITAR("Iron scimitar"),
    IRON_WARHAMMER("Iron warhammer"),
    IRON_PLATEBODY("Iron platebody"),
    STEEL_SCIMITAR("Steel scimitar"),
    STEEL_LONGSWORD("Steel longsword"),
    STEEL_WARHAMMER("Steel warhammer"),
    STEEL_PLATEBODY("Steel platebody"),
    LIMPWURT_ROOT("Limpwurt root"),
    EARTH_TALISMAN("Earth talisman"),
    BODY_TALISMAN("Body talisman"),
    MITHRIL_SWORD("Mithril sword"),
    STEEL_KITESHIELD("Steel kiteshield"),
    BLACK_SQ_SHIELD("Black sq shield"),
    TUNA("Tuna", true),
    LOBSTER("Lobster", true),
    SWORDFISH("Swordfish", true),
    SPINACH_ROLL("Spinach roll");

    private final String displayName;
    private final boolean tradeRestricted;

    public String getDisplayName() {
        return this.displayName;
    }

    public boolean isTradeRestricted() {
        return this.tradeRestricted;
    }

    SellList(String displayName) {
        this(displayName, false);
    }

    SellList(String displayName, boolean tradeRestricted) {
        this.displayName = displayName;
        this.tradeRestricted = tradeRestricted;
    }
}
