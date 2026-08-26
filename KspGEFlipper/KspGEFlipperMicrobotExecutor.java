package net.runelite.client.plugins.microbot.kspgeflipper;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.Collections;
import java.util.Map;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

/**
 * Thin execution adapter. It deliberately contains no market/recommendation logic.
 * The backend decides what to do; this class only translates an accepted action into Microbot GE calls.
 */
final class KspGEFlipperMicrobotExecutor implements KspGEFlipperSuggestionExecutor {
    private static final int COINS = 995;

    private final KspGEFlipperConfig config;
    private final KspGEFlipperOfferTracker tracker;
    private String lastCompletedSuggestionId;
    private Pending pending;
    private String status = "Ready";

    KspGEFlipperMicrobotExecutor(KspGEFlipperConfig config, KspGEFlipperOfferTracker tracker) {
        this.config = config;
        this.tracker = tracker;
    }

    @Override
    public synchronized boolean execute(KspGEFlipperBackendDtos.Suggestion suggestion) {
        if (suggestion == null || suggestion.id == null || suggestion.type == null) return false;
        if (suggestion.id.equals(lastCompletedSuggestionId)) return true;
        if (pending != null) return pending.suggestion.id.equals(suggestion.id);

        String type = suggestion.type.toUpperCase(java.util.Locale.ROOT);
        switch (type) {
            case "WAIT":
                status = "WAIT: " + safe(suggestion.explanation);
                lastCompletedSuggestionId = suggestion.id;
                return true;
            case "BUY":
                return executeBuy(suggestion);
            case "SELL":
                return executeSell(suggestion);
            case "MODIFY_BUY":
            case "MODIFY_SELL":
                return beginCancel(suggestion, true);
            case "ABORT":
                return beginCancel(suggestion, false);
            default:
                status = "Unsupported action: " + type;
                return false;
        }
    }

    @Override
    public synchronized void tick() {
        if (pending == null) return;
        GrandExchangeSlots slot = slot(pending.suggestion.slot);
        if (slot == null) {
            status = "Invalid pending GE slot";
            pending = null;
            return;
        }

        GrandExchangeOfferDetails active = Rs2GrandExchange.getOfferDetails(slot);
        if (active != null && active.isInProgress()) {
            status = "Cancelling slot " + (pending.suggestion.slot + 1);
            return;
        }

        Map<GrandExchangeSlots, GrandExchangeOfferDetails> completed = Rs2GrandExchange.getCompletedOffers();
        GrandExchangeOfferDetails terminal = completed.get(slot);
        if (terminal != null && terminal.getItemId() > 0) {
            Rs2GrandExchange.collectOffer(slot, false);
            sleep(100);
        }

        KspGEFlipperBackendDtos.Suggestion suggestion = pending.suggestion;
        boolean relist = pending.relist;
        pending = null;
        if (!relist) {
            lastCompletedSuggestionId = suggestion.id;
            status = "Aborted slot " + (suggestion.slot + 1);
            return;
        }

        boolean ok = "MODIFY_BUY".equalsIgnoreCase(suggestion.type)
                ? executeBuy(suggestion)
                : executeSell(suggestion);
        if (!ok) status = "Relist pending prerequisites: " + safe(suggestion.name);
    }

    @Override public synchronized String status() { return status; }

    private boolean beginCancel(KspGEFlipperBackendDtos.Suggestion suggestion, boolean relist) {
        GrandExchangeSlots slot = slot(suggestion.slot);
        if (slot == null) {
            status = "Invalid GE slot " + suggestion.slot;
            return false;
        }
        GrandExchangeOfferDetails details = Rs2GrandExchange.getOfferDetails(slot);
        if (details == null || !details.isInProgress()) {
            if (relist) return "MODIFY_BUY".equalsIgnoreCase(suggestion.type) ? executeBuy(suggestion) : executeSell(suggestion);
            lastCompletedSuggestionId = suggestion.id;
            status = "Offer already inactive";
            return true;
        }
        KspGEFlipperOfferTracker.Attribution attr = tracker.attribution(details.getItemId(), details.isSelling() ? "SELL" : "BUY");
        if (attr == null) {
            status = "Protected manual offer in slot " + (suggestion.slot + 1);
            return false;
        }
        pending = new Pending(suggestion, relist);
        status = (relist ? "Modifying" : "Aborting") + " slot " + (suggestion.slot + 1);
        Rs2GrandExchange.cancelSpecificOffers(Collections.singletonList(slot), false);
        return true;
    }

