package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import java.util.Objects;

public final class RecipeInput
{
    private final String itemName; private final int requiredNumerator, requiredDenominator;
    private final boolean consumed, stackable, progressTracked;

    private RecipeInput(String name,int numerator,int denominator,boolean consumed,boolean stackable,boolean tracked) { itemName=Objects.requireNonNull(name,"itemName"); requiredNumerator=numerator; requiredDenominator=denominator; this.consumed=consumed; this.stackable=stackable; progressTracked=tracked; }

    public static RecipeInput consumed(String name,int units)
    {
        if(units<=0) throw new IllegalArgumentException("Consumed input quantity must be positive");
        return new RecipeInput(name,units,1,true,false,true);
    }
    public static RecipeInput consumedStackablePerOutputs(String name,int inputUnits,int outputUnits)
    {
        if(inputUnits<=0||outputUnits<=0) throw new IllegalArgumentException("Fractional input quantities must be positive");
        return new RecipeInput(name,inputUnits,outputUnits,true,true,false);
    }
    public static RecipeInput tool(String name){return new RecipeInput(name,0,1,false,false,false);}

    public String getItemName(){return itemName;} public int getUnitsPerOutput(){return requiredNumerator;}
    public int getRequiredNumerator(){return requiredNumerator;} public int getRequiredDenominator(){return requiredDenominator;}
    public boolean isConsumed(){return consumed;} public boolean isStackable(){return stackable;} public boolean isProgressTracked(){return progressTracked;}

    public int requiredForUnits(int units)
    {
        if(!consumed) return units>0?1:0; if(units<=0) return 0;
        long value=((long)units*requiredNumerator+requiredDenominator-1L)/requiredDenominator;
        return value>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)value;
    }
    public int getPossibleOutputUnits(int available)
    {
        if(!consumed) return available>0?Integer.MAX_VALUE:0; if(available<=0) return 0;
        long value=(long)available*requiredDenominator/requiredNumerator;
        return value>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)value;
    }
    public int getInventorySlotsForUnits(int units)
    {
        if(units<=0) return 0; if(!consumed) return 1;
        int required=requiredForUnits(units); return stackable?(required>0?1:0):required;
    }
    public int getEstimatedCostPerOutput(int price)
    {
        if(!consumed||price<=0) return 0;
        long value=((long)price*requiredNumerator+requiredDenominator-1L)/requiredDenominator;
        return value>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)value;
    }
}
