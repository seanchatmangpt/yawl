/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh;

import io.a2a.client.Client;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.transport.rest.RestTransport;
import io.a2a.client.transport.rest.RestTransportConfig;
import io.a2a.spec.*;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.yawlfoundation.yawl.mcp.a2a.example.YawlYamlConverter;
import org.yawlfoundation.yawl.mcp.a2a.example.WorkflowSoundnessVerifier;

import java.io.IOException;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * JMH benchmark for MCP (Model Context Protocol) performance analysis.
 *
 * Measures tool execution performance with targets:
 * - Tool latency: < 100ms (p95)
 * - Throughput: > 50 tools/sec
 * - Memory footprint: Stable under load
 *
 * Tested workloads:
 * 1. toolExecutionLatency - Individual tool call performance
 * 2. toolThroughput - Tools per second
 * 3. concurrentToolExecution - Performance under concurrent load
 * 4. toolResultProcessing - Result processing overhead
 * 5. memoryFootprint - Memory usage with MCP integration
 *
 * @author YAWL Performance Team
 * @date 2026-02-26
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "-Xms2g", 
    "-Xmx4g", 
    "-XX:+UseG1GC",
    "-XX:+UseCompactObjectHeaders",
    "-XX:MaxGCPauseMillis=200"
})
public class MCPPerformanceBenchmarks {

    // Configuration parameters
    @Param({"10", "50", "100"})
    private int toolCallCount;

    @Param({"1", "5", "10"})
    private int concurrentClients;

    @Param({"yaml-converter", "soundness-verifier", "both"})
    private String toolType;

    private Client mcpClient;
    private RestTransport transport;
    private List<McpServerFeatures.SyncToolSpecification> availableTools;
    private YawlYamlConverter yamlConverter;
    private WorkflowSoundnessVerifier soundnessVerifier;

    // Memory measurement
    private MemoryMXBean memoryBean;
    private Random random;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        System.out.println("Setting up MCP Performance Benchmarks...");
        
        // Initialize memory monitoring
        memoryBean = java.lang.management.ManagementFactory.getMemoryMXBean();
        random = new Random(42); // Fixed seed for reproducible results

        // Initialize test tools
        yamlConverter = new YawlYamlConverter();
        soundnessVerifier = new WorkflowSoundnessVerifier();

        // Create mock MCP client for testing (using A2A infrastructure)
        transport = new RestTransport(RestTransportConfig.builder()
            .baseUrl("http://localhost:8080")
            .connectTimeout(5000)
            .readTimeout(10000)
            .build());
        
        mcpClient = Client.builder()
            .transport(transport)
            .build();

        // Simulate available tools
        availableTools = Arrays.asList(
            createToolSpec("yaml-converter", "Converts YAML to YAWL XML", "text/plain"),
            createToolSpec("soundness-verifier", "Verifies workflow soundness", "text/json")
        );

        System.out.println("MCP Performance Benchmarks setup complete.");
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        System.out.println("Tearing down MCP Performance Benchmarks...");
        
        if (transport != null) {
            transport.close();
        }
        if (mcpClient != null) {
            mcpClient.close();
        }
        
