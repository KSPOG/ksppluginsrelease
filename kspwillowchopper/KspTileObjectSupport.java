package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

/** Small compatibility helper shared by the Forestry handlers. */
public final class KspTileObjectSupport {
    private KspTileObjectSupport() {
    }

    public static boolean hasAction(Rs2TileObjectModel object, String expectedAction) {
        if (object == null || expectedAction == null || expectedAction.trim().isEmpty()) {
            return false;
        }

        try {
            ObjectComposition composition = object.getObjectComposition();
            if (composition == null || composition.getActions() == null) {
                return false;
            }

            for (String action : composition.getActions()) {
                if (action != null
                        && expectedAction.equalsIgnoreCase(Rs2UiHelper.stripTags(action).trim())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Scene cache entries can disappear while being inspected. Treat that
            // as a non-match and let the next Chopper iteration reacquire it.
        }

        return false;
    }
}
