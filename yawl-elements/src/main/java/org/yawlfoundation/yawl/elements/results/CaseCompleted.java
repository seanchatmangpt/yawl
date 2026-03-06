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
 * A {@link CaseOutcome} indicating that a case instance completed normally.
 *
 * <p>Normal completion means the case token traversed the net and reached the output
 * condition ({@code Output Condition} place) via normal task firings. The final case
 * data payload — the XML document held in the output condition — is captured in
 * {@link #outputData()}.</p>
 *
 * <p>Usage in exhaustive pattern matching:</p>
 * <pre>{@code
 * if (outcome instanceof CaseCompleted c) {
 *     archiveService.archive(c.caseID(), c.outputData(), c.terminatedAt());
 * }
 * }</pre>
 */
public final class CaseCompleted extends CaseOutcome {

    private final String outputData;

    /**
     * Constructs a completed case outcome.
     *
     * @param caseID          the unique identifier of the completed case instance
     * @param specificationID the identifier of the workflow specification
     * @param durationMs      elapsed time from launch to output condition in milliseconds
     * @param terminatedAt    the instant the case token reached the output condition
     * @param outputData      the XML-formatted case data at the output condition; may be
     *                        empty but must not be null
     * @throws IllegalArgumentException if {@code caseID} or {@code specificationID} is
     *                                  null or blank, or {@code durationMs} is negative
     * @throws NullPointerException     if {@code terminatedAt} or {@code outputData} is null
     */
    public CaseCompleted(String caseID, String specificationID,
                         long durationMs, Instant terminatedAt, String outputData) {
        super(caseID, specificationID, durationMs, terminatedAt);
        this.outputData = Objects.requireNonNull(outputData,
                "outputData must not be null; use empty string when there is no output data");
    }

    /**
     * Returns the XML-formatted case data produced at the output condition.
     *
     * @return case output data; never null, may be empty
     */
    public String outputData() {
        return outputData;
    }

    @Override
    public String statusCode() {
        return "COMPLETED";
    }

    @Override
    public String summary() {
        return "Case " + caseID() + " completed normally in " + durationMs() + "ms";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CaseCompleted other)) return false;
        return caseID().equals(other.caseID())
                && specificationID().equals(other.specificationID())
                && durationMs() == other.durationMs()
                && terminatedAt().equals(other.terminatedAt())
                && outputData.equals(other.outputData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseID(), specificationID(), durationMs(), terminatedAt(),
                outputData);
    }

    @Override
    public String toString() {
        return "CaseCompleted[caseID=" + caseID() +
                ", specificationID=" + specificationID() +
                ", durationMs=" + durationMs() +
                ", terminatedAt=" + terminatedAt() +
                ", outputData=" + (outputData.isEmpty() ? "<empty>" : outputData) +
                "]";
    }
}
