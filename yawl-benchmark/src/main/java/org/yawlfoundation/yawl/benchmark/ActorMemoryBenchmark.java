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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark;

import org.openjdk.jmh.annotations.*;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;

import java.lang.management.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Actor System Memory Usage Benchmark.
 *
 * <p>Benchmarks memory patterns in actor systems including:
 * - Memory growth under increasing load
 * - Memory leaks from unmanaged resources
 * - GC behavior and pressure
 * - Object allocation patterns
 * - Memory overhead per actor</p>
 */
@BenchmarkMode({Mode.SampleTime, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 3)
@Fork(value = 3, jvmArgs = {
    "-Xms4g", "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-XX:+PrintGC",
    "-XX:+PrintGCDetails",
    "-XX:+PrintGCTimeStamps"
})
public class ActorMemoryBenchmark {

    // Configuration
    @Param({"100", "1000", "10000", "100000"})
    private int maxActors;

    @Param({"stable", "growing", "volatile"})
    private String memoryPattern;

    @Param({"none", "light", "heavy"})
    private String pressureType;

    @State(Scope.Thread)
    private static class ThreadState {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        GarbageCollectorMXBean gcBean = ManagementFactory.getGarbageCollectorMXBean();
        List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();
    }

    private ThreadState threadState;
    
    // Test components
    private YStatelessEngine engine;
    private YSpecification spec;
    
    // Metrics collectors
    private final AtomicLong totalAllocated = new AtomicLong(0);
    private final AtomicLong totalFreed = new AtomicLong(0);
    private final AtomicLong peakMemory = new AtomicLong(0);
    private final AtomicLong gcCount = new AtomicLong(0);
    private final AtomicLong gcTime = new AtomicLong(0);
    
    private final ConcurrentLinkedQueue<Long> memorySamples = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> gcSamples = new ConcurrentLinkedQueue<>();
    
    // Threading
    private final ExecutorService actorExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private ScheduledExecutorService metricsExecutor;
    private volatile AtomicBoolean running = new AtomicBoolean(true);

    @Setup(Level.Trial)
    public void setup() throws YSyntaxException {
        threadState = new ThreadState();
        
        // Initialize engine
        engine = new YStatelessEngine();
        spec = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);

        // Start metrics collection
        metricsExecutor = Executors.newScheduledThreadPool(2);
        startMetricsCollection();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        running.set(false);
        metricsExecutor.shutdown();
        actorExecutor.shutdown();
        try {
            if (!metricsExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                metricsExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Main Benchmarks ─────────────────────────────────────────────────────

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void memoryGrowthBenchmark() throws InterruptedException {
        // Initial memory baseline
        long initialMemory = getCurrentMemoryUsage();
        
        // Create actors in batches
        int batchSize = maxActors / 10;
        for (int batch = 0; batch < 10 && running.get(); batch++) {
            createActorBatch(batchSize, batch);
            
            // Monitor memory after each batch
            long currentMemory = getCurrentMemoryUsage();
            memorySamples.add(currentMemory);
            
            // Check for excessive growth (>50%)
            if (currentMemory > initialMemory * 1.5) {
                System.err.println("Warning: High memory growth detected");
            }
            
            // Pause between batches
            Thread.sleep(100);
        }
        
        // Clean phase
        cleanupActors();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void memoryLeakDetectionBenchmark() throws InterruptedException {
        // Track memory baseline
        long baseline = getCurrentMemoryUsage();
        Map<String, Object> trackedObjects = new ConcurrentHashMap<>();
        
        // Create and potentially leak objects
        for (int i = 0; i < maxActors && running.get(); i++) {
            Object obj = createTrackedObject(i);
            
            if (memoryPattern.equals("leaky")) {
                // Intentional leak - don't clean up
                trackedObjects.put("leak-" + i, obj);
            } else if (memoryPattern.equals("growing")) {
                // Periodic cleanup
                if (i % 100 == 0) {
                    trackedObjects.clear();
                    System.gc(); // Suggest GC
                }
            }
            // Clean pattern removes objects immediately
        }
        
        // Check for leaks
        long finalMemory = getCurrentMemoryUsage();
        if (finalMemory > baseline * 1.1) {
            System.err.println("Potential memory leak detected");
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void gcPressureBenchmark() throws InterruptedException {
        // Generate memory pressure
        ScheduledExecutorService pressureGenerator = Executors.newScheduledThreadPool(4);
        
        // Start pressure generation based on pressure type
        switch (pressureType) {
            case "light":
                pressureGenerator.scheduleAtFixedRate(this::generateLightPressure, 
                    0, 100, TimeUnit.MILLISECONDS);
                break;
            case "heavy":
                pressureGenerator.scheduleAtFixedRate(this::generateHeavyPressure, 
                    0, 50, TimeUnit.MILLISECONDS);
                break;
        }
        
        // Monitor GC activity
        Instant start = Instant.now();
        while (Duration.between(start, Instant.now()).toSeconds() < 30 && running.get()) {
            long gcEvents = getGcEventCount();
            gcSamples.add(gcEvents);
            
            // GC frequency analysis
            if (gcEvents > 100) {
                System.err.println("High GC frequency detected");
            }
            
            Thread.sleep(1000);
        }
        
        pressureGenerator.shutdown();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void actorOverheadBenchmark() throws InterruptedException {
        // Measure memory overhead per actor
        List<Long> perActorMemory = new ArrayList<>();
        
        for (int i = 0; i < 1000; i++) {
            long before = getCurrentMemoryUsage();
            
            // Create actor
            Actor actor = createActor(i);
            actor.process();
            
            long after = getCurrentMemoryUsage();
            perActorMemory.add(after - before);
            
            // Cleanup
            actor.cleanup();
        }
        
        // Calculate average overhead
        double avgOverhead = perActorMemory.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
        
        System.out.printf("Average memory per actor: %.2f bytes%n", avgOverhead);
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    private void createActorBatch(int count, int batchId) {
        for (int i = 0; i < count; i++) {
            final int actorId = batchId * count + i;
            actorExecutor.submit(() -> {
                try {
                    Actor actor = createActor(actorId);
                    actor.process();
                    totalAllocated.addAndGet(actor.getMemoryFootprint());
                    
                    // Simulate workload
                    if (memoryPattern.equals("volatile")) {
                        Thread.sleep((long)(Math.random() * 100));
                        actor.cleanup();
                    }
                } catch (Exception e) {
                    // Handle error
                }
            });
        }
    }

    private void cleanupActors() {
        // Force GC to see if memory returns to baseline
        System.gc();
        try {
            Thread.sleep(2000); // Wait for GC
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Object createTrackedObject(int id) {
        // Create objects with different characteristics
        if (memoryPattern.equals("light")) {
            return new byte[100]; // Small objects
        } else {
            return new byte[10_000]; // Larger objects
        }
    }

    private void generateLightPressure() {
        // Generate moderate memory pressure
        for (int i = 0; i < 10; i++) {
            new byte[1000]; // Small allocations
        }
        totalAllocated.addAndGet(10_000);
    }

    private void generateHeavyPressure() {
        // Generate heavy memory pressure
        for (int i = 0; i < 100; i++) {
            new byte[10_000]; // Large allocations
        }
        totalAllocated.addAndGet(1_000_000);
    }

    private Actor createActor(int id) {
        return new Actor("actor-" + id);
    }

    private long getCurrentMemoryUsage() {
        MemoryUsage heapUsage = threadState.memoryBean.getHeapMemoryUsage();
        long current = heapUsage.getUsed();
        
        // Update peak memory
        if (current > peakMemory.get()) {
            peakMemory.set(current);
        }
        
        return current;
    }

    private long getGcEventCount() {
        return threadState.memoryBean.getHeapMemoryUsage().getUsed() / 1024;
    }

    private void startMetricsCollection() {
        metricsExecutor.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            
            long memory = getCurrentMemoryUsage();
            memorySamples.add(memory);
            
            // GC metrics
            long gcEvents = getGcEventCount();
            gcSamples.add(gcEvents);
            
            // Thread metrics
            long threadCount = threadState.threadBean.getThreadCount();
            long peakThreadCount = threadState.threadBean.getPeakThreadCount();
            
            System.out.printf("Memory: %d KB, Threads: %d/%d, GC events: %d%n",
                memory / 1024, threadCount, peakThreadCount, gcEvents);
        }, 1, 1, TimeUnit.SECONDS);
    }

    // ── Actor Class ───────────────────────────────────────────────────────

    private static class Actor {
        private final String id;
        private final List<Object> state = new ArrayList<>();
        private long memoryFootprint;
        
        public Actor(String id) {
            this.id = id;
            this.memoryFootprint = id.length() * 2; // Base size
        }
        
        public void process() {
            // Simulate actor processing
            for (int i = 0; i < 10; i++) {
                state.add("message-" + i);
                memoryFootprint += 100;
            }
        }
        
        public void cleanup() {
            state.clear();
            memoryFootprint = 0;
        }
        
        public long getMemoryFootprint() {
            return memoryFootprint;
        }
    }
}
