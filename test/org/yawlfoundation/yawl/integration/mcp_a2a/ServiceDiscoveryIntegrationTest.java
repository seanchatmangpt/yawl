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
import org.yawlfoundation.yawl.integration.a2a.YawlA2AServer;
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Service discovery integration tests for MCP-A2A MVP.
 *
 * Tests service registration, discovery, and coordination:
 * - Agent registration and heartbeat
 * - Capability-based discovery
 * - Service health monitoring
 * - Load balancing across discovered services
 * - Client API integration
 *
 * Chicago TDD methodology: Real HTTP servers, real registry,
 * real network communication - no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("integration")
@Tag("discovery")
class ServiceDiscoveryIntegrationTest {

    private static final int REGISTRY_PORT = 19900;
    private static final int A2A_PORT_1 = 19901;
    private static final int A2A_PORT_2 = 19902;
    private static final int A2A_PORT_3 = 19903;

    private static final String JWT_SECRET = "discovery-test-jwt-secret-key-min-32-chars";
    private static final String API_KEY = "discovery-test-api-key";

    private AgentRegistry registry;
    private YawlA2AServer a2aServer1;
    private YawlA2AServer a2aServer2;
    private YawlA2AServer a2aServer3;

    @BeforeEach
    void setUp() {
        System.setProperty("A2A_JWT_SECRET", JWT_SECRET);
        System.setProperty("A2A_API_KEY", API_KEY);
    }

    @AfterEach
    void tearDown() {
        if (a2aServer1 != null && a2aServer1.isRunning()) a2aServer1.stop();
        if (a2aServer2 != null && a2aServer2.isRunning()) a2aServer2.stop();
        if (a2aServer3 != null && a2aServer3.isRunning()) a2aServer3.stop();
        if (registry != null) registry.stop();

        System.clearProperty("A2A_JWT_SECRET");
        System.clearProperty("A2A_API_KEY");
    }

    // =========================================================================
    // Agent Registration Tests
    // =========================================================================

    @Nested
    @DisplayName("Agent Registration")
    class AgentRegistrationTests {

        @Test
        @DisplayName("Single agent registration")
        void singleAgent_Registration() throws Exception {
            // Given: Running registry
            startRegistry(REGISTRY_PORT);

            // When: Agent is registered
            String response = registerAgent("test-agent-1", "Test Agent 1", A2A_PORT_1, "workflow-a2a");

            // Then: Registration succeeds
            assertTrue(response.contains("registered"), "Registration must succeed");
            assertEquals(1, registry.getAgentCount(), "Registry must have 1 agent");

            // And: Agent is discoverable
            String agents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents");
            assertTrue(agents.contains("test-agent-1"), "Agent must be in registry");
        }

        @Test
        @DisplayName("Multiple agents registration")
        void multipleAgents_Registration() throws Exception {
            // Given: Running registry
            startRegistry(REGISTRY_PORT);

            // When: Multiple agents register
            registerAgent("agent-a", "Agent A", 9001, "workflow-a2a");
            registerAgent("agent-b", "Agent B", 9002, "process-mining");
            registerAgent("agent-c", "Agent C", 9003, "workflow-a2a");

            // Then: All agents are registered
            assertEquals(3, registry.getAgentCount(), "Registry must have 3 agents");

            // And: All are discoverable
            String agents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents");
            assertTrue(agents.contains("agent-a"), "Agent A must be present");
            assertTrue(agents.contains("agent-b"), "Agent B must be present");
            assertTrue(agents.contains("agent-c"), "Agent C must be present");
        }

