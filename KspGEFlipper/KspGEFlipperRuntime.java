package net.runelite.client.plugins.microbot.kspgeflipper;

import javax.inject.Inject;

/** Selects either the full backend engine or the proven local fallback. */
final class KspGEFlipperRuntime {
    private static volatile long startedAt;
    static volatile String engine = "Stopped";
    static volatile String backend = "-";
    static volatile String explanation = "-";
    static volatile String dump = "Off";
    static volatile int itemId = -1;
    static volatile String accountKey = "default";

    @Inject private KspGEFlipperScript local;
    private KspGEFlipperBackendScript remote;
    private boolean localRunning;

    boolean run(KspGEFlipperConfig config) {
        shutdown();
        startedAt = System.currentTimeMillis();
        KspGEFlipperConfig.EngineMode mode = config.engineMode();
        if (mode == KspGEFlipperConfig.EngineMode.LOCAL) return startLocal(config, "Local deterministic/self-calibrating");

        KspGEFlipperBackendClient probe = new KspGEFlipperBackendClient(config.backendUrl(), config.backendApiKey());
        if (mode == KspGEFlipperConfig.EngineMode.SERVER || probe.healthy()) {
            remote = new KspGEFlipperBackendScript();
            boolean ok = remote.run(config);
            engine = "Server";
            backend = ok ? "Connected/starting" : "Failed";
            return ok;
        }

        if (config.backendFallback()) return startLocal(config, "Local fallback (server unavailable)");
        engine = "Server unavailable";
        backend = "Unavailable";
        KspGEFlipperScript.status = "Backend unavailable; fallback disabled";
        return false;
    }

    void refreshOverlayState() {
        if (remote != null) {
            backend = remote.backendStatus();
            explanation = remote.explanation();
            dump = remote.dumpStatus();
            KspGEFlipperBackendDtos.DumpSignal signal = remote.latestDump();
            if (signal != null) dump = signal.name + " " + Math.round(signal.recoveryProbability * 100.0) + "%";
        }
    }

    static java.time.Duration runtime() { return java.time.Duration.ofMillis(Math.max(0, System.currentTimeMillis()-startedAt)); }

    void shutdown() {
        if (remote != null) {
            remote.shutdown();
            remote = null;
        }
        if (localRunning && local != null) {
            local.shutdown();
            localRunning = false;
        }
        engine = "Stopped";
        backend = "-";
        explanation = "-";
        dump = "Off";
        itemId = -1;
    }

    private boolean startLocal(KspGEFlipperConfig config, String label) {
        localRunning = local.run(config);
        engine = label;
        backend = "Not used";
        return localRunning;
    }
}
