package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup(KspAioFighterConfig.GROUP)
public interface KspAioFighterMuleConfig extends KspAioFighterConfig, KspMuleConfig
{
    @ConfigSection(name = "Combat", description = "NPC and combat settings", position = 0)
    String combatSection = "combat";
    @ConfigSection(name = "Area", description = "Safe spot and NPC attack area", position = 1)
    String areaSection = "area";
    @ConfigSection(name = "Training", description = "Combat skill targets", position = 2)
    String trainingSection = "training";
    @ConfigSection(name = "Gear", description = "Gear to equip for each training style", position = 3)
    String gearSection = "gear";
    @ConfigSection(name = "Supplies", description = "Healing and potion settings", position = 4)
    String suppliesSection = "supplies";
    @ConfigSection(name = "Loot", description = "Looting, burying, and alching", position = 5)
    String lootSection = "loot";
    @ConfigSection(name = "Paint", description = "RuneScape-style fighter paint shown over the chatbox", position = 6)
    String paintSection = "paint";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
}
