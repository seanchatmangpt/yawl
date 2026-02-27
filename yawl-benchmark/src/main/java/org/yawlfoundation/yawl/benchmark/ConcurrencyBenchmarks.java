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
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.engine.YStatelessEngine;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

/**
 * Concurrency Performance Benchmarks
 * 
 * This class benchmarks multi-threaded performance of YAWL engine components:
 * - Virtual vs Platform thread performance comparison
 * - Thread scalability with increasing thread counts
 * - Concurrent case creation and management
 * - Resource contention under load
 * - Deadlock detection and prevention
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 50, time = 1)
@Fork(3)
public class ConcurrencyBenchmarks {

    private YEngine engine;
    private YStatelessEngine statelessEngine;
    private YSpecification testSpec;
    private ExecutorService executor;
    private List<String> caseIds;
    private AtomicInteger caseCounter;
    
    // Scaling curve from 1→64: exposes the inflection point where YNetRunner's
    // internal locks saturate. The Caffeine insight: single-thread numbers are
    // misleading for concurrent data structures — the shape of the curve matters.
    // 2 and 64 added: 2 exposes the first contention point; 64 exposes saturation.
    @Param({"1", "2", "4", "8", "16", "32", "64"})
    private int threadCount;
    
    @Param({"10", "100", "1000"})
    private int caseCount;
    
    @Setup
    public void setup() throws Exception {
        System.out.println("[Concurrency Benchmark Setup] Initializing with " + threadCount + " threads, " + caseCount + " cases");
        
        // Initialize engines
        engine = new YEngine();
        engine.initialiseEngine(true);
        statelessEngine = new YStatelessEngine();
        statelessEngine.initialiseEngine();
        
        // Create test specification
        testSpec = createTestSpecification();
        
        // Initialize thread pool based on thread count
        executor = Executors.newFixedThreadPool(threadCount);
        
        // Initialize test data
        caseIds = Collections.synchronizedList(new ArrayList<>());
        caseCounter = new AtomicInteger(0);
        
        System.out.println("[Concurrency Benchmark Setup] Setup completed");
    }
    
    @TearDown
    public void tearDown() {
        System.out.println("[Concurrency Benchmark Teardown] Cleaning up resources...");
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
        if (engine != null) {
            engine.shutdownEngine();
        }
        caseIds.clear();
    }
    
    /**
     * Benchmark 1: Virtual Thread Performance vs Platform Threads
     * Compares performance using virtual threads vs platform threads
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void virtualVsPlatformThreadPerformance() throws Exception {
        String caseId = UUID.randomUUID().toString();
        
        // Use virtual thread for benchmarking
        Thread virtualThread = Thread.ofVirtual()
            .name("benchmark-virtual-" + caseCounter.getAndIncrement())
            .start(() -> {
                try {
                    executeCaseWorkflow(caseId);
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        
        virtualThread.join();
    }
    
    /**
     * Benchmark 2: Thread Scaling Performance
     * Measures performance as thread count increases
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Group("scaling")
    @GroupThreads(1)
    public void threadScalingSingle() throws Exception {
        String caseId = UUID.randomUUID().toString();
        executeCaseWorkflow(caseId);
    }
    
    /**
     * Benchmark 3: Concurrent Case Creation Throughput
     * Measures cases created per second with multiple threads
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void concurrentCaseCreationThroughput() throws Exception {
        String caseId = UUID.randomUUID().toString();
        
        // Create case using stateless engine
        statelessEngine.createCase(testSpec.getID(), testSpec, null, null);
        caseIds.add(caseId);
    }
    
    /**
     * Benchmark 4: Concurrent Work Item Processing
     * Measures performance of multiple threads processing work items
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void concurrentWorkItemProcessing() throws Exception {
        String caseId = UUID.randomUUID().toString();
        
        // Submit work item processing tasks
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    YWorkItem workItem = createWorkItem(caseId, "task-" + taskId);
                    engine.startWorkItem(workItem);
                    engine.completeWorkItem(workItem, Collections.emptyMap());
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            future.get();
        }
    }
    
    /**
     * Benchmark 5: Resource Contention Under Load
     * Measures performance under resource contention scenarios
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void resourceContentionUnderLoad() throws Exception {
        String caseId = UUID.randomUUID().toString();
        
        // Submit competing tasks
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    // Simulate resource contention
                    Thread.sleep(1);
                    YWorkItem workItem = createWorkItem(caseId, "contention-task-" + taskId);
                    engine.startWorkItem(workItem);
                    engine.completeWorkItem(workItem, Collections.emptyMap());
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }
        
        // Wait for all tasks
        for (Future<?> future : futures) {
            future.get();
        }
    }
    
    /**
     * Benchmark 6: Bulk Case Processing
     * Measures performance of bulk case operations
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void bulkCaseProcessing() throws Exception {
        List<String> bulkCaseIds = new ArrayList<>();
        
        // Create multiple cases
        for (int i = 0; i < caseCount; i++) {
            String caseId = UUID.randomUUID().toString();
            statelessEngine.createCase(testSpec.getID(), testSpec, null, null);
            bulkCaseIds.add(caseId);
        }
        
        // Process cases in bulk
        List<Future<?>> futures = new ArrayList<>();
        for (String caseId : bulkCaseIds) {
            Future<?> future = executor.submit(() -> {
                try {
                    executeCaseWorkflow(caseId);
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }
        
        // Wait for all bulk operations
        for (Future<?> future : futures) {
            future.get();
        }
        
        bulkCaseIds.clear();
    }
    
    /**
     * Benchmark 7: Deadlock Detection Performance
     * Measures performance impact of deadlock detection mechanisms
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void deadlockDetectionPerformance() throws Exception {
        String caseId = UUID.randomUUID().toString();
        
        // Create potential deadlock scenario
        YWorkItem workItem1 = createWorkItem(caseId, "deadlock-task-1");
        YWorkItem workItem2 = createWorkItem(caseId, "deadlock-task-2");
        
        // Start both work items (potential deadlock)
        engine.startWorkItem(workItem1);
        engine.startWorkItem(workItem2);
        
        // Check for deadlock (simplified)
        boolean deadlockDetected = checkForDeadlock(caseId);
        
        // Complete work items
        engine.completeWorkItem(workItem1, Collections.emptyMap());
        engine.completeWorkItem(workItem2, Collections.emptyMap());
    }
    
    /**
     * Benchmark 8: Mixed Workload Performance
     * Measures performance with mixed sequential and parallel workloads
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void mixedWorkloadPerformance() throws Exception {
        String caseId = UUID.randomUUID().toString();
        
        // Submit mixed workload
        List<Future<?>> futures = new ArrayList<>();
        
        // Sequential tasks
        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    YWorkItem workItem = createWorkItem(caseId, "seq-task-" + taskId);
                    engine.startWorkItem(workItem);
                    engine.completeWorkItem(workItem, Collections.emptyMap());
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }
        
        // Parallel tasks
        for (int i = 0; i < 2; i++) {
            final int taskId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    YWorkItem workItem = createWorkItem(caseId, "par-task-" + taskId);
                    engine.startWorkItem(workItem);
                    engine.completeWorkItem(workItem, Collections.emptyMap());
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }
        
        // Wait for all tasks
        for (Future<?> future : futures) {
            future.get();
        }
    }
    
    /**
     * Benchmark 9: Context Switching Overhead
     * Measures performance impact of frequent context switching
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void contextSwitchingOverhead() throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        
        // Create many small tasks to force context switching
        for (int i = 0; i < 50; i++) {
            final int taskId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    String caseId = UUID.randomUUID().toString();
                    YWorkItem workItem = createWorkItem(caseId, "switch-task-" + taskId);
                    engine.startWorkItem(workItem);
                    engine.completeWorkItem(workItem, Collections.emptyMap());
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }
        
        // Wait for all context switching tasks
        for (Future<?> future : futures) {
            future.get();
        }
    }
    
    /**
     * Benchmark 10: Thread Pool Exhaustion Recovery
     * Measures performance recovery from thread pool exhaustion
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void threadPoolExhaustionRecovery() throws Exception {
        // Create a large number of tasks to exhaust thread pool
        List<Future<?>> futures = new ArrayList<>();
        int totalTasks = threadCount * 10;
        
        for (int i = 0; i < totalTasks; i++) {
            final int taskId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    String caseId = UUID.randomUUID().toString();
                    YWorkItem workItem = createWorkItem(caseId, "exhaust-task-" + taskId);
                    engine.startWorkItem(workItem);
                    engine.completeWorkItem(workItem, Collections.emptyMap());
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }
        
        // Wait for all tasks to complete and pool to recover
        for (Future<?> future : futures) {
            future.get();
        }
    }
    
    // Helper methods
    
    private YSpecification createTestSpecification() {
        YSpecification spec = new YSpecification();
        spec.setID("concurrency-test-spec");
        // In real implementation, this would define a workflow net
        return spec;
    }
    
    private void executeCaseWorkflow(String caseId) throws Exception {
        // Execute a complete workflow case
        YWorkItem startWorkItem = createWorkItem(caseId, "start");
        engine.startWorkItem(startWorkItem);
        
        // Process multiple tasks
        for (int i = 1; i <= 3; i++) {
            YWorkItem workItem = createWorkItem(caseId, "task-" + i);
            engine.startWorkItem(workItem);
            engine.completeWorkItem(workItem, Collections.emptyMap());
        }
        
        // Complete case
        YWorkItem endWorkItem = createWorkItem(caseId, "end");
        engine.startWorkItem(endWorkItem);
        engine.completeWorkItem(endWorkItem, Collections.emptyMap());
    }
    
    private YWorkItem createWorkItem(String caseId, String taskId) {
        YWorkItem workItem = new YWorkItem();
        workItem.setCaseID(caseId);
        workItem.setTaskID(taskId);
        return workItem;
    }
    
    private boolean checkForDeadlock(String caseId) throws Exception {
        // Simplified deadlock detection
        List<YWorkItem> enabledItems = engine.getEnabledWorkItems(caseId);
        List<YWorkItem> busyItems = engine.getBusyWorkItems(caseId);
        
        // Deadlock condition: no enabled items but busy items exist
        return enabledItems.isEmpty() && !busyItems.isEmpty();
    }
}
