package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(KspF2PHighAlchTraderConfig.GROUP)
@ConfigInformation(
        "KSP High Alch Trader dynamically ranks High Alchemy items using live OSRS Wiki prices, " +
        "always scans its built-in F2P pool and automatically adds members-only candidates when the account is members and is in a members world, " +
        "uses one GE offer at a time below configured safe prices, collects it, then alchs the purchased stock before placing another offer. " +
        "<br><br><b>Recommended:</b> start with a Staff of fire equipped or in the bank and enough starting GP. " +
        "The plugin will not intentionally buy an item above the configured minimum profit threshold. " +
        "Its custom anti-ban runs only between confirmed alch casts so GE and banking transitions are not interrupted."
)
public interface KspF2PHighAlchTraderConfig extends Config {
    String GROUP = "ksp-f2p-high-alch-trader";


    @ConfigSection(
            name = "Local Mule",
            description = "Localhost communication with KSP Trade Receiver.",
            position = 100
    )
    String muleSection = "localMule";

    @ConfigItem(
            keyName = "minimumProfitPerCast",
            name = "Minimum profit / alch",
            description = "Minimum projected GP profit per High Alchemy cast after the item and rune costs.",
            position = 1
    )
    @Range(min = 0, max = 5000)
    default int minimumProfitPerCast() {
        return 200;
    }

    @ConfigItem(
            keyName = "minimumExpectedGpPerHour",
            name = "Minimum projected GP/hr",
            description = "Ignore opportunities whose projected profit at 1,200 casts/hour is below this value.",
            position = 2
    )
    @Range(min = 0, max = 5000000)
    default int minimumExpectedGpPerHour() {
        return 200_000;
    }

    @ConfigItem(
            keyName = "minimumVolume",
            name = "Minimum market volume",
            description = "Ignore candidates below this real-time high-price volume value when the API supplies volume.",
            position = 3
    )
    @Range(min = 0, max = 1000000)
    default int minimumVolume() {
        return 20;
    }

    @ConfigItem(
            keyName = "marketRefreshMinutes",
            name = "Market refresh (minutes)",
            description = "How often the candidate ranking is rebuilt from live prices.",
            position = 4
    )
    @Range(min = 1, max = 60)
    default int marketRefreshMinutes() {
        return 5;
    }

    @ConfigItem(
            keyName = "buyPriceBufferPercent",
            name = "Buy price buffer %",
            description = "Optional increase above the latest instant-buy price. The result is always capped so the configured minimum profit is preserved.",
            position = 5
    )
    @Range(min = 0, max = 10)
    default int buyPriceBufferPercent() {
        return 0;
    }

    @ConfigItem(
            keyName = "maxSpendPerCycle",
            name = "Max spend / cycle",
            description = "Maximum coins committed to the single alchable GE buy in one cycle.",
            position = 6
    )
    @Range(min = 10_000, max = 100_000_000)
    default int maxSpendPerCycle() {
        return 2_000_000;
    }

    @ConfigItem(
            keyName = "maxQuantityPerCycle",
            name = "Max quantity / cycle",
            description = "Maximum quantity requested by the single alchable GE offer. Known GE limits are also respected.",
            position = 7
    )
    @Range(min = 1, max = 10000)
    default int maxQuantityPerCycle() {
        return 125;
    }

    @ConfigItem(
            keyName = "reserveCoins",
            name = "Coin reserve",
            description = "Coins kept out of the alchable purchase budget.",
            position = 8
    )
    @Range(min = 0, max = 100_000_000)
    default int reserveCoins() {
        return 100_000;
    }

    @ConfigItem(
            keyName = "natureRuneTarget",
            name = "Nature rune target",
            description = "When Nature runes are low, buy enough to approach this inventory amount.",
            position = 9
    )
    @Range(min = 100, max = 10000)
    default int natureRuneTarget() {
        return 1000;
    }

    @ConfigItem(
            keyName = "minimumNatureRunes",
            name = "Restock Nature below",
            description = "Trigger a Nature rune restock below this inventory quantity.",
            position = 10
    )
    @Range(min = 1, max = 5000)
    default int minimumNatureRunes() {
        return 100;
    }

    @ConfigItem(
            keyName = "useFireStaff",
            name = "Use Staff of fire",
            description = "Equip a Staff of fire from the bank when possible so Fire runes are not required.",
            position = 11
    )
    default boolean useFireStaff() {
        return true;
    }

    @ConfigItem(
            keyName = "fallbackToFireRunes",
            name = "Fallback to Fire runes",
            description = "If a Staff of fire is unavailable, continue using 5 Fire runes per cast instead of stopping.",
            position = 12
    )
    default boolean fallbackToFireRunes() {
        return true;
    }

    @ConfigItem(
            keyName = "fireRuneTarget",
            name = "Fire rune target",
            description = "Target Fire rune amount when operating without a Staff of fire.",
            position = 13
    )
    @Range(min = 500, max = 50000)
    default int fireRuneTarget() {
        return 5000;
    }

    @ConfigItem(
            keyName = "offerTimeoutSeconds",
            name = "GE offer timeout",
            description = "Abort and collect a partially filled purchase after this many seconds.",
            position = 14
    )
    @Range(min = 15, max = 300)
    default int offerTimeoutSeconds() {
        return 45;
    }


    @ConfigItem(
            keyName = "slowBuyCooldownMinutes",
            name = "Slow-buy cooldown",
            description = "When an alchable GE offer reaches the GE offer timeout and is aborted, skip that item for this many minutes before considering it again.",
            position = 15
    )
    @Range(min = 1, max = 240)
    default int slowBuyCooldownMinutes() {
        return 15;
    }

