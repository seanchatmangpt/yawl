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

package org.yawlfoundation.yawl.dspy.forensics;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of anomaly context for root cause analysis.
 *
 * <p>Captures all telemetry data available at the time an anomaly is detected,
 * including metric name, duration, deviation factor, recent samples, and
 * concurrent cases. This context is marshalled to DSPy for multi-chain
 * root cause hypothesis generation.</p>
 *
 * <p><b>Example Usage</b></p>
 * <pre>{@code
 * AnomalyContext context = new AnomalyContext(
 *     "task_processing_latency",
 *     5000L,  // anomaly persisted for 5 seconds
 *     3.2,    // 320% of baseline
 *     Map.of(
 *         "1707000000000", 150L,  // timestamp -> latency ms
 *         "1707000001000", 180L,
 *         "1707000002000", 480L   // SPIKE
 *     ),
 *     List.of("case-001", "case-002", "case-003")
 * );
 * }</pre>
 *
 * @param metricName       the name of the metric that anomalously spiked (e.g., "task_processing_latency")
 * @param durationMs       how long the anomaly persisted (milliseconds)
 * @param deviationFactor  deviation from baseline as a multiplier (e.g., 3.2 = 320% of baseline)
 * @param recentSamples    recent metric values: timestamp (String ms) -> value (Long)
 * @param concurrentCases  list of case IDs running when anomaly occurred
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record AnomalyContext(
        String metricName,
        long durationMs,
        double deviationFactor,
        Map<String, Long> recentSamples,
        List<String> concurrentCases
) {

    /**
     * Validates and constructs an AnomalyContext.
     *
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if durationMs < 0 or deviationFactor <= 0
     */
    public AnomalyContext {
        Objects.requireNonNull(metricName, "metricName must not be null");
        Objects.requireNonNull(recentSamples, "recentSamples must not be null");
        Objects.requireNonNull(concurrentCases, "concurrentCases must not be null");

        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be >= 0, got: " + durationMs);
        }
        if (deviationFactor <= 0) {
            throw new IllegalArgumentException("deviationFactor must be > 0, got: " + deviationFactor);
        }
    }

    /**
     * Returns a summary of this anomaly context for logging.
     */
    @Override
    public String toString() {
        return String.format(
                "AnomalyContext{metric=%s, duration=%dms, deviation=%.2fx, samples=%d, cases=%d}",
                metricName, durationMs, deviationFactor, recentSamples.size(), concurrentCases.size()
        );
    }
}
