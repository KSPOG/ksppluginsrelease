package net.runelite.client.plugins.microbot.kspgeflipper;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Dedicated SSE listener for globally detected dump events. */
final class KspGEFlipperDumpStream implements AutoCloseable {
    private final KspGEFlipperBackendClient client;
    private final Gson gson = new Gson();
    private volatile boolean running;
    private volatile KspGEFlipperBackendDtos.DumpSignal latest;
    private volatile String status = "Off";
    private Thread thread;

    KspGEFlipperDumpStream(KspGEFlipperBackendClient client) { this.client = client; }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "ksp-ge-dump-stream");
        thread.setDaemon(true);
        thread.start();
    }

    KspGEFlipperBackendDtos.DumpSignal latest() { return latest; }
    String status() { return status; }

    private void loop() {
        while (running) {
            try {
                HttpRequest request = client.request("/v1/events/dumps")
                        .header("Accept", "text/event-stream").GET().build();
                status = "Connecting";
                HttpResponse<java.io.InputStream> response = java.net.http.HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) throw new IllegalStateException("dump stream HTTP " + response.statusCode());
                status = "Connected";
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String json = line.substring(5).trim();
                        if (!json.isEmpty()) latest = gson.fromJson(json, KspGEFlipperBackendDtos.DumpSignal.class);
                    }
                }
            } catch (Exception e) {
                if (!running) break;
                status = "Retrying";
                try { Thread.sleep(5_000L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        status = "Off";
    }

    @Override
    public synchronized void close() {
        running = false;
        if (thread != null) thread.interrupt();
    }
}
