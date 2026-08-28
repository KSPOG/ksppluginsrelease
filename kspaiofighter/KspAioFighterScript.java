package net.runelite.client.plugins.microbot.kspaiofighter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.ScriptID;
import net.runelite.api.VarPlayer;
import net.runelite.api.VarClientInt;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.item.Rs2ExplorersRing;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Rs2Tome;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcStats;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.settings.Rs2Settings;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class KspAioFighterScript extends Script
{
	private static final Set<Skill> MELEE_SKILLS = EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE);
	private static final int HIGH_ALCH_FIRE_RUNES_PER_CAST = 5;
	private static final int HIGH_ALCH_NATURE_RUNES_PER_CAST = 1;
	private static final Map<Runes, String[]> ELEMENTAL_STAFF_NAMES = Map.of(
			Runes.AIR, new String[] {"staff of air", "air battlestaff", "mystic air staff", "mist battlestaff", "mystic mist staff", "dust battlestaff", "mystic dust staff", "smoke battlestaff", "mystic smoke staff"},
			Runes.WATER, new String[] {"staff of water", "water battlestaff", "mystic water staff", "mist battlestaff", "mystic mist staff", "mud battlestaff", "mystic mud staff", "steam battlestaff", "mystic steam staff", "kodai wand"},
			Runes.EARTH, new String[] {"staff of earth", "earth battlestaff", "mystic earth staff", "dust battlestaff", "mystic dust staff", "mud battlestaff", "mystic mud staff", "lava battlestaff", "mystic lava staff"},
			Runes.FIRE, new String[] {"staff of fire", "fire battlestaff", "mystic fire staff", "smoke battlestaff", "mystic smoke staff", "steam battlestaff", "mystic steam staff", "lava battlestaff", "mystic lava staff"}
	);

	private static final long WALK_RETRY_MS = 1_800L;
	private static final long GEAR_BANK_RETRY_MS = 60_000L;
	private static final int ATTACK_AREA_WALK_DISTANCE = 1;
	private static final long POST_KILL_LOOT_WINDOW_MS = 350L;
	private static final long LOOT_CLICK_COOLDOWN_MS = 650L;
	private static final int START_CAMERA_PITCH = 280;
	private static final int START_CAMERA_ZOOM = 377;

	private final KspAioFighterConfig config;
	private final ConfigManager configManager;
	private Skill currentTrainingSkill;
	private int cachedTargetNpcMaxHit = 0;
	private String lastTargetNpcName = "-";
	private WorldPoint lastTargetNpcLocation;
	private String lastAction = "Starting";
	private String lastError = "-";
	private long lastTargetUpdateMs = 0L;
	private long lastErrorMs = 0L;
	private long lastSafeSpotWalkAttemptMs = 0L;
	private long lastAttackAreaWalkAttemptMs = 0L;
	private long lastGearBankAttemptMs = 0L;
	private Skill lastGearBankSkill;
	private final Set<String> unavailableGearThisRun = new HashSet<>();
	private final AtomicBoolean missingGearDialogShown = new AtomicBoolean(false);
	private final AtomicBoolean missingRuneDialogShown = new AtomicBoolean(false);
	private int lastFoodRestockCount = 0;
	private static volatile long overlayStartedAtMs = 0L;
	private long startedAtMs = 0L;
	private boolean wasInCombatOrInteracting = false;
	private long postKillLootUntilMs = 0L;
	private int pendingLootId = -1;
	private int pendingLootSceneX = -1;
	private int pendingLootSceneY = -1;
	private int pendingLootWorldViewId = -1;
	private long pendingLootUntilMs = 0L;
	private long lastPaintRefreshMs = 0L;
	private boolean goalCompletionHandled = false;
	private boolean startCameraConfigured = false;
	private int startCameraAttempts = 0;
	private Runnable stopPluginCallback;
	private final AtomicBoolean loopRunning = new AtomicBoolean(false);
	private volatile long runGeneration = 0L;

	@Inject
	public KspAioFighterScript(KspAioFighterConfig config, ConfigManager configManager)
	{
		this.config = config;
		this.configManager = configManager;
	}

	public synchronized boolean run()
	{
		// startUp() can be called more than once by plugin reload/profile changes. The old
		// implementation scheduled another loop every time, which let multiple fighter
		// loops race each other. One loop could restock and walk to the fight area while
		// another saw the full inventory and banked everything again.
		shutdown();

		long generation = ++runGeneration;
		startedAtMs = System.currentTimeMillis();
		overlayStartedAtMs = startedAtMs;
		goalCompletionHandled = false;
		startCameraConfigured = false;
		startCameraAttempts = 0;
		lastFoodRestockCount = 0;
		lastGearBankAttemptMs = 0L;
		lastGearBankSkill = null;
		unavailableGearThisRun.clear();
		missingGearDialogShown.set(false);
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> loop(generation), 0, 250, TimeUnit.MILLISECONDS);
		return true;
	}

	public synchronized void shutdown()
	{
		runGeneration++;
		loopRunning.set(false);
		super.shutdown();
	}

	public void setStopPluginCallback(Runnable stopPluginCallback)
	{
		this.stopPluginCallback = stopPluginCallback;
	}

	private void loop(long generation)
	{
		if (generation != runGeneration)
		{
			return;
		}

		if (!loopRunning.compareAndSet(false, true))
		{
			return;
		}

		try
		{
			if (!Microbot.isLoggedIn() || !super.run())
			{
				return;
			}

			if (!configureStartCameraIfNeeded())
			{
				return;
			}

			updatePostKillLootState();
			refreshPaintState();

			Optional<Skill> selectedSkill = selectTrainingSkill();
			if (selectedSkill.isEmpty())
			{
				handleGoalsReached();
				return;
			}

			Skill skill = selectedSkill.get();
			if (currentTrainingSkill != skill)
			{
				currentTrainingSkill = skill;
				Microbot.log("KSP AIO Fighter training " + skill.getName());
			}

			if (waitForRequiredAreaSetupBeforeBanking())
			{
				return;
			}

			if (buryBonesFromInventory())
			{
				return;
			}

			if (shouldBankForSupplies())
			{
				bankForSupplies();
				return;
			}

			healIfNeeded();
			boolean looted = loot();

			if (looted || shouldBlockAttackForLoot() || shouldPrioritizePostKillLoot())
			{
				setStatus(looted ? "looting drop" : "checking drops");
				return;
			}

			highAlch();

			equipGearFor(skill);
			configureCombatStyle(skill);
			drinkPotionIfUnboosted(skill);
			if (moveToSafeSpotIfNeeded())
			{
				return;
			}
			if (moveToAttackAreaCenterIfNeeded())
			{
				return;
			}
			attackNpc();
		}
		catch (Exception ex)
		{
			lastError = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "<no message>" : ex.getMessage());
			lastErrorMs = System.currentTimeMillis();
			lastAction = "Error";
			Microbot.logStackTrace(getClass().getSimpleName(), ex);
		}
		finally
		{
			loopRunning.set(false);
		}
	}

	private boolean configureStartCameraIfNeeded()
	{
		if (startCameraConfigured)
		{
			return true;
		}

		setStatus("setting start camera");
		try
		{
			startCameraAttempts = 1;

			// Match KspAccountBuilder's general camera setup from TutorialIslandScript:
			// Rs2Camera.setZoom(377), then Rs2Camera.setPitch(280).
			// Do not force yaw here; the old post-tutorial bank yaw made the view feel unnatural for combat.
			Rs2Camera.setZoom(START_CAMERA_ZOOM);
			sleepUntil(() -> getStartCameraZoom() == START_CAMERA_ZOOM, 500);
			Rs2Camera.setPitch(START_CAMERA_PITCH);
			sleepUntil(() -> Rs2Camera.getPitch() > 250, 1_000);

			startCameraConfigured = true;
			Microbot.log("KSP AIO Fighter set AccountBuilder general camera | targetPitch=" + START_CAMERA_PITCH
					+ " actualPitch=" + Rs2Camera.getPitch()
					+ " targetZoom=" + START_CAMERA_ZOOM
					+ " actualZoom=" + getStartCameraZoom()
					+ " yawUnchanged=" + Rs2Camera.getYaw()
					+ " scale=" + getCameraScale());
			return true;
		}
		catch (Exception ex)
		{
			lastError = "Camera: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
			lastErrorMs = System.currentTimeMillis();
			startCameraConfigured = true;
			return true;
		}
	}

	private int getStartCameraZoom()
	{
		return Microbot.getClient().isResized()
				? Microbot.getClient().getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT)
				: Microbot.getClient().getVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT);
	}

	private int getCameraScale()
	{
		return Microbot.getClientThread()
				.runOnClientThreadOptional(() -> Microbot.getClient().getScale())
				.orElse(0);
	}

	private void refreshPaintState()
	{
		long now = System.currentTimeMillis();
		if (now - lastPaintRefreshMs < 250L)
		{
			return;
		}
		lastPaintRefreshMs = now;

		if (currentTrainingSkill == null || getLevel(currentTrainingSkill) >= getTarget(currentTrainingSkill))
		{
			currentTrainingSkill = selectTrainingSkill().orElse(currentTrainingSkill);
		}

		Actor interacting = null;
		try
		{
			if (Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null)
			{
				interacting = Microbot.getClient().getLocalPlayer().getInteracting();
			}
		}
		catch (Exception ignored)
		{
			interacting = null;
		}

		if (interacting != null)
		{
			String name = interacting.getName();
			lastTargetNpcName = name == null || name.isBlank() ? "<unknown>" : name;
			lastTargetNpcLocation = interacting.getWorldLocation();
			lastTargetUpdateMs = now;
			if (Rs2Player.isInCombat() || Rs2Player.isInteracting())
			{
				lastAction = "attacking " + lastTargetNpcName;
			}
		}
		else if ((lastAction == null || lastAction.equals("Starting")) && (Rs2Player.isInCombat() || Rs2Player.isInteracting()))
		{
			lastAction = "fighting";
		}
	}

	private void setStatus(String message)
	{
		lastAction = message;
		Microbot.status = "KSP AIO Fighter: " + message;
	}

	private void handleGoalsReached()
	{
		if (goalCompletionHandled)
		{
			setStatus(config.walkToBankAndLogoutWhenGoalsReached() ? "targets reached - stopping" : "targets reached");
			return;
		}

		if (!config.walkToBankAndLogoutWhenGoalsReached())
		{
			goalCompletionHandled = true;
			setStatus("targets reached - stopping");
			stopPlugin();
			return;
		}

		setStatus("targets reached - walking to bank");
		if (!Rs2Bank.walkToBankAndUseBank())
		{
			return;
		}

		Rs2Bank.closeBank();
		sleepUntil(() -> false, 600);

		setStatus("targets reached - logging out");
		logoutSafely();
		goalCompletionHandled = true;
		setStatus("targets reached - stopped");
		stopPlugin();
	}

	private void stopPlugin()
	{
		try
		{
			if (stopPluginCallback != null)
			{
				stopPluginCallback.run();
				return;
			}
		}
		catch (Exception ex)
		{
			lastError = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "<no message>" : ex.getMessage());
			lastErrorMs = System.currentTimeMillis();
		}

		shutdown();
	}

	private boolean logoutSafely()
	{
		if (invokeStaticNoArg("net.runelite.client.plugins.microbot.util.player.Rs2Player", "logout")
				|| invokeStaticNoArg("net.runelite.client.plugins.microbot.util.player.Rs2Player", "logOut"))
		{
			return true;
		}

		try
		{
			Object logoutButton = Enum.valueOf((Class<Enum>) WidgetInfo.class.asSubclass(Enum.class), "LOGOUT_BUTTON");
			Class<?> rs2WidgetClass = Class.forName("net.runelite.client.plugins.microbot.util.widget.Rs2Widget");
			for (java.lang.reflect.Method method : rs2WidgetClass.getMethods())
			{
				if (!method.getName().toLowerCase(Locale.ENGLISH).contains("click") || method.getParameterCount() != 1)
				{
					continue;
				}

				if (method.getParameterTypes()[0].isAssignableFrom(WidgetInfo.class))
				{
					method.invoke(null, logoutButton);
					return true;
				}
			}
		}
		catch (Exception ignored)
		{
			// Fall through. Some Microbot/RuneLite builds expose logout through Rs2Player only.
		}

		return false;
	}

	private boolean invokeStaticNoArg(String className, String methodName)
	{
		try
		{
			Class<?> clazz = Class.forName(className);
			java.lang.reflect.Method method = clazz.getMethod(methodName);
			method.invoke(null);
			return true;
		}
		catch (Exception ignored)
		{
			return false;
		}
	}

	public long getOverlayRunningTimeMs()
	{
		long start = startedAtMs > 0L ? startedAtMs : overlayStartedAtMs;
		return start <= 0L ? 0L : System.currentTimeMillis() - start;
	}

	public String getOverlayAction()
	{
		refreshPaintState();
		return lastAction == null || lastAction.isEmpty() ? "-" : lastAction;
	}

	public String getOverlayTrainingSkill()
	{
		refreshPaintState();
		Skill skill = currentTrainingSkill != null ? currentTrainingSkill : selectTrainingSkill().orElse(null);
		return skill == null ? "-" : skill.getName();
	}

	public String getOverlayTargetName()
	{
		refreshPaintState();
		return lastTargetNpcName == null || lastTargetNpcName.isEmpty() ? "-" : lastTargetNpcName;
	}

	public WorldPoint getOverlayTargetLocation()
	{
		return lastTargetNpcLocation;
	}

	public int getOverlayTargetMaxHit()
	{
		return cachedTargetNpcMaxHit;
	}

	public long getOverlayLastTargetAgeMs()
	{
		return lastTargetUpdateMs <= 0L ? -1L : System.currentTimeMillis() - lastTargetUpdateMs;
	}

	public String getOverlayLastError()
	{
		return lastError == null || lastError.isEmpty() ? "-" : lastError;
	}

	public long getOverlayLastErrorAgeMs()
	{
		return lastErrorMs <= 0L ? -1L : System.currentTimeMillis() - lastErrorMs;
	}

	public String formatOverlayPoint(WorldPoint point)
	{
		return formatPoint(point);
	}

	public WorldPoint getOverlayAttackAreaTile1()
	{
		return getAttackAreaTile1();
	}

	public WorldPoint getOverlayAttackAreaTile2()
	{
		return getAttackAreaTile2();
	}

	public WorldPoint getOverlaySafeSpot()
	{
		return getSafeSpot();
	}

	private boolean waitForRequiredAreaSetupBeforeBanking()
	{
		// Do not start any banking/restock/gear-bank flow until the user has finished
		// defining the standing tile and the attack area. Banking before area setup made
		// the script immediately walk away from the user while they were still trying to
		// right-click tiles.
		if (config.useSafeSpot() && !isConfiguredTileValid(getSafeSpot()))
		{
			setStatus("set center/safe spot tile before banking");
			return true;
		}

		if (!hasCompleteAttackArea())
		{
			setStatus("set attack area tile 1 and tile 2 before banking");
			return true;
		}

		return false;
	}

	private boolean shouldBankForSupplies()
	{
		boolean fullInventoryNeedsBank = shouldBankBecauseInventoryIsFull();
		boolean needsFood = config.useHealing()
				&& !config.foodName().trim().isEmpty()
				&& getConfiguredFoodCount() <= 0;
		boolean needsMagicRunes = shouldBankForMagicRunes();
		boolean needsHighAlchRunes = shouldBankForHighAlchRunes();

		if (fullInventoryNeedsBank)
		{
			setStatus("inventory full - banking");
		}
		else if (needsFood)
		{
			setStatus("out of food - banking");
		}
		else if (needsMagicRunes)
		{
			setStatus("missing magic runes - banking");
		}
		else if (needsHighAlchRunes)
		{
			setStatus("missing high alch runes - banking");
		}

		return fullInventoryNeedsBank || needsFood || needsMagicRunes || needsHighAlchRunes;
	}

	private boolean shouldBankBecauseInventoryIsFull()
	{
		if (!Rs2Inventory.isFull() || !isLootingEnabled())
		{
			return false;
		}

		// If the inventory is full directly after a gear/supply restock, it is full because of
		// configured supplies, not because loot filled the bag. Banking here caused a loop:
		// restock food -> walk to fight area -> see full inventory -> bank -> deposit all -> repeat.
		if (config.useHealing()
				&& !config.foodName().trim().isEmpty()
				&& lastFoodRestockCount > 0
				&& getConfiguredFoodCount() >= lastFoodRestockCount)
		{
			return false;
		}

		// Match the working jar flow: when burying is enabled, a full inventory
		// containing buryable bones should bury first instead of banking immediately.
		if (config.buryBones() && getBuryableInventoryBone() != null)
		{
			return false;
		}

		return true;
	}

	private void bankForSupplies()
	{
		setStatus(Rs2Inventory.isFull() ? "inventory full - banking" : "banking for supplies");
		lastFoodRestockCount = 0;
		clearPendingLoot();
		postKillLootUntilMs = 0L;

		if (!Rs2Bank.walkToBankAndUseBank())
		{
			return;
		}

		depositAllExceptProtectedRunes();
		Skill gearSkill = currentTrainingSkill != null ? currentTrainingSkill : selectTrainingSkill().orElse(null);
		if (gearSkill != null)
		{
			equipGearFromOpenBank(gearSkill);
		}

		if (!withdrawRunesForSelectedSpell())
		{
			Rs2Bank.closeBank();
			stopPlugin();
			return;
		}
		if (!withdrawRunesForHighAlch())
		{
			Rs2Bank.closeBank();
			stopPlugin();
			return;
		}
		withdrawConfiguredFoodFromOpenBank();
		Rs2Bank.closeBank();
	}

	private void withdrawConfiguredFoodFromOpenBank()
	{
		if (!config.useHealing())
		{
			return;
		}

		String foodName = config.foodName().trim();
		if (foodName.isEmpty())
		{
			return;
		}

		int amountToWithdraw = Math.min(Rs2Inventory.emptySlotCount(), Math.max(0, config.foodAmount()));
		if (amountToWithdraw <= 0)
		{
			return;
		}

		if (!Rs2Bank.hasItem(foodName))
		{
			lastError = "missing food in bank: " + foodName;
			lastErrorMs = System.currentTimeMillis();
			return;
		}

		setStatus("withdrawing " + amountToWithdraw + " " + foodName);
		Rs2Bank.withdrawX(true, foodName, amountToWithdraw, true);
		sleepUntil(() -> getConfiguredFoodCount() >= amountToWithdraw, 2_500);
		lastFoodRestockCount = getConfiguredFoodCount();
	}

	private boolean shouldBankForMagicRunes()
	{
		return isMagicTheActiveTrainingSkill()
				&& !hasRunesForSelectedSpell(1);
	}

	private boolean isMagicTheActiveTrainingSkill()
	{
		if (!config.trainMagic() || getLevel(Skill.MAGIC) >= config.magicTarget())
		{
			return false;
		}

		if (currentTrainingSkill != null && getLevel(currentTrainingSkill) < getTarget(currentTrainingSkill))
		{
			return currentTrainingSkill == Skill.MAGIC;
		}

		return selectTrainingSkill().orElse(null) == Skill.MAGIC;
	}

	private boolean shouldBankForHighAlchRunes()
	{
		return config.highAlchLoot() && !hasHighAlchRunes(1);
	}

	private void depositAllExceptProtectedRunes()
	{
		Set<Integer> protectedRunes = new HashSet<>();

		// Combat runes are stackable. Keep every rune stack used by the configured spell
		// instead of depositing and immediately withdrawing it again on each bank trip.
		// Preserve the stack even when equipment currently supplies that element.
		if (config.trainMagic() && getLevel(Skill.MAGIC) < config.magicTarget())
		{
			getSpellRuneRequirements(config.magicSpell()).keySet().stream()
					.map(Runes::getItemId)
					.forEach(protectedRunes::add);
		}

		if (config.highAlchLoot())
		{
			protectedRunes.add(Runes.NATURE.getItemId());
			protectedRunes.add(Runes.FIRE.getItemId());
		}

		if (protectedRunes.isEmpty())
		{
			Rs2Bank.depositAll();
			return;
		}

		Rs2Bank.depositAllExcept(protectedRunes.toArray(new Integer[0]));
	}

	private boolean withdrawRunesForHighAlch()
	{
		if (!config.highAlchLoot())
		{
			return true;
		}

		List<String> missingRunes = getMissingRunesFromBankForHighAlch();
		if (!missingRunes.isEmpty())
		{
			reportMissingMagicRunes(missingRunes);
			return false;
		}

		withdrawAllAvailableRune(Runes.NATURE);
		if (!isRuneCoveredByEquipment(Runes.FIRE))
		{
			withdrawAllAvailableRune(Runes.FIRE);
		}
		return true;
	}

	private List<String> getMissingRunesFromBankForHighAlch()
	{
		if (!config.highAlchLoot())
		{
			return List.of();
		}

		List<String> missingRunes = new ArrayList<>();
		int natureDeficit = HIGH_ALCH_NATURE_RUNES_PER_CAST - runeQuantity(Runes.NATURE);
		if (natureDeficit > 0 && !Rs2Bank.hasItem(Runes.NATURE.getItemId()))
		{
			missingRunes.add(formatRuneName(Runes.NATURE) + " x" + natureDeficit);
		}

		if (!isRuneCoveredByEquipment(Runes.FIRE))
		{
			int fireDeficit = HIGH_ALCH_FIRE_RUNES_PER_CAST - runeQuantity(Runes.FIRE);
			if (fireDeficit > 0 && !Rs2Bank.hasItem(Runes.FIRE.getItemId()))
			{
				missingRunes.add(formatRuneName(Runes.FIRE) + " x" + fireDeficit);
			}
		}
		return missingRunes;
	}

	private boolean hasHighAlchRunes(int casts)
	{
		boolean hasNatureRunes = runeQuantity(Runes.NATURE) >= HIGH_ALCH_NATURE_RUNES_PER_CAST * casts;
		boolean hasFireRunes = isRuneCoveredByEquipment(Runes.FIRE)
				|| runeQuantity(Runes.FIRE) >= HIGH_ALCH_FIRE_RUNES_PER_CAST * casts;
		return hasNatureRunes && hasFireRunes;
	}

	private Map<Runes, Integer> getSpellRuneRequirements(Rs2CombatSpells spell)
	{
		Map<Runes, Integer> requirements = new LinkedHashMap<>(spell.getRequiredRunes());
		String spellName = spell.name().toUpperCase(Locale.ENGLISH);

		// Some Microbot builds expose an empty/incomplete rune map for strike spells.
		// Keep an explicit fallback so Magic banking always withdraws the real runes.
		if (spellName.contains("WIND_STRIKE"))
		{
			requirements.put(Runes.AIR, 1);
			requirements.put(Runes.MIND, 1);
		}
		else if (spellName.contains("WATER_STRIKE"))
		{
			requirements.put(Runes.WATER, 1);
			requirements.put(Runes.AIR, 1);
			requirements.put(Runes.MIND, 1);
		}
		else if (spellName.contains("EARTH_STRIKE"))
		{
			requirements.put(Runes.EARTH, 2);
			requirements.put(Runes.AIR, 1);
			requirements.put(Runes.MIND, 1);
		}
		else if (spellName.contains("FIRE_STRIKE"))
		{
			requirements.put(Runes.FIRE, 3);
			requirements.put(Runes.AIR, 2);
			requirements.put(Runes.MIND, 1);
		}

		return requirements;
	}

	private boolean withdrawRunesForSelectedSpell()
	{
		if (!isMagicTheActiveTrainingSkill())
		{
			return true;
		}

		List<String> missingRunes = getMissingRunesFromBankForSelectedSpell();
		if (!missingRunes.isEmpty())
		{
			reportMissingMagicRunes(missingRunes);
			return false;
		}

		Rs2CombatSpells spell = config.magicSpell();
		for (Map.Entry<Runes, Integer> runeRequirement : getSpellRuneRequirements(spell).entrySet())
		{
			Runes rune = runeRequirement.getKey();
			if (isRuneCoveredByEquipment(rune))
			{
				continue;
			}

			withdrawAllAvailableRune(rune);
		}
		return true;
	}

	private List<String> getMissingRunesFromBankForSelectedSpell()
	{
		if (!isMagicTheActiveTrainingSkill())
		{
			return List.of();
		}

		List<String> missingRunes = new ArrayList<>();
		for (Map.Entry<Runes, Integer> runeRequirement : getSpellRuneRequirements(config.magicSpell()).entrySet())
		{
			Runes rune = runeRequirement.getKey();
			if (isRuneCoveredByEquipment(rune))
			{
				continue;
			}

			int requiredAmount = runeRequirement.getValue();
			int deficit = requiredAmount - runeQuantity(rune);
			if (deficit > 0 && !Rs2Bank.hasItem(rune.getItemId()))
			{
				missingRunes.add(formatRuneName(rune) + " x" + deficit);
			}
		}
		return missingRunes;
	}

	private void withdrawAllAvailableRune(Runes rune)
	{
		if (!Rs2Bank.hasItem(rune.getItemId()))
		{
			return;
		}

		int before = runeQuantity(rune);
		setStatus("withdrawing all " + formatRuneName(rune));
		boolean withdrawn = invokeBankWithdrawAll(rune.getItemId()) || invokeBankWithdrawAll(formatRuneName(rune));
		if (!withdrawn)
		{
			Rs2Bank.withdrawX(rune.getItemId(), Integer.MAX_VALUE);
		}
		sleepUntil(() -> runeQuantity(rune) > before || !Rs2Bank.hasItem(rune.getItemId()), 2_500);
	}

	private void withdrawRuneDeficit(Runes rune, int requiredAmount)
	{
		int deficit = requiredAmount - runeQuantity(rune);
		if (deficit > 0 && Rs2Bank.hasItem(rune.getItemId()))
		{
			Rs2Bank.withdrawX(rune.getItemId(), deficit);
			sleepUntil(() -> runeQuantity(rune) >= requiredAmount, 2_000);
		}
	}

	private String formatRuneName(Runes rune)
	{
		String name = rune.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
		return name.substring(0, 1).toUpperCase(Locale.ENGLISH) + name.substring(1) + " rune";
	}

	private void reportMissingMagicRunes(List<String> missingRunes)
	{
		lastError = "magic runes not found in bank: " + String.join(", ", missingRunes);
		lastErrorMs = System.currentTimeMillis();
		Microbot.log("KSP AIO Fighter " + lastError);
		showMissingRunesMessageBox(missingRunes);
	}

	private void showMissingRunesMessageBox(List<String> missingRunes)
	{
		if (!missingRuneDialogShown.compareAndSet(false, true))
		{
			return;
		}

		String message = "KSP AIO Fighter cannot find the rune(s) it needs for the configured Magic spell:"
				+ System.lineSeparator() + System.lineSeparator()
				+ String.join(System.lineSeparator(), missingRunes)
				+ System.lineSeparator() + System.lineSeparator()
				+ "Add the rune(s), equip a matching elemental staff, or change spell, then start the plugin again.";

		SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
				null,
				message,
				"KSP AIO Fighter - Missing Magic Runes",
				JOptionPane.ERROR_MESSAGE));
	}

	private boolean hasRunesForSelectedSpell(int casts)
	{
		return getSpellRuneRequirements(config.magicSpell()).entrySet().stream()
				.allMatch(runeRequirement -> isRuneCoveredByEquipment(runeRequirement.getKey())
						|| runeQuantity(runeRequirement.getKey()) >= runeRequirement.getValue() * casts);
	}

	private int runeQuantity(Runes rune)
	{
		return rune == null ? 0 : Rs2Inventory.itemQuantity(rune.getItemId());
	}

	private boolean isRuneCoveredByEquipment(Runes rune)
	{
		if (rune == null) return false;

		// Prefer Microbot's canonical item-ID mappings. This avoids staff-name/cache
		// mismatches and automatically covers combination staves such as Twinflame.
		for (Rs2Staff staff : Rs2Staff.values())
		{
			if (staff != Rs2Staff.NONE && staff.getRunes().contains(rune)
					&& Rs2Equipment.isWearing(staff.getItemID())) return true;
		}
		for (Rs2Tome tome : Rs2Tome.values())
		{
			if (tome != Rs2Tome.NONE && tome.getRunes().contains(rune)
					&& Rs2Equipment.isWearing(tome.getItemID())) return true;
		}

		// Keep the old name fallback for valid rune sources not yet present in the
		// Microbot enum (for example Kodai wand in the water list).
		String[] staffNames = ELEMENTAL_STAFF_NAMES.get(rune);
		return staffNames != null && Rs2Equipment.isWearing(staffNames);
	}

	private void healIfNeeded()
	{
		if (!config.useHealing() || config.foodName().trim().isEmpty())
		{
			return;
		}

		int currentHp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
		int healAt = Math.max(1, Math.min(maxHp - 1, Math.max(1, cachedTargetNpcMaxHit) + getConfiguredFoodHeal()));
		if (currentHp > healAt || getConfiguredFoodCount() <= 0)
		{
			return;
		}

		String action = config.foodName().equalsIgnoreCase("Jug of wine") ? "drink" : "eat";
		if (Rs2Inventory.interact(config.foodName(), action, true))
		{
			sleepUntil(() -> Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) > currentHp, 2_000);
		}
	}

	private void drinkPotionIfUnboosted(Skill skill)
	{
		if (!config.usePotions() || Rs2Player.getBoostedSkillLevel(skill) > Rs2Player.getRealSkillLevel(skill))
		{
			return;
		}

		Rs2Player.drinkCombatPotionAt(skill);
	}

	private int getConfiguredFoodCount()
	{
		return Rs2Inventory.count(config.foodName(), true);
	}

	private int getConfiguredFoodHeal()
	{
		return Arrays.stream(Rs2Food.values())
				.filter(food -> food.getName().equalsIgnoreCase(config.foodName()))
				.map(Rs2Food::getHeal)
				.findFirst()
				.orElse(config.unknownFoodHeal());
	}

	private int getNpcMaxHit(Rs2NpcModel npc)
	{
		if (npc == null)
		{
			return 1;
		}

		try
		{
			Rs2NpcStats stats = Rs2NpcManager.getStats(npc.getId());
			Integer maxHit = stats == null ? null : stats.getMaxHit();
			return maxHit == null ? 1 : Math.max(1, maxHit);
		}
		catch (Exception ex)
		{
			return 1;
		}
	}

	private boolean moveToSafeSpotIfNeeded()
	{
		if (!config.useSafeSpot())
		{
			return false;
		}

		WorldPoint safeSpot = getSafeSpot();
		if (!isConfiguredTileValid(safeSpot))
		{
			setStatus("set safe spot tile");
			return true;
		}

		WorldPoint playerLocation = Rs2Player.getWorldLocation();
		if (playerLocation == null || playerLocation.equals(safeSpot))
		{
			return false;
		}

		if (!canRetryWalk(lastSafeSpotWalkAttemptMs))
		{
			setStatus("returning to safe spot");
			return true;
		}

		lastSafeSpotWalkAttemptMs = System.currentTimeMillis();
		Rs2Walker.walkTo(safeSpot, 0);
		setStatus("returning to safe spot " + formatPoint(safeSpot));
		return true;
	}

	private boolean moveToAttackAreaCenterIfNeeded()
	{
		if (!config.useAttackArea())
		{
			return false;
		}

		// Safe spot mode intentionally owns the standing tile. Otherwise the script would
		// bounce between the safe spot and the attack-area center when both are enabled.
		if (config.useSafeSpot())
		{
			return false;
		}

		if (!hasCompleteAttackArea())
		{
			setStatus("set attack area tile 1 and tile 2");
			return true;
		}

		WorldPoint playerLocation = Rs2Player.getWorldLocation();
		if (playerLocation == null)
		{
			return false;
		}

		if (isInsideConfiguredArea(playerLocation))
		{
			return false;
		}

		WorldPoint center = getAttackAreaCenterFromTiles();
		if (!isConfiguredTileValid(center))
		{
			return false;
		}

		if (!canRetryWalk(lastAttackAreaWalkAttemptMs))
		{
			setStatus("walking to attack area");
			return true;
		}

		lastAttackAreaWalkAttemptMs = System.currentTimeMillis();
		Rs2Walker.walkTo(center, ATTACK_AREA_WALK_DISTANCE);
		setStatus("walking to attack area " + formatPoint(center) + " distance " + ATTACK_AREA_WALK_DISTANCE);
		return true;
	}

	private boolean canRetryWalk(long lastWalkAttemptMs)
	{
		return System.currentTimeMillis() - lastWalkAttemptMs >= WALK_RETRY_MS;
	}

	private String formatPoint(WorldPoint point)
	{
		if (point == null)
		{
			return "<unset>";
		}
		return "(" + point.getX() + ", " + point.getY() + ", " + point.getPlane() + ")";
	}

	private boolean isInsideAttackArea(Rs2NpcModel npc)
	{
		return npc != null && isInsideAttackArea(npc.getWorldLocation());
	}

	private boolean isInsideAttackArea(WorldPoint point)
	{
		if (!config.useAttackArea())
		{
			return true;
		}

		return isInsideConfiguredArea(point);
	}

	private boolean isInsideConfiguredArea(WorldPoint point)
	{
		WorldPoint tile1 = getAttackAreaTile1();
		WorldPoint tile2 = getAttackAreaTile2();
		if (point == null || !isConfiguredTileValid(tile1) || !isConfiguredTileValid(tile2) || tile1.getPlane() != tile2.getPlane())
		{
			return false;
		}

		int minX = Math.min(tile1.getX(), tile2.getX());
		int maxX = Math.max(tile1.getX(), tile2.getX());
		int minY = Math.min(tile1.getY(), tile2.getY());
		int maxY = Math.max(tile1.getY(), tile2.getY());

		return point.getPlane() == tile1.getPlane()
				&& point.getX() >= minX
				&& point.getX() <= maxX
				&& point.getY() >= minY
				&& point.getY() <= maxY;
	}

	private boolean hasCompleteAttackArea()
	{
		WorldPoint tile1 = getAttackAreaTile1();
		WorldPoint tile2 = getAttackAreaTile2();
		return isConfiguredTileValid(tile1)
				&& isConfiguredTileValid(tile2)
				&& tile1.getPlane() == tile2.getPlane();
	}

	private boolean isInsidePlayerAttackSquare(Rs2NpcModel npc)
	{
		return isInsideSquare(npc.getWorldLocation(), Rs2Player.getWorldLocation(), config.attackRadius());
	}

	private boolean isInsideSquare(WorldPoint point, WorldPoint center, int radius)
	{
		return point != null
				&& center != null
				&& point.getPlane() == center.getPlane()
				&& Math.abs(point.getX() - center.getX()) <= radius
				&& Math.abs(point.getY() - center.getY()) <= radius;
	}

	private WorldPoint getSafeSpot()
	{
		return new WorldPoint(getHiddenConfigInt("safeSpotX"), getHiddenConfigInt("safeSpotY"), getHiddenConfigInt("safeSpotPlane"));
	}

	private WorldPoint getAttackAreaCenterFromTiles()
	{
		WorldPoint tile1 = getAttackAreaTile1();
		WorldPoint tile2 = getAttackAreaTile2();
		if (!isConfiguredTileValid(tile1) || !isConfiguredTileValid(tile2) || tile1.getPlane() != tile2.getPlane())
		{
			return null;
		}

		return new WorldPoint((tile1.getX() + tile2.getX()) / 2, (tile1.getY() + tile2.getY()) / 2, tile1.getPlane());
	}

	private WorldPoint getAttackAreaTile1()
	{
		return new WorldPoint(getHiddenConfigInt("attackAreaTile1X"), getHiddenConfigInt("attackAreaTile1Y"), getHiddenConfigInt("attackAreaTile1Plane"));
	}

	private WorldPoint getAttackAreaTile2()
	{
		return new WorldPoint(getHiddenConfigInt("attackAreaTile2X"), getHiddenConfigInt("attackAreaTile2Y"), getHiddenConfigInt("attackAreaTile2Plane"));
	}

	private int getHiddenConfigInt(String key)
	{
		Integer value = configManager.getConfiguration(KspAioFighterConfig.GROUP, key, Integer.class);
		return value == null ? 0 : value;
	}

	private boolean isConfiguredTileValid(WorldPoint worldPoint)
	{
		return worldPoint != null && worldPoint.getX() > 0 && worldPoint.getY() > 0;
	}
	private Optional<Skill> selectTrainingSkill()
	{
		List<Skill> enabledSkills = getEnabledSkills();
		if (currentTrainingSkill != null
				&& enabledSkills.contains(currentTrainingSkill)
				&& getLevel(currentTrainingSkill) < getTarget(currentTrainingSkill))
		{
			return Optional.of(currentTrainingSkill);
		}

		return enabledSkills.stream()
				.filter(skill -> getLevel(skill) < getTarget(skill))
				.findFirst();
	}

	private List<Skill> getEnabledSkills()
	{
		List<Skill> skills = new ArrayList<>();
		if (config.trainAttack())
		{
			skills.add(Skill.ATTACK);
		}
		if (config.trainStrength())
		{
			skills.add(Skill.STRENGTH);
		}
		if (config.trainDefence())
		{
			skills.add(Skill.DEFENCE);
		}
		if (config.trainRanged())
		{
			skills.add(Skill.RANGED);
		}
		if (config.trainMagic())
		{
			skills.add(Skill.MAGIC);
		}
		return skills;
	}

	private int getTarget(Skill skill)
	{
		switch (skill)
		{
			case ATTACK:
				return config.attackTarget();
			case STRENGTH:
				return config.strengthTarget();
			case DEFENCE:
				return config.defenceTarget();
			case RANGED:
				return config.rangedTarget();
			case MAGIC:
				return config.magicTarget();
			default:
				return 99;
		}
	}

	private int getLevel(Skill skill)
	{
		return Microbot.getClient().getRealSkillLevel(skill);
	}

	private void equipGearFor(Skill skill)
	{
		List<String> gearItems = getGearList(skill);
		if (gearItems.isEmpty())
		{
			return;
		}

		List<String> missingConfiguredGear = gearItems.stream()
				.filter(itemName -> !itemName.isEmpty())
				.filter(itemName -> !isConfiguredGearSatisfied(itemName) && !Rs2Inventory.hasItem(itemName))
				.collect(Collectors.toList());

		if (!missingConfiguredGear.isEmpty() && config.bankForGear())
		{
			if (!canRetryGearBank(skill))
			{
				setStatus("missing gear, waiting before bank retry: " + String.join(", ", missingConfiguredGear));
				return;
			}

			lastGearBankAttemptMs = System.currentTimeMillis();
			lastGearBankSkill = skill;
			bankForGearSetup(skill, missingConfiguredGear);
			return;
		}

		for (String itemName : gearItems)
		{
			if (!itemName.isEmpty() && !isConfiguredGearSatisfied(itemName) && Rs2Inventory.hasItem(itemName))
			{
				equipFromInventory(itemName);
			}
		}
	}

	private boolean isConfiguredGearSatisfied(String itemName)
	{
		if (itemName == null || itemName.trim().isEmpty())
		{
			return false;
		}

		// Rs2Equipment.isWearing can fail for the ammo slot on some Microbot/RuneLite builds.
		// Check the equipped item names directly as well so arrows/bolts/darts do not cause
		// repeated gear-bank loops after they were already equipped.
		if (Rs2Equipment.isWearing(itemName))
		{
			return true;
		}

		String wanted = normalizeItemName(itemName);
		return Rs2Equipment.items().stream()
				.map(item -> item.getName() == null ? "" : normalizeItemName(item.getName()))
				.anyMatch(equipped -> equipped.equals(wanted));
	}

	private String normalizeItemName(String itemName)
	{
		return itemName == null ? "" : itemName.trim().toLowerCase(Locale.ENGLISH);
	}

	private boolean isGearMarkedUnavailable(String itemName)
	{
		return unavailableGearThisRun.contains(normalizeItemName(itemName));
	}

	private boolean canRetryGearBank(Skill skill)
	{
		return lastGearBankSkill != skill || System.currentTimeMillis() - lastGearBankAttemptMs >= GEAR_BANK_RETRY_MS;
	}

	private void bankForGearSetup(Skill skill, List<String> missingGearBeforeBank)
	{
		if (!Rs2Bank.walkToBankAndUseBank())
		{
			return;
		}

		setStatus("banking for " + skill.getName() + " gear");
		lastFoodRestockCount = 0;
		if (reportMissingGearNotAvailableInBank(missingGearBeforeBank))
		{
			Rs2Bank.closeBank();
			stopPlugin();
			return;
		}

		Rs2Bank.depositAll();
		sleepUntil(() -> Rs2Inventory.isEmpty(), 1_800);
		depositEquippedItemsFromBank();
		equipGearFromOpenBank(skill);
		reportGearStillMissingAfterBank(skill);
		if (skill == Skill.MAGIC && !withdrawRunesForSelectedSpell())
		{
			Rs2Bank.closeBank();
			stopPlugin();
			return;
		}
		if (!withdrawRunesForHighAlch())
		{
			Rs2Bank.closeBank();
			stopPlugin();
			return;
		}
		withdrawConfiguredFoodFromOpenBank();
		Rs2Bank.closeBank();
	}

	private boolean reportMissingGearNotAvailableInBank(List<String> missingGearBeforeBank)
	{
		List<String> missingFromBank = missingGearBeforeBank.stream()
				.filter(itemName -> !isConfiguredGearSatisfied(itemName))
				.filter(itemName -> !Rs2Inventory.hasItem(itemName))
				.filter(itemName -> !Rs2Bank.hasItem(itemName))
				.collect(Collectors.toList());

		if (missingFromBank.isEmpty())
		{
			return false;
		}

		lastError = "configured gear not found in bank: " + String.join(", ", missingFromBank);
		lastErrorMs = System.currentTimeMillis();
		Microbot.log("KSP AIO Fighter " + lastError);
		showMissingGearMessageBox(missingFromBank);
		return true;
	}

	private void showMissingGearMessageBox(List<String> missingItems)
	{
		if (!missingGearDialogShown.compareAndSet(false, true))
		{
			return;
		}

		String message = "KSP AIO Fighter cannot find the configured gear item(s) it needs in your bank:"
				+ System.lineSeparator() + System.lineSeparator()
				+ String.join(System.lineSeparator(), missingItems)
				+ System.lineSeparator() + System.lineSeparator()
				+ "Add the item(s) to your bank or remove/fix them in the gear config, then start the plugin again.";

		SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
				null,
				message,
				"KSP AIO Fighter - Missing Gear",
				JOptionPane.ERROR_MESSAGE));
	}

	private void reportGearStillMissingAfterBank(Skill skill)
	{
		List<String> stillMissing = getGearList(skill).stream()
				.filter(itemName -> !itemName.isEmpty())
				.filter(itemName -> !isConfiguredGearSatisfied(itemName) && !Rs2Inventory.hasItem(itemName))
				.collect(Collectors.toList());

		if (!stillMissing.isEmpty())
		{
			lastError = "gear still missing after bank: " + String.join(", ", stillMissing);
			lastErrorMs = System.currentTimeMillis();
			Microbot.log("KSP AIO Fighter " + lastError);
		}
	}

	private void equipGearFromOpenBank(Skill skill)
	{
		for (String itemName : getGearList(skill))
		{
			if (itemName.isEmpty() || isConfiguredGearSatisfied(itemName) || !Rs2Bank.hasItem(itemName))
			{
				continue;
			}

			if (isRangedAmmoName(itemName))
			{
				withdrawAllAndEquipAmmo(itemName);
				continue;
			}

			Rs2Bank.withdrawAndEquip(itemName);
			sleepUntil(() -> isConfiguredGearSatisfied(itemName), 2_500);
		}
	}

	private void withdrawAllAndEquipAmmo(String itemName)
	{
		setStatus("withdrawing all " + itemName);
		boolean withdrawn = invokeBankWithdrawAll(itemName);
		if (!withdrawn)
		{
			try
			{
				Rs2Bank.withdrawX(true, itemName, Integer.MAX_VALUE, true);
				withdrawn = true;
			}
			catch (Exception ex)
			{
				lastError = "ammo withdraw failed: " + itemName;
				lastErrorMs = System.currentTimeMillis();
			}
		}

		if (!withdrawn)
		{
			return;
		}

		sleepUntil(() -> Rs2Inventory.hasItem(itemName), 2_500);
		equipFromInventory(itemName);
	}

	private boolean invokeBankWithdrawAll(Object item)
	{
		String[] methodNames = {"withdrawAll", "withdrawAllItem", "withdrawAllItems"};
		Class<?> bankClass = Rs2Bank.class;
		for (String methodName : methodNames)
		{
			for (java.lang.reflect.Method method : bankClass.getMethods())
			{
				if (!method.getName().equals(methodName))
				{
					continue;
				}

				try
				{
					Class<?>[] parameterTypes = method.getParameterTypes();
					if (parameterTypes.length == 1 && canPassWithdrawArgument(parameterTypes[0], item))
					{
						Object result = method.invoke(null, coerceWithdrawArgument(parameterTypes[0], item));
						return !(result instanceof Boolean) || (Boolean) result;
					}
					if (parameterTypes.length == 2 && canPassWithdrawArgument(parameterTypes[0], item) && parameterTypes[1] == boolean.class)
					{
						Object result = method.invoke(null, coerceWithdrawArgument(parameterTypes[0], item), true);
						return !(result instanceof Boolean) || (Boolean) result;
					}
				}
				catch (Exception ignored)
				{
					// Try the next signature/method name.
				}
			}
		}
		return false;
	}

	private boolean canPassWithdrawArgument(Class<?> parameterType, Object item)
	{
		return (parameterType == String.class && item instanceof String)
				|| ((parameterType == int.class || parameterType == Integer.class) && item instanceof Integer);
	}

	private Object coerceWithdrawArgument(Class<?> parameterType, Object item)
	{
		return parameterType == String.class ? String.valueOf(item) : item;
	}

	private boolean isRangedAmmoName(String itemName)
	{
		String normalized = itemName == null ? "" : itemName.trim().toLowerCase(Locale.ENGLISH);
		return normalized.endsWith(" arrow")
				|| normalized.endsWith(" arrows")
				|| normalized.endsWith(" bolt")
				|| normalized.endsWith(" bolts")
				|| normalized.endsWith(" dart")
				|| normalized.endsWith(" darts")
				|| normalized.endsWith(" knife")
				|| normalized.endsWith(" knives")
				|| normalized.endsWith(" javelin")
				|| normalized.endsWith(" javelins")
				|| normalized.endsWith(" thrownaxe")
				|| normalized.endsWith(" thrownaxes")
				|| normalized.contains("chinchompa")
				|| normalized.equals("toktz-xil-ul");
	}

	private void depositEquippedItemsFromBank()
	{
		if (Rs2Equipment.items().isEmpty())
		{
			return;
		}

		if (invokeStaticNoArg("net.runelite.client.plugins.microbot.util.bank.Rs2Bank", "depositEquipment")
				|| invokeStaticNoArg("net.runelite.client.plugins.microbot.util.bank.Rs2Bank", "depositWornItems")
				|| invokeStaticNoArg("net.runelite.client.plugins.microbot.util.bank.Rs2Bank", "depositWornEquipment")
				|| invokeStaticNoArg("net.runelite.client.plugins.microbot.util.bank.Rs2Bank", "depositAllEquipment")
				|| invokeStaticNoArg("net.runelite.client.plugins.microbot.util.bank.Rs2Bank", "depositAllWornItems"))
		{
			sleepUntil(() -> Rs2Equipment.items().isEmpty(), 2_500);
		}
	}

	private void equipFromInventory(String itemName)
	{
		boolean started = Rs2Inventory.interact(itemName, "Wield", true)
				|| Rs2Inventory.interact(itemName, "Wear", true)
				|| Rs2Inventory.interact(itemName, "Equip", true);
		if (started)
		{
			sleepUntil(() -> isConfiguredGearSatisfied(itemName), 2_000);
		}
	}

	private List<String> getGearList(Skill skill)
	{
		switch (skill)
		{
			case ATTACK:
				return csv(config.attackGear());
			case STRENGTH:
				return csv(config.strengthGear());
			case DEFENCE:
				return csv(config.defenceGear());
			case RANGED:
				return csv(config.rangedGear());
			case MAGIC:
				return csv(config.magicGear());
			default:
				return List.of();
		}
	}

	private void configureCombatStyle(Skill skill)
	{
		if (skill == Skill.MAGIC)
		{
			if (Rs2Magic.getCurrentAutoCastSpell() != config.magicSpell())
			{
				Rs2Combat.setAutoCastSpell(config.magicSpell(), false);
			}
			selectStyleFor(skill);
			return;
		}

		selectStyleFor(skill);
	}

	private void selectStyleFor(Skill skill)
	{
		WidgetInfo widget = getStyleWidgetFor(skill);
		if (widget == null)
		{
			return;
		}

		if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT)
		{
			Rs2Tab.switchToCombatOptionsTab();
			sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.COMBAT, 2_000);
		}
		Rs2Combat.setAttackStyle(widget);
	}

	private WidgetInfo getStyleWidgetFor(Skill skill)
	{
		int weaponType = Microbot.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE);
		int styleIndex = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);
		int castingMode = Microbot.getVarbitValue(Varbits.DEFENSIVE_CASTING_MODE);
		TrainingStyle[] styles = getWeaponTypeStyles(weaponType);
		TrainingStyle current = getCurrentStyle(styles, styleIndex, castingMode);

		if (current != null && styleTrains(current, skill))
		{
			return null;
		}

		for (int i = 0; i < styles.length; i++)
		{
			TrainingStyle style = styles[i];
			if (style == null || !styleTrains(style, skill))
			{
				continue;
			}

			if (style.skills.length > 1 && !shouldAllowSharedStyle(style, skill))
			{
				continue;
			}

			return widgetForIndex(i);
		}
		return null;
	}

	private boolean styleTrains(TrainingStyle style, Skill skill)
	{
		return Arrays.asList(style.skills).contains(skill);
	}

	private boolean shouldAllowSharedStyle(TrainingStyle style, Skill skill)
	{
		if (skill == Skill.RANGED || skill == Skill.MAGIC)
		{
			return true;
		}
		return Arrays.stream(style.skills).allMatch(trained -> MELEE_SKILLS.contains(trained) && getLevel(trained) < getTarget(trained));
	}

	private TrainingStyle getCurrentStyle(TrainingStyle[] styles, int styleIndex, int castingMode)
	{
		int index = styleIndex == 4 ? styleIndex + castingMode : styleIndex;
		return index >= 0 && index < styles.length ? styles[index] : null;
	}

	private WidgetInfo widgetForIndex(int index)
	{
		switch (index)
		{
			case 0:
				return WidgetInfo.COMBAT_STYLE_ONE;
			case 1:
				return WidgetInfo.COMBAT_STYLE_TWO;
			case 2:
				return WidgetInfo.COMBAT_STYLE_THREE;
			case 3:
				return WidgetInfo.COMBAT_STYLE_FOUR;
			case 4:
				return WidgetInfo.COMBAT_SPELLS;
			case 5:
				return WidgetInfo.COMBAT_DEFENSIVE_SPELL_BOX;
			default:
				return null;
		}
	}

	private TrainingStyle[] getWeaponTypeStyles(int weaponType)
	{
		try
		{
			if (Microbot.getEnum(EnumID.WEAPON_STYLES) == null)
			{
				return new TrainingStyle[0];
			}

			int weaponStyleEnum = Microbot.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponType);
			if (Microbot.getEnum(weaponStyleEnum) == null)
			{
				return new TrainingStyle[0];
			}

			int[] weaponStyleStructs = Microbot.getEnum(weaponStyleEnum).getIntVals();
			TrainingStyle[] styles = new TrainingStyle[weaponStyleStructs.length];

			for (int i = 0; i < weaponStyleStructs.length; i++)
			{
				if (Microbot.getStructComposition(weaponStyleStructs[i]) == null)
				{
					continue;
				}

				String styleName = Microbot.getStructComposition(weaponStyleStructs[i]).getStringValue(ParamID.ATTACK_STYLE_NAME);
				styles[i] = TrainingStyle.fromName(styleName, i);
			}
			return styles;
		}
		catch (Exception ex)
		{
			return new TrainingStyle[0];
		}
	}

	private void attackNpc()
	{
		if (Rs2Player.isInCombat() || Rs2Player.isInteracting())
		{
			return;
		}

		if (shouldBlockAttackForLoot())
		{
			return;
		}

		Set<String> npcNames = getConfiguredNpcNames().stream()
				.map(name -> name.toLowerCase(Locale.ENGLISH))
				.collect(Collectors.toSet());
		if (config.useAttackArea() && !hasCompleteAttackArea())
		{
			setStatus("set attack area tile 1 and tile 2");
			return;
		}
		if (npcNames.isEmpty())
		{
			setStatus("enter NPC names");
			return;
		}

		Rs2NpcModel npc = Microbot.getRs2NpcCache().query()
				.where(candidate -> candidate.getName() != null)
				.where(candidate -> npcNames.contains(candidate.getName().toLowerCase(Locale.ENGLISH)))
				.where(candidate -> !candidate.isDead())
				.where(this::isInsidePlayerAttackSquare)
				.where(this::isInsideAttackArea)
				.where(this::canAttackNpc)
				.nearestOnClientThread(config.attackRadius() * 2);

		if (npc == null)
		{
			setStatus("no NPC in range");
			return;
		}

		cachedTargetNpcMaxHit = getNpcMaxHit(npc);
		lastTargetNpcName = npc.getName() == null ? "<unknown>" : npc.getName();
		lastTargetNpcLocation = npc.getWorldLocation();
		lastTargetUpdateMs = System.currentTimeMillis();

		// KspAccountBuilder melee combat does not keep turning the camera at every target.
		// Keep the AccountBuilder start-camera angle stable and let npc.click resolve the attack.
		npc.click("Attack");
		setStatus("attacking " + npc.getName());
	}

	private boolean canAttackNpc(Rs2NpcModel npc)
	{
		if (npc == null)
		{
			return false;
		}

		if (npc.isInteractingWithPlayer())
		{
			return true;
		}

		if (npc.isInteracting())
		{
			return false;
		}

		// KspAccountBuilder uses healthRatio < 0 as the reliable idle/untouched NPC signal.
		return npc.getHealthRatio() < 0;
	}


	private boolean isActivelyFightingForLoot()
	{
		Actor interacting = Rs2Player.getInteracting();
		if (interacting != null
				&& interacting.getCombatLevel() > 0
				&& interacting.getHealthRatio() != 0
				&& isInsideAttackArea(interacting.getWorldLocation()))
		{
			return true;
		}

		if (!Rs2Combat.inCombat())
		{
			return false;
		}

		if (!Rs2Player.isAnimating() && !Rs2Player.isInteracting())
		{
			return false;
		}

		WorldPoint playerLocation = Rs2Player.getWorldLocation();
		if (playerLocation == null || !isInsideAttackArea(playerLocation))
		{
			return false;
		}

		return Microbot.getRs2NpcCache().query()
				.where(npc -> npc != null)
				.where(Rs2NpcModel::isInteractingWithPlayer)
				.where(npc -> !npc.isDead())
				.where(npc -> isInsideAttackArea(npc.getWorldLocation()))
				.nearestOnClientThread(config.attackRadius() * 2) != null;
	}

	private void updatePostKillLootState()
	{
		boolean currentlyInCombatOrInteracting = isActivelyFightingForLoot();
		if (currentlyInCombatOrInteracting)
		{
			wasInCombatOrInteracting = true;
			return;
		}

		if (wasInCombatOrInteracting && isLootingEnabled())
		{
			postKillLootUntilMs = System.currentTimeMillis() + POST_KILL_LOOT_WINDOW_MS;
		}

		wasInCombatOrInteracting = false;
	}

	private boolean shouldBlockAttackForLoot()
	{
		if (!isLootingEnabled() || Rs2Inventory.isFull() || isActivelyFightingForLoot())
		{
			return false;
		}

		if (isLootPickupPending())
		{
			return true;
		}

		Rs2TileItemModel loot = findNextLoot();
		if (loot == null)
		{
			return false;
		}

		String lootName = loot.getName();
		String displayName = lootName == null || lootName.isBlank() ? "item" : lootName;
		setStatus(Rs2Player.isMoving() ? "moving to loot " + displayName : "loot available " + displayName);
		return true;
	}

	private boolean shouldPrioritizePostKillLoot()
	{
		return isLootingEnabled()
				&& !Rs2Inventory.isFull()
				&& !Rs2Player.isInCombat()
				&& System.currentTimeMillis() < postKillLootUntilMs;
	}

	private boolean isLootingEnabled()
	{
		return config.lootItems() || config.buryBones();
	}

	private boolean buryBonesFromInventory()
	{
		if (!config.buryBones() || Rs2Bank.isOpen() || Rs2Player.isMoving() || isActivelyFightingForLoot())
		{
			return false;
		}

		Rs2ItemModel bone = getBuryableInventoryBone();
		if (bone == null)
		{
			return false;
		}

		int before = Rs2Inventory.count(bone.getId());
		String boneName = bone.getName() == null || bone.getName().isBlank() ? "bones" : bone.getName();
		setStatus("burying " + boneName);

		boolean clicked = Rs2Inventory.interact(boneName, "Bury", true);
		if (!clicked)
		{
			lastError = "failed to bury " + boneName;
			lastErrorMs = System.currentTimeMillis();
			return false;
		}

		sleepUntil(() -> Rs2Inventory.count(bone.getId()) < before || Rs2Player.isAnimating(), 1_200);
		return true;
	}

	private Rs2ItemModel getBuryableInventoryBone()
	{
		List<Rs2ItemModel> bones = Rs2Inventory.getList(this::isBoneInventoryItem);
		return bones.stream()
				.findFirst()
				.orElse(null);
	}

	private boolean isBoneInventoryItem(Rs2ItemModel item)
	{
		if (item == null || item.isNoted())
		{
			return false;
		}

		String itemName = normalizeLootName(item.getName());
		if (!isBoneLootName(itemName))
		{
			return false;
		}

		String[] actions = item.getInventoryActions();
		if (actions == null)
		{
			return false;
		}

		return Arrays.stream(actions)
				.filter(action -> action != null)
				.anyMatch("Bury"::equalsIgnoreCase);
	}

	private boolean loot()
	{
		// Do not block looting only because the player is still flagged as interacting.
		// After an NPC dies that flag can linger for a few ticks, which caused the visible loot delay.
		if (!isLootingEnabled() || Rs2Inventory.isFull() || Rs2Player.isMoving() || isActivelyFightingForLoot())
		{
			if (Rs2Inventory.isFull() || isActivelyFightingForLoot())
			{
				clearPendingLoot();
			}
			return false;
		}

		if (isLootPickupPending())
		{
			return true;
		}

		Rs2TileItemModel loot = findNextLoot();
		if (loot == null)
		{
			clearPendingLoot();
			return false;
		}

		String lootName = loot.getName();
		String displayName = lootName == null || lootName.isBlank() ? "item" : lootName;

		setStatus("looting " + displayName);
		boolean clicked = KspLootingHelper.take(loot, log, false, "KSP AIO Fighter loot");
		if (clicked)
		{
			markLootPickupPending(loot);
			postKillLootUntilMs = Math.max(postKillLootUntilMs, System.currentTimeMillis() + 350L);
			sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isInteracting() || findNextLoot() == null, 450);
		}
		else
		{
			pendingLootUntilMs = System.currentTimeMillis() + 250L;
			setStatus("loot click unavailable");
		}
		return clicked;
	}

	private boolean isLootPickupPending()
	{
		if (pendingLootUntilMs <= 0L)
		{
			return false;
		}

		if (System.currentTimeMillis() >= pendingLootUntilMs)
		{
			clearPendingLoot();
			return false;
		}

		boolean stillOnGround = Microbot.getRs2TileItemCache().query()
				.where(item -> item != null)
				.where(item -> item.getId() == pendingLootId)
				.where(item -> {
					LocalPoint localPoint = item.getLocalLocation();
					return localPoint != null
							&& localPoint.getSceneX() == pendingLootSceneX
							&& localPoint.getSceneY() == pendingLootSceneY
							&& localPoint.getWorldView() == pendingLootWorldViewId;
				})
				.nearestOnClientThread() != null;

		if (!stillOnGround)
		{
			clearPendingLoot();
			return false;
		}

		setStatus("waiting for loot pickup");
		return true;
	}

	private void markLootPickupPending(Rs2TileItemModel loot)
	{
		LocalPoint localPoint = loot.getLocalLocation();
		if (localPoint == null)
		{
			clearPendingLoot();
			return;
		}

		pendingLootId = loot.getId();
		pendingLootSceneX = localPoint.getSceneX();
		pendingLootSceneY = localPoint.getSceneY();
		pendingLootWorldViewId = localPoint.getWorldView();
		pendingLootUntilMs = System.currentTimeMillis() + LOOT_CLICK_COOLDOWN_MS;
	}

	private void clearPendingLoot()
	{
		pendingLootId = -1;
		pendingLootSceneX = -1;
		pendingLootSceneY = -1;
		pendingLootWorldViewId = -1;
		pendingLootUntilMs = 0L;
	}

	private Rs2TileItemModel findNextLoot()
	{
		Set<String> configuredNames = getLootNameNeedles();
		int searchRadius = Math.max(1, config.attackRadius());
		return Microbot.getRs2TileItemCache().query()
				.fromWorldView()
				.where(item -> item != null)
				.where(item -> !item.isDespawned())
				.where(Rs2TileItemModel::isLootAble)
				.where(this::matchesLootOwnership)
				.where(this::isLootInsideAttackArea)
				.where(this::canStoreLoot)
				.where(item -> matchesConfiguredLoot(item, configuredNames))
				.within(searchRadius)
				.nearestOnClientThread(searchRadius);
	}

	private boolean matchesLootOwnership(Rs2TileItemModel item)
	{
		return !lootOwnOnly() || item.isOwned();
	}

	private boolean isLootInsideAttackArea(Rs2TileItemModel item)
	{
		if (!config.useAttackArea())
		{
			return true;
		}

		return isInsideConfiguredArea(item.getWorldLocation());
	}

	private boolean canStoreLoot(Rs2TileItemModel item)
	{
		if (Rs2Inventory.emptySlotCount() > 0)
		{
			return true;
		}
		return item.isStackable() && Rs2Inventory.hasItem(item.getId());
	}

	private boolean matchesConfiguredLoot(Rs2TileItemModel item, Set<String> configuredNames)
	{
		String itemName = normalizeLootName(item == null ? null : item.getName());
		if (itemName.isEmpty())
		{
			return false;
		}

		// Bury bones is intentionally separate from the custom loot list.
		// Everything else must be an exact configured item-name match. The old
		// contains-based matching made broad entries accidentally loot unrelated items.
		if (config.buryBones() && isBoneLootName(itemName))
		{
			return true;
		}

		return config.lootItems()
				&& !configuredNames.isEmpty()
				&& configuredNames.contains(itemName);
	}

	private boolean isBoneLootName(String itemName)
	{
		return "bones".equals(itemName)
				|| itemName.endsWith(" bones")
				|| itemName.endsWith("bone");
	}

	private String normalizeLootName(String name)
	{
		return name == null ? "" : name.trim().toLowerCase(Locale.ENGLISH);
	}

	private Set<String> getLootNameNeedles()
	{
		return new HashSet<>(csv(config.itemsToLoot()).stream()
				.map(this::normalizeLootName)
				.filter(name -> !name.isEmpty())
				.collect(Collectors.toSet()));
	}

	private boolean lootOwnOnly()
	{
		return config.lootOwnership() == KspLootOwnership.LOOT_OWN;
	}

	private void highAlch()
	{
		if (!config.highAlchLoot())
		{
			return;
		}

		List<Rs2ItemModel> items = Rs2Inventory.getList(Rs2ItemModel::isHaProfitable);
		if (items.isEmpty())
		{
			return;
		}

		if (Rs2ExplorersRing.hasRing() && Rs2ExplorersRing.hasCharges())
		{
			Rs2ExplorersRing.highAlch(items.get(0));
			Rs2ExplorersRing.closeInterface();
			return;
		}

		if (!Rs2Magic.canCast(Rs2Spells.HIGH_LEVEL_ALCHEMY))
		{
			return;
		}

		Rs2ItemModel item = items.get(0);
		Rs2Magic.alch(item);
		if (item.getHaPrice() > Rs2Settings.getMinimumItemValueAlchemyWarning())
		{
			sleepUntil(() -> Rs2Widget.hasWidget("Proceed to cast High Alchemy on it"), 1_200);
			if (Rs2Widget.hasWidget("Proceed to cast High Alchemy on it"))
			{
				Rs2Keyboard.keyPress('1');
			}
		}
		Rs2Player.waitForAnimation();
	}

	private List<String> getConfiguredNpcNames()
	{
		return csv(config.npcNames());
	}

	private List<String> csv(String value)
	{
		if (value == null || value.trim().isEmpty())
		{
			return List.of();
		}

		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(entry -> !entry.isEmpty())
				.collect(Collectors.toList());
	}

	private enum TrainingStyle
	{
		ACCURATE(Skill.ATTACK),
		AGGRESSIVE(Skill.STRENGTH),
		DEFENSIVE(Skill.DEFENCE),
		CONTROLLED(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE),
		RANGING(Skill.RANGED),
		LONGRANGE(Skill.RANGED, Skill.DEFENCE),
		CASTING(Skill.MAGIC),
		DEFENSIVE_CASTING(Skill.MAGIC, Skill.DEFENCE),
		OTHER;

		private final Skill[] skills;

		TrainingStyle(Skill... skills)
		{
			this.skills = skills;
		}

		private static TrainingStyle fromName(String name, int index)
		{
			if (name == null || name.isEmpty())
			{
				return OTHER;
			}

			try
			{
				TrainingStyle style = TrainingStyle.valueOf(name.toUpperCase(Locale.ENGLISH).replace(' ', '_'));
				if (index == 5 && style == DEFENSIVE)
				{
					return DEFENSIVE_CASTING;
				}
				return style;
			}
			catch (IllegalArgumentException ignored)
			{
				return OTHER;
			}
		}
	}
}







