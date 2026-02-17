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

import org.yawlfoundation.yawl.elements.YExternalNetElement;

import java.io.Serial;

/**
 * Exception thrown when an invalid connection is attempted between YAWL net elements.
 *
 * @author Lachlan Aldred
 */
public class YConnectivityException extends YSyntaxException {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Constructs a new connectivity exception for an invalid connection between two elements.
     *
     * @param source      the source net element
     * @param destination the destination net element
     */
    public YConnectivityException(YExternalNetElement source, YExternalNetElement destination) {
        super("YAWL Syntax does not permit %s to be connected with %s".formatted(source, destination));
    }

    /**
     * Constructs a new connectivity exception with the specified message.
     *
     * @param message the detail message
     */
    public YConnectivityException(String message) {
        super(message);
    }

    /**
     * Constructs a new connectivity exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public YConnectivityException(String message, Throwable cause) {
        super(message, cause);
    }
}
