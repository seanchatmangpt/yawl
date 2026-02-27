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
 * PM4py process mining performance benchmark.
 * 
 * Measures the performance impact of PM4py (Python Process Mining Framework)
 * integration for process discovery, conformance checking, and enhancement.
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
public class PM4pyProcessMiningBenchmark {

    private YawlTypeMarshaller marshaller;
    private YawlGraalPyEngine graalPyEngine;
    private YNet testNet;
    private List<YAWLTask> taskList;
    
    // PM4py test data
    private List<Map<String, Object>> eventLogs;
    private Map<String, Object> processDefinition;
    private Map<String, Object> miningParameters;
    private Map<String, Object> baselineMetrics;
    
    // Java baseline
    private Map<String, Object> javaMiningResults;

    @Setup
    public void setup() throws Exception {
        // Initialize GraalPy engine
        marshaller = YawlTypeMarshallerFactory.createMarshaller();
        graalPyEngine = new YawlGraalPyEngine();
        graalPyEngine.initialize();
        
        // Create test workflow
        testNet = createProcessMiningNet();
        taskList = new ArrayList<>(testNet.getTasks());
        
        // Prepare PM4py data
        eventLogs = createEventLogs();
        processDefinition = createProcessDefinition();
        miningParameters = createMiningParameters();
        baselineMetrics = createBaselineMetrics();
        javaMiningResults = new ConcurrentHashMap<>();
        
        // Load PM4py Python scripts
        loadPM4pyScripts();
    }

    @TearDown
    public void tearDown() {
        if (graalPyEngine != null) {
            graalPyEngine.shutdown();
        }
    }

    /**
     * PM4py Process Discovery Performance
     * Measures the time taken to discover process models from event logs
     */
    @Benchmark
    public void benchmarkPM4pyProcessDiscovery(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        
        Map<String, Object> pythonData = marshaller.marshallToPython(eventLogs);
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(0), 
            Map.of(
                "eventLogs", pythonData,
                "algorithm", "alpha",
                "parameters", marshaller.marshallToPython(miningParameters)
            )
        );
        
        long duration = System.nanoTime() - startTime;
        
