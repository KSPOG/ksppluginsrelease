package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLConnection;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.runelite.api.Experience;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * The approved Arcane Codex artwork is an actual layered background image;
 * all text, progress, tasks and RuneLite item sprites are drawn live on top.
 * No generated statistics, simulated screenshots or Java2D substitute frames.
 */
final class KspArcaneCodexRenderer
{
    private static final Logger LOG = LoggerFactory.getLogger(KspArcaneCodexRenderer.class);
    private static final String ARTWORK_FILE = "arcane_codex_background.png";
    private static final long RETRY_INTERVAL_MS = 5000L;
    private static final long DOWNLOAD_RETRY_MS = 60000L;
    private static final int MAX_PNG_BYTES = 8 * 1024 * 1024;
    private static final String SOURCE_ROOT_ARTWORK_URL =
            "https://raw.githubusercontent.com/KSPOG/ksppluginsrelease/main/kspaccountbuilder/" + ARTWORK_FILE;
    private static final int ART_W = 1584, ART_H = 993;
    private static final double SCALE = 0.34; // ~539 x 338 px instead of 792 x 497
    private static final Color GOLD = new Color(245, 198, 116);
    private static final Color PURPLE = new Color(190, 88, 243);
    private static final Color GREEN = new Color(103, 234, 101);
    private static final Color WHITE = new Color(243, 229, 206);
    private static final long READ_INTERVAL_MS = 700L;
    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;
    private final ItemManager itemManager;
    private final Map<Skill, Integer> baselineLevels = new EnumMap<>(Skill.class);
    private volatile BufferedImage background;
    private BufferedImage transparentArtwork;
    private BufferedImage transparentArtworkSource;
    private volatile boolean artworkDownloadInProgress;
    private volatile long nextArtworkDownloadAt;
    private long nextAttemptAt;
    private long nextWarningAt;
    private long nextReadAt;
    private long lastRuntime;
    private int cachedLevel;
    private int cachedXp;
    private int[] inventory = new int[0];
    private Skill cachedSkill;
    private Graphics2D g;

    KspArcaneCodexRenderer(KspAccountBuilderScript script, KspAccountBuilderConfig config, ItemManager itemManager)
    {
        this.script = script;
        this.config = config;
        this.itemManager = itemManager;
    }

    boolean hasArtwork()
    {
        if (background != null) return true;
        long now = System.currentTimeMillis();
        if (now < nextAttemptAt) return false;
        nextAttemptAt = now + RETRY_INTERVAL_MS;

        // Bundled JAR resource is the primary runtime source; the PNG
        // originates from the kspaccountbuilder plugin root at build time.
        try (InputStream stream = getClass().getResourceAsStream(ARTWORK_FILE))
        {
            if (stream != null) background = ImageIO.read(stream);
        }
        catch (IOException e)
        {
            LOG.warn("Account Builder: failed to decode Arcane Codex classpath artwork", e);
        }
        if (background == null)
        {
            try (InputStream stream = getClass().getResourceAsStream(
                    "/net/runelite/client/plugins/microbot/kspaccountbuilder/" + ARTWORK_FILE))
            {
                if (stream != null) background = ImageIO.read(stream);
            }
            catch (IOException e)
            {
                LOG.warn("Account Builder: failed to decode Arcane Codex packaged artwork", e);
            }
        }
        if (background != null)
        {
            LOG.info("Account Builder: Arcane Codex artwork loaded from plugin JAR ({}x{})",
                    background.getWidth(), background.getHeight());
            return true;
        }

        // Source Loader can compile classes without copying non-Java resources.
        String root = System.getProperty("ksp.accountbuilder.pluginRoot", "").trim();
        if (!root.isEmpty() && tryArtwork(new File(root, ARTWORK_FILE))) return true;

        try
        {
            URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
            if (location != null && "file".equalsIgnoreCase(location.getProtocol()))
            {
                File codeSource = Paths.get(location.toURI()).toFile();
                File directory = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
                if (searchAncestors(directory)) return true;
            }
        }
        catch (Exception ignored) { }

        if (searchAncestors(new File(System.getProperty("user.dir", ".")))) return true;
        // KSP Source Loader downloads .java sources only. Its runtime classloader
        // consequently cannot see non-Java files from the repository plugin root.
        // Obtain the exact same plugin-root PNG asynchronously once (never on
        // RuneLite's overlay paint/client thread). Normal JARs still load locally.
        scheduleSourceLoaderArtworkDownload(now);

        if (now >= nextWarningAt)
        {
            nextWarningAt = now + 30000L;
            LOG.warn("Account Builder: missing {} in JAR and kspaccountbuilder plugin root; "
                    + "for Source Loader set -Dksp.accountbuilder.pluginRoot=<plugin folder>",
                    ARTWORK_FILE);
        }
        return false;
    }

