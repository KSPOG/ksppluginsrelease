package com.ksp.geflipper.persistence;

import com.ksp.geflipper.config.ServerConfig;
import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.util.Json;
import com.ksp.geflipper.util.ModelMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** PostgreSQL store. It compiles against java.sql only; the PostgreSQL JDBC driver is a runtime dependency. */
public final class JdbcStore implements Store {
    private final Connection connection;

    public JdbcStore(ServerConfig config) {
        try {
            this.connection = DriverManager.getConnection(config.databaseUrl(), config.databaseUser(), config.databasePassword());
            this.connection.setAutoCommit(true);
            migrate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot connect to PostgreSQL: " + config.databaseUrl(), e);
        }
    }

    private void migrate() {
        try (InputStream in = JdbcStore.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IllegalStateException("schema.sql missing");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim(); if (trimmed.isEmpty()) continue;
                try (Statement st = connection.createStatement()) { st.execute(trimmed); }
            }
        } catch (IOException | SQLException e) { throw new IllegalStateException("Database migration failed", e); }
    }

    @Override public void saveAccount(AccountState a) {
        exec("INSERT INTO users(user_key) VALUES(?) ON CONFLICT(user_key) DO NOTHING", a.accountKey());
        exec("INSERT INTO accounts(account_key,payload) VALUES(?,?::jsonb) ON CONFLICT(account_key) DO UPDATE SET updated_at=NOW(),payload=EXCLUDED.payload", a.accountKey(), Json.stringify(a));
        exec("INSERT INTO account_preferences(account_key,payload) VALUES(?,?::jsonb) ON CONFLICT(account_key) DO UPDATE SET updated_at=NOW(),payload=EXCLUDED.payload", a.accountKey(), Json.stringify(a.strategy()));
    }
    @Override public void saveItem(ItemMeta i){ exec("INSERT INTO items(item_id,name,members,buy_limit,tradeable,payload) VALUES(?,?,?,?,?,?::jsonb) ON CONFLICT(item_id) DO UPDATE SET name=EXCLUDED.name,members=EXCLUDED.members,buy_limit=EXCLUDED.buy_limit,tradeable=EXCLUDED.tradeable,payload=EXCLUDED.payload,updated_at=NOW()",i.itemId(),i.name(),i.members(),i.buyLimit(),i.tradeable(),Json.stringify(i)); }
    @Override public void saveRawMarket(String endpoint,String payload,Instant observedAt){ exec("INSERT INTO raw_market_responses(endpoint,observed_at,payload) VALUES(?,?,?::jsonb)",endpoint,observedAt,payload); }
    @Override public void saveOffers(String accountKey,List<GeOfferState> offers){ exec("INSERT INTO ge_offer_snapshots(account_key,payload) VALUES(?,?::jsonb)",accountKey,Json.stringify(offers)); }
    @Override public void saveMarketPoint(MarketPoint p){
        exec("INSERT INTO market_ticks(item_id,observed_at,high_price,low_price,high_volume,low_volume,resolution) VALUES(?,?,?,?,?,?,?)",p.itemId(),p.timestamp(),p.highPrice(),p.lowPrice(),p.highVolume(),p.lowVolume(),p.resolution());
        if("5M".equalsIgnoreCase(p.resolution())) exec("INSERT INTO market_5m(item_id,observed_at,high_price,low_price,high_volume,low_volume) VALUES(?,?,?,?,?,?) ON CONFLICT(item_id,observed_at) DO UPDATE SET high_price=EXCLUDED.high_price,low_price=EXCLUDED.low_price,high_volume=EXCLUDED.high_volume,low_volume=EXCLUDED.low_volume",p.itemId(),p.timestamp(),p.highPrice(),p.lowPrice(),p.highVolume(),p.lowVolume());
        if("1H".equalsIgnoreCase(p.resolution())) exec("INSERT INTO market_1h(item_id,observed_at,high_price,low_price,high_volume,low_volume) VALUES(?,?,?,?,?,?) ON CONFLICT(item_id,observed_at) DO UPDATE SET high_price=EXCLUDED.high_price,low_price=EXCLUDED.low_price,high_volume=EXCLUDED.high_volume,low_volume=EXCLUDED.low_volume",p.itemId(),p.timestamp(),p.highPrice(),p.lowPrice(),p.highVolume(),p.lowVolume());
    }
    @Override public List<MarketPoint> recentMarketPoints(int itemId,int limit){
        String sql="SELECT item_id,observed_at,high_price,low_price,high_volume,low_volume,resolution FROM market_ticks WHERE item_id=? ORDER BY observed_at DESC LIMIT ?";
        List<MarketPoint> out=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement(sql)){ ps.setInt(1,itemId); ps.setInt(2,Math.max(1,limit)); try(ResultSet rs=ps.executeQuery()){ while(rs.next()) out.add(new MarketPoint(rs.getInt(1),rs.getTimestamp(2).toInstant(),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getString(7))); }}catch(SQLException e){throw db(e);} Collections.reverse(out); return out;
    }
    @Override public void saveForecast(ItemForecast f){ exec("INSERT INTO forecasts(item_id,generated_at,quality,confidence,payload) VALUES(?,?,?,?,?::jsonb)",f.itemId(),f.generatedAt(),f.quality().name(),f.confidence(),Json.stringify(f)); }
    @Override public void saveRecommendation(String accountKey,TradeSuggestion s,Map<String,Double> features,StrategyPreferences preferences){
        String sql="INSERT INTO recommendations(id,account_key,created_at,action,item_id,price,quantity,expected_profit,expected_duration_seconds,confidence,is_hold,status,payload) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(id) DO NOTHING";
        exec(sql,s.id(),accountKey,s.generatedAt(),s.type().name(),s.itemId(),s.price(),s.quantity(),s.expectedProfit(),s.expectedDurationSeconds(),s.confidence(),s.hold(),"ISSUED",Json.stringify(s));
        exec("UPDATE recommendations SET risk_level=?,timeframe_minutes=? WHERE id=?",preferences.riskLevel().name(),preferences.timeframeMinutes(),s.id());
        for(var e:features.entrySet()) exec("INSERT INTO recommendation_features(recommendation_id,feature_name,feature_value) VALUES(?,?,?) ON CONFLICT(recommendation_id,feature_name) DO UPDATE SET feature_value=EXCLUDED.feature_value",s.id(),e.getKey(),e.getValue());
    }
    @Override public Optional<TradeSuggestion> recommendation(UUID id){
        if(id==null)return Optional.empty();try(PreparedStatement ps=connection.prepareStatement("SELECT payload::text FROM recommendations WHERE id=?")){ps.setObject(1,id);try(ResultSet rs=ps.executeQuery()){if(rs.next())return Optional.of(ModelMapper.suggestion(ModelMapper.map(Json.parse(rs.getString(1)))));}}catch(SQLException e){throw db(e);}return Optional.empty();
    }
    @Override public void markRecommendationStatus(UUID id,String status){if(id!=null)exec("UPDATE recommendations SET status=? WHERE id=?",status,id);}
    @Override public String recommendationStatus(UUID id){if(id==null)return "UNKNOWN";try(PreparedStatement ps=connection.prepareStatement("SELECT status FROM recommendations WHERE id=?")){ps.setObject(1,id);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getString(1):"UNKNOWN";}}catch(SQLException e){throw db(e);}}
    @Override public Map<String,Long> recommendationActionCounts(){Map<String,Long> out=new HashMap<>();try(Statement st=connection.createStatement();ResultSet rs=st.executeQuery("SELECT action,COUNT(*) FROM recommendations GROUP BY action")){while(rs.next())out.put(rs.getString(1),rs.getLong(2));}catch(SQLException e){throw db(e);}return out;}
    @Override public void saveExecution(TradeExecution e){ exec("INSERT INTO transactions(id,account_key,item_id,side,price,quantity,amount_spent,executed_at,suggestion_id,recommendation_price_used,recommendation_originated_trade,payload) VALUES(?,?,?,?,?,?,?,?,?,?,?,?::jsonb) ON CONFLICT(id) DO NOTHING",e.id(),e.accountKey(),e.itemId(),e.side().name(),e.price(),e.quantity(),e.amountSpent(),e.timestamp(),e.suggestionId(),e.recommendationPriceUsed(),e.recommendationOriginatedTrade(),Json.stringify(e)); }
    @Override public List<TradeExecution> executions(String accountKey,int limit){
        List<TradeExecution> out=new ArrayList<>();try(PreparedStatement ps=connection.prepareStatement("SELECT payload::text FROM transactions WHERE account_key=? ORDER BY executed_at DESC LIMIT ?")){ps.setString(1,accountKey);ps.setInt(2,Math.max(1,limit));try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(ModelMapper.execution(ModelMapper.map(Json.parse(rs.getString(1)))));}}catch(SQLException e){throw db(e);}Collections.reverse(out);return out;
    }
    @Override public void savePosition(Position p){ exec("INSERT INTO positions(id,account_key,item_id,status,payload) VALUES(?,?,?,?,?::jsonb) ON CONFLICT(id) DO UPDATE SET status=EXCLUDED.status,updated_at=NOW(),payload=EXCLUDED.payload",p.id(),p.accountKey(),p.itemId(),p.status().name(),Json.stringify(p)); }
    @Override public void linkPositionExecution(UUID positionId,UUID executionId){ exec("INSERT INTO position_transactions(position_id,transaction_id) VALUES(?,?) ON CONFLICT DO NOTHING",positionId,executionId); }
    @Override public List<Position> positions(String accountKey){ List<Position> out=new ArrayList<>();try(PreparedStatement ps=connection.prepareStatement("SELECT payload::text FROM positions WHERE account_key=? ORDER BY updated_at")){ps.setString(1,accountKey);try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(ModelMapper.position(ModelMapper.map(Json.parse(rs.getString(1)))));}}catch(SQLException e){throw db(e);}return out; }
    @Override public void saveDump(DumpSignal s){ exec("INSERT INTO dump_events(id,item_id,detected_at,severity,payload) VALUES(?,?,?,?,?::jsonb) ON CONFLICT(id) DO NOTHING",s.id(),s.itemId(),s.detectedAt(),s.severity(),Json.stringify(s)); }
    @Override public List<DumpSignal> dumps(int limit){ List<DumpSignal> out=new ArrayList<>();try(PreparedStatement ps=connection.prepareStatement("SELECT payload::text FROM dump_events ORDER BY detected_at DESC LIMIT ?")){ps.setInt(1,Math.max(1,limit));try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(ModelMapper.dump(ModelMapper.map(Json.parse(rs.getString(1)))));}}catch(SQLException e){throw db(e);}return out; }
    @Override public void saveOutcome(RecommendationOutcome o){ exec("INSERT INTO recommendation_outcomes(recommendation_id,account_key,item_id,recorded_at,payload) VALUES(?,?,?,?,?::jsonb) ON CONFLICT(recommendation_id) DO UPDATE SET recorded_at=EXCLUDED.recorded_at,payload=EXCLUDED.payload",o.recommendationId(),o.accountKey(),o.itemId(),o.recordedAt(),Json.stringify(o)); }
    @Override public List<RecommendationOutcome> outcomes(int limit){ List<RecommendationOutcome> out=new ArrayList<>();try(PreparedStatement ps=connection.prepareStatement("SELECT payload::text FROM recommendation_outcomes ORDER BY recorded_at DESC LIMIT ?")){ps.setInt(1,Math.max(1,limit));try(ResultSet rs=ps.executeQuery()){while(rs.next())out.add(ModelMapper.outcome(ModelMapper.map(Json.parse(rs.getString(1)))));}}catch(SQLException e){throw db(e);}Collections.reverse(out);return out; }
    @Override public void saveMetric(String key,double value,long samples){ exec("INSERT INTO model_metrics(metric_key,metric_value,sample_count) VALUES(?,?,?) ON CONFLICT(metric_key) DO UPDATE SET metric_value=EXCLUDED.metric_value,sample_count=EXCLUDED.sample_count,updated_at=NOW()",key,value,samples); }
    @Override public Map<String,Double> metrics(){ Map<String,Double> out=new HashMap<>();try(Statement st=connection.createStatement();ResultSet rs=st.executeQuery("SELECT metric_key,metric_value FROM model_metrics")){while(rs.next())out.put(rs.getString(1),rs.getDouble(2));}catch(SQLException e){throw db(e);}return out; }
    @Override public void close(){try{connection.close();}catch(SQLException ignored){}}

    private void exec(String sql,Object...args){ try(PreparedStatement ps=connection.prepareStatement(sql)){for(int i=0;i<args.length;i++){Object v=args[i];if(v instanceof Instant instant)ps.setTimestamp(i+1,Timestamp.from(instant));else ps.setObject(i+1,v);}ps.executeUpdate();}catch(SQLException e){throw db(e);} }
    private IllegalStateException db(SQLException e){return new IllegalStateException("PostgreSQL operation failed",e);}
}
