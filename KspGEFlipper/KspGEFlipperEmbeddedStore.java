package net.runelite.client.plugins.microbot.kspgeflipper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static net.runelite.client.plugins.microbot.kspgeflipper.KspGEFlipperEmbeddedModels.*;

/**
 * Crash-tolerant snapshot persistence for embedded mode.
 *
 * The raw release repository cannot bundle an SQLite JDBC driver, so embedded mode
 * uses a bounded JSON snapshot written atomically under ~/.runelite. All state that
 * matters to calibration/portfolio continuity survives client restarts.
 */
final class KspGEFlipperEmbeddedStore {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final Path directory;
    private final Path stateFile;
    private final Path legacyStateFile;
    private PersistedState state;
    private long dirtyGeneration;
    private long savedGeneration;

    KspGEFlipperEmbeddedStore() {
        this(Paths.get(System.getProperty("user.home"), ".runelite", "ksp-ge-flipper"));
    }

    KspGEFlipperEmbeddedStore(Path directory) {
        this.directory = directory;
        this.stateFile = directory.resolve("embedded-state.json.gz");
        this.legacyStateFile = directory.resolve("embedded-state.json");
        this.state = load();
        normalize();
    }

    synchronized PersistedState state() { return state; }

    synchronized void putRecommendation(String accountKey, RecommendationRecord record) {
        if (record == null || record.suggestion == null || record.suggestion.id == null) return;
        record.accountKey = accountKey;
        state.recommendations.put(record.suggestion.id, record);
        trimRecommendations(4000);
        dirty();
    }

    synchronized RecommendationRecord recommendation(String id) {
        return id == null ? null : state.recommendations.get(id);
    }

    synchronized void markRecommendation(String id, String status) {
        RecommendationRecord record = recommendation(id);
        if (record != null) {
            record.status = status == null ? "" : status;
            dirty();
        }
    }

    synchronized void addExecution(KspGEFlipperBackendDtos.TradeExecution execution) {
        ExecutionRecord record = new ExecutionRecord();
        record.execution = execution;
        record.recordedAt = System.currentTimeMillis();
        state.executions.add(record);
        while (state.executions.size() > 6000) state.executions.remove(0);
        dirty();
    }

    synchronized List<KspGEFlipperBackendDtos.TradeExecution> executions(String accountKey, int limit) {
        List<KspGEFlipperBackendDtos.TradeExecution> out = new ArrayList<>();
        for (int i = state.executions.size() - 1; i >= 0 && out.size() < Math.max(1, limit); i--) {
            KspGEFlipperBackendDtos.TradeExecution e = state.executions.get(i).execution;
            if (e != null && (accountKey == null || accountKey.equals(e.accountKey))) out.add(e);
        }
        Collections.reverse(out);
        return out;
    }

    synchronized void putPosition(Position position) {
        if (position == null || position.id == null) return;
        state.positions.put(position.id, position);
        dirty();
    }

    synchronized List<Position> positions(String accountKey) {
        List<Position> out = new ArrayList<>();
        for (Position p : state.positions.values()) if (p != null && accountKey.equals(p.accountKey)) out.add(p);
        out.sort(Comparator.comparingLong(p -> p.openedAt));
        return out;
    }

    synchronized void addOutcome(Outcome outcome) {
        if (outcome == null) return;
        state.outcomes.add(outcome);
        while (state.outcomes.size() > 5000) state.outcomes.remove(0);
        dirty();
    }

    synchronized List<Outcome> outcomes(int limit) {
        int from = Math.max(0, state.outcomes.size() - Math.max(1, limit));
        return new ArrayList<>(state.outcomes.subList(from, state.outcomes.size()));
    }

    synchronized CalibrationBucket calibration(String key) {
        CalibrationBucket bucket = state.calibration.get(key);
        if (bucket == null) {
            bucket = new CalibrationBucket();
            state.calibration.put(key, bucket);
        }
        return bucket;
    }

    synchronized Map<String, CalibrationBucket> calibration() { return state.calibration; }

