package net.runelite.client.plugins.microbot.kspmule;

import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Shared worker-side mule settings. Worker configs inherit these annotated methods and
 * declare a ConfigSection whose value is {@link #SECTION}, so every money-making plugin
 * gets the same controls without duplicating the implementation.
 */
public interface KspMuleConfig
{
    String SECTION = "kspLocalMule";

    @ConfigItem(
            keyName = "muleEnabled",
            name = "Enable Local Mule",
            description = "Transfer excess coins to KSP Trade Receiver over localhost.",
            position = 0,
            section = SECTION
    )
    default boolean muleEnabled()
    {
        return false;
    }

    @Range(min = 1_000, max = 2_000_000_000)
    @ConfigItem(
            keyName = "muleTransferAt",
            name = "Start Transfer At",
            description = "Start a transfer when total coins across inventory and bank reach this amount.",
            position = 1,
            section = SECTION
    )
    default int muleTransferAt()
    {
        return 2_000_000;
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
            keyName = "muleKeepInBank",
            name = "Keep In Bank",
            description = "Protected coin reserve left in the worker bank and excluded from mule transfers.",
            position = 2,
            section = SECTION
    )
    default int muleKeepInBank()
    {
        return 0;
    }

    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
            keyName = "muleKeepTradingCapital",
            name = "Keep Trading Capital",
            description = "Spendable coins restored to the worker inventory after a successful transfer.",
            position = 3,
            section = SECTION
    )
    default int muleKeepTradingCapital()
    {
        return 500_000;
    }

    @Range(min = 1024, max = 65535)
    @ConfigItem(
            keyName = "muleReceiverPort",
            name = "Receiver Port",
            description = "Local TCP port used by KSP Trade Receiver. Default is 17841.",
            position = 4,
            section = SECTION
    )
    default int muleReceiverPort()
    {
        return 17841;
    }

    @Range(min = 30, max = 600)
    @ConfigItem(
            keyName = "muleTimeoutSeconds",
            name = "Mule Timeout",
            description = "Maximum time to wait for queueing, travel and trade completion.",
            position = 5,
            section = SECTION
    )
    default int muleTimeoutSeconds()
    {
        return 180;
    }
}
