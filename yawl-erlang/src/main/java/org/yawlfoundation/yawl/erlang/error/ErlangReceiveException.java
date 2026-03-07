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
 * Thrown when receiving a message from the Erlang distribution fails.
 * Common causes: decode error (malformed ETF), connection closed mid-receive,
 * binary exceeding the 2 GB OTP limit.
 *
 * <p>Remediation: check the sending node encodes terms with the correct OTP version,
 * verify the message size is within OTP limits, and confirm the connection is alive.</p>
 */
public class ErlangReceiveException extends ErlangException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message actionable failure description
     */
    public ErlangReceiveException(String message) {
        super(message);
    }

    /**
     * @param message actionable failure description
     * @param cause   underlying decode or I/O error
     */
    public ErlangReceiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
