/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.pi;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable session state for Process Intelligence operations.
 *
 * <p>Tracks the lifecycle of a PI analysis session including creation time,
 * specification identity, and last analysis timestamp.
 *
 * @param sessionId Unique session identifier (UUID)
 * @param specificationId Workflow specification being analyzed
 * @param createdAt Session creation timestamp
 * @param lastAnalyzedAt Timestamp of most recent analysis (null if never analyzed)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record PISession(
    String sessionId,
    String specificationId,
    Instant createdAt,
    Instant lastAnalyzedAt
) {

    /**
     * Start a new PI session for a specification.
     *
     * <p>Creates a session with a generated UUID and current timestamp.
     * lastAnalyzedAt is set to null initially.
     *
     * @param specificationId Workflow specification identifier
     * @return New PISession with current timestamp
     * @throws NullPointerException if specificationId is null
     */
    public static PISession start(String specificationId) {
        if (specificationId == null) {
            throw new NullPointerException("specificationId is required");
        }
        return new PISession(
            UUID.randomUUID().toString(),
            specificationId,
            Instant.now(),
            null
        );
    }
}
