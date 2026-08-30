package net.runelite.client.plugins.microbot.kspmule;

/**
 * Common worker-side mule settings implemented by KSP money-making plugin configs.
 * Config annotations remain in each plugin so the settings appear in that plugin's panel.
 */
public interface KspMuleConfig
{
    boolean muleEnabled();
    int muleTransferAt();
    int muleKeepInBank();
    int muleKeepTradingCapital();
    int muleReceiverPort();
    int muleTimeoutSeconds();
}