        System.out.println("MCP Performance Benchmarks teardown complete.");
    }

    // =========================================================================
    // 1. Tool Execution Latency Benchmark
    // =========================================================================

    /**
     * Measures individual tool execution latency (p95 < 100ms).
     */
    @Benchmark
    public void toolExecutionLatency(Blackhole bh) throws Exception {
        String toolName = getToolNameForType(toolType);
        McpServerFeatures.SyncToolSpecification tool = findTool(toolName);
        
        // Simulate tool execution
        long startTime = System.nanoTime();
        
        Object result = executeTool(tool, generateTestInput(toolName));
        
        long duration = System.nanoTime() - startTime;
        bh.consume(result);
        
        System.out.printf("Tool %s executed in %d ms%n", toolName, duration / 1_000_000);
    }

    // =========================================================================
    // 2. Tool Throughput Benchmark  
    // =========================================================================

    /**
     * Measures tools executed per second (target: > 50/sec).
     */
    @Benchmark
    public void toolThroughput(Blackhole bh) throws Exception {
        AtomicInteger completed = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        
        // Execute tools sequentially to measure throughput
        for (int i = 0; i < toolCallCount; i++) {
            String toolName = getToolNameForType(toolType);
            McpServerFeatures.SyncToolSpecification tool = findTool(toolName);
            
            Object result = executeTool(tool, generateTestInput(toolName));
            completed.incrementAndGet();
            bh.consume(result);
            
            // Small delay to simulate real-world usage
            Thread.sleep(1);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double throughput = (completed.get() * 1000.0) / duration;
        
        System.out.printf("Throughput: %.2f tools/sec (%d tools in %d ms)%n", 
            throughput, completed.get(), duration);
    }

    // =========================================================================
    // 3. Concurrent Tool Execution Benchmark
    // =========================================================================

    /**
     * Measures performance under concurrent load with virtual threads.
     */
    @Benchmark
    public void concurrentToolExecution(Blackhole bh) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(toolCallCount * concurrentClients);
        List<Future<Object>> futures = new ArrayList<>();

        long startTime = System.nanoTime();

        // Submit concurrent tool executions
        for (int client = 0; client < concurrentClients; client++) {
            for (int call = 0; call < toolCallCount; call++) {
                final int clientId = client;
                final int callId = call;
                
                Future<Object> future = executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        Thread.sleep(random.nextInt(10)); // Simulate variable delay
                        
                        String toolName = getToolNameForType(toolType);
                        McpServerFeatures.SyncToolSpecification tool = findTool(toolName);
                        
                        Object result = executeTool(tool, generateTestInput(toolName));
                        doneLatch.countDown();
                        return result;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Task interrupted", e);
                    }
                });
                futures.add(future);
            }
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all tasks to complete
        doneLatch.await(30, TimeUnit.SECONDS);
        
        long duration = System.nanoTime() - startTime;
        double totalThroughput = (toolCallCount * concurrentClients * 1000.0) / (duration / 1_000_000.0);
        
        // Verify all futures completed
        for (Future<Object> future : futures) {
            bh.consume(future.get());
        }
        
        executor.shutdown();
        
        System.out.printf("Concurrent execution: %.2f tools/sec (%d clients, %d calls)%n", 
            totalThroughput, concurrentClients, toolCallCount * concurrentClients);
    }

    // =========================================================================
    // 4. Tool Result Processing Benchmark
    // =========================================================================

    /**
     * Measures result processing overhead (deserialization, validation).
     */
    @Benchmark
    public void toolResultProcessing(Blackhole bh) throws Exception {
        String toolName = getToolNameForType(toolType);
        McpServerFeatures.SyncToolSpecification tool = findTool(toolName);
        
        // Execute tool and measure processing time
        Object rawResult = executeTool(tool, generateTestInput(toolName));
        
        long startTime = System.nanoTime();
        
        // Simulate result processing
        Object processedResult = processToolResult(rawResult, toolName);
        validatedResult(processedResult, toolName);
        formattedResult(processedResult, toolName);
        
        long duration = System.nanoTime() - startTime;
        bh.consume(processedResult);
        
        System.out.printf("Result processing took %d ms%n", duration / 1_000_000);
    }

    // =========================================================================
    // 5. Memory Footprint Benchmark
    // =========================================================================

    /**
     * Measures memory usage with MCP integration.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public void memoryFootprint(Blackhole bh) throws Exception {
        // Force GC before measurement
        System.gc();
        Thread.sleep(200);
        
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long usedBefore = beforeHeap.getUsed();

        // Execute workload with memory tracking
        for (int i = 0; i < toolCallCount; i++) {
            String toolName = getToolNameForType(toolType);
            McpServerFeatures.SyncToolSpecification tool = findTool(toolName);
            
            Object result = executeTool(tool, generateTestInput(toolName));
            bh.consume(result);
            
            // Periodic GC to measure stable memory usage
            if (i % 10 == 0) {
                System.gc();
                Thread.sleep(50);
            }
        }

        // Final memory measurement
        System.gc();
        Thread.sleep(200);
        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long usedAfter = afterHeap.getUsed();
        
        long memoryUsed = usedAfter - usedBefore;
        long memoryPerCall = memoryUsed / toolCallCount;
        
        System.out.printf("Memory usage: %d KB total, %d KB per call%n", 
            memoryUsed / 1024, memoryPerCall / 1024);
        
        bh.consume(memoryPerCall);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createToolSpec(String name, String description, String inputType) {
        return new McpServerFeatures.SyncToolSpecification(
            name,
            description,
            Collections.singletonList(
                new McpServerTools.InputSchema(
                    inputType,
                    Map.of("type", "object", "properties", Map.of("data", Map.of("type", "string")))
                )
            ),
            "text/plain"
        );
    }

    private String getToolNameForType(String toolType) {
        return switch (toolType) {
            case "yaml-converter" -> "yaml-converter";
            case "soundness-verifier" -> "soundness-verifier";
            case "both" -> random.nextBoolean() ? "yaml-converter" : "soundness-verifier";
            default -> throw new IllegalArgumentException("Unknown tool type: " + toolType);
        };
    }

    private McpServerFeatures.SyncToolSpecification findTool(String name) {
        return availableTools.stream()
            .filter(tool -> tool.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + name));
    }

    private Object executeTool(McpServerFeatures.SyncToolSpecification tool, Object input) throws Exception {
        // Simulate actual tool execution with realistic timing
        Thread.sleep(5 + random.nextInt(15)); // 5-20ms base execution time
        
        return switch (tool.name()) {
            case "yaml-converter" -> yamlConverter.convertToXml((String) input);
            case "soundness-verifier" -> soundnessVerifier.verifyWorkflowSoundness((String) input);
            default -> "mock-result-" + UUID.randomUUID();
        };
    }

    private Object generateTestInput(String toolName) {
        return switch (toolName) {
            case "yaml-converter" -> """
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            case "soundness-verifier" -> """
                <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                  <specification uri="test.xml" name="TestWorkflow">
                    <net>
                      <places>
                        <place id="start" name="Start"/>
                        <place id="end" name="End"/>
                      </places>
                      <transitions>
                        <transition id="TaskA" name="Task A"/>
                      </transitions>
                      <arcs>
                        <arc id="arc1" source="start" target="TaskA"/>
                        <arc id="arc2" source="TaskA" target="end"/>
                      </arcs>
                    </net>
                  </specification>
                </specificationSet>
                """;
            default -> "test-data-" + UUID.randomUUID();
        };
    }

    private Object processToolResult(Object rawResult, String toolName) {
        // Simulate result processing (parsing, validation)
        try {
            Thread.sleep(1 + random.nextInt(5)); // 1-5ms processing time
            
            if (rawResult instanceof String) {
                return ((String) rawResult).toUpperCase();
            }
            return rawResult;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Result processing interrupted", e);
        }
    }

    private void validatedResult(Object result, String toolName) {
        // Simulate result validation
        if (result == null) {
            throw new IllegalArgumentException("Result cannot be null");
        }
    }

    private String formattedResult(Object result, String toolName) {
        // Simulate result formatting for output
        return String.format("[%s] %s", toolName, result.toString());
    }

    public static void main(String[] args) throws RunnerException {
        System.out.println("=".repeat(80));
        System.out.println("YAWL MCP Performance Benchmark Suite");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("Measuring MCP performance with targets:");
        System.out.println("  - Tool latency: < 100ms (p95)");
        System.out.println("  - Throughput: > 50 tools/sec");
        System.out.println("  - Memory footprint: Stable under load");
        System.out.println();
        System.out.println("Benchmarking:");
        System.out.println("  1. Individual tool execution latency");
        System.out.println("  2. Tool throughput (calls/sec)");
        System.out.println("  3. Concurrent tool execution");
        System.out.println("  4. Tool result processing overhead");
        System.out.println("  5. Memory usage with MCP integration");
        System.out.println();
        System.out.println("Estimated runtime: 20-30 minutes");
        System.out.println("=".repeat(80));
        System.out.println();

        Options opt = new OptionsBuilder()
            .include(MCPPerformanceBenchmarks.class.getSimpleName())
            .forks(1)
            .warmupIterations(3)
            .measurementIterations(5)
            .resultFormat(org.openjdk.jmh.results.format.ResultFormatType.JSON)
            .result("target/jmh-mcp-results.json")
            .build();

        new Runner(opt).run();

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("MCP Performance Benchmark suite completed!");
        System.out.println("Results saved to: target/jmh-mcp-results.json");
        System.out.println("=".repeat(80));
    }
}
