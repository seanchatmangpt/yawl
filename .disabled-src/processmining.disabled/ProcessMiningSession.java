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

package org.yawlfoundation.yawl.integration.processmining;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable session object for process mining analysis workflows.
 *
 * Holds state across a process mining analysis run, including session ID,
 * specification reference, and accumulated metrics from conformance and
 * performance analysis.
 *
 * Immutable Java 25 record. Use {@code withMetrics()} to create updated copies
 * with new analysis results.
 *
 * @param sessionId unique session identifier (UUID, auto-generated)
 * @param specificationId YAWL specification identifier
 * @param createdAt creation timestamp (ISO-8601 instant)
 * @param lastAnalyzedAt timestamp of last analysis run (null if never analyzed)
 * @param totalCasesAnalyzed cumulative count of cases analyzed across all runs
 * @param lastFitnessScore fitness score from last analysis (0.0-1.0, or 0.0 if not analyzed)
 * @param lastPrecisionScore precision score from last analysis (0.0-1.0, or 0.0 if not analyzed)
 * @param lastAvgFlowTimeMs average flow time in milliseconds from last analysis (0.0 if not analyzed)
 *
 * @since YAWL 6.0
 * @author YAWL Foundation
 * @version 6.0
 */
public record ProcessMiningSession(
        String sessionId,
        String specificationId,
        Instant createdAt,
        Instant lastAnalyzedAt,
        int totalCasesAnalyzed,
        double lastFitnessScore,
        double lastPrecisionScore,
        double lastAvgFlowTimeMs) {

    /**
     * Construct with validation.
     *
     * @throws IllegalArgumentException if sessionId or specificationId is null/empty,
     *         or if scores are outside [0.0, 1.0]
     */
    public ProcessMiningSession {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (specificationId == null || specificationId.isEmpty()) {
            throw new IllegalArgumentException("specificationId is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        if (lastFitnessScore < 0.0 || lastFitnessScore > 1.0) {
            throw new IllegalArgumentException("lastFitnessScore must be in [0.0, 1.0], got " + lastFitnessScore);
        }
        if (lastPrecisionScore < 0.0 || lastPrecisionScore > 1.0) {
            throw new IllegalArgumentException("lastPrecisionScore must be in [0.0, 1.0], got " + lastPrecisionScore);
        }
        if (totalCasesAnalyzed < 0) {
            throw new IllegalArgumentException("totalCasesAnalyzed must be >= 0");
        }
        if (lastAvgFlowTimeMs < 0.0) {
            throw new IllegalArgumentException("lastAvgFlowTimeMs must be >= 0.0");
        }
    }

    /**
     * Create a new session with auto-generated session ID and current timestamp.
     *
     * @param specificationId YAWL specification identifier
     * @return new session with zero metrics and lastAnalyzedAt = null
     */
    public static ProcessMiningSession start(String specificationId) {
        return new ProcessMiningSession(
                UUID.randomUUID().toString(),
                specificationId,
                Instant.now(),
                null,
                0,
                0.0,
                0.0,
                0.0);
    }

    /**
     * Return a new session with updated metrics and current timestamp.
     *
     * Creates an immutable copy with:
     * - totalCasesAnalyzed updated to cases
     * - lastFitnessScore updated to fitness
     * - lastPrecisionScore updated to precision
     * - lastAvgFlowTimeMs updated to avgFlowMs
     * - lastAnalyzedAt set to Instant.now()
     *
     * @param cases total cases analyzed
     * @param fitness conformance fitness score (0.0-1.0)
     * @param precision conformance precision score (0.0-1.0)
     * @param avgFlowMs average flow time in milliseconds
     * @return new ProcessMiningSession with updated metrics
     * @throws IllegalArgumentException if scores outside [0.0, 1.0] or cases < 0
     */
    public ProcessMiningSession withMetrics(
            int cases,
            double fitness,
            double precision,
            double avgFlowMs) {
        return new ProcessMiningSession(
                this.sessionId,
                this.specificationId,
                this.createdAt,
                Instant.now(),
                cases,
                fitness,
                precision,
                avgFlowMs);
    }

    /**
     * Return human-readable multi-line summary string.
     *
     * Format:
     * <pre>
     * ProcessMiningSession {
     *   sessionId: <id>
     *   specificationId: <id>
     *   createdAt: <timestamp>
     *   lastAnalyzedAt: <timestamp or "never">
     *   totalCasesAnalyzed: <count>
     *   lastFitnessScore: <score>
     *   lastPrecisionScore: <score>
     *   lastAvgFlowTimeMs: <milliseconds>
     * }
     * </pre>
     *
     * @return formatted summary string
     */
    public String toSummary() {
        return """
                ProcessMiningSession {
                  sessionId: %s
                  specificationId: %s
                  createdAt: %s
                  lastAnalyzedAt: %s
                  totalCasesAnalyzed: %d
                  lastFitnessScore: %.3f
                  lastPrecisionScore: %.3f
                  lastAvgFlowTimeMs: %.2f
                }""".formatted(
                        sessionId,
                        specificationId,
                        createdAt,
                        lastAnalyzedAt != null ? lastAnalyzedAt : "never",
                        totalCasesAnalyzed,
                        lastFitnessScore,
                        lastPrecisionScore,
                        lastAvgFlowTimeMs);
    }

    /**
     * Check if this session has recorded analysis results.
     *
     * @return true if lastAnalyzedAt is not null and totalCasesAnalyzed > 0
     */
    public boolean hasAnalyzed() {
        return lastAnalyzedAt != null && totalCasesAnalyzed > 0;
    }
}
