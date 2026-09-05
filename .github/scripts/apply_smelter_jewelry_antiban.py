from pathlib import Path


def replace_once(path: Path, old: str, new: str):
    text = path.read_text(encoding='utf-8')
    if old not in text:
        raise RuntimeError(f'Expected block not found in {path}: {old[:120]!r}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


def replace_all_required(path: Path, old: str, new: str, minimum: int = 1):
    text = path.read_text(encoding='utf-8')
    count = text.count(old)
    if count < minimum:
        raise RuntimeError(f'Expected at least {minimum} matches in {path}, found {count}: {old[:120]!r}')
    path.write_text(text.replace(old, new), encoding='utf-8')


SMART_PROFILE = '''package net.runelite.client.plugins.microbot.kspsmartsmelter;

/** Presets for Smart Smelter task-aware anti-ban behavior. */
public enum SmartSmelterAntibanProfile {
    LIGHT(0.08, 450, 1_300, 18, 30, 4_000, 11_000, 0.18, 90_000, 180_000, 0.06),
    BALANCED(0.15, 650, 2_100, 10, 18, 8_000, 20_000, 0.32, 60_000, 130_000, 0.10),
    HIGH(0.24, 850, 3_000, 7, 13, 12_000, 30_000, 0.48, 45_000, 100_000, 0.14);

    final double shortPauseChance;
    final int shortPauseMinMillis;
    final int shortPauseMaxMillis;
    final int longBreakBatchMin;
    final int longBreakBatchMax;
    final int longBreakMinMillis;
    final int longBreakMaxMillis;
    final double moveMouseAwayChance;
    final int waitingMouseMinMillis;
    final int waitingMouseMaxMillis;
    final double offerTimeoutJitterFraction;

    SmartSmelterAntibanProfile(
            double shortPauseChance,
            int shortPauseMinMillis,
            int shortPauseMaxMillis,
            int longBreakBatchMin,
            int longBreakBatchMax,
            int longBreakMinMillis,
            int longBreakMaxMillis,
            double moveMouseAwayChance,
            int waitingMouseMinMillis,
            int waitingMouseMaxMillis,
            double offerTimeoutJitterFraction
    ) {
        this.shortPauseChance = shortPauseChance;
        this.shortPauseMinMillis = shortPauseMinMillis;
        this.shortPauseMaxMillis = shortPauseMaxMillis;
        this.longBreakBatchMin = longBreakBatchMin;
        this.longBreakBatchMax = longBreakBatchMax;
        this.longBreakMinMillis = longBreakMinMillis;
        this.longBreakMaxMillis = longBreakMaxMillis;
        this.moveMouseAwayChance = moveMouseAwayChance;
        this.waitingMouseMinMillis = waitingMouseMinMillis;
        this.waitingMouseMaxMillis = waitingMouseMaxMillis;
        this.offerTimeoutJitterFraction = offerTimeoutJitterFraction;
    }
}
'''

SMART_CONTROLLER = '''package net.runelite.client.plugins.microbot.kspsmartsmelter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.SmartSmelterState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Smart-Smelter-specific humanization that only acts in safe idle windows. */
@Slf4j
final class SmartSmelterAntibanController {
    private final KspSmartSmelterConfig config;
    private long sessionStartedAt;
    private long pauseUntil;
    private String pauseReason;
    private boolean mouseMovedForPause;
    private long nextWaitingMouseAt;
    private int processedBatchesSinceLongBreak;
    private int nextLongBreakBatch;
    private volatile String activity = "Active";

    SmartSmelterAntibanController(KspSmartSmelterConfig config) {
        this.config = config;
        reset();
    }

    void reset() {
        SmartSmelterAntibanProfile profile = profile();
        sessionStartedAt = System.currentTimeMillis();
        pauseUntil = nextWaitingMouseAt = 0L;
        pauseReason = null;
        mouseMovedForPause = false;
        processedBatchesSinceLongBreak = 0;
        nextLongBreakBatch = randomBetween(profile.longBreakBatchMin, profile.longBreakBatchMax);
        activity = enabled() ? "Active" : "Disabled";
    }

    boolean beforeTick(SmartSmelterState state) {
        if (!enabled()) {
            activity = "Disabled";
            return false;
        }

        long now = System.currentTimeMillis();
        if (pauseUntil > now) {
            if (!isPauseSafeState(state) || criticalEditorOpen()) {
                clearPause();
                activity = "Active";
                return false;
            }
            if (!mouseMovedForPause && roll(profile().moveMouseAwayChance)) {
                moveMouseAway("pause");
                mouseMovedForPause = true;
            }
            activity = (pauseReason == null ? "Pausing" : pauseReason) + " (" + getPauseSeconds() + "s)";
            return true;
        }

        if (pauseUntil > 0L) clearPause();
        if (state == SmartSmelterState.WAITING_FOR_OFFERS || state == SmartSmelterState.NO_PROFITABLE_ROUTE) {
            handleWaitingIdle(now);
        } else {
            activity = "Active";
        }
        return false;
    }

    void onProductionStarted() {
        SmartSmelterAntibanProfile profile = profile();
        if (enabled() && !criticalEditorOpen() && roll(profile.moveMouseAwayChance)) {
            moveMouseAway("production");
            activity = "Production idle";
        }
    }

    void onBatchBanked(boolean anotherBatchAvailable) {
        if (!enabled() || !anotherBatchAvailable) return;

        SmartSmelterAntibanProfile profile = profile();
        if (++processedBatchesSinceLongBreak >= nextLongBreakBatch) {
            processedBatchesSinceLongBreak = 0;
            nextLongBreakBatch = randomBetween(profile.longBreakBatchMin, profile.longBreakBatchMax);
            schedulePause(fatigueAdjusted(profile.longBreakMinMillis),
                    fatigueAdjusted(profile.longBreakMaxMillis), "Bank break");
        } else if (roll(profile.shortPauseChance)) {
            schedulePause(fatigueAdjusted(profile.shortPauseMinMillis),
                    fatigueAdjusted(profile.shortPauseMaxMillis), "Batch pause");
        }
    }

    void onGeWaitStart() {
        if (enabled() && !criticalEditorOpen() && roll(profile().moveMouseAwayChance * 0.75)) {
            moveMouseAway("GE wait");
            activity = "Waiting on GE";
        }
    }

    long jitterOfferTimeout(long baseMillis) {
        if (!enabled() || baseMillis <= 1_000L) return Math.max(1_000L, baseMillis);
        double fraction = profile().offerTimeoutJitterFraction;
        double multiplier = ThreadLocalRandom.current().nextDouble(1.0 - fraction, 1.0 + fraction);
        return Math.max(1_000L, Math.round(baseMillis * multiplier));
    }

    String getStatus() {
        if (!enabled()) return "Disabled";
        String profileName = profile().name();
        return profileName.charAt(0) + profileName.substring(1).toLowerCase(Locale.ROOT) + " · " + activity;
    }

    private long getPauseSeconds() {
        return Math.max(0L, (pauseUntil - System.currentTimeMillis() + 999L) / 1_000L);
    }

    private void handleWaitingIdle(long now) {
        SmartSmelterAntibanProfile profile = profile();
        if (nextWaitingMouseAt == 0L) {
            nextWaitingMouseAt = now + randomBetween(profile.waitingMouseMinMillis, profile.waitingMouseMaxMillis);
            activity = "Market idle";
        } else if (now >= nextWaitingMouseAt && !criticalEditorOpen()) {
            moveMouseAway("market wait");
            nextWaitingMouseAt = now + randomBetween(profile.waitingMouseMinMillis, profile.waitingMouseMaxMillis);
            activity = "Market idle";
        }
    }

    private void schedulePause(int minimumMillis, int maximumMillis, String reason) {
        if (criticalEditorOpen()) return;
        int duration = randomBetween(minimumMillis, maximumMillis);
        pauseUntil = System.currentTimeMillis() + duration;
        pauseReason = activity = reason;
        mouseMovedForPause = false;
        log.debug("KSP Smart Smelter anti-ban scheduled {} for {}ms", reason, duration);
    }

    private void clearPause() {
        pauseUntil = 0L;
        pauseReason = null;
        mouseMovedForPause = false;
    }

    private boolean criticalEditorOpen() {
        try {
            return Rs2GrandExchange.isOfferScreenOpen()
                    || Rs2Widget.isProductionWidgetOpen()
                    || Rs2Widget.hasWidget("Set a price for each item:")
                    || Rs2Widget.hasWidget("How many do you wish to make")
                    || Rs2Widget.hasWidget("How many would you like to make");
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isPauseSafeState(SmartSmelterState state) {
        return state == SmartSmelterState.STARTING
                || state == SmartSmelterState.SCANNING
                || state == SmartSmelterState.BANKING
                || state == SmartSmelterState.WAITING_FOR_OFFERS
                || state == SmartSmelterState.NO_PROFITABLE_ROUTE;
    }

    private void moveMouseAway(String reason) {
        try {
            Rs2Antiban.moveMouseOffScreen();
            log.debug("KSP Smart Smelter anti-ban mouse away: {}", reason);
        } catch (Exception ex) {
            log.debug("KSP Smart Smelter anti-ban mouse-away skipped: {}", ex.getMessage());
        }
    }

    private int fatigueAdjusted(int baseMillis) {
        double hours = Math.max(0L, System.currentTimeMillis() - sessionStartedAt) / 3_600_000.0;
        return Math.max(1, (int) Math.round(baseMillis * (1.0 + Math.min(0.25, hours * 0.06))));
    }

    private boolean enabled() {
        return config != null && config.customAntiban();
    }

    private SmartSmelterAntibanProfile profile() {
        return config == null || config.antibanProfile() == null
                ? SmartSmelterAntibanProfile.BALANCED
                : config.antibanProfile();
    }

    private static boolean roll(double chance) {
        return chance > 0.0 && ThreadLocalRandom.current().nextDouble() < Math.min(1.0, chance);
    }

    private static int randomBetween(int minimum, int maximum) {
        int low = Math.min(minimum, maximum);
        int high = Math.max(minimum, maximum);
        return low == high ? low : ThreadLocalRandom.current().nextInt(low, high + 1);
    }
}
'''

JEWELRY_PROFILE = '''package net.runelite.client.plugins.microbot.kspjewelrycrafter;

/** Presets for Jewellery Crafter task-aware anti-ban behavior. */
public enum JewelryAntibanProfile {
    LIGHT(0.08, 450, 1_300, 18, 30, 4_000, 11_000, 0.18, 90_000, 180_000),
    BALANCED(0.15, 650, 2_100, 10, 18, 8_000, 20_000, 0.32, 60_000, 130_000),
    HIGH(0.24, 850, 3_000, 7, 13, 12_000, 30_000, 0.48, 45_000, 100_000);

    final double shortPauseChance;
    final int shortPauseMinMillis;
    final int shortPauseMaxMillis;
    final int longBreakBatchMin;
    final int longBreakBatchMax;
    final int longBreakMinMillis;
    final int longBreakMaxMillis;
    final double moveMouseAwayChance;
    final int waitingMouseMinMillis;
    final int waitingMouseMaxMillis;

    JewelryAntibanProfile(
            double shortPauseChance,
            int shortPauseMinMillis,
            int shortPauseMaxMillis,
            int longBreakBatchMin,
            int longBreakBatchMax,
            int longBreakMinMillis,
            int longBreakMaxMillis,
            double moveMouseAwayChance,
            int waitingMouseMinMillis,
            int waitingMouseMaxMillis
    ) {
        this.shortPauseChance = shortPauseChance;
        this.shortPauseMinMillis = shortPauseMinMillis;
        this.shortPauseMaxMillis = shortPauseMaxMillis;
        this.longBreakBatchMin = longBreakBatchMin;
        this.longBreakBatchMax = longBreakBatchMax;
        this.longBreakMinMillis = longBreakMinMillis;
        this.longBreakMaxMillis = longBreakMaxMillis;
        this.moveMouseAwayChance = moveMouseAwayChance;
        this.waitingMouseMinMillis = waitingMouseMinMillis;
        this.waitingMouseMaxMillis = waitingMouseMaxMillis;
    }
}
'''

JEWELRY_CONTROLLER = '''package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Jewellery-Crafter-specific humanization that only acts in safe idle windows. */
@Slf4j
final class JewelryAntibanController {
    private final KspJewelryCrafterConfig config;
    private long sessionStartedAt;
    private long pauseUntil;
    private String pauseReason;
    private boolean mouseMovedForPause;
    private long nextWaitingMouseAt;
    private int processedBatchesSinceLongBreak;
    private int nextLongBreakBatch;
    private volatile String activity = "Active";

    JewelryAntibanController(KspJewelryCrafterConfig config) {
        this.config = config;
        reset();
    }

    void reset() {
        JewelryAntibanProfile profile = profile();
        sessionStartedAt = System.currentTimeMillis();
        pauseUntil = nextWaitingMouseAt = 0L;
        pauseReason = null;
        mouseMovedForPause = false;
        processedBatchesSinceLongBreak = 0;
        nextLongBreakBatch = randomBetween(profile.longBreakBatchMin, profile.longBreakBatchMax);
        activity = enabled() ? "Active" : "Disabled";
    }

    boolean beforeTick(JewelryCrafterState state, boolean marketWaiting) {
        if (!enabled()) {
            activity = "Disabled";
            return false;
        }

        long now = System.currentTimeMillis();
        if (pauseUntil > now) {
            if (!isPauseSafeState(state) || criticalEditorOpen()) {
                clearPause();
                activity = "Active";
                return false;
            }
            if (!mouseMovedForPause && roll(profile().moveMouseAwayChance)) {
                moveMouseAway("pause");
                mouseMovedForPause = true;
            }
            activity = (pauseReason == null ? "Pausing" : pauseReason) + " (" + getPauseSeconds() + "s)";
            return true;
        }

        if (pauseUntil > 0L) clearPause();
        if (marketWaiting || state == JewelryCrafterState.WAITING) {
            handleWaitingIdle(now);
        } else {
            activity = "Active";
        }
        return false;
    }

    void onProductionStarted() {
        JewelryAntibanProfile profile = profile();
        if (enabled() && !criticalEditorOpen() && roll(profile.moveMouseAwayChance)) {
            moveMouseAway("production");
            activity = "Production idle";
        }
    }

    void onBatchBanked(boolean anotherBatchAvailable) {
        if (!enabled() || !anotherBatchAvailable) return;

        JewelryAntibanProfile profile = profile();
        if (++processedBatchesSinceLongBreak >= nextLongBreakBatch) {
            processedBatchesSinceLongBreak = 0;
            nextLongBreakBatch = randomBetween(profile.longBreakBatchMin, profile.longBreakBatchMax);
            schedulePause(fatigueAdjusted(profile.longBreakMinMillis),
                    fatigueAdjusted(profile.longBreakMaxMillis), "Bank break");
        } else if (roll(profile.shortPauseChance)) {
            schedulePause(fatigueAdjusted(profile.shortPauseMinMillis),
                    fatigueAdjusted(profile.shortPauseMaxMillis), "Batch pause");
        }
    }

    String getStatus() {
        if (!enabled()) return "Disabled";
        String profileName = profile().name();
        return profileName.charAt(0) + profileName.substring(1).toLowerCase(Locale.ROOT) + " · " + activity;
    }

    private long getPauseSeconds() {
        return Math.max(0L, (pauseUntil - System.currentTimeMillis() + 999L) / 1_000L);
    }

    private void handleWaitingIdle(long now) {
        JewelryAntibanProfile profile = profile();
        if (nextWaitingMouseAt == 0L) {
            nextWaitingMouseAt = now + randomBetween(profile.waitingMouseMinMillis, profile.waitingMouseMaxMillis);
            activity = "Market idle";
        } else if (now >= nextWaitingMouseAt && !criticalEditorOpen()) {
            moveMouseAway("market wait");
            nextWaitingMouseAt = now + randomBetween(profile.waitingMouseMinMillis, profile.waitingMouseMaxMillis);
            activity = "Market idle";
        }
    }

    private void schedulePause(int minimumMillis, int maximumMillis, String reason) {
        if (criticalEditorOpen()) return;
        int duration = randomBetween(minimumMillis, maximumMillis);
        pauseUntil = System.currentTimeMillis() + duration;
        pauseReason = activity = reason;
        mouseMovedForPause = false;
        log.debug("KSP Jewelry Crafter anti-ban scheduled {} for {}ms", reason, duration);
    }

    private void clearPause() {
        pauseUntil = 0L;
        pauseReason = null;
        mouseMovedForPause = false;
    }

    private boolean criticalEditorOpen() {
        try {
            return Rs2GrandExchange.isOfferScreenOpen()
                    || Rs2Widget.isProductionWidgetOpen()
                    || Rs2Widget.hasWidget("Set a price for each item:")
                    || Rs2Widget.hasWidget("How many do you wish to make")
                    || Rs2Widget.hasWidget("How many would you like to make");
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isPauseSafeState(JewelryCrafterState state) {
        return state == JewelryCrafterState.STARTING
                || state == JewelryCrafterState.EVALUATING
                || state == JewelryCrafterState.BANKING
                || state == JewelryCrafterState.WAITING;
    }

    private void moveMouseAway(String reason) {
        try {
            Rs2Antiban.moveMouseOffScreen();
            log.debug("KSP Jewelry Crafter anti-ban mouse away: {}", reason);
        } catch (Exception ex) {
            log.debug("KSP Jewelry Crafter anti-ban mouse-away skipped: {}", ex.getMessage());
        }
    }

    private int fatigueAdjusted(int baseMillis) {
        double hours = Math.max(0L, System.currentTimeMillis() - sessionStartedAt) / 3_600_000.0;
        return Math.max(1, (int) Math.round(baseMillis * (1.0 + Math.min(0.25, hours * 0.06))));
    }

    private boolean enabled() {
        return config != null && config.customAntiban();
    }

    private JewelryAntibanProfile profile() {
        return config == null || config.antibanProfile() == null
                ? JewelryAntibanProfile.BALANCED
                : config.antibanProfile();
    }

    private static boolean roll(double chance) {
        return chance > 0.0 && ThreadLocalRandom.current().nextDouble() < Math.min(1.0, chance);
    }

    private static int randomBetween(int minimum, int maximum) {
        int low = Math.min(minimum, maximum);
        int high = Math.max(minimum, maximum);
        return low == high ? low : ThreadLocalRandom.current().nextInt(low, high + 1);
    }
}
'''


def patch_smart():
    config = Path('kspsmartsmelter/KspSmartSmelterConfig.java')
    replace_once(config,
'''public interface KspSmartSmelterConfig extends Config, KspMuleConfig, KspSupportConfig {
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
''',
'''public interface KspSmartSmelterConfig extends Config, KspMuleConfig, KspSupportConfig {
    @ConfigSection(name = "Anti-ban", description = "Task-aware behavior variation that only runs in safe idle windows", position = 50)
    String antibanSection = "antiban";

    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
''')
    replace_once(config,
'''    @ConfigItem(keyName = "showOverlay", name = "Overlay", description = "Show the Smart Smelter overlay", position = 15)
    default boolean showOverlay() { return true; }
''',
'''    @ConfigItem(keyName = "showOverlay", name = "Overlay", description = "Show the Smart Smelter overlay", position = 15)
    default boolean showOverlay() { return true; }

    @ConfigItem(keyName = "customAntiban", name = "Custom anti-ban", description = "Enable Smart Smelter's safe, task-aware humanization layer", position = 0, section = antibanSection)
    default boolean customAntiban() { return true; }

    @ConfigItem(keyName = "antibanProfile", name = "Anti-ban profile", description = "Light keeps delays small, Balanced is the default, High adds longer pauses and more mouse-away behavior", position = 1, section = antibanSection)
    default SmartSmelterAntibanProfile antibanProfile() { return SmartSmelterAntibanProfile.BALANCED; }
''')

    script = Path('kspsmartsmelter/KspSmartSmelterScript.java')
    replace_once(script,
'''    private final KspSmartSmelterPlugin plugin;
    private final KspSmartSmelterConfig config;
''',
'''    private final KspSmartSmelterPlugin plugin;
    private final KspSmartSmelterConfig config;
    private final SmartSmelterAntibanController antiban;
''')
    replace_once(script,
'''    private volatile long bankInteractionSentAt;
    private volatile long furnaceInteractionSentAt;
''',
'''    private volatile long bankInteractionSentAt;
    private volatile long furnaceInteractionSentAt;
    private volatile int antibanHandledTrips;
''')
    replace_once(script,
'''        this.plugin = plugin;
        this.config = config;
    }
''',
'''        this.plugin = plugin;
        this.config = config;
        this.antiban = new SmartSmelterAntibanController(config);
    }
''')
    replace_once(script,
'''        bankInteractionSentAt = 0L;
        furnaceInteractionSentAt = 0L;

        mainScheduledFuture''',
'''        bankInteractionSentAt = 0L;
        furnaceInteractionSentAt = 0L;
        antibanHandledTrips = 0;
        antiban.reset();

        mainScheduledFuture''')
    replace_once(script,
'''                if (shouldScanPrices()) {
                    refreshRoute();
                }
''',
'''                if (antiban.beforeTick(state)) {
                    return;
                }

                if (shouldScanPrices()) {
                    refreshRoute();
                }
''')
    replace_once(script,
'''        depositProductionInventory(route);

        int availableCycles = bankCycles(route);
        if (availableCycles <= 0) {
''',
'''        depositProductionInventory(route);

        int availableCycles = bankCycles(route);
        if (completedTrips > antibanHandledTrips) {
            antibanHandledTrips = completedTrips;
            antiban.onBatchBanked(availableCycles > 0);
            if (antiban.beforeTick(state)) {
                return;
            }
        }
        if (availableCycles <= 0) {
''')
    replace_once(script,
'''        if (!started) {
            Microbot.status = "Could not start " + route.getOutputName();
            return;
        }

        long timeout =''',
'''        if (!started) {
            Microbot.status = "Could not start " + route.getOutputName();
            return;
        }
        antiban.onProductionStarted();

        long timeout =''')
    replace_once(script,
'''        state = SmartSmelterState.WAITING_FOR_OFFERS;
        Microbot.status = SmartSmelterGeTrader.hasOpenOffers()
                ? "Waiting for GE restock offers"
                : "Restock offers completed";
        sleep(Math.max(3, config.offerWaitSeconds()) * 1000);
''',
'''        state = SmartSmelterState.WAITING_FOR_OFFERS;
        Microbot.status = SmartSmelterGeTrader.hasOpenOffers()
                ? "Waiting for GE restock offers"
                : "Restock offers completed";
        antiban.onGeWaitStart();
        long restockWait = antiban.jitterOfferTimeout(Math.max(3, config.offerWaitSeconds()) * 1000L);
        sleep((int) Math.min(Integer.MAX_VALUE, restockWait));
''')
    replace_once(script,
'''            state = SmartSmelterState.WAITING_FOR_OFFERS;
            Microbot.status = "Selling " + route.getOutputName();
            sleep(Math.max(3, config.offerWaitSeconds()) * 1000);
            SmartSmelterGeTrader.collectCompletedToBank();
''',
'''            state = SmartSmelterState.WAITING_FOR_OFFERS;
            Microbot.status = "Selling " + route.getOutputName();
            antiban.onGeWaitStart();
            long sellWait = antiban.jitterOfferTimeout(Math.max(3, config.offerWaitSeconds()) * 1000L);
            sleep((int) Math.min(Integer.MAX_VALUE, sellWait));
            SmartSmelterGeTrader.collectCompletedToBank();
''')
    replace_once(script,
'''    public int getSelectedInventoryCycles() {
        RouteQuote quote = selectedQuote;
        return quote == null ? 0 : inventoryCycles(quote.getRoute());
    }

    @Override
''',
'''    public int getSelectedInventoryCycles() {
        RouteQuote quote = selectedQuote;
        return quote == null ? 0 : inventoryCycles(quote.getRoute());
    }

    public String getAntibanStatus() {
        return antiban.getStatus();
    }

    @Override
''')

    overlay = Path('kspsmartsmelter/KspSmartSmelterOverlay.java')
    replace_once(overlay,
'''            add("Ranking", config.rankingMode().toString());

            RouteQuote quote =''',
'''            add("Ranking", config.rankingMode().toString());
            add("Anti-ban", shorten(script.getAntibanStatus(), 31));

            RouteQuote quote =''')

    Path('kspsmartsmelter/SmartSmelterAntibanProfile.java').write_text(SMART_PROFILE, encoding='utf-8')
    Path('kspsmartsmelter/SmartSmelterAntibanController.java').write_text(SMART_CONTROLLER, encoding='utf-8')


def patch_jewelry():
    config = Path('kspjewelrycrafter/KspJewelryCrafterConfig.java')
    replace_once(config,
'''public interface KspJewelryCrafterConfig extends Config, KspMuleConfig, KspSupportConfig
{
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
''',
'''public interface KspJewelryCrafterConfig extends Config, KspMuleConfig, KspSupportConfig
{
    @ConfigSection(name = "Anti-ban", description = "Task-aware behavior variation that only runs in safe idle windows", position = 50)
    String antibanSection = "antiban";

    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
''')
    replace_once(config,
'''    @ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show status, membership, recipe and live profitability", position = 11)
    default boolean showOverlay() { return true; }
''',
'''    @ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show status, membership, recipe and live profitability", position = 11)
    default boolean showOverlay() { return true; }

    @ConfigItem(keyName = "customAntiban", name = "Custom anti-ban", description = "Enable Jewellery Crafter's safe, task-aware humanization layer", position = 0, section = antibanSection)
    default boolean customAntiban() { return true; }

    @ConfigItem(keyName = "antibanProfile", name = "Anti-ban profile", description = "Light keeps delays small, Balanced is the default, High adds longer pauses and more mouse-away behavior", position = 1, section = antibanSection)
    default JewelryAntibanProfile antibanProfile() { return JewelryAntibanProfile.BALANCED; }
''')

    script = Path('kspjewelrycrafter/KspJewelryCrafterScript.java')
    replace_once(script,
'''    private KspJewelryCrafterConfig config;
    private final JewelryPriceService prices = new JewelryPriceService();
''',
'''    private KspJewelryCrafterConfig config;
    private final JewelryPriceService prices = new JewelryPriceService();
    private JewelryAntibanController antiban;
''')
    replace_once(script,
'''    private long bankInteractionSentAt;
    private long furnaceInteractionSentAt;
''',
'''    private long bankInteractionSentAt;
    private long furnaceInteractionSentAt;
    private long antibanHandledCraftedCount;
''')
    replace_once(script,
'''        this.config = config;
        state = JewelryCrafterState.STARTING;
''',
'''        this.config = config;
        this.antiban = new JewelryAntibanController(config);
        this.antiban.reset();
        state = JewelryCrafterState.STARTING;
''')
    replace_once(script,
'''        bankInteractionSentAt = furnaceInteractionSentAt = 0L;
        resetCraftingMonitor();
''',
'''        bankInteractionSentAt = furnaceInteractionSentAt = 0L;
        antibanHandledCraftedCount = 0L;
        resetCraftingMonitor();
''')
    replace_once(script,
'''                if (!super.run()) return;
                tick();
''',
'''                if (!super.run()) return;
                boolean marketWaiting = pendingOffer != null || activeBuyOrders() > 0;
                if (antiban != null && antiban.beforeTick(state, marketWaiting)) return;
                tick();
''')
    replace_once(script,
'''        if (!depositCraftedOutput()) return;

        int available = availableInputUnits();
        int mouldId = recipeMouldId();
        boolean hasMould = mouldId > 0 && (inventoryCountById(mouldId) > 0 || bankCountById(mouldId) > 0);
''',
'''        if (!depositCraftedOutput()) return;

        int available = availableInputUnits();
        int mouldId = recipeMouldId();
        boolean hasMould = mouldId > 0 && (inventoryCountById(mouldId) > 0 || bankCountById(mouldId) > 0);
        if (antiban != null && craftedCount > antibanHandledCraftedCount) {
            antibanHandledCraftedCount = craftedCount;
            antiban.onBatchBanked(hasMould && available > 0);
            if (antiban.beforeTick(state, false)) return;
        }
''')
    replace_once(script,
'''        status = "Starting " + activeRecipe.getOutputName();
        sleepUntil(() -> Rs2Player.isAnimating() || craftingInventoryChanged(), 5_000);
        if (craftingInventoryChanged()) observeCraftingProgress();
''',
'''        status = "Starting " + activeRecipe.getOutputName();
        boolean productionStarted = sleepUntil(() -> Rs2Player.isAnimating() || craftingInventoryChanged(), 5_000);
        if (productionStarted && antiban != null) antiban.onProductionStarted();
        if (craftingInventoryChanged()) observeCraftingProgress();
''')
    replace_once(script,
'''    public int getCurrentBatchTarget() { return currentBatchTarget; }

    public boolean run''',
'''    public int getCurrentBatchTarget() { return currentBatchTarget; }
    public String getAntibanStatus() { return antiban == null ? "Disabled" : antiban.getStatus(); }

    public boolean run''')

    overlay = Path('kspjewelrycrafter/KspJewelryCrafterOverlay.java')
    replace_once(overlay,
'''        addLine("Status", script.getStatus());
''',
'''        addLine("Status", script.getStatus());
        addLine("Anti-ban", script.getAntibanStatus());
''')

    Path('kspjewelrycrafter/JewelryAntibanProfile.java').write_text(JEWELRY_PROFILE, encoding='utf-8')
    Path('kspjewelrycrafter/JewelryAntibanController.java').write_text(JEWELRY_CONTROLLER, encoding='utf-8')


def validate():
    smart = Path('kspsmartsmelter/KspSmartSmelterScript.java').read_text(encoding='utf-8')
    jewelry = Path('kspjewelrycrafter/KspJewelryCrafterScript.java').read_text(encoding='utf-8')
    checks = {
        'smart beforeTick': 'antiban.beforeTick(state)' in smart,
        'smart production hook': 'antiban.onProductionStarted()' in smart,
        'smart bank hook': 'antiban.onBatchBanked(availableCycles > 0)' in smart,
        'smart overlay getter': 'getAntibanStatus()' in smart,
        'jewelry beforeTick': 'antiban.beforeTick(state, marketWaiting)' in jewelry,
        'jewelry production hook': 'antiban.onProductionStarted()' in jewelry,
        'jewelry bank hook': 'antiban.onBatchBanked(hasMould && available > 0)' in jewelry,
        'jewelry overlay getter': 'getAntibanStatus()' in jewelry,
    }
    failed = [name for name, ok in checks.items() if not ok]
    if failed:
        raise RuntimeError('Anti-ban validation failed: ' + ', '.join(failed))


if __name__ == '__main__':
    patch_smart()
    patch_jewelry()
    validate()
    print('Smart Smelter and Jewellery Crafter anti-ban patch applied.')
