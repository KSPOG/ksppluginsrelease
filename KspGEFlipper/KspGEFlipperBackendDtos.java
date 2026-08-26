package net.runelite.client.plugins.microbot.kspgeflipper;

import java.util.*;

final class KspGEFlipperBackendDtos {
    private KspGEFlipperBackendDtos() {}

    static final class AccountState {
        String accountKey;
        boolean worldMember;
        boolean accountMember;
        boolean f2pOnly;
        int totalGeSlots;
        int maxPluginSlots;
        long gp;
        Map<Integer, Long> inventory = new HashMap<>();
        Map<Integer, Long> bank = new HashMap<>();
        Map<Integer, Long> uncollected = new HashMap<>();
        Map<Integer, Long> otherStorage = new HashMap<>();
        List<Offer> offers = new ArrayList<>();
        Set<Integer> blockedItems = new HashSet<>();
        Set<String> blockedItemNames = new HashSet<>();
        Set<String> allowedItemNames = new HashSet<>();
        Strategy strategy = new Strategy();
    }

    static final class Strategy {
        int timeframeMinutes;
        String riskLevel;
        boolean sellOnly;
        boolean allowBuyAndHold;
        boolean dumpEnabled;
        int reservedSlots;
        int dumpSlots;
        long minExpectedProfit;
        long minDumpExpectedProfit;
        double maxItemExposurePct;
        double modifyThresholdPct;
        double abortThresholdPct;
    }

    static final class Offer {
        int slot;
        int itemId;
        String side;
        long offerPrice;
        int totalQuantity;
        int filledQuantity;
        long amountSpent;
        boolean active;
        String firstSeen;
        String lastChanged;
        boolean recommendedPriceUsed;
        String suggestionId;
        String candidateType;
    }

    static final class Suggestion {
        String id;
        String type;
        String candidateType;
        int slot;
        int itemId;
        String name;
        long price;
        long exitPrice;
        int quantity;
        long expectedProfit;
        long expectedDurationSeconds;
        double expectedGpPerHour;
        double confidence;
        boolean hold;
        String explanation;
        String generatedAt;
        long marketAgeSeconds;
    }

    static final class TradeExecution {
        String id;
        String accountKey;
        int itemId;
        String side;
        long price;
        int quantity;
        long amountSpent;
        String timestamp;
        String suggestionId;
        boolean recommendationPriceUsed;
        boolean recommendationOriginatedTrade;
        String firstFillAt;
        String fullFillAt;
    }

    static final class DumpEnvelope { List<DumpSignal> active = new ArrayList<>(); }
    static final class DumpSignal {
        String id; int itemId; String name; String detectedAt; double severity; double recoveryProbability;
        long currentLow; long predictedRecoveryPrice; long estimatedRecoverySeconds; long expectedProfit; double volumeAcceleration;
    }
}
