package net.runelite.client.plugins.microbot.kspbossgear;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(KspBossGearConfig.GROUP)
public interface KspBossGearConfig extends Config
{
    String GROUP = "kspbossgear";

    @ConfigItem(
        keyName = "highlightBank",
        name = "Highlight bank",
        description = "Highlight equipment for the selected boss and tier in the bank",
        position = 0
    )
    default boolean highlightBank()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightInventory",
        name = "Highlight inventory",
        description = "Highlight equipment for the selected boss and tier in the inventory",
        position = 1
    )
    default boolean highlightInventory()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightAlternatives",
        name = "Highlight alternatives",
        description = "Also highlight other valid items from the same Wiki recommendation cell",
        position = 2
    )
    default boolean highlightAlternatives()
    {
        return true;
    }

    @Range(min = 5, max = 75)
    @ConfigItem(
        keyName = "highlightOpacity",
        name = "Highlight opacity",
        description = "Fill opacity used for recommended item highlights",
        position = 3
    )
    default int highlightOpacity()
    {
        return 24;
    }
}
