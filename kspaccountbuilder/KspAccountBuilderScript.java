package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.areas.CombatAreas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.meleescript.MeleeScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.KspBankWidgetHelper;
import net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.experiencelamps.KspExperienceLampScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.cookscript.CooksScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.cooksassistant.reqs.Items;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.goblindipscript.GobScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.goblindip.reqs.GobReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.romeoandjuliet.romeoscript.RomeoScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.runemyst.RuneMystScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.cookingscript.CookingScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.levels.CookLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.clevels.CraftingLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.craftingscript.CraftingScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory.CraftInventory;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory.CraftInventory.Ingredient;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.fishingscript.FishingScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.levelreqfishing.LevelReqs;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.firemakingscript.FireMakingScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.fmarea.FireArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.loglevels.LogsLvl;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript.MiningScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.runeessscript.EssenceMining;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.Buy;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript.BuyScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.gearea.GEArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.sellscript.SellScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.recipes.SmithRecipe;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smitharea.SmithArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithlevels.SmithLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithscript.SmithScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.oresreq.ReqOres;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltarea.SmeltArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltscript.SmeltScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.stronghold.script.SoCScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.tutorialisland.TutorialIslandScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.util.autologin.AutoLoginScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas.TreeAreas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.woodcuttingscript.WoodCuttingScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.ClientUI;

