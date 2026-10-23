package net.runelite.client.plugins.microbot.kspaccountbuilder;

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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Compact Account Builder control surface. RuneLite config remains the single
 * source of truth: both the sidebar and standard settings read/write the same keys.
 */
final class KspAccountBuilderPanel extends PluginPanel
{
    private static final Color ACCENT = new Color(255, 226, 138);
    private static final Color PANEL_DARK = new Color(16, 24, 25);
    private static final Color GOLD_BORDER = new Color(201, 155, 43);
    private final KspAccountBuilderPlugin plugin;
    private final KspAccountBuilderConfig config;
    private final KspAccountBuilderScript script;
    private final ConfigManager configManager;

    private final JButton startStop = new JButton();
    private final JLabel status = label();
    private final JLabel task = label();
    private final JLabel runtime = label();
    private final JLabel breakTimer = label();
    private final JLabel switchTimer = label();
    private final JComboBox<String> mode = new JComboBox<>(new String[]{"Automatic", "Single skill", "Single quest"});
    private final JComboBox<KspTrainSingleSkillTask> skill = new JComboBox<>(KspTrainSingleSkillTask.values());
    private final JComboBox<KspQuestTask> quest = new JComboBox<>(KspQuestTask.values());
    private final JComboBox<Enum<?>> target = new JComboBox<>();
    private final JCheckBox compactHud = new JCheckBox("Compact in-game HUD");
    private final JCheckBox breaks = new JCheckBox("Enable breaks");
    private final JCheckBox antiban = new JCheckBox("Enable anti-ban");
    private final JCheckBox skipQuests = new JCheckBox("Skip quests in automatic mode");
    private final JCheckBox randomSwitch = new JCheckBox("Randomize activity switches");
    private final Timer timer;
    private boolean updating;

