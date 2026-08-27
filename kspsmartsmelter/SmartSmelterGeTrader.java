package net.runelite.client.plugins.microbot.kspsmartsmelter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Grand Exchange interaction for Smart Smelter.
 *
 * <p>This deliberately follows the proven Jewellery Crafter pattern:
 * initial offers are placed through {@link Rs2GrandExchange#processOffer(GrandExchangeRequest)}
 * with an explicit BUY slot and exact price/quantity, then verified against the client's
 * live {@link GrandExchangeOffer} array. We do not guess GE setup child widgets.</p>
 */
@Slf4j
final class SmartSmelterGeTrader
{
    private static final int UI_TIMEOUT_MS = 5_000;
    private static final int OFFER_CONFIRM_TIMEOUT_MS = 3_500;

    private SmartSmelterGeTrader()
    {
    }

    static boolean placeBuy(int itemId, String itemName, int quantity, int percent)
    {
        if (itemId <= 0 || itemName == null || itemName.isBlank() || quantity <= 0)
        {
            return false;
        }

        if (!ensureOverview())
        {
            return false;
        }

        GrandExchangeSlots existing = findOfferSlot(itemId, GrandExchangeAction.BUY);
        if (existing != null)
        {
            OfferSnapshot snapshot = getOfferSnapshot(existing);
            if (snapshot != null && snapshot.state == GrandExchangeOfferState.BOUGHT)
            {
                return collectCompletedToBank();
            }
            Microbot.status = "GE buy already active: " + itemName;
            return true;
        }

        List<GrandExchangeSlots> freeSlots = getAvailableGeSlots();
        if (freeSlots.isEmpty())
        {
            Microbot.status = "Waiting for free GE slot";
            return false;
        }

        int price = offerPrice(itemId, GrandExchangeAction.BUY, percent);
        if (price <= 0)
        {
            Microbot.status = "No reliable GE buy price: " + itemName;
            return false;
        }

        GrandExchangeSlots slot = freeSlots.get(0);
        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .slot(slot)
                .action(GrandExchangeAction.BUY)
                .itemName(itemName)
                .exact(true)
                .quantity(quantity)
                .price(price)
                .closeAfterCompletion(false)
                .build();

        Microbot.status = "Buying " + quantity + " x " + itemName
                + " in GE slot " + (slot.ordinal() + 1);

        if (!Rs2GrandExchange.processOffer(request))
        {
            Microbot.status = Rs2GrandExchange.isOpen()
                    ? "GE buy placement failed: " + itemName
                    : "GE closed during buy placement: " + itemName;
            recoverToOverview();
            return false;
        }

        boolean registered = sleepUntil(() -> offerMatches(slot, itemId, GrandExchangeAction.BUY)
                        || findOfferSlot(itemId, GrandExchangeAction.BUY) != null
                        || !Rs2GrandExchange.isOpen(),
                OFFER_CONFIRM_TIMEOUT_MS);

        if (!registered || !Rs2GrandExchange.isOpen())
        {
            Microbot.status = Rs2GrandExchange.isOpen()
                    ? "Waiting for GE slot confirmation: " + itemName
                    : "GE closed after placing: " + itemName;
            return false;
        }

        return findOfferSlot(itemId, GrandExchangeAction.BUY) != null;
    }

    static boolean placeSell(int itemId, String itemName, int quantity, int percent)
    {
        if (itemId <= 0 || itemName == null || itemName.isBlank() || quantity <= 0)
        {
            return false;
        }

        if (!ensureOverview())
        {
            return false;
        }

        GrandExchangeSlots existing = findOfferSlot(itemId, GrandExchangeAction.SELL);
        if (existing != null)
        {
            OfferSnapshot snapshot = getOfferSnapshot(existing);
            if (snapshot != null && snapshot.state == GrandExchangeOfferState.SOLD)
            {
                return collectCompletedToBank();
            }
            Microbot.status = "GE sell already active: " + itemName;
            return true;
        }

        if (getAvailableGeSlots().isEmpty())
        {
            Microbot.status = "Waiting for free GE slot";
            return false;
        }

        int price = offerPrice(itemId, GrandExchangeAction.SELL, percent);
        if (price <= 0)
        {
            Microbot.status = "No reliable GE sell price: " + itemName;
            return false;
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
                .action(GrandExchangeAction.SELL)
                .itemName(itemName)
                .exact(true)
                .quantity(quantity)
                .price(price)
                .closeAfterCompletion(false)
                .build();

        Microbot.status = "Selling " + quantity + " x " + itemName;
        if (!Rs2GrandExchange.processOffer(request))
        {
            Microbot.status = Rs2GrandExchange.isOpen()
                    ? "GE sell placement failed: " + itemName
                    : "GE closed during sell placement: " + itemName;
            recoverToOverview();
            return false;
        }

        boolean registered = sleepUntil(() -> findOfferSlot(itemId, GrandExchangeAction.SELL) != null
                        || !Rs2GrandExchange.isOpen(),
                OFFER_CONFIRM_TIMEOUT_MS);

        if (!registered || !Rs2GrandExchange.isOpen())
        {
            Microbot.status = Rs2GrandExchange.isOpen()
                    ? "Waiting for GE sell confirmation: " + itemName
                    : "GE closed after sell placement: " + itemName;
            return false;
        }

        return true;
    }

    static boolean ensureOverview()
    {
        if (Rs2Bank.isOpen())
        {
            Rs2Bank.closeBank();
            if (!sleepUntil(() -> !Rs2Bank.isOpen(), UI_TIMEOUT_MS))
            {
                return false;
            }
            sleep(250, 450);
        }

        if (!openVerifiedGe())
        {
            return false;
        }

        if (!geSubScreenOpen())
        {
            return true;
        }

        Microbot.status = "Returning to GE overview";
        Rs2GrandExchange.backToOverview();
        if (sleepUntil(() -> Rs2GrandExchange.isOpen() && !geSubScreenOpen(), 4_000))
        {
            return true;
        }

        Microbot.status = Rs2GrandExchange.isOpen()
                ? "Waiting for GE overview"
                : "GE closed - recovering";
        return false;
    }

    static void recoverToOverview()
    {
        if (!Rs2GrandExchange.isOpen())
        {
            return;
        }

        if (geSubScreenOpen())
        {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> Rs2GrandExchange.isOpen() && !geSubScreenOpen(), 4_000);
            sleep(250, 450);
        }
    }

    static boolean collectCompletedToBank()
    {
        if (!hasCompletedOffers())
        {
            return true;
        }

        if (!ensureOverview())
        {
            return false;
        }

        Microbot.status = "Collecting completed GE offers";
        if (!Rs2GrandExchange.collectAllToBank())
        {
            Microbot.status = Rs2GrandExchange.isOpen()
                    ? "Collect all failed - retrying"
                    : "GE closed during Collect all";
            return false;
        }

        if (sleepUntil(() -> !hasCompletedOffers(), UI_TIMEOUT_MS))
        {
            return true;
        }

        Microbot.status = "Waiting for GE Collect all";
        return false;
    }

    static boolean hasOpenOffers()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null)
            {
                return false;
            }
            for (GrandExchangeOffer offer : offers)
            {
                if (offer == null)
                {
                    continue;
                }
                GrandExchangeOfferState state = offer.getState();
                if (state == GrandExchangeOfferState.BUYING
                        || state == GrandExchangeOfferState.SELLING)
                {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private static boolean openVerifiedGe()
    {
        if (Rs2GrandExchange.isOpen())
        {
            return true;
        }

        Microbot.status = "Opening Grand Exchange";
        if (!Rs2GrandExchange.openExchange())
        {
            Microbot.status = "GE widget closed - reopening";
            return false;
        }

        if (sleepUntil(Rs2GrandExchange::isOpen, UI_TIMEOUT_MS))
        {
            return true;
        }

        Microbot.status = "Waiting for Grand Exchange widget";
        return false;
    }

    private static boolean geSetupOpen()
    {
        return Rs2WidgetVisible.setup();
    }

    private static boolean geSubScreenOpen()
    {
        return Rs2GrandExchange.isOfferScreenOpen() || geSetupOpen();
    }

    private static List<GrandExchangeSlots> getAvailableGeSlots()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            List<GrandExchangeSlots> free = new ArrayList<>();
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int max = Math.min(
                    Rs2WorldUtil.isMemberAccount() ? 8 : 3,
                    GrandExchangeSlots.values().length);

            for (int i = 0; i < max; i++)
            {
                GrandExchangeOffer offer = offers != null && i < offers.length ? offers[i] : null;
                if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
                {
                    free.add(GrandExchangeSlots.values()[i]);
                }
            }
            return free;
        }).orElse(new ArrayList<>());
    }

    private static GrandExchangeSlots findOfferSlot(int itemId, GrandExchangeAction action)
    {
        if (itemId <= 0)
        {
            return null;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null)
            {
                return null;
            }

            int max = Math.min(offers.length, GrandExchangeSlots.values().length);
            for (int i = 0; i < max; i++)
            {
                GrandExchangeOffer offer = offers[i];
                if (offer == null || offer.getItemId() != itemId)
                {
                    continue;
                }

                GrandExchangeOfferState state = offer.getState();
                if (action == GrandExchangeAction.BUY
                        && (state == GrandExchangeOfferState.BUYING
                        || state == GrandExchangeOfferState.BOUGHT))
                {
                    return GrandExchangeSlots.values()[i];
                }
                if (action == GrandExchangeAction.SELL
                        && (state == GrandExchangeOfferState.SELLING
                        || state == GrandExchangeOfferState.SOLD))
                {
                    return GrandExchangeSlots.values()[i];
                }
            }
            return null;
        }).orElse(null);
    }

    private static OfferSnapshot getOfferSnapshot(GrandExchangeSlots slot)
    {
        if (slot == null)
        {
            return null;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int index = slot.ordinal();
            if (offers == null || index >= offers.length || offers[index] == null)
            {
                return new OfferSnapshot(0, GrandExchangeOfferState.EMPTY, 0);
            }
            GrandExchangeOffer offer = offers[index];
            return new OfferSnapshot(offer.getItemId(), offer.getState(), offer.getPrice());
        }).orElse(null);
    }

    private static boolean offerMatches(
            GrandExchangeSlots slot,
            int itemId,
            GrandExchangeAction action)
    {
        OfferSnapshot snapshot = getOfferSnapshot(slot);
        if (snapshot == null || snapshot.itemId != itemId)
        {
            return false;
        }
        return action == GrandExchangeAction.BUY
                ? snapshot.state == GrandExchangeOfferState.BUYING
                    || snapshot.state == GrandExchangeOfferState.BOUGHT
                : snapshot.state == GrandExchangeOfferState.SELLING
                    || snapshot.state == GrandExchangeOfferState.SOLD;
    }

    private static boolean hasCompletedOffers()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null)
            {
                return false;
            }
            for (GrandExchangeOffer offer : offers)
            {
                if (offer == null)
                {
                    continue;
                }
                GrandExchangeOfferState state = offer.getState();
                if (state == GrandExchangeOfferState.BOUGHT
                        || state == GrandExchangeOfferState.SOLD)
                {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private static int offerPrice(int itemId, GrandExchangeAction action, int percent)
    {
        try
        {
            WikiPrice market = Rs2GrandExchange.getRealTimePrices(itemId);
            if (market == null)
            {
                return 0;
            }
            int baseline = action == GrandExchangeAction.BUY
                    ? market.buyPrice
                    : market.sellPrice;
            if (baseline <= 0)
            {
                return 0;
            }
            long adjusted = Math.round(baseline * ((100.0 + percent) / 100.0));
            return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, adjusted));
        }
        catch (RuntimeException ex)
        {
            log.debug("Unable to price GE item {}: {}", itemId, ex.getMessage());
            return 0;
        }
    }

    private static final class OfferSnapshot
    {
        private final int itemId;
        private final GrandExchangeOfferState state;
        @SuppressWarnings("unused")
        private final int price;

        private OfferSnapshot(int itemId, GrandExchangeOfferState state, int price)
        {
            this.itemId = itemId;
            this.state = state;
            this.price = price;
        }
    }

    /**
     * Keeps the generated InterfaceID access in one place so the overview logic mirrors
     * Jewellery Crafter without tying the rest of the trader to widget child arithmetic.
     */
    private static final class Rs2WidgetVisible
    {
        private Rs2WidgetVisible()
        {
        }

        private static boolean setup()
        {
            return net.runelite.client.plugins.microbot.util.widget.Rs2Widget
                    .isWidgetVisible(InterfaceID.GeOffers.SETUP);
        }
    }
}
