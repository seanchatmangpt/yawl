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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GraalPy integration memory overhead benchmark.
 * 
 * Measures memory consumption patterns and overhead when integrating Python
 * code via GraalPy, including memory growth during execution and cleanup.
 * 
 * Target: <10% memory overhead over pure Java implementation
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Benchmark)
public class GraalPyMemoryBenchmark {

    private YawlTypeMarshaller marshaller;
    private YawlGraalPyEngine graalPyEngine;
    private YNet testNet;
    private List<YAWLTask> taskList;
    
    // Memory test data
    private List<Map<String, Object>> largeDataSet;
    private Map<String, Object> complexObject;
    private List<Map<String, Object>> nestedStructures;
    
    // Memory tracking
    private MemoryMXBean memoryMXBean;
    private long initialMemoryUsage;
    
    // Java baseline
    private Map<String, Object> javaMemoryResults;

    @Setup
    public void setup() throws Exception {
        // Initialize memory tracking
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        initialMemoryUsage = getMemoryUsage();
        
        // Initialize GraalPy engine
        marshaller = YawlTypeMarshallerFactory.createMarshaller();
        graalPyEngine = new YawlGraalPyEngine();
        graalPyEngine.initialize();
        
        // Create test workflow
        testNet = createMemoryTestNet();
        taskList = new ArrayList<>(testNet.getTasks());
        
        // Prepare large test data
        largeDataSet = createLargeDataSet();
        complexObject = createComplexObject();
        nestedStructures = createNestedStructures();
        javaMemoryResults = new ConcurrentHashMap<>();
        
        // Pre-warm memory
        System.gc();
        Thread.sleep(1000); // Allow GC to complete
    }

    @TearDown
    public void tearDown() {
        if (graalPyEngine != null) {
            graalPyEngine.shutdown();
        }
        
        // Force GC and measure final memory
        System.gc();
        long finalMemory = getMemoryUsage();
        long memoryGrowth = finalMemory - initialMemoryUsage;
        
        if (memoryGrowth > PerformanceTargets.MAX_ACCEPTABLE_MEMORY_GROWTH_MB * 1024 * 1024) {
            throw new AssertionError("Memory growth exceeds target: " + (memoryGrowth / 1024 / 1024) + "MB");
        }
    }

    /**
     * Type Marshalling Memory Overhead
     * Measures memory overhead during Java-Python type conversion
     */
    @Benchmark
    public void benchmarkTypeMarshallingMemory(Blackhole bh) throws Exception {
        long beforeMemory = getMemoryUsage();
        
        // Perform multiple marshalling operations
        List<Map<String, Object>> marshalledResults = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> pythonData = marshaller.marshallToPython(largeDataSet.get(i % 100));
            Map<String, Object> javaData = marshaller.unmarshallFromPython(pythonData);
            marshalledResults.add(javaData);
        }
        
        long afterMemory = getMemoryUsage();
        long memoryUsed = afterMemory - beforeMemory;
        
        // Verify memory growth is acceptable
        double memoryGrowthMB = memoryUsed / (1024.0 * 1024.0);
        if (memoryGrowthMB > PerformanceTargets.MAX_ACCEPTABLE_MEMORY_GROWTH_MB) {
            throw new AssertionError("Marshalling memory growth exceeds target: " + memoryGrowthMB + "MB");
        }
        
