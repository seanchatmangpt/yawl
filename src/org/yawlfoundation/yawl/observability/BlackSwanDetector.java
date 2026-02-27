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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Detects black swan events in YAWL workflow execution.
 *
 * <p>A black swan is a rare, extreme-impact event that sits far outside the normal
 * anomaly distribution. While {@link AnomalyDetector} flags routine outliers at &gt;2.5σ,
 * this detector classifies three categories of extraordinary events:
 * <ul>
 *   <li><b>Extreme outlier</b>: single metric at &gt;{@value #EXTREME_SIGMA_THRESHOLD}σ</li>
 *   <li><b>Anomaly storm</b>: ≥{@value #STORM_ANOMALY_COUNT} anomalies within
 *       {@value #STORM_WINDOW_SECONDS} seconds (cascade failure onset)</li>
 *   <li><b>Systemic failure</b>: same metric deviating in
 *       ≥{@value #SYSTEMIC_CASE_COUNT} independent cases simultaneously</li>
 * </ul>
 *
 * <p>All detected black swans are:
 * <ol>
 *   <li>Logged via {@link StructuredLogger} at ERROR level with full context</li>
 *   <li>Emitted as {@code yawl.blackswan.detected} Micrometer counter (by type and metric)</li>
 *   <li>Escalated via {@link AndonCord} at P0_CRITICAL severity (if cord is configured)</li>
 * </ol>
 *
 * <p>Storm and systemic alerts include a 120-second cooldown after each fire to prevent
 * alert flooding when the system is under sustained stress.
 *
 * <p>Thread-safe. All internal state uses lock-free concurrent data structures.
 *
 * <pre>{@code
 * // Setup
 * BlackSwanDetector detector = new BlackSwanDetector(meterRegistry, andonCord);
 *
 * // Called from AnomalyDetector.handleAnomaly() when sigma is computed
 * Optional<BlackSwanEvent> event = detector.recordAnomaly(
 *     "task.duration", sigmaLevel, observedMs, meanMs, caseId);
 *
 * event.ifPresent(e -> switch (e) {
 *     case BlackSwanEvent.ExtremeOutlier o -> respondToOutlier(o);
 *     case BlackSwanEvent.AnomalyStorm s   -> respondToStorm(s);
 *     case BlackSwanEvent.SystemicFailure f -> respondToSystemic(f);
 * });
 *
 * // On case completion, release systemic tracking resources
 * detector.clearCase(caseId);
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 * @see AnomalyDetector
 * @see BlackSwanEvent
 * @see AndonCord
 */
public class BlackSwanDetector {

    private static final StructuredLogger LOGGER = StructuredLogger.getLogger(BlackSwanDetector.class);

    // Detection thresholds — package-private for test visibility
    static final double EXTREME_SIGMA_THRESHOLD = 5.0;
    static final int STORM_ANOMALY_COUNT = 5;
    static final long STORM_WINDOW_SECONDS = 60L;
    static final int SYSTEMIC_CASE_COUNT = 3;
    static final long ALERT_COOLDOWN_SECONDS = 120L;

    private final MeterRegistry meterRegistry;
    private final AndonCord andonCord;  // nullable — null means log-only mode

    // Storm detection: rolling window of (timestamp, metric) entries
    private final ConcurrentLinkedDeque<AnomalyEntry> recentAnomalies;

    // Systemic detection: metric → Set<caseId> currently deviating
    // Uses ConcurrentHashMap.newKeySet() for a concurrent set
    private final ConcurrentHashMap<String, Set<String>> casesDeviatingPerMetric;

    // Per-metric accumulated sigma for systemic mean calculation
    private final ConcurrentHashMap<String, DoubleAdder> accumulatedSigmaPerMetric;

    // Cooldown: suppress repeated storm/systemic alerts for ALERT_COOLDOWN_SECONDS
    private volatile Instant lastStormFiredAt = Instant.EPOCH;
    private final ConcurrentHashMap<String, Instant> lastSystemicFiredAt;

    /**
     * Creates a BlackSwanDetector with full AndonCord integration.
     *
     * @param meterRegistry Micrometer registry for metric emission (required)
     * @param andonCord     alert cord for P0 escalation; may be null for log-only mode
     */
    public BlackSwanDetector(MeterRegistry meterRegistry, AndonCord andonCord) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.andonCord = andonCord;
        this.recentAnomalies = new ConcurrentLinkedDeque<>();
        this.casesDeviatingPerMetric = new ConcurrentHashMap<>();
        this.accumulatedSigmaPerMetric = new ConcurrentHashMap<>();
        this.lastSystemicFiredAt = new ConcurrentHashMap<>();
    }

    /**
     * Creates a BlackSwanDetector in log-only mode (no AndonCord escalation).
     *
     * @param meterRegistry Micrometer registry for metric emission (required)
     */
    public BlackSwanDetector(MeterRegistry meterRegistry) {
        this(meterRegistry, null);
    }

    /**
     * Records a single anomaly observation and checks for black swan patterns.
     *
     * <p>Evaluates three detection strategies in priority order:
     * {@link BlackSwanEvent.SystemicFailure} &gt;
     * {@link BlackSwanEvent.AnomalyStorm} &gt;
     * {@link BlackSwanEvent.ExtremeOutlier}.
     * Returns the highest-priority event if multiple patterns fire simultaneously.
     *
     * @param metric     metric name (e.g. {@code "task.duration"})
     * @param sigmaLevel sigma deviation level (must be &gt; 0)
     * @param observedMs observed duration in milliseconds
     * @param meanMs     current EWMA mean for this metric
     * @param caseId     case ID for cross-case correlation; may be null to skip systemic check
     * @return the detected black swan event, or {@link Optional#empty()} for normal anomaly traffic
     */
    public Optional<BlackSwanEvent> recordAnomaly(
            String metric, double sigmaLevel, long observedMs, double meanMs, String caseId) {
        Objects.requireNonNull(metric, "metric");

        Instant now = Instant.now();

        // Update storm window
        recentAnomalies.addLast(new AnomalyEntry(now, metric));
        pruneStormWindow(now);

        // Update systemic tracking if caseId supplied
        if (caseId != null) {
            trackCase(metric, caseId, sigmaLevel);
        }

        // --- Detection in priority order: Systemic > Storm > Extreme ---

        // 1. Systemic failure check
        if (caseId != null) {
            Set<String> deviatingCases = casesDeviatingPerMetric.get(metric);
            if (deviatingCases != null && deviatingCases.size() >= SYSTEMIC_CASE_COUNT) {
                Instant cooldownExpiry = lastSystemicFiredAt
                        .getOrDefault(metric, Instant.EPOCH)
                        .plusSeconds(ALERT_COOLDOWN_SECONDS);
                if (now.isAfter(cooldownExpiry)) {
                    lastSystemicFiredAt.put(metric, now);
                    double meanSigma = computeMeanSigma(metric, deviatingCases.size());
                    BlackSwanEvent event = new BlackSwanEvent.SystemicFailure(
                            metric, deviatingCases.size(), meanSigma, now);
                    handleBlackSwan(event, metric, "systemic_failure");
                    return Optional.of(event);
                }
            }
        }

        // 2. Anomaly storm check
        int stormSize = recentAnomalies.size();
        if (stormSize >= STORM_ANOMALY_COUNT) {
            Instant cooldownExpiry = lastStormFiredAt.plusSeconds(ALERT_COOLDOWN_SECONDS);
            if (now.isAfter(cooldownExpiry)) {
                lastStormFiredAt = now;
                List<String> stormMetrics = recentAnomalies.stream()
                        .map(AnomalyEntry::metric)
                        .distinct()
                        .toList();
                BlackSwanEvent event = new BlackSwanEvent.AnomalyStorm(
                        stormSize, STORM_WINDOW_SECONDS, stormMetrics, now);
                handleBlackSwan(event, metric, "anomaly_storm");
                return Optional.of(event);
            }
        }

        // 3. Extreme outlier check
        if (sigmaLevel >= EXTREME_SIGMA_THRESHOLD) {
            BlackSwanEvent event = new BlackSwanEvent.ExtremeOutlier(
                    metric, sigmaLevel, observedMs, meanMs, now);
            handleBlackSwan(event, metric, "extreme_outlier");
            return Optional.of(event);
        }

        return Optional.empty();
    }

    /**
     * Releases cross-case tracking resources for a completed or cancelled case.
     *
     * <p>Call this when a case reaches a terminal state to prevent the systemic
     * detector from counting stale entries as active deviations.
     *
     * @param caseId the case ID to release
     */
    public void clearCase(String caseId) {
        if (caseId == null) {
            return;
        }
        casesDeviatingPerMetric.forEach((metric, cases) -> {
            cases.remove(caseId);
            if (cases.isEmpty()) {
                casesDeviatingPerMetric.remove(metric, cases);
                accumulatedSigmaPerMetric.remove(metric);
            }
        });
    }

    /**
     * Returns the number of anomalies currently in the storm detection window.
     * Exposed for monitoring and testing.
     */
    public int getStormWindowSize() {
        pruneStormWindow(Instant.now());
        return recentAnomalies.size();
    }

    /**
     * Returns the number of cases currently tracked as deviating for the given metric.
     * Exposed for monitoring and testing.
     */
    public int getDeviatingCaseCount(String metric) {
        Set<String> cases = casesDeviatingPerMetric.get(metric);
        return cases == null ? 0 : cases.size();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void pruneStormWindow(Instant now) {
        Instant cutoff = now.minusSeconds(STORM_WINDOW_SECONDS);
        // Remove entries from the head that are older than the window
        while (!recentAnomalies.isEmpty()) {
            AnomalyEntry head = recentAnomalies.peekFirst();
            if (head == null || !head.timestamp().isBefore(cutoff)) {
                break;
            }
            recentAnomalies.pollFirst();
        }
    }

    private void trackCase(String metric, String caseId, double sigmaLevel) {
        casesDeviatingPerMetric
                .computeIfAbsent(metric, k -> ConcurrentHashMap.newKeySet())
                .add(caseId);
        accumulatedSigmaPerMetric
                .computeIfAbsent(metric, k -> new DoubleAdder())
                .add(sigmaLevel);
    }

    private double computeMeanSigma(String metric, int caseCount) {
        DoubleAdder accumulated = accumulatedSigmaPerMetric.get(metric);
        if (accumulated == null || caseCount == 0) {
            return 0;
        }
        return accumulated.sum() / caseCount;
    }

    private void handleBlackSwan(BlackSwanEvent event, String metric, String type) {
        // Build structured log context
        Map<String, Object> logContext = new HashMap<>();
        logContext.put("type", type);
        logContext.put("metric", metric);
        logContext.put("impact_score", event.impactScore());
        logContext.put("detected_at", event.detectedAt().toString());
        enrichContext(logContext, event);

        LOGGER.error("Black swan event detected - " + type, logContext, null);

        // Emit Micrometer counter
        meterRegistry.counter(
                "yawl.blackswan.detected",
                Tags.of(
                        Tag.of("type", type),
                        Tag.of("metric", metric)
                )
        ).increment();

        // Escalate via AndonCord P0 if configured
        if (andonCord != null) {
            Map<String, Object> alertContext = new HashMap<>(logContext);
            andonCord.pull(AndonCord.Severity.P0_CRITICAL, "black_swan." + type, alertContext);
        }
    }

    private void enrichContext(Map<String, Object> ctx, BlackSwanEvent event) {
        switch (event) {
            case BlackSwanEvent.ExtremeOutlier o -> {
                ctx.put("sigma_level", o.sigmaLevel());
                ctx.put("observed_ms", o.observedMs());
                ctx.put("mean_ms", o.meanMs());
            }
            case BlackSwanEvent.AnomalyStorm s -> {
                ctx.put("anomaly_count", s.anomalyCount());
                ctx.put("window_seconds", s.windowSeconds());
                ctx.put("affected_metrics", s.affectedMetrics());
            }
            case BlackSwanEvent.SystemicFailure f -> {
                ctx.put("affected_cases", f.affectedCases());
                ctx.put("mean_sigma_across_cases", f.meanSigmaAcrossCases());
            }
        }
    }

    // Internal record for storm window tracking
    private record AnomalyEntry(Instant timestamp, String metric) {}
}
