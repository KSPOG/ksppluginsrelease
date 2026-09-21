package net.runelite.client.plugins.microbot.kspbonestobananas;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("kspBonesToBananas")
public interface KspBonesToBananasConfig extends Config
{
    enum AntibanProfile
    {
        LIGHT("Light", 0, 180, .015, .004, .012, 3_000, 4_500, 4_000, 9_000, 80, 140),
        BALANCED("Balanced", 40, 320, .030, .010, .028, 3_500, 6_500, 6_000, 16_000, 55, 100),
        HEAVY("Heavy", 90, 520, .060, .020, .050, 4_000, 9_000, 9_000, 24_000, 35, 70);

        final String display;
        final int jitterMinMs, jitterMaxMs, shortPauseMinMs, shortPauseMaxMs;
        final int longBreakMinMs, longBreakMaxMs, castsMin, castsMax;
        final double moveChance, offscreenChance, shortPauseChance;

        AntibanProfile(String display, int jitterMinMs, int jitterMaxMs,
                       double moveChance, double offscreenChance, double shortPauseChance,
                       int shortPauseMinMs, int shortPauseMaxMs,
                       int longBreakMinMs, int longBreakMaxMs,
                       int castsMin, int castsMax)
        {
            this.display = display;
            this.jitterMinMs = jitterMinMs;
            this.jitterMaxMs = jitterMaxMs;
            this.moveChance = moveChance;
            this.offscreenChance = offscreenChance;
            this.shortPauseChance = shortPauseChance;
            this.shortPauseMinMs = shortPauseMinMs;
            this.shortPauseMaxMs = shortPauseMaxMs;
            this.longBreakMinMs = longBreakMinMs;
            this.longBreakMaxMs = longBreakMaxMs;
            this.castsMin = castsMin;
            this.castsMax = castsMax;
        }

        @Override public String toString() { return display; }
    }

    @Range(min = 1, max = 100_000)
    @ConfigItem(keyName = "minProfitPerCast", name = "Minimum profit / cast",
            description = "Never buy or cast a bone type below this estimated GP profit per inventory.", position = 0)
    default int minProfitPerCast() { return 1; }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "minRoiPercent", name = "Minimum ROI %",
            description = "Minimum ROI for fresh bone/rune purchases. Positive profit is always required.", position = 1)
    default int minRoiPercent() { return 1; }

    @Range(min = 0, max = 20)
    @ConfigItem(keyName = "buyMarkupPercent", name = "GE buy markup %",
            description = "Markup above current instant-buy prices.", position = 2)
    default int buyMarkupPercent() { return 1; }

    @Range(min = 0, max = 20)
    @ConfigItem(keyName = "sellDiscountPercent", name = "GE sell discount %",
            description = "Discount below current instant-sell Banana price.", position = 3)
    default int sellDiscountPercent() { return 1; }

    @Range(min = 25, max = 10_000)
    @ConfigItem(keyName = "restockBones", name = "Restock bones",
            description = "Maximum number of profitable bones to buy per restock cycle.", position = 4)
    default int restockBones() { return 1000; }

    @Range(min = 0, max = 100_000_000)
    @ConfigItem(keyName = "cashReserve", name = "Cash reserve",
            description = "Coins the plugin will not commit to new purchases.", position = 5)
    default int cashReserve() { return 10_000; }

    @Range(min = 1, max = 100)
    @ConfigItem(keyName = "maxSpendPercent", name = "Max spend %",
            description = "Maximum percentage of spendable cash used by one restock.", position = 6)
    default int maxSpendPercent() { return 85; }

    @Range(min = 100, max = 10_000)
    @ConfigItem(keyName = "sellThreshold", name = "Banana sell threshold",
            description = "Sell accumulated Bananas when bank stock reaches this amount, or when inputs run out.", position = 7)
    default int sellThreshold() { return 500; }

    @Range(min = 15, max = 300)
    @ConfigItem(keyName = "priceRefreshSeconds", name = "Price refresh",
            description = "Seconds between market rescans when no profitable bone type exists.", position = 8)
    default int priceRefreshSeconds() { return 60; }

    @Range(min = 10, max = 180)
    @ConfigItem(keyName = "geOfferTimeoutSeconds", name = "GE offer timeout",
            description = "Abort and retry a stalled GE offer after this many seconds.", position = 9)
    default int geOfferTimeoutSeconds() { return 35; }

    @Range(min = 1, max = 30)
    @ConfigItem(keyName = "bankOverheadSeconds", name = "Bank cycle estimate",
            description = "Estimated banking seconds per cast, used only for projected GP/h.", position = 10)
    default int bankOverheadSeconds() { return 4; }

    @ConfigItem(keyName = "antiban", name = "Anti-ban",
            description = "Enable non-blocking cast jitter, mouse variation and randomized breaks.", position = 11)
    default boolean antiban() { return true; }

    @ConfigItem(keyName = "antibanProfile", name = "Anti-ban profile",
            description = "Controls anti-ban frequency and pause lengths.", position = 12)
    default AntibanProfile antibanProfile() { return AntibanProfile.BALANCED; }
}
