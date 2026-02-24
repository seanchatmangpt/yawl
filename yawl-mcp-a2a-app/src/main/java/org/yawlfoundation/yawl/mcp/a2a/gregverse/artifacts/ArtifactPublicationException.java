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
 * Exception thrown when artifact publication fails.
 *
 * <p>This exception is thrown when there's an error during the publication
 * of artifacts to the GregVerse marketplace. It can be caused by various
 * issues such as validation failures, storage errors, or network problems.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ArtifactPublicationException extends Exception {

    /**
     * Creates a new ArtifactPublicationException with the specified detail message.
     *
     * @param message the detail message
     */
    public ArtifactPublicationException(String message) {
        super(message);
    }

    /**
     * Creates a new ArtifactPublicationException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public ArtifactPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}