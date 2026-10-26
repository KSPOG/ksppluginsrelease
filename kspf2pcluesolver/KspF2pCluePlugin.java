package net.runelite.client.plugins.microbot.kspf2pcluesolver;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = "KSP F2P Clue Solver",
    description = "Automatic F2P beginner clue routing, GE item preparation and step solving",
    tags = {"microbot", "ksp", "clue scroll", "beginner", "f2p", "grand exchange"},
    authors = {"KSP"},
    version = KspF2pCluePlugin.VERSION,
    minClientVersion = "2.6.43",
    enabledByDefault = false,
    isExternal = true
)
public class KspF2pCluePlugin extends Plugin
{
    public static final String VERSION = "0.1.4";
    @Inject private KspF2pClueConfig config;
    @Inject private KspF2pClueScript script;
    @Inject private KspF2pClueOverlay overlay;
    @Inject private OverlayManager overlayManager;

    @Provides
    KspF2pClueConfig provideConfig(ConfigManager manager)
    { return manager.getConfig(KspF2pClueConfig.class); }

    @Override protected void startUp()
    { overlayManager.add(overlay); script.run(config); }

    @Override protected void shutDown()
    {
        try { script.shutdown(); }
        finally { overlayManager.remove(overlay); }
    }

    @Subscribe public void onWidgetLoaded(WidgetLoaded e)
    { script.onWidgetLoaded(e.getGroupId()); }

    @Subscribe public void onChatMessage(ChatMessage e)
    { script.onChatMessage(e.getMessage()); }

    KspF2pClueScript getScript() { return script; }

    public void prepareHotUnload() { shutDown(); }
}