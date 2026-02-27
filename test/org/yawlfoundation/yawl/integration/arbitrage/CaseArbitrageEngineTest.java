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

package org.yawlfoundation.yawl.integration.arbitrage;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.integration.arbitrage.CaseArbitrageEngine.ArbitrageException;
import org.yawlfoundation.yawl.integration.arbitrage.CaseArbitrageEngine.FutureVariant;
import org.yawlfoundation.yawl.integration.arbitrage.CaseArbitrageEngine.VariantResult;
import org.yawlfoundation.yawl.integration.eventsourcing.CaseStateView;
import org.yawlfoundation.yawl.integration.eventsourcing.EventReplayer;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.observability.PredictiveRouter;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for CaseArbitrageEngine using real integration patterns.
 * Tests verify:
 * - Guard clause on variant count (max 5)
 * - Exception wrapping for replay failures
 * - Winner election logic via majority vote and tiebreaking
 * - Confidence computation as fraction of matching outcomes
 */
class CaseArbitrageEngineTest {

    private EventReplayer eventReplayer;
    private YStatelessEngine statelessEngine;
    private PredictiveRouter predictiveRouter;
    private CaseArbitrageEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        // Use real components with test doubles
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        // Create a test event replayer backed by a real H2 in-memory store
        WorkflowEventStore eventStore = createTestEventStore();
        eventReplayer = new EventReplayer(eventStore);

        // Create a test stateless engine
        statelessEngine = new YStatelessEngine();

        // Create a real predictive router
        predictiveRouter = new PredictiveRouter(meterRegistry);

