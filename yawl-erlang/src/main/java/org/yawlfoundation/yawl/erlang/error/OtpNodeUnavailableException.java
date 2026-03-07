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
package org.yawlfoundation.yawl.erlang.error;

/**
 * Thrown when an OTP node is unavailable — either because it failed to start,
 * timed out during startup, or because the circuit breaker is in OPEN state
 * (fast-failing without making a network call).
 *
 * <p>Remediation:
 * <ul>
 *   <li>If thrown during node start: verify OTP 28 is installed and EPMD is running.</li>
 *   <li>If thrown by the circuit breaker: the node recently experienced repeated failures;
 *       wait for the circuit to transition to HALF_OPEN (default 30s) before retrying.</li>
 * </ul>
 */
public class OtpNodeUnavailableException extends ErlangException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with an actionable message.
     *
     * @param message description of why the node is unavailable
     */
    public OtpNodeUnavailableException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and underlying cause.
     *
     * @param message description of why the node is unavailable
     * @param cause   the root cause (e.g. IOException, TimeoutException)
     */
    public OtpNodeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
