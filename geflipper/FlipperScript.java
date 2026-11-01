package net.runelite.client.plugins.microbot.geflipper;

import com.google.inject.Inject;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State-driven Flipping Copilot follower.
 *
 * <p>The script deliberately does not copy Copilot's suggestion model or hard-code a second
 * flipping strategy. Copilot remains the source of truth: this class reads the active highlight,
 * resolves its interactive RuneLite widget, performs that action, and waits for the next highlight.</p>
 */
public final class FlipperScript extends Script
{
    private static final Logger LOG = LoggerFactory.getLogger(FlipperScript.class);

    private static final int LOOP_INTERVAL_MS = 150;
    private static final int INPUT_OPEN_MODE = 7;
    private static final int INPUT_CLOSE_TIMEOUT_MS = 2500;
    private static final int SPECIAL_ACTION_TIMEOUT_MS = 2800;
    private static final int MENU_OPEN_TIMEOUT_MS = 2500;
    private static final int MENU_STABLE_POLLS = 3;
    private static final int MENU_POLL_DELAY_MIN_MS = 65;
    private static final int MENU_POLL_DELAY_MAX_MS = 95;
    private static final int MENU_ENTRY_HEIGHT = 15;
    private static final int INVENTORY_CHANGE_TIMEOUT_MS = 5000;
    private static final long GE_RETRY_INTERVAL_MS = 4000L;

    private static final Pattern COPILOT_VALUE_PATTERN = Pattern.compile(
        "(?i)Copilot\\s+(quantity|price)\\s*:\\s*([0-9][0-9,]*)");

    private final Client client;
    private final FlipperConfig config;
    private final CopilotOverlayReader overlayReader;
    private final GeWidgetResolver widgetResolver;

    private final Map<String, Long> attemptedHighlights = new HashMap<>();

    private boolean inventoryPrepared;
    private long nextActionAt;
    private long lastGrandExchangeAttempt;
    private long lastDecisionLogAt;
    private String lastDecisionState = "";
    private static final long DECISION_LOG_INTERVAL_MS = 2_000L;

    @Inject
    FlipperScript(
        Client client,
        FlipperConfig config,
        CopilotOverlayReader overlayReader,
        GeWidgetResolver widgetResolver)
    {
        this.client = client;
        this.config = config;
        this.overlayReader = overlayReader;
        this.widgetResolver = widgetResolver;
    }

