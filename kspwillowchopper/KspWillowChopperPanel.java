package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * RuneLite side-panel settings for the Chopper. ConfigManager remains the
 * single source of truth, so settings survive a restart and hot reload.
 */
final class KspWillowChopperPanel extends PluginPanel {
    private final KspWillowChopperConfig config;
    private final ConfigManager configManager;
    private final KspWillowChopperScript script;
    private final KspWillowChopperPlugin plugin;
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");
    private final JButton pauseButton = new JButton("Pause");
    private final JComboBox<KspTree> treeSelector = new JComboBox<>(KspTree.values());
    private final JComboBox<KspChopperMode> modeSelector = new JComboBox<>(KspChopperMode.values());
    private final JCheckBox overlayToggle = new JCheckBox("Show in-game overlay");
    private final JCheckBox forestryToggle = new JCheckBox("Enable Forestry events");
    private final List<EventToggle> forestryEvents = new ArrayList<>();
    private final JLabel hint = new JLabel();
    private final JLabel status = new JLabel();
    private final Timer statusTimer = new Timer(1000, event -> refreshStatus());
    private boolean refreshing;

    KspWillowChopperPanel(KspWillowChopperConfig config, ConfigManager configManager,
                          KspWillowChopperScript script, KspWillowChopperPlugin plugin) {
        this.config = config;
        this.configManager = configManager;
        this.script = script;
        this.plugin = plugin;

        JLabel title = new JLabel("KSP Chopper", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title);

        JLabel subtitle = new JLabel("<html><center>Choose what to chop and how to handle resources.</center></html>",
                SwingConstants.CENTER);
        subtitle.setForeground(Color.LIGHT_GRAY);
        add(subtitle);

        add(heading("Controls"));
        JPanel buttons = new JPanel(new GridLayout(1, 3, 5, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttons.add(startButton);
        buttons.add(pauseButton);
        buttons.add(stopButton);
        add(buttons);
        startButton.addActionListener(event -> {
            plugin.startChopping();
            refreshStatus();
        });
        pauseButton.addActionListener(event -> {
            plugin.togglePauseChopping();
            refreshStatus();
        });
        stopButton.addActionListener(event -> {
            plugin.stopChopping();
            refreshStatus();
        });

        add(heading("Resource collection"));
        treeSelector.setMaximumRowCount(12);
        add(settingRow("Tree / Resource", treeSelector));
        modeSelector.setMaximumRowCount(6);
        add(settingRow("Resource handling", modeSelector));
        hint.setForeground(new Color(255, 203, 118));
        add(hint);

        treeSelector.addActionListener(event -> {
            if (!refreshing && treeSelector.getSelectedItem() instanceof KspTree) {
                configManager.setConfiguration(KspWillowChopperConfig.GROUP, "tree",
                        ((KspTree) treeSelector.getSelectedItem()).name());
                updateHint();
            }
        });
        modeSelector.addActionListener(event -> {
            if (!refreshing && modeSelector.getSelectedItem() instanceof KspChopperMode) {
                configManager.setConfiguration(KspWillowChopperConfig.GROUP, "mode",
                        ((KspChopperMode) modeSelector.getSelectedItem()).name());
                updateHint();
            }
        });

        add(heading("Display"));
        add(dark(overlayToggle));
        overlayToggle.addActionListener(event -> setBoolean("showOverlay", overlayToggle.isSelected()));

        add(heading("Forestry"));
        add(dark(forestryToggle));
        forestryToggle.addActionListener(event -> {
            setBoolean("enableForestry", forestryToggle.isSelected());
            setForestryEnabled(forestryToggle.isSelected());
        });

        addForestryEvent("Rising Roots", "rootEvent", config::rootEvent);
        addForestryEvent("Struggling Sapling", "saplingEvent", config::saplingEvent);
        addForestryEvent("Friendly Entlings", "entlingsEvent", config::entlingsEvent);
        addForestryEvent("Beehives", "hivesEvent", config::hivesEvent);
        addForestryEvent("Pheasant Control", "eggEvent", config::eggEvent);
        addForestryEvent("Poachers / Fox", "foxEvent", config::foxEvent);
        addForestryEvent("Enchantment Ritual", "ritualEvent", config::ritualEvent);
        addForestryEvent("Woodcutting Leprechaun", "leprechaunEvent", config::leprechaunEvent);
        addForestryEvent("Flowering Tree", "flowersEvent", config::flowersEvent);

        add(heading("Runtime"));
        status.setForeground(Color.LIGHT_GRAY);
        add(status);
        JLabel note = new JLabel("<html><center>Changes are saved immediately.<br>"
                + "Breaks and Local Mule remain in plugin configuration.</center></html>",
                SwingConstants.CENTER);
        note.setForeground(Color.LIGHT_GRAY);
        add(note);

        refresh();
    }

    private void addForestryEvent(String title, String key, BooleanSupplier read) {
        JCheckBox check = new JCheckBox(title);
        dark(check);
        check.setBorder(new EmptyBorder(0, 8, 0, 0));
        check.addActionListener(event -> setBoolean(key, check.isSelected()));
        forestryEvents.add(new EventToggle(check, read));
        add(check);
    }

    private void setBoolean(String key, boolean value) {
        if (!refreshing) {
            configManager.setConfiguration(KspWillowChopperConfig.GROUP, key, value);
        }
    }

    void refresh() {
        refreshing = true;
        try {
            treeSelector.setSelectedItem(config.tree());
            modeSelector.setSelectedItem(config.mode());
            overlayToggle.setSelected(config.showOverlay());
            forestryToggle.setSelected(config.enableForestry());
            for (EventToggle event : forestryEvents) {
                event.check.setSelected(event.read.getAsBoolean());
            }
            setForestryEnabled(config.enableForestry());
            updateHint();
            refreshStatus();
        } finally {
            refreshing = false;
        }
    }

    private void updateHint() {
        KspTree tree = (KspTree) treeSelector.getSelectedItem();
        KspChopperMode mode = (KspChopperMode) modeSelector.getSelectedItem();
        boolean progressive = mode != null && mode.isProgressive();
        treeSelector.setEnabled(!progressive);
        if (progressive) {
            hint.setText(mode.isBurn()
                    ? "<html>Auto-select trees by Woodcutting and<br>Firemaking; burns logs before upgrading.</html>"
                    : "<html>Auto-select trees by Woodcutting level.<br>Manual tree selection remains saved.</html>");
        } else if (tree != null && mode != null && mode.isBurn() && !tree.isCampfireBurnable()) {
            hint.setText("<html>This resource cannot be burned.<br>Choose Bank or Drop.</html>");
        } else {
            hint.setText("<html>Bank stores resources, Burn uses<br>a campfire, Drop clears inventory.</html>");
        }
    }

    private void setForestryEnabled(boolean enabled) {
        for (EventToggle event : forestryEvents) {
            event.check.setEnabled(enabled);
        }
    }

    private void refreshStatus() {
        if (script != null) {
            boolean started = script.hasSessionStarted();
            startButton.setEnabled(!started);
            stopButton.setEnabled(started);
            pauseButton.setEnabled(started);
            pauseButton.setText(script.isUserPaused() ? "Resume" : "Pause");
            status.setText("<html><b>" + (started ? script.getState() : "Stopped")
                    + "</b><br>" + (started ? script.getStatus() : "Press Start to chop") + "</html>");
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refresh();
        statusTimer.start();
    }

    @Override
    public void removeNotify() {
        statusTimer.stop();
        super.removeNotify();
    }

    private static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(new EmptyBorder(10, 2, 3, 2));
        return label;
    }

    private static JPanel settingRow(String title, JComponent control) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel label = new JLabel(title);
        label.setForeground(Color.LIGHT_GRAY);
        row.add(label, BorderLayout.NORTH);
        row.add(control, BorderLayout.CENTER);
        return row;
    }

    private static <T extends JComponent> T dark(T control) {
        control.setBackground(ColorScheme.DARK_GRAY_COLOR);
        control.setForeground(Color.WHITE);
        return control;
    }

    private static final class EventToggle {
        private final JCheckBox check;
        private final BooleanSupplier read;

        private EventToggle(JCheckBox check, BooleanSupplier read) {
            this.check = check;
            this.read = read;
        }
    }
}
