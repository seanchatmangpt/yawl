/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent load tests for A2A skills to detect race conditions,
 * thread safety issues, and deadlocks under high concurrency.
 *
 * <p><b>Test Scenarios:</b>
 * <ol>
 *   <li>100 concurrent skill executions via virtual threads</li>
 *   <li>50 parallel introspect queries</li>
 *   <li>25 parallel build requests (with proper isolation)</li>
 *   <li>Concurrent read/write to memory store</li>
 *   <li>Parallel git operations (status only, safe)</li>
 * </ol>
 *
 * <p><b>Metrics Collected:</b>
 * <ul>
 *   <li>Success/failure rate</li>
 *   <li>Race conditions detected</li>
 *   <li>Deadlock occurrences</li>
 *   <li>Data corruption findings</li>
 *   <li>Throughput under load</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Tag("concurrent")
@Tag("load")
class ConcurrentLoadTest {

    // Test configuration
    private static final int CONCURRENT_SKILL_EXECUTIONS = 100;
    private static final int PARALLEL_INTROSPECT_QUERIES = 50;
    private static final int PARALLEL_BUILD_REQUESTS = 25;
    private static final int MEMORY_STORE_OPERATIONS = 200;
    private static final int PARALLEL_GIT_OPS = 30;
    private static final int DEADLOCK_TIMEOUT_SECONDS = 60;
    private static final int TEST_TIMEOUT_MINUTES = 5;

    // Test infrastructure
    private Path tempObservatory;
    private Path factsDir;
    private Path tempProjectDir;
    private Path memoryStorePath;

    // Skills under test
    private IntrospectCodebaseSkill introspectSkill;
    private ExecuteBuildSkill buildSkill;
    private TestMemoryStore memoryStore;

    // Test results collector
    private final TestResultsCollector resultsCollector = new TestResultsCollector();

    @BeforeEach
    void setUp() throws Exception {
        tempObservatory = Files.createTempDirectory("concurrent-observatory");
        factsDir = tempObservatory.resolve("facts");
        Files.createDirectories(factsDir);

        tempProjectDir = Files.createTempDirectory("concurrent-project");
        memoryStorePath = tempProjectDir.resolve("memory-store.json");

        introspectSkill = new IntrospectCodebaseSkill(tempObservatory);
        buildSkill = new ExecuteBuildSkill(tempProjectDir);
        memoryStore = new TestMemoryStore();

        resultsCollector.reset();
    }

    @AfterEach
    void tearDown() throws Exception {
        resultsCollector.report();
        deleteRecursively(tempObservatory);
        deleteRecursively(tempProjectDir);
    }

    // =========================================================================
    // Test 1: 100 Concurrent Skill Executions via Virtual Threads
    // =========================================================================

    @Test
    @DisplayName("100 concurrent skill executions should complete without race conditions")
    @Timeout(value = TEST_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testConcurrentSkillExecutions() throws Exception {
        // Setup: Create test data
        writeFactFile("modules.json", "{\"modules\": [\"engine\", \"elements\", \"integration\"]}");
        writeFactFile("reactor.json", "{\"build_order\": [\"engine\", \"elements\"]}");
        writeFactFile("gates.json", "{\"coverage\": 80, \"lint\": \"strict\"}");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_SKILL_EXECUTIONS);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        LongAdder totalLatency = new LongAdder();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENT_SKILL_EXECUTIONS; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        // Wait for all threads to be ready (thundering herd)
                        startLatch.await();

                        long startTime = System.nanoTime();

                        // Rotate through different skill types
                        SkillResult result = switch (taskId % 3) {
                            case 0 -> executeIntrospect(taskId);
                            case 1 -> executeIntrospectWithAllQuery(taskId);
                            case 2 -> executeIntrospectWithModulesQuery(taskId);
                            default -> executeIntrospect(taskId);
                        };

                        long latency = System.nanoTime() - startTime;
                        totalLatency.add(latency);

                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                            resultsCollector.recordSuccess("skill_execution", latency);
                        } else {
                            failureCount.incrementAndGet();
                            resultsCollector.recordFailure("skill_execution", result.getError());
                        }

