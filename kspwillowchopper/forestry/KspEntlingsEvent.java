package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspForestryEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Friendly Ent / Entling Forestry event.
 *
 * One Entling is hard-locked until that exact NPC morphs/despawns. The locked
 * Entling's CURRENT overhead request is read before every prune. Combined
 * requests do not require alternating cuts: they simply permit more than one
 * valid prune action. We deliberately use one deterministic valid action for
 * each request until the Entling is finished.
 */
@Slf4j
public class KspEntlingsEvent implements BlockingEvent {
    private static final int REGULAR_ENTLING_ID = NpcID.GATHERING_EVENT_ENTLINGS_NPC_01;

    // Slightly longer than one 600 ms game tick, preventing duplicate menu
    // emissions while still allowing the next legitimate prune immediately.
    private static final long MIN_PRUNE_INTERVAL_MS = 700L;
    private static final long FAILED_CLICK_RETRY_MS = 1_200L;

    private final KspWillowChopperPlugin plugin;
    private final Map<Integer, EntlingProgress> progressByIndex = new HashMap<>();

    public KspEntlingsEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        if (!Microbot.isPluginEnabled(plugin) || !Microbot.isLoggedIn()) {
            return false;
        }
        return !getRegularEntlings().isEmpty();
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.FRIENDLY_ENTLINGS);
        progressByIndex.clear();

        if (!plugin.ensureInventorySpace(2)) {
            return true;
        }

        int lockedEntlingIndex = -1;

        while (validate()) {
            var entlings = getRegularEntlings();
            Rs2NpcModel entling;

            if (lockedEntlingIndex >= 0) {
                final int targetIndex = lockedEntlingIndex;
                entling = entlings.stream()
                        .filter(Objects::nonNull)
                        .filter(candidate -> candidate.getIndex() == targetIndex)
                        .findFirst()
                        .orElse(null);

                // The locked Entling left NPC_01: it either completed/morphed or
                // despawned. Only now may a different Entling be acquired.
                if (entling == null) {
                    progressByIndex.remove(lockedEntlingIndex);
                    log.debug("Entlings: released completed/despawned npc={}", lockedEntlingIndex);
                    lockedEntlingIndex = -1;
                    sleep(80);
                    continue;
                }
            } else {
                entling = entlings.stream()
                        .filter(Objects::nonNull)
                        .filter(candidate -> candidate.getId() == REGULAR_ENTLING_ID)
                        .min(Comparator.comparingInt(candidate ->
                                candidate.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                        .orElse(null);

                if (entling == null) {
                    sleep(100);
                    continue;
                }

                lockedEntlingIndex = entling.getIndex();
                log.debug("Entlings: hard-locked npc={}", lockedEntlingIndex);
            }

            final int npcIndex = entling.getIndex();
            final String request = normalizeRequest(entling.getOverheadText());
            final String action = actionForRequest(request);

            // A temporary blank/countdown/satisfaction overhead must never cause
            // the handler to switch Entlings or guess a prune action.
            if (action == null) {
                sleep(100);
                continue;
            }

            EntlingProgress progress = progressByIndex.computeIfAbsent(
                    npcIndex, ignored -> new EntlingProgress());

            if (!Objects.equals(progress.lastObservedRequest, request)) {
                progress.lastObservedRequest = request;
                log.info("Entlings: npc={} request='{}' -> {}", npcIndex, request, action);
            }

            if (!canPruneNow(progress)) {
                sleep(60);
                continue;
            }

            // Re-read immediately before clicking so a server-side overhead
            // update cannot leave us using a stale request/action pair.
            final String requestAtClick = normalizeRequest(entling.getOverheadText());
            final String actionAtClick = actionForRequest(requestAtClick);
            if (actionAtClick == null) {
                sleep(80);
                continue;
            }

            if (!Objects.equals(request, requestAtClick)) {
                progress.lastObservedRequest = requestAtClick;
                log.info("Entlings: npc={} request changed '{}' -> '{}' -> {}",
                        npcIndex, request, requestAtClick, actionAtClick);
            }

            long targetKey = entlingTargetKey(entling);
            int idBefore = entling.getId();
            long now = System.currentTimeMillis();

            log.debug("Entlings: npc={} request='{}' action='{}' prune={}",
                    npcIndex, requestAtClick, actionAtClick, progress.successfulClicks + 1);

            boolean clicked = entling.click(actionAtClick);
            progress.lastAttemptMillis = now;

            if (!clicked) {
                progress.lastFailureMillis = now;
                sleep(80);
                continue;
            }

            plugin.markForestryInteraction(targetKey, actionAtClick);
            progress.successfulClicks++;

            final Rs2NpcModel lockedEntling = entling;
            final String acknowledgedRequest = requestAtClick;

            // Wait for either the pruning animation, a request change, or the
            // Entling morphing out of the regular state. Player#getInteracting()
            // is deliberately not used because it may stay latched after a cut.
            sleepUntil(() ->
                            lockedEntling.getId() != idBefore
                            || !Objects.equals(acknowledgedRequest,
                                    normalizeRequest(lockedEntling.getOverheadText()))
                            || Rs2Player.isAnimating(),
                    1_800);

            if (lockedEntling.getId() == REGULAR_ENTLING_ID && Rs2Player.isAnimating()) {
                sleepUntil(() -> !Rs2Player.isAnimating(), 3_000);
            }
        }

        progressByIndex.clear();
        plugin.completeForestryEvent(KspForestryEvent.FRIENDLY_ENTLINGS);
        return true;
    }

    private boolean canPruneNow(EntlingProgress progress) {
        long now = System.currentTimeMillis();

        if (Rs2Player.isMoving() || Rs2Player.isAnimating(900)) {
            return false;
        }
        if (now - progress.lastAttemptMillis < MIN_PRUNE_INTERVAL_MS) {
            return false;
        }
        return progress.lastFailureMillis == 0L
                || now - progress.lastFailureMillis >= FAILED_CLICK_RETRY_MS;
    }

    private java.util.List<Rs2NpcModel> getRegularEntlings() {
        return Microbot.getRs2NpcCache().query()
                .withId(REGULAR_ENTLING_ID)
                .toList();
    }

    /**
     * Normalize RuneLite/Jagex markup and the known wording variants seen across
     * Forestry revisions. Returned text is canonical and safe for exact mapping.
     */
    private String normalizeRequest(String request) {
        if (request == null) {
            return "";
        }

        String normalized = Rs2TextSanitizer.stripTagsToSpace(request).trim();
        String key = normalized.toLowerCase(Locale.ROOT);

        switch (key) {
            case "breezy at the back!":
            case "breezy on the back!":
                return "Breezy at the back!";
            case "short on back and sides!":
            case "short back and sides!":
                return "Short back and sides!";
            case "a leafy mullet!":
                return "A leafy mullet!";
            case "short on top!":
                return "Short on top!";
            default:
                return normalized;
        }
    }

    /**
     * Exact Friendly Ent request mapping.
     *
     * For the two combined requests, both listed body regions are valid on every
     * prune. Alternating is unnecessary, so use one deterministic valid action:
     * top for the mullet and back for short-back-and-sides. This prevents the
     * previous handler from inventing an alternating haircut sequence.
     */
    private String actionForRequest(String request) {
        if (request == null || request.isEmpty()) {
            return null;
        }

        switch (request) {
            case "Breezy at the back!":
                return "Prune-back";
            case "Short on top!":
                return "Prune-top";
            case "A leafy mullet!":
                return "Prune-top";
            case "Short back and sides!":
                return "Prune-back";
            default:
                return null;
        }
    }

    private long entlingTargetKey(Rs2NpcModel entling) {
        long location = entling.getWorldLocation() == null
                ? 0L
                : (((long) entling.getWorldLocation().getX()) << 32)
                    ^ (entling.getWorldLocation().getY() & 0xffffffffL);
        return (((long) entling.getIndex()) << 48) ^ location;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }

    private static final class EntlingProgress {
        private String lastObservedRequest = "";
        private int successfulClicks;
        private long lastAttemptMillis;
        private long lastFailureMillis;
    }
}
