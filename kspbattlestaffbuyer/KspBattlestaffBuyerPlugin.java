package net.runelite.client.plugins.microbot.kspbattlestaffbuyer;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = "<html>[<font color=#b8f704>KSP</font>] Battlestaff Buyer",
        description = "Buys Battlestaves from Zaff in Varrock, banks when full, and hops when stock is empty.",
        tags = {"ksp", "battlestaff", "zaff", "varrock", "shop", "world hop", "money making"},
        authors = {"KSP"},
        version = KspBattlestaffBuyerPlugin.VERSION,
        minClientVersion = "2.6.19",
        enabledByDefault = false,
        isExternal = true
)
public class KspBattlestaffBuyerPlugin extends Plugin
{
    public static final String VERSION = "0.0.1";

    @Inject
    private KspBattlestaffBuyerScript script;

    @Inject
    private KspBattlestaffBuyerOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        script.run();
    }

    @Override
    protected void shutDown()
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    KspBattlestaffBuyerScript getScript()
    {
        return script;
    }
}
