package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public final class KspTaskDebug
{
    private static final ConcurrentHashMap<String, Long> LAST_LOG_AT = new ConcurrentHashMap<>();

    private KspTaskDebug()
    {
    }

    public static void info(Logger log, boolean enabled, String taskName, String message, Object... args)
    {
        if (!enabled)
        {
            return;
        }

        log.info("[KSP {}] " + message, prependTaskName(taskName, args));
    }

    public static void throttled(Logger log, boolean enabled, String taskName, String key, long intervalMs, String message, Object... args)
    {
        if (!enabled)
        {
            return;
        }

        long now = System.currentTimeMillis();
        String throttleKey = taskName + ':' + key;
        Long lastLogAt = LAST_LOG_AT.get(throttleKey);

        if (lastLogAt != null && now - lastLogAt < intervalMs)
        {
            return;
        }

        LAST_LOG_AT.put(throttleKey, now);
        info(log, true, taskName, message, args);
    }

    private static Object[] prependTaskName(String taskName, Object[] args)
    {
        Object[] prefixed = new Object[args.length + 1];
        prefixed[0] = taskName;
        System.arraycopy(args, 0, prefixed, 1, args.length);
        return prefixed;
    }
}
