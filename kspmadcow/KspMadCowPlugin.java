package net.runelite.client.plugins.microbot.kspmadcow;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.kspmule.KspMuleWorkerService;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
        name = PluginConstants.KSP +"Mad Cow",
        description = "Automates Brutus and Demonic Brutus combat, LMS/Ferox banking with Lumbridge cooldown fallback, Pool recovery, Cowbell travel, looting and special dodging",
        tags = {"microbot", "ksp", "mad cow", "brutus", "demonic brutus", "cow boss", "melee", "ranged", "magic"},
        version = KspMadCowPlugin.VERSION,
        minClientVersion = "2.6.19",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspMadCowPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(KspMadCowPlugin.class);
    public static final String VERSION = "0.1.55";

    @Inject private KspMadCowConfig config;
    @Inject private KspMadCowScript script;
    @Inject private KspMadCowOverlay overlay;
    @Inject private OverlayManager overlayManager;
    @Inject private PluginManager pluginManager;
    private final KspMuleWorkerService muleService = new KspMuleWorkerService("Mad Cow");

    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private boolean overlayAdded;

    @Provides
    KspMadCowConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(KspMadCowConfig.class); }

    @Override
    protected void startUp() {
        stopping.set(false);
        muleService.start(config);
        if (!overlayAdded) { overlayManager.add(overlay); overlayAdded = true; }
        script.run(config, this::requestPluginStop);
    }

    @Override
    protected void shutDown() {
        stopping.set(true);
        muleService.shutdown();
        script.shutdown();
        if (overlayAdded) { overlayManager.remove(overlay); overlayAdded = false; }
    }

    private void requestPluginStop() {
        if (!stopping.compareAndSet(false, true)) return;
        Runnable stop = () -> {
            pluginManager.setPluginEnabled(this, false);
            try {
                if (pluginManager.isActive(this)) pluginManager.stopPlugin(this); else shutDown();
            } catch (PluginInstantiationException ex) {
                log.error("Unable to stop KSP Mad Cow cleanly", ex);
                shutDown();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) stop.run(); else SwingUtilities.invokeLater(stop);
    }

    @Subscribe public void onGameTick(GameTick event) { script.onGameTick(); }
    @Subscribe public void onOverheadTextChanged(OverheadTextChanged event) { script.onBrutusOverheadText(event.getActor(), event.getOverheadText()); }
    @Subscribe public void onAnimationChanged(AnimationChanged event) { script.onBrutusAnimationChanged(event.getActor()); }
    @Subscribe public void onActorDeath(ActorDeath event) { script.onBrutusDeath(event.getActor()); }
    @Subscribe public void onGraphicsObjectCreated(GraphicsObjectCreated event) { script.onGraphicsObjectCreated(event.getGraphicsObject()); }
    @Subscribe public void onItemContainerChanged(ItemContainerChanged event) { if (event.getContainerId() == InventoryID.INV) script.onInventoryChanged(); }
    @Subscribe public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) return;
        script.onGameMessage(event.getMessage());
        if (event.getMessage().toLowerCase().contains("oh dear, you are dead")) {
            log.warn("KSP Mad Cow stopping after player death");
            requestPluginStop();
        }
    }
}
