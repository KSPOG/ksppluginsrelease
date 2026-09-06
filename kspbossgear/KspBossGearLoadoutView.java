package net.runelite.client.plugins.microbot.kspbossgear;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * RuneScape-style visual renderer for the selected Wiki loadout.
 * Known equipment rows are placed on a paper-doll layout; UNKNOWN rows are
 * rendered as an OSRS 4x7 inventory grid.
 */
final class KspBossGearLoadoutView extends JPanel
{
    private static final int SLOT_SIZE = 40;
    private static final Color EMPTY_BORDER = new Color(86, 78, 68);
    private static final Color SLOT_BACKGROUND = new Color(46, 42, 37);

    private final BossGearService service;
    private final ItemManager itemManager;
    private final Map<GearSlot, SlotView> equipmentSlots = new EnumMap<>(GearSlot.class);
    private final List<SlotView> inventorySlots = new ArrayList<>();
    private final SlotView specialAttackSlot;
    private final JPanel inventoryGrid = new JPanel(new GridLayout(7, 4, 3, 3));

    KspBossGearLoadoutView(BossGearService service, ItemManager itemManager)
    {
        this.service = service;
        this.itemManager = itemManager;

        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JLabel equipmentTitle = title("Equipment");
        add(equipmentTitle);
        add(buildEquipmentPanel());

        specialAttackSlot = new SlotView("Spec");
        JPanel specRow = new JPanel(new GridBagLayout());
        specRow.setOpaque(false);
        GridBagConstraints spec = new GridBagConstraints();
        spec.insets = new Insets(2, 0, 4, 0);
        specRow.add(specialAttackSlot, spec);
        add(specRow);

        JLabel inventoryTitle = title("Inventory");
        add(inventoryTitle);

        inventoryGrid.setOpaque(false);
        inventoryGrid.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
        inventoryGrid.setMaximumSize(new Dimension(196, 305));
        inventoryGrid.setPreferredSize(new Dimension(196, 305));
        for (int i = 0; i < 28; i++)
        {
            SlotView slot = new SlotView("");
            inventorySlots.add(slot);
            inventoryGrid.add(slot);
        }
        add(inventoryGrid);
    }

    void setSelection(BossGearService.Selection selection)
    {
        for (SlotView slot : equipmentSlots.values()) slot.setRow(null);
        specialAttackSlot.setRow(null);
        for (SlotView slot : inventorySlots) slot.setRow(null);

        if (selection == null)
        {
            repaint();
            return;
        }

        int inventoryIndex = 0;
        for (BossGearService.ResolvedGearRow row : selection.getRows())
        {
            if (row.getSlot() == GearSlot.UNKNOWN)
            {
                if (inventoryIndex < inventorySlots.size())
                    inventorySlots.get(inventoryIndex++).setRow(row);
                continue;
            }

            if (row.getSlot() == GearSlot.SPECIAL_ATTACK)
            {
                specialAttackSlot.setRow(row);
                continue;
            }

            SlotView slot = equipmentSlots.get(row.getSlot());
            if (slot != null) slot.setRow(row);
        }

        refreshOwnership();
        revalidate();
        repaint();
    }

    void refreshOwnership()
    {
        for (SlotView slot : equipmentSlots.values()) slot.refreshOwnership();
        specialAttackSlot.refreshOwnership();
        for (SlotView slot : inventorySlots) slot.refreshOwnership();
    }

    private JPanel buildEquipmentPanel()
    {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        panel.setMaximumSize(new Dimension(220, 215));
        panel.setPreferredSize(new Dimension(220, 215));

        addEquipmentSlot(panel, GearSlot.HEAD, 2, 0, "Head");
        addEquipmentSlot(panel, GearSlot.CAPE, 0, 1, "Cape");
        addEquipmentSlot(panel, GearSlot.AMULET, 2, 1, "Neck");
        addEquipmentSlot(panel, GearSlot.AMMO, 4, 1, "Ammo");
        addEquipmentSlot(panel, GearSlot.WEAPON, 0, 2, "Weapon");
        addEquipmentSlot(panel, GearSlot.BODY, 2, 2, "Body");
        addEquipmentSlot(panel, GearSlot.SHIELD, 4, 2, "Off-hand");
        addEquipmentSlot(panel, GearSlot.GLOVES, 0, 3, "Hands");
        addEquipmentSlot(panel, GearSlot.LEGS, 2, 3, "Legs");
        addEquipmentSlot(panel, GearSlot.RING, 4, 3, "Ring");
        addEquipmentSlot(panel, GearSlot.BOOTS, 2, 4, "Feet");

        return panel;
    }

