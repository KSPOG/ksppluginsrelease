package com.ksp.geflipper.api;

import com.ksp.geflipper.analytics.CalibrationService;
import com.ksp.geflipper.config.ServerConfig;
import com.ksp.geflipper.dumps.DumpService;
import com.ksp.geflipper.features.FeatureEngine;
import com.ksp.geflipper.forecasting.ForecastService;
import com.ksp.geflipper.marketdata.WikiMarketDataService;
import com.ksp.geflipper.model.Models.*;
import com.ksp.geflipper.persistence.Store;
import com.ksp.geflipper.portfolio.PortfolioService;
import com.ksp.geflipper.recommendations.RecommendationService;
import com.ksp.geflipper.transactions.TransactionService;
import com.ksp.geflipper.util.Json;
import com.ksp.geflipper.util.ModelMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Versioned JSON/SSE API. The protobuf contract is kept under proto/ for a binary transport adapter. */
public final class ApiServer implements AutoCloseable {
    private final ServerConfig config;
    private final Store store;
    private final WikiMarketDataService market;
    private final FeatureEngine features;
    private final ForecastService forecasts;
    private final RecommendationService recommendations;
    private final TransactionService transactions;
    private final PortfolioService portfolio;
    private final DumpService dumps;
    private final CalibrationService calibration;
    private final HttpServer server;

