package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.util.Text;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * First-screen trade acceptance guard for localhost muling.
 *
 * Microbot 2.6.21 can show the worker's coins in the trade interface while the
 * TRADEOFFER item container still reads as empty/stale. The mule service therefore
 * keeps trying to offer the coins and never reaches its first-screen Accept click.
 *
 * This guard uses the rendered YOUR_OFFER widget as the authoritative signal. It
 * only accepts when the active mule workflow is trading, the opponent matches the
 * automatically-discovered mule name, and a visible coin stack exactly matches the
 * transfer amount.
 */
@Slf4j
public class KspHighAlchTradeAcceptGuard
{
    private static final int COINS_ID = 995;
    private static final long ACCEPT_RETRY_MS = 750L;

    private ScheduledExecutorService executor;
    private KspF2PHighAlchTraderConfig config;
    private long lastAcceptAt;
    private volatile boolean stopping;

    public synchronized void start(KspF2PHighAlchTraderConfig config)
    {
        if (executor != null) return;
        this.config = config;
        stopping = false;
        lastAcceptAt = 0L;

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ksp-high-alch-trade-accept");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tickSafely, 250L, 250L, TimeUnit.MILLISECONDS);
    }

    private void tickSafely()
    {
        try
        {
            tick();
        }
        catch (Exception ex)
        {
            log.debug("Mule first-screen accept guard tick failed", ex);
        }
    }

    private void tick()
    {
        KspF2PHighAlchTraderConfig current = config;
        if (stopping || current == null || !current.enableMule() || !Microbot.isLoggedIn()) return;
        if (KspHighAlchMuleService.state != KspHighAlchMuleService.MuleState.TRADING) return;
        if (!Rs2Widget.isWidgetVisible(InterfaceID.Trademain.ACCEPT)) return;

        long expectedCoins = KspHighAlchMuleService.transferCoins;
        if (expectedCoins <= 0L) return;

        String expectedMule = KspHighAlchMuleService.muleName;
        if (expectedMule == null || expectedMule.isBlank() || "-".equals(expectedMule)) return;
        if (!firstScreenMatches(expectedMule)) return;
        if (!visibleOwnOfferHasExactCoins(expectedCoins)) return;

        long now = System.currentTimeMillis();
        if (now - lastAcceptAt < ACCEPT_RETRY_MS) return;
        lastAcceptAt = now;

        if (Rs2Widget.clickWidget(InterfaceID.Trademain.ACCEPT))
        {
            KspHighAlchMuleService.status = "Accepted first trade screen";
            log.info("KSP mule worker accepted first trade screen: {} coins -> {}",
                    expectedCoins, expectedMule);
        }
    }

    private boolean visibleOwnOfferHasExactCoins(long expectedCoins)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(InterfaceID.Trademain.YOUR_OFFER);
            return root != null && !root.isHidden() && treeHasExactItem(root, COINS_ID, expectedCoins);
        }).orElse(false);
    }

    private boolean firstScreenMatches(String expectedMule)
    {
        String wanted = normaliseName(expectedMule);
        if (wanted.isEmpty()) return false;

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(InterfaceID.Trademain.WHOLESCREEN);
            return root != null && !root.isHidden() && treeMatchesName(root, wanted);
        }).orElse(false);
    }

    private static boolean treeHasExactItem(Widget widget, int itemId, long expectedQuantity)
    {
        if (widget == null || widget.isHidden()) return false;
        if (widget.getItemId() == itemId && widget.getItemQuantity() == expectedQuantity) return true;

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
                if (treeHasExactItem(child, itemId, expectedQuantity)) return true;
            }
        }
        return false;
    }

    private static boolean treeMatchesName(Widget widget, String wanted)
    {
        if (widget == null || widget.isHidden()) return false;

        String text = cleanText(widget.getText());
        if (!text.isEmpty())
        {
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.startsWith("trading with"))
            {
                int colon = text.indexOf(':');
                String value = colon >= 0
                        ? text.substring(colon + 1)
                        : text.substring("trading with".length());
                if (normaliseName(value).equals(wanted)) return true;
            }
            if (normaliseName(text).equals(wanted)) return true;
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
                if (treeMatchesName(child, wanted)) return true;
            }
        }
        return false;
    }

    private static String cleanText(String value)
    {
        return value == null ? "" : Text.removeTags(Text.unescapeJagex(value)).trim();
    }

    private static String normaliseName(String value)
    {
        return cleanText(value).replace('_', ' ').replace('\u00A0', ' ')
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public synchronized void shutdown()
    {
        stopping = true;
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) current.shutdownNow();
        config = null;
        lastAcceptAt = 0L;
    }
}
