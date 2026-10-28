package net.runelite.client.plugins.microbot.kspseasonalevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Old School RuneScape's 2026 "All Trick, No Treats" event.
 *
 * Walkthrough: https://oldschool.runescape.wiki/w/2026_Halloween_event
 * Progress is driven by visible scene objects, inventory transitions and
 * NPC actions, not by presumed item/object IDs or a fixed stream of clicks.
 *
 * The wiki documents the sequence but not all menu strings, runtime IDs and
 * quest varbits. Unrecognised UI is logged and awaits review instead of
 * guessing an action. This class is independent of KSP Chopper.
 */
@Slf4j
@Singleton
public final class KspHalloween2026Script extends Script {
    private static final String GROUP = "KspSeasonalEvents";
    private static final String STAGE_KEY = "halloweenStage";
    private static final String LANTERNS_KEY = "halloweenSmashTiles";
    private static final String PENDING_LANTERN_KEY = "halloweenPendingSmashTile";
    private static final String LANTERNS_VERIFIED_KEY = "halloweenLanternsVerified";
    private static final String ADULTS_KEY = "halloweenAdultsGiven";
    private static final WorldArea RIMMINGTON = new WorldArea(2925, 3180, 85, 95, 0);
    private static final WorldPoint STARTER = new WorldPoint(2958, 3211, 0);
    private static final WorldPoint DIANGO = new WorldPoint(3081, 3248, 0);
    private static final WorldPoint CHEMIST = new WorldPoint(2930, 3211, 0);

    // Discovery waypoints, NOT claimed spawn coordinates. The real object
    // must be present in the live cache and expose the required menu action.
    private static final WorldPoint[] RIMMINGTON_SWEEP = {
        new WorldPoint(2957, 3212, 0), new WorldPoint(2969, 3218, 0),
        new WorldPoint(2984, 3229, 0), new WorldPoint(2994, 3212, 0),
        new WorldPoint(2970, 3193, 0), new WorldPoint(2948, 3198, 0),
        new WorldPoint(2941, 3230, 0), new WorldPoint(2970, 3250, 0)
    };
    private static final long ACTION_GAP = 1_400L;
    private static final long WALK_RETRY = 10_000L;
    private static final String[] COSTUME_PIECES = {
        "Skeleton mask", "Skeleton shirt", "Skeleton leggings", "Skeleton boots",
        "Skeleton gloves", "Grim reaper hood", "Grim reaper top",
        "Grim reaper bottoms", "Grim reaper gloves",
        "Zombie head", "Zombie shirt", "Zombie trousers",
        "Zombie boots", "Zombie gloves", "Scythe",
        "Spooky pumpkin lantern", "Halloween scarecrow", "Jack lantern mask",
        "Black h'ween mask", "Red halloween mask", "Blue halloween mask",
        "Green halloween mask", "Ghostly hood", "Ghostly robe"
    };

    public enum Stage {
        STOPPED,
        INTRODUCTION,
        SMASH_LANTERNS,
        VERIFY_LANTERNS,
        REPORT_LANTERNS,
        GET_COSTUME,
        SPOOK_CHILDREN,
        REPORT_CHILDREN,
        CHEMIST_RIDDLE,
        COOK_SUGAR,
        COLLECT_ONIONS,
        COAT_ONIONS,
        GIVE_CANDY,
        FINAL_TALK,
        WAIT_FOR_REWARD,
        COMPLETED,
        NEEDS_REVIEW,
        PAUSED
    }

    @Inject private ConfigManager configManager;
    private volatile KspSeasonalEventsConfig config;
    private volatile Stage stage = Stage.STOPPED;
    private volatile Stage beforePause = Stage.STOPPED;
    private volatile String status = "Stopped - press Start";
    private volatile boolean active;
    private volatile boolean paused;
    private volatile long startedAt;

    private final Set<String> smashedLanternTiles = new HashSet<>();
    private volatile boolean lanternsVerified;
    private final Set<Integer> spookedChildren = new HashSet<>();
    private final Set<Integer> givenAdultIndexes = new HashSet<>();
    private int adultsGiven;
    private int sweepIndex;
    private int sweepsWithoutTarget;
    private int actionsAtStage;
    private long lastActionAt;
    private long lastWalkAt;
    private long lastTalkAt;
    private long lastDiagnosticAt;
    private WorldPoint pendingLantern;
    private long pendingLanternAt;
    private long lastVerificationTalkAt;
    private int verificationTalkAttempts;
    private boolean verificationDialogueSeen;
    private int pendingCandyCount = -1;
    private int pendingAdultIndex = -1;