    public boolean run()
    {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
            this::safeTick,
            0,
            LOOP_INTERVAL_MS,
            TimeUnit.MILLISECONDS);
        return true;
    }

    private void safeTick()
    {
        try
        {
            if (!super.run() || !Microbot.isLoggedIn())
            {
                return;
            }
            tick();
        }
        catch (RuntimeException ex)
        {
            LOG.error("GE Flipper tick failed", ex);
        }
    }

    private void tick()
    {
        if (config.prepareInventory() && !inventoryPrepared)
        {
            Microbot.status = "Preparing GE inventory";
            traceDecision("PREPARE_INVENTORY");
            if (prepareInventory())
            {
                return;
            }
            inventoryPrepared = true;
        }

        CopilotPrompt prompt = readCopilotPrompt();
        if (prompt != null)
        {
            Microbot.status = "Applying Copilot " + prompt.kind;
            traceDecision("COPILOT_PROMPT_" + prompt.kind + "_" + prompt.value);
            applyCopilotPrompt(prompt);
            return;
        }

        // Never click another overlay while any unrelated chatbox input is open.
        if (isChatboxInputOpen())
        {
            Microbot.status = "Waiting for chatbox input";
            traceDecision("WAITING_CHATBOX_INPUT");
            return;
        }

        List<ResolvedCopilotAction> actions = resolveHighlights(overlayReader.snapshot());
        if (actions.isEmpty())
        {
            attemptedHighlights.clear();
            Microbot.status = "Waiting for Copilot highlight";
            traceDecision("WAITING_COPILOT_HIGHLIGHT");
            ensureGrandExchangeAvailable();
            return;
        }

        retainActiveAttempts(actions);
        ResolvedCopilotAction action = selectAction(actions);
        if (action == null)
        {
            return;
        }

        Microbot.status = "Following Copilot: " + action.getRole();
        LOG.info("GE Flipper action | role={} signature={}", action.getRole(), action.getSignature());
        boolean started = execute(action);
        LOG.info("GE Flipper action result | role={} dispatched={}", action.getRole(), started);
        attemptedHighlights.put(action.getSignature(), System.currentTimeMillis());
        scheduleNextAction();

        if (!started)
        {
            LOG.debug("Copilot action did not start: {}", action.getSignature());
        }
    }

    private void traceDecision(String state)
    {
        long now = System.currentTimeMillis();
        if (!state.equals(lastDecisionState) || now - lastDecisionLogAt >= DECISION_LOG_INTERVAL_MS)
        {
            LOG.info("GE Flipper step | state={} status={} inputOpen={} attempts={}",
                state, Microbot.status, isChatboxInputOpen(), attemptedHighlights.size());
            lastDecisionState = state;
            lastDecisionLogAt = now;
        }
    }

    /**
     * Returns true while preparation is still doing work. Returns false when preparation is complete.
     */
    private boolean prepareInventory()
    {
        List<Rs2ItemModel> items = Rs2Inventory.all();
        boolean containsOnlyCoins = !items.isEmpty()
            && items.stream().allMatch(item -> item.getId() == ItemID.COINS);
        if (containsOnlyCoins)
        {
            return false;
        }

        if (!actionReady())
        {
            return true;
        }

        if (!Rs2Bank.isOpen())
        {
            boolean opened = Rs2Bank.openBank();
            scheduleNextAction();
            return true;
        }

        Rs2Bank.depositAll();
        sleepUntil(Rs2Inventory::isEmpty, INVENTORY_CHANGE_TIMEOUT_MS);
        Rs2Bank.withdrawAll(ItemID.COINS);
        Rs2Inventory.waitForInventoryChanges(INVENTORY_CHANGE_TIMEOUT_MS);
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        scheduleNextAction();
        return false;
    }

    private List<ResolvedCopilotAction> resolveHighlights(List<CopilotHighlight> highlights)
    {
        if (highlights == null || highlights.isEmpty())
        {
            return Collections.emptyList();
        }

        return onClientThread(() ->
        {
            List<ResolvedCopilotAction> resolved = new ArrayList<>();
            for (CopilotHighlight highlight : highlights)
            {
                ResolvedCopilotAction action = widgetResolver.resolve(highlight);
                if (action != null)
                {
                    resolved.add(action);
                }
            }
            resolved.sort(Comparator.comparingInt(action -> action.getRole().getPriority()));
            return resolved;
        }, Collections.emptyList());
    }

    private ResolvedCopilotAction selectAction(List<ResolvedCopilotAction> actions)
    {
        if (!actionReady())
        {
            return null;
        }

        long now = System.currentTimeMillis();
        long retryDelay = Math.max(750L, config.highlightRetryDelay());
        for (ResolvedCopilotAction action : actions)
        {
            Long attemptedAt = attemptedHighlights.get(action.getSignature());
            if (attemptedAt == null || now - attemptedAt >= retryDelay)
            {
                return action;
            }
        }
        return null;
    }

    private boolean execute(ResolvedCopilotAction action)
    {
        switch (action.getRole())
        {
            case NPC:
                return interactWithNpc(action.getNpc());
            case ABORT:
                return abortOffer(action);
            case MODIFY:
                return modifyOffer(action);
            case QUANTITY:
            case PRICE:
                return clickValueControl(action);
            case CONFIRM:
                return submitOffer(action);
            case COLLECT:
                return collect(action);
            case BACK:
                return clickWidgetOrBounds(action);
            case QUANTITY_ALL:
                return clickWidgetOrBounds(action);
            case GENERIC_WIDGET:
            default:
                // Normal blue Copilot actions should use the actual RuneLite widget first.
                // The exact overlay rectangle remains the fallback for unusual layouts.
                return clickWidgetOrBounds(action);
        }
    }

    /**
     * Uses the exact context-menu action tied to the geometry-resolved GE slot.
     * The plugin never derives a slot from Copilot's backing widget ID alone.
     */
    private boolean abortOffer(ResolvedCopilotAction action)
    {
        return invokeSlotContextOption(action, "Abort offer");
    }

    /**
     * Modifies the exact slot covered by Copilot's amber highlight. No View-offer
     * fallback is used: opening the wrong status screen is worse than retrying.
     */
    private boolean modifyOffer(ResolvedCopilotAction action)
    {
        boolean invoked = invokeSlotContextOption(action, "Modify offer");
        if (invoked)
        {
            sleepUntil(this::isOfferSetupScreenOpen, SPECIAL_ACTION_TIMEOUT_MS);
        }
        return invoked;
    }

    private boolean invokeSlotContextOption(
        ResolvedCopilotAction action,
        String option)
    {
        SlotTarget target = resolveCurrentSlotTarget(action);
        if (target == null)
        {
            LOG.warn("Unable to resolve GE slot for {} action: {}", option, action.getSignature());
            return false;
        }

        // Never reuse a context menu left open by an earlier attempt. A stale menu can contain
        // entries for another GE slot even when the current highlight is correct.
        if (!closeOpenMenu())
        {
            LOG.warn("Unable to close stale context menu before '{}' on GE slot {}", option, target.slotIndex);
            return false;
        }

        // Right-click the centre of the resolved slot root. Do not right-click Copilot's small
        // overlay rectangle because it may belong to a nested child with different menu entries.
        int clickX = target.clickBounds.x + target.clickBounds.width / 2;
        int clickY = target.clickBounds.y + target.clickBounds.height / 2;
        Microbot.getMouse().click(clickX, clickY, true);

        MenuSnapshot menu = waitForStableSlotMenu(option, target.widgetId);
        if (menu == null)
        {
            LOG.warn(
                "GE slot {} (widget {}, item {}) did not expose a stable '{}' context menu",
                target.slotIndex,
                target.widgetId,
                target.itemId,
                option);
            closeOpenMenu();
            return false;
        }

        // Final invariant: the fully-open menu entry must still belong to the exact slot that
        // was resolved from Copilot's highlight.
        if (menu.entry.param1 != target.widgetId)
        {
            LOG.error(
                "Rejected mismatched GE action '{}': resolved slot widget {}, menu widget {}",
                option,
                target.widgetId,
                menu.entry.param1);
            closeOpenMenu();
            return false;
        }

        Rectangle optionBounds = menu.entryBounds();
        if (!validBounds(optionBounds))
        {
            LOG.warn("Unable to calculate '{}' menu row bounds for GE slot {}", option, target.slotIndex);
            closeOpenMenu();
            return false;
        }

        // Click the actual visible context-menu row only after RuneLite has reported the same
        // open menu geometry and entry list for several consecutive polls.
        Microbot.getMouse().click(optionBounds);
        boolean menuClosed = sleepUntil(() -> !isMenuOpen(), 1200);
        if (!menuClosed)
        {
            LOG.warn("Context menu remained open after clicking '{}' for GE slot {}", option, target.slotIndex);
            closeOpenMenu();
            return false;
        }

        LOG.info(
            "Clicked '{}' on GE slot {} (widget {}, item {})",
            option,
            target.slotIndex,
            target.widgetId,
            target.itemId);
        attemptedHighlights.clear();
        sleep(120, 220);
        return true;
    }

    private MenuSnapshot waitForStableSlotMenu(String option, int slotWidgetId)
    {
        long deadline = System.currentTimeMillis() + MENU_OPEN_TIMEOUT_MS;
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

            sleep(MENU_POLL_DELAY_MIN_MS, MENU_POLL_DELAY_MAX_MS);
        }
        return null;
    }

    private MenuSnapshot readOpenSlotMenu(String option, int slotWidgetId)
    {
        return onClientThread(() ->
        {
            if (!client.isMenuOpen())
            {
                return null;
            }

            Menu menu = client.getMenu();
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

            // RuneLite stores the visually top-most menu entry at the end of the array.
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
        }, null);
    }

    private boolean isMenuOpen()
    {
        return onClientThread(client::isMenuOpen, false);
    }

    private boolean closeOpenMenu()
    {
        if (!isMenuOpen())
        {
            return true;
        }

        Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
        return sleepUntil(() -> !isMenuOpen(), 800);
    }

    private SlotTarget resolveCurrentSlotTarget(ResolvedCopilotAction action)
    {
        return onClientThread(() ->
        {
            int slotIndex = action.getGrandExchangeSlotIndex();
            if (slotIndex < 0 || slotIndex >= 8)
            {
                return null;
            }

            Widget slotWidget = client.getWidget(InterfaceID.GE_OFFERS, 7 + slotIndex);
            if (slotWidget == null || slotWidget.isHidden())
            {
                return null;
            }

            int widgetId = slotWidget.getId();
            int expectedWidgetId = action.getGrandExchangeSlotWidgetId();
            if (expectedWidgetId > 0 && widgetId != expectedWidgetId)
            {
                return null;
            }

            Rectangle slotBounds = slotWidget.getBounds();
            if (!validBounds(slotBounds))
            {
                return null;
            }

            // Context-menu actions are attached to the slot root. Always right-click the full
            // current slot bounds rather than a nested overlay/child intersection.
            Rectangle clickBounds = new Rectangle(slotBounds);

            int itemId = -1;
            if (client.getGrandExchangeOffers() != null
                && slotIndex < client.getGrandExchangeOffers().length
                && client.getGrandExchangeOffers()[slotIndex] != null)
            {
                itemId = client.getGrandExchangeOffers()[slotIndex].getItemId();
            }

            return new SlotTarget(
                slotIndex,
                widgetId,
                itemId,
                slotWidget,
                clickBounds);
        }, null);
    }

    private Widget findGrandExchangeWidget(String label)
    {
        return onClientThread(() ->
        {
            for (int childId = 0; childId < 100; childId++)
            {
                Widget root = client.getWidget(InterfaceID.GE_OFFERS, childId);
                for (Widget widget : descendants(root))
                {
                    if (isVisibleWidget(widget) && widgetMatches(widget, label))
                    {
                        return widget;
                    }
                }
            }
            return null;
        }, null);
    }

    private boolean isOfferSetupScreenOpen()
    {
        return findGrandExchangeWidget("Confirm") != null;
    }

    private boolean widgetMatches(Widget widget, String label)
    {
        if (label.equalsIgnoreCase(normalize(widget.getText()))
            || label.equalsIgnoreCase(normalize(widget.getName())))
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
            if (label.equalsIgnoreCase(normalize(action)))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isVisibleWidget(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return false;
        }
        Rectangle bounds = widget.getBounds();
        return validBounds(bounds);
    }

    private boolean validBounds(Rectangle bounds)
    {
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    private boolean clickValueControl(ResolvedCopilotAction action)
    {
        boolean clicked = clickWidgetOrBounds(action);
        if (!clicked)
        {
            return false;
        }

        boolean opened = sleepUntil(this::isChatboxInputOpen, 1800);
        LOG.info("GE Flipper input widget | role={} clicked=true inputOpened={}",
            action.getRole(), opened);
        return true;
    }

    private boolean submitOffer(ResolvedCopilotAction action)
    {
        boolean clicked = clickWidgetOrBounds(action);
        if (!clicked)
        {
            return false;
        }

        sleepUntil(() ->
            !Rs2GrandExchange.isOfferScreenOpen() || isPriceWarningOpen(),
            SPECIAL_ACTION_TIMEOUT_MS);

        if (isPriceWarningOpen())
        {
            Rs2Widget.clickWidget("Yes");
            sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), SPECIAL_ACTION_TIMEOUT_MS);
        }

        if (!Rs2GrandExchange.isOfferScreenOpen())
        {
            attemptedHighlights.clear();
            return true;
        }
        return false;
    }

    private boolean collect(ResolvedCopilotAction action)
    {
        boolean clicked = clickWidgetOrBounds(action);
        if (clicked)
        {
            sleep(120, 220);
            return true;
        }

        // Current Microbot GE API fallback for unusual stretched-mode layouts.
        return Rs2GrandExchange.collectAllToInventory();
    }

    private boolean clickWidgetOrBounds(ResolvedCopilotAction action)
    {
        Widget widget = action.getInteractiveWidget();
        if (widget != null && Rs2Widget.clickWidget(widget))
        {
            return true;
        }
        return clickExactHighlight(action.getClickBounds());
    }

    private boolean clickExactHighlight(Rectangle bounds)
    {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return false;
        }
        Microbot.getMouse().click(bounds);
        return true;
    }

    private boolean interactWithNpc(NPC npc)
    {
        if (npc == null)
        {
            return false;
        }

        String name = onClientThread(npc::getName, "");
        String action;
        if ("Grand Exchange Clerk".equalsIgnoreCase(name))
        {
            action = "Exchange";
        }
        else if ("Banker".equalsIgnoreCase(name))
        {
            action = "Bank";
        }
        else
        {
            action = "";
        }

        return new Rs2NpcModel(npc).click(action);
    }

    private CopilotPrompt readCopilotPrompt()
    {
        return onClientThread(() ->
        {
            if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != INPUT_OPEN_MODE)
            {
                return null;
            }

            Widget chatbox = client.getWidget(ComponentID.CHATBOX_CONTAINER);
            if (chatbox == null)
            {
                return null;
            }

            for (Widget widget : descendants(chatbox))
            {
                Matcher matcher = COPILOT_VALUE_PATTERN.matcher(normalize(widget.getText()));
                if (!matcher.find())
                {
                    continue;
                }

                String digits = matcher.group(2).replace(",", "");
                try
                {
                    long parsed = Long.parseLong(digits);
                    if (parsed > 0 && parsed <= Integer.MAX_VALUE)
                    {
                        return new CopilotPrompt(matcher.group(1).toLowerCase(), (int) parsed);
                    }
                }
                catch (NumberFormatException ignored)
                {
                    return null;
                }
            }
            return null;
        }, null);
    }

    private void applyCopilotPrompt(CopilotPrompt prompt)
    {
        if (!actionReady())
        {
            return;
        }

        LOG.info("GE Flipper price/quantity entry | kind={} requested={} method=setChatboxValue",
            prompt.kind, prompt.value);
        Rs2GrandExchange.setChatboxValue(prompt.value);
        sleep(90, 160);
        LOG.info("GE Flipper price/quantity confirm | kind={} requested={} enter=dispatch",
            prompt.kind, prompt.value);
        Rs2Keyboard.enter();
        boolean closed = sleepUntil(() -> !isChatboxInputOpen(), INPUT_CLOSE_TIMEOUT_MS);
        if (closed)
        {
            LOG.info("GE Flipper price/quantity confirmed | kind={} requested={}", prompt.kind, prompt.value);
        }
        else
        {
            LOG.warn("GE Flipper price/quantity input stayed open after Enter | kind={} requested={}",
                prompt.kind, prompt.value);
        }
        attemptedHighlights.clear();
        scheduleNextAction();
    }

    private boolean isChatboxInputOpen()
    {
        return onClientThread(
            () -> client.getVarcIntValue(VarClientID.MESLAYERMODE) == INPUT_OPEN_MODE,
            false);
    }

    private boolean isPriceWarningOpen()
    {
        return Rs2Widget.hasWidget("Your offer is much");
    }

    private void ensureGrandExchangeAvailable()
    {
        if (Rs2Bank.isOpen() || Rs2GrandExchange.isOpen() || isChatboxInputOpen())
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastGrandExchangeAttempt < GE_RETRY_INTERVAL_MS || !actionReady())
        {
            return;
        }
        lastGrandExchangeAttempt = now;

        if (!Rs2GrandExchange.openExchange() && config.autoWalkToGe())
        {
            Rs2GrandExchange.walkToGrandExchange();
        }
        scheduleNextAction();
    }

    private void retainActiveAttempts(List<ResolvedCopilotAction> actions)
    {
        Set<String> active = new HashSet<>();
        for (ResolvedCopilotAction action : actions)
        {
            active.add(action.getSignature());
        }
        attemptedHighlights.keySet().retainAll(active);
    }

    private List<Widget> descendants(Widget root)
    {
        List<Widget> result = new ArrayList<>();
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(root, result, visited);
        return result;
    }

    private void collect(Widget widget, List<Widget> result, Set<Widget> visited)
    {
        if (widget == null || !visited.add(widget))
        {
            return;
        }
        result.add(widget);
        collect(widget.getDynamicChildren(), result, visited);
        collect(widget.getChildren(), result, visited);
        collect(widget.getNestedChildren(), result, visited);
        collect(widget.getStaticChildren(), result, visited);
    }

    private void collect(Widget[] widgets, List<Widget> result, Set<Widget> visited)
    {
        if (widgets == null)
        {
            return;
        }
        for (Widget widget : widgets)
        {
            collect(widget, result, visited);
        }
    }

    private String normalize(String value)
    {
        return value == null ? "" : value.replaceAll("<[^>]+>", "").trim();
    }

    private boolean actionReady()
    {
        return System.currentTimeMillis() >= nextActionAt;
    }

    private void scheduleNextAction()
    {
        int configuredMin = Math.max(100, config.minimumActionDelay());
        int configuredMax = Math.max(configuredMin, config.maximumActionDelay());
        int delay = configuredMin == configuredMax
            ? configuredMin
            : ThreadLocalRandom.current().nextInt(configuredMin, configuredMax + 1);
        nextActionAt = System.currentTimeMillis() + delay;
    }

    private <T> T onClientThread(Callable<T> operation, T fallback)
    {
        Optional<T> result = Microbot.getClientThread().runOnClientThreadOptional(operation);
        return result.orElse(fallback);
    }

    @Override
    public void shutdown()
    {
        inventoryPrepared = false;
        nextActionAt = 0L;
        lastGrandExchangeAttempt = 0L;
        attemptedHighlights.clear();
        super.shutdown();
    }

    private static final class SlotTarget
    {
        private final int slotIndex;
        private final int widgetId;
        private final int itemId;
        private final Widget widget;
        private final Rectangle clickBounds;

        private SlotTarget(
            int slotIndex,
            int widgetId,
            int itemId,
            Widget widget,
            Rectangle clickBounds)
        {
            this.slotIndex = slotIndex;
            this.widgetId = widgetId;
            this.itemId = itemId;
            this.widget = widget;
            this.clickBounds = new Rectangle(clickBounds);
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

            // Menu entries are drawn bottom-up. Entry index 0 is the bottom visible row.
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
        private final String target;
        private final int identifier;
        private final MenuAction type;
        private final int param0;
        private final int param1;
        private final int itemId;
        private final boolean forceLeftClick;
        private final int worldViewId;

        private SlotMenuEntry(
            String option,
            String target,
            int identifier,
            MenuAction type,
            int param0,
            int param1,
            int itemId,
            boolean forceLeftClick,
            int worldViewId)
        {
            this.option = option;
            this.target = target;
            this.identifier = identifier;
            this.type = type;
            this.param0 = param0;
            this.param1 = param1;
            this.itemId = itemId;
            this.forceLeftClick = forceLeftClick;
            this.worldViewId = worldViewId;
        }

        private static SlotMenuEntry copyOf(MenuEntry entry)
        {
            return new SlotMenuEntry(
                entry.getOption(),
                entry.getTarget(),
                entry.getIdentifier(),
                entry.getType(),
                entry.getParam0(),
                entry.getParam1(),
                entry.getItemId(),
                entry.isForceLeftClick(),
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
                && normalizeStatic(option).equalsIgnoreCase(normalizeStatic(other.option));
        }

        private static String normalizeStatic(String value)
        {
            return value == null ? "" : value.replaceAll("<[^>]+>", "").trim();
        }

    }

    private static final class CopilotPrompt
    {
        private final String kind;
        private final int value;

        private CopilotPrompt(String kind, int value)
        {
            this.kind = kind;
            this.value = value;
        }
    }
}
