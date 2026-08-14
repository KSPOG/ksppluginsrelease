package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Compact RuneLite sidebar for controlling and monitoring the bank organizer. */
final class KspBankOrganizerPanel extends PluginPanel
{
    private static final Color ACCENT = new Color(80, 220, 120);
    private static final Color DANGER = new Color(120, 45, 45);

    private final KspBankOrganizerPlugin plugin;
    private final BankOrganizerEngine engine;
    private final KspBankOrganizerConfig config;

    private final JLabel modeValue = valueLabel();
    private final JLabel phaseValue = valueLabel();
    private final JLabel plannedValue = valueLabel();
    private final JLabel misplacedValue = valueLabel();
    private final JLabel movedValue = valueLabel();
    private final JLabel sortedValue = valueLabel();
    private final JTextArea messageValue = new JTextArea();

    private final JButton previewButton = new JButton("Preview / Scan Bank");
    private final JButton organizeButton = new JButton("Organize Bank");
    private final JButton stopButton = new JButton("Stop");
    private final Timer refreshTimer;

    KspBankOrganizerPanel(KspBankOrganizerPlugin plugin, BankOrganizerEngine engine, KspBankOrganizerConfig config)
    {
        super(false);
        this.plugin = plugin;
        this.engine = engine;
        this.config = config;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(5, 5, 5, 5));

