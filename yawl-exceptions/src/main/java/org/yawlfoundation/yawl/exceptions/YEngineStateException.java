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

import java.io.Serial;

/**
 * Exception thrown when the engine state is invalid in relation to the request being actioned.
 */
public class YEngineStateException extends YAWLException {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Constructs a new engine state exception with no detail message.
     */
    public YEngineStateException() {
        super();
    }

    /**
     * Constructs a new engine state exception with the specified detail message.
     *
     * @param message the detail message
     */
    public YEngineStateException(String message) {
        super(message);
    }

    /**
     * Constructs a new engine state exception with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public YEngineStateException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new engine state exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public YEngineStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
