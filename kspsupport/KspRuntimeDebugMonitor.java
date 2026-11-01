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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Central low-overhead diagnostics for KSP plugins. */
final class KspRuntimeDebugMonitor
{
    private static final Logger log = LoggerFactory.getLogger("KSP.RuntimeDebug");
    private static final long POLL_MS = 2_000L;
    private static final long HEARTBEAT_MS = 30_000L;
    private static final long STALL_CONFIRM_MS = 120_000L;
    private static final long STALL_SAMPLE_MS = 5_000L;
    private static final long STALL_GRACE_MS = 15_000L;
    private static final long SLOW_SCAN_WARN_MS = 50L;
    private static final long SLOW_SCAN_WARN_COOLDOWN_MS = 10_000L;
    private static final int MAX_VALUE_LENGTH = 120;
    private static final int MAX_CHANGED_FIELDS = 8;

    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private final PluginManager pluginManager;
    private final KspDebugPanel panel;
    private final Map<String, Snapshot> previous = new HashMap<>();
    private final Map<String, Long> lastHeartbeat = new HashMap<>();
    private final Map<String, String> duplicateSignatures = new HashMap<>();
    private final Map<String, StallObservation> stallObservations = new HashMap<>();
    private String visibleStallKey;
    private long lastStallSampleAt;

