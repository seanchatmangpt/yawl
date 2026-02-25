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

package org.yawlfoundation.yawl.integration.conscience;

import junit.framework.TestCase;

/**
 * Chicago TDD tests for ConscienceQueryLibrary SPARQL templates.
 *
 * <p>Validates that query templates contain correct variable bindings,
 * format correctly with parameters, and include necessary FILTER clauses.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ConscienceQueryLibraryTest extends TestCase {

    // =========================================================================
    // RECALL_SIMILAR_DECISIONS
    // =========================================================================

    public void testRecallSimilarDecisionsFormatsCorrectly() {
        String query = String.format(ConscienceQueryLibrary.RECALL_SIMILAR_DECISIONS, "routing");
        assertTrue(query.contains("\"routing\""));
        assertTrue(query.contains("dec:taskType"));
        assertTrue(query.contains("CONSTRUCT"));
    }

    // =========================================================================
    // EXPLAIN_ROUTING — fixed bug: now uses 2 format args
    // =========================================================================

    public void testExplainRoutingFormatsWithAgentAndTimestamp() {
        String query = String.format(ConscienceQueryLibrary.EXPLAIN_ROUTING,
            "agent-1", "2024-02-23T14:32:15Z");
        assertTrue(query.contains("\"agent-1\""));
        assertTrue(query.contains("\"2024-02-23T14:32:15Z\""));
        assertTrue(query.contains("dec:agentId"));
        assertTrue(query.contains("dec:timestamp"));
    }

    public void testExplainRoutingHasTimestampBinding() {
        String template = ConscienceQueryLibrary.EXPLAIN_ROUTING;
        // The fixed version binds ?ts to dec:timestamp in WHERE clause
        assertTrue("EXPLAIN_ROUTING must bind dec:timestamp in WHERE clause",
            template.contains("dec:timestamp ?ts"));
    }

    public void testExplainRoutingHasTimestampFilter() {
        String template = ConscienceQueryLibrary.EXPLAIN_ROUTING;
        assertTrue("EXPLAIN_ROUTING must filter by timestamp",
            template.contains("FILTER(?ts >="));
    }

    // =========================================================================
    // ALL_RECENT_DECISIONS — fixed bug: ?timestamp now bound
    // =========================================================================

    public void testAllRecentDecisionsFormatsWithLimit() {
        String query = String.format(ConscienceQueryLibrary.ALL_RECENT_DECISIONS, 50);
        assertTrue(query.contains("LIMIT 50"));
        assertTrue(query.contains("ORDER BY DESC(?timestamp)"));
    }

    public void testAllRecentDecisionsHasTimestampBinding() {
        String template = ConscienceQueryLibrary.ALL_RECENT_DECISIONS;
        // The fixed version binds ?timestamp to dec:timestamp in WHERE clause
        assertTrue("ALL_RECENT_DECISIONS must bind dec:timestamp ?timestamp",
            template.contains("dec:timestamp ?timestamp"));
    }

    // =========================================================================
    // DECISION_FREQUENCY_BY_AGENT
    // =========================================================================

    public void testDecisionFrequencyByAgentIsSelectQuery() {
        String template = ConscienceQueryLibrary.DECISION_FREQUENCY_BY_AGENT;
        assertTrue(template.contains("SELECT"));
        assertTrue(template.contains("COUNT(?d)"));
        assertTrue(template.contains("GROUP BY ?agentId"));
        assertTrue(template.contains("ORDER BY DESC(?count)"));
    }

    // =========================================================================
    // CONFIDENCE_DISTRIBUTION
    // =========================================================================

    public void testConfidenceDistributionHasBuckets() {
        String template = ConscienceQueryLibrary.CONFIDENCE_DISTRIBUTION;
        assertTrue(template.contains("SELECT"));
        assertTrue(template.contains("?bucket"));
        assertTrue(template.contains("\"low\""));
        assertTrue(template.contains("\"medium-low\""));
        assertTrue(template.contains("\"medium-high\""));
        assertTrue(template.contains("\"high\""));
        assertTrue(template.contains("GROUP BY ?bucket"));
    }

    // =========================================================================
    // LOW_CONFIDENCE_DECISIONS
    // =========================================================================

    public void testLowConfidenceDecisionsFormatsWithThreshold() {
        String query = String.format(ConscienceQueryLibrary.LOW_CONFIDENCE_DECISIONS, 0.5);
        assertTrue(query.contains("FILTER(?confidence <"));
        assertTrue(query.contains("0.5"));
        assertTrue(query.contains("dec:agentId"));
        assertTrue(query.contains("dec:confidence"));
    }

    public void testLowConfidenceDecisionsOrdersByConfidence() {
        String template = ConscienceQueryLibrary.LOW_CONFIDENCE_DECISIONS;
        assertTrue(template.contains("ORDER BY ASC(?confidence)"));
    }

    // =========================================================================
    // Cross-cutting validation
    // =========================================================================

    public void testAllQueriesHaveDecPrefix() {
        String[] queries = {
            ConscienceQueryLibrary.RECALL_SIMILAR_DECISIONS,
            ConscienceQueryLibrary.EXPLAIN_ROUTING,
            ConscienceQueryLibrary.DECISIONS_BY_TASK,
            ConscienceQueryLibrary.ALL_RECENT_DECISIONS,
            ConscienceQueryLibrary.DECISION_FREQUENCY_BY_AGENT,
            ConscienceQueryLibrary.CONFIDENCE_DISTRIBUTION,
            ConscienceQueryLibrary.LOW_CONFIDENCE_DECISIONS
        };

        for (String query : queries) {
            assertTrue("Query must contain dec: prefix declaration",
                query.contains("PREFIX dec:"));
        }
    }

    public void testUtilityClassCannotBeInstantiated() {
        try {
            var constructor = ConscienceQueryLibrary.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            fail("Expected UnsupportedOperationException");
        } catch (Exception e) {
            // Expected — constructor throws UnsupportedOperationException
            assertTrue(e.getCause() instanceof UnsupportedOperationException);
        }
    }
}