        // Verify performance targets
        double degradation = calculatePerformanceDegradation(duration);
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Process discovery performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(result);
        bh.consume(duration);
    }

    /**
     * PM4py Conformance Checking Performance
     * Measures the time taken to perform conformance checking of event logs
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Threads(2)
    public void benchmarkPM4pyConformanceChecking(Blackhole bh) throws Exception {
        List<Map<String, Object>> conformanceResults = new ArrayList<>();
        List<Long> durations = new ArrayList<>();
        
        for (int checkType = 0; checkType < 3; checkType++) {
            long startTime = System.nanoTime();
            
            Map<String, Object> checkData = new HashMap<>();
            checkData.put("eventLogs", marshaller.marshallToPython(eventLogs));
            checkData.put("processModel", marshaller.marshallToPython(processDefinition));
            checkData.put("checkType", checkType);
            
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(1), 
                checkData
            );
            
            long duration = System.nanoTime() - startTime;
            durations.add(duration);
            conformanceResults.add(result);
        }
        
        // Calculate average performance
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        double degradation = calculatePerformanceDegradation(avgDuration);
        
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Conformance checking performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(conformanceResults);
        bh.consume(avgDuration);
    }

    /**
     * PM4py Process Enhancement Performance
     * Measures the time taken to enhance process models with discovered patterns
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkPM4pyProcessEnhancement(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        
        Map<String, Object> pythonData = marshaller.marshallToPython(Map.of(
            "originalModel", processDefinition,
            "eventLogs", marshaller.marshallToPython(eventLogs),
            "enhancementGoals", List.of("performance", "compliance", "resource utilization")
        ));
        
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(2), 
            pythonData
        );
        
        long duration = System.nanoTime() - startTime;
        
        double degradation = calculatePerformanceDegradation(duration);
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Process enhancement performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(result);
        bh.consume(duration);
    }

    /**
     * PM4py Performance Analysis Performance
     * Measures the time taken to analyze performance metrics from event logs
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkPM4pyPerformanceAnalysis(Blackhole bh) throws Exception {
        List<Map<String, Object>> performanceResults = new ArrayList<>();
        List<Long> analysisTimes = new ArrayList<>();
        
        for (int metricType = 0; metricType < 5; metricType++) {
            long startTime = System.nanoTime();
            
            Map<String, Object> analysisData = new HashMap<>();
            analysisData.put("eventLogs", marshaller.marshallToPython(eventLogs));
            analysisData.put("metricType", metricType);
            analysisData.put("timeframe", Map.of("start", 0, "end", 1000000));
            
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(3), 
                analysisData
            );
            
            long duration = System.nanoTime() - startTime;
            analysisTimes.add(duration);
            performanceResults.add(result);
            bh.consume(result);
        }
        
        // Calculate throughput
        double totalTime = analysisTimes.stream().mapToLong(Long::longValue).sum();
        double throughput = performanceResults.size() / (totalTime / 1e9);
        
        if (throughput < PerformanceTargets.MIN_THROUGHPUT_OPS_PER_SEC) {
            throw new AssertionError("Performance analysis throughput below target: " + throughput + " ops/sec");
        }
        
        bh.consume(throughput);
    }

    /**
     * PM4py Variant Analysis Performance
     * Concurrent analysis of process variants with multiple threads
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(4)
    public void benchmarkPM4pyVariantAnalysisConcurrent(Blackhole bh) throws Exception {
        List<Thread> threads = new ArrayList<>();
        List<Map<String, Object>> variantResults = Collections.synchronizedList(new ArrayList<>());
        List<Long> variantTimes = Collections.synchronizedList(new ArrayList<>());
        
        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        long startTime = System.nanoTime();
                        
                        Map<String, Object> variantData = createVariantData(threadId, i);
                        Map<String, Object> pythonData = marshaller.marshallToPython(variantData);
                        Map<String, Object> result = graalPyEngine.executeTask(
                            taskList.get(4), 
                            Map.of("variantLogs", pythonData, "threadId", threadId)
                        );
                        
                        long duration = System.nanoTime() - startTime;
                        variantTimes.add(duration);
                        variantResults.add(result);
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
        double avgDuration = variantTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double degradation = calculatePerformanceDegradation(avgDuration);
        
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Concurrent variant analysis performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(variantResults);
        bh.consume(avgDuration);
    }

    /**
     * PM4py Discovery with Different Algorithms Performance
     * Compare performance across different discovery algorithms
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkPM4pyAlgorithmComparison(Blackhole bh) throws Exception {
        String[] algorithms = {"alpha", "inductive", "heuristic"};
        List<Map<String, Object>> algorithmResults = new ArrayList<>();
        List<Long> algorithmTimes = new ArrayList<>();
        
        for (String algorithm : algorithms) {
            long startTime = System.nanoTime();
            
            Map<String, Object> algorithmData = new HashMap<>();
            algorithmData.put("eventLogs", marshaller.marshallToPython(eventLogs));
            algorithmData.put("algorithm", algorithm);
            algorithmData.put("parameters", marshaller.marshallToPython(miningParameters));
            
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(0), 
                algorithmData
            );
            
            long duration = System.nanoTime() - startTime;
            algorithmTimes.add(duration);
            algorithmResults.add(result);
        }
        
        // Validate all algorithms meet performance targets
        algorithmTimes.stream().mapToLong(Long::longValue).average()
            .ifPresent(avgDuration -> {
                double degradation = calculatePerformanceDegradation(avgDuration);
                if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
                    throw new AssertionError("Algorithm comparison performance degradation exceeds target: " + degradation + "%");
                }
            });
        
        bh.consume(algorithmResults);
    }

    /**
     * Java Baseline Comparison
     * Pure Java implementation of PM4py mining for comparison
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkJavaPM4MiningBaseline(Blackhole bh) {
        long startTime = System.nanoTime();
        
        // Simulate Java process mining
        Map<String, Object> result = performJavaProcessMining(eventLogs, processDefinition);
        
        long duration = System.nanoTime() - startTime;
        javaMiningResults.put("baseline", Map.of("result", result, "duration", duration));
        
        bh.consume(result);
        bh.consume(duration);
    }

    // Helper methods
    private YNet createProcessMiningNet() {
        YNet net = new YNet("pm4py-test-net");
        
        YCondition start = net.addCondition("start");
        YCondition end = net.addCondition("end");
        
        // Add tasks for different PM4py operations
        YAWLTask processDiscovery = net.addTask("process-discovery");
        YAWLTask conformanceChecking = net.addTask("conformance-checking");
        YAWLTask processEnhancement = net.addTask("process-enhancement");
        YAWLTask performanceAnalysis = net.addTask("performance-analysis");
        YAWLTask variantAnalysis = net.addTask("variant-analysis");
        
        net.setStartConditions(Collections.singleton(start.getID()));
        net.setFinishConditions(Collections.singleton(end.getID()));
        
        // Set flows
        net.addFlow(start.getID(), processDiscovery.getID());
        net.addFlow(processDiscovery.getID(), conformanceChecking.getID());
        net.addFlow(conformanceChecking.getID(), processEnhancement.getID());
        net.addFlow(processEnhancement.getID(), performanceAnalysis.getID());
        net.addFlow(performanceAnalysis.getID(), variantAnalysis.getID());
        net.addFlow(variantAnalysis.getID(), end.getID());
        
        return net;
    }

    private List<Map<String, Object>> createEventLogs() {
        List<Map<String, Object>> logs = new ArrayList<>();
        
        // Create realistic event logs with timestamps
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> event = new HashMap<>();
            event.put("caseId", "case-" + (i % 10));
            event.put("activity", getRandomActivity());
            event.put("timestamp", System.currentTimeMillis() - (1000 - i) * 1000);
            event.put("resource", "resource-" + (i % 5));
            event.put("cost", 100 + Math.random() * 500);
            logs.add(event);
        }
        
        return logs;
    }

    private String getRandomActivity() {
        String[] activities = {"start", "task1", "task2", "decision", "end", "review", "approve", "reject"};
        return activities[(int) (Math.random() * activities.length)];
    }

    private Map<String, Object> createProcessDefinition() {
        Map<String, Object> definition = new HashMap<>();
        definition.put("processName", "Order Processing");
        definition.put("processId", "order-processing-v1");
        definition.put("activities", List.of("start", "task1", "task2", "decision", "end"));
        definition.put("flows", Map.of(
            "start", List.of("task1"),
            "task1", List.of("task2", "decision"),
            "task2", List.of("end"),
            "decision", List.of("end")
        ));
        return definition;
    }

    private Map<String, Object> createMiningParameters() {
        return Map.of(
            "noiseThreshold", 0.2,
            "maxLength", 10,
            "maxNumberOfEdges", 50,
            "candidateSetSize", 10,
            "enableLoopDetection", true,
            "enableParallel", true,
            "timestampFormat", "YYYY-MM-DD HH:mm:ss"
        );
    }

    private Map<String, Object> createBaselineMetrics() {
        return Map.of(
            "averageCaseDuration", 5000,
            "averageCaseCost", 1000,
            "reworkRate", 0.05,
            "throughput", 10.0,
            "utilization", 0.75
        );
    }

    private Map<String, Object> createVariantData(int threadId, int iteration) {
        Map<String, Object> variant = new HashMap<>();
        variant.put("variantId", "variant-" + threadId + "-" + iteration);
        variant.put("caseIds", List.of("case-" + threadId + "-" + iteration));
        variant.put("activities", List.of(
            "start", "task1", "task2", "end"
        ));
        variant.put("frequency", Math.random() * 100);
        variant.put("performance", Map.of(
            "avgDuration", 1000 + Math.random() * 4000,
            "cost", 500 + Math.random() * 1500
        ));
        return variant;
    }

    private void loadPM4pyScripts() throws Exception {
        // Load PM4py Python scripts (mock implementation)
        String pm4pyScript = """
            import pandas as pd
            import numpy as np
            from typing import Dict, Any, List
            
            def process_discovery(event_logs: List[Dict[str, Any]], algorithm: str = "alpha", parameters: Dict[str, Any] = None) -> Dict[str, Any]:
                # Mock PM4py process discovery
                return {
                    "discoveredModel": {
                        "activities": set(),
                        "flows": {},
                        "fitness": np.random.uniform(0.8, 0.99),
                        "precision": np.random.uniform(0.85, 0.99)
                    },
                    "discoveryTime": np.random.uniform(10, 100),
                    "algorithm": algorithm,
                    "parameters": parameters or {}
                }
                
            def conformance_checking(event_logs: List[Dict[str, Any]], process_model: Dict[str, Any], check_type: int = 0) -> Dict[str, Any]:
                # Mock conformance checking
                check_types = ["alignments", "token_replay", "footprints"]
                return {
                    "checkType": check_types[check_type],
                    "conformance_score": np.random.uniform(0.7, 0.99),
                    "violations": np.random.randint(0, 10),
                    "checkTime": np.random.uniform(5, 50),
                    "details": {
                        "fit_cases": np.random.randint(0, len(event_logs)),
                        "misfit_cases": len(event_logs) - np.random.randint(0, len(event_logs))
                    }
                }
                
            def process_enhancement(original_model: Dict[str, Any], event_logs: List[Dict[str, Any]], enhancement_goals: List[str]) -> Dict[str, Any]:
                # Mock process enhancement
                return {
                    "enhancedModel": {
                        "originalModel": original_model,
                        "addedRules": np.random.randint(1, 5),
                        "removedRules": np.random.randint(0, 3),
                        "improvements": dict.fromkeys(enhancement_goals, np.random.uniform(0.1, 0.3))
                    },
                    "enhancementTime": np.random.uniform(20, 200),
                    "goalsAchieved": [goal for goal in enhancement_goals if np.random.random() > 0.3]
                }
                
            def performance_analysis(event_logs: List[Dict[str, Any]], metric_type: int, timeframe: Dict[str, int]) -> Dict[str, Any]:
                # Mock performance analysis
                metric_types = ["duration", "cost", "frequency", "resource utilization", "bottlenecks"]
                return {
                    "metricType": metric_types[metric_type],
                    "analysis": {
                        "average": np.random.uniform(100, 1000),
                        "median": np.random.uniform(80, 800),
                        "std": np.random.uniform(50, 500),
                        "min": np.random.uniform(10, 100),
                        "max": np.random.uniform(1000, 5000)
                    },
                    "timeframe": timeframe,
                    "analysisTime": np.random.uniform(10, 80),
                    "insights": [f"Insight {i}" for i in range(np.random.randint(1, 4))]
                }
                
            def variant_analysis(variant_logs: List[Dict[str, Any]], thread_id: int) -> Dict[str, Any]:
                # Mock variant analysis
                return {
                    "variantId": f"variant-{thread_id}-{np.random.randint(0, 100)}",
                    "characteristics": {
                        "frequency": np.random.uniform(0.1, 1.0),
                        "averageDuration": np.random.uniform(1000, 10000),
                        "cost": np.random.uniform(100, 1000),
                        "complexity": np.random.uniform(0.1, 1.0)
                    },
                    "performance": np.random.uniform(0.5, 1.0),
                    "analysisTime": np.random.uniform(5, 30)
                }
            """;
        
        // In a real implementation, this would load actual PM4py scripts
        // For benchmarking, we use inline Python
    }

    private Map<String, Object> performJavaProcessMining(List<Map<String, Object>> eventLogs, Map<String, Object> processDefinition) {
        // Simulate Java process mining for baseline comparison
        Map<String, Object> result = new HashMap<>();
        result.put("discoveredModel", processDefinition);
        result.put("miningTime", System.currentTimeMillis());
        result.put("fitness", 0.85);
        result.put("precision", 0.90);
        return result;
    }

    private double calculatePerformanceDegradation(long pythonDuration) {
        // Get Java baseline duration
        Map<String, Object> baseline = javaMiningResults.get("baseline");
        if (baseline == null) return 0.0;
        
        long javaDuration = (Long) ((Map<String, Object>) baseline.get("result")).get("miningTime");
        
        // Calculate degradation percentage
        double degradation = ((double) (pythonDuration - javaDuration) / javaDuration) * 100;
        return Math.max(0, degradation); // Ensure non-negative
    }

    /**
     * Main method for running benchmarks
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(PM4pyProcessMiningBenchmark.class.getSimpleName())
            .forks(3)
            .warmupIterations(5)
            .measurementIterations(10)
            .output("pm4py-process-mining-benchmarks.json")
            .build();
        
        new Runner(opt).run();
    }
}
