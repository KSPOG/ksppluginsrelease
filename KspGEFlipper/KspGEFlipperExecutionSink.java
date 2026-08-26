package net.runelite.client.plugins.microbot.kspgeflipper;

/** Receives completed GE executions from the RuneLite/Microbot observer. */
interface KspGEFlipperExecutionSink {
    void transaction(KspGEFlipperBackendDtos.TradeExecution execution) throws Exception;
}
