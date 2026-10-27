package net.runelite.client.plugins.microbot.kspaiofighter;

import java.util.List;
import java.util.Locale;

/**
 * Pure potion selection policy: inventories can contain different doses and
 * multiple boosts. Select only a potion that can improve the active combat
 * style, without wasting another dose while the relevant boosts remain active.
 *
 * Neither this policy nor the toggle automatically purchases/withdraws items.
 */
final class KspAioFighterPotionPolicy
{
    enum Style { MELEE, RANGED, MAGIC }

    private static final String[] SUPER_COMBAT = {
            "Super combat potion", "Divine super combat potion"
    };
    private static final String[] COMBAT = {"Combat potion"};
    private static final String[] ATTACK = {
            "Super attack", "Attack potion", "Divine super attack potion"
    };
    private static final String[] STRENGTH = {
            "Super strength", "Strength potion", "Divine super strength potion"
    };
    private static final String[] DEFENCE = {
            "Super defence", "Defence potion", "Divine super defence potion"
    };
    private static final String[] RANGED = {
            "Ranging potion", "Bastion potion", "Divine ranging potion", "Divine bastion potion"
    };
    private static final String[] MAGIC = {
            "Magic potion", "Battlemage potion", "Divine magic potion", "Divine battlemage potion"
    };

    private KspAioFighterPotionPolicy() {}

    /**
     * @param boosts positive values mean an active boost; 0 or negative mean
     *               expired or drained. Order: Attack, Strength, Defence,
     *               Ranged, Magic.
     * @return the exact inventory item name (including dose), or null.
     */
    static String choose(List<String> inventoryNames, Style style, int[] boosts, boolean allowDivine)
    {
        if (inventoryNames == null || style == null || boosts == null || boosts.length < 5)
            return null;

        if (style == Style.RANGED)
            return boosts[3] <= 0 ? firstAvailable(inventoryNames, allowDivine, RANGED) : null;
        if (style == Style.MAGIC)
            return boosts[4] <= 0 ? firstAvailable(inventoryNames, allowDivine, MAGIC) : null;

        boolean attack = boosts[0] <= 0;
        boolean strength = boosts[1] <= 0;
        boolean defence = boosts[2] <= 0;
        int missing = (attack ? 1 : 0) + (strength ? 1 : 0) + (defence ? 1 : 0);
        if (missing == 0) return null;

        // Multi-stat potions are the best use of a dose when several relevant
        // boosts have expired; a basic combat potion affects Attack/Strength only.
        if (missing >= 2 && defence)
        {
            String potion = firstAvailable(inventoryNames, allowDivine, SUPER_COMBAT);
            if (potion != null) return potion;
        }
        if (attack && strength)
        {
            String potion = firstAvailable(inventoryNames, allowDivine, COMBAT);
            if (potion != null) return potion;
            String superCombat = firstAvailable(inventoryNames, allowDivine, SUPER_COMBAT);
            if (superCombat != null) return superCombat;
        }

        // Singles should take precedence over spending a multi-stat potion
        // on just one expired boost.
        if (attack)
        {
            String potion = firstAvailable(inventoryNames, allowDivine, ATTACK);
            if (potion != null) return potion;
        }
        if (strength)
        {
            String potion = firstAvailable(inventoryNames, allowDivine, STRENGTH);
            if (potion != null) return potion;
        }
        if (defence)
        {
            String potion = firstAvailable(inventoryNames, allowDivine, DEFENCE);
            if (potion != null) return potion;
        }
        if (attack || strength)
        {
            String potion = firstAvailable(inventoryNames, allowDivine, COMBAT);
            if (potion != null) return potion;
        }
        return firstAvailable(inventoryNames, allowDivine, SUPER_COMBAT);
    }

    static boolean isSupportedPotion(String itemName)
    {
        String base = baseName(itemName);
        if (base.isEmpty()) return false;
        for (String[] names : new String[][] {SUPER_COMBAT, COMBAT, ATTACK, STRENGTH,
                DEFENCE, RANGED, MAGIC})
            for (String name : names)
                if (name.equalsIgnoreCase(base)) return true;
        return false;
    }

    private static String firstAvailable(List<String> items, boolean allowDivine, String[] preferred)
    {
        for (String base : preferred)
        {
            if (!allowDivine && base.toLowerCase(Locale.ROOT).startsWith("divine ")) continue;
            for (String item : items)
                if (item != null && base.equalsIgnoreCase(baseName(item)))
                    return item;
        }
        return null;
    }

    /** Only a standard OSRS (1)-(4) dose suffix is stripped. */
    private static String baseName(String itemName)
    {
        if (itemName == null) return "";
        String name = itemName.trim();
        int len = name.length();
        if (len >= 3 && name.charAt(len - 1) == ')'
                && name.charAt(len - 3) == '('
                && name.charAt(len - 2) >= '1' && name.charAt(len - 2) <= '4')
            return name.substring(0, len - 3).trim();
        return name;
    }
}
