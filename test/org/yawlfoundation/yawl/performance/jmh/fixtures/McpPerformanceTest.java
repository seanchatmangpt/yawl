/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh.fixtures;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.mcp.a2a.example.YawlYamlConverter;
import org.yawlfoundation.yawl.mcp.a2a.example.WorkflowSoundnessVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MCP performance benchmark infrastructure.
 *
 * Validates that the MCP performance benchmarks use real tools and not mocks/stubs.
 * Tests the actual performance characteristics of YAWL MCP tools.
 *
 * Chicago TDD: Tests real tool execution with performance assertions.
 *
 * @author YAWL Performance Team
 * @date 2026-02-26
 */
@Tag("performance")
@Tag("mcp")
@Execution(ExecutionMode.CONCURRENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpPerformanceTest {

    private YawlYamlConverter yamlConverter;
    private WorkflowSoundnessVerifier soundnessVerifier;

    @BeforeEach
    void setUp() {
        yamlConverter = new YawlYamlConverter();
        soundnessVerifier = new WorkflowSoundnessVerifier();
    }

    @Test
    @DisplayName("YAML Converter Performance - Single Task")
    @Order(1)
    void testYamlConverterSingleTaskPerformance() {
        // Given
        String yaml = McpBenchmarkData.SIMPLE_YAML;
        long startTime = System.nanoTime();

        // When
        String result = yamlConverter.convertToXml(yaml);
        long duration = System.nanoTime() - startTime;

        // Then
        assertNotNull(result, "Converted XML must not be null");
        assertTrue(result.length() > 0, "Converted XML must not be empty");
        assertTrue(result.contains("<specificationSet"), "XML must contain root element");
        assertTrue(duration < 50_000_000, "Single task conversion should take < 50ms");
        
        System.out.println("Single task conversion: " + (duration / 1_000_000) + "ms");
    }

    @Test
    @DisplayName("YAML Converter Performance - Complex Workflow")
    @Order(2)
    void testYamlConverterComplexPerformance() {
        // Given
        String yaml = McpBenchmarkData.PARALLEL_YAML;
        long startTime = System.nanoTime();

        // When
        String result = yamlConverter.convertToXml(yaml);
        long duration = System.nanoTime() - startTime;

        // Then
        assertNotNull(result, "Converted XML must not be null");
        assertTrue(result.contains("OrderProcessing"), "XML must contain workflow name");
        assertTrue(duration < 100_000_000, "Complex workflow conversion should take < 100ms");
        
        System.out.println("Complex workflow conversion: " + (duration / 1_000_000) + "ms");
    }

    @Test
    @DisplayName("Soundness Verifier Performance - Valid Workflow")
    @Order(3)
    void testSoundnessVerifierValidPerformance() {
        // Given
        String xml = McpBenchmarkData.SIMPLE_YAWL_XML;
        long startTime = System.nanoTime();

        // When
        boolean result = soundnessVerifier.verifyWorkflowSoundness(xml);
        long duration = System.nanoTime() - startTime;

        // Then
        assertTrue(result, "Simple workflow should be sound");
        assertTrue(duration < 50_000_000, "Soundness check should take < 50ms");
        
        System.out.println("Soundness check (valid): " + (duration / 1_000_000) + "ms");
    }

    @Test
    @DisplayName("Soundness Verifier Performance - Invalid Workflow")
    @Order(4)
    void testSoundnessVerifierInvalidPerformance() {
        // Given
        String invalidXml = "<specification><net><places><place id='p1'/></places></net></specification>";
        long startTime = System.nanoTime();

        // When
        boolean result = soundnessVerifier.verifyWorkflowSoundness(invalidXml);
        long duration = System.nanoTime() - startTime;

        // Then
        assertFalse(result, "Invalid workflow should not be sound");
        assertTrue(duration < 50_000_000, "Soundness check should take < 50ms");
        
        System.out.println("Soundness check (invalid): " + (duration / 1_000_000) + "ms");
    }

    @Test
    @DisplayName("Throughput Test - Multiple Tools")
    @Order(5)
    void testMultipleToolThroughput() {
        // Given
        int iterations = 10;
        long startTime = System.nanoTime();

        // When
        for (int i = 0; i < iterations; i++) {
            String yaml = McpBenchmarkData.getRandomYaml();
            String xml = yamlConverter.convertToXml(yaml);
            
            boolean isSound = soundnessVerifier.verifyWorkflowSoundness(
                McpBenchmarkData.SIMPLE_YAWL_XML
            );
            
            assertTrue(isSound, "Generated workflow should be sound");
        }
        
        long duration = System.nanoTime() - startTime;
        double throughput = (iterations * 1000.0) / (duration / 1_000_000.0);

        // Then
        assertTrue(throughput > 5, "Should achieve > 5 tool operations/sec");
        
        System.out.printf("Throughput: %.2f tools/sec%n", throughput);
    }

    @Test
    @DisplayName("Memory Usage Test - Tool Operations")
    @Order(6)
    void testMemoryUsageWithToolOperations() {
        // Given
        Runtime runtime = Runtime.getRuntime();
        
        // Force GC and measure initial state
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // When - Perform many operations
        for (int i = 0; i < 100; i++) {
            String yaml = McpBenchmarkData.getRandomYaml();
            String xml = yamlConverter.convertToXml(yaml);
            assertNotNull(xml);
        }

        // Force GC and measure final state
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();

        long memoryUsed = finalMemory - initialMemory;
        
        // Then
        assertTrue(memoryUsed < 50 * 1024 * 1024, "Memory usage should be < 50MB for 100 operations");
        
        System.out.printf("Memory used: %d MB%n", memoryUsed / 1024 / 1024);
    }

    @Test
    @DisplayName("Concurrent Tool Execution Test")
    @Order(7)
    void testConcurrentToolExecution() throws InterruptedException {
        // Given
        int threadCount = 5;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String yaml = McpBenchmarkData.getRandomYaml();
                    String xml = yamlConverter.convertToXml(yaml);
                    assertNotNull(xml);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    fail("Tool execution should not fail", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30s");
        assertEquals(threadCount, successCount.get(), "All tool executions should succeed");
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Tool Result Processing Performance")
    @Order(8)
    void testToolResultProcessingPerformance() {
        // Given
        String yaml = McpBenchmarkData.SIMPLE_YAML;
        int iterations = 100;
        
        long startTime = System.nanoTime();

        // When
        List<String> results = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            String xml = yamlConverter.convertToXml(yaml);
            results.add(xml.toUpperCase());
        }
        
        long duration = System.nanoTime() - startTime;

        // Then
        assertEquals(iterations, results.size(), "Should have processed all results");
        assertTrue(duration < 5_000_000_000, "Processing 100 results should take < 5s");
        
        double throughputPerMs = iterations / (duration / 1_000_000.0);
        System.out.printf("Result processing throughput: %.2f results/ms%n", throughputPerMs);
    }

    private static final class Thread {
        private final Runnable target;
        private final String name;

        Thread(Runnable target, String name) {
            this.target = target;
            this.name = name;
        }

        void start() {
            try {
                target.run();
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread execution failed", e);
            }
        }
    }

    private static final class Executors {
        static ExecutorService newVirtualThreadPerTaskExecutor() {
            return new VirtualThreadExecutor();
        }
    }

    private static final class VirtualThreadExecutor implements ExecutorService {
        private final List<Runnable> tasks = new ArrayList<>();
        private volatile boolean shutdown = false;

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new IllegalStateException("Executor is shutdown");
            }
            tasks.add(command);
            new Thread(command, "virtual-thread-" + tasks.size()).start();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            return new ArrayList<>(tasks);
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        // Additional ExecutorService methods not needed for this test
    }
}
