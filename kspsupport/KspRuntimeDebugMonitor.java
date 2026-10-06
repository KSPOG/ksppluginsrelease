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

/** Central low-noise diagnostics for every source-loaded KSP plugin. */
final class KspRuntimeDebugMonitor
{
    private static final Logger log = LoggerFactory.getLogger("KSP.RuntimeDebug");
    private static final long POLL_MS = 2_000L;
    private static final long HEARTBEAT_MS = 30_000L;
    private static final int MAX_VALUE_LENGTH = 120;
    private static final int MAX_CHANGED_FIELDS = 8;

    private final PluginManager pluginManager;
    private final KspDebugPanel panel;
    private final Map<String, Snapshot> previous = new HashMap<>();
    private final Map<String, Long> lastHeartbeat = new HashMap<>();
    private final Map<String, String> duplicateSignatures = new HashMap<>();
    private ScheduledExecutorService executor;

    KspRuntimeDebugMonitor(PluginManager pluginManager, KspDebugPanel panel)
    {
        this.pluginManager = pluginManager;
        this.panel = panel;
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
        info("runtime monitor started | poll=" + POLL_MS + "ms heartbeat=" + HEARTBEAT_MS + "ms");
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
        info("runtime monitor stopped");
        if (panel != null) panel.disposeConsole();
    }

    private void scanSafely()
    {
        try { scan(); }
        catch (Throwable t)
        {
            log.warn("[KSP-DBG] monitor scan failed", t);
            warn("monitor scan failed | " + t.getClass().getSimpleName() + ": " + safeText(t.getMessage()));
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
        int activeCount = 0;

        for (Plugin plugin : loaded)
        {
            if (plugin instanceof KspSupportPlugin || !isKspPlugin(plugin)) continue;

            String key = instanceKey(plugin);
            seen.add(key);

            boolean enabled = safeEnabled(plugin);
            boolean active = safeActive(plugin);
            Snapshot current = snapshot(plugin, enabled, active);
            Snapshot old = previous.put(key, current);

            if (active)
            {
                activeCount++;
                activeInstancesByClass.computeIfAbsent(plugin.getClass().getName(), k -> new ArrayList<>()).add(key);
            }

            if (old == null)
                info("discovered | plugin=" + current.pluginName + " | enabled=" + enabled + " | active=" + active);
            else
            {
                if (old.enabled != current.enabled || old.active != current.active)
                    info("lifecycle | plugin=" + current.pluginName + " | enabled=" + enabled + " | active=" + active);

                if (active)
                {
                    String changes = changedFields(old.values, current.values);
                    if (!changes.isEmpty())
                        info("state-change | plugin=" + current.pluginName + " | changed=" + changes);
                }
            }

            if (active && now - lastHeartbeat.getOrDefault(key, 0L) >= HEARTBEAT_MS)
            {
                lastHeartbeat.put(key, now);
                info("heartbeat | plugin=" + current.pluginName + " | " + compactState(current.values)
                        + " | client=" + clientSnapshot());
            }
        }

        for (String oldKey : new ArrayList<>(previous.keySet()))
        {
            if (seen.contains(oldKey)) continue;
            Snapshot removed = previous.remove(oldKey);
            lastHeartbeat.remove(oldKey);
            if (removed != null) info("unloaded | plugin=" + removed.pluginName);
        }

        if (panel != null) panel.setActiveCount(activeCount);
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
            String old = duplicateSignatures.get(className);

            if (instances.size() > 1 && !signature.equals(old))
                warn("DUPLICATE ACTIVE PLUGIN INSTANCES | class=" + className + " | count=" + instances.size());
            else if (instances.size() <= 1 && old != null && old.contains(","))
                info("duplicate cleared | class=" + className + " | activeInstances=" + instances.size());

            if (signature.isEmpty()) duplicateSignatures.remove(className);
            else duplicateSignatures.put(className, signature);
        }
    }

    private Snapshot snapshot(Plugin plugin, boolean enabled, boolean active)
    {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> activeScripts = new ArrayList<>();

        collectOperationalFields(plugin, "plugin", values);
        if (active) values.put("status", safeText(Microbot.status));

        for (Field field : allFields(plugin.getClass()))
        {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Object value = read(field, plugin);
            if (!(value instanceof Script)) continue;

            Script script = (Script) value;
            String scriptName = script.getClass().getSimpleName();
            boolean running = false;
            try { running = script.isRunning(); } catch (Throwable ignored) { }

            values.put(scriptName + ".running", Boolean.toString(running));
            if (running) activeScripts.add(scriptName);
            collectOperationalFields(script, scriptName, values);
        }

        activeScripts.sort(Comparator.naturalOrder());
        if (!activeScripts.isEmpty()) values.put("activeScripts", String.join(",", activeScripts));

        return new Snapshot(descriptorName(plugin), enabled, active, values);
    }

