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

/**
 * ScopedValue context bindings for YAWL case execution.
 *
 * <p>CaseContext provides scoped values for context propagation across virtual threads
 * in YAWL workflow execution. These bindings are immutable within scope and automatically
 * inherited by child virtual threads.</p>
 *
 * <h2>Usage pattern</h2>
 * <pre>{@code
 * ScopedValue.where(CaseContext.CASE_ID, caseId)
 *     .where(CaseContext.CORRELATION_ID, correlationId)
 *     .where(CaseContext.START_TIME, Instant.now())
 *     .run(() -> executeCase());
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>ScopedValue bindings are immutable and inherited by forked virtual threads.
 * They are automatically released when the enclosing scope exits.</p>
 *
 * @author YAWL Foundation
 */
public final class CaseContext {

    /**
     * ScopedValue binding for the case identifier.
     * Carries the YAWL case ID across virtual threads.
     */
    public static final ScopedValue<String> CASE_ID = ScopedValue.newInstance();

    /**
     * ScopedValue binding for the correlation ID.
     * Carries a correlation identifier for tracing and debugging purposes.
     */
    public static final ScopedValue<String> CORRELATION_ID = ScopedValue.newInstance();

    /**
     * ScopedValue binding for the start time.
     * Carries the timestamp when the case execution began.
     */
    public static final ScopedValue<Instant> START_TIME = ScopedValue.newInstance();

    private CaseContext() {
        // Private constructor to prevent instantiation
    }
}