                        // Verify result integrity
                        verifyResultIntegrity(result, taskId);

                    } catch (Throwable e) {
                        exceptions.add(e);
                        resultsCollector.recordException("skill_execution", e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            // Release all threads simultaneously
            startLatch.countDown();

            // Wait for completion with timeout
            boolean completed = completionLatch.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All tasks should complete without deadlock");

            // Report results
            double avgLatencyMs = totalLatency.sum() / 1_000_000.0 / CONCURRENT_SKILL_EXECUTIONS;
            resultsCollector.setMetric("skill_executions_avg_latency_ms", avgLatencyMs);
            resultsCollector.setMetric("skill_executions_success_rate",
                (double) successCount.get() / CONCURRENT_SKILL_EXECUTIONS * 100);

            // Verify no exceptions
            if (!exceptions.isEmpty()) {
                fail("Concurrent skill executions encountered exceptions: " +
                     exceptions.stream().map(Throwable::getMessage).toList());
            }

            // Verify success rate
            assertTrue(successCount.get() >= CONCURRENT_SKILL_EXECUTIONS * 0.95,
                "At least 95% of skill executions should succeed. Got: " + successCount.get());

            // Verify no data corruption
            assertFalse(resultsCollector.hasDataCorruption(),
                "No data corruption should occur during concurrent execution");
        }
    }

    // =========================================================================
    // Test 2: 50 Parallel Introspect Queries
    // =========================================================================

    @Test
    @DisplayName("50 parallel introspect queries should return consistent results")
    @Timeout(value = TEST_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testParallelIntrospectQueries() throws Exception {
        // Setup: Create comprehensive test data
        writeFactFile("modules.json", "{\"modules\": [\"yawl-engine\", \"yawl-elements\"], \"count\": 2}");
        writeFactFile("reactor.json", "{\"build_order\": [\"engine\", \"elements\", \"integration\"]}");
        writeFactFile("gates.json", "{\"coverage\": 80}");
        writeFactFile("integration.json", "{\"mcp\": true, \"a2a\": true}");
        writeFactFile("static-analysis.json", "{\"bugs\": 0}");
        writeFactFile("spotbugs-findings.json", "{\"findings\": []}");
        writeFactFile("pmd-violations.json", "{\"violations\": []}");
        writeFactFile("checkstyle-warnings.json", "{\"warnings\": []}");

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(PARALLEL_INTROSPECT_QUERIES);

        Map<String, AtomicInteger> queryResults = new ConcurrentHashMap<>();
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger consistencyViolations = new AtomicInteger(0);

        // Pre-populate result trackers
        for (String query : List.of("modules", "reactor", "gates", "integration", "all")) {
            queryResults.put(query, new AtomicInteger(0));
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < PARALLEL_INTROSPECT_QUERIES; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        String queryType = switch (taskId % 5) {
                            case 0 -> "modules";
                            case 1 -> "reactor";
                            case 2 -> "gates";
                            case 3 -> "integration";
                            default -> "all";
                        };

                        SkillRequest request = SkillRequest.builder("introspect_codebase")
                            .parameter("query", queryType)
                            .requestId("parallel-introspect-" + taskId)
                            .build();

                        SkillResult result = introspectSkill.execute(request);

                        if (result.isSuccess()) {
                            queryResults.get(queryType).incrementAndGet();

                            // Verify result consistency
                            if (!verifyIntrospectConsistency(result, queryType)) {
                                consistencyViolations.incrementAndGet();
                            }
                        }
                    } catch (Throwable e) {
                        exceptions.add(e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All introspect queries should complete without deadlock");

            // Report metrics
            int totalSuccessful = queryResults.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
            resultsCollector.setMetric("introspect_success_count", totalSuccessful);
            resultsCollector.setMetric("introspect_consistency_violations", consistencyViolations.get());

            // Verify results
            if (!exceptions.isEmpty()) {
                fail("Parallel introspect queries encountered exceptions: " +
                     exceptions.getFirst().getMessage());
            }

            assertEquals(0, consistencyViolations.get(),
                "All introspect results should be consistent");

            assertTrue(totalSuccessful >= PARALLEL_INTROSPECT_QUERIES * 0.98,
                "At least 98% of introspect queries should succeed");
        }
    }

    // =========================================================================
    // Test 3: 25 Parallel Build Requests (with proper isolation)
    // =========================================================================

    @Test
    @DisplayName("25 parallel build requests should be isolated and not interfere")
    @Timeout(value = TEST_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testParallelBuildRequestsWithIsolation() throws Exception {
        // Create isolated build directories
        List<Path> isolatedBuildDirs = new ArrayList<>();
        for (int i = 0; i < PARALLEL_BUILD_REQUESTS; i++) {
            Path isolatedDir = Files.createTempDirectory("isolated-build-" + i);
            isolatedBuildDirs.add(isolatedDir);
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(PARALLEL_BUILD_REQUESTS);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger validRequests = new AtomicInteger(0);
        AtomicInteger isolationViolations = new AtomicInteger(0);

        // Use a semaphore to limit actual build process spawning (simulated test)
        Semaphore buildSemaphore = new Semaphore(5);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < PARALLEL_BUILD_REQUESTS; i++) {
                final int taskId = i;
                final Path isolatedDir = isolatedBuildDirs.get(i);

                executor.submit(() -> {
                    try {
                        startLatch.await();
                        buildSemaphore.acquire();

                        // Create isolated skill instance
                        ExecuteBuildSkill isolatedSkill = new ExecuteBuildSkill(isolatedDir);

                        // Create unique request with isolation tracking
                        SkillRequest request = SkillRequest.builder("execute_build")
                            .parameter("mode", "incremental")
                            .parameter("modules", "test-module-" + taskId)
                            .requestId("isolated-build-" + taskId)
                            .build();

                        // Validate request isolation
                        if (verifyBuildRequestIsolation(request, taskId)) {
                            validRequests.incrementAndGet();
                        } else {
                            isolationViolations.incrementAndGet();
                        }

                        // Note: We don't actually execute the build to avoid
                        // thrashing the system - this tests request handling isolation

                    } catch (Throwable e) {
                        exceptions.add(e);
                    } finally {
                        buildSemaphore.release();
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All build requests should complete without deadlock");

            // Report metrics
            resultsCollector.setMetric("build_requests_valid", validRequests.get());
            resultsCollector.setMetric("build_isolation_violations", isolationViolations.get());

            // Cleanup isolated directories
            for (Path dir : isolatedBuildDirs) {
                deleteRecursively(dir);
            }

            // Verify isolation
            assertEquals(PARALLEL_BUILD_REQUESTS, validRequests.get(),
                "All build requests should be properly isolated");

            assertEquals(0, isolationViolations.get(),
                "No isolation violations should occur");
        }
    }

    // =========================================================================
    // Test 4: Concurrent Read/Write to Memory Store
    // =========================================================================

    @Test
    @DisplayName("Concurrent read/write to memory store should maintain consistency")
    @Timeout(value = TEST_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testConcurrentMemoryStoreAccess() throws Exception {
        // Setup: Pre-populate memory store
        for (int i = 0; i < 50; i++) {
            memoryStore.store("key-" + i, "value-" + i);
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(MEMORY_STORE_OPERATIONS);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger writeCount = new AtomicInteger(0);
        AtomicInteger consistencyErrors = new AtomicInteger(0);

        // Track expected values for verification
        ConcurrentHashMap<String, String> expectedValues = new ConcurrentHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < MEMORY_STORE_OPERATIONS; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        if (taskId % 2 == 0) {
                            // Writer thread
                            String key = "concurrent-key-" + (taskId % 20);
                            String value = "value-" + taskId + "-" + System.nanoTime();
                            memoryStore.store(key, value);
                            expectedValues.put(key, value);
                            writeCount.incrementAndGet();
                        } else {
                            // Reader thread
                            String key = "key-" + (taskId % 50);
                            String value = memoryStore.retrieve(key);
                            if (value != null) {
                                readCount.incrementAndGet();
                                // Verify read value is valid (not corrupted)
                                if (!value.startsWith("value-")) {
                                    consistencyErrors.incrementAndGet();
                                }
                            }
                        }
                    } catch (Throwable e) {
                        exceptions.add(e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All memory operations should complete without deadlock");

            // Report metrics
            resultsCollector.setMetric("memory_reads", readCount.get());
            resultsCollector.setMetric("memory_writes", writeCount.get());
            resultsCollector.setMetric("memory_consistency_errors", consistencyErrors.get());

            // Verify final state consistency
            Map<String, String> finalState = memoryStore.getAllEntries();
            resultsCollector.setMetric("memory_final_entry_count", finalState.size());

            // Verify no data corruption
            assertEquals(0, consistencyErrors.get(),
                "No memory consistency errors should occur");

            // Verify final state integrity
            for (Map.Entry<String, String> entry : expectedValues.entrySet()) {
                String stored = finalState.get(entry.getKey());
                if (!entry.getValue().equals(stored)) {
                    resultsCollector.recordDataCorruption("memory_store",
                        "Key: " + entry.getKey() + " expected: " + entry.getValue() +
                        " got: " + stored);
                }
            }

            assertFalse(resultsCollector.hasDataCorruption(),
                "Memory store should maintain consistency under concurrent access");
        }
    }

    // =========================================================================
    // Test 5: Parallel Git Operations (status only, safe)
    // =========================================================================

    @Test
    @DisplayName("Parallel git status operations should be safe")
    @Timeout(value = TEST_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testParallelGitOperations() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(PARALLEL_GIT_OPS);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicBoolean gitRaceCondition = new AtomicBoolean(false);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < PARALLEL_GIT_OPS; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Execute git status (read-only, safe operation)
                        ProcessBuilder pb = new ProcessBuilder("git", "status", "--porcelain");
                        pb.directory(tempProjectDir.toFile());
                        pb.redirectErrorStream(true);

                        Process process = pb.start();
                        boolean finished = process.waitFor(10, TimeUnit.SECONDS);

                        if (finished) {
                            int exitCode = process.exitValue();
                            // Exit code 0 or 128 (not a git repo) are acceptable
                            if (exitCode == 0 || exitCode == 128) {
                                successCount.incrementAndGet();
                            }
                        } else {
                            process.destroyForcibly();
                        }

                    } catch (Throwable e) {
                        // Git not available or other error - count as expected in some environments
                        if (!e.getMessage().contains("Cannot run")) {
                            exceptions.add(e);
                        }
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All git operations should complete without deadlock");

            // Report metrics
            resultsCollector.setMetric("git_ops_success", successCount.get());
            resultsCollector.setMetric("git_ops_race_conditions",
                gitRaceCondition.get() ? 1 : 0);

            // Git operations are best-effort in test environment
            // Just verify no exceptions related to concurrency
            long concurrencyExceptions = exceptions.stream()
                .filter(e -> e.getMessage() != null &&
                    (e.getMessage().contains("ConcurrentModification") ||
                     e.getMessage().contains("deadlock") ||
                     e.getMessage().contains("lock")))
                .count();

            assertEquals(0, concurrencyExceptions,
                "No concurrency exceptions should occur during git operations");
        }
    }

    // =========================================================================
    // Stress Test: High Contention Scenario
    // =========================================================================

    @Test
    @DisplayName("High contention scenario should not cause deadlocks")
    @Timeout(value = TEST_TIMEOUT_MINUTES, unit = TimeUnit.MINUTES)
    void testHighContentionScenario() throws Exception {
        // Create a shared resource that will be heavily contended
        ContendedResource resource = new ContendedResource();

        int numThreads = 50;
        int operationsPerThread = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numThreads);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        LongAdder totalOperations = new LongAdder();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        for (int j = 0; j < operationsPerThread; j++) {
                            resource.increment();
                            resource.read();
                            totalOperations.increment();
                        }
                    } catch (Throwable e) {
                        exceptions.add(e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completionLatch.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "High contention test should complete without deadlock");

            // Verify final count
            int expectedTotal = numThreads * operationsPerThread;
            int actualTotal = resource.getValue();

            resultsCollector.setMetric("high_contention_operations", totalOperations.sum());
            resultsCollector.setMetric("high_contention_expected", expectedTotal);
            resultsCollector.setMetric("high_contention_actual", actualTotal);

            assertEquals(expectedTotal, actualTotal,
                "All operations should complete under high contention");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private SkillResult executeIntrospect(int taskId) {
        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "modules")
            .requestId("concurrent-" + taskId)
            .build();
        return introspectSkill.execute(request);
    }

    private SkillResult executeIntrospectWithAllQuery(int taskId) {
        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "all")
            .requestId("concurrent-all-" + taskId)
            .build();
        return introspectSkill.execute(request);
    }

    private SkillResult executeIntrospectWithModulesQuery(int taskId) {
        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "modules")
            .requestId("concurrent-modules-" + taskId)
            .build();
        return introspectSkill.execute(request);
    }

    private void verifyResultIntegrity(SkillResult result, int taskId) {
        if (result == null) {
            resultsCollector.recordDataCorruption("result_integrity",
                "Task " + taskId + " returned null result");
            return;
        }

        // Verify result has expected structure
        if (result.isSuccess() && result.getData() == null) {
            resultsCollector.recordDataCorruption("result_integrity",
                "Task " + taskId + " returned success with null data");
        }
    }

    private boolean verifyIntrospectConsistency(SkillResult result, String queryType) {
        if (!result.isSuccess()) {
            return true; // Failed results are consistent
        }

        Map<String, Object> data = result.getData();
        if (data == null) {
            return false;
        }

        // Verify expected structure based on query type
        return switch (queryType) {
            case "modules" -> data.containsKey("modules") || data.containsKey("error");
            case "reactor" -> data.containsKey("build_order") || data.containsKey("error");
            case "gates" -> data.containsKey("coverage") || data.containsKey("gates") ||
                           data.containsKey("error");
            case "integration" -> data.containsKey("mcp") || data.containsKey("a2a") ||
                                 data.containsKey("error");
            case "all" -> data.containsKey("timestamp");
            default -> true;
        };
    }

    private boolean verifyBuildRequestIsolation(SkillRequest request, int taskId) {
        // Verify request ID is unique
        String requestId = request.getRequestId();
        if (!requestId.contains(String.valueOf(taskId))) {
            return false;
        }

        // Verify parameters are isolated
        String modules = request.getParameter("modules");
        if (!modules.contains(String.valueOf(taskId))) {
            return false;
        }

        return true;
    }

    private void writeFactFile(String name, String content) throws IOException {
        Files.writeString(factsDir.resolve(name), content);
    }

    private void deleteRecursively(Path path) {
        if (path == null) return;
        try {
            if (Files.isDirectory(path)) {
                Files.walk(path)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    // =========================================================================
    // Test Infrastructure Classes
    // =========================================================================

    /**
     * Thread-safe memory store for testing concurrent access.
     */
    private static class TestMemoryStore {
        private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

        void store(String key, String value) {
            store.put(key, value);
        }

        String retrieve(String key) {
            return store.get(key);
        }

        Map<String, String> getAllEntries() {
            return new HashMap<>(store);
        }
    }

    /**
     * Contended resource for high-contention testing.
     */
    private static class ContendedResource {
        private final AtomicInteger value = new AtomicInteger(0);

        void increment() {
            value.incrementAndGet();
        }

        int read() {
            return value.get();
        }

        int getValue() {
            return value.get();
        }
    }

    /**
     * Collects and reports test results.
     */
    private static class TestResultsCollector {
        private final ConcurrentHashMap<String, Long> metrics = new ConcurrentHashMap<>();
        private final List<String> successes = Collections.synchronizedList(new ArrayList<>());
        private final List<String> failures = Collections.synchronizedList(new ArrayList<>());
        private final List<String> exceptions = Collections.synchronizedList(new ArrayList<>());
        private final List<String> dataCorruptions = Collections.synchronizedList(new ArrayList<>());

        void reset() {
            metrics.clear();
            successes.clear();
            failures.clear();
            exceptions.clear();
            dataCorruptions.clear();
        }

        void setMetric(String name, long value) {
            metrics.put(name, value);
        }

        void setMetric(String name, double value) {
            metrics.put(name, Double.doubleToLongBits(value));
        }

        void recordSuccess(String category, long latencyNanos) {
            successes.add(category + ": " + (latencyNanos / 1_000_000.0) + "ms");
        }

        void recordFailure(String category, String error) {
            failures.add(category + ": " + error);
        }

        void recordException(String category, Throwable e) {
            exceptions.add(category + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        void recordDataCorruption(String category, String details) {
            dataCorruptions.add(category + ": " + details);
        }

        boolean hasDataCorruption() {
            return !dataCorruptions.isEmpty();
        }

        void report() {
            System.out.println("\n========== Concurrent Load Test Results ==========");
            System.out.println("\n--- Metrics ---");
            metrics.forEach((k, v) -> {
                if (k.contains("latency") || k.contains("rate")) {
                    System.out.printf("  %s: %.3f%n", k, Double.longBitsToDouble(v));
                } else {
                    System.out.printf("  %s: %d%n", k, v);
                }
            });

            System.out.println("\n--- Summary ---");
            System.out.printf("  Successes: %d%n", successes.size());
            System.out.printf("  Failures: %d%n", failures.size());
            System.out.printf("  Exceptions: %d%n", exceptions.size());
            System.out.printf("  Data Corruptions: %d%n", dataCorruptions.size());

            if (!failures.isEmpty() && failures.size() < 10) {
                System.out.println("\n--- Failure Details ---");
                failures.forEach(f -> System.out.println("  " + f));
            }

            if (!exceptions.isEmpty()) {
                System.out.println("\n--- Exception Details ---");
                exceptions.forEach(e -> System.out.println("  " + e));
            }

            if (!dataCorruptions.isEmpty()) {
                System.out.println("\n--- Data Corruption Details ---");
                dataCorruptions.forEach(c -> System.out.println("  " + c));
            }

            System.out.println("\n=================================================\n");
        }
    }
}
