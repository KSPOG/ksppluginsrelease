package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Compatibility guard that makes the Local Mule "Keep In Bank" setting a real
 * protected reserve for the existing High Alch trader.
 *
 * The trader predates the mule feature and intentionally withdraws its entire bank
 * coin stack when inventory capital is insufficient. Rather than duplicate or replace
 * that large trading state machine, this guard normalises the bank to exactly the
 * configured protected reserve and marks the trader's existing bank-coin fallback as
 * exhausted. All coins above the reserve are moved to inventory and remain usable as
 * normal trading capital.
 *
 * This class can be removed once the trader exposes a first-class protected-bank-reserve
 * API; until then the reflection is deliberately isolated to this one compatibility shim.
 */
@Slf4j
public class KspHighAlchBankReserveGuard
{
    private static final int COINS_ID = 995;
    private static final long POLL_MS = 500L;

    public static volatile boolean normalising;
    public static volatile String status = "Disabled";

    private final KspF2PHighAlchTraderScript traderScript;
    private ScheduledExecutorService executor;
    private KspF2PHighAlchTraderConfig config;
    private Field bankCoinsExhaustedField;
    private long normalisedReserve = -1L;
    private boolean forcedBankExhausted;
    private boolean pauseOwned;
    private volatile boolean stopping;

    @Inject
    public KspHighAlchBankReserveGuard(KspF2PHighAlchTraderScript traderScript)
    {
        this.traderScript = traderScript;
        try
        {
            bankCoinsExhaustedField = KspF2PHighAlchTraderScript.class.getDeclaredField("bankCoinsExhausted");
            bankCoinsExhaustedField.setAccessible(true);
        }
        catch (ReflectiveOperationException ex)
        {
            log.error("Unable to bind High Alch bank reserve compatibility field", ex);
        }
    }

    public synchronized void start(KspF2PHighAlchTraderConfig config)
    {
        if (executor != null) return;
        this.config = config;
        stopping = false;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ksp-high-alch-bank-reserve");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tickSafely, 0L, POLL_MS, TimeUnit.MILLISECONDS);
    }

    private void tickSafely()
    {
        try
        {
            tick();
        }
        catch (Exception ex)
        {
            status = "Reserve guard error";
            log.error("High Alch bank reserve guard failed", ex);
            releasePause();
        }
    }

    private void tick()
    {
        if (stopping || config == null) return;

        long reserve = config.enableMule() ? Math.max(0L, config.muleKeepInBank()) : 0L;
        if (reserve <= 0L)
        {
            status = "Disabled";
            normalisedReserve = 0L;
            if (forcedBankExhausted)
            {
                setTraderBankCoinsExhausted(false);
                forcedBankExhausted = false;
            }
            return;
        }

        // The mule service temporarily parks trading capital in the bank while a job
        // is active. Do not fight that layout; only keep the old trader blocked from
        // consuming the protected stack.
        if (KspHighAlchMuleService.state != KspHighAlchMuleService.MuleState.IDLE
                && KspHighAlchMuleService.state != KspHighAlchMuleService.MuleState.FAILED)
        {
            setTraderBankCoinsExhausted(true);
            forcedBankExhausted = true;
            status = "Reserve protected during mule job";
            return;
        }

        if (!Microbot.isLoggedIn())
        {
            status = "Waiting for login";
            return;
        }

        long cachedBankCoins = Math.max(0L, Rs2Bank.count(COINS_ID));
        if (normalisedReserve == reserve && cachedBankCoins == reserve)
        {
            setTraderBankCoinsExhausted(true);
            forcedBankExhausted = true;
            status = "Protected " + reserve + " GP in bank";
            return;
        }

        normaliseReserve(reserve);
    }

    private void normaliseReserve(long reserve)
    {
        normalising = true;
        acquirePause();
        try
        {
            status = "Normalising protected bank reserve";
            if (!Rs2Bank.walkToBankAndUseBank())
            {
                status = "Waiting for bank to protect reserve";
                return;
            }

            long bank = Math.max(0L, Rs2Bank.count(COINS_ID));
            long inventory = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID));

            if (bank < reserve)
            {
                long needed = reserve - bank;
                long deposit = Math.min(needed, inventory);
                if (deposit > 0L && deposit <= Integer.MAX_VALUE)
                {
                    Rs2Bank.depositX(COINS_ID, (int) deposit);
                    final long expected = bank + deposit;
                    sleepUntil(() -> Rs2Bank.count(COINS_ID) >= expected, 5_000);
                }
            }
            else if (bank > reserve)
            {
                long withdraw = bank - reserve;
                if (withdraw > 0L && withdraw <= Integer.MAX_VALUE)
                {
                    int before = Rs2Inventory.itemQuantity(COINS_ID);
                    Rs2Bank.withdrawX(COINS_ID, (int) withdraw);
                    sleepUntil(() -> Rs2Inventory.itemQuantity(COINS_ID) > before, 5_000);
                }
            }

            long after = Math.max(0L, Rs2Bank.count(COINS_ID));
            normalisedReserve = after == reserve ? reserve : -1L;
            setTraderBankCoinsExhausted(true);
            forcedBankExhausted = true;
            status = after == reserve
                    ? "Protected " + reserve + " GP in bank"
                    : "Reserve partially protected: " + after + "/" + reserve;
            Rs2Bank.closeBank();
        }
        finally
        {
            normalising = false;
            releasePause();
        }
    }

    private void setTraderBankCoinsExhausted(boolean value)
    {
        Field field = bankCoinsExhaustedField;
        if (field == null) return;
        try
        {
            field.setBoolean(traderScript, value);
        }
        catch (IllegalAccessException ex)
        {
            log.debug("Could not update High Alch bank coin fallback", ex);
        }
    }

    private void acquirePause()
    {
        if (!Microbot.pauseAllScripts.get())
        {
            Microbot.pauseAllScripts.set(true);
            pauseOwned = true;
        }
    }

    private void releasePause()
    {
        if (pauseOwned)
        {
            Microbot.pauseAllScripts.set(false);
            pauseOwned = false;
        }
    }

    public synchronized void shutdown()
    {
        stopping = true;
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) current.shutdownNow();
        if (forcedBankExhausted)
        {
            setTraderBankCoinsExhausted(false);
            forcedBankExhausted = false;
        }
        releasePause();
        normalising = false;
        normalisedReserve = -1L;
        status = "Stopped";
    }
}
