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

package org.yawlfoundation.yawl.elements.results;

import java.time.Instant;
import java.util.Objects;

/**
 * A {@link WorkflowResult} indicating that a workflow executed to successful completion.
 *
 * <p>This result carries the output data produced by the final net of the workflow.
 * Output data is the XML payload of the case data after the output condition fires.</p>
 *
 * <p>Usage in exhaustive pattern matching:</p>
 * <pre>{@code
 * if (result instanceof SuccessfulWorkflow s) {
 *     processOutput(s.outputData());
 * }
 * }</pre>
 */
public final class SuccessfulWorkflow extends WorkflowResult {

    private final String outputData;

    /**
     * Constructs a successful workflow result.
     *
     * @param workflowID  the identifier of the executed workflow specification
     * @param durationMs  elapsed execution time in milliseconds
     * @param completedAt the instant the workflow reached its output condition
     * @param outputData  the XML-formatted case output data; may be empty but not null
     * @throws IllegalArgumentException if {@code workflowID} is null or blank,
     *                                  or {@code durationMs} is negative
     * @throws NullPointerException     if {@code completedAt} or {@code outputData} is null
     */
    public SuccessfulWorkflow(String workflowID, long durationMs,
                               Instant completedAt, String outputData) {
        super(workflowID, durationMs, completedAt);
        this.outputData = Objects.requireNonNull(outputData,
                "outputData must not be null; use empty string if there is no output");
    }

    /**
     * Returns the XML-formatted case output data produced by the workflow.
     *
     * @return output data string; never null, may be empty
     */
    public String outputData() {
        return outputData;
    }

    @Override
    public String statusCode() {
        return "SUCCESS";
    }

    @Override
    public String message() {
        return "Workflow completed successfully";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SuccessfulWorkflow other)) return false;
        return workflowID().equals(other.workflowID())
                && durationMs() == other.durationMs()
                && completedAt().equals(other.completedAt())
                && outputData.equals(other.outputData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workflowID(), durationMs(), completedAt(), outputData);
    }

    @Override
    public String toString() {
        return "SuccessfulWorkflow[workflowID=" + workflowID() +
                ", durationMs=" + durationMs() +
                ", completedAt=" + completedAt() +
                ", outputData=" + (outputData.isEmpty() ? "<empty>" : outputData) +
                "]";
    }
}