    KspAccountBuilderPanel(KspAccountBuilderPlugin plugin, KspAccountBuilderConfig config,
                           KspAccountBuilderScript script, ConfigManager configManager)
    {
        super(false);
        this.plugin = plugin;
        this.config = config;
        this.script = script;
        this.configManager = configManager;
        setLayout(new BorderLayout());
        setBackground(PANEL_DARK);

        JPanel content = new JPanel();
        content.setBackground(PANEL_DARK);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD_BORDER, 1), new EmptyBorder(10, 9, 10, 9)));

        JLabel title = new JLabel("KSP Account Builder");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(ACCENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        JLabel version = new JLabel("v" + KspAccountBuilderPlugin.VERSION);
        version.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(version);
        content.add(Box.createVerticalStrut(8));

        JPanel controls = section("Runtime");
        startStop.setAlignmentX(Component.LEFT_ALIGNMENT);
        startStop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        startStop.setFocusPainted(false);
        startStop.addActionListener(e -> configManager.setConfiguration(
                KspAccountBuilderConfig.CONFIG_GROUP, "runAccountBuilder", !config.runAccountBuilder()));
        startStop.setToolTipText("Starts or stops the existing Account Builder runtime. Stopping cancels the current automation.");
        controls.add(startStop);
        controls.add(pair("State", status));
        controls.add(pair("Task", task));
        controls.add(pair("Runtime", runtime));
        controls.add(pair("Next break", breakTimer));
        controls.add(pair("Activity switch", switchTimer));
        content.add(controls);
        content.add(Box.createVerticalStrut(6));

        JPanel selection = section("Task selection (stop to edit)");
        mode.setToolTipText("Choose automatic progression, one skill, or one quest while stopped.");
        skill.setToolTipText("Select the skill to train while in single skill mode.");
        quest.setToolTipText("Select the quest to complete while in single quest mode.");
        target.setToolTipText("Select the configured training target for the selected skill.");
        addControl(selection, "Mode", mode);
        addControl(selection, "Skill", skill);
        addControl(selection, "Target", target);
        addControl(selection, "Quest", quest);
        mode.addActionListener(e -> {
            if (updating) return;
            int selected = mode.getSelectedIndex();
            configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "runSingleQuest", selected == 2);
            configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "trainSingleSkill", selected == 1);
            refresh();
        });
        skill.addActionListener(e -> {
            if (updating || skill.getSelectedItem() == null) return;
            configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "singleSkillTask", skill.getSelectedItem());
            populateTarget();
        });
        target.addActionListener(e -> {
            if (updating || target.getSelectedItem() == null) return;
            String key = targetKey((KspTrainSingleSkillTask) skill.getSelectedItem());
            if (key != null) configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, key, target.getSelectedItem());
        });
        quest.addActionListener(e -> {
            if (!updating && quest.getSelectedItem() != null)
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "singleQuestTask", quest.getSelectedItem());
        });
        content.add(selection);
        content.add(Box.createVerticalStrut(6));

        JPanel quick = section("Quick settings");
        compactHud.setOpaque(false);
        compactHud.setForeground(new Color(242, 235, 221));
        compactHud.addActionListener(e -> { if (!updating) { configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "compactHud", compactHud.isSelected()); plugin.setCompactOverlay(compactHud.isSelected()); } });
        compactHud.setToolTipText("Switch between the full and compact in-game HUD without restarting the current task.");
        quick.add(compactHud);
        breaks.setToolTipText("Enable the configured randomized break schedule.");
        antiban.setToolTipText("Use the existing Account Builder anti-ban profile.");
        skipQuests.setToolTipText("Skip quest tasks during automatic progression.");
        randomSwitch.setToolTipText("Randomize automatic skilling activity switches.");
        addCheck(quick, breaks, "doBreaks");
        addCheck(quick, antiban, "useAntiban");
        addCheck(quick, skipQuests, "skipQuests");
        addCheck(quick, randomSwitch, "enableActivitySwitchRandomization");
        content.add(quick);
        content.add(Box.createVerticalStrut(6));

        JLabel note = new JLabel("<html><small>Advanced anti-ban intensity, break and activity timers remain in plugin configuration.</small></html>");
        note.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(note);
        add(content, BorderLayout.NORTH);

        timer = new Timer(500, e -> refresh());
        timer.start();
        refresh();
    }

    void dispose() { timer.stop(); }

    private void addCheck(JPanel parent, JCheckBox check, String key)
    {
        check.setOpaque(false);
        check.setForeground(new Color(242, 235, 221));
        check.setAlignmentX(Component.LEFT_ALIGNMENT);
        check.addActionListener(e -> {
            if (!updating) configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, key, check.isSelected());
        });
        parent.add(check);
    }

    private void populateTarget()
    {
        boolean wasUpdating = updating;
        updating = true;
        target.removeAllItems();
        KspTrainSingleSkillTask selected = (KspTrainSingleSkillTask) skill.getSelectedItem();
        Enum<?>[] options = targets(selected);
        for (Enum<?> option : options) target.addItem(option);
        Object configured = selectedTarget(selected);
        if (configured != null) target.setSelectedItem(configured);
        updating = wasUpdating;
    }

    private static String targetKey(KspTrainSingleSkillTask skill)
    {
        if (skill == null) return null;
        switch (skill)
        {
            case MINING: return "singleSkillMiningTarget";
            case WOODCUTTING: return "singleSkillWoodcuttingTarget";
            case FISHING: return "singleSkillFishingTarget";
            case COOKING: return "singleSkillCookingTarget";
            case CRAFTING: return "singleSkillCraftingTarget";
            case MELEE: return "singleSkillMeleeTarget";
            case SMITHING: return "singleSkillSmithingTarget";
            case SMELTING: return "singleSkillSmeltingTarget";
            default: return null;
        }
    }

    private static Enum<?>[] targets(KspTrainSingleSkillTask skill)
    {
        if (skill == null) return new Enum<?>[0];
        switch (skill)
        {
            case MINING: return KspSingleSkillConfigTarget.Mining.values();
            case WOODCUTTING: return KspSingleSkillConfigTarget.Woodcutting.values();
            case FISHING: return KspSingleSkillConfigTarget.Fishing.values();
            case COOKING: return KspSingleSkillConfigTarget.Cooking.values();
            case CRAFTING: return KspSingleSkillConfigTarget.Crafting.values();
            case MELEE: return KspSingleSkillConfigTarget.Melee.values();
            case SMITHING: return KspSingleSkillConfigTarget.Smithing.values();
            case SMELTING: return KspSingleSkillConfigTarget.Smelting.values();
            default: return new Enum<?>[0];
        }
    }

    private Object selectedTarget(KspTrainSingleSkillTask skill)
    {
        if (skill == null) return null;
        switch (skill)
        {
            case MINING: return config.singleSkillMiningTarget();
            case WOODCUTTING: return config.singleSkillWoodcuttingTarget();
            case FISHING: return config.singleSkillFishingTarget();
            case COOKING: return config.singleSkillCookingTarget();
            case CRAFTING: return config.singleSkillCraftingTarget();
            case MELEE: return config.singleSkillMeleeTarget();
            case SMITHING: return config.singleSkillSmithingTarget();
            case SMELTING: return config.singleSkillSmeltingTarget();
            default: return null;
        }
    }

    private void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::refresh); return; }
        updating = true;
        boolean running = plugin.isRuntimeActive();
        startStop.setText(running ? "STOP ACCOUNT BUILDER" : "START ACCOUNT BUILDER");
        startStop.setBackground(running ? new Color(145, 62, 62) : new Color(54, 117, 80));
        status.setText(running ? (script.isBreakActive() ? "On break" : "Running") : "Stopped");
        task.setText(running ? script.getCurrentTaskName() : "-");
        runtime.setText(running ? duration(script.getRuntimeSeconds()) : "-");
        breakTimer.setText(running ? duration(script.getTimeUntilBreakSeconds()) : "-");
        switchTimer.setText(running ? duration(script.getTimeUntilActivitySwitchSeconds()) : "-");

        int requested = config.runSingleQuest() ? 2 : config.trainSingleSkill() ? 1 : 0;
        if (mode.getSelectedIndex() != requested) mode.setSelectedIndex(requested);
        if (skill.getSelectedItem() != config.singleSkillTask())
        {
            skill.setSelectedItem(config.singleSkillTask());
            populateTarget();
        }
        else if (target.getItemCount() == 0) populateTarget();
        Object currentTarget = selectedTarget(config.singleSkillTask());
        if (currentTarget != null && target.getSelectedItem() != currentTarget) target.setSelectedItem(currentTarget);
        if (quest.getSelectedItem() != config.singleQuestTask()) quest.setSelectedItem(config.singleQuestTask());
        compactHud.setSelected(plugin.isCompactOverlay());
        breaks.setSelected(config.doBreaks());
        antiban.setSelected(config.useAntiban());
        skipQuests.setSelected(config.skipQuests());
        randomSwitch.setSelected(config.enableActivitySwitchRandomization());

        mode.setEnabled(!running);
        skill.setEnabled(!running && requested == 1);
        target.setEnabled(!running && requested == 1 && target.getItemCount() > 0);
        quest.setEnabled(!running && requested == 2);
        updating = false;
    }

    private static String duration(long seconds)
    {
        if (seconds < 0) return "-";
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }

    private static JLabel label()
    {
        JLabel result = new JLabel("-");
        result.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        return result;
    }

    private static JPanel section(String name)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(6, 6, 6, 6)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(name);
        label.setForeground(ACCENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(3));
        return panel;
    }

    private static JPanel pair(String title, JLabel value)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 19));
        JLabel caption = new JLabel(title);
        caption.setForeground(Color.WHITE);
        row.add(caption, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private static void addControl(JPanel panel, String title, JComboBox<?> control)
    {
        JLabel label = new JLabel(title);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        panel.add(control);
        panel.add(Box.createVerticalStrut(3));
    }
}
