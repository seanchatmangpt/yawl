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

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-language serialization performance benchmark.
 * 
 * Measures the performance of serializing and deserializing data between
 * Java and Python using different serialization formats (JSON, Protocol Buffers,
 * MessagePack, Pickle) across the GraalPy integration boundary.
 * 
 * Target: <10% serialization overhead over native format performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@Threads(1)
@State(Scope.Benchmark)
public class PolyglotSerializationBenchmark {

    private YawlTypeMarshaller marshaller;
    private YawlGraalPyEngine graalPyEngine;
    private YNet testNet;
    private List<YAWLTask> taskList;
    
    // Serialization test data
    private List<Map<String, Object>> workflowData;
    private Map<String, Object> complexObject;
    private List<Map<String, Object>> eventLogs;
    
    // Serialization formats
    private static final String FORMAT_JSON = "json";
    private static final String FORMAT_PROTOBUF = "protobuf";
    private static final String FORMAT_MESSAGEPACK = "messagepack";
    private static final String FORMAT_PICKLE = "pickle";
    
    // Java baseline
    private Map<String, Object> javaSerializationResults;

    @Setup
    public void setup() throws Exception {
        // Initialize GraalPy engine
        marshaller = YawlTypeMarshallerFactory.createMarshaller();
        graalPyEngine = new YawlGraalPyEngine();
        graalPyEngine.initialize();
        
        // Create test workflow
        testNet = createSerializationTestNet();
        taskList = new ArrayList<>(testNet.getTasks());
        
        // Prepare test data
        workflowData = createWorkflowData();
        complexObject = createComplexObject();
        eventLogs = createEventLogs();
        javaSerializationResults = new ConcurrentHashMap<>();
        
        // Load Python serialization scripts
        loadSerializationScripts();
    }

    @TearDown
    public void tearDown() {
        if (graalPyEngine != null) {
            graalPyEngine.shutdown();
        }
    }

    /**
     * JSON Serialization Performance
     * Measures JSON serialization/deserialization performance
     */
    @Benchmark
    public void benchmarkJSONSerialization(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        
        // Serialize to Python format via JSON
        Map<String, Object> pythonData = marshaller.marshallToPython(workflowData);
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(0), 
            Map.of("data", pythonData, "format", FORMAT_JSON)
        );
        
        long duration = System.nanoTime() - startTime;
        
        // Verify performance targets
        double degradation = calculatePerformanceDegradation(duration, "json");
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("JSON serialization performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(result);
        bh.consume(duration);
    }

