/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.a2a;

import org.junit.jupiter.api.Tag;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Integration tests for YawlA2AServer (V6 feature).
 *
 * Chicago TDD: tests real YawlA2AServer construction, start/stop lifecycle,
 * and HTTP endpoint behavior. Tests use real HTTP connections to a locally
 * started server.
 *
 * Coverage targets:
 * - Constructor validation (all required params)
 * - Port validation (1-65535)
 * - isRunning() lifecycle
 * - HTTP server starts and binds correctly
 * - Agent card endpoint responds
 * - stop() tears down cleanly
 * - Guard conditions
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("integration")
public class YawlA2AServerTest extends TestCase {

    // Use a port unlikely to be in use during tests
    private static final int TEST_PORT = 19876;

    private YawlA2AServer server;

    public YawlA2AServerTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        server = null;
    }

    /**
     * Creates a YawlA2AServer with the required V6 authentication provider.
     * Uses CompositeAuthenticationProvider.production() which is the recommended stack.
     */
    private static YawlA2AServer newServer(String url, String user, String pass, int port) {
        return new YawlA2AServer(url, user, pass, port,
                CompositeAuthenticationProvider.production());
    }

    @Override
    protected void tearDown() {
        if (server != null && server.isRunning()) {
            try {
                server.stop();
            } catch (Exception e) {
                // ignore cleanup errors
            }
        }
    }

    // =========================================================================
    // Constructor tests
    // =========================================================================

    public void testConstructorWithValidParameters() {
        YawlA2AServer s = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT);
        assertNotNull("Server should be constructed successfully", s);
    }

    public void testConstructorWithNullEngineUrlThrows() {
        try {
            newServer(null, "admin", "YAWL", TEST_PORT);
            fail("Expected IllegalArgumentException for null engineUrl");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention engine URL",
                    e.getMessage().toLowerCase().contains("url") ||
                    e.getMessage().toLowerCase().contains("engine"));
        }
    }

    public void testConstructorWithEmptyEngineUrlThrows() {
        try {
            newServer("", "admin", "YAWL", TEST_PORT);
            fail("Expected IllegalArgumentException for empty engineUrl");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testConstructorWithNullUsernameThrows() {
        try {
            newServer("http://localhost:8080/yawl", null, "YAWL", TEST_PORT);
            fail("Expected IllegalArgumentException for null username");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testConstructorWithEmptyUsernameThrows() {
        try {
            newServer("http://localhost:8080/yawl", "", "YAWL", TEST_PORT);
            fail("Expected IllegalArgumentException for empty username");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testConstructorWithNullPasswordThrows() {
        try {
            newServer("http://localhost:8080/yawl", "admin", null, TEST_PORT);
            fail("Expected IllegalArgumentException for null password");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testConstructorWithEmptyPasswordThrows() {
        try {
            newServer("http://localhost:8080/yawl", "admin", "", TEST_PORT);
            fail("Expected IllegalArgumentException for empty password");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testConstructorWithZeroPortThrows() {
        try {
            newServer("http://localhost:8080/yawl", "admin", "YAWL", 0);
            fail("Expected IllegalArgumentException for port 0");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention port",
                    e.getMessage().contains("Port") || e.getMessage().contains("port"));
        }
    }

    public void testConstructorWithNegativePortThrows() {
        try {
            newServer("http://localhost:8080/yawl", "admin", "YAWL", -1);
            fail("Expected IllegalArgumentException for negative port");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testConstructorWithPortAbove65535Throws() {
        try {
            newServer("http://localhost:8080/yawl", "admin", "YAWL", 65536);
            fail("Expected IllegalArgumentException for port 65536");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testConstructorWithMaxValidPort() {
        YawlA2AServer s = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", 65535);
        assertNotNull(s);
    }

    public void testConstructorWithPort1() {
        // Port 1 is technically valid per the guard (>= 1)
        YawlA2AServer s = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", 1);
        assertNotNull(s);
    }

    // =========================================================================
    // isRunning() before start
    // =========================================================================

    public void testIsNotRunningBeforeStart() {
        YawlA2AServer s = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT);
        assertFalse("Server should not be running before start()", s.isRunning());
    }

    // =========================================================================
    // HTTP server lifecycle - start and stop
    // =========================================================================

    public void testStartBindsHttpServer() throws Exception {
        server = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT);

        server.start();
        assertTrue("Server should be running after start()", server.isRunning());
    }

    public void testStopTerminatesHttpServer() throws Exception {
        server = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT);

        server.start();
        assertTrue(server.isRunning());

        server.stop();
        assertFalse("Server should not be running after stop()", server.isRunning());
        server = null; // prevent double-stop in tearDown
    }

    // =========================================================================
    // Agent card endpoint
    // =========================================================================

    public void testAgentCardEndpointResponds() throws Exception {
        server = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT);

        server.start();

        // Give the server a moment to fully initialize
        Thread.sleep(100);

        URL url = new URL("http://localhost:" + TEST_PORT + "/.well-known/agent.json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        int responseCode = conn.getResponseCode();
        assertEquals("Agent card endpoint should respond with 200",
                200, responseCode);

        String contentType = conn.getContentType();
        assertNotNull("Should have Content-Type header", contentType);
        assertTrue("Content-Type should be JSON",
                contentType.contains("json") || contentType.contains("application/json"));

        conn.disconnect();
    }

    public void testHealthEndpointAfterStartIsBound() throws Exception {
        server = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT);

        server.start();
        Thread.sleep(100);

        // Test that POST to / returns some response (even if it's an error from
        // the A2A request handler - it means the server is bound and responding)
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.getOutputStream().write("{\"test\":true}".getBytes());

        // Any response code (even 400/500) means the server is responding
        int responseCode = conn.getResponseCode();
        assertTrue("Server should be responding",
                responseCode >= 100 && responseCode < 600);

        conn.disconnect();
    }

    // =========================================================================
    // Stop before start
    // =========================================================================

    public void testStopBeforeStartIsNoOp() {
        YawlA2AServer s = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT);

        try {
            s.stop();
        } catch (Exception e) {
            fail("stop() before start() should not throw: " + e.getMessage());
        }

        assertFalse("Server should still not be running", s.isRunning());
    }

    // =========================================================================
    // Not Found endpoint
    // =========================================================================

    public void testNotFoundEndpointReturns404() throws Exception {
        server = newServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT);

        server.start();
        Thread.sleep(100);

        URL url = new URL("http://localhost:" + TEST_PORT + "/nonexistent/path");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        int responseCode = conn.getResponseCode();
        // Should be 404 - path falls through to the "/" context handler
        // which returns 404 for unknown paths
        assertTrue("Unknown path should return 4xx",
                responseCode == 404 || responseCode == 400);

        conn.disconnect();
    }
}
