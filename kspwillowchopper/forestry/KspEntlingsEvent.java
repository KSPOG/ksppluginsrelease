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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class KspEntlingsEvent implements BlockingEvent {
    private static final int REGULAR_ENTLING_ID = NpcID.GATHERING_EVENT_ENTLINGS_NPC_01;
    private static final long MAX_EVENT_MS = 120_000L;
    private static final long CLICK_COOLDOWN_MS = 800L;

    private final KspWillowChopperPlugin plugin;

    public KspEntlingsEvent(KspWillowChopperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        return plugin.isForestryEventEnabled(KspForestryEvent.FRIENDLY_ENTLINGS)
                && Microbot.isLoggedIn()
                && !getRegularEntlings().isEmpty();
    }

    @Override
    public boolean execute() {
        plugin.setCurrentForestryEvent(KspForestryEvent.FRIENDLY_ENTLINGS);
        if (!plugin.ensureInventorySpace(2)) {
            return true;
        }

        long deadline = System.currentTimeMillis() + MAX_EVENT_MS;
        long lastClickMillis = 0L;
        Map<Integer, Integer> actionPhaseByNpc = new HashMap<>();

        while (plugin.isForestryEventEnabled(KspForestryEvent.FRIENDLY_ENTLINGS)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            List<Rs2NpcModel> entlings = getRegularEntlings();
            if (entlings.isEmpty()) {
                break;
            }

            entlings.sort(Comparator.comparingInt(entling ->
                    entling.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())));

            boolean interacted = false;
            for (Rs2NpcModel target : entlings) {
                if (target == null) {
                    continue;
                }

                String request = normalizeRequest(target.getOverheadText());
                String[] actions = actionsForRequest(request);
                if (actions.length == 0) {
                    continue;
                }

                long now = System.currentTimeMillis();
                if (now - lastClickMillis < CLICK_COOLDOWN_MS) {
                    sleep(Math.min(100L, CLICK_COOLDOWN_MS - (now - lastClickMillis)));
                    break;
                }
                if (Rs2Player.isMoving() || Rs2Player.isAnimating(800)) {
                    sleep(80);
                    break;
                }

                // Requests containing two regions must actually service both regions.
                // Rotate between the valid actions for that exact entling until it morphs
                // into its pruned NPC form. This prevents repeatedly pruning only Back/Top.
                int phase = actionPhaseByNpc.getOrDefault(target.getIndex(), 0);
                String action = actions[phase % actions.length];

                // Re-read immediately before clicking so we never use an action chosen
                // from stale overhead text after a server-side update.
                String currentRequest = normalizeRequest(target.getOverheadText());
                String[] currentActions = actionsForRequest(currentRequest);
                if (currentActions.length == 0) {
                    continue;
                }
                if (!Objects.equals(request, currentRequest)) {
                    phase = 0;
                    action = currentActions[0];
                } else {
                    action = currentActions[phase % currentActions.length];
                }

                int idBefore = target.getId();
                if (!target.click(action)) {
                    // If a dual-region request changed its active menu ordering during
                    // the tick, try the other valid action before giving up this pass.
                    if (currentActions.length > 1) {
                        String alternate = currentActions[(phase + 1) % currentActions.length];
                        if (!target.click(alternate)) {
                            lastClickMillis = now;
                            sleep(180);
                            continue;
                        }
                        action = alternate;
                        phase++;
                    } else {
                        lastClickMillis = now;
                        sleep(180);
                        continue;
                    }
                }

                plugin.markForestryInteraction(target.getHash(), action);
                actionPhaseByNpc.put(target.getIndex(), phase + 1);
                lastClickMillis = System.currentTimeMillis();
                interacted = true;

                final Rs2NpcModel clickedTarget = target;
                final int clickedId = idBefore;
                sleepUntil(() -> clickedTarget.getId() != clickedId
                        || Rs2Player.isAnimating(), 1_800);
                if (Rs2Player.isAnimating()) {
                    sleepUntil(() -> !Rs2Player.isAnimating(), 3_000);
                }

                // Refresh the NPC cache after every successful prune. Entlings roam and
                // eventually morph to the pruned ID, so stale model/index locks are unsafe.
                break;
            }

            if (!interacted) {
                sleep(100);
            }
        }

        if (getRegularEntlings().isEmpty()) {
            plugin.completeForestryEvent(KspForestryEvent.FRIENDLY_ENTLINGS);
        }
        return true;
    }

    private List<Rs2NpcModel> getRegularEntlings() {
        return Microbot.getRs2NpcCache().query()
                .withId(REGULAR_ENTLING_ID)
                .toList();
    }

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

    private String[] actionsForRequest(String request) {
        if (request == null || request.isEmpty()) {
            return new String[0];
        }

        switch (request) {
            case "Breezy at the back!":
                return new String[]{"Prune-back"};
            case "Short on top!":
                return new String[]{"Prune-top"};
            case "A leafy mullet!":
                return new String[]{"Prune-top", "Prune-sides"};
            case "Short back and sides!":
                return new String[]{"Prune-back", "Prune-sides"};
            default:
                return new String[0];
        }
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