    private ScheduledExecutorService executor;
    private String previousGlobalStatus;
    private long lastSlowScanWarnAt;

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
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        executor.scheduleWithFixedDelay(this::scanSafely, 0L, POLL_MS, TimeUnit.MILLISECONDS);
        info("runtime monitor started | poll=" + POLL_MS + "ms heartbeat=" + HEARTBEAT_MS + "ms | active-only deep scan=true");
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
        stallObservations.clear();
        visibleStallKey = null;
        lastStallSampleAt = 0L;
        if (panel != null) panel.showStall(null, null, null);
        previousGlobalStatus = null;
        info("runtime monitor stopped");
        if (panel != null) panel.disposeConsole();
    }

    private void scanSafely()
    {
        long started = System.nanoTime();
        try
        {
            scan();
        }
        catch (Throwable t)
        {
            log.warn("[KSP-DBG] monitor scan failed", t);
            warn("monitor scan failed | " + t.getClass().getSimpleName() + ": " + safeText(t.getMessage()));
        }
        finally
        {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            long now = System.currentTimeMillis();
            if (elapsedMs >= SLOW_SCAN_WARN_MS && now - lastSlowScanWarnAt >= SLOW_SCAN_WARN_COOLDOWN_MS)
            {
                lastSlowScanWarnAt = now;
                warn("slow debug scan | elapsed=" + elapsedMs + "ms");
            }
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
        List<Heartbeat> heartbeats = new ArrayList<>();
        int activeCount = 0;
        // Take one small shared client snapshot, never one per plugin.
        GameProgress gameProgress = null;
        boolean sampleStalls = now - lastStallSampleAt >= STALL_SAMPLE_MS;
        if (sampleStalls)
        {
            lastStallSampleAt = now;
            gameProgress = clientProgressSnapshot();
        }

        for (Plugin plugin : loaded)
        {
            if (plugin instanceof KspSupportPlugin || !isKspPlugin(plugin)) continue;

            String key = instanceKey(plugin);
            seen.add(key);

            boolean enabled = safeEnabled(plugin);
            boolean active = safeActive(plugin);
            Snapshot old = previous.get(key);
            Snapshot current = active
                    ? snapshotActive(plugin, enabled)
                    : new Snapshot(descriptorName(plugin), enabled, false, Collections.emptyMap());
            previous.put(key, current);
            if (sampleStalls) updateStallObservation(key, current, gameProgress, now);

            if (active)
            {
                activeCount++;
                activeInstancesByClass.computeIfAbsent(plugin.getClass().getName(), k -> new ArrayList<>()).add(key);
            }

            if (old == null)
            {
                if (enabled || active)
                    info("discovered | plugin=" + current.pluginName + " | enabled=" + enabled + " | active=" + active);
            }
            else
            {
                if (old.enabled != enabled || old.active != active)
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
                heartbeats.add(new Heartbeat(current.pluginName, compactState(current.values)));
            }
        }

        logGlobalStatusOnce();

        String client = heartbeats.isEmpty() ? null : clientSnapshot();
        for (Heartbeat heartbeat : heartbeats)
            info("heartbeat | plugin=" + heartbeat.pluginName + " | " + heartbeat.state + " | client=" + client);

        for (String oldKey : new ArrayList<>(previous.keySet()))
        {
            if (seen.contains(oldKey)) continue;
            Snapshot removed = previous.remove(oldKey);
            lastHeartbeat.remove(oldKey);
            if (removed != null) info("unloaded | plugin=" + removed.pluginName);
        }

        if (panel != null) panel.setActiveCount(activeCount);
        stallObservations.keySet().removeIf(key -> !seen.contains(key));
        publishStall();
        checkDuplicateInstances(activeInstancesByClass);
    }

    /**
     * Generic stall detection for running KSP script workers.
     * An enabled but idle plugin is never considered stalled. Normal movement,
     * animation, XP changes and declared operational state transitions reset
     * the timer. Waiting for more than two minutes is a suspected stall, not
     * conclusive evidence that an action failed.
     */
    private void updateStallObservation(String key, Snapshot current, String gameProgress, long now)
    {
        if (!current.active || gameProgress == null
                || !current.values.containsKey("activeScripts")
                || current.pluginName.toLowerCase(Locale.ROOT).contains("account builder"))
        {
            stallObservations.remove(key);
            return;
        }
        String signature = gameProgress.signature + "|" + compactState(current.values);
        StallObservation old = stallObservations.get(key);
        if (old == null || !old.signature.equals(signature))
        {
            stallObservations.put(key, new StallObservation(current.pluginName, signature, now));
            return;
        }
        // An active animation or interaction is not an idle stall.
        // Avoid false reports during legitimately long actions.
        if (gameProgress.busy)
        {
            old.lastProgressAt = now;
            return;
        }
        if (now - old.lastProgressAt < STALL_CONFIRM_MS) return;
        if (old.code == null)
        {
            String token = current.pluginName.toUpperCase(Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", "-").replaceAll("(^-|-$)", "");
            if (token.startsWith("KSP-")) token = token.substring(4);
            old.code = "KSP-" + token + "-STALL-E01";
            old.reason = "Running script has no observable movement, XP or state change for 120 seconds";
            warn("suspected stall | plugin=" + current.pluginName + " | code=" + old.code);
        }
    }

    private void publishStall()
    {
        StallObservation visible = null;
        String visibleKey = null;
        for (Map.Entry<String, StallObservation> entry : stallObservations.entrySet())
        {
            if (entry.getValue().code != null)
            {
                visible = entry.getValue();
                visibleKey = entry.getKey();
                break;
            }
        }
        if (java.util.Objects.equals(visibleStallKey, visibleKey)) return;
        visibleStallKey = visibleKey;
        if (panel != null)
            panel.showStall(visible == null ? null : visible.plugin,
                    visible == null ? null : visible.code,
                    visible == null ? null : visible.reason);
    }

    private static GameProgress clientProgressSnapshot()
    {
        try
        {
            return Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null
                        || !Microbot.isLoggedIn()) return null;
                Player player = Microbot.getClient().getLocalPlayer();
                long totalXp = 0L;
                for (net.runelite.api.Skill skill : net.runelite.api.Skill.values())
                    totalXp += Math.max(0, Microbot.getClient().getSkillExperience(skill));
                // Do not use global status or system time: neither indicates
                // actual in-game progress and would mask a genuine stall.
                boolean busy = player.getAnimation() != -1 || player.getInteracting() != null
                        || player.getPoseAnimation() != player.getIdlePoseAnimation();
                return new GameProgress(player.getWorldLocation() + "|" + totalXp, busy);
            }).orElse(null);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static final class GameProgress
    {
        final String signature;
        final boolean busy;

        GameProgress(String signature, boolean busy)
        {
            this.signature = signature;
            this.busy = busy;
        }
    }

    private static final class StallObservation
    {
        final String plugin;
        final String signature;
        long lastProgressAt;
        String code, reason;

        StallObservation(String plugin, String signature, long lastProgressAt)
        {
            this.plugin = plugin;
            this.signature = signature;
            this.lastProgressAt = lastProgressAt;
        }
    }

    /** Microbot.status is global, so log it once instead of once per active plugin. */
    private void logGlobalStatusOnce()
    {
        String current = safeText(Microbot.status);
        if (java.util.Objects.equals(previousGlobalStatus, current)) return;
        previousGlobalStatus = current;
        if (!current.isEmpty() && !"null".equals(current))
            info("state-change | plugin=Client | changed=status=" + current);
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

    /** Expensive reflection is only performed for active KSP plugins. */
    private Snapshot snapshotActive(Plugin plugin, boolean enabled)
    {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> activeScripts = new ArrayList<>();

        collectOperationalFields(plugin, "plugin", values);

        for (Field field : allFields(plugin.getClass()))
        {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Object value = read(field, plugin);
            if (!(value instanceof Script)) continue;

            Script script = (Script) value;
            boolean running;
            try { running = script.isRunning(); }
            catch (Throwable ignored) { running = false; }
            if (!running) continue;

            String scriptName = script.getClass().getSimpleName();
            activeScripts.add(scriptName);
            collectOperationalFields(script, scriptName, values);
        }

        activeScripts.sort(Comparator.naturalOrder());
        if (!activeScripts.isEmpty()) values.put("activeScripts", String.join(",", activeScripts));
        return new Snapshot(descriptorName(plugin), enabled, true, values);
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

    private static List<Field> allFields(Class<?> type)
    {
        return FIELD_CACHE.computeIfAbsent(type, KspRuntimeDebugMonitor::discoverFields);
    }

    private static List<Field> discoverFields(Class<?> type)
    {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
        {
            try
            {
                for (Field field : c.getDeclaredFields())
                {
                    try { field.setAccessible(true); }
                    catch (Throwable ignored) { }
                    fields.add(field);
                }
            }
            catch (Throwable ignored) { }
        }
        return Collections.unmodifiableList(fields);
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
        if (preferred.size() < 4)
        {
            for (Map.Entry<String, String> entry : values.entrySet())
            {
                String lower = entry.getKey().toLowerCase(Locale.ROOT);
                if (!(lower.contains("state") || lower.contains("task") || lower.contains("stage") || lower.contains("status"))) continue;
                preferred.add(shortKey(entry.getKey()) + "=" + entry.getValue());
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
        return key.startsWith("plugin.") ? key.substring(7) : key;
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
        try { return field.get(owner); }
        catch (Throwable ignored) { return null; }
    }

    private boolean isKspPlugin(Plugin plugin)
    {
        if (plugin == null) return false;

        String className = plugin.getClass().getName().toLowerCase(Locale.ROOT);
        if (className.startsWith("net.runelite.client.plugins.microbot.ksp")) return true;

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
                ? plugin.getClass().getSimpleName() : descriptor.name();
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
        catch (Throwable t)
        {
            return "client-snapshot-error=" + t.getClass().getSimpleName();
        }
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

    private static final class Heartbeat
    {
        final String pluginName, state;

        Heartbeat(String pluginName, String state)
        {
            this.pluginName = pluginName;
            this.state = state;
        }
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
