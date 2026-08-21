package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfitQuote
{
    private final FactoryRecipe recipe; private final boolean valid; private final String error;
    private final Map<String,Integer> inputPrices;
    private final int outputPrice,inputCostPerUnit,taxPerUnit,profitPerUnit,estimatedProfitPerHour; private final double roiPercent;

    private ProfitQuote(FactoryRecipe r,boolean v,String e,Map<String,Integer> p,int o,int i,int t,int profit,double roi,int hourly) { recipe=r; valid=v; error=e; inputPrices=Collections.unmodifiableMap(new LinkedHashMap<>(p)); outputPrice=o; inputCostPerUnit=i; taxPerUnit=t; profitPerUnit=profit; roiPercent=roi; estimatedProfitPerHour=hourly; }

    public static ProfitQuote valid(FactoryRecipe recipe,Map<String,Integer> prices,int output,int inputCost,int tax,int profit,double roi)
    {
        long hourly=(long)profit*recipe.getEstimatedUnitsPerHour();
        return new ProfitQuote(recipe,true,"",prices,output,inputCost,tax,profit,roi,hourly>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)hourly);
    }
    public static ProfitQuote invalid(FactoryRecipe recipe,String error) { return new ProfitQuote(recipe,false,error,Collections.emptyMap(),0,0,0,0,0,0); }
    public boolean meets(F2PProcessingFactoryConfig c) { return valid&&profitPerUnit>0&&profitPerUnit>=c.minimumProfitPerUnit()&&roiPercent>=c.minimumRoiPercent(); }

    public FactoryRecipe getRecipe(){return recipe;} public boolean isValid(){return valid;} public String getError(){return error;}
    public Map<String,Integer> getInputPrices(){return inputPrices;} public int getOutputPrice(){return outputPrice;}
    public int getInputCostPerUnit(){return inputCostPerUnit;} public int getTaxPerUnit(){return taxPerUnit;} public int getProfitPerUnit(){return profitPerUnit;}
    public double getRoiPercent(){return roiPercent;} public int getEstimatedProfitPerHour(){return estimatedProfitPerHour;}
}