        JLabel title = new JLabel("KSP Bank Organizer");
        title.setForeground(ACCENT);
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);

        JLabel version = new JLabel("v" + KspBankOrganizerPlugin.version);
        version.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        version.setFont(FontManager.getRunescapeSmallFont());
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(version);
        content.add(Box.createVerticalStrut(3));

        JPanel actions = section("Actions");
        configureButton(previewButton);
        configureButton(organizeButton);
        configureButton(stopButton);
        stopButton.setBackground(DANGER);

        previewButton.addActionListener(e -> plugin.startRun(OperationMode.PREVIEW));
        organizeButton.addActionListener(e -> plugin.startRun(OperationMode.ORGANIZE));
        stopButton.addActionListener(e -> plugin.stopRun());

        actions.add(previewButton);
        actions.add(Box.createVerticalStrut(2));
        actions.add(organizeButton);
        actions.add(Box.createVerticalStrut(2));
        actions.add(stopButton);
        content.add(actions);
        content.add(Box.createVerticalStrut(3));

        JPanel status = section("Status");
        status.add(row("Mode", modeValue));
        status.add(row("Phase", phaseValue));
        status.add(row("Stacks planned", plannedValue));
        status.add(row("Misplaced", misplacedValue));
        status.add(row("Moved", movedValue));
        status.add(row("Sort moves", sortedValue));
        content.add(status);
        content.add(Box.createVerticalStrut(3));

        JPanel mappings = section("Tab Mappings");
        JPanel grid = new JPanel(new GridLayout(3, 3, 2, 2));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        grid.add(mappingCell("Teleports", config.teleportsTarget(), ItemCategory.TELEPORTS));
        grid.add(mappingCell("Combat", config.gearTarget(), ItemCategory.GEAR));
        grid.add(mappingCell("Potions", config.potionsTarget(), ItemCategory.POTIONS));
        grid.add(mappingCell("Food", config.foodTarget(), ItemCategory.FOOD));
        grid.add(mappingCell("Skilling", config.skillingTarget(), ItemCategory.SKILLING));
        grid.add(mappingCell("Materials", config.materialsTarget(), ItemCategory.RAW_MATERIALS));
        grid.add(mappingCell("High Alch", config.highAlchTarget(), ItemCategory.HIGH_ALCH));
        grid.add(mappingCell("Currency", config.currencyTarget(), ItemCategory.CURRENCY));
        grid.add(mappingCell("Quest/Misc", config.questMiscTarget(), ItemCategory.QUEST_MISC));
        mappings.add(grid);

        JLabel compactNote = new JLabel("Empty category tabs are skipped automatically.");
        compactNote.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        compactNote.setFont(FontManager.getRunescapeSmallFont().deriveFont(9f));
        compactNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        mappings.add(Box.createVerticalStrut(2));
        mappings.add(compactNote);

        content.add(mappings);
        content.add(Box.createVerticalStrut(3));

        JPanel result = section("Last Result");
        messageValue.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        messageValue.setFont(FontManager.getRunescapeSmallFont());
        messageValue.setLineWrap(true);
        messageValue.setWrapStyleWord(true);
        messageValue.setEditable(false);
        messageValue.setFocusable(false);
        messageValue.setOpaque(false);
        messageValue.setRows(3);
        messageValue.setAlignmentX(Component.LEFT_ALIGNMENT);
        result.add(messageValue);
        content.add(result);

        // Deliberately no JScrollPane: the complete control surface is sized to
        // fit the normal RuneLite plugin sidebar.
        add(content, BorderLayout.NORTH);

        refreshTimer = new Timer(250, e -> refresh());
        refreshTimer.start();
        refresh();
    }

    void dispose()
    {
        refreshTimer.stop();
    }

    private void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        boolean running = plugin.isRunActive();
        modeValue.setText(running ? engine.activeMode().displayName() : "Ready");
        phaseValue.setText(engine.phase());
        plannedValue.setText(String.valueOf(engine.plannedCount()));
        misplacedValue.setText(String.valueOf(engine.misplacedCount()));
        movedValue.setText(String.valueOf(engine.movedCount()));
        sortedValue.setText(String.valueOf(engine.sortedCount()));

        String message = engine.lastMessage();
        if (message == null || message.isBlank())
        {
            message = running ? "Organizer is running..." : "Idle";
        }
        messageValue.setText(message);
        messageValue.setForeground(messageLooksLikeError(message)
            ? new Color(255, 125, 125)
            : ColorScheme.LIGHT_GRAY_COLOR);

        previewButton.setEnabled(!running);
        organizeButton.setEnabled(!running);
        stopButton.setEnabled(running);
    }

    private static JPanel section(String title)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(4, 4, 4, 4)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(title);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(2));
        return panel;
    }

    private static JPanel row(String leftText, JLabel right)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 17));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel left = new JLabel(leftText);
        left.setForeground(Color.WHITE);
        left.setFont(FontManager.getRunescapeSmallFont());
        right.setHorizontalAlignment(JLabel.RIGHT);

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private static JPanel mappingCell(String name, BankTarget target, ItemCategory category)
    {
        JPanel cell = new JPanel(new BorderLayout(2, 0));
        cell.setOpaque(false);
        cell.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));

        JLabel left = new JLabel(name);
        left.setForeground(category.getColor());
        left.setFont(FontManager.getRunescapeSmallFont().deriveFont(9f));

        JLabel right = new JLabel(shortTarget(target));
        right.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        right.setFont(FontManager.getRunescapeSmallFont().deriveFont(9f));
        right.setHorizontalAlignment(JLabel.RIGHT);

        cell.add(left, BorderLayout.WEST);
        cell.add(right, BorderLayout.EAST);
        return cell;
    }

    private static String shortTarget(BankTarget target)
    {
        if (target == null) return "-";
        int tab = target.getTabIndex();
        return tab < 0 ? "Ignore" : tab == 0 ? "Main" : "Tab " + tab;
    }

    private static JLabel valueLabel()
    {
        JLabel label = new JLabel("-");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }

    private static void configureButton(JButton button)
    {
        button.setFocusPainted(false);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        button.setPreferredSize(new Dimension(0, 25));
    }

    private static boolean messageLooksLikeError(String message)
    {
        String lower = message.toLowerCase();
        return lower.contains("could not")
            || lower.contains("cannot")
            || lower.contains("failed")
            || lower.contains("error")
            || lower.contains("stopped");
    }
}
