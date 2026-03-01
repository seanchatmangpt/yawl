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

package org.yawlfoundation.yawl.qlever;

import java.lang.foreign.MemorySegment;

/**
 * Status record for QLever operations.
 *
 * <p>This record encapsulates the result of a QLever operation, including:
 * <ul>
 *   <li>The native result handle (MemorySegment)</li>
 *   <li>Error message (null if successful)</li>
 *   <li>HTTP status code (200 for success, 400/500 for errors)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
public record QLeverStatus(
    MemorySegment result,
    String error,
    int httpStatus
) {
    /**
     * Creates a successful status with HTTP 200.
     *
     * @param result the native result handle
     */
    public QLeverStatus(MemorySegment result) {
        this(result, null, 200);
    }

    /**
     * Creates an error status with the specified HTTP status code.
     *
     * @param error the error message
     * @param httpStatus the HTTP status code (typically 400 or 500)
     */
    public QLeverStatus(String error, int httpStatus) {
        this(null, error, httpStatus);
    }

    /**
     * Checks if the operation was successful (HTTP 200).
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean isSuccess() {
        return httpStatus == 200 && error == null;
    }

    /**
     * Checks if the operation resulted in an error.
     *
     * @return true if there was an error, false otherwise
     */
    public boolean isError() {
        return error != null || httpStatus != 200;
    }
}