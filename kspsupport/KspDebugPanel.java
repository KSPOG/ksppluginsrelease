package net.runelite.client.plugins.microbot.kspsupport;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Compact RuneLite sidebar + full KSP-only debug console. */
final class KspDebugPanel extends PluginPanel
{
    private static final int MAX_ENTRIES = 2_500;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color BG = new Color(30, 30, 30);
    private static final Color PANEL = new Color(40, 40, 40);
    private static final Color TEXT = new Color(220, 220, 220);
    private static final Color MUTED = new Color(155, 155, 155);
    private static final Color GREEN = new Color(120, 220, 140);
    private static final Color ORANGE = new Color(240, 180, 80);

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Set<String> plugins = new LinkedHashSet<>();

    private final JLabel activeLabel = valueLabel("0");
    private final JLabel eventLabel = valueLabel("0");
    private final JLabel warnLabel = valueLabel("0");
    private final JLabel pluginLabel = valueLabel("-");
    private final JLabel stateLabel = valueLabel("-");
    private final JTextArea recent = textArea(true);

    private JFrame console;
    private JTextArea consoleText;
    private JComboBox<String> levelFilter;
    private JComboBox<String> pluginFilter;
    private JTextField searchField;
    private JCheckBox autoScroll;
    private JButton pauseButton;

    private boolean paused;
    private int activeCount;
    private int warningCount;

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
        stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
        stats.add(row("Active plugins", activeLabel));
        stats.add(row("Events", eventLabel));
        stats.add(row("Warnings", warnLabel));
        body.add(stats);
        body.add(Box.createVerticalStrut(8));

        JPanel current = card();
        current.setLayout(new BoxLayout(current, BoxLayout.Y_AXIS));
        current.add(smallTitle("CURRENT"));
        current.add(row("Plugin", pluginLabel));
        current.add(row("State", stateLabel));
        body.add(current);
        body.add(Box.createVerticalStrut(8));

        JButton open = new JButton("Open Debug Console");
        open.setAlignmentX(LEFT_ALIGNMENT);
        open.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        open.addActionListener(e -> openConsole());
        body.add(open);
        body.add(Box.createVerticalStrut(8));

        body.add(smallTitle("RECENT EVENTS"));
        recent.setRows(14);
        JScrollPane recentScroll = new JScrollPane(recent);
        recentScroll.setAlignmentX(LEFT_ALIGNMENT);
        recentScroll.setBorder(BorderFactory.createLineBorder(new Color(55, 55, 55)));
        body.add(recentScroll);

