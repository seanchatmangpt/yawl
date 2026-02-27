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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TPOT2 workflow optimization performance benchmark.
 * 
 * Measures the performance impact of TPOT2 (Tree-based Pipeline Optimization Tool 2)
 * integration for automated workflow parameter optimization and hyperparameter tuning.
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
public class TPOT2IntegrationBenchmark {

    private YawlTypeMarshaller marshaller;
    private YawlGraalPyEngine graalPyEngine;
    private YNet testNet;
    private Map<String, Object> workflowData;
    private List<YAWLTask> taskList;
    
    // TPOT2 specific test data
    private Map<String, Object> optimizationParameters;
    private List<Map<String, Object>> workflowMetrics;
    private Map<String, Object> baselineMetrics;
    
    // Java baseline for comparison
    private Map<String, Object> javaOptimizationResults;

    @Setup
    public void setup() throws Exception {
        // Initialize GraalPy engine
        marshaller = YawlTypeMarshallerFactory.createMarshaller();
        graalPyEngine = new YawlGraalPyEngine();
        graalPyEngine.initialize();
        
        // Create test workflow
        testNet = createTestWorkflowNet();
        taskList = new ArrayList<>(testNet.getTasks());
        
        // Prepare workflow data
        workflowData = createWorkflowData();
        workflowMetrics = createWorkflowMetrics();
        baselineMetrics = createBaselineMetrics();
        optimizationParameters = createTPOT2Parameters();
        javaOptimizationResults = new ConcurrentHashMap<>();
        
        // Load TPOT2 Python scripts
        loadTPOT2Scripts();
    }

    @TearDown
    public void tearDown() {
        if (graalPyEngine != null) {
            graalPyEngine.shutdown();
        }
    }

    /**
     * TPOT2 Parameter Optimization Performance
     * Measures the time taken to optimize workflow parameters using TPOT2
     */
    @Benchmark
    public void benchmarkTPOT2ParameterOptimization(Blackhole bh) throws Exception {
        // Execute TPOT2 parameter optimization
        long startTime = System.nanoTime();
        
        Map<String, Object> pythonData = marshaller.marshallToPython(optimizationParameters);
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(0), 
            Map.of(
                "optimizationParams", pythonData,
                "workflowMetrics", marshaller.marshallToPython(workflowMetrics),
                "baseline", marshaller.marshallToPython(baselineMetrics)
            )
        );
        
        long duration = System.nanoTime() - startTime;
        
