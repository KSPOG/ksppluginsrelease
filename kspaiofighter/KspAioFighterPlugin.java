package net.runelite.client.plugins.microbot.kspaiofighter;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.kspmule.KspMuleWorkerService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
		name = PluginConstants.KSP + "KSP AIO Fighter",
		description = "Configurable AIO fighter with side-panel start controls, Explv-style map area selection, equipment loadouts, looting, bone burying, and high alchemy.",
		tags = {"ksp", "aio", "fighter", "combat", "loot", "equipment", "map"},
		authors = {"KSP"},
		version = KspAioFighterPlugin.version,
		minClientVersion = "2.1.32",
		enabledByDefault = PluginConstants.DEFAULT_ENABLED,
		isExternal = PluginConstants.IS_EXTERNAL
)
public class KspAioFighterPlugin extends Plugin
{
	static final String version = "1.9.12";
	private static final String WALK_HERE = "Walk here";
	private static final String SET_SAFE_SPOT = "Set Safe Spot";
	private static final String AREA_TILE_TARGET = "KSP AIO Fighter";
	private static final String RESET_AREAS_CONFIG_KEY = "resetAreas";
	private static volatile Runnable resetAreaCallback;

	static void requestAreaReset()
	{
		Runnable callback = resetAreaCallback;
		if (callback != null) callback.run();
	}

	@Inject private KspAioFighterScript script;
	@Inject private KspAioFighterPaint paint;
	@Inject private OverlayManager overlayManager;
	@Inject private ConfigManager configManager;
	@Inject private KspAioFighterConfig config;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ItemManager itemManager;
	@Inject private KspAioFighterEquipmentSettings equipmentSettings;
	@Inject private KspAioFighterEquipmentIndex equipmentIndex;
	private final KspMuleWorkerService muleService = new KspMuleWorkerService("AIO Fighter");
	private volatile boolean automationRunning;
	private KspAioFighterEquipmentPanel equipmentPanel;
	private NavigationButton navigationButton;