        add(body, BorderLayout.CENTER);
    }

    void append(String level, String message)
    {
        String safeLevel = "WARN".equalsIgnoreCase(level) ? "WARN" : "INFO";
        String clean = message == null ? "" : message.replace('\r', ' ').replace('\n', ' ');
        Entry entry = new Entry(LocalTime.now().format(TIME), safeLevel, pluginOf(clean), typeOf(clean), clean);

        SwingUtilities.invokeLater(() ->
        {
            entries.addLast(entry);
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
            if (!entry.plugin.equals("System")) plugins.add(entry.plugin);
            if (entry.level.equals("WARN")) warningCount++;

            updateSummary(entry);
            eventLabel.setText(Integer.toString(entries.size()));
            warnLabel.setText(Integer.toString(warningCount));
            refreshPluginFilter();
            refreshRecent();
            if (!paused) rebuildConsole();
        });
    }

    void setActiveCount(int count)
    {
        SwingUtilities.invokeLater(() ->
        {
            activeCount = Math.max(0, count);
            activeLabel.setText(Integer.toString(activeCount));
        });
    }

    void disposeConsole()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (console != null) console.dispose();
            console = null;
        });
    }

    private void updateSummary(Entry e)
    {
        if (!e.plugin.equals("System")) pluginLabel.setText(shorten(e.plugin, 24));
        if ("state-change".equals(e.type)) stateLabel.setText(shorten(after(e.message, "changed="), 28));
        else if ("lifecycle".equals(e.type)) stateLabel.setText("Lifecycle changed");
        else if ("unloaded".equals(e.type)) stateLabel.setText("Unloaded");
    }

    private void refreshRecent()
    {
        StringBuilder out = new StringBuilder();
        List<Entry> copy = new ArrayList<>(entries);
        int start = Math.max(0, copy.size() - 12);
        for (int i = start; i < copy.size(); i++)
        {
            Entry e = copy.get(i);
            if ("heartbeat".equals(e.type)) continue;
            out.append(e.time).append(' ')
               .append(e.level.equals("WARN") ? "! " : "  ")
               .append(shorten(e.plugin, 18)).append(" · ")
               .append(shorten(summary(e), 70)).append('\n');
        }
        recent.setText(out.toString());
        recent.setCaretPosition(recent.getDocument().getLength());
    }

    private void openConsole()
    {
        if (console == null)
        {
            console = new JFrame("KSP Debug Console");
            console.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            console.setMinimumSize(new Dimension(900, 520));
            console.setSize(1150, 680);
            console.setLocationByPlatform(true);
            console.getContentPane().setBackground(BG);
            console.setLayout(new BorderLayout());

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
            toolbar.setBackground(PANEL);

            levelFilter = new JComboBox<>(new String[]{"ALL", "INFO", "WARN"});
            pluginFilter = new JComboBox<>(new String[]{"ALL PLUGINS"});
            searchField = new JTextField(22);
            searchField.putClientProperty("JTextField.placeholderText", "Search logs...");
            autoScroll = new JCheckBox("Auto-scroll", true);
            pauseButton = new JButton("Pause");
            JButton clear = new JButton("Clear");
            JButton copy = new JButton("Copy");

            toolbar.add(levelFilter);
            toolbar.add(pluginFilter);
            toolbar.add(searchField);
            toolbar.add(pauseButton);
            toolbar.add(clear);
            toolbar.add(copy);
            toolbar.add(autoScroll);

            levelFilter.addActionListener(e -> rebuildConsole());
            pluginFilter.addActionListener(e -> rebuildConsole());
            searchField.addActionListener(e -> rebuildConsole());
            pauseButton.addActionListener(e ->
            {
                paused = !paused;
                pauseButton.setText(paused ? "Resume" : "Pause");
                if (!paused) rebuildConsole();
            });
            clear.addActionListener(e -> clear());
            copy.addActionListener(e -> copyConsole());

            consoleText = textArea(false);
            consoleText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane logScroll = new JScrollPane(consoleText);

            JTextArea help = textArea(true);
            help.setText("KSP DEBUG\n\n"
                    + "Only KSP diagnostic events are shown here.\n\n"
                    + "INFO  lifecycle/state/heartbeat\n"
                    + "WARN  duplicates/errors\n\n"
                    + "The sidebar stays compact while this window provides the full log stream.");
            help.setBorder(new EmptyBorder(10, 10, 10, 10));
            JScrollPane helpScroll = new JScrollPane(help);
            helpScroll.setPreferredSize(new Dimension(230, 0));

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, helpScroll, logScroll);
            split.setDividerLocation(230);
            split.setResizeWeight(0.0);
            split.setBorder(null);

            console.add(toolbar, BorderLayout.NORTH);
            console.add(split, BorderLayout.CENTER);
            refreshPluginFilter();
            rebuildConsole();
        }

        console.setVisible(true);
        console.toFront();
    }

    private void clear()
    {
        entries.clear();
        warningCount = 0;
        warnLabel.setText("0");
        eventLabel.setText("0");
        recent.setText("");
        if (consoleText != null) consoleText.setText("");
    }

    private void copyConsole()
    {
        if (consoleText == null || consoleText.getText().isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new StringSelection(consoleText.getText()), null);
    }

    private void rebuildConsole()
    {
        if (consoleText == null) return;

        String level = levelFilter == null ? "ALL" : String.valueOf(levelFilter.getSelectedItem());
        String plugin = pluginFilter == null ? "ALL PLUGINS" : String.valueOf(pluginFilter.getSelectedItem());
        String search = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        StringBuilder out = new StringBuilder();

        for (Entry e : entries)
        {
            if (!"ALL".equals(level) && !level.equals(e.level)) continue;
            if (!"ALL PLUGINS".equals(plugin) && !plugin.equals(e.plugin)) continue;
            if (!search.isEmpty() && !e.message.toLowerCase().contains(search)) continue;

            out.append('[').append(e.time).append("] [").append(e.level).append("] ")
               .append('[').append(e.plugin).append("] ")
               .append(summary(e)).append(System.lineSeparator());
        }

        consoleText.setText(out.toString());
        if (autoScroll == null || autoScroll.isSelected())
            consoleText.setCaretPosition(consoleText.getDocument().getLength());
    }

    private void refreshPluginFilter()
    {
        if (pluginFilter == null) return;
        String selected = String.valueOf(pluginFilter.getSelectedItem());
        pluginFilter.removeAllItems();
        pluginFilter.addItem("ALL PLUGINS");
        for (String plugin : plugins) pluginFilter.addItem(plugin);
        pluginFilter.setSelectedItem(selected);
        if (pluginFilter.getSelectedIndex() < 0) pluginFilter.setSelectedIndex(0);
    }

    private static JPanel card()
    {
        JPanel panel = new JPanel();
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        panel.setBackground(PANEL);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        return panel;
    }

    private static JPanel row(String name, JLabel value)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel key = new JLabel(name);
        key.setForeground(MUTED);
        row.add(key, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private static JLabel smallTitle(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ORANGE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel valueLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(GREEN);
        return label;
    }

    private static JTextArea textArea(boolean wrap)
    {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(wrap);
        area.setWrapStyleWord(wrap);
        area.setBackground(BG);
        area.setForeground(TEXT);
        area.setCaretColor(Color.WHITE);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        return area;
    }

    private static String pluginOf(String message)
    {
        String plugin = value(message, "plugin=");
        return plugin == null || plugin.isEmpty() ? "System" : plugin;
    }

    private static String typeOf(String message)
    {
        String s = message.startsWith("[KSP-DBG] ") ? message.substring(10) : message;
        int pipe = s.indexOf('|');
        return (pipe < 0 ? s : s.substring(0, pipe)).trim();
    }

    private static String value(String message, String key)
    {
        int start = message.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = message.indexOf(" | ", start);
        if (end < 0) end = message.length();
        return message.substring(start, end).trim();
    }

    private static String after(String text, String key)
    {
        int i = text.indexOf(key);
        return i < 0 ? text : text.substring(i + key.length()).trim();
    }

    private static String summary(Entry e)
    {
        String s = e.message.startsWith("[KSP-DBG] ") ? e.message.substring(10) : e.message;
        String prefix = e.type + " | ";
        if (s.startsWith(prefix)) s = s.substring(prefix.length());
        s = s.replace("plugin=" + e.plugin + " | ", "");
        return s;
    }

    private static String shorten(String value, int max)
    {
        if (value == null || value.isEmpty()) return "-";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 3)) + "...";
    }

    private static final class Entry
    {
        final String time, level, plugin, type, message;

        Entry(String time, String level, String plugin, String type, String message)
        {
            this.time = time;
            this.level = level;
            this.plugin = plugin;
            this.type = type;
            this.message = message;
        }
    }
}
