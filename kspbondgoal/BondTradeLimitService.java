package net.runelite.client.plugins.microbot.kspbondgoal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads the OSRS Wiki mapping once and exposes published GE buy limits.
 * Network work never runs on the RuneLite client thread.
 */
final class BondTradeLimitService implements AutoCloseable
{
    private static final String MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";
    private static final long RETRY_DELAY_MS = 60_000L;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r ->
    {
        Thread thread = new Thread(r, "ksp-bond-goal-trade-limits");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Map<Integer, Integer> limits = Collections.emptyMap();
    private volatile boolean loaded;
    private volatile boolean loading;
    private volatile boolean closed;
    private volatile long lastAttemptAt;
    private volatile String status = "Loading...";

    void ensureLoaded(Runnable onLoaded)
    {
        if (loaded || loading || closed)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastAttemptAt > 0 && now - lastAttemptAt < RETRY_DELAY_MS)
        {
            return;
        }

        loading = true;
        lastAttemptAt = now;
        status = "Loading...";

        executor.submit(() ->
        {
            try
            {
                Map<Integer, Integer> fetched = fetchLimits();
                if (fetched.isEmpty())
                {
                    status = "Retrying...";
                    return;
                }

                limits = Collections.unmodifiableMap(fetched);
                loaded = true;
                status = "OSRS Wiki (4h)";
                if (!closed && onLoaded != null)
                {
                    onLoaded.run();
                }
            }
            catch (Exception ignored)
            {
                status = "Retrying...";
            }
            finally
            {
                loading = false;
            }
        });
    }

    boolean isLoaded()
    {
        return loaded;
    }

    String getStatus()
    {
        return status;
    }

    int getLimit4Hours(int itemId)
    {
        Integer limit = limits.get(itemId);
        return limit == null ? 0 : Math.max(0, limit);
    }

    private Map<Integer, Integer> fetchLimits() throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(MAPPING_URL))
            .header("User-Agent", "KSP-Bond-Goal/1.0.2 (github.com/KSPOG/ksppluginsrelease)")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IllegalStateException("OSRS Wiki mapping HTTP " + response.statusCode());
        }

        JsonElement root = new JsonParser().parse(response.body());
        if (!root.isJsonArray())
        {
            throw new IllegalStateException("Unexpected OSRS Wiki mapping response");
        }

        Map<Integer, Integer> result = new HashMap<>();
        for (JsonElement element : root.getAsJsonArray())
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            JsonObject item = element.getAsJsonObject();
            if (!item.has("id") || !item.has("limit") || item.get("limit").isJsonNull())
            {
                continue;
            }

            int id = item.get("id").getAsInt();
            int limit = item.get("limit").getAsInt();
            if (id > 0 && limit > 0)
            {
                result.put(id, limit);
            }
        }
        return result;
    }

    @Override
    public void close()
    {
        closed = true;
        executor.shutdownNow();
    }
}
