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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.exceptions;

/**
 * Exception which indicates a failure has occurred within the persistence layer of the YAWL engine.
 *
 * <p>This exception should be caught and handled as a fatal exception within the engine code. As it
 * indicates some failure to persist a runtime object to storage, the usual action would be to gracefully
 * terminate the engine without processing any other work.
 *
 * @author Lachlan Aldred
 */
public class YPersistenceException extends YAWLException {
    private static final long serialVersionUID = 2L;

    /**
     * Constructs a new persistence exception with no detail message.
     */
    public YPersistenceException() {
        super();
    }

    /**
     * Constructs a new persistence exception with the specified detail message.
     *
     * @param message the detail message
     */
    public YPersistenceException(String message) {
        super(message);
    }

    /**
     * Constructs a new persistence exception with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public YPersistenceException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new persistence exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public YPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
