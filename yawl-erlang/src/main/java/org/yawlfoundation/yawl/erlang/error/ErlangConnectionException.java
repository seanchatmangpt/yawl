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
 * Thrown when the Erlang distribution handshake fails or the connection is refused.
 * Common causes: wrong cookie, node not running, EPMD not reachable, firewall blocking port.
 *
 * <p>Remediation: verify the node is started ({@code erl -name foo@host -setcookie cookie}),
 * confirm EPMD is running ({@code epmd -names}), and check the cookie matches.</p>
 */
public class ErlangConnectionException extends ErlangException {

    /** The target node name that could not be connected, e.g. {@code "foo@localhost"}. */
    private final String targetNode;

    /**
     * @param targetNode node name that the connection was attempted to
     * @param message    actionable failure description
     */
    public ErlangConnectionException(String targetNode, String message) {
        super("Cannot connect to Erlang node '" + targetNode + "': " + message);
        this.targetNode = targetNode;
    }

    /**
     * @param targetNode node name that the connection was attempted to
     * @param message    actionable failure description
     * @param cause      root cause from the erl_interface layer
     */
    public ErlangConnectionException(String targetNode, String message, Throwable cause) {
        super("Cannot connect to Erlang node '" + targetNode + "': " + message, cause);
        this.targetNode = targetNode;
    }

    /** Returns the Erlang node name that was the target of the failed connection. */
    public String getTargetNode() { return targetNode; }
}
