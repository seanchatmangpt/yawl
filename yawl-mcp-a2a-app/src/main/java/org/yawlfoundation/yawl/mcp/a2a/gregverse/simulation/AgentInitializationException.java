/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation;

/**
 * Thrown when a Greg-Verse agent cannot be initialized or resolved.
 *
 * <p>This exception indicates that an agent resolution request failed, either
 * because the agent ID was not found in the registry or because instantiation
 * of the agent threw an exception.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class AgentInitializationException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message the error message
     */
    public AgentInitializationException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public AgentInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
