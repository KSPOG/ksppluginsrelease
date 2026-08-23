package net.runelite.client.plugins.microbot.KSPAutoMiner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("KSPAutoMiner")
public interface KSPAutoMinerConfig extends Config {
    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigItem(
            keyName = "mode",
            name = "Mode",
            description = "Select mining mode",
            position = 0,
            section = generalSection
    )
    default KSPAutoMinerMode mode() {
        return KSPAutoMinerMode.MINE_DROP;
    }

    @ConfigItem(
            keyName = "rock",
            name = "Rock",
            description = "Select which rock to mine (non-progressive modes)",
            position = 1,
            section = generalSection
    )
    default KSPAutoMinerRock rock() {
        return KSPAutoMinerRock.COPPER_TIN;
    }

    @ConfigItem(
            keyName = "enableAntiban",
            name = "Enable Antiban",
            description = "Use universal antiban settings",
            position = 2,
            section = generalSection
    )
    default boolean enableAntiban() {
        return true;
    }
}
