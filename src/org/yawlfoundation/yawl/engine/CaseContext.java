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
