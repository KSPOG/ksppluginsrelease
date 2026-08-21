package net.runelite.client.plugins.microbot.kspaiofighter;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
		name = PluginConstants.KSP + "KSP AIO Fighter",
		description = "Configurable AIO fighter with combat targets, looting, bone burying, and high alchemy.",
		tags = {"ksp", "aio", "fighter", "combat", "loot"},
		authors = {"KSP"},
		version = KspAioFighterPlugin.version,
		minClientVersion = "2.1.32",
		enabledByDefault = PluginConstants.DEFAULT_ENABLED,
		isExternal = PluginConstants.IS_EXTERNAL
)
public class KspAioFighterPlugin extends Plugin implements KeyListener
{
	static final String version = "1.9.7";
	private static final String WALK_HERE = "Walk here";
	private static final String SET_SAFE_SPOT = "Set Safe Spot";
	private static final String SET_AREA_TILE_1 = "Set Area Tile 1";
	private static final String SET_AREA_TILE_2 = "Set Area Tile 2";
	private static final String AREA_TILE_TARGET = "KSP AIO Fighter";
	private static final String RESET_AREAS_CONFIG_KEY = "resetAreas";
	private static volatile Runnable resetAreaCallback;

	static void requestAreaReset()
	{
		Runnable callback = resetAreaCallback;
		if (callback != null)
		{
			callback.run();
		}
	}

	@Inject
	private KspAioFighterScript script;

	@Inject
	private KspAioFighterPaint paint;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ConfigManager configManager;

	private volatile boolean shiftHeld;

	private final KeyEventDispatcher shiftKeyEventDispatcher = event ->
	{
		if (event.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			if (event.getID() == KeyEvent.KEY_PRESSED)
			{
				shiftHeld = true;
			}
			else if (event.getID() == KeyEvent.KEY_RELEASED)
			{
				shiftHeld = false;
			}
		}

		return false;
	};

