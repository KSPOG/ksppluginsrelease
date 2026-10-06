package net.runelite.client.plugins.microbot.kspsupport;

import net.runelite.api.Player;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central diagnostics for every source-loaded KSP plugin.
 *
 * Logs lifecycle/state transitions immediately and a low-frequency heartbeat for
 * active plugins. It deliberately avoids per-tick logging and skips fields whose
 * names look credential/account related.
 */
final class KspRuntimeDebugMonitor
{
    private static final Logger log = LoggerFactory.getLogger("KSP.RuntimeDebug");
    private static final long POLL_MS = 2_000L;
    private static final long HEARTBEAT_MS = 30_000L;
    private static final int MAX_VALUE_LENGTH = 140;

    private final PluginManager pluginManager;
    private final Map<String, Snapshot> previous = new HashMap<>();
    private final Map<String, Long> lastHeartbeat = new HashMap<>();
    private final Map<String, String> duplicateSignatures = new HashMap<>();
    private ScheduledExecutorService executor;

    KspRuntimeDebugMonitor(PluginManager pluginManager)
    {
        this.pluginManager = pluginManager;
    }

    synchronized void start()
    {
        if (executor != null && !executor.isShutdown()) return;
        executor = Executors.newSingleThreadScheduledExecutor(r ->
        {
            Thread t = new Thread(r, "ksp-runtime-debug");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::scanSafely, 0L, POLL_MS, TimeUnit.MILLISECONDS);
        log.info("[KSP-DBG] runtime monitor started | poll={}ms heartbeat={}ms", POLL_MS, HEARTBEAT_MS);
    }

    synchronized void stop()
    {
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
        previous.clear();
        lastHeartbeat.clear();
        duplicateSignatures.clear();
        log.info("[KSP-DBG] runtime monitor stopped");
    }

    private void scanSafely()
    {
        try
        {
            scan();
        }
        catch (Throwable t)
        {
            log.warn("[KSP-DBG] monitor scan failed", t);
        }
    }

    private void scan()
    {
        if (pluginManager == null) return;

        Collection<Plugin> loaded = pluginManager.getPlugins();
        if (loaded == null) return;

        long now = System.currentTimeMillis();
        Set<String> seen = new HashSet<>();
        Map<String, List<String>> activeInstancesByClass = new HashMap<>();

        for (Plugin plugin : loaded)
        {
            if (!isKspPlugin(plugin)) continue;

            String key = instanceKey(plugin);
            seen.add(key);

            boolean enabled = safeEnabled(plugin);
            boolean active = safeActive(plugin);
            Snapshot current = snapshot(plugin, enabled, active);
            Snapshot old = previous.put(key, current);

            if (active)
                activeInstancesByClass.computeIfAbsent(plugin.getClass().getName(), k -> new ArrayList<>()).add(key);

            if (old == null)
            {
                log.info("[KSP-DBG] discovered | {}", current.describe());
            }
            else
            {
                if (old.enabled != current.enabled || old.active != current.active)
                    log.info("[KSP-DBG] lifecycle | {}", current.describe());

                if (current.active && !old.state.equals(current.state))
                    log.info("[KSP-DBG] state-change | plugin={} instance={} {} -> {}",
                            current.pluginName, current.instanceId, emptyAsDash(old.state), emptyAsDash(current.state));
            }

            if (current.active && now - lastHeartbeat.getOrDefault(key, 0L) >= HEARTBEAT_MS)
            {
                lastHeartbeat.put(key, now);
                log.info("[KSP-DBG] heartbeat | {} | client={}", current.describe(), clientSnapshot());
            }
        }

        for (String oldKey : new ArrayList<>(previous.keySet()))
        {
            if (seen.contains(oldKey)) continue;
            Snapshot removed = previous.remove(oldKey);
            lastHeartbeat.remove(oldKey);
            if (removed != null)
                log.info("[KSP-DBG] unloaded | plugin={} instance={}", removed.pluginName, removed.instanceId);
        }

        checkDuplicateInstances(activeInstancesByClass);
    }

    private void checkDuplicateInstances(Map<String, List<String>> activeByClass)
    {
        Set<String> classes = new HashSet<>(duplicateSignatures.keySet());
        classes.addAll(activeByClass.keySet());

        for (String className : classes)
        {
            List<String> instances = activeByClass.getOrDefault(className, Collections.emptyList());
            Collections.sort(instances);
            String signature = String.join(",", instances);
            String previousSignature = duplicateSignatures.get(className);

            if (instances.size() > 1 && !signature.equals(previousSignature))
            {
                log.warn("[KSP-DBG] DUPLICATE ACTIVE PLUGIN INSTANCES | class={} count={} instances={}",
                        className, instances.size(), instances);
            }
            else if (instances.size() <= 1 && previousSignature != null && previousSignature.contains(","))
            {
                log.info("[KSP-DBG] duplicate cleared | class={} activeInstances={}", className, instances.size());
            }

            if (signature.isEmpty()) duplicateSignatures.remove(className);
            else duplicateSignatures.put(className, signature);
        }
    }

    private Snapshot snapshot(Plugin plugin, boolean enabled, boolean active)
    {
        String pluginName = descriptorName(plugin);
        String instanceId = Integer.toHexString(System.identityHashCode(plugin));
        Map<String, String> values = new LinkedHashMap<>();
        List<String> activeScripts = new ArrayList<>();

        collectOperationalFields(plugin, "plugin", values);

        for (Field field : allFields(plugin.getClass()))
        {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Object value = read(field, plugin);
            if (!(value instanceof Script)) continue;

            Script script = (Script) value;
            String scriptName = script.getClass().getSimpleName();
            boolean running = false;
            try { running = script.isRunning(); }
            catch (Throwable ignored) { }

            if (running) activeScripts.add(scriptName);
            values.put("script." + scriptName + ".running", Boolean.toString(running));
            collectOperationalFields(script, "script." + scriptName, values);
        }

        activeScripts.sort(Comparator.naturalOrder());
        if (!activeScripts.isEmpty()) values.put("activeScripts", String.join(",", activeScripts));

        String state = join(values);
        return new Snapshot(pluginName, plugin.getClass().getName(), instanceId, enabled, active, state);
    }

    private void collectOperationalFields(Object owner, String prefix, Map<String, String> out)
    {
        if (owner == null) return;

        for (Field field : allFields(owner.getClass()))
        {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            String name = field.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!isOperationalField(lower) || isSensitiveField(lower)) continue;

            Object value = read(field, owner);
            if (!isSimpleValue(value)) continue;
            out.put(prefix + "." + name, safeText(value));
        }
    }

