/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.exceptions;

/**
 * Stub implementation of YPersistenceException.
 *
 * This is a temporary implementation that extends RuntimeException.
 * The real implementation needs to be properly integrated
 * from the original YAWL exceptions system.
 *
 * @author Generated Stub
 * @since YAWL v6.0.0
 */
public class YPersistenceException extends YAWLException {

    /**
     * Constructs a new YPersistenceException with the specified detail message.
     *
     * @param message the detail message (which is saved for later retrieval by the getMessage() method)
     */
    public YPersistenceException(String message) {
        super(message);
    }

    /**
     * Constructs a new YPersistenceException with the specified detail message and cause.
     *
     * @param message the detail message (which is saved for later retrieval by the getMessage() method)
     * @param cause the cause (which is saved for later retrieval by the getCause() method)
     */
    public YPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new YPersistenceException with the specified cause.
     *
     * @param cause the cause (which is saved for later retrieval by the getCause() method)
     */
    public YPersistenceException(Throwable cause) {
        super(cause);
    }
}