package net.runelite.client.plugins.microbot.kspbonestobananas;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Edits an existing Grand Exchange offer without aborting or replacing its slot.
 * Uses the actual right-click menu: slot widgets do not reliably expose "Modify offer"
 * through Widget.getActions(), even when the option exists in the live menu.
 */
@Slf4j
final class KspGeOfferModifier
{
    private static final int MENU_ENTRY_HEIGHT = 15;
    private static final int REQUIRED_STABLE_POLLS = 3;
    private static final int PRICE_VARBIT = 4398; // GE offer price (Microbot's getOfferPrice)
    private static final long MENU_TIMEOUT_MS = 2500L;

    private KspGeOfferModifier() { }

    static boolean modifyPrice(GrandExchangeSlots slot, int price)
    {
        if (slot == null || price <= 0 || !Rs2GrandExchange.isOpen()
                || Rs2GrandExchange.isOfferScreenOpen()) return false;

        if (!openModifyScreen(slot))
        {
            log.warn("GE modify failed at menu selection | slot={}", slot);
            return false;
        }

        Widget container = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 26);
        // Matches GrandExchangeWidget.getPricePerItemButton_X() in Microbot.
        Widget priceButton = container == null ? null : container.getChild(12);
        if (priceButton == null || !valid(priceButton.getBounds())
                || priceButton.isHidden() || !Rs2Widget.clickWidget(priceButton))
        {
            log.warn("GE modify failed: price input widget unavailable | slot={}", slot);
            return false;
        }

        if (!sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null, 2500))
        {
            log.warn("GE modify failed: price chatbox did not open | slot={}", slot);
            return false;
        }

        // Use the same chatbox setter as Rs2GrandExchange.setPrice, but verify
        // the actual GE price varbit before confirming the updated offer.
        sleep(180, 280);
        Rs2GrandExchange.setChatboxValue(price);
        sleep(140, 220);
        Rs2Keyboard.enter();
        if (!sleepUntil(() -> Microbot.getVarbitValue(PRICE_VARBIT) == price, 3000))
        {
            log.warn("GE modify failed: price input was not accepted | slot={} requested={}", slot, price);
            return false;
        }

        // The widget tree may have been rebuilt while the chatbox was active.
        container = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 26);
        Widget[] children = container == null ? null : container.getDynamicChildren();
        Widget confirm = children == null ? null
                : Rs2Widget.findWidget("Confirm", Arrays.asList(children), true);
        if (confirm == null || !valid(confirm.getBounds()) || confirm.isHidden()
                || !Rs2Widget.clickWidget(confirm))
        {
            log.warn("GE modify failed: confirm button unavailable | slot={}", slot);
            return false;
        }

        if (sleepUntil(() -> Rs2Widget.hasWidget("Your offer is much"), 1000))
        {
            Rs2Widget.clickWidget("Yes");
        }
        if (!sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 4000))
        {
            log.warn("GE modify: confirm did not return to overview | slot={}", slot);
            return false;
        }
        // Caller must still verify the changed offer price from the GE state.
        return true;
    }

    private static boolean openModifyScreen(GrandExchangeSlots slot)
    {
        for (int attempt = 0; attempt < 2; attempt++)
        {
            Widget widget = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 7 + slot.ordinal());
            if (widget == null || widget.isHidden() || !valid(widget.getBounds()))
                return false;
            Rectangle bounds = new Rectangle(widget.getBounds());

            if (menuOpen() && !closeMenu()) return false;
            Microbot.getMouse().click(bounds.x + bounds.width / 2,
                    bounds.y + bounds.height / 2, true);

            MenuTarget option = awaitStableModifyOption(widget.getId());
            if (option != null)
            {
                Microbot.getMouse().click(option.clickBounds);
                if (sleepUntil(Rs2GrandExchange::isOfferScreenOpen, 3000))
                    return true;
            }
            closeMenu();
            sleep(100, 180);
        }
        return false;
    }

    private static MenuTarget awaitStableModifyOption(int widgetId)
    {
        long deadline = System.currentTimeMillis() + MENU_TIMEOUT_MS;
        MenuTarget previous = null;
        int stable = 0;
        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted())
        {
            MenuTarget candidate = readLiveModifyOption(widgetId);
            if (candidate == null)
            {
                previous = null;
                stable = 0;
            }
            else
            {
                stable = candidate.matches(previous) ? stable + 1 : 1;
                previous = candidate;
                if (stable >= REQUIRED_STABLE_POLLS) return candidate;
            }
            sleep(65, 95);
        }
        return null;
    }

    private static MenuTarget readLiveModifyOption(int widgetId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            if (!client.isMenuOpen()) return null;
            Menu menu = client.getMenu();
            if (menu == null) return null;
            int x = menu.getMenuX(), y = menu.getMenuY();
            int width = menu.getMenuWidth(), height = menu.getMenuHeight();
            MenuEntry[] entries = menu.getMenuEntries();
            if (width <= 4 || height <= MENU_ENTRY_HEIGHT || entries == null) return null;

            for (int index = entries.length - 1; index >= 0; index--)
            {
                MenuEntry entry = entries[index];
                if (entry == null || entry.getParam1() != widgetId
                        || !"Modify offer".equalsIgnoreCase(clean(entry.getOption())))
                    continue;

                int centerY = y + height - 8 - index * MENU_ENTRY_HEIGHT;
                Rectangle row = new Rectangle(x + 2, centerY - 6, width - 4, 13);
                if (row.y < y || row.y + row.height > y + height) return null;
                String signature = x + ":" + y + ":" + width + ":" + height
                        + ":" + entries.length + ":" + index + ":" + entry.getType()
                        + ":" + entry.getIdentifier() + ":" + entry.getParam0()
                        + ":" + entry.getParam1() + ":" + entry.getItemId();
                return new MenuTarget(row, signature);
            }
            return null;
        }).orElse(null);
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.replaceAll("<[^>]+>", "").trim();
    }

    private static boolean valid(Rectangle bounds)
    {
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    private static boolean menuOpen()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getClient().isMenuOpen()).orElse(false);
    }

    private static boolean closeMenu()
    {
        if (!menuOpen()) return true;
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        return sleepUntil(() -> !menuOpen(), 800);
    }

    private static final class MenuTarget
    {
        final Rectangle clickBounds;
        final String signature;

        MenuTarget(Rectangle clickBounds, String signature)
        {
            this.clickBounds = clickBounds;
            this.signature = signature;
        }

        boolean matches(MenuTarget other)
        {
            return other != null && signature.equals(other.signature);
        }
    }
}
