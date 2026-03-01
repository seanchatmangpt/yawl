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
package org.yawlfoundation.yawl.erlang.term;

/**
 * Erlang port. A reference to an I/O port (e.g., file, socket, pipe) on an Erlang node.
 *
 * <p>Encoded as V4_PORT_EXT (tag 120) in OTP 26+. A port consists of:
 * <ul>
 *   <li>node: the node name where the port is allocated</li>
 *   <li>id: the port identifier (64-bit unsigned)</li>
 *   <li>creation: creation time counter</li>
 * </ul>
 */
public record ErlPort(String node, long id, int creation) implements ErlTerm {

    /**
     * Constructs an ErlPort.
     *
     * @param node non-null node name
     * @param id port identifier
     * @param creation creation counter
     * @throws IllegalArgumentException if node is null
     */
    public ErlPort {
        if (node == null) {
            throw new IllegalArgumentException("ErlPort node must not be null");
        }
    }
}
