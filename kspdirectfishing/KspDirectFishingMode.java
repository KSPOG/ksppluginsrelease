package net.runelite.client.plugins.microbot.kspdirectfishing;

import net.runelite.client.game.FishingSpot;
import java.util.List;

public enum KspDirectFishingMode {
    SHRIMP_ANCHOVIES("Shrimp / Anchovies","Small Net",List.of("Small fishing net"),List.of("Raw shrimps","Raw anchovies"),FishingSpot.SHRIMP.getIds()),
    SARDINE_HERRING("Sardine / Herring","Bait",List.of("Fishing rod","Fishing bait"),List.of("Raw sardine","Raw herring"),FishingSpot.SHRIMP.getIds());
    private final String displayName,primaryAction; private final List<String> requiredItems,rawFish; private final int[] fishingSpotIds;
    KspDirectFishingMode(String name,String action,List<String> items,List<String> fish,int[] ids){displayName=name;primaryAction=action;requiredItems=items;rawFish=fish;fishingSpotIds=ids;}
    public String getDisplayName(){return displayName;} public String getPrimaryAction(){return primaryAction;} public List<String> getRequiredItems(){return requiredItems;} public List<String> getRawFish(){return rawFish;} public int[] getFishingSpotIds(){return fishingSpotIds;}
    public boolean usesBait(){return this==SARDINE_HERRING;}
    @Override public String toString(){return displayName;}
}
