package net.runelite.client.plugins.microbot.kspbossgear;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/** Search-first sidebar for Wiki boss equipment and live ownership status. */
public final class KspBossGearPanel extends PluginPanel
{
    private static final Color ACCENT = new Color(80, 220, 120);

    private final BossGearService service;
    private final KspBossGearLoadoutView loadoutView;
    private final JTextField bossSearch = new JTextField();
    private final JButton loadButton = new JButton("Load Wiki");
    private final JButton refreshButton = new JButton("Refresh");
    private final DefaultListModel<String> suggestionModel = new DefaultListModel<>();
    private final JList<String> suggestions = new JList<>(suggestionModel);
    private final JScrollPane suggestionScroll = new JScrollPane(suggestions);
    private final JComboBox<String> methodCombo = new JComboBox<>();
    private final JComboBox<String> inventoryCombo = new JComboBox<>();
    private final JComboBox<GearTier> tierCombo = new JComboBox<>(GearTier.values());
    private final JPanel gearRows = new JPanel();
    private final JLabel availabilityLabel = new JLabel("No boss loaded");
    private final JLabel statusLabel = new JLabel("Search for a boss to begin.");
    private final JButton wikiButton = new JButton("Open OSRS Wiki");
    private final List<RowView> rowViews = new ArrayList<>();
    private final Timer refreshTimer;

    private boolean updatingControls;

    KspBossGearPanel(BossGearService service, ItemManager itemManager)
    {
        super(false);
        this.service = service;
        this.loadoutView = new KspBossGearLoadoutView(service, itemManager);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(5, 5, 5, 5));
        add(content, BorderLayout.NORTH);

