package com.ksp.geflipper.util;

import com.ksp.geflipper.model.Models.*;

import java.time.Instant;
import java.util.*;

public final class ModelMapper {
    private ModelMapper() {}

    public static AccountState account(Map<String,Object> root) {
        Map<String,Object> account = map(root.getOrDefault("account", root));
        Map<String,Object> preferencesRaw = map(root.getOrDefault("preferences", account.get("strategy")));
        StrategyPreferences strategy = preferences(preferencesRaw);
        String accountKey = string(account, "accountKey", string(account, "account", "default"));
        boolean worldMember = bool(account, "worldMember", bool(account, "membership", false));
        boolean accountMember = bool(account, "accountMember", worldMember);
        boolean f2pOnly = bool(account, "f2pOnly", false);
        return new AccountState(
                accountKey,
                worldMember,
                accountMember,
                f2pOnly,
                integer(account, "totalGeSlots", accountMember ? 8 : 3),
                integer(account, "maxPluginSlots", accountMember ? 8 : 3),
                number(account, "gp", 0),
                intLongMap(account.get("inventory")),
                intLongMap(account.get("bank")),
                intLongMap(account.get("uncollected")),
                intLongMap(account.get("otherStorage")),
                offers(account.get("offers")),
                intSet(account.get("blockedItems")),
                stringSet(account.get("blockedItemNames")),
                stringSet(account.get("allowedItemNames")),
                strategy,
                PortfolioSnapshot.empty());
    }

    public static StrategyPreferences preferences(Map<String,Object> m) {
        StrategyPreferences d = StrategyPreferences.defaults();
        return new StrategyPreferences(
                integer(m, "timeframeMinutes", d.timeframeMinutes()),
                enumeration(RiskLevel.class, string(m, "risk", string(m, "riskLevel", d.riskLevel().name())), d.riskLevel()),
                bool(m, "sellOnly", d.sellOnly()),
                bool(m, "allowBuyAndHold", d.allowBuyAndHold()),
                bool(m, "dumpEnabled", d.dumpEnabled()),
                integer(m, "reservedSlots", d.reservedSlots()),
                integer(m, "dumpSlots", d.dumpSlots()),
                number(m, "minExpectedProfit", d.minExpectedProfit()),
                number(m, "minDumpExpectedProfit", d.minDumpExpectedProfit()),
                decimal(m, "maxItemExposurePct", d.maxItemExposurePct()),
                decimal(m, "modifyThresholdPct", d.modifyThresholdPct()),
                decimal(m, "abortThresholdPct", d.abortThresholdPct()));
    }

    public static TradeExecution execution(Map<String,Object> m) {
        return new TradeExecution(
                uuid(m.get("id"), UUID.randomUUID()),
                string(m, "accountKey", "default"),
                integer(m, "itemId", 0),
                enumeration(Side.class, string(m, "side", "BUY"), Side.BUY),
                number(m, "price", 0),
                integer(m, "quantity", 0),
                number(m, "amountSpent", 0),
                instant(m.get("timestamp"), Instant.now()),
                uuid(m.get("suggestionId"), null),
                bool(m, "recommendationPriceUsed", bool(m, "copilotPriceUsed", false)),
                bool(m, "recommendationOriginatedTrade", bool(m, "wasCopilotSuggestion", false)),
                instant(m.get("firstFillAt"), null),
                instant(m.get("fullFillAt"), null));
    }

    public static GeOfferState offer(Map<String,Object> m) {
        return new GeOfferState(
                integer(m, "slot", integer(m, "box", -1)),
                integer(m, "itemId", 0),
                enumeration(Side.class, string(m, "side", "BUY"), Side.BUY),
                number(m, "offerPrice", number(m, "price", 0)),
                integer(m, "totalQuantity", integer(m, "quantity", 0)),
                integer(m, "filledQuantity", integer(m, "amountTraded", 0)),
                number(m, "amountSpent", 0),
                bool(m, "active", true),
                instant(m.get("firstSeen"), Instant.now()),
                instant(m.get("lastChanged"), Instant.now()),
                bool(m, "recommendedPriceUsed", false),
                uuid(m.get("suggestionId"), null),
                enumeration(CandidateType.class, string(m, "candidateType", "NORMAL_FLIP"), CandidateType.NORMAL_FLIP));
    }

    public static Position position(Map<String,Object> m) {
        return new Position(
                uuid(m.get("id"), UUID.randomUUID()), string(m, "accountKey", "default"), integer(m, "itemId", 0),
                enumeration(PositionType.class, string(m, "type", "COPILOT"), PositionType.COPILOT),
                uuid(m.get("sourceSuggestionId"), null),
                integer(m, "openQuantity", 0), integer(m, "closedQuantity", 0),
                number(m, "totalBuyCost", 0), number(m, "totalSellRevenue", 0), number(m, "taxPaid", 0),
                instant(m.get("openedAt"), Instant.now()), instant(m.get("closedAt"), null),
                enumeration(PositionStatus.class, string(m, "status", "OPEN"), PositionStatus.OPEN));
    }

