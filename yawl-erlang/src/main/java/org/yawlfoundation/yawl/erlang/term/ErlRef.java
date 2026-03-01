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
 * Erlang reference. A globally unique identifier, typically used as a unique token
 * or request correlation identifier.
 *
 * <p>Encoded as NEWER_REFERENCE_EXT (tag 90) in OTP 23+. A reference consists of:
 * <ul>
 *   <li>node: the node name where the reference was created</li>
 *   <li>ids: array of 32-bit identifier components (minimum 1, typically 1-3)</li>
 *   <li>creation: creation time counter</li>
 * </ul>
 */
public record ErlRef(String node, int[] ids, int creation) implements ErlTerm {

    /**
     * Constructs an ErlRef.
     *
     * @param node non-null node name
     * @param ids non-null array of id components (defensively copied)
     * @param creation creation counter
     * @throws IllegalArgumentException if node or ids is null
     */
    public ErlRef {
        if (node == null) {
            throw new IllegalArgumentException("ErlRef node must not be null");
        }
        if (ids == null) {
            throw new IllegalArgumentException("ErlRef ids must not be null");
        }
        ids = ids.clone();
    }
}