    synchronized void putMetric(String key, double value, long samples) {
        Metric metric = state.metrics.get(key);
        if (metric == null) {
            metric = new Metric();
            state.metrics.put(key, metric);
        }
        metric.value = value;
        metric.samples = samples;
        dirty();
    }

    synchronized Metric metric(String key) { return state.metrics.get(key); }
    synchronized Map<String, Metric> metrics() { return state.metrics; }

    synchronized void appendMarketPoint(MarketPoint point, int perItemLimit) {
        if (point == null) return;
        List<MarketPoint> points = state.history.get(point.itemId);
        if (points == null) {
            points = new ArrayList<>();
            state.history.put(point.itemId, points);
        }
        points.add(point);
        int max = Math.max(24, perItemLimit);
        if (points.size() > max) points.subList(0, points.size() - max).clear();
        dirty();
    }

    synchronized List<MarketPoint> marketHistory(int itemId, int limit) {
        List<MarketPoint> points = state.history.get(itemId);
        if (points == null || points.isEmpty()) return new ArrayList<>();
        int from = Math.max(0, points.size() - Math.max(1, limit));
        return new ArrayList<>(points.subList(from, points.size()));
    }

    synchronized void putForecast(Forecast forecast) {
        if (forecast == null || forecast.itemId <= 0) return;
        state.forecasts.put(forecast.itemId, forecast);
        dirty();
    }

    synchronized Forecast forecast(int itemId) { return state.forecasts.get(itemId); }

    synchronized void saveIfDirty() {
        if (dirtyGeneration == savedGeneration) return;
        saveNow();
    }

    synchronized void saveNow() {
        try {
            Files.createDirectories(directory);
            Path tmp = directory.resolve("embedded-state.json.gz.tmp");
            try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(tmp)), StandardCharsets.UTF_8)) {
                gson.toJson(state, writer);
            }
            try {
                Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
            savedGeneration = dirtyGeneration;
        } catch (Exception ignored) {
            // Persistence failure must not take down GE execution. The next save retries.
        }
    }

    private PersistedState load() {
        try {
            if (Files.exists(stateFile)) {
                try (Reader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(stateFile)), StandardCharsets.UTF_8)) {
                    PersistedState loaded = gson.fromJson(reader, PersistedState.class);
                    return loaded == null ? new PersistedState() : loaded;
                }
            }
            if (Files.exists(legacyStateFile)) {
                String json = new String(Files.readAllBytes(legacyStateFile), StandardCharsets.UTF_8);
                PersistedState loaded = gson.fromJson(json, PersistedState.class);
                return loaded == null ? new PersistedState() : loaded;
            }
            return new PersistedState();
        } catch (Exception ignored) {
            try {
                if (Files.exists(stateFile)) {
                    Files.createDirectories(directory);
                    Files.move(stateFile, directory.resolve("embedded-state.corrupt-" + System.currentTimeMillis() + ".json"), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored2) { }
            return new PersistedState();
        }
    }

    private void normalize() {
        if (state.positions == null) state.positions = new java.util.HashMap<>();
        if (state.recommendations == null) state.recommendations = new java.util.HashMap<>();
        if (state.executions == null) state.executions = new ArrayList<>();
        if (state.outcomes == null) state.outcomes = new ArrayList<>();
        if (state.calibration == null) state.calibration = new java.util.HashMap<>();
        if (state.metrics == null) state.metrics = new java.util.HashMap<>();
        if (state.history == null) state.history = new java.util.HashMap<>();
        if (state.forecasts == null) state.forecasts = new java.util.HashMap<>();
    }

    private void trimRecommendations(int max) {
        if (state.recommendations.size() <= max) return;
        List<RecommendationRecord> records = new ArrayList<>(state.recommendations.values());
        records.sort(Comparator.comparingLong(r -> r == null ? 0L : r.createdAt));
        int remove = state.recommendations.size() - max;
        for (RecommendationRecord record : records) {
            if (remove-- <= 0) break;
            if (record != null && record.suggestion != null) state.recommendations.remove(record.suggestion.id);
        }
    }

    private void dirty() { dirtyGeneration++; }
}
