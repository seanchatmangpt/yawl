package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performance benchmark for ScopedValue vs ThreadLocal approaches.
 *
 * This benchmark compares:
 * - ScopedValue binding and retrieval performance
 * - ThreadLocal binding and retrieval performance
 * - Virtual thread compatibility
 * - Memory allocation patterns
 * - Concurrency throughput
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
@DisplayName("ScopedValue Performance Benchmark")
class ScopedValueBenchmark {

    private ScopedValueYEngine scopedEngine;
    private ThreadLocal<YEngine> threadLocalEngine;
    private final AtomicInteger counter = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        scopedEngine = new ScopedValueYEngine();

        // Initialize thread local engine for comparison
        threadLocalEngine = new ThreadLocal<>();
        threadLocalEngine.set(scopedEngine);
    }

    // ==================== Basic Operations ====================

    @Benchmark
    @DisplayName("ScopedValue - Bind and Get")
    void scopedValueBindAndGet(Blackhole bh) {
        ScopedEngineContext.withEngine(scopedEngine, () -> {
            YEngine engine = ScopedEngineContext.current();
            bh.consume(engine);
            return null;
        });
    }

    @Benchmark
    @DisplayName("ThreadLocal - Get")
    void threadLocalGet(Blackhole bh) {
        YEngine engine = threadLocalEngine.get();
        bh.consume(engine);
    }

    @Benchmark
    @DisplayName("ScopedValue - Check Bound")
    void scopedValueCheckBound(Blackhole bh) {
        boolean isBound = ScopedEngineContext.isEngineBound();
        bh.consume(isBound);
    }

    @Benchmark
    @DisplayName("ThreadLocal - Check Not Null")
    void threadLocalCheckNotNull(Blackhole bh) {
        boolean hasEngine = threadLocalEngine.get() != null;
        bh.consume(hasEngine);
    }

    // ==================== Concurrency ====================

    @Benchmark
    @Group("concurrent")
    @GroupThreads(4)
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @DisplayName("ScopedValue - Concurrent Access")
    void scopedValueConcurrent(Blackhole bh) {
        ScopedEngineContext.withEngine(scopedEngine, () -> {
            YEngine engine = ScopedEngineContext.current();
            bh.consume(engine);
            return null;
        });
    }

    @Benchmark
    @Group("concurrent")
    @GroupThreads(4)
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @DisplayName("ThreadLocal - Concurrent Access")
    void threadLocalConcurrent(Blackhole bh) {
        YEngine engine = threadLocalEngine.get();
        bh.consume(engine);
    }

    // ==================== Nested Scopes ====================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @DisplayName("ScopedValue - Nested Scope")
    void scopedValueNestedScope(Blackhole bh) {
        ScopedEngineContext.withEngine(scopedEngine, () -> {
            ScopedEngineContext.inNestedScope(() -> {
                YEngine engine = ScopedEngineContext.current();
                bh.consume(engine);
                return null;
            });
            return null;
        });
    }

    // ==================== Error Handling ====================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @DisplayName("ScopedValue - Exception Handling")
    void scopedValueExceptionHandling(Blackhole bh) {
        try {
            ScopedEngineContext.withEngine(scopedEngine, () -> {
                throw new RuntimeException("Test exception");
            });
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @DisplayName("ThreadLocal - Exception Handling")
    void threadLocalExceptionHandling(Blackhole bh) {
        try {
            // Simulate some operation that might throw
            throw new RuntimeException("Test exception");
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    // ==================== Virtual Thread Compatibility ====================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @DisplayName("ScopedValue - Virtual Thread")
    void scopedValueVirtualThread(Blackhole bh) {
        Thread.ofVirtual().start(() -> {
            ScopedEngineContext.withEngine(scopedEngine, () -> {
                YEngine engine = ScopedEngineContext.current();
                bh.consume(engine);
                return null;
            });
        }).join();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @DisplayName("ThreadLocal - Virtual Thread")
    void threadLocalVirtualThread(Blackhole bh) {
        Thread.ofVirtual().start(() -> {
            YEngine engine = threadLocalEngine.get();
            bh.consume(engine);
        }).join();
    }

    // ==================== Memory Allocation ====================

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Warmup(iterations = 3)
    @Measurement(iterations = 5)
    @DisplayName("ScopedValue - Memory Allocation")
    void scopedValueMemoryAllocation(Blackhole bh) {
        ScopedEngineContext.withEngine(scopedEngine, () -> {
            // Create some objects to measure allocation
            String[] data = new String[10];
            for (int i = 0; i < data.length; i++) {
                data[i] = "test-" + i;
            }
            bh.consume(data);
            return null;
        });
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Warmup(iterations = 3)
    @Measurement(iterations = 5)
    @DisplayName("ThreadLocal - Memory Allocation")
    void threadLocalMemoryAllocation(Blackhole bh) {
        // Create some objects to measure allocation
        String[] data = new String[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = "test-" + i;
        }
        bh.consume(data);
    }

    // ==================== Throughput Tests ====================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @DisplayName("ScopedValue - High Throughput")
    void scopedValueHighThroughput(Blackhole bh) {
        ScopedEngineContext.withEngine(scopedEngine, () -> {
            YEngine engine = ScopedEngineContext.current();
            bh.consume(engine);
            return counter.incrementAndGet();
        });
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @DisplayName("ThreadLocal - High Throughput")
    void threadLocalHighThroughput(Blackhole bh) {
        YEngine engine = threadLocalEngine.get();
        bh.consume(engine);
        counter.incrementAndGet();
    }

    // ==================== Structured Concurrency ====================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @DisplayName("ScopedValue - Structured Concurrency")
    void scopedValueStructuredConcurrency(Blackhole bh) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // Fork multiple tasks with the same engine context
            for (int i = 0; i < 5; i++) {
                final int taskIndex = i;
                scope.fork(() -> {
                    ScopedEngineContext.withEngine(scopedEngine, () -> {
                        YEngine engine = ScopedEngineContext.current();
                        bh.consume(engine);
                        return "task-" + taskIndex;
                    });
                });
            }
            scope.join();
            scope.throwIfFailed();
        }
    }

    // ==================== Mixed Workload ====================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @DisplayName("ScopedValue - Mixed Workload")
    void scopedValueMixedWorkload(Blackhole bh) {
        // Mix of different operations
        ScopedEngineContext.withEngine(scopedEngine, () -> {
            // Read
            YEngine engine = ScopedEngineContext.current();
            bh.consume(engine);

            // Check bound
            boolean isBound = ScopedEngineContext.isEngineBound();
            bh.consume(isBound);

            // Nested operation
            ScopedEngineContext.inNestedScope(() -> {
                YEngine nestedEngine = ScopedEngineContext.current();
                bh.consume(nestedEngine);
                return null;
            });

            return "mixed-workload";
        });
    }
}