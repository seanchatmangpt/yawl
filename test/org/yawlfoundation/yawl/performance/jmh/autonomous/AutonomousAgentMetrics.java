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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.performance.jmh.autonomous;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Comprehensive metrics collection system for autonomous agent performance testing.
 *
 * This class provides structured metrics collection and validation for all
 * autonomous agent operations, supporting real-time monitoring and post-test analysis.
 *
 * @author YAWL Performance Team
 * @version 6.0.0-GA
 */
public class AutonomousAgentMetrics {

    // Core metrics containers
    private final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();
    private final Map<String, AgentMetrics> agentMetrics = new ConcurrentHashMap<>();
    private final Map<String, WorkflowMetrics> workflowMetrics = new ConcurrentHashMap<>();
    private final Map<String, SystemMetrics> systemMetrics = new ConcurrentHashMap<>();

    // Performance requirement validation
    private final PerformanceRequirements requirements = new PerformanceRequirements();

    /**
     * Record an operation with detailed timing and success tracking.
     */
    public void recordOperation(String operationType, String agentId, boolean success,
                               long durationNanos, Map<String, Object> context) {
        OperationMetrics metrics = operationMetrics.computeIfAbsent(
            operationType, k -> new OperationMetrics());

        metrics.totalOperations.incrementAndGet();
        metrics.totalLatency.addAndGet(durationNanos);

        if (success) {
            metrics.successfulOperations.incrementAndGet();
        } else {
            metrics.failedOperations.incrementAndGet();
        }

        // Track percentiles
        metrics.updatePercentiles(durationNanos);

        // Update agent-specific metrics
        AgentMetrics agentMetrics = this.agentMetrics.computeIfAbsent(
            agentId, k -> new AgentMetrics(agentId));
        agentMetrics.recordOperation(operationType, success, durationNanos);

        // Context-specific tracking
        if (context != null) {
            metrics.contextCounts.merge(context.toString(), 1, Integer::sum);
        }
    }

    /**
     * Record discovery operation metrics.
     */
    public void recordDiscovery(String agentId, boolean success, long durationMs,
                               int discoveredAgents, String discoveryType) {
        OperationMetrics metrics = operationMetrics.computeIfAndGet(
            "discovery", k -> new OperationMetrics());

        recordOperation("discovery", agentId, success, durationMs * 1_000_000,
            Map.of("discoveryType", discoveryType, "discoveredCount", discoveredAgents));

        // Discovery-specific tracking
        if (discoveredAgents > 0) {
            metrics.extraMetrics.put("avgDiscoveredAgents",
                (double) metrics.extraMetrics.getOrDefault("avgDiscoveredAgents", 0.0) + discoveredAgents);
        }
    }

    /**
     * Record handoff operation metrics.
     */
    public void recordHandoff(String sourceAgent, String targetAgent, boolean success,
                             long durationMs, int retryCount, boolean statePreserved) {
        Map<String, Object> context = Map.of(
            "sourceAgent", sourceAgent,
            "targetAgent", targetAgent,
            "retryCount", retryCount,
            "statePreserved", statePreserved
        );

        recordOperation("handoff", sourceAgent, success, durationMs * 1_000_000, context);

        // Handoff-specific metrics
        OperationMetrics metrics = operationMetrics.get("handoff");
        if (metrics != null) {
            metrics.extraMetrics.merge("totalRetries", retryCount, Integer::sum);
            metrics.extraMetrics.merge("statePreservedCount", statePreserved ? 1 : 0, Integer::sum);
        }
    }

    /**
     * Record resource allocation metrics.
     */
    public void recordResourceAllocation(String agentId, boolean success, long durationMs,
                                       int allocatedResources, boolean conflictResolved) {
        Map<String, Object> context = Map.of(
            "allocatedResources", allocatedResources,
            "conflictResolved", conflictResolved
        );

        recordOperation("allocation", agentId, success, durationMs * 1_000_000, context);
    }

    /**
     * Record workflow coordination metrics.
     */
    public void recordWorkflowCoordination(String workflowId, boolean success,
                                         long durationMs, int agentsInvolved) {
        WorkflowMetrics metrics = workflowMetrics.computeIfAbsent(
            workflowId, k -> new WorkflowMetrics(workflowId));

        metrics.executionCount.incrementAndGet();
        if (success) {
            metrics.successCount.incrementAndGet();
        }
        metrics.totalDuration.addAndGet(durationMs);
        metrics.agentsInvolved.addAndGet(agentsInvolved);
    }