    @ConfigItem(
            keyName = "customCandidateIds",
            name = "Extra item IDs",
            description = "Optional comma-separated item IDs. Members-only IDs are only eligible when the account has membership and is currently in a members world.",
            position = 16
    )
    default String customCandidateIds() {
        return "";
    }


    @ConfigItem(
            keyName = "excludedCandidateIds",
            name = "Excluded item IDs",
            description = "Optional comma-separated item IDs that must never be selected, including built-in candidates.",
            position = 17
    )
    default String excludedCandidateIds() {
        return "";
    }

    @ConfigItem(
            keyName = "useBankStockFirst",
            name = "Use bank stock first",
            description = "If enabled, existing bank stock matching the selected item may be alched before buying more. Leave disabled to protect pre-existing items.",
            position = 18
    )
    default boolean useBankStockFirst() {
        return false;
    }

    @ConfigItem(
            keyName = "respectGeLimits",
            name = "Respect GE limits",
            description = "Cap purchases using the trade limit returned by the OSRS Wiki mapping API and session purchase tracking.",
            position = 19
    )
    default boolean respectGeLimits() {
        return true;
    }

    @ConfigItem(
            keyName = "minimumCollectDelaySeconds",
            name = "Min collect delay",
            description = "Minimum reaction time in seconds after a completed GE offer is detected before collecting it.",
            position = 20
    )
    @Range(min = 1, max = 30)
    default int minimumCollectDelaySeconds() {
        return 4;
    }

    @ConfigItem(
            keyName = "maximumCollectDelaySeconds",
            name = "Max collect delay",
            description = "Maximum reaction time in seconds after a completed GE offer is detected before collecting it.",
            position = 21
    )
    @Range(min = 1, max = 30)
    default int maximumCollectDelaySeconds() {
        return 8;
    }

    @ConfigItem(
            keyName = "minimumAbortCollectDelaySeconds",
            name = "Min abort collect delay",
            description = "Minimum delay in seconds after an active GE offer is aborted before the overview Collect button is used.",
            position = 22
    )
    @Range(min = 1, max = 60)
    default int minimumAbortCollectDelaySeconds() {
        return 3;
    }

    @ConfigItem(
            keyName = "maximumAbortCollectDelaySeconds",
            name = "Max abort collect delay",
            description = "Maximum delay in seconds after an active GE offer is aborted before the overview Collect button is used.",
            position = 23
    )
    @Range(min = 1, max = 60)
    default int maximumAbortCollectDelaySeconds() {
        return 7;
    }

    @ConfigItem(
            keyName = "customAntiban",
            name = "Custom anti-ban",
            description = "Enable the trader-specific non-blocking anti-ban. It only varies behavior between confirmed High Alchemy casts and does not interrupt GE/bank state transitions.",
            position = 24
    )
    default boolean customAntiban() {
        return true;
    }

    @ConfigItem(
            keyName = "antibanProfile",
            name = "Anti-ban profile",
            description = "LIGHT keeps throughput highest; BALANCED adds moderate variation; HEAVY uses more frequent pauses and mouse-off-screen breaks.",
            position = 25
    )
    default KspHighAlchAntibanProfile antibanProfile() {
        return KspHighAlchAntibanProfile.BALANCED;
    }

    @ConfigItem(
            keyName = "enableMule",
            name = "Enable Local Mule",
            description = "Queue a coin transfer with KSP Trade Receiver on localhost when the configured total-GP threshold is reached.",
            position = 0,
            section = muleSection
    )
    default boolean enableMule() {
        return false;
    }

    @ConfigItem(
            keyName = "muleThreshold",
            name = "Start Transfer At",
            description = "Begin a mule transfer when total coins across inventory and cached bank reach at least this amount.",
            position = 1,
            section = muleSection
    )
    @Range(min = 1_000, max = 2_000_000_000)
    default int muleThreshold() {
        return 2_000_000;
    }

    @ConfigItem(
            keyName = "muleKeepCoins",
            name = "Keep Trading Capital",
            description = "Spendable coins restored to the worker after the mule transfer so the trader can continue. The normal Coin reserve is also respected.",
            position = 2,
            section = muleSection
    )
    @Range(min = 0, max = 2_000_000_000)
    default int muleKeepCoins() {
        return 500_000;
    }

    @ConfigItem(
            keyName = "muleKeepInBank",
            name = "Keep In Bank",
            description = "Protected coin reserve that remains in the bank and is excluded from the transfer amount.",
            position = 3,
            section = muleSection
    )
    @Range(min = 0, max = 2_000_000_000)
    default int muleKeepInBank() {
        return 0;
    }

    @ConfigItem(
            keyName = "mulePort",
            name = "Receiver Port",
            description = "Local TCP port used by KSP Trade Receiver. Both plugins must use the same port.",
            position = 4,
            section = muleSection
    )
    @Range(min = 1024, max = 65535)
    default int mulePort() {
        return 17841;
    }

    @ConfigItem(
            keyName = "muleRequestTimeoutSeconds",
            name = "Mule Timeout",
            description = "Maximum time to wait for queueing, mule login, travel and trade completion before cancelling and resuming.",
            position = 5,
            section = muleSection
    )
    @Range(min = 30, max = 600)
    default int muleRequestTimeoutSeconds() {
        return 180;
    }

}
