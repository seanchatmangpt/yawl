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

package org.yawlfoundation.yawl.integration.fmea;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable record holding the result of a single {@link MarketplaceFmeaAnalyzer} analysis.
 *
 * <p>A report is always produced — even when no violations are found — so callers
 * can inspect status and RPN without null-checking.
 *
 * <p>Status values:
 * <ul>
 *   <li>{@code "GREEN"} — no violations detected; the marketplace event passed all checks</li>
 *   <li>{@code "RED"}   — one or more violations detected; action required</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
 *     eventId, eventType, idempotencyKey, sequenceNumber,
 *     sourceAgent, processedKeys, lastSeenSequence, knownEventTypes);
 * if (!report.isClean()) {
 *     log.error("Marketplace FMEA {} (totalRpn={}): {}",
 *         report.status(), report.totalRpn(), report.violations());
 *     throw new SecurityException("Marketplace event failed FMEA checks");
 * }
 * }</pre>
 *
 * @param analyzedAt  timestamp when the analysis was performed; never {@code null}
 * @param violations  immutable list of detected violations; empty when clean
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record MarketplaceFmeaReport(
        Instant analyzedAt,
        List<MarketplaceFmeaViolation> violations) {

    /**
     * Compact canonical constructor — defensive copy + null validation.
     */
    public MarketplaceFmeaReport {
        Objects.requireNonNull(analyzedAt, "analyzedAt must not be null");
        Objects.requireNonNull(violations, "violations must not be null");
        violations = List.copyOf(violations);   // immutable snapshot
    }

    /**
     * Returns {@code true} when no violations were detected.
     *
     * @return {@code true} iff {@link #violations()} is empty
     */
    public boolean isClean() {
        return violations.isEmpty();
    }

    /**
     * Returns {@code "GREEN"} when the report is clean, {@code "RED"} otherwise.
     *
     * @return {@code "GREEN"} or {@code "RED"}
     */
    public String status() {
        return violations.isEmpty() ? "GREEN" : "RED";
    }

    /**
     * Sum of the RPN values of all detected violations.
     * Zero when the report is clean.
     *
     * @return total RPN (0 when clean)
     */
    public int totalRpn() {
        return violations.stream()
                .mapToInt(MarketplaceFmeaViolation::rpn)
                .sum();
    }
}