        JLabel title = new JLabel("KSP Boss Gear");
        title.setForeground(ACCENT);
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);

        JLabel version = new JLabel("v" + KspBossGearPlugin.VERSION + " • OSRS Wiki backed");
        version.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        version.setFont(FontManager.getRunescapeSmallFont());
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(version);
        content.add(Box.createVerticalStrut(4));

        JPanel searchSection = section("Boss / raid search");
        bossSearch.setFont(FontManager.getRunescapeSmallFont());
        bossSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        bossSearch.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchSection.add(bossSearch);
        searchSection.add(Box.createVerticalStrut(2));

        JPanel searchButtons = new JPanel(new BorderLayout(3, 0));
        searchButtons.setOpaque(false);
        searchButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        configureButton(loadButton);
        configureButton(refreshButton);
        searchButtons.add(loadButton, BorderLayout.CENTER);
        searchButtons.add(refreshButton, BorderLayout.EAST);
        searchSection.add(searchButtons);

        suggestions.setFont(FontManager.getRunescapeSmallFont());
        suggestions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestions.setVisibleRowCount(4);
        suggestionScroll.setBorder(BorderFactory.createEmptyBorder());
        suggestionScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        suggestionScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        suggestionScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        suggestionScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        suggestionScroll.setVisible(false);
        searchSection.add(Box.createVerticalStrut(2));
        searchSection.add(suggestionScroll);
        content.add(searchSection);
        content.add(Box.createVerticalStrut(4));

        JPanel setup = section("Setup");
        configureCombo(methodCombo);
        configureCombo(inventoryCombo);
        configureCombo(tierCombo);
        setup.add(labeledControl("Method", methodCombo));
        setup.add(Box.createVerticalStrut(2));
        setup.add(labeledControl("Inventory", inventoryCombo));
        setup.add(Box.createVerticalStrut(2));
        setup.add(labeledControl("Tier", tierCombo));
        content.add(setup);
        content.add(Box.createVerticalStrut(4));

        JPanel equipment = section("Recommended gear / inventory");
        availabilityLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        availabilityLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        availabilityLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        equipment.add(availabilityLabel);
        equipment.add(Box.createVerticalStrut(2));

        loadoutView.setAlignmentX(Component.LEFT_ALIGNMENT);
        equipment.add(loadoutView);
        content.add(equipment);
        content.add(Box.createVerticalStrut(4));

        JPanel source = section("Source / status");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setToolTipText("Gear and inventory setups are read from the live Old School RuneScape Wiki page.");
        source.add(statusLabel);
        source.add(Box.createVerticalStrut(2));
        configureButton(wikiButton);
        wikiButton.setEnabled(false);
        source.add(wikiButton);
        content.add(source);

        bossSearch.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent e) { updateSuggestions(); }
            @Override public void removeUpdate(DocumentEvent e) { updateSuggestions(); }
            @Override public void changedUpdate(DocumentEvent e) { updateSuggestions(); }
        });
        bossSearch.addActionListener(e -> loadBoss(false));
        loadButton.addActionListener(e -> loadBoss(false));
        refreshButton.addActionListener(e -> loadBoss(true));

        suggestions.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2) useSelectedSuggestion();
            }
        });
        suggestions.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && suggestions.getSelectedValue() != null)
                bossSearch.setToolTipText("Double-click to load " + suggestions.getSelectedValue());
        });

        methodCombo.addActionListener(e -> {
            if (updatingControls) return;
            Object selected = methodCombo.getSelectedItem();
            if (selected != null)
            {
                service.setSelectedMethod(selected.toString());
                syncInventoryCombo();
                rebuildGearRows();
            }
        });
        inventoryCombo.addActionListener(e -> {
            if (updatingControls) return;
            Object selected = inventoryCombo.getSelectedItem();
            service.setSelectedInventoryMethod(selected == null ? null : selected.toString());
            rebuildGearRows();
        });
        tierCombo.addActionListener(e -> {
            if (updatingControls) return;
            GearTier tier = (GearTier) tierCombo.getSelectedItem();
            if (tier != null)
            {
                service.setSelectedTier(tier);
                rebuildGearRows();
            }
        });

        wikiButton.addActionListener(e -> {
            BossGearService.Selection selection = service.getSelection();
            if (selection.getSourceUrl() != null && !selection.getSourceUrl().isEmpty())
                LinkBrowser.browse(selection.getSourceUrl());
        });

        refreshTimer = new Timer(1_000, e -> refreshStatus());
        refreshTimer.start();
        updateSuggestions();
        refreshStatus();
    }

    void dispose()
    {
        refreshTimer.stop();
    }

    private void loadBoss(boolean forceRefresh)
    {
        String query = bossSearch.getText().trim();
        if (query.isEmpty() && suggestions.getSelectedValue() != null)
            query = suggestions.getSelectedValue();
        if (query.isEmpty()) return;

        suggestionScroll.setVisible(false);
        loadButton.setEnabled(false);
        refreshButton.setEnabled(false);
        statusLabel.setText("Loading OSRS Wiki...");

        service.loadBoss(query, forceRefresh).whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
            if (error == null)
            {
                bossSearch.setText(page.getBossName());
                suggestionScroll.setVisible(false);
                applyLoadedPage();
            }
            refreshStatus();
        }));
    }

    private void applyLoadedPage()
    {
        updatingControls = true;
        try
        {
            List<String> methods = service.getMethodNames();
            methodCombo.setModel(new DefaultComboBoxModel<>(methods.toArray(new String[0])));
            methodCombo.setSelectedItem(service.getSelectedMethod());
            List<String> inventories = service.getInventoryMethodNames();
            inventoryCombo.setModel(new DefaultComboBoxModel<>(inventories.toArray(new String[0])));
            inventoryCombo.setSelectedItem(service.getSelectedInventoryMethod());
            tierCombo.setSelectedItem(service.getSelectedTier());
            methodCombo.setEnabled(!methods.isEmpty());
            inventoryCombo.setEnabled(!inventories.isEmpty());
            tierCombo.setEnabled(!methods.isEmpty());
        }
        finally
        {
            updatingControls = false;
        }
        rebuildGearRows();
    }

    private void rebuildGearRows()
    {
        loadoutView.setSelection(service.getSelection());
        refreshOwnership();
    }

    private void syncInventoryCombo()
    {
        boolean previous = updatingControls;
        updatingControls = true;
        try
        {
            List<String> inventories = service.getInventoryMethodNames();
            inventoryCombo.setModel(new DefaultComboBoxModel<>(inventories.toArray(new String[0])));
            inventoryCombo.setSelectedItem(service.getSelectedInventoryMethod());
            inventoryCombo.setEnabled(!inventories.isEmpty());
        }
        finally
        {
            updatingControls = previous;
        }
    }

    private void refreshStatus()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refreshStatus);
            return;
        }

        boolean loading = service.isLoading();
        loadButton.setEnabled(!loading);
        refreshButton.setEnabled(!loading);
        methodCombo.setEnabled(!loading && service.getPage() != null && !service.getMethodNames().isEmpty());
        inventoryCombo.setEnabled(!loading && service.getPage() != null && !service.getInventoryMethodNames().isEmpty());
        tierCombo.setEnabled(!loading && service.getPage() != null);
        wikiButton.setEnabled(!loading && service.getPage() != null);

        String status = service.getStatus();
        statusLabel.setText(ellipsize(status, 44));
        statusLabel.setToolTipText(status);
        statusLabel.setForeground(looksLikeError(status)
            ? new Color(245, 105, 105)
            : ColorScheme.LIGHT_GRAY_COLOR);
        if (isShowing()) refreshOwnership();
    }

    private void refreshOwnership()
    {
        BossGearService.Selection selection = service.getSelection();
        int available = 0;
        for (BossGearService.ResolvedGearRow row : selection.getRows())
        {
            BossGearService.OwnershipMatch match = service.ownership(row);
            if (match.getOwnership() != BossGearService.Ownership.MISSING) available++;
        }

        int total = selection.getRows().size();
        if (total == 0)
        {
            availabilityLabel.setText(service.getPage() == null ? "No boss loaded" : "No resolvable loadout items");
        }
        else
        {
            int missing = total - available;
            availabilityLabel.setText("Available " + available + "/" + total + " • Missing " + missing);
        }
        loadoutView.refreshOwnership();
    }

    private void updateSuggestions()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::updateSuggestions);
            return;
        }

        String query = bossSearch.getText().trim().toLowerCase(Locale.ROOT);
        suggestionModel.clear();
        if (query.isEmpty())
        {
            suggestionScroll.setVisible(false);
            return;
        }

        for (String boss : WikiGearService.KNOWN_BOSSES)
        {
            if (boss.toLowerCase(Locale.ROOT).contains(query))
            {
                suggestionModel.addElement(boss);
                if (suggestionModel.size() >= 4) break;
            }
        }
        suggestionScroll.setVisible(suggestionModel.getSize() > 0);
        revalidate();
    }

    private void useSelectedSuggestion()
    {
        String selected = suggestions.getSelectedValue();
        if (selected == null) return;
        bossSearch.setText(selected);
        suggestionScroll.setVisible(false);
        loadBoss(false);
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

    private static JPanel labeledControl(String labelText, JComboBox<?> control)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setPreferredSize(new Dimension(58, 24));
        row.add(label, BorderLayout.WEST);
        row.add(control, BorderLayout.CENTER);
        return row;
    }

    private static void configureButton(JButton button)
    {
        button.setFocusPainted(false);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        button.setPreferredSize(new Dimension(72, 25));
    }

    private static void configureCombo(JComboBox<?> combo)
    {
        combo.setFont(FontManager.getRunescapeSmallFont());
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        combo.setPreferredSize(new Dimension(120, 25));
    }

    private static String shortOwnership(BossGearService.OwnershipMatch match)
    {
        String value;
        switch (match.getOwnership())
        {
            case EQUIPPED: value = "Worn"; break;
            case INVENTORY: value = "Inv"; break;
            case BANK: value = "Bank"; break;
            case MISSING:
            default: value = "Missing"; break;
        }
        return value + (match.getOwnership() != BossGearService.Ownership.MISSING && !match.isPrimary() ? "*" : "");
    }

    private static String ellipsize(String text, int max)
    {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static boolean looksLikeError(String text)
    {
        String s = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return s.contains("could not") || s.contains("no recommended") || s.contains("http ")
            || s.contains("invalid") || s.contains("error") || s.contains("failed");
    }

    private final class RowView
    {
        private final BossGearService.ResolvedGearRow row;
        private final JPanel panel = new JPanel(new BorderLayout(4, 0));
        private final JLabel status = new JLabel("-");

        private RowView(BossGearService.ResolvedGearRow row)
        {
            this.row = row;
            panel.setOpaque(false);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
            panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));

            JLabel slot = new JLabel(row.getSlot().getDisplayName());
            slot.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            slot.setFont(FontManager.getRunescapeSmallFont());
            slot.setPreferredSize(new Dimension(47, 24));

            JLabel item = new JLabel(ellipsize(row.getPrimaryName(), 20));
            item.setForeground(service.getSelectedTier().getColor());
            item.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            item.setToolTipText(row.tooltip());

            status.setHorizontalAlignment(JLabel.RIGHT);
            status.setFont(FontManager.getRunescapeSmallFont());
            status.setPreferredSize(new Dimension(49, 24));

            panel.add(slot, BorderLayout.WEST);
            panel.add(item, BorderLayout.CENTER);
            panel.add(status, BorderLayout.EAST);
        }
    }
}
