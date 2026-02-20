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

package org.yawlfoundation.yawl.exceptions;

import java.io.Serial;

/**
 * Exception thrown when a user or tenant is not authorized to access a resource.
 *
 * <p>This exception is thrown by the authorization subsystem when a tenant
 * attempts to access a case or resource they do not have permission for.</p>
 *
 * @since 6.0.0
 */
public class UnauthorizedException extends YAWLException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new unauthorized exception with no detail message.
     */
    public UnauthorizedException() {
        super();
    }

    /**
     * Constructs a new unauthorized exception with the specified detail message.
     *
     * @param message the detail message
     */
    public UnauthorizedException(String message) {
        super(message);
    }

    /**
     * Constructs a new unauthorized exception with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public UnauthorizedException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new unauthorized exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
