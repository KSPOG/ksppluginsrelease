package net.runelite.client.plugins.microbot.f2pprocessingfactory;

public enum FactoryState
{
    STARTING,
    OPENING_BANK,
    EVALUATING_RECIPES,
    PREPARING_CYCLE,
    BUYING_INPUTS,
    PREPARING_INVENTORY,
    PROCESSING,
    BANKING_OUTPUT,
    SELLING_OUTPUT,
    WAITING_FOR_LIMIT,
    WAITING_FOR_MARKET,
    STOPPED
}
