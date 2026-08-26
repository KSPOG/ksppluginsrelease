package com.ksp.geflipper.executionmodel;

import java.util.*;

public final class GeTaxService {
    private static final Set<String> TAX_EXEMPT = Set.of(
            "old school bond","chisel","gardening trowel","glassblowing pipe","hammer","needle",
            "pestle and mortar","rake","saw","secateurs","seed dibber","shears","spade","watering can");

    public long taxPerItem(String itemName,long sellPrice){
        if(sellPrice<50||itemName==null||TAX_EXEMPT.contains(itemName.toLowerCase(Locale.ROOT)))return 0;
        return Math.min(5_000_000L,(long)Math.floor(sellPrice*0.02));
    }
    public long postTaxUnitPrice(String itemName,long sellPrice){return Math.max(0,sellPrice-taxPerItem(itemName,sellPrice));}
    public long minimumBreakEvenSell(String itemName,long unitCost){
        long p=Math.max(1,unitCost+1); if(p<50||TAX_EXEMPT.contains(itemName.toLowerCase(Locale.ROOT)))return p;
        if(p<=245_000_000L)return (long)Math.ceil(p/0.98); return p+5_000_000L;
    }
}