    public synchronized boolean start(KspSeasonalEventsConfig configuration) {
        if (active) return true;
        this.config = configuration;
        this.active = true;
        this.paused = false;
        this.startedAt = System.currentTimeMillis();
        lastActionAt = lastWalkAt = lastTalkAt = lastDiagnosticAt = 0L;
        pendingLantern = null;
        pendingLanternAt = 0L;
        lastVerificationTalkAt = 0L;
        verificationTalkAttempts = 0;
        verificationDialogueSeen = false;
        pendingCandyCount = -1;
        spookedChildren.clear();
        givenAdultIndexes.clear();
        sweepIndex = 0;
        sweepsWithoutTarget = 0;
        actionsAtStage = 0;

        smashedLanternTiles.clear();
        String savedTiles = configManager.getConfiguration(GROUP, LANTERNS_KEY);
        if (savedTiles != null && !savedTiles.isBlank())
            smashedLanternTiles.addAll(Arrays.asList(savedTiles.split(",")));
        lanternsVerified = Boolean.parseBoolean(
                configManager.getConfiguration(GROUP, LANTERNS_VERIFIED_KEY));
        pendingLantern = parseLanternTile(configManager.getConfiguration(GROUP, PENDING_LANTERN_KEY));
        // A click recorded before a pause/restart must be checked on location,
        // not forgotten; there is no guaranteed game-level counter in this API.
        pendingLanternAt = 0L;
        String savedAdults = configManager.getConfiguration(GROUP, ADULTS_KEY);
        try { adultsGiven = savedAdults == null ? 0 : Integer.parseInt(savedAdults); }
        catch (NumberFormatException e) { adultsGiven = 0; }

        Stage saved = savedStage();
        if (lanternsVerified && (saved == Stage.SMASH_LANTERNS
                || saved == Stage.VERIFY_LANTERNS))
            saved = Stage.REPORT_LANTERNS;
        change(saved == Stage.STOPPED || saved == Stage.PAUSED || saved == Stage.NEEDS_REVIEW
                ? Stage.INTRODUCTION : saved, "Resuming Halloween 2026 walkthrough");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::safeTick, 0L, 600L, TimeUnit.MILLISECONDS);
        log.info("Halloween 2026 started | stage={} lanterns={}/8 adultsGiven={}",
                stage, getLanternCount(), adultsGiven);
        return true;
    }

    private Stage savedStage() {
        String value = configManager.getConfiguration(GROUP, STAGE_KEY);
        if (value != null) {
            try { return Stage.valueOf(value); }
            catch (IllegalArgumentException ignored) { /* old version or corrupt config */ }
        }
        return Stage.INTRODUCTION;
    }

    private void safeTick() {
        if (!active || paused) return;
        try { tick(); }
        catch (Exception e) {
            log.warn("Halloween stage={} player={} failed", stage, Rs2Player.getWorldLocation(), e);
            stopForReview("Event API or interface changed; inspect client logs");
        }
    }

    private void tick() {
        if (!Microbot.isLoggedIn()) { status = "Waiting for login"; return; }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) { status = "Waiting for player location"; return; }
        if (stage == Stage.COMPLETED || stage == Stage.NEEDS_REVIEW) return;

        if (stage == Stage.VERIFY_LANTERNS
                && (Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption()))
            inspectLanternVerificationDialogue();

        if (Rs2Dialogue.hasContinue()) {
            if (ready()) {
                Rs2Dialogue.clickContinue();
                lastActionAt = System.currentTimeMillis();
                status = "Advancing " + stage + " dialogue";
            }
            return;
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            handleDialogueOption();
            return;
        }

        switch (stage) {
            case INTRODUCTION:
                talkToStarter(player, Stage.SMASH_LANTERNS);
                break;
            case SMASH_LANTERNS:
                smashLanterns(player);
                break;
            case VERIFY_LANTERNS:
                verifyLanternsWithStarter(player);
                break;
            case REPORT_LANTERNS:
                talkToStarter(player, Stage.GET_COSTUME);
                break;
            case GET_COSTUME:
                collectCostume(player);
                break;
            case SPOOK_CHILDREN:
                spookChildren(player);
                break;
            case REPORT_CHILDREN:
                talkToStarter(player, Stage.CHEMIST_RIDDLE);
                break;
            case CHEMIST_RIDDLE:
                solveChemist(player);
                break;
            case COOK_SUGAR:
                cookSugar(player);
                break;
            case COLLECT_ONIONS:
                collectOnions(player);
                break;
            case COAT_ONIONS:
                coatOnions();
                break;
            case GIVE_CANDY:
                giveCandy(player);
                break;
            case FINAL_TALK:
                talkToStarter(player, Stage.WAIT_FOR_REWARD);
                break;
            case WAIT_FOR_REWARD:
                if (Rs2Inventory.hasItem("Vampyre costume top")
                        || Rs2Equipment.isWearing("Vampyre costume top", true)) {
                    change(Stage.COMPLETED, "Vampyre event reward confirmed in inventory/equipment");
                } else {
                    status = "Waiting for Halloween final cutscene and reward confirmation";
                }
                break;
            default:
                break;
        }
    }

    private void handleDialogueOption() {
        if (!ready()) return;
        // Enumerate unknown choices for accurate follow-up debugging.
        List<String> options = Rs2Dialogue.getDialogueOptions().stream()
                .map(w -> w == null ? "" : w.getText())
                .collect(Collectors.toList());
        if (stage == Stage.CHEMIST_RIDDLE && Rs2Dialogue.clickOption("92")) {
            acted("Answered Chemist's COFFIN riddle: 92");
            return;
        }
        String[] choices = {
            "Yes", "Sure", "I'd like to help", "Of course", "Let's do it",
            "I'll help", "I can help", "Okay", "Halloween", "Trick or treat"
        };
        for (String choice : choices) {
            if (Rs2Dialogue.clickOption(choice, true)) {
                acted("Selected confirmed event dialogue: " + choice);
                return;
            }
        }
        if (System.currentTimeMillis() - lastDiagnosticAt > 5_000L) {
            lastDiagnosticAt = System.currentTimeMillis();
            log.warn("Halloween dialogue needs review | stage={} options={}", stage, options);
        }
        status = "Unknown " + stage + " dialogue options; see logs (no random selection)";
    }

    private boolean nearOrWalk(WorldPoint player, WorldPoint destination,
                               int distance, String purpose) {
        if (player.distanceTo(destination) <= distance) return true;
        status = "Walking to " + purpose;
        if (config == null || !config.walkToEvent()) {
            status = "Travel disabled: move toward " + purpose;
            return false;
        }
        if (!Rs2Player.isMoving() && System.currentTimeMillis() - lastWalkAt >= WALK_RETRY) {
            lastWalkAt = System.currentTimeMillis();
            log.info("Halloween travel | stage={} player={} target={}", stage, player, destination);
            Rs2Walker.walkTo(destination, distance);
        }
        return false;
    }

    private void talkToStarter(WorldPoint player, Stage next) {
        if (!RIMMINGTON.contains(player) && !nearOrWalk(player, STARTER, 10, "Angel / Henja"))
            return;
        if (!ready()) return;
        boolean clicked = Rs2Npc.getNpc("Henja") != null
                && Rs2Npc.interact("Henja", "Talk-to");
        if (!clicked) clicked = Rs2Npc.getNpc("Angel") != null
                && Rs2Npc.interact("Angel", "Talk-to");
        if (!clicked) {
            status = "Locating Angel / Henja in their Rimmington home";
            return;
        }
        acted("Speaking to Angel / Henja");
        lastTalkAt = System.currentTimeMillis();
        // Do not block on a fixed, unverified cutscene duration. All later
        // stages use confirmed actions/scene state rather than blind clicks.
        change(next, "Talked to Angel / Henja; next: " + next);
    }

    private Rs2TileObjectModel lantern() {
        return Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> o != null && o.getName() != null
                        && o.getName().toLowerCase(Locale.ROOT).contains("jack")
                        && o.getName().toLowerCase(Locale.ROOT).contains("lantern")
                        && o.hasActionContaining("Smash")
                        && !smashedLanternTiles.contains(tileKey(o.getWorldLocation())))
                .nearestOnClientThread();
    }

    private static String tileKey(WorldPoint point) {
        return point.getX() + ":" + point.getY() + ":" + point.getPlane();
    }

    private static WorldPoint parseLanternTile(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = value.split(":");
            if (parts.length != 3) return null;
            WorldPoint tile = new WorldPoint(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            return RIMMINGTON.contains(tile) ? tile : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void clearPendingLantern() {
        pendingLantern = null;
        pendingLanternAt = 0L;
        configManager.unsetConfiguration(GROUP, PENDING_LANTERN_KEY);
    }

    private void confirmSmashedLantern(WorldPoint tile, String evidence) {
        if (tile == null) return;
        if (smashedLanternTiles.add(tileKey(tile))) {
            configManager.setConfiguration(GROUP, LANTERNS_KEY,
                    String.join(",", smashedLanternTiles));
            log.info("Halloween lantern smash confirmed | tile={} evidence={} count={}/8",
                    tile, evidence, getLanternCount());
        }
        clearPendingLantern();
    }

    private void confirmAllLanterns(String evidence) {
        lanternsVerified = true;
        clearPendingLantern();
        configManager.setConfiguration(GROUP, LANTERNS_VERIFIED_KEY, true);
        log.info("Halloween all eight lanterns verified | evidence={} recordedTiles={}",
                evidence, smashedLanternTiles.size());
    }

    private boolean pendingLanternConfirmed(WorldPoint player) {
        if (pendingLantern == null) return false;
        // Never treat a missing object as smashed when its tile is out of
        // scene range. Revisit the exact clicked tile after any interruption.
        if (player.distanceTo(pendingLantern) > 9) {
            nearOrWalk(player, pendingLantern, 6, "confirm clicked jack-o'-lantern");
            return true;
        }
        if (pendingLanternAt != 0L
                && System.currentTimeMillis() - pendingLanternAt < 1_100L) return true;
        final WorldPoint clickedTile = pendingLantern;
        boolean stillPresent = Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> o != null && clickedTile.equals(o.getWorldLocation())
                        && o.getName() != null
                        && o.getName().toLowerCase(Locale.ROOT).contains("lantern")
                        && o.hasActionContaining("Smash"))
                .nearestOnClientThread() != null;
        if (!stillPresent) {
            confirmSmashedLantern(clickedTile, "smash action disappeared at loaded tile");
            return false;
        }
        if (pendingLanternAt == 0L
                || System.currentTimeMillis() - pendingLanternAt > 7_500L) {
            log.info("Halloween smash still available | tile={} retry allowed", clickedTile);
            clearPendingLantern(); // Only retry a verified still-smashable object.
            return false;
        }
        return true;
    }

    private void smashLanterns(WorldPoint player) {
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 12, "Rimmington lanterns")) return;
        if (pendingLanternConfirmed(player)) return;
        if (lanternsVerified || smashedLanternTiles.size() >= 8) {
            confirmAllLanterns("eight unique smashed locations");
            change(Stage.REPORT_LANTERNS, "All eight lanterns confirmed; return to children");
            return;
        }
        Rs2TileObjectModel target = lantern();
        if (target != null) {
            WorldPoint location = target.getWorldLocation();
            status = "Smashing jack-o'-lantern " + (smashedLanternTiles.size() + 1) + "/8";
            if (ready() && target.click(target.findAction("Smash"))) {
                pendingLantern = location;
                pendingLanternAt = System.currentTimeMillis();
                configManager.setConfiguration(GROUP, PENDING_LANTERN_KEY, tileKey(location));
                lastActionAt = pendingLanternAt;
                log.info("Halloween smash click | id={} tile={}", target.getId(), location);
            }
            return;
        }
        // At 7/8, a previous session may have missed the eighth
        // acknowledgment even though the game accepted the final smash.
        // One complete local discovery sweep is enough before asking the
        // event starter for authoritative progression instead of looping.
        if (sweepsWithoutTarget >= RIMMINGTON_SWEEP.length) {
            verificationTalkAttempts = 0;
            lastVerificationTalkAt = 0L;
            verificationDialogueSeen = false;
            change(Stage.VERIFY_LANTERNS,
                    "No smashable lantern in full Rimmington sweep; checking with Angel/Henja");
            return;
        }
        scanRimmington(player, "Searching for remaining jack-o'-lanterns");
    }

    private void scanRimmington(WorldPoint player, String purpose) {
        if (sweepsWithoutTarget >= 24) {
            stopForReview("Cannot locate remaining " + stage
                    + " targets in Rimmington; check logs/NPC state");
            return;
        }
        WorldPoint next = RIMMINGTON_SWEEP[sweepIndex % RIMMINGTON_SWEEP.length];
        if (player.distanceTo(next) <= 7) {
            sweepIndex++;
            sweepsWithoutTarget++;
            if (stage == Stage.SMASH_LANTERNS
                    && sweepsWithoutTarget >= RIMMINGTON_SWEEP.length) {
                change(Stage.VERIFY_LANTERNS,
                        "Sweep exhausted; checking Halloween lantern progress with children");
                return;
            }
            next = RIMMINGTON_SWEEP[sweepIndex % RIMMINGTON_SWEEP.length];
        }
        status = purpose + " (" + (sweepIndex % RIMMINGTON_SWEEP.length + 1) + "/8 areas)";
        nearOrWalk(player, next, 6, purpose);
    }

    private int costumeCount() {
        int equipped = 0;
        for (String name : COSTUME_PIECES) {
            if (Rs2Equipment.isWearing(name, true)) equipped++;
        }
        return equipped;
    }

    private void collectCostume(WorldPoint player) {
        int equipped = costumeCount();
        if (equipped >= 4) {
            change(Stage.SPOOK_CHILDREN, "Four Halloween outfit pieces equipped");
            return;
        }
        if (!nearOrWalk(player, DIANGO, 8, "Diango in Draynor Village")) return;
        for (String piece : COSTUME_PIECES) {
            if (Rs2Inventory.hasItem(piece) && !Rs2Equipment.isWearing(piece, true) && ready()) {
                Rs2Inventory.wear(piece);
                acted("Equipping Halloween costume: " + piece);
                return;
            }
        }
        if (ready() && Rs2Npc.getNpc("Diango") != null
                && System.currentTimeMillis() - lastTalkAt > 15_000L) {
            if (Rs2Npc.interact("Diango", "Talk-to")) {
                lastTalkAt = System.currentTimeMillis();
                acted("Requesting previous Halloween rewards from Diango");
            }
        }
        // The wiki specifies *where* to get the items but not Diango's
        // current reward interface widget IDs. Do not blindly buy/select.
        status = "Equip 4 Halloween items from Diango (equipped: " + equipped + "/4)";
    }

    private boolean npcHasAction(Rs2NpcModel npc, String action) {
        if (npc == null) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null) return false;
            net.runelite.api.NPCComposition def =
                    Microbot.getClient().getNpcDefinition(npc.getId());
            if (def == null || def.getActions() == null) return false;
            return Arrays.stream(def.getActions()).anyMatch(a ->
                    a != null && a.equalsIgnoreCase(action));
        }).orElse(false);
    }

    private void spookChildren(WorldPoint player) {
        if (costumeCount() < 4) {
            change(Stage.GET_COSTUME, "Need four Halloween outfit pieces equipped");
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 12, "child trick-or-treaters")) return;
        Rs2NpcModel child = Rs2Npc.getNpcs().filter(n -> n.getName() != null
                && n.getName().toLowerCase(Locale.ROOT).contains("trick")
                && npcHasAction(n, "Spook")
                && !spookedChildren.contains(n.getIndex())).findFirst().orElse(null);
        if (child != null) {
            if (ready() && Rs2Npc.interact(child, "Spook")) {
                spookedChildren.add(child.getIndex());
                acted("Spooking child trick-or-treater npcId=" + child.getId()
                        + " index=" + child.getIndex());
                sweepsWithoutTarget = 0;
            }
            return;
        }
        if (sweepsWithoutTarget >= RIMMINGTON_SWEEP.length) {
            change(Stage.REPORT_CHILDREN, "No remaining Spook targets seen; check with Angel/Henja");
            return;
        }
        scanRimmington(player, "Looking for child trick-or-treaters");
    }

    private void solveChemist(WorldPoint player) {
        if (Rs2Inventory.hasItem("Sugar", true)
                || Rs2Inventory.hasItem("Molten sugar")) {
            change(Stage.COOK_SUGAR, "Chemist sugar acquired");
            return;
        }
        if (!nearOrWalk(player, CHEMIST, 8, "the Chemist west of Rimmington")) return;
        if (ready() && Rs2Npc.getNpc("Chemist") != null
                && System.currentTimeMillis() - lastTalkAt > 6_000L
                && Rs2Npc.interact("Chemist", "Talk-to")) {
            lastTalkAt = System.currentTimeMillis();
            acted("Talking to Chemist; COFFIN riddle answer is 92");
        } else {
            status = "Chemist puzzle: select answer 92";
        }
    }

    private Rs2TileObjectModel cookingSurface() {
        return Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> o != null && o.getName() != null
                        && (o.getName().equalsIgnoreCase("Range")
                            || o.getName().equalsIgnoreCase("Fireplace"))
                        && (o.hasActionContaining("Cook") || o.hasActionContaining("Use")))
                .nearestOnClientThread();
    }

    private void cookSugar(WorldPoint player) {
        if (Rs2Inventory.hasItem("Molten sugar")) {
            change(Stage.COLLECT_ONIONS, "Molten sugar ready");
            return;
        }
        if (!Rs2Inventory.hasItem("Sugar", true)) {
            change(Stage.CHEMIST_RIDDLE, "Need sugar from Chemist");
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, CHEMIST, 8, "Rimmington range / fireplace")) return;
        Rs2TileObjectModel surface = cookingSurface();
        if (surface == null) {
            status = "Locating range in children's house or Chemist fireplace";
            return;
        }
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel sugar =
                Rs2Inventory.get("Sugar", true);
        if (sugar != null && ready()
                && Rs2Inventory.useItemOnObject(sugar.getId(), surface.getId())) {
            acted("Cooking sugar at " + surface.getName());
        }
    }

    private void collectOnions(WorldPoint player) {
        if (Rs2Inventory.count("Old onion") >= 5) {
            change(Stage.COAT_ONIONS, "Five event-only old onions collected");
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 8, "old onion crate behind children's house"))
            return;
        Rs2TileObjectModel crate = Microbot.getRs2TileObjectCache().query().fromWorldView()
                .where(o -> o != null && o.getName() != null
                        && o.getName().toLowerCase(Locale.ROOT).contains("onion")
                        && (o.hasActionContaining("Take") || o.hasActionContaining("Search")
                            || o.hasActionContaining("Collect")))
                .nearestOnClientThread();
        if (crate != null && ready()) {
            String action = crate.findAction("Take");
            if (action == null) action = crate.findAction("Search");
            if (action == null) action = crate.findAction("Collect");
            if (action != null && crate.click(action)) acted("Collecting OLD onion (not regular onion)");
        } else {
            status = "Locating the OLD onion crate behind Angel/Henja's house";
        }
    }

    private void coatOnions() {
        int candy = Rs2Inventory.count("Candy onion");
        int old = Rs2Inventory.count("Old onion");
        if (candy >= 5) {
            change(Stage.GIVE_CANDY, "Five candy onions ready for adults");
            return;
        }
        if (old <= 0) {
            change(Stage.COLLECT_ONIONS, "Need remaining old onions");
            return;
        }
        if (!Rs2Inventory.hasItem("Molten sugar")) {
            change(Stage.COOK_SUGAR, "Need more molten sugar");
            return;
        }
        if (ready() && Rs2Inventory.combine("Molten sugar", "Old onion"))
            acted("Coating old onion with molten sugar (" + candy + "/5 prepared)");
    }

    private void giveCandy(WorldPoint player) {
        int candyCount = Rs2Inventory.count("Candy onion");
        if (pendingCandyCount >= 0) {
            if (System.currentTimeMillis() - lastActionAt >= 1_200L) {
                if (candyCount < pendingCandyCount) {
                    int used = pendingCandyCount - candyCount;
                    adultsGiven += used;
                    configManager.setConfiguration(GROUP, ADULTS_KEY, adultsGiven);
                    givenAdultIndexes.add(pendingAdultIndex);
                    log.info("Halloween candy onions delivered | total={} remaining={}",
                            adultsGiven, candyCount);
                }
                pendingCandyCount = -1;
            } else return;
        }
        if (adultsGiven >= 5) {
            change(Stage.FINAL_TALK, "Five adult trick-or-treaters served");
            return;
        }
        if (candyCount <= 0) {
            change(Stage.COAT_ONIONS, "Prepare more candy onions for adults");
            return;
        }
        if (!RIMMINGTON.contains(player)
                && !nearOrWalk(player, STARTER, 10, "adult trick-or-treaters")) return;
        Rs2NpcModel adult = Rs2Npc.getNpcs().filter(n -> n.getName() != null
                && n.getName().toLowerCase(Locale.ROOT).contains("trick")
                && !npcHasAction(n, "Spook")
                && !givenAdultIndexes.contains(n.getIndex())).findFirst().orElse(null);
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel item =
                Rs2Inventory.get("Candy onion");
        if (adult != null && item != null) {
            if (ready() && Rs2Inventory.useItemOnNpc(item.getId(), adult)) {
                pendingCandyCount = candyCount;
                pendingAdultIndex = adult.getIndex();
                acted("Offering candy onion to adult npcId=" + adult.getId());
                sweepsWithoutTarget = 0;
            }
            return;
        }
        scanRimmington(player, "Looking for adult trick-or-treaters");
    }

    private void acted(String message) {
        lastActionAt = System.currentTimeMillis();
        status = message;
        log.info("Halloween action | stage={} | {}", stage, message);
    }

    private boolean ready() {
        return System.currentTimeMillis() - lastActionAt >= ACTION_GAP;
    }

    private void change(Stage next, String message) {
        Stage previous = stage;
        stage = next;
        status = message;
        if (previous != next) {
            log.info("KSP Seasonal Events Halloween | {} -> {} | {}", previous, next, message);
            if (next != Stage.STOPPED && next != Stage.PAUSED && next != Stage.NEEDS_REVIEW)
                configManager.setConfiguration(GROUP, STAGE_KEY, next.name());
            actionsAtStage = 0;
            sweepsWithoutTarget = 0;
            sweepIndex = 0;
        }
    }

    private void stopForReview(String message) {
        log.warn("Halloween review required | stage={} status={} player={} inventory(sugar/old/candy)={}/{}/{}",
                stage, message, Rs2Player.getWorldLocation(), Rs2Inventory.count("Sugar"),
                Rs2Inventory.count("Old onion"), Rs2Inventory.count("Candy onion"));
        beforePause = stage;
        stage = Stage.NEEDS_REVIEW;
        status = message;
    }

    public void onChatMessage(String raw) {
        if (!active || raw == null) return;
        String text = raw.replaceAll("<[^>]*>", "").toLowerCase(Locale.ROOT);
        if (text.contains("you have completed the halloween event")
                || text.contains("you have completed the 2026 halloween event")) {
            change(Stage.COMPLETED, "Halloween completion confirmed by game");
            return;
        }
        if (text.contains("jack") || text.contains("onion")
                || text.contains("trick-or-treat") || text.contains("sugar")
                || text.contains("halloween")) {
            log.info("Halloween game message | stage={} | {}", stage, text);
        }
    }

    public void setPaused(boolean value) {
        if (!active) return;
        if (value) {
            beforePause = stage;
            paused = true;
            stage = Stage.PAUSED;
            status = "Paused (progress preserved)";
        } else {
            paused = false;
            stage = beforePause == Stage.PAUSED ? savedStage() : beforePause;
            status = "Resuming " + stage;
        }
    }

    /** Intentional explicit reset; never silently reset progress on client restart. */
    public synchronized void resetProgress() {
        if (active) return;
        configManager.unsetConfiguration(GROUP, STAGE_KEY);
        configManager.unsetConfiguration(GROUP, LANTERNS_KEY);
        configManager.unsetConfiguration(GROUP, ADULTS_KEY);
        smashedLanternTiles.clear();
        spookedChildren.clear();
        givenAdultIndexes.clear();
        adultsGiven = 0;
        stage = Stage.STOPPED;
        status = "Progress reset; press Start";
    }

    public boolean isActive() { return active; }
    public boolean isPaused() { return paused; }
    public String getStatus() { return status; }
    public Stage getStage() { return stage; }
    public int getLanternCount() { return smashedLanternTiles.size(); }
    public int getAdultsGiven() { return adultsGiven; }
    public long getRuntimeMs() { return startedAt == 0L ? 0L :
            System.currentTimeMillis() - startedAt; }

    @Override
    public synchronized void shutdown() {
        active = false;
        paused = false;
        super.shutdown();
        stage = Stage.STOPPED;
        status = "Stopped - press Start";
    }
}
