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
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.graalpy.execution.YawlGraalPyEngine;
import org.yawlfoundation.yawl.graalpy.types.YawlTypeMarshaller;
import org.yawlfoundation.yawl.graalpy.types.YawlTypeMarshallerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JMH benchmark suite for YAWL GraalPy performance comparison.
 * Establishes performance baselines and tracks Java-Python performance deltas.
 *
 * Target: ≤20% degradation from Java baseline
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Benchmark)
public class PerformanceBaselinesTest {

    private YawlTypeMarshaller marshaller;
    private YawlGraalPyEngine graalPyEngine;
    private YNet testNet;
    private Map<String, Object> testData;
    private List<YAWLTask> taskList;
    
    // Java baseline components
    private YNet javaBaselineNet;
    private Map<String, Object> javaBaselineData;

    @Setup
    public void setup() throws Exception {
        // Initialize GraalPy marshaller and engine
        marshaller = YawlTypeMarshallerFactory.createMarshaller();
        graalPyEngine = new YawlGraalPyEngine();
        
        // Create test YAWL net structure
        testNet = createTestNet();
        javaBaselineNet = createTestNet();
        
        // Prepare test data
        testData = createTestData();
        javaBaselineData = createTestData();
        
        // Prepare task list for benchmarks
        taskList = new ArrayList<>(testNet.getTasks());
        
        // Warm up the engine
        graalPyEngine.initialize();
    }

    @TearDown
    public void tearDown() {
        if (graalPyEngine != null) {
            graalPyEngine.shutdown();
        }
    }

    /**
     * Benchmark Type Marshalling Overhead
     * Measures the cost of converting between Java and Python types.
     */
    @Benchmark
    public void benchmarkTypeMarshalling(Blackhole bh) throws Exception {
        // Test complex object marshalling
        Map<String, Object> pythonData = marshaller.marshallToPython(testData);
        Map<String, Object> javaData = marshaller.unmarshallFromPython(pythonData);
        
        bh.consume(javaData);
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(4)
    public void benchmarkTypeMarshallingConcurrent(Blackhole bh) throws Exception {
        // Concurrent marshalling test
        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Map<String, Object> pythonData = marshaller.marshallToPython(testData);
            Map<String, Object> javaData = marshaller.unmarshallFromPython(pythonData);
            results.add(javaData);
        }
        bh.consume(results);
    }

    /**
     * Execution Latency Benchmarks
     * Measures single operation execution times.
     */
    @Benchmark
    public void benchmarkExecutionLatency(Blackhole bh) throws Exception {
        // Execute a simple workflow task
        long startTime = System.nanoTime();
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(0), 
            testData
        );
        long duration = System.nanoTime() - startTime;
        
        bh.consume(result);
        bh.consume(duration);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkExecutionLatencyComplex(Blackhole bh) throws Exception {
        // Execute complex workflow with multiple tasks
        long startTime = System.nanoTime();
        
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(0), 
            testData
        );
        
        for (int i = 1; i < taskList.size(); i++) {
            result = graalPyEngine.executeTask(
                taskList.get(i), 
                result
            );
        }
        
