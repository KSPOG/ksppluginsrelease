package com.ksp.geflipper.marketdata;

import com.ksp.geflipper.config.ServerConfig;
import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.persistence.Store;
import com.ksp.geflipper.util.Json;
import com.ksp.geflipper.util.ModelMapper;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class WikiMarketDataService implements AutoCloseable {
    private static final String BASE = "https://prices.runescape.wiki/api/v1/osrs/";
    private final ServerConfig config;
    private final Store store;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ksp-wiki-market"));
    private final Map<Integer,ItemMeta> items = new ConcurrentHashMap<>();
    private final Map<Integer,MarketSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<Integer,Deque<MarketPoint>> memoryHistory = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>(Instant.EPOCH);
    private volatile String lastError = "not-started";

    public WikiMarketDataService(ServerConfig config, Store store) { this.config=config; this.store=store; }

    public void start() {
        refreshSafely();
        scheduler.scheduleWithFixedDelay(this::refreshSafely, config.wikiPollInterval().toSeconds(), config.wikiPollInterval().toSeconds(), TimeUnit.SECONDS);
    }

    public Collection<MarketSnapshot> all() { return List.copyOf(snapshots.values()); }
    public Optional<MarketSnapshot> snapshot(int itemId) { return Optional.ofNullable(snapshots.get(itemId)); }
    public Optional<ItemMeta> item(int itemId) { return Optional.ofNullable(items.get(itemId)); }
    public Instant lastSuccess() { return lastSuccess.get(); }
    public String lastError() { return lastError; }
    public boolean ready() { return !snapshots.isEmpty() && Duration.between(lastSuccess.get(),Instant.now()).getSeconds() <= config.latestRejectSeconds(); }

    public List<MarketPoint> history(int itemId, int limit) {
        LinkedHashMap<String,MarketPoint> dedupe = new LinkedHashMap<>();
        for(MarketPoint p:store.recentMarketPoints(itemId,limit)) dedupe.put(p.timestamp()+":"+p.resolution(),p);
        Deque<MarketPoint> local=memoryHistory.get(itemId); if(local!=null) for(MarketPoint p:local) dedupe.put(p.timestamp()+":"+p.resolution(),p);
        List<MarketPoint> out=new ArrayList<>(dedupe.values()); out.sort(Comparator.comparing(MarketPoint::timestamp));
        return out.subList(Math.max(0,out.size()-Math.max(1,limit)),out.size());
    }

    private void refreshSafely() {
        try { refresh(); lastSuccess.set(Instant.now()); lastError=""; }
        catch(Exception e) { lastError=e.getClass().getSimpleName()+": "+e.getMessage(); System.err.println("[market] "+lastError); }
    }

    @SuppressWarnings("unchecked") private void refresh() throws Exception {
        if(items.isEmpty()) loadMapping();
        Map<String,Object> latest = data(get("latest"));
        Map<String,Object> five = data(get("5m"));
        Map<String,Object> hour = data(get("1h"));
        Map<String,Object> day;
        try { day = data(get("24h")); } catch(Exception ignored) { day = Map.of(); }
        Instant observed=Instant.now();
        for(Map.Entry<Integer,ItemMeta> entry:items.entrySet()) {
            int id=entry.getKey(); Map<String,Object> l=ModelMapper.map(latest.get(Integer.toString(id))); if(l.isEmpty())continue;
            Map<String,Object> f=ModelMapper.map(five.get(Integer.toString(id))); Map<String,Object> h=ModelMapper.map(hour.get(Integer.toString(id))); Map<String,Object> d=ModelMapper.map(day.get(Integer.toString(id)));
            long high=ModelMapper.number(l,"high",0), low=ModelMapper.number(l,"low",0); if(high<=0||low<=0)continue;
            Instant highTime=Instant.ofEpochSecond(ModelMapper.number(l,"highTime",observed.getEpochSecond()));
            Instant lowTime=Instant.ofEpochSecond(ModelMapper.number(l,"lowTime",observed.getEpochSecond()));
            MarketSnapshot s=new MarketSnapshot(entry.getValue(),observed,high,low,highTime,lowTime,
                    ModelMapper.number(f,"avgHighPrice",high),ModelMapper.number(f,"avgLowPrice",low),ModelMapper.number(f,"highPriceVolume",0),ModelMapper.number(f,"lowPriceVolume",0),
                    ModelMapper.number(h,"avgHighPrice",high),ModelMapper.number(h,"avgLowPrice",low),ModelMapper.number(h,"highPriceVolume",0),ModelMapper.number(h,"lowPriceVolume",0),
                    ModelMapper.number(d,"highPriceVolume",0)+ModelMapper.number(d,"lowPriceVolume",0));
            snapshots.put(id,s);
            appendPoint(new MarketPoint(id,observed,high,low,Math.max(0,s.volume5mHigh()),Math.max(0,s.volume5mLow()),"LATEST"));
            if(!f.isEmpty()) appendPoint(new MarketPoint(id,observed,s.avg5mHigh(),s.avg5mLow(),s.volume5mHigh(),s.volume5mLow(),"5M"));
            if(!h.isEmpty()) appendPoint(new MarketPoint(id,observed,s.avg1hHigh(),s.avg1hLow(),s.volume1hHigh(),s.volume1hLow(),"1H"));
        }
    }

    private void loadMapping() throws Exception {
        Object parsed=Json.parse(get("mapping")); if(!(parsed instanceof List<?> list))throw new IllegalStateException("Wiki mapping is not an array");
        for(Object raw:list) { Map<String,Object> m=ModelMapper.map(raw); int id=ModelMapper.integer(m,"id",0), limit=ModelMapper.integer(m,"limit",0); String name=ModelMapper.string(m,"name",""); if(id>0&&!name.isBlank()){ ItemMeta item=new ItemMeta(id,name,ModelMapper.bool(m,"members",false),limit,limit>0); items.put(id,item); store.saveItem(item); } }
    }

    private void appendPoint(MarketPoint p) {
        Deque<MarketPoint> q=memoryHistory.computeIfAbsent(p.itemId(),x->new ArrayDeque<>()); synchronized(q){q.addLast(p);while(q.size()>config.marketHistoryLimit())q.removeFirst();}
        store.saveMarketPoint(p);
    }

    private Map<String,Object> data(String json) { Map<String,Object> root=ModelMapper.map(Json.parse(json)); return ModelMapper.map(root.get("data")); }
    private String get(String path) throws Exception {
        HttpRequest req=HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(10)).header("User-Agent","KSP-GE-Flipper-Server/1.0 (https://github.com/KSPOG/ksppluginsrelease)").GET().build();
        HttpResponse<String> res=http.send(req,HttpResponse.BodyHandlers.ofString()); if(res.statusCode()!=200)throw new IllegalStateException(path+" HTTP "+res.statusCode()); store.saveRawMarket(path,res.body(),Instant.now()); return res.body();
    }
    @Override public void close(){scheduler.shutdownNow();}
}
