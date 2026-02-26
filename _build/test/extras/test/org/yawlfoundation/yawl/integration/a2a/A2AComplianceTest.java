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

package org.yawlfoundation.yawl.integration.a2a;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A2A protocol compliance tests ensuring YAWL implements the A2A specification correctly.
 *
 * Chicago TDD: Tests verify compliance with A2A protocol specification requirements.
 * Tests use real server instances and HTTP clients to verify wire-level compliance.
 *
 * Coverage targets:
 * - Agent card compliance with A2A specification
 * - HTTP header requirements (Content-Type, Accept)
 * - Status code compliance
 * - Authentication header formats
 * - Error response formats
 * - Protocol version handling
 * - Capability advertising
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-22
 */
public class A2AComplianceTest extends TestCase {

    private static final int TEST_PORT = 19882;
    private YawlA2AServer server;

    public A2AComplianceTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        server = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT,
            CompositeAuthenticationProvider.production());
        server.start();
        Thread.sleep(100); // allow server to fully bind
    }

    @Override
    protected void tearDown() throws Exception {
        if (server != null && server.isRunning()) {
            server.stop();
        }
        server = null;
        super.tearDown();
    }

    // =========================================================================
    // Agent Card Compliance Tests
    // =========================================================================

    public void testAgentCardHasRequiredA2AFields() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/.well-known/agent.json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int statusCode = conn.getResponseCode();
        assertEquals("Agent card should be accessible", 200, statusCode);

        String response = readResponse(conn);
        assertTrue("Response should be JSON", response.startsWith("{"));
        assertTrue("Response should contain name", response.contains("\"name\""));
        assertTrue("Response should contain description", response.contains("\"description\""));
        assertTrue("Response should contain capabilities", response.contains("\"capabilities\""));
        assertTrue("Response should contain protocolVersion", response.contains("\"protocolVersion\""));
    }

    public void testAgentCardProtocolVersionIsCurrent() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/.well-known/agent.json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int statusCode = conn.getResponseCode();
        assertEquals("Agent card should be accessible", 200, statusCode);

        String response = readResponse(conn);
        assertTrue("Response should contain protocolVersion", response.contains("\"protocolVersion\""));
        // Verify it's using a supported version (v1 or higher)
        assertTrue("Protocol version should be v1 or higher",
                  response.contains("\"protocolVersion\":\"1\""));
    }

    public void testAgentCardListsAllRequiredCapabilities() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/.well-known/agent.json");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int statusCode = conn.getResponseCode();
        assertEquals("Agent card should be accessible", 200, statusCode);

        String response = readResponse(conn);
        assertTrue("Response should contain capabilities array", response.contains("\"capabilities\":["));

        // Verify core A2A capabilities are present
        assertTrue("Should support launch_workflow", response.contains("\"launch_workflow\""));
        assertTrue("Should support query_workflows", response.contains("\"query_workflows\""));
        assertTrue("Should support manage_workitems", response.contains("\"manage_workitems\""));
        assertTrue("Should support cancel_workflow", response.contains("\"cancel_workflow\""));
    }

    // =========================================================================
    // HTTP Header Compliance Tests
    // =========================================================================

    public void testContentTypeHeaderForJsonRequests() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write("{\"test\": true}".getBytes());

        int statusCode = conn.getResponseCode();
        // Should return 401 for unauthenticated request
        assertEquals("Should return 401 for unauthenticated", 401, statusCode);

        String contentType = conn.getContentType();
        assertTrue("Response should be JSON", contentType != null && contentType.contains("application/json"));
    }

    public void testAcceptHeaderHandling() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write("{\"test\": true}".getBytes());

        int statusCode = conn.getResponseCode();
        assertEquals("Should handle Accept header", 401, statusCode);
    }

    // =========================================================================
    // Status Code Compliance Tests
    // =========================================================================

    public void testUnauthorizedRequestsReturn401() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write("{\"test\": true}".getBytes());

        int statusCode = conn.getResponseCode();
        assertEquals("Unauthorized requests should return 401", 401, statusCode);

        String wwwAuth = conn.getHeaderField("WWW-Authenticate");
        assertTrue("Should include WWW-Authenticate header",
                  wwwAuth != null && wwwAuth.length() > 0);
    }

    public void testNotFoundRequestsReturn404() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/nonexistent-endpoint");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        int statusCode = conn.getResponseCode();
        assertEquals("Unknown paths should return 404", 404, statusCode);
    }

    public void testMethodNotAllowedReturn405() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PATCH"); // Not allowed method

        int statusCode = conn.getResponseCode();
        assertEquals("Unsupported HTTP methods should return 405", 405, statusCode);
    }

    // =========================================================================
    // Authentication Compliance Tests
    // =========================================================================

    public void testBasicAuthHeaderFormat() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Basic dGVzdDp0ZXN0"); // base64 encoded "test:test"
        conn.setDoOutput(true);
        conn.getOutputStream().write("{\"test\": true}".getBytes());

        int statusCode = conn.getResponseCode();
        // Should reject invalid basic auth
        assertEquals("Invalid basic auth should return 401", 401, statusCode);
    }

    // =========================================================================
    // Error Response Compliance Tests
    // =========================================================================

    public void testErrorResponseFormat() throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write("{\"test\": true}".getBytes());

        int statusCode = conn.getResponseCode();
        assertEquals("Should return error", 401, statusCode);

        String response = readResponse(conn);
        assertTrue("Error response should be JSON", response.startsWith("{"));
        assertTrue("Error response should contain error field", response.contains("\"error\""));
        assertTrue("Error response should contain message field", response.contains("\"message\""));
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private String readResponse(HttpURLConnection conn) throws Exception {
        if (conn.getResponseCode() < 400) {
            try (BufferedReader reader = new BufferedReader(
                 new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining());
            }
        } else {
            try (BufferedReader reader = new BufferedReader(
                 new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining());
            }
        }
    }
}