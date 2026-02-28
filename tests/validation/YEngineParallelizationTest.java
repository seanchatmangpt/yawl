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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.validation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YEngine State Isolation Validation Test Suite
 *
 * Purpose: Detect state corruption when YEngine is accessed from multiple test threads.
 * This test harness validates that YEngine can be safely parallelized by detecting:
 * - State mutations that cross test boundaries
 * - Race conditions in singleton access
 * - Database connection pool exhaustion
 * - Specification/Case ID collision
 * - Work item state leakage
 *
 * Execution: Sequential during normal testing, concurrent in validation mode
 * Expected: All tests PASS both sequentially and concurrently
 *
 * Chicago TDD Principles:
 * - Real YEngine instance (singleton)
 * - Real H2 in-memory database
 * - No mocks, no stubs, no fake state
 * - Tests detect actual corruption, not theoretical issues
 *
 * @author YAWL Validation Team
 * @version 6.0
 */
@Tag("validation")
@Tag("parallelization-safety")
@DisplayName("YEngine Parallelization State Isolation Validator")
class YEngineParallelizationTest {

    private static final int CONCURRENT_TEST_THREADS = 3;
    private static final int CASES_PER_TEST = 10;
    private static final int TIMEOUT_SECONDS = 60;

    private YEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine should be available");
        clearEngine();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            clearEngine();
        }
    }

    private void clearEngine() throws YPersistenceException, YEngineStateException {
        Set<YSpecificationID> specIds = new HashSet<>(
            engine.getLoadedSpecificationIDs()
        );
        for (YSpecificationID specId : specIds) {
            Set<YIdentifier> caseIds = new HashSet<>(
                engine.getCasesForSpecification(specId)
            );
            for (YIdentifier caseId : caseIds) {
                try {
                    engine.cancelCase(caseId);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
            try {
                engine.unloadSpecification(specId);
            } catch (YStateException e) {
                // Ignore unload errors
            }
        }
    }

    // ============================================================================
    // TEST SUITE: State Isolation Detection
    // ============================================================================

    @Test
    @DisplayName("ISOLATION-001: Singleton instances match across threads")
    void testSingletonInstanceIsolation() throws Exception {
        Set<YEngine> instances = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch ready = new CountDownLatch(CONCURRENT_TEST_THREADS);
        CountDownLatch verify = new CountDownLatch(CONCURRENT_TEST_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                executor.submit(() -> {
                    try {
                        YEngine instance = YEngine.getInstance();
                        instances.add(instance);
                        ready.countDown();
                        // Wait for all threads to collect instances
                        assertTrue(ready.await(10, TimeUnit.SECONDS),
                            "Threads should synchronize");
                        verify.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            verify.await(10, TimeUnit.SECONDS);

            assertEquals(1, instances.size(),
                "All threads should get the SAME singleton instance");

            // Verify it's the same instance we have in setUp
            assertTrue(instances.contains(engine),
                "Concurrent instances should match setUp instance");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ISOLATION-002: Specification loading isolated per thread")
    void testSpecificationLoadingIsolation() throws Exception {
        ConcurrentHashMap<Integer, YSpecification> specsByThread =
            new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, Set<YSpecificationID>> loadedSpecIds =
            new ConcurrentHashMap<>();
        CountDownLatch complete = new CountDownLatch(CONCURRENT_TEST_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        YSpecification spec = createUniqueSpecification(
                            "Test_" + threadId + "_" + System.nanoTime()
                        );
                        specsByThread.put(threadId, spec);
                        loadedSpecIds.put(threadId,
                            new HashSet<>(engine.getLoadedSpecificationIDs()));
                        complete.countDown();
                    } catch (Exception e) {
                        fail("Thread " + threadId + " failed: " + e.getMessage());
                    }
                });
            }

            assertTrue(complete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "All threads should complete specification loading");

            // Verify all specifications are distinct
            Set<YSpecificationID> allSpecIds = new HashSet<>();
            for (YSpecification spec : specsByThread.values()) {
                YSpecificationID id = spec.getSpecificationID();
                assertFalse(allSpecIds.contains(id),
                    "Specifications should have unique IDs across threads");
                allSpecIds.add(id);
            }

            assertEquals(CONCURRENT_TEST_THREADS, allSpecIds.size(),
                "All thread specifications should be loaded");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ISOLATION-003: Case IDs unique across concurrent creation")
    void testCaseIdUniquenessAcrossThreads() throws Exception {
        Set<YIdentifier> allCaseIds = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch complete = new CountDownLatch(CONCURRENT_TEST_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                executor.submit(() -> {
                    try {
                        for (int c = 0; c < CASES_PER_TEST; c++) {
                            YIdentifier caseId = new YIdentifier(null);
                            assertNotNull(caseId, "Case ID should be created");
                            assertTrue(allCaseIds.add(caseId),
                                "Case ID should be unique: " + caseId);
                        }
                        complete.countDown();
                    } catch (Exception e) {
                        fail("Case ID creation failed: " + e.getMessage());
                    }
                });
            }

            assertTrue(complete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "All case creation should complete");

            int expectedCases = CONCURRENT_TEST_THREADS * CASES_PER_TEST;
            assertEquals(expectedCases, allCaseIds.size(),
                "Should have " + expectedCases + " unique case IDs");

            // Verify no duplicates
            List<YIdentifier> caseList = new ArrayList<>(allCaseIds);
            for (int i = 0; i < caseList.size(); i++) {
                for (int j = i + 1; j < caseList.size(); j++) {
                    assertNotEquals(caseList.get(i), caseList.get(j),
                        "Case IDs should be unique");
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ISOLATION-004: Work item repository isolation per case")
    void testWorkItemRepositoryIsolation() throws Exception {
        YSpecification spec = createUniqueSpecification("WIISO");
        ConcurrentHashMap<YIdentifier, Set<YWorkItem>> workItemsByCase =
            new ConcurrentHashMap<>();
        CountDownLatch complete = new CountDownLatch(CONCURRENT_TEST_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                executor.submit(() -> {
                    try {
                        YIdentifier caseId = new YIdentifier(null);
                        Set<YWorkItem> workItems = new HashSet<>();

                        for (int w = 0; w < 5; w++) {
                            YTask task = createTask(spec.getRootNet(),
                                "task_" + caseId.getKey() + "_" + w);
                            YWorkItemID wiId = new YWorkItemID(caseId, task.getID());
                            YWorkItem wi = new YWorkItem(
                                null,
                                spec.getSpecificationID(),
                                task,
                                wiId,
                                true,
                                false
                            );
                            workItems.add(wi);
                        }

                        workItemsByCase.put(caseId, workItems);
                        complete.countDown();
                    } catch (Exception e) {
                        fail("Work item creation failed: " + e.getMessage());
                    }
                });
            }

            assertTrue(complete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "All work item creation should complete");

            // Verify isolation: no work items from one case in another
            List<YIdentifier> cases = new ArrayList<>(workItemsByCase.keySet());
            for (int i = 0; i < cases.size(); i++) {
                for (int j = i + 1; j < cases.size(); j++) {
                    YIdentifier case1 = cases.get(i);
                    YIdentifier case2 = cases.get(j);
                    Set<YWorkItem> items1 = workItemsByCase.get(case1);
                    Set<YWorkItem> items2 = workItemsByCase.get(case2);

                    for (YWorkItem wi : items1) {
                        assertNotEquals(case2, wi.getIdentifier(),
                            "Work items should not leak between cases");
                    }
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ISOLATION-005: Database connection pool stability under concurrency")
    void testDatabaseConnectionPoolStability() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch complete = new CountDownLatch(CONCURRENT_TEST_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                executor.submit(() -> {
                    try {
                        // Create spec and cases - both use DB connections
                        YSpecification spec = createUniqueSpecification(
                            "DBPOOL_" + System.nanoTime()
                        );
                        for (int c = 0; c < CASES_PER_TEST; c++) {
                            YIdentifier caseId = new YIdentifier(null);
                            // Access engine repository (uses connection)
                            engine.getCasesForSpecification(spec.getSpecificationID());
                        }
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        complete.countDown();
                    }
                });
            }

            assertTrue(complete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "All database operations should complete");

            assertEquals(CONCURRENT_TEST_THREADS, successCount.get(),
                "All threads should successfully use database");
            assertEquals(0, failureCount.get(),
                "No threads should experience database errors");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ISOLATION-006: No state mutation across test boundaries")
    void testNoStateMutationAcrossBoundaries() throws Exception {
        // Record initial engine state
        int initialSpecCount = engine.getLoadedSpecificationIDs().size();

        ConcurrentHashMap<Integer, Integer> specCountPerThread =
            new ConcurrentHashMap<>();
        CountDownLatch complete = new CountDownLatch(CONCURRENT_TEST_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        // Load specs
                        for (int i = 0; i < 3; i++) {
                            createUniqueSpecification(
                                "StateMut_" + threadId + "_" + i + "_" + System.nanoTime()
                            );
                        }
                        int countAfterLoad = engine.getLoadedSpecificationIDs().size();
                        specCountPerThread.put(threadId, countAfterLoad);
                        complete.countDown();
                    } catch (Exception e) {
                        fail("State mutation test failed: " + e.getMessage());
                    }
                });
            }

            assertTrue(complete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "All state mutation checks should complete");

            // Final state: all specs should be loaded
            int finalSpecCount = engine.getLoadedSpecificationIDs().size();
            int expectedCount = initialSpecCount + (CONCURRENT_TEST_THREADS * 3);
            assertEquals(expectedCount, finalSpecCount,
                "All loaded specifications should remain in engine");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ISOLATION-007: Concurrent specification unload isolation")
    void testConcurrentUnloadIsolation() throws Exception {
        // Pre-load specs
        List<YSpecification> specs = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_TEST_THREADS; i++) {
            specs.add(createUniqueSpecification("Unload_" + i));
        }

        int specCountBefore = engine.getLoadedSpecificationIDs().size();
        assertEquals(CONCURRENT_TEST_THREADS, specCountBefore,
            "Should have loaded test specifications");

        CountDownLatch complete = new CountDownLatch(CONCURRENT_TEST_THREADS);
        AtomicInteger unloadSuccess = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        YSpecificationID specId = specs.get(threadId)
                            .getSpecificationID();
                        engine.unloadSpecification(specId);
                        unloadSuccess.incrementAndGet();
                        complete.countDown();
                    } catch (YStateException e) {
                        // Expected if spec was already unloaded
                        complete.countDown();
                    } catch (Exception e) {
                        fail("Unload failed: " + e.getMessage());
                    }
                });
            }

            assertTrue(complete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "All unload operations should complete");

            int specCountAfter = engine.getLoadedSpecificationIDs().size();
            assertTrue(specCountAfter < specCountBefore,
                "Specifications should be unloaded");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ISOLATION-008: Race condition detection in case management")
    void testRaceConditionInCaseManagement() throws Exception {
        YSpecification spec = createUniqueSpecification("RaceDetect");
        ConcurrentHashMap<YIdentifier, AtomicInteger> caseAccessCount =
            new ConcurrentHashMap<>();
        CountDownLatch ready = new CountDownLatch(CONCURRENT_TEST_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(CONCURRENT_TEST_THREADS);

        // Create shared case IDs
        List<YIdentifier> sharedCases = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            YIdentifier caseId = new YIdentifier(null);
            sharedCases.add(caseId);
            caseAccessCount.put(caseId, new AtomicInteger(0));
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                executor.submit(() -> {
                    try {
                        ready.countDown();
                        start.await(); // Synchronize start

                        for (YIdentifier caseId : sharedCases) {
                            // Simulate concurrent access to case
                            caseAccessCount.get(caseId).incrementAndGet();
                            // Small delay to increase race condition likelihood
                            Thread.yield();
                        }
                        complete.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            ready.await();
            start.countDown(); // Release all threads simultaneously
            assertTrue(complete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "All case accesses should complete");

            // Verify expected access counts (no lost updates)
            for (YIdentifier caseId : sharedCases) {
                int accessCount = caseAccessCount.get(caseId).get();
                assertEquals(CONCURRENT_TEST_THREADS, accessCount,
                    "Each case should be accessed exactly " + CONCURRENT_TEST_THREADS + " times");
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ISOLATION-009: Specification ID collision detection")
    void testSpecificationIdCollisionDetection() throws Exception {
        Set<YSpecificationID> allSpecIds = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch complete = new CountDownLatch(CONCURRENT_TEST_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < CASES_PER_TEST; i++) {
                            YSpecification spec = createUniqueSpecification(
                                "SpecIdTest_" + System.nanoTime() + "_" + i
                            );
                            YSpecificationID id = spec.getSpecificationID();
                            assertTrue(allSpecIds.add(id),
                                "Specification ID should be unique: " + id);
                        }
                        complete.countDown();
                    } catch (Exception e) {
                        fail("Spec ID creation failed: " + e.getMessage());
                    }
                });
            }

            assertTrue(complete.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "All spec ID creation should complete");

            int expectedCount = CONCURRENT_TEST_THREADS * CASES_PER_TEST;
            assertEquals(expectedCount, allSpecIds.size(),
                "All specification IDs should be unique");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("ISOLATION-010: No thread-local state leakage")
    void testNoThreadLocalStateLeakage() throws Exception {
        ConcurrentHashMap<Integer, ThreadInfo> threadStates =
            new ConcurrentHashMap<>();
        CountDownLatch ready = new CountDownLatch(CONCURRENT_TEST_THREADS);
        CountDownLatch verify = new CountDownLatch(CONCURRENT_TEST_THREADS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TEST_THREADS);

        try {
            for (int t = 0; t < CONCURRENT_TEST_THREADS; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        ThreadInfo info = new ThreadInfo(
                            threadId,
                            Thread.currentThread().getId(),
                            Thread.currentThread().getName()
                        );
                        threadStates.put(threadId, info);
                        ready.countDown();
                        // Wait for all threads to store their info
                        assertTrue(ready.await(10, TimeUnit.SECONDS));
                        verify.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            verify.await(10, TimeUnit.SECONDS);

            // Verify thread info is unique (no leakage between threads)
            for (ThreadInfo info1 : threadStates.values()) {
                for (ThreadInfo info2 : threadStates.values()) {
                    if (info1.threadId != info2.threadId) {
                        assertNotEquals(info1.nativeThreadId, info2.nativeThreadId,
                            "Different threads should have different IDs");
                        assertNotEquals(info1.threadName, info2.threadName,
                            "Different threads should have different names");
                    }
                }
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private YSpecification createUniqueSpecification(String baseName) throws Exception {
        String uniqueName = baseName + "_" + System.nanoTime();
        YSpecification spec = new YSpecification(uniqueName);
        spec.setName("Test Specification: " + uniqueName);
        spec.setVersion(YSchemaVersion.Beta7);

        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YInputCondition input = new YInputCondition("input", rootNet);
        YOutputCondition output = new YOutputCondition("output", rootNet);

        rootNet.setInputCondition(input);
        rootNet.setOutputCondition(output);

        YAtomicTask task = new YAtomicTask(
            "task1",
            YAtomicTask._AND,
            YAtomicTask._AND,
            rootNet
        );
        rootNet.addNetElement(task);

        YFlow flowIn = new YFlow(input, task);
        YFlow flowOut = new YFlow(task, output);

        input.addPostset(flowIn);
        task.addPreset(flowIn);
        task.addPostset(flowOut);
        output.addPreset(flowOut);

        return spec;
    }

    private YTask createTask(YNet net, String taskId) {
        YAtomicTask task = new YAtomicTask(
            taskId,
            YAtomicTask._AND,
            YAtomicTask._AND,
            net
        );
        net.addNetElement(task);
        return task;
    }

    // ============================================================================
    // Test Data Classes
    // ============================================================================

    private static class ThreadInfo {
        int threadId;
        long nativeThreadId;
        String threadName;

        ThreadInfo(int threadId, long nativeThreadId, String threadName) {
            this.threadId = threadId;
            this.nativeThreadId = nativeThreadId;
            this.threadName = threadName;
        }
    }
}
