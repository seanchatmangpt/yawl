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

import org.yawlfoundation.yawl.elements.YExternalNetElement;

/**
 * Exception thrown when a syntax error is encountered in YAWL specification.
 *
 * @author Lachlan Aldred
 */
public class YSyntaxException extends YAWLException {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Constructs a new syntax exception with no detail message.
     */
    public YSyntaxException() {
        super();
    }

    /**
     * Constructs a new syntax exception with the specified detail message.
     *
     * @param message the detail message
     */
    public YSyntaxException(String message) {
        super(message);
    }

    /**
     * Constructs a new syntax exception for an error related to a specific net element.
     *
     * @param element the net element where the syntax error occurred
     * @param message the detail message describing the error
     */
    public YSyntaxException(YExternalNetElement element, String message) {
        super(element.toString() + " " + message);
    }

    /**
     * Constructs a new syntax exception with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public YSyntaxException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new syntax exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public YSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
