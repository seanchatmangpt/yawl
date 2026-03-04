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

package org.yawlfoundation.yawl.tpot2;

/**
 * Checked exception for TPOT2 AutoML failures.
 *
 * <p>Wraps underlying causes and identifies which TPOT2 operation encountered the error.
 * Operations are: "automl" (training/subprocess failures), "config" (configuration errors).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class Tpot2Exception extends Exception {

    private final String operation;

    /**
     * Construct a TPOT2 exception with a message and operation identifier.
     *
     * @param message Human-readable error description
     * @param operation TPOT2 operation name (e.g., "automl", "config")
     */
    public Tpot2Exception(String message, String operation) {
        super(message);
        this.operation = operation;
    }

    /**
     * Construct a TPOT2 exception with a message, operation identifier, and underlying cause.
     *
     * @param message Human-readable error description
     * @param operation TPOT2 operation name
     * @param cause Underlying exception
     */
    public Tpot2Exception(String message, String operation, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }

    /**
     * Get the TPOT2 operation name that failed.
     *
     * @return Operation identifier
     */
    public String getOperation() {
        return operation;
    }
}
