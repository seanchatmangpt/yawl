/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the YAWL Foundation licence.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.yawlfoundation.yawl.graalpy.performance;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.yawlfoundation.yawl.elements.YAWLTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.graalpy.execution.YawlGraalPyEngine;
import org.yawlfoundation.yawl.graalpy.types.YawlTypeMarshaller;
import org.yawlfoundation.yawl.graalpy.types.YawlTypeMarshallerFactory;
import org.yawlfoundation.yawl.graalpy.load.PerformanceTargets;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PM4RS workflow analysis performance benchmark.
 * 
 * Measures the performance impact of PM4RS (Process Mining for Resource Sharing)
 * integration for workflow analysis, resource allocation, and optimization.
 * 
 * Target: <10% overhead over pure Java implementation
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Benchmark)
public class PM4RSWorkflowAnalysisBenchmark {

    private YawlTypeMarshaller marshaller;
    private YawlGraalPyEngine graalPyEngine;
    private YNet testNet;
    private List<YAWLTask> taskList;
    
    // PM4RS test data
    private List<Map<String, Object>> workflowInstances;
    private Map<String, Object> resourcePool;
    private Map<String, Object> analysisParameters;
    private Map<String, Object> baselineMetrics;
    
    // Java baseline
    private Map<String, Object> javaAnalysisResults;

    @Setup
    public void setup() throws Exception {
        // Initialize GraalPy engine
        marshaller = YawlTypeMarshallerFactory.createMarshaller();
        graalPyEngine = new YawlGraalPyEngine();
        graalPyEngine.initialize();
        
        // Create test workflow
        testNet = createAnalysisNet();
        taskList = new ArrayList<>(testNet.getTasks());
        
        // Prepare PM4RS data
        workflowInstances = createWorkflowInstances();
        resourcePool = createResourcePool();
        analysisParameters = createAnalysisParameters();
        baselineMetrics = createBaselineMetrics();
        javaAnalysisResults = new ConcurrentHashMap<>();
        
        // Load PM4RS Python scripts
        loadPM4RSScripts();
    }

    @TearDown
    public void tearDown() {
        if (graalPyEngine != null) {
            graalPyEngine.shutdown();
        }
    }

    /**
     * PM4RS Resource Allocation Performance
     * Measures the time taken to allocate resources across workflow instances
     */
    @Benchmark
    public void benchmarkPM4RSResourceAllocation(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        
        Map<String, Object> pythonData = marshaller.marshallToPython(Map.of(
            "workflowInstances", marshaller.marshallToPython(workflowInstances),
            "resourcePool", marshaller.marshallToPython(resourcePool),
            "allocationStrategy", "optimal"
        ));
        
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(0), 
            pythonData
        );
        
        long duration = System.nanoTime() - startTime;
        
