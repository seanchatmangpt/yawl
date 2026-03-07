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
 * Base checked exception for all Erlang/OTP 28 bridge failures.
 * Subclasses encode the specific failure mode (connection, RPC, send, receive).
 *
 * <p>All messages include actionable context: node name, operation attempted,
 * and a suggested remediation step.</p>
 */
public class ErlangException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a detailed message.
     *
     * @param message actionable description of the failure
     */
    public ErlangException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a detailed message and the underlying cause.
     *
     * @param message actionable description of the failure
     * @param cause   the root cause (e.g. IOException from the socket layer)
     */
    public ErlangException(String message, Throwable cause) {
        super(message, cause);
    }
}
