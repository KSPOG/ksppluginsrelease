package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class KspAioFighterInventoryPanel extends JPanel
{
    private final KspAioFighterInventorySettings settings;
    private final ItemManager itemManager;
    private final Consumer<KspAioFighterGearStyle> loadAction;
    private final JComboBox<KspAioFighterGearStyle> styleSelector = new JComboBox<>(KspAioFighterGearStyle.values());
    private final JCheckBox enabled = new JCheckBox("Use saved setup");
    private final JLabel status = new JLabel("No inventory setup saved", SwingConstants.CENTER);
    private final JLabel[] slots = new JLabel[28];
    private final JButton loadButton = new JButton("Load Setup");
    private boolean refreshing;

    KspAioFighterInventoryPanel(KspAioFighterInventorySettings settings,
                                ItemManager itemManager,
                                Consumer<KspAioFighterGearStyle> loadAction)
    {
        this.settings = settings;
        this.itemManager = itemManager;
        this.loadAction = loadAction;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(0, 0, 8, 0));
        buildUi();
        refresh();
    }

    private void buildUi()
    {
        JLabel title = new JLabel("Inventory Setup", SwingConstants.LEFT);
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(new EmptyBorder(10, 2, 2, 2));
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);

        JLabel help = new JLabel(
            "<html><center>Save and restore a different inventory for each combat style.<br>Enabled setups are loaded before Start.</center></html>",
            SwingConstants.CENTER);
        help.setForeground(Color.LIGHT_GRAY);
        help.setAlignmentX(CENTER_ALIGNMENT);
        add(help);

        JPanel selector = darkPanel(new BorderLayout(5, 0));
        JLabel label = new JLabel("Setup:");
        label.setForeground(Color.WHITE);
        selector.add(label, BorderLayout.WEST);
        styleSelector.addActionListener(e -> refresh());
        selector.add(styleSelector, BorderLayout.CENTER);
        selector.setMaximumSize(new Dimension(220, 31));
        selector.setAlignmentX(CENTER_ALIGNMENT);
        add(selector);

        enabled.setOpaque(false);
        enabled.setForeground(Color.WHITE);
        enabled.setAlignmentX(CENTER_ALIGNMENT);
        enabled.addActionListener(e -> toggleEnabled());
        add(enabled);

        JPanel grid = darkPanel(new GridLayout(7, 4, 2, 2));
        grid.setBorder(new EmptyBorder(4, 6, 4, 6));
        grid.setMaximumSize(new Dimension(224, 315));
        grid.setAlignmentX(CENTER_ALIGNMENT);
        for (int i = 0; i < slots.length; i++)
        {
            JLabel cell = new JLabel("", SwingConstants.CENTER);
            cell.setOpaque(true);
            cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            cell.setForeground(Color.WHITE);
            cell.setPreferredSize(new Dimension(50, 42));
            cell.setMinimumSize(new Dimension(44, 38));
            cell.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
            cell.setHorizontalTextPosition(SwingConstants.CENTER);
            cell.setVerticalTextPosition(SwingConstants.BOTTOM);
            cell.setToolTipText("Empty slot " + (i + 1));
            slots[i] = cell;
            grid.add(cell);
        }
        add(grid);

        status.setForeground(Color.LIGHT_GRAY);
        status.setBorder(new EmptyBorder(2, 2, 2, 2));
        status.setAlignmentX(CENTER_ALIGNMENT);
        add(status);

        JPanel actions = darkPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        JButton saveCurrent = new JButton("Save Current");
        saveCurrent.setToolTipText("Replace this setup with the player's current inventory and quantities");
        saveCurrent.addActionListener(e -> saveCurrent());
        loadButton.setToolTipText("Walk to a bank and restore this saved inventory now");
        loadButton.addActionListener(e -> loadAction.accept(selectedStyle()));
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> clear());
        actions.add(saveCurrent);
        actions.add(loadButton);
        actions.add(clear);
        actions.setAlignmentX(CENTER_ALIGNMENT);
        add(actions);
    }

    private void saveCurrent()
    {
        if (!Microbot.isLoggedIn())
        {
            JOptionPane.showMessageDialog(this,
                "Log in before using Save Current.",
                "AIO Fighter Inventory Setup",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        KspAioFighterGearStyle style = selectedStyle();
        List<KspAioFighterInventoryItem> saved = settings.saveCurrent(style);
        if (saved.isEmpty())
        {
            JOptionPane.showMessageDialog(this,
                "The current inventory is empty, so no setup was saved.",
                "AIO Fighter Inventory Setup",
                JOptionPane.WARNING_MESSAGE);
            refresh();
            return;
        }

        refresh();
        Microbot.status = "KSP AIO Fighter: saved current inventory for " + style;
    }

    private void clear()
    {
        KspAioFighterGearStyle style = selectedStyle();
        if (settings.get(style).isEmpty()) return;
        if (JOptionPane.showConfirmDialog(this,
            "Clear the saved inventory setup for " + style + "?",
            "Clear inventory setup",
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        settings.clear(style);
        refresh();
    }

    private void toggleEnabled()
    {
        if (refreshing) return;
        KspAioFighterGearStyle style = selectedStyle();
        if (enabled.isSelected() && settings.get(style).isEmpty())
        {
            enabled.setSelected(false);
            JOptionPane.showMessageDialog(this,
                "Save an inventory first, then enable the setup.",
                "AIO Fighter Inventory Setup",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        settings.setEnabled(style, enabled.isSelected());
        refresh();
    }

    void refresh()
    {
        refreshing = true;
        try
        {
            KspAioFighterGearStyle style = selectedStyle();
            List<KspAioFighterInventoryItem> items = settings.get(style);
            enabled.setSelected(settings.isEnabled(style));
            enabled.setEnabled(!items.isEmpty());
            loadButton.setEnabled(!items.isEmpty());

            Map<Integer, KspAioFighterInventoryItem> bySlot = new HashMap<>();
            for (KspAioFighterInventoryItem item : items) bySlot.put(item.getSlot(), item);

            for (int slot = 0; slot < slots.length; slot++)
            {
                JLabel cell = slots[slot];
                KspAioFighterInventoryItem item = bySlot.get(slot);
                cell.setIcon(null);
                cell.setText("");
                if (item == null)
                {
                    cell.setToolTipText("Empty slot " + (slot + 1));
                    continue;
                }

                cell.setToolTipText(item.getName() + " x" + item.getQuantity()
                    + (item.isNoted() ? " (noted)" : ""));
                cell.setText(item.getQuantity() > 1 ? compactQuantity(item.getQuantity()) : "");
                AsyncBufferedImage image = itemManager.getImage(item.getId());
                if (image != null) image.addTo(cell);
            }

            int used = settings.usedSlots(style);
            if (items.isEmpty())
            {
                status.setText("No " + style + " inventory saved");
            }
            else
            {
                status.setText((settings.isEnabled(style) ? "Active" : "Saved / disabled")
                    + " - " + used + "/28 occupied slots");
            }
        }
        finally
        {
            refreshing = false;
        }
    }

    private KspAioFighterGearStyle selectedStyle()
    {
        Object value = styleSelector.getSelectedItem();
        return value instanceof KspAioFighterGearStyle
            ? (KspAioFighterGearStyle) value
            : KspAioFighterGearStyle.ATTACK;
    }

    private static JPanel darkPanel(java.awt.LayoutManager layout)
    {
        JPanel panel = new JPanel(layout);
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return panel;
    }

    private static String compactQuantity(int quantity)
    {
        if (quantity >= 1_000_000) return (quantity / 1_000_000) + "m";
        if (quantity >= 10_000) return (quantity / 1_000) + "k";
        return "x" + quantity;
    }
}
