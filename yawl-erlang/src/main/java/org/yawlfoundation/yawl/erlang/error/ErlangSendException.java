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
 * Thrown when sending a message to a registered Erlang process fails.
 * Common causes: process not registered, connection closed, encoding error.
 *
 * <p>Remediation: verify the target process is registered ({@code erlang:whereis/1}),
 * confirm the message term is valid, and check the connection is alive.</p>
 */
public class ErlangSendException extends ErlangException {

    private final String targetName;

    /**
     * @param targetName registered process name the message was sent to
     * @param message    actionable failure description
     */
    public ErlangSendException(String targetName, String message) {
        super("Failed to send to registered process '" + targetName + "': " + message);
        this.targetName = targetName;
    }

    /**
     * @param targetName registered process name the message was sent to
     * @param message    actionable failure description
     * @param cause      underlying erl_interface error
     */
    public ErlangSendException(String targetName, String message, Throwable cause) {
        super("Failed to send to registered process '" + targetName + "': " + message, cause);
        this.targetName = targetName;
    }

    /** Returns the registered process name that was the target of the failed send. */
    public String getTargetName() { return targetName; }
}