	@Provides
	KspAioFighterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KspAioFighterConfig.class);
	}

	@Override
	protected void startUp()
	{
		shiftHeld = false;
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(shiftKeyEventDispatcher);
		keyManager.registerKeyListener(this);
		resetAreaCallback = this::resetAttackArea;
		overlayManager.add(paint);
		script.setStopPluginCallback(this::stopPluginFromScript);
		script.run();
	}

	@Override
	protected void shutDown()
	{
		shiftHeld = false;
		KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(shiftKeyEventDispatcher);
		keyManager.unregisterKeyListener(this);
		resetAreaCallback = null;
		script.shutdown();
		overlayManager.remove(paint);
	}

	private void stopPluginFromScript()
	{
		try
		{
			pluginManager.getClass().getMethod("setPluginEnabled", Plugin.class, boolean.class).invoke(pluginManager, this, false);
		}
		catch (Exception ignored)
		{
			// Older/custom client builds may not expose setPluginEnabled publicly.
		}

		try
		{
			pluginManager.getClass().getMethod("stopPlugin", Plugin.class).invoke(pluginManager, this);
		}
		catch (Exception ignored)
		{
			script.shutdown();
			overlayManager.remove(paint);
		}
	}

	@Subscribe
	private void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!WALK_HERE.equals(event.getOption()) || !event.getTarget().isEmpty())
		{
			return;
		}

		if (isUseSafeSpotEnabled())
		{
			createTileMenuEntry(event, SET_SAFE_SPOT).onClick(entry -> setSafeSpotFromMenuEntry());
		}

		if (shiftHeld && !hasCompleteAttackArea())
		{
			createTileMenuEntry(event, SET_AREA_TILE_1).onClick(entry -> setAttackAreaTileFromMenuEntry(1));
			createTileMenuEntry(event, SET_AREA_TILE_2).onClick(entry -> setAttackAreaTileFromMenuEntry(2));
		}
	}

	private MenuEntry createTileMenuEntry(MenuEntryAdded event, String option)
	{
		return Microbot.getClient().createMenuEntry(1)
				.setOption(option)
				.setTarget(AREA_TILE_TARGET)
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1())
				.setIdentifier(event.getIdentifier())
				.setType(MenuAction.RUNELITE);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!KspAioFighterConfig.GROUP.equals(event.getGroup())
				|| !RESET_AREAS_CONFIG_KEY.equals(event.getKey())
				|| !Boolean.TRUE.equals(configManager.getConfiguration(
						KspAioFighterConfig.GROUP, RESET_AREAS_CONFIG_KEY, Boolean.class)))
		{
			return;
		}

		resetAttackArea();
		configManager.setConfiguration(KspAioFighterConfig.GROUP, RESET_AREAS_CONFIG_KEY, false);
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
	}

	@Override
	public void keyPressed(KeyEvent event)
	{
		if (event.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			shiftHeld = true;
		}
	}

	@Override
	public void keyReleased(KeyEvent event)
	{
		if (event.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			shiftHeld = false;
		}
	}

	private void setSafeSpotFromMenuEntry()
	{
		WorldPoint safeSpot = getSelectedWorldPoint();
		if (safeSpot == null)
		{
			Microbot.status = "KSP AIO Fighter: could not read selected safe spot tile";
			return;
		}

		setConfiguredPoint("safeSpot", safeSpot);
		Microbot.status = "KSP AIO Fighter: safe spot set to " + formatPoint(safeSpot);
	}

	private boolean isUseSafeSpotEnabled()
	{
		return Boolean.TRUE.equals(configManager.getConfiguration(
				KspAioFighterConfig.GROUP, "useSafeSpot", Boolean.class));
	}

	private void setAttackAreaTileFromMenuEntry(int tileNumber)
	{
		WorldPoint selectedTile = getSelectedWorldPoint();
		if (selectedTile == null)
		{
			Microbot.status = "KSP AIO Fighter: could not read selected tile";
			return;
		}

		setConfiguredPoint("attackAreaTile" + tileNumber, selectedTile);
		Microbot.status = "KSP AIO Fighter: area tile " + tileNumber + " set to " + formatPoint(selectedTile);
		tryFinalizeAttackArea();
	}

	private void tryFinalizeAttackArea()
	{
		WorldPoint tile1 = getConfiguredPoint("attackAreaTile1");
		WorldPoint tile2 = getConfiguredPoint("attackAreaTile2");
		if (!isConfiguredTileValid(tile1) || !isConfiguredTileValid(tile2))
		{
			configManager.setConfiguration(KspAioFighterConfig.GROUP, "useAttackArea", false);
			return;
		}

		if (tile1.getPlane() != tile2.getPlane())
		{
			Microbot.status = "KSP AIO Fighter: area tile 1 and tile 2 must be on the same plane";
			configManager.setConfiguration(KspAioFighterConfig.GROUP, "useAttackArea", false);
			return;
		}

		configManager.setConfiguration(KspAioFighterConfig.GROUP, "useAttackArea", true);
		Microbot.status = "KSP AIO Fighter: attack area set from " + formatPoint(tile1) + " to " + formatPoint(tile2);
	}

	private void resetAttackArea()
	{
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "useAttackArea", false);
		setConfiguredPoint("attackAreaTile1", new WorldPoint(0, 0, 0));
		setConfiguredPoint("attackAreaTile2", new WorldPoint(0, 0, 0));
		Microbot.status = "KSP AIO Fighter: attack area reset";
	}

	private boolean hasCompleteAttackArea()
	{
		WorldPoint tile1 = getConfiguredPoint("attackAreaTile1");
		WorldPoint tile2 = getConfiguredPoint("attackAreaTile2");
		return isConfiguredTileValid(tile1)
				&& isConfiguredTileValid(tile2)
				&& tile1.getPlane() == tile2.getPlane();
	}

	private void setConfiguredPoint(String prefix, WorldPoint point)
	{
		configManager.setConfiguration(KspAioFighterConfig.GROUP, prefix + "X", point.getX());
		configManager.setConfiguration(KspAioFighterConfig.GROUP, prefix + "Y", point.getY());
		configManager.setConfiguration(KspAioFighterConfig.GROUP, prefix + "Plane", point.getPlane());
	}

	private WorldPoint getConfiguredPoint(String prefix)
	{
		Integer x = configManager.getConfiguration(KspAioFighterConfig.GROUP, prefix + "X", Integer.class);
		Integer y = configManager.getConfiguration(KspAioFighterConfig.GROUP, prefix + "Y", Integer.class);
		Integer plane = configManager.getConfiguration(KspAioFighterConfig.GROUP, prefix + "Plane", Integer.class);
		return x == null || y == null || plane == null ? null : new WorldPoint(x, y, plane);
	}

	private boolean isConfiguredTileValid(WorldPoint point)
	{
		return point != null && point.getX() > 0 && point.getY() > 0;
	}

	private String formatPoint(WorldPoint point)
	{
		return "(" + point.getX() + ", " + point.getY() + ", " + point.getPlane() + ")";
	}

	private WorldPoint getSelectedWorldPoint()
	{
		var worldView = Microbot.getClient().getTopLevelWorldView();
		var selectedTile = worldView.getSelectedSceneTile();
		if (selectedTile == null)
		{
			return null;
		}

		return worldView.isInstance()
				? WorldPoint.fromLocalInstance(Microbot.getClient(), selectedTile.getLocalLocation())
				: selectedTile.getWorldLocation();
	}
}
