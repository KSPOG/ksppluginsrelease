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
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** RuneLite sidebar for controlling and monitoring the bank organizer. */
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
    private final JLabel messageValue = new JLabel("Idle");

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
        content.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("KSP Bank Organizer");
        title.setForeground(ACCENT);
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);

        JLabel version = new JLabel("v" + KspBankOrganizerPlugin.version);
        version.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        version.setFont(FontManager.getRunescapeSmallFont());
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(version);
        content.add(Box.createVerticalStrut(8));

        JPanel actions = section("Actions");
        configureWideButton(previewButton);
        configureWideButton(organizeButton);
        configureWideButton(stopButton);
        stopButton.setBackground(DANGER);

        previewButton.addActionListener(e -> plugin.startRun(OperationMode.PREVIEW));
        organizeButton.addActionListener(e -> plugin.startRun(OperationMode.ORGANIZE));
        stopButton.addActionListener(e -> plugin.stopRun());

        actions.add(previewButton);
        actions.add(Box.createVerticalStrut(4));
        actions.add(organizeButton);
        actions.add(Box.createVerticalStrut(4));
        actions.add(stopButton);
        content.add(actions);
        content.add(Box.createVerticalStrut(6));

        JPanel status = section("Status");
        status.add(row("Mode", modeValue));
        status.add(row("Phase", phaseValue));
        status.add(row("Stacks planned", plannedValue));
        status.add(row("Misplaced", misplacedValue));
        status.add(row("Moved", movedValue));
        status.add(row("Sort moves", sortedValue));
        content.add(status);
        content.add(Box.createVerticalStrut(6));

        JPanel mappings = section("Tab Mappings");
        mappings.add(mappingRow("Teleports", config.teleportsTarget(), ItemCategory.TELEPORTS));
        mappings.add(mappingRow("Combat", config.gearTarget(), ItemCategory.GEAR));
        mappings.add(mappingRow("Potions", config.potionsTarget(), ItemCategory.POTIONS));
        mappings.add(mappingRow("Food", config.foodTarget(), ItemCategory.FOOD));
        mappings.add(mappingRow("Skilling", config.skillingTarget(), ItemCategory.SKILLING));
        mappings.add(mappingRow("Materials", config.materialsTarget(), ItemCategory.RAW_MATERIALS));
        mappings.add(mappingRow("High Alch", config.highAlchTarget(), ItemCategory.HIGH_ALCH));
        mappings.add(mappingRow("Currency", config.currencyTarget(), ItemCategory.CURRENCY));
        mappings.add(mappingRow("Quest/Misc", config.questMiscTarget(), ItemCategory.QUEST_MISC));
        content.add(mappings);
        content.add(Box.createVerticalStrut(6));

        JPanel result = section("Last Result");
        messageValue.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        messageValue.setFont(FontManager.getRunescapeSmallFont());
        messageValue.setVerticalAlignment(JLabel.TOP);
        messageValue.setAlignmentX(Component.LEFT_ALIGNMENT);
        result.add(messageValue);
        content.add(result);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

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
        modeValue.setText(engine.activeMode().toString());
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
        messageValue.setText("<html><body style='width:205px'>" + escapeHtml(message) + "</body></html>");

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
            new EmptyBorder(6, 6, 6, 6)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(title);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(4));
        return panel;
    }

    private static JPanel row(String leftText, JLabel right)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel left = new JLabel(leftText);
        left.setForeground(Color.WHITE);
        left.setFont(FontManager.getRunescapeSmallFont());
        right.setHorizontalAlignment(JLabel.RIGHT);

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private static JPanel mappingRow(String name, BankTarget target, ItemCategory category)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel left = new JLabel("● " + name);
        left.setForeground(category.getColor());
        left.setFont(FontManager.getRunescapeSmallFont());

        JLabel right = new JLabel(target.toString());
        right.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        right.setFont(FontManager.getRunescapeSmallFont());

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private static JLabel valueLabel()
    {
        JLabel label = new JLabel("-");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }

    private static void configureWideButton(JButton button)
    {
        button.setFocusPainted(false);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
    }

    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
