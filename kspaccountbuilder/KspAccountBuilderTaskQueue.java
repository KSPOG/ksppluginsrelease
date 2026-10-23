package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Optional ordered work plan. Completely inert when the configuration is empty,
 * preserving automatic Account Builder selection.
 *
 * Tasks use elapsed active work seconds as their explicit target. The engine
 * may still perform its existing bank/GE/resource recovery between entries.
 */
final class KspAccountBuilderTaskQueue
{
    static final int MAX_ENTRIES = 32;
    static final int DEFAULT_SECONDS = 300;
    static final String[] SUPPORTED_TASKS = {
        "WOODCUTTING", "MINING", "FISHING", "COOKING", "CRAFTING",
        "FIREMAKING", "SMITHING", "SMELTING", "MELEE", "RUNE_ESSENCE"
    };

    static final class Entry
    {
        final String taskName;
        final int durationSeconds;
        Entry(String name, int duration) { taskName = name; durationSeconds = duration; }
        @Override public String toString() { return taskName.replace('_', ' ') + " (" + durationSeconds + "s)"; }
    }

    private KspAccountBuilderTaskQueue() {}

    static List<Entry> parse(String raw)
    {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<Entry> entries = new ArrayList<>();
        for (String token : raw.split(","))
        {
            if (entries.size() >= MAX_ENTRIES) break;
            String[] parts = token.trim().split(":", -1);
            if (parts.length != 2) continue;
            String task = parts[0].trim().toUpperCase(Locale.ROOT);
            if (!supports(task)) continue;
            try
            {
                int seconds = Integer.parseInt(parts[1].trim());
                if (seconds < 30 || seconds > 86400) continue;
                entries.add(new Entry(task, seconds));
            }
            catch (NumberFormatException ignored) { /* Discard malformed user configuration. */ }
        }
        return Collections.unmodifiableList(entries);
    }

    static boolean supports(String task)
    {
        if (task == null) return false;
        for (String candidate : SUPPORTED_TASKS)
            if (candidate.equals(task)) return true;
        return false;
    }

    static String serialize(List<Entry> entries)
    {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (Entry entry : entries)
        {
            if (b.length() > 0) b.append(',');
            b.append(entry.taskName).append(':').append(entry.durationSeconds);
        }
        return b.toString();
    }

    static String append(String raw, String taskName, int seconds)
    {
        if (!supports(taskName) || seconds < 30 || seconds > 86400) return raw == null ? "" : raw;
        List<Entry> out = new ArrayList<>(parse(raw));
        if (out.size() >= MAX_ENTRIES) return serialize(out);
        out.add(new Entry(taskName, seconds));
        return serialize(out);
    }

    static String move(String raw, int selected, int direction)
    {
        List<Entry> out = new ArrayList<>(parse(raw));
        int next = selected + direction;
        if (selected < 0 || selected >= out.size() || next < 0 || next >= out.size()) return serialize(out);
        Collections.swap(out, selected, next);
        return serialize(out);
    }

    static String remove(String raw, int selected)
    {
        List<Entry> out = new ArrayList<>(parse(raw));
        if (selected >= 0 && selected < out.size()) out.remove(selected);
        return serialize(out);
    }
}
