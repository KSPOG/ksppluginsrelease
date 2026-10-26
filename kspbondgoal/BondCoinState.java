package net.runelite.client.plugins.microbot.kspbondgoal;

/**
 * Last observed GP, stored independently for inventory and bank. Unknown is
 * not zero: a missing bank container, logout or loading screen must never
 * overwrite a previously observed coin stack.
 *
 * Immutable and independent of RuneLite so persistence can be unit tested.
 */
final class BondCoinState
{
    private static final String VERSION = "v1";
    static final String STORAGE_KEY_PREFIX = "lastKnownCoinsV1_";

    static String keyForAccount(long accountHash)
    {
        if (accountHash == 0L) throw new IllegalArgumentException("Account hash is not available");
        return STORAGE_KEY_PREFIX + Long.toUnsignedString(accountHash);
    }
    private static final long MAX_GP = 100_000_000_000L;

    private final boolean inventoryKnown;
    private final long inventoryCoins;
    private final boolean bankKnown;
    private final long bankCoins;

    private BondCoinState(boolean inventoryKnown, long inventoryCoins, boolean bankKnown, long bankCoins)
    {
        this.inventoryKnown = inventoryKnown;
        this.inventoryCoins = inventoryCoins;
        this.bankKnown = bankKnown;
        this.bankCoins = bankCoins;
    }

    static BondCoinState empty()
    {
        return new BondCoinState(false, 0L, false, 0L);
    }

    BondCoinState observedInventory(long coins)
    {
        return new BondCoinState(true, Math.max(0L, coins), bankKnown, bankCoins);
    }

    BondCoinState observedBank(long coins)
    {
        return new BondCoinState(inventoryKnown, inventoryCoins, true, Math.max(0L, coins));
    }

    boolean isInventoryKnown() { return inventoryKnown; }
    boolean isBankKnown() { return bankKnown; }
    long getInventoryCoins() { return inventoryCoins; }
    long getBankCoins() { return bankCoins; }

    long currentCoins(boolean includeBank)
    {
        return inventoryCoins + (includeBank && bankKnown ? bankCoins : 0L);
    }

    String serialize()
    {
        return VERSION + ":" + (inventoryKnown ? 1 : 0) + ":" + inventoryCoins
            + ":" + (bankKnown ? 1 : 0) + ":" + bankCoins;
    }

    static BondCoinState deserialize(String value)
    {
        if (value == null || value.isEmpty()) return empty();
        String[] parts = value.split(":", -1);
        if (parts.length != 5 || !VERSION.equals(parts[0])) return empty();
        try
        {
            if (!("0".equals(parts[1]) || "1".equals(parts[1]))
                || !("0".equals(parts[3]) || "1".equals(parts[3])))
                return empty();
            boolean invKnown = "1".equals(parts[1]);
            boolean bankKnown = "1".equals(parts[3]);
            long inv = Long.parseLong(parts[2]);
            long bank = Long.parseLong(parts[4]);
            if (inv < 0L || bank < 0L || inv > MAX_GP || bank > MAX_GP)
                return empty();
            return new BondCoinState(invKnown, invKnown ? inv : 0L,
                    bankKnown, bankKnown ? bank : 0L);
        }
        catch (NumberFormatException ignored)
        {
            return empty();
        }
    }
}
