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

    private JFrame frame;
    private JEditorPane html;
    private JScrollPane scroll;
    private JComboBox<String> levelFilter, pluginFilter;
    private JTextField search;
    private JCheckBox autoScroll;
    private JButton pause;
    private boolean paused, updatingFilter;
    private int issueCount, unseen;

    KspDebugPanel()
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(new EmptyBorder(8, 8, 8, 8));

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
        Entry e = new Entry(LocalTime.now().format(TIME), lvl, plain(raw), styled(raw), typeOf(msg), msg);

        SwingUtilities.invokeLater(() ->
        {
            entries.addLast(e);
            while (entries.size() > MAX) entries.removeFirst();
            if (!"System".equals(e.plugin)) plugins.add(e.plugin);
            if (!"INFO".equals(e.level)) issueCount++;
            events.setText(Integer.toString(entries.size()));
            issues.setText(Integer.toString(issueCount));
            updateCurrent(e);
            refreshRecent();
            refreshPlugins();

            if (!paused && autoScrollEnabled())
            {
                unseen = 0;
                updateAutoScrollLabel();
                rebuild(true);
            }
            else if (html != null)
            {
                unseen++;
                updateAutoScrollLabel();
            }
        });
    }

    void setActiveCount(int count)
    {
        SwingUtilities.invokeLater(() -> active.setText(Integer.toString(Math.max(0, count))));
    }

    void disposeConsole()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (frame != null) frame.dispose();
            frame = null;
            html = null;
            scroll = null;
        });
    }

    private void updateCurrent(Entry e)
    {
        if (!"System".equals(e.plugin))
        {
            currentPlugin.setText("<html>" + e.pluginHtml + "</html>");
            currentPlugin.setToolTipText(e.plugin);
        }
        if ("ERROR".equals(e.level)) currentState.setText("ERROR");
        else if ("state-change".equals(e.type)) currentState.setText(shorten(after(e.message, "changed="), 28));
        else if ("lifecycle".equals(e.type)) currentState.setText("Lifecycle changed");
        else if ("unloaded".equals(e.type)) currentState.setText("Unloaded");
    }

    private void refreshRecent()
    {
        StringBuilder out = new StringBuilder();
        List<Entry> copy = new ArrayList<>(entries);
        int shown = 0;
        for (int i = copy.size() - 1; i >= 0 && shown < 12; i--)
        {
            Entry e = copy.get(i);
            if ("heartbeat".equals(e.type)) continue;
            String mark = "ERROR".equals(e.level) ? "X " : "WARN".equals(e.level) ? "! " : "  ";
            out.insert(0, e.time + " " + mark + shorten(e.plugin, 18) + " · " + shorten(oneLine(summary(e)), 70) + '\n');
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
            bar.add(levelFilter); bar.add(pluginFilter); bar.add(search); bar.add(pause); bar.add(clear); bar.add(copy); bar.add(refresh); bar.add(autoScroll);

            levelFilter.addActionListener(e -> { if (!updatingFilter) manualRebuild(); });
            pluginFilter.addActionListener(e -> { if (!updatingFilter) manualRebuild(); });
            search.addActionListener(e -> manualRebuild());
            pause.addActionListener(e -> { paused = !paused; pause.setText(paused ? "Resume" : "Pause"); if (!paused) manualRebuild(); });
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
            help.setText("KSP DEBUG\n\nINFO: runtime state/lifecycle\nWARN: KSP/plugin warnings\nERROR: real Logback errors + stack traces\n\nAuto-scroll OFF freezes the visible document. New messages remain buffered until Refresh or Auto-scroll is enabled.");
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

    private boolean autoScrollEnabled() { return autoScroll != null && autoScroll.isSelected(); }

    private void updateAutoScrollLabel()
    {
        if (autoScroll == null) return;
        autoScroll.setText(unseen > 0 && !autoScroll.isSelected() ? "Auto-scroll (" + unseen + " new)" : "Auto-scroll");
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
        for (Entry e : filtered()) body.append(render(e));
        html.setText(document(body.toString()));
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() ->
        {
            if (scroll == null) return;
            JScrollBar b = scroll.getVerticalScrollBar();
            if (followBottom) b.setValue(b.getMaximum());
            else b.setValue(Math.min(oldValue, Math.max(b.getMinimum(), b.getMaximum() - b.getVisibleAmount())));
        }));
    }

    private List<Entry> filtered()
    {
        String lvl = levelFilter == null ? "ALL" : String.valueOf(levelFilter.getSelectedItem());
        String plug = pluginFilter == null ? "ALL PLUGINS" : String.valueOf(pluginFilter.getSelectedItem());
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries)
        {
            if (!"ALL".equals(lvl) && !lvl.equals(e.level)) continue;
            if (!"ALL PLUGINS".equals(plug) && !plug.equals(e.plugin)) continue;
            String hay = (e.plugin + ' ' + e.type + ' ' + summary(e)).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            out.add(e);
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
            for (String p : plugins) pluginFilter.addItem(p);
            pluginFilter.setSelectedItem(selected);
            if (pluginFilter.getSelectedIndex() < 0) pluginFilter.setSelectedIndex(0);
        }
        finally { updatingFilter = false; }
    }

    private void clear()
    {
        entries.clear();
        issueCount = unseen = 0;
        events.setText("0"); issues.setText("0"); recent.setText("");
        updateAutoScrollLabel();
        if (html != null) html.setText(document(""));
    }

    private void copy()
    {
        StringBuilder out = new StringBuilder();
        for (Entry e : filtered()) out.append('[').append(e.time).append("] [").append(e.level).append("] [")
                .append(e.plugin).append("] ").append(summary(e)).append(System.lineSeparator());
        if (out.length() > 0) Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(out.toString()), null);
    }

    private static String render(Entry e)
    {
        String levelColor = "ERROR".equals(e.level) ? "#ff5f56" : "WARN".equals(e.level) ? "#ffb454" : "#78dc8c";
        String typeColor = "ERROR".equals(e.level) ? "#ff7b72" : "WARN".equals(e.level) ? "#ffd166" : "#8ab4f8";
        String body = esc(summaryWithoutType(e)).replace("\n", "<br>");
        return "<div class='line'><span class='time'>[" + esc(e.time) + "]</span> "
                + "<span style='color:" + levelColor + ";font-weight:bold'>[" + e.level + "]</span> "
                + "<span class='plugin'>[" + e.pluginHtml + "]</span> "
                + "<span style='color:" + typeColor + ";font-weight:bold'>" + esc(e.type) + "</span> "
                + "<span>" + body + "</span></div>";
    }

    private static String document(String body)
    {
        return "<html><head><style>body{background:#1e1e1e;color:#dcdcdc;font-family:Consolas,'Courier New',monospace;font-size:12px;margin:8px;}"
                + ".line{margin:0 0 5px 0;white-space:normal}.time{color:#888}.plugin{color:#eee}</style></head><body>" + body + "</body></html>";
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
        String s = raw.trim();
        if (s.regionMatches(true, 0, "<html>", 0, 6)) s = s.substring(6);
        if (s.toLowerCase(Locale.ROOT).endsWith("</html>")) s = s.substring(0, s.length() - 7);
        StringBuilder out = new StringBuilder();
        Matcher m = TAG.matcher(s);
        int pos = 0;
        while (m.find())
        {
            out.append(esc(s.substring(pos, m.start())));
            String tag = m.group(), low = tag.toLowerCase(Locale.ROOT);
            Matcher fm = FONT.matcher(tag);
            if (fm.matches()) out.append("<font color=\"").append(esc(sanitizeColor(fm.group(1)))).append("\">");
            else if ("</font>".equals(low) || "<b>".equals(low) || "</b>".equals(low) || "<i>".equals(low) || "</i>".equals(low)) out.append(low);
            else out.append(esc(tag));
            pos = m.end();
        }
        out.append(esc(s.substring(pos)));
        return out.toString();
    }

    private static String sanitizeColor(String color)
    {
        if (color == null || color.isEmpty()) return "#dddddd";
        String c = color.trim();
        if (c.startsWith("#") && c.length() >= 7)
        {
            String hex = c.substring(1, 7);
            if (hex.matches("[0-9a-fA-F]{6}")) return "#" + hex;
        }
        return c.matches("[A-Za-z]+") ? c : "#dddddd";
    }

    private static String plain(String raw)
    {
        if (raw == null || raw.isEmpty()) return "System";
        return TAG.matcher(raw).replaceAll("").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim();
    }

    private static String pluginOf(String message)
    {
        String p = valueOf(message, "plugin=");
        return p == null || p.isEmpty() ? "System" : p;
    }

    private static String typeOf(String message)
    {
        String s = message.startsWith("[KSP-DBG] ") ? message.substring(10) : message;
        int p = s.indexOf('|');
        return (p < 0 ? s : s.substring(0, p)).trim();
    }

    private static String valueOf(String message, String key)
    {
        int s = message.indexOf(key);
        if (s < 0) return null;
        s += key.length();
        int e = message.indexOf(" | ", s);
        return message.substring(s, e < 0 ? message.length() : e).trim();
    }

    private static String summary(Entry e)
    {
        String s = e.message.startsWith("[KSP-DBG] ") ? e.message.substring(10) : e.message;
        String prefix = e.type + " | ";
        if (s.startsWith(prefix)) s = s.substring(prefix.length());
        String rawPlugin = valueOf(e.message, "plugin=");
        if (rawPlugin != null) s = s.replace("plugin=" + rawPlugin + " | ", "");
        return plain(s);
    }

    private static String summaryWithoutType(Entry e)
    {
        String s = summary(e);
        return s.startsWith(e.type + " | ") ? s.substring(e.type.length() + 3) : s;
    }

    private static String after(String text, String key)
    {
        int i = text.indexOf(key);
        return i < 0 ? plain(text) : plain(text.substring(i + key.length()).trim());
    }

    private static String oneLine(String s) { return s == null ? "" : s.replace('\n', ' ').replace('\r', ' '); }
    private static String shorten(String s, int max) { return s == null || s.isEmpty() ? "-" : s.length() <= max ? s : s.substring(0, Math.max(1, max - 3)) + "..."; }
    private static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private static JPanel card()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        p.setBackground(CARD);
        p.setBorder(new EmptyBorder(8, 8, 8, 8));
        return p;
    }

    private static JPanel row(String name, JLabel value)
    {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel key = new JLabel(name);
        key.setForeground(MUTED);
        p.add(key, BorderLayout.WEST);
        p.add(value, BorderLayout.EAST);
        return p;
    }

    private static JLabel value(String text)
    {
        JLabel l = new JLabel(text);
        l.setForeground(GREEN);
        return l;
    }

    private static JTextArea textArea()
    {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setBackground(BG);
        a.setForeground(new Color(220, 220, 220));
        a.setCaretColor(Color.WHITE);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        return a;
    }

    private static final class Entry
    {
        final String time, level, plugin, pluginHtml, type, message;
        Entry(String time, String level, String plugin, String pluginHtml, String type, String message)
        {
            this.time = time; this.level = level; this.plugin = plugin; this.pluginHtml = pluginHtml; this.type = type; this.message = message;
        }
    }
}
