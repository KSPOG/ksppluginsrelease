package net.runelite.client.plugins.microbot.kspaiofighter;

public enum KspLootOwnership {
    LOOT_OWN("Loot Own"), LOOT_ALL("Loot All");
    private final String name;
    KspLootOwnership(String name){this.name=name;}
    @Override public String toString(){return name;}
}
