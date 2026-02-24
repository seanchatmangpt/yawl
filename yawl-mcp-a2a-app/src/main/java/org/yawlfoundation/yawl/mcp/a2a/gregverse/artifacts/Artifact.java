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

import java.time.LocalDateTime;

/**
 * Base interface for all GregVerse artifacts.
 *
 * <p>Artifacts are the deliverables produced by service providers and published
 * to the GregVerse marketplace. This interface defines the common contract
 * that all artifacts must implement.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface Artifact {
    /**
     * Returns the unique identifier for this artifact.
     *
     * @return the artifact ID
     */
    String getId();

    /**
     * Sets the unique identifier for this artifact.
     *
     * @param id the artifact ID
     */
    void setId(String id);

    /**
     * Returns the creation timestamp of this artifact.
     *
     * @return the creation timestamp
     */
    LocalDateTime getCreatedAt();

    /**
     * Sets the creation timestamp of this artifact.
     *
     * @param createdAt the creation timestamp
     */
    void setCreatedAt(LocalDateTime createdAt);

    /**
     * Returns the current status of this artifact.
     *
     * @return the artifact status
     */
    String getStatus();

    /**
     * Sets the current status of this artifact.
     *
     * @param status the artifact status
     */
    void setStatus(String status);
}