    private void scheduleSourceLoaderArtworkDownload(long now)
    {
        synchronized (this)
        {
            if (background != null || artworkDownloadInProgress || now < nextArtworkDownloadAt) return;
            artworkDownloadInProgress = true;
            nextArtworkDownloadAt = now + DOWNLOAD_RETRY_MS;
        }
        Thread download = new Thread(() -> {
            try
            {
                URLConnection connection = new URL(SOURCE_ROOT_ARTWORK_URL).openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(6000);
                connection.setUseCaches(false);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024 * 1024);
                try (InputStream in = connection.getInputStream())
                {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1)
                    {
                        if (bytes.size() + n > MAX_PNG_BYTES)
                            throw new IOException("Arcane Codex PNG exceeds 8 MiB limit");
                        bytes.write(chunk, 0, n);
                    }
                }
                byte[] data = bytes.toByteArray();
                if (data.length < 8 || (data[0] & 0xff) != 0x89
                        || data[1] != 0x50 || data[2] != 0x4e || data[3] != 0x47)
                    throw new IOException("Repository asset is not a PNG");
                BufferedImage loaded = ImageIO.read(new ByteArrayInputStream(data));
                if (loaded == null || loaded.getWidth() < 1 || loaded.getHeight() < 1)
                    throw new IOException("Could not decode repository PNG");
                background = loaded;
                LOG.info("Account Builder: Arcane Codex artwork loaded from repository plugin root ({}x{}, {} bytes)",
                        loaded.getWidth(), loaded.getHeight(), data.length);
            }
            catch (Exception ex)
            {
                LOG.warn("Account Builder: Arcane Codex plugin-root image download failed; retry in 60s | url={}",
                        SOURCE_ROOT_ARTWORK_URL, ex);
            }
            finally
            {
                artworkDownloadInProgress = false;
            }
        }, "KSP-ArcaneCodex-Artwork");
        download.setDaemon(true);
        download.start();
    }

    private boolean searchAncestors(File start)
    {
        for (File dir = start == null ? null : start.getAbsoluteFile(); dir != null;
                dir = dir.getParentFile())
        {
            if (tryArtwork(new File(dir, ARTWORK_FILE))
                    || tryArtwork(new File(dir, "kspaccountbuilder/" + ARTWORK_FILE))
                    || tryArtwork(new File(dir,
                        "src/main/java/net/runelite/client/plugins/microbot/kspaccountbuilder/" + ARTWORK_FILE)))
                return true;
        }
        return false;
    }

    private boolean tryArtwork(File file)
    {
        if (!file.isFile()) return false;
        try
        {
            BufferedImage loaded = ImageIO.read(file);
            if (loaded == null) return false;
            background = loaded;
            LOG.info("Account Builder: Arcane Codex artwork loaded from {}", file.getAbsolutePath());
            return true;
        }
        catch (IOException e)
        {
            LOG.warn("Account Builder: failed to load artwork at {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * The shipped illustration has opaque black padding outside its irregular
     * gilded frame. Remove only near-black pixels connected to the outer edge.
     * Dark book details and text inside the frame remain untouched.
     * Cache the alpha-masked image; do not repeat this work per paint frame.
     */
    private BufferedImage artworkWithoutBlackBorder()
    {
        BufferedImage source = background;
        if (source == transparentArtworkSource && transparentArtwork != null)
            return transparentArtwork;
        if (source == null) return null;

        int w = source.getWidth(), h = source.getHeight();
        int count = w * h;
        int[] pixels = source.getRGB(0, 0, w, h, null, 0, w);
        boolean[] visited = new boolean[count];
        int[] queue = new int[count];
        int front = 0, back = 0;
        final int threshold = 30;

        // Seed every eligible edge pixel. A strict connected-component mask
        // cannot accidentally remove the dark background inside the frame.
        for (int x = 0; x < w; x++)
        {
            int top = x, bottom = (h - 1) * w + x;
            if (!visited[top] && nearBlack(pixels[top], threshold))
            {
                visited[top] = true;
                queue[back++] = top;
            }
            if (!visited[bottom] && nearBlack(pixels[bottom], threshold))
            {
                visited[bottom] = true;
                queue[back++] = bottom;
            }
        }
        for (int y = 0; y < h; y++)
        {
            int left = y * w, right = left + w - 1;
            if (!visited[left] && nearBlack(pixels[left], threshold))
            {
                visited[left] = true;
                queue[back++] = left;
            }
            if (!visited[right] && nearBlack(pixels[right], threshold))
            {
                visited[right] = true;
                queue[back++] = right;
            }
        }

        while (front < back)
        {
            int position = queue[front++];
            int x = position % w;
            int y = position / w;
            pixels[position] &= 0x00ffffff;
            if (x > 0)
            {
                int n = position - 1;
                if (!visited[n] && nearBlack(pixels[n], threshold))
                {
                    visited[n] = true;
                    queue[back++] = n;
                }
            }
            if (x + 1 < w)
            {
                int n = position + 1;
                if (!visited[n] && nearBlack(pixels[n], threshold))
                {
                    visited[n] = true;
                    queue[back++] = n;
                }
            }
            if (y > 0)
            {
                int n = position - w;
                if (!visited[n] && nearBlack(pixels[n], threshold))
                {
                    visited[n] = true;
                    queue[back++] = n;
                }
            }
            if (y + 1 < h)
            {
                int n = position + w;
                if (!visited[n] && nearBlack(pixels[n], threshold))
                {
                    visited[n] = true;
                    queue[back++] = n;
                }
            }
        }
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, w, h, pixels, 0, w);
        transparentArtwork = result;
        transparentArtworkSource = source;
        LOG.info("Account Builder: removed {} connected black border pixels from Arcane Codex", back);
        return result;
    }

    private static boolean nearBlack(int pixel, int threshold)
    {
        return ((pixel >> 16) & 255) <= threshold
                && ((pixel >> 8) & 255) <= threshold
                && (pixel & 255) <= threshold;
    }

    Dimension render(Graphics2D graphics)
    {
        if (!hasArtwork()) return null;
        String task = script.getCurrentTaskName();
        if (task == null) task = "IDLE";
        String detail = script.getOverlayTaskDetail();
        if (detail == null || detail.trim().isEmpty()) detail = "Waiting for task";
        Skill skill = resolveSkill(task);
        long runtime = script.getRuntimeSeconds();
        if (runtime < lastRuntime)
        {
            baselineLevels.clear();
            nextReadAt = 0;
        }
        lastRuntime = runtime;
        readCurrentState(skill);
        g = (Graphics2D) graphics.create();
        try
        {
            g.scale(SCALE, SCALE);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawImage(artworkWithoutBlackBorder(), 0, 0, ART_W, ART_H, null);
            final Color taskTint = accent(task);
            // All of the following are LIVE overlays on the original reference art.
            text(nice(task), 200, 335, 30, true, GOLD, 305);
            text(detail, 200, 370, 22, false, WHITE, 305);
            drawItem(primaryId(task), 148, 334, 66);
            if (skill == null || cachedLevel <= 0)
            {
                text("TASK", 549, 308, 23, true, PURPLE, 110);
                text("—", 561, 345, 32, true, WHITE, 93);
                text("No skill milestone", 661, 328, 24, false, WHITE, 225);
            }
            else
            {
                int gained = Math.max(0, cachedLevel - baselineLevels.getOrDefault(skill, cachedLevel));
                text("LEVEL", 549, 307, 22, true, PURPLE, 110);
                text(String.valueOf(cachedLevel), 553, 345, 39, true, WHITE, 91);
                // ONE arrow only. The (+gained) is adjacent to the next level.
                text("→ " + Math.min(99, cachedLevel + 1), 656, 320, 35, true, GOLD, 110);
                text("(+" + gained + ")", 760, 320, 29, true, GREEN, 128);
                int currentXp = Experience.getXpForLevel(Math.min(99, cachedLevel));
                int nextXp = Experience.getXpForLevel(Math.min(99, cachedLevel + 1));
                int range = Math.max(1, nextXp - currentXp);
                double portion = cachedLevel >= 99 ? 1.0 :
                        Math.max(0, Math.min(1, (double)(cachedXp - currentXp) / range));
                text((cachedXp - currentXp) + " / " + range + " XP", 658, 348, 19, false, WHITE, 233);
                g.setColor(new Color(22, 19, 31));
                g.fillRoundRect(532, 374, 281, 16, 9, 9);
                g.setColor(taskTint);
                g.fillRoundRect(532, 374, Math.max(1, (int)(281 * portion)), 16, 9, 9);
                text(String.format(Locale.ROOT, "%.1f%%", 100 * portion), 827, 390, 21, true, PURPLE, 88);
            }
            tile(111, 464, "RUNTIME", time(runtime));
            tile(391, 464, script.isAnyBreakActive() ? "BREAK LEFT" : "BREAK IN", breakTime());
            tile(669, 464, "ACCOUNT", time(script.getAccountPlayTimeSeconds()));
            tile(111, 580, "CURRENT TASK", nice(task));
            tile(391, 580, "ACTIVITY", config.trainSingleSkill() ? "SINGLE SKILL" :
                    config.runSingleQuest() ? "QUEST MODE" : "AUTOMATIC");
            tile(669, 580, "NEXT SWITCH", switchTime());
            text("ITEMS", 190, 724, 23, true, PURPLE, 277);
            int primary = primaryId(task);
            int secondary = secondaryId(task, detail);
            drawItem(primary, 243, 782, 74);
            drawItem(secondary, 384, 782, 74);
            text(itemName(task, true, detail), 183, 844, 17, false, WHITE, 114);
            text(itemName(task, false, detail), 330, 844, 17, false, WHITE, 143);
            text("STATUS", 532, 723, 23, true, PURPLE, 344);
            g.setColor(GREEN); g.fillOval(533, 751, 14, 14);
            text(detail, 569, 764, 21, false, WHITE, 321);
            text(script.isAnyBreakActive() ? "Break active" : "Automating active task", 567, 798, 19, false,
                    new Color(173, 166, 185), 327);
            text(config.useAntiban() ? "Task anti-ban enabled" : "Anti-ban disabled", 567, 830, 19, false,
                    new Color(173, 166, 185), 327);
            drawItem(primary, 1378, 226, 80);
        }
        finally { g.dispose(); }
        return new Dimension((int)(ART_W * SCALE), (int)(ART_H * SCALE));
    }

    private void tile(int x, int y, String label, String value)
    {
        text(label, x, y, 21, true, PURPLE, 212);
        text(value, x, y + 42, 29, true, WHITE, 228);
    }

    private void readCurrentState(Skill selected)
    {
        if (System.currentTimeMillis() < nextReadAt && selected == cachedSkill) return;
        nextReadAt = System.currentTimeMillis() + READ_INTERVAL_MS;
        cachedSkill = selected;
        final int[][] snapshot = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null) return null;
            int level = selected == null ? 0 : Microbot.getClient().getRealSkillLevel(selected);
            int xp = selected == null ? 0 : Microbot.getClient().getSkillExperience(selected);
            ItemContainer bag = Microbot.getClient().getItemContainer(InventoryID.INVENTORY);
            Item[] items = bag == null ? new Item[0] : bag.getItems();
            int[] ids = new int[items.length];
            for (int i = 0; i < items.length; i++) ids[i] = items[i] == null ? -1 : items[i].getId();
            return new int[][] {new int[] {level, xp}, ids};
        }).orElse(null);
        if (snapshot == null) return;
        cachedLevel = snapshot[0][0];
        cachedXp = snapshot[0][1];
        inventory = snapshot[1];
        if (selected != null && cachedLevel > 0) baselineLevels.putIfAbsent(selected, cachedLevel);
    }

    private static Skill resolveSkill(String task)
    {
        if (task.contains("RUNE_ESSENCE") || task.contains("MINING")) return Skill.MINING;
        if (task.contains("WOODCUTTING")) return Skill.WOODCUTTING;
        if (task.contains("FIREMAKING")) return Skill.FIREMAKING;
        if (task.contains("FISHING")) return Skill.FISHING;
        if (task.contains("COOKING")) return Skill.COOKING;
        if (task.contains("SMITHING") || task.contains("SMELTING")) return Skill.SMITHING;
        if (task.contains("CRAFTING")) return Skill.CRAFTING;
        if (task.contains("MAGIC")) return Skill.MAGIC;
        if (task.contains("MELEE")) return Skill.ATTACK;
        return null;
    }

    private int preferInventory(int fallback, int... ids)
    {
        for (int id : ids)
            for (int held : inventory)
                if (held == id) return id;
        return fallback;
    }

    private int primaryId(String task)
    {
        if (task.contains("FIREMAKING")) return preferInventory(590, 590);
        if (task.contains("WOODCUTTING")) return preferInventory(1359, 1359, 1357, 1355, 1353, 1351, 1349, 1347, 1350);
        if (task.contains("RUNE_ESSENCE") || task.contains("MINING")) return preferInventory(1275, 1275, 1271, 1273, 1269, 1267, 1265);
        if (task.contains("FISHING")) return preferInventory(309, 309, 307, 303, 301);
        if (task.contains("COOKING")) return preferInventory(379, 379, 333, 329, 315);
        if (task.contains("SMITHING") || task.contains("SMELTING")) return 2347;
        if (task.contains("CRAFTING")) return preferInventory(1755, 1755, 1733);
        if (task.contains("MAGIC")) return preferInventory(1381, 1381, 1383, 1385, 1387);
        if (task.contains("MELEE")) return preferInventory(1289, 1289, 1279, 1277);
        return 8007;
    }

    private int secondaryId(String task, String detail)
    {
        if (task.contains("FIREMAKING") || task.contains("WOODCUTTING"))
        {
            if (contains(detail, "magic")) return 1513;
            if (contains(detail, "yew")) return 1515;
            if (contains(detail, "maple")) return 1517;
            if (contains(detail, "willow")) return 1519;
            if (contains(detail, "oak")) return 1521;
            return preferInventory(1511, 1513, 1515, 1517, 1519, 1521, 1511);
        }
        if (task.contains("RUNE_ESSENCE")) return 1436;
        if (task.contains("MINING")) return preferInventory(440, 440, 436, 438, 442, 444, 447, 449, 451);
        if (task.contains("FISHING")) return preferInventory(335, 335, 331, 317, 321, 327, 349);
        if (task.contains("COOKING")) return preferInventory(377, 377, 335, 331, 317, 321);
        if (task.contains("SMITHING") || task.contains("SMELTING")) return preferInventory(2353, 2353, 2349, 2351, 2359);
        if (task.contains("CRAFTING")) return preferInventory(1623, 1623, 1621, 1619, 1617, 1609);
        if (task.contains("MAGIC")) return preferInventory(554, 554, 555, 556, 557, 558);
        return 1511;
    }

    private static boolean contains(String text, String key)
    {
        return text != null && text.toLowerCase(Locale.ROOT).contains(key);
    }

    private static String itemName(String task, boolean primary, String detail)
    {
        if (primary)
        {
            if (task.contains("FIREMAKING")) return "Tinderbox";
            if (task.contains("WOODCUTTING")) return "Axe";
            if (task.contains("MINING") || task.contains("RUNE_ESSENCE")) return "Pickaxe";
            if (task.contains("FISHING")) return "Fishing tool";
            if (task.contains("COOKING")) return "Cooked food";
            return "Equipment";
        }
        if (task.contains("FIREMAKING") || task.contains("WOODCUTTING"))
        {
            for (String wood : new String[]{"Magic", "Yew", "Maple", "Willow", "Oak"})
                if (contains(detail, wood)) return wood + " logs";
            return "Logs";
        }
        if (task.contains("RUNE_ESSENCE")) return "Rune essence";
        if (task.contains("MINING")) return "Ore";
        if (task.contains("FISHING")) return "Fish";
        if (task.contains("COOKING")) return "Raw food";
        return "Resources";
    }

    private void drawItem(int id, int x, int y, int size)
    {
        if (itemManager == null) return;
        BufferedImage sprite;
        try { sprite = itemManager.getImage(id); }
        catch (RuntimeException ex) { return; }
        if (sprite == null) return;
        g.drawImage(sprite, x - size / 2, y - size / 2, size, size, null);
    }

    private static Color accent(String task)
    {
        if (task.contains("FIREMAKING")) return new Color(255, 146, 68);
        if (task.contains("MINING")) return new Color(79, 167, 247);
        if (task.contains("WOODCUTTING")) return new Color(123, 217, 92);
        if (task.contains("MAGIC")) return PURPLE;
        return new Color(185, 122, 248);
    }

    private String breakTime()
    {
        if (!config.doBreaks()) return "OFF";
        return time(script.isAnyBreakActive() ? script.getBreakTimeRemainingSeconds() : script.getTimeUntilBreakSeconds());
    }

    private String switchTime()
    {
        if (!config.enableActivitySwitchRandomization() || config.trainSingleSkill() || config.runSingleQuest()) return "--";
        return time(script.getTimeUntilActivitySwitchSeconds());
    }

    private static String time(long seconds)
    {
        if (seconds < 0) return "--";
        long hours = TimeUnit.SECONDS.toHours(seconds);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours,
                TimeUnit.SECONDS.toMinutes(seconds) % 60, seconds % 60);
    }

    private static String nice(String text) { return text.replace('_', ' '); }

    private void text(String value, int x, int y, int size, boolean bold, Color color, int maxWidth)
    {
        if (value == null) value = "";
        g.setFont(new Font("Serif", bold ? Font.BOLD : Font.PLAIN, size));
        FontMetrics fm = g.getFontMetrics();
        String shown = value;
        while (shown.length() > 1 && fm.stringWidth(shown) > maxWidth)
            shown = shown.substring(0, shown.length() - 1);
        if (shown.length() < value.length() && shown.length() > 3)
            shown = shown.substring(0, shown.length() - 2) + "..";
        g.setColor(color);
        g.drawString(shown, x, y);
    }
}
