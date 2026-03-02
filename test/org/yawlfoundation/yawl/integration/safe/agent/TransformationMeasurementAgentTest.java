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

package org.yawlfoundation.yawl.integration.safe.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for TransformationMeasurementAgent.
 *
 * @since YAWL 6.0
 */
@DisplayName("TransformationMeasurementAgent Test Suite")
class TransformationMeasurementAgentTest {

    @Test
    @DisplayName("measureFlowMetrics_returnsValidJson")
    void testMeasureFlowMetricsReturnsValidJson() {
        TransformationMeasurementAgent agent = new TransformationMeasurementAgent();

        String result = agent.measureFlowMetrics("art-alpha");

        assertNotNull(result, "measureFlowMetrics() should return non-null");
        assertTrue(result.startsWith("{"), "Result should be JSON object");
        assertTrue(result.endsWith("}"), "Result should end with }");
        assertTrue(result.contains("artId"), "Result should contain artId field");
    }

    @Test
    @DisplayName("measureFlowMetrics_containsFlowVelocity")
    void testMeasureFlowMetricsContainsFlowVelocity() {
        TransformationMeasurementAgent agent = new TransformationMeasurementAgent();

        String result = agent.measureFlowMetrics("art-beta");

        assertTrue(result.contains("\"flowVelocity\""),
            "Result should contain flowVelocity field");
        assertTrue(result.contains("42"),
            "Result should contain velocity value");
    }

    @Test
    @DisplayName("runAssessment_returnsValidJson")
    void testRunAssessmentReturnsValidJson() {
        TransformationMeasurementAgent agent = new TransformationMeasurementAgent();

        String result = agent.runAssessment("art-gamma");

        assertNotNull(result, "runAssessment() should return non-null");
        assertTrue(result.startsWith("{"), "Result should be JSON object");
        assertTrue(result.endsWith("}"), "Result should end with }");
    }

    @Test
    @DisplayName("runAssessment_containsOverallScore")
    void testRunAssessmentContainsOverallScore() {
        TransformationMeasurementAgent agent = new TransformationMeasurementAgent();

        String result = agent.runAssessment("art-delta");

        assertTrue(result.contains("\"overallScore\""),
            "Result should contain overallScore field");
        assertTrue(result.contains("4.1"),
            "Result should contain overall score value");
    }

    @Test
    @DisplayName("runAssessment_containsRecommendations")
    void testRunAssessmentContainsRecommendations() {
        TransformationMeasurementAgent agent = new TransformationMeasurementAgent();

        String result = agent.runAssessment("art-epsilon");

        assertTrue(result.contains("\"recommendations\""),
            "Result should contain recommendations field");
        assertTrue(result.contains("Increase test automation coverage"),
            "Result should contain specific recommendation");
    }

    @Test
    @DisplayName("generateRecommendationTicket_returnsParsableJson")
    void testGenerateRecommendationTicketReturnsParsableJson() {
        TransformationMeasurementAgent agent = new TransformationMeasurementAgent();

        String result = agent.generateRecommendationTicket("Improve deployment frequency");

        assertNotNull(result, "generateRecommendationTicket() should return non-null");
        assertTrue(result.startsWith("{"), "Result should be JSON object");
        assertTrue(result.endsWith("}"), "Result should end with }");
    }

    @Test
    @DisplayName("generateRecommendationTicket_containsType")
    void testGenerateRecommendationTicketContainsType() {
        TransformationMeasurementAgent agent = new TransformationMeasurementAgent();

        String result = agent.generateRecommendationTicket("Fix flaky tests");

        assertTrue(result.contains("\"type\""),
            "Result should contain type field");
        assertTrue(result.contains("story"),
            "Result should contain story type");
    }

    @Test
    @DisplayName("generateRecommendationTicket_containsTitle")
    void testGenerateRecommendationTicketContainsTitle() {
        TransformationMeasurementAgent agent = new TransformationMeasurementAgent();

        String result = agent.generateRecommendationTicket("Add observability metrics");

        assertTrue(result.contains("\"title\""),
            "Result should contain title field");
        assertTrue(result.contains("Improvement"),
            "Result should contain Improvement in title");
        assertTrue(result.contains("Add observability metrics"),
            "Result should contain the finding in title");
    }

    @Test
    @DisplayName("generateRecommendationTicket_containsAcceptanceCriteria")
    void testGenerateRecommendationTicketContainsAcceptanceCriteria() {
        TransformationMeasurementAgent agent = new TransformationMeasurementAgent();

        String result = agent.generateRecommendationTicket("Reduce code review time");

        assertTrue(result.contains("\"acceptanceCriteria\""),
            "Result should contain acceptanceCriteria field");
        assertTrue(result.contains("Metric improves by 10%"),
            "Result should contain metric improvement criteria");
    }
}