    private void addEquipmentSlot(JPanel panel, GearSlot slot, int x, int y, String emptyLabel)
    {
        SlotView view = new SlotView(emptyLabel);
        equipmentSlots.put(slot, view);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(1, 2, 1, 2);
        panel.add(view, gbc);
    }

    private static JLabel title(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont().deriveFont(java.awt.Font.BOLD));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private final class SlotView extends JPanel
    {
        private final JLabel icon = new JLabel("", SwingConstants.CENTER);
        private final String emptyLabel;
        private BossGearService.ResolvedGearRow row;
        private String baseTooltip;

        private SlotView(String emptyLabel)
        {
            this.emptyLabel = emptyLabel;
            setLayout(new GridLayout(1, 1));
            setBackground(SLOT_BACKGROUND);
            setOpaque(true);
            setPreferredSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
            setMinimumSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
            setMaximumSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
            setBorder(BorderFactory.createLineBorder(EMPTY_BORDER, 1));

            icon.setForeground(new Color(130, 125, 116));
            icon.setFont(FontManager.getRunescapeSmallFont().deriveFont(9f));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setVerticalAlignment(SwingConstants.CENTER);
            add(icon);
            showEmpty();
        }

        private void setRow(BossGearService.ResolvedGearRow row)
        {
            this.row = row;
            icon.setIcon(null);

            if (row == null)
            {
                showEmpty();
                return;
            }

            icon.setText("");
            StringBuilder tooltip = new StringBuilder("<html><b>")
                .append(escape(row.getPrimaryName()))
                .append("</b><br>")
                .append(row.getSlot().getDisplayName());
            List<String> alternatives = row.getAlternativeNames();
            if (!alternatives.isEmpty())
            {
                tooltip.append("<br><br>Alternatives:<br>");
                int shown = 0;
                for (String alternative : alternatives)
                {
                    if (shown++ >= 6)
                    {
                        tooltip.append("…");
                        break;
                    }
                    tooltip.append("• ").append(escape(alternative)).append("<br>");
                }
            }
            tooltip.append("</html>");
            baseTooltip = tooltip.toString();
            setToolTipText(baseTooltip);
            icon.setToolTipText(baseTooltip);

            try
            {
                AsyncBufferedImage image = itemManager.getImage(row.getPrimaryId());
                if (image != null) image.addTo(icon);
            }
            catch (Throwable ignored)
            {
                icon.setText("?");
            }
            refreshOwnership();
        }

        private void refreshOwnership()
        {
            if (row == null)
            {
                setBorder(BorderFactory.createLineBorder(EMPTY_BORDER, 1));
                return;
            }

            BossGearService.OwnershipMatch match = service.ownership(row);
            Color color = match.getOwnership().getColor();
            setBorder(BorderFactory.createLineBorder(color, 2));

            String base = baseTooltip == null
                ? "<html>" + escape(row.getPrimaryName()) + "</html>"
                : baseTooltip;
            String updated = base.replace("</html>",
                "<br><b>Status:</b> " + escape(match.displayText()) + "</html>");
            setToolTipText(updated);
            icon.setToolTipText(updated);
        }

        private void showEmpty()
        {
            baseTooltip = null;
            icon.setIcon(null);
            icon.setText(emptyLabel);
            setToolTipText(emptyLabel.isEmpty() ? "Empty inventory slot" : emptyLabel + " slot");
            icon.setToolTipText(getToolTipText());
            setBorder(BorderFactory.createLineBorder(EMPTY_BORDER, 1));
        }
    }

    private static String escape(String value)
    {
        if (value == null) return "";
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
