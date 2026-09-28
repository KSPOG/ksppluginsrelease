package net.runelite.client.plugins.microbot.kspaccountbuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Singleton;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class KspAccountPlayTimeCache
{
    private static final String CACHE_FILE_NAME = "ksp-account-builder-playtime.json";
    private static final long SAVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(30);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path cachePath = RuneLite.RUNELITE_DIR.toPath().resolve(CACHE_FILE_NAME);
    private final Map<String, Long> playTimeByAccount = new HashMap<>();

    private String activeAccountKey;
    private long lastSampleAtMillis;
    private long lastSaveAtMillis;
    private boolean dirty;

    public KspAccountPlayTimeCache()
    {
        load();
    }

    public synchronized void sample(boolean loggedIn, long accountHash)
    {
        long now = System.currentTimeMillis();
        String accountKey = loggedIn && accountHash != 0L
                ? Long.toUnsignedString(accountHash)
                : null;

        if (accountKey == null)
        {
            activeAccountKey = null;
            lastSampleAtMillis = 0L;
            saveIfDue(now);
            return;
        }

        if (!accountKey.equals(activeAccountKey))
        {
            activeAccountKey = accountKey;
            lastSampleAtMillis = now;
            return;
        }

        if (lastSampleAtMillis > 0L && playTimeByAccount.containsKey(accountKey))
        {
            long elapsed = Math.max(0L, now - lastSampleAtMillis);
            if (elapsed > 0L)
            {
                playTimeByAccount.merge(accountKey, elapsed, Long::sum);
                dirty = true;
            }
        }

        lastSampleAtMillis = now;
        saveIfDue(now);
    }

    public synchronized long getPlayTimeMillis(long accountHash)
    {
        if (accountHash == 0L)
        {
            return 0L;
        }

        return Math.max(0L, playTimeByAccount.getOrDefault(Long.toUnsignedString(accountHash), 0L));
    }

    public synchronized boolean hasCachedPlayTime(long accountHash)
    {
        if (accountHash == 0L)
        {
            return false;
        }

        return playTimeByAccount.containsKey(Long.toUnsignedString(accountHash));
    }

    public synchronized void synchronizePlayTimeHours(long accountHash, int playTimeHours)
    {
        synchronizePlayTimeMillis(accountHash, TimeUnit.HOURS.toMillis(playTimeHours));
    }

    public synchronized void synchronizePlayTimeMillis(long accountHash, long playTimeMillis)
    {
        if (accountHash == 0L || playTimeMillis < 0L)
        {
            return;
        }

        String accountKey = Long.toUnsignedString(accountHash);
        long authoritativeMillis = playTimeMillis;
        long currentMillis = playTimeByAccount.getOrDefault(accountKey, 0L);
        if (!playTimeByAccount.containsKey(accountKey) || authoritativeMillis != currentMillis)
        {
            playTimeByAccount.put(accountKey, authoritativeMillis);
            dirty = true;
            flush();
        }
    }

    public synchronized void endSession()
    {
        activeAccountKey = null;
        lastSampleAtMillis = 0L;
        flush();
    }

    public synchronized void flush()
    {
        if (!dirty)
        {
            return;
        }

        Path temporaryPath = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
        try
        {
            Files.createDirectories(cachePath.getParent());
            try (Writer writer = Files.newBufferedWriter(temporaryPath, StandardCharsets.UTF_8))
            {
                GSON.toJson(new CacheData(playTimeByAccount), writer);
            }

            try
            {
                Files.move(
                        temporaryPath,
                        cachePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch (Exception atomicMoveFailure)
            {
                Files.move(temporaryPath, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }

            dirty = false;
            lastSaveAtMillis = System.currentTimeMillis();
        }
        catch (Exception ex)
        {
            log.warn("Unable to save KSP account play-time cache to {}: {}", cachePath, ex.getMessage());
        }
    }

    private void saveIfDue(long now)
    {
        if (dirty && now - lastSaveAtMillis >= SAVE_INTERVAL_MS)
        {
            flush();
        }
    }

    private void load()
    {
        if (!Files.exists(cachePath))
        {
            return;
        }

        try (Reader reader = Files.newBufferedReader(cachePath, StandardCharsets.UTF_8))
        {
            CacheData cacheData = GSON.fromJson(reader, CacheData.class);
            if (cacheData != null && cacheData.playTimeByAccount != null)
            {
                cacheData.playTimeByAccount.forEach((accountKey, playTimeMillis) ->
                {
                    if (accountKey != null && playTimeMillis != null && playTimeMillis >= 0L)
                    {
                        playTimeByAccount.put(accountKey, playTimeMillis);
                    }
                });
            }
        }
        catch (Exception ex)
        {
            log.warn("Unable to load KSP account play-time cache from {}: {}", cachePath, ex.getMessage());
            playTimeByAccount.clear();
        }
    }

    private static final class CacheData
    {
        private Map<String, Long> playTimeByAccount = new HashMap<>();

        private CacheData()
        {
        }

        private CacheData(Map<String, Long> playTimeByAccount)
        {
            this.playTimeByAccount = new HashMap<>(playTimeByAccount);
        }
    }
}
