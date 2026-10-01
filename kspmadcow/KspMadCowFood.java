package net.runelite.client.plugins.microbot.kspmadcow;

import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

/**
 * Mad Cow food choices. Mirrors Microbot's Rs2Food enum and adds plugin-specific
 * choices without requiring a Microbot client update.
 */
public enum KspMadCowFood {
    Dark_Crab(Rs2Food.Dark_Crab),
    ROCKTAIL(Rs2Food.ROCKTAIL),
    MANTA(Rs2Food.MANTA),
    SHARK(Rs2Food.SHARK),
    KARAMBWAN(Rs2Food.KARAMBWAN),
    LOBSTER(Rs2Food.LOBSTER),
    TROUT(Rs2Food.TROUT),
    SALMON(Rs2Food.SALMON),
    SWORDFISH(Rs2Food.SWORDFISH),
    TUNA(Rs2Food.TUNA),
    MONKFISH(Rs2Food.MONKFISH),
    SEA_TURTLE(Rs2Food.SEA_TURTLE),
    CAKE(Rs2Food.CAKE),
    BASS(Rs2Food.BASS),
    COD(Rs2Food.COD),
    POTATO(Rs2Food.POTATO),
    BAKED_POTATO(Rs2Food.BAKED_POTATO),
    POTATO_WITH_CHEESE(Rs2Food.POTATO_WITH_CHEESE),
    EGG_POTATO(Rs2Food.EGG_POTATO),
    CHILLI_POTATO(Rs2Food.CHILLI_POTATO),
    MUSHROOM_POTATO(Rs2Food.MUSHROOM_POTATO),
    TUNA_POTATO(Rs2Food.TUNA_POTATO),
    SHRIMPS(Rs2Food.SHRIMPS),
    HERRING(Rs2Food.HERRING),
    SARDINE(Rs2Food.SARDINE),
    CHOCOLATE_CAKE(Rs2Food.CHOCOLATE_CAKE),
    ANCHOVIES(Rs2Food.ANCHOVIES),
    PLAIN_PIZZA(Rs2Food.PLAIN_PIZZA),
    MEAT_PIZZA(Rs2Food.MEAT_PIZZA),
    ANCHOVY_PIZZA(Rs2Food.ANCHOVY_PIZZA),
    PINEAPPLE_PIZZA(Rs2Food.PINEAPPLE_PIZZA),
    BREAD(Rs2Food.BREAD),
    APPLE_PIE(Rs2Food.APPLE_PIE),
    REDBERRY_PIE(Rs2Food.REDBERRY_PIE),
    MEAT_PIE(Rs2Food.MEAT_PIE),
    PIKE(Rs2Food.PIKE),
    POTATO_WITH_BUTTER(Rs2Food.POTATO_WITH_BUTTER),
    BANANA(Rs2Food.BANANA),
    PEACH(Rs2Food.PEACH),
    ORANGE(Rs2Food.ORANGE),
    PINEAPPLE_RINGS(Rs2Food.PINEAPPLE_RINGS),
    PINEAPPLE_CHUNKS(Rs2Food.PINEAPPLE_CHUNKS),
    JUG_OF_WINE(Rs2Food.JUG_OF_WINE),
    COOKED_LARUPIA(Rs2Food.COOKED_LARUPIA),
    COOKED_BARBTAILED_KEBBIT(Rs2Food.COOKED_BARBTAILED_KEBBIT),
    COOKED_GRAAHK(Rs2Food.COOKED_GRAAHK),
    COOKED_KYATT(Rs2Food.COOKED_KYATT),
    COOKED_PYRE_FOX(Rs2Food.COOKED_PYRE_FOX),
    COOKED_SUNLIGHT_ANTELOPE(Rs2Food.COOKED_SUNLIGHT_ANTELOPE),
    COOKED_DASHING_KEBBIT(Rs2Food.COOKED_DASHING_KEBBIT),
    COOKED_MOONLIGHT_ANTELOPE(Rs2Food.COOKED_MOONLIGHT_ANTELOPE),
    PURPLE_SWEETS(Rs2Food.PURPLE_SWEETS),
    CABBAGE(Rs2Food.CABBAGE),
    BLIGHTED_MANTA_RAY(Rs2Food.BLIGHTED_MANTA_RAY),
    BLIGHTED_ANGLERFISH(Rs2Food.BLIGHTED_ANGLERFISH),
    BLIGHTED_KARAMBWAN(Rs2Food.BLIGHTED_KARAMBWAN),

    KEBAB(1971, "Kebab");

    private final int id;
    private final String name;

    KspMadCowFood(Rs2Food food) {
        this(food.getId(), food.getName());
    }

    KspMadCowFood(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
