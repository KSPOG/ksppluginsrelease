package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import java.util.Locale;

/**
 * Strict GE-only banking/exchange interaction. KspVerifiedBank searches for
 * booths in the wider loaded scene; an out-of-range booth's click() can launch
 * the global WebWalker and leave the Grand Exchange.
 *
 * This helper never invokes the global pathfinder, an object-click helper,
 * or Rs2Npc.click()'s cant-reach recovery. It selects only a LIVE GE actor
 * and dispatches its validated menu option directly.
 */
final class KspSmartSuperheatGeAccess
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

    private KspSmartSuperheatGeAccess() {}

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
        NewMenuEntry entry = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null) return null;
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || !isInGrandExchange(player.getWorldLocation())) return null;
            WorldView view = player.getWorldView();
            if (view == null || view.npcs() == null) return null;

            NPC selected = null;
            String selectedName = null;
            int selectedOption = -1;
            int nearest = Integer.MAX_VALUE;

            for (NPC npc : view.npcs())
            {
                if (npc == null || !isAllowedActor(player.getWorldLocation(), npc.getWorldLocation())
                        || npc.getWorldView() != view) continue;

                String npcName = npc.getName();
                if (clerkOnly && (npcName == null
                        || !npcName.toLowerCase(Locale.ROOT).contains("grand exchange clerk"))) continue;

                NPCComposition composition = npc.getTransformedComposition();
                if (composition == null) composition = npc.getComposition();
                if (composition == null || composition.getActions() == null) continue;

                String[] actions = composition.getActions();
                int actionIndex = -1;
                for (int i = 0; i < actions.length && i < NPC_ACTIONS.length; i++)
                {
                    String label = actions[i];
                    if (label != null && action.equalsIgnoreCase(Rs2UiHelper.stripColTags(label)))
                    {
                        actionIndex = i;
                        break;
                    }
                }
                if (actionIndex < 0) continue;

                int distance = player.getWorldLocation().distanceTo(npc.getWorldLocation());
                if (distance >= nearest) continue;
                selected = npc;
                selectedName = npcName;
                selectedOption = actionIndex;
                nearest = distance;
            }

            if (selected == null) return null;
            return new NewMenuEntry()
                    .param0(0).param1(0).opcode(NPC_ACTIONS[selectedOption].getId())
                    .identifier(selected.getIndex()).itemId(-1)
                    .option(action).target(selectedName)
                    .setWorldViewId(view.getId()).actor(selected);
        }).orElse(null);

        if (entry == null) return false;
        // The menu action is actor-specific; do not use a distant booth
        // or generic NPC interaction which could start a walker recovery.
        NPC npc = (NPC) entry.getActor();
        if (npc == null) return false;
        if (!Rs2Camera.isTileOnScreen(npc.getLocalLocation())) Rs2Camera.turnTo(npc);
        Microbot.doInvoke(entry, Rs2UiHelper.getActorClickbox(npc));
        return true;
    }
}
