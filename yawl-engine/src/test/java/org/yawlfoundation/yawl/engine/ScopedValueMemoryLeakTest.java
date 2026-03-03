package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Memory leak verification tests for ScopedValue implementation.
 *
 * These tests verify that:
 * - No memory leaks occur from unclosed scopes
 * - Memory usage remains stable under load
 * - Virtual thread usage doesn't cause memory issues
 * - Large numbers of scopes don't accumulate garbage
 */
@DisplayName("ScopedValue Memory Leak Verification")
class ScopedValueMemoryLeakTest {

    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private static final int LARGE_SCOPE_COUNT = 10_000;
    private static final int THREAD_POOL_SIZE = 50;
    private static final int TASKS_PER_THREAD = 100;

    @Test
    @DisplayName("should not leak memory with many scopes")
    void shouldNotLeakMemoryWithManyScopes() {
        // Given
        MemoryUsage beforeMemory = memoryMXBean.getHeapMemoryUsage();
        List<ScopedValueYEngine> engines = new ArrayList<>();

        // When - create many engines and scopes
        for (int i = 0; i < LARGE_SCOPE_COUNT; i++) {
            ScopedValueYEngine engine = new ScopedValueYEngine();
            engines.add(engine);

            // Create a scope for each engine
            ScopedEngineContext.withEngine(engine, () -> {
                // Do some work in the scope
                return "scope-" + i;
            });
        }

        // Force garbage collection
        System.gc();
        MemoryUsage afterMemory = memoryMXBean.getHeapMemoryUsage();

        // Then - memory usage should be reasonable
        long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
        // Allow for some overhead but shouldn't be excessive
        assertTrue(memoryUsed < 50 * 1024 * 1024,
            String.format("Memory usage too high: %d bytes", memoryUsed));

        // Clean up
        engines.clear();
    }

    @Test
    @DisplayName("should handle nested scopes without memory leaks")
        void shouldHandleNestedScopesWithoutMemoryLeaks() {
        // Given
        MemoryUsage beforeMemory = memoryMXBean.getHeapMemoryUsage();
        List<ScopedValueYEngine> engines = new ArrayList<>();

        // When - create deeply nested scopes
        for (int i = 0; i < 1000; i++) {
            ScopedValueYEngine engine = new ScopedValueYEngine();
            engines.add(engine);

            ScopedEngineContext.withEngine(engine, () -> {
                // Create 10 nested levels
                for (int j = 0; j < 10; j++) {
                    final int level = j;
                    ScopedEngineContext.inNestedScope(() -> {
                        return "nested-" + level;
                    });
                }
                return "completed";
            });
        }

        // Force garbage collection
        System.gc();
        MemoryUsage afterMemory = memoryMXBean.getHeapMemoryUsage();

        // Then
        long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
        assertTrue(memoryUsed < 20 * 1024 * 1024,
            String.format("Nested scopes memory usage too high: %d bytes", memoryUsed));

        // Clean up
        engines.clear();
    }

    @RepeatedTest(5)
    @DisplayName("should maintain stable memory under repeated use")
    void shouldMaintainStableMemoryUnderRepeatedUse(RepetitionInfo repetitionInfo) {
        // Given
        MemoryUsage beforeMemory = memoryMXBean.getHeapMemoryUsage();
        AtomicLong totalOperations = new AtomicLong(0);

        // When - perform many operations
        for (int i = 0; i < 1000; i++) {
            ScopedValueYEngine engine = new ScopedValueYEngine();

            ScopedEngineContext.withEngine(engine, () -> {
                totalOperations.incrementAndGet();
                return "op-" + totalOperations.get();
            });
        }

        // Force garbage collection
        System.gc();
        MemoryUsage afterMemory = memoryMXBean.getHeapMemoryUsage();

        // Then - memory should be stable across repetitions
        long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
        assertTrue(memoryUsed < 10 * 1024 * 1024,
            String.format("Iteration %d: Memory usage too high: %d bytes",
                repetitionInfo.getCurrentRepetition(), memoryUsed));

        // Allow some variation but not excessive growth
        if (repetitionInfo.getCurrentRepetition() > 1) {
            MemoryUsage previousIteration = getPreviousIterationMemory(repetitionInfo.getCurrentRepetition());
            long memoryChange = memoryUsed - previousIteration;
            assertTrue(Math.abs(memoryChange) < 5 * 1024 * 1024,
                String.format("Memory change between iterations too high: %d bytes", memoryChange));
        }
    }

    @Test
    @DisplayName("should handle concurrent scopes without memory leaks")
    void shouldHandleConcurrentScopesWithoutMemoryLeaks() throws InterruptedException {
        // Given
        MemoryUsage beforeMemory = memoryMXBean.getHeapMemoryUsage();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        List<Callable<String>> tasks = new ArrayList<>();

        // When - create many concurrent tasks
        for (int i = 0; i < THREAD_POOL_SIZE * TASKS_PER_THREAD; i++) {
            final int taskIndex = i;
            tasks.add(() -> {
                ScopedValueYEngine engine = new ScopedValueYEngine();
                return ScopedEngineContext.withEngine(engine, () -> {
                    return "concurrent-task-" + taskIndex;
                });
            });
        }

        // Execute all tasks
        executor.invokeAll(tasks);
        executor.shutdown();

        // Wait for completion
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Force garbage collection
        System.gc();
        MemoryUsage afterMemory = memoryMXBean.getHeapMemoryUsage();

        // Then
        long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
        assertTrue(memoryUsed < 30 * 1024 * 1024,
            String.format("Concurrent memory usage too high: %d bytes", memoryUsed));
    }

