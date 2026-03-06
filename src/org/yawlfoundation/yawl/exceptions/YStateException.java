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
 * Exception thrown when an invalid state is encountered in the YAWL engine.
 *
 * <p><b>Common causes:</b>
 * <ul>
 *   <li>Attempting to complete a work item that is not enabled
 *   <li>Starting a case that is already running
 *   <li>OR-join receiving data when not all branches have completed
 *   <li>Concurrent state modifications (race condition)
 *   <li>Case terminated before operation completed
 * </ul>
 *
 * <p><b>Recovery guidance:</b>
 * <ul>
 *   <li>Check case/work item status before operation
 *   <li>Verify OR-join conditions and branch completion
 *   <li>Review exception message for specific state constraint violated
 *   <li>See {@link #recoveryHint()} for context-specific advice
 * </ul>
 *
 * @author Lachlan Aldred
 * @since 23/04/2003
 */
public class YStateException extends YAWLException {
    @Serial
    private static final long serialVersionUID = 2L;

    private transient String recoveryHint;

    /**
     * Constructs a new state exception with no detail message.
     */
    public YStateException() {
        super();
    }

    /**
     * Constructs a new state exception with the specified detail message.
     *
     * @param message the detail message
     */
    public YStateException(String message) {
        super(message);
    }

    /**
     * Constructs a new state exception with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public YStateException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new state exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public YStateException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new state exception with message, cause, and recovery hint.
     *
     * @param message       the detail message
     * @param cause         the cause of this exception
     * @param recoveryHint  actionable guidance for resolving the state error
     */
    public YStateException(String message, Throwable cause, String recoveryHint) {
        super(message, cause);
        this.recoveryHint = recoveryHint;
    }

    /**
     * Returns actionable recovery guidance for this state error.
     *
     * @return recovery hint (may be null if not provided)
     */
    public String recoveryHint() {
        return recoveryHint;
    }

    @Override
    public String toString() {
        String base = super.toString();
        if (recoveryHint == null) {
            return base;
        }
        return base + " [Recovery hint: " + recoveryHint + "]";
    }
}
