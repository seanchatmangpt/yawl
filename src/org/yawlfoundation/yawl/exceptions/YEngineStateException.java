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
 *
 * <p><b>Common causes:</b>
 * <ul>
 *   <li>Engine not initialized (null or not started)
 *   <li>Engine shut down before operation completed
 *   <li>Database connection lost or unavailable
 *   <li>Case not found (already removed or wrong ID)
 *   <li>Specification not loaded in engine
 * </ul>
 *
 * <p><b>Recovery guidance:</b>
 * <ul>
 *   <li>Verify engine is initialized and running
 *   <li>Check that required specifications are loaded
 *   <li>Verify database connectivity
 *   <li>Ensure case/spec IDs are correct
 *   <li>Retry operation after engine recovers from transient errors
 * </ul>
 *
 * @see org.yawlfoundation.yawl.engine.YEngine
 * @see org.yawlfoundation.yawl.stateless.YStatelessEngine
 */
public class YEngineStateException extends YAWLException {
    @Serial
    private static final long serialVersionUID = 2L;

    private transient String recoveryAction;

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

    /**
     * Constructs a new engine state exception with message, cause, and recovery action.
     *
     * @param message         the detail message
     * @param cause           the cause of this exception
     * @param recoveryAction  recommended action to recover from this error
     */
    public YEngineStateException(String message, Throwable cause, String recoveryAction) {
        super(message, cause);
        this.recoveryAction = recoveryAction;
    }

    /**
     * Returns the recommended recovery action for this engine state error.
     *
     * @return recovery action (may be null if not provided)
     */
    public String getRecoveryAction() {
        return recoveryAction;
    }
}
