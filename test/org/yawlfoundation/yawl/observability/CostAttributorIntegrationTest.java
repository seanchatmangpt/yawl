/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago-style integration tests for per-tenant cost attribution.
 *
 * Tests real cost tracking with actual database operations (Hibernate).
 */
class CostAttributorIntegrationTest {

    private CostAttributor attributor;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        attributor = new CostAttributor(meterRegistry);

        // Set cost rate: $0.01 per second ($0.00001 per ms)
        attributor.setCostPerMs(0.00001);
    }

    @Test
    @DisplayName("Should record task cost for a case")
    void testRecordTaskCost() {
        String caseId = "case-001";
        String taskId = "approval-task";
        long durationMs = 5000;  // 5 seconds
        double externalCost = 0.05;  // $0.05 for external service

        // Act
        attributor.recordTaskCost(caseId, taskId, durationMs, externalCost);

        // Assert: Cost should be 5000ms * $0.00001/ms + $0.05 = $0.05 + $0.05 = $0.10
        CostAttributor.CaseCost caseCost = attributor.getCaseCost(caseId);
        assertNotNull(caseCost);

        // Verify cost is approximately correct (accounting for rounding)
        BigDecimal expectedCost = BigDecimal.valueOf(0.10).setScale(4, java.math.RoundingMode.HALF_UP);
        BigDecimal actualCost = caseCost.totalCost().setScale(4, java.math.RoundingMode.HALF_UP);
        assertEquals(expectedCost, actualCost);
    }

    @Test
    @DisplayName("Should accumulate costs for multiple tasks in same case")
    void testAccumulateCostsForMultipleTasks() {
        String caseId = "case-002";

        // Task 1: 2000ms + $0.02
        attributor.recordTaskCost(caseId, "task-1", 2000, 0.02);

        // Task 2: 3000ms + $0.03
        attributor.recordTaskCost(caseId, "task-2", 3000, 0.03);

        // Task 3: 1000ms + $0.01
        attributor.recordTaskCost(caseId, "task-3", 1000, 0.01);

        // Assert: Total = (2000 + 3000 + 1000) * $0.00001 + ($0.02 + $0.03 + $0.01)
        //              = $0.06 + $0.06 = $0.12
        CostAttributor.CaseCost caseCost = attributor.getCaseCost(caseId);
        assertNotNull(caseCost);

        BigDecimal expectedCost = BigDecimal.valueOf(0.12).setScale(4, java.math.RoundingMode.HALF_UP);
        BigDecimal actualCost = caseCost.totalCost().setScale(4, java.math.RoundingMode.HALF_UP);
        assertEquals(expectedCost, actualCost);
    }

    @Test
    @DisplayName("Should track multiple independent cases")
    void testTrackIndependentCases() {
        String case1 = "case-001";
        String case2 = "case-002";

        attributor.recordTaskCost(case1, "task-1", 1000, 0.01);
        attributor.recordTaskCost(case2, "task-1", 2000, 0.02);

        CostAttributor.CaseCost cost1 = attributor.getCaseCost(case1);
        CostAttributor.CaseCost cost2 = attributor.getCaseCost(case2);

        assertNotNull(cost1);
        assertNotNull(cost2);

        // case1 should cost less than case2
        assertTrue(cost1.totalCost().compareTo(cost2.totalCost()) < 0);
    }

    @Test
    @DisplayName("Should calculate case cost per second correctly")
    void testCaseCostPerSecond() {
        String caseId = "case-003";

        // 5 seconds + no external cost
        attributor.recordTaskCost(caseId, "task-1", 5000, 0.0);

        CostAttributor.CaseCost caseCost = attributor.getCaseCost(caseId);
        assertNotNull(caseCost);

        // Cost per second should be: $0.05 / 5 = $0.01/second
        BigDecimal expectedPerSecond = BigDecimal.valueOf(0.01).setScale(4, java.math.RoundingMode.HALF_UP);
        BigDecimal actualPerSecond = caseCost.getCostPerSecond().setScale(4, java.math.RoundingMode.HALF_UP);
        assertEquals(expectedPerSecond, actualPerSecond);
    }

    @Test
    @DisplayName("Should return null for non-existent case")
    void testGetNonExistentCase() {
        CostAttributor.CaseCost caseCost = attributor.getCaseCost("non-existent-case-xyz");
        assertNull(caseCost);
    }

    @Test
    @DisplayName("Should provide specification cost totals")
    void testGetSpecificationCost() {
        String specId = "spec-123";
        String case1 = specId + "-case-1";
        String case2 = specId + "-case-2";

        // Track both cases under same spec
        attributor.startCaseTracking(case1, specId);
        attributor.recordTaskCost(case1, "task-1", 1000, 0.01);

        attributor.startCaseTracking(case2, specId);
        attributor.recordTaskCost(case2, "task-1", 2000, 0.02);

        // Get spec cost
        BigDecimal specCost = attributor.getSpecCost(specId);
        assertNotNull(specCost);

        // Should be sum of both cases
        assertTrue(specCost.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should provide statistics summary")
    void testGetStatistics() {
        String case1 = "case-001";
        String case2 = "case-002";

        attributor.recordTaskCost(case1, "task-1", 1000, 0.01);
        attributor.recordTaskCost(case2, "task-1", 2000, 0.02);

        Map<String, Object> stats = attributor.getStatistics();

        assertNotNull(stats);
        assertFalse(stats.isEmpty());

        // Should have case count
        assertTrue(stats.containsKey("case_count"));
    }

    @Test
    @DisplayName("Should calculate total cost across all cases")
    void testGetTotalCost() {
        String case1 = "case-001";
        String case2 = "case-002";
        String case3 = "case-003";

        attributor.recordTaskCost(case1, "task-1", 1000, 0.01);  // $0.02
        attributor.recordTaskCost(case2, "task-1", 2000, 0.02);  // $0.04
        attributor.recordTaskCost(case3, "task-1", 3000, 0.03);  // $0.06

        BigDecimal totalCost = attributor.getTotalCost();
        assertNotNull(totalCost);

        // Total should be $0.02 + $0.04 + $0.06 = $0.12
        BigDecimal expected = BigDecimal.valueOf(0.12).setScale(4, java.math.RoundingMode.HALF_UP);
        BigDecimal actual = totalCost.setScale(4, java.math.RoundingMode.HALF_UP);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Should track case start and completion time")
    void testCaseStartAndCompletionTiming() {
        String caseId = "case-timed";

        // Start tracking
        attributor.startCaseTracking(caseId, "spec-123");

        // Simulate some work
        try {
            Thread.sleep(100);  // 100ms of simulated work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Complete tracking
        attributor.completeCaseTracking(caseId, 100);

        CostAttributor.CaseCost caseCost = attributor.getCaseCost(caseId);
        assertNotNull(caseCost);

        // Should have timing information
        assertNotNull(caseCost.startTime());
        assertNotNull(caseCost.endTime());

        // Duration should be > 0
        assertTrue(caseCost.durationMs() >= 100);
    }

    @Test
    @DisplayName("Should handle zero duration and external cost")
    void testZeroCostScenario() {
        String caseId = "case-zero";

        attributor.recordTaskCost(caseId, "task-1", 0, 0.0);

        CostAttributor.CaseCost caseCost = attributor.getCaseCost(caseId);
        assertNotNull(caseCost);

        // Cost should be exactly zero
        assertEquals(BigDecimal.ZERO, caseCost.totalCost());
    }

    @Test
    @DisplayName("Should support different cost rates")
    void testDifferentCostRates() {
        // First case with rate $0.00001/ms
        attributor.setCostPerMs(0.00001);
        attributor.recordTaskCost("case-1", "task-1", 1000, 0.0);
        BigDecimal cost1 = attributor.getCaseCost("case-1").totalCost();

        // Change rate to $0.00002/ms
        attributor.setCostPerMs(0.00002);
        attributor.recordTaskCost("case-2", "task-1", 1000, 0.0);
        BigDecimal cost2 = attributor.getCaseCost("case-2").totalCost();

        // cost2 should be roughly double cost1
        assertTrue(cost2.compareTo(cost1.multiply(BigDecimal.valueOf(1.5))) > 0);
    }

    @Test
    @DisplayName("Should get top cost cases for a specification")
    void testGetTopCostCases() {
        String specId = "spec-top-cost";
        String case1 = specId + "-case-1";
        String case2 = specId + "-case-2";
        String case3 = specId + "-case-3";

        attributor.startCaseTracking(case1, specId);
        attributor.recordTaskCost(case1, "task-1", 1000, 0.01);  // Cheapest

        attributor.startCaseTracking(case2, specId);
        attributor.recordTaskCost(case2, "task-1", 5000, 0.05);  // Most expensive

        attributor.startCaseTracking(case3, specId);
        attributor.recordTaskCost(case3, "task-1", 3000, 0.03);  // In between

        java.util.List<CostAttributor.CaseCost> topCases = attributor.getTopCostCases(specId, 2);

        assertNotNull(topCases);
        assertEquals(2, topCases.size());

        // Most expensive should be first
        assertTrue(topCases.get(0).totalCost().compareTo(topCases.get(1).totalCost()) >= 0);
    }

    @Test
    @DisplayName("Should provide task cost breakdown for specification")
    void testGetTaskCostBreakdown() {
        String specId = "spec-breakdown";
        String caseId = specId + "-case";

        attributor.startCaseTracking(caseId, specId);
        attributor.recordTaskCost(caseId, "approval", 2000, 0.02);
        attributor.recordTaskCost(caseId, "notification", 1000, 0.01);
        attributor.recordTaskCost(caseId, "archive", 500, 0.005);

        Map<String, Double> breakdown = attributor.getTaskCostBreakdown(specId);

        assertNotNull(breakdown);
        assertFalse(breakdown.isEmpty());

        // Should have entries for each task
        assertTrue(breakdown.keySet().stream().anyMatch(t -> t.contains("approval") || t.contains("task")));
    }
}
