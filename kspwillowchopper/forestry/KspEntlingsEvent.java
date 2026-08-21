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

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Friendly Ent / Entling Forestry event.
 *
 * A requested haircut is a set of one or two pruning operations.  The original
 * Microbot handler collapsed the two-operation requests to only Prune-top or
 * Prune-back, which can never complete those hairstyles.  Track progress per NPC
 * index so a combo request advances to its second cut instead of re-clicking the
 * first action every BlockingEvent tick.
 */
@Slf4j
public class KspEntlingsEvent implements BlockingEvent {
    private static final int REGULAR_ENTLING_ID = NpcID.GATHERING_EVENT_ENTLINGS_NPC_01;
    private static final long SATISFACTION_GRACE_MS = 4_000L;

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
                String rawRequest = entling.getOverheadText();
                String request = normalizeRequest(rawRequest);
                String[] actions = actionsForRequest(request);
                if (actions == null) {
                    // Satisfaction / countdown overhead text must never be pruned.
                    continue;
                }

                int npcIndex = entling.getIndex();
                EntlingProgress progress = progressByIndex.get(npcIndex);
                if (progress == null || !request.equals(progress.request)) {
                    progress = new EntlingProgress(request);
                    progressByIndex.put(npcIndex, progress);
                }

                // Once every required part of the haircut has been cut, wait for the
                // NPC to morph to the pruned state.  Do not instantly start the plan
                // over, because pruning an already completed Entling stuns the player.
                if (progress.nextAction >= actions.length) {
                    if (System.currentTimeMillis() - progress.lastActionMillis < SATISFACTION_GRACE_MS) {
                        continue;
                    }

                    // If the server has left the Entling in the regular state with the
                    // exact same request for several seconds, allow one controlled
                    // retry of the haircut plan.  This recovers from a dropped click
                    // without creating the old every-tick spam loop.
                    progress.nextAction = 0;
                }

                String action = actions[progress.nextAction];
                long targetKey = entlingTargetKey(entling);
                if (!plugin.canStartForestryInteraction(targetKey, action)) {
                    continue;
                }

                log.debug("Entlings: {} -> {} ({}/{})",
                        request, action, progress.nextAction + 1, actions.length);

                entling.click(action);
                plugin.markForestryInteraction(targetKey, action);
                progress.nextAction++;
                progress.lastActionMillis = System.currentTimeMillis();
                interactionIssued = true;

                // Wait only for acknowledgement / the short prune animation.  A combo
                // haircut can then advance to its second distinct action promptly.
                sleepUntil(() -> Rs2Player.isAnimating()
                        || entling.getId() != REGULAR_ENTLING_ID
                        || !request.equals(normalizeRequest(entling.getOverheadText())), 1_200);
                sleepUntil(() -> !Rs2Player.isAnimating(), 2_500);
                break; // at most one Forestry interaction per pass
            }

            // Avoid a hot loop when all visible Entlings are already satisfied,
            // awaiting morph, or showing non-haircut countdown text.
            if (!interactionIssued) {
                sleep(100);
            }

            // Remove progress for NPC indices that have morphed/despawned so a newly
            // spawned Entling reusing an old index cannot inherit a previous haircut.
            progressByIndex.keySet().removeIf(index ->
                    entlings.stream().noneMatch(entling -> entling.getIndex() == index));
        }

        progressByIndex.clear();
        plugin.completeForestryEvent(KspForestryEvent.FRIENDLY_ENTLINGS);
        return true;
    }

    private java.util.List<Rs2NpcModel> getRegularEntlings() {
        return Microbot.getRs2NpcCache().query()
                .withId(REGULAR_ENTLING_ID)
                .toList();
    }

    /**
     * Keep current in-game wording plus older wording aliases so a minor wording
     * variation does not make an Entling invisible to the handler.
     */
    private String normalizeRequest(String request) {
        if (request == null) {
            return "";
        }

        String normalized = request.trim();
        if (normalized.equalsIgnoreCase("Breezy at the back!")) {
            return "Breezy on the back!";
        }
        if (normalized.equalsIgnoreCase("Short back and sides!")) {
            return "Short on back and sides!";
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
            case "Short on back and sides!":
                return new String[] {"Prune-back", "Prune-sides"};
            default:
                return null;
        }
    }

    private long entlingTargetKey(Rs2NpcModel entling) {
        // NPC hash can change when compositions transform. Index + world tile remains
        // stable enough for the short interaction latch while still distinguishing
        // the five Entlings in the event.
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
        private int nextAction;
        private long lastActionMillis;

        private EntlingProgress(String request) {
            this.request = request;
            this.nextAction = 0;
            this.lastActionMillis = 0L;
        }
    }
}
