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
import java.util.List;
import java.util.Locale;
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
        int lockedIndex = -1;
        long lastClickMillis = 0L;

        while (plugin.isForestryEventEnabled(KspForestryEvent.FRIENDLY_ENTLINGS)
                && Microbot.isLoggedIn()
                && System.currentTimeMillis() < deadline) {
            List<Rs2NpcModel> entlings = getRegularEntlings();
            if (entlings.isEmpty()) {
                break;
            }

            Rs2NpcModel target = null;
            if (lockedIndex >= 0) {
                final int expectedIndex = lockedIndex;
                target = entlings.stream()
                        .filter(Objects::nonNull)
                        .filter(entling -> entling.getIndex() == expectedIndex)
                        .findFirst()
                        .orElse(null);
                if (target == null) {
                    lockedIndex = -1;
                    sleep(80);
                    continue;
                }
            } else {
                target = entlings.stream()
                        .filter(Objects::nonNull)
                        .min(Comparator.comparingInt(entling ->
                                entling.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                        .orElse(null);
                if (target == null) {
                    sleep(100);
                    continue;
                }
                lockedIndex = target.getIndex();
            }

            String request = normalizeRequest(target.getOverheadText());
            String action = actionForRequest(request);
            if (action == null) {
                sleep(120);
                continue;
            }

            long now = System.currentTimeMillis();
            if (now - lastClickMillis < CLICK_COOLDOWN_MS
                    || Rs2Player.isMoving()
                    || Rs2Player.isAnimating(800)) {
                sleep(80);
                continue;
            }

            // Re-read immediately before clicking so a server-side overhead update
            // cannot make us use a stale haircut request.
            String currentRequest = normalizeRequest(target.getOverheadText());
            String currentAction = actionForRequest(currentRequest);
            if (currentAction == null) {
                sleep(100);
                continue;
            }

            int idBefore = target.getId();
            String acknowledgedRequest = currentRequest;
            if (!target.click(currentAction)) {
                lastClickMillis = now;
                sleep(250);
                continue;
            }

            plugin.markForestryInteraction(target.getHash(), currentAction);
            lastClickMillis = now;
            final Rs2NpcModel clickedTarget = target;

            sleepUntil(() -> clickedTarget.getId() != idBefore
                    || !Objects.equals(acknowledgedRequest, normalizeRequest(clickedTarget.getOverheadText()))
                    || Rs2Player.isAnimating(), 2_000);

            if (clickedTarget.getId() == REGULAR_ENTLING_ID && Rs2Player.isAnimating()) {
                sleepUntil(() -> !Rs2Player.isAnimating(), 3_000);
            }
        }

        if (!validate()) {
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

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
