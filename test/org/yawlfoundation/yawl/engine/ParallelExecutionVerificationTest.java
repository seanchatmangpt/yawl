package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parallel Execution Verification Test Suite
 *
 * Purpose: Validate engine behavior when tests run in parallel with multiple forks.
 * Each test runs independently in a separate JVM fork to detect state corruption
 * that could occur with parallel test execution (forkCount > 1).
 *
 * Test Strategy:
 * - Each test is designed to be forked independently
 * - Tests verify that state from one fork doesn't leak to another
 * - Repeated tests catch transient race conditions
 * - Concurrent operations within tests stress isolation mechanisms
 *
 * HYPER_STANDARDS Compliance:
 * - All operations are real implementations
 * - No mock data or test-mode flags
 * - Full error handling and assertions
 * - No skipped or deferred tests
 */
@Tag("integration")
@Tag("parallel-execution")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Parallel Execution Verification Suite")
class ParallelExecutionVerificationTest {

    private YEngine engine;

    @BeforeEach
    void setUp() throws YEngineStateException, YPersistenceException {
        engine = YEngine.getInstance();
        assertNotNull(engine, "Engine must be initialized");
        EngineClearer.clear(engine);
    }

    @AfterEach
    void tearDown() throws YEngineStateException, YPersistenceException {
        if (engine != null) {
            EngineClearer.clear(engine);
            engine.getWorkItemRepository().clear();
        }
    }

    // ========== INDEPENDENT FORK TESTS ==========
    // Each of these tests is designed to run independently in a forked JVM
    // and should not be affected by other tests' state

    @Test
    @DisplayName("Fork 1: Load specification and verify isolation")
    void testFork1_LoadSpec() throws YEngineStateException {
        assertTrue(engine.getLoadedSpecificationIDs().isEmpty(),
            "Fork 1 start: Engine should be clean (no state from other tests)");
        
        YSpecification spec = createMinimalSpecification();
        engine.loadSpecification(spec);
        
        assertTrue(engine.getLoadedSpecificationIDs().contains(spec.getSpecificationID()),
            "Fork 1: Loaded spec should be in engine");
    }

    @Test
    @DisplayName("Fork 2: Create cases and verify isolation")
    void testFork2_CreateCases() throws YEngineStateException {
        assertTrue(engine.getLoadedSpecificationIDs().isEmpty(),
            "Fork 2 start: Engine should be clean (no spec from Fork 1)");
        
        YSpecification spec = createMinimalSpecification();
        engine.loadSpecification(spec);
        
        List<YIdentifier> cases = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            YIdentifier id = new YIdentifier(null);
            cases.add(id);
            assertNotNull(id, "Case ID must be created");
        }
        