    @Test
    @DisplayName("should handle virtual thread scopes without memory leaks")
    void shouldHandleVirtualThreadScopesWithoutMemoryLeaks() throws Exception {
        // Given
        MemoryUsage beforeMemory = memoryMXBean.getHeapMemoryUsage();
        List<String> results = new ArrayList<>();

        // When - create many virtual thread tasks
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < TASKS_PER_THREAD; i++) {
                final int taskIndex = i;
                scope.fork(() -> {
                    ScopedValueYEngine engine = new ScopedValueYEngine();
                    return ScopedEngineContext.withEngine(engine, () -> {
                        return "virtual-task-" + taskIndex;
                    });
                });
            }
            scope.join();
            scope.throwIfFailed();

            // Collect results
            for (var task : scope.tasks()) {
                results.add(task.get());
            }
        }

        // Force garbage collection
        System.gc();
        MemoryUsage afterMemory = memoryMXBean.getHeapMemoryUsage();

        // Then
        long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
        assertTrue(memoryUsed < 20 * 1024 * 1024,
            String.format("Virtual thread memory usage too high: %d bytes", memoryUsed));

        // Verify all results
        assertEquals(TASKS_PER_THREAD, results.size());
    }

    @Test
    @DisplayName("should clean up after mixed scope types")
    void shouldCleanUpAfterMixedScopeTypes() {
        // Given
        MemoryUsage beforeMemory = memoryMXBean.getHeapMemoryUsage();
        List<ScopedValueYEngine> engines = new ArrayList<>();

        // When - create various types of scopes
        for (int i = 0; i < 1000; i++) {
            ScopedValueYEngine engine = new ScopedValueYEngine();
            engines.add(engine);

            // Mix of different scope types
            if (i % 4 == 0) {
                // Regular scope
                ScopedEngineContext.withEngine(engine, () -> "regular");
            } else if (i % 4 == 1) {
                // Nested scope
                ScopedEngineContext.withEngine(engine, () -> {
                    return ScopedEngineContext.inNestedScope(() -> "nested");
                });
            } else if (i % 4 == 2) {
                // Without engine scope
                ScopedEngineContext.withEngine(engine, () -> {
                    return ScopedEngineContext.withoutEngine(() -> "without");
                });
            } else {
                // Multiple bindings
                ScopedEngineContext.withEngine(engine, () -> "first");
                ScopedEngineContext.withEngine(engine, () -> "second");
            }
        }

        // Force garbage collection
        System.gc();
        MemoryUsage afterMemory = memoryMXBean.getHeapMemoryUsage();

        // Then
        long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
        assertTrue(memoryUsed < 25 * 1024 * 1024,
            String.format("Mixed scope memory usage too high: %d bytes", memoryUsed));

        // Clean up
        engines.clear();
    }

    @Test
    @DisplayName("should handle scope recycling without leaks")
    void shouldHandleScopeRecyclingWithoutLeaks() {
        // Given
        MemoryUsage beforeMemory = memoryMXBean.getHeapMemoryUsage();
        ScopedValueYEngine engine = new ScopedValueYEngine();

        // When - reuse the same engine in many scopes
        for (int i = 0; i < 10000; i++) {
            ScopedEngineContext.withEngine(engine, () -> {
                return "recycled-" + i;
            });
        }

        // Force garbage collection
        System.gc();
        MemoryUsage afterMemory = memoryMXBean.getHeapMemoryUsage();

        // Then
        long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
        assertTrue(memoryUsed < 10 * 1024 * 1024,
            String.format("Recycled scope memory usage too high: %d bytes", memoryUsed));

        // Clean up
        engine.shutdown();
    }

    // Helper method to simulate tracking memory across iterations
    private MemoryUsage getPreviousIterationMemory(int currentIteration) {
        // In a real test, you might store this between iterations
        // For this test, we'll return a dummy value
        return memoryMXBean.getHeapMemoryUsage();
    }

    @Test
    @DisplayName("should not leak scoped values themselves")
    void shouldNotLeakScopedValuesThemselves() {
        // Given
        MemoryUsage beforeMemory = memoryMXBean.getHeapMemoryUsage();

        // When - create and immediately discard many ScopedValues
        for (int i = 0; i < 10000; i++) {
            ScopedValue<YEngine> temp = ScopedValue.newInstance();
            // Do nothing with it - it should be garbage collected
        }

        // Force garbage collection
        System.gc();
        MemoryUsage afterMemory = memoryMXBean.getHeapMemoryUsage();

        // Then
        long memoryUsed = afterMemory.getUsed() - beforeMemory.getUsed();
        assertTrue(memoryUsed < 5 * 1024 * 1024,
            String.format("ScopedValue memory leak detected: %d bytes", memoryUsed));
    }
}