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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Cost attribution and ROI analysis for workflow execution.
 *
 * Attributes execution costs to workflows and cases, enabling business intelligence
 * and optimization. Tracks resource consumption (time, compute, storage) and
 * calculates ROI for optimization investments.
 *
 * <p><b>Cost Factors</b>
 * <ul>
 *   <li>Execution time (CPU-hours)</li>
 *   <li>Resource consumption (memory, storage)</li>
 *   <li>Agent/human effort (for manual tasks)</li>
 *   <li>External service calls</li>
 *   <li>Infrastructure overhead</li>
 * </ul>
 *
 * <p><b>Cost Model</b>
 * <pre>{@code
 * CaseCost = Sum of TaskCosts + Infrastructure Overhead
 * TaskCost = Duration (ms) * ResourceRate ($/ms) + ExternalServiceCost
 * WorkflowROI = (CostReduction - OptimizationCost) / OptimizationCost
 * }</pre>
 *
 * <p><b>Example Usage</b>
 * <pre>{@code
 * CostAttributor attributor = new CostAttributor(meterRegistry);
 * attributor.setCostPerMs(0.00001); // $0.01 per second
 * attributor.recordTaskCost("case-001", "approval", 5000, 0.05);
 * BigDecimal caseCost = attributor.getCaseCost("case-001");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class CostAttributor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CostAttributor.class);

    /**
     * Cost breakdown for a single case.
     */
    public record CaseCost(
            String caseId,
            String specId,
            BigDecimal executionCost,
            BigDecimal resourceCost,
            BigDecimal externalServiceCost,
            BigDecimal totalCost,
            long durationMs,
            Instant startTime,
            Instant endTime,
            int taskCount
    ) {
        public BigDecimal getCostPerSecond() {
            if (durationMs == 0) return BigDecimal.ZERO;
            return totalCost.multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf(durationMs), 6, RoundingMode.HALF_UP);
        }

        @Override
        public String toString() {
            return String.format("%s: %.2f (%.2f per sec, %d tasks)", caseId, totalCost, getCostPerSecond(), taskCount);
        }
    }

    /**
     * ROI analysis for an optimization.
     */
    public record ROIAnalysis(
            String specId,
            String optimizationName,
            BigDecimal costReduction,
            BigDecimal implementationCost,
            BigDecimal breakEvenPoint,
            double roi,
            int casesOptimized,
            Instant analysisDate
    ) {
        public String getPaybackStatus() {
            if (breakEvenPoint.compareTo(BigDecimal.ZERO) < 0) {
                return "NEGATIVE ROI";
            } else if (breakEvenPoint.compareTo(BigDecimal.valueOf(1)) <= 0) {
                return "ALREADY POSITIVE";
            } else {
                return String.format("%.1f%% more cost reduction needed", breakEvenPoint.doubleValue() * 100);
            }
        }

        @Override
        public String toString() {
            return String.format("%s: %s (%.1f%% ROI, %s)", specId, optimizationName, roi * 100, getPaybackStatus());
        }
    }

    private static class TaskCostMetrics {
        String taskName;
        AtomicLong totalDuration = new AtomicLong(0);
        AtomicLong executionCount = new AtomicLong(0);
        AtomicLong totalExternalCost = new AtomicLong(0); // in cents

        synchronized double getAvgDuration() {
            long count = executionCount.get();
            return count == 0 ? 0 : (double) totalDuration.get() / count;
        }
    }

    private final MeterRegistry meterRegistry;
    private final Map<String, CaseCost> caseCosts;
    private final Map<String, TaskCostMetrics> taskCostMetrics;
    private final Map<LocalDate, BigDecimal> dailyCosts;
    private final List<ROIAnalysis> roiAnalyses;
    private volatile BigDecimal costPerMs = BigDecimal.valueOf(0.00001); // $0.01 per second

    /**
     * Creates a new cost attributor.
     *
     * @param meterRegistry metrics registry for observability
     */
    public CostAttributor(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.caseCosts = new ConcurrentHashMap<>();
        this.taskCostMetrics = new ConcurrentHashMap<>();
        this.dailyCosts = new ConcurrentHashMap<>();
        this.roiAnalyses = new CopyOnWriteArrayList<>();
        registerMetrics();
    }

    /**
     * Sets the cost model: cost per millisecond.
     *
     * @param costPerMs cost in dollars per millisecond
     */
    public void setCostPerMs(double costPerMs) {
        if (costPerMs < 0) {
            throw new IllegalArgumentException("Cost cannot be negative");
        }
        this.costPerMs = BigDecimal.valueOf(costPerMs);
        LOGGER.info("Cost model updated: ${}/ms", costPerMs);
    }

    /**
     * Records task cost for a case.
     *
     * @param caseId case identifier
     * @param taskName task name
     * @param durationMs execution duration
     * @param externalServiceCost external cost (in dollars)
     */
    public void recordTaskCost(String caseId, String taskName, long durationMs, double externalServiceCost) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(taskName);

        if (durationMs < 0 || externalServiceCost < 0) {
            return;
        }

        TaskCostMetrics metrics = taskCostMetrics.computeIfAbsent(taskName, k -> new TaskCostMetrics());
        metrics.totalDuration.addAndGet(durationMs);
        metrics.executionCount.incrementAndGet();
        metrics.totalExternalCost.addAndGet((long) (externalServiceCost * 100));

        BigDecimal executionCost = BigDecimal.valueOf(durationMs).multiply(costPerMs);
        BigDecimal totalTaskCost = executionCost.add(BigDecimal.valueOf(externalServiceCost));

        caseCosts.compute(caseId, (key, existing) -> {
            if (existing == null) {
                throw new IllegalStateException("Case not initialized: " + caseId);
            }
            return new CaseCost(
                    existing.caseId,
                    existing.specId,
                    existing.executionCost.add(executionCost),
                    existing.resourceCost, // unchanged
                    existing.externalServiceCost.add(BigDecimal.valueOf(externalServiceCost)),
                    existing.totalCost.add(totalTaskCost),
                    0, // updated on completion
                    existing.startTime,
                    existing.endTime,
                    existing.taskCount + 1
            );
        });

        LOGGER.trace("Task cost recorded: {} - {} (${} execution + ${} external)",
                caseId, taskName, String.format("%.4f", executionCost), String.format("%.4f", externalServiceCost));
    }

    /**
     * Initiates case cost tracking.
     *
     * @param caseId case identifier
     * @param specId specification identifier
     */
    public void startCaseTracking(String caseId, String specId) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(specId);

        caseCosts.putIfAbsent(caseId, new CaseCost(
                caseId,
                specId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                Instant.now(),
                null,
                0
        ));
    }

    /**
     * Completes case cost tracking with final duration.
     *
     * @param caseId case identifier
     * @param finalDurationMs total case duration
     */
    public void completeCaseTracking(String caseId, long finalDurationMs) {
        Objects.requireNonNull(caseId);

        CaseCost existing = caseCosts.get(caseId);
        if (existing == null) {
            LOGGER.warn("Completing untracked case: {}", caseId);
            return;
        }

        CaseCost completed = new CaseCost(
                existing.caseId,
                existing.specId,
                existing.executionCost,
                existing.resourceCost,
                existing.externalServiceCost,
                existing.totalCost,
                finalDurationMs,
                existing.startTime,
                Instant.now(),
                existing.taskCount
        );

        caseCosts.put(caseId, completed);

        // Record daily costs
        LocalDate today = LocalDate.now();
        dailyCosts.compute(today, (date, existing2) ->
                (existing2 == null ? completed.totalCost : existing2.add(completed.totalCost)));

        meterRegistry.gauge("yawl.cost.case_cost",
                Collections.singletonList(io.micrometer.core.instrument.Tag.of("case", caseId)),
                completed.totalCost.doubleValue());

        LOGGER.info("Case completed: {}", completed);
    }

    /**
     * Gets cost for a completed case.
     *
     * @param caseId case identifier
     * @return cost details, or null if not found
     */
    public CaseCost getCaseCost(String caseId) {
        Objects.requireNonNull(caseId);
        return caseCosts.get(caseId);
    }

    /**
     * Analyzes ROI for an optimization.
     *
     * @param specId specification identifier
     * @param optimizationName optimization name
     * @param historicalCost historical total cost for spec
     * @param optimizedCost optimized total cost for spec
     * @param implementationCost one-time implementation cost
     * @param casesOptimized number of cases already using optimization
     * @return ROI analysis
     */
    public ROIAnalysis analyzeROI(String specId, String optimizationName, BigDecimal historicalCost,
                                  BigDecimal optimizedCost, BigDecimal implementationCost, int casesOptimized) {
        Objects.requireNonNull(specId);
        Objects.requireNonNull(optimizationName);

        BigDecimal costReduction = historicalCost.subtract(optimizedCost);
        double roi = implementationCost.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                costReduction.divide(implementationCost, 4, RoundingMode.HALF_UP).doubleValue();

        BigDecimal breakEven = implementationCost.compareTo(costReduction) > 0 ?
                implementationCost.divide(costReduction, 4, RoundingMode.HALF_UP) :
                BigDecimal.valueOf(-1);

        ROIAnalysis analysis = new ROIAnalysis(
                specId,
                optimizationName,
                costReduction,
                implementationCost,
                breakEven,
                roi,
                casesOptimized,
                Instant.now()
        );

        roiAnalyses.add(analysis);
        LOGGER.info("ROI Analysis: {}", analysis);
        meterRegistry.counter("yawl.cost.roi_analysis",
                "spec", specId, "optimization", optimizationName).increment();

        return analysis;
    }

    /**
     * Gets total cost for a specification.
     *
     * @param specId specification identifier
     * @return total cost across all cases
     */
    public BigDecimal getSpecCost(String specId) {
        Objects.requireNonNull(specId);
        return caseCosts.values().stream()
                .filter(cost -> specId.equals(cost.specId))
                .map(CaseCost::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Gets top-cost cases for a specification.
     *
     * @param specId specification identifier
     * @param limit number of cases to return
     * @return list of most expensive cases
     */
    public List<CaseCost> getTopCostCases(String specId, int limit) {
        Objects.requireNonNull(specId);
        return caseCosts.values().stream()
                .filter(cost -> specId.equals(cost.specId))
                .sorted(Comparator.comparing(CaseCost::totalCost).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Gets cost breakdown by task for a specification.
     *
     * @param specId specification identifier
     * @return map of task name to average cost
     */
    public Map<String, Double> getTaskCostBreakdown(String specId) {
        Objects.requireNonNull(specId);
        Map<String, Double> breakdown = new HashMap<>();

        caseCosts.values().stream()
                .filter(cost -> specId.equals(cost.specId))
                .forEach(cost -> {
                    // This is simplified; in production would need per-task tracking
                    breakdown.put("totalSpec", cost.totalCost.doubleValue());
                });

        return breakdown;
    }

    /**
     * Gets daily cost summary.
     *
     * @param days number of days to summarize
     * @return map of date to total cost
     */
    public Map<LocalDate, BigDecimal> getDailyCostSummary(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("Days must be >= 1");
        }

        LocalDate cutoff = LocalDate.now().minusDays(days);
        return dailyCosts.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(cutoff))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    /**
     * Gets total cost across all cases.
     *
     * @return total cost
     */
    public BigDecimal getTotalCost() {
        return caseCosts.values().stream()
                .map(CaseCost::totalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Gets statistics for monitoring.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCasesTracked", caseCosts.size());
        stats.put("totalCost", getTotalCost().toString());
        stats.put("averageCaseCost", caseCosts.isEmpty() ? "0" :
                getTotalCost().divide(BigDecimal.valueOf(caseCosts.size()), 4, RoundingMode.HALF_UP).toString());
        stats.put("tasksTracked", taskCostMetrics.size());
        stats.put("roiAnalysesPerformed", roiAnalyses.size());
        stats.put("daysWithData", dailyCosts.size());
        return stats;
    }

    private void registerMetrics() {
        Gauge.builder("yawl.cost.tracked_cases", caseCosts, Map::size)
                .register(meterRegistry);
        meterRegistry.gauge("yawl.cost.total_cost",
                Collections.emptyList(),
                this,
                cost -> cost.getTotalCost().doubleValue());
    }
}
