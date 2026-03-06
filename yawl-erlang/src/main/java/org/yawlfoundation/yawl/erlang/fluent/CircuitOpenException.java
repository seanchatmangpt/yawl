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
package org.yawlfoundation.yawl.erlang.fluent;

import java.io.Serial;

/**
 * Thrown when a pipeline stage's circuit breaker is in the OPEN state,
 * indicating that the stage has failed too many times and further
 * execution is blocked until the reset timeout elapses.
 */
public final class CircuitOpenException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String stageName;
    private final int consecutiveFailures;

    public CircuitOpenException(String stageName, int consecutiveFailures) {
        super("Circuit breaker OPEN for stage '" + stageName +
              "' after " + consecutiveFailures + " consecutive failures");
        this.stageName = stageName;
        this.consecutiveFailures = consecutiveFailures;
    }

    public String stageName() { return stageName; }

    public int consecutiveFailures() { return consecutiveFailures; }
}
