package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;


/**
 * Strict GE-only banking/exchange interaction. Generic helpers search for
 * booths in the wider loaded scene; an out-of-range booth's click() can launch
 * the global WebWalker and leave the Grand Exchange.
 *
 * This helper never invokes the global pathfinder, an object-click helper,
 * or Rs2Npc.click()'s cant-reach recovery. It selects only a LIVE GE actor
 * and dispatches its validated menu option directly.
 */
@Slf4j
final class KspHighAlchGeAccess
{
    // Grand Exchange central paved square, including clerks and bankers.
    // Deliberately excludes both Varrock West/East banks.
    private static final int MIN_X = 3153;
    private static final int MAX_X = 3178;
    private static final int MIN_Y = 3477;
    private static final int MAX_Y = 3502;
    private static final int MAX_ACTOR_DISTANCE = 17;

    private static final MenuAction[] NPC_ACTIONS = {
            MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION,
            MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION,
            MenuAction.NPC_FIFTH_OPTION
    };

    private static volatile String lastDiagnostic = "GE NPC interaction not attempted";

    private KspHighAlchGeAccess() {}

    static String lastDiagnostic()
    {
        return lastDiagnostic;
    }

    static boolean isInGrandExchange(WorldPoint point)
    {
        return point != null && point.getPlane() == 0
                && point.getX() >= MIN_X && point.getX() <= MAX_X
                && point.getY() >= MIN_Y && point.getY() <= MAX_Y;
    }

    static boolean isAllowedActor(WorldPoint player, WorldPoint actor)
    {
        return isInGrandExchange(player) && isInGrandExchange(actor)
                && player.distanceTo(actor) <= MAX_ACTOR_DISTANCE;
    }

    /**
     * RuneLite's Bank plugin recognizes the bank interface by BANKMAIN; it
     * does not identify "a GE bank" by scanning arbitrary bank booths.
     * Only the GE's named Banker NPCs may open our bank. Clerks handle
     * Exchange and never serve as the bank fallback.
     */
    static boolean isExpectedActor(String name, boolean clerkOnly)
    {
        return name != null && (clerkOnly
                ? "Grand Exchange Clerk".equalsIgnoreCase(name.trim())
                : "Banker".equalsIgnoreCase(name.trim()));
    }

    static boolean openLocalBank()
    {
        return interactLocalActor("Bank", false);
    }

    static boolean openLocalExchange()
    {
        return interactLocalActor("Exchange", true);
    }

    private static boolean interactLocalActor(String action, boolean clerkOnly)
    {
        // Microbot's normal GE opener does this too: selected inventory items
        // change NPC left-click actions into "Use <item> -> NPC", so clear
        // selection BEFORE sending an Exchange/Bank menu action.
        if (Rs2Inventory.isItemSelected())
        {
            Microbot.getMouse().click();
            lastDiagnostic = "Deselecting inventory item before " + action;
            log.info("High Alch GE access | {}", lastDiagnostic);
            return false;
        }

        final String[] inspection = { "No loaded GE " + (clerkOnly ? "Clerk" : "Banker") };
        NewMenuEntry entry = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null)
            {
                inspection[0] = "RuneLite client unavailable";
                return null;
            }
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || !isInGrandExchange(player.getWorldLocation()))
            {
                inspection[0] = "Player outside GE access square";
                return null;
            }
            WorldView view = player.getWorldView();
            if (view == null || view.npcs() == null)
            {
                inspection[0] = "NPC scene not yet available";
                return null;
            }

            NPC selected = null;
            String selectedName = null;
            int selectedOption = -1;
            int nearest = Integer.MAX_VALUE;
            int matchingActors = 0;
            int missingActions = 0;
            String sampleActions = "";

            for (NPC npc : view.npcs())
            {
                if (npc == null || npc.getWorldView() != view
                        || !isAllowedActor(player.getWorldLocation(), npc.getWorldLocation()))
                    continue;

                NPCComposition composition = npc.getTransformedComposition();
                if (composition == null) composition = npc.getComposition();
                String npcName = npc.getName();
                if ((npcName == null || npcName.isBlank()) && composition != null)
                    npcName = composition.getName();
                if (!isExpectedActor(npcName, clerkOnly)) continue;
                matchingActors++;

                String[] actions = composition == null ? null : composition.getActions();
                if (actions == null)
                {
                    missingActions++;
                    continue;
                }

                int actionIndex = -1;
                for (int i = 0; i < actions.length && i < NPC_ACTIONS.length; i++)
                {
                    String label = actions[i];
                    if (label != null && action.equalsIgnoreCase(Rs2UiHelper.stripColTags(label).trim()))
                    {
                        actionIndex = i;
                        break;
                    }
                }
                if (actionIndex < 0)
                {
                    missingActions++;
                    sampleActions = java.util.Arrays.toString(actions);
                    continue;
                }

                int distance = player.getWorldLocation().distanceTo(npc.getWorldLocation());
                if (distance >= nearest) continue;
                selected = npc;
                selectedName = npcName;
                selectedOption = actionIndex;
                nearest = distance;
            }

            if (selected == null)
            {
                inspection[0] = matchingActors == 0
                        ? "No nearby " + (clerkOnly ? "Grand Exchange Clerk" : "GE Banker")
                            + " within " + MAX_ACTOR_DISTANCE + " tiles at " + player.getWorldLocation()
                        : "Found " + matchingActors + " GE NPCs; " + missingActions
                            + " missing " + action + " action; observedActions=" + sampleActions;
                return null;
            }

            inspection[0] = "Using " + action + " on " + selectedName
                    + " id=" + selected.getId() + " index=" + selected.getIndex()
                    + " distance=" + nearest + " optionIndex=" + selectedOption;
            return new NewMenuEntry()
                    .param0(0).param1(0).opcode(NPC_ACTIONS[selectedOption].getId())
                    .identifier(selected.getIndex()).itemId(-1)
                    .option(action).target(selectedName)
                    .setWorldViewId(view.getId()).actor(selected);
        }).orElse(null);

        lastDiagnostic = inspection[0];
        if (entry == null)
        {
            log.debug("High Alch GE access unavailable | {}", lastDiagnostic);
            return false;
        }

        NPC npc = (NPC) entry.getActor();
        if (npc == null)
        {
            lastDiagnostic = "Menu entry actor was null for " + action;
            return false;
        }

        try
        {
            if (!Rs2Camera.isTileOnScreen(npc.getLocalLocation()))
                Rs2Camera.turnTo(npc);
            Microbot.doInvoke(entry, Rs2UiHelper.getActorClickbox(npc));
            log.info("High Alch GE access dispatched | {}", lastDiagnostic);
            return true;
        }
        catch (RuntimeException ex)
        {
            lastDiagnostic = "Failed to dispatch GE " + action + ": "
                    + ex.getClass().getSimpleName();
            log.warn("High Alch GE access invocation failed", ex);
            return false;
        }
    }

}