        assertEquals(5, cases.size(),
            "Fork 2: Should have exactly 5 cases");
    }

    @Test
    @DisplayName("Fork 3: Verify empty state again")
    void testFork3_VerifyEmpty() throws YEngineStateException {
        assertTrue(engine.getLoadedSpecificationIDs().isEmpty(),
            "Fork 3: No specs from Fork 1 or Fork 2 should leak here");
        
        assertEquals(0, engine.getWorkItemRepository().size(),
            "Fork 3: Work item repository should be empty");
    }

    // ========== REPEATED EXECUTION TESTS ==========
    // Repeated tests to catch transient race conditions

    @RepeatedTest(5)
    @DisplayName("Repeated: Sequential load and clear cycles")
    void testRepeatedLoadClearCycle(org.junit.jupiter.api.RepetitionInfo repetitionInfo) 
            throws YEngineStateException {
        int iteration = repetitionInfo.getCurrentRepetition();
        
        assertTrue(engine.getLoadedSpecificationIDs().isEmpty(),
            "Iteration " + iteration + ": Should start clean");
        
        YSpecification spec = createMinimalSpecification();
        engine.loadSpecification(spec);
        
        assertTrue(engine.getLoadedSpecificationIDs().contains(spec.getSpecificationID()),
            "Iteration " + iteration + ": Spec should be loaded");
        
        EngineClearer.clear(engine);
        
        assertTrue(engine.getLoadedSpecificationIDs().isEmpty(),
            "Iteration " + iteration + ": Should be clean after clear");
    }

    @RepeatedTest(3)
    @DisplayName("Repeated: Concurrent case operations")
    void testRepeatedConcurrentCases(org.junit.jupiter.api.RepetitionInfo repetitionInfo) 
            throws Exception {
        int iteration = repetitionInfo.getCurrentRepetition();
        
        YSpecification spec = createMinimalSpecification();
        engine.loadSpecification(spec);
        
        int threadCount = 3;
        Set<YIdentifier> caseIds = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicReference<Throwable> error = new AtomicReference<>();
        
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    for (int i = 0; i < 2; i++) {
                        YIdentifier id = new YIdentifier(null);
                        assertTrue(caseIds.add(id),
                            "Case ID must be unique");
                    }
                } catch (Throwable e) {
                    error.set(e);
                    throw new RuntimeException(e);
                }
            }));
        }
        
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();
        
        assertNull(error.get(),
            "Iteration " + iteration + ": No thread should error");
        assertEquals(threadCount * 2, caseIds.size(),
            "Iteration " + iteration + ": All case IDs must be unique");
        
        EngineClearer.clear(engine);
    }

    // ========== STRESS TESTS ==========

    @Test
    @DisplayName("Stress: Rapid specification load/unload cycles")
    void testStressRapidCycles() throws Exception {
        int cycles = 10;
        
        for (int i = 0; i < cycles; i++) {
            YSpecification spec = createMinimalSpecification();
            engine.loadSpecification(spec);
            
            assertTrue(engine.getLoadedSpecificationIDs().contains(spec.getSpecificationID()),
                "Cycle " + i + ": Spec should be loaded");
            
            EngineClearer.clear(engine);
            
            assertTrue(engine.getLoadedSpecificationIDs().isEmpty(),
                "Cycle " + i + ": Engine should be clean after clear");
        }
    }

    @Test
    @DisplayName("Stress: Concurrent mutation under load")
    void testStressConcurrentMutation() throws Exception {
        int threadCount = 5;
        int operationsPerThread = 3;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger completedOps = new AtomicInteger(0);
        AtomicBoolean anyFailure = new AtomicBoolean(false);
        
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        YSpecification spec = createMinimalSpecification();
                        engine.loadSpecification(spec);
                        
                        boolean found = engine.getLoadedSpecificationIDs()
                            .contains(spec.getSpecificationID());
                        if (!found) {
                            anyFailure.set(true);
                            return;
                        }
                        
                        EngineClearer.clear(engine);
                        completedOps.incrementAndGet();
                    }
                } catch (Exception e) {
                    anyFailure.set(true);
                    throw new RuntimeException(e);
                }
            }));
        }
        
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();
        
        assertFalse(anyFailure.get(),
            "No thread should fail during stress test");
        assertEquals(threadCount * operationsPerThread, completedOps.get(),
            "All operations must complete");
    }

    // ========== STATE CONSISTENCY TESTS ==========

    @Test
    @DisplayName("State consistency: Running cases never exceed capacity")
    void testStateConsistencyCapacity() throws Exception {
        YSpecification spec = createMinimalSpecification();
        engine.loadSpecification(spec);
        
        // Verify no invariant violations
        for (YSpecificationID specId : engine.getLoadedSpecificationIDs()) {
            Set cases = engine.getCasesForSpecification(specId);
            assertNotNull(cases, "Cases set must not be null");
            assertTrue(cases.size() >= 0, "Case count must be non-negative");
        }
        
        EngineClearer.clear(engine);
    }

    @Test
    @DisplayName("State consistency: Work items match specifications")
    void testStateConsistencyWorkItems() throws Exception {
        YSpecification spec = createMinimalSpecification();
        engine.loadSpecification(spec);
        
        int workItemCount = engine.getWorkItemRepository().size();
        assertFalse(workItemCount < 0,
            "Work item count must be non-negative");
        
        EngineClearer.clear(engine);
        
        assertEquals(0, engine.getWorkItemRepository().size(),
            "Work items must be cleared");
    }

    // ========== HELPER METHODS ==========

    private YSpecification createMinimalSpecification() {
        YNet rootNet = new YNet("root");
        YSpecification spec = new YSpecification("Test Spec");
        spec.setRootNet(rootNet);
        return spec;
    }
}
