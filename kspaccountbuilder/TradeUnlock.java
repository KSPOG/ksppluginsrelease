package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public final class TradeUnlock
{
    private static final int SIDE_JOURNAL_TAB_WIDGET_ID = 10_747_958;
    private static final int CHARACTER_SUMMARY_WIDGET_ID = 41_222_146;
    private static final int TIME_PLAYED_WIDGET_ID = 46_661_634;
    private static final int ACCOUNT_SUMMARY_ACTIONS_WIDGET_ID = 46_661_635;
    private static final String CHARACTER_SUMMARY_ACTION = "Character Summary";
    private static final String REVEAL_ACTION = "Reveal";
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)\\s*days?");
    private static final Pattern HOURS_PATTERN = Pattern.compile("(\\d+)\\s*hours?");
    private static final Pattern MINUTES_PATTERN = Pattern.compile("(\\d+)\\s*minutes?");

    private TradeUnlock()
    {
    }

    /**
     * Opens Character Summary, reveals Time Played when required, and returns the
     * total in milliseconds. Returns {@code -1} when it cannot be read this attempt.
     */
    public static long readPlayTimeMillis()
    {
        log.info("[TradeUnlock] Starting account play-time read");
        try
        {
            if (findTimePlayedWidget() == null)
            {
                if (!openSummaryPanel())
                {
                    return -1;
                }
            }

            Widget timePlayedWidget = findTimePlayedWidget();
            if (timePlayedWidget == null)
            {
                log.info("[TradeUnlock] Time Played text widget was not found");
                return -1;
            }

            String text = safeText(timePlayedWidget);
            log.info("[TradeUnlock] Time Played text before reveal: {}", text);
            if (text != null && text.toLowerCase(Locale.ROOT).contains("reveal"))
            {
                if (!clickTimePlayedReveal())
                {
                    return -1;
                }
                if (sleepUntil(TradeUnlock::confirmDialogueOpen, 2_500)
                        && !clickConfirmationOption())
                {
                    return -1;
                }

                sleepUntil(() ->
                {
                    String updatedText = safeText(findTimePlayedWidget());
                    return updatedText != null
                            && !updatedText.toLowerCase(Locale.ROOT).contains("reveal");
                }, 3_000);

                text = safeText(findTimePlayedWidget());
                log.info("[TradeUnlock] Time Played text after reveal: {}", text);
            }

            long playTimeMillis = parsePlayTimeMillis(text);
            log.info("[TradeUnlock] Parsed account play time | minutes={}",
                    playTimeMillis < 0L ? -1L : TimeUnit.MILLISECONDS.toMinutes(playTimeMillis));
            return playTimeMillis;
        }
        catch (Exception ex)
        {
            log.warn("[TradeUnlock] readPlayTimeMillis failed", ex);
            return -1;
        }
    }

    public static int readPlayTimeHours()
    {
        long playTimeMillis = readPlayTimeMillis();
        if (playTimeMillis < 0L)
        {
            return -1;
        }
        return (int) Math.min(Integer.MAX_VALUE, TimeUnit.MILLISECONDS.toHours(playTimeMillis));
    }

    static int parsePlayTimeHours(String text)
    {
        long playTimeMillis = parsePlayTimeMillis(text);
        if (playTimeMillis < 0L)
        {
            return -1;
        }
        return (int) Math.min(Integer.MAX_VALUE, TimeUnit.MILLISECONDS.toHours(playTimeMillis));
    }

    static long parsePlayTimeMillis(String text)
    {
        if (text == null || text.isBlank())
        {
            return -1;
        }

        String normalized = text.replaceAll("<[^>]+>", " ")
                .replace('\u00A0', ' ')
                .toLowerCase(Locale.ROOT);

        int days = findNumber(DAYS_PATTERN, normalized);
        int hours = findNumber(HOURS_PATTERN, normalized);
        int minutes = findNumber(MINUTES_PATTERN, normalized);
        if (days < 0 && hours < 0 && minutes < 0)
        {
            return -1;
        }

        long totalMinutes = Math.max(0, days) * 24L * 60L
                + Math.max(0, hours) * 60L
                + Math.max(0, minutes);
        return TimeUnit.MINUTES.toMillis(totalMinutes);
    }

    private static boolean openSummaryPanel()
    {
        if (findTimePlayedWidget() != null)
        {
            return true;
        }

        Widget journalTab = findWidgetWithAction(
                SIDE_JOURNAL_TAB_WIDGET_ID,
                CHARACTER_SUMMARY_ACTION);
        if (!physicalClick(journalTab))
        {
            log.info("[TradeUnlock] Unable to physically click the side-journal tab");
            return false;
        }
        if (!sleepUntil(() -> isWidgetVisible(CHARACTER_SUMMARY_WIDGET_ID), 1_500))
        {
            log.info("[TradeUnlock] Character Summary entry did not become visible");
            return false;
        }

        if (findTimePlayedWidget() != null)
        {
            return true;
        }

        Widget characterSummary = findWidgetWithAction(
                CHARACTER_SUMMARY_WIDGET_ID,
                CHARACTER_SUMMARY_ACTION);
        if (!physicalClick(characterSummary))
        {
            log.info("[TradeUnlock] Unable to physically click Character Summary");
            return false;
        }

        boolean opened = sleepUntil(() -> findTimePlayedWidget() != null, 3_000);
        if (!opened)
        {
            log.info("[TradeUnlock] Time Played widget did not become visible");
        }
        return opened;
    }

    private static boolean isWidgetVisible(int widgetId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget widget = Microbot.getClient().getWidget(widgetId);
            return widget != null && !widget.isHidden();
        }).orElse(false);
    }

    private static Widget findTimePlayedWidget()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget root = Microbot.getClient().getWidget(TIME_PLAYED_WIDGET_ID);
            Widget timePlayedWidget = findTimePlayedWidget(root);
            if (timePlayedWidget != null)
            {
                return timePlayedWidget;
            }

            Widget summaryContainer = Microbot.getClient().getWidget(WidgetInfo.CHARACTER_SUMMARY_CONTAINER);
            return findTimePlayedWidget(summaryContainer);
        }).orElse(null);
    }

    private static Widget findTimePlayedWidget(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return null;
        }

        String text = safeTextOnClientThread(widget);
        if (text != null && text.toLowerCase(Locale.ROOT).contains("time played"))
        {
            return widget;
        }

        Widget found = findTimePlayedWidget(widget.getStaticChildren());
        if (found != null)
        {
            return found;
        }
        found = findTimePlayedWidget(widget.getDynamicChildren());
        if (found != null)
        {
            return found;
        }
        return findTimePlayedWidget(widget.getNestedChildren());
    }

    private static Widget findTimePlayedWidget(Widget[] widgets)
    {
        if (widgets == null)
        {
            return null;
        }
        for (Widget widget : widgets)
        {
            Widget found = findTimePlayedWidget(widget);
            if (found != null)
            {
                return found;
            }
        }
        return null;
    }

    private static boolean clickTimePlayedReveal()
    {
        Widget revealWidget = findWidgetWithAction(
                ACCOUNT_SUMMARY_ACTIONS_WIDGET_ID,
                REVEAL_ACTION);
        if (revealWidget == null)
        {
            log.info("[TradeUnlock] Reveal action widget was not available");
            return false;
        }

        debugTimePlayedWidget(revealWidget);
        return physicalClick(revealWidget);
    }

    private static Widget findWidgetWithAction(int rootWidgetId, String expectedAction)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                findWidgetWithActionOnClientThread(
                        Microbot.getClient().getWidget(rootWidgetId),
                        expectedAction))
                .orElse(null);
    }

    private static Widget findWidgetWithActionOnClientThread(Widget widget, String expectedAction)
    {
        if (widget == null || widget.isHidden())
        {
            return null;
        }

        String[] actions = widget.getActions();
        if (actions != null)
        {
            for (String action : actions)
            {
                if (expectedAction.equalsIgnoreCase(action))
                {
                    return widget;
                }
            }
        }

        Widget found = findWidgetWithActionOnClientThread(widget.getStaticChildren(), expectedAction);
        if (found != null)
        {
            return found;
        }
        found = findWidgetWithActionOnClientThread(widget.getDynamicChildren(), expectedAction);
        if (found != null)
        {
            return found;
        }
        return findWidgetWithActionOnClientThread(widget.getNestedChildren(), expectedAction);
    }

    private static Widget findWidgetWithActionOnClientThread(Widget[] widgets, String expectedAction)
    {
        if (widgets == null)
        {
            return null;
        }

        for (Widget widget : widgets)
        {
            Widget found = findWidgetWithActionOnClientThread(widget, expectedAction);
            if (found != null)
            {
                return found;
            }
        }
        return null;
    }

    private static void debugTimePlayedWidget(Widget widget)
    {
        if (!log.isDebugEnabled())
        {
            return;
        }

        Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Object[] listener = widget.getOnOpListener();
            log.debug(
                    "[TradeUnlock] Time Played widget id={} parentId={} index={} listener={} onOpListener={} clickMask={} bounds={}",
                    widget.getId(),
                    widget.getParentId(),
                    widget.getIndex(),
                    widget.hasListener(),
                    listener == null ? "none" : listener.length,
                    widget.getClickMask(),
                    widget.getBounds());
            return true;
        });
    }

    private static boolean confirmDialogueOpen()
    {
        return Rs2Dialogue.getDialogueOption("Yes and don't ask me again") != null;
    }

    private static boolean clickConfirmationOption()
    {
        return physicalClick(Rs2Dialogue.getDialogueOption("Yes and don't ask me again"));
    }

    private static boolean physicalClick(Widget widget)
    {
        if (widget == null)
        {
            return false;
        }

        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (widget.isHidden())
            {
                return null;
            }
            Rectangle widgetBounds = widget.getBounds();
            return widgetBounds == null ? null : new Rectangle(widgetBounds);
        }).orElse(null);
        if (bounds == null || bounds.isEmpty())
        {
            log.info("[TradeUnlock] Widget has no clickable bounds");
            return false;
        }

        int clickX = bounds.x + bounds.width / 2;
        int clickY = bounds.y + bounds.height / 2;
        Microbot.getMouse().click(clickX, clickY);
        return true;
    }

    private static String safeText(Widget widget)
    {
        if (widget == null)
        {
            return null;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                safeTextOnClientThread(widget))
                .orElse(null);
    }

    private static String safeTextOnClientThread(Widget widget)
    {
        String text = widget.getText();
        if (text != null && !text.isBlank())
        {
            return text;
        }
        return widget.getName();
    }

    private static int findNumber(Pattern pattern, String text)
    {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
