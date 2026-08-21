package net.runelite.client.plugins.microbot.kspbryophyta;

public enum BryophytaStrategy {
    MELEE("Melee"),RANGED("Ranged"),MAGIC_FIRE("Magic - Fire");
    private final String displayName;
    BryophytaStrategy(String name){displayName=name;}
    @Override public String toString(){return displayName;}
}
