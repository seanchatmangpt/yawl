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

package org.yawlfoundation.yawl.stateless.engine;

import java.time.Instant;

/**
 * Immutable workflow execution context for a single stateless YAWL case invocation.
 *
 * <p>WorkflowContext is a Java 25 record that replaces the previous ad-hoc use of
 * {@code ThreadLocal<String>} for case-ID propagation in the stateless engine.
 * It is bound per-invocation using {@link java.lang.ScopedValue} so that every
 * virtual thread participating in a case's execution tree inherits the same immutable
 * context automatically, without any explicit passing.</p>
 *
 * <h2>Usage pattern</h2>
 * <pre>{@code
 * WorkflowContext ctx = WorkflowContext.of(caseID, specID, engineNbr);
 * ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx, () -> {
 *     runner.continueIfPossible();
 *     runner.start();
 *     return null;
 * });
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>Records are structurally immutable. ScopedValue bindings are inherited by child
 * virtual threads created within a {@code StructuredTaskScope} and are released
 * automatically when the enclosing scope exits — no cleanup required.</p>
 *
 * @param caseID    the string representation of the YAWL case identifier
 * @param specID    the string representation of the specification identifier (uri:version)
 * @param engineNbr the ordinal of the YEngine instance handling this invocation
 * @param startedAt the instant at which this context was created
 *
 * @author YAWL Foundation
 * @see YEngine#WORKFLOW_CONTEXT
 */
public record WorkflowContext(
        String caseID,
        String specID,
        int engineNbr,
        Instant startedAt
) {

    /**
     * Compact canonical constructor — validates required fields.
     *
     * @throws IllegalArgumentException if {@code caseID} or {@code specID} is null or blank,
     *                                  or if {@code startedAt} is null
     */
    public WorkflowContext {
        if (caseID == null || caseID.isBlank()) {
            throw new IllegalArgumentException("WorkflowContext: caseID must not be null or blank");
        }
        if (specID == null || specID.isBlank()) {
            throw new IllegalArgumentException("WorkflowContext: specID must not be null or blank");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("WorkflowContext: startedAt must not be null");
        }
    }

    /**
     * Convenience factory — captures the context at the current instant.
     *
     * @param caseID    string form of the case identifier
     * @param specID    string form of the specification identifier
     * @param engineNbr the ordinal of the YEngine instance
     * @return a new context stamped with {@code Instant.now()}
     */
    public static WorkflowContext of(String caseID, String specID, int engineNbr) {
        return new WorkflowContext(caseID, specID, engineNbr, Instant.now());
    }

    /**
     * Returns a concise description for use in log messages and span attributes.
     *
     * @return {@code "engine/<engineNbr> case/<caseID> spec/<specID>"}
     */
    public String toLogString() {
        return "engine/" + engineNbr + " case/" + caseID + " spec/" + specID;
    }
}
