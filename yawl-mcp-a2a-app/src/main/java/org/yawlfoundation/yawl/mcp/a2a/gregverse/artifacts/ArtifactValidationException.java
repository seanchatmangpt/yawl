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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.artifacts;

/**
 * Exception thrown when artifact validation fails.
 *
 * <p>This exception is thrown when an artifact fails validation before publication.
 * Validation failures can occur due to missing required fields, invalid data formats,
 * or business rule violations.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ArtifactValidationException extends Exception {

    /**
     * Creates a new ArtifactValidationException with the specified detail message.
     *
     * @param message the detail message
     */
    public ArtifactValidationException(String message) {
        super(message);
    }
}