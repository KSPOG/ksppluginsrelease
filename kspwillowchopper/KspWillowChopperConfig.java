package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup(KspWillowChopperConfig.GROUP)
@ConfigInformation(
        "<html>"
                + "<h2>KSP Chopper</h2>"
                + "<p>The runtime uses a simple deterministic cycle instead of retarget/background state queues.</p>"
                + "<p><b>Bank resources ON:</b> chop the selected tree until the inventory is full, bank only the selected resource, then resume chopping.</p>"
                + "<p><b>Bank resources OFF:</b> chop until full, use a nearby campfire when available, or obtain a tinderbox and create a fire, then burn the collected logs before resuming.</p>"
                + "<p>Non-log resources cannot use Firemaking mode.</p>"
                + "<p>Forestry helpers run inside this plugin and do not register themselves as Microbot global blocking events.</p>"
                + "</html>")
public interface KspWillowChopperConfig extends Config, KspMuleConfig {
    String GROUP = "KspWillowChopper";

    @ConfigSection(
            name = "General",
            description = "Core chopping behavior",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Forestry",
            description = "Optional Forestry event handling",
            position = 1,
            closedByDefault = true
    )
    String forestrySection = "forestry";

    @ConfigSection(
            name = "Local Mule",
            description = "Automatic excess-GP transfer to KSP Trade Receiver",
            position = 90
    )
    String muleSection = KspMuleConfig.SECTION;

    @ConfigItem(
            keyName = "tree",
            name = "Tree",
            description = "Tree/resource to chop. The nearest loaded matching object is used.",
            position = 0,
            section = generalSection
    )
    default KspTree tree() {
        return KspTree.WILLOW;
    }

    @ConfigItem(
            keyName = "bankLogs",
            name = "Bank resources",
            description = "ON = bank the selected resource. OFF = burn log-producing resources using Firemaking.",
            position = 1,
            section = generalSection
    )
    default boolean bankLogs() {
        return true;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show overlay",
            description = "Show runtime state, resource counts, XP and Forestry statistics.",
            position = 2,
            section = generalSection
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "enableForestry",
            name = "Enable Forestry",
            description = "Temporarily interrupt the chop/bank/burn cycle to handle supported nearby Forestry events.",
            position = 0,
            section = forestrySection
    )
    default boolean enableForestry() {
        return true;
    }

    @ConfigItem(keyName = "rootEvent", name = "Rising Roots", description = "Handle Rising Roots.", position = 1, section = forestrySection)
    default boolean rootEvent() { return true; }

    @ConfigItem(keyName = "saplingEvent", name = "Struggling Sapling", description = "Handle Struggling Sapling and reuse the learned combination.", position = 2, section = forestrySection)
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