    private void collectOperationalFields(Object owner, String prefix, Map<String, String> out)
    {
        if (owner == null) return;
        for (Field field : allFields(owner.getClass()))
        {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            String lower = field.getName().toLowerCase(Locale.ROOT);
            if (!isOperationalField(lower) || isSensitiveField(lower)) continue;

            Object value = read(field, owner);
            if (!isSimpleValue(value)) continue;
            out.put(prefix + "." + field.getName(), safeText(value));
        }
    }

    private static String changedFields(Map<String, String> oldValues, Map<String, String> newValues)
    {
        List<String> changes = new ArrayList<>();
        Set<String> keys = new HashSet<>(oldValues.keySet());
        keys.addAll(newValues.keySet());
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);

        for (String key : sorted)
        {
            String before = oldValues.get(key);
            String after = newValues.get(key);
            if (java.util.Objects.equals(before, after)) continue;
            changes.add(shortKey(key) + "=" + safeText(after));
            if (changes.size() >= MAX_CHANGED_FIELDS) break;
        }
        return String.join("; ", changes);
    }

    private static String compactState(Map<String, String> values)
    {
        List<String> preferred = new ArrayList<>();
        addIfPresent(preferred, values, "activeScripts");
        addIfPresent(preferred, values, "status");

        if (preferred.size() < 4)
        {
            for (Map.Entry<String, String> e : values.entrySet())
            {
                String lower = e.getKey().toLowerCase(Locale.ROOT);
                if (!(lower.contains("state") || lower.contains("task") || lower.contains("stage"))) continue;
                preferred.add(shortKey(e.getKey()) + "=" + e.getValue());
                if (preferred.size() >= 4) break;
            }
        }
        return preferred.isEmpty() ? "state=-" : String.join(" | ", preferred);
    }

    private static void addIfPresent(List<String> out, Map<String, String> values, String key)
    {
        String value = values.get(key);
        if (value != null && !value.isEmpty()) out.add(key + "=" + value);
    }

    private static String shortKey(String key)
    {
        if (key == null) return "?";
        if (key.startsWith("plugin.")) return key.substring(7);
        return key;
    }

    private static boolean isOperationalField(String name)
    {
        return name.contains("state") || name.contains("status") || name.contains("task")
                || name.contains("step") || name.contains("stage") || name.contains("phase")
                || name.contains("mode") || name.contains("action")
                || name.contains("targetarea") || name.contains("currentarea");
    }

    private static boolean isSensitiveField(String name)
    {
        return name.contains("pass") || name.contains("token") || name.contains("secret")
                || name.contains("apikey") || name.contains("api_key") || name.contains("email")
                || name.contains("username") || name.contains("account") || name.contains("sessionid")
                || name.contains("cookie");
    }

    private static boolean isSimpleValue(Object value)
    {
        return value == null || value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Enum;
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
        catch (Throwable ignored) { return null; }
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
        if (loader != null)
        {
            String name = loader.getClass().getSimpleName();
            if (name.contains("MemoryPluginClassLoader") || name.contains("PluginJarClassLoader")) return true;
        }

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
        PluginDescriptor d = plugin.getClass().getAnnotation(PluginDescriptor.class);
        return d == null || d.name() == null || d.name().isEmpty() ? plugin.getClass().getSimpleName() : d.name();
    }

    private static String instanceKey(Plugin plugin)
    {
        return plugin.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(plugin));
    }

    private static String clientSnapshot()
    {
        try
        {
            return Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                if (Microbot.getClient() == null) return "client=null";
                Player player = Microbot.getClient().getLocalPlayer();
                if (player == null) return "loggedIn=false";
                return "loc=" + player.getWorldLocation()
                        + " anim=" + player.getAnimation()
                        + " moving=" + (player.getPoseAnimation() != player.getIdlePoseAnimation());
            }).orElse("client-thread-unavailable");
        }
        catch (Throwable t) { return "client-snapshot-error=" + t.getClass().getSimpleName(); }
    }

    private void info(String message)
    {
        log.info("[KSP-DBG] {}", message);
        if (panel != null) panel.append("INFO", "[KSP-DBG] " + message);
    }

    private void warn(String message)
    {
        log.warn("[KSP-DBG] {}", message);
        if (panel != null) panel.append("WARN", "[KSP-DBG] " + message);
    }

    private static final class Snapshot
    {
        final String pluginName;
        final boolean enabled, active;
        final Map<String, String> values;

        Snapshot(String pluginName, boolean enabled, boolean active, Map<String, String> values)
        {
            this.pluginName = pluginName;
            this.enabled = enabled;
            this.active = active;
            this.values = values;
        }
    }
}
