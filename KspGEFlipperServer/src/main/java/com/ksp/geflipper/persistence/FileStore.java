package com.ksp.geflipper.persistence;

import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.util.Json;
import com.ksp.geflipper.util.ModelMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class FileStore implements Store {
    private final Path dir;
    private final Map<UUID,TradeSuggestion> recommendations = new ConcurrentHashMap<>();
    private final Map<UUID,String> recommendationStatuses = new ConcurrentHashMap<>();
    private final Map<String,List<TradeExecution>> executions = new ConcurrentHashMap<>();
    private final Map<String,Map<UUID,Position>> positions = new ConcurrentHashMap<>();
    private final Map<Integer,Deque<MarketPoint>> market = new ConcurrentHashMap<>();
    private final Deque<DumpSignal> dumps = new ArrayDeque<>();
    private final Deque<RecommendationOutcome> outcomes = new ArrayDeque<>();
    private final Map<String,Double> metrics = new ConcurrentHashMap<>();

    public FileStore(Path dir) {
        this.dir = dir.toAbsolutePath();
        try { Files.createDirectories(this.dir); load(); }
        catch (IOException e) { throw new IllegalStateException("Cannot initialize data directory " + this.dir, e); }
    }

    @Override public void saveAccount(AccountState account) { append("accounts.jsonl", account); append("account_preferences.jsonl", Map.of("accountKey", account.accountKey(), "strategy", account.strategy())); }
    @Override public void saveItem(ItemMeta item) { append("items.jsonl", item); }
    @Override public void saveRawMarket(String endpoint,String payload,java.time.Instant observedAt) { append("raw_market.jsonl", Map.of("endpoint",endpoint,"observedAt",observedAt,"payload",payload)); }
    @Override public void saveOffers(String accountKey, List<GeOfferState> offers) { append("offers.jsonl", Map.of("accountKey", accountKey, "offers", offers)); }
    @Override public synchronized void saveMarketPoint(MarketPoint point) {
        Deque<MarketPoint> q = market.computeIfAbsent(point.itemId(), ignored -> new ArrayDeque<>()); q.addLast(point); while (q.size() > 4096) q.removeFirst(); append("market.jsonl", point);
    }
    @Override public synchronized List<MarketPoint> recentMarketPoints(int itemId, int limit) {
        Deque<MarketPoint> q = market.get(itemId); if (q == null) return List.of();
        List<MarketPoint> out = new ArrayList<>(q); return out.subList(Math.max(0, out.size()-Math.max(1,limit)), out.size());
    }
    @Override public void saveForecast(ItemForecast forecast) { append("forecasts.jsonl", forecast); }
    @Override public void saveRecommendation(String accountKey, TradeSuggestion suggestion, Map<String,Double> features, StrategyPreferences preferences) {
        recommendations.put(suggestion.id(), suggestion); recommendationStatuses.put(suggestion.id(), "ISSUED"); append("recommendations.jsonl", Map.of("accountKey",accountKey,"suggestion",suggestion,"features",features,"preferences",preferences,"status","ISSUED"));
    }
    @Override public Optional<TradeSuggestion> recommendation(UUID id) { return Optional.ofNullable(recommendations.get(id)); }
    @Override public void markRecommendationStatus(UUID id,String status){ if(id==null)return; recommendationStatuses.put(id,status); append("recommendation_status.jsonl",Map.of("id",id,"status",status,"at",java.time.Instant.now())); }
    @Override public String recommendationStatus(UUID id){ return id==null?"UNKNOWN":recommendationStatuses.getOrDefault(id,"UNKNOWN"); }
    @Override public Map<String,Long> recommendationActionCounts(){ Map<String,Long> out=new HashMap<>(); for(TradeSuggestion s:recommendations.values()) out.merge(s.type().name(),1L,Long::sum); return out; }
    @Override public void saveExecution(TradeExecution execution) { executions.computeIfAbsent(execution.accountKey(), ignored -> Collections.synchronizedList(new ArrayList<>())).add(execution); append("transactions.jsonl", execution); }
    @Override public List<TradeExecution> executions(String accountKey, int limit) { List<TradeExecution> list=executions.getOrDefault(accountKey,List.of()); int from=Math.max(0,list.size()-Math.max(1,limit)); return List.copyOf(list.subList(from,list.size())); }
    @Override public void savePosition(Position position) { positions.computeIfAbsent(position.accountKey(),ignored->new ConcurrentHashMap<>()).put(position.id(),position); append("positions.jsonl",position); }
    @Override public void linkPositionExecution(UUID positionId,UUID executionId) { append("position_transactions.jsonl", Map.of("positionId",positionId,"executionId",executionId)); }
    @Override public List<Position> positions(String accountKey) { return new ArrayList<>(positions.getOrDefault(accountKey,Map.of()).values()); }
    @Override public synchronized void saveDump(DumpSignal signal) { dumps.addFirst(signal); while(dumps.size()>1000)dumps.removeLast(); append("dumps.jsonl",signal); }
    @Override public synchronized List<DumpSignal> dumps(int limit) { return dumps.stream().limit(Math.max(1,limit)).toList(); }
    @Override public synchronized void saveOutcome(RecommendationOutcome outcome) { outcomes.addLast(outcome); while(outcomes.size()>10000)outcomes.removeFirst(); append("outcomes.jsonl",outcome); }
    @Override public synchronized List<RecommendationOutcome> outcomes(int limit) { List<RecommendationOutcome> list=new ArrayList<>(outcomes); return list.subList(Math.max(0,list.size()-Math.max(1,limit)),list.size()); }
    @Override public void saveMetric(String key,double value,long sampleCount){ metrics.put(key,value); append("metrics.jsonl",Map.of("key",key,"value",value,"sampleCount",sampleCount,"at",java.time.Instant.now())); }
    @Override public Map<String,Double> metrics(){ return Map.copyOf(metrics); }

    private synchronized void append(String file,Object value) {
        try { Files.writeString(dir.resolve(file), Json.stringify(value)+System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException e) { throw new IllegalStateException("Persistence write failed: "+file,e); }
    }

    @SuppressWarnings("unchecked") private void load() throws IOException {
        loadLines("market.jsonl", m -> { MarketPoint p=ModelMapper.marketPoint(m); market.computeIfAbsent(p.itemId(),x->new ArrayDeque<>()).addLast(p); });
        loadLines("positions.jsonl", m -> { Position p=ModelMapper.position(m); positions.computeIfAbsent(p.accountKey(),x->new ConcurrentHashMap<>()).put(p.id(),p); });
        loadLines("transactions.jsonl", m -> { TradeExecution e=ModelMapper.execution(m); executions.computeIfAbsent(e.accountKey(),x->Collections.synchronizedList(new ArrayList<>())).add(e); });
        loadLines("outcomes.jsonl", m -> outcomes.addLast(ModelMapper.outcome(m)));
        Path rec = dir.resolve("recommendations.jsonl");
        if(Files.exists(rec)) for(String line:Files.readAllLines(rec,StandardCharsets.UTF_8)) if(!line.isBlank()) try { Map<String,Object> root=ModelMapper.map(Json.parse(line)); Map<String,Object> s=ModelMapper.map(root.get("suggestion")); TradeSuggestion ts=ModelMapper.suggestion(s); recommendations.put(ts.id(),ts); recommendationStatuses.put(ts.id(),String.valueOf(root.getOrDefault("status","ISSUED"))); } catch(RuntimeException ignored){}
        Path status = dir.resolve("recommendation_status.jsonl");
        if(Files.exists(status)) for(String line:Files.readAllLines(status,StandardCharsets.UTF_8)) if(!line.isBlank()) try { Map<String,Object> m=ModelMapper.map(Json.parse(line)); recommendationStatuses.put(UUID.fromString(String.valueOf(m.get("id"))),String.valueOf(m.get("status"))); } catch(RuntimeException ignored){}
        Path metric = dir.resolve("metrics.jsonl");
        if(Files.exists(metric)) for(String line:Files.readAllLines(metric,StandardCharsets.UTF_8)) if(!line.isBlank()) try { Map<String,Object> m=ModelMapper.map(Json.parse(line)); metrics.put(String.valueOf(m.get("key")), ((Number)m.getOrDefault("value",0)).doubleValue()); } catch(RuntimeException ignored){}
    }
    private void loadLines(String name, java.util.function.Consumer<Map<String,Object>> consumer) throws IOException { Path p=dir.resolve(name); if(!Files.exists(p))return; for(String line:Files.readAllLines(p,StandardCharsets.UTF_8)) if(!line.isBlank()) try { consumer.accept(ModelMapper.map(Json.parse(line))); } catch(RuntimeException ignored){} }
}
