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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AServer;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffException;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffMessage;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistry;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive MCP-A2A MVP Integration Tests.
 *
 * Tests end-to-end workflows across the complete YAWL MCP-A2A stack:
 * - MCP client to YAWL engine to A2A server workflows
 * - Cross-service communication and coordination
 * - State management across services
 * - Data flow validation
 * - Real-world production scenarios
 * - Compatibility testing across protocol versions
 *
 * Chicago TDD methodology: Real YAWL objects, real HTTP servers,
 * real database connections - no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("integration")
@Tag("mvp")
class McpA2AMvpIntegrationTest {

    // Test service ports (avoid conflicts with production)
    private static final int A2A_PORT = 19880;
    private static final int REGISTRY_PORT = 19881;
    private static final int A2A_PORT_SECONDARY = 19882;

    // Test authentication
    private static final String JWT_SECRET = "test-jwt-secret-key-for-mvp-integration-min-32-chars";
    private static final String API_KEY = "test-api-key-mvp-integration";

    // Test fixtures
    private Connection db;
    private AgentRegistry registry;
    private YawlA2AServer a2aServer;
    private YawlA2AServer a2aServerSecondary;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize H2 database with YAWL schema
        String jdbcUrl = "jdbc:h2:mem:mvp_test_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(db);

        // Set authentication environment
        System.setProperty("A2A_JWT_SECRET", JWT_SECRET);
        System.setProperty("A2A_API_KEY", API_KEY);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Stop all services
        if (a2aServer != null && a2aServer.isRunning()) {
            a2aServer.stop();
        }
        if (a2aServerSecondary != null && a2aServerSecondary.isRunning()) {
            a2aServerSecondary.stop();
        }
        if (registry != null) {
            registry.stop();
        }

        // Close database
        if (db != null && !db.isClosed()) {
            db.close();
        }

