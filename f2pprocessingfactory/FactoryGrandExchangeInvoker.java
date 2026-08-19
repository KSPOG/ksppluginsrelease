package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Adapter around Microbot's Grand Exchange API.
 *
 * <p>Initial BUY/SELL setup is delegated to Microbot's native
 * {@link Rs2GrandExchange#processOffer(GrandExchangeRequest)} implementation.
 * Repricing an already-active offer follows the supplied GE Flipper's proven
 * slot-context workflow: right-click the exact GE slot root, select the stable
 * {@code Modify offer} menu entry for that slot, change only the price, and
 * resubmit the existing offer.</p>
 */
final class FactoryGrandExchangeInvoker
{
    private static final int POST_OFFER_SETTLE_MIN_MILLIS = 350;
    private static final int POST_OFFER_SETTLE_MAX_MILLIS = 650;
    private static final int CLOSE_TIMEOUT_MILLIS = 5_000;

    private static final int OFFER_CONTAINER_CHILD = 26;
    // Keep these aligned with Microbot's GrandExchangeWidget implementation:
    // getPricePerItemButton_X() -> offer child 12, getItemPriceWidget() -> child 41.
    private static final int PRICE_X_CHILD = 12;
    private static final int PRICE_DISPLAY_CHILD = 41;
    private static final int INPUT_OPEN_MODE = 7;
    private static final int PRICE_ENTRY_ATTEMPTS = 3;
    private static final String PRICE_ENTRY_PROMPT = "Set a price for each item:";
    private static final int PRICE_ENTRY_TIMEOUT_MILLIS = 3_000;
    private static final int PRICE_VERIFY_TIMEOUT_MILLIS = 4_000;
    private static final int FACTORY_ENTER_MIN_GAP_MILLIS = 1_500;
    private static final int MODIFY_OPEN_TIMEOUT_MILLIS = 2_800;
    private static final int MODIFY_RECOVERY_TIMEOUT_MILLIS = 3_000;
    private static final int MENU_OPEN_TIMEOUT_MILLIS = 2_500;
    private static final int MENU_STABLE_POLLS = 3;
    private static final int MENU_POLL_DELAY_MIN_MILLIS = 65;
    private static final int MENU_POLL_DELAY_MAX_MILLIS = 95;
    private static final int MENU_ENTRY_HEIGHT = 15;

    private static volatile String lastFailureReason = "";
    private static volatile boolean modifyInProgress = false;
    private static volatile long modifySellProtectionUntil = 0L;
    private static volatile long lastFactoryEnterAt = 0L;
    private static volatile boolean initialPlacementInProgress = false;

    private FactoryGrandExchangeInvoker()
    {
    }

    static String getLastFailureReason()
    {
        return lastFailureReason;
    }

    private static boolean isExactGeOverviewReady()
    {
        if (!Rs2GrandExchange.isOpen() || Rs2GrandExchange.isOfferScreenOpen()
            || isChatboxInputOpen() || isMesText2Visible() || isMenuOpen())
        {
            return false;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget frame = Microbot.getClient().getWidget(InterfaceID.GE_OFFERS, 1);
            return frame != null && !frame.isHidden();
        }).orElse(false);
    }

    static boolean placeBuyOffer(
        GrandExchangeSlots slot,
        String itemName,
        int itemId,
        int quantity,
        int price)
    {
        lastFailureReason = "";

        if (!validRequest(itemName, itemId, quantity, price))
        {
            return fail("invalid buy request");
        }

        if (!Rs2GrandExchange.isOpen())
        {
            return fail("Grand Exchange is not open");
        }

        // Native Microbot BUY uses Enter internally for GE price/quantity prompts.
        // Never start a fresh native placement while any previous GE editor or
        // chatbox input is still active, otherwise repeated state-machine ticks can
        // make those internal Enter presses appear unrelated to the intended action.
        if (modifyInProgress || initialPlacementInProgress || hasOpenOfferEditor())
        {
            return fail("refusing initial BUY while another GE action/editor is active");
        }
        if (!isExactGeOverviewReady())
        {
            return fail("refusing initial BUY because exact GE overview widget state is not ready");
        }

        GrandExchangeRequest request = GrandExchangeRequest.builder()
            .action(GrandExchangeAction.BUY)
            .slot(slot)
            .itemName(itemName)
            .exact(true)
            .quantity(quantity)
            .price(price)
            .closeAfterCompletion(false)
            .build();

        boolean success;
        initialPlacementInProgress = true;
        try
        {
            if (!isExactGeOverviewReady())
            {
                return fail("initial BUY widget state changed before native placement");
            }
            success = Rs2GrandExchange.processOffer(request);
        }
        catch (Exception ex)
        {
            return fail("Microbot GE BUY threw " + ex.getClass().getSimpleName());
        }
        finally
        {
            initialPlacementInProgress = false;
        }

        if (!success)
        {
            return fail("Microbot GE BUY flow returned false");
        }

        sleep(POST_OFFER_SETTLE_MIN_MILLIS, POST_OFFER_SETTLE_MAX_MILLIS);
        return true;
    }

    static boolean placeSellOffer(
        GrandExchangeSlots preferredSlot,
        String itemName,
        int itemId,
        int quantity,
        int price)
    {
        lastFailureReason = "";

        if (!validRequest(itemName, itemId, quantity, price))
        {
            return fail("invalid sell request");
        }

        if (!Rs2GrandExchange.isOpen())
        {
            return fail("Grand Exchange is not open");
        }

        // A new SELL is the only path that may interact with an inventory item.
        // Never allow it while Modify offer is running or while any GE offer editor
        // is already open; doing so can make the client click Offer on the inventory
        // item instead of editing the existing slot.
        if (modifyInProgress || initialPlacementInProgress || hasOpenOfferEditor()
            || System.currentTimeMillis() < modifySellProtectionUntil)
        {
            return fail("refusing initial SELL during/just after another GE action or while an editor is open");
        }
        if (!isExactGeOverviewReady())
        {
            return fail("refusing initial SELL because exact GE overview widget state is not ready");
        }

        // Microbot's SELL path deliberately starts from the inventory item's
        // "Offer" action and lets the client choose an available GE slot.
        // This diagnostic is intentionally explicit: if an inventory interaction is
        // ever observed during a supposed Modify, the log will prove whether the
        // initial-SELL path was entered.
        Microbot.log("KSP AIO Factory GE INITIAL SELL inventory Offer path: item="
            + itemName + " itemId=" + itemId + " qty=" + quantity + " price=" + price);
        GrandExchangeRequest request = GrandExchangeRequest.builder()
            .action(GrandExchangeAction.SELL)
            .itemName(itemName)
            .exact(true)
            .quantity(quantity)
            .price(price)
            .closeAfterCompletion(false)
            .build();

        boolean success;
        initialPlacementInProgress = true;
        try
        {
            if (!isExactGeOverviewReady())
            {
                return fail("initial SELL widget state changed before native placement");
            }
            success = Rs2GrandExchange.processOffer(request);
        }
        catch (Exception ex)
        {
            return fail("Microbot GE SELL threw " + ex.getClass().getSimpleName());
        }
        finally
        {
            initialPlacementInProgress = false;
        }

        if (!success)
        {
            return fail("Microbot GE SELL flow returned false");
        }

        sleep(POST_OFFER_SETTLE_MIN_MILLIS, POST_OFFER_SETTLE_MAX_MILLIS);
        return true;
    }

    /**
     * Reprices the exact occupied GE slot through its visible "Modify offer"
     * context-menu entry. This intentionally does not abort and recreate the offer.
     */
    static boolean modifyOfferPrice(GrandExchangeSlots slot, int expectedItemId, int newPrice)
    {
        lastFailureReason = "";
        if (slot == null || expectedItemId <= 0 || newPrice <= 0)
        {
            return fail("invalid modify-offer request");
        }
        if (!Rs2GrandExchange.isOpen())
        {
            return fail("Grand Exchange is not open for Modify offer");
        }

        int originalPrice = getLiveOfferPrice(slot, expectedItemId);
        if (originalPrice == newPrice)
        {
            return fail("requested Modify offer price " + newPrice
                + " is identical to the live offer price");
        }

        modifyInProgress = true;
        boolean modifyCompleted = false;
        try
        {
            Microbot.log("KSP AIO Factory GE Modify: slot=" + slot
                + " itemId=" + expectedItemId
                + " livePrice=" + originalPrice
                + " requestedPrice=" + newPrice);

            SlotTarget target = resolveSlotTarget(slot, expectedItemId);
            if (target == null)
            {
                return fail("could not resolve occupied GE slot for Modify offer");
            }
            if (!closeOpenMenu())
            {
                return fail("could not close stale context menu before Modify offer");
            }

            int clickX = target.bounds.x + target.bounds.width / 2;
            int clickY = target.bounds.y + target.bounds.height / 2;
            Microbot.getMouse().click(clickX, clickY, true);

            MenuSnapshot menu = waitForStableSlotMenu("Modify offer", target.widgetId);
            if (menu == null)
            {
                closeOpenMenu();
                return fail("GE slot did not expose a stable Modify offer menu entry");
            }

            Rectangle entryBounds = menu.entryBounds();
            if (!validBounds(entryBounds))
            {
                closeOpenMenu();
                return fail("could not resolve Modify offer menu-row bounds");
            }

            Microbot.getMouse().click(entryBounds);
            if (!sleepUntil(() -> !isMenuOpen(), 1_200))
            {
                closeOpenMenu();
                return fail("Modify offer context menu remained open");
            }

            if (!sleepUntil(FactoryGrandExchangeInvoker::isOfferSetupScreenOpen, MODIFY_OPEN_TIMEOUT_MILLIS))
            {
                return fail("Modify offer did not open the offer setup screen");
            }

            if (!setCurrentOfferPrice(newPrice))
            {
                return false;
            }
            if (!submitCurrentOffer())
            {
                return false;
            }

            boolean changed = sleepUntil(
                () -> getLiveOfferPrice(slot, expectedItemId) == newPrice,
                PRICE_VERIFY_TIMEOUT_MILLIS);
            if (!changed)
            {
                int observed = getLiveOfferPrice(slot, expectedItemId);
                return fail("Modify offer submitted but live price is " + observed
                    + " instead of requested " + newPrice);
            }

            sleep(POST_OFFER_SETTLE_MIN_MILLIS, POST_OFFER_SETTLE_MAX_MILLIS);
            modifyCompleted = true;
            return true;
        }
        finally
        {
            // A failed Modify must never strand the factory inside the sell editor.
            // If any step fails after the editor opens, return to the GE overview
            // before releasing the Modify guard. Otherwise the next script tick only
            // sees the slot binding and waits forever while Confirm remains visible.
            if (!modifyCompleted && hasOpenOfferEditor())
            {
                String failure = lastFailureReason;
                Microbot.log("KSP AIO Factory GE Modify recovery: failure=" + failure);
                recoverOfferEditorToOverview();
            }

            // Keep the inventory-based initial SELL path blocked briefly after the
            // editor closes as well. The client GE offer array can lag the UI by a
            // few ticks after Modify is submitted.
            modifySellProtectionUntil = System.currentTimeMillis() + 5_000L;
            modifyInProgress = false;
        }
    }

    /**
     * Cancels the exact occupied slot through its visible "Abort offer" context-menu
     * entry without collecting. The script decides when to press the GE Collect
     * button, which is important when several factory input offers are live together.
     */
    static boolean cancelOfferWithoutCollect(GrandExchangeSlots slot, int expectedItemId)
    {
        lastFailureReason = "";
        if (slot == null || expectedItemId <= 0)
        {
            return fail("invalid cancel-offer request");
        }
        if (!Rs2GrandExchange.isOpen())
        {
            return fail("Grand Exchange is not open for Abort offer");
        }

        SlotTarget target = resolveSlotTarget(slot, expectedItemId);
        if (target == null)
        {
            return fail("could not resolve occupied GE slot for Abort offer");
        }
        if (!closeOpenMenu())
        {
            return fail("could not close stale context menu before Abort offer");
        }

        int clickX = target.bounds.x + target.bounds.width / 2;
        int clickY = target.bounds.y + target.bounds.height / 2;
        Microbot.getMouse().click(clickX, clickY, true);

        MenuSnapshot menu = waitForStableSlotMenu("Abort offer", target.widgetId);
        if (menu == null)
        {
            closeOpenMenu();
            return fail("GE slot did not expose a stable Abort offer menu entry");
        }

        Rectangle entryBounds = menu.entryBounds();
        if (!validBounds(entryBounds))
        {
            closeOpenMenu();
            return fail("could not resolve Abort offer menu-row bounds");
        }

        Microbot.getMouse().click(entryBounds);
        if (!sleepUntil(() -> !isMenuOpen(), 1_200))
        {
            closeOpenMenu();
            return fail("Abort offer context menu remained open");
        }
        sleep(200, 400);
        return true;
    }

    private static boolean setCurrentOfferPrice(int price)
    {
        int initialEditorPrice = getCurrentEditorPrice();
        if (initialEditorPrice == price)
        {
            return true;
        }

        for (int attempt = 1; attempt <= PRICE_ENTRY_ATTEMPTS; attempt++)
        {
            if (!clickCurrentOfferPriceControl())
            {
                return fail("Modify offer price control was not available/clickable");
            }

            // Enter is dangerous when the wrong chatbox layer is active. Require
            // all three signals for the exact GE absolute-price prompt before we
            // write a value or press Enter: MESLAYERMODE=7, MES_TEXT2 visible, and
            // the literal "Set a price for each item:" prompt.
            if (!sleepUntil(
                FactoryGrandExchangeInvoker::isPriceEntryInputOpen,
                PRICE_ENTRY_TIMEOUT_MILLIS))
            {
                closePriceInputIfOpen();
                if (attempt == PRICE_ENTRY_ATTEMPTS)
                {
                    return fail("Modify offer did not open the exact 'Set a price for each item:' input");
                }
                sleep(250, 450);
                continue;
            }

            sleep(350, 600);
            if (!isPriceEntryInputOpen())
            {
                closePriceInputIfOpen();
                if (attempt == PRICE_ENTRY_ATTEMPTS)
                {
                    return fail("Modify offer price prompt disappeared before value entry");
                }
                continue;
            }

            Rs2GrandExchange.setChatboxValue(price);
            sleep(250, 450);
            if (!pressEnterForPricePrompt(price))
            {
                closePriceInputIfOpen();
                if (attempt == PRICE_ENTRY_ATTEMPTS)
                {
                    return fail("Modify offer refused Enter because the exact price prompt was not active");
                }
                continue;
            }

            if (!sleepUntil(() -> !isPriceEntryInputOpen(), PRICE_ENTRY_TIMEOUT_MILLIS))
            {
                closePriceInputIfOpen();
                if (attempt == PRICE_ENTRY_ATTEMPTS)
                {
                    return fail("Modify offer price input did not close after entering " + price);
                }
                continue;
            }

            // Do not click Confirm until the offer editor itself displays the exact
            // requested value. This catches swallowed Enter/input events before they
            // can resubmit the previous price.
            boolean editorChanged = sleepUntil(
                () -> getCurrentEditorPrice() == price,
                PRICE_VERIFY_TIMEOUT_MILLIS);
            int editorPrice = getCurrentEditorPrice();
            if (editorChanged)
            {
                Microbot.log("KSP AIO Factory GE Modify editor price changed: "
                    + initialEditorPrice + " -> " + editorPrice + " (attempt " + attempt + ")");
                sleep(500, 800);
                return true;
            }

            Microbot.log("KSP AIO Factory GE Modify editor kept price " + editorPrice
                + " instead of " + price + " on attempt " + attempt);
            sleep(300, 500);
        }

        return fail("Modify offer editor never changed from " + initialEditorPrice
            + " to requested " + price);
    }

    /**
     * Clicks the same absolute-price widget that Microbot's native GE setPrice()
     * uses: GE offer-container child 12.
     *
     * <p>Do not synthesize a CC_OP here. A malformed param0/identifier pair can
     * cause the client to execute a different widget operation even when the mouse
     * point is harmless. Resolve the exact child fresh, prove that its centre lies
     * inside the live GE offer container, then physically click that widget just as
     * Microbot's own setPrice implementation does.</p>
     */
    private static boolean clickCurrentOfferPriceControl()
    {
        Widget priceWidget = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget container = Microbot.getClient().getWidget(InterfaceID.GE_OFFERS, OFFER_CONTAINER_CHILD);
            if (container == null || container.isHidden())
            {
                return null;
            }

            Widget candidate = container.getChild(PRICE_X_CHILD);
            if (candidate == null || candidate.isHidden())
            {
                return null;
            }

            Rectangle containerBounds = container.getBounds();
            Rectangle priceBounds = candidate.getBounds();
            if (!validBounds(containerBounds) || !validBounds(priceBounds))
            {
                return null;
            }

            int centerX = priceBounds.x + priceBounds.width / 2;
            int centerY = priceBounds.y + priceBounds.height / 2;
            if (!containerBounds.contains(centerX, centerY))
            {
                Microbot.log("KSP AIO Factory GE Modify refused price widget outside offer container: "
                    + priceBounds + " container=" + containerBounds);
                return null;
            }
            return candidate;
        }).orElse(null);

        if (priceWidget == null)
        {
            return false;
        }

        Microbot.log("KSP AIO Factory GE Modify clicking native price widget: id="
            + priceWidget.getId() + " index=" + priceWidget.getIndex()
            + " bounds=" + priceWidget.getBounds());
        return Rs2Widget.clickWidget(priceWidget);
    }

    private static int getCurrentEditorPrice()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget container = Microbot.getClient().getWidget(InterfaceID.GE_OFFERS, OFFER_CONTAINER_CHILD);
            if (container == null || container.isHidden())
            {
                return -1;
            }

            Widget priceWidget = container.getChild(PRICE_DISPLAY_CHILD);
            if (priceWidget == null || priceWidget.isHidden())
            {
                return -1;
            }
            String text = normalize(priceWidget.getText());
            if (text.isEmpty())
            {
                return -1;
            }
            String digits = text.replaceAll("[^0-9]", "");
            if (digits.isEmpty())
            {
                return -1;
            }
            try
            {
                long value = Long.parseLong(digits);
                return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
            }
            catch (NumberFormatException ignored)
            {
                return -1;
            }
        }).orElse(-1);
    }

    private static boolean isChatboxInputOpen()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
            Microbot.getClient().getVarcIntValue(VarClientID.MESLAYERMODE) == INPUT_OPEN_MODE
        ).orElse(false);
    }

    private static boolean isMesText2Visible()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget widget = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_TEXT2);
            return widget != null && !widget.isHidden();
        }).orElse(false);
    }

    private static boolean isPriceEntryInputOpen()
    {
        // Strict factory-owned Enter gate. MESLAYERMODE=7 alone is not sufficient:
        // other numeric/chatbox inputs can share that mode. Require the live GE offer
        // container as well as MES_TEXT2 and the literal GE price prompt.
        return isGeOfferContainerVisible()
            && isChatboxInputOpen()
            && isMesText2Visible()
            && Rs2Widget.hasWidget(PRICE_ENTRY_PROMPT);
    }

    private static boolean isGeOfferContainerVisible()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget container = Microbot.getClient().getWidget(
                InterfaceID.GE_OFFERS, OFFER_CONTAINER_CHILD);
            return container != null && !container.isHidden();
        }).orElse(false);
    }

    private static boolean pressEnterForPricePrompt(int price)
    {
        if (!isPriceEntryInputOpen())
        {
            Microbot.log("KSP AIO Factory suppressed Enter: exact GE price prompt is not active");
            return false;
        }

        long now = System.currentTimeMillis();
        long remainingGap = FACTORY_ENTER_MIN_GAP_MILLIS - (now - lastFactoryEnterAt);
        if (remainingGap > 0L)
        {
            sleep((int) Math.min(Integer.MAX_VALUE, remainingGap));
        }

        if (!isPriceEntryInputOpen())
        {
            Microbot.log("KSP AIO Factory suppressed Enter after cooldown: GE price prompt closed");
            return false;
        }

        Microbot.log("KSP AIO Factory GE Modify pressing Enter for confirmed price prompt: " + price);
        Rs2Keyboard.enter();
        lastFactoryEnterAt = System.currentTimeMillis();
        return true;
    }

    private static void closePriceInputIfOpen()
    {
        // During Modify recovery a stale MESLAYERMODE/MES_TEXT2 layer may remain
        // even if the prompt text has already vanished. Escape may close that layer,
        // but Enter is never sent unless isPriceEntryInputOpen() is strictly true.
        if (isPriceEntryInputOpen()
            || (isGeOfferContainerVisible() && isChatboxInputOpen() && isMesText2Visible()))
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleepUntil(() -> !isChatboxInputOpen() && !isMesText2Visible(), 800);
        }
    }

    private static int getLiveOfferPrice(GrandExchangeSlots slot, int expectedItemId)
    {
        if (slot == null || expectedItemId <= 0)
        {
            return -1;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            int index = slot.ordinal();
            if (offers == null || index < 0 || index >= offers.length)
            {
                return -1;
            }
            GrandExchangeOffer offer = offers[index];
            if (offer == null || offer.getItemId() != expectedItemId)
            {
                return -1;
            }
            return offer.getPrice();
        }).orElse(-1);
    }

    private static boolean submitCurrentOffer()
    {
        Widget confirm = getCurrentOfferConfirmWidget();
        if (confirm == null || !Rs2Widget.clickWidget(confirm))
        {
            return fail("Modify offer Confirm button was not available/clickable");
        }

        sleepUntil(
            () -> !isOfferSetupScreenOpen()
                || Rs2Widget.hasWidget("Your offer is much"),
            MODIFY_OPEN_TIMEOUT_MILLIS);

        if (Rs2Widget.hasWidget("Your offer is much"))
        {
            if (!Rs2Widget.clickWidget("Yes", true))
            {
                return fail("Modify offer warning confirmation failed");
            }
            sleepUntil(() -> !isOfferSetupScreenOpen(), MODIFY_OPEN_TIMEOUT_MILLIS);
        }

        if (isOfferSetupScreenOpen())
        {
            return fail("Modify offer did not submit");
        }
        return true;
    }

    private static Widget getCurrentOfferConfirmWidget()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget container = Microbot.getClient().getWidget(InterfaceID.GE_OFFERS, OFFER_CONTAINER_CHILD);
            if (container == null || container.isHidden())
            {
                return null;
            }

            Widget[] dynamicChildren = container.getDynamicChildren();
            if (dynamicChildren == null)
            {
                return null;
            }

            for (Widget child : dynamicChildren)
            {
                if (child == null || child.isHidden())
                {
                    continue;
                }
                if ("Confirm".equalsIgnoreCase(normalize(child.getText())))
                {
                    return child;
                }
            }
            return null;
        }).orElse(null);
    }

    private static boolean isOfferSetupScreenOpen()
    {
        // Use the same live GE offer container and dynamic Confirm widget that
        // Microbot's GrandExchangeWidget uses. This avoids a false negative during
        // Modify transitions where the generic offer-screen visibility flag or a
        // global text search has not updated yet.
        return getCurrentOfferConfirmWidget() != null;
    }

    static boolean hasOpenOfferEditor()
    {
        return isChatboxInputOpen() || isOfferSetupScreenOpen();
    }

    /**
     * Recovery used after a failed Modify or by the script when it discovers a
     * tracked factory slot while the setup editor is still open. This path never
     * touches the inventory. It first closes a GE chatbox input, then uses
     * Microbot's canonical Back-to-overview action, and finally falls back to Escape
     * / closing the GE if the client refuses to leave the editor.
     */
    static boolean recoverStaleOfferEditor()
    {
        if (modifyInProgress)
        {
            return false;
        }
        return recoverOfferEditorToOverview();
    }

    private static boolean recoverOfferEditorToOverview()
    {
        closePriceInputIfOpen();
        if (!isOfferSetupScreenOpen())
        {
            return true;
        }

        try
        {
            Rs2GrandExchange.backToOverview();
        }
        catch (Exception ignored)
        {
            // Keyboard/API fallback below.
        }

        if (sleepUntil(() -> !isOfferSetupScreenOpen(), MODIFY_RECOVERY_TIMEOUT_MILLIS))
        {
            Microbot.log("KSP AIO Factory GE Modify recovery returned to overview");
            return true;
        }

        for (int attempt = 1; attempt <= 3; attempt++)
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleep(250, 400);
            if (!isOfferSetupScreenOpen())
            {
                Microbot.log("KSP AIO Factory GE Modify recovery escaped editor on attempt " + attempt);
                return true;
            }
        }

        boolean closed = closeExchangeWithoutMouse();
        Microbot.log("KSP AIO Factory GE Modify recovery fallback closeExchange=" + closed);
        return closed || !isOfferSetupScreenOpen();
    }

    private static SlotTarget resolveSlotTarget(GrandExchangeSlots slot, int expectedItemId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            int slotIndex = slot.ordinal();
            if (slotIndex < 0 || slotIndex >= 8)
            {
                return null;
            }

            Widget slotWidget = Microbot.getClient().getWidget(InterfaceID.GE_OFFERS, 7 + slotIndex);
            if (slotWidget == null || slotWidget.isHidden())
            {
                return null;
            }

            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            if (offers == null || slotIndex >= offers.length || offers[slotIndex] == null)
            {
                return null;
            }
            if (offers[slotIndex].getItemId() != expectedItemId)
            {
                return null;
            }

            Rectangle bounds = slotWidget.getBounds();
            if (!validBounds(bounds))
            {
                return null;
            }
            return new SlotTarget(slotWidget.getId(), new Rectangle(bounds));
        }).orElse(null);
    }

    private static MenuSnapshot waitForStableSlotMenu(String option, int slotWidgetId)
    {
        long deadline = System.currentTimeMillis() + MENU_OPEN_TIMEOUT_MILLIS;
        MenuSnapshot previous = null;
        int stablePolls = 0;

        while (System.currentTimeMillis() < deadline)
        {
            MenuSnapshot current = readOpenSlotMenu(option, slotWidgetId);
            if (current == null)
            {
                previous = null;
                stablePolls = 0;
            }
            else if (current.sameLayoutAndEntry(previous))
            {
                stablePolls++;
                if (stablePolls >= MENU_STABLE_POLLS)
                {
                    return current;
                }
            }
            else
            {
                previous = current;
                stablePolls = 1;
            }
            sleep(MENU_POLL_DELAY_MIN_MILLIS, MENU_POLL_DELAY_MAX_MILLIS);
        }
        return null;
    }

    private static MenuSnapshot readOpenSlotMenu(String option, int slotWidgetId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (!Microbot.getClient().isMenuOpen())
            {
                return null;
            }

            Menu menu = Microbot.getClient().getMenu();
            if (menu == null)
            {
                return null;
            }

            int menuX = menu.getMenuX();
            int menuY = menu.getMenuY();
            int menuWidth = menu.getMenuWidth();
            int menuHeight = menu.getMenuHeight();
            MenuEntry[] entries = menu.getMenuEntries();
            if (menuWidth <= 0 || menuHeight <= 0 || entries == null || entries.length == 0)
            {
                return null;
            }

            for (int index = entries.length - 1; index >= 0; index--)
            {
                MenuEntry entry = entries[index];
                if (entry == null
                    || entry.getParam1() != slotWidgetId
                    || !option.equalsIgnoreCase(normalize(entry.getOption())))
                {
                    continue;
                }

                return new MenuSnapshot(
                    menuX,
                    menuY,
                    menuWidth,
                    menuHeight,
                    entries.length,
                    index,
                    SlotMenuEntry.copyOf(entry));
            }
            return null;
        }).orElse(null);
    }

    private static boolean isMenuOpen()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().isMenuOpen()).orElse(false);
    }

    private static boolean closeOpenMenu()
    {
        if (!isMenuOpen())
        {
            return true;
        }
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        return sleepUntil(() -> !isMenuOpen(), 800);
    }

    /**
     * Close the GE through Microbot's own API. Escape is retained only as a recovery
     * fallback if the client is still on an offer/input layer after the API close call.
     */
    static boolean closeExchangeWithoutMouse()
    {
        if (!Rs2GrandExchange.isOpen() && !Rs2GrandExchange.isOfferScreenOpen())
        {
            return true;
        }

        try
        {
            Rs2GrandExchange.closeExchange();
        }
        catch (Exception ignored)
        {
            // Recovery below.
        }

        if (sleepUntil(
            () -> !Rs2GrandExchange.isOpen() && !Rs2GrandExchange.isOfferScreenOpen(),
            CLOSE_TIMEOUT_MILLIS))
        {
            return true;
        }

        for (int attempt = 0; attempt < 4; attempt++)
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleep(200, 350);
            if (!Rs2GrandExchange.isOpen() && !Rs2GrandExchange.isOfferScreenOpen())
            {
                return true;
            }
        }

        return !Rs2GrandExchange.isOpen() && !Rs2GrandExchange.isOfferScreenOpen();
    }

    private static boolean validRequest(String itemName, int itemId, int quantity, int price)
    {
        return itemName != null
            && !itemName.isBlank()
            && itemId > 0
            && quantity > 0
            && price > 0;
    }

    private static boolean validBounds(Rectangle bounds)
    {
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.replaceAll("<[^>]+>", "").trim();
    }

    private static boolean fail(String reason)
    {
        lastFailureReason = reason == null || reason.isBlank() ? "unknown GE API failure" : reason;
        Microbot.log("KSP AIO Factory GE failure: " + lastFailureReason);
        return false;
    }

    private static final class SlotTarget
    {
        private final int widgetId;
        private final Rectangle bounds;

        private SlotTarget(int widgetId, Rectangle bounds)
        {
            this.widgetId = widgetId;
            this.bounds = bounds;
        }
    }

    private static final class MenuSnapshot
    {
        private final int menuX;
        private final int menuY;
        private final int menuWidth;
        private final int menuHeight;
        private final int entryCount;
        private final int entryIndex;
        private final SlotMenuEntry entry;

        private MenuSnapshot(
            int menuX,
            int menuY,
            int menuWidth,
            int menuHeight,
            int entryCount,
            int entryIndex,
            SlotMenuEntry entry)
        {
            this.menuX = menuX;
            this.menuY = menuY;
            this.menuWidth = menuWidth;
            this.menuHeight = menuHeight;
            this.entryCount = entryCount;
            this.entryIndex = entryIndex;
            this.entry = entry;
        }

        private boolean sameLayoutAndEntry(MenuSnapshot other)
        {
            return other != null
                && menuX == other.menuX
                && menuY == other.menuY
                && menuWidth == other.menuWidth
                && menuHeight == other.menuHeight
                && entryCount == other.entryCount
                && entryIndex == other.entryIndex
                && entry.sameAction(other.entry);
        }

        private Rectangle entryBounds()
        {
            if (entryCount <= 0 || entryIndex < 0 || entryIndex >= entryCount
                || menuWidth <= 4 || menuHeight <= MENU_ENTRY_HEIGHT)
            {
                return null;
            }

            // RuneLite draws context-menu rows bottom-up; entry index 0 is the
            // bottom visible row. This is the same geometry used by GE Flipper.
            int centerY = menuY + menuHeight - 8 - (entryIndex * MENU_ENTRY_HEIGHT);
            int rowTop = centerY - (MENU_ENTRY_HEIGHT / 2) + 1;
            return new Rectangle(
                menuX + 2,
                rowTop,
                Math.max(1, menuWidth - 4),
                MENU_ENTRY_HEIGHT - 2);
        }
    }

    private static final class SlotMenuEntry
    {
        private final String option;
        private final int identifier;
        private final MenuAction type;
        private final int param0;
        private final int param1;
        private final int itemId;
        private final int worldViewId;

        private SlotMenuEntry(
            String option,
            int identifier,
            MenuAction type,
            int param0,
            int param1,
            int itemId,
            int worldViewId)
        {
            this.option = option;
            this.identifier = identifier;
            this.type = type;
            this.param0 = param0;
            this.param1 = param1;
            this.itemId = itemId;
            this.worldViewId = worldViewId;
        }

        private static SlotMenuEntry copyOf(MenuEntry entry)
        {
            return new SlotMenuEntry(
                entry.getOption(),
                entry.getIdentifier(),
                entry.getType(),
                entry.getParam0(),
                entry.getParam1(),
                entry.getItemId(),
                entry.getWorldViewId());
        }

        private boolean sameAction(SlotMenuEntry other)
        {
            return other != null
                && identifier == other.identifier
                && type == other.type
                && param0 == other.param0
                && param1 == other.param1
                && itemId == other.itemId
                && worldViewId == other.worldViewId
                && normalize(option).equalsIgnoreCase(normalize(other.option));
        }
    }
}
