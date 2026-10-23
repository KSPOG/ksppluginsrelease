package net.runelite.client.plugins.microbot.kspaccountbuilder;

/**
 * Cooperative pause switch scoped to KSP Account Builder child scripts.
 *
 * The shared Microbot.pauseAllScripts flag would also stop unrelated plugins.
 * Child task loops instead check this gate before each scheduled tick.
 * No child is shut down, no task is re-initialized, and no executor is
 * cancelled when the Account Builder is paused.
 *
 * Already-running actions finish their current iteration; this is not an
 * unsafe Thread.suspend or a cancellation of in-flight game interactions.
 */
public final class KspAccountBuilderPauseGate
{
    private static volatile boolean paused;

    private KspAccountBuilderPauseGate() {}

    public static boolean isPaused() { return paused; }

    static void setPaused(boolean value) { paused = value; }
}