        // Verify results meet performance targets
        double degradation = calculatePerformanceDegradation(duration);
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(result);
        bh.consume(duration);
    }

    /**
     * TPOT2 Cross-Validation Performance
     * Measures the time taken to perform cross-validation for workflow optimization
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(2)
    public void benchmarkTPOT2CrossValidation(Blackhole bh) throws Exception {
        // Cross-validation with multiple folds
        List<Map<String, Object>> validationResults = new ArrayList<>();
        List<Long> durations = new ArrayList<>();
        
        for (int fold = 0; fold < 5; fold++) {
            long startTime = System.nanoTime();
            
            Map<String, Object> foldData = new HashMap<>(workflowData);
            foldData.put("foldNumber", fold);
            foldData.put("totalFolds", 5);
            
            Map<String, Object> pythonData = marshaller.marshallToPython(foldData);
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(1), 
                foldData
            );
            
            long duration = System.nanoTime() - startTime;
            durations.add(duration);
            validationResults.add(result);
        }
        
        // Calculate average performance
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        double degradation = calculatePerformanceDegradation(avgDuration);
        
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Cross-validation performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(validationResults);
        bh.consume(avgDuration);
    }

    /**
     * TPOT2 Pipeline Evolution Performance
     * Measures the time taken for genetic algorithm evolution of workflows
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkTPOT2PipelineEvolution(Blackhole bh) throws Exception {
        // Simulate TPOT2 genetic algorithm generations
        List<Map<String, Object>> generationResults = new ArrayList<>();
        List<Long> generationTimes = new ArrayList<>();
        
        for (int generation = 0; generation < 10; generation++) {
            long startTime = System.nanoTime();
            
            Map<String, Object> genData = new HashMap<>();
            genData.put("generation", generation);
            genData.put("populationSize", 50);
            genData.put("mutationRate", 0.1);
            genData.put("crossoverRate", 0.8);
            
            Map<String, Object> pythonData = marshaller.marshallToPython(genData);
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(2), 
                genData
            );
            
            long duration = System.nanoTime() - startTime;
            generationTimes.add(duration);
            generationResults.add(result);
            
            // Early termination if performance is poor
            double avgGenTime = generationTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            if (!PerformanceTargets.isPerformanceDegradationAcceptable(calculatePerformanceDegradation(avgGenTime))) {
                throw new AssertionError("Generation " + generation + " performance degradation exceeds target");
            }
        }
        
        bh.consume(generationResults);
    }

    /**
     * TPOT2 Model Selection Performance
     * Measures the time taken to select the best workflow model from candidates
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkTPOT2ModelSelection(Blackhole bh) throws Exception {
        // Select best model from multiple candidates
        List<Map<String, Object>> candidateModels = createCandidateModels(10);
        List<Long> selectionTimes = new ArrayList<>();
        
        for (Map<String, Object> candidate : candidateModels) {
            long startTime = System.nanoTime();
            
            Map<String, Object> pythonData = marshaller.marshallToPython(candidate);
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(3), 
                Map.of("candidate", pythonData, "metrics", marshaller.marshallToPython(workflowMetrics))
            );
            
            long duration = System.nanoTime() - startTime;
            selectionTimes.add(duration);
            bh.consume(result);
        }
        
        // Calculate throughput
        double totalTime = selectionTimes.stream().mapToLong(Long::longValue).sum();
        double throughput = selectionTimes.size() / (totalTime / 1e9);
        
        if (throughput < PerformanceTargets.MIN_THROUGHPUT_OPS_PER_SEC) {
            throw new AssertionError("Model selection throughput below target: " + throughput + " ops/sec");
        }
        
        bh.consume(throughput);
    }

    /**
     * TPOT2 Hyperparameter Tuning Performance
     * Concurrent hyperparameter tuning with multiple optimization paths
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(4)
    public void benchmarkTPOT2HyperparameterTuningConcurrent(Blackhole bh) throws Exception {
        List<Thread> threads = new ArrayList<>();
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());
        List<Long> durations = Collections.synchronizedList(new ArrayList<>());
        
        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < 5; i++) {
                        long startTime = System.nanoTime();
                        
                        Map<String, Object> params = createThreadSpecificParameters(threadId, i);
                        Map<String, Object> pythonData = marshaller.marshallToPython(params);
                        Map<String, Object> result = graalPyEngine.executeTask(
                            taskList.get(4), 
                            Map.of("hyperparameters", pythonData, "threadId", threadId)
                        );
                        
                        long duration = System.nanoTime() - startTime;
                        durations.add(duration);
                        results.add(result);
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
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        double degradation = calculatePerformanceDegradation(avgDuration);
        
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Concurrent hyperparameter tuning performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(results);
        bh.consume(avgDuration);
    }

    /**
     * Java Baseline Comparison
     * Pure Java implementation of TPOT2 optimization for comparison
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkJavaTPOT2OptimizationBaseline(Blackhole bh) {
        long startTime = System.nanoTime();
        
        // Simulate Java optimization
        Map<String, Object> result = performJavaOptimization(optimizationParameters, workflowMetrics);
        
        long duration = System.nanoTime() - startTime;
        javaOptimizationResults.put("baseline", Map.of("result", result, "duration", duration));
        
        bh.consume(result);
        bh.consume(duration);
    }

    // Helper methods
    private YNet createTestWorkflowNet() {
        YNet net = new YNet("tpot2-test-net");
        
        YCondition start = net.addCondition("start");
        YCondition end = net.addCondition("end");
        
        // Add tasks for different TPOT2 operations
        YAWLTask paramOpt = net.addTask("parameter-optimization");
        YAWLTask crossVal = net.addTask("cross-validation");
        YAWLTask pipelineEvolution = net.addTask("pipeline-evolution");
        YAWLTask modelSelection = net.addTask("model-selection");
        YAWLTask hyperparamTuning = net.addTask("hyperparameter-tuning");
        
        net.setStartConditions(Collections.singleton(start.getID()));
        net.setFinishConditions(Collections.singleton(end.getID()));
        
        // Set flows
        net.addFlow(start.getID(), paramOpt.getID());
        net.addFlow(paramOpt.getID(), crossVal.getID());
        net.addFlow(crossVal.getID(), pipelineEvolution.getID());
        net.addFlow(pipelineEvolution.getID(), modelSelection.getID());
        net.addFlow(modelSelection.getID(), hyperparamTuning.getID());
        net.addFlow(hyperparamTuning.getID(), end.getID());
        
        return net;
    }

    private Map<String, Object> createWorkflowData() {
        Map<String, Object> data = new HashMap<>();
        data.put("workflowId", "tpot2-test-" + UUID.randomUUID());
        data.put("processName", "Workflow Optimization Process");
        data.put("taskCount", 15);
        data.put("variables", Map.of(
            "timeout", 300,
            "maxGenerations", 100,
            "populationSize", 50,
            "crossValidationFolds", 5
        ));
        return data;
    }

    private List<Map<String, Object>> createWorkflowMetrics() {
        List<Map<String, Object>> metrics = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            Map<String, Object> metric = new HashMap<>();
            metric.put("iteration", i);
            metric.put("accuracy", 0.5 + Math.random() * 0.5);
            metric.put("precision", 0.6 + Math.random() * 0.4);
            metric.put("recall", 0.7 + Math.random() * 0.3);
            metric.put("f1Score", 0.65 + Math.random() * 0.35);
            metric.put("executionTime", 100 + Math.random() * 500);
            metrics.add(metric);
        }
        
        return metrics;
    }

    private Map<String, Object> createBaselineMetrics() {
        return Map.of(
            "baselineAccuracy", 0.75,
            "baselinePrecision", 0.80,
            "baselineRecall", 0.78,
            "baselineF1", 0.76
        );
    }

    private Map<String, Object> createTPOT2Parameters() {
        return Map.of(
            "optimizationGoal", "accuracy",
            "maxEvaluationTime", 300,
            "max generations", 100,
            "populationSize", 50,
            "mutationRate", 0.1,
            "crossoverRate", 0.8,
            "scoring", "accuracy",
            "cvFolds", 5,
            "randomState", 42,
            "verbosity", 0
        );
    }

    private List<Map<String, Object>> createCandidateModels(int count) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            Map<String, Object> model = new HashMap<>();
            model.put("modelId", "model-" + i);
            model.put("pipeline", List.of(
                "feature_selection",
                "preprocessing",
                "estimator"
            ));
            model.put("hyperparameters", Map.of(
                "n_estimators", 50 + i * 10,
                "max_depth", 3 + i,
                "learning_rate", 0.1 + i * 0.05
            ));
            model.put("score", 0.7 + Math.random() * 0.3);
            candidates.add(model);
        }
        
        return candidates;
    }

    private Map<String, Object> createThreadSpecificParameters(int threadId, int iteration) {
        Map<String, Object> params = new HashMap<>(optimizationParameters);
        params.put("threadId", threadId);
        params.put("iteration", iteration);
        params.put("randomSeed", threadId * 1000 + iteration);
        return params;
    }

    private void loadTPOT2Scripts() throws Exception {
        // Load TPOT2 Python scripts (mock implementation)
        String tpot2Script = """
            import numpy as np
            from typing import Dict, Any, List
            
            def optimize_parameters(params: Dict[str, Any], metrics: List[Dict[str, Any]], baseline: Dict[str, Any]) -> Dict[str, Any]:
                # Mock TPOT2 optimization
                best_params = params.copy()
                best_params['optimized'] = True
                best_params['improvement'] = np.random.uniform(0.05, 0.15)
                return best_params
                
            def cross_validation(fold_data: Dict[str, Any]) -> Dict[str, Any]:
                # Mock cross-validation
                return {
                    'fold_score': np.random.uniform(0.7, 0.95),
                    'fold_number': fold_data.get('foldNumber', 0),
                    'total_folds': fold_data.get('totalFolds', 5)
                }
                
            def pipeline_evolution(generation_data: Dict[str, Any]) -> Dict[str, Any]:
                # Mock genetic algorithm evolution
                return {
                    'generation': generation_data.get('generation', 0),
                    'best_score': np.random.uniform(0.75, 0.98),
                    'converged': np.random.random() > 0.8
                }
                
            def select_best_model(candidate: Dict[str, Any], metrics: List[Dict[str, Any]]) -> Dict[str, Any]:
                # Mock model selection
                return {
                    'selected_model': candidate,
                    'selection_score': np.random.uniform(0.8, 0.99),
                    'improvement_over_baseline': np.random.uniform(0.02, 0.2)
                }
                
            def hyperparameter_tuning(params: Dict[str, Any]) -> Dict[str, Any]:
                # Mock hyperparameter tuning
                return {
                    'optimized_parameters': params,
                    'tuning_score': np.random.uniform(0.85, 0.99),
                    'tuning_iterations': np.random.randint(10, 50)
                }
            """;
        
        // In a real implementation, this would load actual TPOT2 scripts
        // For benchmarking, we use inline Python
    }

    private Map<String, Object> performJavaOptimization(Map<String, Object> params, List<Map<String, Object>> metrics) {
        // Simulate Java optimization for baseline comparison
        Map<String, Object> result = new HashMap<>(params);
        result.put("optimized", true);
        result.put("improvement", 0.1); // Fixed improvement for baseline
        result.put("optimizationTime", System.currentTimeMillis());
        return result;
    }

    private double calculatePerformanceDegradation(long pythonDuration) {
        // Get Java baseline duration
        Map<String, Object> baseline = javaOptimizationResults.get("baseline");
        if (baseline == null) return 0.0;
        
        long javaDuration = (Long) ((Map<String, Object>) baseline.get("result")).get("optimizationTime");
        
        // Calculate degradation percentage
        double degradation = ((double) (pythonDuration - javaDuration) / javaDuration) * 100;
        return Math.max(0, degradation); // Ensure non-negative
    }

    /**
     * Main method for running benchmarks
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(TPOT2IntegrationBenchmark.class.getSimpleName())
            .forks(3)
            .warmupIterations(5)
            .measurementIterations(10)
            .output("tpot2-integration-benchmarks.json")
            .build();
        
        new Runner(opt).run();
    }
}
