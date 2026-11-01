package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.NPC;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

/**
 * Resolves Copilot's visual highlight to the current interactive RuneLite widget.
 * Every method in this class is called on the client thread.
 */
final class GeWidgetResolver
{
    private static final int FIRST_GE_SLOT_CHILD = 7;
    private static final int GE_SLOT_COUNT = 8;

    private final Client client;

    @Inject
    GeWidgetResolver(Client client)
    {
        this.client = client;
    }

    ResolvedCopilotAction resolve(CopilotHighlight highlight)
    {
        if (highlight == null)
        {
            return null;
        }

        if (highlight.isNpc())
        {
            NPC npc = highlight.getNpc();
            String name = npc == null ? "" : safe(npc.getName());
            int index = npc == null ? -1 : npc.getIndex();
            return npc == null
                ? null
                : ResolvedCopilotAction.npc(npc, "N:" + index + ':' + name);
        }

        Widget source = highlight.getWidget();
        if (!isVisible(source))
        {
            return null;
        }

        Rectangle overlayBounds = overlayBounds(source, highlight.getRelativeBounds());
        if (!validBounds(overlayBounds))
        {
            return null;
        }

        OfferSlotMatch slotMatch = findOwningOfferSlot(source, overlayBounds);
        boolean offerSlotHighlight = slotMatch != null;

        // Copilot removes home-screen slot overlays asynchronously. Once any GE slot screen
        // is open, a stale home-screen overlay must never outrank the new active control.
        if (offerSlotHighlight && client.getVarbitValue(VarbitID.GE_SELECTEDSLOT) > 0)
        {
            return null;
        }

        boolean occupiedOfferSlot = offerSlotHighlight && isOccupied(slotMatch.slotIndex);
        boolean normalBlueHighlight = isBlueHighlight(highlight.getColor());
        ResolvedCopilotAction.Role role = resolveOfferSlotRole(
            offerSlotHighlight,
            highlight.getColor());

        if (occupiedOfferSlot
            && role == ResolvedCopilotAction.Role.GENERIC_WIDGET
            && !normalBlueHighlight)
        {
            // Unknown colours on occupied slots are unsafe: a left-click opens View offer.
            return null;
        }

        Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
        List<Widget> offerDescendants = descendants(offerContainer);

        Widget confirm = findFirst(offerDescendants, "Confirm");
        Widget quantityAll = findFirst(offerDescendants, "All");
        List<Widget> ellipsisButtons = findAll(offerDescendants, "...");
        ellipsisButtons.sort(Comparator.comparingInt(widget -> widget.getBounds().x));

        Widget quantity = ellipsisButtons.isEmpty()
            ? visibleChild(offerContainer, 7)
            : ellipsisButtons.get(0);
        Widget price = ellipsisButtons.size() < 2
            ? visibleChild(offerContainer, 12)
            : ellipsisButtons.get(ellipsisButtons.size() - 1);

        Widget collect = findFirst(
            descendants(client.getWidget(InterfaceID.GE_OFFERS, 6)),
            "Collect");
        Widget back = client.getWidget(InterfaceID.GE_OFFERS, 4);

        Widget interactive = source;
        if (role == ResolvedCopilotAction.Role.ABORT
            || role == ResolvedCopilotAction.Role.MODIFY)
        {
            // Critical: always use the slot selected by screen geometry. Copilot overlays can
            // retain a stale backing Widget reference while their drawn bounds are current.
            interactive = slotMatch.slotWidget;
        }
        else if (matches(source, overlayBounds, quantityAll))
        {
            role = ResolvedCopilotAction.Role.QUANTITY_ALL;
            interactive = quantityAll;
        }
        else if (matches(source, overlayBounds, quantity))
        {
            role = ResolvedCopilotAction.Role.QUANTITY;
            interactive = quantity;
        }
        else if (matches(source, overlayBounds, price))
        {
            role = ResolvedCopilotAction.Role.PRICE;
            interactive = price;
        }
        else if (matches(source, overlayBounds, confirm))
        {
            role = ResolvedCopilotAction.Role.CONFIRM;
            interactive = confirm;
        }
        else if (matches(source, overlayBounds, collect))
        {
            role = ResolvedCopilotAction.Role.COLLECT;
            interactive = collect;
        }
        else if (matches(source, overlayBounds, back))
        {
            role = ResolvedCopilotAction.Role.BACK;
            interactive = back;
        }

        Rectangle clickBounds;
        if (role == ResolvedCopilotAction.Role.ABORT
            || role == ResolvedCopilotAction.Role.MODIFY)
        {
            // Right-click the exact highlighted area, but invoke only an entry tied to the
            // geometry-resolved slot root.
            clickBounds = new Rectangle(overlayBounds);
        }
        else
        {
            clickBounds = role == ResolvedCopilotAction.Role.GENERIC_WIDGET
                ? new Rectangle(overlayBounds)
                : bounds(interactive, overlayBounds);
        }

        int slotIndex = slotMatch == null ? -1 : slotMatch.slotIndex;
        int slotWidgetId = slotMatch == null ? -1 : slotMatch.slotWidget.getId();
        String signature = "W:" + role + ':'
            + source.getId() + ':' + source.getIndex() + ':' + source.getItemId() + ':'
            + "slot=" + slotIndex + ':' + slotWidgetId + ':'
            + clickBounds.x + ':' + clickBounds.y + ':' + clickBounds.width + ':' + clickBounds.height;

        return ResolvedCopilotAction.widget(
            role,
            source,
            interactive,
            clickBounds,
            signature,
            slotIndex,
            slotWidgetId);
    }

