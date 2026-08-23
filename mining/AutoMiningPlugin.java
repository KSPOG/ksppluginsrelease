package net.runelite.client.plugins.microbot.mining;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Auto Mining",
        description = "Mines and banks ores",
        tags = {"mining", "microbot", "skilling"},
        version = AutoMiningPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class AutoMiningPlugin extends Plugin {
    public static final String version = "1.0.31";
    @Inject
    private Client client;

    @Inject
    private AutoMiningConfig config;
    @Provides
    AutoMiningConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoMiningConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoMiningOverlay autoMiningOverlay;

    @Inject
    AutoMiningScript autoMiningScript;

    @Subscribe
    public void onClientTick(ClientTick event) {
        Player localPlayer = client.getLocalPlayer();
        WorldPoint location = localPlayer == null ? null : localPlayer.getWorldLocation();
        boolean animating = localPlayer != null && localPlayer.getAnimation() != -1;
        autoMiningScript.onClientTick(location, animating);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        autoMiningScript.onMiningLevelSnapshot(client.getRealSkillLevel(Skill.MINING));
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (event != null) {
            autoMiningScript.onGameObjectDespawned(event.getGameObject());
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (event != null) {
            autoMiningScript.onGameObjectSpawned(event.getGameObject());
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event != null && event.getContainerId() == InventoryID.INVENTORY.getId()) {
            autoMiningScript.onInventoryChanged(event.getItemContainer());
        }
    }


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(autoMiningOverlay);
        }
        autoMiningScript.run(config);
    }

    protected void shutDown() {
        autoMiningScript.shutdown();
        overlayManager.remove(autoMiningOverlay);
    }
}
