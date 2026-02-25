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
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.state.YInternalCondition;
import org.yawlfoundation.yawl.stateless.engine.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.engine.YCaseMonitor;
import org.yawlfoundation.yawl.stateless.engine.YCaseMonitor.CaseState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Core YAWL Engine Performance Benchmarks
 * 
 * This class benchmarks fundamental YAWL engine operations including:
 * - YNetRunner task execution latency
 * - YWorkItem throughput (tasks/second)
 * - YStatelessEngine scalability
 * - Memory usage patterns
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 50, time = 1)
@Fork(3)
@Threads(1)
public class YAWLEngineBenchmarks {

    private YEngine engine;
    private YStatelessEngine statelessEngine;
    private YSpecification sequentialSpec;
    private YSpecification parallelSpec;
    private YSpecification multiChoiceSpec;
    private YSpecification cancelRegionSpec;
    
    private List<String> testCaseIds;
    private Map<String, List<YWorkItem>> workItemCache;
    
    @Setup
    public void setup() throws Exception {
        System.out.println("[Benchmark Setup] Initializing YAWL engine and test specifications...");
        
        // Initialize YAWL engine
        engine = new YEngine();
        engine.initialiseEngine(true);
        
        // Initialize stateless engine
        statelessEngine = new YStatelessEngine();
        statelessEngine.initialiseEngine();
        
        // Load test specifications
        sequentialSpec = loadSpecification("SequentialWorkflow");
        parallelSpec = loadSpecification("ParallelSplitSync");
        multiChoiceSpec = loadSpecification("MultiChoice");
        cancelRegionSpec = loadSpecification("CancelRegion");
        
        // Generate test case IDs
        testCaseIds = generateTestCaseIds(100);
        
        // Pre-generate work items
        workItemCache = new ConcurrentHashMap<>();
        for (String caseId : testCaseIds) {
            workItemCache.put(caseId, generateWorkItems(caseId, 5));
        }
        
        System.out.println("[Benchmark Setup] Setup completed with " + testCaseIds.size() + " test cases");
    }
    
    @TearDown
    public void tearDown() {
        System.out.println("[Benchmark Teardown] Cleaning up resources...");
        if (engine != null) {
            engine.shutdownEngine();
        }
        workItemCache.clear();
    }
    
    /**
     * Benchmark: YNetRunner task execution latency
     * Measures the time to complete a single task in a workflow case
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void netRunnerTaskExecutionLatency() throws Exception {
        String caseId = testCaseIds.get((int) (System.nanoTime() % testCaseIds.size()));
        YWorkItem workItem = findEnabledWorkItem(caseId);
        
        if (workItem != null) {
            engine.startWorkItem(workItem);
            // Simulate task processing
            Thread.sleep(1);
            engine.completeWorkItem(workItem, Collections.emptyMap());
        }
    }
    
    /**
     * Benchmark: YWorkItem checkout/checkout latency
     * Measures the performance of work item operations
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void workItemCheckoutLatency() throws Exception {
        String caseId = testCaseIds.get((int) (System.nanoTime() % testCaseIds.size()));
        List<YWorkItem> workItems = workItemCache.get(caseId);
        
        if (workItems != null && !workItems.isEmpty()) {
            YWorkItem workItem = workItems.get(0);
            engine.startWorkItem(workItem);
            engine.completeWorkItem(workItem, Collections.emptyMap());
        }
    }
    
    /**
     * Benchmark: YStatelessEngine case creation throughput
     * Measures cases created per second
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void statelessEngineCaseCreationThroughput() throws Exception {
        String caseId = UUID.randomUUID().toString();
        YSpecification spec = sequentialSpec;
        
        statelessEngine.createCase(spec.getID(), spec, null, null);
    }
    
    /**
     * Benchmark: YStatelessEngine case monitoring scalability
     * Measures monitoring performance with increasing case counts
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void statelessEngineCaseMonitoringScalability() throws Exception {
        YCaseMonitor monitor = new YCaseMonitor();
        monitor.initialiseEngine();
        
        // Monitor with different case counts
        int caseCount = (int) (System.nanoTime() % 100) + 10;
        for (int i = 0; i < caseCount; i++) {
            String caseId = "case-" + i;
            monitor.getCaseState(caseId, sequentialSpec);
        }
        
        monitor.shutdownEngine();
    }
    
    /**
     * Benchmark: Engine startup performance
     * Measures time to initialize YAWL engine
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void engineStartupPerformance() throws Exception {
        YEngine tempEngine = new YEngine();
        long startTime = System.nanoTime();
        tempEngine.initialiseEngine(true);
        long endTime = System.nanoTime();
        
        tempEngine.shutdownEngine();
        
        return (endTime - startTime) / 1_000_000.0;
    }
    
    /**
     * Benchmark: Task transition performance
     * Measures the time to transition a task from one state to another
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void taskTransitionPerformance() throws Exception {
        String caseId = testCaseIds.get((int) (System.nanoTime() % testCaseIds.size()));
        YWorkItem workItem = findEnabledWorkItem(caseId);
        
        if (workItem != null) {
            long start = System.nanoTime();
            engine.startWorkItem(workItem);
            engine.completeWorkItem(workItem, Collections.emptyMap());
            long end = System.nanoTime();
            
            return (end - start) / 1_000_000.0;
        }
        
        return 0.0;
    }
    
    /**
     * Benchmark: Concurrent task execution
     * Measures performance with multiple concurrent threads
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Group("concurrent")
    @GroupThreads(4)
    public void concurrentTaskExecution() throws Exception {
        String caseId = testCaseIds.get((int) (System.nanoTime() % testCaseIds.size()));
        YWorkItem workItem = findEnabledWorkItem(caseId);
        
        if (workItem != null) {
            engine.startWorkItem(workItem);
            Thread.sleep(5); // Simulate processing time
            engine.completeWorkItem(workItem, Collections.emptyMap());
        }
    }
    
    /**
     * Benchmark: Memory usage during case execution
     * Measures memory consumption patterns
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void memoryUsageDuringCaseExecution() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();
        
        // Execute a simple case
        String caseId = UUID.randomUUID().toString();
        statelessEngine.createCase(sequentialSpec.getID(), sequentialSpec, null, null);
        
        long after = runtime.totalMemory() - runtime.freeMemory();
        return after - before;
    }
    
    // Helper methods
    
    private YSpecification loadSpecification(String name) throws Exception {
        // In a real implementation, this would load from XML files
        // For benchmarking, we create minimal specifications
        return new YSpecification(); // Simplified for benchmarking
    }
    
    private List<String> generateTestCaseIds(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add("case-" + UUID.randomUUID().toString());
        }
        return ids;
    }
    
    private List<YWorkItem> generateWorkItems(String caseId, int count) {
        List<YWorkItem> workItems = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            YWorkItem workItem = new YWorkItem();
            workItem.setCaseID(caseId);
            workItem.setTaskID("task-" + i);
            workItems.add(workItem);
        }
        return workItems;
    }
    
    private YWorkItem findEnabledWorkItem(String caseId) throws Exception {
        // Simplified implementation for benchmarking
        List<YWorkItem> items = engine.getEnabledWorkItems(caseId);
        return items.isEmpty() ? null : items.get(0);
    }
}
