package net.runelite.client.plugins.microbot.kspgeflipper;

import javax.inject.Inject;

/** Selects embedded intelligence, optional remote sharing, or the legacy local fallback. */
final class KspGEFlipperRuntime {
    private static volatile long startedAt;
    static volatile String engine = "Stopped";
    static volatile String backend = "-";
    static volatile String explanation = "-";
    static volatile String dump = "Off";
    static volatile int itemId = -1;
    static volatile String accountKey = "default";

    @Inject private KspGEFlipperScript local;
    private KspGEFlipperEmbeddedScript embedded;
    private KspGEFlipperBackendScript remote;
    private boolean localRunning;

    boolean run(KspGEFlipperConfig config) {
        shutdown();
        startedAt = System.currentTimeMillis();
        KspGEFlipperConfig.EngineMode mode = config.engineMode();
        if (mode == KspGEFlipperConfig.EngineMode.LOCAL) return startLocal(config, "Legacy local");

        if (mode == KspGEFlipperConfig.EngineMode.EMBEDDED || mode == KspGEFlipperConfig.EngineMode.AUTO) {
            if (startEmbedded(config)) return true;
            if (mode == KspGEFlipperConfig.EngineMode.AUTO && remoteHealthy(config) && startRemote(config)) return true;
            if (config.backendFallback()) return startLocal(config, "Legacy fallback (embedded unavailable)");
            engine = "Embedded unavailable";
            backend = "Unavailable";
            KspGEFlipperScript.status = "Embedded engine unavailable; fallback disabled";
            return false;
        }

        // REMOTE and legacy SERVER both mean explicit shared-backend mode.
        if (remoteHealthy(config) && startRemote(config)) return true;
        if (config.backendFallback()) {
            if (startEmbedded(config)) return true;
            return startLocal(config, "Legacy fallback (remote + embedded unavailable)");
        }
        engine = "Remote unavailable";
        backend = "Unavailable";
        KspGEFlipperScript.status = "Remote backend unavailable; fallback disabled";
        return false;
    }

    void refreshOverlayState() {
        if (embedded != null) {
            backend = embedded.engineStatus();
            explanation = embedded.explanation();
            KspGEFlipperBackendDtos.DumpSignal signal = embedded.latestDump();
            dump = signal == null ? dump : signal.name + " " + Math.round(signal.recoveryProbability * 100.0) + "%";
        } else if (remote != null) {
            backend = remote.backendStatus();
            explanation = remote.explanation();
            dump = remote.dumpStatus();
            KspGEFlipperBackendDtos.DumpSignal signal = remote.latestDump();
            if (signal != null) dump = signal.name + " " + Math.round(signal.recoveryProbability * 100.0) + "%";
        }
    }

    KspGEFlipperEmbeddedScript.JsonAccess embeddedData() {
        return embedded == null ? null : embedded.data();
    }

    static java.time.Duration runtime() {
        return java.time.Duration.ofMillis(Math.max(0, System.currentTimeMillis() - startedAt));
    }

    void shutdown() {
        if (embedded != null) {
            embedded.shutdown();
            embedded = null;
        }
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

    private boolean startEmbedded(KspGEFlipperConfig config) {
        try {
            embedded = new KspGEFlipperEmbeddedScript();
            boolean ok = embedded.run(config);
            if (!ok) {
                embedded = null;
                return false;
            }
            engine = "Embedded";
            backend = "In-process";
            return true;
        } catch (Exception e) {
            if (embedded != null) embedded.shutdown();
            embedded = null;
            return false;
        }
    }

    private boolean startRemote(KspGEFlipperConfig config) {
        try {
            remote = new KspGEFlipperBackendScript();
            boolean ok = remote.run(config);
            if (!ok) {
                remote = null;
                return false;
            }
            engine = "Remote";
            backend = "Connected/starting";
            return true;
        } catch (Exception e) {
            if (remote != null) remote.shutdown();
            remote = null;
            return false;
        }
    }

    private boolean remoteHealthy(KspGEFlipperConfig config) {
        return new KspGEFlipperBackendClient(config.backendUrl(), config.backendApiKey()).healthy();
    }

    private boolean startLocal(KspGEFlipperConfig config, String label) {
        localRunning = local.run(config);
        engine = label;
        backend = "Not used";
        return localRunning;
    }
}
