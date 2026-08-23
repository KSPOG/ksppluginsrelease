package net.runelite.client.plugins.microbot.kspbryophyta;

final class BryophytaEquipmentItem {
    private final int id;
    private final String name;
    private final boolean members;
    private final boolean twoHanded;

    BryophytaEquipmentItem(int id, String name, boolean members, boolean twoHanded) {
        this.id = id;
        this.name = name;
        this.members = members;
        this.twoHanded = twoHanded;
    }

    int getId() { return id; }
    String getName() { return name; }
    boolean isMembers() { return members; }
    boolean isTwoHanded() { return twoHanded; }
    @Override public String toString() { return name; }
}
