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
 *
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.integration.a2a.auth.*;
import org.yawlfoundation.yawl.integration.a2a.handoff.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive A2A validation tests for CI/CD integration.
 *
 * Tests all aspects of the A2A server implementation:
 * - Agent card discovery and validation
 * - Authentication provider functionality
 * - Skill registration and execution
 * - Handoff protocol compliance (ADR-025)
 * - Performance characteristics
 *
 * All tests use real server instances (Chicago TDD style).
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.CONCURRENT)
class A2AValidationTest {

    private static final int TEST_PORT = 19890;
    private static final String JWT_SECRET =
        "validation-test-secret-minimum-32-characters";
    private static final String API_KEY_MASTER = "validation-master-key-16";
    private static final String API_KEY_VALUE = "validation-test-api-key";

    private static YawlA2AServer server;
    private static ApiKeyAuthenticationProvider apiKeyProvider;
    private static JwtAuthenticationProvider jwtProvider;

    @BeforeAll
    static void setUpClass() throws Exception {
        apiKeyProvider = new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        apiKeyProvider.registerKey("test-agent", "test-user", API_KEY_VALUE,
            Set.of(AuthenticatedPrincipal.PERM_ALL));

        jwtProvider = new JwtAuthenticationProvider(JWT_SECRET, null);

        CompositeAuthenticationProvider auth =
            new CompositeAuthenticationProvider(jwtProvider, apiKeyProvider);

        server = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT, auth);

