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
 * Erlang process identifier (PID). Uniquely identifies a process on an Erlang node.
 *
 * <p>Encoded as NEW_PID_EXT (tag 88) in OTP 23+. The PID consists of:
 * <ul>
 *   <li>node: the node name (e.g., "mynode@localhost")</li>
 *   <li>id: the process ID (32-bit unsigned)</li>
 *   <li>serial: serial number for process reuse (32-bit unsigned)</li>
 *   <li>creation: creation time counter (32-bit unsigned)</li>
 * </ul>
 */
public record ErlPid(String node, int id, int serial, int creation) implements ErlTerm {

    /**
     * Constructs an ErlPid.
     *
     * @param node non-null node name (e.g., "node@host")
     * @param id process id
     * @param serial serial number
     * @param creation creation counter
     * @throws IllegalArgumentException if node is null
     */
    public ErlPid {
        if (node == null) {
            throw new IllegalArgumentException("ErlPid node must not be null");
        }
    }
}
