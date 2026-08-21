package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

/**
 * Small compatibility helpers for the current tile-object query/model API.
 * Avoids the deprecated Rs2GameObject facade.
 */
public final class KspTileObjectSupport {
    private KspTileObjectSupport() {
    }

    public static boolean hasAction(Rs2TileObjectModel object, String expectedAction) {
        if (object == null || expectedAction == null || expectedAction.isEmpty()) {
            return false;
        }

        try {
            ObjectComposition composition = object.getObjectComposition();
            if (composition == null) {
                return false;
            }

            String[] actions = composition.getActions();
            if (actions == null) {
                return false;
            }

            for (String action : actions) {
                if (action == null) {
                    continue;
                }
                String clean = Rs2UiHelper.stripTags(action);
                if (expectedAction.equalsIgnoreCase(clean)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }
}
