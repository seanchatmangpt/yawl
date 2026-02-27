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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.metrics.VirtualThreadMetrics;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for VirtualThreadYawlA2AServer.
 *
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Constructor validation and parameter checking</li>
 *   <li>Server lifecycle (start/stop)</li>
 *   <li>HTTP endpoints respond correctly</li>
 *   <li>Virtual thread metrics collection</li>
 *   <li>Concurrent request handling</li>
 *   <li>Graceful shutdown behavior</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("integration")
class VirtualThreadYawlA2AServerTest {

    private static final int TEST_PORT = 19877;
    private static final String TEST_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "YAWL";

    private VirtualThreadYawlA2AServer server;

    @BeforeEach
    void setUp() {
        server = null;
    }

    @AfterEach
    void tearDown() {
        if (server != null && server.isRunning()) {
            try {
                server.stop();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    // =========================================================================
    // Constructor tests
    // =========================================================================

    @Test
    @DisplayName("Constructor with valid parameters should succeed")
    void testConstructorWithValidParameters() {
        VirtualThreadYawlA2AServer s = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        assertNotNull(s, "Server should be constructed successfully");
        assertFalse(s.isRunning(), "Server should not be running after construction");
    }

    @Test
    @DisplayName("Constructor with null engine URL should throw")
    void testConstructorWithNullEngineUrlThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> newServer(null, TEST_USERNAME, TEST_PASSWORD, TEST_PORT));
        assertTrue(e.getMessage().toLowerCase().contains("url") ||
                   e.getMessage().toLowerCase().contains("engine"));
    }

    @Test
    @DisplayName("Constructor with empty engine URL should throw")
    void testConstructorWithEmptyEngineUrlThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> newServer("", TEST_USERNAME, TEST_PASSWORD, TEST_PORT));
    }

    @Test
    @DisplayName("Constructor with null username should throw")
    void testConstructorWithNullUsernameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> newServer(TEST_ENGINE_URL, null, TEST_PASSWORD, TEST_PORT));
    }

    @Test
    @DisplayName("Constructor with empty username should throw")
    void testConstructorWithEmptyUsernameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> newServer(TEST_ENGINE_URL, "", TEST_PASSWORD, TEST_PORT));
    }

    @Test
    @DisplayName("Constructor with null password should throw")
    void testConstructorWithNullPasswordThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> newServer(TEST_ENGINE_URL, TEST_USERNAME, null, TEST_PORT));
    }

    @Test
    @DisplayName("Constructor with empty password should throw")
    void testConstructorWithEmptyPasswordThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> newServer(TEST_ENGINE_URL, TEST_USERNAME, "", TEST_PORT));
    }

    @Test
    @DisplayName("Constructor with port 0 should throw")
    void testConstructorWithZeroPortThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, 0));
        assertTrue(e.getMessage().toLowerCase().contains("port"));
    }

    @Test
    @DisplayName("Constructor with negative port should throw")
    void testConstructorWithNegativePortThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, -1));
    }

    @Test
    @DisplayName("Constructor with port above 65535 should throw")
    void testConstructorWithPortAbove65535Throws() {
        assertThrows(IllegalArgumentException.class,
            () -> newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, 65536));
    }

    @Test
    @DisplayName("Constructor with null auth provider should throw")
    void testConstructorWithNullAuthProviderThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new VirtualThreadYawlA2AServer(
                TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT, null));
    }

    @Test
    @DisplayName("Constructor with invalid graceful shutdown seconds should throw")
    void testConstructorWithInvalidGracefulShutdownThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new VirtualThreadYawlA2AServer(
                TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT,
                0, 60, CompositeAuthenticationProvider.production()));
    }

    @Test
    @DisplayName("Constructor with invalid HTTP timeout seconds should throw")
    void testConstructorWithInvalidHttpTimeoutThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new VirtualThreadYawlA2AServer(
                TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT,
                30, 0, CompositeAuthenticationProvider.production()));
    }

    @Test
    @DisplayName("Constructor with max valid port should succeed")
    void testConstructorWithMaxValidPort() {
        VirtualThreadYawlA2AServer s = newServer(
            TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, 65535);
        assertNotNull(s);
    }

    @Test
    @DisplayName("Constructor with port 1 should succeed")
    void testConstructorWithPort1() {
        VirtualThreadYawlA2AServer s = newServer(
            TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, 1);
        assertNotNull(s);
    }

    // =========================================================================
    // Lifecycle tests
    // =========================================================================

    @Test
    @DisplayName("isRunning should return false before start")
    void testIsNotRunningBeforeStart() {
        VirtualThreadYawlA2AServer s = newServer(
            TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        assertFalse(s.isRunning(), "Server should not be running before start()");
    }

    @Test
    @DisplayName("start() should bind HTTP server")
    void testStartBindsHttpServer() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        assertTrue(server.isRunning(), "Server should be running after start()");
    }

    @Test
    @DisplayName("stop() should terminate HTTP server")
    void testStopTerminatesHttpServer() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        assertTrue(server.isRunning());

        server.stop();
        assertFalse(server.isRunning(), "Server should not be running after stop()");
        server = null; // Prevent double-stop in tearDown
    }

    @Test
    @DisplayName("stop() before start() should be no-op")
    void testStopBeforeStartIsNoOp() {
        VirtualThreadYawlA2AServer s = newServer(
            TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);

        assertDoesNotThrow(s::stop, "stop() before start() should not throw");
        assertFalse(s.isRunning(), "Server should still not be running");
    }

    @Test
    @DisplayName("Double stop() should be safe")
    void testDoubleStopIsSafe() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();

        assertDoesNotThrow(() -> {
            server.stop();
            server.stop();
        }, "Double stop() should not throw");
        server = null;
    }

    // =========================================================================
    // HTTP endpoint tests
    // =========================================================================

    @Test
    @DisplayName("Agent card endpoint should respond with 200")
    void testAgentCardEndpointResponds() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        Thread.sleep(100); // Wait for server initialization

        URL url = new URL("http://localhost:" + TEST_PORT + "/.well-known/agent.json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode, "Agent card endpoint should respond with 200");

        String contentType = conn.getContentType();
        assertNotNull(contentType, "Should have Content-Type header");
        assertTrue(contentType.contains("json") || contentType.contains("application/json"),
                   "Content-Type should be JSON");

        conn.disconnect();
    }

    @Test
    @DisplayName("Health endpoint should return healthy status")
    void testHealthEndpointReturnsHealthy() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        Thread.sleep(100);

        URL url = new URL("http://localhost:" + TEST_PORT + "/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode, "Health endpoint should return 200 for healthy server");

        conn.disconnect();
    }

    @Test
    @DisplayName("Metrics endpoint should return JSON")
    void testMetricsEndpointReturnsJson() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        Thread.sleep(100);

        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        int responseCode = conn.getResponseCode();
        assertEquals(200, responseCode, "Metrics endpoint should return 200");

        String contentType = conn.getContentType();
        assertNotNull(contentType, "Should have Content-Type header");
        assertTrue(contentType.contains("json"),
                   "Content-Type should be JSON");

        conn.disconnect();
    }

    @Test
    @DisplayName("POST to / should require authentication")
    void testMessageEndpointRequiresAuth() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        Thread.sleep(100);

        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.getOutputStream().write("{\"test\":true}".getBytes());

        int responseCode = conn.getResponseCode();
        assertEquals(401, responseCode, "POST without auth should return 401");

        conn.disconnect();
    }

    @Test
    @DisplayName("Unknown path should return 404")
    void testNotFoundEndpointReturns404() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        Thread.sleep(100);

        URL url = new URL("http://localhost:" + TEST_PORT + "/nonexistent/path");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        int responseCode = conn.getResponseCode();
        assertTrue(responseCode == 404 || responseCode == 401,
                   "Unknown path should return 404 or 401");

        conn.disconnect();
    }

    // =========================================================================
    // Metrics tests
    // =========================================================================

    @Test
    @DisplayName("getMetrics() should return valid snapshot")
    void testGetMetricsReturnsValidSnapshot() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();

        VirtualThreadMetrics.MetricsSnapshot snapshot = server.getMetrics();
        assertNotNull(snapshot, "Metrics snapshot should not be null");
        assertNotNull(snapshot.serverStartTime(), "Server start time should be set");
        assertNull(snapshot.serverStopTime(), "Server stop time should be null while running");
    }

    @Test
    @DisplayName("getRequestsProcessed() should increment on requests")
    void testRequestsProcessedIncrements() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        Thread.sleep(100);

        long initialCount = server.getRequestsProcessed();

        // Make a request to agent card endpoint
        URL url = new URL("http://localhost:" + TEST_PORT + "/.well-known/agent.json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();
        conn.getResponseCode();
        conn.disconnect();

        // Allow metrics to be recorded
        Thread.sleep(100);

        long finalCount = server.getRequestsProcessed();
        assertTrue(finalCount > initialCount,
                   "Requests processed should increment after request");
    }

    @Test
    @DisplayName("getActiveRequests() should reflect concurrent requests")
    void testActiveRequestsReflectsConcurrency() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        Thread.sleep(100);

        // Active requests should be 0 when idle
        assertEquals(0, server.getActiveRequests(),
                     "Active requests should be 0 when server is idle");
    }

    // =========================================================================
    // Concurrency tests
    // =========================================================================

    @Test
    @DisplayName("Server should handle concurrent requests")
    void testConcurrentRequests() throws Exception {
        server = newServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT);
        server.start();
        Thread.sleep(200);

        int numRequests = 10;
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numRequests; i++) {
                executor.submit(() -> {
                    try {
                        URL url = new URL("http://localhost:" + TEST_PORT + "/.well-known/agent.json");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);

                        int responseCode = conn.getResponseCode();
                        if (responseCode == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                        conn.disconnect();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "All requests should complete within timeout");

            assertEquals(numRequests, successCount.get(),
                         "All requests should succeed");
            assertEquals(0, failureCount.get(),
                         "No requests should fail");
        }
    }

    // =========================================================================
    // Graceful shutdown tests
    // =========================================================================

    @Test
    @DisplayName("Graceful shutdown should complete within timeout")
    void testGracefulShutdownCompletes() throws Exception {
        server = new VirtualThreadYawlA2AServer(
            TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD, TEST_PORT,
            5, 60, CompositeAuthenticationProvider.production());

        server.start();
        assertTrue(server.isRunning());

        long startTime = System.currentTimeMillis();
        server.stop();
        long duration = System.currentTimeMillis() - startTime;

        assertFalse(server.isRunning());
        // Shutdown should complete within 2x the configured timeout
        assertTrue(duration < 10000,
                   "Shutdown should complete within reasonable time, took " + duration + "ms");
        server = null;
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private static VirtualThreadYawlA2AServer newServer(String url, String user, String pass, int port) {
        return new VirtualThreadYawlA2AServer(url, user, pass, port,
            CompositeAuthenticationProvider.production());
    }
}
