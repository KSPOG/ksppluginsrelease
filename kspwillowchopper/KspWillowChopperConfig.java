package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(KspWillowChopperConfig.GROUP)
@ConfigInformation(
        "<html>"
                + "<h2>KSP Willow Chopper</h2>"
                + "<p>Dedicated willow chopper with direct bank/tree interaction.</p>"
                + "<p><b>Bank logs ON:</b> chop until full, directly open the nearby bank, deposit willow logs, "
                + "close the bank, then directly click the nearest loaded willow tree.</p>"
                + "<p><b>Bank logs OFF:</b> burn willow logs on a nearby Forester's Campfire. "
                + "If no campfire exists, the plugin obtains/uses a tinderbox and creates a fire first.</p>"
                + "<p>Forestry event helpers can be enabled individually.</p>"
                + "</html>")
public interface KspWillowChopperConfig extends Config {
    String GROUP = "KspWillowChopper";

    @ConfigSection(name = "General", description = "Core willow chopping behavior", position = 0)
    String generalSection = "general";

    @ConfigSection(name = "Forestry", description = "Forestry random event handling", position = 1, closedByDefault = true)
    String forestrySection = "forestry";

    @ConfigItem(
            keyName = "bankLogs",
            name = "Bank logs",
            description = "ON = bank willow logs. OFF = burn willow logs on a Forester's Campfire.",
            position = 0,
            section = generalSection
    )
    default boolean bankLogs() { return true; }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show overlay",
            description = "Show runtime, XP, logs, Forestry and campfire statistics.",
            position = 1,
            section = generalSection
    )
    default boolean showOverlay() { return true; }

    @ConfigItem(
            keyName = "enableForestry",
            name = "Enable Forestry",
            description = "Allow the plugin to participate in nearby Forestry events.",
            position = 0,
            section = forestrySection
    )
    default boolean enableForestry() { return true; }

    @ConfigItem(keyName = "rootEvent", name = "Rising Roots", description = "Handle Rising Roots.", position = 1, section = forestrySection)
    default boolean rootEvent() { return true; }

    @ConfigItem(keyName = "saplingEvent", name = "Struggling Sapling", description = "Handle Struggling Sapling and reuse the discovered optimal combination for the whole event.", position = 2, section = forestrySection)
    default boolean saplingEvent() { return true; }

    @ConfigItem(keyName = "entlingsEvent", name = "Friendly Entlings", description = "Handle Friendly Entlings.", position = 3, section = forestrySection)
    default boolean entlingsEvent() { return true; }

    @ConfigItem(keyName = "hivesEvent", name = "Beehives", description = "Handle Beehive events.", position = 4, section = forestrySection)
    default boolean hivesEvent() { return true; }

    @ConfigItem(keyName = "eggEvent", name = "Pheasant Control", description = "Handle Pheasant Control.", position = 5, section = forestrySection)
    default boolean eggEvent() { return true; }

    @ConfigItem(keyName = "foxEvent", name = "Poachers / Fox", description = "Handle Poachers/Fox events.", position = 6, section = forestrySection)
    default boolean foxEvent() { return true; }

    @ConfigItem(keyName = "ritualEvent", name = "Enchantment Ritual", description = "Handle Enchantment Ritual circles.", position = 7, section = forestrySection)
    default boolean ritualEvent() { return true; }

    @ConfigItem(keyName = "leprechaunEvent", name = "Woodcutting Leprechaun", description = "Use End of Rainbow tiles during the Leprechaun event.", position = 8, section = forestrySection)
    default boolean leprechaunEvent() { return true; }

    @ConfigItem(keyName = "flowersEvent", name = "Flowering Tree", description = "Participate in Flowering Tree events.", position = 9, section = forestrySection)
    default boolean flowersEvent() { return true; }
}
