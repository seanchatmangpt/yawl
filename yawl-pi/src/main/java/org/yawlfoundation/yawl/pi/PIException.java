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

package org.yawlfoundation.yawl.pi;

/**
 * Checked exception for Process Intelligence module failures.
 *
 * <p>Wraps underlying causes and identifies which PI connection encountered the error.
 * Connections are: "predictive", "prescriptive", "optimization", "rag", "dataprep".
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class PIException extends Exception {

    private final String connection;

    /**
     * Construct a PI exception with a message and connection identifier.
     *
     * @param message Human-readable error description
     * @param connection PI connection name (e.g., "predictive", "prescriptive")
     */
    public PIException(String message, String connection) {
        super(message);
        this.connection = connection;
    }

    /**
     * Construct a PI exception with a message, connection identifier, and underlying cause.
     *
     * @param message Human-readable error description
     * @param connection PI connection name
     * @param cause Underlying exception
     */
    public PIException(String message, String connection, Throwable cause) {
        super(message, cause);
        this.connection = connection;
    }

    /**
     * Get the PI connection name that failed.
     *
     * @return Connection identifier
     */
    public String getConnection() {
        return connection;
    }
}