import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.sheepshearer.sheepscript.SheepScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.xmarksthespot.xmarksscript.XMarksScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.therestlessghost.ghostscript.GhostScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.impcatcher.impscript.ImpScript;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.questing.shared.SimpleQuestScript;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class KspAccountBuilderScript extends Script
{
    private enum BuilderTask
    {
        TUTORIAL_ISLAND(0),
        COOKS_ASSISTANT(30),
        GOBLIN_DIPLOMACY(30),
        ROMEO_AND_JULIET(30),
        RUNE_MYSTERIES(30),
        SHEEP_SHEARER(30),
        X_MARKS_THE_SPOT(30),
        THE_RESTLESS_GHOST(30),
        IMP_CATCHER(30),
        STRONGHOLD_OF_SECURITY(30),
        RUNE_ESSENCE(7),
        MINING(7),
        WOODCUTTING(7),
        FIREMAKING(12),
        FISHING(12),
        COOKING(14),
        CRAFTING(14),
        MELEE(14),
        GE_SELL(1),
        GE_BUY(1),
        SMITHING(14),
        SMELTING(14);

        private final int selectionWeight;

        BuilderTask(int selectionWeight)
        {
            this.selectionWeight = selectionWeight;
        }

        private int getSelectionWeight() { return selectionWeight; }
    }

    private static final int LOOP_DELAY_MS = 100;
    private static final String EXTERNAL_AUTO_LOGIN_PLUGIN_CLASS = "net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin";
    // Camera values recovered from the supplied Account Builder 1.5.200 bytecode.
    private static final int POST_TUTORIAL_BANK_CAMERA_PITCH = 2821;
    private static final int POST_TUTORIAL_BANK_CAMERA_YAW = 1951;
    private static final int POST_TUTORIAL_BANK_CAMERA_SCALE = 612;
    private static final int POST_TUTORIAL_BANK_CAMERA_RAW_ZOOM = 330;
    private static final int CAMERA_ZOOM_MIN = 0;
    private static final int CAMERA_ZOOM_MAX = 1400;
    private static final int CAMERA_SCALE_TOLERANCE = 12;
    private static final int CAMERA_SCALE_SAMPLE_DELAY_MS = 220;
    private static final int COINS_ID = 995;
    private static final String COINS = "Coins";
    private static final int MIN_QUEST_BUY_PRICE = 1_000;
    private static final String BLUE_GOBLIN_MAIL = "Blue goblin mail";
    private static final String ORANGE_GOBLIN_MAIL = "Orange goblin mail";
    private static final int CHICKEN_TARGET_COMBAT_STAT_LEVEL = 15;
    private static final long PLAY_TIME_READ_RETRY_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long BREAK_LOGOUT_COMBAT_GRACE_MS = TimeUnit.SECONDS.toMillis(11);
    private static final long TASK_SWITCH_ACTION_COOLDOWN_MS = 500L;
    private static final long TASK_SWITCH_DISPATCH_TIMEOUT_MS = 1_500L;
    private static final int DRAYNOR_CORRIDOR_MIN_X = 3050;
    private static final int DRAYNOR_CORRIDOR_MAX_X = 3135;
    private static final int DRAYNOR_CORRIDOR_MIN_Y = 3230;
    private static final int DRAYNOR_CORRIDOR_MAX_Y = 3400;

    @Inject
    private MiningScript miningScript;

    @Inject
    private WoodCuttingScript woodCuttingScript;

    @Inject
    private FireMakingScript fireMakingScript;

    @Inject
    private FishingScript fishingScript;

    @Inject
    private CookingScript cookingScript;

    @Inject
    private CraftingScript craftingScript;

    @Inject
    private MeleeScript meleeScript;

    @Inject
    private BuyScript buyScript;

    @Inject
    private SellScript sellScript;

    @Inject
    private SmithScript smithScript;

    @Inject
    private SmeltScript smeltScript;

    @Inject
    private TutorialIslandScript tutorialIslandScript;

    @Inject
    private CooksScript cooksScript;

    @Inject
    private GobScript gobScript;

    @Inject
    private RomeoScript romeoScript;

    @Inject
    private RuneMystScript runeMystScript;

    @Inject
    private SheepScript sheepScript;

    @Inject
    private XMarksScript xMarksScript;

    @Inject
    private GhostScript ghostScript;

    @Inject
    private ImpScript impScript;

    @Inject
    private EssenceMining essenceMining;

    @Inject
    private SoCScript strongholdScript;

    @Inject
    private AutoLoginScript autoLoginScript;

    @Inject
    private KspExperienceLampScript experienceLampScript;

    @Inject
    private KspAccountPlayTimeCache accountPlayTimeCache;

    @Inject
    private KspAccountTaskCache accountTaskCache;

    private volatile BuilderTask currentTask;

    private volatile boolean breakActive;

    private long startedAtMillis;

    private boolean taskStarted;
    private long nextBreakAtMillis;
    private long breakEndsAtMillis;
    private long nextActivitySwitchAtMillis;
    private long pausedActivitySwitchRemainingMillis;
    private boolean activitySwitchTimerPaused;
    private boolean sharedBreakActive;
    private boolean experienceLampInterruptionActive;
    private boolean experienceLampPausedActivityTimer;
    private boolean essenceMineRecoveryActive;
    private long lastStatusLogAt;
    private KspAccountBuilderConfig config;
    private boolean debugEnabled;
    private boolean debugLoggingApplied;
    private volatile BuilderTask pendingTask;
    private BuilderTask preselectedHandoffTask;
    private BuilderTask singleSkillRecoveryTask;
    private BuilderTask auditedSingleSkillTask;
    private BuilderTask pendingSingleSkillAuditTask;
    private int singleSkillAuditBankEpoch;
    private boolean singleSkillAuditBankWasOpen;
    private String activeSingleSkillSelection;
    private boolean pendingRandomTaskSelection;
    private boolean awaitingNextActivityStart;
    private boolean awaitingActivitySwitchTimerStart;
    private boolean breakLogoutRequested;
    private boolean breakCombatWaitLogged;
    private long lastCombatObservedAtMillis;
    private boolean postTutorialBankCameraPending;
    private volatile boolean shuttingDown;
    private long lastBreakLoginAttemptAt;
    private long lastLoginHandoffLogAt;
    private String originalWindowTitle = "Microbot";
    private long synchronizedPlayTimeAccountHash;
    private volatile long currentAccountHashSnapshot;
    private long nextPlayTimeReadAtMillis;
    private BankLocation taskSwitchBankLocation;
    private long lastTaskSwitchActionAtMs;
    private long pendingTaskSwitchInventoryAtMs;
    private int pendingTaskSwitchInventoryEmptySlots = -1;
    private long pendingTaskSwitchEquipmentAtMs;
    private boolean taskSwitchBankResetPending;

    public BuilderTask getCurrentTask() { return currentTask; }

    public boolean isBreakActive() { return breakActive; }

    public long getStartedAtMillis() { return startedAtMillis; }

    public boolean run(KspAccountBuilderConfig config)
    {
        shutdown();
        shuttingDown = false;
        this.config = config;
        resetRuntimeState();

        stopExternalAutoLoginPlugin("builder-start");
        startAutoLoginHelper();
        if (experienceLampScript != null) experienceLampScript.run();

        syncDebugLogging(true);
        applyAntibanSettings();
        captureOriginalWindowTitle();
        startedAtMillis = System.currentTimeMillis();
        scheduleNextBreak();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::runLoop,
                0,
                LOOP_DELAY_MS,
                TimeUnit.MILLISECONDS);
        return true;
    }

    private void runLoop()
    {
        try
        {
            if (!super.run()) return;

            syncDebugLogging(false);

            if (KspWorldMapGuard.closeIfOpen())
            {
                KspWalkerGuard.clearActiveWalker("ksp_account_builder_world_map_open");
                maybeLogStatus();
                return;
            }

            refreshAccountIdentity();
            if (handleExperienceLampInterruption())
            {
                updateWindowTitle();
                maybeLogStatus();
                return;
            }

            processTimers();
            updateWindowTitle();

            if (isAnyBreakActive()
                    || !Microbot.isLoggedIn()
                    || !isReadyAfterLoginHandoff())
            {
                maybeLogStatus();
                return;
            }

            if (handleUnexpectedEssenceMineRecovery()
                    || pendingTask != null
                    || pendingRandomTaskSelection)
            {
                maybeLogStatus();
                return;
            }

            if (currentTask == null)
            {
                currentTask = resolveStartingTask();
                awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
                debug("Selected initial task | currentTask={}", currentTask);
            }

            runAccountBuilderCycle();
            sampleAccountPlayTime();
            maybeLogStatus();
        }
        catch (Exception ex)
        {
            log.error("[KSP Account Builder] Main account-builder loop failed; keeping scheduler alive", ex);
            taskStarted = false;
            pendingTask = null;
            pendingRandomTaskSelection = false;
            awaitingNextActivityStart = false;
            awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
        }
    }

    private void resetRuntimeState()
    {
        currentTask = null;
        taskStarted = false;
        breakActive = false;
        pendingTask = null;
        preselectedHandoffTask = null;
        singleSkillRecoveryTask = null;
        auditedSingleSkillTask = null;
        pendingSingleSkillAuditTask = null;
        singleSkillAuditBankEpoch = -1;
        singleSkillAuditBankWasOpen = false;
        activeSingleSkillSelection = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = false;
        breakLogoutRequested = false;
        breakCombatWaitLogged = false;
        lastCombatObservedAtMillis = 0L;
        postTutorialBankCameraPending = true;
        lastBreakLoginAttemptAt = 0L;
        lastLoginHandoffLogAt = 0L;
        synchronizedPlayTimeAccountHash = 0L;
        currentAccountHashSnapshot = 0L;
        nextPlayTimeReadAtMillis = 0L;
        taskSwitchBankLocation = null;
        lastTaskSwitchActionAtMs = 0L;
        pendingTaskSwitchInventoryAtMs = 0L;
        pendingTaskSwitchInventoryEmptySlots = -1;
        pendingTaskSwitchEquipmentAtMs = 0L;
        taskSwitchBankResetPending = false;
        pausedActivitySwitchRemainingMillis = -1L;
        activitySwitchTimerPaused = false;
        sharedBreakActive = false;
        essenceMineRecoveryActive = false;
        nextActivitySwitchAtMillis = -1L;
        lastStatusLogAt = 0L;
        debugLoggingApplied = false;
    }

    private void syncDebugLogging(boolean force)
    {
        boolean enabled = config != null && config.debugLogging();
        if (!force && debugLoggingApplied && enabled == debugEnabled) return;

        debugEnabled = enabled;
        miningScript.setDebugLogging(enabled);
        woodCuttingScript.setDebugLogging(enabled);
        fireMakingScript.setDebugLogging(enabled);
        fishingScript.setDebugLogging(enabled);
        cookingScript.setDebugLogging(enabled);
        craftingScript.setDebugLogging(enabled);
        meleeScript.setDebugLogging(enabled);
        buyScript.setDebugLogging(enabled);
        sellScript.setDebugLogging(enabled);
        smithScript.setDebugLogging(enabled);
        smeltScript.setDebugLogging(enabled);
        tutorialIslandScript.setDebugLogging(enabled);
        cooksScript.setDebugLogging(enabled);
        gobScript.setDebugLogging(enabled);
        romeoScript.setDebugLogging(enabled);
        runeMystScript.setDebugLogging(enabled);
        sheepScript.setDebugLogging(enabled);
        xMarksScript.setDebugLogging(enabled);
        ghostScript.setDebugLogging(enabled);
        impScript.setDebugLogging(enabled);
        essenceMining.setDebugLogging(enabled);
        if (autoLoginScript != null) autoLoginScript.setDebugLogging(enabled);
        debugLoggingApplied = true;
    }

    private boolean handleExperienceLampInterruption()
    {
        if (!Microbot.isLoggedIn() || experienceLampScript == null)
        {
            return false;
        }

        if (experienceLampScript.hasPendingLamp())
        {
            if (!experienceLampInterruptionActive)
            {
                experienceLampInterruptionActive = true;
                experienceLampPausedActivityTimer = !activitySwitchTimerPaused;
                pauseActivitySwitchTimer(System.currentTimeMillis());
                if (taskStarted && currentTask != null)
                {
                    stopCurrentTaskScript();
                    taskStarted = false;
                }
                KspWalkerGuard.clearActiveWalker("ksp_experience_lamp_interruption");
                debug("Paused task for experience lamp | currentTask={}", currentTask);
            }

            Microbot.status = "Using experience lamp";
            return true;
        }

        if (!experienceLampInterruptionActive)
        {
            return false;
        }

        experienceLampInterruptionActive = false;
        if (experienceLampPausedActivityTimer)
        {
            resumeActivitySwitchTimer(System.currentTimeMillis());
        }
        experienceLampPausedActivityTimer = false;
        Microbot.status = currentTask == null
                ? "Experience lamp complete"
                : "Resuming " + currentTask;
        debug("Experience lamp complete; resuming task | currentTask={}", currentTask);
        return true;
    }

    private void processTimers()
    {
        if (shuttingDown) return;

        long now = System.currentTimeMillis();
        if (Microbot.isLoggedIn() && Rs2Combat.inCombat()) lastCombatObservedAtMillis = now;

        updateSharedBreakState(now);
        if (sharedBreakActive) return;

        if (config.doBreaks())
        {
            if (!breakActive && now >= nextBreakAtMillis) startBreak(now);
            if (breakActive) handleBreak(now);
            if (breakActive) return;
        }

        if (!Microbot.isLoggedIn()) return;

        if (currentTask == null || isSingleSkillTaskForced())
        {
            clearPendingActivitySwitch();
            return;
        }

        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            clearPendingActivitySwitch();
            clearActivitySwitchTimerState();
            return;
        }

        if (!canUseActivitySwitchTimer() || awaitingNextActivityStart || awaitingActivitySwitchTimerStart)
        {
            return;
        }

        if (pendingTask == null
                && !pendingRandomTaskSelection
                && nextActivitySwitchAtMillis > 0L
                && now >= nextActivitySwitchAtMillis)
        {
            if (!isSafeToStartActivitySwitch()) return;

            pendingRandomTaskSelection = true;
            taskStarted = false;
            stopAllTaskScriptsExcept(null);
            debug("Preparing activity switch from {}; next task will be selected after bank cleanup", currentTask);
        }

        if (pendingRandomTaskSelection && switchToRandomTaskAfterBank(currentTask))
        {
            pendingRandomTaskSelection = false;
            pendingTask = null;
            awaitNextActivityStart();
            return;
        }

        if (pendingTask != null && switchTask(pendingTask))
        {
            pendingTask = null;
            awaitNextActivityStart();
        }
    }

    private void startBreak(long now)
    {
        pauseActivitySwitchTimer(now);
        breakActive = true;
        taskStarted = false;
        clearPendingActivitySwitch();
        breakLogoutRequested = false;
        breakCombatWaitLogged = false;
        lastBreakLoginAttemptAt = 0L;
        stopExternalAutoLoginPlugin("ksp-break-start");
        stopAutoLoginHelperForBreak();
        stopAllTaskScriptsExcept(null);
        breakEndsAtMillis = now + TimeUnit.MINUTES.toMillis(
                randomMinutes(config.breakDurationMinMinutes(), config.breakDurationMaxMinutes()));
        debug("Starting break for {} seconds", getBreakTimeRemainingSeconds());
    }

    private void handleBreak(long now)
    {
        if (Microbot.isLoggedIn() && !breakLogoutRequested)
        {
            long grace = BREAK_LOGOUT_COMBAT_GRACE_MS - (now - lastCombatObservedAtMillis);
            if (Rs2Combat.inCombat() || grace > 0L)
            {
                Microbot.status = "Waiting to leave combat before break";
                if (!breakCombatWaitLogged)
                {
                    breakCombatWaitLogged = true;
                    debug("Delaying break logout until combat is clear | graceRemainingSeconds={}",
                            Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(grace) + 1L));
                }
            }
            else
            {
                Rs2Player.logout();
                breakLogoutRequested = true;
                breakCombatWaitLogged = false;
            }
        }

        if (now < breakEndsAtMillis) return;

        breakActive = false;
        breakLogoutRequested = false;
        breakCombatWaitLogged = false;
        scheduleNextBreak();
        resumeActivitySwitchTimer(now);
        debug("Break completed, resuming tasks");
        startAutoLoginHelper();
        if (!Microbot.isLoggedIn()) attemptLoginAfterBreak();
    }

    private void clearPendingActivitySwitch()
    {
        pendingTask = null;
        preselectedHandoffTask = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = false;
        nextActivitySwitchAtMillis = -1L;
    }

    private void awaitNextActivityStart()
    {
        awaitingNextActivityStart = true;
        awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
        nextActivitySwitchAtMillis = -1L;
    }

    private boolean isSafeToStartActivitySwitch()
    {
        if (Rs2Walker.getCurrentTarget() != null
                || Rs2Player.isMoving()
                || Rs2Player.isInteracting()
                || Rs2Dialogue.isInDialogue())
        {
            KspTaskDebug.throttled(log, debugEnabled, "Builder", "activity-switch-travel-deferral", 5_000L,
                    "Deferring expired activity switch until travel completes | task={} player={} walkerTarget={} moving={} interacting={} dialogue={}",
                    currentTask,
                    getSafePlayerLocation(),
                    Rs2Walker.getCurrentTarget(),
                    Rs2Player.isMoving(),
                    Rs2Player.isInteracting(),
                    Rs2Dialogue.isInDialogue());
            return false;
        }

        return true;
    }

    private void startAutoLoginHelper()
    {
        if (autoLoginScript == null)
        {
            return;
        }

        stopExternalAutoLoginPlugin("ksp-helper-start");
        autoLoginScript.setDebugLogging(debugEnabled);
        autoLoginScript.run(() -> !breakActive);
    }

    private void stopAutoLoginHelperForBreak()
    {
        if (autoLoginScript == null)
        {
            return;
        }

        autoLoginScript.shutdown();
        debug("Paused KSP AutoLogin helper for break");
    }

    private void runAccountBuilderCycle()
    {
        if (currentTask == BuilderTask.FISHING)
        {
            fishingScript.stopWalkerIfInsideTargetArea();
        }

        BuilderTask forcedTask = resolveSingleSkillTask();
        refreshSingleSkillTargetIfChanged(forcedTask);

        if (handleForcedTaskBlockers(forcedTask))
        {
            return;
        }

        boolean recovering = handleSingleSkillResourceRecovery(forcedTask);
        if (recovering)
        {
            if (currentTask != BuilderTask.GE_BUY || buyScript.isComplete())
            {
                return;
            }
        }
        else
        {
            if (auditSingleSkillResourcesIfNeeded(forcedTask))
            {
                return;
            }

            applySingleSkillOverride();
            if (forcedTask != null && !hasResourcesForTask(forcedTask))
            {
                startSingleSkillResourceRecovery(forcedTask);
                return;
            }
        }

        if (!isSingleSkillTaskForced())
        {
            handleTutorialIslandPriority();
        }

        if (postTutorialBankCameraPending && currentTask != BuilderTask.TUTORIAL_ISLAND)
        {
            postTutorialBankCameraPending = !setPostTutorialBankCamera();
        }

        if (handlePendingMeleeSellHandoff())
        {
            return;
        }

        if (!isSingleSkillTaskForced() && !hasResourcesForTask(currentTask)
                && !switchToTaskWithResources())
        {
            taskStarted = false;
            return;
        }

        if (taskStarted)
        {
            if (!handleCompletedTask())
            {
                maybeStartActivitySwitchTimer();
            }
            return;
        }

        taskStarted = startTask(currentTask);
        if (taskStarted && awaitingNextActivityStart)
        {
            awaitingNextActivityStart = false;
            debug("Activity switch completed; waiting to reach task area before starting next switch timer");
        }
        maybeStartActivitySwitchTimer();
    }

    private boolean handleForcedTaskBlockers(BuilderTask forcedTask)
    {
        SimpleQuestScript questScript = newQuestScript(forcedTask);
        if (questScript != null && questScript.isComplete())
        {
            currentTask = forcedTask;
            taskStarted = false;
            Microbot.status = forcedTask + " complete";
            return true;
        }

        if ((forcedTask == BuilderTask.ROMEO_AND_JULIET || forcedTask == BuilderTask.RUNE_MYSTERIES)
                && isOneTimeTaskCompleted(forcedTask))
        {
            currentTask = forcedTask;
            taskStarted = false;
            Microbot.status = forcedTask == BuilderTask.ROMEO_AND_JULIET
                    ? "Romeo and Juliet complete"
                    : "Rune Mysteries complete";
            return true;
        }

        if (forcedTask == BuilderTask.RUNE_ESSENCE && !isRuneMysteriesComplete())
        {
            stopCurrentTaskScript();
            currentTask = forcedTask;
            taskStarted = false;
            Microbot.status = "Rune Essence requires Rune Mysteries";
            return true;
        }

        if (isSingleSkillTargetRequired(forcedTask)
                && resolveSingleSkillTarget(config.singleSkillTask()) == KspSingleSkillTarget.NONE)
        {
            if (taskStarted || currentTask != forcedTask)
            {
                stopCurrentTaskScript();
                currentTask = forcedTask;
                taskStarted = false;
            }
            Microbot.status = "Select a " + config.singleSkillTask() + " target";
            return true;
        }
        return false;
    }

    private boolean handleCompletedTask()
    {
        if (currentTask == null)
        {
            return false;
        }

        boolean forced = isSingleSkillTaskForced();
        SimpleQuestScript questScript = newQuestScript(currentTask);
        if (questScript != null)
        {
            if (!questScript.isComplete()) return false;
            markOneTimeComplete(KspAccountTaskCache.OneTimeTask.valueOf(currentTask.name()));
            return finishOneTimeTask(forced, currentTask + " complete");
        }
        switch (currentTask)
        {
            case TUTORIAL_ISLAND:
                if (forced || !tutorialIslandScript.isComplete()) return false;
                stopCurrentTaskScript();
                taskStarted = false;
                postTutorialBankCameraPending = !setPostTutorialBankCamera();
                if (!switchToRandomTaskAfterBank(BuilderTask.TUTORIAL_ISLAND)) stopCurrentTaskScript();
                return true;

            case COOKS_ASSISTANT:
                if (forced || !cooksScript.isComplete()) return false;
                return finishAndSwitch();

            case GOBLIN_DIPLOMACY:
                if (forced || !gobScript.isComplete()) return false;
                return finishAndSwitch();

            case ROMEO_AND_JULIET:
                if (!romeoScript.isComplete()) return false;
                markOneTimeComplete(KspAccountTaskCache.OneTimeTask.ROMEO_AND_JULIET);
                return finishOneTimeTask(forced, "Romeo and Juliet complete");

            case RUNE_MYSTERIES:
                if (!runeMystScript.isComplete()) return false;
                markOneTimeComplete(KspAccountTaskCache.OneTimeTask.RUNE_MYSTERIES);
                return finishOneTimeTask(forced, "Rune Mysteries complete");

            case STRONGHOLD_OF_SECURITY:
                if (forced || !strongholdScript.isComplete()) return false;
                markOneTimeComplete(KspAccountTaskCache.OneTimeTask.STRONGHOLD_OF_SECURITY);
                return finishAndSwitch();

            case GE_BUY:
                return !forced && buyScript.isComplete() && finishAndSwitch();

            case GE_SELL:
                return !forced && sellScript.isComplete() && finishAndSwitch();

            default:
                return false;
        }
    }

    private boolean finishAndSwitch()
    {
        stopCurrentTaskScript();
        taskStarted = false;
        if (!switchToTaskWithResources())
        {
            stopCurrentTaskScript();
        }
        return true;
    }

    private boolean finishOneTimeTask(boolean forced, String status)
    {
        stopCurrentTaskScript();
        taskStarted = false;
        if (forced)
        {
            Microbot.status = status;
            return true;
        }
        if (!switchToTaskWithResources())
        {
            stopCurrentTaskScript();
        }
        return true;
    }

    private void markOneTimeComplete(KspAccountTaskCache.OneTimeTask task)
    {
        accountTaskCache.setCompleted(getCurrentAccountHash(), task, true);
    }

    private boolean startTask(BuilderTask task)
    {
        if (task == null)
        {
            return false;
        }

        stopAllTaskScriptsExcept(task);
        if (task == BuilderTask.TUTORIAL_ISLAND
                || task == BuilderTask.ROMEO_AND_JULIET
                || task == BuilderTask.RUNE_MYSTERIES
                || newQuestScript(task) != null
                || task == BuilderTask.STRONGHOLD_OF_SECURITY)
        {
            clearActivitySwitchTimerState();
        }

        switch (task)
        {
            case TUTORIAL_ISLAND:
                return tutorialIslandScript.run();
            case COOKS_ASSISTANT:
                return cooksScript.run();
            case GOBLIN_DIPLOMACY:
                return gobScript.run();
            case ROMEO_AND_JULIET:
                return romeoScript.run();
            case RUNE_MYSTERIES:
                return runeMystScript.run();
            case SHEEP_SHEARER: return sheepScript.run();
            case X_MARKS_THE_SPOT: return xMarksScript.run();
            case THE_RESTLESS_GHOST: return ghostScript.run();
            case IMP_CATCHER: return impScript.run();
            case RUNE_ESSENCE:
                return essenceMining.run();
            case STRONGHOLD_OF_SECURITY:
                return strongholdScript.run();
            case MINING:
            {
                Areas area = resolveSingleSkillTarget(KspTrainSingleSkillTask.MINING).getValue(Areas.class);
                miningScript.setProgressiveMining(area == null);
                return miningScript.run(area == null ? resolveMiningStartArea() : area);
            }
            case WOODCUTTING:
            {
                TreeAreas area = resolveSingleSkillTarget(KspTrainSingleSkillTask.WOODCUTTING).getValue(TreeAreas.class);
                woodCuttingScript.setProgressiveWoodcutting(area == null);
                return woodCuttingScript.run(area == null ? resolveWoodcuttingStartArea() : area);
            }
            case FIREMAKING:
                return fireMakingScript.run(FireArea.FM_AREA_DRAYNOR_BANK);
            case FISHING:
            {
                net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas area =
                        resolveSingleSkillTarget(KspTrainSingleSkillTask.FISHING)
                                .getValue(net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas.class);
                return fishingScript.run(area == null ? resolveFishingStartArea() : area, area == null);
            }
            case COOKING:
            {
                net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.areas.Areas area =
                        resolveSingleSkillTarget(KspTrainSingleSkillTask.COOKING)
                                .getValue(net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.areas.Areas.class);
                return cookingScript.run(area == null
                        ? net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.areas.Areas.EDGEVILLE_RANGE
                        : area);
            }
            case CRAFTING:
            {
                CraftingLevels level = resolveSingleSkillTarget(KspTrainSingleSkillTask.CRAFTING).getValue(CraftingLevels.class);
                return craftingScript.run(level == null ? CraftingLevels.LEATHER_GLOVES : level, level == null);
            }
            case MELEE:
            {
                CombatAreas area = resolveSingleSkillTarget(KspTrainSingleSkillTask.MELEE).getValue(CombatAreas.class);
                return area == null ? meleeScript.run() : meleeScript.run(area);
            }
            case GE_SELL:
                return sellScript.run(GEArea.GRAND_EXCHANGE);
            case GE_BUY:
                return buyScript.run(GEArea.GRAND_EXCHANGE);
            case SMITHING:
            {
                SmithLevels level = resolveSingleSkillTarget(KspTrainSingleSkillTask.SMITHING).getValue(SmithLevels.class);
                return smithScript.run(SmithArea.SMITH_AREA_VARROCK_WEST_ANVIL, level);
            }
            case SMELTING:
            {
                BarLevels bar = resolveSingleSkillTarget(KspTrainSingleSkillTask.SMELTING).getValue(BarLevels.class);
                return smeltScript.run(
                        SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE,
                        bar == null ? resolveSmeltingFallbackBar() : bar,
                        bar == null);
            }
            default:
                return false;
        }
    }

    private boolean isReadyAfterLoginHandoff()
    {
        if (autoLoginScript != null && autoLoginScript.isActive())
        {
            debugLoginHandoffWait("autologin-helper-active");
            return false;
        }

        if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null)
        {
            debugLoginHandoffWait("local-player-null");
            return false;
        }

        if (getSafePlayerLocation() == null)
        {
            debugLoginHandoffWait("world-location-null");
            return false;
        }

        return true;
    }

    private void debugLoginHandoffWait(String reason)
    {
        long now = System.currentTimeMillis();
        if (now - lastLoginHandoffLogAt < 3_000)
        {
            return;
        }

        lastLoginHandoffLogAt = now;
        debug("Waiting after login before starting task | reason={} autoLoginState={} player={} taskStarted={} currentTask={}",
                reason,
                autoLoginScript == null ? "none" : autoLoginScript.getState(),
                getSafePlayerLocation(),
                taskStarted,
                currentTask);
    }

    private void handleTutorialIslandPriority()
    {
        if (!TutorialIslandScript.isOnTutorialIsland())
        {
            return;
        }

        if (currentTask != BuilderTask.TUTORIAL_ISLAND)
        {
            stopCurrentTaskScript();
            currentTask = BuilderTask.TUTORIAL_ISLAND;
            taskStarted = false;
            pendingTask = null;
            pendingRandomTaskSelection = false;
            awaitingNextActivityStart = false;
            awaitingActivitySwitchTimerStart = false;
            nextActivitySwitchAtMillis = -1L;
            debug("Detected Tutorial Island; switching to tutorial task");
        }

    }

    private BuilderTask resolveStartingTask()
    {
        if (TutorialIslandScript.isOnTutorialIsland())
        {
            return BuilderTask.TUTORIAL_ISLAND;
        }

        BuilderTask singleSkillTask = resolveSingleSkillTask();
        if (singleSkillTask != null)
        {
            return singleSkillTask;
        }

        // Startup must be instantaneous: do not scan bank-backed resource state
        // across every task before assigning currentTask. Pick an eligible weighted
        // task first; the normal cycle validates only that task's resources and
        // performs recovery/switching if needed.
        return getRandomTaskExcluding(null);
    }

    private void applySingleSkillOverride()
    {
        if (!isSingleSkillTaskForced())
        {
            return;
        }

        BuilderTask singleSkillTask = resolveSingleSkillTask();
        if (singleSkillTask == null || currentTask == singleSkillTask)
        {
            return;
        }

        if (!stopCurrentTaskForHandoff(singleSkillTask))
        {
            return;
        }

        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to apply single skill override; still preparing bank handoff to {}", singleSkillTask);
            return;
        }

        if (!ensureInventoryTabOpenForTaskSelection())
        {
            debug("Waiting to apply single skill override; inventory tab is not open");
            return;
        }

        pendingTask = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = false;
        nextActivitySwitchAtMillis = -1L;
        currentTask = singleSkillTask;
        debug("Single skill override switched task to {}", currentTask);
    }

    private boolean auditSingleSkillResourcesIfNeeded(BuilderTask forcedTask)
    {
        if (forcedTask == BuilderTask.RUNE_ESSENCE && essenceMining.isInEssenceMine())
        {
            auditedSingleSkillTask = forcedTask;
            clearPendingSingleSkillAudit();
            return false;
        }

        if (forcedTask == null)
        {
            clearSingleSkillResourceAudit();
            return false;
        }

        if (forcedTask == BuilderTask.TUTORIAL_ISLAND
                || forcedTask == BuilderTask.ROMEO_AND_JULIET
                || forcedTask == BuilderTask.GE_BUY
                || auditedSingleSkillTask == forcedTask)
        {
            return false;
        }

        if (pendingSingleSkillAuditTask != forcedTask)
        {
            stopCurrentTaskScript();
            taskStarted = false;
            pendingSingleSkillAuditTask = forcedTask;
            singleSkillAuditBankWasOpen = Rs2Bank.isOpen();
            singleSkillAuditBankEpoch = Rs2Bank.getBankLiveEpoch();
            Microbot.status = "Checking bank for " + forcedTask;
        }

        if (!ensureTaskSwitchBankOpen())
        {
            Microbot.status = "Walking to bank for " + forcedTask;
            return true;
        }

        if (!Rs2Bank.verifyBankMirrorAfterOpen(singleSkillAuditBankWasOpen, singleSkillAuditBankEpoch))
        {
            Microbot.status = "Syncing bank for " + forcedTask;
            return true;
        }

        boolean ready = hasResourcesForTask(forcedTask);
        auditedSingleSkillTask = forcedTask;
        clearPendingSingleSkillAudit();

        if (!ready)
        {
            startSingleSkillResourceRecovery(forcedTask);
            return true;
        }

        Rs2Bank.closeBank();
        Microbot.status = "Resources ready for " + forcedTask;
        return true;
    }

    private void clearPendingSingleSkillAudit()
    {
        pendingSingleSkillAuditTask = null;
        singleSkillAuditBankEpoch = -1;
        singleSkillAuditBankWasOpen = false;
    }

    private void clearSingleSkillResourceAudit()
    {
        auditedSingleSkillTask = null;
        pendingSingleSkillAuditTask = null;
        singleSkillAuditBankEpoch = -1;
        singleSkillAuditBankWasOpen = false;
    }

    private boolean handleSingleSkillResourceRecovery(BuilderTask forcedTask)
    {
        if (singleSkillRecoveryTask == null)
        {
            return false;
        }

        if (forcedTask == null || forcedTask != singleSkillRecoveryTask)
        {
            debug("Cancelling Single Skill resource recovery | previousTarget={} newTarget={}",
                    singleSkillRecoveryTask,
                    forcedTask);
            buyScript.shutdown();
            taskStarted = false;
            singleSkillRecoveryTask = null;
            clearSingleSkillResourceAudit();
            return false;
        }

        if (currentTask != BuilderTask.GE_BUY)
        {
            currentTask = BuilderTask.GE_BUY;
            taskStarted = false;
        }

        if (!taskStarted || !buyScript.isComplete())
        {
            Microbot.status = "Getting resources for " + forcedTask;
            return true;
        }

        boolean requirementsAvailable = hasResourcesForTask(forcedTask);
        buyScript.shutdown();
        taskStarted = false;
        currentTask = forcedTask;
        singleSkillRecoveryTask = null;
        auditedSingleSkillTask = forcedTask;

        if (requirementsAvailable)
        {
            Microbot.status = "Resources ready for " + forcedTask;
            debug("Single Skill resource recovery completed | target={}", forcedTask);
        }
        else
        {
            Microbot.status = "Missing resources for " + forcedTask;
            debug("Single Skill resource recovery finished but requirements remain unavailable | target={}",
                    forcedTask);
        }
        return true;
    }

    private void startSingleSkillResourceRecovery(BuilderTask forcedTask)
    {
        SimpleQuestScript questScript = newQuestScript(forcedTask);
        if (questScript != null)
        {
            Microbot.status = "Not enough GP for " + forcedTask + " inputs (need " + questScript.getMissingRequirementCost() + ")";
            return;
        }
        if (forcedTask == null
                || forcedTask == BuilderTask.GE_BUY
                || forcedTask == BuilderTask.ROMEO_AND_JULIET
                || forcedTask == BuilderTask.RUNE_MYSTERIES
                || (forcedTask == BuilderTask.RUNE_ESSENCE && !isRuneMysteriesComplete()))
        {
            return;
        }

        stopCurrentTaskScript();
        taskStarted = false;
        singleSkillRecoveryTask = forcedTask;
        currentTask = BuilderTask.GE_BUY;
        clearActivitySwitchTimerState();
        Microbot.status = "Getting resources for " + forcedTask;
        debug("Single Skill task {} is missing requirements; starting bank/GE recovery", forcedTask);
    }

    private BuilderTask resolveSingleSkillTask()
    {
        if (config == null) return null;
        if (config.runSingleQuest()) return builderTask(config.singleQuestTask());
        return config.trainSingleSkill() ? builderTask(config.singleSkillTask()) : null;
    }

    private BuilderTask builderTask(Enum<?> task)
    {
        if (task == null) return null;
        try
        {
            return BuilderTask.valueOf(task.name());
        }
        catch (IllegalArgumentException ignored)
        {
            return null;
        }
    }

    private KspSingleSkillTarget resolveSingleSkillTarget(KspTrainSingleSkillTask task)
    {
        if (config == null || task == null || !config.trainSingleSkill())
        {
            return KspSingleSkillTarget.AUTOMATIC;
        }

        switch (task)
        {
            case MINING:
                return config.singleSkillMiningTarget().getTarget();
            case WOODCUTTING:
                return config.singleSkillWoodcuttingTarget().getTarget();
            case FISHING:
                return config.singleSkillFishingTarget().getTarget();
            case COOKING:
                return config.singleSkillCookingTarget().getTarget();
            case CRAFTING:
                return config.singleSkillCraftingTarget().getTarget();
            case MELEE:
                return config.singleSkillMeleeTarget().getTarget();
            case SMITHING:
                return config.singleSkillSmithingTarget().getTarget();
            case SMELTING:
                return config.singleSkillSmeltingTarget().getTarget();
            default:
                return KspSingleSkillTarget.AUTOMATIC;
        }
    }

    private void refreshSingleSkillTargetIfChanged(BuilderTask forcedTask)
    {
        if (forcedTask == null)
        {
            activeSingleSkillSelection = null;
            return;
        }

        String selection;
        if (config.runSingleQuest())
        {
            selection = "QUEST:" + config.singleQuestTask().name();
        }
        else
        {
            KspTrainSingleSkillTask selectedTask = config.singleSkillTask();
            KspSingleSkillTarget target = resolveSingleSkillTarget(selectedTask);
            selection = "SKILL:" + selectedTask.name() + ":" + target.name();
        }
        if (activeSingleSkillSelection == null)
        {
            activeSingleSkillSelection = selection;
            return;
        }

        if (activeSingleSkillSelection.equals(selection))
        {
            return;
        }

        debug("Single Skill selection changed | previous={} selected={}",
                activeSingleSkillSelection,
                selection);
        stopCurrentTaskScript();
        buyScript.shutdown();
        taskStarted = false;
        singleSkillRecoveryTask = null;
        clearSingleSkillResourceAudit();
        activeSingleSkillSelection = selection;
    }

    private boolean isSingleSkillTaskForced() { return resolveSingleSkillTask() != null; }

    private boolean isSingleSkillTargetRequired(BuilderTask task)
    {
        return task == BuilderTask.MINING
                || task == BuilderTask.WOODCUTTING
                || task == BuilderTask.FISHING
                || task == BuilderTask.COOKING
                || task == BuilderTask.CRAFTING
                || task == BuilderTask.MELEE
                || task == BuilderTask.SMITHING
                || task == BuilderTask.SMELTING;
    }

    private boolean isActivitySwitchRandomizationEnabled()
    {
        return config != null
                && config.enableActivitySwitchRandomization()
                && !isSingleSkillTaskForced();
    }

    private boolean canUseActivitySwitchTimer()
    {
        return isActivitySwitchRandomizationEnabled()
                && currentTask != null
                && currentTask != BuilderTask.TUTORIAL_ISLAND
                && currentTask != BuilderTask.RUNE_MYSTERIES
                && newQuestScript(currentTask) == null
                && currentTask != BuilderTask.STRONGHOLD_OF_SECURITY;
    }

    private void clearActivitySwitchTimerState()
    {
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = false;
        nextActivitySwitchAtMillis = -1L;
        pausedActivitySwitchRemainingMillis = -1L;
        activitySwitchTimerPaused = false;
    }

    private Areas resolveMiningStartArea() { return Areas.TIN_COPPER_VARROCK_EAST; }

    private TreeAreas resolveWoodcuttingStartArea() { return TreeAreas.REGULAR_TREE_VARROCK_WEST; }

    private net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas resolveFishingStartArea()
    {
        return net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas.SHRIMP_ANCHOVIES;
    }

    private BarLevels resolveSmeltingFallbackBar() { return BarLevels.BRONZE; }

    private boolean hasResourcesForTask(BuilderTask task)
    {
        if (task == null || isTaskTemporarilyDisabled(task)) return false;
        SimpleQuestScript questScript = newQuestScript(task);
        if (questScript != null)
            return !questScript.isComplete() && getAvailableCoins() >= questScript.getMissingRequirementCost();

        switch (task)
        {
            case TUTORIAL_ISLAND: return TutorialIslandScript.isOnTutorialIsland();
            case COOKS_ASSISTANT: return !cooksScript.isComplete() && hasEnoughCoinsForCooksAssistant();
            case GOBLIN_DIPLOMACY: return !gobScript.isComplete() && hasEnoughCoinsForGoblinDiplomacy();
            case ROMEO_AND_JULIET:
            case RUNE_MYSTERIES:
            case STRONGHOLD_OF_SECURITY:
                return !isOneTimeTaskCompleted(task);
            case RUNE_ESSENCE:
                return isRuneMysteriesComplete() && hasAnyToolAvailable(Buy.PICKAXE_NAMES);
            case MINING: return hasAnyToolAvailable(Buy.PICKAXE_NAMES);
            case WOODCUTTING: return hasAnyToolAvailable(Buy.AXE_NAMES);
            case FIREMAKING: return hasAnyFiremakingResourcesAvailable();
            case FISHING: return hasAnyFishingResourcesAvailable();
            case COOKING: return hasAnyCookingResourcesAvailable();
            case CRAFTING: return hasAnyCraftingResourcesAvailable();
            case MELEE: return hasAnyMeleeResourcesAvailable();
            case GE_SELL: return hasAnyGeSellResourcesAvailable();
            case GE_BUY: return hasAnyGeBuyResourcesAvailable();
            case SMITHING: return hasAnySmithingResourcesAvailable();
            case SMELTING: return hasAnySmeltingResourcesAvailable();
            default: return false;
        }
    }

    private boolean isTaskTemporarilyDisabled(BuilderTask task) { return task == BuilderTask.STRONGHOLD_OF_SECURITY; }

    private boolean isOneTimeTaskCompleted(BuilderTask task)
    {
        if (task == null) return false;
        SimpleQuestScript questScript = newQuestScript(task);
        if (questScript != null) return questScript.isComplete();

        KspAccountTaskCache.OneTimeTask oneTimeTask;
        try
        {
            oneTimeTask = KspAccountTaskCache.OneTimeTask.valueOf(task.name());
        }
        catch (IllegalArgumentException ignored)
        {
            return false;
        }

        long accountHash = getCurrentAccountHash();
        return accountHash == 0L || accountTaskCache.isCompleted(accountHash, oneTimeTask);
    }

    private boolean isRuneMysteriesComplete() { return Rs2Player.getQuestState(Quest.RUNE_MYSTERIES) == QuestState.FINISHED; }

    private boolean hasEnoughCoinsForCooksAssistant()
    {
        int missingItems = 0;
        for (Items item : Items.values())
        {
            if (countOwnedQuestItem(item.getDisplayName(), item.getItemId()) <= 0)
            {
                missingItems++;
            }
        }

        return hasCoinsForQuestBuys("Cook's Assistant", missingItems);
    }

    private boolean hasEnoughCoinsForGoblinDiplomacy()
    {
        int missingPlainMail = Math.max(0, getRequiredPlainGoblinMailCount() - countOwnedQuestItem(GobReqs.GOBLIN_MAIL.getDisplayName(), GobReqs.GOBLIN_MAIL.getItemId()));
        int missingBlueDye = countOwnedQuestItem(BLUE_GOBLIN_MAIL) > 0
                || countOwnedQuestItem(GobReqs.BLUE_DYE.getDisplayName(), GobReqs.BLUE_DYE.getItemId()) > 0 ? 0 : 1;
        int missingOrangeDye = countOwnedQuestItem(ORANGE_GOBLIN_MAIL) > 0
                || countOwnedQuestItem(GobReqs.ORANGE_DYE.getDisplayName(), GobReqs.ORANGE_DYE.getItemId()) > 0 ? 0 : 1;

        return hasCoinsForQuestBuys("Goblin Diplomacy", missingPlainMail + missingBlueDye + missingOrangeDye);
    }

    private int getRequiredPlainGoblinMailCount()
    {
        int requiredMail = 1;

        if (countOwnedQuestItem(BLUE_GOBLIN_MAIL) <= 0)
        {
            requiredMail++;
        }

        if (countOwnedQuestItem(ORANGE_GOBLIN_MAIL) <= 0)
        {
            requiredMail++;
        }

        return requiredMail;
    }

    private boolean hasCoinsForQuestBuys(String questName, int missingItems)
    {
        if (missingItems <= 0)
        {
            return true;
        }

        long requiredCoins = (long) missingItems * MIN_QUEST_BUY_PRICE;
        long availableCoins = getAvailableCoins();
        boolean enoughCoins = availableCoins >= requiredCoins;
        if (!enoughCoins)
        {
            debug("Skipping {} due to insufficient GP | missingItems={} availableCoins={} requiredCoins={}",
                    questName,
                    missingItems,
                    availableCoins,
                    requiredCoins);
        }
        return enoughCoins;
    }

    private int countOwnedQuestItem(String itemName)
    {
        return stored(itemName);
    }

    private int countOwnedQuestItem(String itemName, int itemId)
    {
        return Rs2Inventory.itemQuantity(itemId)
                + Math.max(0, Rs2Bank.count(itemName));
    }

    private long getAvailableCoins()
    {
        return Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID))
                + Math.max(0L, Rs2Bank.count(COINS));
    }

    private boolean hasAnyToolAvailable(String[] toolNames)
    {
        for (String name : toolNames) if (hasAnywhere(name)) return true;
        return false;
    }

    private boolean hasAnyMeleeResourcesAvailable()
    {
        if (!hasRequiredMeleeGearAvailable())
        {
            return false;
        }

        CombatAreas selectedArea = resolveSingleSkillTarget(KspTrainSingleSkillTask.MELEE)
                .getValue(CombatAreas.class);
        boolean trainingOnChickens;
        if (selectedArea != null)
        {
            trainingOnChickens = selectedArea == CombatAreas.CHICKENS;
        }
        else
        {
            int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
            int strengthLevel = Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
            int defenceLevel = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);
            trainingOnChickens = attackLevel < CHICKEN_TARGET_COMBAT_STAT_LEVEL
                    || strengthLevel < CHICKEN_TARGET_COMBAT_STAT_LEVEL
                    || defenceLevel < CHICKEN_TARGET_COMBAT_STAT_LEVEL;
        }

        if (trainingOnChickens)
        {
            return true;
        }

        int inventoryFood = Buy.getMeleeFoodCountInInventory();
        if (currentTask == BuilderTask.MELEE && taskStarted && inventoryFood > 0)
        {
            return true;
        }

        int availableFood = Buy.getMeleeFoodCountAvailable();
        boolean hasEnoughFood = availableFood >= Buy.MELEE_TARGET_FOOD_COUNT;
        if (!hasEnoughFood)
        {
            debug("Skipping Melee; non-chicken targets require at least {} Trout/Salmon | available={}",
                    Buy.MELEE_TARGET_FOOD_COUNT,
                    availableFood);
        }
        return hasEnoughFood;
    }

    private boolean hasRequiredMeleeGearAvailable()
    {
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        int defenceLevel = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);

        Buy.MeleeGearPlan gearPlan = Buy.buildMeleeGearPlan(
                attackLevel,
                defenceLevel,
                Rs2Player.getQuestState(Quest.DRAGON_SLAYER_I) == QuestState.FINISHED,
                this::hasMeleeItemAvailable);

        if (!gearPlan.getMissingItems().isEmpty())
        {
            debug("Skipping Melee; required gear is missing | missing={}", gearPlan.getMissingItems());
            return false;
        }

        return !gearPlan.getDesiredItems().isEmpty();
    }

    private boolean hasMeleeItemAvailable(String itemName)
    {
        return hasAnywhere(itemName);
    }

        private boolean hasAnySmeltingResourcesAvailable()
    {
        int level = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        for (int i = BarLevels.values().length - 1; i >= 0; i--)
        {
            BarLevels bar = BarLevels.values()[i];
            if (level < bar.getRequiredSmithingLevel()) continue;

            ReqOres req = ReqOres.valueOf(bar.name());
            if (stored(req.getPrimaryOreName()) < req.getPrimaryOreAmount()) continue;
            if (!req.hasSecondaryOre() || stored(req.getSecondaryOreName()) >= req.getSecondaryOreAmount()) return true;
        }
        return false;
    }

    private boolean hasAnyFiremakingResourcesAvailable()
    {
        if (!hasTinderboxAnywhere()) return false;

        int level = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        for (int i = LogsLvl.values().length - 1; i >= 0; i--)
        {
            LogsLvl logs = LogsLvl.values()[i];
            if (level < logs.getRequiredLevel()) continue;
            if (level >= LogsLvl.WILLOW_LOGS.getRequiredLevel() && logs != LogsLvl.WILLOW_LOGS) continue;
            if (level >= LogsLvl.OAK_LOGS.getRequiredLevel() && logs == LogsLvl.LOGS) continue;
            if (stored(logs.getDisplayName()) > 0) return true;
        }
        return false;
    }

    private boolean hasAnyFishingResourcesAvailable()
    {
        LevelReqs fish = LevelReqs.bestForFishingLevel(Microbot.getClient().getRealSkillLevel(Skill.FISHING));
        net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.needed.Inventory required =
                net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.needed.Inventory.valueOf(fish.name());
        for (String item : required.getRequiredItems()) if (stored(item) <= 0) return false;
        return true;
    }

    private boolean hasAnyCookingResourcesAvailable()
    {
        int level = Microbot.getClient().getRealSkillLevel(Skill.COOKING);
        for (CookLevels fish : CookLevels.values())
        {
            if (level >= fish.getRequiredLevel() && stored(fish.getRawItemName()) > 0) return true;
        }
        return false;
    }

    private boolean hasAnyCraftingResourcesAvailable()
    {
        CraftingLevels selectedLevel = resolveSingleSkillTarget(KspTrainSingleSkillTask.CRAFTING)
                .getValue(CraftingLevels.class);
        if (selectedLevel != null)
        {
            return hasCraftingRecipeResources(CraftInventory.valueOf(selectedLevel.name()));
        }

        int craftingLevel = Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
        for (int index = CraftingLevels.values().length - 1; index >= 0; index--)
        {
            CraftingLevels level = CraftingLevels.values()[index];
            if (craftingLevel >= level.getRequiredLevel()
                    && hasCraftingRecipeResources(CraftInventory.valueOf(level.name())))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasCraftingRecipeResources(CraftInventory recipe)
    {
        for (Ingredient ingredient : recipe.getIngredients())
        {
            if (stored(ingredient.getItemName()) < ingredient.getAmount()) return false;
        }
        return true;
    }

    private boolean handleUnexpectedEssenceMineRecovery()
    {
        if (currentTask == BuilderTask.RUNE_ESSENCE)
        {
            essenceMineRecoveryActive = false;
            return false;
        }

        if (!essenceMining.isInEssenceMine())
        {
            if (essenceMineRecoveryActive)
            {
                essenceMineRecoveryActive = false;
                Microbot.status = currentTask == null
                        ? "Essence mine exit complete"
                        : "Resuming " + currentTask;
                debug("Unexpected essence mine recovery complete | currentTask={}", currentTask);
            }
            return false;
        }

        if (!essenceMineRecoveryActive)
        {
            if (taskStarted && currentTask != null)
            {
                stopCurrentTaskScript();
                taskStarted = false;
            }
            KspWalkerGuard.clearActiveWalker("ksp_unexpected_essence_mine_recovery");
            essenceMineRecoveryActive = true;
            debug("Paused task to exit unexpected essence mine | currentTask={} player={}",
                    currentTask,
                    Rs2Player.getWorldLocation());
        }

        Microbot.status = "Leaving unexpected essence mine";
        essenceMining.recoverFromEssenceMine();
        return true;
    }

    private boolean hasAnySmithingResourcesAvailable()
    {
        if (!hasHammerWithRequiredIdAnywhere())
        {
            return false;
        }

        SmithLevels selectedLevel = resolveSingleSkillTarget(KspTrainSingleSkillTask.SMITHING)
                .getValue(SmithLevels.class);
        if (selectedLevel != null)
        {
            return hasSmithingRecipeResources(selectedLevel);
        }

        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        for (int i = SmithLevels.values().length - 1; i >= 0; i--)
        {
            SmithLevels level = SmithLevels.values()[i];
            if (smithingLevel < level.getRequiredLevel())
            {
                continue;
            }

            if (hasSmithingRecipeResources(level))
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasSmithingRecipeResources(SmithLevels level)
    {
        int skill = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        if (skill < level.getRequiredLevel()) return false;

        SmithRecipe recipe = SmithRecipe.valueOf(level.name());
        String bar = recipe.getDisplayName().split(" ")[0] + " bar";
        boolean ready = stored(bar) >= recipe.getBarRequirement();
        if (!ready) debug("Skipping Smithing recipe; insufficient {} | recipe={}", bar, recipe.getDisplayName());
        return ready;
    }

    private boolean hasAnyGeSellResourcesAvailable()
    {
        return sellScript.hasSellListItemsAvailable() || hasAnyOutdatedToolAvailable();
    }

    private boolean hasAnyGeBuyResourcesAvailable() { return Buy.hasAnyGeBuyRequirementMissing() && buyScript.canAffordMissingBuys(); }

    private boolean hasAnyOutdatedToolAvailable()
    {
        return hasOutdatedTool(Buy.PICKAXE_NAMES, resolveDesiredPickaxeForSellingTask())
                || hasOutdatedTool(Buy.AXE_NAMES, resolveDesiredAxeForSellingTask());
    }

    private boolean hasOutdatedTool(String[] tools, String desired)
    {
        for (String tool : tools)
        {
            if (!tool.equalsIgnoreCase(desired) && hasAnywhere(tool)) return true;
        }
        return false;
    }

    private int stored(String itemName)
    {
        return itemName == null ? 0 : Rs2Inventory.count(itemName, true) + Math.max(0, Rs2Bank.count(itemName));
    }

    private boolean hasAnywhere(String itemName)
    {
        return itemName != null && (Rs2Equipment.isWearing(itemName) || stored(itemName) > 0);
    }

    private String resolveDesiredPickaxeForSellingTask() { return Buy.resolveDesiredPickaxeNameForGear(); }

    private String resolveDesiredAxeForSellingTask() { return Buy.resolveDesiredAxeNameForGear(); }

    private boolean hasHammerWithRequiredIdAnywhere() { return Buy.hasHammerAnywhere(); }

    private boolean hasTinderboxAnywhere() { return Buy.hasTinderboxAnywhere(); }

    private boolean switchToTaskWithResources()
    {
        if (preselectedHandoffTask == null)
        {
            preselectedHandoffTask = getRandomTaskWithResourcesExcluding(currentTask);
            if (preselectedHandoffTask == null)
            {
                debug("Current task {} has no resources and no alternative task is available", currentTask);
                return false;
            }
            debug("Preselected replacement task {} before bank handoff | currentTask={}",
                    preselectedHandoffTask, currentTask);
        }

        if (!stopCurrentTaskForHandoff(preselectedHandoffTask))
        {
            return false;
        }

        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to switch task; bank handoff in progress | currentTask={} nextTask={}",
                    currentTask, preselectedHandoffTask);
            return false;
        }

        BuilderTask nextTask = preselectedHandoffTask;
        preselectedHandoffTask = null;
        pendingTask = null;
        singleSkillRecoveryTask = null;
        clearSingleSkillResourceAudit();
        activeSingleSkillSelection = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        debug("Switching task from {} to {} because resources are unavailable", currentTask, nextTask);
        currentTask = nextTask;
        awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
        nextActivitySwitchAtMillis = -1L;
        return true;
    }

    private boolean switchToRandomTaskAfterBank(BuilderTask excludedTask)
    {
        if (preselectedHandoffTask == null)
        {
            preselectedHandoffTask = getRandomTaskWithResourcesExcluding(excludedTask);
            if (preselectedHandoffTask == null)
            {
                preselectedHandoffTask = getRandomTaskExcluding(excludedTask);
            }

            if (preselectedHandoffTask == null)
            {
                debug("No next task could be selected | excluded={}", excludedTask);
                return false;
            }

            debug("Preselected next task {} before bank handoff | excluded={}",
                    preselectedHandoffTask, excludedTask);
        }

        if (!stopCurrentTaskForHandoff(preselectedHandoffTask))
        {
            return false;
        }

        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to select next task; bank handoff in progress | currentTask={} nextTask={}",
                    currentTask, preselectedHandoffTask);
            return false;
        }

        BuilderTask nextTask = preselectedHandoffTask;
        preselectedHandoffTask = null;
        pendingTask = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        currentTask = nextTask;
        awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
        nextActivitySwitchAtMillis = -1L;
        debug("Selected next task after bank cleanup | currentTask={}", currentTask);
        return true;
    }

    private BuilderTask getRandomTaskWithResourcesExcluding(BuilderTask excluded)
    {
        if (TutorialIslandScript.isOnTutorialIsland())
        {
            return excluded == BuilderTask.TUTORIAL_ISLAND ? null : BuilderTask.TUTORIAL_ISLAND;
        }

        return selectTaskWithSupportFallback(excluded, true, false);
    }

    private BuilderTask selectTaskWithSupportFallback(
            BuilderTask excluded,
            boolean requireResources,
            boolean includeUnavailable)
    {
        long regularMask = 0L;
        long supportMask = 0L;
        int regularWeight = 0;
        int supportWeight = 0;
        int[] weights = new int[BuilderTask.values().length];

        for (BuilderTask task : BuilderTask.values())
        {
            if (task == excluded || task == BuilderTask.TUTORIAL_ISLAND) continue;
            if (!includeUnavailable && (isTaskTemporarilyDisabled(task) || isOneTimeTaskCompleted(task))) continue;
            if (requireResources && !hasResourcesForTask(task)) continue;

            int weight = getTaskSelectionWeight(task);
            weights[task.ordinal()] = weight;
            if (isSupportTask(task))
            {
                supportMask |= 1L << task.ordinal();
                supportWeight += weight;
            }
            else
            {
                regularMask |= 1L << task.ordinal();
                regularWeight += weight;
            }
        }

        if (regularWeight > 0) return pickWeightedTask(regularMask, regularWeight, weights);
        return supportWeight > 0 ? pickWeightedTask(supportMask, supportWeight, weights) : null;
    }

    private BuilderTask pickWeightedTask(long mask, int totalWeight, int[] weights)
    {
        if (totalWeight <= 0) return null;

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (BuilderTask task : BuilderTask.values())
        {
            if ((mask & (1L << task.ordinal())) == 0L) continue;
            roll -= weights[task.ordinal()];
            if (roll < 0) return task;
        }
        return null;
    }

    private boolean isSupportTask(BuilderTask task) { return task == BuilderTask.GE_BUY; }

    private boolean handlePendingMeleeSellHandoff()
    {
        if (isSingleSkillTaskForced()
                || currentTask != BuilderTask.MELEE
                || !meleeScript.hasPendingSellHandoff())
        {
            return false;
        }

        if (!hasAnyGeSellResourcesAvailable())
        {
            meleeScript.clearPendingSellHandoff();
            return false;
        }

        Microbot.status = "Preparing GE sell";
        if (!switchTask(BuilderTask.GE_SELL)) return true;

        meleeScript.clearPendingSellHandoff();
        taskStarted = false;
        clearActivitySwitchTimerState();
        awaitingNextActivityStart = true;
        return true;
    }

    private void stopCurrentTaskScript()
    {
        Script script = scriptFor(currentTask);
        if (script != null) script.shutdown();
    }

    private boolean stopCurrentTaskForHandoff(BuilderTask nextTask)
    {
        BuilderTask outgoing = currentTask;
        stopCurrentTaskScript();
        taskStarted = false;
        if (!isTaskScriptRunning(outgoing)) return true;

        debug("Waiting for outgoing task to stop before handoff | currentTask={} nextTask={}", outgoing, nextTask);
        return false;
    }

    private boolean isTaskScriptRunning(BuilderTask task)
    {
        Script script = scriptFor(task);
        return script != null && script.isRunning();
    }

    private Script scriptFor(BuilderTask task)
    {
        if (task == null) return null;
        switch (task)
        {
            case TUTORIAL_ISLAND: return tutorialIslandScript;
            case COOKS_ASSISTANT: return cooksScript;
            case GOBLIN_DIPLOMACY: return gobScript;
            case ROMEO_AND_JULIET: return romeoScript;
            case RUNE_MYSTERIES: return runeMystScript;
            case SHEEP_SHEARER: return sheepScript;
            case X_MARKS_THE_SPOT: return xMarksScript;
            case THE_RESTLESS_GHOST: return ghostScript;
            case IMP_CATCHER: return impScript;
            case RUNE_ESSENCE: return essenceMining;
            case STRONGHOLD_OF_SECURITY: return strongholdScript;
            case MINING: return miningScript;
            case WOODCUTTING: return woodCuttingScript;
            case FIREMAKING: return fireMakingScript;
            case FISHING: return fishingScript;
            case COOKING: return cookingScript;
            case CRAFTING: return craftingScript;
            case MELEE: return meleeScript;
            case GE_SELL: return sellScript;
            case GE_BUY: return buyScript;
            case SMITHING: return smithScript;
            case SMELTING: return smeltScript;
            default: return null;
        }
    }

    private SimpleQuestScript newQuestScript(BuilderTask task)
    {
        if (task == null) return null;
        switch (task)
        {
            case SHEEP_SHEARER: return sheepScript;
            case X_MARKS_THE_SPOT: return xMarksScript;
            case THE_RESTLESS_GHOST: return ghostScript;
            case IMP_CATCHER: return impScript;
            default: return null;
        }
    }

    private void stopAllTaskScriptsExcept(BuilderTask keep)
    {
        for (BuilderTask task : BuilderTask.values())
        {
            if (task == keep) continue;
            Script script = scriptFor(task);
            if (script != null) script.shutdown();
        }
    }

    private boolean switchTask(BuilderTask nextTask)
    {
        if (!stopCurrentTaskForHandoff(nextTask) || !prepareForTaskSwitchAtBank()) return false;
        currentTask = nextTask;
        debug("Switching task to {}", currentTask);
        return true;
    }

    private BuilderTask getRandomTaskExcluding(BuilderTask excluded)
    {
        if (TutorialIslandScript.isOnTutorialIsland()) return BuilderTask.TUTORIAL_ISLAND;

        BuilderTask task = selectTask(excluded, false, false, false);
        return task != null ? task : selectTask(excluded, false, true, true);
    }

    private BuilderTask selectTask(BuilderTask excluded, boolean requireResources, boolean includeSupport, boolean includeUnavailable)
    {
        long mask = 0L;
        int totalWeight = 0;
        int[] weights = new int[BuilderTask.values().length];

        for (BuilderTask task : BuilderTask.values())
        {
            if (!isTaskCandidate(task, excluded, requireResources, includeSupport, includeUnavailable)) continue;

            int weight = getTaskSelectionWeight(task);
            weights[task.ordinal()] = weight;
            mask |= 1L << task.ordinal();
            totalWeight += weight;
        }

        return pickWeightedTask(mask, totalWeight, weights);
    }

    private boolean isTaskCandidate(
            BuilderTask task,
            BuilderTask excluded,
            boolean requireResources,
            boolean includeSupport,
            boolean includeUnavailable)
    {
        if (task == excluded || task == BuilderTask.TUTORIAL_ISLAND) return false;
        if (!includeUnavailable && (isTaskTemporarilyDisabled(task) || isOneTimeTaskCompleted(task))) return false;
        if (!includeSupport && isSupportTask(task)) return false;
        return !requireResources || hasResourcesForTask(task);
    }

    private int getTaskSelectionWeight(BuilderTask task)
    {
        if (task == null) return 0;
        int level = getTaskSkillLevel(task);
        return Math.max(1, task.getSelectionWeight() + (level > 0 ? Math.max(0, 40 - level) / 4 : 0));
    }

    private int getTaskSkillLevel(BuilderTask task)
    {
        if (Microbot.getClient() == null) return 0;

        switch (task)
        {
            case MINING:
            case RUNE_ESSENCE: return Microbot.getClient().getRealSkillLevel(Skill.MINING);
            case WOODCUTTING: return Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
            case FIREMAKING: return Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
            case FISHING: return Microbot.getClient().getRealSkillLevel(Skill.FISHING);
            case COOKING: return Microbot.getClient().getRealSkillLevel(Skill.COOKING);
            case CRAFTING: return Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
            case MELEE:
                return Math.min(
                        Microbot.getClient().getRealSkillLevel(Skill.ATTACK),
                        Math.min(
                                Microbot.getClient().getRealSkillLevel(Skill.STRENGTH),
                                Microbot.getClient().getRealSkillLevel(Skill.DEFENCE)));
            case SMITHING:
            case SMELTING: return Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
            default: return 0;
        }
    }

    private boolean ensureInventoryTabOpenForTaskSelection()
    {
        if (!Microbot.isLoggedIn()) return false;
        if (Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY) return true;
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        return false;
    }

    private boolean prepareForTaskSwitchAtBank()
    {
        if (postTutorialBankCameraPending && !TutorialIslandScript.isOnTutorialIsland())
        {
            postTutorialBankCameraPending = !setPostTutorialBankCamera();
        }

        if (!ensureTaskSwitchBankOpen() || !depositInventoryForTaskSwitch()) return false;
        if (!unequipGatheringTools()) return false;
        if (!ensureTaskSwitchBankOpen() || !depositInventoryForTaskSwitch()) return false;

        taskSwitchBankLocation = null;
        return true;
    }

    private boolean ensureTaskSwitchBankOpen()
    {
        if (taskSwitchBankResetPending)
        {
            if (Rs2Bank.isOpen())
            {
                if (taskSwitchActionReady())
                {
                    Rs2Bank.closeBank();
                    markTaskSwitchAction();
                }
                return false;
            }
            taskSwitchBankResetPending = false;
        }

        if (Rs2Bank.isOpen()) return true;
        if (Rs2Player.isMoving() || !taskSwitchActionReady()) return false;

        boolean acted = tryOpenTaskSwitchBank("direct-open");
        if (!acted) acted = tryWalkToTaskSwitchBank();
        if (acted) markTaskSwitchAction();
        return false;
    }

    private boolean tryOpenTaskSwitchBank(String source)
    {
        try
        {
            return Rs2Bank.openBank();
        }
        catch (RuntimeException ex)
        {
            debug("Task switch bank open failed | source={} message={}", source, ex.getMessage());
            return false;
        }
    }

    private boolean tryWalkToTaskSwitchBank()
    {
        try
        {
            BankLocation bankLocation = resolveTaskSwitchBankLocation();
            return bankLocation != null && Rs2Bank.walkToBankAndUseBank(bankLocation);
        }
        catch (RuntimeException ex)
        {
            debug("Task switch bank walk failed | message={}", ex.getMessage());
            return false;
        }
    }

    private BankLocation resolveTaskSwitchBankLocation()
    {
        if (taskSwitchBankLocation != null)
        {
            return taskSwitchBankLocation;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (isInDraynorManorCorridor(playerLocation))
        {
            taskSwitchBankLocation = BankLocation.DRAYNOR_VILLAGE;
            debug("Pinned task switch bank to {} in Draynor Manor corridor | player={}",
                    taskSwitchBankLocation, playerLocation);
            return taskSwitchBankLocation;
        }

        taskSwitchBankLocation = Rs2Bank.getNearestBank();
        debug("Pinned nearest task switch bank | bank={} player={}",
                taskSwitchBankLocation, playerLocation);
        return taskSwitchBankLocation;
    }

    private boolean isInDraynorManorCorridor(WorldPoint location)
    {
        return location != null
                && location.getPlane() == 0
                && location.getX() >= DRAYNOR_CORRIDOR_MIN_X
                && location.getX() <= DRAYNOR_CORRIDOR_MAX_X
                && location.getY() >= DRAYNOR_CORRIDOR_MIN_Y
                && location.getY() <= DRAYNOR_CORRIDOR_MAX_Y;
    }

    private boolean setPostTutorialBankCamera()
    {
        if (isDialogueOpen())
        {
            debug("Waiting to set post-tutorial camera; dialogue is still open");
            return false;
        }

        applyCameraAngle(
                POST_TUTORIAL_BANK_CAMERA_PITCH,
                POST_TUTORIAL_BANK_CAMERA_YAW);
        applyCameraScale(POST_TUTORIAL_BANK_CAMERA_SCALE);
        // Re-apply pitch/yaw after zoom calibration because the client can nudge
        // the target while the camera scale is being changed.
        applyCameraAngle(
                POST_TUTORIAL_BANK_CAMERA_PITCH,
                POST_TUTORIAL_BANK_CAMERA_YAW);

        debug(
                "Set exact post-tutorial bank camera | targetPitch={} actualPitch={} targetYaw={} actualYaw={} targetScale={} actualZoom={} actualScale={}",
                POST_TUTORIAL_BANK_CAMERA_PITCH,
                Rs2Camera.getPitch(),
                POST_TUTORIAL_BANK_CAMERA_YAW,
                Rs2Camera.getYaw(),
                POST_TUTORIAL_BANK_CAMERA_SCALE,
                getZoom(),
                getCameraScale()
        );
        return true;
    }

    private void applyCameraAngle(int pitch, int yaw)
    {
        Microbot.getClientThread().invoke(() ->
        {
            Microbot.getClient().setCameraPitchRelaxerEnabled(true);
            Microbot.getClient().setCameraPitchTarget(pitch);
            Microbot.getClient().setCameraYawTarget(yaw);
        });
    }

    private void applyCameraScale(int targetScale)
    {
        if (targetScale <= 0)
        {
            return;
        }

        int bestZoom = clampCameraZoom(getZoom());
        int bestScale = getCameraScale();
        int bestDistance = cameraScaleDistance(bestScale, targetScale);

        int seedScale = sampleCameraScaleAtZoom(POST_TUTORIAL_BANK_CAMERA_RAW_ZOOM);
        int seedDistance = cameraScaleDistance(seedScale, targetScale);
        if (seedDistance < bestDistance)
        {
            bestZoom = clampCameraZoom(POST_TUTORIAL_BANK_CAMERA_RAW_ZOOM);
            bestScale = seedScale;
            bestDistance = seedDistance;
        }

        int low = CAMERA_ZOOM_MIN;
        int high = Math.max(bestZoom, POST_TUTORIAL_BANK_CAMERA_RAW_ZOOM);
        if (bestScale < targetScale)
        {
            high = CAMERA_ZOOM_MAX;
        }

        for (int i = 0; i < 12 && low <= high && bestDistance > CAMERA_SCALE_TOLERANCE; i++)
        {
            int mid = low + (high - low) / 2;
            int scale = sampleCameraScaleAtZoom(mid);
            int distance = cameraScaleDistance(scale, targetScale);

            if (distance < bestDistance)
            {
                bestZoom = clampCameraZoom(mid);
                bestScale = scale;
                bestDistance = distance;
            }

            if (scale < targetScale)
            {
                low = mid + 1;
            }
            else
            {
                high = mid - 1;
            }
        }

        setRawCameraZoom(bestZoom);
        sleep(CAMERA_SCALE_SAMPLE_DELAY_MS);
        debug(
                "Applied calibrated camera scale | targetScale={} chosenRawZoom={} chosenScale={} finalScale={} finalRawZoom={}",
                targetScale,
                bestZoom,
                bestScale,
                getCameraScale(),
                getZoom());
    }

    private int sampleCameraScaleAtZoom(int zoom)
    {
        setRawCameraZoom(zoom);
        sleep(CAMERA_SCALE_SAMPLE_DELAY_MS);
        return getCameraScale();
    }

    private int cameraScaleDistance(int scale, int targetScale) { return scale <= 0 ? Integer.MAX_VALUE : Math.abs(scale - targetScale); }

    private void setRawCameraZoom(int zoom)
    {
        int clampedZoom = clampCameraZoom(zoom);
        Microbot.getClientThread().invoke(() ->
        {
            Microbot.getClient().setVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT, clampedZoom);
            Microbot.getClient().setVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT, clampedZoom);
            Microbot.getClient().runScript(
                    ScriptID.CAMERA_DO_ZOOM,
                    clampedZoom,
                    clampedZoom);
        });
    }

    private int clampCameraZoom(int zoom) { return Math.max(CAMERA_ZOOM_MIN, Math.min(CAMERA_ZOOM_MAX, zoom)); }

    private boolean isDialogueOpen()
    {
        try
        {
            return Rs2Dialogue.isInDialogue() || Rs2Dialogue.hasContinue();
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private int getZoom()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().isResized()
                        ? Microbot.getClient().getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT)
                        : Microbot.getClient().getVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT))
                .orElse(0);
    }

    private int getCameraScale()
    {
        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getScale())
                .orElse(0);
    }

    private boolean depositInventoryForTaskSwitch()
    {
        if (!Rs2Bank.isOpen() || closeTaskSwitchBankTutorialOverlayIfOpen()) return false;
        if (Rs2Inventory.isEmpty()) {
            pendingTaskSwitchInventoryAtMs = 0L;
            pendingTaskSwitchInventoryEmptySlots = -1;
            return true;
        }
        if (!taskSwitchActionReady() || taskSwitchInventoryActionPending()) return false;

        Microbot.status = "Depositing inventory before next task";
        int before = Rs2Inventory.emptySlotCount();
        if (Rs2Bank.depositAll()) {
            pendingTaskSwitchInventoryEmptySlots = before;
            pendingTaskSwitchInventoryAtMs = System.currentTimeMillis();
        }
        markTaskSwitchAction();
        return false;
    }

    private boolean unequipGatheringTools()
    {
        if (!isGatheringToolEquipped()) {
            pendingTaskSwitchEquipmentAtMs = 0L;
            return true;
        }
        if (!Rs2Bank.isOpen()
                || closeTaskSwitchBankTutorialOverlayIfOpen()
                || !taskSwitchActionReady()
                || taskSwitchEquipmentActionPending()) return false;

        if (Rs2Bank.depositEquipment()) {
            pendingTaskSwitchEquipmentAtMs = System.currentTimeMillis();
        }
        markTaskSwitchAction();
        return false;
    }

    private boolean isGatheringToolEquipped()
    {
        return Rs2Equipment.isWearing("pickaxe", false) || Rs2Equipment.isWearing("pickaxe")
                || Rs2Equipment.isWearing("axe", false) || Rs2Equipment.isWearing("axe");
    }

    private boolean taskSwitchInventoryActionPending()
    {
        if (pendingTaskSwitchInventoryAtMs == 0L) return false;
        if (Rs2Inventory.emptySlotCount() != pendingTaskSwitchInventoryEmptySlots
                || System.currentTimeMillis() - pendingTaskSwitchInventoryAtMs >= TASK_SWITCH_DISPATCH_TIMEOUT_MS) {
            pendingTaskSwitchInventoryAtMs = 0L;
            pendingTaskSwitchInventoryEmptySlots = -1;
            return false;
        }
        return true;
    }

    private boolean taskSwitchEquipmentActionPending()
    {
        if (pendingTaskSwitchEquipmentAtMs == 0L) return false;
        if (!isGatheringToolEquipped()
                || System.currentTimeMillis() - pendingTaskSwitchEquipmentAtMs >= TASK_SWITCH_DISPATCH_TIMEOUT_MS) {
            pendingTaskSwitchEquipmentAtMs = 0L;
            return false;
        }
        return true;
    }

    private boolean taskSwitchActionReady()
    {
        return System.currentTimeMillis() - lastTaskSwitchActionAtMs >= TASK_SWITCH_ACTION_COOLDOWN_MS;
    }

    private void markTaskSwitchAction()
    {
        lastTaskSwitchActionAtMs = System.currentTimeMillis();
    }

    private boolean closeTaskSwitchBankTutorialOverlayIfOpen()
    {
        if (!KspBankWidgetHelper.closeBankTutorialOverlayIfOpen()) return false;

        debug("Closed bank tutorial overlay during task switch; resetting bank");
        taskSwitchBankResetPending = true;
        markTaskSwitchAction();
        return true;
    }

    private void scheduleNextBreak()
    {
        if (!config.doBreaks())
        {
            nextBreakAtMillis = -1L;
            breakEndsAtMillis = -1L;
            return;
        }

        long delayMinutes = randomMinutes(config.breakAfterMinMinutes(), config.breakAfterMaxMinutes());
        nextBreakAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes);
    }

    private void scheduleNextActivitySwitch()
    {
        if (!canUseActivitySwitchTimer())
        {
            nextActivitySwitchAtMillis = -1L;
            pausedActivitySwitchRemainingMillis = -1L;
            return;
        }

        long delayMinutes = randomMinutes(config.activitySwitchMinMinutes(), config.activitySwitchMaxMinutes());
        nextActivitySwitchAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes);
        pausedActivitySwitchRemainingMillis = -1L;
        activitySwitchTimerPaused = false;
        debug("Activity switch timer scheduled | currentTask={} delayMinutes={} remainingSeconds={}",
                currentTask,
                delayMinutes,
                getTimeUntilActivitySwitchSeconds());
    }

    private void pauseActivitySwitchTimer(long now)
    {
        if (activitySwitchTimerPaused)
        {
            return;
        }

        pausedActivitySwitchRemainingMillis = nextActivitySwitchAtMillis > 0L
                ? Math.max(0L, nextActivitySwitchAtMillis - now)
                : -1L;
        nextActivitySwitchAtMillis = -1L;
        activitySwitchTimerPaused = true;
        debug("Activity switch timer paused | currentTask={} remainingSeconds={} breakActive={} sharedBreakActive={}",
                currentTask,
                getPausedActivitySwitchSeconds(),
                breakActive,
                sharedBreakActive);
    }

    private void resumeActivitySwitchTimer(long now)
    {
        if (!activitySwitchTimerPaused || breakActive || sharedBreakActive)
        {
            return;
        }

        if (pausedActivitySwitchRemainingMillis >= 0L && canUseActivitySwitchTimer())
        {
            nextActivitySwitchAtMillis = now + pausedActivitySwitchRemainingMillis;
        }
        pausedActivitySwitchRemainingMillis = -1L;
        activitySwitchTimerPaused = false;
        debug("Activity switch timer resumed | currentTask={} remainingSeconds={} nextAtMillis={}",
                currentTask,
                getTimeUntilActivitySwitchSeconds(),
                nextActivitySwitchAtMillis);
    }

    private void updateSharedBreakState(long now)
    {
        boolean active = isSharedBreakHandlerActive();
        if (active == sharedBreakActive)
        {
            return;
        }

        sharedBreakActive = active;
        if (active)
        {
            pauseActivitySwitchTimer(now);
            debug("Paused activity switch timer for shared Microbot break | remainingSeconds={}",
                    getPausedActivitySwitchSeconds());
            return;
        }

        resumeActivitySwitchTimer(now);
        debug("Resumed activity switch timer after shared Microbot break | remainingSeconds={}",
                getTimeUntilActivitySwitchSeconds());
    }

    private boolean isSharedBreakHandlerActive()
    {
        try
        {
            return BreakHandlerScript.isBreakActive() || BreakHandlerScript.isMicroBreakActive();
        }
        catch (Exception ex)
        {
            debug("Unable to read shared break-handler state | error={}", ex.getMessage());
            return false;
        }
    }

    public boolean isAnyBreakActive() { return breakActive || sharedBreakActive; }

    private long getPausedActivitySwitchSeconds()
    {
        return pausedActivitySwitchRemainingMillis < 0L
                ? -1L
                : TimeUnit.MILLISECONDS.toSeconds(pausedActivitySwitchRemainingMillis);
    }

    private void maybeStartActivitySwitchTimer()
    {
        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            clearActivitySwitchTimerState();
            return;
        }

        if (!canUseActivitySwitchTimer() || !awaitingActivitySwitchTimerStart || !taskStarted)
        {
            return;
        }

        if (!isInCurrentTaskArea())
        {
            return;
        }

        awaitingActivitySwitchTimerStart = false;
        scheduleNextActivitySwitch();
        debug("Started activity switch timer for {} after reaching task area", currentTask);
    }

    private boolean isInCurrentTaskArea()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        if (location == null || currentTask == null || currentTask == BuilderTask.TUTORIAL_ISLAND) return false;

        switch (currentTask)
        {
            case STRONGHOLD_OF_SECURITY:
            case COOKS_ASSISTANT:
            case GOBLIN_DIPLOMACY:
            case ROMEO_AND_JULIET:
            case RUNE_MYSTERIES:
            case SHEEP_SHEARER:
            case X_MARKS_THE_SPOT:
            case THE_RESTLESS_GHOST:
            case IMP_CATCHER:
            case CRAFTING:
                return true;
            case RUNE_ESSENCE: return essenceMining.isInTaskArea();
            case MINING: return miningScript.getTargetArea().toWorldArea().contains(location);
            case WOODCUTTING: return woodCuttingScript.getTargetArea().contains(location);
            case FIREMAKING: return fireMakingScript.getTargetArea().toWorldArea().contains(location);
            case FISHING: return fishingScript.getTargetArea().toWorldArea().contains(location);
            case COOKING: return cookingScript.getTargetArea().getArea().contains(location);
            case MELEE: return meleeScript.getTargetArea().contains(location);
            case GE_SELL: return sellScript.getTargetArea().toWorldArea().contains(location);
            case GE_BUY: return buyScript.getTargetArea().toWorldArea().contains(location);
            case SMITHING: return smithScript.getTargetArea().toWorldArea().contains(location);
            case SMELTING: return smeltScript.getTargetArea().toWorldArea().contains(location);
            default: return false;
        }
    }

    private int randomMinutes(int min, int max)
    {
        int safeMin = Math.min(min, max);
        int safeMax = Math.max(min, max);
        return ThreadLocalRandom.current().nextInt(safeMin, safeMax + 1);
    }


    private void applyAntibanSettings()
    {
        Rs2Antiban.resetAntibanSettings(true);

        if (!config.useAntiban())
        {
            debug("Antiban disabled by configuration");
            return;
        }

        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        for (PlayStyle playStyle : PlayStyle.values())
        {
            playStyle.resetPlayStyle();
        }
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.universalAntiban = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.nonLinearIntervals = false;
        Rs2AntibanSettings.actionCooldownChance = 0.20;
        Rs2AntibanSettings.overwriteScriptSettings = true;
        debug("Applied antiban settings | enabled={} universal={} naturalMouse={} playStyle={} dynamicIntensity={} locked={}",
                Rs2AntibanSettings.antibanEnabled,
                Rs2AntibanSettings.universalAntiban,
                Rs2AntibanSettings.naturalMouse,
                Rs2AntibanSettings.usePlayStyle,
                Rs2AntibanSettings.dynamicIntensity,
                Rs2AntibanSettings.overwriteScriptSettings);
    }

    private void debug(String message, Object... args)
    {
        if (debugEnabled)
        {
            KspTaskDebug.info(log, true, "Builder", message, args);
        }
    }

    private void maybeLogStatus()
    {
        long now = System.currentTimeMillis();
        if (now - lastStatusLogAt >= 10_000)
        {
            WorldPoint playerLocation = getSafePlayerLocation();
            debug("active task | currentTask={} pendingTask={} breakActive={} player={} moving={} animating={} interacting={} bankOpen={} taskStarted={} activityTimer={}",
                    currentTask,
                    pendingTask,
                    breakActive,
                    playerLocation,
                    playerLocation != null && Rs2Player.isMoving(),
                    playerLocation != null && Rs2Player.isAnimating(),
                    playerLocation != null && Rs2Player.isInteracting(),
                    Rs2Bank.isOpen(),
                    taskStarted,
                    getActivitySwitchTimerDebugState());
            lastStatusLogAt = now;
        }
    }

    private String getActivitySwitchTimerDebugState()
    {
        if (isSingleSkillTaskForced())
        {
            return "disabled:single-skill";
        }

        if (activitySwitchTimerPaused)
        {
            return "paused:" + getPausedActivitySwitchSeconds() + "s";
        }

        if (isAnyBreakActive())
        {
            return "break-active:" + getPausedActivitySwitchSeconds() + "s";
        }

        if (!canUseActivitySwitchTimer())
        {
            return "disabled";
        }

        if (awaitingNextActivityStart)
        {
            return "awaiting-next-task";
        }

        if (awaitingActivitySwitchTimerStart)
        {
            return "awaiting-task-area";
        }

        if (pendingTask != null || pendingRandomTaskSelection)
        {
            return "switching";
        }

        if (nextActivitySwitchAtMillis > 0L)
        {
            return "running:" + getTimeUntilActivitySwitchSeconds() + "s";
        }

        return "not-scheduled";
    }

    private WorldPoint getSafePlayerLocation()
    {
        try
        {
            if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null)
            {
                return null;
            }

            return Rs2Player.getWorldLocation();
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private void captureOriginalWindowTitle()
    {
        try
        {
            String currentTitle = ClientUI.getFrame().getTitle();
            if (currentTitle != null && !currentTitle.isEmpty())
            {
                originalWindowTitle = currentTitle;
            }
        }
        catch (Exception ignored)
        {
        }
    }

    private void updateWindowTitle()
    {
        try
        {
            if (!breakActive)
            {
                ClientUI.getFrame().setTitle(originalWindowTitle);
                return;
            }

            long breakRemaining = getBreakTimeRemainingSeconds();
            String title = originalWindowTitle + " - Breaking for: " + formatBreakDuration(breakRemaining);
            ClientUI.getFrame().setTitle(title);
        }
        catch (Exception ignored)
        {
        }
    }

    private String formatBreakDuration(long totalSeconds)
    {
        if (totalSeconds < 0)
        {
            return "--:--";
        }

        Duration duration = Duration.ofSeconds(totalSeconds);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (hours > 0)
        {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }

        return String.format("%02d:%02d", minutes, seconds);
    }

    private void attemptLoginAfterBreak()
    {
        long now = System.currentTimeMillis();
        if ((now - lastBreakLoginAttemptAt) < TimeUnit.SECONDS.toMillis(10))
        {
            return;
        }

        lastBreakLoginAttemptAt = now;
        stopExternalAutoLoginPlugin("post-break-login");

        if (LoginManager.getActiveProfile() == null)
        {
            debug("Unable to log back in after break; no active login profile is configured");
            return;
        }

        try
        {
            int world = LoginManager.getRandomWorld(false);
            boolean loginStarted = LoginManager.login(world);
            debug("Triggered manual F2P login after break | world={} started={}", world, loginStarted);
        }
        catch (Exception ex)
        {
            debug("Failed to trigger login after break: {}", ex.getMessage());
        }
    }

    private void stopExternalAutoLoginPlugin(String reason)
    {
        try
        {
            var externalAutoLoginPlugin = Microbot.getPlugin(EXTERNAL_AUTO_LOGIN_PLUGIN_CLASS);
            if (externalAutoLoginPlugin == null || !Microbot.isPluginEnabled(externalAutoLoginPlugin))
            {
                return;
            }

            boolean stopped = Microbot.stopPlugin(externalAutoLoginPlugin);
            debug("Stopped standalone AutoLogin plugin | reason={} stopped={}", reason, stopped);
        }
        catch (Exception ex)
        {
            debug("Failed to stop standalone AutoLogin plugin | reason={} error={}", reason, ex.getMessage());
        }
    }

    public String getCurrentTaskName()
    {
        BuilderTask pending = pendingTask;
        if (pending != null)
        {
            return "SWITCHING_TO_" + pending.name();
        }

        BuilderTask current = currentTask;
        return current == null ? "IDLE" : current.name();
    }

    public long getRuntimeSeconds()
    {
        if (startedAtMillis <= 0L)
        {
            return 0L;
        }
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startedAtMillis);
    }

    public long getAccountPlayTimeSeconds()
    {
        return TimeUnit.MILLISECONDS.toSeconds(
                accountPlayTimeCache.getPlayTimeMillis(currentAccountHashSnapshot));
    }

    public long getTimeUntilBreakSeconds()
    {
        if (config == null || !config.doBreaks() || breakActive || nextBreakAtMillis <= 0L)
        {
            return -1L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(nextBreakAtMillis - System.currentTimeMillis()));
    }

    public long getBreakTimeRemainingSeconds()
    {
        if (!breakActive || breakEndsAtMillis <= 0L)
        {
            return -1L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(breakEndsAtMillis - System.currentTimeMillis()));
    }

    public long getTimeUntilActivitySwitchSeconds()
    {
        if (isSingleSkillTaskForced())
        {
            return -1L;
        }

        if (isAnyBreakActive())
        {
            return getPausedActivitySwitchSeconds();
        }

        if (!canUseActivitySwitchTimer()
                || nextActivitySwitchAtMillis <= 0L
                || pendingTask != null
                || pendingRandomTaskSelection
                || awaitingNextActivityStart)
        {
            return -1L;
        }
        return Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(nextActivitySwitchAtMillis - System.currentTimeMillis()));
    }

    @Override
    public void shutdown()
    {
        shuttingDown = true;
        if (accountPlayTimeCache != null)
        {
            accountPlayTimeCache.sample(Microbot.isLoggedIn(), currentAccountHashSnapshot);
            accountPlayTimeCache.endSession();
        }

        super.shutdown();
        taskStarted = false;
        breakActive = false;
        startedAtMillis = 0L;
        nextBreakAtMillis = -1L;
        breakEndsAtMillis = -1L;
        nextActivitySwitchAtMillis = -1L;
        pausedActivitySwitchRemainingMillis = -1L;
        activitySwitchTimerPaused = false;
        sharedBreakActive = false;
        experienceLampInterruptionActive = false;
        experienceLampPausedActivityTimer = false;
        debugEnabled = false;
        pendingTask = null;
        singleSkillRecoveryTask = null;
        clearSingleSkillResourceAudit();
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        awaitingActivitySwitchTimerStart = false;
        breakLogoutRequested = false;
        breakCombatWaitLogged = false;
        lastCombatObservedAtMillis = 0L;
        postTutorialBankCameraPending = false;
        taskSwitchBankLocation = null;
        lastBreakLoginAttemptAt = 0L;
        updateWindowTitle();

        stopAllTaskScriptsExcept(null);
        if (autoLoginScript != null) autoLoginScript.shutdown();
        if (experienceLampScript != null) experienceLampScript.shutdown();
        Rs2Antiban.resetAntibanSettings(true);
    }

    private void refreshAccountIdentity()
    {
        boolean loggedIn = Microbot.isLoggedIn();
        long accountHash = loggedIn ? getCurrentAccountHash() : 0L;

        if (loggedIn
                && accountHash != 0L
                && currentAccountHashSnapshot != 0L
                && accountHash != currentAccountHashSnapshot)
        {
            if (currentTask != null)
            {
                stopCurrentTaskScript();
            }
            taskStarted = false;
            currentTask = null;
            pendingTask = null;
            preselectedHandoffTask = null;
            pendingRandomTaskSelection = false;
            awaitingNextActivityStart = false;
            awaitingActivitySwitchTimerStart = false;
            nextActivitySwitchAtMillis = -1L;
            synchronizedPlayTimeAccountHash = 0L;
            nextPlayTimeReadAtMillis = 0L;
            taskSwitchBankLocation = null;
            debug("Account changed; reset task state before selecting for new account");
        }

        currentAccountHashSnapshot = accountHash;
        accountPlayTimeCache.sample(loggedIn, accountHash);
    }

    private boolean sampleAccountPlayTime()
    {
        boolean loggedIn = Microbot.isLoggedIn();
        if (loggedIn && (TutorialIslandScript.isPreTutorialBlockingWidgetOpen()
                || TutorialIslandScript.isInTutorialIslandArea()
                || TutorialIslandScript.isOnTutorialIsland()))
        {
            synchronizedPlayTimeAccountHash = 0L;
            currentAccountHashSnapshot = 0L;
            nextPlayTimeReadAtMillis = 0L;
            return true;
        }

        long accountHash = currentAccountHashSnapshot != 0L
                ? currentAccountHashSnapshot
                : getCurrentAccountHash();

        if (!loggedIn)
        {
            return false;
        }

        if (accountHash == 0L)
        {
            KspTaskDebug.throttled(log, true, "Builder", "play-time-account-hash", 5_000L,
                    "Waiting to read account play time | reason=account-hash-unavailable");
            return false;
        }

        if (accountHash == synchronizedPlayTimeAccountHash)
        {
            return true;
        }

        // The authoritative varc only needs to be sampled once per account for the
        // lifetime of this loaded client. AccountPlayTimeCache tracks elapsed logged-in
        // time locally after that, so task/plugin restarts must not re-read the varc.
        if (accountPlayTimeCache.hasAuthoritativePlayTimeThisSession(accountHash))
        {
            synchronizedPlayTimeAccountHash = accountHash;
            nextPlayTimeReadAtMillis = 0L;
            return true;
        }

        if (!isReadyAfterLoginHandoff())
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextPlayTimeReadAtMillis)
        {
            return false;
        }

        log.info("[KSP Builder] Reading authoritative account play time varc | accountHash={}",
                Long.toUnsignedString(accountHash));
        long playTimeMillis = TradeUnlock.readPlayTimeMillis();
        if (playTimeMillis < 0L)
        {
            nextPlayTimeReadAtMillis = now + PLAY_TIME_READ_RETRY_MS;
            log.info("[KSP Builder] Account play-time read failed; retrying in {} seconds",
                    TimeUnit.MILLISECONDS.toSeconds(PLAY_TIME_READ_RETRY_MS));
            return false;
        }

        accountPlayTimeCache.synchronizePlayTimeMillis(accountHash, playTimeMillis);
        synchronizedPlayTimeAccountHash = accountHash;
        nextPlayTimeReadAtMillis = 0L;
        log.info("[KSP Builder] Synchronized account play time | accountHash={} minutes={}",
                Long.toUnsignedString(accountHash),
                TimeUnit.MILLISECONDS.toMinutes(playTimeMillis));
        return true;
    }

    private long getCurrentAccountHash()
    {
        if (!Microbot.isLoggedIn() || Microbot.getClient() == null)
        {
            return 0L;
        }

        return Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getAccountHash())
                .orElse(0L);
    }
}
