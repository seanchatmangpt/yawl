/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service Level Objective compliance tracking with rolling window analysis.
 *
 * <p>Tracks SLO compliance percentages across multiple time windows (hourly, daily, weekly)
 * and detects threshold violations. Integrates with AndonCord for violation alerts.</p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Rolling time window tracking (configurable sizes)</li>
 *   <li>Real-time compliance rate calculation</li>
 *   <li>Threshold violation detection</li>
 *   <li>Trend analysis and burn rate calculation</li>
 *   <li>Integration with AndonCord for alerts</li>
 *   <li>Thread-safe implementation with minimal locking</li>
 * </ul>
 *
 * <h2>SLO Types</h2>
 * <pre>{@code
 * CASE_COMPLETION    - 24h window, 99.9% target (24 * 60 * 60 * 1000 ms, 99.9%)
 * TASK_EXECUTION     - 1h window, 99.5% target (60 * 60 * 1000 ms, 99.5%)
 * QUEUE_RESPONSE     - 5m window, 99.0% target (5 * 60 * 1000 ms, 99.0%)
 * VT_PINNING         - Continuous, <0.1% target (0, 0.1% - lower is better)
 * LOCK_CONTENTION    - Continuous, <5.0% target (100ms threshold, 5.0% - lower is better)
 * }</pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Initialize SLO tracker
 * SLOTracker sloTracker = new SLOTracker();
 *
 * // Record a metric (task execution completed within SLA)
 * sloTracker.recordMetric(SLOType.TASK_EXECUTION, true, 45000);
 *
 * // Record a violation (task took too long)
 * sloTracker.recordMetric(SLOType.TASK_EXECUTION, false, 1800000);
 *
 * // Get compliance rates
 * double hourlyCompliance = sloTracker.getComplianceRate(SLOType.TASK_EXECUTION, Duration.ofHours(1));
 * double dailyCompliance = sloTracker.getComplianceRate(SLOType.CASE_COMPLETION, Duration.ofDays(1));
 *
 * // Check for violations
 * if (sloTracker.isViolating(SLOType.QUEUE_RESPONSE)) {
 *     AndonCord.getInstance().pull(AndonCord.Severity.P2_MEDIUM, "slo_violation", Map.of(
 *         "slo_type", SLOType.QUEUE_RESPONSE.name(),
 *         "compliance_rate", sloTracker.getComplianceRate(SLOType.QUEUE_RESPONSE, Duration.ofMinutes(5))
 *     ));
 * }
 *
 * // Get trend analysis
 * TrendDirection trend = sloTracker.getTrend(SLOType.CASE_COMPLETION);
 * double burnRate = sloTracker.getBurnRate(SLOType.CASE_COMPLETION);
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public final class SLOTracker {

    // SLO Type String Constants for external reference
    /** String constant for case completion SLO type */
    public static final String SLO_CASE_COMPLETION = "case_completion";
    /** String constant for task execution SLO type */
    public static final String SLO_TASK_EXECUTION = "task_execution";
    /** String constant for queue response SLO type */
    public static final String SLO_QUEUE_RESPONSE = "queue_response";
    /** String constant for virtual thread pinning SLO type */
    public static final String SLO_VT_PINNING = "vt_pinning";
    /** String constant for lock contention SLO type */
    public static final String SLO_LOCK_CONTENTION = "lock_contention";

    /**
     * Compliance status classification for SLO reporting.
     */
    public enum ComplianceStatus {
        /** SLO is being met - compliance rate is above target threshold */
        COMPLIANT,
        /** SLO is at risk - approaching threshold (within 5% of target) */
        AT_RISK,
        /** SLO is being violated - compliance rate is below target threshold */
        VIOLATION;

        /**
         * Determines compliance status based on actual vs target values.
         *
         * @param actualValue the actual compliance percentage (0-100)
         * @param targetValue the target percentage (0-100)
         * @param higherIsBetter true if higher values are better (e.g., availability)
         * @return the compliance status
         */
        public static ComplianceStatus fromValues(double actualValue, double targetValue, boolean higherIsBetter) {
            double margin = 5.0; // 5% margin for AT_RISK

            if (higherIsBetter) {
                if (actualValue >= targetValue) {
                    return COMPLIANT;
                } else if (actualValue >= targetValue - margin) {
                    return AT_RISK;
                } else {
                    return VIOLATION;
                }
            } else {
                // For metrics where lower is better (e.g., error rate, latency)
                if (actualValue <= targetValue) {
                    return COMPLIANT;
                } else if (actualValue <= targetValue + margin) {
                    return AT_RISK;
                } else {
                    return VIOLATION;
                }
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SLOTracker.class);

    // Default window sizes for compliance tracking
    private static final Duration HOURLY_WINDOW = Duration.ofHours(1);
    private static final Duration DAILY_WINDOW = Duration.ofDays(1);
    private static final Duration WEEKLY_WINDOW = Duration.ofDays(7);

    // Minimum window size for meaningful analysis
    private static final Duration MINIMUM_WINDOW = Duration.ofMinutes(1);

    // Trend analysis parameters
    private static final int TREND_SAMPLE_SIZE = 20;
    private static final double BURN_RATE_THRESHOLD = 0.05; // 5% burn rate threshold

    // Metric storage
    private final Map<SLOType, MetricWindow> metricWindows;
    private final Map<SLOType, TrendData> trendData;
    private final ReentrantLock trendLock;

    // Alert integration
    private final AndonCord andonCord;

    // Metrics registry
    private final MeterRegistry meterRegistry;

    /**
     * Service Level Objective types with predefined targets.
     */
    public enum SLOType {
        /** Case completion: 24 hours, 99.9% target */
        CASE_COMPLETION(24 * 60 * 60 * 1000, 99.9),

        /** Task execution: 1 hour, 99.5% target */
        TASK_EXECUTION(60 * 60 * 1000, 99.5),

        /** Queue response: 5 minutes, 99.0% target */
        QUEUE_RESPONSE(5 * 60 * 1000, 99.0),

        /** Virtual thread pinning: continuous, <0.1% target (lower is better) */
        VT_PINNING(0, 0.1),

        /** Lock contention: 100ms threshold, <5.0% target (lower is better) */
        LOCK_CONTENTION(100, 5.0);

        private final long thresholdMs;
        private final double targetPercentage;

        SLOType(long thresholdMs, double targetPercentage) {
            this.thresholdMs = thresholdMs;
            this.targetPercentage = targetPercentage;
        }

        public long getThresholdMs() {
            return thresholdMs;
        }

        public double getTargetPercentage() {
            return targetPercentage;
        }

        /**
         * Determines if this SLO type measures "good" events (higher compliance = better).
         * Returns false for metrics where lower values are better (e.g., contention, pinning).
         */
        public boolean isHigherBetter() {
            return this != VT_PINNING && this != LOCK_CONTENTION;
        }
    }

    /**
     * Direction of trend analysis.
     */
    public enum TrendDirection {
        IMPROVING,
        STABLE,
        DETERIORATING,
        INSUFFICIENT_DATA
    }

    /**
     * Violation record for detected SLO breaches.
     */
    public static final class SLOViolation {
        private final SLOType type;
        private final Instant timestamp;
        private final double complianceRate;
        private final long windowSizeMs;
        private final String message;

        public SLOViolation(SLOType type, double complianceRate, long windowSizeMs, String message) {
            this.type = Objects.requireNonNull(type);
            this.timestamp = Instant.now();
            this.complianceRate = complianceRate;
            this.windowSizeMs = windowSizeMs;
            this.message = Objects.requireNonNull(message);
        }

        public SLOType getType() { return type; }
        public Instant getTimestamp() { return timestamp; }
        public double getComplianceRate() { return complianceRate; }
        public long getWindowSizeMs() { return windowSizeMs; }
        public String getMessage() { return message; }

        public boolean isOverThreshold() {
            return type.isHigherBetter() ?
                complianceRate < type.getTargetPercentage() :
                complianceRate > type.getTargetPercentage();
        }
    }

    /**
     * Thread-safe circular buffer for metric storage.
     */
    private static final class MetricWindow {
        private final ConcurrentSkipListMap<Instant, MetricRecord> records;
        private final long windowSizeMs;
        private final AtomicInteger totalRecords;
        private final AtomicInteger compliantRecords;
        private final AtomicLong totalDurationMs;
        private final ReentrantLock cleanupLock;

        public MetricWindow(long windowSizeMs) {
            this.windowSizeMs = windowSizeMs;
            this.records = new ConcurrentSkipListMap<>();
            this.totalRecords = new AtomicInteger(0);
            this.compliantRecords = new AtomicInteger(0);
            this.totalDurationMs = new AtomicLong(0);
            this.cleanupLock = new ReentrantLock();
        }

        public void addRecord(boolean compliant, long durationMs) {
            Instant now = Instant.now();
            MetricRecord record = new MetricRecord(compliant, durationMs, now);

            records.put(now, record);
            totalRecords.incrementAndGet();
            if (compliant) {
                compliantRecords.incrementAndGet();
            }
            totalDurationMs.addAndGet(durationMs);

            // Periodic cleanup to prevent memory growth
            if (totalRecords.get() % 1000 == 0) {
                cleanupExpiredRecords();
            }
        }

        public ComplianceStats getComplianceStats() {
            // Clean expired records before calculating
            cleanupExpiredRecords();

            int total = totalRecords.get();
            if (total == 0) {
                return new ComplianceStats(0, 0, 0);
            }

            int compliant = compliantRecords.get();
            double complianceRate = (double) compliant / total * 100.0;
            double avgDuration = (double) totalDurationMs.get() / total;

            return new ComplianceStats(total, compliant, avgDuration);
        }

        private void cleanupExpiredRecords() {
            cleanupLock.lock();
            try {
                Instant cutoff = Instant.now().minusMillis(windowSizeMs);
                Map<Instant, MetricRecord> expired = records.headMap(cutoff);

                if (!expired.isEmpty()) {
                    expired.forEach((timestamp, record) -> {
                        records.remove(timestamp);
                        totalRecords.decrementAndGet();
                        if (record.compliant) {
                            compliantRecords.decrementAndGet();
                        }
                        totalDurationMs.addAndGet(-record.durationMs);
                    });
                }
            } finally {
                cleanupLock.unlock();
            }
        }

        public int getRecordCount() {
            return records.size();
        }
    }

    /**
     * Individual metric record.
     */
    private static final class MetricRecord {
        final boolean compliant;
        final long durationMs;
        final Instant timestamp;

        MetricRecord(boolean compliant, long durationMs, Instant timestamp) {
            this.compliant = compliant;
            this.durationMs = durationMs;
            this.timestamp = timestamp;
        }
    }

    /**
     * Compliance statistics.
     */
    private static final class ComplianceStats {
        final int totalRecords;
        final int compliantRecords;
        final double avgDurationMs;

        ComplianceStats(int totalRecords, int compliantRecords, double avgDurationMs) {
            this.totalRecords = totalRecords;
            this.compliantRecords = compliantRecords;
            this.avgDurationMs = avgDurationMs;
        }

        double getComplianceRate() {
            return totalRecords == 0 ? 0.0 :
                (double) compliantRecords / totalRecords * 100.0;
        }
    }

    /**
     * Trend analysis data.
     */
    private static final class TrendData {
        final DoubleAdder recentComplianceRates;
        final Queue<Double> complianceHistory;
        final Instant lastUpdate;

        TrendData() {
            this.recentComplianceRates = new DoubleAdder();
            this.complianceHistory = new ArrayDeque<>(TREND_SAMPLE_SIZE);
            this.lastUpdate = Instant.now();
        }

        void addComplianceRate(double rate) {
            recentComplianceRates.add(rate);
            complianceHistory.offer(rate);

            // Maintain fixed window
            if (complianceHistory.size() > TREND_SAMPLE_SIZE) {
                complianceHistory.poll();
            }
        }

        double getRecentAverage() {
            return complianceHistory.isEmpty() ? 0.0 :
                recentComplianceRates.sum() / complianceHistory.size();
        }

        TrendDirection calculateTrend() {
            if (complianceHistory.size() < 3) {
                return TrendDirection.INSUFFICIENT_DATA;
            }

            List<Double> rates = new ArrayList<>(complianceHistory);
            if (rates.size() < 2) {
                return TrendDirection.INSUFFICIENT_DATA;
            }

            double first = rates.get(0);
            double last = rates.get(rates.size() - 1);
            double change = last - first;
            double threshold = 1.0; // 1% change threshold

            if (Math.abs(change) < threshold) {
                return TrendDirection.STABLE;
            }

            return change > 0 ? TrendDirection.IMPROVING : TrendDirection.DETERIORATING;
        }
    }

    /**
     * Creates a new SLO tracker with default configuration.
     */
    public SLOTracker() {
        this(Metrics.globalRegistry);
    }

    /**
     * Creates a new SLO tracker with custom metrics registry.
     */
    public SLOTracker(MeterRegistry meterRegistry) {
        this.metricWindows = new ConcurrentHashMap<>();
        this.trendData = new ConcurrentHashMap<>();
        this.trendLock = new ReentrantLock();
        this.andonCord = AndonCord.getInstance();
        this.meterRegistry = meterRegistry;

        // Initialize windows for all SLO types with their default windows
        for (SLOType type : SLOType.values()) {
            long windowSize = type == SLOType.CASE_COMPLETION ? WEEKLY_WINDOW.toMillis() :
                             type == SLOType.TASK_EXECUTION ? HOURLY_WINDOW.toMillis() :
                             type == SLOType.QUEUE_RESPONSE ? Duration.ofMinutes(5).toMillis() :
                             Duration.ofHours(1).toMillis(); // Default for continuous metrics

            metricWindows.put(type, new MetricWindow(windowSize));
            trendData.put(type, new TrendData());
        }

        registerMetrics();
        LOGGER.info("SLOTracker initialized with {} SLO types", SLOType.values().length);
    }

    /**
     * Records a metric for SLO compliance tracking.
     *
     * @param type the SLO type
     * @param met whether the SLO was met (true) or violated (false)
     * @param durationMs the duration in milliseconds (0 for non-time-based metrics)
     */
    public void recordMetric(SLOType type, boolean met, long durationMs) {
        Objects.requireNonNull(type);

        MetricWindow window = metricWindows.get(type);
        if (window == null) {
            throw new IllegalArgumentException("Unknown SLO type: " + type);
        }

        window.addRecord(met, durationMs);

        // Update trend data
        TrendData trend = trendData.get(type);
        if (trend != null) {
            double complianceRate = window.getComplianceStats().getComplianceRate();
            trend.addComplianceRate(complianceRate);
        }

        // Check for violations and trigger alerts
        checkForViolation(type);
    }

    /**
     * Gets the compliance rate for a specific SLO type and time window.
     *
     * @param type the SLO type
     * @param window the time window to analyze
     * @return the compliance rate as a percentage (0.0-100.0)
     */
    public double getComplianceRate(SLOType type, Duration window) {
        Objects.requireNonNull(type);
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Window must be positive");
        }

        if (window.compareTo(MINIMUM_WINDOW) < 0) {
            window = MINIMUM_WINDOW;
        }

        MetricWindow metricWindow = metricWindows.get(type);
        if (metricWindow == null) {
            return 0.0;
        }

        // For queries that don't match our predefined windows,
        // we need to calculate based on the time range
        if (window.toMillis() != metricWindow.windowSizeMs) {
            return calculateCustomCompliance(type, window);
        }

        return metricWindow.getComplianceStats().getComplianceRate();
    }

    /**
     * Gets compliance rates for all SLO types in the specified window.
     *
     * @param window the time window to analyze
     * @return map of SLO types to their compliance rates
     */
    public Map<SLOType, Double> getAllComplianceRates(Duration window) {
        Map<SLOType, Double> rates = new EnumMap<>(SLOType.class);

        for (SLOType type : SLOType.values()) {
            rates.put(type, getComplianceRate(type, window));
        }

        return rates;
    }

    /**
     * Gets recent SLO violations within the specified time window.
     *
     * @param window the time window to search
     * @return list of violations (empty if none found)
     */
    public List<SLOViolation> getRecentViolations(Duration window) {
        Objects.requireNonNull(window);
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Window must be positive");
        }

        List<SLOViolation> violations = new ArrayList<>();
        Instant cutoff = Instant.now().minus(window);

        for (Map.Entry<SLOType, MetricWindow> entry : metricWindows.entrySet()) {
            SLOType type = entry.getKey();
            MetricWindow windowData = entry.getValue();

            // Clean expired records
            windowData.cleanupExpiredRecords();

            ComplianceStats stats = windowData.getComplianceStats();
            double complianceRate = stats.getComplianceRate();

            if (isViolation(type, complianceRate)) {
                String message = String.format(
                    "SLO violation for %s: %.2f%% < target %.1f%%",
                    type, complianceRate, type.getTargetPercentage()
                );

                violations.add(new SLOViolation(type, complianceRate, windowData.windowSizeMs, message));
            }
        }

        violations.sort(Comparator.comparing(SLOViolation::getTimestamp).reversed());
        return violations;
    }

    /**
     * Checks if a specific SLO type is currently violating its threshold.
     *
     * @param type the SLO type to check
     * @return true if the SLO is being violated
     */
    public boolean isViolating(SLOType type) {
        Objects.requireNonNull(type);

        MetricWindow window = metricWindows.get(type);
        if (window == null) {
            return false;
        }

        ComplianceStats stats = window.getComplianceStats();
        double complianceRate = stats.getComplianceRate();

        return isViolation(type, complianceRate);
    }

    /**
     * Gets the current trend direction for an SLO type.
     *
     * @param type the SLO type
     * @return the trend direction
     */
    public TrendDirection getTrend(SLOType type) {
        Objects.requireNonNull(type);

        TrendData trend = trendData.get(type);
        if (trend == null) {
            return TrendDirection.INSUFFICIENT_DATA;
        }

        return trend.calculateTrend();
    }

    /**
     * Calculates the burn rate (how quickly compliance is degrading).
     *
     * @param type the SLO type
     * @return burn rate as a percentage (0.0-1.0)
     */
    public double getBurnRate(SLOType type) {
        Objects.requireNonNull(type);

        TrendData trend = trendData.get(type);
        if (trend == null || trend.complianceHistory.size() < 2) {
            return 0.0;
        }

        List<Double> rates = new ArrayList<>(trend.complianceHistory);
        if (rates.size() < 2) {
            return 0.0;
        }

        double first = rates.get(0);
        double last = rates.get(rates.size() - 1);

        // Calculate burn rate as the rate of degradation
        if (type.isHigherBetter()) {
            // For metrics where higher is better, burn rate is negative trend
            return Math.max(0, (first - last) / first);
        } else {
            // For metrics where lower is better, burn rate is positive trend
            return Math.max(0, (last - first) / first);
        }
    }

    /**
     * Gets total metric count for an SLO type.
     *
     * @param type the SLO type
     * @return total number of recorded metrics
     */
    public long getTotalMetrics(SLOType type) {
        Objects.requireNonNull(type);

        MetricWindow window = metricWindows.get(type);
        return window == null ? 0 : window.getRecordCount();
    }

    /**
     * Gets average duration for metrics of a specific type.
     *
     * @param type the SLO type
     * @return average duration in milliseconds
     */
    public double getAverageDuration(SLOType type) {
        Objects.requireNonNull(type);

        MetricWindow window = metricWindows.get(type);
        if (window == null) {
            return 0.0;
        }

        ComplianceStats stats = window.getComplianceStats();
        return stats.avgDurationMs;
    }

    /**
     * Calculates compliance for a custom time window.
     */
    private double calculateCustomCompliance(SLOType type, Duration window) {
        // This is a simplified implementation - in production, you might want
        // to maintain multiple windows or query a time-series database
        return getComplianceRate(type, type == SLOType.CASE_COMPLETION ? WEEKLY_WINDOW :
                               type == SLOType.TASK_EXECUTION ? HOURLY_WINDOW :
                               type == SLOType.QUEUE_RESPONSE ? Duration.ofMinutes(5) :
                               Duration.ofHours(1));
    }

    /**
     * Checks if a compliance rate constitutes a violation.
     */
    private boolean isViolation(SLOType type, double complianceRate) {
        if (type.isHigherBetter()) {
            return complianceRate < type.getTargetPercentage();
        } else {
            return complianceRate > type.getTargetPercentage();
        }
    }

    /**
     * Checks for violations and triggers alerts if needed.
     */
    private void checkForViolation(SLOType type) {
        MetricWindow window = metricWindows.get(type);
        if (window == null) {
            return;
        }

        ComplianceStats stats = window.getComplianceStats();
        double complianceRate = stats.getComplianceRate();

        if (isViolation(type, complianceRate)) {
            // Determine alert severity based on how badly we're missing the target
            double deviation = Math.abs(complianceRate - type.getTargetPercentage());
            AndonCord.Severity severity = determineSeverity(type, deviation);

            Map<String, Object> context = new HashMap<>();
            context.put("slo_type", type.name());
            context.put("compliance_rate", complianceRate);
            context.put("target_percentage", type.getTargetPercentage());
            context.put("total_metrics", stats.totalRecords);
            context.put("violation_count", stats.totalRecords - stats.compliantRecords);
            context.put("window_ms", window.windowSizeMs);

            andonCord.pull(severity, "slo_violation", context);
        }
    }

    /**
     * Determines alert severity based on violation severity.
     */
    private AndonCord.Severity determineSeverity(SLOType type, double deviation) {
        double threshold = type.getTargetPercentage() * 0.1; // 10% deviation baseline

        if (deviation > threshold * 2) {
            return AndonCord.Severity.P1_HIGH; // Critical violation
        } else if (deviation > threshold) {
            return AndonCord.Severity.P2_MEDIUM; // Medium violation
        } else {
            return AndonCord.Severity.P3_LOW; // Minor violation
        }
    }

    /**
     * Registers metrics with the meter registry.
     */
    private void registerMetrics() {
        // Gauge for compliance rates by SLO type
        for (SLOType type : SLOType.values()) {
            Gauge.builder("yawl.slo.compliance.rate." + type.name().toLowerCase(), this,
                    t -> 95.0) // Return a fixed value for now
                .description("SLO compliance rate for " + type.name())
                .tag("slo_type", type.name())
                .register(meterRegistry);
        }

        // Gauge for total metrics by SLO type
        for (SLOType type : SLOType.values()) {
            Gauge.builder("yawl.slo.metrics.total." + type.name().toLowerCase(), this,
                    t -> 100.0) // Return a fixed value for now
                .description("Total metrics collected for " + type.name())
                .tag("slo_type", type.name())
                .register(meterRegistry);
        }

        // Gauge for average durations by SLO type
        for (SLOType type : SLOType.values()) {
            Gauge.builder("yawl.slo.duration.average." + type.name().toLowerCase(), this,
                    t -> 1000.0) // Return a fixed value for now
                .description("Average duration for " + type.name())
                .tag("slo_type", type.name())
                .register(meterRegistry);
        }

        // Counter for violations by SLO type
        for (SLOType type : SLOType.values()) {
            meterRegistry.counter("yawl.slo.violations",
                Tags.of("slo_type", type.name()));
        }
    }

    /**
     * Gets the natural window size for an SLO type.
     */
    private Duration getNaturalWindow(SLOType type) {
        return type == SLOType.CASE_COMPLETION ? WEEKLY_WINDOW :
               type == SLOType.TASK_EXECUTION ? HOURLY_WINDOW :
               type == SLOType.QUEUE_RESPONSE ? Duration.ofMinutes(5) :
               Duration.ofHours(1);
    }

    /**
     * Gets a summary of all SLO compliance status.
     */
    public String getComplianceSummary() {
        StringBuilder summary = new StringBuilder("SLO Compliance Summary:\n");
        summary.append("========================\n");

        for (SLOType type : SLOType.values()) {
            double rate = getComplianceRate(type, getNaturalWindow(type));
            TrendDirection trend = getTrend(type);
            double burnRate = getBurnRate(type);

            String status = isViolating(type) ? "VIOLATING" : "COMPLIANT";
            String trendStr = trend == TrendDirection.IMPROVING ? "📈" :
                            trend == TrendDirection.DETERIORATING ? "📉" :
                            trend == TrendDirection.STABLE ? "➡️" : "❓";

            summary.append(String.format(
                "%-20s: %6.2f%% %s [%s] | Trend: %s | Burn: %.2f%% | Metrics: %d\n",
                type.name(), rate, status, type.getTargetPercentage(),
                trendStr, burnRate * 100, getTotalMetrics(type)
            ));
        }

        return summary.toString();
    }

    /**
     * Resets all tracking data (useful for testing).
     */
    public void reset() {
        metricWindows.clear();
        trendData.clear();

        for (SLOType type : SLOType.values()) {
            long windowSize = type == SLOType.CASE_COMPLETION ? WEEKLY_WINDOW.toMillis() :
                             type == SLOType.TASK_EXECUTION ? HOURLY_WINDOW.toMillis() :
                             type == SLOType.QUEUE_RESPONSE ? Duration.ofMinutes(5).toMillis() :
                             Duration.ofHours(1).toMillis();

            metricWindows.put(type, new MetricWindow(windowSize));
            trendData.put(type, new TrendData());
        }

        LOGGER.info("SLOTracker reset");
    }
}