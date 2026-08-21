package net.runelite.client.plugins.microbot.kspbankorganizer;

public enum GearSortMode {
    COMBAT_STYLE("Combat style"), EQUIPMENT_TYPE("Equipment type");
    private final String displayName;
    GearSortMode(String name){displayName=name;}
    @Override public String toString(){return displayName;}
}
