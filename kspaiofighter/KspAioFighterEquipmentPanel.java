package net.runelite.client.plugins.microbot.kspaiofighter;

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

final class KspAioFighterEquipmentPanel extends PluginPanel
{
    private final KspAioFighterEquipmentSettings settings;
    private final KspAioFighterEquipmentIndex index;
    private final ItemManager itemManager;
    private final JComboBox<KspAioFighterGearStyle> styleSelector = new JComboBox<>(KspAioFighterGearStyle.values());
    private final Map<EquipmentInventorySlot, JButton> slotButtons = new EnumMap<>(EquipmentInventorySlot.class);
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);

    KspAioFighterEquipmentPanel(KspAioFighterEquipmentSettings settings,
                                KspAioFighterEquipmentIndex index,
                                ItemManager itemManager)
    {
        this.settings = settings;
        this.index = index;
        this.itemManager = itemManager;
        buildUi();
        refreshSlots();
    }

    private void buildUi()
    {
        JLabel title = new JLabel("KSP AIO Fighter Gear", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title);

        JLabel help = new JLabel("<html><center>Bryophyta-style equipment selection.<br>Each training skill stores its own loadout.</center></html>", SwingConstants.CENTER);
        help.setForeground(Color.LIGHT_GRAY);
        add(help);

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

        status.setForeground(Color.LIGHT_GRAY);
        add(status);

        JPanel actions = darkPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        JButton reset = new JButton("Reset setup");
        reset.addActionListener(e -> resetStyle());
        JButton load = new JButton("Load items");
        load.addActionListener(e -> ensureLoaded(null));
        actions.add(reset);
        actions.add(load);
        add(actions);
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
        status.setText("Editing: " + style);
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
        status.setText("Loading equipment database...");
        index.ensureLoaded(success -> SwingUtilities.invokeLater(() -> {
            if (!success)
            {
                status.setText("Could not load equipment database.");
                JOptionPane.showMessageDialog(this, "Log in and try Load items again.", "AIO Fighter Equipment", JOptionPane.ERROR_MESSAGE);
                return;
            }
            status.setText("Equipment database ready.");
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
