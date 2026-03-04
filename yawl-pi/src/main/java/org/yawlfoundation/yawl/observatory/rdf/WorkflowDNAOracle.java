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

package org.yawlfoundation.yawl.observatory.rdf;

import org.yawlfoundation.yawl.integration.processmining.XesToYawlSpecGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;

/**
 * Stub implementation of WorkflowDNAOracle - throws UnsupportedOperationException per H-GUARDS.
 *
 * <p>This class is a stub implementation that throws UnsupportedOperationException for all methods.
 * The real implementation should be in the yawl-integration module, but it's currently missing
 * due to compilation errors in that module. This stub allows the yawl-pi module to compile while
 * following the H-GUARDS pattern of "real implementation or throw".</p>
 *
 * <p>Once the yawl-integration module compiles successfully, this stub should be removed and
 * the real WorkflowDNAOracle should be used from the integration module.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class WorkflowDNAOracle {

    /**
     * Records a discovered activity signature from execution history.
     * Used to cache pattern metadata and enable fast lookup.
     */
    public record DNASignature(
            String specId,
            List<String> activitySequence,
            Map<String, Long> taskDurations,
            int caseCount,
            double failureRate,
            Instant lastObservedAt
    ) {}

    /**
     * Records a recommendation for a new case assessment.
     * Provided to YNetRunner to inform execution decisions.
     */
    public record DNARecommendation(
            String caseId,
            String matchedPattern,
            double historicalFailureRate,
            String riskMessage,
            Optional<String> alternativePathXml,
            List<String> prePositionResources,
            Instant generatedAt
    ) {}

    /**
     * Constructs a new WorkflowDNAOracle.
     *
     * <p>Throws UnsupportedOperationException because the real implementation requires
     * dependencies that are not available in the yawl-pi module.</p>
     *
     * @param xesGenerator XES-to-YAWL specification generator (currently unused)
     * @throws UnsupportedOperationException always - this is a stub implementation
     */
    public WorkflowDNAOracle(XesToYawlSpecGenerator xesGenerator) {
        throw new UnsupportedOperationException(
            "WorkflowDNAOracle requires real implementation. " +
            "The yawl-integration module should provide this class, but it currently has compilation errors. " +
            "Fix the compilation errors in yawl-integration module or provide a real implementation."
        );
    }

    /**
     * Absorbs a completed case execution into the DNA graph.
     *
     * <p>Throws UnsupportedOperationException because this is a stub implementation.</p>
     *
     * @param caseId unique case identifier
     * @param specId specification ID
     * @param activitySequence ordered list of activities
     * @param taskDurations map of activity names to execution times
     * @param caseFailed true if case failed
     * @throws UnsupportedOperationException always - stub implementation
     */
    public void absorb(String caseId, String specId, List<String> activitySequence,
                       Map<String, Long> taskDurations, boolean caseFailed) {
        throw new UnsupportedOperationException(
            "WorkflowDNAOracle.absorb() requires real implementation. " +
            "The real implementation should be in yawl-integration module."
        );
    }

    /**
     * Assesses risk for a new case execution based on historical patterns.
     *
     * <p>Throws UnsupportedOperationException because this is a stub implementation.</p>
     *
     * @param newCaseId case ID being assessed
     * @param specId specification ID
     * @param expectedActivities expected activity sequence
     * @return DNARecommendation with risk assessment
     * @throws UnsupportedOperationException always - stub implementation
     */
    public DNARecommendation assess(String newCaseId, String specId,
                                    List<String> expectedActivities) {
        throw new UnsupportedOperationException(
            "WorkflowDNAOracle.assess() requires real implementation. " +
            "The real implementation should be in yawl-integration module."
        );
    }

    /**
     * Gets the total count of absorbed cases in the RDF graph.
     *
     * <p>Throws UnsupportedOperationException because this is a stub implementation.</p>
     *
     * @return number of absorbed cases
     * @throws UnsupportedOperationException always - stub implementation
     */
    public int getAbsorbedCaseCount() {
        throw new UnsupportedOperationException(
            "WorkflowDNAOracle.getAbsorbedCaseCount() requires real implementation. " +
            "The real implementation should be in yawl-integration module."
        );
    }

    /**
     * Prunes case executions absorbed before a given time.
     *
     * <p>Throws UnsupportedOperationException because this is a stub implementation.</p>
     *
     * @param maxAge maximum age to retain
     * @throws UnsupportedOperationException always - stub implementation
     */
    public void pruneOlderThan(Duration maxAge) {
        throw new UnsupportedOperationException(
            "WorkflowDNAOracle.pruneOlderThan() requires real implementation. " +
            "The real implementation should be in yawl-integration module."
        );
    }
}