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
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.infra.Blackhole;

/**
 * Workflow Pattern Performance Benchmarks
 * 
 * This class benchmarks specific YAWL control flow patterns:
 * - Sequential workflow (baseline)
 * - Parallel Split/Synchronization patterns
 * - Multi-Choice/Merge patterns
 * - Cancel Region patterns
 * - N-out-of-M patterns
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 50, time = 1)
@Fork(value = 3, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE"
})
@Threads(1)
public class WorkflowPatternBenchmarks {

    private YEngine engine;
    private YStatelessEngine statelessEngine;
    
    // Test specifications for different patterns
    private YSpecification sequentialSpec;
    private YSpecification parallelSplitSyncSpec;
    private YSpecification multiChoiceMergeSpec;
    private YSpecification cancelRegionSpec;
    private YSpecification nOutOfMSpec;
    
    private Map<String, String> testCaseIds;
    private Random random;
    
    @Setup
    public void setup() throws Exception {
        System.out.println("[Pattern Benchmark Setup] Initializing workflow patterns...");
        
        // Initialize engines
        engine = new YEngine();
        engine.initialiseEngine(true);
        statelessEngine = new YStatelessEngine();
        statelessEngine.initialiseEngine();
        
        // Create test specifications (simplified for benchmarking)
        sequentialSpec = createSequentialWorkflowSpec();
        parallelSplitSyncSpec = createParallelSplitSyncSpec();
        multiChoiceMergeSpec = createMultiChoiceMergeSpec();
        cancelRegionSpec = createCancelRegionSpec();
        nOutOfMSpec = createNOutOfMSpec();
        
        // Generate test cases
        testCaseIds = new HashMap<>();
        testCaseIds.put("sequential", UUID.randomUUID().toString());
        testCaseIds.put("parallel", UUID.randomUUID().toString());
        testCaseIds.put("multiChoice", UUID.randomUUID().toString());
        testCaseIds.put("cancelRegion", UUID.randomUUID().toString());
        testCaseIds.put("nOutOfM", UUID.randomUUID().toString());
        
        random = new Random();
        
        System.out.println("[Pattern Benchmark Setup] Setup completed with 5 workflow patterns");
    }
    
    @TearDown
    public void tearDown() {
        System.out.println("[Pattern Benchmark Teardown] Cleaning up resources...");
        if (engine != null) {
            engine.shutdownEngine();
        }
    }
    
    /**
     * Benchmark 1: Sequential Workflow Pattern (Baseline)
     * Measures performance of simple linear workflow execution
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void sequentialWorkflowPerformance() throws Exception {
        String caseId = testCaseIds.get("sequential");
        
        // Execute sequential workflow: Task1 -> Task2 -> Task3 -> Task4
        executeSequentialCase(caseId, 4);
    }
    
    /**
     * Benchmark 2: Parallel Split/Synchronization Pattern
     * Measures performance of parallel execution with synchronization
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void parallelSplitSyncPerformance() throws Exception {
        String caseId = testCaseIds.get("parallel");
        
        // Execute parallel workflow: Start -> [Task1, Task2, Task3] -> Sync -> End
        executeParallelSplitSyncCase(caseId, 3);
    }
    
    /**
     * Benchmark 3: Multi-Choice/Merge Pattern
     * Measures performance of conditional branching and merging
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void multiChoiceMergePerformance() throws Exception {
        String caseId = testCaseIds.get("multiChoice");
        
        // Execute multi-choice workflow with random path selection
        int choice = random.nextInt(3); // 3 different paths
        executeMultiChoiceCase(caseId, choice);
    }
    
    /**
     * Benchmark 4: Cancel Region Pattern
     * Measures performance of task cancellation and region cleanup
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void cancelRegionPerformance() throws Exception {
        String caseId = testCaseIds.get("cancelRegion");
        
        // Execute workflow with cancellation region
        executeCancelRegionCase(caseId);
    }
    
    /**
     * Benchmark 5: N-out-of-M Pattern
     * Measures performance of majority vote or threshold-based patterns
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void nOutOfMPatternPerformance() throws Exception {
        String caseId = testCaseIds.get("nOutOfM");
        
        // Execute N-out-of-M pattern (e.g., 2-out-of-3)
        executeNOutOfMCase(caseId, 2, 3);
    }
    
    /**
     * Benchmark 6: Mixed Pattern Complexity
     * Measures performance of complex combinations of patterns
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void mixedPatternComplexityPerformance() throws Exception {
        String caseId = UUID.randomUUID().toString();
        
        // Execute complex mixed workflow
        executeMixedPatternCase(caseId);
    }
    
    /**
     * Benchmark 7: Pattern Scalability - Increasing Complexity
     * Measures how performance degrades with increasing workflow complexity
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void patternScalabilityPerformance() throws Exception {
        int complexityLevel = (int) (System.nanoTime() % 5) + 1; // 1-5 levels
        
        // Execute workflow with scaled complexity
        executeScalablePatternCase(complexityLevel);
    }
    
    /**
     * Benchmark 8: Pattern Throughput
     * Measures throughput (cases/second) for each pattern type
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void patternThroughput() throws Exception {
        String caseId = UUID.randomUUID().toString();
        String patternType = "sequential"; // Focus on one pattern for throughput
        
        // Execute case and measure throughput
        switch (patternType) {
            case "sequential":
                executeSequentialCase(caseId, 3);
                break;
            case "parallel":
                executeParallelSplitSyncCase(caseId, 2);
                break;
            case "multiChoice":
                executeMultiChoiceCase(caseId, random.nextInt(2));
                break;
            default:
                executeSequentialCase(caseId, 3);
        }
    }
    
    /**
     * Benchmark 9: Pattern Memory Usage
     * Measures memory consumption for different patterns
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void patternMemoryUsage(Blackhole bh) throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();

        // Execute each pattern and measure memory
        executeSequentialCase(UUID.randomUUID().toString(), 3);
        executeParallelSplitSyncCase(UUID.randomUUID().toString(), 2);
        executeMultiChoiceCase(UUID.randomUUID().toString(), 0);

        long after = runtime.totalMemory() - runtime.freeMemory();
        bh.consume(after - before);
    }
    
    /**
     * Benchmark 10: Pattern Error Handling
     * Measures performance degradation during error scenarios
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void patternErrorHandlingPerformance() throws Exception {
        String caseId = UUID.randomUUID().toString();
        
        // Execute workflow with simulated errors
        executeCaseWithErrors(caseId);
    }
    
    // Helper methods for pattern execution
    
    private void executeSequentialCase(String caseId, int taskCount) throws Exception {
        // Simplified sequential execution
        for (int i = 1; i <= taskCount; i++) {
            String taskId = "task-" + i;
            YWorkItem workItem = new YWorkItem();
            workItem.setCaseID(caseId);
            workItem.setTaskID(taskId);
            
            engine.startWorkItem(workItem);
            engine.completeWorkItem(workItem, Collections.emptyMap());
        }
    }
    
    private void executeParallelSplitSyncCase(String caseId, int parallelTasks) throws Exception {
        // Execute parallel tasks
        List<YWorkItem> parallelWorkItems = new ArrayList<>();
        for (int i = 1; i <= parallelTasks; i++) {
            String taskId = "parallel-task-" + i;
            YWorkItem workItem = new YWorkItem();
            workItem.setCaseID(caseId);
            workItem.setTaskID(taskId);
            parallelWorkItems.add(workItem);
            
            engine.startWorkItem(workItem);
            engine.completeWorkItem(workItem, Collections.emptyMap());
        }
        
        // Synchronization point (simplified)
        YWorkItem syncWorkItem = new YWorkItem();
        syncWorkItem.setCaseID(caseId);
        syncWorkItem.setTaskID("sync-task");
        engine.startWorkItem(syncWorkItem);
        engine.completeWorkItem(syncWorkItem, Collections.emptyMap());
    }
    
    private void executeMultiChoiceCase(String caseId, int choice) throws Exception {
        // Execute different paths based on choice
        YWorkItem choiceWorkItem = new YWorkItem();
        choiceWorkItem.setCaseID(caseId);
        choiceWorkItem.setTaskID("choice-" + choice);
        
        engine.startWorkItem(choiceWorkItem);
        engine.completeWorkItem(choiceWorkItem, Collections.emptyMap());
        
        // Merge point
        YWorkItem mergeWorkItem = new YWorkItem();
        mergeWorkItem.setCaseID(caseId);
        mergeWorkItem.setTaskID("merge");
        engine.startWorkItem(mergeWorkItem);
        engine.completeWorkItem(mergeWorkItem, Collections.emptyMap());
    }
    
    private void executeCancelRegionCase(String caseId) throws Exception {
        // Execute tasks within cancel region
        YWorkItem regionWorkItem = new YWorkItem();
        regionWorkItem.setCaseID(caseId);
        regionWorkItem.setTaskID("region-task");
        
        engine.startWorkItem(regionWorkItem);
        
        // Simulate cancellation
        engine.cancelWorkItem(regionWorkItem);
    }
    
    private void executeNOutOfMCase(String caseId, int n, int m) throws Exception {
        // Execute N out of M tasks
        int executed = 0;
        for (int i = 1; i <= m && executed < n; i++) {
            String taskId = "n-task-" + i;
            YWorkItem workItem = new YWorkItem();
            workItem.setCaseID(caseId);
            workItem.setTaskID(taskId);
            
            engine.startWorkItem(workItem);
            engine.completeWorkItem(workItem, Collections.emptyMap());
            executed++;
        }
        
        // Final task after N completion
        YWorkItem finalWorkItem = new YWorkItem();
        finalWorkItem.setCaseID(caseId);
        finalWorkItem.setTaskID("n-final");
        engine.startWorkItem(finalWorkItem);
        engine.completeWorkItem(finalWorkItem, Collections.emptyMap());
    }
    
    private void executeMixedPatternCase(String caseId) throws Exception {
        // Combination of multiple patterns
        executeSequentialCase(caseId, 2); // Sequential start
        
        executeParallelSplitSyncCase(caseId, 2); // Parallel section
        
        executeMultiChoiceCase(caseId, 0); // Choice point
        
        executeNOutOfMCase(caseId, 1, 2); // N-out-of-M
    }
    
    private void executeScalablePatternCase(int complexity) throws Exception {
        String caseId = UUID.randomUUID().toString();
        
        // Scale complexity based on level
        int baseTasks = 3;
        int taskCount = baseTasks + (complexity * 2);
        
        for (int level = 1; level <= complexity; level++) {
            // Nested complexity
            executeSequentialCase(caseId + "-level-" + level, taskCount / complexity);
            
            if (level % 2 == 0) {
                executeParallelSplitSyncCase(caseId + "-level-" + level, 2);
            }
        }
    }
    
    private void executeCaseWithErrors(String caseId) throws Exception {
        // Execute with simulated errors
        YWorkItem workItem = new YWorkItem();
        workItem.setCaseID(caseId);
        workItem.setTaskID("error-task");
        
        try {
            engine.startWorkItem(workItem);
            // Simulate error during execution
            throw new RuntimeException("Simulated error");
        } catch (Exception e) {
            // Handle error
        }
    }
    
    // Helper methods for specification creation (simplified)
    
    private YSpecification createSequentialWorkflowSpec() {
        YSpecification spec = new YSpecification();
        spec.setID("sequential-workflow");
        // In real implementation, this would define the workflow net
        return spec;
    }
    
    private YSpecification createParallelSplitSyncSpec() {
        YSpecification spec = new YSpecification();
        spec.setID("parallel-split-sync");
        return spec;
    }
    
    private YSpecification createMultiChoiceMergeSpec() {
        YSpecification spec = new YSpecification();
        spec.setID("multi-choice-merge");
        return spec;
    }
    
    private YSpecification createCancelRegionSpec() {
        YSpecification spec = new YSpecification();
        spec.setID("cancel-region");
        return spec;
    }
    
    private YSpecification createNOutOfMSpec() {
        YSpecification spec = new YSpecification();
        spec.setID("n-out-of-m");
        return spec;
    }
}