    public ApiServer(ServerConfig config, Store store, WikiMarketDataService market, FeatureEngine features,
                     ForecastService forecasts, RecommendationService recommendations, TransactionService transactions,
                     PortfolioService portfolio, DumpService dumps, CalibrationService calibration) {
        try {
            this.config = config;
            this.store = store;
            this.market = market;
            this.features = features;
            this.forecasts = forecasts;
            this.recommendations = recommendations;
            this.transactions = transactions;
            this.portfolio = portfolio;
            this.dumps = dumps;
            this.calibration = calibration;
            this.server = HttpServer.create(new InetSocketAddress(config.bindHost(), config.port()), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            routes();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot bind API server", e);
        }
    }

    public void start() {
        server.start();
        System.out.println("KSP GE Flipper API listening on http://" + config.bindHost() + ":" + config.port());
    }

    private void routes() {
        server.createContext("/health", this::health);
        server.createContext("/v1/account", this::account);
        server.createContext("/v1/recommendation", this::recommendation);
        server.createContext("/v1/recommendations", this::recommendationLookup);
        server.createContext("/v1/transactions", this::transaction);
        server.createContext("/v1/outcomes", this::outcome);
        server.createContext("/v1/offers", this::offers);
        server.createContext("/v1/prices", this::prices);
        server.createContext("/v1/dumps", this::dumpList);
        server.createContext("/v1/events/dumps", this::dumpStream);
        server.createContext("/v1/portfolio", this::portfolio);
        server.createContext("/v1/metrics", this::metrics);
    }

    private void health(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "GET")) return;
        send(x, 200, Map.of(
                "status", market.ready() ? "UP" : "DEGRADED",
                "marketReady", market.ready(),
                "lastMarketSuccess", market.lastSuccess(),
                "lastMarketError", market.lastError(),
                "persistence", store.getClass().getSimpleName(),
                "time", Instant.now()));
    }

    private void account(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "POST")) return;
        try {
            AccountState account = ModelMapper.account(Json.object(body(x)));
            store.saveAccount(account);
            store.saveOffers(account.accountKey(), account.offers());
            send(x, 202, Map.of("accepted", true, "accountKey", account.accountKey()));
        } catch (Exception e) { error(x, 400, e); }
    }

    private void recommendation(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "POST")) return;
        try {
            AccountState account = ModelMapper.account(Json.object(body(x)));
            send(x, 200, recommendations.recommend(account));
        } catch (Exception e) { error(x, 400, e); }
    }

    private void recommendationLookup(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "GET")) return;
        try {
            String id = pathTail(x, "/v1/recommendations");
            if (id.isBlank()) throw new IllegalArgumentException("Recommendation id is required");
            Optional<TradeSuggestion> recommendation = store.recommendation(UUID.fromString(id));
            if (recommendation.isEmpty()) { send(x, 404, Map.of("error", "not_found")); return; }
            send(x, 200, Map.of("suggestion", recommendation.get(), "status", store.recommendationStatus(recommendation.get().id())));
        } catch (Exception e) { error(x, 400, e); }
    }

    private void transaction(HttpExchange x) throws IOException {
        if (!auth(x)) return;
        if ("GET".equalsIgnoreCase(x.getRequestMethod())) {
            String account = query(x).getOrDefault("account", "default");
            int limit = parseInt(query(x).get("limit"), 250, 1, 10_000);
            send(x, 200, Map.of("accountKey", account, "transactions", store.executions(account, limit)));
            return;
        }
        if (!requireMethod(x, "POST")) return;
        try {
            TradeExecution execution = ModelMapper.execution(Json.object(body(x)));
            if (execution.itemId() <= 0 || execution.quantity() <= 0 || execution.price() <= 0)
                throw new IllegalArgumentException("itemId, quantity and price must be positive");
            send(x, 200, transactions.record(execution));
        } catch (Exception e) { error(x, 400, e); }
    }

    private void outcome(HttpExchange x) throws IOException {
        if (!auth(x)) return;
        if ("GET".equalsIgnoreCase(x.getRequestMethod())) {
            int limit = parseInt(query(x).get("limit"), 250, 1, 10_000);
            send(x, 200, Map.of("outcomes", store.outcomes(limit)));
            return;
        }
        if (!requireMethod(x, "POST")) return;
        try {
            RecommendationOutcome outcome = ModelMapper.outcome(Json.object(body(x)));
            calibration.recordOutcome(outcome);
            send(x, 202, Map.of("accepted", true));
        } catch (Exception e) { error(x, 400, e); }
    }

    private void offers(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "POST")) return;
        try {
            Map<String,Object> m = Json.object(body(x));
            String account = ModelMapper.string(m, "accountKey", "default");
            List<GeOfferState> offers = new ArrayList<>();
            for (Map<String,Object> offer : ModelMapper.mapList(m.get("offers"))) offers.add(ModelMapper.offer(offer));
            store.saveOffers(account, offers);
            send(x, 202, Map.of("accepted", offers.size()));
        } catch (Exception e) { error(x, 400, e); }
    }

    private void prices(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "GET")) return;
        try {
            int id = Integer.parseInt(pathTail(x, "/v1/prices"));
            int timeframe = parseInt(query(x).get("timeframe"), 30, 5, 10_080);
            MarketSnapshot snapshot = market.snapshot(id).orElseThrow(() -> new IllegalArgumentException("Unknown itemId"));
            List<MarketPoint> history = market.history(id, 512);
            MarketFeatures featureSet = features.extract(snapshot, history);
            ItemForecast forecast = forecasts.forecast(snapshot, featureSet, history, timeframe, config.latestRejectSeconds());
            send(x, 200, Map.of("current", snapshot, "history", history, "features", featureSet, "forecast", forecast));
        } catch (Exception e) { error(x, 404, e); }
    }

    private void dumpList(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "GET")) return;
        send(x, 200, Map.of("active", dumps.active()));
    }

    private void dumpStream(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "GET")) return;
        Headers headers = x.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream");
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");
        x.sendResponseHeaders(200, 0);
        BlockingQueue<DumpSignal> queue = dumps.subscribe();
        try (OutputStream out = x.getResponseBody()) {
            out.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            while (true) {
                DumpSignal signal = queue.poll(25, TimeUnit.SECONDS);
                String frame = signal == null ? ": keepalive\n\n" : "event: dump\ndata: " + Json.stringify(signal) + "\n\n";
                out.write(frame.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception ignored) {
            // Client disconnect is a normal stream termination.
        } finally {
            dumps.unsubscribe(queue);
        }
    }

    private void portfolio(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "GET")) return;
        String account = query(x).getOrDefault("account", "default");
        send(x, 200, portfolio.snapshot(account));
    }

    private void metrics(HttpExchange x) throws IOException {
        if (!auth(x) || !requireMethod(x, "GET")) return;
        send(x, 200, Map.of(
                "calibration", calibration.metrics(),
                "raw", store.metrics(),
                "recommendationActions", store.recommendationActionCounts()));
    }

    private boolean auth(HttpExchange x) throws IOException {
        if (config.apiKey() == null || config.apiKey().isBlank()) return true;
        String provided = x.getRequestHeaders().getFirst("X-KSP-API-Key");
        boolean ok = provided != null && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), config.apiKey().getBytes(StandardCharsets.UTF_8));
        if (!ok) send(x, 401, Map.of("error", "unauthorized"));
        return ok;
    }

    private boolean requireMethod(HttpExchange x, String method) throws IOException {
        if (method.equalsIgnoreCase(x.getRequestMethod())) return true;
        send(x, 405, Map.of("error", "method_not_allowed", "expected", method));
        return false;
    }

    private static String body(HttpExchange x) throws IOException {
        return new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String pathTail(HttpExchange x, String prefix) {
        String rest = x.getRequestURI().getPath().substring(prefix.length());
        return rest.startsWith("/") ? rest.substring(1) : rest;
    }

    private static Map<String,String> query(HttpExchange x) {
        Map<String,String> out = new HashMap<>();
        String raw = x.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split("&")) {
            String[] kv = part.split("=", 2);
            out.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return out;
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(value))); }
        catch (Exception ignored) { return fallback; }
    }

    private static void send(HttpExchange x, int status, Object value) throws IOException {
        byte[] bytes = Json.stringify(value).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = x.getResponseBody()) { out.write(bytes); }
    }

    private static void error(HttpExchange x, int status, Exception e) throws IOException {
        send(x, status, Map.of("error", e.getClass().getSimpleName(), "message", String.valueOf(e.getMessage())));
    }

    @Override public void close() { server.stop(1); }
}