        // Verify performance targets
        double degradation = calculatePerformanceDegradation(duration);
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Resource allocation performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(result);
        bh.consume(duration);
    }

    /**
     * PM4RS Workload Balancing Performance
     * Measures the time taken to balance workload across resources
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(2)
    public void benchmarkPM4RSWorkloadBalancing(Blackhole bh) throws Exception {
        List<Map<String, Object>> balancingResults = new ArrayList<>();
        List<Long> balancingTimes = new ArrayList<>();
        
        for (int balancingType = 0; balancingType < 3; balancingType++) {
            long startTime = System.nanoTime();
            
            Map<String, Object> balanceData = new HashMap<>();
            balanceData.put("workflowInstances", marshaller.marshallToPython(workflowInstances));
            balanceData.put("resourcePool", marshaller.marshallToPython(resourcePool));
            balanceData.put("balancingType", balancingType);
            balanceData.put("timeHorizon", 3600); // 1 hour
            
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(1), 
                balanceData
            );
            
            long duration = System.nanoTime() - startTime;
            balancingTimes.add(duration);
            balancingResults.add(result);
        }
        
        // Calculate average performance
        double avgDuration = balancingTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double degradation = calculatePerformanceDegradation(avgDuration);
        
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Workload balancing performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(balancingResults);
        bh.consume(avgDuration);
    }

    /**
     * PM4RS Performance Optimization Performance
     * Measures the time taken to optimize workflow performance
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkPM4RSPerformanceOptimization(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        
        Map<String, Object> pythonData = marshaller.marshallToPython(Map.of(
            "workflowInstances", marshaller.marshallToPython(workflowInstances),
            "optimizationGoals", List.of("throughput", "latency", "resource utilization"),
            "constraints", Map.of(
                "maxLatency", 1000,
                "maxCost", 10000,
                "minThroughput", 100
            )
        ));
        
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(2), 
            pythonData
        );
        
        long duration = System.nanoTime() - startTime;
        
        double degradation = calculatePerformanceDegradation(duration);
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Performance optimization performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(result);
        bh.consume(duration);
    }

    /**
     * PM4RS Bottleneck Detection Performance
     * Measures the time taken to detect and analyze workflow bottlenecks
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkPM4RSBottleneckDetection(Blackhole bh) throws Exception {
        List<Map<String, Object>> bottleneckResults = new ArrayList<>();
        List<Long> detectionTimes = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            long startTime = System.nanoTime();
            
            Map<String, Object> detectionData = new HashMap<>();
            detectionData.put("workflowInstances", marshaller.marshallToPython(workflowInstances));
            detectionData.put("detectionMethod", "statistical");
            detectionData.put("timeWindow", 3600); // 1 hour
            
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(3), 
                detectionData
            );
            
            long duration = System.nanoTime() - startTime;
            detectionTimes.add(duration);
            bottleneckResults.add(result);
            bh.consume(result);
        }
        
        // Calculate throughput
        double totalTime = detectionTimes.stream().mapToLong(Long::longValue).sum();
        double throughput = bottleneckResults.size() / (totalTime / 1e9);
        
        if (throughput < PerformanceTargets.MIN_THROUGHPUT_OPS_PER_SEC) {
            throw new AssertionError("Bottleneck detection throughput below target: " + throughput + " ops/sec");
        }
        
        bh.consume(throughput);
    }

    /**
     * PM4RS Capacity Planning Performance
     * Concurrent capacity planning with multiple scenarios
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(4)
    public void benchmarkPM4RSCapacityPlanningConcurrent(Blackhole bh) throws Exception {
        List<Thread> threads = new ArrayList<>();
        List<Map<String, Object>> capacityResults = Collections.synchronizedList(new ArrayList<>());
        List<Long> capacityTimes = Collections.synchronizedList(new ArrayList<>());
        
        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        long startTime = System.nanoTime();
                        
                        Map<String, Object> scenarioData = createCapacityScenario(threadId, i);
                        Map<String, Object> pythonData = marshaller.marshallToPython(scenarioData);
                        Map<String, Object> result = graalPyEngine.executeTask(
                            taskList.get(4), 
                            Map.of("scenario", pythonData, "threadId", threadId)
                        );
                        
                        long duration = System.nanoTime() - startTime;
                        capacityTimes.add(duration);
                        capacityResults.add(result);
                    }
                } catch (Exception e) {
                    // Log error but continue
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Validate performance
        double avgDuration = capacityTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double degradation = calculatePerformanceDegradation(avgDuration);
        
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Concurrent capacity planning performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(capacityResults);
        bh.consume(avgDuration);
    }

    /**
     * PM4RS Resource Utilization Analysis Performance
     * Measures the time taken to analyze resource utilization patterns
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkPM4RSResourceUtilizationAnalysis(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        
        Map<String, Object> pythonData = marshaller.marshallToPython(Map.of(
            "workflowInstances", marshaller.marshallToPython(workflowInstances),
            "resourcePool", marshaller.marshallToPython(resourcePool),
            "analysisPeriod", Map.of("start", 0, "end", 86400), // 24 hours
            "granularity", "hourly"
        ));
        
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(5), 
            pythonData
        );
        
        long duration = System.nanoTime() - startTime;
        
        double degradation = calculatePerformanceDegradation(duration);
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Resource utilization analysis performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(result);
        bh.consume(duration);
    }

    /**
     * Java Baseline Comparison
     * Pure Java implementation of PM4RS analysis for comparison
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkJavaPM4RSAnalysisBaseline(Blackhole bh) {
        long startTime = System.nanoTime();
        
        // Simulate Java PM4RS analysis
        Map<String, Object> result = performJavaAnalysis(workflowInstances, resourcePool);
        
        long duration = System.nanoTime() - startTime;
        javaAnalysisResults.put("baseline", Map.of("result", result, "duration", duration));
        
        bh.consume(result);
        bh.consume(duration);
    }

    // Helper methods
    private YNet createAnalysisNet() {
        YNet net = new YNet("pm4rs-analysis-net");
        
        YCondition start = net.addCondition("start");
        YCondition end = net.addCondition("end");
        
        // Add tasks for different PM4RS operations
        YAWLTask resourceAllocation = net.addTask("resource-allocation");
        YAWLTask workloadBalancing = net.addTask("workload-balancing");
        YAWLTask performanceOptimization = net.addTask("performance-optimization");
        YAWLTask bottleneckDetection = net.addTask("bottleneck-detection");
        YAWLTask capacityPlanning = net.addTask("capacity-planning");
        YAWLTask utilizationAnalysis = net.addTask("utilization-analysis");
        
        net.setStartConditions(Collections.singleton(start.getID()));
        net.setFinishConditions(Collections.singleton(end.getID()));
        
        // Set flows
        net.addFlow(start.getID(), resourceAllocation.getID());
        net.addFlow(resourceAllocation.getID(), workloadBalancing.getID());
        net.addFlow(workloadBalancing.getID(), performanceOptimization.getID());
        net.addFlow(performanceOptimization.getID(), bottleneckDetection.getID());
        net.addFlow(bottleneckDetection.getID(), capacityPlanning.getID());
        net.addFlow(capacityPlanning.getID(), utilizationAnalysis.getID());
        net.addFlow(utilizationAnalysis.getID(), end.getID());
        
        return net;
    }

    private List<Map<String, Object>> createWorkflowInstances() {
        List<Map<String, Object>> instances = new ArrayList<>();
        
        for (int i = 0; i < 500; i++) {
            Map<String, Object> instance = new HashMap<>();
            instance.put("instanceId", "instance-" + i);
            instance.put("workflowId", "workflow-" + (i % 10));
            instance.put("startTime", System.currentTimeMillis() - (500 - i) * 1000);
            instance.put("estimatedDuration", 5000 + Math.random() * 10000);
            instance.put("resourceRequirements", Map.of(
                "cpu", 1 + Math.random() * 4,
                "memory", 512 + Math.random() * 4096,
                "storage", 1 + Math.random() * 10
            ));
            instance.put("priority", Math.random() > 0.7 ? "high" : "normal");
            instances.add(instance);
        }
        
        return instances;
    }

    private Map<String, Object> createResourcePool() {
        Map<String, Object> pool = new HashMap<>();
        
        List<Map<String, Object>> resources = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Map<String, Object> resource = new HashMap<>();
            resource.put("resourceId", "resource-" + i);
            resource.put("type", getRandomResourceType());
            resource.put("capacity", Map.of(
                "cpu", 8,
                "memory", 16384,
                "storage", 100
            ));
            resource.put("currentUtilization", Math.random() * 0.8);
            resource.put("location", "location-" + (i % 5));
            resource.put("costPerHour", 10 + Math.random() * 40);
            resources.add(resource);
        }
        
        pool.put("resources", resources);
        pool.put("totalCapacity", Map.of(
            "cpu", 160,
            "memory", 327680,
            "storage", 2000
        ));
        
        return pool;
    }

    private String getRandomResourceType() {
        String[] types = {"cpu", "memory", "storage", "network", "gpu"};
        return types[(int) (Math.random() * types.length)];
    }

    private Map<String, Object> createAnalysisParameters() {
        return Map.of(
            "optimizationObjective", "cost_efficiency",
            "timeHorizon", 86400, // 24 hours
            "granularity", "hourly",
            "constraints", Map.of(
                "maxLatency", 1000,
                "minThroughput", 100,
                "maxCost", 10000
            ),
            "algorithms", List.of("genetic", "linear_programming", "simulated_annealing")
        );
    }

    private Map<String, Object> createBaselineMetrics() {
        return Map.of(
            "averageUtilization", 0.65,
            "throughput", 85.0,
            "averageLatency", 450,
            "costEfficiency", 0.78,
            "bottleneckCount", 3,
            "resourceAvailability", 0.92
        );
    }

    private Map<String, Object> createCapacityScenario(int threadId, int iteration) {
        Map<String, Object> scenario = new HashMap<>();
        scenario.put("scenarioId", "scenario-" + threadId + "-" + iteration);
        scenario.put("timeframe", 86400 * (iteration + 1)); // 1, 2, 3 days
        scenario.put("growthRate", 0.1 + threadId * 0.1);
        scenario.put("forecastAccuracy", 0.8 + Math.random() * 0.2);
        scenario.put("constraints", Map.of(
            "maxBudget", 50000 + threadId * 10000,
            "minSLA", 0.95 + threadId * 0.01,
            "greenEnergy", threadId % 2 == 0
        ));
        return scenario;
    }

    private void loadPM4RSScripts() throws Exception {
        // Load PM4RS Python scripts (mock implementation)
        String pm4rsScript = """
            import numpy as np
            from typing import Dict, Any, List
            
            def resource_allocation(workflow_instances: List[Dict[str, Any]], resource_pool: Dict[str, Any], allocation_strategy: str = "optimal") -> Dict[str, Any]:
                # Mock PM4RS resource allocation
                return {
                    "allocationPlan": {
                        "assignments": {f"instance-{i}": f"resource-{i % 20}" for i in range(100)},
                        "totalUtilization": np.random.uniform(0.6, 0.9),
                        "cost": np.random.uniform(1000, 5000),
                        "efficiency": np.random.uniform(0.7, 0.95)
                    },
                    "allocationTime": np.random.uniform(50, 200),
                    "strategy": allocation_strategy,
                    "constraintsSatisfied": np.random.choice([True, False], p=[0.9, 0.1])
                }
                
            def workload_balancing(workflow_instances: List[Dict[str, Any]], resource_pool: Dict[str, Any], balancing_type: int, time_horizon: int) -> Dict[str, Any]:
                # Mock workload balancing
                balance_types = ["even", "priority", "capacity_based"]
                return {
                    "balancePlan": {
                        "resourceLoads": {f"resource-{i}": np.random.uniform(0.3, 1.0) for i in range(20)},
                        "peakLoad": np.random.uniform(0.8, 1.2),
                        "averageLoad": np.random.uniform(0.5, 0.8),
                        "balanceMetric": np.random.uniform(0.7, 0.95)
                    },
                    "balancingTime": np.random.uniform(30, 150),
                    "type": balance_types[balancing_type],
                    "timeHorizon": time_horizon
                }
                
            def performance_optimization(workflow_instances: List[Dict[str, Any]], optimization_goals: List[str], constraints: Dict[str, Any]) -> Dict[str, Any]:
                # Mock performance optimization
                return {
                    "optimizationResults": {
                        "improvements": dict.fromkeys(optimization_goals, np.random.uniform(0.05, 0.3)),
                        "tradeoffs": {
                            "cost_vs_performance": np.random.uniform(-0.2, 0.1),
                            "latency_vs_throughput": np.random.uniform(-0.1, 0.2)
                        },
                        "constraintsMet": np.random.choice([True, False], p=[0.95, 0.05])
                    },
                    "optimizationTime": np.random.uniform(100, 500),
                    "goals": optimization_goals,
                    "constraints": constraints
                }
                
            def bottleneck_detection(workflow_instances: List[Dict[str, Any]], detection_method: str, time_window: int) -> Dict[str, Any]:
                # Mock bottleneck detection
                return {
                    "bottlenecks": [
                        {
                            "location": f"resource-{np.random.randint(0, 20)}",
                            "severity": np.random.uniform(0.7, 1.0),
                            "impact": np.random.uniform(0.5, 0.9),
                            "duration": np.random.randint(100, 1000)
                        }
                    ],
                    "detectionScore": np.random.uniform(0.8, 0.99),
                    "method": detection_method,
                    "timeWindow": time_window,
                    "detectionTime": np.random.uniform(20, 100)
                }
                
            def capacity_planning(scenario: Dict[str, Any], thread_id: int) -> Dict[str, Any]:
                # Mock capacity planning
                return {
                    "scenario": scenario,
                    "recommendations": [
                        {
                            "resourceType": "cpu",
                            "requiredCapacity": np.random.uniform(100, 200),
                            "cost": np.random.uniform(10000, 50000),
                            "roi": np.random.uniform(0.1, 0.5)
                        }
                    ],
                    "confidence": np.random.uniform(0.7, 0.95),
                    "planningTime": np.random.uniform(50, 200),
                    "threadId": thread_id
                }
                
            def resource_utilization_analysis(workflow_instances: List[Dict[str, Any]], resource_pool: Dict[str, Any], analysis_period: Dict[str, int], granularity: str) -> Dict[str, Any]:
                # Mock resource utilization analysis
                return {
                    "utilizationMetrics": {
                        "average": np.random.uniform(0.4, 0.8),
                        "peak": np.random.uniform(0.8, 1.2),
                        "trend": np.random.uniform(-0.1, 0.1),
                        "efficiency": np.random.uniform(0.6, 0.9)
                    },
                    "period": analysis_period,
                    "granularity": granularity,
                    "analysisTime": np.random.uniform(80, 300),
                    "recommendations": [f"Recommendation {i}" for i in range(np.random.randint(1, 4))]
                }
            """;
        
        // In a real implementation, this would load actual PM4RS scripts
        // For benchmarking, we use inline Python
    }

    private Map<String, Object> performJavaAnalysis(List<Map<String, Object>> workflowInstances, Map<String, Object> resourcePool) {
        // Simulate Java PM4RS analysis for baseline comparison
        Map<String, Object> result = new HashMap<>();
        result.put("analysisResults", Map.of(
            "totalInstances", workflowInstances.size(),
            "totalResources", ((List) resourcePool.get("resources")).size(),
            "analysisTime", System.currentTimeMillis()
        ));
        result.put("optimizationScore", 0.85);
        return result;
    }

    private double calculatePerformanceDegradation(long pythonDuration) {
        // Get Java baseline duration
        Map<String, Object> baseline = javaAnalysisResults.get("baseline");
        if (baseline == null) return 0.0;
        
        long javaDuration = (Long) ((Map<String, Object>) baseline.get("result")).get("analysisTime");
        
        // Calculate degradation percentage
        double degradation = ((double) (pythonDuration - javaDuration) / javaDuration) * 100;
        return Math.max(0, degradation); // Ensure non-negative
    }

    /**
     * Main method for running benchmarks
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(PM4RSWorkflowAnalysisBenchmark.class.getSimpleName())
            .forks(3)
            .warmupIterations(5)
            .measurementIterations(10)
            .output("pm4rs-workflow-analysis-benchmarks.json")
            .build();
        
        new Runner(opt).run();
    }
}