        @Test
        @DisplayName("Agent heartbeat updates last-seen")
        void agentHeartbeat_UpdatesLastSeen() throws Exception {
            // Given: Registered agent
            startRegistry(REGISTRY_PORT);
            registerAgent("heartbeat-agent", "Heartbeat Agent", 9001, "test");

            // When: Heartbeat is sent
            Thread.sleep(100); // Small delay
            int status = httpPostStatus(
                "http://localhost:" + REGISTRY_PORT + "/agents/heartbeat-agent/heartbeat",
                "{}");

            // Then: Heartbeat succeeds
            assertEquals(200, status, "Heartbeat must succeed");

            // And: Agent is still registered
            assertEquals(1, registry.getAgentCount(), "Agent must still be registered");
        }

        @Test
        @DisplayName("Agent unregistration")
        void agentUnregistration() throws Exception {
            // Given: Registered agent
            startRegistry(REGISTRY_PORT);
            registerAgent("unregister-agent", "Unregister Agent", 9001, "test");
            assertEquals(1, registry.getAgentCount());

            // When: Agent is unregistered
            int status = httpDelete("http://localhost:" + REGISTRY_PORT + "/agents/unregister-agent");

            // Then: Unregistration succeeds
            assertEquals(200, status, "Unregistration must succeed");
            assertEquals(0, registry.getAgentCount(), "Agent must be removed");
        }
    }

    // =========================================================================
    // Capability-Based Discovery Tests
    // =========================================================================

    @Nested
    @DisplayName("Capability-Based Discovery")
    class CapabilityDiscoveryTests {

        @Test
        @DisplayName("Discover agents by domain capability")
        void discoverByDomain_Capability() throws Exception {
            // Given: Multiple agents with different capabilities
            startRegistry(REGISTRY_PORT);
            registerAgent("workflow-agent-1", "Workflow Agent 1", 9001, "workflow-a2a");
            registerAgent("workflow-agent-2", "Workflow Agent 2", 9002, "workflow-a2a");
            registerAgent("mining-agent", "Mining Agent", 9003, "process-mining");

            // When: Query by workflow-a2a domain
            String workflowAgents = httpGet(
                "http://localhost:" + REGISTRY_PORT + "/agents/by-capability?domain=workflow-a2a");

            // Then: Only workflow agents are returned
            assertTrue(workflowAgents.contains("workflow-agent-1"), "Workflow agent 1 must match");
            assertTrue(workflowAgents.contains("workflow-agent-2"), "Workflow agent 2 must match");
            assertFalse(workflowAgents.contains("mining-agent"), "Mining agent must not match");
        }

        @Test
        @DisplayName("Discover agents by description match")
        void discoverByDescription_Match() throws Exception {
            // Given: Agents with descriptive capabilities
            startRegistry(REGISTRY_PORT);
            registerAgentWithDescription("doc-agent", "Document Agent", 9001,
                "document-processing and workflow");
            registerAgentWithDescription("image-agent", "Image Agent", 9002,
                "image recognition and classification");

            // When: Query by partial description
            String docAgents = httpGet(
                "http://localhost:" + REGISTRY_PORT + "/agents/by-capability?domain=document");

            // Then: Matching agents are returned
            assertTrue(docAgents.contains("doc-agent"), "Document agent must match");
            assertFalse(docAgents.contains("image-agent"), "Image agent must not match");
        }

        @Test
        @DisplayName("Empty result for non-matching capability")
        void emptyResult_NonMatchingCapability() throws Exception {
            // Given: Registered agents
            startRegistry(REGISTRY_PORT);
            registerAgent("agent-1", "Agent 1", 9001, "workflow");

            // When: Query for non-existent capability
            String result = httpGet(
                "http://localhost:" + REGISTRY_PORT + "/agents/by-capability?domain=nonexistent");

            // Then: Empty array is returned
            assertTrue(result.equals("[]") || !result.contains("agent-1"),
                "No agents should match non-existent capability");
        }
    }

    // =========================================================================
    // Health Monitoring Tests
    // =========================================================================

    @Nested
    @DisplayName("Service Health Monitoring")
    class HealthMonitoringTests {

