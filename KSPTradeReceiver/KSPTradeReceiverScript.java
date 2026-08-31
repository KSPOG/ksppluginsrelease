package net.runelite.client.plugins.microbot.KSPTradeReceiver;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.util.Text;

import java.awt.Rectangle;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KSPTradeReceiverScript extends Script
{
    private static final long FIRST_ACCEPT_COOLDOWN_MS = 900L;
    private static final long CONFIRM_ACCEPT_COOLDOWN_MS = 900L;
    private static final long REQUEST_RESPONSE_COOLDOWN_MS = 1_000L;
    private static final String OTHER_PLAYER_ACCEPTED = "other player has accepted";

    public static volatile String status = "Idle";
    public static volatile String configuredTrader = "-";
    public static volatile String pendingTrader = "-";
    public static volatile String savedTradeTile = "-";
    public static volatile int inventorySlots;
    public static volatile int acceptedFirstScreens;
    public static volatile int acceptedConfirmations;
    public static volatile int bankTrips;
    public static volatile int ignoredTradeRequests;
    public static volatile boolean banking;
    public static volatile boolean ownOfferSafe = true;

    private volatile KSPTradeReceiverConfig config;
    private volatile String pendingTraderRaw;
    private volatile long pendingRequestAt;
    private volatile boolean stopping;

    private WorldPoint tradeTile;
    private WorldPoint returnTile;
    private long lastFirstAcceptAt;
    private long lastConfirmAcceptAt;
    private long lastRequestResponseAt;
    private static long startTimeMs;

    public boolean run(KSPTradeReceiverConfig config)
    {
        this.config = config;
        resetSessionState();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (stopping) return;
                if (!super.run()) return;

                KSPTradeReceiverConfig currentConfig = this.config;
                if (currentConfig == null) return;

                configuredTrader = displayConfiguredName(currentConfig.traderName());

                if (!Microbot.isLoggedIn())
                {
                    status = "Logged out";
                    return;
                }

                inventorySlots = Rs2Inventory.count();
                expirePendingRequestIfNeeded(currentConfig);

                if (banking)
                {
                    return;
                }

                if (isTradeOpen())
                {
                    handleTradeScreens(currentConfig);
                    return;
                }

                if (currentConfig.bankWhenFull() && Rs2Inventory.isFull())
                {
                    bankAndReturn(currentConfig);
                    return;
                }

                if (hasLivePendingRequest(currentConfig) && currentConfig.respondToTradeRequests())
                {
                    respondToPendingRequest(currentConfig);
                    return;
                }

                status = configuredName(currentConfig).isEmpty()
                        ? "Set Trader Name in config"
                        : "Waiting for " + displayConfiguredName(currentConfig.traderName());
            }
            catch (Exception ex)
            {
                status = "Error - see log";
                Microbot.log("KSP Trade Receiver error: " + ex.getMessage());
                log.error("KSP Trade Receiver loop error", ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS);

        return true;
    }

    public void onChatMessage(ChatMessage event)
    {
        KSPTradeReceiverConfig currentConfig = this.config;
        if (stopping || currentConfig == null || event == null || event.getType() != ChatMessageType.TRADEREQ)
        {
            return;
        }

        String message = cleanText(event.getMessage());
        if (!message.toLowerCase(Locale.ROOT).contains("wishes to trade with you"))
        {
            return;
        }

        String sender = extractTradeRequestSender(event, message);
        if (sender.isEmpty())
        {
            ignoredTradeRequests++;
            return;
        }

        String allowed = configuredName(currentConfig);
        if (allowed.isEmpty() || !normaliseName(sender).equals(allowed))
        {
            ignoredTradeRequests++;
            status = "Ignored trade request from " + sender;
            return;
        }

        pendingTraderRaw = sender;
        pendingTrader = sender;
        pendingRequestAt = System.currentTimeMillis();

        WorldPoint location = Rs2Player.getWorldLocation();
        if (location != null)
        {
            tradeTile = location;
            savedTradeTile = formatTile(location);
        }

        status = "Trade request from " + sender;
    }

    private void handleTradeScreens(KSPTradeReceiverConfig currentConfig)
    {
        if (isConfirmationScreenOpen())
        {
            handleConfirmationScreen(currentConfig);
            return;
        }

        if (!isFirstTradeScreenOpen())
        {
            status = "Trade interface detected";
            return;
        }

        if (!firstScreenMatchesConfiguredTrader(currentConfig))
        {
            status = "Trade blocked - trader name mismatch";
            return;
        }

        captureTradeTileIfMissing();

        if (currentConfig.requireEmptyOwnOffer())
        {
            ownOfferSafe = ownOfferIsEmpty();
            if (!ownOfferSafe)
            {
                status = "Trade blocked - your offer is not empty";
                return;
            }
        }
        else
        {
            ownOfferSafe = true;
        }

        if (!currentConfig.autoAcceptFirstScreen())
        {
            status = "Verified trade - first accept disabled";
            return;
        }

        if (!otherPlayerHasAccepted(InterfaceID.Trademain.WHOLESCREEN))
        {
            status = "Waiting for " + displayConfiguredName(currentConfig.traderName()) + " to accept first screen";
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastFirstAcceptAt < FIRST_ACCEPT_COOLDOWN_MS)
        {
            status = "Counterparty accepted - waiting on first screen";
            return;
        }

        if (Rs2Widget.clickWidget(InterfaceID.Trademain.ACCEPT))
        {
            lastFirstAcceptAt = now;
            acceptedFirstScreens++;
            status = "Counterparty accepted - accepted first screen";
        }
        else
        {
            status = "First-screen Accept unavailable";
        }
    }

    private void handleConfirmationScreen(KSPTradeReceiverConfig currentConfig)
    {
        String opponent = confirmationOpponent();
        String allowed = configuredName(currentConfig);

        if (allowed.isEmpty() || opponent.isEmpty() || !normaliseName(opponent).equals(allowed))
        {
            status = "Confirmation blocked - opponent mismatch";
            return;
        }

        captureTradeTileIfMissing();

        if (!currentConfig.autoAcceptConfirmation())
        {
            status = "Verified confirmation - accept disabled";
            return;
        }

        if (!otherPlayerHasAccepted(InterfaceID.Tradeconfirm.UNIVERSE))
        {
            status = "Waiting for " + displayConfiguredName(currentConfig.traderName()) + " to accept confirmation";
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastConfirmAcceptAt < CONFIRM_ACCEPT_COOLDOWN_MS)
        {
            status = "Counterparty accepted - waiting on confirmation";
            return;
        }

        if (Rs2Widget.clickWidget(InterfaceID.Tradeconfirm.TRADE2ACCEPT))
        {
            lastConfirmAcceptAt = now;
            acceptedConfirmations++;
            status = "Counterparty accepted - accepted confirmation";
            clearPendingRequest();
        }
        else
        {
            status = "Confirmation Accept unavailable";
        }
    }

    private void respondToPendingRequest(KSPTradeReceiverConfig currentConfig)
    {
        if (!hasLivePendingRequest(currentConfig))
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRequestResponseAt < REQUEST_RESPONSE_COOLDOWN_MS)
        {
            status = "Waiting for trade window";
            return;
        }

        lastRequestResponseAt = now;
        String target = pendingTraderRaw;
        status = "Responding to " + target;

        if (!tradeWithPlayer(target))
        {
            status = "Waiting for " + target + " to be visible";
        }
    }

    private boolean tradeWithPlayer(String name)
    {
        final String wanted = normaliseName(name);
        if (wanted.isEmpty()) return false;

        Rs2PlayerModel model = Microbot.getRs2PlayerCache().getStream()
                .filter(p -> p != null && p.getPlayer() != null)
                .filter(p -> normaliseName(p.getPlayer().getName()).equals(wanted))
                .findFirst()
                .orElse(null);

        if (model == null) return false;

        Player player = model.getPlayer();
        NativePlayerAction action = getNativeTradeAction();
        if (action == null) return false;

        NewMenuEntry entry = new NewMenuEntry()
                .option("Trade with")
                .target(player.getName())
                .identifier(player.getId())
                .opcode(action.menuActionId)
                .param0(0)
                .param1(0)
                .actor(player);

        Microbot.doInvoke(entry, new Rectangle(1, 1));
        return true;
    }

    private NativePlayerAction getNativeTradeAction()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            String[] options = Microbot.getClient().getPlayerOptions();
            int[] menuTypes = Microbot.getClient().getPlayerMenuTypes();
            if (options == null || menuTypes == null) return null;

            int count = Math.min(options.length, menuTypes.length);
            for (int i = 0; i < count; i++)
            {
                String option = cleanText(options[i]);
                if (!"Trade with".equalsIgnoreCase(option)) continue;

                int actionId = menuTypes[i];
                if (actionId >= MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET)
                {
                    actionId -= MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET;
                }

                MenuAction action = MenuAction.of(actionId);
                if (!isNativePlayerAction(action)) return null;
                return new NativePlayerAction(actionId);
            }

            return null;
        }).orElse(null);
    }

    private static boolean isNativePlayerAction(MenuAction action)
    {
        return action == MenuAction.PLAYER_FIRST_OPTION
                || action == MenuAction.PLAYER_SECOND_OPTION
                || action == MenuAction.PLAYER_THIRD_OPTION
                || action == MenuAction.PLAYER_FOURTH_OPTION
                || action == MenuAction.PLAYER_FIFTH_OPTION
                || action == MenuAction.PLAYER_SIXTH_OPTION
                || action == MenuAction.PLAYER_SEVENTH_OPTION
                || action == MenuAction.PLAYER_EIGHTH_OPTION;
    }

    private boolean firstScreenMatchesConfiguredTrader(KSPTradeReceiverConfig currentConfig)
    {
        final String allowed = configuredName(currentConfig);
        if (allowed.isEmpty()) return false;

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(InterfaceID.Trademain.WHOLESCREEN);
            return root != null && !root.isHidden() && widgetTreeMatchesTrader(root, allowed);
        }).orElse(false);
    }

    private boolean otherPlayerHasAccepted(int rootWidgetId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(rootWidgetId);
            return root != null && !root.isHidden() && widgetTreeContainsOtherPlayerAccepted(root);
        }).orElse(false);
    }

    private static boolean widgetTreeContainsOtherPlayerAccepted(Widget widget)
    {
        if (widget == null || widget.isHidden()) return false;

        String text = cleanText(widget.getText()).toLowerCase(Locale.ROOT);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1).trim();
        if (OTHER_PLAYER_ACCEPTED.equals(text)) return true;

        Widget[][] groups = {
                widget.getChildren(),
                widget.getDynamicChildren(),
                widget.getNestedChildren(),
                widget.getStaticChildren()
        };

        for (Widget[] group : groups)
        {
            if (group == null) continue;
            for (Widget child : group)
            {
                if (widgetTreeContainsOtherPlayerAccepted(child)) return true;
            }
        }

        return false;
    }

    private static boolean widgetTreeMatchesTrader(Widget widget, String allowed)
    {
        if (widget == null || widget.isHidden()) return false;

        String text = cleanText(widget.getText());
        if (!text.isEmpty())
        {
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.startsWith("trading with"))
            {
                int colon = text.indexOf(':');
                String value = colon >= 0 ? text.substring(colon + 1) : text.substring("trading with".length());
                if (normaliseName(value).equals(allowed)) return true;
            }

            if (normaliseName(text).equals(allowed)) return true;
        }

        Widget[][] groups = {
                widget.getChildren(),
                widget.getDynamicChildren(),
                widget.getNestedChildren(),
                widget.getStaticChildren()
        };

        for (Widget[] group : groups)
        {
            if (group == null) continue;
            for (Widget child : group)
            {
                if (widgetTreeMatchesTrader(child, allowed)) return true;
            }
        }

        return false;
    }

    private boolean ownOfferIsEmpty()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(InterfaceID.Trademain.YOUR_OFFER);
            return root != null && !root.isHidden() && !widgetTreeContainsItem(root);
        }).orElse(false);
    }

    private static boolean widgetTreeContainsItem(Widget widget)
    {
        if (widget == null || widget.isHidden()) return false;
        if (widget.getItemId() >= 0 && widget.getItemQuantity() > 0) return true;

        Widget[][] groups = {
                widget.getChildren(),
                widget.getDynamicChildren(),
                widget.getNestedChildren(),
                widget.getStaticChildren()
        };

        for (Widget[] group : groups)
        {
            if (group == null) continue;
            for (Widget child : group)
            {
                if (widgetTreeContainsItem(child)) return true;
            }
        }

        return false;
    }

    private String confirmationOpponent()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Microbot.getClient().getWidget(InterfaceID.Tradeconfirm.TRADEOPPONENT);
            if (widget == null || widget.isHidden()) return "";

            String text = cleanText(widget.getText());
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.startsWith("trading with"))
            {
                int colon = text.indexOf(':');
                return cleanText(colon >= 0 ? text.substring(colon + 1) : text.substring("trading with".length()));
            }
            return text;
        }).orElse("");
    }

    private void bankAndReturn(KSPTradeReceiverConfig currentConfig)
    {
        banking = true;
        try
        {
            captureTradeTileIfMissing();
            returnTile = tradeTile != null ? tradeTile : Rs2Player.getWorldLocation();

            status = "Inventory full - walking to bank";
            if (!Rs2Bank.walkToBankAndUseBank())
            {
                status = "Could not open nearest bank";
                return;
            }

            status = "Depositing inventory";
            if (!Rs2Bank.depositAll() && !Rs2Inventory.isEmpty())
            {
                status = "Deposit failed";
                return;
            }

            Rs2Bank.closeBank();
            bankTrips++;

            if (!currentConfig.returnToTradeTile() || returnTile == null)
            {
                status = "Banked - return disabled";
                return;
            }

            status = "Returning to trade tile";
            boolean arrived = Rs2Walker.walkTo(returnTile, 0);
            if (!arrived)
            {
                arrived = Rs2Walker.walkTo(returnTile, 1);
            }

            if (arrived)
            {
                tradeTile = returnTile;
                savedTradeTile = formatTile(returnTile);
                status = "Returned - waiting for trade";
            }
            else
            {
                status = "Banked - could not reach saved tile";
            }
        }
        finally
        {
            banking = false;
        }
    }

    private boolean isTradeOpen()
    {
        return isFirstTradeScreenOpen() || isConfirmationScreenOpen();
    }

    private boolean isFirstTradeScreenOpen()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.Trademain.ACCEPT);
    }

    private boolean isConfirmationScreenOpen()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.Tradeconfirm.TRADE2ACCEPT);
    }

    private void captureTradeTileIfMissing()
    {
        if (tradeTile != null) return;

        WorldPoint location = Rs2Player.getWorldLocation();
        if (location != null)
        {
            tradeTile = location;
            savedTradeTile = formatTile(location);
        }
    }

    private boolean hasLivePendingRequest(KSPTradeReceiverConfig currentConfig)
    {
        if (pendingTraderRaw == null || pendingRequestAt <= 0L) return false;
        long timeout = Math.max(5L, Math.min(60L, currentConfig.requestTimeoutSeconds())) * 1_000L;
        return System.currentTimeMillis() - pendingRequestAt <= timeout;
    }

    private void expirePendingRequestIfNeeded(KSPTradeReceiverConfig currentConfig)
    {
        if (pendingTraderRaw != null && !hasLivePendingRequest(currentConfig))
        {
            clearPendingRequest();
        }
    }

    private void clearPendingRequest()
    {
        pendingTraderRaw = null;
        pendingTrader = "-";
        pendingRequestAt = 0L;
    }

    private static String extractTradeRequestSender(ChatMessage event, String cleanedMessage)
    {
        String eventName = cleanText(event.getName());
        if (!eventName.isEmpty())
        {
            return eventName;
        }

        String lower = cleanedMessage.toLowerCase(Locale.ROOT);
        int suffix = lower.indexOf(" wishes to trade with you");
        return suffix > 0 ? cleanText(cleanedMessage.substring(0, suffix)) : "";
    }

    private static String configuredName(KSPTradeReceiverConfig config)
    {
        return config == null ? "" : normaliseName(config.traderName());
    }

    private static String displayConfiguredName(String name)
    {
        String cleaned = cleanText(name);
        return cleaned.isEmpty() ? "-" : cleaned;
    }

    private static String normaliseName(String name)
    {
        return cleanText(name)
                .replace('_', ' ')
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static String cleanText(String value)
    {
        if (value == null) return "";
        return Text.removeTags(Text.unescapeJagex(value)).trim();
    }

    private static String formatTile(WorldPoint point)
    {
        return point == null
                ? "-"
                : point.getX() + ", " + point.getY() + ", " + point.getPlane();
    }

    public static Duration getRuntime()
    {
        return startTimeMs <= 0L
                ? Duration.ZERO
                : Duration.ofMillis(Math.max(0L, System.currentTimeMillis() - startTimeMs));
    }

    private void resetSessionState()
    {
        startTimeMs = System.currentTimeMillis();
        status = "Starting";
        configuredTrader = pendingTrader = savedTradeTile = "-";
        inventorySlots = acceptedFirstScreens = acceptedConfirmations = bankTrips = ignoredTradeRequests = 0;
        banking = false;
        ownOfferSafe = true;

        pendingTraderRaw = null;
        pendingRequestAt = lastFirstAcceptAt = lastConfirmAcceptAt = lastRequestResponseAt = 0L;
        tradeTile = returnTile = null;
        stopping = false;
    }

    @Override
    public void shutdown()
    {
        stopping = true;
        super.shutdown();
        clearPendingRequest();
        status = "Stopped";
        banking = false;
    }

    private static final class NativePlayerAction
    {
        private final int menuActionId;

        private NativePlayerAction(int menuActionId)
        {
            this.menuActionId = menuActionId;
        }
    }
}
