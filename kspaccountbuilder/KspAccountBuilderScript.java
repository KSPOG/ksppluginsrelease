package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.Getter;
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
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.ClientUI;

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

        private int getSelectionWeight()
        {
            return selectionWeight;
        }
    }

    private static final int LOOP_DELAY_MS = 600;
    private static final String EXTERNAL_AUTO_LOGIN_PLUGIN_CLASS = "net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin";
    private static final int POST_TUTORIAL_BANK_CAMERA_PITCH = 358;
    private static final int POST_TUTORIAL_BANK_CAMERA_YAW = 968;
    private static final int POST_TUTORIAL_BANK_CAMERA_SCALE = 1154;
    private static final int POST_TUTORIAL_BANK_CAMERA_ZOOM = 377;
    private static final int CAMERA_SCALE_TOLERANCE = 16;
    private static final int COINS_ID = 995;
    private static final String COINS = "Coins";
    private static final int MIN_QUEST_BUY_PRICE = 1_000;
    private static final String BLUE_GOBLIN_MAIL = "Blue goblin mail";
    private static final String ORANGE_GOBLIN_MAIL = "Orange goblin mail";
    private static final int CHICKEN_TARGET_COMBAT_STAT_LEVEL = 15;
    private static final long PLAY_TIME_READ_RETRY_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long BREAK_LOGOUT_COMBAT_GRACE_MS = TimeUnit.SECONDS.toMillis(11);
    private static final String BRONZE_SWORD = "Bronze sword";
    private static final String WOODEN_SHIELD = "Wooden shield";
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

    @Getter
    private volatile BuilderTask currentTask;

    @Getter
    private volatile boolean breakActive;

    @Getter
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
    private volatile BuilderTask pendingTask;
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
    private long nextPlayTimeReadAtMillis;
    private BankLocation taskSwitchBankLocation;

    public boolean run(KspAccountBuilderConfig config)
    {
        shutdown();
        shuttingDown = false;
        this.config = config;
        this.debugEnabled = config.debugLogging();
        breakActive = false;
        stopExternalAutoLoginPlugin("builder-start");
        startAutoLoginHelper();
        if (experienceLampScript != null)
        {
            experienceLampScript.run();
        }
        miningScript.setDebugLogging(debugEnabled);
        woodCuttingScript.setDebugLogging(debugEnabled);
        fireMakingScript.setDebugLogging(debugEnabled);
        fishingScript.setDebugLogging(debugEnabled);
        cookingScript.setDebugLogging(debugEnabled);
        craftingScript.setDebugLogging(debugEnabled);
        meleeScript.setDebugLogging(debugEnabled);
        buyScript.setDebugLogging(debugEnabled);
        sellScript.setDebugLogging(debugEnabled);
        smithScript.setDebugLogging(debugEnabled);
        smeltScript.setDebugLogging(debugEnabled);
        tutorialIslandScript.setDebugLogging(debugEnabled);
        cooksScript.setDebugLogging(debugEnabled);
        gobScript.setDebugLogging(debugEnabled);
        romeoScript.setDebugLogging(debugEnabled);
        runeMystScript.setDebugLogging(debugEnabled);
        essenceMining.setDebugLogging(debugEnabled);
        applyAntibanSettings();

        currentTask = null;
        taskStarted = false;
        breakActive = false;
        pendingTask = null;
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
        nextPlayTimeReadAtMillis = 0L;
        taskSwitchBankLocation = null;
        pausedActivitySwitchRemainingMillis = -1L;
        activitySwitchTimerPaused = false;
        sharedBreakActive = false;
        essenceMineRecoveryActive = false;
        captureOriginalWindowTitle();

        startedAtMillis = System.currentTimeMillis();
        lastStatusLogAt = 0L;

        scheduleNextBreak();
        nextActivitySwitchAtMillis = -1L;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!super.run())
                {
                    return;
                }

                debugEnabled = config.debugLogging();
                miningScript.setDebugLogging(debugEnabled);
                woodCuttingScript.setDebugLogging(debugEnabled);
                fireMakingScript.setDebugLogging(debugEnabled);
                fishingScript.setDebugLogging(debugEnabled);
                cookingScript.setDebugLogging(debugEnabled);
                craftingScript.setDebugLogging(debugEnabled);
                meleeScript.setDebugLogging(debugEnabled);
                buyScript.setDebugLogging(debugEnabled);
                sellScript.setDebugLogging(debugEnabled);
                smithScript.setDebugLogging(debugEnabled);
                smeltScript.setDebugLogging(debugEnabled);
                tutorialIslandScript.setDebugLogging(debugEnabled);
                cooksScript.setDebugLogging(debugEnabled);
                gobScript.setDebugLogging(debugEnabled);
                romeoScript.setDebugLogging(debugEnabled);
                runeMystScript.setDebugLogging(debugEnabled);
                essenceMining.setDebugLogging(debugEnabled);
                autoLoginScript.setDebugLogging(debugEnabled);

                if (KspWorldMapGuard.closeIfOpen())
                {
                    KspWalkerGuard.clearActiveWalker("ksp_account_builder_world_map_open");
                    maybeLogStatus();
                    return;
                }

                sampleAccountPlayTime();
                if (handleExperienceLampInterruption())
                {
                    updateWindowTitle();
                    maybeLogStatus();
                    return;
                }
                processTimers();
                updateWindowTitle();
                if (isAnyBreakActive())
                {
                    maybeLogStatus();
                    return;
                }

                if (!Microbot.isLoggedIn())
                {
                    maybeLogStatus();
                    return;
                }

                if (!isReadyAfterLoginHandoff())
                {
                    maybeLogStatus();
                    return;
                }

                if (!isPlayTimeConfirmedForCurrentAccount())
                {
                    Microbot.status = "Confirming account play time";
                    maybeLogStatus();
                    return;
                }

                if (handleUnexpectedEssenceMineRecovery())
                {
                    maybeLogStatus();
                    return;
                }

                if (pendingTask != null)
                {
                    maybeLogStatus();
                    return;
                }

                KspWalkerGuard.recoverActiveWalkIfIdle();

                if (currentTask == null)
                {
                    currentTask = resolveStartingTask();
                    awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
                    debug("Selected initial task after play-time confirmation | currentTask={}", currentTask);
                }

                runAccountBuilderCycle();
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
        }, 0, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);

        return true;
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
        if (shuttingDown)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (Microbot.isLoggedIn() && Rs2Combat.inCombat())
        {
            lastCombatObservedAtMillis = now;
        }
        boolean singleSkillTaskForced = isSingleSkillTaskForced();
        updateSharedBreakState(now);

        if (sharedBreakActive)
        {
            return;
        }

        if (config.doBreaks())
        {
            if (!breakActive && now >= nextBreakAtMillis)
            {
                pauseActivitySwitchTimer(now);
                breakActive = true;
                taskStarted = false;
                pendingTask = null;
                pendingRandomTaskSelection = false;
                awaitingNextActivityStart = false;
                breakLogoutRequested = false;
                breakCombatWaitLogged = false;
                lastBreakLoginAttemptAt = 0L;
                stopExternalAutoLoginPlugin("ksp-break-start");
                stopAutoLoginHelperForBreak();
                miningScript.shutdown();
                woodCuttingScript.shutdown();
                fireMakingScript.shutdown();
                fishingScript.shutdown();
                cookingScript.shutdown();
                meleeScript.shutdown();
                buyScript.shutdown();
                sellScript.shutdown();
                smithScript.shutdown();
                smeltScript.shutdown();
                tutorialIslandScript.shutdown();
                cooksScript.shutdown();
                gobScript.shutdown();
                romeoScript.shutdown();
                runeMystScript.shutdown();
                essenceMining.shutdown();
                breakEndsAtMillis = now + TimeUnit.MINUTES.toMillis(randomMinutes(config.breakDurationMinMinutes(), config.breakDurationMaxMinutes()));
                debug("Starting break for {} seconds", getBreakTimeRemainingSeconds());
            }

            if (!shuttingDown && breakActive && Microbot.isLoggedIn() && !breakLogoutRequested)
            {
                long combatGraceRemainingMillis = BREAK_LOGOUT_COMBAT_GRACE_MS
                        - (now - lastCombatObservedAtMillis);
                if (Rs2Combat.inCombat() || combatGraceRemainingMillis > 0L)
                {
                    Microbot.status = "Waiting to leave combat before break";
                    if (!breakCombatWaitLogged)
                    {
                        breakCombatWaitLogged = true;
                        debug("Delaying break logout until combat is clear | graceRemainingSeconds={}",
                                Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(combatGraceRemainingMillis) + 1L));
                    }
                }
                else
                {
                    Rs2Player.logout();
                    breakLogoutRequested = true;
                    breakCombatWaitLogged = false;
                    sleepUntil(() -> !Microbot.isLoggedIn(), 5_000);
                }
            }

            if (breakActive && now >= breakEndsAtMillis)
            {
                breakActive = false;
                breakLogoutRequested = false;
                breakCombatWaitLogged = false;
                scheduleNextBreak();
                resumeActivitySwitchTimer(now);
                debug("Break completed, resuming tasks");
                startAutoLoginHelper();

                if (!Microbot.isLoggedIn())
                {
                    attemptLoginAfterBreak();
                }
            }

            if (breakActive)
            {
                return;
            }
        }

        if (!Microbot.isLoggedIn())
        {
            return;
        }

        if (!isPlayTimeConfirmedForCurrentAccount() || currentTask == null)
        {
            pendingTask = null;
            pendingRandomTaskSelection = false;
            awaitingNextActivityStart = false;
            awaitingActivitySwitchTimerStart = false;
            nextActivitySwitchAtMillis = -1L;
            return;
        }

        if (singleSkillTaskForced)
        {
            pendingTask = null;
            pendingRandomTaskSelection = false;
            awaitingNextActivityStart = false;
            awaitingActivitySwitchTimerStart = false;
            nextActivitySwitchAtMillis = -1L;
            return;
        }

        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            pendingTask = null;
            pendingRandomTaskSelection = false;
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
            if (!isSafeToStartActivitySwitch())
            {
                return;
            }

            if (!ensureInventoryTabOpenForTaskSelection())
            {
                return;
            }

            pendingRandomTaskSelection = true;
            taskStarted = false;
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            cookingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            romeoScript.shutdown();
            runeMystScript.shutdown();
            essenceMining.shutdown();
            debug("Preparing activity switch from {}; next task will be selected after bank cleanup", currentTask);
        }

        if (pendingRandomTaskSelection && switchToRandomTaskAfterBank(currentTask))
        {
            pendingRandomTaskSelection = false;
            pendingTask = null;
            awaitingNextActivityStart = true;
            awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
            nextActivitySwitchAtMillis = -1L;
            return;
        }

        if (pendingTask != null && switchTask(pendingTask))
        {
            pendingTask = null;
            awaitingNextActivityStart = true;
            awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
            nextActivitySwitchAtMillis = -1L;
        }
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
        if (!taskStarted && currentTask != BuilderTask.COOKING)
        {
            cookingScript.shutdown();
        }

        if (currentTask == BuilderTask.FISHING)
        {
            fishingScript.stopWalkerIfInsideTargetArea();
        }

        BuilderTask forcedSingleSkillTask = resolveSingleSkillTask();
        refreshSingleSkillTargetIfChanged(forcedSingleSkillTask);
        if (forcedSingleSkillTask == BuilderTask.ROMEO_AND_JULIET
                && isOneTimeTaskCompleted(BuilderTask.ROMEO_AND_JULIET))
        {
            currentTask = BuilderTask.ROMEO_AND_JULIET;
            taskStarted = false;
            Microbot.status = "Romeo and Juliet complete";
            return;
        }

        if (forcedSingleSkillTask == BuilderTask.RUNE_MYSTERIES
                && isOneTimeTaskCompleted(BuilderTask.RUNE_MYSTERIES))
        {
            currentTask = BuilderTask.RUNE_MYSTERIES;
            taskStarted = false;
            Microbot.status = "Rune Mysteries complete";
            return;
        }

        if (forcedSingleSkillTask == BuilderTask.RUNE_ESSENCE
                && !isRuneMysteriesComplete())
        {
            stopCurrentTaskScript();
            currentTask = BuilderTask.RUNE_ESSENCE;
            taskStarted = false;
            Microbot.status = "Rune Essence requires Rune Mysteries";
            return;
        }

        if (isSingleSkillTargetRequired(forcedSingleSkillTask)
                && resolveSingleSkillTarget(config.singleSkillTask()) == KspSingleSkillTarget.NONE)
        {
            if (taskStarted || currentTask != forcedSingleSkillTask)
            {
                stopCurrentTaskScript();
                taskStarted = false;
                currentTask = forcedSingleSkillTask;
            }
            Microbot.status = "Select a " + config.singleSkillTask() + " target";
            return;
        }

        boolean recoveringSingleSkillResources = handleSingleSkillResourceRecovery(forcedSingleSkillTask);
        if (recoveringSingleSkillResources)
        {
            if (currentTask != BuilderTask.GE_BUY || buyScript.isComplete())
            {
                return;
            }
        }
        else
        {
            if (auditSingleSkillResourcesIfNeeded(forcedSingleSkillTask))
            {
                return;
            }

            applySingleSkillOverride();

            if (forcedSingleSkillTask != null && !hasResourcesForTask(forcedSingleSkillTask))
            {
                startSingleSkillResourceRecovery(forcedSingleSkillTask);
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

        if (!isSingleSkillTaskForced() && !hasResourcesForTask(currentTask))
        {
            if (!switchToTaskWithResources())
            {
                taskStarted = false;
                return;
            }
        }

        if (taskStarted)
        {
            if (!isSingleSkillTaskForced()
                    && currentTask == BuilderTask.TUTORIAL_ISLAND
                    && tutorialIslandScript.isComplete())
            {
                tutorialIslandScript.shutdown();
                taskStarted = false;
                postTutorialBankCameraPending = true;
                postTutorialBankCameraPending = !setPostTutorialBankCamera();
                if (!switchToRandomTaskAfterBank(BuilderTask.TUTORIAL_ISLAND))
                {
                    stopCurrentTaskScript();
                }
                return;
            }

            if (!isSingleSkillTaskForced()
                    && currentTask == BuilderTask.COOKS_ASSISTANT
                    && cooksScript.isComplete())
            {
                cooksScript.shutdown();
                taskStarted = false;
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                }
                return;
            }

            if (!isSingleSkillTaskForced()
                    && currentTask == BuilderTask.GOBLIN_DIPLOMACY
                    && gobScript.isComplete())
            {
                gobScript.shutdown();
                taskStarted = false;
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                }
                return;
            }

            if (currentTask == BuilderTask.ROMEO_AND_JULIET
                    && romeoScript.isComplete())
            {
                accountTaskCache.setCompleted(
                        getCurrentAccountHash(),
                        KspAccountTaskCache.OneTimeTask.ROMEO_AND_JULIET,
                        true);
                romeoScript.shutdown();
                taskStarted = false;
                if (isSingleSkillTaskForced())
                {
                    Microbot.status = "Romeo and Juliet complete";
                    return;
                }
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                }
                return;
            }

            if (currentTask == BuilderTask.RUNE_MYSTERIES
                    && runeMystScript.isComplete())
            {
                accountTaskCache.setCompleted(
                        getCurrentAccountHash(),
                        KspAccountTaskCache.OneTimeTask.RUNE_MYSTERIES,
                        true);
                runeMystScript.shutdown();
                taskStarted = false;
                if (isSingleSkillTaskForced())
                {
                    Microbot.status = "Rune Mysteries complete";
                    return;
                }
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                }
                return;
            }

            if (!isSingleSkillTaskForced()
                    && currentTask == BuilderTask.STRONGHOLD_OF_SECURITY
                    && strongholdScript.isComplete())
            {
                accountTaskCache.setCompleted(
                        getCurrentAccountHash(),
                        KspAccountTaskCache.OneTimeTask.STRONGHOLD_OF_SECURITY,
                        true);
                strongholdScript.shutdown();
                taskStarted = false;
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                }
                return;
            }

            if (!isSingleSkillTaskForced() && currentTask == BuilderTask.GE_BUY && buyScript.isComplete())
            {
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                    taskStarted = false;
                }
                return;
            }
            if (!isSingleSkillTaskForced() && currentTask == BuilderTask.GE_SELL && sellScript.isComplete())
            {
                if (!switchToTaskWithResources())
                {
                    stopCurrentTaskScript();
                    taskStarted = false;
                }
                return;
            }
            maybeStartActivitySwitchTimer();
            return;
        }

        if (currentTask != BuilderTask.STRONGHOLD_OF_SECURITY)
        {
            strongholdScript.shutdown();
        }

        if (currentTask != BuilderTask.ROMEO_AND_JULIET)
        {
            romeoScript.shutdown();
        }

        if (currentTask != BuilderTask.RUNE_MYSTERIES)
        {
            runeMystScript.shutdown();
        }

        if (currentTask != BuilderTask.RUNE_ESSENCE)
        {
            essenceMining.shutdown();
        }

        if (currentTask != BuilderTask.CRAFTING)
        {
            craftingScript.shutdown();
        }

        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            clearActivitySwitchTimerState();
            taskStarted = tutorialIslandScript.run();
        }
        else if (currentTask == BuilderTask.COOKS_ASSISTANT)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            gobScript.shutdown();
            taskStarted = cooksScript.run();
        }
        else if (currentTask == BuilderTask.GOBLIN_DIPLOMACY)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            taskStarted = gobScript.run();
        }
        else if (currentTask == BuilderTask.ROMEO_AND_JULIET)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            cookingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            strongholdScript.shutdown();
            clearActivitySwitchTimerState();
            taskStarted = romeoScript.run();
        }
        else if (currentTask == BuilderTask.RUNE_MYSTERIES)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            cookingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            romeoScript.shutdown();
            strongholdScript.shutdown();
            clearActivitySwitchTimerState();
            taskStarted = runeMystScript.run();
        }
        else if (currentTask == BuilderTask.RUNE_ESSENCE)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            cookingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            romeoScript.shutdown();
            strongholdScript.shutdown();
            runeMystScript.shutdown();
            taskStarted = essenceMining.run();
        }
        else if (currentTask == BuilderTask.STRONGHOLD_OF_SECURITY)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            cookingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            clearActivitySwitchTimerState();
            taskStarted = strongholdScript.run();
        }
        else if (currentTask == BuilderTask.MINING)
        {
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            Areas selectedArea = resolveSingleSkillTarget(KspTrainSingleSkillTask.MINING)
                    .getValue(Areas.class);
            miningScript.setProgressiveMining(selectedArea == null);
            taskStarted = miningScript.run(selectedArea == null ? resolveMiningStartArea() : selectedArea);
        }
        else if (currentTask == BuilderTask.WOODCUTTING)
        {
            miningScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            TreeAreas selectedArea = resolveSingleSkillTarget(KspTrainSingleSkillTask.WOODCUTTING)
                    .getValue(TreeAreas.class);
            woodCuttingScript.setProgressiveWoodcutting(selectedArea == null);
            taskStarted = woodCuttingScript.run(
                    selectedArea == null ? resolveWoodcuttingStartArea() : selectedArea);
        }
        else if (currentTask == BuilderTask.FIREMAKING)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = fireMakingScript.run(FireArea.FM_AREA_DRAYNOR_BANK);
        }
        else if (currentTask == BuilderTask.FISHING)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas selectedArea =
                    resolveSingleSkillTarget(KspTrainSingleSkillTask.FISHING)
                            .getValue(net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas.class);
            taskStarted = fishingScript.run(
                    selectedArea == null ? resolveFishingStartArea() : selectedArea,
                    selectedArea == null);
        }
        else if (currentTask == BuilderTask.COOKING)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.areas.Areas selectedArea =
                    resolveSingleSkillTarget(KspTrainSingleSkillTask.COOKING)
                            .getValue(net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.areas.Areas.class);
            taskStarted = cookingScript.run(selectedArea == null
                    ? net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.areas.Areas.EDGEVILLE_RANGE
                    : selectedArea);
        }
        else if (currentTask == BuilderTask.CRAFTING)
        {
            CraftingLevels selectedLevel = resolveSingleSkillTarget(KspTrainSingleSkillTask.CRAFTING)
                    .getValue(CraftingLevels.class);
            taskStarted = craftingScript.run(
                    selectedLevel == null ? CraftingLevels.LEATHER_GLOVES : selectedLevel,
                    selectedLevel == null);
        }
        else if (currentTask == BuilderTask.MELEE)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            CombatAreas selectedArea = resolveSingleSkillTarget(KspTrainSingleSkillTask.MELEE)
                    .getValue(CombatAreas.class);
            taskStarted = selectedArea == null ? meleeScript.run() : meleeScript.run(selectedArea);
        }
        else if (currentTask == BuilderTask.GE_SELL)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = sellScript.run(GEArea.GRAND_EXCHANGE);
        }
        else if (currentTask == BuilderTask.GE_BUY)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            taskStarted = buyScript.run(GEArea.GRAND_EXCHANGE);
        }
        else if (currentTask == BuilderTask.SMITHING)
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smeltScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            SmithLevels selectedLevel = resolveSingleSkillTarget(KspTrainSingleSkillTask.SMITHING)
                    .getValue(SmithLevels.class);
            taskStarted = smithScript.run(SmithArea.SMITH_AREA_VARROCK_WEST_ANVIL, selectedLevel);
        }
        else
        {
            miningScript.shutdown();
            woodCuttingScript.shutdown();
            fireMakingScript.shutdown();
            fishingScript.shutdown();
            meleeScript.shutdown();
            buyScript.shutdown();
            sellScript.shutdown();
            smithScript.shutdown();
            tutorialIslandScript.shutdown();
            cooksScript.shutdown();
            gobScript.shutdown();
            BarLevels selectedBar = resolveSingleSkillTarget(KspTrainSingleSkillTask.SMELTING)
                    .getValue(BarLevels.class);
            taskStarted = smeltScript.run(
                    SmeltArea.SMELT_AREA_EDGEVILLE_FURNACE,
                    selectedBar == null ? resolveSmeltingFallbackBar() : selectedBar,
                    selectedBar == null);
        }

        if (taskStarted && awaitingNextActivityStart)
        {
            awaitingNextActivityStart = false;
            debug("Activity switch completed; waiting to reach task area before starting next switch timer");
        }

        maybeStartActivitySwitchTimer();
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

        BuilderTask taskWithResources = getRandomTaskWithResourcesExcluding(null);
        return taskWithResources != null ? taskWithResources : getRandomTaskExcluding(null);
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
            pendingSingleSkillAuditTask = null;
            singleSkillAuditBankEpoch = -1;
            singleSkillAuditBankWasOpen = false;
            debug("Skipping Single Skill bank resource audit while inside rune essence mine");
            return false;
        }

        if (forcedTask == null
                || forcedTask == BuilderTask.TUTORIAL_ISLAND
                || forcedTask == BuilderTask.ROMEO_AND_JULIET
                || forcedTask == BuilderTask.GE_BUY
                || auditedSingleSkillTask == forcedTask)
        {
            if (forcedTask == null)
            {
                clearSingleSkillResourceAudit();
            }
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
            debug("Starting Single Skill bank resource audit | target={} bankOpen={} bankEpoch={}",
                    forcedTask,
                    singleSkillAuditBankWasOpen,
                    singleSkillAuditBankEpoch);
        }

        if (!ensureTaskSwitchBankOpen())
        {
            Microbot.status = "Walking to bank for " + forcedTask;
            return true;
        }

        boolean bankMirrorReady = Rs2Bank.verifyBankMirrorAfterOpen(
                singleSkillAuditBankWasOpen,
                singleSkillAuditBankEpoch);
        if (!bankMirrorReady)
        {
            sleepUntil(() -> Rs2Bank.verifyBankMirrorAfterOpen(
                    singleSkillAuditBankWasOpen,
                    singleSkillAuditBankEpoch), 1_500);
            bankMirrorReady = Rs2Bank.verifyBankMirrorAfterOpen(
                    singleSkillAuditBankWasOpen,
                    singleSkillAuditBankEpoch);
        }

        if (!bankMirrorReady)
        {
            Microbot.status = "Syncing bank for " + forcedTask;
            KspTaskDebug.throttled(log, debugEnabled, "Builder", "single-skill-bank-sync", 3_000L,
                    "Waiting for fresh bank snapshot before Single Skill resource check | target={} epochBefore={} epochNow={}",
                    forcedTask,
                    singleSkillAuditBankEpoch,
                    Rs2Bank.getBankLiveEpoch());
            return true;
        }

        boolean resourcesAvailable = hasResourcesForTask(forcedTask);
        auditedSingleSkillTask = forcedTask;
        pendingSingleSkillAuditTask = null;
        singleSkillAuditBankEpoch = -1;
        singleSkillAuditBankWasOpen = false;
        debug("Completed Single Skill bank resource audit | target={} resourcesAvailable={} bankEpoch={}",
                forcedTask,
                resourcesAvailable,
                Rs2Bank.getBankLiveEpoch());

        if (!resourcesAvailable)
        {
            startSingleSkillResourceRecovery(forcedTask);
            return true;
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
        Microbot.status = "Resources ready for " + forcedTask;
        return true;
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
        if (config == null)
        {
            return null;
        }

        if (config.runSingleQuest())
        {
            switch (config.singleQuestTask())
            {
                case COOKS_ASSISTANT:
                    return BuilderTask.COOKS_ASSISTANT;
                case GOBLIN_DIPLOMACY:
                    return BuilderTask.GOBLIN_DIPLOMACY;
                case ROMEO_AND_JULIET:
                    return BuilderTask.ROMEO_AND_JULIET;
                case RUNE_MYSTERIES:
                    return BuilderTask.RUNE_MYSTERIES;
                default:
                    return null;
            }
        }

        if (!config.trainSingleSkill())
        {
            return null;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.RUNE_ESSENCE)
        {
            return BuilderTask.RUNE_ESSENCE;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.MINING)
        {
            return BuilderTask.MINING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.WOODCUTTING)
        {
            return BuilderTask.WOODCUTTING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.FIREMAKING)
        {
            return BuilderTask.FIREMAKING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.FISHING)
        {
            return BuilderTask.FISHING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.COOKING)
        {
            return BuilderTask.COOKING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.CRAFTING)
        {
            return BuilderTask.CRAFTING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.MELEE)
        {
            return BuilderTask.MELEE;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.SMELTING)
        {
            return BuilderTask.SMELTING;
        }

        if (config.singleSkillTask() == KspTrainSingleSkillTask.SMITHING)
        {
            return BuilderTask.SMITHING;
        }

        return null;
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

    private boolean isSingleSkillTaskForced()
    {
        return resolveSingleSkillTask() != null;
    }

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

    private Areas resolveMiningStartArea()
    {
        return Areas.TIN_COPPER_VARROCK_EAST;
    }

    private TreeAreas resolveWoodcuttingStartArea()
    {
        return TreeAreas.REGULAR_TREE_VARROCK_WEST;
    }

    private net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas resolveFishingStartArea()
    {
        return net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas.SHRIMP_ANCHOVIES;
    }

    private BarLevels resolveSmeltingFallbackBar()
    {
        return BarLevels.BRONZE;
    }

    private boolean hasResourcesForTask(BuilderTask task)
    {
        if (isTaskTemporarilyDisabled(task))
        {
            return false;
        }

        if (task == BuilderTask.TUTORIAL_ISLAND)
        {
            return TutorialIslandScript.isOnTutorialIsland();
        }

        if (task == BuilderTask.COOKS_ASSISTANT)
        {
            return !cooksScript.isComplete() && hasEnoughCoinsForCooksAssistant();
        }

        if (task == BuilderTask.GOBLIN_DIPLOMACY)
        {
            return !gobScript.isComplete() && hasEnoughCoinsForGoblinDiplomacy();
        }

        if (task == BuilderTask.ROMEO_AND_JULIET)
        {
            // The script gathers its own Cadava berries. Completion is handled by the
            // one-time task cache and must not be interpreted as missing GE resources.
            return !isOneTimeTaskCompleted(task);
        }

        if (task == BuilderTask.RUNE_MYSTERIES)
        {
            return !isOneTimeTaskCompleted(task);
        }

        if (task == BuilderTask.RUNE_ESSENCE)
        {
            return isRuneMysteriesComplete() && hasAnyToolAvailable(Buy.PICKAXE_NAMES);
        }

        if (task == BuilderTask.STRONGHOLD_OF_SECURITY)
        {
            return !isOneTimeTaskCompleted(task);
        }

        if (task == BuilderTask.MINING)
        {
            return hasAnyToolAvailable(Buy.PICKAXE_NAMES);
        }

        if (task == BuilderTask.WOODCUTTING)
        {
            return hasAnyToolAvailable(Buy.AXE_NAMES);
        }

        if (task == BuilderTask.FIREMAKING)
        {
            return hasAnyFiremakingResourcesAvailable();
        }

        if (task == BuilderTask.FISHING)
        {
            return hasAnyFishingResourcesAvailable();
        }

        if (task == BuilderTask.COOKING)
        {
            return hasAnyCookingResourcesAvailable();
        }

        if (task == BuilderTask.CRAFTING)
        {
            return hasAnyCraftingResourcesAvailable();
        }

        if (task == BuilderTask.MELEE)
        {
            return hasAnyMeleeResourcesAvailable();
        }

        if (task == BuilderTask.GE_SELL)
        {
            return hasAnyGeSellResourcesAvailable();
        }

        if (task == BuilderTask.GE_BUY)
        {
            return hasAnyGeBuyResourcesAvailable();
        }

        if (task == BuilderTask.SMITHING)
        {
            return hasAnySmithingResourcesAvailable();
        }

        return hasAnySmeltingResourcesAvailable();
    }

    private boolean isTaskTemporarilyDisabled(BuilderTask task)
    {
        return task == BuilderTask.STRONGHOLD_OF_SECURITY;
    }

    private boolean isOneTimeTaskCompleted(BuilderTask task)
    {
        KspAccountTaskCache.OneTimeTask oneTimeTask;
        if (task == BuilderTask.STRONGHOLD_OF_SECURITY)
        {
            oneTimeTask = KspAccountTaskCache.OneTimeTask.STRONGHOLD_OF_SECURITY;
        }
        else if (task == BuilderTask.ROMEO_AND_JULIET)
        {
            oneTimeTask = KspAccountTaskCache.OneTimeTask.ROMEO_AND_JULIET;
        }
        else if (task == BuilderTask.RUNE_MYSTERIES)
        {
            oneTimeTask = KspAccountTaskCache.OneTimeTask.RUNE_MYSTERIES;
        }
        else
        {
            return false;
        }

        long accountHash = getCurrentAccountHash();
        return accountHash == 0L
                || accountTaskCache.isCompleted(
                        accountHash,
                        oneTimeTask);
    }

    private boolean isRuneMysteriesComplete()
    {
        return Rs2Player.getQuestState(Quest.RUNE_MYSTERIES) == QuestState.FINISHED;
    }

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
        return Rs2Inventory.count(itemName)
                + Rs2Inventory.count(itemName, true)
                + Math.max(0, Rs2Bank.count(itemName));
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
        for (String toolName : toolNames)
        {
            if (Rs2Equipment.isWearing(toolName) || Rs2Inventory.hasItem(toolName) || Rs2Bank.count(toolName) > 0)
            {
                return true;
            }
        }

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
        return itemName != null
                && (Rs2Equipment.isWearing(itemName)
                || Rs2Inventory.hasItem(itemName)
                || Rs2Inventory.hasItem(itemName, true)
                || Rs2Bank.count(itemName) > 0);
    }

    private boolean hasBankItem(String itemName)
    {
        return itemName != null && Rs2Bank.count(itemName) > 0;
    }

    private boolean hasAnySmeltingResourcesAvailable()
    {
        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        for (int i = BarLevels.values().length - 1; i >= 0; i--)
        {
            BarLevels bar = BarLevels.values()[i];
            if (smithingLevel < bar.getRequiredSmithingLevel())
            {
                continue;
            }

            ReqOres req = ReqOres.valueOf(bar.name());
            int primaryAvailable = Rs2Inventory.count(req.getPrimaryOreName()) + Math.max(0, Rs2Bank.count(req.getPrimaryOreName()));
            if (primaryAvailable < req.getPrimaryOreAmount())
            {
                continue;
            }

            if (!req.hasSecondaryOre())
            {
                return true;
            }

            int secondaryAvailable = Rs2Inventory.count(req.getSecondaryOreName()) + Math.max(0, Rs2Bank.count(req.getSecondaryOreName()));
            if (secondaryAvailable >= req.getSecondaryOreAmount())
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyFiremakingResourcesAvailable()
    {
        if (!hasTinderboxAnywhere())
        {
            return false;
        }

        int firemakingLevel = Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
        for (int i = LogsLvl.values().length - 1; i >= 0; i--)
        {
            LogsLvl logsLvl = LogsLvl.values()[i];
            if (firemakingLevel < logsLvl.getRequiredLevel())
            {
                continue;
            }

            if (firemakingLevel >= LogsLvl.WILLOW_LOGS.getRequiredLevel()
                    && logsLvl != LogsLvl.WILLOW_LOGS)
            {
                continue;
            }

            if (firemakingLevel >= LogsLvl.OAK_LOGS.getRequiredLevel()
                    && logsLvl == LogsLvl.LOGS)
            {
                continue;
            }

            int availableLogs = Rs2Inventory.count(logsLvl.getDisplayName()) + Math.max(0, Rs2Bank.count(logsLvl.getDisplayName()));
            if (availableLogs > 0)
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyFishingResourcesAvailable()
    {
        int fishingLevel = Microbot.getClient().getRealSkillLevel(Skill.FISHING);
        LevelReqs targetFish = LevelReqs.bestForFishingLevel(fishingLevel);
        net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.needed.Inventory requiredInventory =
                net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.needed.Inventory.valueOf(targetFish.name());

        for (String itemName : requiredInventory.getRequiredItems())
        {
            int available = Rs2Inventory.count(itemName)
                    + Rs2Inventory.count(itemName, true)
                    + Math.max(0, Rs2Bank.count(itemName));
            if (available <= 0)
            {
                return false;
            }
        }

        return true;
    }

    private boolean hasAnyCookingResourcesAvailable()
    {
        int cookingLevel = Microbot.getClient().getRealSkillLevel(Skill.COOKING);
        for (CookLevels fish : CookLevels.values())
        {
            if (cookingLevel < fish.getRequiredLevel())
            {
                continue;
            }

            int available = Rs2Inventory.count(fish.getRawItemName())
                    + Rs2Inventory.count(fish.getRawItemName(), true)
                    + Math.max(0, Rs2Bank.count(fish.getRawItemName()));
            if (available > 0)
            {
                return true;
            }
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
            int available = Rs2Inventory.count(ingredient.getItemName())
                    + Math.max(0, Rs2Bank.count(ingredient.getItemName()));
            if (available < ingredient.getAmount())
            {
                return false;
            }
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
        int smithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        if (smithingLevel < level.getRequiredLevel())
        {
            debug("Skipping Smithing recipe; level requirement not met | recipe={} requiredLevel={} level={}",
                    level.getDisplayName(),
                    level.getRequiredLevel(),
                    smithingLevel);
            return false;
        }

        SmithRecipe recipe = SmithRecipe.valueOf(level.name());
        String barName = recipe.getDisplayName().split(" ")[0] + " bar";
        int barsAvailable = Rs2Inventory.count(barName) + Math.max(0, Rs2Bank.count(barName));
        boolean hasResources = barsAvailable >= recipe.getBarRequirement();
        if (!hasResources)
        {
            debug("Skipping Smithing recipe; insufficient bars | recipe={} bar={} required={} available={}",
                    recipe.getDisplayName(),
                    barName,
                    recipe.getBarRequirement(),
                    barsAvailable);
        }
        return hasResources;
    }

    private boolean hasAnyGeSellResourcesAvailable()
    {
        if (sellScript.hasSellListItemsAvailable())
        {
            return true;
        }

        if (hasAnyOutdatedToolAvailable())
        {
            return true;
        }

        return false;
    }

    private boolean hasAnyGeBuyResourcesAvailable()
    {
        return Buy.hasAnyGeBuyRequirementMissing() && buyScript.canAffordMissingBuys();
    }

    private boolean hasAnyOutdatedToolAvailable()
    {
        String desiredPickaxe = resolveDesiredPickaxeForSellingTask();
        String desiredAxe = resolveDesiredAxeForSellingTask();

        for (String pickaxeName : Buy.PICKAXE_NAMES)
        {
            if (pickaxeName.equalsIgnoreCase(desiredPickaxe))
            {
                continue;
            }

            if (Rs2Equipment.isWearing(pickaxeName)
                    || Rs2Inventory.hasItem(pickaxeName)
                    || Rs2Inventory.hasItem(pickaxeName, true)
                    || Rs2Bank.count(pickaxeName) > 0)
            {
                return true;
            }
        }

        for (String axeName : Buy.AXE_NAMES)
        {
            if (axeName.equalsIgnoreCase(desiredAxe))
            {
                continue;
            }

            if (Rs2Equipment.isWearing(axeName)
                    || Rs2Inventory.hasItem(axeName)
                    || Rs2Inventory.hasItem(axeName, true)
                    || Rs2Bank.count(axeName) > 0)
            {
                return true;
            }
        }

        return false;
    }

    private String resolveDesiredPickaxeForSellingTask()
    {
        return Buy.resolveDesiredPickaxeNameForGear();
    }

    private String resolveDesiredAxeForSellingTask()
    {
        return Buy.resolveDesiredAxeNameForGear();
    }

    private boolean hasHammerWithRequiredIdAnywhere()
    {
        return Buy.hasHammerAnywhere();
    }

    private boolean hasTinderboxAnywhere()
    {
        return Buy.hasTinderboxAnywhere();
    }

    private boolean switchToTaskWithResources()
    {
        if (!stopCurrentTaskForHandoff(null))
        {
            return false;
        }

        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to switch task; still preparing bank handoff before selecting replacement for {}", currentTask);
            return false;
        }

        BuilderTask nextTask = getRandomTaskWithResourcesExcluding(currentTask);
        if (nextTask == null)
        {
            debug("Current task {} has no resources and no alternative task is available after bank cleanup", currentTask);
            return false;
        }

        if (!ensureInventoryTabOpenForTaskSelection())
        {
            debug("Waiting to switch task; inventory tab is not open before switching from {} to {}", currentTask, nextTask);
            return false;
        }

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
        if (!stopCurrentTaskForHandoff(null))
        {
            return false;
        }

        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to select next task; still preparing bank handoff from {}", currentTask);
            return false;
        }

        BuilderTask nextTask = getRandomTaskWithResourcesExcluding(excludedTask);
        if (nextTask == null)
        {
            nextTask = getRandomTaskExcluding(excludedTask);
        }

        if (nextTask == null)
        {
            debug("No next task could be selected after bank cleanup | excluded={}", excludedTask);
            return false;
        }

        if (!ensureInventoryTabOpenForTaskSelection())
        {
            debug("Waiting to select next task; inventory tab is not open before switching from {} to {}", currentTask, nextTask);
            return false;
        }

        pendingTask = null;
        pendingRandomTaskSelection = false;
        awaitingNextActivityStart = false;
        currentTask = nextTask;
        awaitingActivitySwitchTimerStart = canUseActivitySwitchTimer();
        nextActivitySwitchAtMillis = -1L;
        debug("Selected next task after bank cleanup | currentTask={}", currentTask);
        return true;
    }

    private BuilderTask getRandomTaskWithResourcesExcluding(BuilderTask excludedTask)
    {
        if (TutorialIslandScript.isOnTutorialIsland())
        {
            return excludedTask == BuilderTask.TUTORIAL_ISLAND ? null : BuilderTask.TUTORIAL_ISLAND;
        }

        BuilderTask primaryTask = getRandomTaskWithResourcesExcluding(excludedTask, false);
        if (primaryTask != null)
        {
            return primaryTask;
        }

        return getRandomTaskWithResourcesExcluding(excludedTask, true);
    }

    private BuilderTask getRandomTaskWithResourcesExcluding(BuilderTask excludedTask, boolean includeSupportTasks)
    {
        if (TutorialIslandScript.isOnTutorialIsland())
        {
            return excludedTask == BuilderTask.TUTORIAL_ISLAND ? null : BuilderTask.TUTORIAL_ISLAND;
        }

        BuilderTask[] candidates = new BuilderTask[BuilderTask.values().length];
        int candidateCount = 0;

        for (BuilderTask task : BuilderTask.values())
        {
            if (task == excludedTask
                    || task == BuilderTask.TUTORIAL_ISLAND
                    || isTaskTemporarilyDisabled(task)
                    || isOneTimeTaskCompleted(task))
            {
                continue;
            }

            if (!includeSupportTasks && isSupportTask(task))
            {
                continue;
            }

            if (hasResourcesForTask(task))
            {
                candidates[candidateCount++] = task;
            }
        }

        if (candidateCount == 0)
        {
            return null;
        }

        return selectWeightedTask(candidates, candidateCount);
    }

    private boolean isSupportTask(BuilderTask task)
    {
        return task == BuilderTask.GE_BUY;
    }

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
            debug("Cleared melee GE sell handoff; no sellable bank items remain");
            return false;
        }

        Microbot.status = "Preparing GE sell";
        if (!switchTask(BuilderTask.GE_SELL))
        {
            return true;
        }

        meleeScript.clearPendingSellHandoff();
        taskStarted = false;
        clearActivitySwitchTimerState();
        awaitingNextActivityStart = true;
        debug("Melee loot banked; switching directly to GE_SELL");
        return true;
    }

    private void stopCurrentTaskScript()
    {
        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            tutorialIslandScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.COOKS_ASSISTANT)
        {
            cooksScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.GOBLIN_DIPLOMACY)
        {
            gobScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.ROMEO_AND_JULIET)
        {
            romeoScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.RUNE_MYSTERIES)
        {
            runeMystScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.RUNE_ESSENCE)
        {
            essenceMining.shutdown();
            return;
        }

        if (currentTask == BuilderTask.STRONGHOLD_OF_SECURITY)
        {
            strongholdScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.MINING)
        {
            miningScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.WOODCUTTING)
        {
            woodCuttingScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.FIREMAKING)
        {
            fireMakingScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.FISHING)
        {
            fishingScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.COOKING)
        {
            cookingScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.CRAFTING)
        {
            craftingScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.MELEE)
        {
            meleeScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.GE_SELL || currentTask == BuilderTask.GE_BUY)
        {
            buyScript.shutdown();
            sellScript.shutdown();
            return;
        }

        if (currentTask == BuilderTask.SMITHING)
        {
            smithScript.shutdown();
            return;
        }

        smeltScript.shutdown();
        tutorialIslandScript.shutdown();
    }

    private boolean stopCurrentTaskForHandoff(BuilderTask nextTask)
    {
        BuilderTask outgoingTask = currentTask;
        stopCurrentTaskScript();
        taskStarted = false;

        if (isTaskScriptRunning(outgoingTask))
        {
            debug("Waiting for outgoing task to stop before handoff | currentTask={} nextTask={}",
                    outgoingTask, nextTask);
            return false;
        }

        debug("Outgoing task stopped for handoff | currentTask={} nextTask={}", outgoingTask, nextTask);
        return true;
    }

    private boolean isTaskScriptRunning(BuilderTask task)
    {
        if (task == null)
        {
            return false;
        }

        switch (task)
        {
            case TUTORIAL_ISLAND:
                return tutorialIslandScript.isRunning();
            case COOKS_ASSISTANT:
                return cooksScript.isRunning();
            case GOBLIN_DIPLOMACY:
                return gobScript.isRunning();
            case ROMEO_AND_JULIET:
                return romeoScript.isRunning();
            case RUNE_MYSTERIES:
                return runeMystScript.isRunning();
            case RUNE_ESSENCE:
                return essenceMining.isRunning();
            case STRONGHOLD_OF_SECURITY:
                return strongholdScript.isRunning();
            case MINING:
                return miningScript.isRunning();
            case WOODCUTTING:
                return woodCuttingScript.isRunning();
            case FIREMAKING:
                return fireMakingScript.isRunning();
            case FISHING:
                return fishingScript.isRunning();
            case COOKING:
                return cookingScript.isRunning();
            case CRAFTING:
                return craftingScript.isRunning();
            case MELEE:
                return meleeScript.isRunning();
            case GE_SELL:
                return sellScript.isRunning();
            case GE_BUY:
                return buyScript.isRunning();
            case SMITHING:
                return smithScript.isRunning();
            case SMELTING:
                return smeltScript.isRunning();
            default:
                return false;
        }
    }

    private boolean switchTask(BuilderTask nextTask)
    {
        if (!stopCurrentTaskForHandoff(nextTask))
        {
            return false;
        }

        if (!prepareForTaskSwitchAtBank())
        {
            debug("Waiting to switch task; still preparing bank/gear handoff to {}", nextTask);
            return false;
        }

        currentTask = nextTask;
        debug("Switching task to {}", currentTask);
        return true;
    }

    private BuilderTask getRandomTaskExcluding(BuilderTask excludedTask)
    {
        if (TutorialIslandScript.isOnTutorialIsland())
        {
            return BuilderTask.TUTORIAL_ISLAND;
        }

        BuilderTask nextTask = getRandomPrimaryTaskExcluding(excludedTask);
        if (nextTask != null)
        {
            return nextTask;
        }

        BuilderTask[] candidates = new BuilderTask[BuilderTask.values().length];
        int candidateCount = 0;
        for (BuilderTask task : BuilderTask.values())
        {
            if (task == excludedTask || task == BuilderTask.TUTORIAL_ISLAND)
            {
                continue;
            }

            candidates[candidateCount++] = task;
        }

        return selectWeightedTask(candidates, candidateCount);
    }

    private BuilderTask getRandomPrimaryTaskExcluding(BuilderTask excludedTask)
    {
        BuilderTask[] candidates = new BuilderTask[BuilderTask.values().length];
        int candidateCount = 0;

        for (BuilderTask task : BuilderTask.values())
        {
            if (task == excludedTask
                    || task == BuilderTask.TUTORIAL_ISLAND
                    || isSupportTask(task)
                    || isTaskTemporarilyDisabled(task)
                    || isOneTimeTaskCompleted(task))
            {
                continue;
            }

            candidates[candidateCount++] = task;
        }

        if (candidateCount == 0)
        {
            return null;
        }

        return selectWeightedTask(candidates, candidateCount);
    }

    private BuilderTask selectWeightedTask(BuilderTask[] candidates, int candidateCount)
    {
        if (candidateCount <= 0)
        {
            return null;
        }

        int totalWeight = 0;
        for (int index = 0; index < candidateCount; index++)
        {
            totalWeight += getTaskSelectionWeight(candidates[index]);
        }

        if (totalWeight <= 0)
        {
            return candidates[ThreadLocalRandom.current().nextInt(candidateCount)];
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (int index = 0; index < candidateCount; index++)
        {
            BuilderTask candidate = candidates[index];
            int weight = getTaskSelectionWeight(candidate);
            if (roll < weight)
            {
                debug("Weighted task selection | selected={} weight={} totalWeight={} candidates={}",
                        candidate,
                        weight,
                        totalWeight,
                        candidateCount);
                return candidate;
            }
            roll -= weight;
        }

        return candidates[candidateCount - 1];
    }

    private int getTaskSelectionWeight(BuilderTask task)
    {
        if (task == null)
        {
            return 0;
        }

        int skillLevel = getTaskSkillLevel(task);
        int catchUpBonus = skillLevel > 0
                ? Math.max(0, 40 - skillLevel) / 4
                : 0;
        return Math.max(1, task.getSelectionWeight() + catchUpBonus);
    }

    private int getTaskSkillLevel(BuilderTask task)
    {
        if (Microbot.getClient() == null)
        {
            return 0;
        }

        switch (task)
        {
            case MINING:
            case RUNE_ESSENCE:
                return Microbot.getClient().getRealSkillLevel(Skill.MINING);
            case WOODCUTTING:
                return Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
            case FIREMAKING:
                return Microbot.getClient().getRealSkillLevel(Skill.FIREMAKING);
            case FISHING:
                return Microbot.getClient().getRealSkillLevel(Skill.FISHING);
            case COOKING:
                return Microbot.getClient().getRealSkillLevel(Skill.COOKING);
            case CRAFTING:
                return Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
            case MELEE:
                return Math.min(
                        Microbot.getClient().getRealSkillLevel(Skill.ATTACK),
                        Math.min(
                                Microbot.getClient().getRealSkillLevel(Skill.STRENGTH),
                                Microbot.getClient().getRealSkillLevel(Skill.DEFENCE)));
            case SMITHING:
            case SMELTING:
                return Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
            default:
                return 0;
        }
    }

    private boolean ensureInventoryTabOpenForTaskSelection()
    {
        if (!Microbot.isLoggedIn())
        {
            return false;
        }

        if (Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY)
        {
            return true;
        }

        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
        sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 1_500);

        return Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY;
    }

    private boolean prepareForTaskSwitchAtBank()
    {
        if (postTutorialBankCameraPending && !TutorialIslandScript.isOnTutorialIsland())
        {
            postTutorialBankCameraPending = !setPostTutorialBankCamera();
        }

        if (!ensureTaskSwitchBankOpen())
        {
            return false;
        }

        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        sleep(300);

        if (!depositInventoryForTaskSwitch())
        {
            return false;
        }

        if (!unequipGatheringTools())
        {
            return false;
        }

        if (!ensureTaskSwitchBankOpen())
        {
            return false;
        }

        depositGatheringToolsInInventory();

        boolean prepared = depositInventoryForTaskSwitch();
        if (prepared)
        {
            taskSwitchBankLocation = null;
        }
        return prepared;
    }

    private boolean ensureTaskSwitchBankOpen()
    {
        if (Rs2Bank.isOpen())
        {
            return true;
        }

        if (tryOpenTaskSwitchBank("direct-open"))
        {
            sleepUntil(Rs2Bank::isOpen, 2_000);
            if (Rs2Bank.isOpen())
            {
                return true;
            }
        }

        if (tryWalkToTaskSwitchBank())
        {
            sleepUntil(Rs2Bank::isOpen, 3_000);
            return Rs2Bank.isOpen();
        }

        return Rs2Bank.isOpen();
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

        Microbot.getClientThread().invoke(() ->
        {
            Microbot.getClient().setCameraPitchRelaxerEnabled(true);
            Microbot.getClient().setCameraPitchTarget(POST_TUTORIAL_BANK_CAMERA_PITCH);
            Microbot.getClient().setCameraYawTarget(POST_TUTORIAL_BANK_CAMERA_YAW);
        });
        Rs2Camera.setPitchInstant(POST_TUTORIAL_BANK_CAMERA_PITCH);
        Rs2Camera.setYawInstant(POST_TUTORIAL_BANK_CAMERA_YAW);
        applyCameraZoom(POST_TUTORIAL_BANK_CAMERA_ZOOM);
        Microbot.getClientThread().invoke(() ->
        {
            Microbot.getClient().setCameraPitchTarget(POST_TUTORIAL_BANK_CAMERA_PITCH);
            Microbot.getClient().setCameraYawTarget(POST_TUTORIAL_BANK_CAMERA_YAW);
        });
        boolean scaleSet = sleepUntil(
                () -> Math.abs(getCameraScale() - POST_TUTORIAL_BANK_CAMERA_SCALE)
                        <= CAMERA_SCALE_TOLERANCE,
                2_000);
        debug(
                "Set post-tutorial bank camera | scaleSet={} targetPitch={} actualPitch={} targetYaw={} actualYaw={} targetZoom={} actualZoom={} targetScale={} actualScale={}",
                scaleSet,
                POST_TUTORIAL_BANK_CAMERA_PITCH,
                Rs2Camera.getPitch(),
                POST_TUTORIAL_BANK_CAMERA_YAW,
                Rs2Camera.getYaw(),
                POST_TUTORIAL_BANK_CAMERA_ZOOM,
                getZoom(),
                POST_TUTORIAL_BANK_CAMERA_SCALE,
                getCameraScale()
        );
        return true;
    }

    private void applyCameraZoom(int zoom)
    {
        Microbot.getClientThread().invoke(() ->
        {
            Microbot.getClient().setVarcIntValue(VarClientInt.CAMERA_ZOOM_FIXED_VIEWPORT, zoom);
            Microbot.getClient().setVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT, zoom);
            Microbot.getClient().runScript(ScriptID.CAMERA_DO_ZOOM, zoom, zoom);
        });
    }

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

    private boolean depositInventoryForTaskSwitch()
    {
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        if (closeTaskSwitchBankTutorialOverlayIfOpen())
        {
            return false;
        }

        if (!Rs2Inventory.isEmpty())
        {
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 1_500);
            sleep(200);
        }

        return Rs2Bank.isOpen();
    }

    private boolean unequipGatheringTools()
    {
        boolean hasPickaxeEquipped = Rs2Equipment.isWearing("pickaxe", false) || Rs2Equipment.isWearing("pickaxe");
        boolean hasAxeEquipped = Rs2Equipment.isWearing("axe", false) || Rs2Equipment.isWearing("axe");
        if (!hasPickaxeEquipped && !hasAxeEquipped)
        {
            return true;
        }

        if (Rs2Bank.isOpen())
        {
            if (closeTaskSwitchBankTutorialOverlayIfOpen())
            {
                return false;
            }

            Rs2Bank.depositEquipment();
            sleepUntil(() -> !isGatheringToolEquipped(), 1_500);
            return !isGatheringToolEquipped();
        }

        return false;
    }

    private boolean isGatheringToolEquipped()
    {
        return Rs2Equipment.isWearing("pickaxe", false) || Rs2Equipment.isWearing("pickaxe")
                || Rs2Equipment.isWearing("axe", false) || Rs2Equipment.isWearing("axe");
    }

    private void depositGatheringToolsInInventory()
    {
        if (closeTaskSwitchBankTutorialOverlayIfOpen())
        {
            return;
        }

        for (String pickaxeName : Buy.PICKAXE_NAMES)
        {
            if (Rs2Inventory.hasItem(pickaxeName))
            {
                Rs2Bank.depositAll(pickaxeName);
                sleep(100);
            }
        }

        for (String axeName : Buy.AXE_NAMES)
        {
            if (Rs2Inventory.hasItem(axeName))
            {
                Rs2Bank.depositAll(axeName);
                sleep(100);
            }
        }
    }

    private boolean closeTaskSwitchBankTutorialOverlayIfOpen()
    {
        if (!KspBankWidgetHelper.closeBankTutorialOverlayIfOpen())
        {
            return false;
        }

        sleep(300);
        if (Rs2Bank.isOpen())
        {
            debug("Closed bank tutorial overlay during task switch; resetting bank");
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
        }

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

    public boolean isAnyBreakActive()
    {
        return breakActive || sharedBreakActive;
    }

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
        if (Rs2Player.getWorldLocation() == null)
        {
            return false;
        }

        if (currentTask == BuilderTask.TUTORIAL_ISLAND)
        {
            return false;
        }

        if (currentTask == BuilderTask.STRONGHOLD_OF_SECURITY)
        {
            return true;
        }

        if (currentTask == BuilderTask.COOKS_ASSISTANT
                || currentTask == BuilderTask.GOBLIN_DIPLOMACY
                || currentTask == BuilderTask.ROMEO_AND_JULIET
                || currentTask == BuilderTask.RUNE_MYSTERIES)
        {
            return true;
        }

        if (currentTask == BuilderTask.RUNE_ESSENCE)
        {
            return essenceMining.isInTaskArea();
        }

        if (currentTask == BuilderTask.MINING)
        {
            return miningScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.WOODCUTTING)
        {
            return woodCuttingScript.getTargetArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.FIREMAKING)
        {
            return fireMakingScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.FISHING)
        {
            return fishingScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.COOKING)
        {
            return cookingScript.getTargetArea().getArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.CRAFTING)
        {
            return true;
        }

        if (currentTask == BuilderTask.MELEE)
        {
            return meleeScript.getTargetArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.GE_SELL)
        {
            return sellScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.GE_BUY)
        {
            return buyScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        if (currentTask == BuilderTask.SMITHING)
        {
            return smithScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
        }

        return smeltScript.getTargetArea().toWorldArea().contains(Rs2Player.getWorldLocation());
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
                accountPlayTimeCache.getPlayTimeMillis(getCurrentAccountHash()));
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
            accountPlayTimeCache.sample(Microbot.isLoggedIn(), getCurrentAccountHash());
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

        if (miningScript != null)
        {
            miningScript.shutdown();
        }

        if (woodCuttingScript != null)
        {
            woodCuttingScript.shutdown();
        }

        if (fireMakingScript != null)
        {
            fireMakingScript.shutdown();
        }

        if (fishingScript != null)
        {
            fishingScript.shutdown();
        }

        if (cookingScript != null)
        {
            cookingScript.shutdown();
        }

        if (craftingScript != null)
        {
            craftingScript.shutdown();
        }

        if (meleeScript != null)
        {
            meleeScript.shutdown();
        }

        if (buyScript != null)
        {
            buyScript.shutdown();
        }

        if (sellScript != null)
        {
            sellScript.shutdown();
        }

        if (smithScript != null)
        {
            smithScript.shutdown();
        }

        if (smeltScript != null)
        {
            smeltScript.shutdown();
        }

        if (tutorialIslandScript != null)
        {
            tutorialIslandScript.shutdown();
        }

        if (cooksScript != null)
        {
            cooksScript.shutdown();
        }

        if (gobScript != null)
        {
            gobScript.shutdown();
        }

        if (romeoScript != null)
        {
            romeoScript.shutdown();
        }

        if (runeMystScript != null)
        {
            runeMystScript.shutdown();
        }

        if (essenceMining != null)
        {
            essenceMining.shutdown();
        }

        if (strongholdScript != null)
        {
            strongholdScript.shutdown();
        }

        if (autoLoginScript != null)
        {
            autoLoginScript.shutdown();
        }

        if (experienceLampScript != null)
        {
            experienceLampScript.shutdown();
        }

        Rs2Antiban.resetAntibanSettings(true);
    }

    private void sampleAccountPlayTime()
    {
        boolean loggedIn = Microbot.isLoggedIn();
        if (loggedIn && (TutorialIslandScript.isPreTutorialBlockingWidgetOpen() || TutorialIslandScript.isInTutorialIslandArea() || TutorialIslandScript.isOnTutorialIsland()))
        {
            synchronizedPlayTimeAccountHash = 0L;
            nextPlayTimeReadAtMillis = 0L;
            return;
        }

        long accountHash = getCurrentAccountHash();
        accountPlayTimeCache.sample(loggedIn, accountHash);

        if (loggedIn
                && accountHash != 0L
                && synchronizedPlayTimeAccountHash != 0L
                && accountHash != synchronizedPlayTimeAccountHash)
        {
            if (currentTask != null)
            {
                stopCurrentTaskScript();
            }
            taskStarted = false;
            currentTask = null;
            pendingTask = null;
            pendingRandomTaskSelection = false;
            awaitingNextActivityStart = false;
            awaitingActivitySwitchTimerStart = false;
            nextActivitySwitchAtMillis = -1L;
            synchronizedPlayTimeAccountHash = 0L;
        nextPlayTimeReadAtMillis = 0L;
        taskSwitchBankLocation = null;
        debug("Account changed; stopped task selection until play time is confirmed");
        }

        if (!loggedIn)
        {
            return;
        }

        if (accountHash == 0L)
        {
            KspTaskDebug.throttled(log, true, "Builder", "play-time-account-hash", 5_000L,
                    "Waiting to read account play time | reason=account-hash-unavailable");
            return;
        }

        if (accountHash == synchronizedPlayTimeAccountHash)
        {
            return;
        }

        if (!isReadyAfterLoginHandoff())
        {
            return;
        }

        if (accountPlayTimeCache.hasCachedPlayTime(accountHash))
        {
            synchronizedPlayTimeAccountHash = accountHash;
            nextPlayTimeReadAtMillis = 0L;
            log.info("[KSP Builder] Using cached account play time | accountHash={} minutes={}; skipping widget read",
                    Long.toUnsignedString(accountHash),
                    TimeUnit.MILLISECONDS.toMinutes(accountPlayTimeCache.getPlayTimeMillis(accountHash)));
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextPlayTimeReadAtMillis)
        {
            return;
        }

        log.info("[KSP Builder] Reading account play time | accountHash={}",
                Long.toUnsignedString(accountHash));
        long playTimeMillis = TradeUnlock.readPlayTimeMillis();
        if (playTimeMillis < 0L)
        {
            nextPlayTimeReadAtMillis = now + PLAY_TIME_READ_RETRY_MS;
            log.info("[KSP Builder] Account play-time read failed; retrying in {} seconds",
                    TimeUnit.MILLISECONDS.toSeconds(PLAY_TIME_READ_RETRY_MS));
            return;
        }

        accountPlayTimeCache.synchronizePlayTimeMillis(accountHash, playTimeMillis);
        synchronizedPlayTimeAccountHash = accountHash;
        nextPlayTimeReadAtMillis = 0L;
        log.info("[KSP Builder] Synchronized account play time | accountHash={} minutes={}",
                Long.toUnsignedString(accountHash),
                TimeUnit.MILLISECONDS.toMinutes(playTimeMillis));
    }

    private boolean isPlayTimeConfirmedForCurrentAccount()
    {
        if (Microbot.isLoggedIn() && (TutorialIslandScript.isPreTutorialBlockingWidgetOpen() || TutorialIslandScript.isInTutorialIslandArea() || TutorialIslandScript.isOnTutorialIsland()))
        {
            return true;
        }

        long accountHash = getCurrentAccountHash();
        return accountHash != 0L && accountHash == synchronizedPlayTimeAccountHash;
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
