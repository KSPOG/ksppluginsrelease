package net.runelite.client.plugins.microbot.kspmadcow;


import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;
import net.runelite.api.Actor;
import net.runelite.api.EnumComposition;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.EnumID;
import net.runelite.api.GraphicsObject;
import net.runelite.api.MenuAction;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.NPC;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class KspMadCowScript extends Script {
    private static final Logger log = LoggerFactory.getLogger(KspMadCowScript.class);
    public enum CombatMode {
        MELEE("Melee"),
        RANGED("Ranged"),
        MAGIC("Magic");

        private final String displayName;

        CombatMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private enum SpecialAttack {
        NONE,
        CHARGE,
        STOMP
    }

    public static final int BRUTUS_ID = 15626;
    public static final int BRUTUS_ALT_ID = 15627;
    public static final int DEMONIC_BRUTUS_ID = 15628;
    public static final int DEMONIC_BRUTUS_GHOST_ID = 15629;
    public static final int ABYSSAL_POTATO_ID = 33118;
    public static final int MOOLETA_ID = 33101;
    public static final int COWBELL_EMPTY_ID = 33103;
    public static final int COWBELL_CHARGED_ID = 33104;
    public static final int AIR_RUNE_ID = 556;
    public static final int ALTAR_ID = 409;
    public static final int POOL_OF_REFRESHMENT_ID = 39651;
    /** Ferox Enclave bank chest shown by Object ID Examiner. */
    private static final int FEROX_BANK_CHEST_ID = 26711;
    /** Death's Office exit portal. Used only as a recovery guard if Ferox pathing ever enters Death's Domain. */
    private static final int DEATHS_OFFICE_EXIT_PORTAL_ID = 39549;

    private static final WorldPoint ALTAR_LOCATION = new WorldPoint(3243, 3207, 0);
    private static final WorldArea ALTAR_AREA = new WorldArea(new WorldPoint(3240, 3204, 0), 8, 6);

    private static final String LMS_MINIGAME_NAME = "Last Man Standing";
    private static final WorldPoint LMS_TELEPORT_DESTINATION = new WorldPoint(3150, 3635, 0);
    private static final WorldPoint POOL_OF_REFRESHMENT_LOCATION = new WorldPoint(3128, 3633, 0);
    private static final WorldArea FEROX_ENCLAVE_AREA = new WorldArea(new WorldPoint(3118, 3616, 0), 55, 40);
    // Do not use Rs2Bank.walkToBank() inside Ferox. The generic nearest-bank planner can
    // choose an unintended route through Death's Domain. Keep the Ferox route pinned to
    // the actual enclave bank tile instead.
    private static final WorldPoint FEROX_BANK_LOCATION = new WorldPoint(3130, 3631, 0);
    private static final WorldArea FEROX_BANK_AREA =
            new WorldArea(new WorldPoint(3124, 3626, 0), 13, 11);
    // Microbot BankLocation.LUMBRIDGE_TOP points to this bank on the second floor.
    // Keep the target local to the plugin so this fallback does not depend on bank
    // planner heuristics selecting a different nearby bank.
    private static final WorldPoint LUMBRIDGE_BANK_LOCATION = new WorldPoint(3209, 3220, 2);
    private static final WorldArea LUMBRIDGE_BANK_AREA =
            new WorldArea(new WorldPoint(3204, 3215, 2), 12, 11);
    // Brutus dodge movement remains collision/walkability checked, but no longer
    // uses a user-defined rectangular click boundary.

    // Since the 11 March 2026 OSRS update, Minigame Teleport is available directly
    // from every spellbook. Use the spellbook entry and the dedicated Minigames menu
    // instead of the legacy Chat Channel -> Grouping dropdown flow.
    private static final String MINIGAME_TELEPORT_SPELL_NAME = "Minigame Teleport";
    private static final String MINIGAME_TELEPORT_MENU_TITLE = "Minigames";
    // Current post-March-2026 Minigame Teleport interface. Widget Inspector shows the
    // destination cards under group 951 (for example LMS currently appears as 951.16),
    // with the visible scroll viewport rooted at child 3. Do not hardcode LMS to child 16:
    // search the group by its text because card ordering can change.
    private static final int MINIGAME_TELEPORT_WIDGET_GROUP = 951;
    private static final int MINIGAME_TELEPORT_CONTENT_CHILD = 3;
    private static final int MINIGAME_TELEPORT_LIST_CHILD = 4;
    private static final int MINIGAME_TELEPORT_SCROLLBAR_CHILD = 26;
    private static final int SPELLBOOK_WIDGET_GROUP = 218;
    private static final int SPELLBOOK_WIDGET_CHILD = 3;
    private static final int LMS_TELEPORT_RETRY_TICKS = 30;
    /** Mouse-wheel throttle while the new Minigames window is open and LMS is below the fold. */
    private static final long MINIGAME_MENU_SCROLL_INTERVAL_MS = 350L;
    private static final int REQUIRED_INITIAL_MINIGAME_SCROLLS = 2;
    private static final int MAX_MINIGAME_SCROLL_ATTEMPTS = 6;
    private static final long MINIGAME_TELEPORT_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(20);
    private static final int POOL_REFRESH_SETTLE_TICKS = 5;
    /**
     * After the Pool animation ends, require two complete idle game ticks before Cowbell Teleport.
     * Movement is not represented by the player animation id, so this separately prevents an
     * already-queued Ferox movement click from carrying the player into Death's Domain.
     */
    private static final int POOL_POST_ANIMATION_IDLE_TICKS = 2;
    /** Brief post-refresh grace period so the pool interaction settles before Cowbell Teleport. */
    private static final long COWBELL_AFTER_POOL_DELAY_MS = 450L;
    /** If no Pool animation is ever observed, retry Drink instead of allowing Cowbell travel. */
    private static final int POOL_ANIMATION_START_TIMEOUT_TICKS = 8;
    /** Throttle the direct current-tile click used to cancel residual Ferox movement. */
    private static final long FEROX_MOVEMENT_STOP_RETRY_MS = 600L;
    /** After one direct Ferox bank-chest click, wait for the bank to open before retrying. */
    private static final long FEROX_BANK_OPEN_RETRY_MS = 4_000L;
    /** Retry interval for the emergency Death's Office exit portal. */
    private static final int DEATHS_OFFICE_EXIT_RETRY_TICKS = 3;
    /**
     * Keep re-issuing Quick-escape while RuneLite still confirms the Brutus instance.
     * One accepted menu invoke is not proof that the transition actually happened.
     */
    private static final long QUICK_ESCAPE_RETRY_INTERVAL_MS = 1_500L;
    /** Re-issue Cowbell Teleport if the previous click was not confirmed by an actual teleport. */
    private static final int COWBELL_TRAVEL_RETRY_TICKS = 2;
    /** Hard wall-clock cooldown between Cowbell Teleport attempts to prevent retry spam after the Pool. */
    private static final long COWBELL_TRAVEL_RETRY_COOLDOWN_MS = 2_500L;
    /** Polling interval only; action execution itself has no artificial cooldowns. */
    private static final long LOOP_INTERVAL_MS = 100L;

    private static final String BRUTUS_NAME = "Brutus";
    private static final String COWBELL_TRAVEL_ACTION = "Teleport";
    private static final String COWBELL_SPEED_ACTION = "Ring";
    private static final long COWBELL_SPEED_RING_RETRY_COOLDOWN_MS = 4_000L;
    private static final int COWBELL_SPEED_RING_MAX_ATTEMPTS = 3;
    /**
     * Brutus pen entry action only. The only valid entry interaction is "Release".
     * Do NOT include generic/alternate actions such as "Enter" or "Quick-enter" here:
     * Death's Domain entrances use "Enter" and exist both beside the Ferox bank/pool
     * and in Lumbridge. Treating every visible Enter object as the Brutus pen caused
     * the plugin to invoke Death's Domain instead of using Cowbell Teleport.
     */
    private static final String[] PEN_ENTRY_ACTIONS = {"Release"};
    /** Death's Domain entrances observed by Object Examiner; never valid Brutus pen objects. */
    private static final Set<Integer> DEATHS_DOMAIN_ENTRANCE_IDS = Set.of(38426, 39637);
    private static final int PEN_ENTRY_RETRY_TICKS = 4;
    private static final String[] QUICK_ESCAPE_ACTIONS = {"Quick-escape", "Quick escape"};
    private static final String[] FEROX_BANK_CHEST_ACTIONS = {"Use", "Bank", "Open"};

    private static final Set<Integer> CHARGE_ANIMATIONS = Set.of(
            AnimationID.COW_BOSS_HEAVY_BREATH,
            AnimationID.COW_BOSS_CHARGE,
            AnimationID.COW_BOSS_GHOST_CHARGE,
            AnimationID.COW_BOSS_CHARGING_FADE_IN_FADE_OUT,
            AnimationID.COW_BOSS_GHOST_CHARGING_FADE_IN_FADE_OUT,
            AnimationID.COW_BOSS_CHARGE_START_FADE_OUT,
            AnimationID.COW_BOSS_GHOST_CHARGE_START_FADE_OUT,
            AnimationID.COW_BOSS_CHARGE_END_FADE_IN,
            AnimationID.COW_BOSS_GHOST_CHARGE_END_FADE_IN
    );

    private static final Set<Integer> STOMP_ANIMATIONS = Set.of(
            AnimationID.COW_BOSS_STOMP,
            AnimationID.COW_BOSS_GHOST_STOMP,
            AnimationID.COW_BOSS_SPIRIT_STOMP,
            AnimationID.COW_BOSS_GHOST_SPIRIT_STOMP,
            AnimationID.COW_BOSS_STOMP_FADE_IN,
            AnimationID.COW_BOSS_GHOST_STOMP_FADE_IN
    );

    private static final Set<Integer> STOMP_GRAPHICS = Set.of(
            SpotanimID.VFX_COWBOSS_STOMP_IMPACT01,
            SpotanimID.VFX_COWBOSS_STOMP_IMPACT02,
            SpotanimID.VFX_COWBOSS_STOMP_IMPACT03
    );

    /**
     * Demonic Brutus uses the same core Charge/Stomp animations plus ghost copies.
     * Only the primary attack animations below are allowed to start a new hard-mode
     * dodge step; fade/transition animations merely refine the active geometry.
     */
    private static final Set<Integer> DEMONIC_SPECIAL_STEP_ANIMATIONS = Set.of(
            AnimationID.COW_BOSS_CHARGE,
            AnimationID.COW_BOSS_GHOST_CHARGE,
            AnimationID.COW_BOSS_STOMP,
            AnimationID.COW_BOSS_GHOST_STOMP,
            AnimationID.COW_BOSS_SPIRIT_STOMP,
            AnimationID.COW_BOSS_GHOST_SPIRIT_STOMP
    );

    /** Hard-mode phase-two prayer projectile launch graphics. */
    private static final int DEMONIC_RANGED_LAUNCH_GRAPHIC = SpotanimID.VFX_COWBOSS_HM_LAUNCH_RANGED_01;
    private static final int DEMONIC_MELEE_LAUNCH_GRAPHIC = SpotanimID.VFX_COWBOSS_HM_LAUNCH_MELEE_01;
    private static final int DEMONIC_MAGIC_LAUNCH_GRAPHIC = SpotanimID.VFX_COWBOSS_HM_LAUNCH_MAGIC_01;
    private static final int DEMONIC_PRAYER_OVERRIDE_TICKS = 2;
    private static final int DEMONIC_FEED_RETRY_TICKS = 5;
    private static final int DEMONIC_TRANSFORM_TIMEOUT_TICKS = 20;

    /** Retain marker tiles long enough to cover one complete Stomp sequence. */
    private static final int STOMP_GRAPHICS_LIFETIME_TICKS = 6;
    /** Do not issue a second Stomp re-attack while the first click is being registered. */
    private static final int STOMP_REATTACK_CONFIRM_TICKS = 2;
    /** Retry a dodge on the very next game tick if the first local scene-walk made no progress. */
    private static final int SPECIAL_MOVEMENT_RETRY_STALL_TICKS = 1;
    /** A single dodge may issue at most two requests to one target before using the other candidate. */
    private static final int SPECIAL_MOVEMENT_ATTEMPTS_PER_TARGET = 2;

    private static final String[] MELEE_COMBINED_POTIONS = {
            "Super combat potion", "Combat potion", "Divine super combat potion"
    };
    private static final String[] ATTACK_POTIONS = {
            "Super attack", "Attack potion", "Divine super attack potion"
    };
    private static final String[] STRENGTH_POTIONS = {
            "Super strength", "Strength potion", "Divine super strength potion"
    };
    private static final String[] DEFENCE_POTIONS = {
            "Super defence", "Defence potion", "Divine super defence potion"
    };
    private static final String[] RANGED_POTIONS = {
            "Ranging potion", "Bastion potion", "Divine ranging potion", "Divine bastion potion"
    };
    private static final String[] MAGIC_POTIONS = {
            "Magic potion", "Battlemage potion", "Divine magic potion", "Divine battlemage potion"
    };

    private static final WidgetInfo[] MELEE_STYLE_WIDGETS = {
            WidgetInfo.COMBAT_STYLE_ONE,
            WidgetInfo.COMBAT_STYLE_TWO,
            WidgetInfo.COMBAT_STYLE_THREE,
            WidgetInfo.COMBAT_STYLE_FOUR
    };

    private static final Rs2PrayerEnum[] MANAGED_COMBAT_PRAYERS = {
            Rs2PrayerEnum.CLARITY_THOUGHT,
            Rs2PrayerEnum.IMPROVED_REFLEXES,
            Rs2PrayerEnum.INCREDIBLE_REFLEXES,
            Rs2PrayerEnum.SUPERHUMAN_STRENGTH,
            Rs2PrayerEnum.ULTIMATE_STRENGTH,
            Rs2PrayerEnum.SHARP_EYE,
            Rs2PrayerEnum.HAWK_EYE,
            Rs2PrayerEnum.EAGLE_EYE,
            Rs2PrayerEnum.MYSTIC_WILL,
            Rs2PrayerEnum.MYSTIC_LORE,
            Rs2PrayerEnum.MYSTIC_MIGHT,
            Rs2PrayerEnum.PROTECT_MAGIC,
            Rs2PrayerEnum.PROTECT_RANGE,
            Rs2PrayerEnum.PROTECT_MELEE
    };

    private KspMadCowConfig config;
    private Runnable stopRequest;

    private volatile KspMadCowState state = KspMadCowState.STOPPED;
    private volatile Skill trainingSkill = Skill.ATTACK;
    private volatile int killCount;
    private volatile String lastMessage = "Stopped";
    private volatile CombatMode combatMode = CombatMode.MELEE;
    private volatile String activePrayerSummary = "None";
    private volatile boolean brutusAlive;
    private volatile int trackedRangedAmmoId = -1;
    private volatile String trackedRangedAmmoName = "";
    private int lastCombatGearWeaponId = Integer.MIN_VALUE;
    private int lastCombatGearAmmoId = Integer.MIN_VALUE;
    private CombatMode lastLoggedCombatMode;

    private volatile boolean running;
    private boolean initialBankCheckPending = true;
    private volatile boolean instanceConfirmed;
    private boolean clientInstanceDetected;
    private boolean travelRequired = true;
    private boolean cowbellTravelIssued;
    /** A Cowbell Teleport click was sent, but the world-position change has not yet confirmed it. */
    private boolean cowbellTravelPending;
    private int cowbellTravelAttemptTick = Integer.MIN_VALUE;
    private int cowbellTravelAttemptCount;
    private long cowbellTravelLastAttemptAtMs;
    private WorldPoint cowbellTravelOrigin;
    private long cowbellChargeAttemptRevision = -1L;
    private int cowbellChargeAttemptTick = Integer.MIN_VALUE;
    private boolean cowbellChargeInputPending;
    private boolean cowbellChargeInputSubmitted;
    private int cowbellChargeInputAmount;
    private int cowbellChargeInputSubmitTick = Integer.MIN_VALUE;
    private String lastPenEntryAction = "";
    private int lastPenEntryAttemptTick = Integer.MIN_VALUE;
    private boolean leavingInstance;
    /** True once at least one Quick-escape interaction has been issued for this leave transaction. */
    private boolean quickLeaveClickIssued;
    /** Wall-clock time of the last Quick-escape attempt; used to retry until the instance is actually gone. */
    private long quickLeaveLastAttemptAtMs;
    private boolean altarInteractionIssued;
    private boolean bankActionPending;
    private long bankActionInventoryRevision;
    private String bankActionDescription = "";
    private BooleanSupplier bankActionComplete;
    /** A direct click on Ferox bank chest 26711 was issued; do not spam it while the player approaches/opens it. */
    private boolean feroxBankOpenPending;
    private long feroxBankOpenIssuedAtMs;
    private boolean lmsBankTeleportIssued;
    private int lmsBankTeleportIssuedTick = Integer.MIN_VALUE;
    private long lastMinigameMenuScrollAtMs;
    private int minigameMenuScrollCount;
    /** Confirmed by a game message; shared between RuneLite client thread and script executor. */
    private volatile long minigameTeleportCooldownUntilMs;
    /** Current banking cycle has abandoned Ferox and is explicitly targeting Lumbridge top bank. */
    private boolean lumbridgeFallbackBanking;
    /**
     * A cooldown-fallback bank cycle completed at Lumbridge. Before Cowbell travel, route
     * through the original Lumbridge altar coordinates and restore any missing Prayer.
     * This is intentionally independent of the normal "only when Prayer is zero" path.
     */
    private boolean lumbridgePostBankAltarPending;
    private boolean postBankRefreshPending;
    /** Pool finished at Ferox; finish local Cowbell preparation without invoking any walker movement. */
    private boolean feroxPostPoolTravelPending;
    private boolean poolRefreshIssued;
    private int poolRefreshIssuedTick = Integer.MIN_VALUE;
    private int poolPrayerPointsBeforeInteraction = -1;
    private boolean poolPrayerWasFullBeforeInteraction;
    /** True once the local player's Pool of Refreshment animation has actually been observed. */
    private boolean poolAnimationObserved;
    /** First game tick after the Pool animation ended with the player fully stationary. */
    private int poolPostAnimationIdleTick = Integer.MIN_VALUE;
    /** Last direct current-tile click used to cancel residual Ferox movement. */
    private long lastFeroxMovementStopAtMs;
    /** Last emergency click on Death's Office exit portal. */
    private int lastDeathsOfficeExitAttemptTick = Integer.MIN_VALUE;
    private long poolPrayerRestoredAtMs;
    private boolean suppliesErrorShown;
    private boolean altarRestorePending;
    private int altarPrayerPointsBeforeInteraction = -1;
    private boolean altarTeleportPending;
    private boolean combatReacquirePending;
    private int lastCombatAttackTick = Integer.MIN_VALUE;
    private boolean healActionPending;
    private long healActionInventoryRevision = -1L;
    private int healActionHpBefore = -1;
    private int healActionIssuedTick = Integer.MIN_VALUE;
    private boolean killArmed;
    private boolean speedRingPending;
    private boolean speedRingPrioritySatisfied = true;
    private int speedRingAttempts;
    private long speedRingLastAttemptAtMs;
    private volatile boolean prayersSuppressedUntilRespawn;
    private volatile NPC lastKnownBrutusNpc;
    /** Client-thread snapshots used by dodge logic; never block the script thread waiting for RuneLite. */
    private volatile WorldArea lastKnownBrutusArea;
    private volatile int lastKnownBrutusOrientation = -1;
    private volatile int cachedGameTick = Integer.MIN_VALUE;
    private volatile WorldPoint cachedCombatScenePlayerLocation;

    // Demonic Brutus access/attempt state. One Abyssal potato is consumed for each
    // hard-mode attempt, so the script leaves after a completed attempt and banks
    // for a fresh potato before entering again.
    private boolean demonicFeedPending;
    private int demonicFeedIssuedTick = Integer.MIN_VALUE;
    private int demonicFeedStartedTick = Integer.MIN_VALUE;
    private boolean demonicAttemptActive;
    private boolean demonicAttemptCompleted;
    private volatile Rs2PrayerEnum demonicProtectionPrayer;
    private volatile int demonicProtectionPrayerUntilTick = Integer.MIN_VALUE;

    private volatile SpecialAttack activeSpecial = SpecialAttack.NONE;
    private SpecialAttack pendingSpecial = SpecialAttack.NONE;
    private boolean pendingSpecialFromAnimation;
    private int pendingSpecialAnimation = -1;
    private WorldArea pendingSpecialArea;
    private int pendingSpecialOrientation = -1;
    private SpecialAttack cuePrimedAttack = SpecialAttack.NONE;
    private int cuePrimedTick = Integer.MIN_VALUE;
    private boolean chargeSequenceLatched;
    private boolean chargeSequenceAnimationObserved;
    private boolean stompSequenceLatched;
    private boolean specialAnimationObserved;
    private boolean specialMovementAttempted;
    private int lastSpecialMovementTick = Integer.MIN_VALUE;
    private int specialMovementIssueCount;
    private int specialMovementTargetAttempts;
    private int specialMovementLastProgressTick = Integer.MIN_VALUE;
    private WorldPoint specialMovementLastPlayerLocation;
    private final Set<WorldPoint> specialMovementIssuedTargets = new HashSet<>();
    private boolean specialTargetReached;
    private WorldPoint specialDodgeTarget;
    private List<WorldPoint> specialDodgeCandidates = List.of();
    private int specialCandidateIndex;
    private int currentBrutusAnimation = -1;
    private int currentBrutusAnimationFrame = -1;
    private int lastObservedBrutusAnimation = Integer.MIN_VALUE;
    private int lastObservedBrutusFrame = -1;
    private String lastObservedOverheadCue = "";
    private int lastSpecialReattackTick = Integer.MIN_VALUE;
    private int activeSpecialStartedTick = Integer.MIN_VALUE;
    private int stompActivationTick = Integer.MIN_VALUE;
    private boolean stompReattackIssued;
    private int stompReattackIssuedTick = Integer.MIN_VALUE;
    private int lastCombatBlockedLogTick = Integer.MIN_VALUE;
    private String lastCombatBlockedReason = "";
    private int lastRangedAmmoEquipTick = Integer.MIN_VALUE;
    private int lastHealDebugTick = Integer.MIN_VALUE;
    private int lastPotionDebugTick = Integer.MIN_VALUE;

    private final ConcurrentHashMap<WorldPoint, Integer> stompImpactTileTicks = new ConcurrentHashMap<>();
    private final AtomicLong inventoryRevision = new AtomicLong();
    private long startedAtNanos;
    private long stoppedElapsedMillis;

    private volatile OverlaySnapshot overlaySnapshot = OverlaySnapshot.stopped();

    @Inject
    public KspMadCowScript() {
    }

    public synchronized boolean run(KspMadCowConfig config, Runnable stopRequest) {
        if (running) {
            return false;
        }

        if (scheduledExecutorService == null || scheduledExecutorService.isShutdown()) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ScriptThreadFactory());
        }

        this.config = Objects.requireNonNull(config);
        this.stopRequest = stopRequest;
        resetForStart();
        Microbot.enableAutoRunOn = config.autoRun();
        visibleDebug("Lifecycle", "KSP Mad Cow v" + KspMadCowPlugin.VERSION
                + " started; combat movement mode=canvas-only; minimap disabled");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!running || Thread.currentThread().isInterrupted()) {
                return;
            }

            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    refreshOverlaySnapshot();
                    return;
                }
                tick();
            } catch (Exception ex) {
                setState(KspMadCowState.ERROR, "Unexpected error: " + ex.getClass().getSimpleName());
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            } finally {
                refreshOverlaySnapshot();
            }
        }, 0L, LOOP_INTERVAL_MS, TimeUnit.MILLISECONDS);

        return true;
    }

    private void resetForStart() {
        state = KspMadCowState.STARTING;
        trainingSkill = Skill.ATTACK;
        killCount = 0;
        lastMessage = "Starting";
        combatMode = CombatMode.MELEE;
        activePrayerSummary = "None";
        brutusAlive = false;
        trackedRangedAmmoId = -1;
        trackedRangedAmmoName = "";
        lastCombatGearWeaponId = Integer.MIN_VALUE;
        lastCombatGearAmmoId = Integer.MIN_VALUE;
        lastLoggedCombatMode = null;
        running = true;
        initialBankCheckPending = true;
        instanceConfirmed = false;
        clientInstanceDetected = false;
        travelRequired = true;
        cowbellTravelIssued = false;
        cowbellTravelPending = false;
        cowbellTravelAttemptTick = Integer.MIN_VALUE;
        cowbellTravelAttemptCount = 0;
        cowbellTravelLastAttemptAtMs = 0L;
        cowbellTravelOrigin = null;
        cowbellChargeAttemptRevision = -1L;
        cowbellChargeAttemptTick = Integer.MIN_VALUE;
        clearCowbellChargeInputState();
        lastPenEntryAction = "";
        lastPenEntryAttemptTick = Integer.MIN_VALUE;
        leavingInstance = false;
        quickLeaveClickIssued = false;
        quickLeaveLastAttemptAtMs = 0L;
        altarInteractionIssued = false;
        bankActionPending = false;
        bankActionDescription = "";
        bankActionComplete = null;
        resetFeroxBankOpenState();
        lmsBankTeleportIssued = false;
        lmsBankTeleportIssuedTick = Integer.MIN_VALUE;
        lastMinigameMenuScrollAtMs = 0L;
        minigameMenuScrollCount = 0;
        minigameTeleportCooldownUntilMs = 0L;
        lumbridgeFallbackBanking = false;
        lumbridgePostBankAltarPending = false;
        postBankRefreshPending = false;
        feroxPostPoolTravelPending = false;
        poolRefreshIssued = false;
        poolRefreshIssuedTick = Integer.MIN_VALUE;
        poolPrayerPointsBeforeInteraction = -1;
        poolPrayerWasFullBeforeInteraction = false;
        poolAnimationObserved = false;
        poolPostAnimationIdleTick = Integer.MIN_VALUE;
        lastFeroxMovementStopAtMs = 0L;
        lastDeathsOfficeExitAttemptTick = Integer.MIN_VALUE;
        poolPrayerRestoredAtMs = 0L;
        suppliesErrorShown = false;
        altarRestorePending = false;
        altarPrayerPointsBeforeInteraction = -1;
        altarTeleportPending = false;
        combatReacquirePending = false;
        lastCombatAttackTick = Integer.MIN_VALUE;
        clearHealActionState();
        killArmed = false;
        speedRingPending = false;
        speedRingPrioritySatisfied = true;
        speedRingAttempts = 0;
        speedRingLastAttemptAtMs = 0L;
        prayersSuppressedUntilRespawn = false;
        lastKnownBrutusNpc = null;
        lastKnownBrutusArea = null;
        lastKnownBrutusOrientation = -1;
        cachedGameTick = Integer.MIN_VALUE;
        cachedCombatScenePlayerLocation = null;
        demonicFeedPending = false;
        demonicFeedIssuedTick = Integer.MIN_VALUE;
        demonicFeedStartedTick = Integer.MIN_VALUE;
        demonicAttemptActive = false;
        demonicAttemptCompleted = false;
        demonicProtectionPrayer = null;
        demonicProtectionPrayerUntilTick = Integer.MIN_VALUE;
        clearSpecialAvoidance();
        pendingSpecial = SpecialAttack.NONE;
        cuePrimedAttack = SpecialAttack.NONE;
        cuePrimedTick = Integer.MIN_VALUE;
        chargeSequenceLatched = false;
        chargeSequenceAnimationObserved = false;
        stompSequenceLatched = false;
        currentBrutusAnimation = -1;
        currentBrutusAnimationFrame = -1;
        lastObservedBrutusAnimation = Integer.MIN_VALUE;
        lastObservedBrutusFrame = -1;
        lastObservedOverheadCue = "";
        lastSpecialReattackTick = Integer.MIN_VALUE;
        activeSpecialStartedTick = Integer.MIN_VALUE;
        lastSpecialMovementTick = Integer.MIN_VALUE;
        specialMovementIssueCount = 0;
        specialMovementTargetAttempts = 0;
        specialMovementLastProgressTick = Integer.MIN_VALUE;
        specialMovementLastPlayerLocation = null;
        specialMovementIssuedTargets.clear();
        stompActivationTick = Integer.MIN_VALUE;
        stompReattackIssued = false;
        stompReattackIssuedTick = Integer.MIN_VALUE;
        lastCombatBlockedLogTick = Integer.MIN_VALUE;
        lastCombatBlockedReason = "";
        lastRangedAmmoEquipTick = Integer.MIN_VALUE;
        lastHealDebugTick = Integer.MIN_VALUE;
        lastPotionDebugTick = Integer.MIN_VALUE;
        stompImpactTileTicks.clear();
        startedAtNanos = System.nanoTime();
        stoppedElapsedMillis = 0L;
        overlaySnapshot = OverlaySnapshot.starting();
    }

    private void tick() {
        if (!running) {
            return;
        }

        pruneStompImpactTiles();

        // The Cowbell charge quantity prompt is a chatbox numeric input, not a
        // normal dialogue option. Handle it before every other script action so
        // banking/travel logic cannot run while the prompt is waiting for input.
        if (handleCowbellChargeAmountInput()) {
            return;
        }

        // Burying some bones can show the one-time warning:
        //   Bury the bones? (This grants Prayer XP)
        //   1. Yes.
        //   2. Yes, and don't ask again.
        // Always take option 2 when automatic bone burying is enabled. This must
        // run before the generic Brutus-entry dialogue handler because a partial
        // match for "Yes" would otherwise select option 1.
        if (handleBoneBuryDialogue()) {
            return;
        }

        // Feeding the Abyssal potato opens its own confirmation prompt inside the
        // Brutus instance. Handle that independently from the pen-entry setting:
        // enabling Demonic Brutus means the hard-mode confirmation is intentional.
        if (handleDemonicFeedDialogue()) {
            return;
        }

        if (handleEntryDialogue()) {
            return;
        }

        if (config.autoRetaliate()) {
            Rs2Combat.setAutoRetaliate(true);
        }

        combatMode = detectCombatMode();
        Rs2NpcModel brutus = findBrutus();
        boolean visibleBossAlive = isAlive(brutus);
        ObjectAction quickLeave = findObjectAction(QUICK_ESCAPE_ACTIONS);
        clientInstanceDetected = isClientInInstancedRegion();

        if (leavingInstance) {
            handleLeavingInstance(quickLeave);
            return;
        }

        // TopLevelWorldView#isInstance() is generic and is true for many unrelated
        // instanced areas (for example Death's Domain). Never treat that flag alone
        // as proof that we are inside the Brutus encounter. Require Brutus-specific
        // evidence, or retain an already-confirmed Brutus instance only while the
        // player is still mapped into the Brutus destination/template area.
        boolean insideInstance = isBrutusInstanceContext(quickLeave, brutus);
        if (insideInstance) {
            boolean newlyEnteredInstance = !instanceConfirmed;
            instanceConfirmed = true;
            if (newlyEnteredInstance) {
                resetDemonicAttemptForInstance();
            }
            travelRequired = false;
            cowbellTravelIssued = false;
            lastPenEntryAction = "";
            lastPenEntryAttemptTick = Integer.MIN_VALUE;
            brutusAlive = visibleBossAlive;
            if (brutus != null && brutus.getNpc() != null) {
                lastKnownBrutusNpc = brutus.getNpc();
            }
            if (visibleBossAlive) {
                // A live Brutus means the previous fast-respawn transaction is complete.
                speedRingPending = false;
                speedRingPrioritySatisfied = true;
                speedRingAttempts = 0;
                speedRingLastAttemptAtMs = 0L;
                if (config.demonicBrutus()) {
                    demonicAttemptActive = true;
                    demonicFeedPending = false;
                    demonicFeedIssuedTick = Integer.MIN_VALUE;
                    demonicFeedStartedTick = Integer.MIN_VALUE;
                }
                killArmed = true;
                // ActorDeath suppresses combat prayers for the whole respawn gap. A
                // newly alive Brutus is the only state that releases that suppression.
                prayersSuppressedUntilRespawn = false;
            }
            handleInsideInstance(brutus, visibleBossAlive, quickLeave);
            return;
        }

        // Safety net for the specific Ferox failure the user observed. Death's Domain
        // is also a RuneLite instance, but it has a unique exit portal (39549). If a
        // stale movement click or external walker ever gets us there, immediately
        // cancel all walker state and leave before any Cowbell/banking state machine
        // is allowed to continue.
        if (clientInstanceDetected && recoverFromDeathsOffice()) {
            return;
        }

        if (instanceConfirmed) {
            // Covers manual exits and map transitions that did not originate from our gate click.
            instanceConfirmed = false;
            initialBankCheckPending = true;
            travelRequired = false;
            killArmed = false;
        }

        brutusAlive = false;
        resetSpecialDetection();
        handleOutsideInstance();
    }

    private void handleInsideInstance(Rs2NpcModel brutus, boolean bossAlive, ObjectAction quickLeave) {
        // Combat inside the Brutus instance never uses the global walker or
        // minimap. Cancel a travel route only when one actually exists; repeatedly
        // clearing an already-null route floods WebWalkLog and can obscure the
        // Brutus diagnostics.
        clearWalkerRouteIfActive("brutus-instance-combat");
        initialBankCheckPending = false;
        altarRestorePending = false;

        // Hard mode begins as ordinary Brutus. Feed one Abyssal potato before any
        // normal-Brutus attack is allowed, then wait for the real Demonic Brutus
        // NPC (15628). Ghost copies (15629) are mechanics only and are never attack
        // targets.
        if (config.demonicBrutus() && !bossAlive && !demonicAttemptCompleted) {
            if (prepareDemonicBrutus(quickLeave)) {
                return;
            }
        }

        if (!hasConfiguredFood()) {
            disableManagedCombatPrayers();
            quickLeave(quickLeave, "Configured food depleted");
            return;
        }

        // Healing is an emergency action and must remain available during combat
        // animations. A food click must not suppress a simultaneously detected
        // special, so special movement is still evaluated in the same script cycle.
        boolean healingHandled = eatIfNeeded();

        // Keep processing an already active special even if the NPC cache briefly
        // drops Brutus during a Charge transform/movement transition. Otherwise the
        // completed rear-tile dodge is discarded before the Attack interaction can
        // be confirmed.
        if ((bossAlive
                || activeSpecial != SpecialAttack.NONE
                || pendingSpecial != SpecialAttack.NONE)
                && handleSpecialAttack(brutus)) {
            return;
        }

        if (healingHandled) {
            return;
        }

        // Equipment determines the combat mode. Make sure Magic has the configured
        // elemental staff + autocast spell selected, and merge any recovered
        // matching ranged ammunition back into the equipped ammo stack before an
        // attack/re-attack can be issued.
        if (maintainCombatEquipmentAndStyle(bossAlive)) {
            return;
        }

        // Movement and food cancel the current combat interaction. Reacquire the
        // boss before lower-priority maintenance such as potions, prayers or styles.
        if (bossAlive && combatReacquirePending) {
            attackBrutus(brutus);
            return;
        }

        // A newly spawned Brutus re-enables the configured prayers before potion or
        // equipment maintenance can consume another cycle.
        if (bossAlive && !prayersSuppressedUntilRespawn && manageCombatPrayers()) {
            return;
        }

        if (bossAlive && useStatBoostingPotionIfNeeded()) {
            return;
        }

        if (equipMooletaFromInventory()) {
            return;
        }

        if (balanceCombatStyle()) {
            return;
        }

        if (!bossAlive) {
            resetSpecialDetection();
        }

        if (!bossAlive && disableManagedCombatPrayers()) {
            return;
        }

        // Speed mode must wait for Brutus to completely despawn after the death
        // animation. Ringing while the dead NPC is still present is too early and
        // the game can ignore the fast-respawn request.
        if (!bossAlive && speedRingPending && ringCowbellForFastRespawn(brutus)) {
            return;
        }

        // Ranged recovery is lower priority than Speed Ring. Never start picking
        // arrows/bolts up while the Ring transaction for this kill is still pending,
        // including during the retry cooldown.
        if (!bossAlive
                && combatMode == CombatMode.RANGED
                && config.speed()
                && !config.demonicBrutus()
                && !speedRingPrioritySatisfied) {
            setState(KspMadCowState.WAITING_FOR_RESPAWN,
                    "Speed: Ring has priority over recovering ranged ammo");
            return;
        }

        // Matching arrows/bolts are supply recovery and are handled independently
        // of the generic Loot all setting.
        if (!bossAlive && recoverTrackedRangedAmmo()) {
            return;
        }

        if (!bossAlive && buryBones()) {
            return;
        }

        if (!bossAlive && lootOneItem()) {
            return;
        }

        if (!bossAlive && config.demonicBrutus() && demonicAttemptCompleted) {
            quickLeave(quickLeave, "Demonic Brutus defeated; banking for another Abyssal potato");
            return;
        }

        if (!bossAlive) {
            if (config.bankWhenFull() && Rs2Inventory.isFull()) {
                quickLeave(quickLeave, "Inventory full");
                return;
            }
            setState(KspMadCowState.WAITING_FOR_RESPAWN, "Waiting for Brutus to respawn");
            return;
        }

        attackBrutus(brutus);
    }

    private void handleOutsideInstance() {
        disableManagedCombatPrayers();

        // Banking is a hard master switch. Check it before any post-bank, LMS,
        // cooldown-fallback or walker state can continue from a previous tick.
        // This guarantees that disabling Banking also disables Minigame Teleport.
        if (!config.bankWhenFull()) {
            cancelBankingAutomationIfDisabled();
        }

        // Ferox is interaction-only. Never start any Rs2Walker movement while inside
        // the enclave; kill any surviving route before bank, Pool, or Cowbell logic.
        if (isAtFeroxEnclave()) {
            clearWalkerRouteIfActive("brutus-ferox-global-walker-disabled");
        }

        // Every completed bank cycle at Ferox must use the Pool of Refreshment before
        // any Cowbell charging/travel branch is allowed to run.
        if (postBankRefreshPending) {
            handlePostBankRefresh();
            return;
        }

        // Ferox is intentionally interaction-only after landing: bank interaction ->
        // restock -> close bank -> Pool interaction -> Cowbell preparation. Never
        // invoke a walker movement API during this hand-off.
        if (feroxPostPoolTravelPending) {
            handleFeroxPostPoolTravel();
            return;
        }

        // Once a Cowbell click has been issued, keep the script in a strict
        // teleport-confirmation transaction. Do not allow banking, walker routing,
        // altar routing, or pen-entry logic to run until the player has actually
        // moved away from the click origin. This also prevents any stale Ferox
        // walker target from pulling the account into Death's Domain while a failed
        // Cowbell click is being retried.
        if (cowbellTravelPending) {
            travelWithCowbell(true);
            return;
        }

        // Prayer restoration and its follow-up teleport are transactional. Once the
        // altar has been clicked, no banking, Cowbell charging, equipment or style
        // branch may interrupt the transition.
        if (altarTeleportPending) {
            travelWithCowbell(true);
            if (!travelRequired || cowbellTravelIssued) {
                altarTeleportPending = false;
            }
            return;
        }

        if (altarRestorePending && restorePrayerBeforeTravel()) {
            return;
        }

        if (needsBanking()) {
            handleBanking();
            return;
        }

        if (!travelRequired) {
            ObjectAction entry = findPenEntryAction();

            // After Release the top-level WorldView may switch to an
            // instance one or two client cycles before Brutus/Quick-escape has
            // populated. Wait for live Brutus-instance evidence without ever
            // declaring the encounter from a coordinate-area check.
            if (clientInstanceDetected && !lastPenEntryAction.isEmpty()) {
                setState(KspMadCowState.ENTERING_INSTANCE,
                        "Instance transition detected; waiting for Brutus evidence");
                return;
            }

            // Cowbell arrival is proven by the live pen interaction itself. If the
            // pen object is not present, the old travel-complete flag is stale;
            // re-arm Cowbell travel instead of relying on an "in Brutus area"
            // coordinate test.
            if (entry == null) {
                visibleDebug("Travel", "Pen entry action not found; re-arming Cowbell travel"
                        + " location=" + Rs2Player.getWorldLocation()
                        + " cowbellTravelIssued=" + cowbellTravelIssued);
                travelRequired = true;
                cowbellTravelIssued = false;
                lastPenEntryAction = "";
                lastPenEntryAttemptTick = Integer.MIN_VALUE;
                setState(KspMadCowState.TRAVELLING,
                        "Brutus pen not detected; resuming Cowbell travel");
                return;
            }

            cowbellTravelIssued = false;
            enterInstance(entry);
            return;
        }

        if (chargeCowbellIfPossible()) {
            return;
        }

        if (equipMooletaFromInventory()) {
            return;
        }

        if (balanceCombatStyle()) {
            return;
        }

        if (restorePrayerBeforeTravel()) {
            return;
        }

        // Bank closing is asynchronous. A completed banking cycle can reach this
        // branch one script tick before the bank widget has actually disappeared.
        // Never charge, equip for travel, or invoke Cowbell Teleport while the bank
        // is still open; close it and wait for a later tick instead.
        if (closeBankBeforeCowbellAction("Cowbell travel")) {
            return;
        }

        travelWithCowbell();
    }

    private void handleLeavingInstance(ObjectAction quickLeave) {
        disableManagedCombatPrayers();
        resetSpecialDetection();

        // A generic RuneLite instance is not necessarily the Brutus instance.
        // Death's Domain and other instanced maps must be allowed to complete the
        // leave transition instead of keeping instanceConfirmed latched forever.
        if (isBrutusInstanceContext(quickLeave, findBrutus())) {
            instanceConfirmed = true;

            // Do not treat an accepted Quick-escape menu invoke as proof that the
            // player actually left. The object can accept the interaction while the
            // transition fails or never starts. Keep retrying at a small throttle
            // until Brutus-specific instance detection itself turns false.
            long now = System.currentTimeMillis();
            boolean retryReady = !quickLeaveClickIssued
                    || quickLeaveLastAttemptAtMs <= 0L
                    || now - quickLeaveLastAttemptAtMs >= QUICK_ESCAPE_RETRY_INTERVAL_MS;

            if (quickLeave == null) {
                setState(KspMadCowState.LEAVING_INSTANCE,
                        "Waiting for Quick-escape gate; still inside instance");
                return;
            }

            if (!retryReady) {
                setState(KspMadCowState.LEAVING_INSTANCE,
                        "Waiting until outside the instance");
                return;
            }

            if (!playerReadyForAction()) {
                setState(KspMadCowState.LEAVING_INSTANCE,
                        "Still inside instance; waiting to retry Quick-escape");
                return;
            }

            setState(KspMadCowState.LEAVING_INSTANCE,
                    quickLeaveClickIssued
                            ? "Still inside instance; retrying Quick-escape"
                            : "Using Quick-escape gate");
            if (clickQuickEscapeGate(quickLeave)) {
                quickLeaveClickIssued = true;
                quickLeaveLastAttemptAtMs = now;
            } else {
                // Throttle failed invokes too; otherwise a missing/invalid clickbox
                // would be retried every 100 ms and could flood the client.
                quickLeaveLastAttemptAtMs = now;
            }
            return;
        }

        // Only now may banking/pathfinding begin.
        leavingInstance = false;
        quickLeaveClickIssued = false;
        quickLeaveLastAttemptAtMs = 0L;
        instanceConfirmed = false;
        clientInstanceDetected = false;
        initialBankCheckPending = true;
        travelRequired = false;
        cowbellTravelIssued = false;
        lastPenEntryAction = "";
        lastPenEntryAttemptTick = Integer.MIN_VALUE;
        killArmed = false;
        setState(KspMadCowState.BANKING, "Outside instance; preparing to bank");
    }

    private void cancelBankingAutomationIfDisabled() {
        if (config.bankWhenFull()) {
            return;
        }

        boolean minigamesOpen = Rs2Widget.findWidget(MINIGAME_TELEPORT_MENU_TITLE, true) != null;
        boolean hadBankingState = initialBankCheckPending
                || lmsBankTeleportIssued
                || lumbridgeFallbackBanking
                || bankActionPending
                || postBankRefreshPending
                || minigamesOpen
                || Rs2Bank.isOpen();

        initialBankCheckPending = false;
        bankActionPending = false;
        resetFeroxBankOpenState();
        postBankRefreshPending = false;
        feroxPostPoolTravelPending = false;
        lumbridgeFallbackBanking = false;
        resetPoolRefreshState();
        resetLmsTeleportAttemptState();

        if (minigamesOpen) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }

        if (hadBankingState) {
            clearWalkerRouteIfActive("brutus-banking-disabled");
            visibleDebug("Bank", "Banking disabled; cancelled LMS/Lumbridge banking and blocked Minigame Teleport");
            setState(KspMadCowState.TRAVELLING,
                    "Banking disabled; Minigame Teleport blocked");
        }
    }

    private boolean needsBanking() {
        // The legacy bankWhenFull config key is now the master Banking toggle so
        // existing users who already had it disabled keep banking disabled.
        if (!config.bankWhenFull()) {
            cancelBankingAutomationIfDisabled();
            return false;
        }

        // initialBankCheckPending is a bookkeeping flag, not a banking reason.
        // Previously it forced an LMS Minigame Teleport whenever the plugin started
        // or returned outside the instance, even when every actual restock trigger
        // was satisfied/disabled. Only enter the banking route for a concrete need.
        boolean bankingNeeded = !hasRequiredFoodAmount()
                || !hasCowbell()
                || (config.demonicBrutus() && !Rs2Inventory.hasItem(ABYSSAL_POTATO_ID))
                || Rs2Inventory.isFull();

        if (!bankingNeeded) {
            if (initialBankCheckPending) {
                visibleDebug("Bank", "Initial bank check cleared: no concrete banking requirement");
            }
            initialBankCheckPending = false;
            resetLmsTeleportAttemptState();
            lumbridgeFallbackBanking = false;

            // Configs can be changed while the Minigames modal is already open.
            // Close it immediately when the banking reason disappears so a stale
            // LMS selection cannot complete on the following tick.
            if (Rs2Widget.findWidget(MINIGAME_TELEPORT_MENU_TITLE, true) != null) {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            }
        }

        return bankingNeeded;
    }

    private void handleBanking() {
        if (!config.bankWhenFull()) {
            cancelBankingAutomationIfDisabled();
            return;
        }

        if (!lumbridgeFallbackBanking && isMinigameTeleportCooldownActive()) {
            activateLumbridgeBankFallback("Minigame Teleport cooldown is active");
        }

        if (lumbridgeFallbackBanking) {
            setState(KspMadCowState.BANKING, "Preparing supplies via Lumbridge bank");

            resetFeroxBankOpenState();
            if (!isAtLumbridgeBank()) {
                bankActionPending = false;
                if (Rs2Bank.isOpen()) {
                    Rs2Bank.closeBank();
                    return;
                }

                setState(KspMadCowState.BANKING,
                        "Minigame Teleport unavailable; walking to Lumbridge bank");
                if (!Rs2Player.isMoving()) {
                    walkToOutsideFerox(LUMBRIDGE_BANK_LOCATION, 3);
                }
                return;
            }

            // A stale long-distance walker target can otherwise pull the player away
            // while the bank interaction is being attempted upstairs.
            clearWalkerRouteIfActive("brutus-entered-lumbridge-bank");
        } else {
            setState(KspMadCowState.BANKING, "Preparing supplies via Last Man Standing");

            // Ferox is the preferred route. If the minigame teleport is unavailable,
            // activateLumbridgeBankFallback() switches this banking cycle to Lumbridge.
            if (!isAtFeroxEnclave()) {
                bankActionPending = false;
                resetFeroxBankOpenState();
                if (Rs2Bank.isOpen()) {
                    Rs2Bank.closeBank();
                    return;
                }
                if (!ensureLastManStandingBankTeleport()) {
                    return;
                }
            } else {
                resetLmsTeleportAttemptState();
            }
        }

        if (!Rs2Bank.isOpen()) {
            bankActionPending = false;
            if (lumbridgeFallbackBanking) {
                setState(KspMadCowState.BANKING, "At Lumbridge; opening bank");
                KspVerifiedBank.openBank();
            } else {
                // Ferox is interaction-only: never call the generic KspVerifiedBank.openBank()
                // here because that helper is allowed to select another bank target or
                // start walker movement. Target the known Ferox bank chest (26711)
                // directly, turn the camera to it when needed, then click its real bank
                // action. The game handles the short local approach after the click.
                clearWalkerRouteIfActive("brutus-ferox-direct-bank-interaction");
                openFeroxBankChestDirectly();
            }
            return;
        }

        // The bank opening is the acknowledgement for the direct Ferox chest click.
        // Clear the interaction latch before starting deposit/withdraw transactions.
        resetFeroxBankOpenState();

        if (awaitingBankInventoryChange()) {
            return;
        }

        Rs2ItemModel unwanted = Rs2Inventory.all().stream()
                .filter(item -> !keepDuringBanking(item))
                .findFirst()
                .orElse(null);
        if (unwanted != null) {
            setState(KspMadCowState.BANKING, "Depositing " + unwanted.getName());
            if (Rs2Bank.depositAll(unwanted.getId())) {
                final int unwantedId = unwanted.getId();
                markBankAction("deposit " + unwanted.getName(), () -> !Rs2Inventory.hasItem(unwantedId));
            }
            return;
        }

        if (!hasCowbell()) {
            if (Rs2Bank.hasBankItem(COWBELL_CHARGED_ID, 1)) {
                setState(KspMadCowState.BANKING, "Withdrawing charged Cowbell");
                if (Rs2Bank.withdrawOne(COWBELL_CHARGED_ID)) {
                    markBankAction("withdraw Cowbell", this::hasCowbell);
                }
                return;
            }
            if (Rs2Bank.hasBankItem(COWBELL_EMPTY_ID, 1)) {
                setState(KspMadCowState.BANKING, "Withdrawing empty Cowbell");
                if (Rs2Bank.withdrawOne(COWBELL_EMPTY_ID)) {
                    markBankAction("withdraw Cowbell", this::hasCowbell);
                }
                return;
            }
            supplyFailure("No Cowbell amulet was found in inventory, equipment, or bank");
            return;
        }

        if (config.demonicBrutus() && !Rs2Inventory.hasItem(ABYSSAL_POTATO_ID)) {
            setState(KspMadCowState.BANKING, "Withdrawing Abyssal potato for Demonic Brutus");
            if (!Rs2Bank.hasBankItem(ABYSSAL_POTATO_ID, 1)) {
                supplyFailure("Demonic Brutus requires one Abyssal potato per attempt, but none was found in the bank");
                return;
            }
            if (Rs2Bank.withdrawOne(ABYSSAL_POTATO_ID)) {
                markBankAction("withdraw Abyssal potato",
                        () -> Rs2Inventory.hasItem(ABYSSAL_POTATO_ID));
            }
            return;
        }

        int foodTarget = Math.max(1, config.foodAmount());
        int foodCount = getConfiguredFoodQuantity();
        if (foodCount < foodTarget) {
            setState(KspMadCowState.BANKING,
                    "Withdrawing " + config.food().getName() + " (" + foodCount + "/" + foodTarget + ")");
            if (!Rs2Bank.hasBankItem(config.food().getId(), foodTarget - foodCount)) {
                supplyFailure("The bank does not contain enough " + config.food().getName()
                        + " (inventory " + foodCount + "/" + foodTarget + ")");
                return;
            }
            if (Rs2Bank.withdrawDeficit(config.food().getId(), foodTarget)) {
                markBankAction("withdraw food", this::hasRequiredFoodAmount);
            }
            return;
        }

        // Magic supply setup is driven by the detected equipped weapon type.
        // Every rune type used by the selected spell is withdrawn with Withdraw-All.
        // The matching elemental staff is still equipped for the selected spell, but
        // banking no longer uses a configured cast-count target.
        if (handleMagicBanking()) {
            return;
        }

        int magicAirReserve = minimumMagicRuneReserve(Runes.AIR);
        int airTarget = Math.max(1, config.airRunesToCarry());
        int airCount = Rs2Inventory.itemQuantity(AIR_RUNE_ID);
        int chargeableAirCount = Math.max(0, airCount - magicAirReserve);
        if (chargeableAirCount < airTarget) {
            if (Rs2Bank.hasBankItem(AIR_RUNE_ID, 1)) {
                final int previousAirCount = airCount;
                setState(KspMadCowState.BANKING,
                        "Withdrawing all available air runes (chargeable "
                                + chargeableAirCount + "/" + airTarget + ")");
                visibleDebug("Bank", "Withdraw-All Air runes requested inventory="
                        + airCount + " magicReserve=" + magicAirReserve
                        + " chargeable=" + chargeableAirCount
                        + " configuredTarget=" + airTarget);
                if (Rs2Bank.withdrawAll(AIR_RUNE_ID)) {
                    markBankAction("withdraw all air runes",
                            () -> Rs2Inventory.itemQuantity(AIR_RUNE_ID) > previousAirCount);
                } else {
                    visibleDebug("Bank", "Withdraw-All Air runes was not issued");
                }
                return;
            }

            if (chargeableAirCount == 0 && Rs2Inventory.hasItem(COWBELL_EMPTY_ID)) {
                visibleDebug("Bank", "Empty Cowbell cannot be charged: no Air runes are available beyond magic reserve="
                        + magicAirReserve);
                supplyFailure("The Cowbell is empty and no spare air runes are available for charging");
                return;
            }
        }

        if (config.equipMooleta()
                && combatMode == CombatMode.MELEE
                && !Rs2Equipment.isWearing(MOOLETA_ID)
                && !Rs2Inventory.hasItem(MOOLETA_ID)
                && Rs2Bank.hasBankItem(MOOLETA_ID, 1)) {
            setState(KspMadCowState.BANKING, "Withdrawing Mooleta");
            if (Rs2Bank.withdrawOne(MOOLETA_ID)) {
                markBankAction("withdraw Mooleta", () -> Rs2Inventory.hasItem(MOOLETA_ID) || Rs2Equipment.isWearing(MOOLETA_ID));
            }
            return;
        }

        String potionToWithdraw = findPotionToWithdrawFromBank();
        if (potionToWithdraw != null) {
            setState(KspMadCowState.BANKING, "Withdrawing " + potionToWithdraw);
            if (Rs2Bank.withdrawOne(potionToWithdraw)) {
                String[] expectedPotions = potionPrioritiesForMode(combatMode);
                markBankAction("withdraw potion", () -> hasAnyInventoryPotion(expectedPotions));
            }
            return;
        }

        initialBankCheckPending = false;
        suppliesErrorShown = false;
        travelRequired = true;
        cowbellTravelIssued = false;
        lastPenEntryAction = "";
        lastPenEntryAttemptTick = Integer.MIN_VALUE;
        instanceConfirmed = false;
        clientInstanceDetected = false;
        leavingInstance = false;
        quickLeaveClickIssued = false;
        quickLeaveLastAttemptAtMs = 0L;

        if (lumbridgeFallbackBanking) {
            lumbridgeFallbackBanking = false;
            postBankRefreshPending = false;
            resetPoolRefreshState();
            resetLmsTeleportAttemptState();
            Rs2Bank.closeBank();

            if (config.usePrayer()) {
                // Cooldown fallback: after Lumbridge banking, route through the original
                // Lumbridge altar coordinates before Cowbell travel if Prayer is enabled.
                // This restores partially depleted Prayer as well as zero Prayer.
                lumbridgePostBankAltarPending = true;
                altarRestorePending = true;
                altarInteractionIssued = false;
                altarPrayerPointsBeforeInteraction = -1;
                altarTeleportPending = false;
                setState(KspMadCowState.RESTORING_PRAYER,
                        "Lumbridge banking complete; restoring Prayer at old altar");
            } else {
                // Master Prayer toggle is off: do not visit the altar just because the
                // Minigame Teleport fallback used Lumbridge bank. Continue directly to
                // the normal Cowbell preparation/travel path.
                lumbridgePostBankAltarPending = false;
                altarRestorePending = false;
                altarInteractionIssued = false;
                altarPrayerPointsBeforeInteraction = -1;
                altarTeleportPending = false;
                setState(KspMadCowState.TRAVELLING,
                        "Lumbridge banking complete; Prayer disabled, preparing Cowbell");
            }
            return;
        }

        // Preferred Ferox route is strictly interaction-driven:
        // bank -> clear/restock -> close bank -> click Pool.
        feroxPostPoolTravelPending = false;
        postBankRefreshPending = true;
        resetPoolRefreshState();
        Rs2Bank.closeBank();
    }

    private boolean ensureLastManStandingBankTeleport() {
        // Hard safety guard: this method must never progress to the Magic tab or
        // Minigames interface while Banking is disabled, even if called from stale state.
        if (!config.bankWhenFull()) {
            cancelBankingAutomationIfDisabled();
            return false;
        }

        if (isAtFeroxEnclave()) {
            resetLmsTeleportAttemptState();
            return true;
        }

        if (isMinigameTeleportCooldownActive()) {
            activateLumbridgeBankFallback("Minigame Teleport is on cooldown");
            return false;
        }

        clearWalkerRouteIfActive("brutus-lms-bank-teleport");

        int gameTick = currentGameTick();
        if (lmsBankTeleportIssued) {
            if (gameTick != Integer.MIN_VALUE
                    && lmsBankTeleportIssuedTick != Integer.MIN_VALUE
                    && gameTick - lmsBankTeleportIssuedTick >= LMS_TELEPORT_RETRY_TICKS) {
                // Do not loop forever on a rejected minigame teleport. A confirmed
                // cooldown message switches immediately; this timeout is a defensive
                // fallback for clients where the rejection message text changes.
                visibleDebug("Bank",
                        "LMS teleport did not land within retry window; using Lumbridge bank");
                activateLumbridgeBankFallback("LMS teleport did not land");
                return false;
            }

            setState(KspMadCowState.MINIGAME_TELEPORTING,
                    "Waiting for Last Man Standing minigame teleport");
            return false;
        }

        setState(KspMadCowState.MINIGAME_TELEPORTING,
                "Selecting Last Man Standing minigame teleport");
        if (issueLastManStandingMinigameTeleport()) {
            lmsBankTeleportIssued = true;
            lmsBankTeleportIssuedTick = gameTick;
            setState(KspMadCowState.MINIGAME_TELEPORTING,
                    "Last Man Standing teleport issued; waiting for Ferox Enclave");
        }
        return false;
    }

    private boolean isMinigameTeleportCooldownActive() {
        long until = minigameTeleportCooldownUntilMs;
        if (until <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() < until) {
            return true;
        }
        minigameTeleportCooldownUntilMs = 0L;
        return false;
    }

    private boolean isAtFeroxBank() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation != null
                && playerLocation.getPlane() == 0
                && FEROX_BANK_AREA.contains(playerLocation);
    }

    private boolean isAtLumbridgeBank() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation != null
                && playerLocation.getPlane() == 2
                && LUMBRIDGE_BANK_AREA.contains(playerLocation);
    }

    private void activateLumbridgeBankFallback(String reason) {
        if (!lumbridgeFallbackBanking) {
            visibleDebug("Bank", reason + "; switching to Lumbridge bank");
        }
        lumbridgeFallbackBanking = true;
        bankActionPending = false;
        resetFeroxBankOpenState();
        resetLmsTeleportAttemptState();
        clearWalkerRouteIfActive("brutus-lumbridge-bank-fallback");

        // The Minigames modal blocks normal world interaction. Close it before the
        // walker starts the Lumbridge route. Escape is safe when the modal is absent.
        if (Rs2Widget.findWidget(MINIGAME_TELEPORT_MENU_TITLE, true) != null) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }
        setState(KspMadCowState.BANKING,
                "Minigame Teleport unavailable; using Lumbridge bank");
    }

    private void resetLmsTeleportAttemptState() {
        lmsBankTeleportIssued = false;
        lmsBankTeleportIssuedTick = Integer.MIN_VALUE;
        lastMinigameMenuScrollAtMs = 0L;
        minigameMenuScrollCount = 0;
    }

    private void resetPoolRefreshState() {
        poolRefreshIssued = false;
        poolRefreshIssuedTick = Integer.MIN_VALUE;
        poolPrayerPointsBeforeInteraction = -1;
        poolPrayerWasFullBeforeInteraction = false;
        poolAnimationObserved = false;
        poolPostAnimationIdleTick = Integer.MIN_VALUE;
        poolPrayerRestoredAtMs = 0L;
    }

    private boolean issueLastManStandingMinigameTeleport() {
        // Final hard guard at the actual spell-cast boundary. Even if some stale
        // caller reaches this method, Banking=false means Minigame Teleport is forbidden.
        if (!config.bankWhenFull()) {
            cancelBankingAutomationIfDisabled();
            return false;
        }

        // Minigame Teleport cannot be started while a dialogue is occupying the interface.
        if (Rs2Dialogue.isInDialogue()) {
            setState(KspMadCowState.MINIGAME_TELEPORTING,
                    "Waiting for dialogue to close before LMS teleport");
            return false;
        }

        // Once the spell opens the Minigames window, deliberately perform two real
        // mouse-wheel scrolls over the 951.4 Minigames list before attempting LMS.
        // The client does not reliably make the LMS card interactable until the list
        // has actually received wheel input; direct widget invokes and synthetic
        // scrollY updates are therefore intentionally not used here.
        Widget minigamesTitle = Rs2Widget.findWidget(MINIGAME_TELEPORT_MENU_TITLE, true);
        if (minigamesTitle != null) {
            if (minigameMenuScrollCount < REQUIRED_INITIAL_MINIGAME_SCROLLS) {
                if (scrollMinigameMenu(minigamesTitle, true)) {
                    minigameMenuScrollCount++;
                    setState(KspMadCowState.MINIGAME_TELEPORTING,
                            "Scrolling Minigames menu ("
                                    + minigameMenuScrollCount + "/"
                                    + REQUIRED_INITIAL_MINIGAME_SCROLLS + ")");
                } else {
                    setState(KspMadCowState.MINIGAME_TELEPORTING,
                            "Waiting to scroll Minigames menu");
                }
                return false;
            }

            Widget destinationWidget = findLastManStandingMinigameCard();
            if (destinationWidget == null) {
                if (minigameMenuScrollCount < MAX_MINIGAME_SCROLL_ATTEMPTS
                        && scrollMinigameMenu(minigamesTitle, true)) {
                    minigameMenuScrollCount++;
                    setState(KspMadCowState.MINIGAME_TELEPORTING,
                            "LMS not visible yet; scrolling Minigames menu ("
                                    + minigameMenuScrollCount + "/"
                                    + MAX_MINIGAME_SCROLL_ATTEMPTS + ")");
                } else {
                    setState(KspMadCowState.MINIGAME_TELEPORTING,
                            "Waiting for Last Man Standing card after Minigames scroll");
                }
                return false;
            }

            // After the two required real wheel scrolls, attempt the physical LMS card
            // click immediately. Avoid another synthetic visibility calculation here: on this
            // interface the card becomes usable as a consequence of the wheel events themselves.
            if (clickMinigameDestinationCard(destinationWidget)) {
                lastMinigameMenuScrollAtMs = 0L;
                minigameMenuScrollCount = 0;
                visibleDebug("Bank", "Clicked spellbook Minigame Teleport card after real wheel scrolls: "
                        + LMS_MINIGAME_NAME + " destination=" + LMS_TELEPORT_DESTINATION);
                return true;
            }

            // If the card has not become clickable yet (different scaling/layout), perform one
            // additional real wheel scroll per retry until it does, capped to prevent runaway scrolling.
            if (minigameMenuScrollCount < MAX_MINIGAME_SCROLL_ATTEMPTS
                    && scrollMinigameMenu(minigamesTitle, true)) {
                minigameMenuScrollCount++;
                setState(KspMadCowState.MINIGAME_TELEPORTING,
                        "LMS card not clickable yet; additional Minigames scroll ("
                                + minigameMenuScrollCount + "/"
                                + MAX_MINIGAME_SCROLL_ATTEMPTS + ")");
            } else {
                setState(KspMadCowState.MINIGAME_TELEPORTING,
                        "Waiting to click Last Man Standing after Minigames scroll");
            }
            return false;
        }

        // The menu is closed, so a future open starts with a fresh two-wheel-scroll sequence.
        minigameMenuScrollCount = 0;
        lastMinigameMenuScrollAtMs = 0L;

        // Open Magic and locate the new Minigame Teleport spell by name so spellbook
        // reordering does not require a hardcoded slot.
        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
            Rs2Tab.switchToMagicTab();
            setState(KspMadCowState.MINIGAME_TELEPORTING,
                    "Opening Magic book for Minigame Teleport");
            return false;
        }

        Widget spellbook = Rs2Widget.getWidget(SPELLBOOK_WIDGET_GROUP, SPELLBOOK_WIDGET_CHILD);
        if (spellbook == null) {
            setState(KspMadCowState.MINIGAME_TELEPORTING,
                    "Waiting for Magic book");
            return false;
        }

        Widget minigameTeleportSpell = Rs2Widget.findWidget(
                MINIGAME_TELEPORT_SPELL_NAME, List.of(spellbook), true);
        if (minigameTeleportSpell == null) {
            setState(KspMadCowState.MINIGAME_TELEPORTING,
                    "Minigame Teleport spell is hidden/not visible in Magic book");
            return false;
        }

        if (!clickClickableWidgetAncestor(minigameTeleportSpell)) {
            setState(KspMadCowState.MINIGAME_TELEPORTING,
                    "Unable to open Minigame Teleport spell");
            return false;
        }

        setState(KspMadCowState.MINIGAME_TELEPORTING,
                "Opening Minigame Teleport menu from Magic book");
        return false;
    }

    /**
     * Find the LMS card in the dedicated Minigames interface. The card itself contains
     * additional line-break/location markup, so this deliberately uses a partial text
     * match instead of exact equality.
     */
    private Widget findLastManStandingMinigameCard() {
        // Widget Inspector shows the destination entries (including 951.16 for LMS in the
        // current ordering) beneath 951.3 CONTENT. Search that content subtree first.
        Widget listRoot = Rs2Widget.getWidget(
                MINIGAME_TELEPORT_WIDGET_GROUP, MINIGAME_TELEPORT_CONTENT_CHILD);

        if (listRoot != null) {
            Widget destination = Rs2Widget.findWidget(
                    LMS_MINIGAME_NAME, List.of(listRoot), false);
            if (destination != null) {
                return destination;
            }
        }

        // Compatibility fallback if Jagex moves the list root but keeps the visible text.
        return Rs2Widget.findWidget(LMS_MINIGAME_NAME, false);
    }

    private static int packedWidgetId(int group, int child) {
        return (group << 16) | child;
    }

    /**
     * Returns -1 when LMS is above the visible list viewport, +1 when it is below it,
     * and 0 once its centre is actually inside the viewport and it is safe to click.
     * All guarded Widget geometry reads stay on the RuneLite client thread.
     */
    private int getMinigameDestinationViewportPosition(Widget destinationWidget) {
        if (destinationWidget == null) {
            return 1;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget viewport = Microbot.getClient().getWidget(
                    MINIGAME_TELEPORT_WIDGET_GROUP, MINIGAME_TELEPORT_LIST_CHILD);
            if (viewport == null || viewport.isHidden() || destinationWidget.isHidden()) {
                return 1;
            }

            Rectangle viewportBounds = viewport.getBounds();
            Rectangle destinationBounds = destinationWidget.getBounds();
            if (viewportBounds == null || destinationBounds == null
                    || viewportBounds.width <= 0 || viewportBounds.height <= 0
                    || destinationBounds.width <= 0 || destinationBounds.height <= 0) {
                return 1;
            }

            int viewportTop = viewportBounds.y + 6;
            int viewportBottom = viewportBounds.y + viewportBounds.height - 6;
            int destinationCenterY = (int) destinationBounds.getCenterY();

            if (destinationCenterY < viewportTop) {
                return -1;
            }
            if (destinationCenterY > viewportBottom) {
                return 1;
            }
            return 0;
        }).orElse(1);
    }

    /**
     * Scroll the dedicated Minigames window in the required direction. The scroll point is
     * taken from the actual 951.3 content viewport when available, with the previous modal
     * geometry heuristic retained only as a compatibility fallback.
     */
    private boolean scrollMinigameMenu(Widget minigamesTitle, boolean down) {
        long now = System.currentTimeMillis();
        if (now - lastMinigameMenuScrollAtMs < MINIGAME_MENU_SCROLL_INTERVAL_MS) {
            return false;
        }

        // Use a real wheel event over the centre of 951.4. Microbot's VirtualMouse
        // moves the cursor to this point and dispatches an actual MouseWheelEvent to
        // the game canvas, which is the same interaction that was observed to reveal LMS.
        Point scrollPoint = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget list = Microbot.getClient().getWidget(
                    MINIGAME_TELEPORT_WIDGET_GROUP, MINIGAME_TELEPORT_LIST_CHILD);
            if (list != null && !list.isHidden()) {
                Rectangle bounds = list.getBounds();
                if (bounds != null && bounds.width > 0 && bounds.height > 0) {
                    // Keep away from the scrollbar and modal borders.
                    int x = Math.max(bounds.x + 20,
                            Math.min((int) bounds.getCenterX(), bounds.x + bounds.width - 40));
                    int y = Math.max(bounds.y + 20,
                            Math.min((int) bounds.getCenterY(), bounds.y + bounds.height - 20));
                    return new Point(x, y);
                }
            }

            Rectangle titleBounds = minigamesTitle == null ? null : minigamesTitle.getBounds();
            if (titleBounds == null || titleBounds.width <= 0 || titleBounds.height <= 0) {
                return null;
            }
            return new Point((int) titleBounds.getCenterX(), titleBounds.y + titleBounds.height + 180);
        }).orElse(null);

        if (scrollPoint == null) {
            return false;
        }

        if (down) {
            Microbot.getMouse().scrollDown(scrollPoint);
        } else {
            Microbot.getMouse().scrollUp(scrollPoint);
        }

        lastMinigameMenuScrollAtMs = now;
        visibleDebug("Bank", "Minigames wheel scroll " + (down ? "down" : "up")
                + " at " + scrollPoint.getX() + "," + scrollPoint.getY());
        return true;
    }

    /**
     * Invoke the Last Man Standing destination directly from the Minigames widget tree.
     *
     * Widget Inspector shows LMS as a type-4 dynamic widget beneath group 951 even when
     * the card is outside the visible scroll viewport. Because the widget already exists,
     * there is no requirement to scroll it onto the canvas first. We resolve the nearest
     * card-sized ancestor and use its real widget op when available; otherwise type-4
     * widgets are activated with {@link MenuAction#WIDGET_TYPE_4}.
     */
    private boolean invokeMinigameDestinationCard(Widget textWidget) {
        if (textWidget == null) {
            return false;
        }

        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Widget current = textWidget;
                Widget card = null;
                Widget actionable = null;

                for (int depth = 0; depth < 8 && current != null; depth++) {
                    Rectangle bounds = current.getBounds();
                    String[] actions = current.getActions();
                    boolean hasAction = actions != null
                            && Arrays.stream(actions).anyMatch(action -> action != null && !action.isBlank());
                    boolean hasOpListener = current.getOnOpListener() != null;

                    if (actionable == null && (hasAction || hasOpListener)) {
                        actionable = current;
                    }

                    if (card == null && bounds != null
                            && bounds.width >= 180
                            && bounds.height >= 48
                            && bounds.height <= 180) {
                        card = current;
                    }

                    current = current.getParent();
                }

                Widget target = actionable != null ? actionable : (card != null ? card : textWidget);
                if (target == null) {
                    return false;
                }

                String[] actions = target.getActions();
                int identifier = 1;
                MenuAction actionType = MenuAction.CC_OP;
                String option = "Select";

                if (actions != null) {
                    for (int i = 0; i < actions.length; i++) {
                        String action = actions[i];
                        if (action != null && !action.isBlank()) {
                            identifier = i + 1;
                            option = action;
                            break;
                        }
                    }
                }

                // The new Minigames cards are type-4 widgets and often expose no actions[]
                // (the LMS Widget Inspector entry showed Actions=null). In that case CC_OP
                // has nothing to resolve, so invoke the widget's native type-4 interaction.
                if ((actions == null || Arrays.stream(actions).noneMatch(a -> a != null && !a.isBlank()))
                        && target.getOnOpListener() == null
                        && target.getType() == 4) {
                    actionType = MenuAction.WIDGET_TYPE_4;
                    identifier = 0;
                    option = "";
                }

                int param0 = target.getIndex();
                if (param0 < 0) {
                    param0 = 0;
                }

                Microbot.doInvoke(
                        new NewMenuEntry()
                                .option(option)
                                .target(LMS_MINIGAME_NAME)
                                .identifier(identifier)
                                .type(actionType)
                                .param0(param0)
                                .param1(target.getId())
                                .itemId(-1),
                        // Keep the synthetic invoke independent of whether the card is currently
                        // clipped outside the viewport. Menu parameters identify the widget.
                        new Rectangle(1, 1, 1, 1)
                );

                visibleDebug("Bank", "LMS widget invoke issued"
                        + " id=" + target.getId()
                        + " index=" + param0
                        + " type=" + target.getType()
                        + " menu=" + actionType
                        + " identifier=" + identifier);
                return true;
            }).orElse(false);
        } catch (Exception ex) {
            log.warn("[KSP Mad Cow][Bank] LMS widget invoke failed", ex);
            visibleDebug("Bank", "LMS widget invoke exception=" + ex.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Click the full Last Man Standing destination card rather than its text label. The
     * card shown by Jagex is a large row-sized widget; choose the nearest visible ancestor
     * with card-like geometry, preferring one that owns an action/on-op listener.
     */
    private boolean clickMinigameDestinationCard(Widget textWidget) {
        if (textWidget == null) {
            return false;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget current = textWidget;
            Widget bestCard = null;
            int bestScore = Integer.MIN_VALUE;

            Rectangle textBounds = textWidget.getBounds();
            int textCenterX = textBounds != null ? (int) textBounds.getCenterX() : Integer.MIN_VALUE;
            int textCenterY = textBounds != null ? (int) textBounds.getCenterY() : Integer.MIN_VALUE;

            for (int depth = 0; depth < 8 && current != null; depth++) {
                if (!current.isHidden()) {
                    Rectangle bounds = current.getBounds();
                    if (bounds != null && bounds.width > 0 && bounds.height > 0) {
                        String[] actions = current.getActions();
                        boolean hasAction = actions != null
                                && Arrays.stream(actions).anyMatch(action -> action != null && !action.isBlank());
                        boolean hasOpListener = current.getOnOpListener() != null;

                        boolean containsTextCenter = textBounds == null
                                || bounds.contains(textCenterX, textCenterY);
                        boolean cardSized = bounds.width >= 180
                                && bounds.height >= 48
                                && bounds.height <= 180;

                        if (containsTextCenter && cardSized) {
                            int score = 0;
                            if (hasAction) score += 20;
                            if (hasOpListener) score += 20;
                            // Prefer the card wrapper over the small label but reject giant roots.
                            score += Math.min(12, bounds.width / 40);
                            score += Math.min(8, bounds.height / 20);
                            score -= depth;

                            if (score > bestScore) {
                                bestScore = score;
                                bestCard = current;
                            }
                        }
                    }
                }
                current = current.getParent();
            }

            if (bestCard != null) {
                return Rs2Widget.clickWidget(bestCard);
            }

            // Compatibility fallback for layouts where the destination itself owns the click.
            current = textWidget;
            for (int depth = 0; depth < 8 && current != null; depth++) {
                if (!current.isHidden()) {
                    Rectangle bounds = current.getBounds();
                    String[] actions = current.getActions();
                    boolean hasAction = actions != null
                            && Arrays.stream(actions).anyMatch(action -> action != null && !action.isBlank());
                    if (bounds != null && bounds.width > 0 && bounds.height > 0
                            && (hasAction || current.getOnOpListener() != null)) {
                        return Rs2Widget.clickWidget(current);
                    }
                }
                current = current.getParent();
            }

            return false;
        }).orElse(false);
    }

    /**
     * Text labels inside the new Minigame Teleport menu are often children of the
     * actual clickable destination card. Resolve and click the actionable ancestor
     * entirely on the RuneLite client thread.
     *
     * RuneLite Widget accessors such as isHidden(), getBounds(), getActions(),
     * getOnOpListener(), and getParent() are client-thread guarded. The Mad Cow
     * scheduler runs on its own executor, so touching those accessors directly from
     * the script thread throws IllegalStateException ("must be called on client thread").
     *
     * Keep both the hierarchy walk and the final Rs2Widget click inside the same
     * client-thread callback so no live Widget is inspected again after returning to
     * the script executor.
     */
    private boolean clickClickableWidgetAncestor(Widget widget) {
        if (widget == null) {
            return false;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget current = widget;
            Widget fallback = null;

            for (int depth = 0; depth < 6 && current != null; depth++) {
                if (!current.isHidden()) {
                    Rectangle bounds = current.getBounds();
                    if (fallback == null && bounds != null && bounds.width > 0 && bounds.height > 0) {
                        // Keep the nearest visible child as the fallback. Do not replace it with
                        // a broad root container while walking up the widget hierarchy.
                        fallback = current;
                    }

                    String[] actions = current.getActions();
                    boolean hasAction = actions != null
                            && Arrays.stream(actions).anyMatch(action -> action != null && !action.isBlank());
                    if (hasAction || current.getOnOpListener() != null) {
                        return Rs2Widget.clickWidget(current);
                    }
                }
                current = current.getParent();
            }

            return fallback != null && Rs2Widget.clickWidget(fallback);
        }).orElse(false);
    }

    private boolean isAtFeroxEnclave() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return playerLocation != null
                && playerLocation.getPlane() == 0
                && FEROX_ENCLAVE_AREA.contains(playerLocation);
    }

    private void handlePostBankRefresh() {
        if (!postBankRefreshPending) {
            return;
        }

        if (Rs2Bank.isOpen()) {
            setState(KspMadCowState.USING_REFRESHMENT_POOL,
                    "Closing bank before Pool of Refreshment");
            Rs2Bank.closeBank();
            return;
        }

        if (!isAtFeroxEnclave()) {
            // This should only occur if an external/manual teleport interrupted the
            // post-bank sequence. Re-enter the normal banking route rather than
            // allowing the Cowbell to bypass the required pool interaction.
            initialBankCheckPending = true;
            postBankRefreshPending = false;
            feroxPostPoolTravelPending = false;
            poolRefreshIssued = false;
            poolRefreshIssuedTick = Integer.MIN_VALUE;
            poolPrayerPointsBeforeInteraction = -1;
            poolPrayerWasFullBeforeInteraction = false;
            poolAnimationObserved = false;
            poolPostAnimationIdleTick = Integer.MIN_VALUE;
            poolPrayerRestoredAtMs = 0L;
            setState(KspMadCowState.BANKING,
                    "Left Ferox before refresh; restarting LMS banking route");
            return;
        }

        int gameTick = currentGameTick();
        if (poolRefreshIssued) {
            // Treat the Pool interaction as a strict animation transaction. The Prayer
            // orb can update before the Drink animation has finished, which previously
            // allowed Cowbell Teleport to be clicked while the player was still locked
            // in the Pool animation. Keep clearing any stale walker target and do not
            // permit Cowbell travel until we have actually observed the Pool animation
            // and then observed the player return to idle.
            clearWalkerRouteIfActive("brutus-ferox-pool-animation-lock");
            boolean poolAnimating = isPlayerCurrentlyAnimating();
            if (poolAnimating) {
                poolAnimationObserved = true;
                poolPostAnimationIdleTick = Integer.MIN_VALUE;
                setState(KspMadCowState.USING_REFRESHMENT_POOL,
                        "Waiting for Pool of Refreshment animation to end");
                return;
            }

            if (!poolAnimationObserved) {
                int ticksSinceDrink = gameTick == Integer.MIN_VALUE
                        || poolRefreshIssuedTick == Integer.MIN_VALUE
                        ? 0
                        : Math.max(0, gameTick - poolRefreshIssuedTick);
                if (ticksSinceDrink < POOL_ANIMATION_START_TIMEOUT_TICKS) {
                    setState(KspMadCowState.USING_REFRESHMENT_POOL,
                            "Waiting for Pool of Refreshment animation to start");
                    return;
                }

                // Never fall through to Cowbell travel if the expected animation was
                // not observed. Retry the Pool interaction instead.
                visibleDebug("Bank", "Pool animation was not observed after "
                        + ticksSinceDrink + " ticks; retrying Drink");
                resetPoolRefreshState();
                setState(KspMadCowState.USING_REFRESHMENT_POOL,
                        "Pool animation not observed; retrying Drink");
                return;
            }

            // Animation -1 only means the animation has ended; it does not mean the
            // player has stopped moving. A previously queued scene/path click can
            // therefore continue walking after Drink. Cancel that movement locally
            // and require two stationary game ticks before Cowbell is even eligible.
            if (stopResidualFeroxMovement("Pool complete; stopping residual Ferox movement")) {
                poolPostAnimationIdleTick = Integer.MIN_VALUE;
                return;
            }

            if (poolPostAnimationIdleTick == Integer.MIN_VALUE) {
                poolPostAnimationIdleTick = gameTick;
                setState(KspMadCowState.USING_REFRESHMENT_POOL,
                        "Pool complete; confirming player is stationary");
                return;
            }

            if (gameTick == Integer.MIN_VALUE
                    || gameTick - poolPostAnimationIdleTick < POOL_POST_ANIMATION_IDLE_TICKS) {
                setState(KspMadCowState.USING_REFRESHMENT_POOL,
                        "Pool complete; waiting for stationary Cowbell hand-off");
                return;
            }

            // With Prayer disabled, the Pool is still used for the normal Ferox
            // refresh step, but Prayer-point changes must not gate Cowbell travel.
            // The animation transaction above is the authoritative completion signal.
            if (!config.usePrayer()) {
                boolean settleComplete = gameTick != Integer.MIN_VALUE
                        && poolRefreshIssuedTick != Integer.MIN_VALUE
                        && gameTick - poolRefreshIssuedTick >= POOL_REFRESH_SETTLE_TICKS;
                if (!settleComplete || isPlayerCurrentlyAnimating()) {
                    setState(KspMadCowState.USING_REFRESHMENT_POOL,
                            "Prayer disabled; waiting for Pool interaction to finish");
                    return;
                }

                completeFeroxPoolRefresh(
                        "Pool animation complete; preparing Cowbell locally");
                return;
            }

            int prayerPoints = getCurrentPrayerPointsSafe();
            boolean prayerChanged = poolPrayerPointsBeforeInteraction >= 0
                    && prayerPoints > poolPrayerPointsBeforeInteraction;

            if (prayerChanged) {
                int before = poolPrayerPointsBeforeInteraction;
                long now = System.currentTimeMillis();
                if (poolPrayerRestoredAtMs == 0L) {
                    poolPrayerRestoredAtMs = now;
                    visibleDebug("Bank", "Pool Prayer changed before=" + before
                            + " now=" + prayerPoints + "; waiting "
                            + COWBELL_AFTER_POOL_DELAY_MS + "ms before Cowbell");
                }

                long elapsed = now - poolPrayerRestoredAtMs;
                if (elapsed < COWBELL_AFTER_POOL_DELAY_MS) {
                    setState(KspMadCowState.USING_REFRESHMENT_POOL,
                            "Prayer refreshed; waiting briefly before Cowbell");
                    return;
                }

                completeFeroxPoolRefresh(
                        "Pool animation complete and Prayer refreshed; preparing Cowbell locally");
                return;
            }

            // Never use the settle timer as a substitute for a Prayer-point increase.
            // If Prayer was below its real level when Drink was issued, the Cowbell
            // MUST remain blocked until the client has actually reported a higher
            // boosted Prayer level. This prevents teleporting while the Pool action
            // is still pending or before the Prayer orb/widget visibly updates.
            if (!poolPrayerWasFullBeforeInteraction) {
                setState(KspMadCowState.USING_REFRESHMENT_POOL,
                        "Waiting for Prayer points to increase after Pool");
                return;
            }

            // Prayer was already full before Drink, so no increase can occur. In
            // that one case only, use the normal Pool settle/animation completion
            // as the completion signal.
            boolean settleComplete = gameTick != Integer.MIN_VALUE
                    && poolRefreshIssuedTick != Integer.MIN_VALUE
                    && gameTick - poolRefreshIssuedTick >= POOL_REFRESH_SETTLE_TICKS;

            if (!settleComplete || isPlayerCurrentlyAnimating()) {
                setState(KspMadCowState.USING_REFRESHMENT_POOL,
                        "Prayer already full; waiting for Pool interaction to finish");
                return;
            }

            completeFeroxPoolRefresh(
                    "Pool animation complete; preparing Cowbell locally");
            return;
        }

        Rs2TileObjectModel pool = Microbot.getRs2TileObjectCache().query()
                .withId(POOL_OF_REFRESHMENT_ID)
                .where(this::isObjectInPlayerWorldView)
                .nearestOnClientThread();

        if (pool == null) {
            // Hard rule: no walker calls in Ferox. After the bank closes we wait for
            // the Pool object to be available and click it directly; we never issue a
            // tile/minimap/web-walker movement command to reach it.
            clearWalkerRouteIfActive("brutus-ferox-waiting-for-pool-object");
            setState(KspMadCowState.USING_REFRESHMENT_POOL,
                    "Bank closed; waiting to click Pool of Refreshment");
            return;
        }

        // If the Pool object is loaded, interact with the object directly even when
        // a few tiles away. The game can locally approach the object; no web-walker
        // route or transport graph is needed.
        String poolAction = matchingAction(pool, new String[]{"Drink"});
        if (poolAction == null) {
            setState(KspMadCowState.USING_REFRESHMENT_POOL,
                    "Waiting for Pool of Refreshment Drink action");
            return;
        }

        clearWalkerRouteIfActive("brutus-ferox-pool");
        setState(KspMadCowState.USING_REFRESHMENT_POOL,
                "Using Pool of Refreshment");
        int prayerBeforePool = getCurrentPrayerPointsSafe();
        int realPrayerBeforePool = getRealPrayerLevelSafe();
        if (invokeSceneObjectActionDirect(pool, poolAction, "Pool of Refreshment")) {
            poolRefreshIssued = true;
            poolRefreshIssuedTick = gameTick;
            poolPrayerPointsBeforeInteraction = prayerBeforePool;
            poolPrayerWasFullBeforeInteraction = realPrayerBeforePool > 0
                    && prayerBeforePool >= realPrayerBeforePool;
            poolAnimationObserved = false;
            poolPostAnimationIdleTick = Integer.MIN_VALUE;
            poolPrayerRestoredAtMs = 0L;
            visibleDebug("Bank", "Pool of Refreshment interaction issued id="
                    + POOL_OF_REFRESHMENT_ID + " location=" + POOL_OF_REFRESHMENT_LOCATION
                    + " action=" + poolAction + " prayerBefore=" + prayerBeforePool
                    + " realPrayer=" + realPrayerBeforePool
                    + " alreadyFull=" + poolPrayerWasFullBeforeInteraction);
        }
    }

    private void completeFeroxPoolRefresh(String status) {
        postBankRefreshPending = false;
        resetPoolRefreshState();
        lmsBankTeleportIssued = false;
        lmsBankTeleportIssuedTick = Integer.MIN_VALUE;
        feroxPostPoolTravelPending = true;
        clearWalkerRouteIfActive("brutus-ferox-pool-complete");
        setState(KspMadCowState.TRAVELLING, status);
    }

    /**
     * Runs only after the Ferox bank has been closed and the Pool interaction has
     * fully completed. No movement API is allowed here; preparation is inventory/
     * equipment interaction only, followed by Cowbell Teleport.
     */
    private void handleFeroxPostPoolTravel() {
        if (!feroxPostPoolTravelPending) {
            return;
        }

        if (!isAtFeroxEnclave()) {
            feroxPostPoolTravelPending = false;
            return;
        }

        clearWalkerRouteIfActive("brutus-ferox-post-pool-no-walker");

        if (Rs2Bank.isOpen()) {
            setState(KspMadCowState.BANKING, "Closing bank before Cowbell preparation");
            Rs2Bank.closeBank();
            return;
        }

        if (Rs2Player.isMoving() || isPlayerCurrentlyAnimating()) {
            setState(KspMadCowState.TRAVELLING,
                    "Pool complete; waiting for local movement/animation to finish");
            return;
        }

        if (chargeCowbellIfPossible()) {
            return;
        }

        if (!Rs2Inventory.hasItem(COWBELL_CHARGED_ID)
                && !Rs2Equipment.isWearing(COWBELL_CHARGED_ID)) {
            feroxPostPoolTravelPending = false;
            initialBankCheckPending = true;
            setState(KspMadCowState.BANKING,
                    "Cowbell is not charged; reopening Ferox bank for supplies");
            return;
        }

        if (equipMooletaFromInventory()) {
            return;
        }

        if (balanceCombatStyle()) {
            return;
        }

        feroxPostPoolTravelPending = false;
        setState(KspMadCowState.TRAVELLING,
                "Ferox sequence complete; teleporting with Cowbell");
        travelWithCowbell(false);
    }

    private boolean handleMagicBanking() {
        if (combatMode != CombatMode.MAGIC || config == null || config.combatSpell() == null) {
            return false;
        }

        KspMadCowSpell selection = config.combatSpell();
        int staffId = selection.getStaffItemId();

        if (!Rs2Equipment.isWearing(staffId) && !Rs2Inventory.hasItem(staffId)) {
            if (!Rs2Bank.hasBankItem(staffId, 1)) {
                supplyFailure("Missing elemental staff for " + selection + " (item " + staffId + ")");
                return true;
            }

            setState(KspMadCowState.BANKING, "Withdrawing staff for " + selection);
            visibleDebug("Magic", "withdrawing elemental staff id=" + staffId
                    + " spell=" + selection);
            if (Rs2Bank.withdrawOne(staffId)) {
                markBankAction("withdraw " + selection + " staff",
                        () -> Rs2Inventory.hasItem(staffId) || Rs2Equipment.isWearing(staffId));
            }
            return true;
        }

        boolean matchingElementalStaffEquipped = Rs2Equipment.isWearing(staffId);

        for (Map.Entry<Runes, Integer> entry : selection.getRequiredRunes().entrySet()) {
            Runes rune = entry.getKey();
            Integer perCast = entry.getValue();
            if (rune == null || perCast == null || perCast <= 0) {
                continue;
            }

            // An equipped elemental staff provides an unlimited supply of its
            // matching elemental rune. Do not withdraw that rune from the bank.
            // This is intentionally based on equipment, not merely having the staff
            // in inventory, so the bank behavior reflects the character's active gear.
            if (matchingElementalStaffEquipped && rune == selection.getStaffRune()) {
                visibleDebug("Magic", "skipping " + rune
                        + " rune withdrawal; matching staff is equipped id=" + staffId
                        + " spell=" + selection);
                continue;
            }

            int runeId = rune.getItemId();
            int current = Rs2Inventory.itemQuantity(runeId);

            // The selected spell owns the rune list. Whenever the bank still has
            // one of those rune types, take the complete stack instead of targeting
            // an arbitrary number of casts. Stackable runes do not consume extra
            // slots as their quantity grows.
            if (Rs2Bank.hasBankItem(runeId, 1)) {
                final int previousQuantity = current;
                setState(KspMadCowState.BANKING,
                        "Withdrawing all " + rune.name().toLowerCase(Locale.ROOT)
                                + " runes for " + selection);
                visibleDebug("Magic", "withdraw-all rune=" + rune
                        + " inventoryBefore=" + current
                        + " spell=" + selection);
                if (Rs2Bank.withdrawAll(runeId)) {
                    markBankAction("withdraw all " + rune.name().toLowerCase(Locale.ROOT) + " runes",
                            () -> Rs2Inventory.itemQuantity(runeId) > previousQuantity
                                    || !Rs2Bank.hasBankItem(runeId, 1));
                }
                return true;
            }

            // Once the bank stack is exhausted, make sure there are at least enough
            // runes for one cast unless the equipped elemental staff supplies that
            // rune. This reports an actual missing-supply problem without reintroducing
            // a configurable cast-count target.
            if (!(matchingElementalStaffEquipped && rune == selection.getStaffRune())
                    && current < perCast) {
                supplyFailure("Not enough " + rune.name().toLowerCase(Locale.ROOT)
                        + " runes to cast " + selection
                        + " (need " + perCast + ", inventory " + current + ")");
                return true;
            }
        }

        return false;
    }

    private int minimumMagicRuneReserve(Runes rune) {
        if (combatMode != CombatMode.MAGIC
                || config == null
                || config.combatSpell() == null
                || rune == null) {
            return 0;
        }

        KspMadCowSpell selection = config.combatSpell();
        if (rune == selection.getStaffRune()) {
            return 0;
        }

        Integer perCast = selection.getRequiredRunes().get(rune);
        return perCast == null || perCast <= 0 ? 0 : perCast;
    }

    private boolean isConfiguredMagicSupply(int itemId) {
        if (combatMode != CombatMode.MAGIC || config == null || config.combatSpell() == null) {
            return false;
        }

        KspMadCowSpell selection = config.combatSpell();
        if (itemId == selection.getStaffItemId()) {
            return true;
        }

        for (Runes rune : selection.getRequiredRunes().keySet()) {
            if (rune != null && rune.getItemId() == itemId) {
                return true;
            }
        }
        return false;
    }

    private int chargeableCowbellAirRunes() {
        int total = Rs2Inventory.itemQuantity(AIR_RUNE_ID);
        int reserve = minimumMagicRuneReserve(Runes.AIR);
        return Math.max(0, total - reserve);
    }

    private boolean keepDuringBanking(Rs2ItemModel item) {
        if (item == null) {
            return false;
        }
        int id = item.getId();
        if (id == config.food().getId()
                || id == AIR_RUNE_ID
                || id == COWBELL_EMPTY_ID
                || id == COWBELL_CHARGED_ID
                || (config.demonicBrutus() && id == ABYSSAL_POTATO_ID)
                || id == MOOLETA_ID
                || id == trackedRangedAmmoId
                || isConfiguredMagicSupply(id)) {
            return true;
        }
        return config.useStatBoostingPotions() && isKnownStatPotion(item.getName());
    }

    private void markBankAction(String description, BooleanSupplier completionCondition) {
        bankActionPending = true;
        bankActionInventoryRevision = inventoryRevision.get();
        bankActionDescription = description;
        bankActionComplete = completionCondition;
    }

    private boolean awaitingBankInventoryChange() {
        if (!bankActionPending) {
            return false;
        }
        boolean completed = bankActionComplete != null && bankActionComplete.getAsBoolean();
        if (completed || inventoryRevision.get() != bankActionInventoryRevision) {
            bankActionPending = false;
            bankActionDescription = "";
            bankActionComplete = null;
            return false;
        }
        setState(KspMadCowState.BANKING, "Waiting for inventory update: " + bankActionDescription);
        return true;
    }

    public void onInventoryChanged() {
        inventoryRevision.incrementAndGet();
    }

    /**
     * Capture the server rejection immediately instead of waiting for the LMS landing
     * timeout. The exact wording has changed across interfaces, so match both the
     * explicit Minigame Teleport name and the established 20-minute restriction text.
     */
    public void onGameMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        String normalized = message
                .replaceAll("<[^>]+>", "")
                .toLowerCase(Locale.ROOT);
        boolean namedCooldown = normalized.contains("minigame teleport")
                && (normalized.contains("cooldown")
                    || normalized.contains("20 minute")
                    || normalized.contains("wait")
                    || normalized.contains("once every"));
        boolean genericTwentyMinuteTeleport = normalized.contains("teleport")
                && normalized.contains("20 minute")
                && (normalized.contains("only") || normalized.contains("once every"));

        if (!namedCooldown && !genericTwentyMinuteTeleport) {
            return;
        }

        minigameTeleportCooldownUntilMs = System.currentTimeMillis()
                + MINIGAME_TELEPORT_COOLDOWN_MS;
        log.info("[KSP Mad Cow][Bank] Minigame Teleport cooldown detected from game message: {}",
                normalized);
    }

    public void onGraphicsObjectCreated(GraphicsObject graphicsObject) {
        if (!running || graphicsObject == null) {
            return;
        }

        int graphicId = graphicsObject.getId();

        // Demonic Brutus phase two launches four prayer projectiles during each
        // special. Switch on the launch graphic itself rather than the later impact
        // graphic so the correct overhead can be active before damage resolves.
        if (config != null && config.demonicBrutus()) {
            Rs2PrayerEnum protection = protectionPrayerForDemonicLaunch(graphicId);
            if (protection != null) {
                demonicProtectionPrayer = protection;
                int gameTick = currentGameTick();
                demonicProtectionPrayerUntilTick = gameTick == Integer.MIN_VALUE
                        ? Integer.MIN_VALUE
                        : gameTick + DEMONIC_PRAYER_OVERRIDE_TICKS;
                visibleDebug("Demonic", "phase-two projectile launch id=" + graphicId
                        + " -> " + protection.getName()
                        + " tick=" + gameTick);
            }
        }

        if (!STOMP_GRAPHICS.contains(graphicId) || graphicsObject.getLocation() == null) {
            return;
        }

        try {
            WorldPoint tile = WorldPoint.fromLocal(Microbot.getClient(), graphicsObject.getLocation());
            if (tile == null) {
                return;
            }

            int gameTick = Microbot.getClient().getTickCount();
            stompImpactTileTicks.put(tile, gameTick);

            // These graphics are impact-time evidence. They are intentionally not
            // allowed to start or delay a dodge because movement initiated here
            // would occur after the dangerous tiles have already resolved.
            visibleDebug("Stomp", "impact graphic id=" + graphicId
                    + " tile=" + tile
                    + " tick=" + gameTick
                    + " active=" + activeSpecial
                    + " selectedSafeTile=" + specialDodgeTarget);
            if (activeSpecial == SpecialAttack.STOMP
                    && specialDodgeTarget != null
                    && specialDodgeTarget.equals(tile)) {
                visibleDebug("Stomp", "WARNING impact appeared on predicted safe tile " + tile);
            }
        } catch (Exception ex) {
            log.debug("Unable to record Brutus Stomp graphic tile", ex);
        }
    }

    private Rs2PrayerEnum protectionPrayerForDemonicLaunch(int graphicId) {
        if (graphicId == DEMONIC_MAGIC_LAUNCH_GRAPHIC) {
            return Rs2PrayerEnum.PROTECT_MAGIC;
        }
        if (graphicId == DEMONIC_MELEE_LAUNCH_GRAPHIC) {
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        if (graphicId == DEMONIC_RANGED_LAUNCH_GRAPHIC) {
            return Rs2PrayerEnum.PROTECT_RANGE;
        }
        return null;
    }

    private Rs2PrayerEnum activeDemonicProtectionPrayer() {
        if (config == null || !config.demonicBrutus() || demonicProtectionPrayer == null) {
            return null;
        }

        int gameTick = currentGameTick();
        if (demonicProtectionPrayerUntilTick != Integer.MIN_VALUE
                && gameTick != Integer.MIN_VALUE
                && gameTick > demonicProtectionPrayerUntilTick) {
            demonicProtectionPrayer = null;
            demonicProtectionPrayerUntilTick = Integer.MIN_VALUE;
            return null;
        }
        return demonicProtectionPrayer;
    }

    private void pruneStompImpactTiles() {
        int gameTick = currentGameTick();
        if (gameTick == Integer.MIN_VALUE || stompImpactTileTicks.isEmpty()) {
            return;
        }

        boolean removed = stompImpactTileTicks.entrySet().removeIf(entry -> {
            int observedTick = entry.getValue();
            return observedTick > gameTick
                    || gameTick - observedTick > STOMP_GRAPHICS_LIFETIME_TICKS;
        });
        if (removed) {
        }
    }

    private Set<WorldPoint> currentStompImpactTiles() {
        pruneStompImpactTiles();
        if (stompImpactTileTicks.isEmpty()) {
            return Set.of();
        }

        int minimumTick = stompActivationTick == Integer.MIN_VALUE
                ? Integer.MIN_VALUE
                : stompActivationTick - 1;
        Set<WorldPoint> result = new HashSet<>();
        stompImpactTileTicks.forEach((tile, observedTick) -> {
            if (observedTick >= minimumTick) {
                result.add(tile);
            }
        });
        return result;
    }

    private boolean handleCowbellChargeAmountInput() {
        String prompt = visibleChatboxFullInputText();
        boolean cowbellPromptVisible = isCowbellChargeAmountPrompt(prompt);

        if (!cowbellPromptVisible) {
            // Once the server applies the submitted quantity, inventoryRevision
            // changes because air runes and/or the Cowbell stack changed.
            if (cowbellChargeInputPending
                    && inventoryRevision.get() != cowbellChargeAttemptRevision) {
                clearCowbellChargeInputState();
                cowbellChargeAttemptRevision = -1L;
                cowbellChargeAttemptTick = Integer.MIN_VALUE;
            }
            return false;
        }

        int availableAirRunes = chargeableCowbellAirRunes();
        int amount = Math.min(100, Math.max(0, availableAirRunes));
        if (amount <= 0) {
            setState(KspMadCowState.CHARGING_COWBELL,
                    "Cowbell charge prompt open, but no air runes remain");
            return true;
        }

        // Recover safely if the plugin was started while this prompt was already
        // open, or if the prompt appeared after the normal state timed out.
        if (!cowbellChargeInputPending) {
            cowbellChargeInputPending = true;
            cowbellChargeAttemptRevision = inventoryRevision.get();
            cowbellChargeAttemptTick = currentGameTick();
            cowbellChargeInputAmount = amount;
        } else if (cowbellChargeInputAmount <= 0) {
            cowbellChargeInputAmount = amount;
        }

        if (!cowbellChargeInputSubmitted) {
            cowbellChargeInputAmount = Math.min(100, Math.min(cowbellChargeInputAmount, amount));
            setState(KspMadCowState.CHARGING_COWBELL,
                    "Entering " + cowbellChargeInputAmount + " Cowbell charge"
                            + (cowbellChargeInputAmount == 1 ? "" : "s"));

            // RuneScape's amount box accepts ordinary typed digits followed by
            // Enter. Rs2Keyboard dispatches those key events directly to the game
            // canvas, so this works without stealing desktop focus.
            Rs2Keyboard.typeString(Integer.toString(cowbellChargeInputAmount));
            Rs2Keyboard.enter();
            cowbellChargeInputSubmitted = true;
            cowbellChargeInputSubmitTick = currentGameTick();
        } else {
            setState(KspMadCowState.CHARGING_COWBELL,
                    "Waiting for " + cowbellChargeInputAmount + " Cowbell charges to apply");
        }

        return true;
    }

    private String visibleChatboxFullInputText() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            StringBuilder visibleText = new StringBuilder();
            WidgetInfo[] candidates = {
                    WidgetInfo.CHATBOX_FULL_INPUT,
                    WidgetInfo.CHATBOX_TITLE,
                    WidgetInfo.CHATBOX_INPUT
            };

            for (WidgetInfo candidate : candidates) {
                var widget = Microbot.getClient().getWidget(candidate);
                if (widget == null || widget.isHidden() || widget.getText() == null) {
                    continue;
                }
                if (visibleText.length() > 0) {
                    visibleText.append(' ');
                }
                visibleText.append(widget.getText());
            }

            return visibleText.length() == 0 ? null : visibleText.toString();
        }).orElse(null);
    }

    private boolean isCowbellChargeAmountPrompt(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = text
                .replaceAll("<[^>]*>", " ")
                .replace('&', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        return normalized.contains("how many charges do you want to apply");
    }

    private void clearCowbellChargeInputState() {
        cowbellChargeInputPending = false;
        cowbellChargeInputSubmitted = false;
        cowbellChargeInputAmount = 0;
        cowbellChargeInputSubmitTick = Integer.MIN_VALUE;
    }

    /**
     * Cowbell charging/teleporting must happen with the bank fully closed.
     * Rs2Bank.closeBank() is not guaranteed to make Rs2Bank.isOpen() false in the
     * same script cycle, so callers stop for this tick and re-check on the next one.
     */
    private boolean closeBankBeforeCowbellAction(String action) {
        if (!Rs2Bank.isOpen()) {
            return false;
        }

        clearWalkerRouteIfActive("brutus-close-bank-before-cowbell");
        setState(KspMadCowState.BANKING, "Closing bank before " + action);
        Rs2Bank.closeBank();
        return true;
    }

    /**
     * Cancels any shortest-path state and waits for any interaction-driven movement
     * inside Ferox to finish. No walker movement command is issued in the enclave.
     */
    private boolean stopResidualFeroxMovement(String status) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null || !FEROX_ENCLAVE_AREA.contains(playerLocation)) {
            return false;
        }

        clearWalkerRouteIfActive("brutus-ferox-stop-residual-movement");

        if (!Rs2Player.isMoving()) {
            return false;
        }

        // Do not counter-click with Rs2Walker.walkFastCanvas here. Once in Ferox the
        // plugin is interaction-only; simply wait for the local interaction approach
        // to finish before continuing.
        lastFeroxMovementStopAtMs = System.currentTimeMillis();
        setState(KspMadCowState.USING_REFRESHMENT_POOL, status);
        return true;
    }

    /**
     * Recover if an unintended Ferox movement/transport puts the account inside
     * Death's Office. The unique exit portal (object 39549, action Use) returns the
     * player to the entrance they came from. Keep postBankRefreshPending intact so,
     * if this happened before the Pool transaction completed, the Pool sequence
     * resumes after returning to Ferox.
     */
    private boolean recoverFromDeathsOffice() {
        Rs2TileObjectModel exitPortal = Microbot.getRs2TileObjectCache().query()
                .withId(DEATHS_OFFICE_EXIT_PORTAL_ID)
                .nearestOnClientThread();
        if (exitPortal == null) {
            return false;
        }

        clearWalkerRouteIfActive("brutus-unexpected-deaths-office");
        resetCowbellTravelAttemptState();
        cowbellTravelIssued = false;
        travelRequired = true;
        instanceConfirmed = false;
        leavingInstance = false;
        quickLeaveClickIssued = false;
        quickLeaveLastAttemptAtMs = 0L;

        String action = matchingAction(exitPortal, new String[]{"Use"});
        int gameTick = currentGameTick();
        if (action == null) {
            setState(KspMadCowState.TRAVELLING,
                    "Unexpectedly in Death's Office; waiting for exit portal");
            return true;
        }

        if (lastDeathsOfficeExitAttemptTick != Integer.MIN_VALUE
                && gameTick != Integer.MIN_VALUE
                && gameTick - lastDeathsOfficeExitAttemptTick < DEATHS_OFFICE_EXIT_RETRY_TICKS) {
            setState(KspMadCowState.TRAVELLING,
                    "Unexpectedly in Death's Office; waiting to retry exit portal");
            return true;
        }

        if (!playerReadyForAction()) {
            setState(KspMadCowState.TRAVELLING,
                    "Unexpectedly in Death's Office; waiting to use exit portal");
            return true;
        }

        setState(KspMadCowState.TRAVELLING,
                "Unexpectedly in Death's Office; returning to Ferox");
        boolean issued = exitPortal.click(action);
        lastDeathsOfficeExitAttemptTick = gameTick;
        visibleDebug("Travel", "Death's Office recovery exit issued=" + issued
                + " action=" + action);
        return true;
    }

    private boolean chargeCowbellIfPossible() {
        // Hard guard as this method can be reached directly from several post-bank
        // states. Inventory combine operations against the Cowbell must never be
        // attempted while the bank interface is open.
        if (closeBankBeforeCowbellAction("Cowbell charging")) {
            return true;
        }

        long revision = inventoryRevision.get();
        int gameTick = currentGameTick();

        if (cowbellChargeInputPending) {
            if (revision != cowbellChargeAttemptRevision) {
                clearCowbellChargeInputState();
                cowbellChargeAttemptRevision = -1L;
                cowbellChargeAttemptTick = Integer.MIN_VALUE;
            } else {
                int anchorTick = cowbellChargeInputSubmitted
                        ? cowbellChargeInputSubmitTick
                        : cowbellChargeAttemptTick;
                int waitTicks = cowbellChargeInputSubmitted ? 5 : 4;

                if (gameTick == Integer.MIN_VALUE
                        || anchorTick == Integer.MIN_VALUE
                        || gameTick <= anchorTick + waitTicks) {
                    setState(KspMadCowState.CHARGING_COWBELL,
                            cowbellChargeInputSubmitted
                                    ? "Waiting for Cowbell charges to apply"
                                    : "Waiting for Cowbell amount prompt");
                    return true;
                }

                // No prompt and no inventory update means the Cowbell rejected the
                // use (normally because it is already full). Do not repeatedly use
                // the same unchanged rune stack.
                clearCowbellChargeInputState();
                return false;
            }
        }

        int airRunes = chargeableCowbellAirRunes();
        if (airRunes <= 0) {
            cowbellChargeAttemptRevision = -1L;
            cowbellChargeAttemptTick = Integer.MIN_VALUE;
            clearCowbellChargeInputState();
            return false;
        }

        if (Rs2Equipment.isWearing(COWBELL_EMPTY_ID)
                || Rs2Equipment.isWearing(COWBELL_CHARGED_ID)) {
            if (!playerReadyForAction()) {
                setState(KspMadCowState.CHARGING_COWBELL, "Waiting to remove Cowbell for charging");
                return true;
            }

            int equippedCowbellId = Rs2Equipment.isWearing(COWBELL_CHARGED_ID)
                    ? COWBELL_CHARGED_ID
                    : COWBELL_EMPTY_ID;
            setState(KspMadCowState.CHARGING_COWBELL,
                    "Removing Cowbell to add " + airRunes + " air rune" + (airRunes == 1 ? "" : "s"));
            Rs2Equipment.unEquip(equippedCowbellId);
            return true;
        }

        int cowbellId = Rs2Inventory.hasItem(COWBELL_CHARGED_ID)
                ? COWBELL_CHARGED_ID
                : Rs2Inventory.hasItem(COWBELL_EMPTY_ID)
                ? COWBELL_EMPTY_ID
                : -1;
        if (cowbellId == -1) {
            return false;
        }

        if (!playerReadyForAction()) {
            setState(KspMadCowState.CHARGING_COWBELL, "Waiting to charge Cowbell");
            return true;
        }

        int amountToApply = Math.min(100, airRunes);
        setState(KspMadCowState.CHARGING_COWBELL,
                "Opening Cowbell amount prompt for " + amountToApply + " charge"
                        + (amountToApply == 1 ? "" : "s"));
        Rs2Inventory.combine(AIR_RUNE_ID, cowbellId);
        cowbellChargeAttemptRevision = revision;
        cowbellChargeAttemptTick = gameTick;
        cowbellChargeInputPending = true;
        cowbellChargeInputSubmitted = false;
        cowbellChargeInputAmount = amountToApply;
        cowbellChargeInputSubmitTick = Integer.MIN_VALUE;
        return true;
    }

    private void travelWithCowbell() {
        travelWithCowbell(false);
    }

    private void travelWithCowbell(boolean bypassAnimationWait) {
        if (!travelRequired || instanceConfirmed || clientInstanceDetected || leavingInstance) {
            resetCowbellTravelAttemptState();
            return;
        }

        // Teleport is an inventory/equipment action and must never be issued through
        // an open bank. This also protects retry passes from clicking Teleport if a
        // bank was manually opened while a Cowbell transaction was pending.
        if (closeBankBeforeCowbellAction("Cowbell Teleport")) {
            return;
        }

        WorldPoint currentLocation = Rs2Player.getWorldLocation();

        // Absolute Ferox safety guard. Cowbell Teleport must never be issued or
        // retried while any player animation is still active at Ferox. In particular,
        // the Pool's Prayer/stat update can arrive before its Drink animation ends.
        // Continuously cancel stale walker routes here as well so an old bank/pool
        // target cannot pull the player toward Death's Domain during the wait.
        if (currentLocation != null && FEROX_ENCLAVE_AREA.contains(currentLocation)) {
            if (isPlayerCurrentlyAnimating()) {
                clearWalkerRouteIfActive("brutus-ferox-animation-before-cowbell");
                setState(KspMadCowState.USING_REFRESHMENT_POOL,
                        "Waiting for Ferox animation to end before Cowbell Teleport");
                return;
            }

            // Movement is independent of the animation id. Do not allow the
            // bypassAnimationWait retry path to click Cowbell while the character
            // is still following any queued Ferox movement. Stop it with a local
            // current-tile scene click and wait for a later tick.
            if (stopResidualFeroxMovement("Stopping Ferox movement before Cowbell Teleport")) {
                return;
            }
        }

        // Do not infer Cowbell arrival from coordinates. The live Release/
        // Release interaction is the authoritative proof that the player
        // is at the Brutus pen. This also prevents unrelated locations from being
        // classified as "in Brutus area".
        ObjectAction visiblePenEntry = findPenEntryAction();
        if (visiblePenEntry != null) {
            clearWalkerRouteIfActive("brutus-pen-entry-visible-before-cowbell");
            resetCowbellTravelAttemptState();
            travelRequired = false;
            cowbellTravelIssued = true;
            lastPenEntryAction = "";
            lastPenEntryAttemptTick = Integer.MIN_VALUE;
            setState(KspMadCowState.TRAVELLING,
                    "Brutus pen detected; skipping Cowbell Teleport");
            return;
        }

        // A stale shortest-path target must never be allowed to compete with the
        // Cowbell transaction. This is intentionally done on every confirmation/
        // retry pass, not just immediately before the first click.
        clearWalkerRouteIfActive("brutus-cowbell-teleport-transaction");

        if (cowbellTravelPending) {
            if (cowbellTeleportConfirmed(currentLocation)) {
                visibleDebug("Travel", "Cowbell teleport confirmed origin="
                        + cowbellTravelOrigin + " now=" + currentLocation
                        + " attempts=" + cowbellTravelAttemptCount);
                cowbellTravelPending = false;
                cowbellTravelAttemptTick = Integer.MIN_VALUE;
                cowbellTravelOrigin = null;
                cowbellTravelAttemptCount = 0;
                cowbellTravelLastAttemptAtMs = 0L;
                travelRequired = false;
                cowbellTravelIssued = true;
                lastPenEntryAction = "";
                lastPenEntryAttemptTick = Integer.MIN_VALUE;
                setState(KspMadCowState.TRAVELLING,
                        "Cowbell teleport confirmed; locating Brutus pen");
                return;
            }

            int gameTick = currentGameTick();
            int ticksSinceAttempt = gameTick == Integer.MIN_VALUE
                    || cowbellTravelAttemptTick == Integer.MIN_VALUE
                    ? COWBELL_TRAVEL_RETRY_TICKS
                    : Math.max(0, gameTick - cowbellTravelAttemptTick);
            long now = System.currentTimeMillis();
            long msSinceAttempt = cowbellTravelLastAttemptAtMs <= 0L
                    ? COWBELL_TRAVEL_RETRY_COOLDOWN_MS
                    : Math.max(0L, now - cowbellTravelLastAttemptAtMs);

            if (ticksSinceAttempt < COWBELL_TRAVEL_RETRY_TICKS
                    || msSinceAttempt < COWBELL_TRAVEL_RETRY_COOLDOWN_MS) {
                long remainingMs = Math.max(0L,
                        COWBELL_TRAVEL_RETRY_COOLDOWN_MS - msSinceAttempt);
                setState(KspMadCowState.TRAVELLING,
                        "Waiting for Cowbell retry cooldown (" + remainingMs + "ms)");
                return;
            }

            visibleDebug("Travel", "Cowbell click not confirmed after "
                    + ticksSinceAttempt + " ticks / " + msSinceAttempt
                    + "ms; retrying Teleport attempt="
                    + (cowbellTravelAttemptCount + 1));
            // Keep the original origin so any successful retry can be confirmed
            // against the location where the transaction began.
            cowbellTravelPending = false;
            cowbellTravelAttemptTick = Integer.MIN_VALUE;
        }

        if (!Rs2Inventory.hasItem(COWBELL_CHARGED_ID)
                && !Rs2Equipment.isWearing(COWBELL_CHARGED_ID)) {
            resetCowbellTravelAttemptState();
            initialBankCheckPending = true;
            return;
        }

        // The pool/altar completion signal may arrive slightly before the action
        // animation fully clears. The first attempt may therefore be rejected by
        // the game. We still permit that first attempt, but unlike older versions
        // we do not treat a returned click boolean as teleport success; failed
        // attempts are retried until position movement confirms the teleport.
        if (!bypassAnimationWait && !playerReadyForAction()) {
            setState(KspMadCowState.TRAVELLING, "Waiting to use Cowbell");
            return;
        }

        if (cowbellTravelOrigin == null) {
            cowbellTravelOrigin = currentLocation;
        }

        int gameTick = currentGameTick();
        setState(KspMadCowState.TRAVELLING,
                "Using Cowbell: " + COWBELL_TRAVEL_ACTION
                        + " (attempt " + (cowbellTravelAttemptCount + 1) + ")");

        boolean clickIssued = interactInventoryFirstAvailable(
                COWBELL_CHARGED_ID, COWBELL_TRAVEL_ACTION);
        if (!clickIssued && Rs2Equipment.isWearing(COWBELL_CHARGED_ID)) {
            clickIssued = Rs2Equipment.interact(
                    COWBELL_CHARGED_ID, COWBELL_TRAVEL_ACTION);
        }

        cowbellTravelAttemptCount++;
        cowbellTravelAttemptTick = gameTick;
        cowbellTravelLastAttemptAtMs = System.currentTimeMillis();

        if (clickIssued) {
            cowbellTravelPending = true;
            cowbellTravelIssued = false;
            visibleDebug("Travel", "Cowbell Teleport click issued; awaiting actual position change"
                    + " origin=" + cowbellTravelOrigin
                    + " attempt=" + cowbellTravelAttemptCount);
            setState(KspMadCowState.TRAVELLING,
                    "Cowbell clicked; waiting for teleport confirmation");
        } else {
            // Do not enter ERROR and do not release the transaction. The item may
            // temporarily reject interaction while the Pool/altar animation is
            // settling. Retry on the next retry window instead.
            cowbellTravelPending = true;
            cowbellTravelIssued = false;
            visibleDebug("Travel", "Cowbell Teleport action was not issued; will retry"
                    + " attempt=" + cowbellTravelAttemptCount);
            setState(KspMadCowState.TRAVELLING,
                    "Cowbell Teleport not accepted; retrying");
        }
    }

    private boolean cowbellTeleportConfirmed(WorldPoint currentLocation) {
        if (!cowbellTravelPending) {
            return false;
        }

        // Confirm Cowbell travel only from a live Brutus-pen interaction. Do not use
        // player coordinates, displacement, or a generic "Brutus area" rectangle:
        // those signals previously allowed stale movement/instance state to masquerade
        // as a successful Cowbell teleport.
        return findPenEntryAction() != null;
    }

    private void resetCowbellTravelAttemptState() {
        cowbellTravelPending = false;
        cowbellTravelAttemptTick = Integer.MIN_VALUE;
        cowbellTravelAttemptCount = 0;
        cowbellTravelLastAttemptAtMs = 0L;
        cowbellTravelOrigin = null;
    }

    private void enterInstance(ObjectAction entry) {
        int gameTick = currentGameTick();
        boolean sameActionAsLastAttempt = entry.action.equalsIgnoreCase(lastPenEntryAction);

        if (sameActionAsLastAttempt && lastPenEntryAttemptTick != Integer.MIN_VALUE) {
            int ticksSinceAttempt = gameTick == Integer.MIN_VALUE
                    ? PEN_ENTRY_RETRY_TICKS
                    : Math.max(0, gameTick - lastPenEntryAttemptTick);
            if (ticksSinceAttempt < PEN_ENTRY_RETRY_TICKS) {
                setState(KspMadCowState.ENTERING_INSTANCE,
                        "Waiting to retry pen gate (" + (PEN_ENTRY_RETRY_TICKS - ticksSinceAttempt) + " tick"
                                + (PEN_ENTRY_RETRY_TICKS - ticksSinceAttempt == 1 ? "" : "s") + ")");
                return;
            }

            // The previous gate interaction did not move us into the instance.
            // Only retry once the player is idle so we do not interrupt movement,
            // an animation, or the successful transition itself.
            if (!playerReadyForAction()) {
                setState(KspMadCowState.ENTERING_INSTANCE, "Gate retry ready; waiting for player to become idle");
                return;
            }

            setState(KspMadCowState.ENTERING_INSTANCE, "Retrying pen gate: " + entry.action);
        } else {
            setState(KspMadCowState.ENTERING_INSTANCE, "Pen action: " + entry.action);
            if (!playerReadyForAction()) {
                return;
            }
        }

        if (entry.object.click(entry.action)) {
            lastPenEntryAction = entry.action;
            lastPenEntryAttemptTick = gameTick;
            travelRequired = false;
        }
    }

    private void resetDemonicAttemptForInstance() {
        lastKnownBrutusNpc = null;
        demonicFeedPending = false;
        demonicFeedIssuedTick = Integer.MIN_VALUE;
        demonicFeedStartedTick = Integer.MIN_VALUE;
        demonicAttemptActive = false;
        demonicAttemptCompleted = false;
        demonicProtectionPrayer = null;
        demonicProtectionPrayerUntilTick = Integer.MIN_VALUE;
    }

    /**
     * Handles the one-time bone-bury warning. The permanent second option contains
     * punctuation ("Yes, and don't ask again."), so matching the older string
     * "Yes and don't ask again" does not work. Match the unique phrase instead,
     * then fall back to selecting option index 2. While the warning is visible we
     * always consume the tick so combat/entry logic cannot accidentally choose the
     * first generic "Yes" option.
     */
    private boolean handleBoneBuryDialogue() {
        if (!running || config == null || !config.buryBones()) {
            return false;
        }

        if (!Rs2Dialogue.hasQuestion("Bury the bones")) {
            return false;
        }

        setState(KspMadCowState.BURYING_BONES,
                "Confirming bone bury warning: don't ask again");

        boolean clicked = Rs2Dialogue.clickOption("don't ask again");
        if (!clicked) {
            // This dialogue is known to have Yes / Yes-and-don't-ask / No.
            // Restrict the index fallback to this exact question so option 2 can
            // never leak into an unrelated dialogue.
            clicked = Rs2Dialogue.keyPressForDialogueOption(2);
        }

        visibleDebug("Bones", "Bone bury warning detected; option 2 issued=" + clicked);
        return true;
    }

    /**
     * The Abyssal-potato confirmation is part of hard-mode selection, not the
     * configurable pen-entry confirmation. Always confirm it when Demonic Brutus
     * mode is enabled and this script has just issued the potato-on-Brutus action.
     */
    private boolean handleDemonicFeedDialogue() {
        if (!running || config == null || !config.demonicBrutus() || !demonicFeedPending) {
            return false;
        }

        if (Rs2Dialogue.hasDialogueOption("Yes")) {
            setState(KspMadCowState.ENTERING_INSTANCE, "Confirming Abyssal potato for Demonic Brutus");
            boolean clicked = Rs2Dialogue.clickOption("Yes");
            if (clicked) {
                visibleDebug("Demonic", "Confirmed Abyssal potato dialogue");
            }
            return clicked;
        }

        if (Rs2Dialogue.hasContinue()) {
            setState(KspMadCowState.ENTERING_INSTANCE, "Continuing Demonic Brutus transformation");
            Rs2Dialogue.clickContinue();
            return true;
        }

        return false;
    }

    private boolean prepareDemonicBrutus(ObjectAction quickLeave) {
        if (config == null || !config.demonicBrutus()) {
            return false;
        }

        Rs2NpcModel demonic = findBrutus();
        if (isAlive(demonic)) {
            if (!demonicAttemptActive) {
                visibleDebug("Demonic", "Demonic Brutus spawned id="
                        + demonic.getId() + " index=" + demonic.getIndex());
            }
            demonicAttemptActive = true;
            demonicFeedPending = false;
            demonicFeedIssuedTick = Integer.MIN_VALUE;
            demonicFeedStartedTick = Integer.MIN_VALUE;
            return false;
        }

        // At 0 HP the hard-mode encounter can briefly transition before phase two.
        // Once 15628 has been observed, never feed another potato during that same
        // instance merely because the target disappears for a few ticks.
        if (demonicAttemptActive) {
            setState(KspMadCowState.WAITING_FOR_RESPAWN,
                    "Waiting for Demonic Brutus phase transition");
            return true;
        }

        int gameTick = currentGameTick();
        boolean hasPotato = Rs2Inventory.hasItem(ABYSSAL_POTATO_ID);

        if (demonicFeedPending) {
            int elapsed = gameTick == Integer.MIN_VALUE
                    || demonicFeedIssuedTick == Integer.MIN_VALUE
                    ? 0
                    : gameTick - demonicFeedIssuedTick;

            if (!hasPotato) {
                int totalElapsed = gameTick == Integer.MIN_VALUE
                        || demonicFeedStartedTick == Integer.MIN_VALUE
                        ? 0
                        : gameTick - demonicFeedStartedTick;
                if (totalElapsed >= DEMONIC_TRANSFORM_TIMEOUT_TICKS) {
                    supplyFailure("Abyssal potato was consumed but Demonic Brutus did not appear");
                    return true;
                }
                setState(KspMadCowState.ENTERING_INSTANCE,
                        "Abyssal potato consumed; waiting for Demonic Brutus");
                return true;
            }

            // If neither a confirmation/transformation nor inventory consumption
            // occurred, permit another Use-on-NPC attempt after a short tick delay.
            if (elapsed < DEMONIC_FEED_RETRY_TICKS) {
                setState(KspMadCowState.ENTERING_INSTANCE,
                        "Waiting for Abyssal potato confirmation");
                return true;
            }
            demonicFeedPending = false;
        }

        if (!hasPotato) {
            disableManagedCombatPrayers();
            quickLeave(quickLeave, "Abyssal potato missing; returning to bank");
            return true;
        }

        Rs2NpcModel normalBrutus = findNormalBrutusForDemonicFeed();
        if (!isAlive(normalBrutus)) {
            setState(KspMadCowState.ENTERING_INSTANCE,
                    "Waiting for Brutus before feeding Abyssal potato");
            return true;
        }

        if (gameTick != Integer.MIN_VALUE
                && demonicFeedIssuedTick != Integer.MIN_VALUE
                && gameTick - demonicFeedIssuedTick < DEMONIC_FEED_RETRY_TICKS) {
            return true;
        }

        setState(KspMadCowState.ENTERING_INSTANCE, "Feeding Abyssal potato to Brutus");
        boolean issued = Rs2Inventory.useItemOnNpc(ABYSSAL_POTATO_ID, normalBrutus.getNpc());
        visibleDebug("Demonic", "Abyssal potato Use-on-Brutus issued=" + issued
                + " normalId=" + normalBrutus.getId()
                + " normalIndex=" + normalBrutus.getIndex()
                + " tick=" + gameTick);
        if (issued) {
            demonicFeedPending = true;
            demonicFeedIssuedTick = gameTick;
            if (demonicFeedStartedTick == Integer.MIN_VALUE) {
                demonicFeedStartedTick = gameTick;
            }
        }
        return true;
    }

    private Rs2NpcModel findNormalBrutusForDemonicFeed() {
        return Microbot.getRs2NpcCache().query()
                .withIds(BRUTUS_ID, BRUTUS_ALT_ID)
                .nearestOnClientThread();
    }

    private boolean handleEntryDialogue() {
        if (!running || config == null || !config.autoConfirmEntry()) {
            return false;
        }
        if (travelRequired && !cowbellTravelIssued && lastPenEntryAction.isEmpty()) {
            return false;
        }

        if (Rs2Dialogue.hasDialogueOption("Yes and don't ask again")) {
            setState(KspMadCowState.ENTERING_INSTANCE, "Confirming Brutus entry");
            return Rs2Dialogue.clickOption("Yes and don't ask again");
        }

        if (Rs2Dialogue.hasDialogueOption("Yes")) {
            setState(KspMadCowState.ENTERING_INSTANCE, "Confirming Brutus entry");
            return Rs2Dialogue.clickOption("Yes");
        }

        if (Rs2Dialogue.hasContinue()) {
            setState(KspMadCowState.ENTERING_INSTANCE, "Continuing Brutus entry dialogue");
            Rs2Dialogue.clickContinue();
            return true;
        }

        return false;
    }

    private boolean eatIfNeeded() {
        int hpPercent = hitpointsPercent();
        int threshold = config.eatAtPercent();
        if (hpPercent > threshold) {
            clearHealActionState();
            return false;
        }

        int debugTick = currentGameTick();
        if (debugTick != lastHealDebugTick) {
            lastHealDebugTick = debugTick;
            visibleDebug("Heal", "threshold reached hp=" + hpPercent
                    + "% threshold=" + threshold
                    + "% pending=" + healActionPending
                    + " foodId=" + config.food().getId()
                    + " foodQty=" + getConfiguredFoodQuantity());
        }

        // Eating must not depend on the generic idle predicate. OSRS permits food
        // use while the player is in combat, so attack animations cannot suppress
        // healing at or below the configured threshold.
        if (healActionPending) {
            boolean inventoryChanged = inventoryRevision.get() != healActionInventoryRevision;
            boolean hpChanged = hpPercent > healActionHpBefore;
            int gameTick = currentGameTick();
            boolean confirmationWindowExpired = gameTick != Integer.MIN_VALUE
                    && healActionIssuedTick != Integer.MIN_VALUE
                    && gameTick > healActionIssuedTick + 1;

            if (inventoryChanged || hpChanged) {
                visibleDebug("Heal", "food confirmed inventoryChanged=" + inventoryChanged
                        + " hpChanged=" + hpChanged
                        + " hpBefore=" + healActionHpBefore
                        + "% hpNow=" + hpPercent + "%");
                clearHealActionState();
                // If still below the threshold, allow another food on the next loop.
                return true;
            }

            if (!confirmationWindowExpired) {
                setState(KspMadCowState.HEALING,
                        "Waiting for food confirmation at " + hpPercent + "% HP");
                return true;
            }

            visibleDebug("Heal", "food click not confirmed; retrying");
            // The click was not accepted; retry after clearing only this attempt.
            clearHealActionState();
        }

        Rs2ItemModel food = Rs2Inventory.get(config.food().getId());
        if (food == null) {
            visibleDebug("Heal", "no configured food found hp=" + hpPercent
                    + "% foodId=" + config.food().getId());
            return false;
        }

        setState(KspMadCowState.HEALING,
                "Eating " + config.food().getName() + " at " + hpPercent + "% HP");
        long revisionBefore = inventoryRevision.get();
        boolean clicked = Rs2Inventory.interact(food, "Eat");
        if (!clicked) {
            clicked = Rs2Inventory.interact(food, "eat");
        }
        visibleDebug("Heal", "eat issued=" + clicked
                + " item=" + food.getName()
                + " hp=" + hpPercent
                + "% threshold=" + threshold + "%");
        if (clicked) {
            healActionPending = true;
            healActionInventoryRevision = revisionBefore;
            healActionHpBefore = hpPercent;
            healActionIssuedTick = currentGameTick();
            combatReacquirePending = true;
            return true;
        }
        return false;
    }

    private void clearHealActionState() {
        healActionPending = false;
        healActionInventoryRevision = -1L;
        healActionHpBefore = -1;
        healActionIssuedTick = Integer.MIN_VALUE;
    }

    private boolean restorePrayerBeforeTravel() {
        // Master toggle: when Prayer use is disabled, no altar restoration path may
        // block Cowbell travel (including the forced Lumbridge-bank fallback route).
        if (!config.usePrayer()) {
            lumbridgePostBankAltarPending = false;
            altarRestorePending = false;
            altarInteractionIssued = false;
            altarPrayerPointsBeforeInteraction = -1;
            altarTeleportPending = false;
            return false;
        }

        boolean forcedLumbridgeRestore = lumbridgePostBankAltarPending;
        if (!forcedLumbridgeRestore && !config.restorePrayerBeforeTravel()) {
            altarRestorePending = false;
            altarInteractionIssued = false;
            altarPrayerPointsBeforeInteraction = -1;
            return false;
        }

        int prayerPoints = getCurrentPrayerPointsSafe();
        int realPrayerLevel = getRealPrayerLevelSafe();

        // The cooldown fallback deliberately restores partially depleted Prayer after
        // Lumbridge banking. If Prayer is already full, there is nothing for the altar
        // to change, so complete the forced restore transaction without clicking it.
        if (forcedLumbridgeRestore
                && !altarInteractionIssued
                && prayerPoints >= Math.max(1, realPrayerLevel)) {
            lumbridgePostBankAltarPending = false;
            altarRestorePending = false;
            altarPrayerPointsBeforeInteraction = -1;
            clearWalkerRouteIfActive("brutus-lumbridge-prayer-already-full");
            setState(KspMadCowState.TRAVELLING,
                    "Prayer already full after Lumbridge bank; preparing Cowbell");
            return false;
        }

        // Ferox has its own local Prayer/stat restore source. Never leave Ferox for
        // the Lumbridge altar just because Prayer is empty or a stale Lumbridge
        // fallback transaction is still armed. This is especially important after
        // an LMS/Ferox arrival: the global walker is intentionally disabled here,
        // so reporting "Walking to Lumbridge altar" would otherwise deadlock the
        // travel state.
        boolean feroxPrayerRestoreRequired = forcedLumbridgeRestore
                || altarRestorePending
                || prayerPoints <= 0;
        if (feroxPrayerRestoreRequired && isAtFeroxEnclave()) {
            clearWalkerRouteIfActive("brutus-ferox-use-pool-instead-of-lumbridge-altar");

            // Any Lumbridge altar transaction is invalid once we are physically in
            // Ferox. Clear it before deciding whether supplies still need banking.
            lumbridgePostBankAltarPending = false;
            altarRestorePending = false;
            altarInteractionIssued = false;
            altarPrayerPointsBeforeInteraction = -1;
            altarTeleportPending = false;

            // Preserve the required Ferox order. If concrete supplies are missing,
            // let the normal banking branch run first; banking completion will queue
            // the Pool automatically. Otherwise go directly to the Pool now.
            if (needsBanking()) {
                setState(KspMadCowState.BANKING,
                        "At Ferox; banking before Pool instead of using Lumbridge altar");
                return false;
            }

            postBankRefreshPending = true;
            feroxPostPoolTravelPending = false;
            resetPoolRefreshState();
            setState(KspMadCowState.USING_REFRESHMENT_POOL,
                    "At Ferox; using Pool of Refreshment instead of Lumbridge altar");
            handlePostBankRefresh();
            return true;
        }

        // Prayer points changing after Pray-at is the authoritative completion
        // signal. Immediately transition to Cowbell teleport without waiting for
        // the altar animation or generic player-idle state.
        if (altarInteractionIssued
                && altarPrayerPointsBeforeInteraction >= 0
                && prayerPoints != altarPrayerPointsBeforeInteraction) {
            visibleDebug("Altar", "Prayer changed after Pray-at before="
                    + altarPrayerPointsBeforeInteraction + " now=" + prayerPoints
                    + "; teleporting immediately");
            altarRestorePending = false;
            altarInteractionIssued = false;
            altarPrayerPointsBeforeInteraction = -1;
            lumbridgePostBankAltarPending = false;
            altarTeleportPending = true;
            clearWalkerRouteIfActive("brutus-altar-prayer-restored");
            setState(KspMadCowState.TRAVELLING,
                    "Prayer points changed; immediately teleporting with Cowbell");
            travelWithCowbell(true);
            if (!travelRequired || cowbellTravelIssued) {
                altarTeleportPending = false;
            }
            return true;
        }

        if (!forcedLumbridgeRestore && !altarRestorePending && prayerPoints > 0) {
            altarInteractionIssued = false;
            altarPrayerPointsBeforeInteraction = -1;
            return false;
        }

        altarRestorePending = true;
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null || !ALTAR_AREA.contains(playerLocation)) {
            setState(KspMadCowState.RESTORING_PRAYER,
                    forcedLumbridgeRestore
                            ? "Walking to old Lumbridge altar after bank"
                            : "Walking to Lumbridge altar");
            if (!Rs2Player.isMoving()) {
                walkToOutsideFerox(ALTAR_LOCATION, 3);
            }
            return true;
        }

        // The long route is complete as soon as the player enters interaction range.
        if (Rs2Walker.getCurrentTarget() != null) {
            visibleDebug("Altar", "entered altar area at " + playerLocation
                    + "; cancelling walker target " + Rs2Walker.getCurrentTarget());
        }
        clearWalkerRouteIfActive("brutus-entered-altar-area");

        Rs2TileObjectModel altar = Microbot.getRs2TileObjectCache().query()
                .withId(ALTAR_ID)
                .nearestOnClientThread();
        if (altar == null) {
            setState(KspMadCowState.RESTORING_PRAYER, "Waiting for nearby altar ID 409");
            return true;
        }

        setState(KspMadCowState.RESTORING_PRAYER,
                altarInteractionIssued
                        ? "Waiting for Prayer points to change"
                        : "Directly praying at Lumbridge altar");
        if (!altarInteractionIssued) {
            int before = prayerPoints;
            boolean clicked = altar.click("Pray-at");
            log.debug("[Altar] Pray-at interaction issued={} prayerBefore={} altarId={} player={}",
                    clicked, before, ALTAR_ID, playerLocation);
            if (clicked) {
                altarPrayerPointsBeforeInteraction = before;
                altarInteractionIssued = true;
            }
        }
        return true;
    }

    private boolean manageCombatPrayers() {
        if (!config.usePrayer() || !config.useCombatPrayers()) {
            return disableManagedCombatPrayers();
        }
        if (getCurrentPrayerPointsSafe() <= 0) {
            activePrayerSummary = "None (0 points)";
            return false;
        }

        Rs2PrayerEnum[] desired = desiredPrayersFor(
                combatMode,
                getRealPrayerLevelSafe(),
                activeDemonicProtectionPrayer()
        );
        List<Rs2PrayerEnum> desiredList = Arrays.asList(desired);

        // Change one prayer per cycle so the state is always observable and deterministic.
        for (Rs2PrayerEnum prayer : MANAGED_COMBAT_PRAYERS) {
            if (Rs2Prayer.isPrayerActive(prayer) && !desiredList.contains(prayer)) {
                setState(KspMadCowState.MANAGING_PRAYERS, "Disabling " + prayer.getName());
                Rs2Prayer.toggle(prayer, false);
                refreshActivePrayerSummary();
                return true;
            }
        }
        for (Rs2PrayerEnum prayer : desired) {
            if (!Rs2Prayer.isPrayerActive(prayer)) {
                setState(KspMadCowState.MANAGING_PRAYERS, "Activating " + prayer.getName());
                Rs2Prayer.toggle(prayer, true);
                refreshActivePrayerSummary();
                return true;
            }
        }

        refreshActivePrayerSummary();
        return false;
    }

    private Rs2PrayerEnum[] desiredPrayersFor(
            CombatMode mode,
            int prayerLevel,
            Rs2PrayerEnum demonicProtectionOverride
    ) {
        List<Rs2PrayerEnum> desired = new ArrayList<>();

        // Normal Brutus is melee-only. Demonic Brutus adds phase-two Magic/Melee/
        // Ranged projectiles; their launch graphics temporarily override the melee
        // protection prayer while the offensive prayer remains active.
        Rs2PrayerEnum protection = demonicProtectionOverride != null
                ? demonicProtectionOverride
                : Rs2PrayerEnum.PROTECT_MELEE;
        if (prayerLevel >= protection.getLevel()) {
            desired.add(protection);
        }

        switch (mode) {
            case RANGED:
                if (prayerLevel >= Rs2PrayerEnum.EAGLE_EYE.getLevel()) {
                    desired.add(Rs2PrayerEnum.EAGLE_EYE);
                } else if (prayerLevel >= Rs2PrayerEnum.HAWK_EYE.getLevel()) {
                    desired.add(Rs2PrayerEnum.HAWK_EYE);
                } else if (prayerLevel >= Rs2PrayerEnum.SHARP_EYE.getLevel()) {
                    desired.add(Rs2PrayerEnum.SHARP_EYE);
                }
                break;

            case MAGIC:
                if (prayerLevel >= Rs2PrayerEnum.MYSTIC_MIGHT.getLevel()) {
                    desired.add(Rs2PrayerEnum.MYSTIC_MIGHT);
                } else if (prayerLevel >= Rs2PrayerEnum.MYSTIC_LORE.getLevel()) {
                    desired.add(Rs2PrayerEnum.MYSTIC_LORE);
                } else if (prayerLevel >= Rs2PrayerEnum.MYSTIC_WILL.getLevel()) {
                    desired.add(Rs2PrayerEnum.MYSTIC_WILL);
                }
                break;

            case MELEE:
            default:
                if (prayerLevel >= Rs2PrayerEnum.ULTIMATE_STRENGTH.getLevel()) {
                    desired.add(Rs2PrayerEnum.ULTIMATE_STRENGTH);
                } else if (prayerLevel >= Rs2PrayerEnum.SUPERHUMAN_STRENGTH.getLevel()) {
                    desired.add(Rs2PrayerEnum.SUPERHUMAN_STRENGTH);
                }

                if (prayerLevel >= Rs2PrayerEnum.INCREDIBLE_REFLEXES.getLevel()) {
                    desired.add(Rs2PrayerEnum.INCREDIBLE_REFLEXES);
                } else if (prayerLevel >= Rs2PrayerEnum.IMPROVED_REFLEXES.getLevel()) {
                    desired.add(Rs2PrayerEnum.IMPROVED_REFLEXES);
                } else if (prayerLevel >= Rs2PrayerEnum.CLARITY_THOUGHT.getLevel()) {
                    desired.add(Rs2PrayerEnum.CLARITY_THOUGHT);
                }
                break;
        }

        return desired.toArray(new Rs2PrayerEnum[0]);
    }

    private boolean disableManagedCombatPrayers() {
        boolean changed = false;
        for (Rs2PrayerEnum prayer : MANAGED_COMBAT_PRAYERS) {
            if (Rs2Prayer.isPrayerActive(prayer)) {
                Rs2Prayer.toggle(prayer, false);
                changed = true;
            }
        }
        refreshActivePrayerSummary();
        return changed;
    }

    private void refreshActivePrayerSummary() {
        List<String> active = new ArrayList<>();
        for (Rs2PrayerEnum prayer : MANAGED_COMBAT_PRAYERS) {
            if (Rs2Prayer.isPrayerActive(prayer)) {
                active.add(prayer.getName());
            }
        }
        activePrayerSummary = active.isEmpty() ? "None" : String.join(" + ", active);
    }

    private CombatMode detectCombatMode() {
        Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        Rs2ItemModel ammo = Rs2Equipment.get(EquipmentInventorySlot.AMMO);

        refreshTrackedRangedAmmo(ammo);

        CombatMode detected = detectCombatModeFromEquipmentNames(weapon, ammo);
        if (detected == null) {
            detected = detectCombatModeFromEquippedWeaponCategory();
        }
        if (detected == null) {
            detected = CombatMode.MELEE;
        }

        int weaponId = weapon == null ? -1 : weapon.getId();
        int ammoId = ammo == null ? -1 : ammo.getId();
        if (weaponId != lastCombatGearWeaponId
                || ammoId != lastCombatGearAmmoId
                || detected != lastLoggedCombatMode) {
            visibleDebug("CombatStyle", "gear detection weapon="
                    + describeEquipmentItem(weapon)
                    + " ammo=" + describeEquipmentItem(ammo)
                    + " weaponCategory=" + Microbot.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY)
                    + " detected=" + detected
                    + (detected == CombatMode.MAGIC && config != null
                    ? " configuredSpell=" + config.combatSpell()
                    : "")
                    + (trackedRangedAmmoId > 0
                    ? " trackedAmmo=" + trackedRangedAmmoName + "(" + trackedRangedAmmoId + ")"
                    : ""));
            lastCombatGearWeaponId = weaponId;
            lastCombatGearAmmoId = ammoId;
            lastLoggedCombatMode = detected;
        }

        return detected;
    }

    private CombatMode detectCombatModeFromEquippedWeaponCategory() {
        try {
            int weaponCategory = Microbot.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
            EnumComposition weaponStyles = Microbot.getEnum(EnumID.WEAPON_STYLES);
            if (weaponStyles == null) {
                return null;
            }

            int styleEnumId = weaponStyles.getIntValue(weaponCategory);
            EnumComposition styleEnum = Microbot.getEnum(styleEnumId);
            if (styleEnum == null || styleEnum.getIntVals() == null) {
                return null;
            }

            boolean ranged = false;
            boolean magic = false;
            for (int styleStructId : styleEnum.getIntVals()) {
                StructComposition styleStruct = Microbot.getStructComposition(styleStructId);
                if (styleStruct == null) {
                    continue;
                }

                String styleName = styleStruct.getStringValue(ParamID.ATTACK_STYLE_NAME);
                if (styleName == null) {
                    continue;
                }

                String normalized = styleName.toLowerCase(Locale.ROOT);
                if (normalized.contains("rapid")
                        || normalized.contains("longrange")
                        || normalized.contains("long range")
                        || normalized.contains("ranged")) {
                    ranged = true;
                }
                if (normalized.contains("spell")
                        || normalized.contains("cast")
                        || normalized.contains("magic")) {
                    magic = true;
                }
            }

            // Staves expose a Spell/Autocast combat option even if the currently
            // selected style happens to be Bash/Pound/Focus. That makes the
            // equipped weapon category a better mode signal than the selected
            // attack-style button.
            if (magic) {
                return CombatMode.MAGIC;
            }
            if (ranged) {
                return CombatMode.RANGED;
            }
            return CombatMode.MELEE;
        } catch (Exception ex) {
            log.debug("Unable to detect combat mode from equipped weapon category", ex);
            return null;
        }
    }

    private CombatMode detectCombatModeFromEquipmentNames(
            Rs2ItemModel weapon, Rs2ItemModel ammo) {
        String weaponName = weapon == null || weapon.getName() == null
                ? ""
                : weapon.getName().toLowerCase(Locale.ROOT);

        if (weaponName.contains("bow")
                || weaponName.contains("crossbow")
                || weaponName.contains("ballista")
                || weaponName.contains("blowpipe")
                || weaponName.contains("chinchompa")
                || weaponName.contains("javelin")
                || weaponName.contains("throwing knife")
                || weaponName.contains("thrownaxe")) {
            return CombatMode.RANGED;
        }

        if (weaponName.contains("staff")
                || weaponName.contains("wand")
                || weaponName.contains("sceptre")
                || weaponName.contains("scepter")
                || weaponName.contains("trident")
                || weaponName.contains("sanguinesti")
                || weaponName.contains("tumeken")) {
            return CombatMode.MAGIC;
        }

        if (weaponName.isEmpty() && isRangedAmmo(ammo)) {
            return CombatMode.RANGED;
        }

        return weapon == null ? CombatMode.MELEE : null;
    }

    private void refreshTrackedRangedAmmo(Rs2ItemModel ammo) {
        if (!isRangedAmmo(ammo)) {
            return;
        }

        if (trackedRangedAmmoId != ammo.getId()) {
            trackedRangedAmmoId = ammo.getId();
            trackedRangedAmmoName = ammo.getName() == null ? "ammo" : ammo.getName();
            visibleDebug("Ranged", "tracking equipped ammunition "
                    + trackedRangedAmmoName + " id=" + trackedRangedAmmoId
                    + " quantity=" + ammo.getQuantity());
        } else if (ammo.getName() != null) {
            trackedRangedAmmoName = ammo.getName();
        }
    }

    private boolean isRangedAmmo(Rs2ItemModel item) {
        if (item == null || item.getName() == null) {
            return false;
        }
        String name = item.getName().toLowerCase(Locale.ROOT);
        return name.contains("arrow") || name.contains("bolt");
    }

    private String describeEquipmentItem(Rs2ItemModel item) {
        if (item == null) {
            return "none";
        }
        return (item.getName() == null ? "item" : item.getName())
                + "(" + item.getId() + ")";
    }

    private boolean maintainCombatEquipmentAndStyle(boolean bossAlive) {
        combatMode = detectCombatMode();

        if (combatMode == CombatMode.RANGED) {
            trainingSkill = Skill.RANGED;
            return equipTrackedRangedAmmoFromInventory();
        }

        if (combatMode != CombatMode.MAGIC) {
            return false;
        }

        trainingSkill = Skill.MAGIC;
        KspMadCowSpell selection = config.combatSpell();
        if (selection == null) {
            setState(KspMadCowState.ERROR, "No Magic spell configured");
            return true;
        }

        int realMagic = readLevels(Skill.MAGIC)[1];
        int requiredMagic = selection.getCombatSpell().getRequiredLevel();
        if (realMagic < requiredMagic) {
            setState(KspMadCowState.ERROR,
                    "Magic level " + realMagic + " is too low for " + selection
                            + " (requires " + requiredMagic + ")");
            visibleDebug("Magic", "autocast blocked: level=" + realMagic
                    + " required=" + requiredMagic
                    + " spell=" + selection);
            return true;
        }

        int staffId = selection.getStaffItemId();
        if (!Rs2Equipment.isWearing(staffId)) {
            if (!Rs2Inventory.hasItem(staffId)) {
                setState(KspMadCowState.ERROR,
                        "Configured elemental staff is missing for " + selection);
                visibleDebug("Magic", "required staff missing id=" + staffId
                        + " spell=" + selection);
                return true;
            }

            if (!playerReadyForAction()) {
                setState(KspMadCowState.FIGHTING,
                        "Waiting to equip elemental staff for " + selection);
                return true;
            }

            setState(KspMadCowState.FIGHTING,
                    "Equipping elemental staff for " + selection);
            boolean equipped = Rs2Inventory.interact(staffId, "Wield");
            visibleDebug("Magic", "staff equip issued=" + equipped
                    + " staffId=" + staffId
                    + " spell=" + selection);
            combatReacquirePending = true;
            return true;
        }

        if (!bossAlive) {
            return false;
        }

        if (Rs2Magic.getCurrentAutoCastSpell() == selection.getCombatSpell()) {
            return false;
        }

        if (!playerReadyForAction()) {
            setState(KspMadCowState.FIGHTING,
                    "Waiting to select autocast " + selection);
            return true;
        }

        setState(KspMadCowState.FIGHTING, "Selecting autocast " + selection);
        boolean selected = Rs2Combat.setAutoCastSpell(selection.getCombatSpell(), false);
        visibleDebug("Magic", "autocast selection issued=" + selected
                + " spell=" + selection
                + " current=" + Rs2Magic.getCurrentAutoCastSpell());
        combatReacquirePending = true;
        return true;
    }

    private boolean equipTrackedRangedAmmoFromInventory() {
        if (combatMode != CombatMode.RANGED
                || trackedRangedAmmoId <= 0
                || !Rs2Inventory.hasItem(trackedRangedAmmoId)) {
            return false;
        }

        if (!playerReadyForAction()) {
            setState(KspMadCowState.FIGHTING,
                    "Waiting to equip recovered " + trackedRangedAmmoName);
            return true;
        }

        int gameTick = currentGameTick();
        if (gameTick != Integer.MIN_VALUE && gameTick == lastRangedAmmoEquipTick) {
            return true;
        }
        lastRangedAmmoEquipTick = gameTick;

        int quantity = Rs2Inventory.itemQuantity(trackedRangedAmmoId);
        setState(KspMadCowState.FIGHTING,
                "Equipping recovered " + trackedRangedAmmoName);
        boolean issued = Rs2Inventory.interact(trackedRangedAmmoId, "Wield");
        if (!issued) {
            issued = Rs2Inventory.interact(trackedRangedAmmoId, "Equip");
        }
        visibleDebug("Ranged", "ammo equip issued=" + issued
                + " ammo=" + trackedRangedAmmoName
                + " id=" + trackedRangedAmmoId
                + " inventoryQuantity=" + quantity);
        if (issued) {
            combatReacquirePending = true;
        }
        return issued;
    }

    private boolean ringCowbellForFastRespawn(Rs2NpcModel brutus) {
        if (!speedRingPending) {
            return false;
        }

        if (config == null || !config.speed() || config.demonicBrutus()) {
            speedRingPending = false;
            speedRingPrioritySatisfied = true;
            speedRingAttempts = 0;
            speedRingLastAttemptAtMs = 0L;
            return false;
        }

        // ActorDeath fires at the start of the death sequence. The NPC remains in
        // the scene for the death animation, so bossAlive is already false even
        // though Brutus has not disappeared yet. Do not Ring until findBrutus()
        // can no longer resolve the normal Brutus NPC at all.
        if (brutus != null && brutus.getNpc() != null) {
            setState(KspMadCowState.WAITING_FOR_RESPAWN,
                    "Speed: waiting for Brutus to completely disappear before Ring");
            return true;
        }

        if (speedRingAttempts >= COWBELL_SPEED_RING_MAX_ATTEMPTS) {
            visibleDebug("Speed", "Cowbell Ring was not accepted after "
                    + speedRingAttempts + " attempts; waiting for normal respawn");
            speedRingPending = false;
            speedRingPrioritySatisfied = true;
            return false;
        }

        long now = System.currentTimeMillis();
        if (speedRingLastAttemptAtMs > 0L
                && now - speedRingLastAttemptAtMs < COWBELL_SPEED_RING_RETRY_COOLDOWN_MS) {
            setState(KspMadCowState.WAITING_FOR_RESPAWN,
                    "Speed: waiting for 4-second Cowbell Ring retry cooldown");
            return true;
        }

        if (!playerReadyForAction()) {
            setState(KspMadCowState.WAITING_FOR_RESPAWN,
                    "Speed: waiting to Ring Cowbell");
            return true;
        }

        int cowbellId = Rs2Inventory.hasItem(COWBELL_CHARGED_ID)
                ? COWBELL_CHARGED_ID
                : Rs2Inventory.hasItem(COWBELL_EMPTY_ID)
                ? COWBELL_EMPTY_ID
                : Rs2Equipment.isWearing(COWBELL_CHARGED_ID)
                ? COWBELL_CHARGED_ID
                : Rs2Equipment.isWearing(COWBELL_EMPTY_ID)
                ? COWBELL_EMPTY_ID
                : -1;

        if (cowbellId == -1) {
            visibleDebug("Speed", "Speed is enabled but no Cowbell is available to Ring");
            speedRingPending = false;
            speedRingPrioritySatisfied = true;
            return false;
        }

        speedRingAttempts++;
        speedRingLastAttemptAtMs = now;
        setState(KspMadCowState.WAITING_FOR_RESPAWN,
                "Speed: ringing Cowbell for faster Brutus respawn");

        boolean issued = false;
        if (Rs2Inventory.hasItem(cowbellId)) {
            issued = Rs2Inventory.interact(cowbellId, COWBELL_SPEED_ACTION);
        }
        if (!issued && Rs2Equipment.isWearing(cowbellId)) {
            issued = Rs2Equipment.interact(cowbellId, COWBELL_SPEED_ACTION);
        }

        visibleDebug("Speed", "Cowbell Ring attempt=" + speedRingAttempts
                + " issued=" + issued
                + " cowbellId=" + cowbellId);

        if (issued) {
            speedRingPending = false;
            speedRingPrioritySatisfied = true;
            speedRingAttempts = 0;
            speedRingLastAttemptAtMs = 0L;
            setState(KspMadCowState.WAITING_FOR_RESPAWN,
                    "Speed: Cowbell Ring used; waiting for fast respawn");
        }
        return true;
    }

    private boolean recoverTrackedRangedAmmo() {
        if (combatMode != CombatMode.RANGED || trackedRangedAmmoId <= 0) {
            return false;
        }

        if (equipTrackedRangedAmmoFromInventory()) {
            return true;
        }

        if (!playerReadyForAction()) {
            return false;
        }

        Rs2TileItemModel ammo = Microbot.getRs2TileItemCache().query()
                .where(item -> item.isLootAble() && item.getId() == trackedRangedAmmoId)
                .within(config.lootRadius())
                .nearestOnClientThread();
        if (ammo == null) {
            return false;
        }

        boolean fits = !Rs2Inventory.isFull()
                || (ammo.isStackable() && Rs2Inventory.hasItem(trackedRangedAmmoId));
        if (!fits) {
            visibleDebug("Ranged", "matching ammo on ground but inventory is full ammo="
                    + trackedRangedAmmoName + " id=" + trackedRangedAmmoId);
            return false;
        }

        setState(KspMadCowState.LOOTING,
                "Recovering " + trackedRangedAmmoName);
        boolean pickedUp = ammo.pickup();
        visibleDebug("Ranged", "ammo pickup issued=" + pickedUp
                + " ammo=" + trackedRangedAmmoName
                + " id=" + trackedRangedAmmoId);
        return pickedUp;
    }

    private boolean equipMooletaFromInventory() {
        combatMode = detectCombatMode();
        if (!config.equipMooleta()
                || combatMode != CombatMode.MELEE
                || Rs2Equipment.isWearing(MOOLETA_ID)
                || !Rs2Inventory.hasItem(MOOLETA_ID)) {
            return false;
        }
        if (!playerReadyForAction()) {
            setState(KspMadCowState.EQUIPPING_MOOLETA, "Waiting to equip Mooleta");
            return true;
        }
        setState(KspMadCowState.EQUIPPING_MOOLETA, "Equipping Mooleta");
        return Rs2Inventory.interact(MOOLETA_ID, "Wield");
    }

    private boolean balanceCombatStyle() {
        combatMode = detectCombatMode();
        if (combatMode == CombatMode.RANGED) {
            trainingSkill = Skill.RANGED;
            return false;
        }
        if (combatMode == CombatMode.MAGIC) {
            trainingSkill = Skill.MAGIC;
            return false;
        }
        if (!config.balanceCombatStats()) {
            return false;
        }

        Skill targetSkill = lowestMeleeSkill();
        trainingSkill = targetSkill;
        int targetStyleIndex = findStyleIndex(targetSkill);
        if (targetStyleIndex < 0 || targetStyleIndex >= MELEE_STYLE_WIDGETS.length) {
            return false;
        }

        int currentStyleIndex = Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE);
        if (currentStyleIndex == targetStyleIndex) {
            return false;
        }
        if (!playerReadyForAction()) {
            return false;
        }

        setMessage("Switching style to train " + targetSkill.getName());
        Rs2Tab.switchTo(InterfaceTab.COMBAT);
        return Rs2Combat.setAttackStyle(MELEE_STYLE_WIDGETS[targetStyleIndex]);
    }

    private Skill lowestMeleeSkill() {
        int[] levels = readLevels(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE);
        int attack = levels[1];
        int strength = levels[3];
        int defence = levels[5];
        if (strength < attack && strength <= defence) {
            return Skill.STRENGTH;
        }
        if (defence < attack && defence < strength) {
            return Skill.DEFENCE;
        }
        return Skill.ATTACK;
    }

    private int findStyleIndex(Skill targetSkill) {
        try {
            int weaponCategory = Microbot.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
            EnumComposition weaponStyles = Microbot.getEnum(EnumID.WEAPON_STYLES);
            if (weaponStyles == null) {
                return -1;
            }
            int styleEnumId = weaponStyles.getIntValue(weaponCategory);
            EnumComposition styleEnum = Microbot.getEnum(styleEnumId);
            if (styleEnum == null || styleEnum.getIntVals() == null) {
                return -1;
            }

            int controlledFallback = -1;
            int[] styleStructs = styleEnum.getIntVals();
            for (int index = 0; index < Math.min(styleStructs.length, MELEE_STYLE_WIDGETS.length); index++) {
                StructComposition styleStruct = Microbot.getStructComposition(styleStructs[index]);
                if (styleStruct == null) {
                    continue;
                }
                String styleName = styleStruct.getStringValue(ParamID.ATTACK_STYLE_NAME);
                if (styleName == null) {
                    continue;
                }
                String normalized = styleName.toLowerCase(Locale.ROOT);
                if (normalized.equals("controlled")) {
                    controlledFallback = index;
                }
                if (targetSkill == Skill.ATTACK && normalized.equals("accurate")) {
                    return index;
                }
                if (targetSkill == Skill.STRENGTH && normalized.equals("aggressive")) {
                    return index;
                }
                if (targetSkill == Skill.DEFENCE && normalized.equals("defensive")) {
                    return index;
                }
            }
            return controlledFallback;
        } catch (Exception ex) {
            log.debug("Unable to resolve attack style", ex);
            return -1;
        }
    }

    private synchronized boolean handleSpecialAttack(Rs2NpcModel brutus) {
        if (!config.dodgeSpecials()) {
            clearSpecialAvoidance();
            return false;
        }

        // Special detection is intentionally event-driven. OverheadTextChanged and
        // AnimationChanged already execute on RuneLite's client thread and queue the
        // dodge immediately. Never synchronously poll the client thread from this
        // scheduled script loop: runOnClientThreadOptional() can wait for the client
        // thread and stall the entire combat loop, which makes a later dodge arrive
        // far too late. The event handlers below remain the authoritative source for
        // Brutus special cues/animation geometry.

        SpecialSignal signal = consumePendingSpecial();
        if (signal.attack != SpecialAttack.NONE) {
            activateSpecial(signal, brutus);
        }

        if (activeSpecial == SpecialAttack.NONE) {
            return false;
        }

        if (specialDodgeCandidates.isEmpty()) {
            specialDodgeCandidates = activeSpecial == SpecialAttack.CHARGE
                    ? findChargeDodgeCandidates(brutus, null, -1)
                    : findStompDodgeCandidates(brutus, null, -1);
            specialCandidateIndex = 0;
            specialDodgeTarget = specialDodgeCandidates.isEmpty() ? null : specialDodgeCandidates.get(0);
        }

        // Treat arrival on any precomputed safe candidate as completion. A local
        // scene click can resolve onto another equivalent side/rear candidate even
        // when the originally selected tile is occupied for a client cycle.
        WorldPoint reachedCandidate = playerSpecialCandidate();
        if (reachedCandidate != null) {
            specialDodgeTarget = reachedCandidate;
            specialTargetReached = true;
            clearWalkerRouteIfActive("brutus-special-safe-tile");
            visibleDebug("Dodge", "safe tile reached special=" + activeSpecial
                    + " scenePlayer=" + getCombatScenePlayerLocation()
                    + " cachedPlayer=" + Rs2Player.getWorldLocation()
                    + " selected=" + reachedCandidate
                    + " candidates=" + specialDodgeCandidates);
        }

        if (specialTargetReached) {
            if (activeSpecial == SpecialAttack.CHARGE) {
                handleChargeSafeTileReattack();
            } else {
                handleStompSafeTileReattack();
            }
            return true;
        }

        String target = specialDodgeTarget == null
                ? "searching"
                : specialDodgeTarget.getX() + ", " + specialDodgeTarget.getY() + ", " + specialDodgeTarget.getPlane();
        setState(KspMadCowState.DODGING,
                (activeSpecial == SpecialAttack.CHARGE
                        ? "Dodging Charge behind Brutus @ "
                        : "Dodging Stomp immediately to side/rear @ ") + target);

        if (specialDodgeTarget == null) {
            return true;
        }

        // Issue one immediate local dodge click. After that, never click the same
        // destination simply because the player is momentarily not moving. A retry
        // is allowed only after the player has shown no tile progress for multiple
        // game ticks, and the retry rotates to another safe candidate when possible.
        int gameTick = currentGameTick();
        updateSpecialMovementProgress(gameTick);
        if (shouldIssueSpecialMovement(gameTick)) {
            lastSpecialMovementTick = gameTick;
            issueSpecialMovement();
        }
        return true;
    }

    private void handleChargeSafeTileReattack() {
        handleSpecialSafeTileReattack("Charge");
    }

    private void handleStompSafeTileReattack() {
        handleSpecialSafeTileReattack("Stomp");
    }

    private void handleSpecialSafeTileReattack(String specialName) {
        setState(KspMadCowState.FIGHTING,
                specialName + " safe tile reached; re-attacking Brutus by NPC ID");

        if (isPlayerTargetingBrutusId()) {
            visibleDebug("Combat", specialName + " re-attack confirmed; target is Brutus");
            finishSpecialAvoidance(false);
            return;
        }

        combatReacquirePending = true;
        // The requested recovery rule is animation-based: once the player is not
        // animating, invoke Brutus again. Do not additionally block on movement.
        if (isPlayerCurrentlyAnimating()) {
            logCombatBlockedOnce(specialName + " re-attack", "player animating");
            return;
        }

        int gameTick = currentGameTick();
        if (gameTick != Integer.MIN_VALUE && gameTick == lastSpecialReattackTick) {
            return;
        }
        lastSpecialReattackTick = gameTick;

        Rs2NpcModel idBrutus = findBrutus();
        if (idBrutus == null && isBrutusIdNpc(lastKnownBrutusNpc)) {
            idBrutus = new Rs2NpcModel(lastKnownBrutusNpc);
        }
        if (idBrutus == null || idBrutus.getNpc() == null
                || !isBrutusIdNpc(idBrutus.getNpc())) {
            visibleDebug("Combat", specialName + " re-attack skipped; configured Brutus target not found"
                    + (config != null && config.demonicBrutus()
                    ? " id=" + DEMONIC_BRUTUS_ID
                    : " ids=" + BRUTUS_ID + "/" + BRUTUS_ALT_ID));
            return;
        }

        boolean issued = invokeBrutusAttackById(idBrutus, specialName + " re-attack");
        visibleDebug("Combat", specialName + " re-attack issued=" + issued
                + " tick=" + gameTick
                + " npcId=" + idBrutus.getId()
                + " npcIndex=" + idBrutus.getIndex()
                + " player=" + Rs2Player.getWorldLocation()
                + " safeTile=" + specialDodgeTarget);
        if (issued) {
            // Release the special state immediately after the Attack menu action.
            // Normal combat owns confirmation/retry from here, while the shared
            // game-tick gate prevents an immediate duplicate click.
            lastCombatAttackTick = gameTick;
            finishSpecialAvoidance(true);
        }
    }

    private void pollBrutusAnimation(Rs2NpcModel brutus) {
        BrutusAnimationSnapshot animation = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPC npc = brutus.getNpc();
            if (npc == null) {
                return BrutusAnimationSnapshot.NONE;
            }
            int orientation = npc.getOrientation();
            if (orientation < 0) {
                orientation = npc.getCurrentOrientation();
            }
            return new BrutusAnimationSnapshot(
                    npc.getAnimation(),
                    npc.getAnimationFrame(),
                    npc.getWorldArea(),
                    orientation
            );
        }).orElse(BrutusAnimationSnapshot.NONE);

        currentBrutusAnimation = animation.animation;
        currentBrutusAnimationFrame = animation.frame;
        boolean newCycle = currentBrutusAnimation != lastObservedBrutusAnimation
                || (currentBrutusAnimation == lastObservedBrutusAnimation
                && currentBrutusAnimationFrame >= 0
                && lastObservedBrutusFrame >= 0
                && currentBrutusAnimationFrame < lastObservedBrutusFrame);

        if (newCycle) {
            queueSpecialFromAnimation(
                    currentBrutusAnimation,
                    animation.area,
                    animation.orientation,
                    false
            );
        }
        lastObservedBrutusAnimation = currentBrutusAnimation;
        lastObservedBrutusFrame = currentBrutusAnimationFrame;

        if (chargeSequenceLatched
                && chargeSequenceAnimationObserved
                && !CHARGE_ANIMATIONS.contains(currentBrutusAnimation)
                && activeSpecial != SpecialAttack.CHARGE) {
            chargeSequenceLatched = false;
            chargeSequenceAnimationObserved = false;
        }

        if (stompSequenceLatched
                && !STOMP_ANIMATIONS.contains(currentBrutusAnimation)
                && activeSpecial != SpecialAttack.STOMP
                && !"stomp".equals(lastObservedOverheadCue)) {
            stompSequenceLatched = false;
        }
    }

    private void pollBrutusOverheadCue(Rs2NpcModel brutus) {
        String overheadText = Microbot.getClientThread().runOnClientThreadOptional(
                () -> brutus.getNpc() == null ? null : brutus.getNpc().getOverheadText()
        ).orElse(null);
        String cue = normalizeSpecialCue(overheadText);
        if (!cue.equals(lastObservedOverheadCue)) {
            String previousCue = lastObservedOverheadCue;
            lastObservedOverheadCue = cue;
            visibleDebug("Special", "overhead changed previous='" + previousCue
                    + "' current='" + cue
                    + "' raw='" + overheadText
                    + "' animation=" + currentBrutusAnimation
                    + " active=" + activeSpecial
                    + " pending=" + pendingSpecial);
            if ("charge".equals(cue)) {
                queueSpecialFromCue(
                        SpecialAttack.CHARGE,
                        getNpcArea(brutus),
                        getNpcOrientation(brutus)
                );
            } else if ("stomp".equals(cue)) {
                queueSpecialFromCue(
                        SpecialAttack.STOMP,
                        getNpcArea(brutus),
                        getNpcOrientation(brutus)
                );
            }
        }
    }

    /**
     * Called from RuneLite's GameTick event on the client thread. Dodge progress and
     * retry logic consume these snapshots instead of synchronously waiting on the
     * client thread from the scheduled script executor.
     */
    public void onGameTick() {
        if (!running) {
            return;
        }
        refreshClientCombatSnapshot();
    }

    private void refreshClientCombatSnapshot() {
        if (Microbot.getClient() == null || !Microbot.getClient().isClientThread()) {
            return;
        }
        cachedGameTick = Microbot.getClient().getTickCount();
        Player player = Microbot.getClient().getLocalPlayer();
        cachedCombatScenePlayerLocation = player == null ? null : player.getWorldLocation();
    }

    private void cacheBrutusGeometry(Actor actor) {
        refreshClientCombatSnapshot();
        if (actor == null) {
            return;
        }
        lastKnownBrutusArea = actor.getWorldArea();
        int orientation = actor.getOrientation();
        if (orientation < 0) {
            orientation = actor.getCurrentOrientation();
        }
        lastKnownBrutusOrientation = orientation;
    }

    public void onBrutusOverheadText(Actor actor, String overheadText) {
        if (!running || !isBrutusActor(actor)) {
            return;
        }
        if (isBrutusIdNpc((NPC) actor)) {
            lastKnownBrutusNpc = (NPC) actor;
        }
        cacheBrutusGeometry(actor);
        String cue = normalizeSpecialCue(overheadText);
        lastObservedOverheadCue = cue;
        int orientation = actor.getOrientation();
        if (orientation < 0) {
            orientation = actor.getCurrentOrientation();
        }
        visibleDebug("Special", "overhead event cue='" + cue
                + "' raw='" + overheadText
                + "' animation=" + actor.getAnimation()
                + " frame=" + actor.getAnimationFrame()
                + " area=" + actor.getWorldArea()
                + " orientation=" + orientation
                + " active=" + activeSpecial
                + " pending=" + pendingSpecial);
        if ("charge".equals(cue)) {
            queueSpecialFromCue(SpecialAttack.CHARGE, actor.getWorldArea(), orientation);
            issueImmediateSpecialDodgeFromEvent();
        } else if ("stomp".equals(cue)) {
            queueSpecialFromCue(SpecialAttack.STOMP, actor.getWorldArea(), orientation);
            issueImmediateSpecialDodgeFromEvent();
        }
    }

    public void onBrutusAnimationChanged(Actor actor) {
        if (!running || !isBrutusActor(actor)) {
            return;
        }
        if (isBrutusIdNpc((NPC) actor)) {
            lastKnownBrutusNpc = (NPC) actor;
        }
        cacheBrutusGeometry(actor);
        int animation = actor.getAnimation();
        currentBrutusAnimation = animation;
        currentBrutusAnimationFrame = actor.getAnimationFrame();
        int orientation = actor.getOrientation();
        if (orientation < 0) {
            orientation = actor.getCurrentOrientation();
        }
        visibleDebug("Animation", "event id=" + animation
                + " frame=" + currentBrutusAnimationFrame
                + " area=" + actor.getWorldArea()
                + " orientation=" + orientation
                + " active=" + activeSpecial
                + " pending=" + pendingSpecial
                + " cue=" + cuePrimedAttack);
        queueSpecialFromAnimation(
                animation,
                actor.getWorldArea(),
                orientation,
                config != null
                        && config.demonicBrutus()
                        && DEMONIC_SPECIAL_STEP_ANIMATIONS.contains(animation)
        );

        // Event callbacks run as soon as RuneLite exposes the animation. For
        // Demonic/unprimed steps this removes the old wait for the next 100 ms
        // script poll; for normal Brutus it is a no-op if the overhead cue already
        // issued the movement.
        issueImmediateSpecialDodgeFromEvent();
    }

    /**
     * Issue the first dodge click directly from RuneLite's overhead/animation event
     * instead of waiting for the scheduled script loop. The regular special handler
     * remains authoritative for progress, retry and re-attack.
     */
    private synchronized void issueImmediateSpecialDodgeFromEvent() {
        if (!running
                || config == null
                || !config.dodgeSpecials()
                || !instanceConfirmed) {
            return;
        }

        SpecialSignal signal = consumePendingSpecial();
        if (signal.attack != SpecialAttack.NONE) {
            activateSpecial(signal, null);
        }

        if (activeSpecial == SpecialAttack.NONE || specialTargetReached) {
            return;
        }

        if (specialDodgeCandidates.isEmpty()) {
            specialDodgeCandidates = activeSpecial == SpecialAttack.CHARGE
                    ? findChargeDodgeCandidates(null, null, -1)
                    : findStompDodgeCandidates(null, null, -1);
            specialCandidateIndex = 0;
            specialDodgeTarget = specialDodgeCandidates.isEmpty()
                    ? null
                    : specialDodgeCandidates.get(0);
        }

        if (specialDodgeTarget == null || specialMovementAttempted) {
            return;
        }

        int gameTick = currentGameTick();
        lastSpecialMovementTick = gameTick;
        visibleDebug("Dodge", "event-time immediate " + activeSpecial
                + " target=" + specialDodgeTarget
                + " player=" + getCombatScenePlayerLocation());
        issueSpecialMovement();
    }

    /**
     * Brutus's overhead cues are the authoritative special labels:
     * growls = Charge and snorts = Stomp/Slam. A Stomp must begin immediately,
     * so either cue queues its dodge immediately with the current live
     * footprint/orientation. Matching animations may refine geometry, but can
     * never select or replace the special type.
     */
    private synchronized void queueSpecialFromCue(
            SpecialAttack attack,
            WorldArea area,
            int orientation
    ) {
        if (attack == SpecialAttack.NONE) {
            return;
        }

        int gameTick = currentGameTick();
        visibleDebug("Special", "cue=" + attack
                + " tick=" + gameTick
                + " animation=" + currentBrutusAnimation
                + " area=" + area
                + " orientation=" + orientation
                + " active=" + activeSpecial
                + " pending=" + pendingSpecial);

        // Overhead text is the authoritative special label. Never let an
        // animation classification turn Charge into Stomp or Stomp into Charge.
        if (activeSpecial != SpecialAttack.NONE) {
            if (activeSpecial == attack) {
                int activeAge = gameTick == Integer.MIN_VALUE
                        || activeSpecialStartedTick == Integer.MIN_VALUE
                        ? 0
                        : gameTick - activeSpecialStartedTick;
                if (activeAge <= 1) {
                    visibleDebug("Special", "same-tick duplicate cue=" + attack
                            + " ignored activeAge=" + activeAge);
                    return;
                }

                // A repeated overhead cue after the original cue disappeared is a
                // new special cycle. Restart stale movement/re-attack state instead
                // of remaining permanently latched to a failed dodge.
                visibleDebug("Special", "new " + attack
                        + " cue restarts stale active special ageTicks=" + activeAge
                        + " oldTarget=" + specialDodgeTarget
                        + " scenePlayer=" + getCombatScenePlayerLocation());
                clearSpecialAvoidance();
                combatReacquirePending = true;
            } else {

            // Brutus cannot begin two different specials simultaneously. A new,
            // authoritative opposite overhead cue means the old dodge state is
            // stale. Replace it immediately instead of continuing to treat the
            // new Charge as Stomp (or vice versa).
            visibleDebug("Special", "authoritative cue=" + attack
                    + " supersedes stale active=" + activeSpecial
                    + " scenePlayer=" + getCombatScenePlayerLocation()
                    + " cachedPlayer=" + Rs2Player.getWorldLocation()
                    + " oldTarget=" + specialDodgeTarget);
            clearSpecialAvoidance();
            combatReacquirePending = true;
            }
        }

        if (pendingSpecial != SpecialAttack.NONE && pendingSpecial != attack) {
            visibleDebug("Special", "replacing stale pending=" + pendingSpecial
                    + " with authoritative cue=" + attack);
        }

        pendingSpecial = attack;
        pendingSpecialFromAnimation = false;
        pendingSpecialAnimation = -1;
        pendingSpecialArea = area;
        pendingSpecialOrientation = orientation;
        cuePrimedAttack = attack;
        cuePrimedTick = gameTick;

        if (attack == SpecialAttack.CHARGE) {
            chargeSequenceLatched = true;
            chargeSequenceAnimationObserved = false;
            stompSequenceLatched = false;
        } else {
            stompSequenceLatched = true;
            chargeSequenceLatched = false;
            chargeSequenceAnimationObserved = false;
        }

        visibleDebug("Special", attack + " queued immediately from overhead cue");
    }

    private synchronized void queueSpecialFromAnimation(
            int animation,
            WorldArea area,
            int orientation,
            boolean allowUnprimedDemonicStep
    ) {
        SpecialAttack classified = specialForAnimation(animation);
        if (classified == SpecialAttack.NONE) {
            return;
        }

        int gameTick = currentGameTick();
        visibleDebug("Animation", "id=" + animation
                + " classified=" + classified
                + " frame=" + currentBrutusAnimationFrame
                + " tick=" + gameTick
                + " active=" + activeSpecial
                + " pending=" + pendingSpecial
                + " cue=" + cuePrimedAttack);

        // Normal Brutus uses the growl/snort overhead as the authoritative start
        // signal. Demonic Brutus adds three rapidly sequenced ghost copies; their
        // primary Charge/Stomp animations are therefore valid hard-mode dodge-step
        // signals even without another overhead cue.
        if (activeSpecial != SpecialAttack.NONE) {
            if (activeSpecial != classified) {
                visibleDebug("Animation", "ignored conflicting classification=" + classified
                        + " while active=" + activeSpecial);
                return;
            }
            specialAnimationObserved = true;
            if (classified == SpecialAttack.CHARGE) {
                chargeSequenceAnimationObserved = true;
            }

            if (allowUnprimedDemonicStep && area != null && orientation >= 0) {
                List<WorldPoint> refined = classified == SpecialAttack.CHARGE
                        ? findChargeDodgeCandidates(null, area, orientation)
                        : findStompDodgeCandidates(null, area, orientation);
                if (!refined.isEmpty()) {
                    specialDodgeCandidates = refined;
                    specialCandidateIndex = 0;
                    specialDodgeTarget = refined.get(0);
                    specialMovementTargetAttempts = 0;
                    specialMovementAttempted = false;
                    specialTargetReached = false;
                    specialMovementLastProgressTick = gameTick;
                    specialMovementLastPlayerLocation = getCombatScenePlayerLocation();
                    combatReacquirePending = true;
                    visibleDebug("Demonic", "queued follow-up " + classified
                            + " ghost/real step from animation=" + animation
                            + " candidates=" + refined);
                }
                return;
            }

            if (!specialMovementAttempted && !specialTargetReached && area != null && orientation >= 0) {
                List<WorldPoint> refined = classified == SpecialAttack.CHARGE
                        ? findChargeDodgeCandidates(null, area, orientation)
                        : findStompDodgeCandidates(null, area, orientation);
                if (!refined.isEmpty()) {
                    specialDodgeCandidates = refined;
                    specialCandidateIndex = 0;
                    specialDodgeTarget = refined.get(0);
                    specialMovementTargetAttempts = 0;
                    visibleDebug("Dodge", "refined " + classified + " geometry from matching animation: "
                            + refined);
                }
            }
            return;
        }

        if (pendingSpecial == SpecialAttack.NONE) {
            if (!allowUnprimedDemonicStep) {
                visibleDebug("Animation", "ignored unprimed animation=" + animation
                        + "; waiting for growl/snort cue");
                return;
            }

            pendingSpecial = classified;
            pendingSpecialFromAnimation = true;
            pendingSpecialAnimation = animation;
            pendingSpecialArea = area;
            pendingSpecialOrientation = orientation;
            specialAnimationObserved = true;
            if (classified == SpecialAttack.CHARGE) {
                chargeSequenceLatched = true;
                chargeSequenceAnimationObserved = true;
                stompSequenceLatched = false;
            } else {
                stompSequenceLatched = true;
                chargeSequenceLatched = false;
                chargeSequenceAnimationObserved = false;
            }
            visibleDebug("Demonic", "hard-mode animation started unprimed "
                    + classified + " step animation=" + animation
                    + " area=" + area
                    + " orientation=" + orientation);
            return;
        }
        if (pendingSpecial != classified) {
            visibleDebug("Animation", "ignored classification=" + classified
                    + " because authoritative pending cue=" + pendingSpecial);
            return;
        }

        pendingSpecialFromAnimation = true;
        pendingSpecialAnimation = animation;
        if (area != null) {
            pendingSpecialArea = area;
        }
        if (orientation >= 0) {
            pendingSpecialOrientation = orientation;
        }
        specialAnimationObserved = true;
        if (classified == SpecialAttack.CHARGE) {
            chargeSequenceAnimationObserved = true;
        }
        visibleDebug("Animation", "matching animation enriched pending=" + pendingSpecial
                + " area=" + pendingSpecialArea
                + " orientation=" + pendingSpecialOrientation);
    }

    private synchronized SpecialSignal consumePendingSpecial() {
        if (pendingSpecial == SpecialAttack.NONE) {
            return SpecialSignal.NONE;
        }
        SpecialSignal signal = new SpecialSignal(
                pendingSpecial,
                pendingSpecialFromAnimation,
                pendingSpecialAnimation,
                pendingSpecialArea,
                pendingSpecialOrientation
        );
        pendingSpecial = SpecialAttack.NONE;
        pendingSpecialFromAnimation = false;
        pendingSpecialAnimation = -1;
        pendingSpecialArea = null;
        pendingSpecialOrientation = -1;
        return signal;
    }

    private void activateSpecial(SpecialSignal signal, Rs2NpcModel brutus) {
        if (signal.attack == SpecialAttack.NONE) {
            return;
        }
        if (activeSpecial != SpecialAttack.NONE) {
            if (activeSpecial == signal.attack && signal.fromAnimation) {
                specialAnimationObserved = true;
            } else if (activeSpecial != signal.attack) {
                visibleDebug("Special", "activation conflict ignored active=" + activeSpecial
                        + " incoming=" + signal.attack
                        + " animation=" + signal.animation
                        + " area=" + signal.area
                        + " orientation=" + signal.orientation);
            }
            return;
        }

        activeSpecial = signal.attack;
        specialAnimationObserved = signal.fromAnimation;
        specialMovementAttempted = false;
        lastSpecialMovementTick = Integer.MIN_VALUE;
        specialMovementIssueCount = 0;
        specialMovementTargetAttempts = 0;
        specialMovementLastProgressTick = currentGameTick();
        activeSpecialStartedTick = currentGameTick();
        specialMovementLastPlayerLocation = getCombatScenePlayerLocation();
        specialMovementIssuedTargets.clear();
        specialTargetReached = false;
        lastSpecialReattackTick = Integer.MIN_VALUE;
        stompReattackIssued = false;
        stompReattackIssuedTick = Integer.MIN_VALUE;
        stompActivationTick = signal.attack == SpecialAttack.STOMP
                ? currentGameTick()
                : Integer.MIN_VALUE;
        combatReacquirePending = true;
        // Stomp must move from the overhead/animation signal itself. The three
        // RuneLite Stomp graphics appear at impact time and are therefore used only
        // as post-event validation, never as a movement prerequisite.
        specialDodgeCandidates = signal.attack == SpecialAttack.CHARGE
                ? findChargeDodgeCandidates(brutus, signal.area, signal.orientation)
                : findStompDodgeCandidates(brutus, signal.area, signal.orientation);
        specialCandidateIndex = 0;
        specialDodgeTarget = specialDodgeCandidates.isEmpty() ? null : specialDodgeCandidates.get(0);
        visibleDebug("Special", "activated=" + signal.attack
                + " animation=" + signal.animation
                + " orientation=" + signal.orientation
                + " area=" + signal.area
                + " scenePlayer=" + getCombatScenePlayerLocation()
                + " cachedPlayer=" + Rs2Player.getWorldLocation()
                + " candidates=" + specialDodgeCandidates
                + " selected=" + specialDodgeTarget);
    }

    private void updateSpecialMovementProgress(int gameTick) {
        WorldPoint player = getCombatScenePlayerLocation();
        if (player == null) {
            return;
        }
        if (specialMovementLastPlayerLocation == null
                || !player.equals(specialMovementLastPlayerLocation)) {
            WorldPoint previous = specialMovementLastPlayerLocation;
            specialMovementLastPlayerLocation = player;
            specialMovementLastProgressTick = gameTick;
            visibleDebug("Dodge", "movement progress special=" + activeSpecial
                    + " from=" + previous
                    + " to=" + player
                    + " target=" + specialDodgeTarget
                    + " tick=" + gameTick);
        }
    }

    private boolean shouldIssueSpecialMovement(int gameTick) {
        if (specialDodgeTarget == null) {
            return false;
        }
        if (gameTick != Integer.MIN_VALUE && gameTick == lastSpecialMovementTick) {
            return false;
        }
        if (!specialMovementAttempted) {
            return true;
        }
        // Movement progress is tracked from the cached client-thread player tile. If
        // the tile changed this game tick, updateSpecialMovementProgress() refreshed
        // specialMovementLastProgressTick and the stall test below suppresses a retry.
        // Do not call Rs2Player.isMoving() here: it performs a synchronous client-thread
        // read and can freeze the script for 10 seconds when the client is busy.
        if (gameTick == Integer.MIN_VALUE || lastSpecialMovementTick == Integer.MIN_VALUE) {
            return false;
        }

        boolean stalled = gameTick - lastSpecialMovementTick >= SPECIAL_MOVEMENT_RETRY_STALL_TICKS
                && (specialMovementLastProgressTick == Integer.MIN_VALUE
                || gameTick - specialMovementLastProgressTick >= SPECIAL_MOVEMENT_RETRY_STALL_TICKS);
        if (!stalled) {
            return false;
        }

        if (specialMovementTargetAttempts >= SPECIAL_MOVEMENT_ATTEMPTS_PER_TARGET) {
            return selectNextSpecialCandidate();
        }
        return true;
    }

    private boolean hasUnattemptedSpecialCandidate() {
        return specialDodgeCandidates.size() > 1
                && specialCandidateIndex + 1 < specialDodgeCandidates.size();
    }

    private boolean selectNextSpecialCandidate() {
        if (!hasUnattemptedSpecialCandidate()) {
            visibleDebug("Dodge", "no fallback candidate remains for " + activeSpecial
                    + " target=" + specialDodgeTarget);
            return false;
        }
        specialCandidateIndex++;
        specialDodgeTarget = specialDodgeCandidates.get(specialCandidateIndex);
        specialMovementTargetAttempts = 0;
        specialMovementAttempted = false;
        specialMovementLastProgressTick = currentGameTick();
        specialMovementLastPlayerLocation = getCombatScenePlayerLocation();
        visibleDebug("Dodge", "switching " + activeSpecial + " fallback target to "
                + specialDodgeTarget);
        return true;
    }

    private void issueSpecialMovement() {
        if (specialDodgeTarget == null) {
            visibleDebug("Dodge", "no selected target for " + activeSpecial);
            return;
        }

        // Brutus combat movement must never use the minimap or global web walker.
        // Clear a surviving route once, then invoke WALK directly on the scene canvas.
        clearWalkerRouteIfActive("brutus-special-dodge");
        WorldPoint playerBefore = getCombatScenePlayerLocation();
        boolean issued = invokeLocalSceneWalk(specialDodgeTarget);
        specialMovementAttempted = true;
        specialMovementTargetAttempts++;
        specialMovementIssueCount++;
        specialMovementLastPlayerLocation = playerBefore;
        specialMovementLastProgressTick = currentGameTick();

        visibleDebug("Dodge", "special=" + activeSpecial
                + " directCanvasWalk=" + issued
                + " attempt=" + specialMovementTargetAttempts
                + " total=" + specialMovementIssueCount
                + " player=" + playerBefore
                + " target=" + specialDodgeTarget
                + " candidates=" + specialDodgeCandidates);

        if (!issued) {
            // A canvas conversion/click rejection will not improve by repeatedly
            // clicking the same tile. Try the other valid rear/behind candidate
            // immediately, while still never using the minimap.
            selectNextSpecialCandidate();
        }
    }

    private boolean invokeLocalSceneWalk(WorldPoint target) {
        if (target == null || Microbot.getClient() == null) {
            return false;
        }

        // Event-time dodges already run on the client thread. Scheduled retry logic
        // must never block for up to 10 seconds waiting for it, so queue the retry
        // asynchronously and validate the target again at execution time.
        if (!Microbot.getClient().isClientThread()) {
            final WorldPoint queuedTarget = target;
            final SpecialAttack queuedSpecial = activeSpecial;
            Microbot.getClientThread().invokeLater(() -> {
                synchronized (KspMadCowScript.this) {
                    if (!running
                            || activeSpecial != queuedSpecial
                            || !Objects.equals(specialDodgeTarget, queuedTarget)) {
                        return;
                    }
                    boolean accepted = invokeLocalSceneWalkClientThread(queuedTarget);
                    if (!accepted && Objects.equals(specialDodgeTarget, queuedTarget)) {
                        selectNextSpecialCandidate();
                    }
                }
            });
            return true;
        }

        return invokeLocalSceneWalkClientThread(target);
    }

    private boolean invokeLocalSceneWalkClientThread(WorldPoint target) {
        if (target == null
                || Microbot.getClient() == null
                || !Microbot.getClient().isClientThread()
                || !instanceConfirmed) {
            return false;
        }

        SceneWalkProjection projection = projectCombatSceneTile(target);
        Point canvasPoint = projection.canvasPoint;

        // Final hard safety gate immediately before WALK. A tile can report as
        // individually walkable while sitting across the Brutus fence. Only submit
        // the click when collision BFS confirms the target is reachable from the
        // player's current tile in this same live instance.
        if (!isCombatSceneReachable(target)) {
            visibleDebug("Dodge", "reachable-instance guard rejected target=" + target
                    + " local=" + projection.localPoint
                    + " scenePlayer=" + getCombatScenePlayerLocation()
                    + "; fence/out-of-instance WALK suppressed");
            return false;
        }

        if (!projection.available()
                || canvasPoint.getX() < 0
                || canvasPoint.getY() < 0
                || canvasPoint.getX() >= Microbot.getClient().getCanvasWidth()
                || canvasPoint.getY() >= Microbot.getClient().getCanvasHeight()) {
            visibleDebug("Dodge", "scene-only walk rejected"
                    + " targetScene=" + target
                    + " local=" + projection.localPoint
                    + " canvas=" + canvasPoint
                    + " worldViewBase=" + projection.worldViewBase
                    + " scenePlayer=" + getCombatScenePlayerLocation()
                    + " cachedPlayer=" + Rs2Player.getWorldLocation()
                    + "; minimap fallback is disabled");
            return false;
        }

        try {
            NewMenuEntry entry = new NewMenuEntry()
                    .option("Walk here")
                    .target("")
                    .identifier(0)
                    .type(MenuAction.WALK)
                    .param0(canvasPoint.getX())
                    .param1(canvasPoint.getY())
                    .itemId(0);

            Microbot.doInvoke(entry, new Rectangle(
                    canvasPoint.getX(),
                    canvasPoint.getY(),
                    1,
                    1
            ));
            return true;
        } catch (Exception ex) {
            log.warn("[KSP Mad Cow][Dodge] Direct canvas WALK invoke failed for {}", target, ex);
            visibleDebug("Dodge", "direct canvas WALK exception="
                    + ex.getClass().getSimpleName()
                    + " targetScene=" + target
                    + " local=" + projection.localPoint
                    + " canvas=" + canvasPoint);
            return false;
        }
    }

    private SceneWalkProjection projectCombatSceneTile(WorldPoint target) {
        if (target == null || Microbot.getClient() == null) {
            return SceneWalkProjection.NONE;
        }

        Player player = Microbot.getClient().getLocalPlayer();
        NPC brutus = isBrutusIdNpc(lastKnownBrutusNpc) ? lastKnownBrutusNpc : null;
        var worldView = brutus != null && brutus.getWorldView() != null
                ? brutus.getWorldView()
                : player != null && player.getWorldView() != null
                ? player.getWorldView()
                : Microbot.getClient().getTopLevelWorldView();
        if (worldView == null) {
            return SceneWalkProjection.NONE;
        }

        // Dodge targets are generated from Brutus's live Actor WorldArea, so they
        // must resolve directly inside that exact live WorldView. Do not reinterpret
        // a failed live-scene coordinate as a template coordinate: that can project
        // a fence/out-of-instance tile onto an unrelated tile and submit a bad WALK.
        LocalPoint localPoint = LocalPoint.fromWorld(worldView, target);
        if (localPoint == null) {
            return new SceneWalkProjection(
                    null,
                    null,
                    new WorldPoint(worldView.getBaseX(), worldView.getBaseY(), worldView.getPlane())
            );
        }

        Point canvasPoint = Perspective.localToCanvas(
                Microbot.getClient(),
                localPoint,
                worldView.getPlane()
        );
        return new SceneWalkProjection(
                localPoint,
                canvasPoint,
                new WorldPoint(worldView.getBaseX(), worldView.getBaseY(), worldView.getPlane())
        );
    }

    private void finishSpecialAvoidance(boolean reacquirePending) {
        combatReacquirePending = reacquirePending;
        // A short dodge must not leave a stale destination in the global walker.
        clearWalkerRouteIfActive("brutus-special-finished");
        clearSpecialAvoidance();
    }

    private List<WorldPoint> findChargeDodgeCandidates(
            Rs2NpcModel brutus,
            WorldArea capturedArea,
            int capturedOrientation
    ) {
        WorldArea area = capturedArea != null ? capturedArea : getNpcArea(brutus);
        int orientation = capturedOrientation >= 0
                ? capturedOrientation
                : getNpcOrientation(brutus);

        if (area == null || orientation < 0) {
            return List.of();
        }

        int[] facing = orientationVector(orientation);
        int behindX = -facing[0];
        int behindY = -facing[1];
        if (behindX == 0 && behindY == 0) {
            return List.of();
        }

        int minX = area.getX();
        int maxX = area.getX() + area.getWidth() - 1;
        int minY = area.getY();
        int maxY = area.getY() + area.getHeight() - 1;

        List<WorldPoint> candidates = new ArrayList<>();

        if (behindX != 0 && behindY != 0) {
            // Diagonal facing: prefer the exact tile one step beyond the rear corner.
            // The two rear-edge neighbours remain one tile behind the footprint and
            // provide reachable fallbacks if the corner itself is blocked.
            int x = behindX < 0 ? minX - 1 : maxX + 1;
            int y = behindY < 0 ? minY - 1 : maxY + 1;
            int edgeX = behindX < 0 ? minX : maxX;
            int edgeY = behindY < 0 ? minY : maxY;
            candidates.add(new WorldPoint(x, y, area.getPlane()));
            candidates.add(new WorldPoint(x, edgeY, area.getPlane()));
            candidates.add(new WorldPoint(edgeX, y, area.getPlane()));
        } else if (behindX != 0) {
            // East/west facing: every candidate is exactly one tile beyond the rear edge.
            int x = behindX < 0 ? minX - 1 : maxX + 1;
            addRearEdgeCandidates(candidates, x, minY, maxY, true, area.getPlane());
        } else {
            // North/south facing: every candidate is exactly one tile beyond the rear edge.
            int y = behindY < 0 ? minY - 1 : maxY + 1;
            addRearEdgeCandidates(candidates, y, minX, maxX, false, area.getPlane());
        }

        List<WorldPoint> rearCandidates = preferWalkableCandidates(candidates, area);
        if (!rearCandidates.isEmpty()) {
            return rearCandidates;
        }

        // If Brutus has his rear edge against the fence, the geometrically "behind"
        // tiles can be walkable ground on the other side of the fence but are not
        // reachable from the arena. Fall back to the two lateral edges instead.
        // This keeps the dodge inside the player's current collision-connected
        // component without reintroducing a hard-coded combat rectangle.
        List<WorldPoint> sideCandidates = new ArrayList<>();
        int leftX = -facing[1];
        int leftY = facing[0];
        addOutsideAreaEdgeCandidates(sideCandidates, area, leftX, leftY);
        addOutsideAreaEdgeCandidates(sideCandidates, area, -leftX, -leftY);
        List<WorldPoint> reachableSides = preferWalkableCandidates(sideCandidates, area);
        WorldPoint player = getCombatScenePlayerLocation();
        if (player != null && reachableSides.size() > 1) {
            reachableSides.sort(java.util.Comparator.comparingInt(player::distanceTo2D));
        }
        if (!reachableSides.isEmpty()) {
            visibleDebug("Charge", "rear blocked by fence; using reachable side dodge candidates="
                    + reachableSides + " player=" + player + " area=" + area);
        }
        return reachableSides;
    }

    private void addOutsideAreaEdgeCandidates(
            List<WorldPoint> candidates,
            WorldArea area,
            int directionX,
            int directionY
    ) {
        if (area == null || (directionX == 0 && directionY == 0)) {
            return;
        }
        int minX = area.getX();
        int maxX = area.getX() + area.getWidth() - 1;
        int minY = area.getY();
        int maxY = area.getY() + area.getHeight() - 1;

        if (directionX != 0 && directionY != 0) {
            int x = directionX < 0 ? minX - 1 : maxX + 1;
            int y = directionY < 0 ? minY - 1 : maxY + 1;
            int edgeX = directionX < 0 ? minX : maxX;
            int edgeY = directionY < 0 ? minY : maxY;
            candidates.add(new WorldPoint(x, y, area.getPlane()));
            candidates.add(new WorldPoint(x, edgeY, area.getPlane()));
            candidates.add(new WorldPoint(edgeX, y, area.getPlane()));
        } else if (directionX != 0) {
            int x = directionX < 0 ? minX - 1 : maxX + 1;
            addRearEdgeCandidates(candidates, x, minY, maxY, true, area.getPlane());
        } else {
            int y = directionY < 0 ? minY - 1 : maxY + 1;
            addRearEdgeCandidates(candidates, y, minX, maxX, false, area.getPlane());
        }
    }

    private void addRearEdgeCandidates(
            List<WorldPoint> candidates,
            int fixedCoordinate,
            int minimum,
            int maximum,
            boolean fixedX,
            int plane
    ) {
        int center = minimum + (maximum - minimum) / 2;
        if (fixedX) {
            candidates.add(new WorldPoint(fixedCoordinate, center, plane));
            for (int value = minimum; value <= maximum; value++) {
                candidates.add(new WorldPoint(fixedCoordinate, value, plane));
            }
        } else {
            candidates.add(new WorldPoint(center, fixedCoordinate, plane));
            for (int value = minimum; value <= maximum; value++) {
                candidates.add(new WorldPoint(value, fixedCoordinate, plane));
            }
        }
    }

    private int[] orientationVector(int orientation) {
        int normalized = ((orientation % 2048) + 2048) % 2048;
        int direction = ((normalized + 128) / 256) & 7;
        switch (direction) {
            case 0: return new int[]{0, -1};  // South
            case 1: return new int[]{-1, -1}; // South-west
            case 2: return new int[]{-1, 0};  // West
            case 3: return new int[]{-1, 1};  // North-west
            case 4: return new int[]{0, 1};   // North
            case 5: return new int[]{1, 1};   // North-east
            case 6: return new int[]{1, 0};   // East
            case 7: return new int[]{1, -1};  // South-east
            default: return new int[]{0, 0};
        }
    }

    private List<WorldPoint> findStompDodgeCandidates(
            Rs2NpcModel brutus,
            WorldArea capturedArea,
            int capturedOrientation
    ) {
        WorldPoint player = getCombatScenePlayerLocation();
        WorldArea area = capturedArea != null ? capturedArea : getNpcArea(brutus);
        int orientation = capturedOrientation >= 0
                ? capturedOrientation
                : getNpcOrientation(brutus);
        if (player == null || area == null || orientation < 0) {
            visibleDebug("Stomp", "cannot calculate rear corners player=" + player
                    + " area=" + area + " orientation=" + orientation);
            return List.of();
        }

        int[] facing = orientationVector(orientation);
        if (facing[0] == 0 && facing[1] == 0) {
            visibleDebug("Stomp", "invalid orientation=" + orientation);
            return List.of();
        }

        WorldPoint backLeft = stompRearCorner(area, facing, true);
        WorldPoint backRight = stompRearCorner(area, facing, false);
        boolean leftWalkable = backLeft != null && isCombatSceneWalkable(backLeft);
        boolean rightWalkable = backRight != null && isCombatSceneWalkable(backRight);

        List<WorldPoint> candidates = new ArrayList<>();
        if (leftWalkable && rightWalkable) {
            if (player.distanceTo2D(backLeft) <= player.distanceTo2D(backRight)) {
                candidates.add(backLeft);
                candidates.add(backRight);
            } else {
                candidates.add(backRight);
                candidates.add(backLeft);
            }
        } else if (leftWalkable) {
            candidates.add(backLeft);
        } else if (rightWalkable) {
            candidates.add(backRight);
        } else {
            // Collision data can lag on the first animation cycle. Keep the two
            // geometrically valid rear corners for this initial pass; the reachable
            // lateral fallback below is used only if both rear corners are rejected.
            if (backLeft != null && backRight != null
                    && player.distanceTo2D(backRight) < player.distanceTo2D(backLeft)) {
                candidates.add(backRight);
                candidates.add(backLeft);
            } else {
                if (backLeft != null) candidates.add(backLeft);
                if (backRight != null) candidates.add(backRight);
            }
        }

        // Keep dodge targets collision-safe, but do not impose a hard rectangular
        // combat click boundary. The user requested that area restriction be removed.
        candidates = candidates.stream()
                .filter(this::isCombatSceneSafeCandidate)
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        // When Brutus backs onto a fence, both geometrically ideal rear corners
        // can sit outside the arena's reachable collision component. Previously
        // that left Stomp with zero candidates, so the event-time dodge simply did
        // nothing. In that exact case, move to either lateral edge instead. These
        // tiles are perpendicular to Brutus's facing (never directly in front),
        // remain outside his footprint, and still have to pass the same live
        // instance + collision-BFS reachability guard before they can be clicked.
        if (candidates.isEmpty()) {
            List<WorldPoint> sideCandidates = new ArrayList<>();
            int leftX = -facing[1];
            int leftY = facing[0];
            addOutsideAreaEdgeCandidates(sideCandidates, area, leftX, leftY);
            addOutsideAreaEdgeCandidates(sideCandidates, area, -leftX, -leftY);

            candidates = preferWalkableCandidates(sideCandidates, area);
            if (player != null && candidates.size() > 1) {
                candidates.sort(java.util.Comparator.comparingInt(player::distanceTo2D));
            }

            if (!candidates.isEmpty()) {
                visibleDebug("Stomp", "rear corners blocked by fence; using reachable lateral dodge candidates="
                        + candidates + " player=" + player + " area=" + area);
            }
        }

        // Impact graphics are damage-time observations only. They must never
        // remove, delay, or replace the animation/cue-time rear-corner choice.
        Set<WorldPoint> observedImpactTiles = currentStompImpactTiles();
        visibleDebug("Stomp", "orientation=" + orientation
                + " facing=(" + facing[0] + "," + facing[1] + ")"
                + " area=" + area
                + " player=" + player
                + " backLeft=" + backLeft + " walkable=" + leftWalkable
                + " backRight=" + backRight + " walkable=" + rightWalkable
                + " impacts=" + observedImpactTiles
                + " candidates=" + candidates);
        return candidates;
    }

    private WorldPoint stompRearCorner(WorldArea area, int[] facing, boolean leftSide) {
        int minX = area.getX();
        int maxX = area.getX() + area.getWidth() - 1;
        int minY = area.getY();
        int maxY = area.getY() + area.getHeight() - 1;
        int centerX = minX + (maxX - minX) / 2;
        int centerY = minY + (maxY - minY) / 2;

        int backX = -facing[0];
        int backY = -facing[1];
        int leftX = -facing[1];
        int leftY = facing[0];
        int sideX = leftSide ? leftX : -leftX;
        int sideY = leftSide ? leftY : -leftY;

        // Combine "back" with "left/right" relative to Brutus's facing. For
        // cardinal facing this produces the two tiles diagonally outside the rear
        // corners. For diagonal facing it produces the two tiles outside the rear
        // edges. Both are behind Brutus and never in the frontal Slam pattern.
        int relativeX = Integer.compare(backX + sideX, 0);
        int relativeY = Integer.compare(backY + sideY, 0);
        int xDistance = Math.max(1, area.getWidth() / 2 + 1);
        int yDistance = Math.max(1, area.getHeight() / 2 + 1);

        return new WorldPoint(
                centerX + relativeX * xDistance,
                centerY + relativeY * yDistance,
                area.getPlane()
        );
    }

    private List<WorldPoint> preferWalkableCandidates(
            List<WorldPoint> candidates,
            WorldArea excludedArea
    ) {
        List<WorldPoint> walkable = distinctCandidates(candidates, excludedArea, true);
        // Never fall back to a blocked/unreachable candidate. A missed dodge is
        // preferable to clicking a tile the player cannot reach.
        return walkable;
    }

    private List<WorldPoint> distinctCandidates(List<WorldPoint> candidates, WorldArea excludedArea, boolean requireWalkable) {
        List<WorldPoint> result = new ArrayList<>();
        Set<WorldPoint> seen = new HashSet<>();
        for (WorldPoint candidate : candidates) {
            if (candidate == null || !seen.add(candidate)) {
                continue;
            }
            if (excludedArea != null && excludedArea.contains(candidate)) {
                continue;
            }
            if (requireWalkable && !isCombatSceneSafeCandidate(candidate)) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    private WorldArea getNpcArea(Rs2NpcModel brutus) {
        if (Microbot.getClient() != null && Microbot.getClient().isClientThread()) {
            NPC npc = brutus == null ? null : brutus.getNpc();
            if (npc != null) {
                lastKnownBrutusArea = npc.getWorldArea();
                return lastKnownBrutusArea;
            }
        }
        return lastKnownBrutusArea;
    }

    private int getNpcOrientation(Rs2NpcModel brutus) {
        if (Microbot.getClient() != null && Microbot.getClient().isClientThread()) {
            NPC npc = brutus == null ? null : brutus.getNpc();
            if (npc != null) {
                int orientation = npc.getOrientation();
                lastKnownBrutusOrientation = orientation >= 0
                        ? orientation
                        : npc.getCurrentOrientation();
                return lastKnownBrutusOrientation;
            }
        }
        return lastKnownBrutusOrientation;
    }

    private boolean isPlayerOnSpecialTarget() {
        return playerSpecialCandidate() != null;
    }

    private WorldPoint playerSpecialCandidate() {
        WorldPoint player = getCombatScenePlayerLocation();
        if (player == null) {
            return null;
        }
        if (specialDodgeTarget != null && player.equals(specialDodgeTarget)) {
            return specialDodgeTarget;
        }
        for (WorldPoint candidate : specialDodgeCandidates) {
            if (player.equals(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private WorldPoint getCombatScenePlayerLocation() {
        if (Microbot.getClient() != null && Microbot.getClient().isClientThread()) {
            refreshClientCombatSnapshot();
        }
        return cachedCombatScenePlayerLocation;
    }

    private boolean isCombatSceneWalkable(WorldPoint worldPoint) {
        if (worldPoint == null
                || Microbot.getClient() == null
                || !Microbot.getClient().isClientThread()) {
            return false;
        }
        SceneWalkProjection projection = projectCombatSceneTile(worldPoint);
        return projection.localPoint != null && Rs2Tile.isWalkable(projection.localPoint);
    }

    /**
     * A walkable tile on the far side of a fence is not a valid dodge destination.
     * Convert the live instance tile back to its template WorldPoint and let
     * Microbot's collision BFS verify it belongs to the same reachable component as
     * the player. This is intentionally client-thread-only so dodge code never waits
     * synchronously for the client thread.
     */
    private boolean isCombatSceneReachable(WorldPoint worldPoint) {
        if (worldPoint == null
                || Microbot.getClient() == null
                || !Microbot.getClient().isClientThread()) {
            return false;
        }
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null
                || player.getWorldLocation() == null
                || player.getWorldView() == null
                || !player.getWorldView().isInstance()) {
            return false;
        }
        NPC brutus = isBrutusIdNpc(lastKnownBrutusNpc) ? lastKnownBrutusNpc : null;
        if (brutus == null
                || brutus.getWorldView() == null
                || brutus.getWorldView().getId() != player.getWorldView().getId()) {
            return false;
        }
        if (player.getWorldLocation().distanceTo2D(worldPoint) > 12) {
            return false;
        }
        SceneWalkProjection projection = projectCombatSceneTile(worldPoint);
        if (projection.localPoint == null) {
            return false;
        }
        LocalPoint playerLocal = player.getLocalLocation();
        if (playerLocal == null
                || playerLocal.getWorldView() != projection.localPoint.getWorldView()) {
            return false;
        }
        WorldPoint templateTarget = WorldPoint.fromLocalInstance(
                Microbot.getClient(), projection.localPoint);
        return templateTarget != null && Rs2Tile.isTileReachable(templateTarget);
    }

    private boolean isCombatSceneSafeCandidate(WorldPoint worldPoint) {
        return isCombatSceneWalkable(worldPoint) && isCombatSceneReachable(worldPoint);
    }

    private void clearWalkerRouteIfActive(String reason) {
        if (Rs2Walker.getCurrentTarget() != null) {
            Rs2Walker.clearWalkingRoute(reason);
        }
    }

    /**
     * Global Rs2Walker.walkTo() is transport-aware. Never allow any stale banking,
     * altar, or recovery branch to start it while the player is physically inside
     * Ferox Enclave; Ferox movement is local-only in this plugin.
     */
    private boolean walkToOutsideFerox(WorldPoint target, int distance) {
        if (isAtFeroxEnclave()) {
            clearWalkerRouteIfActive("brutus-ferox-blocked-global-walk");
            visibleDebug("Travel", "Blocked global walkTo while inside Ferox target=" + target);
            return false;
        }
        return Rs2Walker.walkTo(target, distance);
    }

    private int currentGameTick() {
        if (Microbot.getClient() != null && Microbot.getClient().isClientThread()) {
            cachedGameTick = Microbot.getClient().getTickCount();
        }
        return cachedGameTick;
    }

    private boolean isBrutusMoving(Rs2NpcModel brutus) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            NPC npc = brutus == null ? null : brutus.getNpc();
            return npc != null && npc.getPoseAnimation() != npc.getIdlePoseAnimation();
        }).orElse(false);
    }

    private SpecialAttack specialForAnimation(int animation) {
        // Trigger on the actual Brutus action animations. The remaining generated
        // transition animations are kept in CHARGE_ANIMATIONS/STOMP_ANIMATIONS so
        // the state remains active, but they do not create duplicate dodge clicks.
        if (animation == AnimationID.COW_BOSS_HEAVY_BREATH
                || animation == AnimationID.COW_BOSS_CHARGE
                || animation == AnimationID.COW_BOSS_GHOST_CHARGE) {
            return SpecialAttack.CHARGE;
        }
        // Trigger on the earliest generated Stomp phase, including fade-in. The
        // sequence latch prevents later transition animations from creating a
        // second dodge for the same Stomp.
        if (STOMP_ANIMATIONS.contains(animation)) {
            return SpecialAttack.STOMP;
        }
        return SpecialAttack.NONE;
    }

    private boolean isAnimationFor(SpecialAttack attack, int animation) {
        return attack == SpecialAttack.CHARGE
                ? CHARGE_ANIMATIONS.contains(animation)
                : attack == SpecialAttack.STOMP && STOMP_ANIMATIONS.contains(animation);
    }

    private String normalizeSpecialCue(String overheadText) {
        if (overheadText == null) {
            return "";
        }
        String normalized = overheadText.toLowerCase(Locale.ROOT);
        if (normalized.contains("growl")) {
            return "charge";
        }
        if (normalized.contains("snort")) {
            return "stomp";
        }
        return "";
    }

    private synchronized void clearSpecialAvoidance() {
        activeSpecial = SpecialAttack.NONE;
        specialAnimationObserved = false;
        specialMovementAttempted = false;
        lastSpecialMovementTick = Integer.MIN_VALUE;
        specialMovementIssueCount = 0;
        specialMovementTargetAttempts = 0;
        specialMovementLastProgressTick = Integer.MIN_VALUE;
        specialMovementLastPlayerLocation = null;
        specialMovementIssuedTargets.clear();
        specialTargetReached = false;
        specialDodgeTarget = null;
        specialDodgeCandidates = List.of();
        specialCandidateIndex = 0;
        lastSpecialReattackTick = Integer.MIN_VALUE;
        activeSpecialStartedTick = Integer.MIN_VALUE;
        stompActivationTick = Integer.MIN_VALUE;
        stompReattackIssued = false;
        stompReattackIssuedTick = Integer.MIN_VALUE;
    }

    private synchronized void resetSpecialDetection() {
        clearSpecialAvoidance();
        pendingSpecial = SpecialAttack.NONE;
        pendingSpecialFromAnimation = false;
        pendingSpecialAnimation = -1;
        pendingSpecialArea = null;
        pendingSpecialOrientation = -1;
        cuePrimedAttack = SpecialAttack.NONE;
        cuePrimedTick = Integer.MIN_VALUE;
        chargeSequenceLatched = false;
        chargeSequenceAnimationObserved = false;
        stompSequenceLatched = false;
        currentBrutusAnimation = -1;
        currentBrutusAnimationFrame = -1;
        lastObservedBrutusAnimation = Integer.MIN_VALUE;
        lastObservedBrutusFrame = -1;
        lastObservedOverheadCue = "";
        if (!stompImpactTileTicks.isEmpty()) {
            stompImpactTileTicks.clear();
        }
    }

    private boolean useStatBoostingPotionIfNeeded() {
        if (!config.useStatBoostingPotions() || !playerReadyForAction()) {
            return false;
        }

        for (String potionName : potionPrioritiesForMode(combatMode)) {
            Rs2ItemModel potion = findInventoryPotion(potionName);
            if (potion == null) {
                continue;
            }
            boolean needsBoost = needsPotionBoost(potion.getName());
            int gameTick = currentGameTick();
            if (gameTick != lastPotionDebugTick) {
                lastPotionDebugTick = gameTick;
                visibleDebug("Potion", "check item=" + potion.getName()
                        + " needsDose=" + needsBoost
                        + " stats=" + Arrays.toString(readCombatStats()));
            }
            if (!needsBoost) {
                continue;
            }
            setState(KspMadCowState.USING_POTION, "Drinking " + potion.getName());
            boolean clicked = Rs2Inventory.interact(potion, "Drink");
            if (!clicked) {
                clicked = Rs2Inventory.interact(potion, "drink");
            }
            visibleDebug("Potion", "drink issued=" + clicked + " item=" + potion.getName());
            if (clicked) {
                combatReacquirePending = true;
            }
            return clicked;
        }
        return false;
    }

    private boolean needsPotionBoost(String potionName) {
        String name = potionName == null ? "" : potionName.toLowerCase(Locale.ROOT);
        int[] stats = readCombatStats();
        int attackBoosted = stats[0];
        int attackReal = stats[1];
        int strengthBoosted = stats[2];
        int strengthReal = stats[3];
        int defenceBoosted = stats[4];
        int defenceReal = stats[5];
        int rangedBoosted = stats[6];
        int rangedReal = stats[7];
        int magicBoosted = stats[8];
        int magicReal = stats[9];

        // Re-dose only when every positive boost supplied by that potion has
        // completely expired. A partial decay must never consume another dose.
        if (name.contains("combat potion")) {
            return attackBoosted <= attackReal
                    && strengthBoosted <= strengthReal
                    && defenceBoosted <= defenceReal;
        }
        if (name.contains("bastion")) {
            return rangedBoosted <= rangedReal && defenceBoosted <= defenceReal;
        }
        if (name.contains("battlemage")) {
            return magicBoosted <= magicReal && defenceBoosted <= defenceReal;
        }
        if (name.contains("strength")) {
            return strengthBoosted <= strengthReal;
        }
        if (name.contains("attack")) {
            return attackBoosted <= attackReal;
        }
        if (name.contains("defence") || name.contains("defense")) {
            return defenceBoosted <= defenceReal;
        }
        if (name.contains("ranging")) {
            return rangedBoosted <= rangedReal;
        }
        if (name.contains("magic")) {
            return magicBoosted <= magicReal;
        }
        return false;
    }

    private String findPotionToWithdrawFromBank() {
        if (!config.useStatBoostingPotions()) {
            return null;
        }

        String[] priorities = potionPrioritiesForMode(combatMode);
        if (hasAnyInventoryPotion(priorities)) {
            return null;
        }

        for (String name : priorities) {
            if (Rs2Bank.hasBankItem(name, 1, false)) {
                return name;
            }
        }
        return null;
    }

    private String[] potionPrioritiesForMode(CombatMode mode) {
        if (mode == CombatMode.RANGED) {
            return RANGED_POTIONS;
        }
        if (mode == CombatMode.MAGIC) {
            return MAGIC_POTIONS;
        }
        List<String> names = new ArrayList<>();
        names.addAll(Arrays.asList(MELEE_COMBINED_POTIONS));
        names.addAll(Arrays.asList(STRENGTH_POTIONS));
        names.addAll(Arrays.asList(ATTACK_POTIONS));
        names.addAll(Arrays.asList(DEFENCE_POTIONS));
        return names.toArray(new String[0]);
    }

    private boolean hasAnyInventoryPotion(String[] names) {
        for (String name : names) {
            if (findInventoryPotion(name) != null) {
                return true;
            }
        }
        return false;
    }

    private Rs2ItemModel findInventoryPotion(String baseName) {
        String normalized = baseName.toLowerCase(Locale.ROOT);
        return Rs2Inventory.all().stream()
                .filter(item -> item.getName() != null)
                .filter(item -> {
                    String itemName = item.getName().toLowerCase(Locale.ROOT);
                    return itemName.equals(normalized) || itemName.startsWith(normalized + "(");
                })
                .findFirst()
                .orElse(null);
    }

    private boolean isKnownStatPotion(String itemName) {
        if (itemName == null) {
            return false;
        }
        for (CombatMode mode : CombatMode.values()) {
            for (String potion : potionPrioritiesForMode(mode)) {
                String normalized = potion.toLowerCase(Locale.ROOT);
                String item = itemName.toLowerCase(Locale.ROOT);
                if (item.equals(normalized) || item.startsWith(normalized + "(")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean buryBones() {
        if (!config.buryBones() || !playerReadyForAction()) {
            return false;
        }

        Rs2TileItemModel groundBones = Microbot.getRs2TileItemCache().query()
                .where(item -> item.isLootAble() && isBones(item.getName()))
                .within(config.lootRadius())
                .nearestOnClientThread();
        if (groundBones != null) {
            setState(KspMadCowState.BURYING_BONES, "Burying " + groundBones.getName());
            return groundBones.click("Bury");
        }

        List<Rs2ItemModel> inventoryBones = Rs2Inventory.getBones();
        if (!inventoryBones.isEmpty()) {
            Rs2ItemModel bones = inventoryBones.get(0);
            setState(KspMadCowState.BURYING_BONES, "Burying " + bones.getName());
            return Rs2Inventory.interact(bones, "Bury");
        }
        return false;
    }

    private boolean lootOneItem() {
        if (!config.lootAll() || !playerReadyForAction()) {
            return false;
        }

        Set<String> specificLoot = configuredSpecificLootItems();
        Rs2TileItemModel loot = Microbot.getRs2TileItemCache().query()
                .where(item -> item.isLootAble()
                        && !isBones(item.getName())
                        && (specificLoot.isEmpty() || specificLoot.contains(normalizeLootName(item.getName()))))
                .within(config.lootRadius())
                .nearestOnClientThread();
        if (loot == null) {
            return false;
        }

        boolean fits = !Rs2Inventory.isFull() || (loot.isStackable() && Rs2Inventory.hasItem(loot.getId()));
        if (!fits) {
            return false;
        }

        setState(KspMadCowState.LOOTING, "Taking " + loot.getName());
        return loot.pickup();
    }

    /**
     * Parses the editable loot whitelist. A blank edit box means the existing
     * loot-everything behavior remains active. Item-name matching is exact after
     * trimming whitespace and is case-insensitive.
     */
    private Set<String> configuredSpecificLootItems() {
        String configured = config.specificLootItems();
        if (configured == null || configured.isBlank()) {
            return Set.of();
        }

        Set<String> items = new HashSet<>();
        for (String token : configured.split("[,;\\r\\n]+")) {
            String normalized = normalizeLootName(token);
            if (!normalized.isEmpty()) {
                items.add(normalized);
            }
        }
        return items;
    }

    private String normalizeLootName(String itemName) {
        return itemName == null ? "" : itemName.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Opens only the Ferox Enclave bank chest identified by the user's Object ID
     * Examiner (26711). No generic bank lookup, camera dependency, or walker call
     * is permitted here.
     */
    private boolean openFeroxBankChestDirectly() {
        if (Rs2Bank.isOpen()) {
            resetFeroxBankOpenState();
            return true;
        }

        long now = System.currentTimeMillis();
        if (feroxBankOpenPending) {
            long elapsed = now - feroxBankOpenIssuedAtMs;
            if (elapsed < FEROX_BANK_OPEN_RETRY_MS) {
                // One click is enough. The game may spend several client cycles walking
                // the final tile(s) and opening the interface. Re-clicking every 100 ms
                // cancels/restarts that interaction and is exactly what caused the spam.
                setState(KspMadCowState.BANKING,
                        "Waiting for Ferox bank chest interaction to finish");
                return true;
            }

            visibleDebug("Bank", "Ferox bank did not open after " + elapsed
                    + "ms; allowing one retry");
            resetFeroxBankOpenState();
        }

        Rs2TileObjectModel bankChest = Microbot.getRs2TileObjectCache().query()
                .withId(FEROX_BANK_CHEST_ID)
                .where(this::isObjectInPlayerWorldView)
                .nearestOnClientThread();
        if (bankChest == null) {
            setState(KspMadCowState.BANKING,
                    "At Ferox Enclave; waiting for bank chest");
            return false;
        }

        String bankAction = matchingAction(bankChest, FEROX_BANK_CHEST_ACTIONS);
        if (bankAction == null) {
            setState(KspMadCowState.BANKING,
                    "Ferox bank chest found; waiting for bank action");
            return false;
        }

        setState(KspMadCowState.BANKING,
                "At Ferox Enclave; clicking bank chest once");
        boolean issued = invokeSceneObjectActionDirect(bankChest, bankAction, "Ferox bank chest");
        if (issued) {
            feroxBankOpenPending = true;
            feroxBankOpenIssuedAtMs = now;
            visibleDebug("Bank", "Direct Ferox bank chest interaction issued id="
                    + FEROX_BANK_CHEST_ID + " action=" + bankAction
                    + "; waiting for bank interface before any retry");
        }
        return issued;
    }

    private void resetFeroxBankOpenState() {
        feroxBankOpenPending = false;
        feroxBankOpenIssuedAtMs = 0L;
    }

    /**
     * Quick-escape does not require the gate to be inside the current camera
     * viewport. Invoke the object's menu action directly using its live scene
     * coordinates and WorldView id. This is deliberately walker-free.
     */
    private boolean clickQuickEscapeGate(ObjectAction quickLeave) {
        if (quickLeave == null || quickLeave.object == null) {
            return false;
        }

        boolean issued = invokeSceneObjectActionDirect(
                quickLeave.object, quickLeave.action, "Quick-escape gate");
        if (issued) {
            visibleDebug("Travel", "Quick-escape interaction issued action="
                    + quickLeave.action + " objectId=" + quickLeave.object.getId());
        }
        return issued;
    }

    /**
     * Direct scene-object menu invocation which does not depend on the object being
     * visible on the canvas. The object must belong to the local player's current
     * WorldView. For GameObjects the current Microbot model exposes the scene-min
     * world location, so converting that back to LocalPoint yields the exact scene
     * menu parameters even inside an instance.
     */
    private boolean invokeSceneObjectActionDirect(Rs2TileObjectModel object,
                                                   String action,
                                                   String debugName) {
        if (object == null || action == null || action.isBlank()) {
            return false;
        }

        // Use Microbot's current API tile-object model for the actual invoke. Unlike
        // the legacy util tile-object model, this API implementation does NOT call
        // Rs2Walker when the object is off-screen/farther away. It keeps the raw
        // underlying TileObject, computes the correct scene parameters for multi-tile
        // GameObjects, resolves transformed/impostor actions and carries WorldViewId.
        // That is important for the 39651 Pool of Refreshment, where our hand-built
        // wrapper MenuEntry could be accepted locally but ignored by the game.
        if (!isObjectInPlayerWorldView(object)) {
            return false;
        }

        try {
            visibleDebug("Object", "Direct API interact " + debugName
                    + " id=" + object.getId() + " action=" + action
                    + " worldView=" + object.getWorldView().getId());
            return object.click(action);
        } catch (Exception ex) {
            visibleDebug("Object", "Direct API interaction failed for " + debugName
                    + " id=" + object.getId() + " action=" + action
                    + " error=" + ex.getClass().getSimpleName());
            return false;
        }
    }

    private boolean isObjectInPlayerWorldView(Rs2TileObjectModel object) {
        if (object == null || object.getWorldView() == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null
                    && player.getWorldView() != null
                    && player.getWorldView().getId() == object.getWorldView().getId();
        }).orElse(false);
    }

    private boolean quickLeave(ObjectAction knownQuickLeave, String reason) {
        ObjectAction quickLeave = knownQuickLeave != null
                ? knownQuickLeave
                : findObjectAction(QUICK_ESCAPE_ACTIONS);

        // Do not abandon the leave transaction just because the scene cache misses
        // the gate for one cycle. Keep the instance latched and retry until the
        // Quick-escape object is available.
        leavingInstance = true;
        quickLeaveClickIssued = false;
        quickLeaveLastAttemptAtMs = 0L;
        if (quickLeave == null) {
            setState(KspMadCowState.LEAVING_INSTANCE,
                    reason + "; waiting for Quick-escape gate");
            return true;
        }

        setState(KspMadCowState.LEAVING_INSTANCE, reason + "; using Quick-escape gate");
        if (playerReadyForAction() && clickQuickEscapeGate(quickLeave)) {
            quickLeaveClickIssued = true;
            quickLeaveLastAttemptAtMs = System.currentTimeMillis();
        }
        return true;
    }

    private void attackBrutus(Rs2NpcModel brutus) {
        if (isPlayerTargetingAnyBrutus()) {
            combatReacquirePending = false;
            lastCombatBlockedReason = "";
            setState(KspMadCowState.FIGHTING, "Fighting Brutus");
            return;
        }

        // Once the player has no Brutus interaction target, keep combat
        // reacquisition at the top of the inside-instance priority chain. This is
        // especially important after eating, drinking, Charge and Stomp.
        combatReacquirePending = true;

        Rs2NpcModel idBrutus = findBrutus();
        if (idBrutus == null) {
            idBrutus = brutus;
        }
        if (idBrutus == null || idBrutus.getNpc() == null
                || !isBrutusIdNpc(idBrutus.getNpc())) {
            logCombatBlockedOnce("normal attack", "Brutus NPC IDs not found");
            return;
        }

        setState(KspMadCowState.FIGHTING,
                combatReacquirePending
                        ? "Re-attacking Brutus after movement/action"
                        : "Attacking Brutus");

        // Re-click Brutus whenever the player is no longer animating and has no
        // Brutus interaction target. Movement is not an additional blocker.
        if (isPlayerCurrentlyAnimating()) {
            logCombatBlockedOnce("normal attack", "player animating");
            return;
        }

        int gameTick = currentGameTick();
        if (gameTick != Integer.MIN_VALUE && gameTick == lastCombatAttackTick) {
            return;
        }
        lastCombatAttackTick = gameTick;
        lastCombatBlockedReason = "";

        boolean issued = invokeBrutusAttackById(idBrutus,
                combatReacquirePending ? "combat reacquire" : "normal attack");
        visibleDebug("Combat", "attack issued=" + issued
                + " tick=" + gameTick
                + " npcId=" + idBrutus.getId()
                + " npcIndex=" + idBrutus.getIndex()
                + " reacquirePending=" + combatReacquirePending
                + " moving=" + Rs2Player.isMoving()
                + " scenePlayer=" + getCombatScenePlayerLocation()
                + " cachedPlayer=" + Rs2Player.getWorldLocation());
        combatReacquirePending = true;
    }

    private boolean invokeBrutusAttackById(Rs2NpcModel brutus, String context) {
        if (brutus == null || brutus.getNpc() == null) {
            visibleDebug("Combat", context + " rejected: Brutus model is null");
            return false;
        }

        // RuneLite actor fields are client-thread guarded. The client-thread fix resolved the
        // Attack option on the client thread, but then read npc.getName() and the
        // actor clickbox from KspMadCowScript-main. That threw before doInvoke could
        // submit the menu action. Build and invoke the complete NPC interaction on
        // the client thread and return only immutable diagnostic data to the script.
        AttackInvokeResult result;
        try {
            result = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                NPC npc = brutus.getNpc();
                if (npc == null) {
                    return AttackInvokeResult.failure(-1, -1, AttackMenuResolution.NONE,
                            "NPC disappeared before client-thread invocation");
                }

                int npcId = npc.getId();
                int npcIndex = npc.getIndex();
                if (!isBrutusIdNpc(npc)) {
                    return AttackInvokeResult.failure(npcId, npcIndex,
                            AttackMenuResolution.NONE, "resolved actor is not the configured Brutus target");
                }

                AttackMenuResolution resolution = resolveAttackMenu(npc);
                if (!resolution.available()) {
                    return AttackInvokeResult.failure(npcId, npcIndex, resolution,
                            "Attack option unavailable");
                }

                String npcName = npc.getName();
                Rectangle clickbox = Rs2UiHelper.getActorClickbox(npc);
                if (clickbox == null) {
                    clickbox = new Rectangle(1, 1, 1, 1);
                }

                // Do not retain the live NPC actor in NewMenuEntry. VirtualMouse can
                // otherwise re-read its clickbox later from its executor thread. The
                // NPC index and resolved NPC_*_OPTION are sufficient for the action.
                Microbot.doInvoke(
                        new NewMenuEntry()
                                .option("Attack")
                                .target(npcName == null ? BRUTUS_NAME : npcName)
                                .identifier(npcIndex)
                                .type(resolution.menuAction)
                                .param0(0)
                                .param1(0)
                                .itemId(-1),
                        clickbox
                );
                return AttackInvokeResult.success(npcId, npcIndex, resolution);
            }).orElse(AttackInvokeResult.failure(-1, -1, AttackMenuResolution.NONE,
                    "client-thread invocation returned no result"));
        } catch (Exception ex) {
            log.warn("[KSP Mad Cow][Combat] Client-thread Attack invoke failed context={}", context, ex);
            visibleDebug("Combat", context + " client-thread invoke exception="
                    + ex.getClass().getSimpleName());
            return false;
        }

        if (!result.issued) {
            visibleDebug("Combat", context + " not issued"
                    + " reason=" + result.failureReason
                    + " npcId=" + result.npcId
                    + " npcIndex=" + result.npcIndex
                    + " actions=" + result.resolution.actionsSummary);
            return false;
        }

        visibleDebug("Combat", context + " menu invoke"
                + " npcId=" + result.npcId
                + " npcIndex=" + result.npcIndex
                + " optionIndex=" + result.resolution.optionIndex
                + " menuAction=" + result.resolution.menuAction
                + " actions=" + result.resolution.actionsSummary
                + " thread=client");
        return true;
    }

    private AttackMenuResolution resolveAttackMenu(NPC npc) {
        if (npc == null) {
            return AttackMenuResolution.NONE;
        }

        NPCComposition transformed = npc.getTransformedComposition();
        NPCComposition base = npc.getComposition();
        NPCComposition definition = Microbot.getClient().getNpcDefinition(npc.getId());
        NPCComposition[] compositions = {transformed, base, definition};
        StringBuilder summaries = new StringBuilder();

        for (int sourceIndex = 0; sourceIndex < compositions.length; sourceIndex++) {
            NPCComposition composition = compositions[sourceIndex];
            if (composition == null) {
                continue;
            }
            if (sourceIndex > 0 && composition == compositions[sourceIndex - 1]) {
                continue;
            }

            String[] actions = composition.getActions();
            if (summaries.length() > 0) {
                summaries.append(" | ");
            }
            summaries.append(sourceIndex == 0 ? "transformed="
                    : sourceIndex == 1 ? "base=" : "definition=")
                    .append(actions == null ? "null" : Arrays.toString(actions));
            if (actions == null) {
                continue;
            }

            for (int index = 0; index < Math.min(actions.length, 5); index++) {
                String action = actions[index];
                if (action != null && action.equalsIgnoreCase("Attack")) {
                    return new AttackMenuResolution(
                            index,
                            npcMenuAction(index),
                            summaries.toString()
                    );
                }
            }
        }

        return new AttackMenuResolution(
                -1,
                null,
                summaries.length() == 0 ? "no-composition" : summaries.toString()
        );
    }

    private MenuAction npcMenuAction(int index) {
        switch (index) {
            case 0: return MenuAction.NPC_FIRST_OPTION;
            case 1: return MenuAction.NPC_SECOND_OPTION;
            case 2: return MenuAction.NPC_THIRD_OPTION;
            case 3: return MenuAction.NPC_FOURTH_OPTION;
            case 4: return MenuAction.NPC_FIFTH_OPTION;
            default: return null;
        }
    }

    private void logCombatBlockedOnce(String context, String reason) {
        int gameTick = currentGameTick();
        String key = context + ":" + reason;
        if (key.equals(lastCombatBlockedReason)
                && gameTick != Integer.MIN_VALUE
                && lastCombatBlockedLogTick != Integer.MIN_VALUE
                && gameTick - lastCombatBlockedLogTick < 5) {
            return;
        }
        lastCombatBlockedLogTick = gameTick;
        lastCombatBlockedReason = key;
        visibleDebug("Combat", context + " blocked=" + reason
                + " tick=" + gameTick
                + " player=" + Rs2Player.getWorldLocation()
                + " animation=" + currentPlayerAnimation()
                + " moving=" + Rs2Player.isMoving()
                + " targetingBrutus=" + isPlayerTargetingBrutusId());
    }

    private int currentPlayerAnimation() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player == null ? -1 : player.getAnimation();
        }).orElse(-1);
    }

    private boolean isPlayerTargetingBrutus(Rs2NpcModel brutus) {
        return isPlayerTargetingBrutusId();
    }

    private boolean isPlayerTargetingAnyBrutus() {
        return isPlayerTargetingBrutusId();
    }

    private boolean isPlayerTargetingBrutusId() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            Actor target = player == null ? null : player.getInteracting();
            return target instanceof NPC && isBrutusIdNpc((NPC) target);
        }).orElse(false);
    }

    private boolean playerReadyForAction() {
        return !Rs2Player.isMoving() && !isPlayerCurrentlyAnimating();
    }

    private boolean isPlayerCurrentlyAnimating() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player != null && player.getAnimation() != -1;
        }).orElse(false);
    }

    private boolean isClientInInstancedRegion() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var worldView = Microbot.getClient().getTopLevelWorldView();
            return worldView != null && worldView.isInstance();
        }).orElse(false);
    }

    /**
     * Brutus-specific instance detection. RuneLite's WorldView#isInstance() only
     * tells us that the current map is instanced; it does not identify which
     * instance it is. Using it directly caused Death's Domain to be reported as
     * the Brutus encounter.
     */
    private boolean isBrutusInstanceContext(ObjectAction quickLeave, Rs2NpcModel brutus) {
        // RuneLite's generic instance flag is necessary but never sufficient:
        // Death's Domain and many unrelated maps are also instances.
        if (!clientInstanceDetected) {
            return false;
        }

        // Confirm the encounter exclusively from live Brutus-specific scene evidence.
        // Coordinate/template-area checks and lastPenEntryAction are deliberately not
        // accepted as proof, because both previously caused false "in Brutus area"
        // / premature-instance states immediately after Cowbell + Release.
        if (quickLeave != null
                || (brutus != null && brutus.getNpc() != null)
                || hasAnyBrutusNpcInWorldView()) {
            return true;
        }

        // Once positively confirmed, retain the encounter during the normal respawn
        // gap while RuneLite still reports the same top-level instanced WorldView.
        return instanceConfirmed;
    }

    private boolean hasAnyBrutusNpcInWorldView() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var worldView = Microbot.getClient().getTopLevelWorldView();
            if (worldView == null) {
                return false;
            }
            for (NPC npc : worldView.npcs()) {
                if (npc == null) {
                    continue;
                }
                int id = npc.getId();
                if (id == BRUTUS_ID
                        || id == BRUTUS_ALT_ID
                        || id == DEMONIC_BRUTUS_ID
                        || id == DEMONIC_BRUTUS_GHOST_ID) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    /**
     * Returns true only for the NPC that may be attacked in the currently selected
     * encounter mode. Demonic ghost copies are deliberately excluded.
     */
    private boolean isBrutusIdNpc(NPC npc) {
        if (npc == null) {
            return false;
        }
        int id = npc.getId();
        if (config != null && config.demonicBrutus()) {
            return id == DEMONIC_BRUTUS_ID;
        }
        return id == BRUTUS_ID || id == BRUTUS_ALT_ID;
    }

    private boolean isNormalBrutusNpc(NPC npc) {
        if (npc == null) {
            return false;
        }
        int id = npc.getId();
        return id == BRUTUS_ID || id == BRUTUS_ALT_ID;
    }

    private boolean isDemonicBrutusMechanicNpc(NPC npc) {
        if (npc == null) {
            return false;
        }
        int id = npc.getId();
        return id == DEMONIC_BRUTUS_ID || id == DEMONIC_BRUTUS_GHOST_ID;
    }

    private boolean isBrutusMechanicNpc(NPC npc) {
        if (config != null && config.demonicBrutus()) {
            return isDemonicBrutusMechanicNpc(npc);
        }
        return isNormalBrutusNpc(npc);
    }

    private Rs2NpcModel findBrutus() {
        int[] targetIds = config != null && config.demonicBrutus()
                ? new int[]{DEMONIC_BRUTUS_ID}
                : new int[]{BRUTUS_ID, BRUTUS_ALT_ID};

        Rs2NpcModel cached = Microbot.getRs2NpcCache().query()
                .withIds(targetIds)
                .nearestOnClientThread();
        if (cached != null && cached.getNpc() != null) {
            lastKnownBrutusNpc = cached.getNpc();
            return cached;
        }

        // Charge can briefly outrun the asynchronous Microbot NPC cache. Fall back
        // to RuneLite's live WorldView so the rear-tile re-attack state is not lost.
        Rs2NpcModel live = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var worldView = Microbot.getClient().getTopLevelWorldView();
            if (worldView == null) {
                return null;
            }

            Player player = Microbot.getClient().getLocalPlayer();
            WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
            NPC nearest = null;
            int nearestDistance = Integer.MAX_VALUE;

            for (NPC npc : worldView.npcs()) {
                if (npc == null || !isBrutusIdNpc(npc)) {
                    continue;
                }

                WorldPoint npcLocation = npc.getWorldLocation();
                int distance = playerLocation == null || npcLocation == null
                        ? 0
                        : playerLocation.distanceTo2D(npcLocation);
                if (nearest == null || distance < nearestDistance) {
                    nearest = npc;
                    nearestDistance = distance;
                }
            }

            return nearest == null ? null : new Rs2NpcModel(nearest);
        }).orElse(null);

        if (live != null && live.getNpc() != null) {
            lastKnownBrutusNpc = live.getNpc();
        }
        return live;
    }

    private boolean isAlive(Rs2NpcModel brutus) {
        if (brutus == null || brutus.getNpc() == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> !brutus.getNpc().isDead()
        ).orElse(false);
    }

    public synchronized void onBrutusDeath(Actor actor) {
        if (!running || !instanceConfirmed || !killArmed || !isCombatTargetActor(actor)) {
            return;
        }
        killArmed = false;
        killCount++;
        brutusAlive = false;
        speedRingPending = config != null && config.speed() && !config.demonicBrutus();
        speedRingPrioritySatisfied = !speedRingPending;
        speedRingAttempts = 0;
        speedRingLastAttemptAtMs = 0L;
        prayersSuppressedUntilRespawn = true;
        if (config != null && config.demonicBrutus()) {
            demonicAttemptCompleted = true;
            demonicAttemptActive = false;
            demonicProtectionPrayer = null;
            demonicProtectionPrayerUntilTick = Integer.MIN_VALUE;
        }
        disableManagedCombatPrayers();
        resetSpecialDetection();
        combatReacquirePending = false;
        setMessage((config != null && config.demonicBrutus() ? "Demonic Brutus" : "Brutus")
                + " defeated; prayers disabled; kill count " + killCount);
    }

    private boolean isCombatTargetActor(Actor actor) {
        return actor instanceof NPC && isBrutusIdNpc((NPC) actor);
    }

    private boolean isBrutusActor(Actor actor) {
        return actor instanceof NPC && isBrutusMechanicNpc((NPC) actor);
    }

    /**
     * Locates only the actual Brutus pen entry interaction. This intentionally does
     * not use the generic "Enter" action and explicitly rejects the two known
     * Death's Domain entrances (38426 in Lumbridge and 39637 in Ferox).
     *
     * Keeping this as a dedicated lookup is important: travelWithCowbell() uses the
     * presence of this object as its teleport-arrival confirmation. A generic action
     * lookup can therefore both suppress the Cowbell click and then cause
     * enterInstance() to invoke the wrong world object.
     */
    private ObjectAction findPenEntryAction() {
        Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query()
                .where(this::isObjectInPlayerWorldView)
                .where(candidate -> !DEATHS_DOMAIN_ENTRANCE_IDS.contains(candidate.getId()))
                .where(candidate -> matchingAction(candidate, PEN_ENTRY_ACTIONS) != null)
                .nearestOnClientThread();
        if (object == null) {
            return null;
        }

        String action = matchingAction(object, PEN_ENTRY_ACTIONS);
        if (action == null) {
            return null;
        }

        visibleDebug("Travel", "Brutus pen candidate accepted id=" + object.getId()
                + " action=" + action + " worldView=" + object.getWorldView().getId());
        return new ObjectAction(object, action);
    }

    private ObjectAction findObjectAction(String... requestedActions) {
        String[] requested = Arrays.stream(requestedActions)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
        if (requested.length == 0) {
            return null;
        }

        Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query()
                .where(this::isObjectInPlayerWorldView)
                .where(candidate -> matchingAction(candidate, requested) != null)
                .nearestOnClientThread();
        if (object == null) {
            return null;
        }
        String action = matchingAction(object, requested);
        return action == null ? null : new ObjectAction(object, action);
    }

    private String matchingAction(Rs2TileObjectModel object, String[] requestedActions) {
        try {
            if (object == null || object.getObjectComposition() == null) {
                return null;
            }
            var composition = object.getObjectComposition();
            if (composition.getImpostorIds() != null && composition.getImpostor() != null) {
                composition = composition.getImpostor();
            }
            String[] actions = composition.getActions();
            if (actions == null) {
                return null;
            }

            for (String requested : requestedActions) {
                String normalizedRequested = normalizeAction(requested);
                for (String actual : actions) {
                    if (actual == null) {
                        continue;
                    }
                    String normalizedActual = normalizeAction(actual);
                    if (normalizedActual.equals(normalizedRequested)
                            || normalizedActual.contains(normalizedRequested)) {
                        return actual;
                    }
                }
            }
        } catch (Exception ignored) {
            // Object may transform or disappear during lookup.
        }
        return null;
    }

    private boolean interactInventoryFirstAvailable(int itemId, String... preferredActions) {
        Rs2ItemModel item = Rs2Inventory.get(itemId);
        if (item == null || item.getInventoryActions() == null) {
            return false;
        }
        for (String preferred : preferredActions) {
            for (String actual : item.getInventoryActions()) {
                if (actual != null && normalizeAction(actual).equals(normalizeAction(preferred))) {
                    return Rs2Inventory.interact(item, actual);
                }
            }
        }
        return false;
    }

    private String normalizeAction(String action) {
        return action.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private int getConfiguredFoodQuantity() {
        return config == null ? 0 : Rs2Inventory.itemQuantity(config.food().getId());
    }

    private boolean hasConfiguredFood() {
        return getConfiguredFoodQuantity() > 0;
    }

    private boolean hasRequiredFoodAmount() {
        return getConfiguredFoodQuantity() >= Math.max(1, config.foodAmount());
    }

    private boolean hasCowbell() {
        return Rs2Inventory.hasItem(COWBELL_EMPTY_ID, COWBELL_CHARGED_ID)
                || Rs2Equipment.isWearing(COWBELL_EMPTY_ID, COWBELL_CHARGED_ID);
    }

    private boolean isBones(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("bones");
    }

    private WorldPoint offset(WorldPoint point, int x, int y) {
        return new WorldPoint(point.getX() + x, point.getY() + y, point.getPlane());
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private boolean isWalkable(WorldPoint point) {
        try {
            return Rs2Tile.isWalkable(Rs2LocalPoint.fromWorldInstance(point));
        } catch (Exception ignored) {
            return false;
        }
    }

    private int hitpointsPercent() {
        int[] values = readLevels(Skill.HITPOINTS);
        return values[0] * 100 / Math.max(1, values[1]);
    }

    private int getCurrentPrayerPointsSafe() {
        return readLevels(Skill.PRAYER)[0];
    }

    private int getRealPrayerLevelSafe() {
        return readLevels(Skill.PRAYER)[1];
    }

    private int[] readLevels(Skill... skills) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int[] values = new int[skills.length * 2];
            for (int i = 0; i < skills.length; i++) {
                values[i * 2] = Microbot.getClient().getBoostedSkillLevel(skills[i]);
                values[i * 2 + 1] = Microbot.getClient().getRealSkillLevel(skills[i]);
            }
            return values;
        }).orElse(new int[skills.length * 2]);
    }

    private int[] readCombatStats() {
        return readLevels(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC);
    }

    private void supplyFailure(String message) {
        setState(KspMadCowState.ERROR, message);
        if (!suppliesErrorShown) {
            suppliesErrorShown = true;
            Microbot.showMessage(message);
        }
        if (config.shutdownOnMissingSupplies()) {
            requestPluginStop();
        }
    }

    private void requestPluginStop() {
        Runnable callback = stopRequest;
        if (callback != null) {
            callback.run();
        } else {
            shutdown();
        }
    }

    private void visibleDebug(String category, String message) {
        if (config != null && !config.debugLogging()) {
            return;
        }
        String line = "[KSP Mad Cow][" + category + "] " + message;
        try {
            // Microbot.log writes at INFO through Microbot's own logger, which is
            // the stream shown by the Microbot log panel.
            Microbot.log(line);
        } catch (Exception ex) {
            log.info(line);
            log.debug("Unable to forward KSP Mad Cow debug message to Microbot log", ex);
        }
    }

    private void setState(KspMadCowState newState, String message) {
        state = newState;
        setMessage(message);
    }

    private void setMessage(String message) {
        lastMessage = message;
        Microbot.status = message;
    }

    private void refreshOverlaySnapshot() {
        try {
            int[] hpPrayer = readLevels(Skill.HITPOINTS, Skill.PRAYER);
            int hpPercent = hpPrayer[0] * 100 / Math.max(1, hpPrayer[1]);
            int prayerCurrent = hpPrayer[2];
            int prayerReal = hpPrayer[3];
            long elapsed = elapsedRuntimeMillis();
            double killsPerHour = elapsed <= 0L ? 0.0 : killCount * 3_600_000.0 / elapsed;

            overlaySnapshot = new OverlaySnapshot(
                    formatRuntime(elapsed),
                    killCount,
                    String.format(Locale.ROOT, "%.1f", killsPerHour),
                    state,
                    lastMessage,
                    instanceConfirmed,
                    clientInstanceDetected,
                    travelPhaseSummary(),
                    bossStatus(),
                    combatMode.getDisplayName(),
                    trainingDisplay(),
                    hpPercent,
                    prayerCurrent,
                    prayerReal,
                    prayerPlanSummary(prayerReal),
                    activePrayerSummary,
                    activeSpecialSummary(),
                    currentBrutusAnimation,
                    currentBrutusAnimationFrame,
                    specialTargetSummary(),
                    combatReacquirePending,
                    config == null ? "Food" : config.food().getName(),
                    getConfiguredFoodQuantity(),
                    config == null ? 0 : Math.max(1, config.foodAmount()),
                    Rs2Inventory.fullSlotCount(),
                    cowbellStatus(),
                    Rs2Inventory.itemQuantity(AIR_RUNE_ID),
                    mooletaStatus(),
                    altarRestorePending,
                    potionStatus(),
                    running
            );
        } catch (Exception ex) {
            log.debug("Unable to refresh Brutus overlay snapshot", ex);
        }
    }

    private long elapsedRuntimeMillis() {
        if (running && startedAtNanos > 0L) {
            return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
        }
        return stoppedElapsedMillis;
    }

    private String formatRuntime(long elapsedMillis) {
        long secondsTotal = elapsedMillis / 1_000L;
        long hours = secondsTotal / 3_600L;
        long minutes = (secondsTotal % 3_600L) / 60L;
        long seconds = secondsTotal % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String travelPhaseSummary() {
        if (leavingInstance) {
            return "Leaving instance";
        }
        if (instanceConfirmed) {
            return "Inside instance";
        }
        if (!travelRequired) {
            return cowbellTravelIssued ? "Travelling to pen" : "At/entering pen";
        }
        if (initialBankCheckPending) {
            return "Restocking";
        }
        return "Cowbell armed";
    }

    private String bossStatus() {
        if (brutusAlive) {
            return "Alive";
        }
        if (instanceConfirmed) {
            return "Respawning";
        }
        return "Outside instance";
    }

    private String trainingDisplay() {
        if (combatMode == CombatMode.RANGED) {
            return "Ranged";
        }
        if (combatMode == CombatMode.MAGIC) {
            return "Magic";
        }
        return config != null && config.balanceCombatStats()
                ? trainingSkill.getName()
                : "Current melee style";
    }

    private String prayerPlanSummary(int prayerLevel) {
        Rs2PrayerEnum[] desired = desiredPrayersFor(combatMode, prayerLevel, activeDemonicProtectionPrayer());
        if (desired.length == 0) {
            return "None at current level";
        }
        List<String> names = new ArrayList<>();
        for (Rs2PrayerEnum prayer : desired) {
            names.add(prayer.getName());
        }
        return String.join(" + ", names);
    }

    private String activeSpecialSummary() {
        if (activeSpecial == SpecialAttack.CHARGE) {
            return "Charge";
        }
        if (activeSpecial == SpecialAttack.STOMP) {
            return "Stomp";
        }
        return "None";
    }

    private String specialTargetSummary() {
        if (specialDodgeTarget == null) {
            return "None";
        }
        return specialDodgeTarget.getX() + ", " + specialDodgeTarget.getY() + ", " + specialDodgeTarget.getPlane();
    }

    private String cowbellStatus() {
        if (Rs2Equipment.isWearing(COWBELL_CHARGED_ID)) {
            return "Charged (equipped)";
        }
        if (Rs2Inventory.hasItem(COWBELL_CHARGED_ID)) {
            return "Charged";
        }
        if (Rs2Equipment.isWearing(COWBELL_EMPTY_ID)) {
            return "Empty (equipped)";
        }
        if (Rs2Inventory.hasItem(COWBELL_EMPTY_ID)) {
            return "Empty";
        }
        return "Missing";
    }

    private String mooletaStatus() {
        if (combatMode != CombatMode.MELEE || config == null || !config.equipMooleta()) {
            return "Not required";
        }
        if (Rs2Equipment.isWearing(MOOLETA_ID)) {
            return "Equipped";
        }
        if (Rs2Inventory.hasItem(MOOLETA_ID)) {
            return "Inventory";
        }
        return "Not available";
    }

    private String potionStatus() {
        if (config == null || !config.useStatBoostingPotions()) {
            return "Disabled";
        }
        for (String potion : potionPrioritiesForMode(combatMode)) {
            Rs2ItemModel item = findInventoryPotion(potion);
            if (item != null) {
                return item.getName();
            }
        }
        return "None carried";
    }

    public KspMadCowState getState() {
        return state;
    }

    public Skill getTrainingSkill() {
        return trainingSkill;
    }

    public int getKillCount() {
        return killCount;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public CombatMode getCombatMode() {
        return combatMode;
    }

    public String getActivePrayerSummary() {
        return activePrayerSummary;
    }

    public boolean isBrutusAlive() {
        return brutusAlive;
    }

    public OverlaySnapshot getOverlaySnapshot() {
        return overlaySnapshot;
    }

    @Override
    public synchronized void shutdown() {
        if (!running && state == KspMadCowState.STOPPED) {
            return;
        }

        stoppedElapsedMillis = elapsedRuntimeMillis();
        running = false;

        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        super.shutdown();

        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdownNow();
        }

        try {
            if (Microbot.isLoggedIn()) {
                disableManagedCombatPrayers();
            }
        } catch (Exception ex) {
            log.debug("Unable to disable Brutus prayers during shutdown", ex);
        }

        clearWalkerRouteIfActive("brutus-shutdown");
        resetSpecialDetection();
        bankActionPending = false;
        bankActionComplete = null;
        resetFeroxBankOpenState();
        lmsBankTeleportIssued = false;
        lmsBankTeleportIssuedTick = Integer.MIN_VALUE;
        lastMinigameMenuScrollAtMs = 0L;
        minigameMenuScrollCount = 0;
        minigameTeleportCooldownUntilMs = 0L;
        lumbridgeFallbackBanking = false;
        lumbridgePostBankAltarPending = false;
        postBankRefreshPending = false;
        poolRefreshIssued = false;
        poolRefreshIssuedTick = Integer.MIN_VALUE;
        poolPrayerPointsBeforeInteraction = -1;
        poolPrayerWasFullBeforeInteraction = false;
        poolAnimationObserved = false;
        poolPostAnimationIdleTick = Integer.MIN_VALUE;
        lastFeroxMovementStopAtMs = 0L;
        lastDeathsOfficeExitAttemptTick = Integer.MIN_VALUE;
        poolPrayerRestoredAtMs = 0L;
        leavingInstance = false;
        quickLeaveClickIssued = false;
        quickLeaveLastAttemptAtMs = 0L;
        cowbellTravelIssued = false;
        resetCowbellTravelAttemptState();
        altarInteractionIssued = false;
        altarRestorePending = false;
        altarPrayerPointsBeforeInteraction = -1;
        altarTeleportPending = false;
        clearHealActionState();
        instanceConfirmed = false;
        clientInstanceDetected = false;
        combatReacquirePending = false;
        lastCombatAttackTick = Integer.MIN_VALUE;
        lastCombatBlockedLogTick = Integer.MIN_VALUE;
        lastCombatBlockedReason = "";
        lastRangedAmmoEquipTick = Integer.MIN_VALUE;
        trackedRangedAmmoId = -1;
        trackedRangedAmmoName = "";
        lastHealDebugTick = Integer.MIN_VALUE;
        lastPotionDebugTick = Integer.MIN_VALUE;
        killArmed = false;
        speedRingPending = false;
        speedRingPrioritySatisfied = true;
        speedRingAttempts = 0;
        speedRingLastAttemptAtMs = 0L;
        prayersSuppressedUntilRespawn = false;
        lastKnownBrutusNpc = null;
        travelRequired = true;
        lastPenEntryAction = "";
        lastPenEntryAttemptTick = Integer.MIN_VALUE;
        initialBankCheckPending = true;
        stopRequest = null;
        state = KspMadCowState.STOPPED;
        lastMessage = "Stopped";
        Microbot.status = "KSP Mad Cow stopped";
        refreshOverlaySnapshot();
    }

    public static final class OverlaySnapshot {
        private final String runtime;
        private final int kills;
        private final String killsPerHour;
        private final KspMadCowState state;
        private final String action;
        private final boolean instanceConfirmed;
        private final boolean clientInstance;
        private final String travelPhase;
        private final String bossStatus;
        private final String combatMode;
        private final String training;
        private final int hitpointsPercent;
        private final int prayerCurrent;
        private final int prayerReal;
        private final String prayerPlan;
        private final String activePrayers;
        private final String special;
        private final int brutusAnimation;
        private final int brutusAnimationFrame;
        private final String specialTarget;
        private final boolean reattackPending;
        private final String foodName;
        private final int foodCount;
        private final int foodTarget;
        private final int inventorySlots;
        private final String cowbell;
        private final int airRunes;
        private final String mooleta;
        private final boolean altarRestore;
        private final String potion;
        private final boolean running;

        private OverlaySnapshot(
                String runtime,
                int kills,
                String killsPerHour,
                KspMadCowState state,
                String action,
                boolean instanceConfirmed,
                boolean clientInstance,
                String travelPhase,
                String bossStatus,
                String combatMode,
                String training,
                int hitpointsPercent,
                int prayerCurrent,
                int prayerReal,
                String prayerPlan,
                String activePrayers,
                String special,
                int brutusAnimation,
                int brutusAnimationFrame,
                String specialTarget,
                boolean reattackPending,
                String foodName,
                int foodCount,
                int foodTarget,
                int inventorySlots,
                String cowbell,
                int airRunes,
                String mooleta,
                boolean altarRestore,
                String potion,
                boolean running) {
            this.runtime = runtime;
            this.kills = kills;
            this.killsPerHour = killsPerHour;
            this.state = state;
            this.action = action;
            this.instanceConfirmed = instanceConfirmed;
            this.clientInstance = clientInstance;
            this.travelPhase = travelPhase;
            this.bossStatus = bossStatus;
            this.combatMode = combatMode;
            this.training = training;
            this.hitpointsPercent = hitpointsPercent;
            this.prayerCurrent = prayerCurrent;
            this.prayerReal = prayerReal;
            this.prayerPlan = prayerPlan;
            this.activePrayers = activePrayers;
            this.special = special;
            this.brutusAnimation = brutusAnimation;
            this.brutusAnimationFrame = brutusAnimationFrame;
            this.specialTarget = specialTarget;
            this.reattackPending = reattackPending;
            this.foodName = foodName;
            this.foodCount = foodCount;
            this.foodTarget = foodTarget;
            this.inventorySlots = inventorySlots;
            this.cowbell = cowbell;
            this.airRunes = airRunes;
            this.mooleta = mooleta;
            this.altarRestore = altarRestore;
            this.potion = potion;
            this.running = running;
        }

        public String getRuntime() { return runtime; }
        public int getKills() { return kills; }
        public String getKillsPerHour() { return killsPerHour; }
        public KspMadCowState getState() { return state; }
        public String getAction() { return action; }
        public boolean isInstanceConfirmed() { return instanceConfirmed; }
        public boolean isClientInstance() { return clientInstance; }
        public String getTravelPhase() { return travelPhase; }
        public String getBossStatus() { return bossStatus; }
        public String getCombatMode() { return combatMode; }
        public String getTraining() { return training; }
        public int getHitpointsPercent() { return hitpointsPercent; }
        public int getPrayerCurrent() { return prayerCurrent; }
        public int getPrayerReal() { return prayerReal; }
        public String getPrayerPlan() { return prayerPlan; }
        public String getActivePrayers() { return activePrayers; }
        public String getSpecial() { return special; }
        public int getBrutusAnimation() { return brutusAnimation; }
        public int getBrutusAnimationFrame() { return brutusAnimationFrame; }
        public String getSpecialTarget() { return specialTarget; }
        public boolean isReattackPending() { return reattackPending; }
        public String getFoodName() { return foodName; }
        public int getFoodCount() { return foodCount; }
        public int getFoodTarget() { return foodTarget; }
        public int getInventorySlots() { return inventorySlots; }
        public String getCowbell() { return cowbell; }
        public int getAirRunes() { return airRunes; }
        public String getMooleta() { return mooleta; }
        public boolean isAltarRestore() { return altarRestore; }
        public String getPotion() { return potion; }
        public boolean isRunning() { return running; }

        private static OverlaySnapshot starting() {
            return new OverlaySnapshot(
                    "00:00:00", 0, "0.0", KspMadCowState.STARTING, "Starting",
                    false, false, "Restocking", "Outside instance", "Melee", "Attack",
                    0, 0, 0, "None", "None", "None", -1, -1, "None", false,
                    "Food", 0, 0, 0, "Unknown", 0, "Unknown", false, "Disabled", true
            );
        }

        private static OverlaySnapshot stopped() {
            return new OverlaySnapshot(
                    "00:00:00", 0, "0.0", KspMadCowState.STOPPED, "Stopped",
                    false, false, "Stopped", "Outside instance", "-", "-",
                    0, 0, 0, "-", "None", "None", -1, -1, "None", false,
                    "Food", 0, 0, 0, "-", 0, "-", false, "Disabled", false
            );
        }
    }

    private static final class AttackInvokeResult {
        private final boolean issued;
        private final int npcId;
        private final int npcIndex;
        private final AttackMenuResolution resolution;
        private final String failureReason;

        private AttackInvokeResult(
                boolean issued,
                int npcId,
                int npcIndex,
                AttackMenuResolution resolution,
                String failureReason
        ) {
            this.issued = issued;
            this.npcId = npcId;
            this.npcIndex = npcIndex;
            this.resolution = resolution == null ? AttackMenuResolution.NONE : resolution;
            this.failureReason = failureReason == null ? "" : failureReason;
        }

        private static AttackInvokeResult success(
                int npcId,
                int npcIndex,
                AttackMenuResolution resolution
        ) {
            return new AttackInvokeResult(true, npcId, npcIndex, resolution, "");
        }

        private static AttackInvokeResult failure(
                int npcId,
                int npcIndex,
                AttackMenuResolution resolution,
                String reason
        ) {
            return new AttackInvokeResult(false, npcId, npcIndex, resolution, reason);
        }
    }

    private static final class AttackMenuResolution {
        private static final AttackMenuResolution NONE =
                new AttackMenuResolution(-1, null, "unresolved");

        private final int optionIndex;
        private final MenuAction menuAction;
        private final String actionsSummary;

        private AttackMenuResolution(
                int optionIndex,
                MenuAction menuAction,
                String actionsSummary
        ) {
            this.optionIndex = optionIndex;
            this.menuAction = menuAction;
            this.actionsSummary = actionsSummary;
        }

        private boolean available() {
            return optionIndex >= 0 && menuAction != null;
        }
    }

    private static final class SceneWalkProjection {
        private static final SceneWalkProjection NONE =
                new SceneWalkProjection(null, null, null);

        private final LocalPoint localPoint;
        private final Point canvasPoint;
        private final WorldPoint worldViewBase;

        private SceneWalkProjection(
                LocalPoint localPoint,
                Point canvasPoint,
                WorldPoint worldViewBase
        ) {
            this.localPoint = localPoint;
            this.canvasPoint = canvasPoint;
            this.worldViewBase = worldViewBase;
        }

        private boolean available() {
            return localPoint != null && canvasPoint != null;
        }
    }

    private static final class SpecialSignal {
        private static final SpecialSignal NONE = new SpecialSignal(
                SpecialAttack.NONE, false, -1, null, -1
        );
        private final SpecialAttack attack;
        private final boolean fromAnimation;
        @SuppressWarnings("unused")
        private final int animation;
        private final WorldArea area;
        private final int orientation;

        private SpecialSignal(
                SpecialAttack attack,
                boolean fromAnimation,
                int animation,
                WorldArea area,
                int orientation
        ) {
            this.attack = attack;
            this.fromAnimation = fromAnimation;
            this.animation = animation;
            this.area = area;
            this.orientation = orientation;
        }
    }

    private static final class BrutusAnimationSnapshot {
        private static final BrutusAnimationSnapshot NONE =
                new BrutusAnimationSnapshot(-1, -1, null, -1);

        private final int animation;
        private final int frame;
        private final WorldArea area;
        private final int orientation;

        private BrutusAnimationSnapshot(
                int animation,
                int frame,
                WorldArea area,
                int orientation
        ) {
            this.animation = animation;
            this.frame = frame;
            this.area = area;
            this.orientation = orientation;
        }
    }

    private static final class ObjectAction {
        private final Rs2TileObjectModel object;
        private final String action;

        private ObjectAction(Rs2TileObjectModel object, String action) {
            this.object = object;
            this.action = action;
        }
    }

    private final class ScriptThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "KspMadCowScript-main");
            thread.setDaemon(true);
            return thread;
        }
    }
}
