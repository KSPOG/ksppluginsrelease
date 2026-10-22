package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.Script;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reclaim Microbot Script executors when Account Builder switches or restarts tasks.
 * The upstream Script.shutdown() cancels futures but leaves worker threads alive.
 */
public abstract class KspManagedScript extends Script
{
    private final AtomicInteger executorThreadSequence = new AtomicInteger();

    @Override
    public void shutdown()
    {
        try
        {
            super.shutdown();
        }
        finally
        {
            ScheduledExecutorService previous = scheduledExecutorService;
            if (previous != null)
            {
                previous.shutdownNow();
            }
            ThreadFactory threadFactory = task ->
            {
                Thread thread = new Thread(task, getClass().getSimpleName() + "-" + executorThreadSequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            ScheduledThreadPoolExecutor next = new ScheduledThreadPoolExecutor(1, threadFactory);
            next.setRemoveOnCancelPolicy(true);
            scheduledExecutorService = next;
        }
    }
}
