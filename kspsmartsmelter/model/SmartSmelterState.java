package net.runelite.client.plugins.microbot.kspsmartsmelter.model;

public enum SmartSmelterState {
    STARTING,
    SCANNING,
    WALKING_TO_BANK,
    BANKING,
    WALKING_TO_FURNACE,
    SMELTING,
    WALKING_TO_GE,
    RESTOCKING,
    WAITING_FOR_OFFERS,
    NO_PROFITABLE_ROUTE,
    STOPPED
}