    /**
     * Calculate performance summary for validation.
     */
    public PerformanceSummary calculateSummary() {
        PerformanceSummary summary = new PerformanceSummary();

        // Overall performance
        summary.totalOperations = operationMetrics.values().stream()
            .mapToInt(m -> m.totalOperations.get())
            .sum();
        summary.totalSuccesses = operationMetrics.values().stream()
            .mapToInt(m -> m.successfulOperations.get())
            .sum();
        summary.totalFailures = operationMetrics.values().stream()
            .mapToInt(m -> m.failedOperations.get())
            .sum();

        // Calculate success rates
        if (summary.totalOperations > 0) {
            summary.overallSuccessRate = (double) summary.totalSuccesses / summary.totalOperations * 100;
        }

        // Calculate latency metrics
        for (Map.Entry<String, OperationMetrics> entry : operationMetrics.entrySet()) {
            OperationMetrics metrics = entry.getValue();
            if (metrics.totalOperations.get() > 0) {
                double avgLatency = (double) metrics.totalLatency.get() / metrics.totalOperations.get() / 1_000_000;
                summary.avgLatencies.put(entry.getKey(), avgLatency);

                // Calculate throughput (ops/sec)
                summary.throughputs.put(entry.getKey(),
                    metrics.totalOperations.get() * 1000.0 / (metrics.testDurationMs));
            }
        }

        // Validate against requirements
        summary.requirementsMet = validateRequirements();

        return summary;
    }

    /**
     * Validate performance requirements.
     */
    public boolean validateRequirements() {
        PerformanceSummary summary = calculateSummary();

        // Check discovery latency requirement (< 50ms)
        double discoveryLatency = summary.avgLatencies.getOrDefault("discovery", 100.0);
        boolean discoveryValid = discoveryLatency < requirements.maxDiscoveryLatencyMs;

        // Check message throughput requirement (> 1000 ops/sec)
        double messageThroughput = summary.throughputs.getOrDefault("discovery", 0.0);
        boolean throughputValid = messageThroughput > requirements.minMessageThroughput;

        // Check handoff success rate requirement (> 99%)
        double handoffSuccessRate = calculateOperationSuccessRate("handoff");
        boolean handoffValid = handoffSuccessRate >= requirements.minHandoffSuccessRate;

        // Check resource allocation accuracy requirement (> 95%)
        double allocationAccuracy = calculateOperationSuccessRate("allocation");
        boolean allocationValid = allocationAccuracy >= requirements.minAllocationAccuracy;

        // Update requirements validation
        requirements.discoveryLatencyMet = discoveryValid;
        requirements.messageThroughputMet = throughputValid;
        requirements.handoffSuccessRateMet = handoffValid;
        requirements.allocationAccuracyMet = allocationValid;

        return discoveryValid && throughputValid && handoffValid && allocationValid;
    }

    /**
     * Calculate success rate for specific operation type.
     */
    private double calculateOperationSuccessRate(String operationType) {
        OperationMetrics metrics = operationMetrics.get(operationType);
        if (metrics == null || metrics.totalOperations.get() == 0) {
            return 0.0;
        }

        return (double) metrics.successfulOperations.get() / metrics.totalOperations.get() * 100;
    }