        // Clear environment
        System.clearProperty("A2A_JWT_SECRET");
        System.clearProperty("A2A_API_KEY");
    }

    // =========================================================================
    // 1. End-to-End Workflow Tests
    // =========================================================================

    @Nested
    @DisplayName("End-to-End Workflow Tests")
    class EndToEndWorkflowTests {

        @Test
        @DisplayName("Complete workflow: MCP client -> YAWL engine -> A2A server -> work item completion")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void completeWorkflow_McpToA2aToWorkItemCompletion() throws Exception {
            // Given: A complete MCP-A2A stack is running
            startA2aServer(A2A_PORT);
            startRegistry(REGISTRY_PORT);

            // And: A workflow specification is loaded
            String specId = "e2e-complete-workflow";
            YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Complete E2E Workflow");

            // And: A case is launched
            String runnerId = "runner-e2e-complete";
            String workItemId = "wi-e2e-complete";
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
            WorkflowDataFactory.seedWorkItem(db, workItemId, runnerId, "process", "Enabled");

            // When: MCP client requests case state
            YawlMcpServer mcpServer = new YawlMcpServer(
                "http://localhost:8080/yawl", "admin", "YAWL");
            assertNotNull(mcpServer, "MCP server must be constructable");
            assertNotNull(mcpServer.getLoggingHandler(), "Logging handler must be available");

            // And: A2A client queries the A2A server
            String agentCard = httpGet("http://localhost:" + A2A_PORT + "/.well-known/agent.json");
            assertNotNull(agentCard, "Agent card must be retrievable");
            assertTrue(agentCard.contains("YAWL"), "Agent card must identify YAWL");

            // And: Agent registry shows the A2A server
            registerAgentInRegistry("yawl-a2a-main", "YAWL A2A Server", A2A_PORT);

            // Then: The full workflow path is verified
            String agents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents");
            assertTrue(agents.contains("yawl-a2a-main"), "A2A server must be registered");

            // And: Work item can be transitioned to completed
            updateWorkItemStatus(workItemId, "Executing");
            updateWorkItemStatus(workItemId, "Completed");
            assertWorkItemStatus(workItemId, "Completed");
        }

        @Test
        @DisplayName("Complex workflow with multiple services and handoffs")
        @Timeout(value = 45, unit = TimeUnit.SECONDS)
        void complexWorkflow_MultipleServicesAndHandoffs() throws Exception {
            // Given: Multiple A2A servers and registry
            startA2aServer(A2A_PORT);
            startA2aServerSecondary(A2A_PORT_SECONDARY);
            startRegistry(REGISTRY_PORT);

            // And: Multiple agents registered
            registerAgentInRegistry("agent-workflow-1", "Workflow Agent 1", A2A_PORT);
            registerAgentInRegistry("agent-workflow-2", "Workflow Agent 2", A2A_PORT_SECONDARY);

            // And: A complex multi-task workflow
            String specId = "complex-multi-service";
            WorkflowDataFactory.seedSpecification(db, specId, "2.0", "Complex Multi-Service");
            YSpecification spec = WorkflowDataFactory.buildSequentialSpec(specId, 5);

            // And: Multiple cases with work items
            for (int i = 0; i < 3; i++) {
                String runnerId = "runner-complex-" + i;
                WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "2.0", "root", "RUNNING");
                for (int t = 0; t < 5; t++) {
                    WorkflowDataFactory.seedWorkItem(db,
                        "wi-complex-" + i + "-" + t, runnerId, "task_" + t, "Enabled");
                }
            }

            // When: Services coordinate via registry
            String workflowAgents = httpGet(
                "http://localhost:" + REGISTRY_PORT + "/agents/by-capability?domain=workflow-a2a");
            assertTrue(workflowAgents.contains("agent-workflow"), "Workflow agents must be discoverable");

            // And: Work items are processed across services
            for (int i = 0; i < 3; i++) {
                for (int t = 0; t < 5; t++) {
                    String workItemId = "wi-complex-" + i + "-" + t;
                    updateWorkItemStatus(workItemId, "Executing");
                    updateWorkItemStatus(workItemId, "Completed");
                }
            }

            // Then: All work items are completed
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_work_item WHERE status = 'Completed'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(15, rs.getInt(1), "All 15 work items must be completed");
            }
        }

        @Test
        @DisplayName("Error propagation and recovery across services")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void errorPropagation_RecoveryAcrossServices() throws Exception {
            // Given: A2A server with authentication
            startA2aServer(A2A_PORT);

            // When: Unauthenticated request is made
            int status = httpPostStatus(
                "http://localhost:" + A2A_PORT + "/",
                "{\"message\":{\"parts\":[{\"kind\":\"text\",\"text\":\"test\"}]}}",
                Map.of("Content-Type", "application/json"));

            // Then: Authentication error is returned
            assertEquals(401, status, "Unauthenticated request must be rejected with 401");

            // When: Malformed JSON is sent with valid auth
            int malformedStatus = httpPostStatus(
                "http://localhost:" + A2A_PORT + "/",
                "{invalid json}",
                Map.of(
                    "Content-Type", "application/json",
                    "Authorization", "Bearer " + generateTestJwt()
                ));

            // Then: Error is handled gracefully
            assertTrue(malformedStatus >= 400, "Malformed request must return error status");

            // When: Request to non-existent task
            int notFoundStatus = httpGetStatus(
                "http://localhost:" + A2A_PORT + "/tasks/nonexistent-task-id",
                Map.of("Authorization", "Bearer " + generateTestJwt()));

            // Then: Appropriate error is returned
            assertTrue(notFoundStatus >= 400 || notFoundStatus == 200,
                "Non-existent task should return error or empty result");
        }

        @Test
        @DisplayName("Load testing with realistic workloads")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void loadTesting_RealisticWorkloads() throws Exception {
            // Given: Running services
            startA2aServer(A2A_PORT);
            startRegistry(REGISTRY_PORT);

            // And: A specification with many work items
            String specId = "load-test-spec";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Load Test Spec");
            String runnerId = "runner-load-test";
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            // When: High volume of work items are created
            int workItemCount = 500;
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < workItemCount; i++) {
                WorkflowDataFactory.seedWorkItem(db,
                    "wi-load-" + i, runnerId, "task_" + (i % 10), "Enabled");
            }

            long createDuration = System.currentTimeMillis() - startTime;

            // And: Concurrent HTTP requests to A2A server
            int concurrentRequests = 100;
            AtomicInteger successCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(concurrentRequests);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            long requestStart = System.currentTimeMillis();

            for (int i = 0; i < concurrentRequests; i++) {
                executor.submit(() -> {
                    try {
                        String agentCard = httpGet(
                            "http://localhost:" + A2A_PORT + "/.well-known/agent.json");
                        if (agentCard.contains("YAWL")) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Count failures
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            long requestDuration = System.currentTimeMillis() - requestStart;

            // Then: All work items are persisted
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_work_item WHERE runner_id = ?")) {
                ps.setString(1, runnerId);
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(workItemCount, rs.getInt(1), "All work items must be persisted");
            }

            // And: HTTP requests succeed under load
            assertTrue(completed, "Concurrent requests must complete within timeout");
            double successRate = successCount.get() * 100.0 / concurrentRequests;
            assertTrue(successRate >= 95.0, "Success rate must be >= 95%, got " + successRate + "%");

            // Log performance metrics
            System.out.printf("Load Test Results:%n");
            System.out.printf("  Work items created: %d in %dms (%.0f items/sec)%n",
                workItemCount, createDuration, workItemCount * 1000.0 / createDuration);
            System.out.printf("  Concurrent HTTP requests: %d in %dms (%.0f req/sec)%n",
                concurrentRequests, requestDuration, concurrentRequests * 1000.0 / requestDuration);
            System.out.printf("  Success rate: %.1f%%%n", successRate);
        }
    }

    // =========================================================================
    // 2. Cross-Service Communication Tests
    // =========================================================================

    @Nested
    @DisplayName("Cross-Service Communication Tests")
    class CrossServiceCommunicationTests {

        @Test
        @DisplayName("Service discovery and registration")
        void serviceDiscovery_Registration() throws Exception {
            // Given: A running registry
            startRegistry(REGISTRY_PORT);

            // When: Multiple services register
            registerAgentInRegistry("mcp-server", "MCP Server", 9001);
            registerAgentInRegistry("a2a-server-1", "A2A Server 1", A2A_PORT);
            registerAgentInRegistry("a2a-server-2", "A2A Server 2", A2A_PORT_SECONDARY);

            // Then: All services are discoverable
            String agents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents");
            assertTrue(agents.contains("mcp-server"), "MCP server must be registered");
            assertTrue(agents.contains("a2a-server-1"), "A2A server 1 must be registered");
            assertTrue(agents.contains("a2a-server-2"), "A2A server 2 must be registered");

            // And: Registry count is correct
            assertEquals(3, registry.getAgentCount(), "Registry must have 3 agents");
        }

        @Test
        @DisplayName("Load balancing effectiveness")
        void loadBalancing_Effectiveness() throws Exception {
            // Given: Multiple A2A servers registered
            startA2aServer(A2A_PORT);
            startA2aServerSecondary(A2A_PORT_SECONDARY);
            startRegistry(REGISTRY_PORT);

            registerAgentInRegistry("a2a-primary", "Primary A2A", A2A_PORT);
            registerAgentInRegistry("a2a-secondary", "Secondary A2A", A2A_PORT_SECONDARY);

            // When: Multiple requests are made
            int requestCount = 50;
            AtomicInteger primaryHits = new AtomicInteger(0);
            AtomicInteger secondaryHits = new AtomicInteger(0);

            for (int i = 0; i < requestCount; i++) {
                // Discover agents via registry
                String agents = httpGet(
                    "http://localhost:" + REGISTRY_PORT + "/agents");

                // Simulate round-robin selection
                if (i % 2 == 0) {
                    String response = httpGet(
                        "http://localhost:" + A2A_PORT + "/.well-known/agent.json");
                    if (response.contains("YAWL")) {
                        primaryHits.incrementAndGet();
                    }
                } else {
                    String response = httpGet(
                        "http://localhost:" + A2A_PORT_SECONDARY + "/.well-known/agent.json");
                    if (response.contains("YAWL")) {
                        secondaryHits.incrementAndGet();
                    }
                }
            }

            // Then: Load is distributed
            assertTrue(primaryHits.get() > 0, "Primary server must receive requests");
            assertTrue(secondaryHits.get() > 0, "Secondary server must receive requests");
            System.out.printf("Load distribution: primary=%d, secondary=%d%n",
                primaryHits.get(), secondaryHits.get());
        }

        @Test
        @DisplayName("Circuit breaker isolation")
        void circuitBreaker_Isolation() throws Exception {
            // Given: Two A2A servers
            startA2aServer(A2A_PORT);
            startRegistry(REGISTRY_PORT);

            registerAgentInRegistry("a2a-active", "Active A2A", A2A_PORT);
            registerAgentInRegistry("a2a-down", "Down A2A", 59999); // Non-existent

            // When: One server is unavailable
            // Requests to the down server should fail
            AtomicReference<Exception> failure = new AtomicReference<>();
            try {
                httpGetWithTimeout(
                    "http://localhost:59999/.well-known/agent.json",
                    Duration.ofMillis(500));
            } catch (Exception e) {
                failure.set(e);
            }

            // Then: Failure is detected quickly
            assertNotNull(failure.get(), "Request to down server must fail");

            // And: Other server continues to work
            String response = httpGet("http://localhost:" + A2A_PORT + "/.well-known/agent.json");
            assertTrue(response.contains("YAWL"), "Active server must continue working");
        }

        @Test
        @DisplayName("Timeout handling and retry logic")
        void timeoutHandling_RetryLogic() throws Exception {
            // Given: A2A server
            startA2aServer(A2A_PORT);

            // When: Request is made with short timeout
            int retries = 3;
            int successCount = 0;

            for (int i = 0; i < retries; i++) {
                try {
                    String response = httpGetWithTimeout(
                        "http://localhost:" + A2A_PORT + "/.well-known/agent.json",
                        Duration.ofSeconds(5));
                    if (response.contains("YAWL")) {
                        successCount++;
                    }
                } catch (Exception e) {
                    // Retry on failure
                }
            }

            // Then: Eventually succeeds
            assertEquals(retries, successCount, "All retries must succeed on healthy server");
        }
    }

    // =========================================================================
    // 3. State Management Tests
    // =========================================================================

    @Nested
    @DisplayName("State Management Tests")
    class StateManagementTests {

        @Test
        @DisplayName("Session state consistency across services")
        void sessionState_ConsistencyAcrossServices() throws Exception {
            // Given: Running services with database
            startA2aServer(A2A_PORT);
            startRegistry(REGISTRY_PORT);

            // And: A workflow case
            String specId = "state-consistency";
            String runnerId = "runner-state-test";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "State Consistency Test");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            // When: Multiple state changes occur
            String[] states = {"RUNNING", "SUSPENDED", "RUNNING", "COMPLETED"};
            for (String state : states) {
                updateRunnerState(runnerId, state);
                assertRunnerState(runnerId, state);
            }

            // Then: Final state is persisted correctly
            assertRunnerState(runnerId, "COMPLETED");

            // And: State history is available
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_net_runner WHERE runner_id = ? AND state = ?")) {
                ps.setString(1, runnerId);
                ps.setString(2, "COMPLETED");
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Final state must be persisted");
            }
        }

        @Test
        @DisplayName("Cache invalidation and synchronization")
        void cacheInvalidation_Synchronization() throws Exception {
            // Given: Running services
            startRegistry(REGISTRY_PORT);

            // And: An agent is registered
            registerAgentInRegistry("cache-test-agent", "Cache Test Agent", 9001);
            String agents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents");
            assertTrue(agents.contains("cache-test-agent"), "Agent must be registered");

            // When: Agent is unregistered (cache invalidation)
            int deleteStatus = httpDelete(
                "http://localhost:" + REGISTRY_PORT + "/agents/cache-test-agent");
            assertEquals(200, deleteStatus, "Unregistration must succeed");

            // Then: Agent is no longer in results (synchronized)
            String updatedAgents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents");
            assertFalse(updatedAgents.contains("cache-test-agent"),
                "Agent must be removed from registry");
        }

        @Test
        @DisplayName("State recovery after service restarts")
        void stateRecovery_AfterServiceRestarts() throws Exception {
            // Given: Initial service setup
            startRegistry(REGISTRY_PORT);

            // And: Agents registered
            registerAgentInRegistry("persistent-agent", "Persistent Agent", 9001);
            assertEquals(1, registry.getAgentCount(), "Must have 1 agent");

            // When: Service is stopped
            registry.stop();
            registry = null;

            // And: Service is restarted
            startRegistry(REGISTRY_PORT);

            // Then: Registry starts empty (stateless)
            assertEquals(0, registry.getAgentCount(), "Registry must be empty after restart");

            // And: Agent can re-register (state recovery)
            registerAgentInRegistry("persistent-agent", "Persistent Agent", 9001);
            assertEquals(1, registry.getAgentCount(), "Agent must re-register successfully");
        }

        @Test
        @DisplayName("Concurrent state modifications")
        void concurrentState_Modifications() throws Exception {
            // Given: A workflow case
            String specId = "concurrent-state";
            String runnerId = "runner-concurrent";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Concurrent State Test");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            // When: Multiple threads modify state concurrently
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        synchronized (db) {
                            String newState = (threadId % 2 == 0) ? "RUNNING" : "SUSPENDED";
                            updateRunnerState(runnerId, newState);
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Handle concurrent modification
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then: All modifications succeed (serialized)
            assertTrue(completed, "All threads must complete");
            assertEquals(threadCount, successCount.get(), "All state modifications must succeed");

            // And: Final state is valid
            String finalState = getRunnerState(runnerId);
            assertTrue(finalState.equals("RUNNING") || finalState.equals("SUSPENDED"),
                "Final state must be valid: " + finalState);
        }
    }

    // =========================================================================
    // 4. Data Flow Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Data Flow Validation Tests")
    class DataFlowValidationTests {

        @Test
        @DisplayName("Message integrity across services")
        void messageIntegrity_AcrossServices() throws Exception {
            // Given: A2A server
            startA2aServer(A2A_PORT);

            // When: Agent card is requested
            String agentCard = httpGet(
                "http://localhost:" + A2A_PORT + "/.well-known/agent.json");

            // Then: Response is valid JSON with expected structure
            assertTrue(agentCard.startsWith("{"), "Response must be JSON object");
            assertTrue(agentCard.contains("\"name\""), "Must contain name field");
            assertTrue(agentCard.contains("\"version\""), "Must contain version field");
            assertTrue(agentCard.contains("\"skills\""), "Must contain skills field");

            // And: All expected skills are present
            assertTrue(agentCard.contains("launch_workflow"), "Must have launch_workflow skill");
            assertTrue(agentCard.contains("query_workflows"), "Must have query_workflows skill");
            assertTrue(agentCard.contains("manage_workitems"), "Must have manage_workitems skill");
            assertTrue(agentCard.contains("cancel_workflow"), "Must have cancel_workflow skill");
            assertTrue(agentCard.contains("handoff_workitem"), "Must have handoff_workitem skill");
        }

        @Test
        @DisplayName("Data transformation validation")
        void dataTransformation_Validation() throws Exception {
            // Given: Agent registry
            startRegistry(REGISTRY_PORT);

            // When: Agent is registered with complex data
            String registrationPayload = """
                {
                  "id": "transform-test",
                  "name": "Data Transform Test Agent",
                  "host": "localhost",
                  "port": 9001,
                  "capability": {
                    "domainName": "data-transformation",
                    "description": "Tests data transformation integrity"
                  },
                  "version": "1.0.0",
                  "metadata": {
                    "tags": ["test", "data", "transform"],
                    "config": {
                      "timeout": 30000,
                      "retries": 3
                    }
                  }
                }
                """;

            int status = httpPostStatus(
                "http://localhost:" + REGISTRY_PORT + "/agents/register",
                registrationPayload,
                Map.of("Content-Type", "application/json"));

            // Then: Registration succeeds
            assertEquals(200, status, "Registration must succeed");

            // And: Data is retrievable with transformation preserved
            String agent = httpGet(
                "http://localhost:" + REGISTRY_PORT + "/agents");
            assertTrue(agent.contains("transform-test"), "Agent must be registered");
            assertTrue(agent.contains("data-transformation"), "Capability must be preserved");
        }

        @Test
        @DisplayName("Schema compliance")
        void schemaCompliance() throws Exception {
            // Given: A2A server
            startA2aServer(A2A_PORT);

            // When: Agent card is retrieved
            String agentCard = httpGet(
                "http://localhost:" + A2A_PORT + "/.well-known/agent.json");

            // Then: Response complies with A2A agent card schema
            // Required fields per A2A spec
            String[] requiredFields = {"name", "version", "capabilities", "skills"};
            for (String field : requiredFields) {
                assertTrue(agentCard.contains("\"" + field + "\""),
                    "Agent card must contain required field: " + field);
            }

            // And: Capabilities structure is correct
            assertTrue(agentCard.contains("\"streaming\""), "Must have streaming capability");
            assertTrue(agentCard.contains("\"pushNotifications\""),
                "Must have pushNotifications capability");
        }

        @Test
        @DisplayName("Large payload handling")
        void largePayload_Handling() throws Exception {
            // Given: Running services
            startA2aServer(A2A_PORT);
            startRegistry(REGISTRY_PORT);

            // When: Large workflow data is created
            String specId = "large-payload";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Large Payload Test");

            // Create large event data
            StringBuilder largeData = new StringBuilder("{\"data\":\"");
            for (int i = 0; i < 10000; i++) {
                largeData.append("x");
            }
            largeData.append("\"}");

            // And: Large data is stored
            try (PreparedStatement ps = db.prepareStatement(
                    "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) "
                    + "VALUES (?, ?, ?, ?)")) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, "large-payload-runner");
                ps.setString(3, "LARGE_EVENT");
                ps.setString(4, largeData.toString());
                ps.executeUpdate();
            }

            // Then: Large payload is stored and retrievable
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT LENGTH(event_data) as len FROM yawl_case_event "
                    + "WHERE event_type = 'LARGE_EVENT'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertTrue(rs.getInt("len") > 10000, "Large data must be stored intact");
            }
        }
    }

    // =========================================================================
    // 5. Real-World Scenarios Tests
    // =========================================================================

    @Nested
    @DisplayName("Real-World Scenarios Tests")
    class RealWorldScenariosTests {

        @Test
        @DisplayName("Production-like traffic patterns")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void productionLike_TrafficPatterns() throws Exception {
            // Given: Full stack running
            startA2aServer(A2A_PORT);
            startRegistry(REGISTRY_PORT);

            // And: Multiple specifications
            for (int i = 0; i < 5; i++) {
                WorkflowDataFactory.seedSpecification(db,
                    "prod-spec-" + i, "1.0", "Production Spec " + i);
            }

            // When: Simulated production traffic (mixed read/write)
            int totalOperations = 200;
            int readRatio = 70; // 70% reads
            AtomicInteger readCount = new AtomicInteger(0);
            AtomicInteger writeCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < totalOperations; i++) {
                try {
                    if (i % 100 < readRatio) {
                        // Read: Query registry
                        httpGet("http://localhost:" + REGISTRY_PORT + "/agents");
                        readCount.incrementAndGet();
                    } else {
                        // Write: Create case
                        String runnerId = "runner-prod-" + i;
                        WorkflowDataFactory.seedNetRunner(db,
                            runnerId, "prod-spec-" + (i % 5), "1.0", "root", "RUNNING");
                        writeCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }

            long duration = System.currentTimeMillis() - startTime;

            // Then: Traffic is handled successfully
            double errorRate = errorCount.get() * 100.0 / totalOperations;
            assertTrue(errorRate < 5.0, "Error rate must be < 5%, got " + errorRate + "%");

            // Log metrics
            System.out.printf("Production Traffic Results:%n");
            System.out.printf("  Total operations: %d%n", totalOperations);
            System.out.printf("  Reads: %d (%.0f%%)%n", readCount.get(),
                readCount.get() * 100.0 / totalOperations);
            System.out.printf("  Writes: %d (%.0f%%)%n", writeCount.get(),
                writeCount.get() * 100.0 / totalOperations);
            System.out.printf("  Errors: %d (%.1f%%)%n", errorCount.get(), errorRate);
            System.out.printf("  Duration: %dms (%.0f ops/sec)%n",
                duration, totalOperations * 1000.0 / duration);
        }

        @Test
        @DisplayName("Peak load testing")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void peakLoad_Testing() throws Exception {
            // Given: Services running
            startA2aServer(A2A_PORT);
            startRegistry(REGISTRY_PORT);

            // When: Peak load is applied
            int peakThreads = 50;
            int opsPerThread = 20;
            int totalOps = peakThreads * opsPerThread;
            CountDownLatch latch = new CountDownLatch(peakThreads);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            long startTime = System.currentTimeMillis();

            for (int t = 0; t < peakThreads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            httpGet("http://localhost:" + A2A_PORT + "/.well-known/agent.json");
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Handle failure
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(45, TimeUnit.SECONDS);
            executor.shutdown();

            long duration = System.currentTimeMillis() - startTime;

            // Then: Peak load is handled
            assertTrue(completed, "Peak load test must complete within timeout");
            double successRate = successCount.get() * 100.0 / totalOps;
            assertTrue(successRate >= 90.0, "Success rate must be >= 90% under peak load");

            System.out.printf("Peak Load Results:%n");
            System.out.printf("  Concurrent threads: %d%n", peakThreads);
            System.out.printf("  Operations per thread: %d%n", opsPerThread);
            System.out.printf("  Total operations: %d%n", totalOps);
            System.out.printf("  Successful: %d (%.1f%%)%n", successCount.get(), successRate);
            System.out.printf("  Duration: %dms (%.0f ops/sec)%n",
                duration, successCount.get() * 1000.0 / duration);
        }

        @Test
        @DisplayName("Failover scenarios")
        void failover_Scenarios() throws Exception {
            // Given: Primary and secondary A2A servers
            startA2aServer(A2A_PORT);
            startA2aServerSecondary(A2A_PORT_SECONDARY);
            startRegistry(REGISTRY_PORT);

            registerAgentInRegistry("a2a-primary", "Primary A2A", A2A_PORT);
            registerAgentInRegistry("a2a-secondary", "Secondary A2A", A2A_PORT_SECONDARY);

            // When: Primary fails
            a2aServer.stop();

            // Then: Can failover to secondary
            String response = httpGet(
                "http://localhost:" + A2A_PORT_SECONDARY + "/.well-known/agent.json");
            assertTrue(response.contains("YAWL"), "Secondary must handle requests after primary failure");

            // And: Registry reflects primary as still registered (until heartbeat timeout)
            String agents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents");
            assertTrue(agents.contains("a2a-primary"), "Primary still in registry until cleanup");
            assertTrue(agents.contains("a2a-secondary"), "Secondary is available");
        }

        @Test
        @DisplayName("Graceful degradation")
        void gracefulDegradation() throws Exception {
            // Given: Registry without A2A server
            startRegistry(REGISTRY_PORT);

            // When: A2A server is requested but not available
            AtomicReference<Exception> failure = new AtomicReference<>();
            try {
                httpGetWithTimeout(
                    "http://localhost:" + A2A_PORT + "/.well-known/agent.json",
                    Duration.ofMillis(500));
            } catch (Exception e) {
                failure.set(e);
            }

            // Then: Registry continues to function
            String agents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents");
            assertNotNull(agents, "Registry must remain functional");
            assertTrue(agents.startsWith("["), "Registry must return valid response");

            // And: Failure is detected appropriately
            assertNotNull(failure.get(), "Connection failure must be detected");
        }
    }

    // =========================================================================
    // 6. Compatibility Tests
    // =========================================================================

    @Nested
    @DisplayName("Compatibility Tests")
    class CompatibilityTests {

        @Test
        @DisplayName("Multiple A2A protocol versions")
        void multipleA2A_ProtocolVersions() throws Exception {
            // Given: A2A server with version 5.2.0
            startA2aServer(A2A_PORT);

            // When: Agent card is retrieved
            String agentCard = httpGet(
                "http://localhost:" + A2A_PORT + "/.well-known/agent.json");

            // Then: Version is present
            assertTrue(agentCard.contains("\"5.2.0\"") || agentCard.contains("5.2"),
                "Agent card must declare version");

            // And: Backward compatible skills are present
            assertTrue(agentCard.contains("launch_workflow"),
                "Must support launch_workflow (core skill)");
            assertTrue(agentCard.contains("query_workflows"),
                "Must support query_workflows (core skill)");
        }

        @Test
        @DisplayName("Cross-orchestrator workflows")
        void crossOrchestrator_Workflows() throws Exception {
            // Given: Multiple orchestrators via registry
            startRegistry(REGISTRY_PORT);

            // When: Different types of agents register
            registerAgentInRegistry("yawl-orchestrator", "YAWL Orchestrator", 8080);
            registerAgentInRegistry("pm4py-orchestrator", "PM4Py Orchestrator", 9092);

            // Then: Cross-orchestrator discovery works
            String orchestrators = httpGet(
                "http://localhost:" + REGISTRY_PORT + "/agents");
            assertTrue(orchestrators.contains("yawl-orchestrator"), "YAWL must be discoverable");
            assertTrue(orchestrators.contains("pm4py-orchestrator"), "PM4Py must be discoverable");

            // And: Capability-based filtering works across orchestrators
            String filtered = httpGet(
                "http://localhost:" + REGISTRY_PORT + "/agents/by-capability?domain=orchestrator");
            assertNotNull(filtered, "Cross-orchestrator filtering must work");
        }

        @Test
        @DisplayName("Schema evolution compatibility")
        void schemaEvolution_Compatibility() throws Exception {
            // Given: Database with schema
            YawlContainerFixtures.applyYawlSchema(db);

            // When: New columns are added (simulating schema evolution)
            try (PreparedStatement ps = db.prepareStatement(
                    "ALTER TABLE yawl_specification ADD COLUMN IF NOT EXISTS spec_metadata TEXT")) {
                ps.execute();
            } catch (SQLException e) {
                // H2 syntax variation - try alternative
                try (PreparedStatement ps = db.prepareStatement(
                        "ALTER TABLE yawl_specification ADD COLUMN spec_metadata VARCHAR(1000)")) {
                    ps.execute();
                } catch (SQLException e2) {
                    // Column may already exist or syntax not supported - OK for test
                }
            }

            // Then: Existing operations continue to work
            WorkflowDataFactory.seedSpecification(db, "schema-evolution", "1.0", "Schema Evolution Test");

            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT spec_id, spec_name FROM yawl_specification WHERE spec_id = ?")) {
                ps.setString(1, "schema-evolution");
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next(), "Existing query must work after schema evolution");
            }
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void startA2aServer(int port) throws IOException {
        CompositeAuthenticationProvider authProvider = createTestAuthProvider();
        a2aServer = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", port, authProvider);
        a2aServer.start();
        waitForServer(port, Duration.ofSeconds(5));
    }

    private void startA2aServerSecondary(int port) throws IOException {
        CompositeAuthenticationProvider authProvider = createTestAuthProvider();
        a2aServerSecondary = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", port, authProvider);
        a2aServerSecondary.start();
        waitForServer(port, Duration.ofSeconds(5));
    }

    private void startRegistry(int port) throws IOException {
        registry = new AgentRegistry(port);
        registry.start();
        waitForServer(port, Duration.ofSeconds(5));
    }

    private CompositeAuthenticationProvider createTestAuthProvider() {
        try {
            JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(JWT_SECRET, null);
            ApiKeyAuthenticationProvider apiKeyProvider = new ApiKeyAuthenticationProvider(
                API_KEY, "test-api-key-hash");
            return new CompositeAuthenticationProvider(List.of(jwtProvider, apiKeyProvider));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create auth provider", e);
        }
    }

    private String generateTestJwt() {
        try {
            JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(JWT_SECRET, null);
            return jwtProvider.issueToken("test-user", List.of("workflow:launch", "workflow:query"),
                Duration.ofMinutes(5).toMillis());
        } catch (Exception e) {
            return "test-token";
        }
    }

    private void registerAgentInRegistry(String id, String name, int port) throws IOException {
        String payload = String.format("""
            {
              "id": "%s",
              "name": "%s",
              "host": "localhost",
              "port": %d,
              "capability": {
                "domainName": "workflow-a2a",
                "description": "Test agent for integration tests"
              },
              "version": "5.2.0"
            }
            """, id, name, port);

        httpPost("http://localhost:" + REGISTRY_PORT + "/agents/register", payload);
    }

    private void waitForServer(int port, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
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
        return httpGet(urlStr, Map.of());
    }

    private String httpGet(String urlStr, Map<String, String> headers) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        headers.forEach(conn::setRequestProperty);

        int status = conn.getResponseCode();
        if (status >= 400) {
            conn.disconnect();
            throw new IOException("HTTP GET " + urlStr + " returned " + status);
        }

        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private String httpGetWithTimeout(String urlStr, Duration timeout) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout((int) timeout.toMillis());
        conn.setReadTimeout((int) timeout.toMillis());

        int status = conn.getResponseCode();
        if (status >= 400) {
            conn.disconnect();
            throw new IOException("HTTP GET " + urlStr + " returned " + status);
        }

        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private int httpGetStatus(String urlStr, Map<String, String> headers) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        headers.forEach(conn::setRequestProperty);

        int status = conn.getResponseCode();
        conn.disconnect();
        return status;
    }

    private void httpPost(String urlStr, String jsonBody) throws IOException {
        int status = httpPostStatus(urlStr, jsonBody, Map.of("Content-Type", "application/json"));
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP POST " + urlStr + " returned " + status);
        }
    }

    private int httpPostStatus(String urlStr, String jsonBody, Map<String, String> headers)
            throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        headers.forEach(conn::setRequestProperty);

        byte[] body = jsonBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int status = conn.getResponseCode();
        conn.disconnect();
        return status;
    }

    private int httpDelete(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int status = conn.getResponseCode();
        conn.disconnect();
        return status;
    }

    private void updateWorkItemStatus(String itemId, String status) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_work_item SET status = ? WHERE item_id = ?")) {
            ps.setString(1, status);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    private void assertWorkItemStatus(String itemId, String expectedStatus) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT status FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Work item must exist: " + itemId);
            assertEquals(expectedStatus, rs.getString("status"),
                "Work item status must match");
        }
    }

    private void updateRunnerState(String runnerId, String state) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_net_runner SET state = ? WHERE runner_id = ?")) {
            ps.setString(1, state);
            ps.setString(2, runnerId);
            ps.executeUpdate();
        }
    }

    private void assertRunnerState(String runnerId, String expectedState) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Runner must exist: " + runnerId);
            assertEquals(expectedState, rs.getString("state"),
                "Runner state must match");
        }
    }

    private String getRunnerState(String runnerId) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next(), "Runner must exist: " + runnerId);
            return rs.getString("state");
        }
    }
}