        server.start();
        Thread.sleep(200); // Allow server to fully bind
    }

    @AfterAll
    static void tearDownClass() {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    // =========================================================================
    // Agent Card Validation Tests
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Agent card endpoint returns HTTP 200")
    void testAgentCardEndpointReturns200() throws Exception {
        HttpURLConnection conn = get("/.well-known/agent.json", null, null);
        assertEquals(200, conn.getResponseCode(),
            "Agent card endpoint should return 200");
        conn.disconnect();
    }

    @Test
    @Order(2)
    @DisplayName("Agent card has JSON content type")
    void testAgentCardContentType() throws Exception {
        HttpURLConnection conn = get("/.well-known/agent.json", null, null);
        String contentType = conn.getContentType();
        assertNotNull(contentType, "Content-Type header should be present");
        assertTrue(contentType.contains("json"),
            "Content-Type should be JSON");
        conn.disconnect();
    }

    @Test
    @Order(3)
    @DisplayName("Agent card contains required skills")
    void testAgentCardContainsSkills() throws Exception {
        String body = readBody(get("/.well-known/agent.json", null, null));

        assertTrue(body.contains("launch_workflow"),
            "Agent card should contain launch_workflow skill");
        assertTrue(body.contains("query_workflows"),
            "Agent card should contain query_workflows skill");
        assertTrue(body.contains("manage_workitems"),
            "Agent card should contain manage_workitems skill");
        assertTrue(body.contains("cancel_workflow"),
            "Agent card should contain cancel_workflow skill");
        assertTrue(body.contains("handoff_workitem"),
            "Agent card should contain handoff_workitem skill");
    }

    @Test
    @Order(4)
    @DisplayName("Agent card requires no authentication")
    void testAgentCardNoAuthRequired() throws Exception {
        HttpURLConnection conn = get("/.well-known/agent.json", null, null);
        assertEquals(200, conn.getResponseCode(),
            "Agent card should be accessible without authentication");
        conn.disconnect();
    }

    // =========================================================================
    // Authentication Validation Tests
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("POST without credentials returns 401")
    void testPostWithoutCredentialsReturns401() throws Exception {
        HttpURLConnection conn = post("/", null, null,
            "{\"message\":\"test\"}");
        assertEquals(401, conn.getResponseCode(),
            "POST without credentials should return 401");
        conn.disconnect();
    }

    @Test
    @Order(11)
    @DisplayName("Invalid API key returns 401")
    void testInvalidApiKeyReturns401() throws Exception {
        HttpURLConnection conn = post("/", "X-API-Key", "invalid-key",
            "{\"message\":\"test\"}");
        assertEquals(401, conn.getResponseCode(),
            "Invalid API key should return 401");
        conn.disconnect();
    }

    @Test
    @Order(12)
    @DisplayName("Valid API key authenticates successfully")
    void testValidApiKeyAuthenticates() throws Exception {
        HttpURLConnection conn = post("/", "X-API-Key", API_KEY_VALUE,
            "{\"message\":\"list specifications\"}");
        assertNotEquals(401, conn.getResponseCode(),
            "Valid API key should not return 401");
        conn.disconnect();
    }

    @Test
    @Order(13)
    @DisplayName("Invalid JWT returns 401")
    void testInvalidJwtReturns401() throws Exception {
        HttpURLConnection conn = post("/", "Authorization",
            "Bearer invalid.jwt.token", "{\"message\":\"test\"}");
        assertEquals(401, conn.getResponseCode(),
            "Invalid JWT should return 401");
        conn.disconnect();
    }

    @Test
    @Order(14)
    @DisplayName("Valid JWT authenticates successfully")
    void testValidJwtAuthenticates() throws Exception {
        String token = jwtProvider.issueToken("test-agent",
            List.of(AuthenticatedPrincipal.PERM_ALL), 60_000L);

        HttpURLConnection conn = post("/", "Authorization",
            "Bearer " + token, "{\"message\":\"list specifications\"}");
        assertNotEquals(401, conn.getResponseCode(),
            "Valid JWT should not return 401");
        conn.disconnect();
    }

    @Test
    @Order(15)
    @DisplayName("401 response includes WWW-Authenticate header")
    void testUnauthorizedIncludesWwwAuthenticate() throws Exception {
        HttpURLConnection conn = post("/", null, null,
            "{\"message\":\"test\"}");
        String wwwAuth = conn.getHeaderField("WWW-Authenticate");
        assertNotNull(wwwAuth,
            "401 response must include WWW-Authenticate header");
        assertTrue(wwwAuth.contains("Bearer") || wwwAuth.contains("ApiKey"),
            "WWW-Authenticate should reference supported scheme");
        conn.disconnect();
    }

    // =========================================================================
    // Permission-Based Authorization Tests
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("Query-only permission cannot launch workflow")
    void testQueryOnlyCannotLaunch() throws Exception {
        String token = jwtProvider.issueToken("query-agent",
            List.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY), 60_000L);

        HttpURLConnection conn = post("/", "Authorization",
            "Bearer " + token, "{\"message\":\"launch workflow test\"}");

        int code = conn.getResponseCode();
        assertTrue(code == 403 || code == 400,
            "Query-only agent should get 403 or 400 for launch attempt");
        conn.disconnect();
    }

    // =========================================================================
    // Handoff Protocol Validation Tests
    // =========================================================================

    @Test
    @Order(30)
    @DisplayName("Handoff endpoint requires authentication")
    void testHandoffRequiresAuth() throws Exception {
        HttpURLConnection conn = post("/handoff", null, null,
            "{\"message\":\"YAWL_HANDOFF:WI-42\"}");
        assertEquals(401, conn.getResponseCode(),
            "Handoff endpoint should require authentication");
        conn.disconnect();
    }

    @Test
    @Order(31)
    @DisplayName("Handoff with valid auth is processed")
    void testHandoffWithValidAuth() throws Exception {
        HttpURLConnection conn = post("/handoff", "X-API-Key", API_KEY_VALUE,
            "{\"message\":\"YAWL_HANDOFF:WI-42\"}");

        int code = conn.getResponseCode();
        assertNotEquals(401, code,
            "Handoff with valid auth should not return 401");
        conn.disconnect();
    }

    @Test
    @Order(32)
    @DisplayName("Handoff without prefix is rejected")
    void testHandoffWithoutPrefix() throws Exception {
        HttpURLConnection conn = post("/handoff", "X-API-Key", API_KEY_VALUE,
            "{\"message\":\"WI-42:agent-a:agent-b\"}");

        int code = conn.getResponseCode();
        assertTrue(code >= 400,
            "Handoff without YAWL_HANDOFF prefix should be rejected");
        conn.disconnect();
    }

    // =========================================================================
    // Error Response Validation Tests
    // =========================================================================

    @Test
    @Order(40)
    @DisplayName("Error response is valid JSON")
    void testErrorResponseBodyIsJson() throws Exception {
        HttpURLConnection conn = post("/", null, null,
            "{\"message\":\"test\"}");
        String body = readErrorBody(conn);
        assertTrue(body.contains("error"),
            "Error response should contain 'error' key");
        conn.disconnect();
    }

    @Test
    @Order(41)
    @DisplayName("Malformed JSON returns 400")
    void testMalformedJsonReturns400() throws Exception {
        HttpURLConnection conn = post("/", "X-API-Key", API_KEY_VALUE,
            "not valid json");
        assertEquals(400, conn.getResponseCode(),
            "Malformed JSON should return 400");
        conn.disconnect();
    }

    // =========================================================================
    // Performance Validation Tests
    // =========================================================================

    @Test
    @Order(50)
    @DisplayName("Agent card response under 100ms")
    void testAgentCardPerformance() throws Exception {
        long start = System.nanoTime();
        HttpURLConnection conn = get("/.well-known/agent.json", null, null);
        readBody(conn);
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsed < 100,
            "Agent card response should be under 100ms, was: " + elapsed + "ms");
    }

    @Test
    @Order(51)
    @DisplayName("Authentication under 50ms")
    void testAuthenticationPerformance() throws Exception {
        long start = System.nanoTime();
        HttpURLConnection conn = post("/", "X-API-Key", API_KEY_VALUE,
            "{\"message\":\"test\"}");
        conn.getResponseCode();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsed < 50,
            "Authentication should be under 50ms, was: " + elapsed + "ms");
        conn.disconnect();
    }

    // =========================================================================
    // Server Lifecycle Tests
    // =========================================================================

    @Test
    @Order(60)
    @DisplayName("Server is running after start")
    void testServerIsRunning() {
        assertTrue(server.isRunning(),
            "Server should be running after start");
    }

    // =========================================================================
    // HTTP Helper Methods
    // =========================================================================

    private HttpURLConnection get(String path, String headerName, String headerValue)
            throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (headerName != null && headerValue != null) {
            conn.setRequestProperty(headerName, headerValue);
        }
        return conn;
    }

    private HttpURLConnection post(String path, String headerName, String headerValue,
                                   String body) throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "application/json");
        if (headerName != null && headerValue != null) {
            conn.setRequestProperty(headerName, headerValue);
        }
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bodyBytes.length);
        conn.getOutputStream().write(bodyBytes);
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } finally {
            conn.disconnect();
        }
    }

    private String readErrorBody(HttpURLConnection conn) throws Exception {
        try {
            java.io.InputStream errorStream = conn.getErrorStream();
            if (errorStream == null) {
                throw new IllegalStateException(
                    "Expected error stream from HTTP " + conn.getResponseCode() +
                    " but getErrorStream() returned null");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } finally {
            conn.disconnect();
        }
    }
}
