package net.runelite.client.plugins.microbot.kspwillowchopper;


import net.runelite.client.plugins.microbot.kspsupport.KspBreakConfig;
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
                + "<p>Configure the tree, resource mode, overlay and Forestry events in the Chopper side panel.</p>"
                + "<p><b>Chop &amp; Bank:</b> bank the selected resource when full.</p>"
                + "<p><b>Chop &amp; Burn:</b> use campfire Firemaking for logs.</p>"
                + "<p><b>Chop &amp; Drop:</b> drop only the selected resource when full.</p>"
                + "<p><b>Progressive Chop &amp; Bank / Burn / Drop:</b> automatically change trees as skills advance.</p>"
                + "<p>Progressive Burn respects both Woodcutting and Firemaking levels.</p>"
                + "<p>Non-log resources cannot use Firemaking mode.</p>"
                + "<p>Forestry helpers run inside this plugin and do not register themselves as Microbot global blocking events.</p>"
                + "</html>")
public interface KspWillowChopperConfig extends Config, KspMuleConfig, KspBreakConfig {
    @ConfigSection(name = "Break Handler", description = "Randomized logout breaks", position = 100)
    String breakHandlerSection = KspBreakConfig.SECTION;

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
            section = generalSection,
            hidden = true
    )
    default KspTree tree() {
        return KspTree.WILLOW;
    }

    @ConfigItem(
            keyName = "mode",
            name = "Resource handling",
            description = "Manual and progressive Bank, Burn, and Drop. Configure in the Chopper side panel.",
            position = 1,
            section = generalSection,
            hidden = true
    )
    default KspChopperMode mode() {
        return KspChopperMode.CHOP_BANK;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show overlay",
            description = "Show runtime state, resource counts, XP and Forestry statistics.",
            position = 2,
            section = generalSection,
            hidden = true
    )
    default boolean showOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "enableForestry",
            name = "Enable Forestry",
            description = "Temporarily interrupt the chop/bank/burn cycle to handle supported nearby Forestry events.",
            position = 0,
            section = forestrySection,
            hidden = true
    )
    default boolean enableForestry() {
        return true;
    }

    @ConfigItem(keyName = "rootEvent", name = "Rising Roots", description = "Handle Rising Roots.", position = 1, section = forestrySection, hidden = true)
    default boolean rootEvent() { return true; }

    @ConfigItem(keyName = "saplingEvent", name = "Struggling Sapling", description = "Handle Struggling Sapling and reuse the learned combination.", position = 2, section = forestrySection, hidden = true)
    default boolean saplingEvent() { return true; }

    @ConfigItem(keyName = "entlingsEvent", name = "Friendly Entlings", description = "Handle Friendly Entlings.", position = 3, section = forestrySection, hidden = true)
    default boolean entlingsEvent() { return true; }

    @ConfigItem(keyName = "hivesEvent", name = "Beehives", description = "Handle Beehive events.", position = 4, section = forestrySection, hidden = true)
    default boolean hivesEvent() { return true; }

    @ConfigItem(keyName = "eggEvent", name = "Pheasant Control", description = "Handle Pheasant Control.", position = 5, section = forestrySection, hidden = true)
    default boolean eggEvent() { return true; }

    @ConfigItem(keyName = "foxEvent", name = "Poachers / Fox", description = "Handle Poachers/Fox events.", position = 6, section = forestrySection, hidden = true)
    default boolean foxEvent() { return true; }

    @ConfigItem(keyName = "ritualEvent", name = "Enchantment Ritual", description = "Handle Enchantment Ritual circles.", position = 7, section = forestrySection, hidden = true)
    default boolean ritualEvent() { return true; }

    @ConfigItem(keyName = "leprechaunEvent", name = "Woodcutting Leprechaun", description = "Use End of Rainbow tiles during the Leprechaun event.", position = 8, section = forestrySection, hidden = true)
    default boolean leprechaunEvent() { return true; }

    @ConfigItem(keyName = "flowersEvent", name = "Flowering Tree", description = "Participate in Flowering Tree events.", position = 9, section = forestrySection, hidden = true)
    default boolean flowersEvent() { return true; }
}
