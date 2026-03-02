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
 * Test suite for PortfolioOptimizationAgent.
 *
 * @since YAWL 6.0
 */
@DisplayName("PortfolioOptimizationAgent Test Suite")
class PortfolioOptimizationAgentTest {

    @Test
    @DisplayName("optimize_withNullGateway_returnsFallbackJson")
    void testOptimizeWithNullGatewayReturnsFallbackJson() {
        PortfolioOptimizationAgent agent = new PortfolioOptimizationAgent(null);

        String result = agent.optimize(
            "{\"items\": []}",
            "{\"budget\": 100000}"
        );

        assertNotNull(result, "optimize() should return non-null result");
        assertFalse(result.isBlank(), "optimize() should return non-empty result");
        assertTrue(result.contains("LLM not configured"),
            "Result should indicate LLM not configured when gateway is null");
        assertTrue(result.contains("topPriorities"),
            "Result should contain topPriorities field");
    }

    @Test
    @DisplayName("optimize_returnsParsableJson")
    void testOptimizeReturnsParsableJson() {
        PortfolioOptimizationAgent agent = new PortfolioOptimizationAgent(null);

        String result = agent.optimize(
            "{\"items\": [{\"id\": \"epic-001\", \"value\": 100}]}",
            "{\"budget\": 50000}"
        );

        assertTrue(result.startsWith("{"), "Result should be JSON object");
        assertTrue(result.endsWith("}"), "Result should end with }");
        assertTrue(result.contains("\""), "Result should contain JSON quotes");
    }

    @Test
    @DisplayName("optimize_returnValueNotBlank")
    void testOptimizeReturnValueNotBlank() {
        PortfolioOptimizationAgent agent = new PortfolioOptimizationAgent(null);

        String backlogJson = "{\"epics\": [\"AI adoption\", \"Platform modernization\"]}";
        String budgetJson = "{\"lean_budget\": 200000}";

        String result = agent.optimize(backlogJson, budgetJson);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.trim().isEmpty(), "Result should not be empty");
        assertTrue(result.length() > 10, "Result should have reasonable length");
    }

    @Test
    @DisplayName("optimize_containsExpectedFields")
    void testOptimizeContainsExpectedFields() {
        PortfolioOptimizationAgent agent = new PortfolioOptimizationAgent(null);

        String result = agent.optimize("{}", "{}");

        assertTrue(result.contains("topPriorities"),
            "Result should contain topPriorities field");
        assertTrue(result.contains("wsjfScores"),
            "Result should contain wsjfScores field");
    }
}