    private static boolean isOperationalField(String name)
    {
        return name.contains("state")
                || name.contains("status")
                || name.contains("task")
                || name.contains("step")
                || name.contains("stage")
                || name.contains("phase")
                || name.contains("mode")
                || name.contains("action")
                || name.contains("targetarea")
                || name.contains("currentarea");
    }

    private static boolean isSensitiveField(String name)
    {
        return name.contains("pass")
                || name.contains("token")
                || name.contains("secret")
                || name.contains("apikey")
                || name.contains("api_key")
                || name.contains("email")
                || name.contains("username")
                || name.contains("account")
                || name.contains("sessionid")
                || name.contains("cookie");
    }

    private static boolean isSimpleValue(Object value)
    {
        return value == null
                || value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum;
    }

    private static String safeText(Object value)
    {
        if (value == null) return "null";
        String text;
        try { text = String.valueOf(value); }
        catch (Throwable ignored) { return "<unavailable>"; }
        text = text.replace('\n', ' ').replace('\r', ' ');
        return text.length() <= MAX_VALUE_LENGTH ? text : text.substring(0, MAX_VALUE_LENGTH) + "...";
    }

    private static Object read(Field field, Object owner)
    {
        try
        {
            if (!field.isAccessible()) field.setAccessible(true);
            return field.get(owner);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static List<Field> allFields(Class<?> type)
    {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
        {
            try { Collections.addAll(fields, c.getDeclaredFields()); }
            catch (Throwable ignored) { }
        }
        return fields;
    }

    private boolean isKspPlugin(Plugin plugin)
    {
        if (plugin == null) return false;

        ClassLoader loader = plugin.getClass().getClassLoader();
        if (loader != null && loader.getClass().getSimpleName().contains("MemoryPluginClassLoader")) return true;

        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null) return false;
        for (String author : descriptor.authors())
            if (author != null && author.toLowerCase(Locale.ROOT).contains("ksp")) return true;
        return descriptor.name() != null && descriptor.name().toLowerCase(Locale.ROOT).contains("ksp");
    }

    private boolean safeEnabled(Plugin plugin)
    {
        try { return pluginManager.isPluginEnabled(plugin); }
        catch (Throwable ignored) { return false; }
    }

    private boolean safeActive(Plugin plugin)
    {
        try { return pluginManager.isActive(plugin); }
        catch (Throwable ignored) { return safeEnabled(plugin); }
    }

    private static String descriptorName(Plugin plugin)
    {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        return descriptor == null || descriptor.name() == null || descriptor.name().isEmpty()
                ? plugin.getClass().getSimpleName()
                : descriptor.name();
    }

    private static String instanceKey(Plugin plugin)
    {
        return plugin.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(plugin));
    }

    private static String join(Map<String, String> values)
    {
        if (values.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet())
        {
            if (out.length() > 0) out.append(" | ");
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    private static String clientSnapshot()
    {
        try
        {
            return Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                if (Microbot.getClient() == null) return "client=null";
                Player player = Microbot.getClient().getLocalPlayer();
                if (player == null) return "loggedIn=false status=" + safeText(Microbot.status);
                return "loc=" + player.getWorldLocation()
                        + " plane=" + Microbot.getClient().getPlane()
                        + " anim=" + player.getAnimation()
                        + " moving=" + (player.getPoseAnimation() != player.getIdlePoseAnimation())
                        + " status=" + safeText(Microbot.status);
            }).orElse("client-thread-unavailable");
        }
        catch (Throwable t)
        {
            return "client-snapshot-error=" + t.getClass().getSimpleName();
        }
    }

    private static String emptyAsDash(String value)
    {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private static final class Snapshot
    {
        final String pluginName;
        final String className;
        final String instanceId;
        final boolean enabled;
        final boolean active;
        final String state;

        Snapshot(String pluginName, String className, String instanceId, boolean enabled, boolean active, String state)
        {
            this.pluginName = pluginName;
            this.className = className;
            this.instanceId = instanceId;
            this.enabled = enabled;
            this.active = active;
            this.state = state == null ? "" : state;
        }

        String describe()
        {
            return "plugin=" + pluginName
                    + " class=" + className
                    + " instance=" + instanceId
                    + " enabled=" + enabled
                    + " active=" + active
                    + (state.isEmpty() ? "" : " | " + state);
        }
    }
}
