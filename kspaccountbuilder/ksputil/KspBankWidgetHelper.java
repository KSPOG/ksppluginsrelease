package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil;

import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public final class KspBankWidgetHelper
{
    private static final int BANK_TUTORIAL_GROUP = 12;
    private static final int BANK_TUTORIAL_TEXT_CHILD = 4;
    private static final int SCREEN_HIGHLIGHT_GROUP = 664;
    private static final int SCREEN_HIGHLIGHT_CLOSE_CHILD = 29;

    private KspBankWidgetHelper() {}

    public static boolean closeBankTutorialOverlayIfOpen()
    {
        if (!isBankTutorialOverlayOpen())
        {
            return false;
        }

        if (Rs2Widget.isWidgetVisible(SCREEN_HIGHLIGHT_GROUP, SCREEN_HIGHLIGHT_CLOSE_CHILD))
        {
            Rs2Widget.clickWidget(SCREEN_HIGHLIGHT_GROUP, SCREEN_HIGHLIGHT_CLOSE_CHILD);
            return true;
        }

        return false;
    }

    public static boolean closeBankTutorialOverlayIfOpenAndWait()
    {
        // Legacy compatibility wrapper. Tutorial/task loops are state-driven,
        // so never block the scheduler thread after dispatching the close click.
        return closeBankTutorialOverlayIfOpen();
    }

    private static boolean isBankTutorialOverlayOpen()
    {
        return Rs2Widget.isWidgetVisible(BANK_TUTORIAL_GROUP, BANK_TUTORIAL_TEXT_CHILD)
                || Rs2Widget.isWidgetVisible(SCREEN_HIGHLIGHT_GROUP, SCREEN_HIGHLIGHT_CLOSE_CHILD);
    }

}
