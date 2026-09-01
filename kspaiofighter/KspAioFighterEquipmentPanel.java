package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class KspAioFighterEquipmentPanel extends PluginPanel
{
    private final KspAioFighterEquipmentSettings settings;
    private final KspAioFighterEquipmentIndex index;
    private final ItemManager itemManager;
    private final Runnable startAction;
    private final Runnable stopAction;
    private final Supplier<Boolean> runningSupplier;
    private final Supplier<WorldPoint> mapCentreSupplier;
    private final Supplier<WorldPoint[]> attackAreaSupplier;
    private final Supplier<Boolean> attackAreaEnabledSupplier;
    private final BiConsumer<WorldPoint, WorldPoint> attackAreaSetter;
    private final Runnable attackAreaResetter;

    private final JComboBox<KspAioFighterGearStyle> styleSelector = new JComboBox<>(KspAioFighterGearStyle.values());
    private final Map<EquipmentInventorySlot, JButton> slotButtons = new EnumMap<>(EquipmentInventorySlot.class);
    private final JLabel gearStatus = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel areaStatus = new JLabel("No attack area selected", SwingConstants.CENTER);
    private final JLabel automationStatus = new JLabel("Stopped - press Start when ready", SwingConstants.CENTER);
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");

    KspAioFighterEquipmentPanel(KspAioFighterEquipmentSettings settings,
                                KspAioFighterEquipmentIndex index,
                                ItemManager itemManager,
                                Runnable startAction,
                                Runnable stopAction,
                                Supplier<Boolean> runningSupplier,
                                Supplier<WorldPoint> mapCentreSupplier,
                                Supplier<WorldPoint[]> attackAreaSupplier,
                                Supplier<Boolean> attackAreaEnabledSupplier,
                                BiConsumer<WorldPoint, WorldPoint> attackAreaSetter,
                                Runnable attackAreaResetter)
    {
        this.settings = settings;
        this.index = index;
        this.itemManager = itemManager;
        this.startAction = startAction;
        this.stopAction = stopAction;
        this.runningSupplier = runningSupplier;
        this.mapCentreSupplier = mapCentreSupplier;
        this.attackAreaSupplier = attackAreaSupplier;
        this.attackAreaEnabledSupplier = attackAreaEnabledSupplier;
        this.attackAreaSetter = attackAreaSetter;
        this.attackAreaResetter = attackAreaResetter;
        buildUi();
        refreshSlots();
        refreshAreaStatus();
        refreshAutomationState();
    }

    private void buildUi()
    {
        JLabel title = new JLabel("KSP AIO Fighter", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title);

        JLabel help = new JLabel(
            "<html><center>Configure equipment and your attack area here.<br>The fighter stays idle until you press Start.</center></html>",
            SwingConstants.CENTER);
        help.setForeground(Color.LIGHT_GRAY);
        add(help);

        add(sectionTitle("Equipment"));

        JPanel selector = darkPanel(new BorderLayout(6, 0));
        JLabel selectorLabel = new JLabel("Edit setup:");
        selectorLabel.setForeground(Color.WHITE);
        selector.add(selectorLabel, BorderLayout.WEST);
        styleSelector.addActionListener(e -> refreshSlots());
        selector.add(styleSelector, BorderLayout.CENTER);
        selector.setMaximumSize(new Dimension(220, 32));
        add(selector);

        JPanel equipment = darkPanel(new GridBagLayout());
        equipment.setBorder(new EmptyBorder(8, 0, 8, 0));
        addSlot(equipment, EquipmentInventorySlot.HEAD, 1, 0);
        addSlot(equipment, EquipmentInventorySlot.CAPE, 0, 1);
        addSlot(equipment, EquipmentInventorySlot.AMULET, 1, 1);
        addSlot(equipment, EquipmentInventorySlot.AMMO, 2, 1);
        addSlot(equipment, EquipmentInventorySlot.WEAPON, 0, 2);
        addSlot(equipment, EquipmentInventorySlot.BODY, 1, 2);
        addSlot(equipment, EquipmentInventorySlot.SHIELD, 2, 2);
        addSlot(equipment, EquipmentInventorySlot.LEGS, 1, 3);
        addSlot(equipment, EquipmentInventorySlot.GLOVES, 0, 4);
        addSlot(equipment, EquipmentInventorySlot.BOOTS, 1, 4);
        addSlot(equipment, EquipmentInventorySlot.RING, 2, 4);
        add(equipment);

        gearStatus.setForeground(Color.LIGHT_GRAY);
        add(gearStatus);

        JPanel gearActions = darkPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        JButton reset = new JButton("Reset setup");
        reset.addActionListener(e -> resetStyle());
        JButton load = new JButton("Load items");
        load.addActionListener(e -> ensureLoaded(null));
        gearActions.add(reset);
        gearActions.add(load);
        add(gearActions);

        add(sectionTitle("Attack Area"));
        JPanel areaPanel = darkPanel(new BorderLayout(4, 6));
        areaPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        areaStatus.setForeground(Color.LIGHT_GRAY);
        areaPanel.add(areaStatus, BorderLayout.NORTH);
        JPanel areaButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        areaButtons.setOpaque(false);
        JButton selectArea = new JButton("Select Area on Map");
        selectArea.addActionListener(e -> openAreaMap());
        JButton clearArea = new JButton("Clear Area");
        clearArea.addActionListener(e -> clearAttackArea());
        areaButtons.add(selectArea);
        areaButtons.add(clearArea);
        areaPanel.add(areaButtons, BorderLayout.CENTER);
        areaPanel.setMaximumSize(new Dimension(230, 72));
        add(areaPanel);

        add(sectionTitle("Controls"));
        automationStatus.setForeground(Color.LIGHT_GRAY);
        add(automationStatus);
        JPanel controls = darkPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        startButton.addActionListener(e -> {
            startAction.run();
            refreshAutomationState();
        });
        stopButton.addActionListener(e -> {
            stopAction.run();
            refreshAutomationState();
        });
        startButton.setPreferredSize(new Dimension(96, 30));
        stopButton.setPreferredSize(new Dimension(96, 30));
        controls.add(startButton);
        controls.add(stopButton);
        add(controls);
    }

    private JLabel sectionTitle(String text)
    {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(new EmptyBorder(10, 2, 2, 2));
        return label;
    }

    private void addSlot(JPanel panel, EquipmentInventorySlot slot, int x, int y)
    {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(68, 54));
        button.setToolTipText(pretty(slot));
        button.addActionListener(e -> openPicker(slot));
        slotButtons.put(slot, button);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(2, 2, 2, 2);
        panel.add(button, c);
    }

    private void refreshSlots()
    {
        KspAioFighterGearStyle style = selectedStyle();
        for (Map.Entry<EquipmentInventorySlot, JButton> entry : slotButtons.entrySet())
        {
            String name = settings.get(style, entry.getKey());
            entry.getValue().setText(shortName(name.isBlank() ? pretty(entry.getKey()) : name));
            entry.getValue().setToolTipText(name.isBlank() ? pretty(entry.getKey()) + ": empty" : pretty(entry.getKey()) + ": " + name);
        }
        gearStatus.setText("Editing: " + style);
    }

    void refreshAreaStatus()
    {
        WorldPoint[] area = attackAreaSupplier.get();
        WorldPoint first = area != null && area.length > 0 ? area[0] : null;
        WorldPoint second = area != null && area.length > 1 ? area[1] : null;
        if (!valid(first) || !valid(second) || first.getPlane() != second.getPlane())
        {
            areaStatus.setText("No attack area selected");
            return;
        }
        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int maxY = Math.max(first.getY(), second.getY());
        boolean enabled = Boolean.TRUE.equals(attackAreaEnabledSupplier.get());
        areaStatus.setText("<html><center>" + (enabled ? "Active" : "Saved / disabled")
            + ": (" + minX + ", " + minY + ") - (" + maxX + ", " + maxY + ")"
            + "<br>Plane " + first.getPlane() + " | " + (maxX - minX + 1) + " x " + (maxY - minY + 1) + "</center></html>");
    }

    void refreshAutomationState()
    {
        boolean running = Boolean.TRUE.equals(runningSupplier.get());
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        automationStatus.setText(running ? "Running" : "Stopped - press Start when ready");
    }

    private void openAreaMap()
    {
        WorldPoint[] area = attackAreaSupplier.get();
        WorldPoint first = area != null && area.length > 0 ? area[0] : null;
        WorldPoint second = area != null && area.length > 1 ? area[1] : null;
        WorldPoint centre = mapCentreSupplier.get();
        KspAioFighterAreaMapDialog.show(this, centre, first, second, (a, b) -> {
            attackAreaSetter.accept(a, b);
            refreshAreaStatus();
        });
    }

    private void clearAttackArea()
    {
        if (JOptionPane.showConfirmDialog(this,
            "Clear the saved AIO Fighter attack area?",
            "Clear attack area",
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        attackAreaResetter.run();
        refreshAreaStatus();
    }

    private void openPicker(EquipmentInventorySlot slot)
    {
        ensureLoaded(() -> showPicker(slot));
    }

    private void ensureLoaded(Runnable next)
    {
        if (index.isLoaded())
        {
            if (next != null) next.run();
            return;
        }
        gearStatus.setText("Loading equipment database...");
        index.ensureLoaded(success -> SwingUtilities.invokeLater(() -> {
            if (!success)
            {
                gearStatus.setText("Could not load equipment database.");
                JOptionPane.showMessageDialog(this, "Log in and try Load items again.", "AIO Fighter Equipment", JOptionPane.ERROR_MESSAGE);
                return;
            }
            gearStatus.setText("Equipment database ready.");
            if (next != null) next.run();
        }));
    }

    private void showPicker(EquipmentInventorySlot slot)
    {
        List<KspAioFighterEquipmentItem> allItems = index.itemsFor(slot);
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Select " + pretty(slot));
        dialog.setModal(true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setPreferredSize(new Dimension(720, 600));

        JPanel north = new JPanel(new BorderLayout(8, 8));
        north.setBorder(new EmptyBorder(8, 8, 0, 8));
        JTextField search = new JTextField();
        JCheckBox f2pOnly = new JCheckBox("F2P only");
        north.add(search, BorderLayout.CENTER);
        north.add(f2pOnly, BorderLayout.EAST);
        dialog.add(north, BorderLayout.NORTH);

        DefaultListModel<KspAioFighterEquipmentItem> model = new DefaultListModel<>();
        JList<KspAioFighterEquipmentItem> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ItemRenderer(itemManager));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        dialog.add(scroll, BorderLayout.CENTER);

        Runnable rebuild = () -> {
            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            model.clear();
            for (KspAioFighterEquipmentItem item : allItems)
            {
                if (f2pOnly.isSelected() && item.isMembers()) continue;
                if (!query.isEmpty() && !item.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
                model.addElement(item);
            }
        };
        search.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent e) { rebuild.run(); }
            @Override public void removeUpdate(DocumentEvent e) { rebuild.run(); }
            @Override public void changedUpdate(DocumentEvent e) { rebuild.run(); }
        });
        f2pOnly.addActionListener(e -> rebuild.run());

        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(new EmptyBorder(0, 8, 8, 8));
        JButton empty = new JButton("Leave empty");
        empty.addActionListener(e -> {
            settings.clear(selectedStyle(), slot);
            dialog.dispose();
            refreshSlots();
        });
        south.add(empty, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());
        JButton select = new JButton("Select");
        select.addActionListener(e -> selectItem(slot, list.getSelectedValue(), dialog));
        right.add(cancel);
        right.add(select);
        south.add(right, BorderLayout.EAST);
        dialog.add(south, BorderLayout.SOUTH);

        rebuild.run();
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private void selectItem(EquipmentInventorySlot slot, KspAioFighterEquipmentItem item, JDialog dialog)
    {
        if (item == null) return;
        KspAioFighterGearStyle style = selectedStyle();
        settings.set(style, slot, item.getName());
        if (slot == EquipmentInventorySlot.WEAPON && item.isTwoHanded()) settings.clear(style, EquipmentInventorySlot.SHIELD);
        dialog.dispose();
        refreshSlots();
    }

    private void resetStyle()
    {
        if (JOptionPane.showConfirmDialog(this, "Clear all selected gear for " + selectedStyle() + "?", "Reset gear", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        settings.clearStyle(selectedStyle());
        refreshSlots();
    }

    private KspAioFighterGearStyle selectedStyle()
    {
        Object value = styleSelector.getSelectedItem();
        return value instanceof KspAioFighterGearStyle ? (KspAioFighterGearStyle) value : KspAioFighterGearStyle.ATTACK;
    }

    private static JPanel darkPanel(java.awt.LayoutManager layout)
    {
        JPanel panel = new JPanel(layout);
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return panel;
    }

    private static String pretty(EquipmentInventorySlot slot)
    {
        String raw = slot.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static String shortName(String value)
    {
        if (value == null || value.isBlank()) return "Empty";
        return value.length() <= 11 ? value : value.substring(0, 10) + "…";
    }

    private static boolean valid(WorldPoint point)
    {
        return point != null && point.getX() > 0 && point.getY() > 0;
    }

    private static final class ItemRenderer extends JPanel implements ListCellRenderer<KspAioFighterEquipmentItem>
    {
        private final ItemManager itemManager;
        private final JLabel icon = new JLabel();
        private final JLabel text = new JLabel();

        private ItemRenderer(ItemManager itemManager)
        {
            super(new BorderLayout(8, 0));
            this.itemManager = itemManager;
            setBorder(new EmptyBorder(4, 6, 4, 6));
            icon.setPreferredSize(new Dimension(36, 36));
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends KspAioFighterEquipmentItem> list, KspAioFighterEquipmentItem value, int index, boolean isSelected, boolean cellHasFocus)
        {
            setBackground(isSelected ? ColorScheme.DARKER_GRAY_COLOR : list.getBackground());
            text.setForeground(isSelected ? Color.WHITE : list.getForeground());
            text.setText(value == null ? "" : value.getName() + (value.isMembers() ? "  (Members)" : ""));
            icon.setIcon(null);
            if (value != null)
            {
                AsyncBufferedImage image = itemManager.getImage(value.getId());
                image.addTo(icon);
            }
            return this;
        }
    }
}
