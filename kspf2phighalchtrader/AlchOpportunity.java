package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

/** Immutable market snapshot for one High Alchemy candidate. */
public final class AlchOpportunity {
    private final int itemId,instantBuyPrice,highAlchValue,natureRunePrice,fireRuneCost,profitPerCast,volume,tradeLimitPer4Hours; private final String itemName; private final long expectedProfitPerHour;
    public AlchOpportunity(int id,String name,int buy,int alch,int nature,int fire,int profit,long hourly,int volume,int limit){itemId=id;itemName=name;instantBuyPrice=buy;highAlchValue=alch;natureRunePrice=nature;fireRuneCost=fire;profitPerCast=profit;expectedProfitPerHour=hourly;this.volume=volume;tradeLimitPer4Hours=limit;}
    public int getItemId(){return itemId;} public String getItemName(){return itemName;} public int getInstantBuyPrice(){return instantBuyPrice;} public int getHighAlchValue(){return highAlchValue;} public int getNatureRunePrice(){return natureRunePrice;}
    public int getFireRuneCost(){return fireRuneCost;} public int getProfitPerCast(){return profitPerCast;} public long getExpectedProfitPerHour(){return expectedProfitPerHour;} public int getVolume(){return volume;} public int getTradeLimitPer4Hours(){return tradeLimitPer4Hours;}
}
