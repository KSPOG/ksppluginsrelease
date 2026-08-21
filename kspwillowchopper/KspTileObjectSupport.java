package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

/** Compatibility helpers for the current tile-object model API. */
public final class KspTileObjectSupport {
    private KspTileObjectSupport() {}

    public static boolean hasAction(Rs2TileObjectModel object, String expectedAction) {
        if (object == null || expectedAction == null || expectedAction.isEmpty()) return false;

        try {
            ObjectComposition composition = object.getObjectComposition();
            String[] actions = composition == null ? null : composition.getActions();
            if (actions == null) return false;

            for (String action : actions) {
                if (action != null && expectedAction.equalsIgnoreCase(Rs2UiHelper.stripTags(action))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
