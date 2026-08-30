package net.runelite.client.plugins.microbot.KSPTradeReceiver;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("KSPTradeReceiver")
public interface KSPTradeReceiverConfig extends Config
{
    @ConfigSection(
            name = "Trader",
            description = "Who the plugin may accept trades from",
            position = 0
    )
    String traderSection = "trader";

    @ConfigSection(
            name = "Safety",
            description = "Trade acceptance safety checks",
            position = 1
    )
    String safetySection = "safety";

    @ConfigSection(
            name = "Banking",
            description = "Inventory-full banking behaviour",
            position = 2
    )
    String bankingSection = "banking";

    @ConfigSection(
            name = "Overlay",
            description = "Overlay settings",
            position = 3
    )
    String overlaySection = "overlay";

    @ConfigItem(
            keyName = "traderName",
            name = "Trader Name",
            description = "Exact RuneScape display name allowed to trade with this account",
            position = 0,
            section = traderSection
    )
    default String traderName()
    {
        return "";
    }

    @ConfigItem(
            keyName = "respondToTradeRequests",
            name = "Respond To Requests",
            description = "Automatically respond when the configured player sends a trade request",
            position = 1,
            section = traderSection
    )
    default boolean respondToTradeRequests()
    {
        return true;
    }

    @ConfigItem(
            keyName = "requestTimeoutSeconds",
            name = "Request Timeout",
            description = "How many seconds an incoming configured-player trade request stays valid",
            position = 2,
            section = traderSection
    )
    default int requestTimeoutSeconds()
    {
        return 15;
    }

    @ConfigItem(
            keyName = "requireEmptyOwnOffer",
            name = "Require Empty Own Offer",
            description = "Refuse first-screen acceptance if your side of the trade contains an item",
            position = 0,
            section = safetySection
    )
    default boolean requireEmptyOwnOffer()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoAcceptFirstScreen",
            name = "Accept First Screen",
            description = "Automatically accept the first trade screen after verifying the configured player",
            position = 1,
            section = safetySection
    )
    default boolean autoAcceptFirstScreen()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoAcceptConfirmation",
            name = "Accept Confirmation",
            description = "Automatically accept the final trade confirmation after verifying the opponent name",
            position = 2,
            section = safetySection
    )
    default boolean autoAcceptConfirmation()
    {
        return true;
    }

    @ConfigItem(
            keyName = "bankWhenFull",
            name = "Bank When Full",
            description = "Walk to the nearest bank and deposit the inventory when all 28 slots are occupied",
            position = 0,
            section = bankingSection
    )
    default boolean bankWhenFull()
    {
        return true;
    }

    @ConfigItem(
            keyName = "returnToTradeTile",
            name = "Return To Trade Tile",
            description = "After banking, walk back to the tile saved when the configured trade was received",
            position = 1,
            section = bankingSection
    )
    default boolean returnToTradeTile()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Show trade, banking and return-to-tile status",
            position = 0,
            section = overlaySection
    )
    default boolean showOverlay()
    {
        return true;
    }
}