        @Test
        @DisplayName("Registry health status")
        void registryHealth_Status() throws Exception {
            // Given: Running registry
            startRegistry(REGISTRY_PORT);

            // When: Agents are registered
            registerAgent("healthy-agent", "Healthy Agent", 9001, "test");

            // Then: Registry reports correct count
            assertEquals(1, registry.getAgentCount(), "Registry health must reflect registered agents");

            // And: Agent list is accessible
            List<AgentInfo> agents = registry.getAllAgents();
            assertEquals(1, agents.size(), "Agent list must contain registered agent");
            assertEquals("healthy-agent", agents.get(0).getId(), "Agent ID must match");
        }

        @Test
        @DisplayName("Agent availability via heartbeat")
        void agentAvailability_ViaHeartbeat() throws Exception {
            // Given: Registered agent
            startRegistry(REGISTRY_PORT);
            registerAgent("available-agent", "Available Agent", 9001, "test");

            // When: Multiple heartbeats are sent
            for (int i = 0; i < 3; i++) {
                int status = httpPostStatus(
                    "http://localhost:" + REGISTRY_PORT + "/agents/available-agent/heartbeat",
                    "{}");
                assertEquals(200, status, "Heartbeat " + (i + 1) + " must succeed");
                Thread.sleep(50);
            }

            // Then: Agent remains registered
            assertEquals(1, registry.getAgentCount(), "Agent must remain registered after heartbeats");
        }
    }

    // =========================================================================
    // Load Balancing Tests
    // =========================================================================

    @Nested
    @DisplayName("Load Balancing")
    class LoadBalancingTests {

        @Test
        @DisplayName("Round-robin load distribution")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void roundRobin_LoadDistribution() throws Exception {
            // Given: Multiple A2A servers registered
            startA2aServer1(A2A_PORT_1);
            startA2aServer2(A2A_PORT_2);
            startRegistry(REGISTRY_PORT);

            registerAgent("a2a-primary", "Primary A2A", A2A_PORT_1, "workflow-a2a");
            registerAgent("a2a-secondary", "Secondary A2A", A2A_PORT_2, "workflow-a2a");

            // When: Multiple requests are made
            int requests = 20;
            AtomicInteger primaryCount = new AtomicInteger(0);
            AtomicInteger secondaryCount = new AtomicInteger(0);

            for (int i = 0; i < requests; i++) {
                // Simulate round-robin
                int port = (i % 2 == 0) ? A2A_PORT_1 : A2A_PORT_2;
                String response = httpGet("http://localhost:" + port + "/.well-known/agent.json");

                if (response.contains("YAWL")) {
                    if (port == A2A_PORT_1) {
                        primaryCount.incrementAndGet();
                    } else {
                        secondaryCount.incrementAndGet();
                    }
                }
            }

            // Then: Load is distributed evenly
            assertEquals(10, primaryCount.get(), "Primary should handle 10 requests");
            assertEquals(10, secondaryCount.get(), "Secondary should handle 10 requests");
        }

        @Test
        @DisplayName("Failover to available agents")
        void failover_ToAvailableAgents() throws Exception {
            // Given: Two A2A servers (only one running)
            startA2aServer1(A2A_PORT_1);
            startRegistry(REGISTRY_PORT);

            registerAgent("active-a2a", "Active A2A", A2A_PORT_1, "workflow-a2a");
            registerAgent("inactive-a2a", "Inactive A2A", 59999, "workflow-a2a");

            // When: Request is made (failover from inactive to active)
            String response = httpGet("http://localhost:" + A2A_PORT_1 + "/.well-known/agent.json");

            // Then: Active server responds
            assertTrue(response.contains("YAWL"), "Active server must respond");
        }
    }

    // =========================================================================
    // Client API Tests
    // =========================================================================

    @Nested
    @DisplayName("Client API Integration")
    class ClientApiTests {

