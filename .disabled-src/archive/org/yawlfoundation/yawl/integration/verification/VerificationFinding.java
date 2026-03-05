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

package org.yawlfoundation.yawl.integration.verification;

import java.util.Objects;

/**
 * Immutable record representing a single verification finding
 * (detected deadlock or soundness issue) in a workflow.
 *
 * <p>Each finding includes:
 * <ul>
 *   <li>The deadlock pattern that was detected</li>
 *   <li>The task or place ID involved</li>
 *   <li>A human-readable description</li>
 *   <li>Severity level (ERROR, WARNING, INFO)</li>
 * </ul>
 *
 * @param pattern the detected {@link DeadlockPattern}
 * @param taskId the ID of the affected task or place
 * @param description detailed description of the issue
 * @param severity severity level ({@link Severity})
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record VerificationFinding(
    DeadlockPattern pattern,
    String taskId,
    String description,
    Severity severity
) {
    /**
     * Severity enumeration for verification findings.
     */
    public enum Severity {
        /** Critical issue that prevents workflow execution */
        ERROR,
        /** Potential issue that may cause problems in certain scenarios */
        WARNING,
        /** Informational finding for workflow improvement */
        INFO
    }

    /**
     * Constructs a VerificationFinding with the given parameters.
     *
     * @param pattern the detected deadlock pattern (non-null)
     * @param taskId the affected task/place ID (non-null, non-empty)
     * @param description human-readable description (non-null, non-empty)
     * @param severity severity level (non-null)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if taskId or description is empty
     */
    public VerificationFinding {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(severity, "severity must not be null");

        if (taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be empty");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be empty");
        }
    }

    /**
     * Returns a formatted string representation of this finding.
     *
     * @return string in format: "[SEVERITY] PATTERN_NAME: taskId - description"
     */
    @Override
    public String toString() {
        return "[%s] %s: %s - %s"
            .formatted(severity, pattern.displayName(), taskId, description);
    }
}
