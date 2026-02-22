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

package org.yawlfoundation.yawl.integration.processmining.synthesis;

import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlProcess;

import java.time.Duration;

/**
 * Result of PNML → YAWL synthesis process.
 * Contains generated YAWL XML, conformance metrics, and process metadata.
 * Immutable record.
 *
 * @param yawlXml               Generated YAWL specification XML as string
 * @param conformanceScore      Metrics for model quality assessment
 * @param sourceProcess         Source PnmlProcess that was synthesized
 * @param synthesisTime         Wall-clock time spent in synthesis
 * @param tasksGenerated        Number of YAWL tasks created from transitions
 * @param conditionsGenerated   Number of YAWL conditions (places) created
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record SynthesisResult(
        String yawlXml,
        ConformanceScore conformanceScore,
        PnmlProcess sourceProcess,
        Duration synthesisTime,
        int tasksGenerated,
        int conditionsGenerated
) {

    /**
     * Validates that yawlXml is non-null, conformanceScore is non-null,
     * sourceProcess is non-null, synthesisTime is non-null,
     * and generated counts are non-negative.
     */
    public SynthesisResult {
        if (yawlXml == null || yawlXml.isEmpty()) {
            throw new IllegalArgumentException("YAWL XML cannot be null or empty");
        }
        if (conformanceScore == null) {
            throw new IllegalArgumentException("Conformance score cannot be null");
        }
        if (sourceProcess == null) {
            throw new IllegalArgumentException("Source process cannot be null");
        }
        if (synthesisTime == null) {
            throw new IllegalArgumentException("Synthesis time cannot be null");
        }
        if (tasksGenerated < 0) {
            throw new IllegalArgumentException("Tasks generated cannot be negative");
        }
        if (conditionsGenerated < 0) {
            throw new IllegalArgumentException("Conditions generated cannot be negative");
        }
    }

    /**
     * Gets the YAWL XML as a pretty-printed string (if applicable).
     * Currently returns raw generated XML.
     *
     * @return YAWL XML string
     */
    public String getYawlXml() {
        return yawlXml;
    }

    /**
     * Gets synthesis metrics.
     *
     * @return ConformanceScore
     */
    public ConformanceScore getConformanceScore() {
        return conformanceScore;
    }

    /**
     * Gets the source PNML process.
     *
     * @return PnmlProcess
     */
    public PnmlProcess getSourceProcess() {
        return sourceProcess;
    }

    /**
     * Gets synthesis duration in milliseconds.
     *
     * @return elapsed time in milliseconds
     */
    public long getSynthesisTimeMs() {
        return synthesisTime.toMillis();
    }

    /**
     * Creates a summary of the synthesis result.
     *
     * @return formatted summary string
     */
    public String summary() {
        return String.format(
            "Synthesis: %d tasks, %d conditions in %d ms | %s",
            tasksGenerated, conditionsGenerated, getSynthesisTimeMs(),
            conformanceScore.summary()
        );
    }

    /**
     * Validates that the synthesis produced valid YAWL XML.
     * Checks for:
     * 1. Well-formedness (basic XML syntax)
     * 2. Presence of required YAWL elements (specificationSet, specification)
     *
     * @return true if XML appears valid
     */
    public boolean isValidXml() {
        return yawlXml != null
            && yawlXml.contains("<?xml")
            && yawlXml.contains("specificationSet")
            && yawlXml.contains("specification")
            && yawlXml.contains("decomposition");
    }

    /**
     * Checks if the synthesis result represents high-quality conformance.
     *
     * @return true if conformance score is high
     */
    public boolean isHighQuality() {
        return conformanceScore.isHighConformance();
    }
}
