package net.runelite.client.plugins.microbot.kspsupport;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/** Dedicated live console for centralized KSP runtime diagnostics only. */
final class KspDebugPanel extends PluginPanel
{
    private static final int MAX_ENTRIES = 1_500;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final JTextArea output = new JTextArea();
    private final JComboBox<String> levelFilter = new JComboBox<>(new String[]{"ALL", "INFO", "WARN"});
    private final JCheckBox autoScroll = new JCheckBox("Auto-scroll", true);
    private final JButton pauseButton = new JButton("Pause");
    private boolean paused;

    KspDebugPanel()
    {
        super();
        setLayout(new BorderLayout(0, 6));

        output.setEditable(false);
        output.setLineWrap(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton clearButton = new JButton("Clear");
        JButton copyButton = new JButton("Copy");
        controls.add(levelFilter);
        controls.add(pauseButton);
        controls.add(clearButton);
        controls.add(copyButton);
        controls.add(autoScroll);

        levelFilter.addActionListener(e -> rebuild());
        pauseButton.addActionListener(e ->
        {
            paused = !paused;
            pauseButton.setText(paused ? "Resume" : "Pause");
            if (!paused) rebuild();
        });
        clearButton.addActionListener(e -> clear());
        copyButton.addActionListener(e -> copyVisible());

        add(controls, BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);
    }

    void append(String level, String message)
    {
        String safeLevel = "WARN".equalsIgnoreCase(level) ? "WARN" : "INFO";
        String safeMessage = message == null ? "" : message.replace('\r', ' ').replace('\n', ' ');
        String time = LocalTime.now().format(TIME_FORMAT);

        SwingUtilities.invokeLater(() ->
        {
            entries.addLast(new Entry(time, safeLevel, safeMessage));
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
            if (!paused && matchesFilter(safeLevel))
            {
                output.append(format(time, safeLevel, safeMessage));
                if (autoScroll.isSelected()) output.setCaretPosition(output.getDocument().getLength());
            }
        });
    }

    void clear()
    {
        Runnable clear = () ->
        {
            entries.clear();
            output.setText("");
        };
        if (SwingUtilities.isEventDispatchThread()) clear.run();
        else SwingUtilities.invokeLater(clear);
    }

    private void rebuild()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::rebuild);
            return;
        }

        StringBuilder text = new StringBuilder();
        for (Entry entry : new ArrayList<>(entries))
        {
            if (matchesFilter(entry.level)) text.append(format(entry.time, entry.level, entry.message));
        }
        output.setText(text.toString());
        if (autoScroll.isSelected()) output.setCaretPosition(output.getDocument().getLength());
    }

    private boolean matchesFilter(String level)
    {
        Object selected = levelFilter.getSelectedItem();
        return selected == null || "ALL".equals(selected) || selected.equals(level);
    }

    private void copyVisible()
    {
        String text = output.getText();
        if (text == null || text.isEmpty()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private static String format(String time, String level, String message)
    {
        return '[' + time + "] [" + level + "] " + message + System.lineSeparator();
    }

    private static final class Entry
    {
        final String time;
        final String level;
        final String message;

        Entry(String time, String level, String message)
        {
            this.time = time;
            this.level = level;
            this.message = message;
        }
    }
}