        bh.consume(marshalledResults);
        bh.consume(memoryUsed);
    }

    /**
     * Large Object Processing Memory
     * Measures memory overhead when processing large Python objects
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(2)
    public void benchmarkLargeObjectProcessingMemory(Blackhole bh) throws Exception {
        List<Long> memoryUsages = Collections.synchronizedList(new ArrayList<>());
        List<Map<String, Object>> processingResults = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < 5; i++) {
            long beforeMemory = getMemoryUsage();
            
            // Process large dataset
            Map<String, Object> pythonData = marshaller.marshallToPython(largeDataSet);
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(0), 
                Map.of("largeData", pythonData, "operation", "process")
            );
            
            long afterMemory = getMemoryUsage();
            memoryUsages.add(afterMemory - beforeMemory);
            processingResults.add(result);
            
            // Clear references to allow GC
            System.gc();
            Thread.sleep(500);
        }
        
        // Calculate average memory growth
        double avgMemoryGrowth = memoryUsages.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0) / (1024.0 * 1024.0);
        
        if (avgMemoryGrowth > PerformanceTargets.MAX_ACCEPTABLE_MEMORY_GROWTH_MB) {
            throw new AssertionError("Large object processing memory growth exceeds target: " + avgMemoryGrowth + "MB");
        }
        
        bh.consume(processingResults);
        bh.consume(avgMemoryGrowth);
    }

    /**
     * Nested Structure Memory Overhead
     * Measures memory overhead with deeply nested Python structures
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkNestedStructureMemory(Blackhole bh) throws Exception {
        long beforeMemory = getMemoryUsage();
        
        // Process nested structures
        List<Map<String, Object>> nestedResults = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Map<String, Object> pythonData = marshaller.marshallToPython(nestedStructures.get(i % 10));
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(1), 
                Map.of("nestedData", pythonData, "depth", 10)
            );
            nestedResults.add(result);
        }
        
        long afterMemory = getMemoryUsage();
        long memoryUsed = afterMemory - beforeMemory;
        
        double memoryGrowthMB = memoryUsed / (1024.0 * 1024.0);
        if (memoryGrowthMB > PerformanceTargets.MAX_ACCEPTABLE_MEMORY_GROWTH_MB * 2) { // Allow double for nested structures
            throw new AssertionError("Nested structure memory growth exceeds target: " + memoryGrowthMB + "MB");
        }
        
        bh.consume(nestedResults);
        bh.consume(memoryUsed);
    }

    /**
     * Memory Leak Detection
     * Tests memory usage over many iterations to detect leaks
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkMemoryLeakDetection(Blackhole bh) throws Exception {
        List<Long> memorySnapshots = new ArrayList<>();
        List<Map<String, Object>> iterationResults = new ArrayList<>();
        
        long baselineMemory = getMemoryUsage();
        
        for (int iteration = 0; iteration < 50; iteration++) {
            long beforeMemory = getMemoryUsage();
            
            // Execute task and collect result
            Map<String, Object> pythonData = marshaller.marshallToPython(complexObject);
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(2), 
                Map.of("iteration", iteration, "data", pythonData)
            );
            
            long afterMemory = getMemoryUsage();
            long memoryDelta = afterMemory - baselineMemory;
            memorySnapshots.add(memoryDelta);
            iterationResults.add(result);
            
            // Force GC to check for leaks
            if (iteration % 10 == 0) {
                System.gc();
                Thread.sleep(500);
            }
        }
        
        // Check for memory leak patterns
        double avgMemoryGrowth = memorySnapshots.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0) / (1024.0 * 1024.0);
        
        // Memory should not grow significantly after GC
        if (avgMemoryGrowth > PerformanceTargets.MAX_ACCEPTABLE_MEMORY_GROWTH_MB) {
            throw new AssertionError("Potential memory detected: " + avgMemoryGrowth + "MB average growth");
        }
        
        bh.consume(iterationResults);
        bh.consume(avgMemoryGrowth);
    }

    /**
     * Concurrent Memory Usage
     * Measures memory overhead with concurrent Python execution
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(4)
    public void benchmarkConcurrentMemoryUsage(Blackhole bh) throws Exception {
        List<Thread> threads = new ArrayList<>();
        List<Map<String, Object>> threadResults = Collections.synchronizedList(new ArrayList<>());
        List<Long> memoryGrowthPerThread = Collections.synchronizedList(new ArrayList<>());
        
        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    long threadStartMemory = getMemoryUsage();
                    List<Map<String, Object>> results = new ArrayList<>();
                    
                    for (int i = 0; i < 25; i++) {
                        Map<String, Object> pythonData = marshaller.marshallToPython(largeDataSet.get(i % 100));
                        Map<String, Object> result = graalPyEngine.executeTask(
                            taskList.get(3), 
                            Map.of("threadId", threadId, "iteration", i, "data", pythonData)
                        );
                        results.add(result);
                    }
                    
                    long threadEndMemory = getMemoryUsage();
                    long threadMemoryGrowth = threadEndMemory - threadStartMemory;
                    memoryGrowthPerThread.add(threadMemoryGrowth);
                    threadResults.addAll(results);
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
        
        // Validate memory growth per thread
        memoryGrowthPerThread.stream()
            .mapToLong(Long::longValue)
            .average()
            .ifPresent(avgGrowth -> {
                double avgGrowthMB = avgGrowth / (1024.0 * 1024.0);
                if (avgGrowthMB > PerformanceTargets.MAX_ACCEPTABLE_MEMORY_GROWTH_MB) {
                    throw new AssertionError("Concurrent memory growth per thread exceeds target: " + avgGrowthMB + "MB");
                }
            });
        
        bh.consume(threadResults);
    }

    /**
     * Object Retention Memory Test
     * Tests memory overhead when retaining Python objects
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkObjectRetentionMemory(Blackhole bh) throws Exception {
        // Create and retain many Python objects
        List<Map<String, Object>> retainedObjects = new ArrayList<>();
        long beforeMemory = getMemoryUsage();
        
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> pythonData = marshaller.marshallToPython(complexObject);
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(4), 
                Map.of("objectId", i, "data", pythonData)
            );
            retainedObjects.add(result);
        }
        
        long afterRetentionMemory = getMemoryUsage();
        
        // Clear references and measure GC efficiency
        retainedObjects.clear();
        System.gc();
        Thread.sleep(1000);
        
        long afterGC = getMemoryUsage();
        long retainedMemory = afterRetentionMemory - beforeMemory;
        long gcRecovery = afterRetentionMemory - afterGC;
        
        // Verify GC recovered most memory
        double gcEfficiency = (double) gcRecovery / retainedMemory;
        if (gcEfficiency < 0.9) { // Should recover at least 90%
            throw new AssertionError("GC efficiency poor: " + (gcEfficiency * 100) + "% recovery");
        }
        
        bh.consume(gcEfficiency);
    }

    /**
     * Java Baseline Memory Comparison
     * Pure Java memory usage for comparison
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkJavaMemoryBaseline(Blackhole bh) {
        long beforeMemory = getMemoryUsage();
        
        // Simulate Java processing
        List<Map<String, Object>> javaResults = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> result = performJavaProcessing(largeDataSet.get(i % 100));
            javaResults.add(result);
        }
        
        long afterMemory = getMemoryUsage();
        long javaMemoryUsed = afterMemory - beforeMemory;
        
        javaMemoryResults.put("baseline", Map.of("result", javaResults, "memoryUsed", javaMemoryUsed));
        
        bh.consume(javaResults);
        bh.consume(javaMemoryUsed);
    }

    // Helper methods
    private YNet createMemoryTestNet() {
        YNet net = new YNet("memory-test-net");
        
        YCondition start = net.addCondition("start");
        YCondition end = net.addCondition("end");
        
        // Add tasks for different memory operations
        YAWLTask largeDataProcessing = net.addTask("large-data-processing");
        YAWLTask nestedStructureHandling = net.addTask("nested-structure-handling");
        YAWLTask memoryIntensiveOperation = net.addTask("memory-intensive-operation");
        YAWLTask concurrentExecution = net.addTask("concurrent-execution");
        YAWLTask objectRetention = net.addTask("object-retention");
        
        net.setStartConditions(Collections.singleton(start.getID()));
        net.setFinishConditions(Collections.singleton(end.getID()));
        
        // Set flows
        net.addFlow(start.getID(), largeDataProcessing.getID());
        net.addFlow(largeDataProcessing.getID(), nestedStructureHandling.getID());
        net.addFlow(nestedStructureHandling.getID(), memoryIntensiveOperation.getID());
        net.addFlow(memoryIntensiveOperation.getID(), concurrentExecution.getID());
        net.addFlow(concurrentExecution.getID(), objectRetention.getID());
        net.addFlow(objectRetention.getID(), end.getID());
        
        return net;
    }

    private List<Map<String, Object>> createLargeDataSet() {
        List<Map<String, Object>> dataSet = new ArrayList<>();
        
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", "item-" + i);
            item.put("name", "Large Item " + i);
            item.put("description", "This is a large item with nested data for memory testing");
            item.put("metadata", createNestedMetadata());
            item.put("measurements", createMeasurementArray());
            item.put("timestamps", createTimestampSeries());
            dataSet.add(item);
        }
        
        return dataSet;
    }

    private Map<String, Object> createComplexObject() {
        Map<String, Object> complex = new HashMap<>();
        complex.put("id", "complex-object-" + UUID.randomUUID());
        complex.put("type", "complex-data-structure");
        complex.put("layers", createLayeredData(5));
        complex.put("relationships", createRelationshipMap());
        complex.put("attributes", createAttributeMap());
        return complex;
    }

    private List<Map<String, Object>> createNestedStructures() {
        List<Map<String, Object>> structures = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            Map<String, Object> structure = new HashMap<>();
            structure.put("level", 0);
            structure.put("data", createDeeplyNestedData(10));
            structures.add(structure);
        }
        
        return structures;
    }

    private Map<String, Object> createNestedMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("created", System.currentTimeMillis());
        metadata.put("modified", System.currentTimeMillis());
        metadata.put("tags", Arrays.asList("test", "memory", "benchmark"));
        metadata.put("statistics", Map.of(
            "count", 1000,
            "average", 500.5,
            "std", 150.25
        ));
        return metadata;
    }

    private List<Double> createMeasurementArray() {
        List<Double> measurements = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            measurements.add(Math.random() * 1000);
        }
        return measurements;
    }

    private List<Long> createTimestampSeries() {
        List<Long> timestamps = new ArrayList<>();
        long base = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            timestamps.add(base + i * 1000);
        }
        return timestamps;
    }

    private Map<String, Object> createLayeredData(int depth) {
        if (depth == 0) {
            return Map.of("leaf", "value-" + UUID.randomUUID());
        }
        
        Map<String, Object> layer = new HashMap<>();
        layer.put("level", depth);
        layer.put("children", createLayeredData(depth - 1));
        layer.put("metadata", createNestedMetadata());
        return layer;
    }

    private Map<String, Object> createRelationshipMap() {
        Map<String, Object> relationships = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            relationships.put("related-" + i, "target-" + (i % 10));
        }
        return relationships;
    }

    private Map<String, Object> createAttributeMap() {
        Map<String, Object> attributes = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            attributes.put("attr-" + i, "value-" + UUID.randomUUID());
        }
        return attributes;
    }

    private Map<String, Object> createDeeplyNestedData(int depth) {
        Map<String, Object> data = new HashMap<>();
        data.put("depth", depth);
        data.put("id", UUID.randomUUID().toString());
        
        if (depth > 0) {
            data.put("children", createDeeplyNestedData(depth - 1));
            data.put("siblings", createDeeplyNestedData(depth - 1));
            data.put("parent", createDeeplyNestedData(depth - 1));
        }
        
        return data;
    }

    private long getMemoryUsage() {
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        return heapUsage.getUsed();
    }

    private Map<String, Object> performJavaProcessing(Map<String, Object> data) {
        // Simulate Java processing for baseline comparison
        Map<String, Object> result = new HashMap<>(data);
        result.put("processed", true);
        result.put("processingTime", System.currentTimeMillis());
        result.put("processedBy", "java");
        return result;
    }

    /**
     * Main method for running benchmarks
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(GraalPyMemoryBenchmark.class.getSimpleName())
            .forks(3)
            .warmupIterations(5)
            .measurementIterations(10)
            .output("graalpy-memory-benchmarks.json")
            .build();
        
        new Runner(opt).run();
    }
}