    private OfferSlotMatch findOwningOfferSlot(Widget source, Rectangle overlayBounds)
    {
        OfferSlotMatch best = null;
        long bestScore = Long.MIN_VALUE;
        int centerX = overlayBounds.x + overlayBounds.width / 2;
        int centerY = overlayBounds.y + overlayBounds.height / 2;

        for (int slotIndex = 0; slotIndex < GE_SLOT_COUNT; slotIndex++)
        {
            Widget slotWidget = client.getWidget(
                InterfaceID.GE_OFFERS,
                FIRST_GE_SLOT_CHILD + slotIndex);
            if (!isVisible(slotWidget))
            {
                continue;
            }

            Rectangle slotBounds = slotWidget.getBounds();
            Rectangle intersection = overlayBounds.intersection(slotBounds);
            long intersectionArea = intersection.isEmpty()
                ? 0L
                : (long) intersection.width * intersection.height;
            boolean containsCenter = slotBounds.contains(centerX, centerY);

            // Geometry is authoritative. Widget identity is only a small tie-breaker because
            // Copilot can draw a current rectangle from a stale slot Widget reference.
            long score = intersectionArea;
            if (containsCenter)
            {
                score += 1_000_000_000L;
            }
            if (source == slotWidget)
            {
                score += 10_000L;
            }
            else if (source.getId() == slotWidget.getId()
                && source.getIndex() == slotWidget.getIndex())
            {
                score += 5_000L;
            }

            if ((containsCenter || intersectionArea > 0L) && score > bestScore)
            {
                best = new OfferSlotMatch(slotIndex, slotWidget);
                bestScore = score;
            }
        }

        if (best != null)
        {
            return best;
        }

        // Last-resort compatibility fallback for clients that report zero-sized slot roots.
        int sourceGroup = source.getId() >>> 16;
        int sourceChild = source.getId() & 0xFFFF;
        int fallbackIndex = sourceChild - FIRST_GE_SLOT_CHILD;
        if (sourceGroup == InterfaceID.GE_OFFERS
            && fallbackIndex >= 0
            && fallbackIndex < GE_SLOT_COUNT)
        {
            Widget slotWidget = client.getWidget(
                InterfaceID.GE_OFFERS,
                FIRST_GE_SLOT_CHILD + fallbackIndex);
            if (slotWidget != null)
            {
                return new OfferSlotMatch(fallbackIndex, slotWidget);
            }
        }
        return null;
    }

    private ResolvedCopilotAction.Role resolveOfferSlotRole(
        boolean offerSlotHighlight,
        Color color)
    {
        if (!offerSlotHighlight || color == null)
        {
            return ResolvedCopilotAction.Role.GENERIC_WIDGET;
        }

        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();

        if (red >= 180 && green <= 80 && blue <= 100)
        {
            return ResolvedCopilotAction.Role.ABORT;
        }

        if (red >= 170 && green >= 70 && green <= 180 && blue <= 90)
        {
            return ResolvedCopilotAction.Role.MODIFY;
        }

        return ResolvedCopilotAction.Role.GENERIC_WIDGET;
    }

    private boolean isBlueHighlight(Color color)
    {
        if (color == null)
        {
            return false;
        }

        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        return red <= 100 && green >= 120 && blue >= 170;
    }

    private boolean isOccupied(int slotIndex)
    {
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers != null && slotIndex >= 0 && slotIndex < offers.length)
        {
            GrandExchangeOffer offer = offers[slotIndex];
            return offer != null && offer.getState() != GrandExchangeOfferState.EMPTY;
        }

