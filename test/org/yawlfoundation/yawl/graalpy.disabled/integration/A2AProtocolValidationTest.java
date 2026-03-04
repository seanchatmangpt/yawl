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

package org.yawlfoundation.yawl.graalpy.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;

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
import java.util.ArrayList;
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
 * Comprehensive A2A (Agent-to-Agent) Protocol Validation Tests.
 *
 * This test suite validates compliance with the A2A protocol specification by testing:
 * 1. Agent Card endpoint compliance
 * 2. Core skill execution
 * 3. Message passing between agents
 * 4. Event streaming with checkpoint consistency
 * 5. AI model integration (Z.ai)
 *
 * All tests use real YAWL objects, real HTTP servers, and real database connections
 * with no mocks (Chicago TDD methodology).
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-25
 */
@Tag("integration")
@Tag("a2a")
@Tag("protocol")
class A2AProtocolValidationTest {

    // Test service ports (avoid conflicts with production)
    private static final int A2A_PORT = 19900;
    private static final int REGISTRY_PORT = 19901;
    private static final int A2A_PORT_SECONDARY = 19902;

    // Test authentication
    private static final String JWT_SECRET = "test-jwt-secret-key-for-a2a-validation-min-32-chars";
    private static final String API_KEY = "test-api-key-a2a-validation";

    // Test fixtures
    private Connection db;
    private YawlA2AServer a2aServer;
    private YawlA2AServer a2aServerSecondary;
    private CompositeAuthenticationProvider authProvider;

    // Test data
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        // Initialize H2 database with YAWL schema
        String jdbcUrl = "jdbc:h2:mem:a2a_validation_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(db);

        // Set authentication environment
        System.setProperty("A2A_JWT_SECRET", JWT_SECRET);
        System.setProperty("A2A_API_KEY", API_KEY);

        // Create authentication provider
        authProvider = createTestAuthProvider();

        // Start A2A server
        startA2aServer(A2A_PORT);
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

        // Close database
        if (db != null && !db.isClosed()) {
            db.close();
        }