    public static TradeSuggestion suggestion(Map<String,Object> m) {
        return new TradeSuggestion(
                uuid(m.get("id"), UUID.randomUUID()), enumeration(SuggestionType.class, string(m, "type", "WAIT"), SuggestionType.WAIT),
                enumeration(CandidateType.class, string(m, "candidateType", "NORMAL_FLIP"), CandidateType.NORMAL_FLIP),
                integer(m, "slot", -1), integer(m, "itemId", -1), string(m, "name", "-"), number(m, "price", 0),
                number(m, "exitPrice", 0), integer(m, "quantity", 0), number(m, "expectedProfit", 0),
                number(m, "expectedDurationSeconds", 0), decimal(m, "expectedGpPerHour", 0), decimal(m, "confidence", 0),
                bool(m, "hold", false), string(m, "explanation", ""), instant(m.get("generatedAt"), Instant.now()),
                number(m, "marketAgeSeconds", 0));
    }

    public static DumpSignal dump(Map<String,Object> m) {
        return new DumpSignal(uuid(m.get("id"), UUID.randomUUID()), integer(m,"itemId",0), string(m,"name",""),
                instant(m.get("detectedAt"), Instant.now()), decimal(m,"severity",0), decimal(m,"recoveryProbability",0),
                number(m,"currentLow",0), number(m,"predictedRecoveryPrice",0), number(m,"estimatedRecoverySeconds",0),
                number(m,"expectedProfit",0), decimal(m,"volumeAcceleration",0));
    }

    public static RecommendationOutcome outcome(Map<String,Object> m) {
        return new RecommendationOutcome(
                uuid(m.get("recommendationId"), UUID.randomUUID()), string(m, "accountKey", "default"), integer(m, "itemId", 0),
                number(m, "predictedProfit", 0), number(m, "actualProfit", 0), number(m, "predictedDurationSeconds", 0),
                number(m, "actualDurationSeconds", 0), number(m, "recommendedPrice", 0), decimal(m, "actualAveragePrice", 0),
                bool(m, "modified", false), bool(m, "aborted", false), instant(m.get("recordedAt"), Instant.now()));
    }

    public static MarketPoint marketPoint(Map<String,Object> m) {
        return new MarketPoint(integer(m,"itemId",0), instant(m.get("timestamp"), Instant.now()), number(m,"highPrice",0),
                number(m,"lowPrice",0), number(m,"highVolume",0), number(m,"lowVolume",0), string(m,"resolution","LATEST"));
    }

    public static Map<String,Object> map(Object value) {
        if (value instanceof Map<?,?> raw) {
            Map<String,Object> out = new LinkedHashMap<>();
            raw.forEach((k,v) -> out.put(String.valueOf(k), v)); return out;
        }
        return new LinkedHashMap<>();
    }

    public static List<Map<String,Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> values)) return List.of();
        List<Map<String,Object>> out = new ArrayList<>(); for (Object item : values) out.add(map(item)); return out;
    }

    public static Map<Integer,Long> intLongMap(Object value) {
        Map<Integer,Long> out = new HashMap<>();
        if (value instanceof Map<?,?> raw) for (Map.Entry<?,?> e : raw.entrySet()) try { out.put(Integer.parseInt(String.valueOf(e.getKey())), longValue(e.getValue(),0)); } catch (RuntimeException ignored) {}
        return out;
    }

    public static Set<Integer> intSet(Object value) {
        Set<Integer> out = new HashSet<>();
        if (value instanceof Collection<?> c) for (Object v : c) try { out.add((int) longValue(v,0)); } catch (RuntimeException ignored) {}
        return out;
    }

    public static Set<String> stringSet(Object value) {
        Set<String> out = new HashSet<>();
        if (value instanceof Collection<?> c) for (Object v : c) if (v != null && !String.valueOf(v).isBlank()) out.add(String.valueOf(v).trim().toLowerCase(Locale.ROOT));
        return out;
    }

    private static List<GeOfferState> offers(Object value) {
        List<GeOfferState> out = new ArrayList<>(); for (Map<String,Object> m : mapList(value)) out.add(offer(m)); return out;
    }

    public static String string(Map<String,Object> m, String key, String fallback) { Object v=m.get(key); return v==null?fallback:String.valueOf(v); }
    public static long number(Map<String,Object> m, String key, long fallback) { return longValue(m.get(key), fallback); }
    public static int integer(Map<String,Object> m, String key, int fallback) { return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, longValue(m.get(key), fallback))); }
    public static double decimal(Map<String,Object> m, String key, double fallback) { Object v=m.get(key); if(v instanceof Number n)return n.doubleValue(); try{return v==null?fallback:Double.parseDouble(String.valueOf(v));}catch(RuntimeException e){return fallback;} }
    public static boolean bool(Map<String,Object> m, String key, boolean fallback) { Object v=m.get(key); return v instanceof Boolean b?b:v==null?fallback:Boolean.parseBoolean(String.valueOf(v)); }
    public static long longValue(Object v,long fallback){ if(v instanceof Number n)return n.longValue(); try{return v==null?fallback:Long.parseLong(String.valueOf(v));}catch(RuntimeException e){return fallback;} }
    public static Instant instant(Object v, Instant fallback){ try{return v==null?fallback:Instant.parse(String.valueOf(v));}catch(RuntimeException e){return fallback;} }
    public static UUID uuid(Object v, UUID fallback){ try{return v==null?fallback:UUID.fromString(String.valueOf(v));}catch(RuntimeException e){return fallback;} }
    public static <E extends Enum<E>> E enumeration(Class<E> type,String raw,E fallback){ try{return Enum.valueOf(type,raw.toUpperCase(Locale.ROOT));}catch(RuntimeException e){return fallback;} }
}
