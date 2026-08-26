package net.runelite.client.plugins.microbot.kspgeflipper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

final class KspGEFlipperBackendClient implements KspGEFlipperExecutionSink {
    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;
    private final String apiKey;

    KspGEFlipperBackendClient(String baseUrl, String apiKey) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        this.baseUrl = normalized;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    boolean healthy() {
        try {
            HttpResponse<String> response = send(request("/health").GET().build());
            if (response.statusCode() != 200) return false;
            JsonObject root = gson.fromJson(response.body(), JsonObject.class);
            return root != null && root.has("marketReady") && root.get("marketReady").getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    KspGEFlipperBackendDtos.Suggestion recommendation(KspGEFlipperBackendDtos.AccountState account) throws Exception {
        String body = gson.toJson(account);
        HttpResponse<String> response = send(request("/v1/recommendation")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        require2xx(response, "recommendation");
        return gson.fromJson(response.body(), KspGEFlipperBackendDtos.Suggestion.class);
    }

    public void transaction(KspGEFlipperBackendDtos.TradeExecution execution) throws Exception {
        HttpResponse<String> response = send(request("/v1/transactions")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(execution))).build());
        require2xx(response, "transaction");
    }

    JsonObject json(String path) throws Exception {
        HttpResponse<String> response = send(request(path).GET().build());
        require2xx(response, path);
        return gson.fromJson(response.body(), JsonObject.class);
    }

    KspGEFlipperBackendDtos.DumpEnvelope dumps() throws Exception {
        HttpResponse<String> response = send(request("/v1/dumps").GET().build());
        require2xx(response, "dumps");
        return gson.fromJson(response.body(), KspGEFlipperBackendDtos.DumpEnvelope.class);
    }

    HttpRequest.Builder request(String path) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(8));
        if (!apiKey.isBlank()) request.header("X-KSP-API-Key", apiKey);
        return request.header("User-Agent", "KSP-GE-Flipper-Plugin/" + KspGEFlipperPlugin.VERSION);
    }

    HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    String baseUrl() { return baseUrl; }
    String apiKey() { return apiKey; }

    private static void require2xx(HttpResponse<?> response, String operation) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(operation + " HTTP " + response.statusCode());
        }
    }
}
