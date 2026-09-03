package net.runelite.client.plugins.microbot.mining;


import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;
import net.runelite.client.plugins.microbot.mining.data.MiningOreOption;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;

@ConfigGroup("Mining")
@ConfigInformation("<h2>Auto Mining</h2>" +
        "<h3>Version: " + AutoMiningPlugin.version + "</h3>" +
        "<p>1. <strong>Ore Selection:</strong> Copper &amp; Tin mode keeps both ore amounts balanced automatically.</p>" +
        "<p></p>" +
        "<p>2. <strong>Distance to Stray:</strong> Set the maximum distance in tiles that the bot can travel from its initial position.</p>" +
        "<p></p>" +
        "<p>3. <strong>Banking:</strong> Deposit inventory is used automatically. The best usable pickaxe is restored or upgraded before leaving the bank.</p>" +
        "<p></p>" +
        "<p>4. <strong>Dropping:</strong> Inventory pickaxes are retained automatically; all other inventory items are dropped.</p>" +
        "<p></p>" +
        "<p>5. <strong>Basalt:</strong> Enable UseBank to note basalt at Snowflake.</p>")
public interface AutoMiningConfig extends Config, KspMuleConfig, KspSupportConfig
{
    @ConfigSection(name = "General", description = "General settings", position = 0)
    String generalSection = "general";
    @ConfigSection(name = "Dropping", description = "Dropping settings", position = 1)
    String droppingSection = "droppingSection";
    @ConfigSection(name = "Banking", description = "Banking settings", position = 2)
    String bankingSection = "bankingSection";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;

    @ConfigItem(keyName = "Ore", name = "Ore", description = "Choose the ore to mine", position = 0, section = generalSection)
    default MiningOreOption ORE() { return MiningOreOption.COPPER_AND_TIN; }
    @ConfigItem(keyName = "progressiveMode", name = "Progressive mode", description = "Mine balanced Copper & Tin at levels 1-14, then automatically select the highest unlocked ore", position = 1, section = generalSection)
    default boolean progressiveMode() { return false; }
    @ConfigItem(keyName = "DistanceToStray", name = "Distance to Stray", description = "Maximum distance from the initial mining position", position = 2, section = generalSection)
    default int distanceToStray() { return 20; }
    @ConfigItem(keyName = "maxPlayersInArea", name = "Max players in area", description = "Hop when at least this many mining players are nearby. 0 disables hopping", position = 3, section = generalSection)
    default int maxPlayersInArea() { return 0; }
    @ConfigItem(keyName = "leagueMode", name = "League mode (anti-AFK)", description = "Periodically press a key to reset the idle timer", position = 4, section = generalSection)
    default boolean leagueMode() { return false; }
    @ConfigItem(keyName = "UseBank", name = "UseBank", description = "Bank the inventory and return to the mining location", position = 0, section = bankingSection)
    default boolean useBank() { return false; }
    @ConfigItem(keyName = "clayBracelet", name = "Use Clay Bracelet", description = "Withdraw and equip a bracelet of clay. Start with one equipped", position = 1, section = bankingSection)
    default boolean clayBracelet() { return false; }
    @ConfigItem(keyName = "dropOrder", name = "Drop Order", description = "Order used when dropping inventory items", position = 0, section = droppingSection)
    default InteractOrder interactOrder() { return InteractOrder.STANDARD; }
}