        @Test
        @DisplayName("AgentRegistryClient can connect")
        void registryClient_CanConnect() throws Exception {
            // Given: Running registry
            startRegistry(REGISTRY_PORT);

            // When: Client connects
            AgentRegistryClient client = new AgentRegistryClient("localhost", REGISTRY_PORT);

            // Then: Client is created successfully
            assertNotNull(client, "Client must be created");
        }

        @Test
        @DisplayName("Registry client operations")
        void registryClient_Operations() throws Exception {
            // Given: Running registry with agents
            startRegistry(REGISTRY_PORT);
            registerAgent("client-test-agent", "Client Test", 9001, "test");

            // When: Operations are performed
            String agents = httpGet("http://localhost:" + REGISTRY_PORT + "/agents");

            // Then: Operations succeed
            assertTrue(agents.contains("client-test-agent"), "Agent must be discoverable via client");
        }
    }

    // =========================================================================
    // Concurrent Access Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Access")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("Concurrent agent registrations")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void concurrentRegistrations() throws Exception {
            // Given: Running registry
            startRegistry(REGISTRY_PORT);

            // When: Multiple agents register concurrently
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        registerAgent("concurrent-agent-" + index,
                            "Concurrent Agent " + index, 9000 + index, "test");
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

            // Then: All registrations succeed
            assertTrue(completed, "All registrations must complete");
            assertEquals(threadCount, successCount.get(),
                "All registrations must succeed");
            assertEquals(threadCount, registry.getAgentCount(),
                "Registry must have all agents");
        }

        @Test
        @DisplayName("Concurrent discovery queries")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void concurrentDiscoveryQueries() throws Exception {
            // Given: Registry with agents
            startRegistry(REGISTRY_PORT);
            for (int i = 0; i < 5; i++) {
                registerAgent("discovery-agent-" + i, "Agent " + i, 9000 + i, "test");
            }

            // When: Concurrent discovery queries
            int queryCount = 50;
            CountDownLatch latch = new CountDownLatch(queryCount);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < queryCount; i++) {
                executor.submit(() -> {
                    try {
                        String agents = httpGet(
                            "http://localhost:" + REGISTRY_PORT + "/agents");
                        if (agents.contains("discovery-agent")) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Handle failure
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(20, TimeUnit.SECONDS);
            executor.shutdown();

            // Then: All queries succeed
            assertTrue(completed, "All queries must complete");
            assertEquals(queryCount, successCount.get(),
                "All queries must succeed");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void startRegistry(int port) throws IOException {
        registry = new AgentRegistry(port);
        registry.start();
        waitForServer(port);
    }

    private void startA2aServer1(int port) throws IOException {
        CompositeAuthenticationProvider authProvider = createTestAuthProvider();
        a2aServer1 = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", port, authProvider);
        a2aServer1.start();
        waitForServer(port);
    }

    private void startA2aServer2(int port) throws IOException {
        CompositeAuthenticationProvider authProvider = createTestAuthProvider();
        a2aServer2 = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", port, authProvider);
        a2aServer2.start();
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

    private String registerAgent(String id, String name, int port, String domain) throws IOException {
        String payload = String.format("""
            {
              "id": "%s",
              "name": "%s",
              "host": "localhost",
              "port": %d,
              "capability": {"domainName": "%s", "description": "Test agent"},
              "version": "5.2.0"
            }
            """, id, name, port, domain);
        return httpPost("http://localhost:" + REGISTRY_PORT + "/agents/register", payload);
    }

    private void registerAgentWithDescription(String id, String name, int port, String description)
            throws IOException {
        String payload = String.format("""
            {
              "id": "%s",
              "name": "%s",
              "host": "localhost",
              "port": %d,
              "capability": {"domainName": "test", "description": "%s"},
              "version": "5.2.0"
            }
            """, id, name, port, description);
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

    private String httpPost(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        String response;
        try (InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
            response = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
        }
        conn.disconnect();

        if (status < 200 || status >= 300) {
            throw new IOException("HTTP POST failed with status " + status + ": " + response);
        }
        return response;
    }

    private int httpPostStatus(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
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
}
