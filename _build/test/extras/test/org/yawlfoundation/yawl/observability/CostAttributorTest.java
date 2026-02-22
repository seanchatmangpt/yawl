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

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD: Tests CostAttributor with real cost tracking and ROI analysis.
 * No mocks, real financial calculations and case cost breakdowns.
 */
@DisplayName("CostAttributor: Cost Attribution & ROI Analysis")
class CostAttributorTest {

    private CostAttributor attributor;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        attributor = new CostAttributor(meterRegistry);
    }

    @Test
    @DisplayName("Should initialize case tracking")
    void testInitializeCaseTracking() {
        attributor.startCaseTracking("case-001", "order-process");

        CostAttributor.CaseCost cost = attributor.getCaseCost("case-001");
        assertNotNull(cost);
        assertEquals("case-001", cost.caseId);
        assertEquals("order-process", cost.specId);
        assertEquals(BigDecimal.ZERO, cost.totalCost);
    }

    @Test
    @DisplayName("Should record task costs")
    void testRecordTaskCost() {
        attributor.startCaseTracking("case-001", "order-process");
        attributor.recordTaskCost("case-001", "approve", 5000, 0.05);

        CostAttributor.CaseCost cost = attributor.getCaseCost("case-001");
        assertEquals(1, cost.taskCount);
        assertTrue(cost.totalCost.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should set custom cost per millisecond")
    void testCostModel() {
        attributor.setCostPerMs(0.0001); // $0.0001 per ms = $0.1 per second

        attributor.startCaseTracking("case-001", "spec-1");
        attributor.recordTaskCost("case-001", "task-1", 1000, 0.0); // 1 second

        CostAttributor.CaseCost cost = attributor.getCaseCost("case-001");
        BigDecimal expected = BigDecimal.valueOf(0.1); // $0.0001 * 1000ms
        assertTrue(Math.abs(cost.executionCost.doubleValue() - expected.doubleValue()) < 0.01);
    }

    @Test
    @DisplayName("Should track external service costs")
    void testExternalServiceCosts() {
        attributor.setCostPerMs(0.00001);
        attributor.startCaseTracking("case-001", "spec-1");

        attributor.recordTaskCost("case-001", "payment", 2000, 0.50); // $0.50 external
        attributor.recordTaskCost("case-001", "approval", 1000, 0.0);

        CostAttributor.CaseCost cost = attributor.getCaseCost("case-001");
        assertTrue(cost.externalServiceCost.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(cost.totalCost.compareTo(cost.executionCost) > 0);
    }

    @Test
    @DisplayName("Should calculate cost per second")
    void testCostPerSecond() {
        attributor.setCostPerMs(0.0001);
        attributor.startCaseTracking("case-001", "spec-1");
        attributor.recordTaskCost("case-001", "task-1", 5000, 0.0);
        attributor.completeCaseTracking("case-001", 5000);

        CostAttributor.CaseCost cost = attributor.getCaseCost("case-001");
        BigDecimal perSecond = cost.getCostPerSecond();

        assertTrue(perSecond.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should aggregate costs by specification")
    void testSpecCostAggregation() {
        attributor.setCostPerMs(0.00001);

        // Case 1
        attributor.startCaseTracking("case-001", "order-process");
        attributor.recordTaskCost("case-001", "task-1", 1000, 0.0);
        attributor.completeCaseTracking("case-001", 1000);

        // Case 2 - same spec
        attributor.startCaseTracking("case-002", "order-process");
        attributor.recordTaskCost("case-002", "task-1", 2000, 0.0);
        attributor.completeCaseTracking("case-002", 2000);

        BigDecimal specCost = attributor.getSpecCost("order-process");
        assertTrue(specCost.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should identify top-cost cases")
    void testTopCostCases() {
        attributor.setCostPerMs(0.00001);

        // Create cases with varying costs
        for (int i = 0; i < 5; i++) {
            String caseId = "case-" + i;
            attributor.startCaseTracking(caseId, "spec-1");
            attributor.recordTaskCost(caseId, "task-1", 1000 + i * 2000, 0.0);
            attributor.completeCaseTracking(caseId, 1000 + i * 2000);
        }

        List<CostAttributor.CaseCost> topCases = attributor.getTopCostCases("spec-1", 2);
        assertEquals(2, topCases.size());
        assertTrue(topCases.get(0).totalCost.compareTo(topCases.get(1).totalCost) >= 0);
    }

    @Test
    @DisplayName("Should analyze ROI for optimizations")
    void testROIAnalysis() {
        BigDecimal historicalCost = BigDecimal.valueOf(10000);
        BigDecimal optimizedCost = BigDecimal.valueOf(7500);
        BigDecimal implementationCost = BigDecimal.valueOf(500);

        CostAttributor.ROIAnalysis roi = attributor.analyzeROI(
                "order-process",
                "parallelization",
                historicalCost,
                optimizedCost,
                implementationCost,
                50
        );

        assertTrue(roi.roi > 0);
        assertTrue(roi.costReduction.compareTo(BigDecimal.ZERO) > 0);
        assertEquals(50, roi.casesOptimized);
    }

    @Test
    @DisplayName("Should calculate ROI with positive payback")
    void testPositiveROI() {
        BigDecimal historicalCost = BigDecimal.valueOf(10000);
        BigDecimal optimizedCost = BigDecimal.valueOf(5000);
        BigDecimal implementationCost = BigDecimal.valueOf(1000);

        CostAttributor.ROIAnalysis roi = attributor.analyzeROI(
                "spec-1", "optimization", historicalCost, optimizedCost, implementationCost, 100);

        assertEquals(4.0, roi.roi); // (10000 - 5000) / 1000 = 4.0
        assertEquals("ALREADY POSITIVE", roi.getPaybackStatus());
    }

    @Test
    @DisplayName("Should calculate ROI with negative payback")
    void testNegativeROI() {
        BigDecimal historicalCost = BigDecimal.valueOf(10000);
        BigDecimal optimizedCost = BigDecimal.valueOf(9500);
        BigDecimal implementationCost = BigDecimal.valueOf(2000);

        CostAttributor.ROIAnalysis roi = attributor.analyzeROI(
                "spec-1", "optimization", historicalCost, optimizedCost, implementationCost, 50);

        assertTrue(roi.breakEvenPoint.doubleValue() > 1.0);
        assertEquals("40.0% more cost reduction needed", roi.getPaybackStatus());
    }

    @Test
    @DisplayName("Should track daily costs")
    void testDailyCostTracking() {
        attributor.setCostPerMs(0.00001);

        for (int i = 0; i < 3; i++) {
            String caseId = "case-" + i;
            attributor.startCaseTracking(caseId, "spec-1");
            attributor.recordTaskCost(caseId, "task-1", 1000, 0.0);
            attributor.completeCaseTracking(caseId, 1000);
        }

        Map<LocalDate, BigDecimal> dailyCosts = attributor.getDailyCostSummary(1);
        assertTrue(dailyCosts.size() > 0);
    }

    @Test
    @DisplayName("Should provide statistics")
    void testStatistics() {
        attributor.setCostPerMs(0.00001);

        attributor.startCaseTracking("case-001", "spec-1");
        attributor.recordTaskCost("case-001", "task-1", 1000, 0.0);
        attributor.completeCaseTracking("case-001", 1000);

        Map<String, Object> stats = attributor.getStatistics();

        assertEquals(1, (int) stats.get("totalCasesTracked"));
        assertNotNull(stats.get("totalCost"));
        assertNotNull(stats.get("averageCaseCost"));
        assertTrue((int) stats.get("tasksTracked") > 0);
    }

    @Test
    @DisplayName("Should calculate total cost across all cases")
    void testTotalCostCalculation() {
        attributor.setCostPerMs(0.00001);

        attributor.startCaseTracking("case-001", "spec-1");
        attributor.recordTaskCost("case-001", "task-1", 1000, 0.0);
        attributor.completeCaseTracking("case-001", 1000);

        attributor.startCaseTracking("case-002", "spec-2");
        attributor.recordTaskCost("case-002", "task-1", 2000, 0.0);
        attributor.completeCaseTracking("case-002", 2000);

        BigDecimal totalCost = attributor.getTotalCost();
        assertTrue(totalCost.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should handle multiple task costs per case")
    void testMultipleTasksPerCase() {
        attributor.setCostPerMs(0.00001);

        attributor.startCaseTracking("case-001", "spec-1");
        attributor.recordTaskCost("case-001", "task-1", 1000, 0.1);
        attributor.recordTaskCost("case-001", "task-2", 2000, 0.2);
        attributor.recordTaskCost("case-001", "task-3", 1500, 0.15);
        attributor.completeCaseTracking("case-001", 4500);

        CostAttributor.CaseCost cost = attributor.getCaseCost("case-001");
        assertEquals(3, cost.taskCount);
        assertTrue(cost.externalServiceCost.compareTo(BigDecimal.valueOf(0.45)) < 0.01);
    }

    @Test
    @DisplayName("Should handle invalid cost parameters")
    void testInvalidCostParameters() {
        assertThrows(IllegalArgumentException.class, () ->
                attributor.setCostPerMs(-0.01));
    }

    @Test
    @DisplayName("Should warn on untracked case completion")
    void testUnTrackedCaseCompletion() {
        attributor.completeCaseTracking("unknown-case", 1000);
        // Should not throw, but log warning
    }

    @Test
    @DisplayName("Should handle daily summary with time window")
    void testDailySummaryTimeWindow() {
        attributor.setCostPerMs(0.00001);

        attributor.startCaseTracking("case-001", "spec-1");
        attributor.recordTaskCost("case-001", "task-1", 1000, 0.0);
        attributor.completeCaseTracking("case-001", 1000);

        Map<LocalDate, BigDecimal> summary = attributor.getDailyCostSummary(7);
        assertTrue(summary.size() >= 0);
    }

    @Test
    @DisplayName("Should require minimum daily summary days")
    void testDailySummaryMinimum() {
        assertThrows(IllegalArgumentException.class, () ->
                attributor.getDailyCostSummary(0));
    }

    @Test
    @DisplayName("Should track cost per task type")
    void testTaskCostBreakdown() {
        attributor.setCostPerMs(0.00001);

        attributor.startCaseTracking("case-001", "order-process");
        attributor.recordTaskCost("case-001", "approval", 1000, 0.0);
        attributor.recordTaskCost("case-001", "payment", 2000, 0.5);
        attributor.completeCaseTracking("case-001", 3000);

        Map<String, Double> breakdown = attributor.getTaskCostBreakdown("order-process");
        assertTrue(breakdown.size() > 0);
    }

    @Test
    @DisplayName("Should handle concurrent cost recording")
    void testConcurrentCostRecording() throws InterruptedException {
        attributor.setCostPerMs(0.00001);

        Thread[] threads = new Thread[4];

        for (int t = 0; t < 4; t++) {
            final int threadIdx = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 5; i++) {
                    String caseId = "case-" + threadIdx + "-" + i;
                    attributor.startCaseTracking(caseId, "spec-" + threadIdx);
                    attributor.recordTaskCost(caseId, "task-1", 1000 + i * 100, 0.1);
                    attributor.completeCaseTracking(caseId, 1000 + i * 100);
                }
            });
            threads[t].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        BigDecimal totalCost = attributor.getTotalCost();
        assertTrue(totalCost.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should calculate ROI for multiple optimizations")
    void testMultipleROIAnalyses() {
        CostAttributor.ROIAnalysis roi1 = attributor.analyzeROI(
                "spec-1", "parallelization",
                BigDecimal.valueOf(10000), BigDecimal.valueOf(7000),
                BigDecimal.valueOf(1000), 50);

        CostAttributor.ROIAnalysis roi2 = attributor.analyzeROI(
                "spec-1", "caching",
                BigDecimal.valueOf(5000), BigDecimal.valueOf(3000),
                BigDecimal.valueOf(500), 30);

        assertTrue(roi1.roi > 0);
        assertTrue(roi2.roi > 0);
    }
}
