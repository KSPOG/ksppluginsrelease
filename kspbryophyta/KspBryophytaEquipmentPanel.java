package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class KspBryophytaEquipmentPanel extends PluginPanel {
    private static final int CANVAS_WIDTH = 213;
    private static final int SOURCE_WIDTH = 288;
    private static final int SOURCE_HEIGHT = 309;
    private static final int CANVAS_HEIGHT = Math.round((float) SOURCE_HEIGHT * CANVAS_WIDTH / SOURCE_WIDTH);

    private static final Map<EquipmentInventorySlot, Rectangle> SOURCE_SLOT_BOUNDS = new EnumMap<>(EquipmentInventorySlot.class);

    static {
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.HEAD, new Rectangle(117, 8, 53, 53));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.CAPE, new Rectangle(54, 66, 54, 55));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.AMULET, new Rectangle(116, 66, 55, 55));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.AMMO, new Rectangle(178, 66, 55, 55));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.WEAPON, new Rectangle(34, 125, 56, 55));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.BODY, new Rectangle(116, 125, 55, 55));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.SHIELD, new Rectangle(196, 125, 56, 55));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.LEGS, new Rectangle(116, 184, 55, 55));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.GLOVES, new Rectangle(35, 240, 55, 55));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.BOOTS, new Rectangle(116, 240, 55, 55));
        SOURCE_SLOT_BOUNDS.put(EquipmentInventorySlot.RING, new Rectangle(196, 240, 55, 55));
    }

    private final BryophytaEquipmentSettings equipmentSettings;
    private final BryophytaEquipmentIndex equipmentIndex;
    private final ItemManager itemManager;
    private final KspBryophytaScript script;
    private final KspBryophytaConfig config;

    private final JComboBox<BryophytaStrategy> strategySelector = new JComboBox<>(BryophytaStrategy.values());
    private final EquipmentCanvas equipmentCanvas;
    private final JLabel infoLabel = new JLabel(" ");
    private final JLabel runStateLabel = new JLabel(" ");
    private final JButton startStopButton = new JButton();
    private final javax.swing.Timer runStateTimer;
    private volatile boolean controlBusy;

    KspBryophytaEquipmentPanel(
            BryophytaEquipmentSettings equipmentSettings,
            BryophytaEquipmentIndex equipmentIndex,
            ItemManager itemManager,
            BryophytaStrategy initialStrategy,
            KspBryophytaScript script,
            KspBryophytaConfig config) {
        super();
        this.equipmentSettings = equipmentSettings;
        this.equipmentIndex = equipmentIndex;
        this.itemManager = itemManager;
        this.script = script;
        this.config = config;

        BufferedImage background = BryophytaEquipmentAssets.loadEquipmentSlots();

        equipmentCanvas = new EquipmentCanvas(background);

        buildUi();
        strategySelector.setSelectedItem(initialStrategy == null ? BryophytaStrategy.MELEE : initialStrategy);
        refreshCanvas();
        refreshRunControls();

        runStateTimer = new javax.swing.Timer(500, e -> refreshRunControls());
        runStateTimer.setRepeats(true);
        runStateTimer.start();
    }
    private void buildUi() {
        JLabel title = centeredLabel("Bryophyta Equipment", Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title);
        add(centeredLabel("<html><center>Click an equipment slot to choose an item.<br>Each strategy stores its own setup.</center></html>", Color.LIGHT_GRAY));

        JPanel runControls = darkPanel(new BorderLayout(0, 4));
        runControls.setBorder(new EmptyBorder(6, 0, 8, 0));
        startStopButton.setFocusPainted(false);
        startStopButton.setPreferredSize(new Dimension(CANVAS_WIDTH, 34));
        startStopButton.setMaximumSize(new Dimension(CANVAS_WIDTH, 34));
        startStopButton.addActionListener(e -> toggleScript());
        runControls.add(startStopButton, BorderLayout.CENTER);
        runStateLabel.setHorizontalAlignment(SwingConstants.CENTER);
        runStateLabel.setForeground(Color.LIGHT_GRAY);
        runControls.add(runStateLabel, BorderLayout.SOUTH);
        add(runControls);

        JPanel strategyRow = darkPanel(new BorderLayout(6, 0));
        JLabel strategyLabel = new JLabel("Edit setup:");
        strategyLabel.setForeground(Color.WHITE);
        strategyRow.add(strategyLabel, BorderLayout.WEST);
        strategySelector.addActionListener(e -> refreshCanvas());
        strategyRow.add(strategySelector, BorderLayout.CENTER);
        add(strategyRow);

        equipmentCanvas.setAlignmentX(CENTER_ALIGNMENT);
        add(equipmentCanvas);
        infoLabel.setForeground(Color.LIGHT_GRAY);
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(infoLabel);

        JPanel buttons = darkPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        buttons.add(button("Reset defaults", "Remove all custom overrides for the selected strategy", e -> resetCurrentStrategy()));
        buttons.add(button("Load items", "Build the local equipment database used by the item picker", e -> ensureIndexLoaded(null)));
        add(buttons);
    }

    private void toggleScript() {
        if (controlBusy) {
            return;
        }

        controlBusy = true;
        refreshRunControls();

        Thread controlThread = new Thread(() -> {
            try {
                if (script.isRunning()) {
                    script.shutdown();
                } else {
                    script.run(config);
                }
            } finally {
                controlBusy = false;
                SwingUtilities.invokeLater(this::refreshRunControls);
            }
        }, "KspBryophyta-Control");
        controlThread.setDaemon(true);
        controlThread.start();
    }

    private void refreshRunControls() {
        boolean running = script.isRunning();

        startStopButton.setText(controlBusy ? "Please wait..." : (running ? "Stop" : "Start"));
        startStopButton.setEnabled(!controlBusy);
        startStopButton.setToolTipText(running
                ? "Stop the Bryophyta automation without disabling the plugin"
                : "Start the Bryophyta automation using the current configuration");

        if (controlBusy) {
            runStateLabel.setText("Changing script state...");
        }
        else if (running) {
            runStateLabel.setText("Automation: Running");
        } else {
            runStateLabel.setText("Automation: Stopped");
        }
    }
    void shutdownPanel() {
        runStateTimer.stop();
    }
    private static JLabel centeredLabel(String text, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setForeground(color);
        return label;
    }

    private static JPanel darkPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return panel;
    }

    private static JButton button(String text, String tooltip, ActionListener listener) {
        JButton button = new JButton(text);
        if (tooltip != null) {
            button.setToolTipText(tooltip);
        }
        button.addActionListener(listener);
        return button;
    }

    private void indexReady(Runnable onSuccess) {
        infoLabel.setText("Equipment database ready.");
        if (onSuccess != null) {
            onSuccess.run();
        }
    }

    private static DocumentListener onDocumentChange(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { action.run(); }
            @Override public void removeUpdate(DocumentEvent e) { action.run(); }
            @Override public void changedUpdate(DocumentEvent e) { action.run(); }
        };
    }

    private BryophytaStrategy selectedStrategy() {
        Object selected = strategySelector.getSelectedItem();
        return selected instanceof BryophytaStrategy ? (BryophytaStrategy) selected : BryophytaStrategy.MELEE;
    }

    private void refreshCanvas() {
        equipmentCanvas.refresh(selectedStrategy());
        infoLabel.setText("Editing: " + selectedStrategy());
    }
    private void resetCurrentStrategy() {
        BryophytaStrategy strategy = selectedStrategy();
        int option = JOptionPane.showConfirmDialog(this,
                "Reset every equipment slot for " + strategy + " to the built-in defaults?",
                "Reset equipment", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (option != JOptionPane.YES_OPTION) {
            return;
        }
        equipmentSettings.resetStrategy(strategy);
        refreshCanvas();
    }

    private void openPicker(EquipmentInventorySlot slot) {
        ensureIndexLoaded(() -> showPicker(slot));
    }
    private void ensureIndexLoaded(Runnable onSuccess) {
        if (equipmentIndex.isLoaded()) {
            indexReady(onSuccess);
            return;
        }

        infoLabel.setText("Loading equipment database...");
        equipmentIndex.ensureLoaded(success -> {
            if (success) {
                refreshCanvas();
                indexReady(onSuccess);
                return;
            }
            infoLabel.setText("Could not load item database.");
            JOptionPane.showMessageDialog(this, "The RuneScape item cache was not ready. Log in and try again.",
                    "Equipment database", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void showPicker(EquipmentInventorySlot slot) {
        List<BryophytaEquipmentItem> allItems = equipmentIndex.itemsFor(slot);
        if (allItems.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No equipable items were found for " + prettySlot(slot) + ".",
                    "Equipment picker",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Select " + prettySlot(slot), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(620, 520));
        dialog.setPreferredSize(new Dimension(760, 620));
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel north = new JPanel(new BorderLayout(8, 8));
        north.setBorder(new EmptyBorder(8, 8, 0, 8));
        JTextField searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search equipment...");
        north.add(searchField, BorderLayout.CENTER);

        JCheckBox f2pOnly = new JCheckBox("F2P only");
        f2pOnly.setToolTipText("Hide members-only items");
        north.add(f2pOnly, BorderLayout.EAST);
        dialog.add(north, BorderLayout.NORTH);

        DefaultListModel<BryophytaEquipmentItem> model = new DefaultListModel<>();
        JList<BryophytaEquipmentItem> itemList = new JList<>(model);
        itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        itemList.setVisibleRowCount(-1);
        itemList.setFixedCellWidth(230);
        itemList.setFixedCellHeight(60);
        itemList.setCellRenderer(new EquipmentItemRenderer(itemManager));

        JScrollPane scrollPane = new JScrollPane(itemList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        dialog.add(scrollPane, BorderLayout.CENTER);

        Runnable rebuild = () -> {
            String query = searchField.getText() == null
                    ? ""
                    : searchField.getText().trim().toLowerCase(Locale.ROOT);
            boolean onlyF2p = f2pOnly.isSelected();

            model.clear();
            for (BryophytaEquipmentItem item : allItems) {
                if (onlyF2p && item.isMembers()) {
                    continue;
                }
                if (!query.isEmpty() && !item.getName().toLowerCase(Locale.ROOT).contains(query)) {
                    continue;
                }
                model.addElement(item);
            }

            Integer effectiveId = effectiveItemId(selectedStrategy(), slot);
            if (effectiveId != null) {
                for (int i = 0; i < model.size(); i++) {
                    if (model.get(i).getId() == effectiveId) {
                        itemList.setSelectedIndex(i);
                        itemList.ensureIndexIsVisible(i);
                        break;
                    }
                }
            }
        };

        searchField.getDocument().addDocumentListener(onDocumentChange(rebuild));
        f2pOnly.addActionListener(e -> rebuild.run());

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setBorder(new EmptyBorder(0, 8, 8, 8));

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftButtons.add(button("Leave empty", null, e -> {
            equipmentSettings.setEmpty(selectedStrategy(), slot);
            dialog.dispose();
            refreshCanvas();
        }));
        leftButtons.add(button("Use default", null, e -> {
            equipmentSettings.resetSlot(selectedStrategy(), slot);
            dialog.dispose();
            refreshCanvas();
        }));
        south.add(leftButtons, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightButtons.add(button("Cancel", null, e -> dialog.dispose()));
        rightButtons.add(button("Select", null, e -> selectFromPicker(slot, itemList.getSelectedValue(), dialog)));
        south.add(rightButtons, BorderLayout.EAST);

        dialog.add(south, BorderLayout.SOUTH);

        itemList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    selectFromPicker(slot, itemList.getSelectedValue(), dialog);
                }
            }
        });

        rebuild.run();
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private void selectFromPicker(
            EquipmentInventorySlot slot,
            BryophytaEquipmentItem selected,
            JDialog dialog) {
        if (selected == null) {
            return;
        }

        BryophytaStrategy strategy = selectedStrategy();

        if (slot == EquipmentInventorySlot.SHIELD) {
            Integer weaponId = effectiveItemId(strategy, EquipmentInventorySlot.WEAPON);
            BryophytaEquipmentItem weapon = findById(EquipmentInventorySlot.WEAPON, weaponId);
            if (weapon != null && weapon.isTwoHanded()) {
                JOptionPane.showMessageDialog(
                        dialog,
                        weapon.getName() + " is two-handed. Choose a one-handed weapon before selecting a shield.",
                        "Incompatible equipment",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }
        }

        equipmentSettings.setItem(strategy, slot, selected.getId());

        if (slot == EquipmentInventorySlot.WEAPON && selected.isTwoHanded()) {
            equipmentSettings.setEmpty(strategy, EquipmentInventorySlot.SHIELD);
        }

        dialog.dispose();
        refreshCanvas();
    }

    private Integer effectiveItemId(BryophytaStrategy strategy, EquipmentInventorySlot slot) {
        Integer override = equipmentSettings.getOverrideItemId(strategy, slot);
        if (override != null) {
            return override > 0 ? override : null;
        }

        String defaultName = BryophytaLoadout.defaultEquipmentFor(strategy).get(slot);
        return equipmentIndex.findItemIdByName(slot, defaultName);
    }
    private BryophytaEquipmentItem findById(EquipmentInventorySlot slot, Integer id) {
        if (id == null) {
            return null;
        }
        return equipmentIndex.itemsFor(slot).stream().filter(item -> item.getId() == id).findFirst().orElse(null);
    }
    private static String prettySlot(EquipmentInventorySlot slot) {
        String lower = slot.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private final class EquipmentCanvas extends JPanel {
        private final BufferedImage background;
        private final Map<EquipmentInventorySlot, JButton> buttons = new EnumMap<>(EquipmentInventorySlot.class);

        private EquipmentCanvas(BufferedImage background) {
            this.background = background;
            setLayout(null);
            setOpaque(false);
            setPreferredSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));
            setMinimumSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));
            setMaximumSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));

            for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS) {
                JButton button = new JButton();
                button.setOpaque(false);
                button.setContentAreaFilled(false);
                button.setBorderPainted(false);
                button.setFocusPainted(false);
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                button.setHorizontalTextPosition(SwingConstants.CENTER);
                button.setVerticalTextPosition(SwingConstants.CENTER);
                button.addActionListener(e -> openPicker(slot));

                Rectangle scaled = scale(SOURCE_SLOT_BOUNDS.get(slot));
                button.setBounds(scaled);
                add(button);
                buttons.put(slot, button);
            }
        }

        private void refresh(BryophytaStrategy strategy) {
            for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS) {
                JButton button = buttons.get(slot);
                if (button == null) {
                    continue;
                }

                button.setIcon(null);
                button.setText("");

                Integer override = equipmentSettings.getOverrideItemId(strategy, slot);
                String name = equipmentSettings.displayNameFor(strategy, slot);
                boolean custom = override != null;
                boolean empty = override != null && override == BryophytaEquipmentSettings.EXPLICIT_EMPTY;

                button.setToolTipText(prettySlot(slot) + ": " + name + (custom ? " (custom)" : " (default)"));

                if (empty) {
                    button.setText("×");
                    button.setForeground(new Color(220, 90, 90));
                    button.setFont(button.getFont().deriveFont(Font.BOLD, 24f));
                    continue;
                }

                Integer itemId = effectiveItemId(strategy, slot);
                if (itemId != null && itemId > 0) {
                    AsyncBufferedImage image = itemManager.getImage(itemId);
                    if (image != null) {
                        image.addTo(button);
                    }
                }
            }

            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (background != null) {
                graphics.drawImage(background, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT, null);
            }
        }

        private Rectangle scale(Rectangle source) {
            float scale = (float) CANVAS_WIDTH / SOURCE_WIDTH;
            return new Rectangle(
                    Math.round(source.x * scale),
                    Math.round(source.y * scale),
                    Math.round(source.width * scale),
                    Math.round(source.height * scale)
            );
        }
    }

    private static final class EquipmentItemRenderer extends JPanel implements ListCellRenderer<BryophytaEquipmentItem> {
        private final ItemManager itemManager;
        private final JLabel iconLabel = new JLabel();
        private final JLabel textLabel = new JLabel();

        private EquipmentItemRenderer(ItemManager itemManager) {
            super(new BorderLayout(8, 0));
            this.itemManager = itemManager;
            setBorder(new EmptyBorder(5, 7, 5, 7));
            setOpaque(true);

            iconLabel.setPreferredSize(new Dimension(40, 40));
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
            add(iconLabel, BorderLayout.WEST);

            textLabel.setForeground(Color.WHITE);
            add(textLabel, BorderLayout.CENTER);
        }

        @Override
        public java.awt.Component getListCellRendererComponent(
                JList<? extends BryophytaEquipmentItem> list,
                BryophytaEquipmentItem value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            if (value == null) {
                iconLabel.setIcon(null);
                textLabel.setText("");
                return this;
            }

            setBackground(isSelected ? list.getSelectionBackground() : ColorScheme.DARKER_GRAY_COLOR);
            textLabel.setText("<html>" + escape(value.getName())
                    + (value.isMembers() ? "<br><font color='#c9a24a'>Members</font>" : "<br><font color='#9bcf7a'>Free-to-play</font>")
                    + (value.isTwoHanded() ? " &nbsp; <font color='#bbbbbb'>2H</font>" : "")
                    + "</html>");

            iconLabel.setIcon(null);
            AsyncBufferedImage image = itemManager.getImage(value.getId());
            if (image != null) {
                image.addTo(iconLabel);
            }

            return this;
        }

        private static String escape(String value) {
            return value
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }
    }
}
