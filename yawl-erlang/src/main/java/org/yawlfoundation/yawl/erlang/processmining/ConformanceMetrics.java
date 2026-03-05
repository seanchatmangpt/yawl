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
 * Result record for conformance checking operations.
 *
 * <p>Contains token replay metrics for evaluating how well an event log
 * conforms to a process model (Petri net).</p>
 *
 * @param success    whether the conformance check succeeded
 * @param fitness    fitness measure (0.0 to 1.0) - how well log fits model
 * @param precision  precision measure (0.0 to 1.0) - how precisely model describes log
 * @param produced   total tokens produced during replay
 * @param consumed   total tokens consumed during replay
 * @param missing    tokens missing (transitions fired without enabling)
 * @param remaining  tokens remaining in model after replay
 * @param error      error message if check failed
 */
public record ConformanceMetrics(
    boolean success,
    double fitness,
    double precision,
    long produced,
    long consumed,
    long missing,
    long remaining,
    String error
) {
    /**
     * Creates successful conformance metrics.
     */
    public static ConformanceMetrics success(
            double fitness, double precision,
            long produced, long consumed, long missing, long remaining) {
        return new ConformanceMetrics(true, fitness, precision,
            produced, consumed, missing, remaining, null);
    }

    /**
     * Creates failed conformance metrics.
     */
    public static ConformanceMetrics error(String error) {
        return new ConformanceMetrics(false, 0.0, 0.0, 0, 0, 0, 0, error);
    }

    /**
     * Creates ConformanceMetrics from a map (from Erlang/NIF JSON response).
     *
     * @param data map with fitness, precision, produced, consumed, missing, remaining
     * @return ConformanceMetrics
     */
    static ConformanceMetrics fromMap(java.util.Map<String, Object> data) {
        double fitness = ((Number) data.getOrDefault("fitness", 0.0)).doubleValue();
        double precision = ((Number) data.getOrDefault("precision", 0.0)).doubleValue();
        long produced = ((Number) data.getOrDefault("produced", 0L)).longValue();
        long consumed = ((Number) data.getOrDefault("consumed", 0L)).longValue();
        long missing = ((Number) data.getOrDefault("missing", 0L)).longValue();
        long remaining = ((Number) data.getOrDefault("remaining", 0L)).longValue();
        return success(fitness, precision, produced, consumed, missing, remaining);
    }
}
