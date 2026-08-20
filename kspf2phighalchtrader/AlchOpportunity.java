package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

/**
 * Immutable market snapshot for one High Alchemy candidate.
 */
public final class AlchOpportunity {
    private final int itemId;
    private final String itemName;
    private final int instantBuyPrice;
    private final int highAlchValue;
    private final int natureRunePrice;
    private final int fireRuneCost;
    private final int profitPerCast;
    private final long expectedProfitPerHour;
    private final int volume;
    private final int tradeLimitPer4Hours;

    public AlchOpportunity(
            int itemId,
            String itemName,
            int instantBuyPrice,
            int highAlchValue,
            int natureRunePrice,
            int fireRuneCost,
            int profitPerCast,
            long expectedProfitPerHour,
            int volume,
            int tradeLimitPer4Hours) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.instantBuyPrice = instantBuyPrice;
        this.highAlchValue = highAlchValue;
        this.natureRunePrice = natureRunePrice;
        this.fireRuneCost = fireRuneCost;
        this.profitPerCast = profitPerCast;
        this.expectedProfitPerHour = expectedProfitPerHour;
        this.volume = volume;
        this.tradeLimitPer4Hours = tradeLimitPer4Hours;
    }

    public int getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public int getInstantBuyPrice() {
        return instantBuyPrice;
    }

    public int getHighAlchValue() {
        return highAlchValue;
    }

    public int getNatureRunePrice() {
        return natureRunePrice;
    }

    public int getFireRuneCost() {
        return fireRuneCost;
    }

    public int getProfitPerCast() {
        return profitPerCast;
    }

    public long getExpectedProfitPerHour() {
        return expectedProfitPerHour;
    }

    public int getVolume() {
        return volume;
    }

    public int getTradeLimitPer4Hours() {
        return tradeLimitPer4Hours;
    }

}
