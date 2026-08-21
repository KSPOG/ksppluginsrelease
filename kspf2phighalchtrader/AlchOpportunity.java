package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Immutable market snapshot for one High Alchemy candidate. */
@Getter
@RequiredArgsConstructor
public final class AlchOpportunity
{
    private final int itemId;
    private final String itemName;
    private final int instantBuyPrice;
    private final int highAlchValue;
    private final int natureRunePrice;
    private final int fireRuneCost;
    private final int profitPerCast;
    private final long expectedProfitPerHour;
    private final int volume;
    private final int tradeLimitPer4Hours;
}
