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
package org.yawlfoundation.yawl.erlang.processmining;

import org.yawlfoundation.yawl.erlang.term.ErlTerm;

/**
 * Result record for OCEL2 operations.
 *
 * <p>Encapsulates the result of OCEL2 parsing and query operations,
 * supporting both success and error cases in a single type.</p>
 *
 * @param success  whether the operation succeeded
 * @param handle   OCEL2 log handle for subsequent operations (null if error)
 * @param count    count value for count operations (events, objects)
 * @param error    error message if operation failed
 */
public record Ocel2Result(
    boolean success,
    Object handle,
    long count,
    String error
) {
    /**
     * Creates a successful result with a handle.
     */
    public static Ocel2Result success(Object handle) {
        return new Ocel2Result(true, handle, 0, null);
    }

    /**
     * Creates a successful result with a count.
     */
    public static Ocel2Result count(long count) {
        return new Ocel2Result(true, null, count, null);
    }

    /**
     * Creates a failed result with an error message.
     */
    public static Ocel2Result error(String error) {
        return new Ocel2Result(false, null, 0, error);
    }
}
