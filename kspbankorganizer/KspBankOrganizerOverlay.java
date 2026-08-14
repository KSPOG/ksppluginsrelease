package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class KspBankOrganizerOverlay extends OverlayPanel
{
    private static final int BANK_GROUP_ID = 12;
    private static final int BANK_ITEMS_CHILD_ID = 12;

    private final KspBankOrganizerConfig config;
    private final BankOrganizerEngine engine;
    private final Client client;
    private final BankSnapshotReader snapshotReader;

    private BankSnapshot overlaySnapshot;
    private long nextSnapshotRefreshNanos;

    @Inject
    KspBankOrganizerOverlay(
        KspBankOrganizerPlugin plugin,
        KspBankOrganizerConfig config,
        BankOrganizerEngine engine,
        Client client,
        BankSnapshotReader snapshotReader)
    {
        super(plugin);
        this.config = config;
        this.engine = engine;
        this.client = client;
        this.snapshotReader = snapshotReader;

        // RuneLite's default Overlay layer is UNDER_WIDGETS. Bank item widgets
        // therefore paint over the overlay. Bank highlighting must be rendered
        // above the bank interface.
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGHEST);
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        if (config.showCategoryBoxes() && Rs2Bank.isOpen())
        {
            renderBankHighlights(graphics);
        }

        panelComponent.setPreferredSize(new Dimension(220, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("KSP Bank Organizer v" + KspBankOrganizerPlugin.version)
            .color(Color.GREEN)
            .build());
        panelComponent.getChildren().add(LineComponent.builder().left("Mode").right(engine.phase().equals("Idle") ? "Ready" : engine.activeMode().displayName()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Phase").right(engine.phase()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Stacks planned").right(String.valueOf(engine.plannedCount())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Misplaced").right(String.valueOf(engine.misplacedCount())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Moved").right(String.valueOf(engine.movedCount())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Sort moves").right(String.valueOf(engine.sortedCount())).build());
        return super.render(graphics);
    }

    private void renderBankHighlights(Graphics2D graphics)
    {
        BankSnapshot snapshot = engine.latestSnapshot();
        if (snapshot == null)
        {
            snapshot = getLiveOverlaySnapshot();
        }
        if (snapshot == null)
        {
            return;
        }

        Map<Integer, ItemCategory> categories = engine.categoriesById();
        Map<Integer, Integer> targets = engine.targetsById();

        if (categories.isEmpty())
        {
            AutoCategorizer overlayCategorizer = new AutoCategorizer();
            overlayCategorizer.configure(config);

            Map<Integer, ItemCategory> liveCategories = new java.util.HashMap<>();
            Map<Integer, Integer> liveTargets = new java.util.HashMap<>();
            for (BankSnapshot.BankStack stack : snapshot.items())
            {
                ItemCategory category = overlayCategorizer.categorize(stack);
                liveCategories.put(stack.itemId(), category);
                liveTargets.put(stack.itemId(), configuredTargetFor(category));
            }
            categories = liveCategories;
            targets = liveTargets;
        }
        int alpha = Math.max(0, Math.min(255, config.overlayOpacity() * 255 / 100));

        Stroke previousStroke = graphics.getStroke();
        try
        {
            for (BankSnapshot.BankStack stack : snapshot.items())
            {
                // Only the currently visible bank tab can have item widgets on
                // screen. The snapshot contains all real bank tabs.
                if (stack.tab() != snapshot.currentTab())
                {
                    continue;
                }

                Rectangle bounds = getBankItemBounds(stack.slot(), stack.itemId());
                if (bounds == null || !Rs2UiHelper.isRectangleWithinCanvas(bounds))
                {
                    continue;
                }

                ItemCategory category = categories.get(stack.itemId());
                if (category != null)
                {
                    Color base = category.getColor();
                    graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                    graphics.fill(bounds);
                }

                Integer target = targets.get(stack.itemId());
                if (config.highlightMisplaced() && target != null && target >= 0 && stack.tab() != target)
                {
                    graphics.setColor(config.misplacedColor());
                    graphics.setStroke(new BasicStroke(2.0f));
                    graphics.draw(bounds);
                }
            }
        }
        finally
        {
            graphics.setStroke(previousStroke);
        }
    }    
    private BankSnapshot getLiveOverlaySnapshot()
    {
        if (!Rs2Bank.isOpen())
        {
            overlaySnapshot = null;
            return null;
        }

        long now = System.nanoTime();
        if (overlaySnapshot != null && now < nextSnapshotRefreshNanos)
        {
            return overlaySnapshot;
        }

        try
        {
            BankSnapshot fresh = snapshotReader.read();
            overlaySnapshot = fresh;
            nextSnapshotRefreshNanos = now + 250_000_000L;
            return fresh;
        }
        catch (Throwable ignored)
        {
            return overlaySnapshot;
        }
    }

    private int configuredTargetFor(ItemCategory category)
    {
        switch (category)
        {
            case TELEPORTS: return config.teleportsTarget().getTabIndex();
            case GEAR: return config.gearTarget().getTabIndex();
            case POTIONS: return config.potionsTarget().getTabIndex();
            case FOOD: return config.foodTarget().getTabIndex();
            case SKILLING: return config.skillingTarget().getTabIndex();
            case RAW_MATERIALS: return config.materialsTarget().getTabIndex();
            case HIGH_ALCH: return config.highAlchTarget().getTabIndex();
            case CURRENCY: return config.currencyTarget().getTabIndex();
            case QUEST_MISC: return config.questMiscTarget().getTabIndex();
            default: return -1;
        }
    }

    /**
     * Resolve the visible bank item rectangle. Rs2Bank.getItemBounds() is the
     * preferred Microbot path; the direct widget lookup is a fallback for the
     * bank layouts where the helper returns no rectangle while the item widget
     * itself is already rendered.
     *
     * This method runs from RuneLite's overlay render path, so Widget access is
     * kept local to the render call and no Widget escapes to worker threads.
     */
    private Rectangle getBankItemBounds(int slot, int itemId)
    {
        Rectangle bounds = Rs2Bank.getItemBounds(slot);
        if (bounds != null && bounds.width > 0 && bounds.height > 0)
        {
            return new Rectangle(bounds);
        }

        Widget container = client.getWidget(BANK_GROUP_ID, BANK_ITEMS_CHILD_ID);
        if (container == null || container.isHidden())
        {
            return null;
        }

        Widget[] children = container.getDynamicChildren();
        if (children == null || children.length == 0)
        {
            children = container.getChildren();
        }
        if (children == null)
        {
            return null;
        }

        // Bank item widgets are laid out by bank slot. Prefer the exact slot,
        // then fall back to an item-id match if the current layout is dynamic.
        if (slot >= 0 && slot < children.length)
        {
            Widget child = children[slot];
            if (isVisibleItemWidget(child, itemId))
            {
                return new Rectangle(child.getBounds());
            }
        }

        for (Widget child : children)
        {
            if (isVisibleItemWidget(child, itemId))
            {
                return new Rectangle(child.getBounds());
            }
        }

        return null;
    }

    private static boolean isVisibleItemWidget(Widget widget, int itemId)
    {
        if (widget == null || widget.isHidden())
        {
            return false;
        }

        Rectangle bounds = widget.getBounds();
        return widget.getItemId() == itemId
            && bounds != null
            && bounds.width > 0
            && bounds.height > 0;
    }

}
