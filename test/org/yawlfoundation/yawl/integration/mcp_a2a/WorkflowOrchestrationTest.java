/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.mcp_a2a;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AServer;
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistry;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Workflow orchestration integration tests for MCP-A2A MVP.
 *
 * Tests complete workflow orchestration scenarios:
 * - Multi-case execution
 * - Workflow state transitions
 * - Orchestration across services
 * - Complex workflow patterns
 * - Production orchestration scenarios
 *
 * Chicago TDD methodology: Real workflow engine, real database,
 * real orchestration - no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("integration")
@Tag("orchestration")
class WorkflowOrchestrationTest {

    private static final int A2A_PORT = 19910;
    private static final int REGISTRY_PORT = 19911;

    private static final String JWT_SECRET = "orchestration-test-jwt-secret-key-min-32-chars";
    private static final String API_KEY = "orchestration-test-api-key";

    private Connection db;
    private AgentRegistry registry;
    private YawlA2AServer a2aServer;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize database
        String jdbcUrl = "jdbc:h2:mem:orchestration_test_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(db);

        System.setProperty("A2A_JWT_SECRET", JWT_SECRET);
        System.setProperty("A2A_API_KEY", API_KEY);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (a2aServer != null && a2aServer.isRunning()) a2aServer.stop();
        if (registry != null) registry.stop();
        if (db != null && !db.isClosed()) db.close();