	@Provides
	KspAioFighterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(KspAioFighterConfig.class);
	}

	@Override
	protected void startUp()
	{
		automationRunning = false;
		resetAreaCallback = this::resetAttackArea;
		script.setStopPluginCallback(this::stopAutomationFromScript);
		equipmentSettings.importLegacyIfNeeded();
		addEquipmentPanel();
		Microbot.status = "KSP AIO Fighter: ready - press Start in the side panel";
	}

	@Override
	protected void shutDown()
	{
		resetAreaCallback = null;
		stopAutomation();
		removeEquipmentPanel();
	}

	private synchronized void startAutomation()
	{
		if (automationRunning) return;
		automationRunning = true;
		try
		{
			overlayManager.add(paint);
			muleService.start(config);
			script.run();
			Microbot.status = "KSP AIO Fighter: running";
		}
		catch (Exception ex)
		{
			automationRunning = false;
			script.shutdown();
			muleService.shutdown();
			overlayManager.remove(paint);
			Microbot.status = "KSP AIO Fighter: failed to start - "
					+ (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
		}
		refreshPanelAutomationState();
	}

	private synchronized void stopAutomation()
	{
		boolean wasRunning = automationRunning;
		automationRunning = false;
		script.shutdown();
		muleService.shutdown();
		overlayManager.remove(paint);
		if (wasRunning) Microbot.status = "KSP AIO Fighter: stopped";
		refreshPanelAutomationState();
	}

	private void stopAutomationFromScript()
	{
		SwingUtilities.invokeLater(this::stopAutomation);
	}

	private boolean isAutomationRunning()
	{
		return automationRunning;
	}

	private void refreshPanelAutomationState()
	{
		KspAioFighterEquipmentPanel panel = equipmentPanel;
		if (panel == null) return;
		if (SwingUtilities.isEventDispatchThread()) panel.refreshAutomationState();
		else SwingUtilities.invokeLater(panel::refreshAutomationState);
	}

	private void addEquipmentPanel()
	{
		if (navigationButton != null) return;
		equipmentPanel = new KspAioFighterEquipmentPanel(
			equipmentSettings,
			equipmentIndex,
			itemManager,
			this::startAutomation,
			this::stopAutomation,
			this::isAutomationRunning,
			this::getAreaMapCentre,
			this::getConfiguredAttackArea,
			this::isAttackAreaEnabled,
			this::setAttackAreaFromMap,
			this::resetAttackArea);
		navigationButton = NavigationButton.builder()
				.tooltip("KSP AIO Fighter")
				.priority(8)
				.icon(createEquipmentIcon())
				.panel(equipmentPanel)
				.build();
		clientToolbar.addNavigation(navigationButton);
	}

	private void removeEquipmentPanel()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
		equipmentPanel = null;
	}

	private static BufferedImage createEquipmentIcon()
	{
		BufferedImage source = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = source.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new java.awt.Color(210, 210, 210));
			graphics.fillRoundRect(13, 3, 6, 20, 3, 3);
			graphics.fillRoundRect(7, 20, 18, 5, 3, 3);
			graphics.setColor(new java.awt.Color(145, 105, 55));
			graphics.fillRoundRect(14, 24, 4, 6, 2, 2);
		}
		finally
		{
			graphics.dispose();
		}
		return resize(source, 16, 16);
	}

	private static BufferedImage resize(BufferedImage source, int width, int height)
	{
		BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally
		{
			graphics.dispose();
		}
		return scaled;
	}

	@Subscribe
	private void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!WALK_HERE.equals(event.getOption()) || !event.getTarget().isEmpty()) return;
		if (isUseSafeSpotEnabled()) createTileMenuEntry(event, SET_SAFE_SPOT).onClick(entry -> setSafeSpotFromMenuEntry());
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
		if (!KspAioFighterConfig.GROUP.equals(event.getGroup())) return;

		if (RESET_AREAS_CONFIG_KEY.equals(event.getKey())
				&& Boolean.TRUE.equals(configManager.getConfiguration(
					KspAioFighterConfig.GROUP, RESET_AREAS_CONFIG_KEY, Boolean.class)))
		{
			resetAttackArea();
			configManager.setConfiguration(KspAioFighterConfig.GROUP, RESET_AREAS_CONFIG_KEY, false);
		}

		if (event.getKey() != null && (event.getKey().startsWith("attackAreaTile") || "useAttackArea".equals(event.getKey())))
		{
			KspAioFighterEquipmentPanel panel = equipmentPanel;
			if (panel != null) SwingUtilities.invokeLater(panel::refreshAreaStatus);
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
		return Boolean.TRUE.equals(configManager.getConfiguration(KspAioFighterConfig.GROUP, "useSafeSpot", Boolean.class));
	}

	private boolean isAttackAreaEnabled()
	{
		return Boolean.TRUE.equals(configManager.getConfiguration(KspAioFighterConfig.GROUP, "useAttackArea", Boolean.class));
	}

	private void setAttackAreaFromMap(WorldPoint first, WorldPoint second)
	{
		if (!isConfiguredTileValid(first) || !isConfiguredTileValid(second))
		{
			Microbot.status = "KSP AIO Fighter: invalid attack area selection";
			return;
		}
		if (first.getPlane() != second.getPlane())
		{
			Microbot.status = "KSP AIO Fighter: attack area corners must be on the same plane";
			return;
		}

		setConfiguredPoint("attackAreaTile1", first);
		setConfiguredPoint("attackAreaTile2", second);
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "useAttackArea", true);
		Microbot.status = "KSP AIO Fighter: attack area set from " + formatPoint(first) + " to " + formatPoint(second);
	}

	private void resetAttackArea()
	{
		configManager.setConfiguration(KspAioFighterConfig.GROUP, "useAttackArea", false);
		setConfiguredPoint("attackAreaTile1", new WorldPoint(0, 0, 0));
		setConfiguredPoint("attackAreaTile2", new WorldPoint(0, 0, 0));
		Microbot.status = "KSP AIO Fighter: attack area reset";
	}

	private WorldPoint[] getConfiguredAttackArea()
	{
		return new WorldPoint[] {
			getConfiguredPoint("attackAreaTile1"),
			getConfiguredPoint("attackAreaTile2")
		};
	}

	private WorldPoint getAreaMapCentre()
	{
		WorldPoint[] area = getConfiguredAttackArea();
		if (isConfiguredTileValid(area[0]) && isConfiguredTileValid(area[1]) && area[0].getPlane() == area[1].getPlane())
		{
			return new WorldPoint(
				(area[0].getX() + area[1].getX()) / 2,
				(area[0].getY() + area[1].getY()) / 2,
				area[0].getPlane());
		}

		WorldPoint player = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) return null;
			return Microbot.getClient().getLocalPlayer().getWorldLocation();
		}).orElse(null);
		if (isConfiguredTileValid(player)) return player;
		return new WorldPoint(3244, 3468, 0);
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
		if (selectedTile == null) return null;
		return worldView.isInstance()
				? WorldPoint.fromLocalInstance(Microbot.getClient(), selectedTile.getLocalLocation())
				: selectedTile.getWorldLocation();
	}
}
