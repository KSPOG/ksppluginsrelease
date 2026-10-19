package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.clues;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item requirements to be selected by the resolved beginner-clue step.
 * These are NOT global requirements: buying all of them up front would waste
 * coins and can block a fresh F2P account.
 */
public final class BeginnerClueRequirements
{
    private BeginnerClueRequirements() { }

    public static Map<String, Integer> forCharlieStep(String normalizedStepText)
    {
        if (normalizedStepText == null) return Collections.emptyMap();
        String text = normalizedStepText.toLowerCase(java.util.Locale.ROOT);
        String item = null;
        if (text.contains("iron ore")) item = "Iron ore";
        else if (text.contains("iron dagger")) item = "Iron dagger";
        else if (text.contains("raw herring")) item = "Raw herring";
        else if (text.contains("raw trout")) item = "Raw trout";
        else if (text.contains("trout")) item = "Trout";
        else if (text.contains("pike")) item = "Pike";
        else if (text.contains("leather body")) item = "Leather body";
        else if (text.contains("leather chaps")) item = "Leather chaps";
        if (item == null) return Collections.emptyMap();
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put(item, 1);
        return result;
    }

    public static Map<String, Integer> forDigStep()
    {
        return Collections.singletonMap("Spade", 1);
    }
}
