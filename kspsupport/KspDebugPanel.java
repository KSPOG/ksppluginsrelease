package net.runelite.client.plugins.microbot.kspsupport;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compact sidebar plus a styled, filterable KSP debug console. */
final class KspDebugPanel extends PluginPanel
{
    private static final int MAX = 2500;
    private static final int UI_BATCH_MS = 150;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color BG = new Color(30, 30, 30), CARD = new Color(40, 40, 40);
    private static final Color MUTED = new Color(155, 155, 155), GREEN = new Color(120, 220, 140);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern FONT = Pattern.compile("(?i)<font\\s+color\\s*=\\s*['\"]?([^'\"\\s>]+)['\"]?\\s*>");

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Set<String> plugins = new LinkedHashSet<>();
    private final JLabel active = value("0"), events = value("0"), issues = value("0");
    private final JLabel currentPlugin = value("-"), currentState = value("-");
    private final JTextArea recent = textArea();
    private final Timer uiTimer;

    private JFrame frame;
    private JEditorPane html;
    private JScrollPane scroll;
    private JComboBox<String> levelFilter, pluginFilter;
    private JTextField search;
    private JCheckBox autoScroll;
    private JButton pause;
    private boolean paused, updatingFilter, pluginsDirty;
    private int issueCount, unseen;
    private Entry latest;

    KspDebugPanel()
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        uiTimer = new Timer(UI_BATCH_MS, e -> flushUi());
        uiTimer.setRepeats(false);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("KSP Debug");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        body.add(title);
        body.add(Box.createVerticalStrut(8));

        JPanel stats = card();
        stats.add(row("Active plugins", active));
        stats.add(row("Events", events));
        stats.add(row("Warnings/Errors", issues));
        body.add(stats);
        body.add(Box.createVerticalStrut(8));

        JPanel now = card();
        now.add(row("Plugin", currentPlugin));
        now.add(row("State", currentState));
        body.add(now);
        body.add(Box.createVerticalStrut(8));

        JButton open = new JButton("Open Debug Console");
        open.setAlignmentX(LEFT_ALIGNMENT);
        open.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        open.addActionListener(e -> openConsole());
        body.add(open);
        body.add(Box.createVerticalStrut(8));

