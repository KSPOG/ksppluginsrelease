package net.runelite.client.plugins.microbot.kspgeflipper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistent, conservative feedback model for the GE flipper.
 *
 * <p>The model intentionally learns only multiplicative calibration factors. It does not replace the market model;
 * it corrects systematic execution errors observed on this machine/account over time.</p>
 */
@Slf4j
final class KspGEFlipperCalibration {
    private static final int FILE_VERSION = 1;
    private static final double EPSILON = 1e-9;
    private static final String GLOBAL_ITEM = "*";

    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Map<String, ActivePrediction> active = new HashMap<>();
    private final Path path;

    KspGEFlipperCalibration() {
        String home = System.getProperty("user.home", ".");
        path = Paths.get(home, ".runelite", "ksp-ge-flipper-calibration.json");
    }

    synchronized void load() {
        buckets.clear();
        active.clear();
        if (!Files.isRegularFile(path)) return;

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonObject stored = object(root, "buckets");
            if (stored == null) return;

            for (Map.Entry<String, JsonElement> entry : stored.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                Bucket bucket = Bucket.fromJson(entry.getValue().getAsJsonObject());
                if (bucket.samples > 0) buckets.put(entry.getKey(), bucket);
            }
            log.info("KSP_GE calibration loaded path={} buckets={} samples={}", path, buckets.size(), summary().samples);
        } catch (Exception e) {
            log.warn("KSP_GE calibration load failed: {}", e.getMessage());
        }
    }

    synchronized void registerRecommendation(String suggestionId, int itemId, String type, int predictedBuy,
                                             int predictedSell, int quantity, long predictedProfit,
                                             double predictedMinutes, double predictedConfidence,
                                             double predictedExecutionProbability) {
        ActivePrediction prediction = new ActivePrediction();
        prediction.suggestionId = suggestionId;
        prediction.itemId = itemId;
        prediction.type = type;
        prediction.predictedBuy = predictedBuy;
        prediction.predictedSell = predictedSell;
        prediction.quantity = quantity;
        prediction.predictedProfit = predictedProfit;
        prediction.predictedMinutes = Math.max(0.1, predictedMinutes);
        prediction.predictedConfidence = clamp(predictedConfidence, 0.01, 0.99);
        prediction.predictedExecutionProbability = clamp(predictedExecutionProbability, 0.01, 0.99);
        prediction.createdAt = System.currentTimeMillis();
        active.put(suggestionId, prediction);
    }

    synchronized void recordModification(String suggestionId) {
        ActivePrediction prediction = active.get(suggestionId);
        if (prediction != null) prediction.modifications++;
    }

    synchronized void recordBuyFill(String suggestionId, int quantity, int actualPrice) {
        ActivePrediction prediction = active.get(suggestionId);
        if (prediction == null || quantity <= 0 || actualPrice <= 0) return;
        prediction.buyFilledAt = prediction.buyFilledAt == 0 ? System.currentTimeMillis() : prediction.buyFilledAt;
        prediction.actualBuyQuantity += quantity;
        prediction.actualBuyValue += (long) quantity * actualPrice;
    }

    synchronized void recordSellFill(String suggestionId, int quantity, int actualPrice, long realizedProfit) {
        ActivePrediction prediction = active.get(suggestionId);
        if (prediction == null || quantity <= 0 || actualPrice <= 0) return;
        prediction.actualSellQuantity += quantity;
        prediction.actualSellValue += (long) quantity * actualPrice;
        prediction.realizedProfit += realizedProfit;
    }

    synchronized void complete(String suggestionId, double learningRate) {
        ActivePrediction prediction = active.remove(suggestionId);
        if (prediction == null) return;
        updateOutcome(prediction, true, learningRate);
        save();
    }

    synchronized void abort(String suggestionId, double learningRate) {
        ActivePrediction prediction = active.remove(suggestionId);
        if (prediction == null) return;
        updateOutcome(prediction, false, learningRate);
        save();
    }

    synchronized Adjustment adjustment(int itemId, String type, boolean enabled, int warmupSamples,
                                       double maxAdjustmentPercent) {
        if (!enabled) return Adjustment.NEUTRAL;

        int warmup = Math.max(1, warmupSamples);
        Bucket global = buckets.get(key(type, GLOBAL_ITEM));
        if (global == null || global.samples < warmup) {
            int samples = global == null ? 0 : global.samples;
            return new Adjustment(1, 1, 1, 1, samples, 0, false);
        }

        Bucket item = buckets.get(key(type, Integer.toString(itemId)));
        double itemWeight = item == null ? 0 : clamp(item.samples / (double) Math.max(warmup, 20), 0, 0.70);
        Bucket blended = Bucket.blend(global, item, itemWeight);
        double limit = clamp(maxAdjustmentPercent, 0, 100) / 100.0;
        double min = Math.max(0.10, 1.0 - limit);
        double max = 1.0 + limit;

        double execution = safeDivide(blended.completionEwma, blended.predictedExecutionEwma, 1.0);
        double confidence = 1.05
                - clamp(blended.durationAbsErrorEwma, 0, 2.0) * 0.18
                - clamp(blended.profitAbsErrorEwma, 0, 2.0) * 0.15;
        double modificationRate = safeDivide(blended.modifications, Math.max(1, blended.samples), 0);
        confidence *= 1.0 / (1.0 + modificationRate * 0.08);

        return new Adjustment(
                clamp(blended.durationRatioEwma, min, max),
                clamp(execution, min, max),
                clamp(blended.profitRatioEwma, min, max),
                clamp(confidence, min, Math.min(max, 1.08)),
                blended.samples,
                modificationRate,
                true);
    }

    synchronized Summary summary() {
        Bucket normal = buckets.get(key("NORMAL", GLOBAL_ITEM));
        Bucket dump = buckets.get(key("DUMP", GLOBAL_ITEM));
        int normalSamples = normal == null ? 0 : normal.samples;
        int dumpSamples = dump == null ? 0 : dump.samples;
        int samples = normalSamples + dumpSamples;
        if (samples == 0) return Summary.EMPTY;

        double normalWeight = normalSamples / (double) samples;
        double dumpWeight = dumpSamples / (double) samples;
        double duration = value(normal, b -> b.durationRatioEwma, 1) * normalWeight
                + value(dump, b -> b.durationRatioEwma, 1) * dumpWeight;
        double execution = executionFactor(normal) * normalWeight + executionFactor(dump) * dumpWeight;
        double profit = value(normal, b -> b.profitRatioEwma, 1) * normalWeight
                + value(dump, b -> b.profitRatioEwma, 1) * dumpWeight;
        double modifications = ((normal == null ? 0 : normal.modifications) + (dump == null ? 0 : dump.modifications))
                / (double) Math.max(1, samples);
        int completed = (normal == null ? 0 : normal.completed) + (dump == null ? 0 : dump.completed);
        int aborted = (normal == null ? 0 : normal.aborted) + (dump == null ? 0 : dump.aborted);
        return new Summary(samples, completed, aborted, duration, execution, profit, modifications);
    }

    Path storagePath() {
        return path;
    }

    private void updateOutcome(ActivePrediction prediction, boolean completed, double learningRate) {
        long now = System.currentTimeMillis();
        double alpha = clamp(learningRate, 0.01, 1.0);
        double actualMinutes = Math.max(0.05, (now - prediction.createdAt) / 60_000.0);
        double durationRatio = clamp(actualMinutes / Math.max(0.1, prediction.predictedMinutes), 0.10, 10.0);
        double profitRatio = completed && prediction.predictedProfit > 0
                ? clamp(prediction.realizedProfit / (double) prediction.predictedProfit, -2.0, 5.0)
                : 0.0;
        double durationAbsError = Math.abs(actualMinutes - prediction.predictedMinutes) / Math.max(0.1, prediction.predictedMinutes);
        double profitAbsError = completed && prediction.predictedProfit != 0
                ? Math.abs(prediction.realizedProfit - prediction.predictedProfit) / (double) Math.max(1, Math.abs(prediction.predictedProfit))
                : 1.0;
        double buyRatio = prediction.actualBuyQuantity > 0 && prediction.predictedBuy > 0
                ? (prediction.actualBuyValue / (double) prediction.actualBuyQuantity) / prediction.predictedBuy : 1.0;
        double sellRatio = prediction.actualSellQuantity > 0 && prediction.predictedSell > 0
                ? (prediction.actualSellValue / (double) prediction.actualSellQuantity) / prediction.predictedSell : 1.0;

        updateBucket(key(prediction.type, GLOBAL_ITEM), prediction, completed, alpha, durationRatio, profitRatio,
                durationAbsError, profitAbsError, buyRatio, sellRatio, now);
        updateBucket(key(prediction.type, Integer.toString(prediction.itemId)), prediction, completed, alpha,
                durationRatio, profitRatio, durationAbsError, profitAbsError, buyRatio, sellRatio, now);
    }

    private void updateBucket(String key, ActivePrediction prediction, boolean completed, double alpha,
                              double durationRatio, double profitRatio, double durationAbsError,
                              double profitAbsError, double buyRatio, double sellRatio, long now) {
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
        boolean first = bucket.samples == 0;
        bucket.samples++;
        if (completed) bucket.completed++; else bucket.aborted++;
        bucket.modifications += prediction.modifications;
        bucket.lastUpdated = now;

        bucket.durationRatioEwma = ewma(bucket.durationRatioEwma, durationRatio, alpha, first);
        bucket.profitRatioEwma = ewma(bucket.profitRatioEwma, profitRatio, alpha, first);
        bucket.buyPriceRatioEwma = ewma(bucket.buyPriceRatioEwma, buyRatio, alpha, first);
        bucket.sellPriceRatioEwma = ewma(bucket.sellPriceRatioEwma, sellRatio, alpha, first);
        bucket.durationAbsErrorEwma = ewma(bucket.durationAbsErrorEwma, durationAbsError, alpha, first);
        bucket.profitAbsErrorEwma = ewma(bucket.profitAbsErrorEwma, profitAbsError, alpha, first);
        bucket.predictedExecutionEwma = ewma(bucket.predictedExecutionEwma,
                prediction.predictedExecutionProbability, alpha, first);
        bucket.completionEwma = ewma(bucket.completionEwma, completed ? 1.0 : 0.0, alpha, first);
        bucket.predictedConfidenceEwma = ewma(bucket.predictedConfidenceEwma,
                prediction.predictedConfidence, alpha, first);
    }

    private synchronized void save() {
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", FILE_VERSION);
            root.addProperty("updatedAt", System.currentTimeMillis());
            JsonObject stored = new JsonObject();
            for (Map.Entry<String, Bucket> entry : buckets.entrySet()) {
                if (entry.getValue().samples > 0) stored.add(entry.getKey(), entry.getValue().toJson());
            }
            root.add("buckets", stored);

            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("KSP_GE calibration save failed: {}", e.getMessage());
        }
    }

    private static String key(String type, String item) {
        return (type == null ? "NORMAL" : type) + ":" + item;
    }

    private static double executionFactor(Bucket bucket) {
        return bucket == null ? 1.0 : safeDivide(bucket.completionEwma, bucket.predictedExecutionEwma, 1.0);
    }

    private interface BucketValue {
        double get(Bucket bucket);
    }

    private static double value(Bucket bucket, BucketValue value, double fallback) {
        return bucket == null ? fallback : value.get(bucket);
    }

    private static JsonObject object(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private static double ewma(double oldValue, double newValue, double alpha, boolean first) {
        return first ? newValue : oldValue * (1.0 - alpha) + newValue * alpha;
    }

    private static double safeDivide(double numerator, double denominator, double fallback) {
        return Math.abs(denominator) < EPSILON ? fallback : numerator / denominator;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Adjustment {
        static final Adjustment NEUTRAL = new Adjustment(1, 1, 1, 1, 0, 0, false);
        final double durationMultiplier;
        final double executionMultiplier;
        final double profitMultiplier;
        final double confidenceMultiplier;
        final int samples;
        final double modificationRate;
        final boolean active;

        Adjustment(double durationMultiplier, double executionMultiplier, double profitMultiplier,
                   double confidenceMultiplier, int samples, double modificationRate, boolean active) {
            this.durationMultiplier = durationMultiplier;
            this.executionMultiplier = executionMultiplier;
            this.profitMultiplier = profitMultiplier;
            this.confidenceMultiplier = confidenceMultiplier;
            this.samples = samples;
            this.modificationRate = modificationRate;
            this.active = active;
        }
    }

    static final class Summary {
        static final Summary EMPTY = new Summary(0, 0, 0, 1, 1, 1, 0);
        final int samples;
        final int completed;
        final int aborted;
        final double durationMultiplier;
        final double executionMultiplier;
        final double profitMultiplier;
        final double modificationRate;

        Summary(int samples, int completed, int aborted, double durationMultiplier, double executionMultiplier,
                double profitMultiplier, double modificationRate) {
            this.samples = samples;
            this.completed = completed;
            this.aborted = aborted;
            this.durationMultiplier = durationMultiplier;
            this.executionMultiplier = executionMultiplier;
            this.profitMultiplier = profitMultiplier;
            this.modificationRate = modificationRate;
        }
    }

    private static final class ActivePrediction {
        String suggestionId;
        int itemId;
        String type;
        int predictedBuy;
        int predictedSell;
        int quantity;
        long predictedProfit;
        double predictedMinutes;
        double predictedConfidence;
        double predictedExecutionProbability;
        long createdAt;
        long buyFilledAt;
        int modifications;
        int actualBuyQuantity;
        long actualBuyValue;
        int actualSellQuantity;
        long actualSellValue;
        long realizedProfit;
    }

    private static final class Bucket {
        int samples;
        int completed;
        int aborted;
        int modifications;
        long lastUpdated;
        double durationRatioEwma = 1;
        double profitRatioEwma = 1;
        double buyPriceRatioEwma = 1;
        double sellPriceRatioEwma = 1;
        double durationAbsErrorEwma;
        double profitAbsErrorEwma;
        double predictedExecutionEwma = 1;
        double completionEwma = 1;
        double predictedConfidenceEwma = 1;

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("samples", samples);
            object.addProperty("completed", completed);
            object.addProperty("aborted", aborted);
            object.addProperty("modifications", modifications);
            object.addProperty("lastUpdated", lastUpdated);
            object.addProperty("durationRatioEwma", durationRatioEwma);
            object.addProperty("profitRatioEwma", profitRatioEwma);
            object.addProperty("buyPriceRatioEwma", buyPriceRatioEwma);
            object.addProperty("sellPriceRatioEwma", sellPriceRatioEwma);
            object.addProperty("durationAbsErrorEwma", durationAbsErrorEwma);
            object.addProperty("profitAbsErrorEwma", profitAbsErrorEwma);
            object.addProperty("predictedExecutionEwma", predictedExecutionEwma);
            object.addProperty("completionEwma", completionEwma);
            object.addProperty("predictedConfidenceEwma", predictedConfidenceEwma);
            return object;
        }

        static Bucket fromJson(JsonObject object) {
            Bucket bucket = new Bucket();
            bucket.samples = integer(object, "samples", 0);
            bucket.completed = integer(object, "completed", 0);
            bucket.aborted = integer(object, "aborted", 0);
            bucket.modifications = integer(object, "modifications", 0);
            bucket.lastUpdated = longValue(object, "lastUpdated", 0);
            bucket.durationRatioEwma = number(object, "durationRatioEwma", 1);
            bucket.profitRatioEwma = number(object, "profitRatioEwma", 1);
            bucket.buyPriceRatioEwma = number(object, "buyPriceRatioEwma", 1);
            bucket.sellPriceRatioEwma = number(object, "sellPriceRatioEwma", 1);
            bucket.durationAbsErrorEwma = number(object, "durationAbsErrorEwma", 0);
            bucket.profitAbsErrorEwma = number(object, "profitAbsErrorEwma", 0);
            bucket.predictedExecutionEwma = number(object, "predictedExecutionEwma", 1);
            bucket.completionEwma = number(object, "completionEwma", 1);
            bucket.predictedConfidenceEwma = number(object, "predictedConfidenceEwma", 1);
            return bucket;
        }

        static Bucket blend(Bucket global, Bucket item, double itemWeight) {
            if (item == null || itemWeight <= 0) return global;
            Bucket blended = new Bucket();
            double globalWeight = 1.0 - itemWeight;
            blended.samples = global.samples;
            blended.completed = global.completed;
            blended.aborted = global.aborted;
            double globalModificationRate = global.modifications / (double) Math.max(1, global.samples);
            double itemModificationRate = item.modifications / (double) Math.max(1, item.samples);
            blended.modifications = (int) Math.round((globalModificationRate * globalWeight
                    + itemModificationRate * itemWeight) * blended.samples);
            blended.durationRatioEwma = global.durationRatioEwma * globalWeight + item.durationRatioEwma * itemWeight;
            blended.profitRatioEwma = global.profitRatioEwma * globalWeight + item.profitRatioEwma * itemWeight;
            blended.buyPriceRatioEwma = global.buyPriceRatioEwma * globalWeight + item.buyPriceRatioEwma * itemWeight;
            blended.sellPriceRatioEwma = global.sellPriceRatioEwma * globalWeight + item.sellPriceRatioEwma * itemWeight;
            blended.durationAbsErrorEwma = global.durationAbsErrorEwma * globalWeight + item.durationAbsErrorEwma * itemWeight;
            blended.profitAbsErrorEwma = global.profitAbsErrorEwma * globalWeight + item.profitAbsErrorEwma * itemWeight;
            blended.predictedExecutionEwma = global.predictedExecutionEwma * globalWeight + item.predictedExecutionEwma * itemWeight;
            blended.completionEwma = global.completionEwma * globalWeight + item.completionEwma * itemWeight;
            blended.predictedConfidenceEwma = global.predictedConfidenceEwma * globalWeight + item.predictedConfidenceEwma * itemWeight;
            return blended;
        }

        private static int integer(JsonObject object, String key, int fallback) {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
        }

        private static long longValue(JsonObject object, String key, long fallback) {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : fallback;
        }

        private static double number(JsonObject object, String key, double fallback) {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : fallback;
        }
    }
}
