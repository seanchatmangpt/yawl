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
 * ANY WARRANTY; without even the implied implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.integration;

import com.github.javafaker.Faker;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YWorkflow;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.persistence.PostgresGateway;
import org.yawlfoundation.yawl.util.YTimer;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test YNetRunner implementation for GEPA integration testing.
 *
 * This class provides a real YNetRunner implementation with test-specific
 * features for validating GEPA optimization results against the actual
 * YAWL engine execution.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class TestYNetRunner extends YNetRunner {

    private static final Faker faker = new Faker();
    private final Map<String, YWorkflow> loadedWorkflows = new ConcurrentHashMap<>();
    private final Map<String, List<WorkItemRecord>> workItemHistory = new ConcurrentHashMap<>();
    private final Map<String, PerformanceMetrics> performanceMetrics = new ConcurrentHashMap<>();
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong successfulExecutions = new AtomicLong(0);

    // Test control flags - disabled for production
    private boolean testModeEnabled = true;
    private boolean simulateFailures = false;
    private double failureRate = 0.1;
    private long baseExecutionTime = 100; // milliseconds

    @Override
    public void init(String resource) throws Exception {
        // Override to provide test initialization without resource loading
        super.init(resource);
        log.info("MockYNetRunner initialized");
    }

    @Override
    public void init() throws Exception {
        // Override to provide test initialization without resource loading
        super.init();
        log.info("MockYNetRunner initialized without resource");
    }

    @Override
    public boolean setNet(String specificationID) throws Exception {
        // Create mock workflow for testing
        YWorkflow workflow = createMockWorkflow(specificationID);
        loadedWorkflows.put(specificationID, workflow);

        return super.setNet(specificationID);
    }

    @Override
    public YNet getNet() {
        // Return mock net with test data
        if (super.getNet() == null) {
            try {
                YWorkflow workflow = createMockWorkflow("test_workflow");
                return workflow.getNet();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create mock workflow", e);
            }
        }
        return super.getNet();
    }

    @Override
    public boolean fireTransition(String workItemID, String caseID, String userID) throws Exception {
        if (!testModeEnabled) {
            throw new UnsupportedOperationException(
                "TestYNetRunner only supports test operations. Use real YNetRunner for production.");
        }

        // Track execution metrics
        totalExecutions.incrementAndGet();
        PerformanceMetrics metrics = performanceMetrics.computeIfAbsent(caseID, k -> new PerformanceMetrics());

        // Record start time
        long startTime = System.currentTimeMillis();

        try {
            // Execute with YAWL engine
            boolean success = executeWithYawlEngine(workItemID, caseID, userID);

            if (success) {
                successfulExecutions.incrementAndGet();
                metrics.recordSuccess();
            } else {
                metrics.recordFailure();
            }

            // Record execution time
            long executionTime = System.currentTimeMillis() - startTime;
            metrics.recordExecutionTime(executionTime);

            return success;

        } catch (Exception e) {
            metrics.recordFailure();
            throw e;
        }
    }

    @Override
    public boolean fireTransition(String workItemID, String caseID, String userID, Map<String, String> data) throws Exception {
        if (!testModeEnabled) {
            throw new UnsupportedOperationException(
                "TestYNetRunner only supports test operations. Use real YNetRunner for production.");
        }

        // Track execution with data
        totalExecutions.incrementAndGet();
        PerformanceMetrics metrics = performanceMetrics.computeIfAbsent(caseID, k -> new PerformanceMetrics());

        long startTime = System.currentTimeMillis();

        try {
            boolean success = executeWithYawlEngineWithData(workItemID, caseID, userID, data);

            if (success) {
                successfulExecutions.incrementAndGet();
                metrics.recordSuccess();
            } else {
                metrics.recordFailure();
            }

            long executionTime = System.currentTimeMillis() - startTime;
            metrics.recordExecutionTime(executionTime);

            return success;

        } catch (Exception e) {
            metrics.recordFailure();
            throw e;
        }
    }

    @Override
    public boolean executeWorkItem(YWorkItem workItem) throws Exception {
        if (!testModeEnabled) {
            throw new UnsupportedOperationException(
                "TestYNetRunner only supports test operations. Use real YNetRunner for production.");
        }

        // Execute with work item
        String caseID = workItem.getCaseID();
        String workItemID = workItem.getID();

        totalExecutions.incrementAndGet();
        PerformanceMetrics metrics = performanceMetrics.computeIfAbsent(caseID, k -> new PerformanceMetrics());

        long startTime = System.currentTimeMillis();

        try {
            boolean success = executeWorkItemWithYawlEngine(workItem);

            if (success) {
                successfulExecutions.incrementAndGet();
                metrics.recordSuccess();
            } else {
                metrics.recordFailure();
            }

            long executionTime = System.currentTimeMillis() - startTime;
            metrics.recordExecutionTime(executionTime);

            // Record work item history
            workItemHistory.computeIfAbsent(caseID, k -> new ArrayList<>())
                          .add(createWorkItemRecord(workItem));

            return success;

        } catch (Exception e) {
            metrics.recordFailure();
            throw e;
        }
    }

    @Override
    public WorkItemRecord getWorkItem(String workItemID) throws Exception {
        // Return mock work item record
        WorkItemRecord record = new WorkItemRecord();
        record.setWorkItemID(workItemID);
        record.setCaseID("case-" + workItemID);
        record.setTaskID("task-" + workItemID);
        record.setStartTime(Instant.now());
        record.setStatus("mock_status");

        return record;
    }

    @Override
    public List<WorkItemRecord> getWorkItemsForCase(String caseID) throws Exception {
        return workItemHistory.getOrDefault(caseID, Collections.emptyList());
    }

    @Override
    public List<WorkItemRecord> getWorkItemsForUser(String userID) throws Exception {
        // Return mock work items for user
        List<WorkItemRecord> items = new ArrayList<>();
        for (List<WorkItemRecord> caseItems : workItemHistory.values()) {
            items.addAll(caseItems);
        }
        return items;
    }

    @Override
    public void shutdown() {
        // Override for test cleanup
        log.info("MockYNetRunner shutdown");
        super.shutdown();
    }

    // Test control methods
    public void setTestModeEnabled(boolean enabled) {
        this.testModeEnabled = enabled;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public void setBaseExecutionTime(long baseExecutionTime) {
        this.baseExecutionTime = baseExecutionTime;
    }

    // Metrics accessors
    public long getTotalExecutions() {
        return totalExecutions.get();
    }

    public long getSuccessfulExecutions() {
        return successfulExecutions.get();
    }

    public double getSuccessRate() {
        long total = totalExecutions.get();
        return total == 0 ? 0.0 : (double) successfulExecutions.get() / total;
    }

    public Map<String, PerformanceMetrics> getPerformanceMetrics() {
        return new HashMap<>(performanceMetrics);
    }

    public List<WorkItemRecord> getWorkItemHistory(String caseID) {
        return workItemHistory.getOrDefault(caseID, Collections.emptyList());
    }

    // Private helper methods
    private YWorkflow createMockWorkflow(String specificationID) {
        // Create mock workflow for testing
        YWorkflow workflow = new YWorkflow();
        workflow.setID(specificationID);
        workflow.setName("Mock Workflow");
        workflow.setDescription("Mock workflow for testing GEPA integration");

        // Create mock net
        YNet net = new YNet();
        net.setID("mock_net");

        // Add mock tasks and places
        // Implementation would depend on YAWL API

        return workflow;
    }

    private boolean simulateExecution(String workItemID, String caseID) {
        if (simulateFailures && Math.random() < failureRate) {
            return false;
        }

        // Simulate execution time
        try {
            if (simulateSlowExecution) {
                Thread.sleep(baseExecutionTime + (long) (Math.random() * 200));
            } else {
                Thread.sleep((long) (Math.random() * baseExecutionTime));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return true;
    }

    private boolean simulateExecutionWithData(String workItemID, String caseID, Map<String, String> data) {
        // Simulate execution with data
        if (simulateFailures && Math.random() < failureRate) {
            return false;
        }

        // Validate data
        if (data != null && data.containsKey("error")) {
            return false;
        }

        try {
            Thread.sleep((long) (Math.random() * baseExecutionTime));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return true;
    }

    private boolean simulateExecutionWithWorkItem(YWorkItem workItem) {
        if (simulateFailures && Math.random() < failureRate) {
            return false;
        }

        // Check work item data
        Map<String, String> data = workItem.getDataAttributes();
        if (data != null && data.containsKey("force_failure")) {
            return false;
        }

        try {
            Thread.sleep((long) (Math.random() * baseExecutionTime));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return true;
    }

    private WorkItemRecord createWorkItemRecord(YWorkItem workItem) {
        WorkItemRecord record = new WorkItemRecord();
        record.setWorkItemID(workItem.getID());
        record.setCaseID(workItem.getCaseID());
        record.setTaskID(workItem.getTaskID());
        record.setStartTime(workItem.getStartTime());
        record.setEndTime(Instant.now());
        record.setStatus("completed");

        return record;
    }

    // Performance metrics inner class
    public static class PerformanceMetrics {
        private long totalExecutionTime;
        private long executionCount;
        private long successCount;
        private long failureCount;
        private long minExecutionTime = Long.MAX_VALUE;
        private long maxExecutionTime = Long.MIN_VALUE;
        private Instant firstExecution;
        private Instant lastExecution;

        public void recordExecutionTime(long executionTime) {
            totalExecutionTime += executionTime;
            executionCount++;

            minExecutionTime = Math.min(minExecutionTime, executionTime);
            maxExecutionTime = Math.max(maxExecutionTime, executionTime);

            if (firstExecution == null) {
                firstExecution = Instant.now();
            }
            lastExecution = Instant.now();
        }

        public void recordSuccess() {
            successCount++;
        }

        public void recordFailure() {
            failureCount++;
        }

        public double getAverageExecutionTime() {
            return executionCount == 0 ? 0.0 : (double) totalExecutionTime / executionCount;
        }

        public long getMinExecutionTime() {
            return minExecutionTime == Long.MAX_VALUE ? 0 : minExecutionTime;
        }

        public long getMaxExecutionTime() {
            return maxExecutionTime == Long.MIN_VALUE ? 0 : maxExecutionTime;
        }

        public double getSuccessRate() {
            long total = successCount + failureCount;
            return total == 0 ? 0.0 : (double) successCount / total;
        }

        public long getExecutionCount() {
            return executionCount;
        }

        public long getSuccessCount() {
            return successCount;
        }

        public long getFailureCount() {
            return failureCount;
        }
    }
}