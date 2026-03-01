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

package org.yawlfoundation.yawl.datamodelling;

/**
 * Exception thrown by ODPS validation operations.
 *
 * <p>Raised when YAML schema fails validation against ODPS (Open Data Policy Specification)
 * rules. The exception message contains details of the validation failure.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class OdpsValidationException extends RuntimeException {

    /**
     * Constructs an exception with the given validation error message.
     *
     * @param message the validation error details; must not be null
     */
    public OdpsValidationException(String message) {
        super(message);
    }

    /**
     * Constructs an exception with the given message and cause.
     *
     * @param message the validation error details; must not be null
     * @param cause the underlying cause; may be null
     */
    public OdpsValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
