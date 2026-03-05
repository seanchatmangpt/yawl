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

package org.yawlfoundation.yawl.engine;

import java.time.Instant;

/**
 * Immutable snapshot of the execution context for a single YAWL case invocation.
 *
 * <p>CaseExecutionContext is a Java 25 record that captures the identifiers needed to
 * correlate a unit of work (case start, task kick, or task completion) with its
 * surrounding engine context. It is designed to be bound via
 * {@link java.lang.ScopedValue} so that every virtual thread participating in the
 * execution of one case can read the same immutable context without synchronisation.</p>
 *
 * <h2>Usage pattern</h2>
 * <pre>{@code
 * CaseExecutionContext ctx = new CaseExecutionContext(caseID, specID, Instant.now());
 * ScopedValue.callWhere(YNetRunner.CASE_CONTEXT, ctx.caseID(), () -> {
 *     runner.kick(pmgr);
 *     return null;
 * });
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>Records are inherently immutable. The ScopedValue binding is inherited
 * automatically by child virtual threads (e.g., {@code StructuredTaskScope} subtasks)
 * and released when the enclosing {@code ScopedValue.callWhere()} scope exits.</p>
 *
 * @param caseID   the string representation of the YAWL case identifier
 * @param specID   the string representation of the specification identifier (key:version)
 * @param startedAt the instant at which this execution context was created
 *
 * @author YAWL Foundation
 * @see YNetRunner#CASE_CONTEXT
 */
public record CaseExecutionContext(
        String caseID,
        String specID,
        Instant startedAt
) {

    /**
     * Compact canonical constructor — validates non-null, non-blank identifiers.
     *
     * @throws IllegalArgumentException if {@code caseID} or {@code specID} is null or blank
     */
    public CaseExecutionContext {
        if (caseID == null || caseID.isBlank()) {
            throw new IllegalArgumentException("CaseExecutionContext: caseID must not be null or blank");
        }
        if (specID == null || specID.isBlank()) {
            throw new IllegalArgumentException("CaseExecutionContext: specID must not be null or blank");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("CaseExecutionContext: startedAt must not be null");
        }
    }

    /**
     * Convenience factory — captures the context at the current instant.
     *
     * @param caseID string form of the case identifier
     * @param specID string form of the specification identifier
     * @return a new context stamped with {@code Instant.now()}
     */
    public static CaseExecutionContext of(String caseID, String specID) {
        return new CaseExecutionContext(caseID, specID, Instant.now());
    }

    /**
     * Returns a concise description for use in log messages and span attributes.
     *
     * @return {@code "case/<caseID> spec/<specID>"}
     */
    public String toLogString() {
        return "case/" + caseID + " spec/" + specID;
    }
}
