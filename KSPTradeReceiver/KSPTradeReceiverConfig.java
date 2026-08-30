package net.runelite.client.plugins.microbot.KSPTradeReceiver;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("KSPTradeReceiver")
public interface KSPTradeReceiverConfig extends Config
{
    @ConfigSection(
            name = "Local Mule",
            description = "Loopback communication used by worker plugins on this computer",
            position = 0
    )
    String localMuleSection = "localMule";

    @ConfigSection(
            name = "Trader",
            description = "Manual/fallback trader configuration",
            position = 1
    )
    String traderSection = "trader";

    @ConfigSection(
            name = "Safety",
            description = "Trade acceptance safety checks",
            position = 2
    )
    String safetySection = "safety";

    @ConfigSection(
            name = "Banking",
            description = "Inventory banking behaviour",
            position = 3
    )
    String bankingSection = "banking";

    @ConfigSection(
            name = "Overlay",
            description = "Overlay settings",
            position = 4
    )
    String overlaySection = "overlay";

    @ConfigItem(
            keyName = "enableLocalMule",
            name = "Enable Local Mule",
            description = "Listen only on 127.0.0.1 for READY_TO_TRADE jobs from local KSP worker plugins",
            position = 0,
            section = localMuleSection
    )
    default boolean enableLocalMule()
    {
        return true;
    }

    @ConfigItem(
            keyName = "localMulePort",
            name = "Local Port",
            description = "Loopback TCP port used by the receiver and worker plugins",
            position = 1,
            section = localMuleSection
    )
    @Range(min = 1024, max = 65535)
    default int localMulePort()
    {
        return 17841;
    }

    @ConfigItem(
            keyName = "logoutQuietSeconds",
            name = "Logout Quiet Time",
            description = "After the final queued/active transfer completes, remain online this many seconds before logging out. A new job cancels the logout.",
            position = 2,
            section = localMuleSection
    )
    @Range(min = 1, max = 120)
    default int logoutQuietSeconds()
    {
        return 10;
    }

    @ConfigItem(
            keyName = "jobTimeoutMinutes",
            name = "Worker Timeout",
            description = "Fail a queued/active mule job when its worker stops polling the local coordinator for this many minutes",
            position = 3,
            section = localMuleSection
    )
    @Range(min = 1, max = 60)
    default int jobTimeoutMinutes()
    {
        return 5;
    }

    @ConfigItem(
            keyName = "autoLoginForJobs",
            name = "Login For Jobs",
            description = "Use Microbot LoginManager with the active profile whenever at least one local mule job is pending",
            position = 4,
            section = localMuleSection
    )
    default boolean autoLoginForJobs()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoLogoutWhenDone",
            name = "Logout When Done",
            description = "Log out only after every local mule job is complete/failed/cancelled and the quiet timer expires",
            position = 5,
            section = localMuleSection
    )
    default boolean autoLogoutWhenDone()
    {
        return true;
    }

    @ConfigItem(
            keyName = "traderName",
            name = "Manual Trader Name",
            description = "Fallback RuneScape display name when Local Mule is disabled. Local Mule jobs dynamically supply the active trader name.",
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
            description = "Automatically respond when the current allowed player sends a trade request",
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
            description = "How many seconds an incoming allowed-player trade request stays valid",
            position = 2,
            section = traderSection
    )
    @Range(min = 5, max = 60)
    default int requestTimeoutSeconds()
    {
        return 15;
    }

    @ConfigItem(
            keyName = "requireEmptyOwnOffer",
            name = "Require Empty Own Offer",
            description = "Refuse first-screen acceptance if the mule's side of the trade contains an item",
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
            description = "Automatically accept the first trade screen after verifying the active worker",
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
            description = "Automatically accept the final trade confirmation after verifying the active worker",
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
            keyName = "bankAfterTransfer",
            name = "Bank After Transfer",
            description = "Bank the received inventory after every completed local mule transfer before activating the next worker",
            position = 1,
            section = bankingSection
    )
    default boolean bankAfterTransfer()
    {
        return true;
    }

    @ConfigItem(
            keyName = "returnToTradeTile",
            name = "Return To Trade Tile",
            description = "After banking, walk back to the saved trade tile before processing the next worker",
            position = 2,
            section = bankingSection
    )
    default boolean returnToTradeTile()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Show local coordinator, trade, banking and lifecycle status",
            position = 0,
            section = overlaySection
    )
    default boolean showOverlay()
    {
        return true;
    }
}
