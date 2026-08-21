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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Friendly Ent / Entling Forestry event.
 *
 * Important mechanics:
 * - A regular Entling remains NPC_01 until it is fully satisfied.
 * - The request text determines which prune options are valid.
 * - A correct Entling must be pruned repeatedly until it morphs to the pruned NPC.
 * - Player#getInteracting() can remain pointed at the Entling after a prune animation,
 *   so the generic Forestry actor-interaction guard must not be used for the repeat cuts.
 *   Movement + animation + an Entling-local click latch provide the anti-spam guard here.
 */
@Slf4j
public class KspEntlingsEvent implements BlockingEvent {
    private static final int REGULAR_ENTLING_ID = NpcID.GATHERING_EVENT_ENTLINGS_NPC_01;

    // One game tick is 600 ms.  Keep a little extra headroom so the same menu
    // action cannot be emitted twice before the server has acknowledged the first.
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

        while (validate()) {
            var entlings = getRegularEntlings();
            entlings.sort(Comparator.comparingInt(entling ->
                    entling.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())));

            boolean interactionIssued = false;

            for (Rs2NpcModel entling : entlings) {
                if (entling == null || entling.getId() != REGULAR_ENTLING_ID) {
                    continue;
                }

                String request = normalizeRequest(entling.getOverheadText());
                String[] validActions = actionsForRequest(request);
                if (validActions == null || validActions.length == 0) {
                    // Fully-pruned/satisfaction/countdown text is deliberately ignored.
                    continue;
                }

                int npcIndex = entling.getIndex();
                EntlingProgress progress = progressByIndex.get(npcIndex);
                if (progress == null || !Objects.equals(progress.request, request)) {
                    progress = new EntlingProgress(request);
                    progressByIndex.put(npcIndex, progress);
                }

                if (!canPruneNow(progress)) {
                    continue;
                }

                // For requests that permit two pruning locations, alternate between
                // them.  Both are correct choices; the important part is to keep
                // performing correct prunes until the Entling actually morphs.
                String action = validActions[progress.nextActionIndex % validActions.length];
                long targetKey = entlingTargetKey(entling);

                log.debug("Entlings: npc={} request='{}' action='{}' trim={}",
                        npcIndex, request, action, progress.successfulClicks + 1);

                String requestBefore = request;
                int idBefore = entling.getId();
                long now = System.currentTimeMillis();

                boolean clicked = entling.click(action);
                progress.lastAttemptMillis = now;

                if (!clicked) {
                    progress.lastFailureMillis = now;
                    continue;
                }

                plugin.markForestryInteraction(targetKey, action);
                progress.successfulClicks++;
                progress.nextActionIndex = (progress.nextActionIndex + 1) % validActions.length;
                interactionIssued = true;

                // Wait for a real acknowledgement: prune animation, request text
                // transition, or NPC morph.  Do not use Player#isInteracting here;
                // it can stay latched to the same Entling after the action is done.
                sleepUntil(() ->
                                entling.getId() != idBefore
                                || !Objects.equals(requestBefore, normalizeRequest(entling.getOverheadText()))
                                || Rs2Player.isAnimating(),
                        1_800);

                // If the Entling has not morphed, let the prune animation finish
                // before issuing the next correct cut.
                if (entling.getId() == REGULAR_ENTLING_ID && Rs2Player.isAnimating()) {
                    sleepUntil(() -> !Rs2Player.isAnimating(), 3_000);
                }

                break; // at most one Entling click per loop pass
            }

            // Remove state for Entlings that morphed to the fully-pruned NPC or
            // despawned. A reused NPC index must start with a fresh trim counter.
            progressByIndex.keySet().removeIf(index ->
                    entlings.stream().noneMatch(entling -> entling.getIndex() == index));

            if (!interactionIssued) {
                sleep(100);
            }
        }

        progressByIndex.clear();
        plugin.completeForestryEvent(KspForestryEvent.FRIENDLY_ENTLINGS);
        return true;
    }

    /**
     * Entlings are intentionally special-cased instead of using the generic
     * Forestry interaction guard. RuneLite may keep LocalPlayer#getInteracting()
     * set to the same Entling between repeated prunes; treating that stale actor
     * pointer as "busy" prevents the second and later trims forever.
     */
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
     * Keep the exact current in-game wording shown by the Entlings and accept a
     * couple of historical aliases for compatibility.
     */
    private String normalizeRequest(String request) {
        if (request == null) {
            return "";
        }

        String normalized = request.trim();
        if (normalized.equalsIgnoreCase("Breezy at the back!")) {
            return "Breezy on the back!";
        }
        if (normalized.equalsIgnoreCase("Short on back and sides!")) {
            return "Short back and sides!";
        }
        return normalized;
    }

    private String[] actionsForRequest(String request) {
        if (request == null || request.isEmpty()) {
            return null;
        }

        switch (request) {
            case "Breezy on the back!":
                return new String[] {"Prune-back"};
            case "Short on top!":
                return new String[] {"Prune-top"};
            case "A leafy mullet!":
                return new String[] {"Prune-top", "Prune-sides"};
            case "Short back and sides!":
                return new String[] {"Prune-back", "Prune-sides"};
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
        private final String request;
        private int nextActionIndex;
        private int successfulClicks;
        private long lastAttemptMillis;
        private long lastFailureMillis;

        private EntlingProgress(String request) {
            this.request = request;
            this.nextActionIndex = 0;
            this.successfulClicks = 0;
            this.lastAttemptMillis = 0L;
            this.lastFailureMillis = 0L;
        }
    }
}