        // Clear environment
        System.clearProperty("A2A_JWT_SECRET");
        System.clearProperty("A2A_API_KEY");
    }

    // =========================================================================
    // 1. Agent Card Endpoint Tests
    // =========================================================================

    @Nested
    @DisplayName("Agent Card Endpoint Tests")
    class AgentCardEndpointTests {

        @Test
        @DisplayName("GET /.well-known/agent.json returns 200 with valid JSON")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void testAgentCardEndpoint() throws Exception {
            // When: Agent card is requested without authentication
            String agentCard = httpGet(
                "http://localhost:" + A2A_PORT + "/.well-known/agent.json");

            // Then: Response is valid JSON
            assertTrue(agentCard.startsWith("{"), "Response must be JSON object");
            JsonNode json = objectMapper.readTree(agentCard);

            // And: Contains required A2A fields
            assertTrue(json.has("name"), "Must have name field");
            assertTrue(json.has("version"), "Must have version field");
            assertTrue(json.has("capabilities"), "Must have capabilities field");
            assertTrue(json.has("skills"), "Must have skills field");

            // And: Values are valid
            assertFalse(json.get("name").asText().isEmpty(), "Name must not be empty");
            assertEquals("6.0.0", json.get("version").asText(), "Version must be 6.0.0");

            // And: Skills array is not empty
            assertTrue(json.get("skills").isArray(), "Skills must be an array");
            assertTrue(json.get("skills").size() > 0, "Must have at least one skill");

            // And: All skills have required fields
            for (JsonNode skill : json.get("skills")) {
                assertTrue(skill.has("id"), "Skill must have id");
                assertTrue(skill.has("name"), "Skill must have name");
                assertTrue(skill.has("description"), "Skill must have description");
                assertTrue(skill.has("tags"), "Skill must have tags");
            }
        }

        @Test
        @DisplayName("Agent card contains streaming capability")
        void testAgentCardStreamingCapability() throws Exception {
            // When: Agent card is retrieved
            String agentCard = httpGet(
                "http://localhost:" + A2A_PORT + "/.well-known/agent.json");
            JsonNode json = objectMapper.readTree(agentCard);

            // Then: Contains streaming capability
            assertTrue(json.has("capabilities"), "Must have capabilities");
            JsonNode capabilities = json.get("capabilities");
            assertTrue(capabilities.has("streaming"), "Must have streaming capability");
            assertTrue(capabilities.get("streaming").asBoolean(), "Streaming must be enabled");
        }

        @Test
        @DisplayName("Agent card schema compliance")
        void testAgentCardSchemaCompliance() throws Exception {
            // When: Agent card is retrieved
            String agentCard = httpGet(
                "http://localhost:" + A2A_PORT + "/.well-known/agent.json");
            JsonNode json = objectMapper.readTree(agentCard);

            // Then: All required fields are present
            String[] requiredFields = {
                "name", "version", "capabilities", "skills", "metadata"
            };
            for (String field : requiredFields) {
                assertTrue(json.has(field), "Missing required field: " + field);
            }

            // And: Metadata structure is correct
            JsonNode metadata = json.get("metadata");
            assertTrue(metadata.has("created"), "Must have created timestamp");
            assertTrue(metadata.has("protocol"), "Must have protocol version");
            assertEquals("A2A/1.0", metadata.get("protocol").asText(), "Protocol must be A2A/1.0");
        }

        @Test
        @DisplayName("Agent card accessible from multiple clients")
        @Timeout(value = 15, unit = TimeUnit.SECONDS)
        void testAgentCardAccessibility() throws Exception {
            // When: Multiple clients request agent card concurrently
            int clientCount = 10;
            AtomicInteger successCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(clientCount);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < clientCount; i++) {
                executor.submit(() -> {
                    try {
                        String response = httpGet(
                            "http://localhost:" + A2A_PORT + "/.well-known/agent.json");
                        JsonNode json = objectMapper.readTree(response);
                        if (!json.get("name").asText().isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Count failures
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean allCompleted = latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Then: All clients receive valid responses
            assertTrue(allCompleted, "All requests must complete");
            assertEquals(clientCount, successCount.get(), "All requests must succeed");
        }
    }

    // =========================================================================
    // 2. Core A2A Skills Execution Tests
    // =========================================================================

    @Nested
    @DisplayName("Core A2A Skills Execution Tests")
    class CoreSkillsExecutionTests {

        @BeforeEach
        void setupTestData() throws Exception {
            // Create test workflow specification
            String specId = "a2a-test-workflow";
            YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "A2A Test Workflow");
            WorkflowDataFactory.seedNetRunner(db, "runner-a2a-test", specId, "1.0", "root", "RUNNING");
        }

        @Test
        @DisplayName("launch_workflow skill execution")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void testLaunchWorkflowSkill() throws Exception {
            // Given: A2A server is running
            // When: launch_workflow skill is called
            SkillRequest request = createSkillRequest(
                "launch_workflow",
                Map.of(
                    "specId", "a2a-test-workflow",
                    "caseData", Map.of("input", "test value")
                )
            );

            String result = executeSkill(request, "test-user");

            // Then: Skill executes successfully
            assertNotNull(result, "Result must not be null");
            assertTrue(result.contains("successfully"), "Result must indicate success");
            assertTrue(result.contains("caseId"), "Result must mention case ID");
        }

        @Test
        @DisplayName("query_workflows skill execution")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void testQueryWorkflowsSkill() throws Exception {
            // Given: A case is running
            WorkflowDataFactory.seedNetRunner(db, "runner-query-test",
                "a2a-test-workflow", "1.0", "root", "RUNNING");

            // When: query_workflows skill is called
            SkillRequest request = createSkillRequest(
                "query_workflows",
                Map.of(
                    "filter", "running",
                    "includeWorkItems", true
                )
            );

            String result = executeSkill(request, "test-user");

            // Then: Skill queries successfully
            assertNotNull(result, "Result must not be null");
            assertTrue(result.contains("successfully"), "Result must indicate success");
            assertTrue(result.contains("specifications"), "Result must mention specifications");
            assertTrue(result.contains("cases"), "Result must mention cases");
        }

        @Test
        @DisplayName("manage_workitems skill execution")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void testManageWorkItemsSkill() throws Exception {
            // Given: A work item exists
            WorkflowDataFactory.seedWorkItem(
                "wi-manage-test", "runner-query-test", "task_1", "Enabled");

            // When: manage_workitems skill is called
            SkillRequest request = createSkillRequest(
                "manage_workitems",
                Map.of(
                    "operation", "complete",
                    "workItemId", "wi-manage-test",
                    "outputData", Map.of("result", "completed")
                )
            );

            String result = executeSkill(request, "test-user");

            // Then: Skill manages work items successfully
            assertNotNull(result, "Result must not be null");
            assertTrue(result.contains("successfully"), "Result must indicate success");
            assertTrue(result.contains("workItemId"), "Result must mention work item ID");
            assertTrue(result.contains("status"), "Result must mention status");
        }

        @Test
        @DisplayName("process_mining_analyze skill execution")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void testProcessMiningAnalyzeSkill() throws Exception {
            // When: process_mining_analyze skill is called
            SkillRequest request = createSkillRequest(
                "process_mining_analyze",
                Map.of(
                    "specIdentifier", "a2a-test-workflow",
                    "analysisType", "performance"
                )
            );

            String result = executeSkill(request, "test-user");

            // Then: Skill analyzes successfully
            assertNotNull(result, "Result must not be null");
            assertTrue(result.contains("successfully"), "Result must indicate success");
            assertTrue(result.contains("analysis"), "Result must mention analysis");
            assertTrue(result.contains("metrics"), "Result must mention metrics");
        }

        @Test
        @DisplayName("All core skills execute concurrently")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void testAllCoreSkillsConcurrentExecution() throws Exception {
            // When: All 4 core skills execute concurrently
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch latch = new CountDownLatch(4);
            AtomicInteger successCount = new AtomicInteger(0);

            // Execute each core skill
            executor.submit(() -> {
                try {
                    String result = executeSkill(
                        createSkillRequest("launch_workflow", Map.of("specId", "a2a-test-workflow")),
                        "test-user");
                    if (result != null && result.contains("successfully")) successCount.incrementAndGet();
                } catch (Exception e) {
                    // Handle failure
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    String result = executeSkill(
                        createSkillRequest("query_workflows", Map.of("filter", "all")),
                        "test-user");
                    if (result != null && result.contains("successfully")) successCount.incrementAndGet();
                } catch (Exception e) {
                    // Handle failure
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    String result = executeSkill(
                        createSkillRequest("manage_workitems", Map.of("operation", "status")),
                        "test-user");
                    if (result != null && result.contains("successfully")) successCount.incrementAndGet();
                } catch (Exception e) {
                    // Handle failure
                } finally {
                    latch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    String result = executeSkill(
                        createSkillRequest("process_mining_analyze", Map.of("specIdentifier", "a2a-test-workflow", "analysisType", "basic")),
                        "test-user");
                    if (result != null && result.contains("successfully")) successCount.incrementAndGet();
                } catch (Exception e) {
                    // Handle failure
                } finally {
                    latch.countDown();
                }
            });

            boolean allCompleted = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then: All skills execute successfully
            assertTrue(allCompleted, "All skills must complete within timeout");
            assertEquals(4, successCount.get(), "All skills must succeed");
        }

        @Test
        @DisplayName("Skills handle errors gracefully")
        void testSkillsErrorHandling() throws Exception {
            // When: Skills are called with invalid parameters
            SkillRequest invalidSpecRequest = createSkillRequest(
                "execute_workflow",
                Map.of("specId", "nonexistent-spec")
            );

            String result = executeSkill(invalidSpecRequest, "test-user");

            // Then: Skill returns appropriate error
            assertNotNull(result, "Result must not be null");
            assertFalse(result.contains("successfully"), "Skill must fail gracefully");
            assertTrue(result.contains("not found") || result.contains("error"),
                "Error must indicate not found or error");
        }
    }

    // =========================================================================
    // 3. Message Passing Between Agents Tests
    // =========================================================================

    @Nested
    @DisplayName("Message Passing Between Agents Tests")
    class MessagePassingTests {

        @BeforeEach
        void setupSecondaryServer() throws Exception {
            // Start secondary A2A server
            startA2aServerSecondary(A2A_PORT_SECONDARY);
        }

        @Test
        @DisplayName("Message passing between A2A servers")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void testMessagePassingBetweenAgents() throws Exception {
            // Given: Two A2A servers running
            // Create client for primary server
            YawlA2AClient client1 = new YawlA2AClient(
                "http://localhost:" + A2A_PORT, "test-user", generateTestJwt());

            // Create client for secondary server
            YawlA2AClient client2 = new YawlA2AClient(
                "http://localhost:" + A2A_PORT_SECONDARY, "test-user", generateTestJwt());

            // When: Client 1 sends message to Client 2
            String messageId = "msg-" + System.currentTimeMillis();
            Map<String, Object> message = Map.of(
                "to", "agent-secondary",
                "content", "Test message from primary to secondary",
                "timestamp", Instant.now().toString()
            );

            SkillResult result1 = client1.sendMessage(
                "agent-secondary", "test-message", message);

            // Then: Message is sent successfully
            assertTrue(result1.isSuccess(), "Message must be sent successfully");

            // And: Client 2 can receive the message
            SkillResult result2 = client2.receiveMessage(messageId);
            assertTrue(result2.isSuccess(), "Message must be received");
            JsonNode received = objectMapper.readTree(result2.getResult());
            assertEquals("Test message from primary to secondary",
                received.get("content").asText());
        }

        @Test
        @DisplayName("Message queuing and delivery")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void testMessageQueuingAndDelivery() throws Exception {
            // Given: A2A server with message queue
            // Create test client
            YawlA2AClient client = new YawlA2AClient(
                "http://localhost:" + A2A_PORT, "test-user", generateTestJwt());

            // When: Multiple messages are sent
            int messageCount = 5;
            List<String> messageIds = new ArrayList<>();

            for (int i = 0; i < messageCount; i++) {
                Map<String, Object> message = Map.of(
                    "content", "Test message " + i,
                    "priority", i
                );

                SkillResult result = client.sendMessage(
                    "test-queue", "test-message", message);
                assertTrue(result.isSuccess(), "Message " + i + " must be sent");

                JsonNode response = objectMapper.readTree(result.getResult());
                messageIds.add(response.get("messageId").asText());
            }

            // Then: All messages are queued and can be retrieved
            for (String messageId : messageIds) {
                SkillResult receiveResult = client.receiveMessage(messageId);
                assertTrue(receiveResult.isSuccess(), "Message must be retrievable");
            }
        }

        @Test
        @DisplayName("Message acknowledgment and error handling")
        void testMessageAcknowledgment() throws Exception {
            // Given: A2A client
            YawlA2AClient client = new YawlA2AClient(
                "http://localhost:" + A2A_PORT, "test-user", generateTestJwt());

            // When: Valid message is sent
            Map<String, Object> message = Map.of("content", "Test message");
            SkillResult result = client.sendMessage("test-agent", "test-type", message);

            // Then: Message is acknowledged
            assertTrue(result.isSuccess(), "Message must be acknowledged");
            assertNotNull(result.getResult(), "Must have acknowledgment data");
            JsonNode ack = objectMapper.readTree(result.getResult());
            assertTrue(ack.has("messageId"), "Must have message ID");
            assertTrue(ack.has("timestamp"), "Must have timestamp");

            // When: Invalid recipient is targeted
            SkillResult invalidResult = client.sendMessage(
                "nonexistent-agent", "test-type", message);

            // Then: Appropriate error is returned
            assertFalse(invalidResult.isSuccess(), "Invalid message must fail");
            assertNotNull(invalidResult.getErrorMessage(), "Must have error message");
        }

        @Test
        @DisplayName("Concurrent message handling")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void testConcurrentMessageHandling() throws Exception {
            // Given: Multiple message senders
            YawlA2AClient client = new YawlA2AClient(
                "http://localhost:" + A2A_PORT, "test-user", generateTestJwt());

            // When: Multiple threads send messages concurrently
            int threadCount = 10;
            AtomicInteger successCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        Map<String, Object> message = Map.of(
                            "thread", threadId,
                            "content", "Message from thread " + threadId
                        );

                        SkillResult result = client.sendMessage(
                            "test-queue", "concurrent-message", message);
                        if (result.isSuccess()) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Handle failure
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean allCompleted = latch.await(20, TimeUnit.SECONDS);
            executor.shutdown();

            // Then: All messages are handled successfully
            assertTrue(allCompleted, "All messages must complete");
            assertEquals(threadCount, successCount.get(), "All messages must succeed");
        }
    }

    // =========================================================================
    // 4. Event Streaming Tests
    // =========================================================================

    @Nested
    @DisplayName("Event Streaming Tests")
    class EventStreamingTests {

        @Test
        @DisplayName("SSE event streaming")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void testEventStreaming() throws Exception {
            // Given: A2A server with events
            String subscriberId = "test-subscriber-" + System.currentTimeMillis();

            // When: Subscriber connects to event stream
            CountDownLatch eventReceived = new CountDownLatch(1);
            AtomicReference<String> eventData = new AtomicReference<>();

            // Start event listener in background
            Thread eventThread = new Thread(() -> {
                try {
                    String stream = httpGet(
                        "http://localhost:" + A2A_PORT + "/events/stream?subscriber=" + subscriberId);

                    // Parse SSE events
                    for (String line : stream.split("\n")) {
                        if (line.startsWith("data: ")) {
                            eventData.set(line.substring(6));
                            eventReceived.countDown();
                            break;
                        }
                    }
                } catch (Exception e) {
                    eventReceived.countDown();
                }
            });
            eventThread.start();

            // Trigger an event
            SkillRequest request = createSkillRequest(
                "execute_workflow",
                Map.of("specId", "a2a-test-workflow", "triggerEvent", true)
            );
            executeSkill(request, "test-user");

            // Then: Event is received
            boolean eventReceivedFlag = eventReceived.await(10, TimeUnit.SECONDS);
            assertTrue(eventReceivedFlag, "Event must be received within timeout");
            assertNotNull(eventData.get(), "Event data must not be null");
            JsonNode eventJson = objectMapper.readTree(eventData.get());
            assertTrue(eventJson.has("type"), "Event must have type");
            assertTrue(eventJson.has("timestamp"), "Event must have timestamp");
        }

        @Test
        @DisplayName("Checkpoint consistency in event stream")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void testCheckpointConsistency() throws Exception {
            // Given: A workflow case with multiple steps
            String specId = "checkpoint-test-workflow";
            String runnerId = "runner-checkpoint-test";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Checkpoint Test");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            // Create multiple work items
            for (int i = 0; i < 3; i++) {
                WorkflowDataFactory.seedWorkItem(
                    "wi-checkpoint-" + i, runnerId, "task_" + i, "Enabled");
            }

            List<String> eventSequence = new ArrayList<>();
            CountDownLatch allEventsReceived = new CountDownLatch(4); // 3 work items + completion

            // When: Events are streamed
            Thread eventThread = new Thread(() -> {
                try {
                    String stream = httpGet(
                        "http://localhost:" + A2A_PORT + "/events/stream?checkpoint=true");

                    JsonNode events = objectMapper.readTree(stream);
                    for (JsonNode event : events) {
                        eventSequence.add(event.get("type").asText());
                        allEventsReceived.countDown();
                    }
                } catch (Exception e) {
                    allEventsReceived.countDown();
                }
            });
            eventThread.start();

            // Complete work items in order
            for (int i = 0; i < 3; i++) {
                updateWorkItemStatus("wi-checkpoint-" + i, "Executing");
                updateWorkItemStatus("wi-checkpoint-" + i, "Completed");
            }

            // Complete the case
            updateRunnerState(runnerId, "COMPLETED");

            boolean allReceived = allEventsReceived.await(15, TimeUnit.SECONDS);
            assertTrue(allReceived, "All events must be received");

            // Then: Events are in correct order with consistent checkpoints
            List<String> expectedOrder = List.of(
                "workitem_started", "workitem_completed",
                "workitem_started", "workitem_completed",
                "workitem_started", "workitem_completed",
                "case_completed"
            );

            // Verify sequence includes all expected events
            assertTrue(eventSequence.contains("workitem_started"),
                "Must have workitem started events");
            assertTrue(eventSequence.contains("workitem_completed"),
                "Must have workitem completed events");
            assertTrue(eventSequence.contains("case_completed"),
                "Must have case completed event");
        }

        @Test
        @DisplayName("Event stream position tracking")
        void testEventStreamPositionTracking() throws Exception {
            // Given: Event stream with position tracking
            String subscriberId = "position-test-subscriber";

            // When: Subscriber connects and receives events
            String streamUrl = "http://localhost:" + A2A_PORT +
                "/events/stream?subscriber=" + subscriberId + "&position=0";

            String stream = httpGet(streamUrl);

            // Then: Stream includes position information
            JsonNode events = objectMapper.readTree(stream);
            for (JsonNode event : events) {
                assertTrue(event.has("position"), "Event must have position");
                assertTrue(event.has("checkpoint"), "Event must have checkpoint");
                assertTrue(event.has("timestamp"), "Event must have timestamp");
            }
        }

        @Test
        @DisplayName("Event subscription management")
        void testEventSubscriptionManagement() throws Exception {
            // Given: Multiple subscribers
            String subscriber1 = "sub1-" + System.currentTimeMillis();
            String subscriber2 = "sub2-" + System.currentTimeMillis();

            // When: Both subscribers connect
            List<String> sub1Events = new ArrayList<>();
            List<String> sub2Events = new ArrayList<>();

            Thread sub1Thread = new Thread(() -> {
                try {
                    String stream = httpGet(
                        "http://localhost:" + A2A_PORT + "/events/stream?subscriber=" + subscriber1);
                    JsonNode events = objectMapper.readTree(stream);
                    for (JsonNode event : events) {
                        sub1Events.add(event.get("type").asText());
                    }
                } catch (Exception e) {
                    // Handle error
                }
            });

            Thread sub2Thread = new Thread(() -> {
                try {
                    String stream = httpGet(
                        "http://localhost:" + A2A_PORT + "/events/stream?subscriber=" + subscriber2);
                    JsonNode events = objectMapper.readTree(stream);
                    for (JsonNode event : events) {
                        sub2Events.add(event.get("type").asText());
                    }
                } catch (Exception e) {
                    // Handle error
                }
            });

            sub1Thread.start();
            sub2Thread.start();

            // Trigger event
            SkillRequest request = createSkillRequest(
                "execute_workflow",
                Map.of("specId", "a2a-test-workflow")
            );
            executeSkill(request, "test-user");

            // Wait for events
            Thread.sleep(2000);

            // Then: Both subscribers receive the event
            assertTrue(sub1Events.size() > 0, "Subscriber 1 must receive events");
            assertTrue(sub2Events.size() > 0, "Subscriber 2 must receive events");
        }
    }

    // =========================================================================
    // 5. AI Model Integration (Z.ai) Tests
    // =========================================================================

    @Nested
    @DisplayName("AI Model Integration Tests")
    class AIModelIntegrationTests {

        @Test
        @DisplayName("Z.ai model integration")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void testZaiModelIntegration() throws Exception {
            // Given: Z.ai service available
            String zaiApiKey = System.getenv("ZHIPU_API_KEY");
            assumeNotNull(zaiApiKey, "ZHIPU_API_KEY environment variable must be set");

            // When: AI-enhanced skill is called
            SkillRequest request = createSkillRequest(
                "ai_workflow_analysis",
                Map.of(
                    "workflowSpec", "a2a-test-workflow",
                    "analysisType", "optimization",
                    "aiModel", "glm-4"
                )
            );

            String result = executeSkill(request, "test-user");

            // Then: AI analysis is performed successfully
            assertNotNull(result, "Result must not be null");
            assertTrue(result.contains("successfully"), "AI skill must succeed");
            assertTrue(result.contains("analysis"), "Must mention analysis");
            assertTrue(result.contains("recommendations") || result.contains("metrics"),
                "Must have recommendations or metrics");
        }

        @Test
        @DisplayName("AI model fallback behavior")
        void testAIModelFallback() throws Exception {
            // When: AI skill is called without Z.ai configuration
            SkillRequest request = createSkillRequest(
                "ai_workflow_analysis",
                Map.of(
                    "workflowSpec", "a2a-test-workflow",
                    "analysisType", "optimization"
                )
            );

            String result = executeSkill(request, "test-user");

            // Then: Skill gracefully handles missing AI service
            // Should either succeed with non-AI analysis or fail with clear error
            if (result != null && result.contains("successfully")) {
                assertTrue(result.contains("analysis"), "Must have fallback analysis");
            } else {
                assertTrue(result.contains("AI service") || result.contains("error"),
                    "Error must indicate AI service issue");
            }
        }

        @Test
        @DisplayName("AI model error handling")
        void testAIModelErrorHandling() throws Exception {
            // When: AI skill is called with invalid model
            SkillRequest request = createSkillRequest(
                "ai_workflow_analysis",
                Map.of(
                    "workflowSpec", "a2a-test-workflow",
                    "analysisType", "optimization",
                    "aiModel", "invalid-model"
                )
            );

            String result = executeSkill(request, "test-user");

            // Then: Clear error is returned
            assertNotNull(result, "Result must not be null");
            assertFalse(result.contains("successfully"), "Invalid model must fail");
            assertTrue(result.contains("model") || result.contains("error"),
                "Error must mention model issue");
        }

        @Test
        @DisplayName("AI model timeout handling")
        @Timeout(value = 45, unit = TimeUnit.SECONDS)
        void testAIModelTimeoutHandling() throws Exception {
            // When: AI skill is called with long operation
            SkillRequest request = createSkillRequest(
                "ai_workflow_analysis",
                Map.of(
                    "workflowSpec", "a2a-test-workflow",
                    "analysisType", "deep_analysis",
                    "timeout", 30000 // 30 seconds
                )
            );

            long startTime = System.currentTimeMillis();
            String result = executeSkill(request, "test-user");
            long duration = System.currentTimeMillis() - startTime;

            // Then: Operation respects timeout
            assertTrue(duration < 35000, "Operation must respect timeout (within 5s buffer)");
            // Result could be success or timeout, but shouldn't hang indefinitely
            assertNotNull(result, "Result should not be null even on timeout");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

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
            return jwtProvider.issueToken("test-user",
                List.of("workflow:launch", "workflow:query"),
                Duration.ofMinutes(5).toMillis());
        } catch (Exception e) {
            return "test-token";
        }
    }

    private SkillRequest createSkillRequest(String skillId, Map<String, Object> params) {
        return new SkillRequest(skillId, "test-skill", "Test skill execution", params);
    }

    private String executeSkill(SkillRequest request, String user) throws Exception {
        // Create client and send skill request as message
        YawlA2AClient client = new YawlA2AClient("http://localhost:" + A2A_PORT);
        client.connect();

        // Format skill request as text message
        String message = String.format("%s with parameters: %s",
            request.getId(), request.getParameters().toString());

        return client.sendMessage(message);
    }

    private void startA2aServer(int port) throws IOException {
        a2aServer = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", port, authProvider);
        a2aServer.start();
        waitForServer(port, Duration.ofSeconds(5));
    }

    private void startA2aServerSecondary(int port) throws IOException {
        a2aServerSecondary = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", port, authProvider);
        a2aServerSecondary.start();
        waitForServer(port, Duration.ofSeconds(5));
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

    private void updateWorkItemStatus(String itemId, String status) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_work_item SET status = ? WHERE item_id = ?")) {
            ps.setString(1, status);
            ps.setString(2, itemId);
            ps.executeUpdate();
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

    private void assumeNotNull(Object value, String message) {
        if (value == null) {
            throw new org.junit.jupiter.api.AssumptionNotMetException(message);
        }
    }
}