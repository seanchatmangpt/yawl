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

package org.yawlfoundation.yawl.integration.immune;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable record of a predicted deadlock for a specific case instance.
 *
 * <p>When a task firing could lead to a deadlock state (as detected by
 * WorkflowImmuneSystem), this record encapsulates:
 * <ul>
 *   <li>The case ID affected by the prediction</li>
 *   <li>The task ID that was just fired</li>
 *   <li>The type of deadlock pattern found (e.g., "IMPLICIT_DEADLOCK", "LIVELOCK")</li>
 *   <li>Elements (tasks/places) involved in the deadlock</li>
 *   <li>Confidence level (0.0 = unsure, 1.0 = certain)</li>
 *   <li>Timestamp when prediction was made</li>
 * </ul>
 *
 * @param caseId           the case identifier (non-null, non-empty)
 * @param firedTaskId      the task that was just fired (non-null, non-empty)
 * @param findingType      the deadlock pattern name from SoundnessVerifier
 *                         (e.g., "IMPLICIT_DEADLOCK", "ORPHANED_PLACE", "LIVELOCK")
 * @param affectedElements set of task/place IDs involved (may be empty)
 * @param confidence       probability 0.0..1.0 that deadlock will occur
 * @param timestamp        when the prediction was generated (non-null)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record DeadlockPrediction(
    String caseId,
    String firedTaskId,
    String findingType,
    Set<String> affectedElements,
    double confidence,
    Instant timestamp
) {
    /**
     * Canonical constructor with validation.
     *
     * @throws NullPointerException if caseId, firedTaskId, findingType, or timestamp is null
     * @throws IllegalArgumentException if caseId or firedTaskId is empty,
     *                                  or confidence is not in [0.0, 1.0]
     */
    public DeadlockPrediction {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(firedTaskId, "firedTaskId must not be null");
        Objects.requireNonNull(findingType, "findingType must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        if (caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be empty");
        }
        if (firedTaskId.isBlank()) {
            throw new IllegalArgumentException("firedTaskId must not be empty");
        }
        if (findingType.isBlank()) {
            throw new IllegalArgumentException("findingType must not be empty");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in range [0.0, 1.0]");
        }

        // Normalize null affectedElements to empty set
        if (affectedElements == null) {
            affectedElements = Set.of();
        }
    }
}
