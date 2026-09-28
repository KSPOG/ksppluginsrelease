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

@Singleton
@Slf4j
public class KspAccountTaskCache
{
    public enum OneTimeTask
    {
        STRONGHOLD_OF_SECURITY,
        ROMEO_AND_JULIET,
        RUNE_MYSTERIES
    }

    private static final String CACHE_FILE_NAME = "ksp-account-builder-task-state.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path cachePath = RuneLite.RUNELITE_DIR.toPath().resolve(CACHE_FILE_NAME);
    private final Map<String, Map<String, Boolean>> taskStateByAccount = new HashMap<>();

    public KspAccountTaskCache()
    {
        load();
    }

    public synchronized boolean isCompleted(long accountHash, OneTimeTask task)
    {
        if (accountHash == 0L || task == null)
        {
            return false;
        }

        Map<String, Boolean> taskStates = taskStateByAccount.get(Long.toUnsignedString(accountHash));
        return taskStates != null && Boolean.TRUE.equals(taskStates.get(task.name()));
    }

    public synchronized void setCompleted(long accountHash, OneTimeTask task, boolean completed)
    {
        if (accountHash == 0L || task == null)
        {
            return;
        }

        String accountKey = Long.toUnsignedString(accountHash);
        Map<String, Boolean> taskStates = taskStateByAccount.computeIfAbsent(
                accountKey,
                ignored -> new HashMap<>());
        Boolean previous = taskStates.put(task.name(), completed);
        if (previous == null || previous != completed)
        {
            flush();
        }
    }

    private void flush()
    {
        Path temporaryPath = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
        try
        {
            Files.createDirectories(cachePath.getParent());
            try (Writer writer = Files.newBufferedWriter(temporaryPath, StandardCharsets.UTF_8))
            {
                GSON.toJson(new CacheData(taskStateByAccount), writer);
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
        }
        catch (Exception ex)
        {
            log.warn("Unable to save KSP account task cache to {}: {}", cachePath, ex.getMessage());
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
            if (cacheData == null || cacheData.taskStateByAccount == null)
            {
                return;
            }

            cacheData.taskStateByAccount.forEach((accountKey, taskStates) ->
            {
                if (accountKey == null || taskStates == null)
                {
                    return;
                }

                Map<String, Boolean> validTaskStates = new HashMap<>();
                taskStates.forEach((taskName, completed) ->
                {
                    if (taskName != null && completed != null)
                    {
                        validTaskStates.put(taskName, completed);
                    }
                });
                taskStateByAccount.put(accountKey, validTaskStates);
            });
        }
        catch (Exception ex)
        {
            log.warn("Unable to load KSP account task cache from {}: {}", cachePath, ex.getMessage());
            taskStateByAccount.clear();
        }
    }

    private static final class CacheData
    {
        private Map<String, Map<String, Boolean>> taskStateByAccount = new HashMap<>();

        private CacheData()
        {
        }

        private CacheData(Map<String, Map<String, Boolean>> taskStateByAccount)
        {
            taskStateByAccount.forEach((accountKey, taskStates) ->
                    this.taskStateByAccount.put(accountKey, new HashMap<>(taskStates)));
        }
    }
}
