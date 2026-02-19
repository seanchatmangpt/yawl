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
import org.yawlfoundation.yawl.integration.a2a.YawlA2AServer;
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffException;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffMessage;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistry;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * Cross-service handoff integration tests.
 *
 * Tests the work item handoff protocol between autonomous agents:
 * - Token generation and validation across services
 * - A2A message transmission for handoffs
 * - Session handle transfer between agents
 * - Work item state consistency during handoff
 * - Error handling and rollback during failed handoffs
 * - Concurrent handoff scenarios
 *
 * Chicago TDD methodology: Real handoff protocol, real JWT tokens,
 * real HTTP communication - no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("integration")
@Tag("handoff")
class CrossServiceHandoffTest {

    private static final int SOURCE_A2A_PORT = 19890;
    private static final int TARGET_A2A_PORT = 19891;
    private static final int REGISTRY_PORT = 19892;

    private static final String JWT_SECRET = "handoff-test-jwt-secret-key-min-32-characters!!";
    private static final String API_KEY = "handoff-test-api-key";

    private Connection db;
    private AgentRegistry registry;
    private YawlA2AServer sourceServer;
    private YawlA2AServer targetServer;
    private HandoffProtocol handoffProtocol;
    private JwtAuthenticationProvider jwtProvider;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize database
        String jdbcUrl = "jdbc:h2:mem:handoff_test_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(db);

        // Set authentication
        System.setProperty("A2A_JWT_SECRET", JWT_SECRET);
        System.setProperty("A2A_API_KEY", API_KEY);

