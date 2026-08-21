package net.runelite.client.plugins.microbot.kspbankorganizer;

final class PlannedItem {
    private final int itemId,targetTab; private final String name; private final ItemCategory category;
    PlannedItem(int id,String name,ItemCategory category,int tab){itemId=id;this.name=name;this.category=category;targetTab=tab;}
    int itemId(){return itemId;} String name(){return name;} ItemCategory category(){return category;} int targetTab(){return targetTab;}
}
