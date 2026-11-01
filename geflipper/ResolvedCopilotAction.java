package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Rectangle;
import net.runelite.api.NPC;
import net.runelite.api.widgets.Widget;

final class ResolvedCopilotAction
{
    enum Role
    {
        ABORT(0),
        MODIFY(0),
        QUANTITY_ALL(1),
        QUANTITY(2),
        PRICE(3),
        CONFIRM(4),
        COLLECT(5),
        BACK(6),
        GENERIC_WIDGET(10),
        NPC(10);

        private final int priority;

        Role(int priority)
        {
            this.priority = priority;
        }

        int getPriority()
        {
            return priority;
        }
    }

    private final Role role;
    private final Widget sourceWidget;
    private final Widget interactiveWidget;
    private final NPC npc;
    private final Rectangle clickBounds;
    private final String signature;
    private final int grandExchangeSlotIndex;
    private final int grandExchangeSlotWidgetId;

    private ResolvedCopilotAction(
        Role role,
        Widget sourceWidget,
        Widget interactiveWidget,
        NPC npc,
        Rectangle clickBounds,
        String signature,
        int grandExchangeSlotIndex,
        int grandExchangeSlotWidgetId)
    {
        this.role = role;
        this.sourceWidget = sourceWidget;
        this.interactiveWidget = interactiveWidget;
        this.npc = npc;
        this.clickBounds = clickBounds == null ? null : new Rectangle(clickBounds);
        this.signature = signature;
        this.grandExchangeSlotIndex = grandExchangeSlotIndex;
        this.grandExchangeSlotWidgetId = grandExchangeSlotWidgetId;
    }

    static ResolvedCopilotAction widget(
        Role role,
        Widget sourceWidget,
        Widget interactiveWidget,
        Rectangle clickBounds,
        String signature,
        int grandExchangeSlotIndex,
        int grandExchangeSlotWidgetId)
    {
        return new ResolvedCopilotAction(
            role,
            sourceWidget,
            interactiveWidget,
            null,
            clickBounds,
            signature,
            grandExchangeSlotIndex,
            grandExchangeSlotWidgetId);
    }

    static ResolvedCopilotAction npc(NPC npc, String signature)
    {
        return new ResolvedCopilotAction(
            Role.NPC,
            null,
            null,
            npc,
            null,
            signature,
            -1,
            -1);
    }

    Role getRole()
    {
        return role;
    }

    Widget getSourceWidget()
    {
        return sourceWidget;
    }

    Widget getInteractiveWidget()
    {
        return interactiveWidget;
    }

    NPC getNpc()
    {
        return npc;
    }

    Rectangle getClickBounds()
    {
        return clickBounds == null ? null : new Rectangle(clickBounds);
    }

    String getSignature()
    {
        return signature;
    }

    int getGrandExchangeSlotIndex()
    {
        return grandExchangeSlotIndex;
    }

    int getGrandExchangeSlotWidgetId()
    {
        return grandExchangeSlotWidgetId;
    }

    boolean isGrandExchangeSlotAction()
    {
        return role == Role.ABORT || role == Role.MODIFY;
    }
}
