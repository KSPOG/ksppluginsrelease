package net.runelite.client.plugins.microbot.kspseasonalevents;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 2026 Halloween entry stage for a reusable seasonal event runner.
 *
 * Only the live 2026 official entry (Angel/Henja in Rimmington) is verified.
 * The linked third-party video cannot be retrieved as a transcript in CI;
 * do not invent the post-introduction quest actions or randomly click nearby
 * scenery. Stop safely at an unverified task so the account stays in place.
 */
@Slf4j
@Singleton
public final class KspHalloween2026Script extends Script {
    private static final WorldArea RIMMINGTON = new WorldArea(2925, 3180, 85, 95, 0);
    private static final WorldPoint RIMMINGTON_APPROACH = new WorldPoint(2958, 3211, 0);
    private static final long ACTION_COOLDOWN_MS = 2_000L;
    private static final long WALK_RETRY_MS = 15_000L;
    private static final long INTRO_WAIT_MS = 10_000L;

    public enum Stage {
        STOPPED, TRAVEL, FIND_STARTER, INTRODUCTION, AWAIT_EVENT_STEP,
        COMPLETED, PAUSED
    }

    private volatile Stage stage = Stage.STOPPED;
    private volatile String status = "Stopped - press Start";
    private volatile boolean paused;
    private volatile boolean active;
    private volatile boolean introClicked;
    private volatile boolean verifiedComplete;
    private volatile long startedAt;
    private long lastActionAt;
    private long lastWalkAt;
    private long lastIntroAt;
    private long lastStatusAt;
    private volatile KspSeasonalEventsConfig config;

    public synchronized boolean start(KspSeasonalEventsConfig configuration) {
        if (active) return true;
        config = configuration;
        active = true;
        paused = false;
        introClicked = false;
        verifiedComplete = false;
        startedAt = System.currentTimeMillis();
        lastActionAt = 0L;
        lastWalkAt = 0L;
        lastIntroAt = 0L;
        change(Stage.TRAVEL, "Checking Halloween 2026 starting location");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::safeTick, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void safeTick() {
        if (!active || paused || verifiedComplete) return;
        try {
            tick();
        } catch (Exception exception) {
            log.warn("Halloween 2026 runner: stage={} failure", stage, exception);
            // Unknown game updates never trigger a blind retry cascade.
            change(Stage.AWAIT_EVENT_STEP, "Unexpected event interface: inspect logs");
        }
    }

    private void tick() {
        if (!Microbot.isLoggedIn()) {
            status = "Waiting for login";
            return;
        }
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            status = "Waiting for loaded player location";
            return;
        }
        if (stage == Stage.AWAIT_EVENT_STEP || stage == Stage.COMPLETED) return;

        if (Rs2Dialogue.hasContinue()) {
            if (ready()) {
                Rs2Dialogue.clickContinue();
                lastActionAt = System.currentTimeMillis();
                change(Stage.INTRODUCTION, "Advancing Halloween introduction");
            }
            return;
        }
        if (Rs2Dialogue.hasSelectAnOption()) {
            // Only confirm an explicit yes/agree choice. Unrecognized event
            // branches are not auto-selected without a verified walkthrough.
            if (ready() && (Rs2Dialogue.clickOption("Yes", true)
                    || Rs2Dialogue.clickOption("Sure", true)
                    || Rs2Dialogue.clickOption("I'd like to help", true))) {
                lastActionAt = System.currentTimeMillis();
                change(Stage.INTRODUCTION, "Accepted Halloween introduction choice");
            } else {
                change(Stage.AWAIT_EVENT_STEP,
                        "Unknown Halloween dialogue option - manual review needed");
            }
            return;
        }

        if (!introClicked && !RIMMINGTON.contains(player)) {
            change(Stage.TRAVEL, "Walking to Halloween starters in Rimmington");
            if (config != null && config.walkToEvent()
                    && System.currentTimeMillis() - lastWalkAt >= WALK_RETRY_MS
                    && !Rs2Player.isMoving()) {
                lastWalkAt = System.currentTimeMillis();
                Rs2Walker.walkTo(RIMMINGTON_APPROACH, 8);
            }
            if (config != null && !config.walkToEvent())
                status = "Move to Rimmington to begin Halloween 2026";
            return;
        }

        if (!introClicked) {
            change(Stage.FIND_STARTER, "Locating Angel or Henja in Rimmington");
            if (!ready()) return;
            if (Rs2Npc.getNpc("Henja") != null && Rs2Npc.interact("Henja", "Talk-to")
                    || Rs2Npc.getNpc("Angel") != null && Rs2Npc.interact("Angel", "Talk-to")) {
                introClicked = true;
                lastIntroAt = System.currentTimeMillis();
                lastActionAt = lastIntroAt;
                change(Stage.INTRODUCTION, "Speaking to Halloween event starter");
            } else if (System.currentTimeMillis() - lastStatusAt >= 8_000L) {
                lastStatusAt = System.currentTimeMillis();
                log.info("Halloween starter not in scene | player={} expected=Angel/Henja", player);
            }
            return;
        }

        if (System.currentTimeMillis() - lastIntroAt >= INTRO_WAIT_MS) {
            change(Stage.AWAIT_EVENT_STEP,
                    "Introduction reached; next 2026 event stage unverified - no blind clicks");
        } else {
            status = "Waiting for Halloween event dialogue / transition";
        }
    }

    /** Only server-confirmed completion dialogue may mark rewards finished. */
    public void onChatMessage(String message) {
        if (!active || message == null) return;
        String m = message.replaceAll("<[^>]*>", "").toLowerCase(Locale.ROOT);
        if (m.contains("you have completed the 2026 halloween event")
                || m.contains("you have completed the halloween event")) {
            verifiedComplete = true;
            change(Stage.COMPLETED, "Halloween 2026 completion confirmed by game");
            Rs2Walker.clearWalkingRoute("ksp_seasonal_halloween_completed");
        }
    }

    private boolean ready() {
        return System.currentTimeMillis() - lastActionAt >= ACTION_COOLDOWN_MS;
    }

    private void change(Stage next, String label) {
        if (next != stage) log.info("KSP Seasonal Events Halloween | {} -> {} | {}", stage, next, label);
        stage = next;
        status = label;
    }

    public void setPaused(boolean pause) {
        if (!active) return;
        paused = pause;
        if (pause) {
            stage = Stage.PAUSED;
            status = "Paused";
            Rs2Walker.clearWalkingRoute("ksp_seasonal_pause");
        } else {
            stage = introClicked ? Stage.AWAIT_EVENT_STEP : Stage.TRAVEL;
            status = introClicked ? "Review unverified Halloween stage" : "Resuming Halloween entry";
        }
    }

    public boolean isActive() { return active; }
    public boolean isPaused() { return paused; }
    public String getStatus() { return status; }
    public Stage getStage() { return stage; }
    public long getRuntimeMs() { return startedAt == 0 ? 0L : System.currentTimeMillis() - startedAt; }

    @Override
    public synchronized void shutdown() {
        active = false;
        paused = false;
        if (stage == Stage.TRAVEL)
            Rs2Walker.clearWalkingRoute("ksp_seasonal_stopped");
        super.shutdown();
        stage = Stage.STOPPED;
        status = "Stopped - press Start";
        introClicked = false;
    }
}