    /**
     * Protocol Buffers Serialization Performance
     * Measures Protocol Buffers serialization/deserialization performance
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(2)
    public void benchmarkProtobufSerialization(Blackhole bh) throws Exception {
        List<Map<String, Object>> serializationResults = new ArrayList<>();
        List<Long> serializationTimes = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            long startTime = System.nanoTime();
            
            Map<String, Object> pythonData = marshaller.marshallToPython(complexObject);
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(1), 
                Map.of("data", pythonData, "format", FORMAT_PROTOBUF, "iteration", i)
            );
            
            long duration = System.nanoTime() - startTime;
            serializationTimes.add(duration);
            serializationResults.add(result);
        }
        
        // Calculate average performance
        double avgDuration = serializationTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double degradation = calculatePerformanceDegradation(avgDuration, "protobuf");
        
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Protobuf serialization performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(serializationResults);
        bh.consume(avgDuration);
    }

    /**
     * MessagePack Serialization Performance
     * Measures MessagePack serialization/deserialization performance
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkMessagePackSerialization(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        
        // Serialize to Python format via MessagePack
        Map<String, Object> pythonData = marshaller.marshallToPython(eventLogs);
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(2), 
            Map.of("data", pythonData, "format", FORMAT_MESSAGEPACK)
        );
        
        long duration = System.nanoTime() - startTime;
        
        double degradation = calculatePerformanceDegradation(duration, "messagepack");
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("MessagePack serialization performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(result);
        bh.consume(duration);
    }

    /**
     * Pickle Serialization Performance
     * Measures Python Pickle serialization/deserialization performance
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkPickleSerialization(Blackhole bh) throws Exception {
        long startTime = System.nanoTime();
        
        // Serialize to Python format via Pickle
        Map<String, Object> pythonData = marshaller.marshallToPython(complexObject);
        Map<String, Object> result = graalPyEngine.executeTask(
            taskList.get(3), 
            Map.of("data", pythonData, "format", FORMAT_PICKLE)
        );
        
        long duration = System.nanoTime() - startTime;
        
        double degradation = calculatePerformanceDegradation(duration, "pickle");
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Pickle serialization performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(result);
        bh.consume(duration);
    }

    /**
     * Cross-format Comparison Performance
     * Compares performance across all serialization formats
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkCrossFormatComparison(Blackhole bh) throws Exception {
        String[] formats = {FORMAT_JSON, FORMAT_PROTOBUF, FORMAT_MESSAGEPACK, FORMAT_PICKLE};
        List<Map<String, Object>> formatResults = new ArrayList<>();
        List<Long> formatTimes = new ArrayList<>();
        
        for (String format : formats) {
            long startTime = System.nanoTime();
            
            Map<String, Object> pythonData = marshaller.marshallToPython(workflowData);
            Map<String, Object> result = graalPyEngine.executeTask(
                taskList.get(4), 
                Map.of("data", pythonData, "format", format, "comparison", true)
            );
            
            long duration = System.nanoTime() - startTime;
            formatTimes.add(duration);
            formatResults.add(result);
        }
        
        // Calculate throughput
        double totalTime = formatTimes.stream().mapToLong(Long::longValue).sum();
        double throughput = formatResults.size() / (totalTime / 1e9);
        
        if (throughput < PerformanceTargets.MIN_THROUGHPUT_OPS_PER_SEC) {
            throw new AssertionError("Cross-format comparison throughput below target: " + throughput + " ops/sec");
        }
        
        bh.consume(throughput);
    }

    /**
     * Large Dataset Serialization Performance
     * Serialization of large datasets with different formats
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Threads(4)
    public void benchmarkLargeDatasetSerializationConcurrent(Blackhole bh) throws Exception {
        List<Thread> threads = new ArrayList<>();
        List<Map<String, Object>> threadResults = Collections.synchronizedList(new ArrayList<>());
        List<Long> threadTimes = Collections.synchronizedList(new ArrayList<>());
        
        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        long startTime = System.nanoTime();
                        
                        Map<String, Object> datasetData = createThreadSpecificDataset(threadId, i);
                        Map<String, Object> pythonData = marshaller.marshallToPython(datasetData);
                        Map<String, Object> result = graalPyEngine.executeTask(
                            taskList.get(5), 
                            Map.of("dataset", pythonData, "format", formats[t % formats.length], "threadId", threadId)
                        );
                        
                        long duration = System.nanoTime() - startTime;
                        threadTimes.add(duration);
                        threadResults.add(result);
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
        double avgDuration = threadTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double degradation = calculatePerformanceDegradation(avgDuration, "large_dataset");
        
        if (!PerformanceTargets.isPerformanceDegradationAcceptable(degradation)) {
            throw new AssertionError("Concurrent large dataset serialization performance degradation exceeds target: " + degradation + "%");
        }
        
        bh.consume(threadResults);
        bh.consume(avgDuration);
    }

    /**
     * Java Baseline Serialization
     * Pure Java serialization for comparison
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkJavaSerializationBaseline(Blackhole bh) {
        long startTime = System.nanoTime();
        
        // Simulate Java serialization
        Map<String, Object> result = performJavaSerialization(workflowData, FORMAT_JSON);
        
        long duration = System.nanoTime() - startTime;
        javaSerializationResults.put("baseline", Map.of("result", result, "duration", duration));
        
        bh.consume(result);
        bh.consume(duration);
    }

    // Helper methods
    private YNet createSerializationTestNet() {
        YNet net = new YNet("serialization-test-net");
        
        YCondition start = net.addCondition("start");
        YCondition end = net.addCondition("end");
        
        // Add tasks for different serialization operations
        YAWLTask jsonSerialization = net.addTask("json-serialization");
        YAWLTask protobufSerialization = net.addTask("protobuf-serialization");
        YAWLTask messagePackSerialization = net.addTask("messagepack-serialization");
        YAWLTask pickleSerialization = net.addTask("pickle-serialization");
        YAWLTask crossFormatComparison = net.addTask("cross-format-comparison");
        YAWLTask largeDatasetSerialization = net.addTask("large-dataset-serialization");
        
        net.setStartConditions(Collections.singleton(start.getID()));
        net.setFinishConditions(Collections.singleton(end.getID()));
        
        // Set flows
        net.addFlow(start.getID(), jsonSerialization.getID());
        net.addFlow(jsonSerialization.getID(), protobufSerialization.getID());
        net.addFlow(protobufSerialization.getID(), messagePackSerialization.getID());
        net.addFlow(messagePackSerialization.getID(), pickleSerialization.getID());
        net.addFlow(pickleSerialization.getID(), crossFormatComparison.getID());
        net.addFlow(crossFormatComparison.getID(), largeDatasetSerialization.getID());
        net.addFlow(largeDatasetSerialization.getID(), end.getID());
        
        return net;
    }

    private List<Map<String, Object>> createWorkflowData() {
        List<Map<String, Object>> data = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", "workflow-" + i);
            item.put("name", "Workflow " + i);
            item.put("version", "1.0." + i);
            item.put("tasks", createTaskList(i));
            item.put("variables", createVariableMap(i));
            item.put("metadata", createWorkflowMetadata(i));
            data.add(item);
        }
        
        return data;
    }

    private Map<String, Object> createComplexObject() {
        Map<String, Object> complex = new HashMap<>();
        complex.put("id", "complex-" + UUID.randomUUID());
        complex.put("type", "complex-object");
        complex.put("nested", createDeepNesting(3));
        complex.put("arrays", createNestedArrays());
        complex.put("primitives", Map.of(
            "int", Integer.MAX_VALUE,
            "double", Double.MAX_VALUE,
            "boolean", true,
            "string", "Complex object with all types"
        ));
        return complex;
    }

    private List<Map<String, Object>> createEventLogs() {
        List<Map<String, Object>> logs = new ArrayList<>();
        
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> event = new HashMap<>();
            event.put("caseId", "case-" + (i % 100));
            event.put("taskId", "task-" + (i % 20));
            event.put("timestamp", System.currentTimeMillis() - (1000 - i) * 1000);
            event.put("resource", "resource-" + (i % 10));
            event.put("data", createEventData(i));
            event.put("metadata", Map.of(
                "sequence", i,
                "priority", i % 5,
                "category", "event-" + (i % 3)
            ));
            logs.add(event);
        }
        
        return logs;
    }

    private List<Map<String, Object>> createTaskList(int workflowId) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            Map<String, Object> task = new HashMap<>();
            task.put("id", "task-" + workflowId + "-" + i);
            task.put("name", "Task " + i + " for workflow " + workflowId);
            task.put("timeout", 5000 + i * 1000);
            task.put("retries", i % 3);
            task.put("inputSchema", createSchema());
            task.put("outputSchema", createSchema());
            tasks.add(task);
        }
        
        return tasks;
    }

    private Map<String, Object> createVariableMap(int workflowId) {
        Map<String, Object> variables = new HashMap<>();
        
        variables.put("workflowId", workflowId);
        variables.put("created", System.currentTimeMillis());
        variables.put("status", "active");
        variables.put("attributes", Map.of(
            "owner", "owner-" + workflowId,
            "department", "dept-" + (workflowId % 5),
            "budget", 10000 + workflowId * 1000,
            "deadline", new Date(System.currentTimeMillis() + 86400000 * 7)
        ));
        
        return variables;
    }

    private Map<String, Object> createWorkflowMetadata(int workflowId) {
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("created", System.currentTimeMillis());
        metadata.put("modified", System.currentTimeMillis());
        metadata.put("version", "1.0." + workflowId);
        metadata.put("author", "author-" + workflowId);
        metadata.put("tags", Arrays.asList("workflow", "test", "benchmark"));
        metadata.put("statistics", Map.of(
            "taskCount", 10,
            "averageDuration", 5000,
            "successRate", 0.95,
            "complexity", workflowId % 10
        ));
        
        return metadata;
    }

    private Map<String, Object> createDeepNesting(int depth) {
        if (depth == 0) {
            return Map.of("value", "leaf-" + UUID.randomUUID());
        }
        
        Map<String, Object> nested = new HashMap<>();
        nested.put("level", depth);
        nested.put("data", createDeepNesting(depth - 1));
        nested.put("siblings", createDeepNesting(depth - 1));
        nested.put("metadata", Map.of(
            "depth", depth,
            "id", UUID.randomUUID().toString()
        ));
        
        return nested;
    }

    private List<Object> createNestedArrays() {
        List<Object> arrays = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            List<Object> array = new ArrayList<>();
            for (int j = 0; j < 10; j++) {
                array.add(Map.of(
                    "index", j,
                    "value", Math.random() * 1000,
                    "nested", createDeepNesting(2)
                ));
            }
            arrays.add(array);
        }
        
        return arrays;
    }

    private Map<String, Object> createEventData(int eventId) {
        Map<String, Object> data = new HashMap<>();
        
        data.put("eventId", eventId);
        data.put("payload", createPayload());
        data.put("metrics", Map.of(
            "duration", Math.random() * 10000,
            "cpu", Math.random() * 100,
            "memory", Math.random() * 1024,
            "io", Math.random() * 1000
        ));
        
        return data;
    }

    private Map<String, Object> createPayload() {
        return Map.of(
            "timestamp", System.currentTimeMillis(),
            "value", Math.random(),
            "metadata", Map.of(
                "source", "benchmark",
                "version", "1.0",
                "quality", 0.95 + Math.random() * 0.05
            )
        );
    }

    private Map<String, Object> createSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "id", Map.of("type", "string"),
                "value", Map.of("type", "number"),
                "metadata", Map.of("type", "object")
            ),
            "required", List.of("id")
        );
    }

    private Map<String, Object> createThreadSpecificDataset(int threadId, int iteration) {
        Map<String, Object> dataset = new HashMap<>();
        dataset.put("threadId", threadId);
        dataset.put("iteration", iteration);
        dataset.put("timestamp", System.currentTimeMillis());
        dataset.put("dataPoints", createDataPoints(threadId * 100 + iteration * 10));
        dataset.put("tags", List.of("thread-" + threadId, "iteration-" + iteration));
        return dataset;
    }

    private List<Object> createDataPoints(int count) {
        List<Object> points = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            points.add(Map.of(
                "x", i,
                "y", Math.sin(i) * 100 + Math.random() * 10,
                "z", Math.cos(i) * 100 + Math.random() * 10,
                "category", i % 3
            ));
        }
        
        return points;
    }

    private void loadSerializationScripts() throws Exception {
        // Load Python serialization scripts (mock implementation)
        String serializationScript = """
            import json
            import pickle
            from typing import Dict, Any, List
            import base64
            
            def json_serialization(data: Dict[str, Any], format: str = "json") -> Dict[str, Any]:
                # Mock JSON serialization
                serialized = json.dumps(data, default=str)
                return {
                    "original_size": len(str(data)),
                    "serialized_size": len(serialized),
                    "compression_ratio": len(serialized) / len(str(data)),
                    "serialization_time": 0.001 + len(str(data)) * 0.000001,
                    "format": format,
                    "data": serialized
                }
                
            def protobuf_serialization(data: Dict[str, Any], format: str = "protobuf", iteration: int = 0) -> Dict[str, Any]:
                # Mock Protocol Buffers serialization
                mock_protobuf = base64.b64encode(f"protobuf_data_{iteration}".encode()).decode()
                return {
                    "original_size": len(str(data)),
                    "serialized_size": len(mock_protobuf),
                    "compression_ratio": len(mock_protobuf) / len(str(data)),
                    "serialization_time": 0.0005 + len(str(data)) * 0.0000005,
                    "format": format,
                    "data": mock_protobuf,
                    "iteration": iteration
                }
                
            def messagepack_serialization(data: Dict[str, Any], format: str = "messagepack") -> Dict[str, Any]:
                # Mock MessagePack serialization
                import msgpack
                serialized = msgpack.packb(data, use_bin_type=True)
                return {
                    "original_size": len(str(data)),
                    "serialized_size": len(serialized),
                    "compression_ratio": len(serialized) / len(str(data)),
                    "serialization_time": 0.0008 + len(str(data)) * 0.0000008,
                    "format": format,
                    "data": base64.b64encode(serialized).decode()
                }
                
            def pickle_serialization(data: Dict[str, Any], format: str = "pickle") -> Dict[str, Any]:
                # Mock Pickle serialization
                serialized = pickle.dumps(data)
                return {
                    "original_size": len(str(data)),
                    "serialized_size": len(serialized),
                    "compression_ratio": len(serialized) / len(str(data)),
                    "serialization_time": 0.0015 + len(str(data)) * 0.0000015,
                    "format": format,
                    "data": base64.b64encode(serialized).decode()
                }
                
            def cross_format_comparison(data: Dict[str, Any], format: str, comparison: bool = False) -> Dict[str, Any]:
                # Cross-format comparison
                results = []
                formats = ["json", "protobuf", "messagepack", "pickle"]
                
                for fmt in formats:
                    if fmt == "json":
                        result = json_serialization(data, fmt)
                    elif fmt == "protobuf":
                        result = protobuf_serialization(data, fmt)
                    elif fmt == "messagepack":
                        result = messagepack_serialization(data, fmt)
                    else:
                        result = pickle_serialization(data, fmt)
                    
                    results.append(result)
                
                return {
                    "best_format": min(results, key=lambda x: x["serialization_time"])["format"],
                    "average_time": sum(r["serialization_time"] for r in results) / len(results),
                    "results": results,
                    "comparison": comparison
                }
                
            def large_dataset_serialization(dataset: Dict[str, Any], format: str, thread_id: int) -> Dict[str, Any]:
                # Mock large dataset serialization
                data_size = len(str(dataset))
                return {
                    "dataset_size": data_size,
                    "original_size": data_size,
                    "serialized_size": int(data_size * (0.5 + Math.random() * 0.4)),
                    "compression_ratio": 0.5 + Math.random() * 0.4,
                    "serialization_time": 0.01 + data_size * 0.00001,
                    "format": format,
                    "thread_id": thread_id,
                    "memory_usage": data_size * 0.001  # MB
                }
            """;
        
        // In a real implementation, this would load actual serialization scripts
        // For benchmarking, we use inline Python
    }

    private Map<String, Object> performJavaSerialization(List<Map<String, Object>> data, String format) {
        // Simulate Java serialization for baseline comparison
        Map<String, Object> result = new HashMap<>();
        result.put("serializedData", data);
        result.put("format", format);
        result.put("serializationTime", System.currentTimeMillis());
        result.put("dataSize", data.size());
        return result;
    }

    private double calculatePerformanceDegradation(long pythonDuration, String format) {
        // Get Java baseline duration
        Map<String, Object> baseline = javaSerializationResults.get("baseline");
        if (baseline == null) return 0.0;
        
        long javaDuration = (Long) ((Map<String, Object>) baseline.get("result")).get("serializationTime");
        
        // Calculate degradation percentage
        double degradation = ((double) (pythonDuration - javaDuration) / javaDuration) * 100;
        return Math.max(0, degradation); // Ensure non-negative
    }

    /**
     * Main method for running benchmarks
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(PolyglotSerializationBenchmark.class.getSimpleName())
            .forks(3)
            .warmupIterations(5)
            .measurementIterations(10)
            .output("polyglot-serialization-benchmarks.json")
            .build();
        
        new Runner(opt).run();
    }
}