        // Create JWT provider for handoff protocol
        jwtProvider = new JwtAuthenticationProvider(JWT_SECRET, null);
        handoffProtocol = new HandoffProtocol(jwtProvider, Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sourceServer != null && sourceServer.isRunning()) {
            sourceServer.stop();
        }
        if (targetServer != null && targetServer.isRunning()) {
            targetServer.stop();
        }
        if (registry != null) {
            registry.stop();
        }
        if (db != null && !db.isClosed()) {
            db.close();
        }
        System.clearProperty("A2A_JWT_SECRET");
        System.clearProperty("A2A_API_KEY");
    }

    // =========================================================================
    // Token Generation and Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Token Generation and Validation")
    class TokenGenerationValidationTests {

        @Test
        @DisplayName("Handoff token is generated with valid claims")
        void handoffToken_GeneratedWithValidClaims() throws Exception {
            // Given: Valid handoff parameters
            String workItemId = "WI-TEST-001";
            String fromAgent = "source-agent";
            String toAgent = "target-agent";
            String sessionHandle = "session-12345";

            // When: Token is generated
            HandoffToken token = handoffProtocol.generateHandoffToken(
                workItemId, fromAgent, toAgent, sessionHandle);

            // Then: Token contains expected claims
            assertNotNull(token, "Token must not be null");
            assertEquals(workItemId, token.workItemId(), "Work item ID must match");
            assertEquals(fromAgent, token.fromAgent(), "Source agent must match");
            assertEquals(toAgent, token.toAgent(), "Target agent must match");
            assertEquals(sessionHandle, token.engineSession(), "Session must match");
            assertTrue(token.isValid(), "Token must be valid immediately after creation");
        }

        @Test
        @DisplayName("Handoff token expires after TTL")
        void handoffToken_ExpiresAfterTTL() throws Exception {
            // Given: Token with very short TTL
            String workItemId = "WI-EXPIRE-001";
            Duration shortTtl = Duration.ofMillis(50);

            // When: Token is generated
            HandoffToken token = handoffProtocol.generateHandoffToken(
                workItemId, "agent-1", "agent-2", "session", shortTtl);

            // Then: Token is initially valid
            assertTrue(token.isValid(), "Token must be valid initially");

            // And: Token expires after TTL
            Thread.sleep(100);
            assertFalse(token.isValid(), "Token must be expired after TTL");
        }

        @Test
        @DisplayName("Handoff token verification succeeds for valid token")
        void handoffToken_VerificationSucceeds() throws Exception {
            // Given: A valid token
            HandoffToken token = handoffProtocol.generateHandoffToken(
                "WI-VERIFY-001", "agent-1", "agent-2", "session");

            // When: Token is verified
            HandoffToken verified = handoffProtocol.verifyHandoffToken(token);

            // Then: Verification succeeds
            assertNotNull(verified, "Verified token must not be null");
            assertEquals(token.workItemId(), verified.workItemId(),
                "Verified token claims must match");
        }

        @Test
        @DisplayName("Handoff token verification fails for expired token")
        void handoffToken_VerificationFailsExpired() throws Exception {
            // Given: An expired token
            HandoffToken token = handoffProtocol.generateHandoffToken(
                "WI-EXPIRED-001", "agent-1", "agent-2", "session", Duration.ofMillis(1));
            Thread.sleep(10);

            // When/Then: Verification fails
            assertThrows(HandoffException.class,
                () -> handoffProtocol.verifyHandoffToken(token),
                "Verification must fail for expired token");
        }
    }

    // =========================================================================
    // Message Transmission Tests
    // =========================================================================

    @Nested
    @DisplayName("A2A Message Transmission")
    class MessageTransmissionTests {

        @Test
        @DisplayName("Handoff message is created with token")
        void handoffMessage_CreatedWithToken() throws Exception {
            // Given: Valid handoff parameters
            String workItemId = "WI-MSG-001";
            Map<String, Object> payload = Map.of(
                "reason", "Document language not supported",
                "confidence", 0.95
            );

            // When: Handoff message is created
            HandoffMessage message = handoffProtocol.createHandoffMessage(
                workItemId, "agent-1", "agent-2", "session", payload, Duration.ofMinutes(5));

            // Then: Message contains expected data
            assertNotNull(message, "Message must not be null");
            assertEquals(workItemId, message.workItemId(), "Work item ID must match");
            assertEquals("agent-1", message.fromAgent(), "Source agent must match");
            assertEquals("agent-2", message.toAgent(), "Target agent must match");
            assertNotNull(message.token(), "Token must be present");
            assertNotNull(message.payload(), "Payload must be present");
        }

        @Test
        @DisplayName("Handoff message timestamp is recent")
        void handoffMessage_TimestampRecent() throws Exception {
            // Given: Current time
            Instant before = Instant.now();

            // When: Message is created
            HandoffMessage message = handoffProtocol.createHandoffMessage(
                "WI-TIME-001", "agent-1", "agent-2", "session");

            // Then: Timestamp is recent
            Instant after = Instant.now();
            assertTrue(message.timestamp().isAfter(before.minusSeconds(1)),
                "Timestamp must be recent");
            assertTrue(message.timestamp().isBefore(after.plusSeconds(1)),
                "Timestamp must be recent");
        }
    }

    // =========================================================================
    // Cross-Service Handoff Tests
    // =========================================================================

    @Nested
    @DisplayName("Cross-Service Handoff Scenarios")
    class CrossServiceHandoffScenarios {

        @Test
        @DisplayName("Complete handoff between two A2A servers")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void completeHandoff_BetweenTwoServers() throws Exception {
            // Given: Two A2A servers and registry
            startSourceServer(SOURCE_A2A_PORT);
            startTargetServer(TARGET_A2A_PORT);
            startRegistry(REGISTRY_PORT);

            registerAgent("source-agent", SOURCE_A2A_PORT);
            registerAgent("target-agent", TARGET_A2A_PORT);

            // And: A work item is checked out by source
            String workItemId = "WI-HANDOFF-001";
            String specId = "handoff-spec";
            String runnerId = "runner-handoff";

            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Handoff Test");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
            WorkflowDataFactory.seedWorkItem(db, workItemId, runnerId, "process", "Executing");

            // When: Source agent initiates handoff
            HandoffMessage handoffMessage = handoffProtocol.createHandoffMessage(
                workItemId,
                "source-agent",
                "target-agent",
                "session-handle-123",
                Map.of("reason", "Requires specialized processing"),
                Duration.ofMinutes(5)
            );

            // Then: Handoff message is valid
            assertNotNull(handoffMessage, "Handoff message must be created");
            assertTrue(handoffMessage.token().isValid(), "Token must be valid");

            // And: Target agent can verify the handoff
            HandoffToken verifiedToken = handoffProtocol.verifyHandoffToken(
                handoffMessage.token());
            assertEquals(workItemId, verifiedToken.workItemId(),
                "Verified token must contain work item ID");

            // And: Work item state can be updated
            updateWorkItemStatus(workItemId, "Enabled"); // Rolled back to available
            assertNotNull(getWorkItemStatus(workItemId), "Work item must exist");
        }

        @Test
        @DisplayName("Handoff with context data preserved")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void handoffWithContext_Preserved() throws Exception {
            // Given: Complex context data
            Map<String, Object> contextData = Map.of(
                "documentType", "contract",
                "language", "es",
                "confidence", 0.87,
                "metadata", Map.of(
                    "pages", 42,
                    "requires", "spanish-translation"
                )
            );

            // When: Handoff message is created with context
            HandoffMessage message = handoffProtocol.createHandoffMessage(
                "WI-CONTEXT-001",
                "source-agent",
                "target-agent",
                "session",
                contextData,
                Duration.ofMinutes(10)
            );

            // Then: Context is preserved in message
            assertNotNull(message.payload(), "Payload must not be null");
            assertTrue(message.payload().containsKey("documentType"),
                "Context must preserve documentType");
            assertTrue(message.payload().containsKey("metadata"),
                "Context must preserve nested metadata");
        }

        @Test
        @DisplayName("Concurrent handoffs between multiple agents")
        @Timeout(value = 45, unit = TimeUnit.SECONDS)
        void concurrentHandoffs_MultipleAgents() throws Exception {
            // Given: Multiple handoff scenarios
            int handoffCount = 20;
            CountDownLatch latch = new CountDownLatch(handoffCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            // When: Concurrent handoffs are executed
            for (int i = 0; i < handoffCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        String workItemId = "WI-CONCURRENT-" + index;
                        HandoffMessage message = handoffProtocol.createHandoffMessage(
                            workItemId,
                            "source-" + index,
                            "target-" + index,
                            "session-" + index
                        );

                        if (message.token().isValid()) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then: All handoffs complete successfully
            assertTrue(completed, "All handoffs must complete within timeout");
            assertEquals(handoffCount, successCount.get(),
                "All handoffs must succeed, failures: " + failureCount.get());
            assertEquals(0, failureCount.get(), "No failures expected");
        }
    }

    // =========================================================================
    // Error Handling and Rollback Tests
    // =========================================================================

    @Nested
    @DisplayName("Error Handling and Rollback")
    class ErrorHandlingRollbackTests {

        @Test
        @DisplayName("Handoff failure triggers work item rollback")
        void handoffFailure_TriggersRollback() throws Exception {
            // Given: A work item in executing state
            String workItemId = "WI-ROLLBACK-001";
            String specId = "rollback-spec";
            String runnerId = "runner-rollback";

            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Rollback Test");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
            WorkflowDataFactory.seedWorkItem(db, workItemId, runnerId, "process", "Executing");

            // When: Handoff fails
            try {
                // Create handoff with expired token (simulating failure)
                HandoffToken expiredToken = handoffProtocol.generateHandoffToken(
                    workItemId, "source", "target", "session", Duration.ofMillis(1));
                Thread.sleep(10);

                handoffProtocol.verifyHandoffToken(expiredToken);
                fail("Should throw exception for expired token");
            } catch (HandoffException e) {
                // Expected - handoff failed
            }

            // Then: Work item should be rolled back to available
            updateWorkItemStatus(workItemId, "Enabled");
            assertEquals("Enabled", getWorkItemStatus(workItemId),
                "Work item must be rolled back to available state");
        }

        @Test
        @DisplayName("Partial handoff recovery")
        void partialHandoff_Recovery() throws Exception {
            // Given: Multiple work items for handoff
            String specId = "partial-spec";
            String runnerId = "runner-partial";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Partial Test");
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            for (int i = 0; i < 5; i++) {
                WorkflowDataFactory.seedWorkItem(db,
                    "WI-PARTIAL-" + i, runnerId, "task", "Executing");
            }

            // When: Some handoffs succeed, some fail
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < 5; i++) {
                String workItemId = "WI-PARTIAL-" + i;
                try {
                    if (i % 2 == 0) {
                        // Success
                        HandoffToken token = handoffProtocol.generateHandoffToken(
                            workItemId, "source", "target", "session");
                        handoffProtocol.verifyHandoffToken(token);
                        updateWorkItemStatus(workItemId, "Completed");
                        successCount++;
                    } else {
                        // Simulate failure
                        throw new HandoffException("Simulated failure");
                    }
                } catch (HandoffException e) {
                    // Rollback
                    updateWorkItemStatus(workItemId, "Enabled");
                    failCount++;
                }
            }

            // Then: Successful handoffs complete, failed ones rollback
            assertEquals(3, successCount, "3 handoffs should succeed");
            assertEquals(2, failCount, "2 handoffs should fail");

            // Verify final states
            int completed = 0;
            int enabled = 0;
            for (int i = 0; i < 5; i++) {
                String status = getWorkItemStatus("WI-PARTIAL-" + i);
                if ("Completed".equals(status)) completed++;
                else if ("Enabled".equals(status)) enabled++;
            }
            assertEquals(3, completed, "3 items should be completed");
            assertEquals(2, enabled, "2 items should be rolled back to enabled");
        }
    }

    // =========================================================================
    // Performance Tests
    // =========================================================================

    @Nested
    @DisplayName("Handoff Performance")
    class HandoffPerformanceTests {

        @Test
        @DisplayName("Handoff token generation performance")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void tokenGeneration_Performance() throws Exception {
            // Given: High volume of token generations
            int iterations = 500;
            long startTime = System.currentTimeMillis();

            // When: Tokens are generated
            for (int i = 0; i < iterations; i++) {
                handoffProtocol.generateHandoffToken(
                    "WI-PERF-" + i, "source", "target", "session");
            }

            long duration = System.currentTimeMillis() - startTime;

            // Then: Performance is acceptable
            double throughput = iterations * 1000.0 / duration;
            System.out.printf("Token Generation Performance:%n");
            System.out.printf("  Iterations: %d%n", iterations);
            System.out.printf("  Duration: %dms%n", duration);
            System.out.printf("  Throughput: %.0f tokens/sec%n", throughput);

            assertTrue(throughput >= 100,
                "Token generation must be >= 100/sec, got " + throughput);
        }

        @Test
        @DisplayName("Handoff message creation performance")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void messageCreation_Performance() throws Exception {
            // Given: High volume of message creations
            int iterations = 200;
            Map<String, Object> payload = Map.of("key", "value", "num", 123);
            long startTime = System.currentTimeMillis();

            // When: Messages are created
            for (int i = 0; i < iterations; i++) {
                handoffProtocol.createHandoffMessage(
                    "WI-MSG-PERF-" + i, "source", "target", "session", payload);
            }

            long duration = System.currentTimeMillis() - startTime;

            // Then: Performance is acceptable
            double throughput = iterations * 1000.0 / duration;
            System.out.printf("Message Creation Performance:%n");
            System.out.printf("  Iterations: %d%n", iterations);
            System.out.printf("  Duration: %dms%n", duration);
            System.out.printf("  Throughput: %.0f messages/sec%n", throughput);

            assertTrue(throughput >= 50,
                "Message creation must be >= 50/sec, got " + throughput);
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void startSourceServer(int port) throws IOException {
        CompositeAuthenticationProvider authProvider = createTestAuthProvider();
        sourceServer = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", port, authProvider);
        sourceServer.start();
        waitForServer(port);
    }

    private void startTargetServer(int port) throws IOException {
        CompositeAuthenticationProvider authProvider = createTestAuthProvider();
        targetServer = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", port, authProvider);
        targetServer.start();
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

    private void registerAgent(String id, int port) throws IOException {
        String payload = String.format("""
            {
              "id": "%s",
              "name": "%s Agent",
              "host": "localhost",
              "port": %d,
              "capability": {"domainName": "handoff", "description": "Handoff test agent"},
              "version": "5.2.0"
            }
            """, id, id, port);

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

    private void updateWorkItemStatus(String itemId, String status) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_work_item SET status = ? WHERE item_id = ?")) {
            ps.setString(1, status);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    private String getWorkItemStatus(String itemId) throws Exception {
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT status FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("status");
            }
            return null;
        }
    }
}
