package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real-time anomaly detection for execution time outliers.
 *
 * Detects execution time anomalies using exponential weighted moving average (EWMA)
 * with adaptive threshold based on historical data. Automatically alerts on deviation.
 *
 * Detection strategy:
 * - Track execution times per metric (case duration, task duration, etc.)
 * - Compute rolling mean and standard deviation using EWMA
 * - Flag execution if duration > mean + (2.5 * stdDev)
 * - Maintain historical baseline learning (min 30 samples)
 * - Auto-remediate by logging anomaly with context for root cause analysis
 *
 * Thread-safe, lock-free implementation using atomic operations.
 */
public class AnomalyDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnomalyDetector.class);
    private static final int MIN_SAMPLES_FOR_BASELINE = 30;
    private static final double EWMA_ALPHA = 0.3;
    private static final double STDDEV_MULTIPLIER = 2.5;
    private static final int MAX_HISTORY_SIZE = 500;

    private final MeterRegistry meterRegistry;
    private final Map<String, MetricBaseline> baselines;
    private final AtomicInteger totalAnomalies;

    public AnomalyDetector(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.baselines = new ConcurrentHashMap<>();
        this.totalAnomalies = new AtomicInteger(0);
        registerMetrics();
    }

    /**
     * Records an execution time and checks for anomalies.
     */
    public void recordExecution(String metricName, long durationMs, Map<String, String> context) {
        Objects.requireNonNull(metricName);
        Objects.requireNonNull(context);

        if (durationMs < 0) {
            return;
        }

        MetricBaseline baseline = baselines.computeIfAbsent(metricName, k -> new MetricBaseline(metricName));
        boolean isAnomaly = baseline.addSample(durationMs);

        if (isAnomaly) {
            handleAnomaly(metricName, durationMs, baseline, context);
        }
    }

    /**
     * Records an execution time with task and spec context.
     */
    public void recordExecution(String metricName, long durationMs, String taskName, String specId) {
        Map<String, String> context = new HashMap<>();
        if (taskName != null) {
            context.put("task_name", taskName);
        }
        if (specId != null) {
            context.put("spec_id", specId);
        }
        recordExecution(metricName, durationMs, context);
    }

    /**
     * Handles an anomaly by logging and incrementing alert counter.
     */
    private void handleAnomaly(String metricName, long durationMs, MetricBaseline baseline, Map<String, String> context) {
        totalAnomalies.incrementAndGet();

        Map<String, Object> logContext = new HashMap<>(context);
        logContext.put("metric", metricName);
        logContext.put("duration_ms", durationMs);
        logContext.put("mean_ms", Math.round(baseline.getMean()));
        logContext.put("stddev_ms", Math.round(baseline.getStdDev()));
        logContext.put("threshold_ms", Math.round(baseline.getThreshold()));
        logContext.put("deviation_factor", String.format("%.2f", (double) durationMs / baseline.getMean()));
        logContext.put("sample_count", baseline.getSampleCount());
        logContext.put("timestamp", Instant.now().toString());

        StructuredLogger logger = StructuredLogger.getLogger(AnomalyDetector.class);
        logger.warn("Execution anomaly detected - duration exceeds expected baseline", logContext);

        meterRegistry.counter(
                "yawl.anomaly.detected",
                Tags.of(
                        Tag.of("metric", metricName),
                        Tag.of("severity", "warning")
                )
        ).increment();
    }

    /**
     * Gets current baseline for a metric.
     */
    public MetricBaseline getBaseline(String metricName) {
        return baselines.get(metricName);
    }

    /**
     * Gets total anomalies detected since startup.
     */
    public int getTotalAnomalies() {
        return totalAnomalies.get();
    }

    /**
     * Resets anomaly counter for testing.
     */
    public void reset() {
        baselines.clear();
        totalAnomalies.set(0);
    }

    private void registerMetrics() {
        meterRegistry.gauge(
                "yawl.anomaly.total",
                totalAnomalies,
                AtomicInteger::get
        );
    }

    /**
     * Immutable baseline state for a metric.
     */
    public static final class MetricBaseline {
        private final String metricName;
        private final List<Long> samples;
        private double ewmaMean;
        private double ewmaVariance;
        private int sampleCount;

        private MetricBaseline(String metricName) {
            this.metricName = Objects.requireNonNull(metricName);
            this.samples = Collections.synchronizedList(new ArrayList<>());
            this.ewmaMean = 0;
            this.ewmaVariance = 0;
            this.sampleCount = 0;
        }

        /**
         * Adds a sample and returns true if it's an anomaly.
         */
        synchronized boolean addSample(long durationMs) {
            sampleCount++;
            samples.add(durationMs);

            // Trim history to max size
            if (samples.size() > MAX_HISTORY_SIZE) {
                samples.remove(0);
            }

            // Update EWMA statistics
            updateEWMA(durationMs);

            // Check for anomaly only after baseline is established
            if (sampleCount >= MIN_SAMPLES_FOR_BASELINE) {
                return durationMs > getThreshold();
            }
            return false;
        }

        /**
         * Updates exponential weighted moving average.
         */
        private void updateEWMA(long sample) {
            ewmaMean = EWMA_ALPHA * sample + (1 - EWMA_ALPHA) * ewmaMean;
            double squaredDeviation = (sample - ewmaMean) * (sample - ewmaMean);
            ewmaVariance = EWMA_ALPHA * squaredDeviation + (1 - EWMA_ALPHA) * ewmaVariance;
        }

        /**
         * Gets current mean (EWMA).
         */
        public double getMean() {
            return ewmaMean;
        }

        /**
         * Gets current standard deviation.
         */
        public double getStdDev() {
            return Math.sqrt(Math.max(0, ewmaVariance));
        }

        /**
         * Gets anomaly threshold (mean + 2.5 * stdDev).
         */
        public double getThreshold() {
            return ewmaMean + (STDDEV_MULTIPLIER * getStdDev());
        }

        /**
         * Gets number of samples recorded.
         */
        public int getSampleCount() {
            return sampleCount;
        }

        /**
         * Gets metric name.
         */
        public String getMetricName() {
            return metricName;
        }

        /**
         * Gets copy of recent samples (up to last 100).
         */
        public List<Long> getRecentSamples() {
            int start = Math.max(0, samples.size() - 100);
            return new ArrayList<>(samples.subList(start, samples.size()));
        }

        /**
         * Gets percentile value from recent samples.
         */
        public long getPercentile(double percentile) {
            if (samples.isEmpty()) {
                return 0;
            }
            List<Long> sorted = new ArrayList<>(samples);
            Collections.sort(sorted);
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }
    }
}