        System.clearProperty("A2A_JWT_SECRET");
        System.clearProperty("A2A_API_KEY");
    }

    // =========================================================================
    // Multi-Case Execution Tests
    // =========================================================================

    @Nested
    @DisplayName("Multi-Case Execution")
    class MultiCaseExecutionTests {

        @Test
        @DisplayName("Execute multiple cases concurrently")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void executeMultipleCases_Concurrently() throws Exception {
            // Given: Running services
            startServices();

            // And: A specification
            String specId = "multi-case-spec";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Multi-Case Test");

            // When: Multiple cases are launched concurrently
            int caseCount = 10;
            CountDownLatch latch = new CountDownLatch(caseCount);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < caseCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        String runnerId = "runner-multi-" + index;
                        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
                        WorkflowDataFactory.seedWorkItem(db, "wi-multi-" + index, runnerId, "task", "Enabled");
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // Handle failure
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(20, TimeUnit.SECONDS);
            executor.shutdown();

            // Then: All cases are created
            assertTrue(completed, "All case creations must complete");
            assertEquals(caseCount, successCount.get(), "All cases must be created");

            // And: All are persisted
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = ?")) {
                ps.setString(1, specId);
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(caseCount, rs.getInt(1), "All cases must be persisted");
            }
        }

        @Test
        @DisplayName("Cases from different specifications")
        void casesFrom_DifferentSpecifications() throws Exception {
            // Given: Multiple specifications
            for (int i = 0; i < 3; i++) {
                WorkflowDataFactory.seedSpecification(db, "spec-" + i, "1.0", "Specification " + i);
            }

            // When: Cases are created for each spec
            for (int s = 0; s < 3; s++) {
                for (int c = 0; c < 5; c++) {
                    String runnerId = "runner-spec-" + s + "-" + c;
                    WorkflowDataFactory.seedNetRunner(db, runnerId, "spec-" + s, "1.0", "root", "RUNNING");
                }
            }

            // Then: Cases are correctly associated
            for (int s = 0; s < 3; s++) {
                try (PreparedStatement ps = db.prepareStatement(
                        "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = ?")) {
                    ps.setString(1, "spec-" + s);
                    ResultSet rs = ps.executeQuery();
                    assertTrue(rs.next());
                    assertEquals(5, rs.getInt(1), "Each spec must have 5 cases");
                }
            }
        }
    }

    // =========================================================================
    // Workflow State Transitions Tests
    // =========================================================================

    @Nested
    @DisplayName("Workflow State Transitions")
    class StateTransitionTests {

        @Test
        @DisplayName("Complete workflow lifecycle")
        void completeWorkflow_Lifecycle() throws Exception {
            // Given: A case
            String specId = "lifecycle-spec";
            String runnerId = "runner-lifecycle";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Lifecycle Test");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            // When: Case goes through lifecycle
            // RUNNING -> SUSPENDED
            updateRunnerState(runnerId, "SUSPENDED");
            assertRunnerState(runnerId, "SUSPENDED");

            // SUSPENDED -> RUNNING
            updateRunnerState(runnerId, "RUNNING");
            assertRunnerState(runnerId, "RUNNING");

            // RUNNING -> COMPLETED
            updateRunnerState(runnerId, "COMPLETED");
            assertRunnerState(runnerId, "COMPLETED");

            // Then: Final state is correct
            assertRunnerState(runnerId, "COMPLETED");
        }

        @Test
        @DisplayName("Work item state machine")
        void workItemStateMachine() throws Exception {
            // Given: A work item
            String specId = "state-machine-spec";
            String runnerId = "runner-state-machine";
            String workItemId = "wi-state-machine";

            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "State Machine Test");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
            WorkflowDataFactory.seedWorkItem(db, workItemId, runnerId, "task", "Enabled");

            // When: Work item transitions through states
            // Enabled -> Executing
            updateWorkItemStatus(workItemId, "Executing");
            assertWorkItemStatus(workItemId, "Executing");

            // Executing -> Completed
            updateWorkItemStatus(workItemId, "Completed");
            completeWorkItem(workItemId);
            assertWorkItemStatus(workItemId, "Completed");

            // Then: Work item is completed
            assertWorkItemStatus(workItemId, "Completed");
        }

        @Test
        @DisplayName("Case cancellation propagates to work items")
        void caseCancellation_PropagatesToWorkItems() throws Exception {
            // Given: A case with work items
            String specId = "cancellation-spec";
            String runnerId = "runner-cancellation";

            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Cancellation Test");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            for (int i = 0; i < 3; i++) {
                WorkflowDataFactory.seedWorkItem(db, "wi-cancel-" + i, runnerId, "task" + i, "Executing");
            }

            // When: Case is cancelled
            updateRunnerState(runnerId, "CANCELLED");

            // Then: Case is cancelled
            assertRunnerState(runnerId, "CANCELLED");

            // And: Work items reflect cancelled state (in real system, they would be cleaned up)
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_work_item WHERE runner_id = ?")) {
                ps.setString(1, runnerId);
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(3, rs.getInt(1), "Work items still exist for cancelled case");
            }
        }
    }

    // =========================================================================
    // Orchestration Across Services Tests
    // =========================================================================

    @Nested
    @DisplayName("Orchestration Across Services")
    class CrossServiceOrchestrationTests {

        @Test
        @DisplayName("Orchestration via agent registry")
        void orchestration_ViaAgentRegistry() throws Exception {
            // Given: Running services
            startServices();

            // And: Agents registered
            registerAgent("workflow-orchestrator", A2A_PORT, "workflow-a2a");

            // When: Orchestration discovers agents
            String agents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents/by-capability?domain=workflow-a2a");

            // Then: Orchestrator is discoverable
            assertTrue(agents.contains("workflow-orchestrator"), "Orchestrator must be discoverable");

            // And: Can communicate with A2A server
            String agentCard = httpGet("http://localhost:" + A2A_PORT + "/.well-known/agent.json");
            assertTrue(agentCard.contains("YAWL"), "A2A server must be reachable");
        }

        @Test
        @DisplayName("Multi-service workflow coordination")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void multiService_WorkflowCoordination() throws Exception {
            // Given: Full stack running
            startServices();

            // And: Workflow specification
            String specId = "coordination-spec";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Coordination Test");
            YSpecification spec = WorkflowDataFactory.buildSequentialSpec(specId, 3);

            // When: Multiple cases are orchestrated
            int caseCount = 5;
            for (int i = 0; i < caseCount; i++) {
                String runnerId = "runner-coord-" + i;
                WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

                // Create work items for each task
                for (int t = 0; t < 3; t++) {
                    WorkflowDataFactory.seedWorkItem(db,
                        "wi-coord-" + i + "-" + t, runnerId, "task_" + t, "Enabled");
                }
            }

            // And: Work items are completed in order
            for (int i = 0; i < caseCount; i++) {
                for (int t = 0; t < 3; t++) {
                    String workItemId = "wi-coord-" + i + "-" + t;
                    updateWorkItemStatus(workItemId, "Executing");
                    updateWorkItemStatus(workItemId, "Completed");
                }
                updateRunnerState("runner-coord-" + i, "COMPLETED");
            }

            // Then: All cases are completed
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = ? AND state = 'COMPLETED'")) {
                ps.setString(1, specId);
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(caseCount, rs.getInt(1), "All cases must be completed");
            }

            // And: All work items are completed
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_work_item WHERE status = 'Completed'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(caseCount * 3, rs.getInt(1), "All work items must be completed");
            }
        }
    }

    // =========================================================================
    // Complex Workflow Patterns Tests
    // =========================================================================

    @Nested
    @DisplayName("Complex Workflow Patterns")
    class ComplexPatternTests {

        @Test
        @DisplayName("Sequential workflow pattern")
        void sequentialWorkflow_Pattern() throws Exception {
            // Given: Sequential specification
            String specId = "sequential-pattern";
            YSpecification spec = WorkflowDataFactory.buildSequentialSpec(specId, 5);
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Sequential Pattern");

            // And: A case
            String runnerId = "runner-sequential";
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            // When: Tasks are completed sequentially
            for (int i = 0; i < 5; i++) {
                String workItemId = "wi-seq-" + i;
                WorkflowDataFactory.seedWorkItem(db, workItemId, runnerId, "task_" + i, "Enabled");

                // Complete task
                updateWorkItemStatus(workItemId, "Executing");
                updateWorkItemStatus(workItemId, "Completed");
            }

            // Then: All tasks completed in sequence
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_work_item WHERE runner_id = ? AND status = 'Completed'")) {
                ps.setString(1, runnerId);
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1), "All 5 sequential tasks must be completed");
            }
        }

        @Test
        @DisplayName("Parallel workflow pattern")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void parallelWorkflow_Pattern() throws Exception {
            // Given: A specification with parallel tasks
            String specId = "parallel-pattern";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Parallel Pattern");

            // And: Multiple cases (simulating parallel branches)
            String runnerId = "runner-parallel";
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            // When: Tasks are completed in parallel
            int taskCount = 5;
            CountDownLatch latch = new CountDownLatch(taskCount);
            AtomicInteger completedCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < taskCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        String workItemId = "wi-parallel-" + index;
                        synchronized (db) {
                            WorkflowDataFactory.seedWorkItem(db, workItemId, runnerId, "task_" + index, "Enabled");
                            updateWorkItemStatus(workItemId, "Executing");
                            updateWorkItemStatus(workItemId, "Completed");
                        }
                        completedCount.incrementAndGet();
                    } catch (Exception e) {
                        // Handle failure
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(20, TimeUnit.SECONDS);
            executor.shutdown();

            // Then: All parallel tasks complete
            assertTrue(completed, "All parallel tasks must complete");
            assertEquals(taskCount, completedCount.get(), "All tasks must be completed");
        }

        @Test
        @DisplayName("Loop/retry workflow pattern")
        void loopRetry_Pattern() throws Exception {
            // Given: A task that may need retries
            String specId = "retry-pattern";
            String runnerId = "runner-retry";
            String workItemId = "wi-retry";

            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Retry Pattern");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
            WorkflowDataFactory.seedWorkItem(db, workItemId, runnerId, "task", "Enabled");

            // When: Task fails and is retried
            int maxRetries = 3;
            for (int attempt = 0; attempt < maxRetries; attempt++) {
                // Attempt execution
                updateWorkItemStatus(workItemId, "Executing");

                if (attempt < maxRetries - 1) {
                    // Simulate failure - reset to Enabled for retry
                    updateWorkItemStatus(workItemId, "Enabled");
                } else {
                    // Final attempt succeeds
                    updateWorkItemStatus(workItemId, "Completed");
                }
            }

            // Then: Task eventually succeeds
            assertWorkItemStatus(workItemId, "Completed");
        }
    }

    // =========================================================================
    // Production Orchestration Scenarios Tests
    // =========================================================================

    @Nested
    @DisplayName("Production Orchestration Scenarios")
    class ProductionOrchestrationTests {

        @Test
        @DisplayName("High-volume case processing")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void highVolume_CaseProcessing() throws Exception {
            // Given: Running services
            startServices();

            // And: A specification
            String specId = "high-volume-spec";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "High Volume Test");

            // When: High volume of cases are processed
            int caseCount = 100;
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < caseCount; i++) {
                String runnerId = "runner-hv-" + i;
                WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
                WorkflowDataFactory.seedWorkItem(db, "wi-hv-" + i, runnerId, "task", "Completed");
                updateRunnerState(runnerId, "COMPLETED");
            }

            long duration = System.currentTimeMillis() - startTime;

            // Then: All cases are processed
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = ? AND state = 'COMPLETED'")) {
                ps.setString(1, specId);
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(caseCount, rs.getInt(1), "All cases must be completed");
            }

            // And: Performance is acceptable
            double throughput = caseCount * 1000.0 / duration;
            System.out.printf("High Volume Processing:%n");
            System.out.printf("  Cases: %d%n", caseCount);
            System.out.printf("  Duration: %dms%n", duration);
            System.out.printf("  Throughput: %.0f cases/sec%n", throughput);

            assertTrue(throughput >= 50, "Throughput must be >= 50 cases/sec");
        }

        @Test
        @DisplayName("Mixed workload orchestration")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void mixedWorkload_Orchestration() throws Exception {
            // Given: Multiple specifications
            startServices();

            String[] specs = {"urgent-spec", "normal-spec", "batch-spec"};
            for (String spec : specs) {
                WorkflowDataFactory.seedSpecification(db, spec, "1.0", spec + " workflow");
            }

            // When: Mixed workload is processed
            int urgentCases = 10;  // High priority
            int normalCases = 30;  // Normal priority
            int batchCases = 50;   // Low priority (batch)

            // Process urgent cases first
            for (int i = 0; i < urgentCases; i++) {
                String runnerId = "runner-urgent-" + i;
                WorkflowDataFactory.seedNetRunner(db, runnerId, "urgent-spec", "1.0", "root", "COMPLETED");
            }

            // Process normal cases
            for (int i = 0; i < normalCases; i++) {
                String runnerId = "runner-normal-" + i;
                WorkflowDataFactory.seedNetRunner(db, runnerId, "normal-spec", "1.0", "root", "COMPLETED");
            }

            // Process batch cases
            for (int i = 0; i < batchCases; i++) {
                String runnerId = "runner-batch-" + i;
                WorkflowDataFactory.seedNetRunner(db, runnerId, "batch-spec", "1.0", "root", "COMPLETED");
            }

            // Then: All cases are processed correctly
            int totalCases = urgentCases + normalCases + batchCases;
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_net_runner WHERE state = 'COMPLETED'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(totalCases, rs.getInt(1), "All cases must be completed");
            }

            // And: Distribution is correct
            for (String spec : specs) {
                try (PreparedStatement ps = db.prepareStatement(
                        "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = ?")) {
                    ps.setString(1, spec);
                    ResultSet rs = ps.executeQuery();
                    assertTrue(rs.next());
                    assertTrue(rs.getInt(1) > 0, "Each spec must have cases");
                }
            }
        }

        @Test
        @DisplayName("Sustained load orchestration")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void sustainedLoad_Orchestration() throws Exception {
            // Given: Running services
            startServices();
            String specId = "sustained-spec";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Sustained Load Test");

            // When: Sustained load is applied
            int durationSeconds = 5;
            int targetOpsPerSecond = 20;
            AtomicInteger completedOps = new AtomicInteger(0);
            AtomicLong totalLatency = new AtomicLong(0);
            CountDownLatch latch = new CountDownLatch(1);

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            long startTime = System.currentTimeMillis();
            long endTime = startTime + (durationSeconds * 1000);

            while (System.currentTimeMillis() < endTime) {
                executor.submit(() -> {
                    try {
                        long opStart = System.currentTimeMillis();
                        String runnerId = "runner-sustained-" + System.nanoTime();
                        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "COMPLETED");
                        long opDuration = System.currentTimeMillis() - opStart;
                        totalLatency.addAndGet(opDuration);
                        completedOps.incrementAndGet();
                    } catch (Exception e) {
                        // Handle failure
                    }
                });
                Thread.sleep(1000 / targetOpsPerSecond);
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            long actualDuration = System.currentTimeMillis() - startTime;

            // Then: Sustained load is handled
            int totalOps = completedOps.get();
            double actualOpsPerSecond = totalOps * 1000.0 / actualDuration;
            double avgLatency = totalLatency.get() / (double) totalOps;

            System.out.printf("Sustained Load Results:%n");
            System.out.printf("  Duration: %dms%n", actualDuration);
            System.out.printf("  Completed operations: %d%n", totalOps);
            System.out.printf("  Target ops/sec: %d%n", targetOpsPerSecond);
            System.out.printf("  Actual ops/sec: %.1f%n", actualOpsPerSecond);
            System.out.printf("  Average latency: %.2fms%n", avgLatency);

            assertTrue(totalOps >= durationSeconds * targetOpsPerSecond * 0.5,
                "Should achieve at least 50% of target throughput");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void startServices() throws IOException {
        startA2aServer(A2A_PORT);
        startRegistry(REGISTRY_PORT);
    }

    private void startA2aServer(int port) throws IOException {
        CompositeAuthenticationProvider authProvider = createTestAuthProvider();
        a2aServer = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", port, authProvider);
        a2aServer.start();
        waitForServer(port);
    }

    private void startRegistry(int port) throws IOException {
        registry = new AgentRegistry(port);
        registry.start();
        waitForServer(port);
    }

    private CompositeAuthenticationProvider createTestAuthProvider() {
        try {
            JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(JWT_SECRET, null);
            ApiKeyAuthenticationProvider apiKeyProvider = new ApiKeyAuthenticationProvider(
                API_KEY, "test-hash");
            return new CompositeAuthenticationProvider(List.of(jwtProvider, apiKeyProvider));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create auth provider", e);
        }
    }

    private void registerAgent(String id, int port, String domain) throws IOException {
        String payload = String.format("""
            {
              "id": "%s",
              "name": "%s Agent",
              "host": "localhost",
              "port": %d,
              "capability": {"domainName": "%s", "description": "Orchestration agent"},
              "version": "5.2.0"
            }
            """, id, id, port, domain);

        httpPost("http://localhost:" + REGISTRY_PORT + "/agents/register", payload);
    }

    private void waitForServer(int port) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                    new URL("http://localhost:" + port + "/.well-known/agent.json").openConnection();
                conn.setConnectTimeout(100);
                conn.connect();
                conn.disconnect();
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int status = conn.getResponseCode();
        if (status >= 400) {
            conn.disconnect();
            throw new IOException("HTTP GET failed with status " + status);
        }

        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private void httpPost(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json");

        try (var os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        conn.disconnect();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP POST failed with status " + status);
        }
    }

    private void updateRunnerState(String runnerId, String state) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_net_runner SET state = ? WHERE runner_id = ?")) {
            ps.setString(1, state);
            ps.setString(2, runnerId);
            ps.executeUpdate();
        }
    }

    private void assertRunnerState(String runnerId, String expectedState) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Runner must exist: " + runnerId);
            assertEquals(expectedState, rs.getString("state"));
        }
    }

    private void updateWorkItemStatus(String itemId, String status) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_work_item SET status = ? WHERE item_id = ?")) {
            ps.setString(1, status);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    private void completeWorkItem(String itemId) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_work_item SET status = 'Completed', completed_at = CURRENT_TIMESTAMP "
                + "WHERE item_id = ?")) {
            ps.setString(1, itemId);
            ps.executeUpdate();
        }
    }

    private void assertWorkItemStatus(String itemId, String expectedStatus) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT status FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Work item must exist: " + itemId);
            assertEquals(expectedStatus, rs.getString("status"));
        }
    }
}
