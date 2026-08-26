package net.runelite.client.plugins.microbot.kspsmartsmelter;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RankingMode;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RouteQuote;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.SmeltRoute;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SmartRouteSelector {
    private static final double GE_TAX_RATE = 0.02;
    private static final int GE_TAX_CAP_PER_ITEM = 5_000_000;

    private SmartRouteSelector() {
    }

    public static List<RouteQuote> scan(KspSmartSmelterConfig config) {
        int smithingLevel = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getRealSkillLevel(Skill.SMITHING))
                .orElse(1);

        boolean member = Rs2WorldUtil.isMemberAccount();
        List<RouteQuote> quotes = new ArrayList<>();

        for (SmeltRoute route : SmeltRoute.values()) {
            if (smithingLevel < route.getSmithingLevel()) {
                continue;
            }
            if (route.isMembersOnly() && !member) {
                continue;
            }
            if (route.isRiskyIron() && !config.allowIron()) {
                continue;
            }
            if (route.isCannonballs()) {
                if (!config.allowCannonballs() || !hasMould()) {
                    continue;
                }
            }

            RouteQuote quote = quote(route);
            if (quote == null) {
                continue;
            }
            if (quote.getProfitPerCycle() < config.minProfitPerCycle()) {
                continue;
            }
            if (quote.getRoiPercent() < config.minRoiPercent()) {
                continue;
            }
            quotes.add(quote);
        }

        Comparator<RouteQuote> comparator;
        RankingMode ranking = config.rankingMode();
        if (ranking == RankingMode.ROI) {
            comparator = Comparator.comparingDouble(RouteQuote::getRoiPercent);
        } else if (ranking == RankingMode.PROFIT_PER_CYCLE) {
            comparator = Comparator.comparingDouble(RouteQuote::getProfitPerCycle);
        } else {
            comparator = Comparator.comparingDouble(RouteQuote::getTripProfit);
        }

        quotes.sort(comparator.reversed());
        return quotes;
    }

    private static RouteQuote quote(SmeltRoute route) {
        double inputCost = 0;
        int liquidity = Integer.MAX_VALUE;

        int[] ids = route.getInputIds();
        int[] quantities = route.getInputQuantities();

        for (int i = 0; i < ids.length; i++) {
            WikiPrice price = Rs2GrandExchange.getRealTimePrices(ids[i]);
            if (price == null || price.buyPrice <= 0) {
                return null;
            }
            inputCost += (double) price.buyPrice * quantities[i];
            if (price.volume > 0) {
                liquidity = Math.min(liquidity, price.volume);
            }
        }

        WikiPrice output = Rs2GrandExchange.getRealTimePrices(route.getOutputId());
        if (output == null || output.sellPrice <= 0 || inputCost <= 0) {
            return null;
        }

        if (output.volume > 0) {
            liquidity = Math.min(liquidity, output.volume);
        }
        if (liquidity == Integer.MAX_VALUE) {
            liquidity = 0;
        }

        int taxPerItem = Math.min(
                GE_TAX_CAP_PER_ITEM,
                (int) Math.floor(output.sellPrice * GE_TAX_RATE)
        );
        double netSellPerItem = Math.max(0, output.sellPrice - taxPerItem);
        double expectedOutput = route.getOutputQuantity() * route.getExpectedYield();
        double netRevenue = netSellPerItem * expectedOutput;
        double profit = netRevenue - inputCost;
        double roi = (profit / inputCost) * 100.0;
        int tripCycles = route.getMaxCyclesPerTrip();
        double tripProfit = profit * tripCycles;

        return new RouteQuote(
                route,
                inputCost,
                netRevenue,
                profit,
                roi,
                tripProfit,
                tripCycles,
                liquidity
        );
    }

    public static boolean hasMould() {
        return Rs2Inventory.hasItem(ItemID.AMMO_MOULD)
                || Rs2Inventory.hasItem(ItemID.DOUBLE_AMMO_MOULD)
                || bankHas(ItemID.AMMO_MOULD)
                || bankHas(ItemID.DOUBLE_AMMO_MOULD);
    }

    private static boolean bankHas(int id) {
        try {
            return Rs2Bank.bankItems().stream().anyMatch(item -> item.getId() == id && item.getQuantity() > 0);
        } catch (Exception ignored) {
            return false;
        }
    }
}
