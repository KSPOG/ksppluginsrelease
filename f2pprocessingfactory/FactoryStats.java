package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public final class FactoryStats
{
    private final long startedAt = System.currentTimeMillis();
    private final AtomicLong unitsProcessed = new AtomicLong();
    private final AtomicLong itemsBought = new AtomicLong();
    private final AtomicLong itemsSold = new AtomicLong();
    private final AtomicLong coinsSpent = new AtomicLong();
    private final AtomicLong grossRevenue = new AtomicLong();
    private final AtomicLong estimatedTax = new AtomicLong();

    public void recordProcessed(long quantity)
    {
        if (quantity > 0)
        {
            unitsProcessed.addAndGet(quantity);
        }
    }

    public void recordBought(long quantity, long unitPrice)
    {
        if (quantity <= 0)
        {
            return;
        }
        itemsBought.addAndGet(quantity);
        coinsSpent.addAndGet(safeMultiply(quantity, unitPrice));
    }

    public void recordSold(long quantity, long unitPrice, int taxPercent)
    {
        if (quantity <= 0)
        {
            return;
        }
        long revenue = safeMultiply(quantity, unitPrice);
        itemsSold.addAndGet(quantity);
        grossRevenue.addAndGet(revenue);
        estimatedTax.addAndGet((long) Math.floor(revenue * (Math.max(0, taxPercent) / 100.0)));
    }

    public long getUnitsProcessed()
    {
        return unitsProcessed.get();
    }

    public long getItemsBought()
    {
        return itemsBought.get();
    }

    public long getItemsSold()
    {
        return itemsSold.get();
    }

    public long getCoinsSpent()
    {
        return coinsSpent.get();
    }

    public long getGrossRevenue()
    {
        return grossRevenue.get();
    }

    public long getEstimatedTax()
    {
        return estimatedTax.get();
    }

    public long getSessionCashFlow()
    {
        return grossRevenue.get() - estimatedTax.get() - coinsSpent.get();
    }

    public Duration getRuntime()
    {
        return Duration.ofMillis(Math.max(0L, System.currentTimeMillis() - startedAt));
    }

    private static long safeMultiply(long left, long right)
    {
        try
        {
            return Math.multiplyExact(left, right);
        }
        catch (ArithmeticException ignored)
        {
            return Long.MAX_VALUE;
        }
    }
}
