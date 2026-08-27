package com.ksp.geflipper.config;

import java.nio.file.Path;
import java.time.Duration;

public record ServerConfig(
        String bindHost,
        int port,
        String apiKey,
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        Path dataDir,
        Duration wikiPollInterval,
        Duration dumpPollInterval,
        int marketHistoryLimit,
        int latestWarnSeconds,
        int latestRejectSeconds
) {
    public static ServerConfig fromEnvironment() {
        return new ServerConfig(
                env("KSP_BIND_HOST", "0.0.0.0"),
                integer("KSP_PORT", 8181),
                env("KSP_API_KEY", ""),
                env("KSP_DB_URL", ""),
                env("KSP_DB_USER", "ksp"),
                env("KSP_DB_PASSWORD", "ksp"),
                Path.of(env("KSP_DATA_DIR", "data")),
                Duration.ofSeconds(integer("KSP_WIKI_POLL_SECONDS", 30)),
                Duration.ofSeconds(integer("KSP_DUMP_POLL_SECONDS", 15)),
                integer("KSP_MARKET_HISTORY_LIMIT", 4096),
                integer("KSP_LATEST_WARN_SECONDS", 120),
                integer("KSP_LATEST_REJECT_SECONDS", 300));
    }

    public boolean postgresEnabled() {
        return databaseUrl != null && !databaseUrl.isBlank();
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int integer(String key, int fallback) {
        try { return Integer.parseInt(env(key, Integer.toString(fallback))); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
