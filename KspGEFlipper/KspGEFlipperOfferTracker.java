package net.runelite.client.plugins.microbot.kspgeflipper;

import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;

import java.time.Instant;
import java.util.*;

final class KspGEFlipperOfferTracker {
    static final class Times {
        final Instant firstSeen;
        final Instant lastChanged;
        final Instant firstFill;
        Times(Instant firstSeen, Instant lastChanged, Instant firstFill) {
            this.firstSeen = firstSeen;
            this.lastChanged = lastChanged;
            this.firstFill = firstFill;
        }
    }
    static final class Attribution {
        final String suggestionId;
        final int itemId;
        final String side;
        final long price;
        final int quantity;
        final String candidateType;
        final Instant issuedAt;
        Attribution(String suggestionId, int itemId, String side, long price, int quantity, String candidateType) {
            this.suggestionId = suggestionId;
            this.itemId = itemId;
            this.side = side;
            this.price = price;
            this.quantity = quantity;
            this.candidateType = candidateType;
            this.issuedAt = Instant.now();
        }
    }
    private static final class Observed {
        int fingerprint;
        Instant firstSeen;
        Instant lastChanged;
        Instant firstFill;
    }

    private final Map<GrandExchangeSlots, Observed> observed = new EnumMap<>(GrandExchangeSlots.class);
    private final Map<String, Attribution> attribution = new HashMap<>();

    synchronized Times observe(GrandExchangeSlots slot, GrandExchangeOfferDetails details) {
        Instant now = Instant.now();
        int fingerprint = Objects.hash(details.getItemId(), details.getPrice(), details.getTotalQuantity(), details.isSelling(), details.getState());
        Observed state = observed.get(slot);
        if (state == null || state.fingerprint != fingerprint) {
            state = new Observed();
            state.fingerprint = fingerprint;
            state.firstSeen = now;
            state.lastChanged = now;
            observed.put(slot, state);
        } else if (details.getQuantitySold() > 0 && state.firstFill == null) {
            state.firstFill = now;
            state.lastChanged = now;
        }
        return new Times(state.firstSeen, state.lastChanged, state.firstFill);
    }

    synchronized void mark(KspGEFlipperBackendDtos.Suggestion suggestion, String side) {
        if (suggestion == null || suggestion.id == null || suggestion.itemId <= 0) return;
        attribution.put(key(suggestion.itemId, side), new Attribution(suggestion.id, suggestion.itemId, side, suggestion.price, suggestion.quantity, suggestion.candidateType));
    }

    synchronized Attribution attribution(int itemId, String side) {
        Attribution value = attribution.get(key(itemId, side));
        if (value != null && java.time.Duration.between(value.issuedAt, Instant.now()).toHours() >= 8) {
            attribution.remove(key(itemId, side));
            return null;
        }
        return value;
    }

    synchronized Times times(GrandExchangeSlots slot) {
        Observed state = observed.get(slot);
        return state == null ? new Times(Instant.now(), Instant.now(), null) : new Times(state.firstSeen, state.lastChanged, state.firstFill);
    }

    private static String key(int itemId, String side) { return itemId + ":" + side; }
}
