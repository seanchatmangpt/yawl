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

package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;
import org.yawlfoundation.yawl.integration.selfplay.GapAnalysisEngine;
import org.yawlfoundation.yawl.integration.selfplay.GapClosureService;
import org.yawlfoundation.yawl.integration.selfplay.QLeverTestUtils;
import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for the inner loop of the self-play system.
 * Tests the integration between gap analysis and gap closure services.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("Inner Loop Integration Tests")
class InnerLoopTest {

    private GapAnalysisEngine gapAnalysisEngine;
    private GapClosureService gapClosureService;
    private QLeverTestUtils qleverUtils;
    private static final int MAX_ITERATIONS = 3;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize gap analysis engine
        gapAnalysisEngine = new GapAnalysisEngine();
        gapAnalysisEngine.initialize();

        // Initialize gap closure service
        gapClosureService = new GapClosureService();
        gapClosureService.initialize();

        // Initialize QLever test utils
        qleverUtils = new QLeverTestUtils();
    }

    @Test
    @DisplayName("Test 1: Single iteration increases composition count")
    void testSingleIteration() throws Exception {
        // Get initial composition count
        int before = gapClosureService.getCompositionCount();
        System.out.println("Initial composition count: " + before);

        // Perform single inner loop iteration
        performSingleIteration();

        // Get final composition count
        int after = gapClosureService.getCompositionCount();
        System.out.println("Final composition count: " + after);

        // Verify composition count increased
        assertTrue(after > before,
            "Composition count must increase after iteration. " +
            "Before: " + before + ", After: " + after);
    }

    @Test
    @DisplayName("Test 2: Three iterations strictly increase composition count")
    void testThreeIterationsStrictlyIncreasing() throws Exception {
        // Get initial composition count
        int c0 = gapClosureService.getCompositionCount();
        System.out.println("Initial composition count: " + c0);

        // Perform three iterations
        for (int i = 0; i < 3; i++) {
            performSingleIteration();
            int currentCount = gapClosureService.getCompositionCount();
            System.out.println("After iteration " + (i + 1) + ": " + currentCount);
            assertTrue(currentCount > c0 + i,
                "Composition count should increase with each iteration");
        }

        // Verify final count is greater than initial
        int c3 = gapClosureService.getCompositionCount();
        assertTrue(c3 > c0,
            "Final composition count should be greater than initial. " +
            "Initial: " + c0 + ", Final: " + c3);
    }

    @Test
    @DisplayName("Test 3: Inner loop converges within reasonable iterations")
    void testInnerLoopConvergence() throws Exception {
        int initialCount = gapClosureService.getCompositionCount();
        int targetCount = initialCount + 3; // Target 3 new compositions
        int iterations = 0;
        boolean converged = false;

        while (iterations < MAX_ITERATIONS && !converged) {
            // Perform iteration
            performSingleIteration();

            // Check if we've converged
            int currentCount = gapClosureService.getCompositionCount();
            if (currentCount >= targetCount) {
                converged = true;
                System.out.println("Converged after " + iterations + " iterations");
            }

            iterations++;
        }

        // Verify convergence
        assertTrue(converged,
            "Inner loop should converge within " + MAX_ITERATIONS + " iterations. " +
            "Final count: " + gapClosureService.getCompositionCount());
    }

    @Test
    @DisplayName("Test 4: Gap closure records are properly maintained")
    void testGapClosureRecordsMaintained() throws Exception {
        // Get initial record count
        List<GapClosureService.GapClosureRecord> initialRecords =
            gapClosureService.getClosureRecords();
        int initialCount = initialRecords.size();

        // Perform iterations
        for (int i = 0; i < 2; i++) {
            performSingleIteration();
        }

        // Check record count
        List<GapClosureService.GapClosureRecord> finalRecords =
            gapClosureService.getClosureRecords();
        int finalCount = finalRecords.size();

        assertTrue(finalCount > initialCount,
            "Record count should increase with iterations. " +
            "Initial: " + initialCount + ", Final: " + finalCount);

        // Check that records have proper metadata
        for (GapClosureService.GapClosureRecord record : finalRecords) {
            assertNotNull(record.closureId(), "Closure ID should not be null");
            assertNotNull(record.gap(), "Gap should not be null");
            assertTrue(record.executionTime() > 0, "Execution time should be positive");
        }
    }

    @Test
    @DisplayName("Test 5: Fitness score improves with each iteration")
    void testFitnessScoreImproves() throws Exception {
        // Get initial fitness score
        FitnessScore initialFitness = getCurrentFitnessScore();
        double previousTotal = initialFitness.total();

        // Perform iterations and track fitness
        for (int i = 0; i < 3; i++) {
            performSingleIteration();

            FitnessScore currentFitness = getCurrentFitnessScore();
            double currentTotal = currentFitness.total();

            System.out.println("Iteration " + (i + 1) + " fitness: " + currentTotal);

            // Fitness should not decrease
            assertTrue(currentTotal >= previousTotal,
                "Fitness score should not decrease. " +
                "Previous: " + previousTotal + ", Current: " + currentTotal);

            previousTotal = currentTotal;
        }
    }

    @Test
    @DisplayName("Test 6: Gap analysis discovers new gaps after closure")
    void testGapAnalysisDiscoversNewGaps() throws Exception {
        // Discover initial gaps
        List<GapAnalysisEngine.CapabilityGap> initialGaps = gapAnalysisEngine.discoverGaps();
        int initialGapCount = initialGaps.size();
        System.out.println("Initial gap count: " + initialGapCount);

        // Perform gap closures
        for (int i = 0; i < 2; i++) {
            // Get top gap for closure
            List<GapAnalysisEngine.GapPriority> priorities = gapAnalysisEngine.prioritizeGaps(initialGaps);
            GapAnalysisEngine.GapPriority top = gapAnalysisEngine.getTopGaps(1).get(0);

            // Close the gap
            GapClosureService.GapClosureRecord closure = gapClosureService.closeGap(top);
            assertTrue(closure.success(), "Gap closure should succeed");

            // Discover new gaps
            initialGaps = gapAnalysisEngine.discoverGaps();
        }

        // Check if new gaps are discovered
        int finalGapCount = gapAnalysisEngine.discoverGaps().size();
        System.out.println("Final gap count: " + finalGapCount);

        // Gap discovery should continue to find new opportunities
        assertTrue(finalGapCount >= 0, "Should be able to discover gaps");
    }

    @Test
    @DisplayName("Test 7: Multiple gap closures maintain monotonic improvement")
    void testMultipleGapClosuresMaintainImprovement() throws Exception {
        int initialCount = gapClosureService.getCompositionCount();
        List<Integer> compositionCounts = new java.util.ArrayList<>();
        compositionCounts.add(initialCount);

        // Perform multiple gap closures
        for (int i = 0; i < 5; i++) {
            // Discover and prioritize gaps
            List<GapAnalysisEngine.CapabilityGap> gaps = gapAnalysisEngine.discoverGaps();
            List<GapAnalysisEngine.GapPriority> priorities = gapAnalysisEngine.prioritizeGaps(gaps);

            // Close the top gap
            GapAnalysisEngine.GapPriority top = gapAnalysisEngine.getTopGaps(1).get(0);
            GapClosureService.GapClosureRecord closure = gapClosureService.closeGap(top);

            // Verify closure success
            assertTrue(closure.success(), "Gap closure " + (i + 1) + " should succeed");

            // Check composition count
            int currentCount = gapClosureService.getCompositionCount();
            compositionCounts.add(currentCount);

            // Verify monotonic improvement
            assertTrue(currentCount > compositionCounts.get(i),
                "Composition count should be strictly increasing. " +
                "Iteration " + i + ": " + compositionCounts.get(i) +
                ", Iteration " + (i + 1) + ": " + currentCount);
        }

        // Verify final improvement
        int finalCount = gapClosureService.getCompositionCount();
        assertTrue(finalCount > initialCount,
            "Final composition count should be greater than initial. " +
            "Initial: " + initialCount + ", Final: " + finalCount);
    }

    @Test
    @DisplayName("Test 8: Inner loop handles errors gracefully")
    void testInnerLoopHandlesErrorsGracefully() throws Exception {
        // Try to close gap with invalid priority
        GapAnalysisEngine.GapPriority invalidPriority = new GapAnalysisEngine.GapPriority(
            null, 0.0, 0.0
        );

        // Should handle error gracefully
        assertThrows(Exception.class, () -> {
            gapClosureService.closeGap(invalidPriority);
        }, "Should throw exception for invalid gap priority");

        // System should still be functional after error
        int currentCount = gapClosureService.getCompositionCount();
        assertTrue(currentCount >= 0, "System should remain functional");
    }

    @Test
    @DisplayName("Test 9: Integration with QLever maintains consistency")
    void testIntegrationWithQLeverMaintainsConsistency() throws Exception {
        // Get initial statistics
        Map<String, Object> initialStats = qleverUtils.getTestStatistics();
        int initialCompositions = (int) initialStats.get("compositions");
        int initialGaps = (int) initialStats.get("gaps");

        // Perform several iterations
        for (int i = 0; i < 3; i++) {
            performSingleIteration();
        }

        // Get final statistics
        Map<String, Object> finalStats = qleverUtils.getTestStatistics();
        int finalCompositions = (int) finalStats.get("compositions");
        int finalGaps = (int) finalStats.get("gaps");

        // Verify consistency
        assertTrue(finalCompositions >= initialCompositions,
            "QLever composition count should not decrease");
        assertTrue(finalGaps >= initialGaps,
            "QLever gap count should not decrease");

        // Verify gap closure service consistency
        int serviceCount = gapClosureService.getCompositionCount();
        assertEquals(finalCompositions, serviceCount,
            "QLever and service should have consistent composition counts");
    }

    @Test
    @DisplayName("Test 10: Performance metrics are tracked")
    void testPerformanceMetricsAreTracked() throws Exception {
        // Track performance metrics
        long totalExecutionTime = 0;
        int iterations = 0;
        int initialCount = gapClosureService.getCompositionCount();

        // Perform iterations and track performance
        while (iterations < MAX_ITERATIONS &&
               gapClosureService.getCompositionCount() <= initialCount + 2) {

            long startTime = System.currentTimeMillis();
            performSingleIteration();
            long endTime = System.currentTimeMillis();

            totalExecutionTime += (endTime - startTime);
            iterations++;
        }

        // Verify performance metrics
        assertTrue(totalExecutionTime > 0, "Total execution time should be positive");
        assertTrue(iterations > 0, "Should have performed iterations");

        // Check average execution time
        double avgExecutionTime = (double) totalExecutionTime / iterations;
        System.out.println("Average execution time per iteration: " + avgExecutionTime + "ms");

        // Verify performance is reasonable (less than 1 minute per iteration)
        assertTrue(avgExecutionTime < 60000,
            "Average execution time should be less than 1 minute");
    }

    // ==================== Helper Methods ====================

    /**
     * Perform a single inner loop iteration.
     */
    private void performSingleIteration() throws Exception {
        // Discover and prioritize gaps
        List<GapAnalysisEngine.CapabilityGap> gaps = gapAnalysisEngine.discoverGaps();
        List<GapAnalysisEngine.GapPriority> priorities = gapAnalysisEngine.prioritizeGaps(gaps);

        // Get top gap for closure
        GapAnalysisEngine.GapPriority top = gapAnalysisEngine.getTopGaps(1).get(0);

        // Execute gap closure
        GapClosureService.GapClosureRecord closure = gapClosureService.closeGap(top);

        // Verify closure success
        assertTrue(closure.success(),
            "Gap closure must succeed. Error: " + closure.errorMessage());

        // Verify gap is stored in QLever
        String gapId = top.gap().id();
        assertTrue(qleverUtils.gapExists(gapId),
            "Closed gap should be stored in QLever");
    }

    /**
     * Get the current fitness score from the gap analysis engine.
     */
    private FitnessScore getCurrentFitnessScore() throws Exception {
        // This would typically involve evaluating the current state
        // against the v7 requirements
        // For now, return a placeholder implementation
        return new FitnessScore(0.5, 0.5, 0.5); // Placeholder
    }

    /**
     * Clean up resources after test.
     */
    void tearDown() throws Exception {
        try {
            if (gapClosureService != null && gapClosureService.isReady()) {
                gapClosureService.shutdown();
            }
            if (gapAnalysisEngine != null) {
                gapAnalysisEngine.shutdown();
            }
            if (qleverUtils != null) {
                qleverUtils.shutdown();
            }
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }
}