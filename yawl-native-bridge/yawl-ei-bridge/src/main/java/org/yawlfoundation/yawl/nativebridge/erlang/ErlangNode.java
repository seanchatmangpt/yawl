/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.nativebridge.erlang;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stub implementation of ErlangNode.
 *
 * This class requires jextract-generated native code to function properly.
 * The full implementation requires:
 * - ei_cnode_t and ei_x_buff_t structures from jextract
 * - Native library functions for Erlang communication
 * - Proper error handling and connection management
 *
 * @see IMPLEMENTATION_SUMMARY.md for build instructions
 */
public class ErlangNode {

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private String nodeName;

    public ErlangNode() {
        throw new UnsupportedOperationException(
            "ErlangNode requires jextract-generated native code. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    /**
     * Connects to an Erlang node.
     *
     * @param nodeName The Erlang node name
     * @param cookie The Erlang cookie
     * @throws ErlangException if connection fails
     */
    public void connect(String nodeName, String cookie) throws ErlangException {
        throw new UnsupportedOperationException(
            "connect() requires jextract-generated native code. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    /**
     * Disconnects from the Erlang node.
     */
    public void disconnect() {
        throw new UnsupportedOperationException(
            "disconnect() requires jextract-generated native code. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    /**
     * Makes an RPC call to an Erlang module.
     *
     * @param module The module name
     * @param function The function name
     * @param arguments The arguments
     * @return The result
     * @throws ErlangException if the RPC fails
     */
    public ErlTerm rpc(String module, String function, List<ErlTerm> arguments) throws ErlangException {
        throw new UnsupportedOperationException(
            "rpc() requires jextract-generated native code. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }

    /**
     * Checks if the node is connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected.get() && !closed.get();
    }

    /**
     * Checks if the connection is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes the connection.
     *
     * @throws ErlangException if closing fails
     */
    public void close() throws ErlangException {
        throw new UnsupportedOperationException(
            "close() requires jextract-generated native code. " +
            "See IMPLEMENTATION_SUMMARY.md for build instructions."
        );
    }
}