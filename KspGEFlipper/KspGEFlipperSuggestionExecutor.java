package net.runelite.client.plugins.microbot.kspgeflipper;

interface KspGEFlipperSuggestionExecutor {
    /** Apply a newly issued recommendation. Implementations must be idempotent by suggestion id. */
    boolean execute(KspGEFlipperBackendDtos.Suggestion suggestion);

    /** Advance any non-blocking cancel/collect/relist state. */
    void tick();

    /** Human-readable executor state for the overlay. */
    String status();

    default void shutdown() {}
}
