package net.runelite.client.plugins.microbot.kspsupport;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bounded on-demand client.log extraction. Never called from the Swing EDT. */
final class KspSupportReportLog
{
    private static final int MAX_READ_BYTES = 128 * 1024;
    private static final int MAX_LINES = 100;

    private KspSupportReportLog() {}

    static String capture(String pluginName)
    {
        File file = new File(new File(new File(System.getProperty("user.home"), ".runelite"), "logs"), "client.log");
        if (!file.isFile()) return "(client.log unavailable)";
        try (RandomAccessFile input = new RandomAccessFile(file, "r"))
        {
            long size = input.length();
            int count = (int) Math.min(MAX_READ_BYTES, size);
            byte[] bytes = new byte[count];
            input.seek(size - count);
            input.readFully(bytes);
            String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\\R");
            List<String> matches = new ArrayList<>();
            String query = pluginName == null ? "" : pluginName.toLowerCase(java.util.Locale.ROOT);
            boolean trace = false;
            for (int i = size > count ? 1 : 0; i < lines.length; i++)
            {
                String line = lines[i];
                String lower = line.toLowerCase(java.util.Locale.ROOT);
                boolean relevant = (!query.isEmpty() && lower.contains(query))
                        || line.contains("[KSP") || line.contains("kspsupport")
                        || line.contains("[WebWalk]") || line.contains("[Walker]");
                boolean stack = trace && (line.startsWith("    at ")
                        || line.startsWith("\t") || line.startsWith("Caused by:"));
                if (relevant || stack)
                {
                    matches.add(line);
                    trace = relevant;
                }
                else if (!line.trim().isEmpty()) trace = false;
            }
            if (matches.isEmpty()) return "(no matching recent client.log entries)";
            return String.join("\n", matches.subList(Math.max(0, matches.size() - MAX_LINES), matches.size()));
        }
        catch (IOException | SecurityException ex)
        {
            return "(client.log read failed: " + ex.getClass().getSimpleName() + ")";
        }
    }
}
