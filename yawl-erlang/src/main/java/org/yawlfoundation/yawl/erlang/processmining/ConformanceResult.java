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
package org.yawlfoundation.yawl.erlang.processmining;

/**
 * Result of a process conformance check.
 *
 * <p>Records the token replay fitness and precision metrics computed by the
 * Erlang process mining engine, along with event counts and a diagnostic summary.</p>
 *
 * @param fitness fitness metric (0.0 to 1.0), measuring how well the event log
 *                conforms to the discovered model. 1.0 is perfect conformance.
 * @param precision precision metric (0.0 to 1.0), measuring the specificity of
 *                  the discovered model. 1.0 means the model allows exactly the
 *                  behavior in the log.
 * @param totalEvents total number of events processed in the log
 * @param conformingEvents number of events that matched the model during replay
 * @param diagnosis human-readable diagnostic summary (e.g.,
 *                  "fitness=0.95, precision=0.89, 950/1000 events conforming")
 */
public record ConformanceResult(
    double fitness,
    double precision,
    int totalEvents,
    int conformingEvents,
    String diagnosis
) {
    /**
     * Constructs a ConformanceResult with the given metrics.
     *
     * @param fitness       must be in range [0.0, 1.0]
     * @param precision     must be in range [0.0, 1.0]
     * @param totalEvents   must be non-negative
     * @param conformingEvents must be non-negative and ≤ totalEvents
     * @param diagnosis     human-readable summary
     * @throws IllegalArgumentException if fitness or precision are outside [0.0, 1.0],
     *                                  or if event counts are invalid
     */
    public ConformanceResult {
        if (fitness < 0.0 || fitness > 1.0) {
            throw new IllegalArgumentException(
                "fitness must be in [0.0, 1.0], got " + fitness);
        }
        if (precision < 0.0 || precision > 1.0) {
            throw new IllegalArgumentException(
                "precision must be in [0.0, 1.0], got " + precision);
        }
        if (totalEvents < 0) {
            throw new IllegalArgumentException(
                "totalEvents must be non-negative, got " + totalEvents);
        }
        if (conformingEvents < 0 || conformingEvents > totalEvents) {
            throw new IllegalArgumentException(
                "conformingEvents must be in [0, totalEvents], got " +
                    conformingEvents + " (totalEvents=" + totalEvents + ")");
        }
        if (diagnosis == null) {
            throw new IllegalArgumentException("diagnosis must be non-null");
        }
    }
}
