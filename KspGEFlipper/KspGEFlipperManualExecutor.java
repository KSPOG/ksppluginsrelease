package net.runelite.client.plugins.microbot.kspgeflipper;

final class KspGEFlipperManualExecutor implements KspGEFlipperSuggestionExecutor {
    private String lastId;
    private String status = "Manual confirmation";

    @Override
    public boolean execute(KspGEFlipperBackendDtos.Suggestion suggestion) {
        if (suggestion == null || suggestion.id == null) return false;
        if (suggestion.id.equals(lastId)) return true;
        lastId = suggestion.id;
        status = "Manual: " + safe(suggestion.type) + " " + safe(suggestion.name);
        return true;
    }

    @Override public void tick() {}
    @Override public String status() { return status; }

    private static String safe(String value) { return value == null ? "" : value; }
}
