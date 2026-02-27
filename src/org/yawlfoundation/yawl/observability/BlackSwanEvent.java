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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Sealed event hierarchy for black swan occurrences in YAWL workflow execution.
 *
 * <p>A black swan is a rare, extreme-impact event that falls far outside the normal
 * anomaly distribution. Three distinct patterns are classified:
 * <ul>
 *   <li>{@link ExtremeOutlier} — single metric at &gt;5σ from its EWMA baseline</li>
 *   <li>{@link AnomalyStorm} — burst of ≥5 anomalies within a 60-second window (cascade onset)</li>
 *   <li>{@link SystemicFailure} — same metric deviating in ≥3 independent cases simultaneously</li>
 * </ul>
 *
 * <p>Use exhaustive pattern matching (Java 21+ switch expression) to handle all subtypes:
 * <pre>{@code
 * String desc = switch (event) {
 *     case BlackSwanEvent.ExtremeOutlier e -> "σ=" + e.sigmaLevel();
 *     case BlackSwanEvent.AnomalyStorm s   -> "burst=" + s.anomalyCount();
 *     case BlackSwanEvent.SystemicFailure f -> "cases=" + f.affectedCases();
 * };
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public sealed interface BlackSwanEvent
        permits BlackSwanEvent.ExtremeOutlier,
                BlackSwanEvent.AnomalyStorm,
                BlackSwanEvent.SystemicFailure {

    /**
     * Timestamp when this black swan was first detected.
     */
    Instant detectedAt();

    /**
     * Composite impact score from 0 (low) to 100 (catastrophic).
     * Used for alert routing and dashboard severity display.
     */
    int impactScore();

    /**
     * Single metric at &gt;5σ — a catastrophic single-observation deviation.
     *
     * <p>Indicates a single measurement so far outside the historical distribution
     * that it is statistically extraordinary (beyond the 5σ boundary that
     * standard anomaly detectors ignore as "too extreme").
     *
     * @param metric      metric name (e.g. {@code "task.duration"})
     * @param sigmaLevel  actual sigma level observed (e.g. 7.3 for 7.3σ)
     * @param observedMs  observed value in milliseconds
     * @param meanMs      current EWMA baseline mean in milliseconds
     * @param detectedAt  when the event was detected
     */
    record ExtremeOutlier(
            String metric,
            double sigmaLevel,
            long observedMs,
            double meanMs,
            Instant detectedAt
    ) implements BlackSwanEvent {

        public ExtremeOutlier {
            Objects.requireNonNull(metric, "metric");
            Objects.requireNonNull(detectedAt, "detectedAt");
        }

        @Override
        public int impactScore() {
            return (int) Math.min(100, 60 + (sigmaLevel - 5.0) * 8);
        }
    }

    /**
     * Burst of ≥N anomalies across any metrics within a rolling time window.
     *
     * <p>Signals the onset of a cascade failure: many independent metrics are
     * simultaneously deviating, indicating systemic degradation rather than
     * an isolated incident.
     *
     * @param anomalyCount     number of anomalies that fired within the window
     * @param windowSeconds    the observation window in seconds
     * @param affectedMetrics  distinct metric names that contributed to the storm
     * @param detectedAt       when the threshold was first crossed
     */
    record AnomalyStorm(
            int anomalyCount,
            long windowSeconds,
            List<String> affectedMetrics,
            Instant detectedAt
    ) implements BlackSwanEvent {

        public AnomalyStorm {
            Objects.requireNonNull(affectedMetrics, "affectedMetrics");
            Objects.requireNonNull(detectedAt, "detectedAt");
            affectedMetrics = List.copyOf(affectedMetrics);
        }

        @Override
        public int impactScore() {
            return Math.min(100, 50 + anomalyCount * 5);
        }
    }

    /**
     * Same metric deviating across ≥N independent cases simultaneously.
     *
     * <p>Signals an infrastructure-level failure: a shared resource, dependency,
     * or configuration has degraded and is affecting all running cases in parallel.
     * Cannot be explained by a single slow case.
     *
     * @param metric                 the metric name common to all affected cases
     * @param affectedCases          number of independent cases currently deviating
     * @param meanSigmaAcrossCases   average sigma level across the affected cases
     * @param detectedAt             when the cross-case threshold was first crossed
     */
    record SystemicFailure(
            String metric,
            int affectedCases,
            double meanSigmaAcrossCases,
            Instant detectedAt
    ) implements BlackSwanEvent {

        public SystemicFailure {
            Objects.requireNonNull(metric, "metric");
            Objects.requireNonNull(detectedAt, "detectedAt");
        }

        @Override
        public int impactScore() {
            return Math.min(100, 70 + affectedCases * 5);
        }
    }
}
