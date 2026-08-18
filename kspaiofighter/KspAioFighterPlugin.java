package net.runelite.client.plugins.microbot.kspaiofighter;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
			Microbot.getClient().createMenuEntry(1)
					.setOption(SET_SAFE_SPOT)
					.setTarget(AREA_TILE_TARGET)
					.setParam0(event.getActionParam0())
					.setParam1(event.getActionParam1())
					.setIdentifier(event.getIdentifier())
					.setType(MenuAction.RUNELITE)
					.onClick(this::setSafeSpotFromMenuEntry);
		}

		if (shiftHeld && !hasCompleteAttackArea())
		{
			Microbot.getClient().createMenuEntry(1)
					.setOption(SET_AREA_TILE_1)
					.setTarget(AREA_TILE_TARGET)
					.setParam0(event.getActionParam0())
					.setParam1(event.getActionParam1())
					.setIdentifier(event.getIdentifier())
					.setType(MenuAction.RUNELITE)
					.onClick(entry -> setAttackAreaTileFromMenuEntry(1));

			Microbot.getClient().createMenuEntry(1)
					.setOption(SET_AREA_TILE_2)
					.setTarget(AREA_TILE_TARGET)
					.setParam0(event.getActionParam0())
					.setParam1(event.getActionParam1())
					.setIdentifier(event.getIdentifier())
					.setType(MenuAction.RUNELITE)
					.onClick(entry -> setAttackAreaTileFromMenuEntry(2));
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!KspAioFighterConfig.GROUP.equals(event.getGroup())
				|| !RESET_AREAS_CONFIG_KEY.equals(event.getKey()))
		{
			return;
		}

		Boolean resetRequested = configManager.getConfiguration(
				KspAioFighterConfig.GROUP,
				RESET_AREAS_CONFIG_KEY,
				Boolean.class);
		if (!Boolean.TRUE.equals(resetRequested))
		{
			return;
		}

		resetAttackArea();
		configManager.setConfiguration(KspAioFighterConfig.GROUP, RESET_AREAS_CONFIG_KEY, false);
	}

	@Override
	public void keyTyped(KeyEvent event)
	{
		// Not used.
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

	private void setSafeSpotFromMenuEntry(MenuEntry entry)
	{
		WorldPoint safeSpot = getSelectedWorldPoint();
		if (safeSpot == null)
		{
			Microbot.status = "KSP AIO Fighter: could not read selected safe spot tile";
			return;
		}

		configManager.setConfiguration(KspAioFighterConfig.GROUP, "safeSpotX", safeSpot.getX());
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "safeSpotY", safeSpot.getY());
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "safeSpotPlane", safeSpot.getPlane());
		Microbot.status = "KSP AIO Fighter: safe spot set to " + formatPoint(safeSpot);
	}

	private boolean isUseSafeSpotEnabled()
	{
		Boolean enabled = configManager.getConfiguration(KspAioFighterConfig.GROUP, "useSafeSpot", Boolean.class);
		return Boolean.TRUE.equals(enabled);
	}

	private void setAttackAreaTileFromMenuEntry(int tileNumber)
	{
		WorldPoint selectedTile = getSelectedWorldPoint();
		if (selectedTile == null)
		{
			Microbot.status = "KSP AIO Fighter: could not read selected tile";
			return;
		}

		if (tileNumber == 1)
		{
			configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile1X", selectedTile.getX());
			configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile1Y", selectedTile.getY());
			configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile1Plane", selectedTile.getPlane());
			Microbot.status = "KSP AIO Fighter: area tile 1 set to " + formatPoint(selectedTile);
		}
		else
		{
			configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile2X", selectedTile.getX());
			configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile2Y", selectedTile.getY());
			configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile2Plane", selectedTile.getPlane());
			Microbot.status = "KSP AIO Fighter: area tile 2 set to " + formatPoint(selectedTile);
		}

		tryFinalizeAttackArea();
	}

	private void tryFinalizeAttackArea()
	{
		WorldPoint tile1 = getAttackAreaTile1FromConfig();
		WorldPoint tile2 = getAttackAreaTile2FromConfig();
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
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile1X", 0);
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile1Y", 0);
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile1Plane", 0);
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile2X", 0);
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile2Y", 0);
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile2Plane", 0);
		Microbot.status = "KSP AIO Fighter: attack area reset";
	}

	private boolean hasCompleteAttackArea()
	{
		WorldPoint tile1 = getAttackAreaTile1FromConfig();
		WorldPoint tile2 = getAttackAreaTile2FromConfig();
		return isConfiguredTileValid(tile1)
				&& isConfiguredTileValid(tile2)
				&& tile1.getPlane() == tile2.getPlane();
	}

	private WorldPoint getAttackAreaTile1FromConfig()
	{
		Integer x = configManager.getConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile1X", Integer.class);
		Integer y = configManager.getConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile1Y", Integer.class);
		Integer plane = configManager.getConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile1Plane", Integer.class);

		if (x == null || y == null || plane == null)
		{
			return null;
		}

		return new WorldPoint(x, y, plane);
	}

	private WorldPoint getAttackAreaTile2FromConfig()
	{
		Integer x = configManager.getConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile2X", Integer.class);
		Integer y = configManager.getConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile2Y", Integer.class);
		Integer plane = configManager.getConfiguration(KspAioFighterConfig.GROUP, "attackAreaTile2Plane", Integer.class);

		if (x == null || y == null || plane == null)
		{
			return null;
		}

		return new WorldPoint(x, y, plane);
	}

	private boolean isConfiguredTileValid(WorldPoint worldPoint)
	{
		return worldPoint != null && worldPoint.getX() > 0 && worldPoint.getY() > 0;
	}

	private String formatPoint(WorldPoint point)
	{
		if (point == null)
		{
			return "<unset>";
		}
		return "(" + point.getX() + ", " + point.getY() + ", " + point.getPlane() + ")";
	}

	private WorldPoint getSelectedWorldPoint()
	{
		if (Microbot.getClient().getTopLevelWorldView().getSelectedSceneTile() == null)
		{
			return null;
		}

		return Microbot.getClient().getTopLevelWorldView().isInstance()
				? WorldPoint.fromLocalInstance(Microbot.getClient(), Microbot.getClient().getTopLevelWorldView().getSelectedSceneTile().getLocalLocation())
				: Microbot.getClient().getTopLevelWorldView().getSelectedSceneTile().getWorldLocation();
	}
}