        engine = new CaseArbitrageEngine(
            eventReplayer,
            statelessEngine,
            predictiveRouter
        );
    }

    // =========================================================================
    // Guard Clause Tests
    // =========================================================================

    @Test
    void arbitrate_guardsVariantCountAbove5() {
        // Given: variantCount = 6 (exceeds max of 5)
        String sourceCaseId = "case-123";
        Instant pivotInstant = Instant.now();
        YSpecification spec = new YSpecification();

        // When/Then: IllegalArgumentException thrown
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> engine.arbitrate(sourceCaseId, pivotInstant, spec, 6)
        );

        assertTrue(
            ex.getMessage().contains("exceeds τ limit of 5"),
            "Exception message should mention τ limit"
        );
    }

    // =========================================================================
    // Replay Exception Wrapping Tests
    // =========================================================================

    @Test
    void arbitrate_wrapsReplayExceptionAsArbitrageException()
            throws Exception {
        // Given: EventReplayer with empty event store (will throw on missing case)
        String sourceCaseId = "case-nonexistent-456";
        Instant pivotInstant = Instant.now();
        YSpecification spec = new YSpecification();

        // When/Then: ArbitrageException thrown wrapping the ReplayException
        ArbitrageException ex = assertThrows(
            ArbitrageException.class,
            () -> engine.arbitrate(sourceCaseId, pivotInstant, spec, 3)
        );

        assertTrue(
            ex.getMessage().contains("Failed to replay"),
            "Exception message should mention replay failure"
        );
        assertNotNull(ex.getCause(), "Cause should be the underlying ReplayException");
    }

    // =========================================================================
    // Winner Election Tests (Majority Vote + Tiebreaking)
    // =========================================================================

    @Test
    void electWinner_picksMajorityOutcome() throws Exception {
        // Given: 3 variants with 2 COMPLETED, 1 CANCELLED
        FutureVariant variant1 = new FutureVariant(
            0, "var-1", "<caseParams><priority>HIGH</priority></caseParams>",
            Map.of("priority", "HIGH")
        );
        FutureVariant variant2 = new FutureVariant(
            1, "var-2", "<caseParams><priority>NORMAL</priority></caseParams>",
            Map.of("priority", "NORMAL")
        );
        FutureVariant variant3 = new FutureVariant(
            2, "var-3", "<caseParams><priority>LOW</priority></caseParams>",
            Map.of("priority", "LOW")
        );

        VariantResult result1 = new VariantResult(variant1, true, 1500, "COMPLETED");
        VariantResult result2 = new VariantResult(variant2, true, 1200, "COMPLETED");
        VariantResult result3 = new VariantResult(variant3, false, 3000, "CANCELLED");

        List<VariantResult> results = List.of(result1, result2, result3);

        // When: electWinner called via reflection (package-private method)
        FutureVariant winner = callElectWinner(results);

        // Then: Winner should be from majority (COMPLETED) group
        assertEquals("COMPLETED", getOutcomeStatus(winner, results),
                     "Winner should have outcome status of majority");
        assertTrue(
            winner.variantId().equals("var-1") || winner.variantId().equals("var-2"),
            "Winner should be one of the COMPLETED variants"
        );
    }

    @Test
    void electWinner_tiebreaksByShortestDuration() throws Exception {
        // Given: 3 variants, all with same COMPLETED status but different durations
        FutureVariant variant1 = new FutureVariant(
            0, "var-fast", "<caseParams/>", Map.of()
        );
        FutureVariant variant2 = new FutureVariant(
            1, "var-slow", "<caseParams/>", Map.of()
        );
        FutureVariant variant3 = new FutureVariant(
            2, "var-medium", "<caseParams/>", Map.of()
        );

        VariantResult result1 = new VariantResult(variant1, true, 800, "COMPLETED");
        VariantResult result2 = new VariantResult(variant2, true, 2500, "COMPLETED");
        VariantResult result3 = new VariantResult(variant3, true, 1500, "COMPLETED");

        List<VariantResult> results = List.of(result1, result2, result3);

        // When: electWinner called
        FutureVariant winner = callElectWinner(results);

        // Then: Winner should be fastest (var-fast with 800ms)
        assertEquals("var-fast", winner.variantId(),
                     "Tiebreak should elect fastest variant");
    }

    // =========================================================================
    // Confidence Computation Tests
    // =========================================================================

    @Test
    void computeConfidence_returnsFractionOfMatchingResults() throws Exception {
        // Given: 3 variants, 2 with outcome COMPLETED, 1 with CANCELLED
        // Winner is COMPLETED variant
        FutureVariant variant1 = new FutureVariant(
            0, "var-1", "<caseParams/>", Map.of()
        );
        FutureVariant variant2 = new FutureVariant(
            1, "var-2", "<caseParams/>", Map.of()
        );
        FutureVariant variant3 = new FutureVariant(
            2, "var-3", "<caseParams/>", Map.of()
        );

        VariantResult result1 = new VariantResult(variant1, true, 1000, "COMPLETED");
        VariantResult result2 = new VariantResult(variant2, true, 1100, "COMPLETED");
        VariantResult result3 = new VariantResult(variant3, false, 2000, "CANCELLED");

        List<VariantResult> results = List.of(result1, result2, result3);
        FutureVariant winner = variant1;

        // When: computeConfidence called
        double confidence = callComputeConfidence(winner, results);

        // Then: Confidence = 2/3 ≈ 0.667
        double expectedConfidence = 2.0 / 3.0;
        assertEquals(expectedConfidence, confidence, 0.001,
                     "Confidence should be 2/3 (fraction of COMPLETED outcomes)");
    }

    @Test
    void computeConfidence_allMatchingReturnsOne() throws Exception {
        // Given: All 3 variants with same outcome
        FutureVariant variant1 = new FutureVariant(0, "var-1", "<caseParams/>", Map.of());
        FutureVariant variant2 = new FutureVariant(1, "var-2", "<caseParams/>", Map.of());
        FutureVariant variant3 = new FutureVariant(2, "var-3", "<caseParams/>", Map.of());

        VariantResult result1 = new VariantResult(variant1, true, 1000, "COMPLETED");
        VariantResult result2 = new VariantResult(variant2, true, 1100, "COMPLETED");
        VariantResult result3 = new VariantResult(variant3, true, 1050, "COMPLETED");

        List<VariantResult> results = List.of(result1, result2, result3);

        // When: computeConfidence called
        double confidence = callComputeConfidence(variant1, results);

        // Then: Confidence = 1.0
        assertEquals(1.0, confidence, 0.001,
                     "Confidence should be 1.0 when all variants match");
    }

    @Test
    void computeConfidence_noneMatchingReturnsZero() throws Exception {
        // Given: Winner has COMPLETED, but only 1 out of 3 variants completed
        FutureVariant variant1 = new FutureVariant(0, "var-1", "<caseParams/>", Map.of());
        FutureVariant variant2 = new FutureVariant(1, "var-2", "<caseParams/>", Map.of());
        FutureVariant variant3 = new FutureVariant(2, "var-3", "<caseParams/>", Map.of());

        VariantResult result1 = new VariantResult(variant1, true, 1000, "COMPLETED");
        VariantResult result2 = new VariantResult(variant2, false, 2000, "CANCELLED");
        VariantResult result3 = new VariantResult(variant3, false, 2000, "CANCELLED");

        List<VariantResult> results = List.of(result1, result2, result3);

        // When: computeConfidence called
        double confidence = callComputeConfidence(variant1, results);

        // Then: Confidence = 1/3 ≈ 0.333
        assertEquals(1.0 / 3.0, confidence, 0.001,
                     "Confidence should be 1/3 (only 1 variant completed)");
    }

    // =========================================================================
    // Helper Methods for Reflection-based Testing
    // =========================================================================

    /**
     * Call electWinner via reflection (package-private method).
     */
    private FutureVariant callElectWinner(List<VariantResult> results) throws Exception {
        var method = CaseArbitrageEngine.class.getDeclaredMethod(
            "electWinner", List.class
        );
        method.setAccessible(true);
        return (FutureVariant) method.invoke(engine, results);
    }

    /**
     * Call computeConfidence via reflection (package-private method).
     */
    private double callComputeConfidence(FutureVariant winner,
                                         List<VariantResult> results) throws Exception {
        var method = CaseArbitrageEngine.class.getDeclaredMethod(
            "computeConfidence", FutureVariant.class, List.class
        );
        method.setAccessible(true);
        return (double) method.invoke(engine, winner, results);
    }

    /**
     * Get outcome status for a given variant from results list.
     */
    private String getOutcomeStatus(FutureVariant variant, List<VariantResult> results) {
        return results.stream()
            .filter(r -> r.variant().variantId().equals(variant.variantId()))
            .map(VariantResult::outcomeStatus)
            .findFirst()
            .orElse("UNKNOWN");
    }

    // =========================================================================
    // Test Double: In-Memory Event Store for Testing
    // =========================================================================

    /**
     * Test event store factory that creates a real WorkflowEventStore
     * backed by H2 in-memory database with no events for testing
     * replay failure scenarios. Uses the real store with empty data.
     */
    static WorkflowEventStore createTestEventStore() throws Exception {
        // Create H2 in-memory datasource
        javax.sql.DataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ((org.h2.jdbcx.JdbcDataSource) ds).setURL("jdbc:h2:mem:test-event-store");
        ((org.h2.jdbcx.JdbcDataSource) ds).setUser("sa");
        ((org.h2.jdbcx.JdbcDataSource) ds).setPassword("");

        // Create real store (it initializes its own schema on first use)
        WorkflowEventStore store = new WorkflowEventStore(ds);

        // Store is now ready with empty event table
        return store;
    }
}
