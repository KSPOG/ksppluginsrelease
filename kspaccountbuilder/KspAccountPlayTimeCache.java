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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    // Runtime-only marker: once Jagex's authoritative varc was read for an account,
    // plugin/task restarts in the same loaded client reuse the synchronized cache.
    private final Set<String> authoritativeAccountsThisSession = new HashSet<>();

    // Keep the active account in primitive fields so the 250 ms sampler does not
    // allocate a new account-key String and boxed Long every tick.
    private long activeAccountHash;
    private String activeAccountKey;
    private long activePlayTimeMillis = -1L;
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
        if (!loggedIn || accountHash == 0L)
        {
            storeActiveValue();
            clearActiveAccount();
            saveIfDue(now);
            return;
        }

        if (accountHash != activeAccountHash)
        {
            storeActiveValue();
            activeAccountHash = accountHash;
            activeAccountKey = Long.toUnsignedString(accountHash);
            activePlayTimeMillis = playTimeByAccount.getOrDefault(activeAccountKey, -1L);
            lastSampleAtMillis = now;
            return;
        }

        if (lastSampleAtMillis > 0L && activePlayTimeMillis >= 0L)
        {
            long elapsed = Math.max(0L, now - lastSampleAtMillis);
            if (elapsed > 0L)
            {
                activePlayTimeMillis += elapsed;
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

        if (accountHash == activeAccountHash && activeAccountKey != null)
        {
            return Math.max(0L, activePlayTimeMillis);
        }

        return Math.max(0L, playTimeByAccount.getOrDefault(Long.toUnsignedString(accountHash), 0L));
    }

    public synchronized boolean hasCachedPlayTime(long accountHash)
    {
        if (accountHash == 0L)
        {
            return false;
        }

        if (accountHash == activeAccountHash && activeAccountKey != null)
        {
            return activePlayTimeMillis >= 0L;
        }

        return playTimeByAccount.containsKey(Long.toUnsignedString(accountHash));
    }

    public synchronized boolean hasAuthoritativePlayTimeThisSession(long accountHash)
    {
        if (accountHash == 0L)
        {
            return false;
        }

        String accountKey = accountHash == activeAccountHash && activeAccountKey != null
                ? activeAccountKey
                : Long.toUnsignedString(accountHash);
        return authoritativeAccountsThisSession.contains(accountKey);
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

        String accountKey = accountHash == activeAccountHash && activeAccountKey != null
                ? activeAccountKey
                : Long.toUnsignedString(accountHash);
        authoritativeAccountsThisSession.add(accountKey);

        if (accountHash == activeAccountHash && activeAccountKey != null)
        {
            if (activePlayTimeMillis != playTimeMillis)
            {
                activePlayTimeMillis = playTimeMillis;
                dirty = true;
            }
            lastSampleAtMillis = System.currentTimeMillis();
        }
        else
        {
            Long current = playTimeByAccount.get(accountKey);
            if (current == null || current.longValue() != playTimeMillis)
            {
                playTimeByAccount.put(accountKey, playTimeMillis);
                dirty = true;
            }
        }

        flush();
    }

    public synchronized void endSession()
    {
        storeActiveValue();
        clearActiveAccount();
        flush();
    }

    public synchronized void flush()
    {
        if (!dirty)
        {
            return;
        }

        storeActiveValue();
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

    private void storeActiveValue()
    {
        if (activeAccountKey == null || activePlayTimeMillis < 0L)
        {
            return;
        }

        Long stored = playTimeByAccount.get(activeAccountKey);
        if (stored == null || stored.longValue() != activePlayTimeMillis)
        {
            playTimeByAccount.put(activeAccountKey, activePlayTimeMillis);
        }
    }

    private void clearActiveAccount()
    {
        activeAccountHash = 0L;
        activeAccountKey = null;
        activePlayTimeMillis = -1L;
        lastSampleAtMillis = 0L;
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
                for (Map.Entry<String, Long> entry : cacheData.playTimeByAccount.entrySet())
                {
                    String accountKey = entry.getKey();
                    Long playTimeMillis = entry.getValue();
                    if (accountKey != null && playTimeMillis != null && playTimeMillis >= 0L)
                    {
                        playTimeByAccount.put(accountKey, playTimeMillis);
                    }
                }
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

        private CacheData() {}

        private CacheData(Map<String, Long> playTimeByAccount)
        {
            this.playTimeByAccount = playTimeByAccount;
        }
    }
}
