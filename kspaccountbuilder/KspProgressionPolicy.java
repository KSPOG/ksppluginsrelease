package net.runelite.client.plugins.microbot.kspaccountbuilder;

/**
 * Deterministic, resource-gated Account Builder task ranking.
 *
 * The scheduler must first filter missing supplies, completed quests,
 * disallowed tasks and manually selected modes. This policy only ranks
 * currently executable candidates; it never buys or sells directly.
 *
 * Scores are priorities rather than GP/h or XP/h predictions.
 */
public final class KspProgressionPolicy
{
    private static final long LOW_COIN_RESERVE = 2_500L;

    private KspProgressionPolicy() {}

    public static int priority(String task, int level, long coins,
                               boolean missingSupplies, boolean affordableSupplies)
    {
        if (task == null) return Integer.MIN_VALUE;
        int catchUp = Math.max(0, 50 - Math.max(1, level)) / 3;
        switch (task)
        {
            case "GE_SELL":
                // Sell when a purchase is blocked by cash, not because
                // a random support-task roll interrupted productive training.
                return missingSupplies && !affordableSupplies ? 180
                        : coins < LOW_COIN_RESERVE ? 90 : 26;
            case "GE_BUY":
                // Restocking is only useful when the execution layer can
                // afford its known requirements or finish an existing offer.
                return missingSupplies && affordableSupplies ? 170 : 15;
            case "RUNE_MYSTERIES":
                return 115;
            case "STRONGHOLD_OF_SECURITY":
                return 100;
            case "COOKS_ASSISTANT":
            case "SHEEP_SHEARER":
            case "X_MARKS_THE_SPOT":
            case "THE_RESTLESS_GHOST":
                return 96;
            case "GOBLIN_DIPLOMACY":
            case "ROMEO_AND_JULIET":
            case "IMP_CATCHER":
                return 92;
            case "MINING":
            case "WOODCUTTING":
                return 82 + catchUp;
            case "FISHING":
                return 78 + catchUp;
            case "MELEE":
                return 76 + catchUp;
            case "CRAFTING":
            case "SMITHING":
            case "SMELTING":
                return 73 + catchUp;
            case "RUNE_ESSENCE":
                return 70 + catchUp;
            case "COOKING":
            case "FIREMAKING":
                return 66 + catchUp;
            default:
                return 40 + catchUp;
        }
    }
}