    private boolean executeBuy(KspGEFlipperBackendDtos.Suggestion suggestion) {
        if (suggestion.itemId <= 0 || suggestion.quantity <= 0 || suggestion.price <= 0 || suggestion.name == null) return false;
        long required = multiplySafe(suggestion.price, suggestion.quantity);
        if (!ensureCoins(required)) return false;
        if (!ensureGe()) return false;
        int price = safeInt(suggestion.price);
        status = "Buying " + suggestion.name + " @ " + price;
        boolean ok = Rs2GrandExchange.buyItem(suggestion.name, price, suggestion.quantity);
        if (ok) {
            tracker.mark(suggestion, "BUY");
            lastCompletedSuggestionId = suggestion.id;
        }
        return ok;
    }

    private boolean executeSell(KspGEFlipperBackendDtos.Suggestion suggestion) {
        if (suggestion.itemId <= 0 || suggestion.quantity <= 0 || suggestion.price <= 0 || suggestion.name == null) return false;
        if (!ensureItem(suggestion.itemId, suggestion.quantity)) return false;
        if (!ensureGe()) return false;
        int available = Rs2Inventory.itemQuantity(suggestion.itemId);
        int quantity = Math.min(suggestion.quantity, available);
        if (quantity <= 0) return false;
        int price = safeInt(suggestion.price);
        status = "Selling " + suggestion.name + " @ " + price;
        boolean ok = Rs2GrandExchange.sellItem(suggestion.name, quantity, price);
        if (ok) {
            tracker.mark(suggestion, "SELL");
            lastCompletedSuggestionId = suggestion.id;
        }
        return ok;
    }

    private boolean ensureCoins(long required) {
        if (Rs2Inventory.itemQuantity(COINS) >= required) return true;
        long deficit = required - Rs2Inventory.itemQuantity(COINS);
        if (deficit <= 0) return true;
        status = "Loading coins from bank";
        if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
        if (!Rs2Bank.openBank()) return false;
        boolean ok = Rs2Bank.withdrawX(COINS, safeInt(deficit));
        sleep(150);
        Rs2Bank.closeBank();
        return ok && Rs2Inventory.itemQuantity(COINS) >= required;
    }

    private boolean ensureItem(int itemId, int quantity) {
        if (Rs2Inventory.itemQuantity(itemId) >= quantity) return true;
        int deficit = quantity - Rs2Inventory.itemQuantity(itemId);
        status = "Loading sale item from bank";
        if (Rs2GrandExchange.isOpen()) Rs2GrandExchange.closeExchange();
        if (!Rs2Bank.openBank()) return false;
        boolean ok = Rs2Bank.withdrawX(itemId, deficit);
        sleep(150);
        Rs2Bank.closeBank();
        return ok && Rs2Inventory.itemQuantity(itemId) > 0;
    }

    private boolean ensureGe() {
        if (Rs2GrandExchange.isOpen()) return true;
        status = "Opening GE";
        if (Rs2GrandExchange.openExchange()) return true;
        if (!config.walkToGe() || !Rs2GrandExchange.walkToGrandExchange()) return false;
        return Rs2GrandExchange.openExchange();
    }

    private static GrandExchangeSlots slot(int index) {
        GrandExchangeSlots[] values = GrandExchangeSlots.values();
        return index >= 0 && index < values.length ? values[index] : null;
    }

    private static int safeInt(long value) { return (int) Math.max(1, Math.min(Integer.MAX_VALUE, value)); }
    private static long multiplySafe(long a, int b) {
        if (a <= 0 || b <= 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }
    private static String safe(String value) { return value == null ? "" : value; }

    private static final class Pending {
        final KspGEFlipperBackendDtos.Suggestion suggestion;
        final boolean relist;
        Pending(KspGEFlipperBackendDtos.Suggestion suggestion, boolean relist) { this.suggestion = suggestion; this.relist = relist; }
    }
}