        Widget slotWidget = client.getWidget(
            InterfaceID.GE_OFFERS,
            FIRST_GE_SLOT_CHILD + slotIndex);
        for (Widget descendant : descendants(slotWidget))
        {
            if ("Empty".equalsIgnoreCase(normalize(descendant.getText())))
            {
                return false;
            }
        }
        return true;
    }

    private boolean matches(Widget source, Rectangle overlayBounds, Widget candidate)
    {
        if (!isVisible(candidate))
        {
            return false;
        }

        if (source == candidate
            || (source.getId() == candidate.getId() && source.getIndex() == candidate.getIndex()))
        {
            return true;
        }

        Rectangle candidateBounds = candidate.getBounds();
        Rectangle intersection = overlayBounds.intersection(candidateBounds);
        if (intersection.isEmpty())
        {
            return false;
        }

        long overlapArea = (long) intersection.width * intersection.height;
        long smallerArea = Math.min(
            (long) overlayBounds.width * overlayBounds.height,
            (long) candidateBounds.width * candidateBounds.height);
        return overlapArea >= Math.max(12L, Math.round(smallerArea * 0.25d));
    }

    private Rectangle overlayBounds(Widget widget, Rectangle relativeBounds)
    {
        Rectangle widgetBounds = widget.getBounds();
        if (!validBounds(widgetBounds))
        {
            return null;
        }

        Rectangle result = new Rectangle(widgetBounds);
        if (relativeBounds != null)
        {
            result.x += relativeBounds.x;
            result.y += relativeBounds.y;
            result.width = relativeBounds.width;
            result.height = relativeBounds.height;
        }
        return result;
    }

    private Rectangle bounds(Widget widget, Rectangle fallback)
    {
        return isVisible(widget) ? new Rectangle(widget.getBounds()) : new Rectangle(fallback);
    }

    private Widget visibleChild(Widget parent, int index)
    {
        if (parent == null)
        {
            return null;
        }
        Widget child = parent.getChild(index);
        return isVisible(child) ? child : null;
    }

    private Widget findFirst(List<Widget> widgets, String label)
    {
        for (Widget widget : widgets)
        {
            if (isVisible(widget) && hasLabel(widget, label))
            {
                return widget;
            }
        }
        return null;
    }

    private List<Widget> findAll(List<Widget> widgets, String label)
    {
        List<Widget> matches = new ArrayList<>();
        for (Widget widget : widgets)
        {
            if (isVisible(widget) && hasLabel(widget, label))
            {
                matches.add(widget);
            }
        }
        return matches;
    }

    private boolean hasLabel(Widget widget, String expected)
    {
        if (expected.equalsIgnoreCase(normalize(widget.getText()))
            || expected.equalsIgnoreCase(normalize(widget.getName())))
        {
            return true;
        }

        String[] actions = widget.getActions();
        if (actions == null)
        {
            return false;
        }
        for (String action : actions)
        {
            if (expected.equalsIgnoreCase(normalize(action)))
            {
                return true;
            }
        }
        return false;
    }

    private List<Widget> descendants(Widget root)
    {
        if (root == null)
        {
            return Collections.emptyList();
        }

        List<Widget> result = new ArrayList<>();
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(root, result, visited);
        return result;
    }

    private void collect(Widget widget, List<Widget> output, Set<Widget> visited)
    {
        if (widget == null || !visited.add(widget))
        {
            return;
        }
        output.add(widget);
        collect(widget.getDynamicChildren(), output, visited);
        collect(widget.getChildren(), output, visited);
        collect(widget.getNestedChildren(), output, visited);
        collect(widget.getStaticChildren(), output, visited);
    }

    private void collect(Widget[] widgets, List<Widget> output, Set<Widget> visited)
    {
        if (widgets == null)
        {
            return;
        }
        for (Widget widget : widgets)
        {
            collect(widget, output, visited);
        }
    }

    private boolean isVisible(Widget widget)
    {
        return widget != null && !widget.isHidden() && validBounds(widget.getBounds());
    }

    private boolean validBounds(Rectangle rectangle)
    {
        return rectangle != null && rectangle.width > 0 && rectangle.height > 0;
    }

    private String normalize(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replaceAll("<[^>]+>", "").trim();
    }

    private String safe(String value)
    {
        return value == null ? "" : value;
    }

    private static final class OfferSlotMatch
    {
        private final int slotIndex;
        private final Widget slotWidget;

        private OfferSlotMatch(int slotIndex, Widget slotWidget)
        {
            this.slotIndex = slotIndex;
            this.slotWidget = slotWidget;
        }
    }
}
