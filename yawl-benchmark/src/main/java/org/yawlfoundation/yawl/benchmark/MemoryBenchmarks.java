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
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.engine.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.engine.YCaseMonitor;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Memory Performance Benchmarks
 * 
 * This class benchmarks memory usage patterns and GC behavior in YAWL:
 * - Heap usage during workflow execution
 * - Garbage collection pressure
 * - Memory scalability with case counts
 * - Object allocation patterns
 * - Memory leak detection
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 50, time = 1)
@Fork(value = 3, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders"
})
@Threads(1)
public class MemoryBenchmarks {

    private YEngine engine;
    private YStatelessEngine statelessEngine;
    private YCaseMonitor caseMonitor;
    
    private MemoryMXBean memoryMXBean;
    private GarbageCollectorMXBean gcMXBean;
    private ThreadMXBean threadMXBean;
    
    private List<String> caseIds;
    private AtomicInteger caseCounter;
    private Random random;
    
    @Param({"10", "100", "1000", "10000"})
    private int caseCount;
    
    @Param({"sequential", "parallel", "multiChoice"})
    private String workflowPattern;
    
    @Setup
    public void setup() throws Exception {
        System.out.println("[Memory Benchmark Setup] Initializing with " + caseCount + " cases, pattern: " + workflowPattern);
        
        // Initialize engines
        engine = new YEngine();
        engine.initialiseEngine(true);
        statelessEngine = new YStatelessEngine();
        statelessEngine.initialiseEngine();
        caseMonitor = new YCaseMonitor();
        caseMonitor.initialiseEngine();
        
        // Initialize memory monitoring
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        gcMXBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
        threadMXBean = ManagementFactory.getThreadMXBean();
        
        // Initialize test data
        caseIds = Collections.synchronizedList(new ArrayList<>());
        caseCounter = new AtomicInteger(0);
        random = new Random();
        
        System.out.println("[Memory Benchmark Setup] Setup completed");
    }
    
    @TearDown
    public void tearDown() {
        System.out.println("[Memory Benchmark Teardown] Cleaning up resources...");
        if (engine != null) {
            engine.shutdownEngine();
        }
        if (statelessEngine != null) {
            statelessEngine.shutdownEngine();
        }
        if (caseMonitor != null) {
            caseMonitor.shutdownEngine();
        }
        caseIds.clear();
        System.gc(); // Suggest GC cleanup
    }
    