        long duration = System.nanoTime() - startTime;
        bh.consume(duration);
    }

    /**
     * Throughput Benchmarks
     * Measures operations per second.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkThroughput(Blackhole bh) throws Exception {
        // Execute many simple operations
        for (int i = 0; i < 100; i++) {
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(0), 
                testData
            );
            bh.consume(result);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Threads(8)
    public void benchmarkThroughputConcurrent(Blackhole bh) throws Exception {
        // Concurrent throughput test
        List<Thread> threads = new ArrayList<>();
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());
        
        for (int t = 0; t < 8; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 50; i++) {
                    try {
                        Map<String, Object> result = graalPyEngine.executeTask(
                            taskList.get(i % taskList.size()), 
                            testData
                        );
                        results.add(result);
                    } catch (Exception e) {
                        // Consume exception
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        bh.consume(results);
    }

    /**
     * Memory Usage Tracking
     * Measures memory consumption patterns.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void benchmarkMemoryUsage(Blackhole bh) throws Exception {
        // Track memory during marshalling operations
        Runtime runtime = Runtime.getRuntime();
        
        long before = runtime.totalMemory() - runtime.freeMemory();
        
        // Perform multiple marshalling operations
        for (int i = 0; i < 100; i++) {
            Map<String, Object> pythonData = marshaller.marshallToPython(testData);
            Map<String, Object> javaData = marshaller.unmarshallFromPython(pythonData);
            bh.consume(javaData);
        }
        
        long after = runtime.totalMemory() - runtime.freeMemory();
        long used = after - before;
        
        bh.consume(used);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void benchmarkMemoryUsageExecution(Blackhole bh) throws Exception {
        // Track memory during execution
        Runtime runtime = Runtime.getRuntime();
        
        long before = runtime.totalMemory() - runtime.freeMemory();
        
        // Execute multiple operations
        for (int i = 0; i < 50; i++) {
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(0), 
                testData
            );
            bh.consume(result);
        }
        
        long after = runtime.totalMemory() - runtime.freeMemory();
        long used = after - before;
        
        bh.consume(used);
    }

    /**
     * Concurrency Performance Benchmarks
     * Measures parallel execution performance.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(1)
    public void benchmarkConcurrencySequential(Blackhole bh) throws Exception {
        // Sequential execution baseline
        List<Map<String, Object>> results = new ArrayList<>();
        
        long startTime = System.nanoTime();
        for (int i = 0; i < taskList.size(); i++) {
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(i), 
                testData
            );
            results.add(result);
        }
        long duration = System.nanoTime() - startTime;
        
        bh.consume(results);
        bh.consume(duration);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(4)
    public void benchmarkConcurrencyParallel(Blackhole bh) throws Exception {
        // Parallel execution
        List<Thread> threads = new ArrayList<>();
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        long startTime = System.nanoTime();
        
        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < taskList.size() / 4; i++) {
                        Map<String, Object> result = graalPyEngine.executeTask(
                            taskList.get((threadId * taskList.size() / 4) + i), 
                            testData
                        );
                        results.add(result);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        long duration = System.nanoTime() - startTime;
        
        bh.consume(results);
        bh.consume(exceptions);
        bh.consume(duration);
    }

    /**
     * Java Baseline Comparison Benchmarks
     * Pure Java implementations for comparison.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkJavaBaselineExecution(Blackhole bh) throws Exception {
        // Pure Java execution baseline
        Map<String, Object> result = javaBaselineData;
        
        long startTime = System.nanoTime();
        for (int i = 0; i < taskList.size(); i++) {
            result = executeJavaTask(taskList.get(i), result);
        }
        long duration = System.nanoTime() - startTime;
        
        bh.consume(result);
        bh.consume(duration);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkJavaBaselineThroughput(Blackhole bh) throws Exception {
        // Pure Java throughput baseline
        for (int i = 0; i < 100; i++) {
            Map<String, Object> result = executeJavaTask(taskList.get(0), javaBaselineData);
            bh.consume(result);
        }
    }

    // Helper methods for test data generation
    private YNet createTestNet() {
        YNet net = new YNet("test-net");
        
        // Create a simple workflow net
        YCondition start = net.addCondition("start");
        YCondition end = net.addCondition("end");
        YAWLTask task1 = net.addTask("task1");
        YAWLTask task2 = net.addTask("task2");
        
        // Set net elements
        net.setStartConditions(Collections.singleton(start.getID()));
        net.setFinishConditions(Collections.singleton(end.getID()));
        
        // Set flows
        net.addFlow(start.getID(), task1.getID());
        net.addFlow(task1.getID(), task2.getID());
        net.addFlow(task2.getID(), end.getID());
        
        return net;
    }

    private Map<String, Object> createTestData() {
        Map<String, Object> data = new HashMap<>();
        
        // Create realistic workflow data
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("caseId", "case-" + UUID.randomUUID());
        caseData.put("startTime", System.currentTimeMillis());
        caseData.put("status", "running");
        
        // Add process data
        Map<String, Object> processData = new HashMap<>();
        processData.put("processId", "test-process");
        processData.put("version", "1.0");
        processData.put("variables", createProcessVariables());
        
        // Add input data
        Map<String, Object> inputData = new HashMap<>();
        inputData.put("customerName", "Test Customer");
        inputData.put("orderId", "order-" + UUID.randomUUID());
        inputData.put("amount", 1000.50);
        inputData.put("items", createItemsList());
        
        data.put("caseData", caseData);
        data.put("processData", processData);
        data.put("inputData", inputData);
        data.put("timestamp", System.currentTimeMillis());
        
        return data;
    }

    private List<Map<String, Object>> createItemsList() {
        List<Map<String, Object>> items = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("itemId", "item-" + i);
            item.put("name", "Test Item " + i);
            item.put("quantity", i);
            item.put("price", 100.0 * i);
            items.add(item);
        }
        
        return items;
    }

    private Map<String, Object> createProcessVariables() {
        Map<String, Object> variables = new HashMap<>();
        
        variables.put("deadline", new Date(System.currentTimeMillis() + 86400000));
        variables.put("priority", "high");
        variables.put("assignee", "team@example.com");
        variables.put("notes", "Test case for performance benchmark");
        
        return variables;
    }

    // Pure Java task execution for baseline comparison
    private Map<String, Object> executeJavaTask(YAWLTask task, Map<String, Object> inputData) {
        // Simulate Java task execution
        Map<String, Object> result = new HashMap<>(inputData);
        
        // Simple task logic
        long currentTime = System.currentTimeMillis();
        result.put("timestamp", currentTime);
        
        // Process input data
        if (inputData.containsKey("inputData")) {
            Map<String, Object> input = (Map<String, Object>) inputData.get("inputData");
            Map<String, Object> output = new HashMap<>(input);
            
            // Apply task transformations
            if (input.containsKey("amount")) {
                double amount = (double) input.get("amount");
                output.put("processedAmount", amount * 1.1); // 10% increase
            }
            
            result.put("outputData", output);
        }
        
        return result;
    }

    /**
     * Main method for running benchmarks
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(PerformanceBaselinesTest.class.getSimpleName())
            .forks(3)
            .warmupIterations(5)
            .measurementIterations(10)
            .output("performance-benchmarks.json")
            .build();
        
        new Runner(opt).run();
    }
}
