package net.runelite.client.plugins.microbot.ksprenderdisable;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.PluginConstants;

@PluginDescriptor(
    name = PluginConstants.KSP + "Disable Render",
    description = "Reduces or freezes game rendering to lower GPU/CPU rendering load.",
    tags = {"ksp", "render", "fps", "performance", "lowcpu", "microbot"},
    version = KspDisableRenderPlugin.VERSION,
    minClientVersion = "2.6.19",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
public class KspDisableRenderPlugin extends Plugin
{
    public static final String VERSION = "1.0.0";

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private KspDisableRenderConfig config;

    @Inject
    private RenderCallbackManager renderCallbackManager;

    private volatile boolean filterEntities;
    private volatile boolean filterTileObjects;
    private volatile boolean filterSceneTiles;

    private boolean original2DMaskCaptured;
    private int original2DMask = Client.DRAW_2D_ALL;

    private Scene adjustedScene;
    private int originalDrawDistance = -1;

    private DrawCallbacks originalDrawCallbacks;
    private FrozenDrawCallbacks frozenDrawCallbacks;

    private final RenderCallback renderFilter = new RenderCallback()
    {
        @Override
        public boolean addEntity(Renderable renderable, boolean ui)
        {
            return !filterEntities;
        }

        @Override
        public boolean drawObject(Scene scene, TileObject object)
        {
            return !filterTileObjects;
        }

        @Override
        public boolean drawTile(Scene scene, Tile tile)
        {
            return !filterSceneTiles;
        }
    };

    @Override
    protected void startUp()
    {
        renderCallbackManager.register(renderFilter);
        refreshFilterFlags();
        clientThread.invokeLater(this::applyClientSettings);
    }

    @Override
    protected void shutDown()
    {
        filterEntities = false;
        filterTileObjects = false;
        filterSceneTiles = false;
        renderCallbackManager.unregister(renderFilter);
        clientThread.invokeLater(this::restoreClientSettings);
    }

    @Provides
    KspDisableRenderConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(KspDisableRenderConfig.class);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!KspDisableRenderConfig.GROUP.equals(event.getGroup()))
        {
            return;
        }

        refreshFilterFlags();
        clientThread.invokeLater(() ->
        {
            restoreClientSettings();
            applyClientSettings();
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invokeLater(this::applySceneSettings);
        }
    }

    private void refreshFilterFlags()
    {
        filterEntities = config.filterEntities();
        filterTileObjects = config.filterTileObjects();
        filterSceneTiles = config.filterSceneTiles();
    }

    private void applyClientSettings()
    {
        apply2DMask();
        applySceneSettings();

        if (config.renderMode() == RenderMode.FREEZE_OUTPUT)
        {
            freezeRendererOutput();
        }
    }

    private void apply2DMask()
    {
        if (!config.disable2DExtras())
        {
            return;
        }

        if (!original2DMaskCaptured)
        {
            original2DMask = client.getDraw2DMask();
            original2DMaskCaptured = true;
        }

        client.setDraw2DMask(Client.DRAW_2D_NONE);
    }

    private void restore2DMask()
    {
        if (original2DMaskCaptured)
        {
            client.setDraw2DMask(original2DMask);
            original2DMaskCaptured = false;
        }
    }

    private void applySceneSettings()
    {
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null)
        {
            return;
        }

        Scene scene = worldView.getScene();
        if (scene == null)
        {
            return;
        }

        if (adjustedScene != scene)
        {
            adjustedScene = scene;
            originalDrawDistance = scene.getDrawDistance();
        }

        scene.setDrawDistance(Math.max(0, config.drawDistance()));
    }

    private void restoreSceneSettings()
    {
        if (adjustedScene != null && originalDrawDistance >= 0)
        {
            adjustedScene.setDrawDistance(originalDrawDistance);
        }

        adjustedScene = null;
        originalDrawDistance = -1;
    }

    private void freezeRendererOutput()
    {
        if (frozenDrawCallbacks != null)
        {
            return;
        }

        DrawCallbacks current = client.getDrawCallbacks();
        if (current == null)
        {
            return;
        }

        originalDrawCallbacks = current;
        frozenDrawCallbacks = new FrozenDrawCallbacks(current);
        client.setDrawCallbacks(frozenDrawCallbacks);
    }

    private void restoreRendererOnly()
    {
        if (frozenDrawCallbacks != null && client.getDrawCallbacks() == frozenDrawCallbacks)
        {
            client.setDrawCallbacks(originalDrawCallbacks);
        }

        frozenDrawCallbacks = null;
        originalDrawCallbacks = null;
    }

    private void restoreClientSettings()
    {
        restoreRendererOnly();
        restore2DMask();
        restoreSceneSettings();
    }

    private static final class FrozenDrawCallbacks implements DrawCallbacks
    {
        private final DrawCallbacks delegate;

        private FrozenDrawCallbacks(DrawCallbacks delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public void draw(int overlayColor)
        {
            // Freeze the final frame while preserving renderer lifecycle callbacks.
        }

        @Override
        public void swapScene(Scene scene)
        {
            delegate.swapScene(scene);
        }

        @Override
        public void loadScene(Scene scene)
        {
            delegate.loadScene(scene);
        }

        @Override
        public void loadScene(WorldView worldView, Scene scene)
        {
            delegate.loadScene(worldView, scene);
        }

        @Override
        public void despawnWorldView(WorldView worldView)
        {
            delegate.despawnWorldView(worldView);
        }

        @Override
        public void invalidateZone(Scene scene, int zoneX, int zoneZ)
        {
            delegate.invalidateZone(scene, zoneX, zoneZ);
        }
    }
}