    /**
     * Export metrics to JSON for analysis.
     */
    public String exportToJson() {
        PerformanceSummary summary = calculateSummary();

        return """
            {
              "timestamp": "%s",
              "performanceTargets": {
                "maxDiscoveryLatencyMs": %d,
                "minMessageThroughput": %d,
                "minHandoffSuccessRate": %d,
                "minAllocationAccuracy": %d
              },
              "summary": {
                "totalOperations": %d,
                "totalSuccesses": %d,
                "totalFailures": %d,
                "overallSuccessRate": %.2f%%,
                "requirementsMet": %s
              },
              "operationMetrics": {
                %s
              },
              "validation": {
                "discoveryLatencyMet": %s,
                "messageThroughputMet": %s,
                "handoffSuccessRateMet": %s,
                "allocationAccuracyMet": %s
              }
            }
            """.formatted(
            Instant.now(),
            requirements.maxDiscoveryLatencyMs,
            requirements.minMessageThroughput,
            requirements.minHandoffSuccessRate,
            requirements.minAllocationAccuracy,
            summary.totalOperations,
            summary.totalSuccesses,
            summary.totalFailures,
            summary.overallSuccessRate,
            summary.requirementsMet,
            summary.operationMetrics.entrySet().stream()
                .map(e -> "\"%s\": {\"avgLatencyMs\": %.2f, \"throughputOpsPerSec\": %.2f}".formatted(
                    e.getKey(), e.getValue().avgLatency(), e.getValue().throughput()))
                .collect(Collectors.joining(",\n                ")),
            requirements.discoveryLatencyMet,
            requirements.messageThroughputMet,
            requirements.handoffSuccessRateMet,
            requirements.allocationAccuracyMet
        );
    }

    // Inner classes for metrics structure
    public static class OperationMetrics {
        final AtomicInteger totalOperations = new AtomicInteger(0);
        final AtomicInteger successfulOperations = new AtomicInteger(0);
        final AtomicInteger failedOperations = new AtomicInteger(0);
        final AtomicLong totalLatency = new AtomicLong(0);
        final Map<String, Integer> contextCounts = new ConcurrentHashMap<>();
        final Map<String, Object> extraMetrics = new ConcurrentHashMap<>();
        final List<Long> latencies = new ArrayList<>();
        long testDurationMs = 0;

        void updatePercentiles(long durationNanos) {
            latencies.add(durationNanos);
            if (latencies.size() > 10000) {
                // Keep only recent measurements for memory efficiency
                latencies.subList(0, 5000).clear();
            }
        }

        double avgLatency() {
            if (totalOperations.get() == 0) return 0.0;
            return (double) totalLatency.get() / totalOperations.get() / 1_000_000;
        }

        double percentile(int p) {
            if (latencies.isEmpty()) return 0.0;
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);

            int index = (int) (p / 100.0 * (sorted.size() - 1));
            return sorted.get(index) / 1_000_000.0;
        }

        double throughput() {
            if (testDurationMs == 0) return 0.0;
            return totalOperations.get() * 1000.0 / testDurationMs;
        }
    }

    public static class AgentMetrics {
        final String agentId;
        final AtomicInteger totalOperations = new AtomicInteger(0);
        final Map<String, AtomicInteger> operationCounts = new ConcurrentHashMap<>();
        final Map<String, List<Long>> operationLatencies = new ConcurrentHashMap<>();

        AgentMetrics(String agentId) {
            this.agentId = agentId;
        }

        void recordOperation(String operationType, boolean success, long durationNanos) {
            totalOperations.incrementAndGet();
            operationCounts.computeIfAbsent(operationType, k -> new AtomicInteger(0))
                .incrementAndGet();

            operationLatencies.computeIfAbsent(operationType, k -> new ArrayList<>())
                .add(durationNanos);
        }
    }

    public static class WorkflowMetrics {
        final String workflowId;
        final AtomicInteger executionCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicLong totalDuration = new AtomicLong(0);
        final AtomicInteger agentsInvolved = new AtomicInteger(0);

        WorkflowMetrics(String workflowId) {
            this.workflowId = workflowId;
        }
    }

    public static class SystemMetrics {
        final Instant startTime = Instant.now();
        final Map<String, Long> resourceUsage = new ConcurrentHashMap<>();
        final List<String> criticalEvents = new ArrayList<>();
    }

    public static class PerformanceRequirements {
        final int maxDiscoveryLatencyMs = 50;
        final int minMessageThroughput = 1000;
        final int minHandoffSuccessRate = 99;
        final int minAllocationAccuracy = 95;

        boolean discoveryLatencyMet = false;
        boolean messageThroughputMet = false;
        boolean handoffSuccessRateMet = false;
        boolean allocationAccuracyMet = false;
    }

    public static class PerformanceSummary {
        int totalOperations = 0;
        int totalSuccesses = 0;
        int totalFailures = 0;
        double overallSuccessRate = 0.0;
        boolean requirementsMet = false;
        final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();
        final Map<String, Double> avgLatencies = new ConcurrentHashMap<>();
        final Map<String, Double> throughputs = new ConcurrentHashMap<>();
    }
}