    /**
     * Benchmark 1: Heap Usage During Workflow Execution
     * Measures heap memory consumption during workflow processing
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long heapUsageDuringWorkflowExecution(Blackhole bh) throws Exception {
        String caseId = "memory-case-" + caseCounter.getAndIncrement();
        caseIds.add(caseId);

        long initialHeap = memoryMXBean.getHeapMemoryUsage().getUsed();

        // Execute workflow
        executeWorkflowPattern(caseId, workflowPattern);

        long finalHeap = memoryMXBean.getHeapMemoryUsage().getUsed();
        long heapDelta = finalHeap - initialHeap;

        bh.consume(heapDelta);
        return heapDelta;
    }
    
    /**
     * Benchmark 2: GC Pressure Under Load
     * Measures garbage collection activity during high load scenarios
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long gcPressureUnderLoad(Blackhole bh) throws Exception {
        long initialGcTime = gcMXBean.getCollectionTime();

        // Execute workflow with high object creation
        for (int i = 0; i < caseCount; i++) {
            String caseId = "gc-case-" + i;
            executeWorkflowPattern(caseId, workflowPattern);

            // Force some object creation
            bh.consume(createLargeObjects(100));
        }

        long gcTimeDelta = gcMXBean.getCollectionTime() - initialGcTime;
        bh.consume(gcTimeDelta);
        return gcTimeDelta;
    }
    
    /**
     * Benchmark 3: Memory Scalability with Case Counts
     * Measures how memory usage scales with increasing case counts
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long memoryScalabilityWithCaseCounts(Blackhole bh) throws Exception {
        long initialMemory = memoryMXBean.getHeapMemoryUsage().getUsed();

        // Create and process multiple cases
        for (int i = 0; i < caseCount; i++) {
            String caseId = "scalable-case-" + i;
            statelessEngine.createCase("spec-id", createTestSpecification(), null, null);

            // Process case
            YWorkItem workItem = createWorkItem(caseId, "memory-task");
            engine.startWorkItem(workItem);
            engine.completeWorkItem(workItem, Collections.emptyMap());
            bh.consume(workItem);
        }

        long delta = memoryMXBean.getHeapMemoryUsage().getUsed() - initialMemory;
        bh.consume(delta);
        return delta;
    }
    
    /**
     * Benchmark 4: Object Allocation Patterns
     * Measures object allocation during workflow execution
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long objectAllocationPatterns(Blackhole bh) throws Exception {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long initialAllocations = threadBean.getThreadAllocatedBytes(Thread.currentThread().getId());

        // Execute workflow with many object allocations
        String caseId = "alloc-case-" + caseCounter.getAndIncrement();
        executeWorkflowPattern(caseId, workflowPattern);

        // Additional allocations
        for (int i = 0; i < 1000; i++) {
            String data = "data-" + i + "-" + UUID.randomUUID();
            bh.consume(data.split("-"));
        }

        long delta = threadBean.getThreadAllocatedBytes(Thread.currentThread().getId()) - initialAllocations;
        bh.consume(delta);
        return delta;
    }
    
    /**
     * Benchmark 5: Memory Leak Detection
     * Tests for memory leaks by monitoring memory growth over time
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long memoryLeakDetection(Blackhole bh) throws Exception {
        long initialMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        List<String> leakTestObjects = new ArrayList<>();

        // Create objects that might leak
        for (int i = 0; i < caseCount; i++) {
            String caseId = "leak-case-" + i;
            leakTestObjects.add(caseId);

            // Execute workflow
            executeWorkflowPattern(caseId, workflowPattern);

            // Store references (potential leak)
            List<YWorkItem> workItems = engine.getEnabledWorkItems(caseId);
            leakTestObjects.addAll(workItems.stream().map(w -> w.getCaseID()).toList());
        }

        // Clear references to test for proper cleanup
        leakTestObjects.clear();
        System.gc();

        long delta = memoryMXBean.getHeapMemoryUsage().getUsed() - initialMemory;
        bh.consume(delta);
        return delta;
    }
    
    /**
     * Benchmark 6: Non-Heap Memory Usage
     * Measures non-heap memory usage (code cache, metaspace, etc.)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long nonHeapMemoryUsage(Blackhole bh) throws Exception {
        MemoryUsage nonHeapInitial = memoryMXBean.getNonHeapMemoryUsage();

        // Execute operations that affect non-heap memory
        for (int i = 0; i < 100; i++) {
            String caseId = "nonheap-case-" + i;
            executeWorkflowPattern(caseId, workflowPattern);

            // Class loading affects metaspace
            bh.consume(Class.forName("java.lang.String"));
        }

        long delta = memoryMXBean.getNonHeapMemoryUsage().getUsed() - nonHeapInitial.getUsed();
        bh.consume(delta);
        return delta;
    }
    
    /**
     * Benchmark 7: Thread Memory Usage
     * Measures memory used by threads during concurrent execution
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long threadMemoryUsage(Blackhole bh) throws Exception {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long initialThreadMemory = 0;

        // Measure current thread memory
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            initialThreadMemory += threadBean.getThreadAllocatedBytes(thread.getId());
        }

        // Execute concurrent operations using virtual threads (Java 25 best practice)
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount(); i++) {
            Thread t = Thread.ofVirtual().start(() -> {
                try {
                    String caseId = "thread-case-" + UUID.randomUUID();
                    executeWorkflowPattern(caseId, workflowPattern);
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(t);
        }

        // Wait for threads to complete
        for (Thread t : threads) {
            t.join();
        }

        long finalThreadMemory = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            finalThreadMemory += threadBean.getThreadAllocatedBytes(thread.getId());
        }

        long delta = finalThreadMemory - initialThreadMemory;
        bh.consume(delta);
        return delta;
    }
    
    /**
     * Benchmark 8: Memory Usage by Case Monitor
     * Measures memory usage of the case monitoring component
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long caseMonitorMemoryUsage(Blackhole bh) throws Exception {
        long initialMemory = memoryMXBean.getHeapMemoryUsage().getUsed();

        // Use case monitor extensively
        for (int i = 0; i < caseCount; i++) {
            String caseId = "monitor-case-" + i;
            YSpecification spec = createTestSpecification();

            // Get case state (uses memory)
            bh.consume(caseMonitor.getCaseState(caseId, spec));

            // Monitor case changes
            bh.consume(caseMonitor.getCaseChanges(caseId));
        }

        long delta = memoryMXBean.getHeapMemoryUsage().getUsed() - initialMemory;
        bh.consume(delta);
        return delta;
    }
    
    /**
     * Benchmark 9: Large Dataset Processing Memory Impact
     * Measures memory impact of processing large datasets
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public long largeDatasetProcessingMemoryImpact(Blackhole bh) throws Exception {
        long initialMemory = memoryMXBean.getHeapMemoryUsage().getUsed();

        // Create and process large dataset
        List<String> largeDataset = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            largeDataset.add("data-point-" + i + "-" + UUID.randomUUID());
        }

        // Process dataset with workflow
        String caseId = "large-data-case-" + caseCounter.getAndIncrement();
        for (String data : largeDataset) {
            YWorkItem workItem = createWorkItem(caseId, "process-" + data.hashCode());
            engine.startWorkItem(workItem);
            engine.completeWorkItem(workItem, Collections.singletonMap("data", data));
            bh.consume(workItem);
        }

        long delta = memoryMXBean.getHeapMemoryUsage().getUsed() - initialMemory;
        bh.consume(delta);
        return delta;
    }
    
    /**
     * Benchmark 10: Memory Recovery After Load
     * Measures how well memory is recovered after processing load
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double memoryRecoveryAfterLoad(Blackhole bh) throws Exception {
        long peakMemory = 0;

        // Process load and track peak memory
        for (int i = 0; i < caseCount; i++) {
            long currentMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
            peakMemory = Math.max(peakMemory, currentMemory);

            String caseId = "recovery-case-" + i;
            executeWorkflowPattern(caseId, workflowPattern);
        }

        // Clear all references and suggest GC
        caseIds.clear();
        System.gc();

        // Wait a bit for GC to complete
        Thread.sleep(100);

        long recoveredMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        double ratio = peakMemory > 0 ? (double) (peakMemory - recoveredMemory) / peakMemory : 0.0;
        bh.consume(ratio);
        return ratio;
    }
    
    // Helper methods
    
    private int threadCount() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }
    
    private void executeWorkflowPattern(String caseId, String pattern) throws Exception {
        switch (pattern) {
            case "sequential":
                executeSequentialWorkflow(caseId);
                break;
            case "parallel":
                executeParallelWorkflow(caseId);
                break;
            case "multiChoice":
                executeMultiChoiceWorkflow(caseId);
                break;
            default:
                executeSequentialWorkflow(caseId);
        }
    }
    
    private void executeSequentialWorkflow(String caseId) throws Exception {
        for (int i = 1; i <= 5; i++) {
            YWorkItem workItem = createWorkItem(caseId, "seq-task-" + i);
            engine.startWorkItem(workItem);
            engine.completeWorkItem(workItem, Collections.emptyMap());
        }
    }
    
    private void executeParallelWorkflow(String caseId) throws Exception {
        List<YWorkItem> parallelTasks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            YWorkItem workItem = createWorkItem(caseId, "par-task-" + i);
            parallelTasks.add(workItem);
            engine.startWorkItem(workItem);
        }
        
        for (YWorkItem workItem : parallelTasks) {
            engine.completeWorkItem(workItem, Collections.emptyMap());
        }
    }
    
    private void executeMultiChoiceWorkflow(String caseId) throws Exception {
        int choice = random.nextInt(3);
        YWorkItem choiceWorkItem = createWorkItem(caseId, "choice-" + choice);
        engine.startWorkItem(choiceWorkItem);
        engine.completeWorkItem(choiceWorkItem, Collections.emptyMap());
        
        // Merge point
        YWorkItem mergeWorkItem = createWorkItem(caseId, "merge");
        engine.startWorkItem(mergeWorkItem);
        engine.completeWorkItem(mergeWorkItem, Collections.emptyMap());
    }
    
    private List<String> createLargeObjects(int count) {
        List<String> largeObjects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 1000; j++) {
                sb.append("data-point-").append(i).append("-").append(j);
            }
            largeObjects.add(sb.toString());
        }
        return largeObjects;
    }
    
    private YWorkItem createWorkItem(String caseId, String taskId) {
        YWorkItem workItem = new YWorkItem();
        workItem.setCaseID(caseId);
        workItem.setTaskID(taskId);
        return workItem;
    }
    
    private YSpecification createTestSpecification() {
        YSpecification spec = new YSpecification();
        spec.setID("memory-test-spec");
        return spec;
    }
}