        recent.setRows(15);
        JScrollPane rs = new JScrollPane(recent);
        rs.setAlignmentX(LEFT_ALIGNMENT);
        body.add(rs);
        add(body, BorderLayout.CENTER);
    }

    void append(String level, String message)
    {
        String lvl = normalizeLevel(level);
        String msg = message == null ? "" : message.replace('\r', '\n');
        String raw = pluginOf(msg);
        Entry entry = new Entry(LocalTime.now().format(TIME), lvl, plain(raw), styled(raw), typeOf(msg), msg);

        SwingUtilities.invokeLater(() ->
        {
            entries.addLast(entry);
            while (entries.size() > MAX) entries.removeFirst();
            if (!"System".equals(entry.plugin) && plugins.add(entry.plugin)) pluginsDirty = true;
            if (!"INFO".equals(entry.level)) issueCount++;
            latest = entry;

            if (html != null && (paused || !autoScrollEnabled())) unseen++;
            if (!uiTimer.isRunning()) uiTimer.start();
        });
    }

    private void flushUi()
    {
        events.setText(Integer.toString(entries.size()));
        issues.setText(Integer.toString(issueCount));
        if (latest != null) updateCurrent(latest);
        refreshRecent();
        if (pluginsDirty)
        {
            pluginsDirty = false;
            refreshPlugins();
        }

        if (html != null)
        {
            if (!paused && autoScrollEnabled())
            {
                unseen = 0;
                updateAutoScrollLabel();
                rebuild(true);
            }
            else updateAutoScrollLabel();
        }
    }

    void setActiveCount(int count)
    {
        SwingUtilities.invokeLater(() -> active.setText(Integer.toString(Math.max(0, count))));
    }

    void disposeConsole()
    {
        SwingUtilities.invokeLater(() ->
        {
            uiTimer.stop();
            if (frame != null) frame.dispose();
            frame = null;
            html = null;
            scroll = null;
        });
    }

    private void updateCurrent(Entry entry)
    {
        if (!"System".equals(entry.plugin))
        {
            currentPlugin.setText("<html>" + entry.pluginHtml + "</html>");
            currentPlugin.setToolTipText(entry.plugin);
        }
        if ("ERROR".equals(entry.level)) currentState.setText("ERROR");
        else if ("state-change".equals(entry.type)) currentState.setText(shorten(after(entry.message, "changed="), 28));
        else if ("lifecycle".equals(entry.type)) currentState.setText("Lifecycle changed");
        else if ("unloaded".equals(entry.type)) currentState.setText("Unloaded");
    }

    private void refreshRecent()
    {
        StringBuilder out = new StringBuilder();
        List<Entry> copy = new ArrayList<>(entries);
        int shown = 0;
        for (int i = copy.size() - 1; i >= 0 && shown < 12; i--)
        {
            Entry entry = copy.get(i);
            if ("heartbeat".equals(entry.type)) continue;
            String mark = "ERROR".equals(entry.level) ? "X " : "WARN".equals(entry.level) ? "! " : "  ";
            out.insert(0, entry.time + " " + mark + shorten(entry.plugin, 18) + " · "
                    + shorten(oneLine(summary(entry)), 70) + '\n');
            shown++;
        }
        recent.setText(out.toString());
        recent.setCaretPosition(recent.getDocument().getLength());
    }

    private void openConsole()
    {
        if (frame == null)
        {
            frame = new JFrame("KSP Debug Console");
            frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            frame.setMinimumSize(new Dimension(900, 520));
            frame.setSize(1180, 700);
            frame.setLocationByPlatform(true);

            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            bar.setBackground(CARD);
            levelFilter = new JComboBox<>(new String[]{"ALL", "INFO", "WARN", "ERROR"});
            pluginFilter = new JComboBox<>(new String[]{"ALL PLUGINS"});
            search = new JTextField(22);
            search.putClientProperty("JTextField.placeholderText", "Search logs...");
            autoScroll = new JCheckBox("Auto-scroll", true);
            pause = new JButton("Pause");
            JButton clear = new JButton("Clear"), copy = new JButton("Copy"), refresh = new JButton("Refresh");
            bar.add(levelFilter); bar.add(pluginFilter); bar.add(search); bar.add(pause);
            bar.add(clear); bar.add(copy); bar.add(refresh); bar.add(autoScroll);

            levelFilter.addActionListener(e -> { if (!updatingFilter) manualRebuild(); });
            pluginFilter.addActionListener(e -> { if (!updatingFilter) manualRebuild(); });
            search.addActionListener(e -> manualRebuild());
            pause.addActionListener(e ->
            {
                paused = !paused;
                pause.setText(paused ? "Resume" : "Pause");
                if (!paused) manualRebuild();
            });
            clear.addActionListener(e -> clear());
            copy.addActionListener(e -> copy());
            refresh.addActionListener(e -> manualRebuild());
            autoScroll.addActionListener(e ->
            {
                updateAutoScrollLabel();
                if (autoScroll.isSelected() && !paused)
                {
                    unseen = 0;
                    updateAutoScrollLabel();
                    rebuild(true);
                }
            });

            html = new JEditorPane();
            html.setEditable(false);
            html.setContentType("text/html");
            html.setEditorKit(new HTMLEditorKit());
            html.setBackground(BG);
            scroll = new JScrollPane(html);

            JTextArea help = textArea();
            help.setLineWrap(true);
            help.setWrapStyleWord(true);
            help.setText("KSP DEBUG\n\nINFO: runtime state/lifecycle\nWARN: KSP/plugin warnings\nERROR: real Logback errors + stack traces\n\nUI updates are batched to avoid client stalls. Auto-scroll OFF freezes the visible document until Refresh or Auto-scroll is enabled.");
            help.setBorder(new EmptyBorder(10, 10, 10, 10));
            JScrollPane hs = new JScrollPane(help);
            hs.setPreferredSize(new Dimension(245, 0));

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, hs, scroll);
            split.setDividerLocation(245);
            split.setResizeWeight(0);
            frame.add(bar, BorderLayout.NORTH);
            frame.add(split, BorderLayout.CENTER);
            refreshPlugins();
            rebuild(true);
        }
        frame.setVisible(true);
        frame.toFront();
    }

    private boolean autoScrollEnabled()
    {
        return autoScroll != null && autoScroll.isSelected();
    }

    private void updateAutoScrollLabel()
    {
        if (autoScroll == null) return;
        autoScroll.setText(unseen > 0 && !autoScroll.isSelected()
                ? "Auto-scroll (" + unseen + " new)" : "Auto-scroll");
    }

    private void manualRebuild()
    {
        unseen = 0;
        updateAutoScrollLabel();
        rebuild(autoScrollEnabled());
    }

    private void rebuild(boolean followBottom)
    {
        if (html == null || scroll == null) return;
        int oldValue = scroll.getVerticalScrollBar().getValue();
        StringBuilder body = new StringBuilder();
        for (Entry entry : filtered()) body.append(render(entry));
        html.setText(document(body.toString()));
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() ->
        {
            if (scroll == null) return;
            JScrollBar bar = scroll.getVerticalScrollBar();
            if (followBottom) bar.setValue(bar.getMaximum());
            else bar.setValue(Math.min(oldValue, Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount())));
        }));
    }

    private List<Entry> filtered()
    {
        String lvl = levelFilter == null ? "ALL" : String.valueOf(levelFilter.getSelectedItem());
        String plug = pluginFilter == null ? "ALL PLUGINS" : String.valueOf(pluginFilter.getSelectedItem());
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries)
        {
            if (!"ALL".equals(lvl) && !lvl.equals(entry.level)) continue;
            if (!"ALL PLUGINS".equals(plug) && !plug.equals(entry.plugin)) continue;
            String haystack = (entry.plugin + ' ' + entry.type + ' ' + summary(entry)).toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !haystack.contains(query)) continue;
            out.add(entry);
        }
        return out;
    }

    private void refreshPlugins()
    {
        if (pluginFilter == null) return;
        String selected = String.valueOf(pluginFilter.getSelectedItem());
        updatingFilter = true;
        try
        {
            pluginFilter.removeAllItems();
            pluginFilter.addItem("ALL PLUGINS");
            for (String plugin : plugins) pluginFilter.addItem(plugin);
            pluginFilter.setSelectedItem(selected);
            if (pluginFilter.getSelectedIndex() < 0) pluginFilter.setSelectedIndex(0);
        }
        finally
        {
            updatingFilter = false;
        }
    }

    private void clear()
    {
        entries.clear();
        issueCount = unseen = 0;
        latest = null;
        events.setText("0");
        issues.setText("0");
        recent.setText("");
        updateAutoScrollLabel();
        if (html != null) html.setText(document(""));
    }

    private void copy()
    {
        StringBuilder out = new StringBuilder();
        for (Entry entry : filtered())
            out.append('[').append(entry.time).append("] [").append(entry.level).append("] [")
                    .append(entry.plugin).append("] ").append(summary(entry)).append(System.lineSeparator());
        if (out.length() > 0)
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(out.toString()), null);
    }

    private static String render(Entry entry)
    {
        String levelColor = "ERROR".equals(entry.level) ? "#ff5f56" : "WARN".equals(entry.level) ? "#ffb454" : "#78dc8c";
        String typeColor = "ERROR".equals(entry.level) ? "#ff7b72" : "WARN".equals(entry.level) ? "#ffd166" : "#8ab4f8";
        String body = esc(summaryWithoutType(entry)).replace("\n", "<br>");
        return "<div class='line'><span class='time'>[" + esc(entry.time) + "]</span> "
                + "<span style='color:" + levelColor + ";font-weight:bold'>[" + entry.level + "]</span> "
                + "<span class='plugin'>[" + entry.pluginHtml + "]</span> "
                + "<span style='color:" + typeColor + ";font-weight:bold'>" + esc(entry.type) + "</span> "
                + "<span>" + body + "</span></div>";
    }

    private static String document(String body)
    {
        return "<html><head><style>body{background:#1e1e1e;color:#dcdcdc;font-family:Consolas,'Courier New',monospace;font-size:12px;margin:8px;}"
                + ".line{margin:0 0 5px 0;white-space:normal}.time{color:#888}.plugin{color:#eee}</style></head><body>"
                + body + "</body></html>";
    }

    private static String normalizeLevel(String level)
    {
        if ("ERROR".equalsIgnoreCase(level)) return "ERROR";
        if ("WARN".equalsIgnoreCase(level)) return "WARN";
        return "INFO";
    }

    private static String styled(String raw)
    {
        if (raw == null || raw.isEmpty()) return "System";
        String source = raw.trim();
        if (source.regionMatches(true, 0, "<html>", 0, 6)) source = source.substring(6);
        if (source.toLowerCase(Locale.ROOT).endsWith("</html>")) source = source.substring(0, source.length() - 7);
        StringBuilder out = new StringBuilder();
        Matcher matcher = TAG.matcher(source);
        int position = 0;
        while (matcher.find())
        {
            out.append(esc(source.substring(position, matcher.start())));
            String tag = matcher.group(), lower = tag.toLowerCase(Locale.ROOT);
            Matcher font = FONT.matcher(tag);
            if (font.matches()) out.append("<font color=\"").append(esc(sanitizeColor(font.group(1)))).append("\">");
            else if ("</font>".equals(lower) || "<b>".equals(lower) || "</b>".equals(lower)
                    || "<i>".equals(lower) || "</i>".equals(lower)) out.append(lower);
            else out.append(esc(tag));
            position = matcher.end();
        }
        out.append(esc(source.substring(position)));
        return out.toString();
    }

    private static String sanitizeColor(String color)
    {
        if (color == null || color.isEmpty()) return "#dddddd";
        String value = color.trim();
        if (value.startsWith("#") && value.length() >= 7)
        {
            String hex = value.substring(1, 7);
            if (hex.matches("[0-9a-fA-F]{6}")) return "#" + hex;
        }
        return value.matches("[A-Za-z]+") ? value : "#dddddd";
    }

    private static String plain(String raw)
    {
        if (raw == null || raw.isEmpty()) return "System";
        return TAG.matcher(raw).replaceAll("").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").trim();
    }

    private static String pluginOf(String message)
    {
        String plugin = valueOf(message, "plugin=");
        return plugin == null || plugin.isEmpty() ? "System" : plugin;
    }

    private static String typeOf(String message)
    {
        String value = message.startsWith("[KSP-DBG] ") ? message.substring(10) : message;
        int pipe = value.indexOf('|');
        return (pipe < 0 ? value : value.substring(0, pipe)).trim();
    }

    private static String valueOf(String message, String key)
    {
        int start = message.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = message.indexOf(" | ", start);
        return message.substring(start, end < 0 ? message.length() : end).trim();
    }

    private static String summary(Entry entry)
    {
        String value = entry.message.startsWith("[KSP-DBG] ") ? entry.message.substring(10) : entry.message;
        String prefix = entry.type + " | ";
        if (value.startsWith(prefix)) value = value.substring(prefix.length());
        String rawPlugin = valueOf(entry.message, "plugin=");
        if (rawPlugin != null) value = value.replace("plugin=" + rawPlugin + " | ", "");
        return plain(value);
    }

    private static String summaryWithoutType(Entry entry)
    {
        String value = summary(entry);
        return value.startsWith(entry.type + " | ") ? value.substring(entry.type.length() + 3) : value;
    }

    private static String after(String text, String key)
    {
        int index = text.indexOf(key);
        return index < 0 ? plain(text) : plain(text.substring(index + key.length()).trim());
    }

    private static String oneLine(String value)
    {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String shorten(String value, int max)
    {
        return value == null || value.isEmpty() ? "-" : value.length() <= max
                ? value : value.substring(0, Math.max(1, max - 3)) + "...";
    }

    private static String esc(String value)
    {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static JPanel card()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        panel.setBackground(CARD);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        return panel;
    }

    private static JPanel row(String name, JLabel value)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel key = new JLabel(name);
        key.setForeground(MUTED);
        panel.add(key, BorderLayout.WEST);
        panel.add(value, BorderLayout.EAST);
        return panel;
    }

    private static JLabel value(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(GREEN);
        return label;
    }

    private static JTextArea textArea()
    {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setBackground(BG);
        area.setForeground(new Color(220, 220, 220));
        area.setCaretColor(Color.WHITE);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        return area;
    }

    private static final class Entry
    {
        final String time, level, plugin, pluginHtml, type, message;

        Entry(String time, String level, String plugin, String pluginHtml, String type, String message)
        {
            this.time = time;
            this.level = level;
            this.plugin = plugin;
            this.pluginHtml = pluginHtml;
            this.type = type;
            this.message = message;
        }
    }
}
