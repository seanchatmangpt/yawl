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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.email;

import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;
import org.yawlfoundation.yawl.dspy.adaptation.AdaptationAction;
import org.yawlfoundation.yawl.dspy.adaptation.WorkflowAdaptationContext;
import org.yawlfoundation.yawl.dspy.forensics.AnomalyContext;
import org.yawlfoundation.yawl.dspy.forensics.ForensicsReport;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test fixture builders for Chicago TDD email-driven integration tests.
 *
 * <p>Provides fluent builders for creating realistic test data that
 * exercises all code paths in email generation. Builders create real
 * objects, not mocks, following Chicago School TDD discipline.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class TestFixtureBuilders {

    private TestFixtureBuilders() {
        // Utility class - no instantiation
    }

    // ========================================================================
    // DspyExecutionMetrics Builders
    // ========================================================================

    /**
     * Creates a list of DspyExecutionMetrics for a 24-hour period.
     *
     * @param count number of metrics to generate
     * @return list of realistic execution metrics
     */
    public static List<DspyExecutionMetrics> createMetricsFor24Hours(int count) {
        List<DspyExecutionMetrics> metrics = new ArrayList<>();
        Instant now = Instant.now();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            // Spread metrics over 24 hours
            Instant timestamp = now.minus(random.nextLong(24), ChronoUnit.HOURS)
                    .minus(random.nextLong(60), ChronoUnit.MINUTES);

            // Realistic metrics with some variance
            double confidenceBase = 0.82 + (random.nextDouble() * 0.15); // 0.82-0.97
            boolean cacheHit = random.nextDouble() < 0.90; // 90% cache hit rate

            metrics.add(DspyExecutionMetrics.builder()
                    .compilationTimeMs(cacheHit ? 0 : 150 + random.nextLong(200))
                    .executionTimeMs(200 + random.nextLong(800))
                    .inputTokens(800 + random.nextLong(600))
                    .outputTokens(300 + random.nextLong(300))
                    .qualityScore(confidenceBase)
                    .cacheHit(cacheHit)
                    .contextReused(random.nextDouble() < 0.60)
                    .timestamp(timestamp)
                    .build());
        }

        return Collections.unmodifiableList(metrics);
    }

    /**
     * Creates DspyExecutionMetrics with specific confidence score.
     */
    public static DspyExecutionMetrics createMetricsWithConfidence(double confidence) {
        return DspyExecutionMetrics.builder()
                .compilationTimeMs(100)
                .executionTimeMs(500)
                .inputTokens(1000)
                .outputTokens(400)
                .qualityScore(confidence)
                .cacheHit(true)
                .contextReused(true)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates DspyExecutionMetrics with low confidence (below threshold).
     */
    public static DspyExecutionMetrics createLowConfidenceMetrics() {
        return DspyExecutionMetrics.builder()
                .compilationTimeMs(100)
                .executionTimeMs(500)
                .inputTokens(1000)
                .outputTokens(400)
                .qualityScore(0.55) // Below 0.70 threshold
                .cacheHit(false)
                .contextReused(false)
                .timestamp(Instant.now())
                .build();
    }

    // ========================================================================
    // AnomalyContext Builders
    // ========================================================================

    /**
     * Creates an AnomalyContext for resource contention scenario.
     */
    public static AnomalyContext createResourceContentionAnomaly() {
        Map<String, Long> recentSamples = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            recentSamples.put(String.valueOf(now - i * 1000L), 150L + (i % 50));
        }

        List<String> concurrentCases = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            concurrentCases.add(String.format("C%06d", 1234 + i));
        }

        return new AnomalyContext(
                "case_completion_latency",
                3200L,
                3.2, // 320% deviation
                recentSamples,
                concurrentCases
        );
    }

    /**
     * Creates an AnomalyContext for queue overflow scenario.
     */
    public static AnomalyContext createQueueOverflowAnomaly() {
        Map<String, Long> recentSamples = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < 50; i++) {
            recentSamples.put(String.valueOf(now - i * 1000L), 500L + (i * 10L));
        }

        List<String> concurrentCases = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            concurrentCases.add(String.format("C%06d", 5678 + i));
        }

        return new AnomalyContext(
                "queue_depth",
                5000L,
                2.5, // 250% deviation
                recentSamples,
                concurrentCases
        );
    }

    // ========================================================================
    // ForensicsReport Builders
    // ========================================================================

    /**
     * Creates a high-confidence ForensicsReport for resource contention.
     */
    public static ForensicsReport createResourceContentionForensicsReport() {
        return new ForensicsReport(
                "Resource contention from 12 concurrent cases",
                0.85,
                List.of(
                        "metric spike +320% at 14:32:15",
                        "concurrent cases spike 8 → 12",
                        "CPU utilization 95%",
                        "agent pool exhausted"
                ),
                "Scale up agents pool by 4 units",
                Instant.now()
        );
    }

    /**
     * Creates a ForensicsReport for external dependency timeout.
     */
    public static ForensicsReport createDependencyTimeoutForensicsReport() {
        return new ForensicsReport(
                "External dependency timeout causing cascading delays",
                0.78,
                List.of(
                        "latency spike in external API calls",
                        "timeout pattern detected",
                        "retry behavior increasing load"
                ),
                "Check external service health and implement circuit breaker",
                Instant.now()
        );
    }

    // ========================================================================
    // AdaptationAction Builders
    // ========================================================================

    /**
     * Creates a list of AdaptationActions for a 24-hour period.
     */
    public static List<AdaptationAction> createAdaptationActionsFor24Hours(int count) {
        List<AdaptationAction> actions = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            int actionType = random.nextInt(4);
            actions.add(switch (actionType) {
                case 0 -> new AdaptationAction.AddResource(
                        "agent-" + random.nextInt(100),
                        "task-" + random.nextInt(50),
                        "Critical bottleneck: allocating additional agent"
                );
                case 1 -> new AdaptationAction.ReRoute(
                        "task-" + random.nextInt(50),
                        "expedited-path-" + random.nextInt(5),
                        "Moderate bottleneck: trying alternate path"
                );
                case 2 -> new AdaptationAction.EscalateCase(
                        String.format("C%06d", random.nextInt(10000)),
                        random.nextBoolean() ? "manager" : "director",
                        "Critical bottleneck with no resources available"
                );
                default -> new AdaptationAction.SkipTask(
                        "task-" + random.nextInt(50),
                        "Rule-based bypass triggered"
                );
            });
        }

        return Collections.unmodifiableList(actions);
    }

    /**
     * Creates an AddResource action with specific details.
     */
    public static AdaptationAction.AddResource createAddResourceAction(
            String agentId, String taskId, String reason) {
        return new AdaptationAction.AddResource(agentId, taskId, reason);
    }

    /**
     * Creates an EscalateCase action with specific details.
     */
    public static AdaptationAction.EscalateCase createEscalateCaseAction(
            String caseId, String level, String reason) {
        return new AdaptationAction.EscalateCase(caseId, level, reason);
    }

    // ========================================================================
    // WorkflowAdaptationContext Builders
    // ========================================================================

    /**
     * Creates a WorkflowAdaptationContext for critical bottleneck scenario.
     */
    public static WorkflowAdaptationContext createCriticalBottleneckContext() {
        return WorkflowAdaptationContext.builder()
                .caseId("case-critical-001")
                .specId("high-value-transaction")
                .bottleneckScore(0.92)
                .enabledTasks(List.of("FinalApproval"))
                .busyTasks(List.of("FinalApproval", "RiskAssessment"))
                .queueDepth(40)
                .avgTaskLatencyMs(5000)
                .availableAgents(0)
                .eventType("BOTTLENECK_DETECTED")
                .eventPayload(Map.of("taskName", "FinalApproval", "severity", "critical"))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a WorkflowAdaptationContext for moderate bottleneck scenario.
     */
    public static WorkflowAdaptationContext createModerateBottleneckContext() {
        return WorkflowAdaptationContext.builder()
                .caseId("case-moderate-001")
                .specId("loan-approval")
                .bottleneckScore(0.65)
                .enabledTasks(List.of("ApproveApplication", "RequestDocumentation"))
                .busyTasks(List.of("ValidateDocuments"))
                .queueDepth(15)
                .avgTaskLatencyMs(1800)
                .availableAgents(2)
                .eventType("BOTTLENECK_DETECTED")
                .eventPayload(Map.of("taskName", "ValidateDocuments", "severity", "moderate"))
                .timestamp(Instant.now())
                .build();
    }

    // ========================================================================
    // Bootstrap Metrics Builders
    // ========================================================================

    /**
     * Creates bootstrap metrics for a 30-day period.
     */
    public static BootstrapMetrics createBootstrapMetricsFor30Days() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        return new BootstrapMetrics(
                45231, // total cases processed
                892,   // bootstrap invocations
                3,     // cache invalidations
                0.987, // success rate (98.7%)
                0.942, // training coverage (94.2%)
                0.991, // confidence compliance (99.1%)
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()
        );
    }

    /**
     * Creates bootstrap metrics with specific success rate.
     */
    public static BootstrapMetrics createBootstrapMetricsWithSuccessRate(double successRate) {
        return new BootstrapMetrics(
                1000,
                50,
                1,
                successRate,
                0.92,
                0.95,
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now()
        );
    }

    // ========================================================================
    // Routing Metrics Builders
    // ========================================================================

    /**
     * Creates routing metrics for a quarter.
     */
    public static RoutingMetrics createRoutingMetricsForQuarter(String quarter) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Q1 2026 should be better than Q4 2025
        boolean isRecent = quarter.contains("2026");
        double baseAccuracy = isRecent ? 0.88 : 0.82;
        double basePrecision = isRecent ? 0.83 : 0.78;
        double baseRecall = isRecent ? 0.81 : 0.76;

        return new RoutingMetrics(
                quarter,
                baseAccuracy + random.nextDouble() * 0.05,
                basePrecision + random.nextDouble() * 0.05,
                baseRecall + random.nextDouble() * 0.07,
                isRecent ? 89234 : 76387, // total examples
                isRecent ? 12847 : 8234   // new examples
        );
    }

    // ========================================================================
    // Worklet Selection Metrics Builders
    // ========================================================================

    /**
     * Creates worklet selection metrics for a month.
     */
    public static WorkletSelectionMetrics createWorkletMetricsForMonth(int days) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        return new WorkletSelectionMetrics(
                0.947, // selection accuracy (94.7%)
                0.982, // case completion rate (98.2%)
                0.873, // resource utilization (87.3%)
                45231, // total selections
                3,     // low confidence events
                days,
                Instant.now().minus(days, ChronoUnit.DAYS),
                Instant.now()
        );
    }

    // ========================================================================
    // Value Object Records
    // ========================================================================

    /**
     * Bootstrap metrics for compliance certification.
     */
    public record BootstrapMetrics(
            long totalCasesProcessed,
            long bootstrapInvocations,
            long cacheInvalidations,
            double successRate,
            double trainingCoverage,
            double confidenceCompliance,
            Instant periodStart,
            Instant periodEnd
    ) {}

    /**
     * Routing metrics for quarterly comparison.
     */
    public record RoutingMetrics(
            String quarter,
            double accuracy,
            double precision,
            double recall,
            long totalExamples,
            long newExamples
    ) {
        public double f1Score() {
            return 2 * (precision * recall) / (precision + recall);
        }
    }

    /**
     * Worklet selection metrics for board reporting.
     */
    public record WorkletSelectionMetrics(
            double selectionAccuracy,
            double caseCompletionRate,
            double resourceUtilization,
            long totalSelections,
            long lowConfidenceEvents,
            int daysInPeriod,
            Instant periodStart,
            Instant periodEnd
    ) {}
}
