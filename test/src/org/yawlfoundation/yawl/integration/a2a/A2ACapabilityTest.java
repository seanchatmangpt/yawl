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

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.engine.YAWLEngine;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.integration.a2a.auth.ApiKeyAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test class for A2A capabilities.
 *
 * Chicago TDD: Tests the full A2A integration workflow including
 * specification upload, agent discovery, and case creation using real
 * HTTP transport with no mocks. Tests against a live YawlA2AServer.
 *
 * Coverage targets:
 * - Specification upload via A2A with authentication
 * - Agent discovery and capability enumeration
 * - Case creation through A2A with data validation
 * - Error handling for invalid requests
 * - Resource lifecycle management
 * - Authentication edge cases
 * - JSON-RPC protocol compliance
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@DisplayName("A2A Capability Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class A2ACapabilityTest {

    private static final int TEST_PORT = 19999;
    private static final String ENGINE_URL = "http://localhost:8080/yawl";
    private static final String TEST_SPEC_NAME = "SimpleOrderProcess";
    private static final String API_KEY_MASTER = "capability-test-master-key";
    private static final String API_KEY_VALUE = "capability-test-api-key";
    private static final String JWT_SECRET = "capability-test-jwt-secret-32chars";

    private YawlA2AServer server;
    private YawlA2AClient client;
    private String testCaseId;
    private YAWLEngine yawlEngine;

    @BeforeAll
    void setup() throws Exception {
        // Create test specification
        String specContent = createTestSpecification();
        Path specFile = Files.createTempFile(TEST_SPEC_NAME, ".xml");
        Files.write(specFile, specContent.getBytes(StandardCharsets.UTF_8));

        // Set up authentication
        setupAuthentication();

        // Start A2A server
        server = new YawlA2AServer(
            ENGINE_URL,
            "admin",
            "YAWL-A2A-Capability-Test",
            TEST_PORT,
            createAuthenticationProvider()
        );
        server.start();

        // Wait for server to bind
        Thread.sleep(200);

        // Initialize YAWL engine
        yawlEngine = new YAWLEngine();
        assertNotNull(yawlEngine, "YAWL Engine should initialize successfully");

        // Create test client
        client = new YawlA2AClient("http://localhost:" + TEST_PORT);
    }

    @AfterAll
    void cleanup() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null && server.isRunning()) {
            server.stop();
        }
        if (yawlEngine != null) {
            yawlEngine.shutdown();
        }
    }

    @BeforeEach
    void beforeEach() throws Exception {
        // Upload fresh specification before each test
        uploadTestSpecification();
        testCaseId = null;
    }

    @AfterEach
    void afterEach() {
        // Clean up any created test cases
        if (testCaseId != null) {
            try {
                yawlEngine.removeCase(testCaseId);
            } catch (Exception e) {
                // Log but don't fail the test
                System.err.println("Failed to clean up test case " + testCaseId + ": " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Agent Card Discovery - Public Access")
    void testAgentCardDiscovery() throws Exception {
        // Agent card should be publicly accessible without authentication
        HttpURLConnection conn = createConnection("/.well-known/agent.json", "GET");
        assertEquals(200, conn.getResponseCode(), "Agent card should be publicly accessible");

        String response = readResponse(conn);
        assertAgentCardValid(response);

        conn.disconnect();
    }

    @Test
    @DisplayName("Specification Upload - Authentication Required")
    void testSpecificationUploadRequiresAuthentication() throws Exception {
        // Upload without authentication should fail
        HttpURLConnection conn = createConnection("/workflows", "POST");
        assertEquals(401, conn.getResponseCode(), "Specification upload requires authentication");

        // Verify WWW-Authenticate header is present
        String wwwAuth = conn.getHeaderField("WWW-Authenticate");
        assertNotNull(wwwAuth, "401 response should include WWW-Authenticate header");
        assertTrue(wwwAuth.contains("Bearer") || wwwAuth.contains("YAWL"),
            "WWW-Authenticate should mention YAWL or Bearer");

        conn.disconnect();
    }

    @Test
    @DisplayName("Specification Upload - Valid Request")
    void testSpecificationUploadSuccess() throws Exception {
        // Create test specification content
        String specContent = createTestSpecification();

        // Upload with valid API key
        HttpURLConnection conn = createAuthenticatedConnection(
            "/workflows",
            "POST",
            API_KEY_VALUE,
            specContent
        );

        assertEquals(200, conn.getResponseCode(), "Specification upload should succeed with valid API key");

        String response = readResponse(conn);
        assertTrue(response.contains("\"id\""), "Upload response should contain specification ID");
        assertTrue(response.contains("\"name\":\"" + TEST_SPEC_NAME + "\""),
            "Response should contain specification name");

        // Extract spec ID for later use
        String specId = extractSpecId(response);
        assertNotNull(specId, "Should extract valid specification ID");
    }

    @Test
    @DisplayName("Agent Discovery - List Available Workflows")
    void testAgentDiscoveryWorkflows() throws Exception {
        // First upload a specification
        uploadTestSpecification();

        // Query workflows with valid API key
        HttpURLConnection conn = createAuthenticatedConnection(
            "/workflows",
            "GET",
            API_KEY_VALUE,
            ""
        );

        assertEquals(200, conn.getResponseCode(), "Workflow query should succeed");

        String response = readResponse(conn);
        assertTrue(response.contains(TEST_SPEC_NAME), "Response should contain uploaded specification");
        assertTrue(response.contains("\"id\""), "Response should contain workflow IDs");
    }

    @Test
    @DisplayName("Case Creation - Authentication Required")
    void testCaseCreationRequiresAuthentication() throws Exception {
        // Create case without authentication should fail
        String requestBody = createCaseCreationRequest("case-123");

        HttpURLConnection conn = createConnection("/cases", "POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));

        assertEquals(401, conn.getResponseCode(), "Case creation requires authentication");
        conn.disconnect();
    }

    @Test
    @DisplayName("Case Creation - Success")
    void testCaseCreationSuccess() throws Exception {
        // First upload specification
        uploadTestSpecification();

        // Create case with valid API key
        String requestBody = createCaseCreationRequest("test-case-" + Instant.now().toEpochMilli());

        HttpURLConnection conn = createAuthenticatedConnection(
            "/cases",
            "POST",
            API_KEY_VALUE,
            requestBody
        );

        assertEquals(201, conn.getResponseCode(), "Case creation should succeed");

        String response = readResponse(conn);
        assertTrue(response.contains("\"id\""), "Response should contain case ID");
        assertTrue(response.contains("\"status\":\"created\""), "Case should be created with status 'created'");

        // Store case ID for cleanup
        testCaseId = extractCaseId(response);
        assertNotNull(testCaseId, "Should extract valid case ID");
    }

    @Test
    @DisplayName("Case Retrieval - Authentication Required")
    void testCaseRetrievalRequiresAuthentication() throws Exception {
        // First create a valid case
        testCaseCreationSuccess();

        // Try to retrieve case without authentication
        HttpURLConnection conn = createConnection(
            "/cases/" + testCaseId,
            "GET"
        );

        assertEquals(401, conn.getResponseCode(), "Case retrieval requires authentication");
        conn.disconnect();
    }

    @Test
    @DisplayName("Case Retrieval - Success")
    void testCaseRetrievalSuccess() throws Exception {
        // First create a valid case
        testCaseCreationSuccess();

        // Retrieve case with valid API key
        HttpURLConnection conn = createAuthenticatedConnection(
            "/cases/" + testCaseId,
            "GET",
            API_KEY_VALUE,
            ""
        );

        assertEquals(200, conn.getResponseCode(), "Case retrieval should succeed");

        String response = readResponse(conn);
        assertTrue(response.contains(testCaseId), "Response should contain correct case ID");
        assertTrue(response.contains("\"status\":\"created\""), "Case should have status 'created'");
    }

    @Test
    @DisplayName("Invalid API Key - All Operations Fail")
    void testInvalidApiKeyFailsAllOperations() throws Exception {
        // Try various operations with invalid API key
        String[] endpoints = {"/workflows", "/cases"};
        String[] methods = {"POST", "GET"};

        for (String endpoint : endpoints) {
            for (String method : methods) {
                HttpURLConnection conn = createAuthenticatedConnection(
                    endpoint,
                    method,
                    "invalid-api-key",
                    method.equals("POST") ? "{}" : ""
                );

                // Should fail with 401
                assertEquals(401, conn.getResponseCode(),
                    "Endpoint " + endpoint + " with invalid API key should return 401");
                conn.disconnect();
            }
        }
    }

    @Test
    @DisplayName("JWT Authentication - Valid Token")
    void testJwtAuthenticationValidToken() throws Exception {
        // Generate JWT token
        JwtAuthenticationProvider jwtProvider = new JwtAuthenticationProvider(JWT_SECRET, null);
        String token = jwtProvider.issueToken("test-agent",
            List.of(AuthenticatedPrincipal.PERM_ALL), 60_000L);

        // Use JWT token for workflow upload
        HttpURLConnection conn = createAuthenticatedConnection(
            "/workflows",
            "POST",
            null,
            createTestSpecification(),
            "Authorization",
            "Bearer " + token
        );

        assertEquals(200, conn.getResponseCode(), "JWT authentication should work for valid token");
        conn.disconnect();
    }

    @Test
    @DisplayName("JWT Authentication - Invalid Token")
    void testJwtAuthenticationInvalidToken() throws Exception {
        // Use invalid JWT token
        HttpURLConnection conn = createAuthenticatedConnection(
            "/workflows",
            "POST",
            null,
            "{}",
            "Authorization",
            "Bearer invalid.jwt.token.here"
        );

        assertEquals(401, conn.getResponseCode(), "JWT authentication should fail for invalid token");
        conn.disconnect();
    }

    @Test
    @DisplayName("Case Creation - Invalid Specification ID")
    void testCaseCreationInvalidSpec() throws Exception {
        // Try to create case with non-existent specification
        String requestBody = String.format(
            "{\"specificationId\":\"non-existent-spec\",\"caseName\":\"Test Case\"}"
        );

        HttpURLConnection conn = createAuthenticatedConnection(
            "/cases",
            "POST",
            API_KEY_VALUE,
            requestBody
        );

        // Should fail with appropriate error code (likely 404)
        assertTrue(conn.getResponseCode() >= 400,
            "Case creation with non-existent spec should fail");

        String response = readErrorResponse(conn);
        assertTrue(response.contains("error"), "Error response should contain error information");
    }

    @Test
    @DisplayName("Malformed JSON - Error Handling")
    void testMalformedJsonErrorHandling() throws Exception {
        // Send malformed JSON to workflow upload
        HttpURLConnection conn = createAuthenticatedConnection(
            "/workflows",
            "POST",
            API_KEY_VALUE,
            "{invalid json}"
        );

        assertEquals(400, conn.getResponseCode(), "Malformed JSON should return 400");

        String response = readErrorResponse(conn);
        assertTrue(response.contains("error"), "Should return error information");
        conn.disconnect();
    }

    @Test
    @DisplayName("Unknown Endpoint - Not Found")
    void testUnknownEndpointReturns404() throws Exception {
        // Request unknown endpoint
        HttpURLConnection conn = createAuthenticatedConnection(
            "/unknown/endpoint",
            "GET",
            API_KEY_VALUE,
            ""
        );

        assertEquals(404, conn.getResponseCode(), "Unknown endpoint should return 404");
        conn.disconnect();
    }

    @Test
    @DisplayName("Case Listing - Empty Results")
    void testCaseListingEmptyResults() throws Exception {
        // Case listing when no cases exist
        HttpURLConnection conn = createAuthenticatedConnection(
            "/cases",
            "GET",
            API_KEY_VALUE,
            ""
        );

        assertEquals(200, conn.getResponseCode(), "Case listing should return 200 even when empty");

        String response = readResponse(conn);
        assertTrue(response.contains("[]") || response.contains("\"cases\":[]"),
            "Empty case list should return empty array");
        conn.disconnect();
    }

    @Test
    @DisplayName("Case Listing - With Cases")
    void testCaseListingWithCases() throws Exception {
        // Create multiple cases
        String case1Id = createTestCase("case-1");
        String case2Id = createTestCase("case-2");

        try {
            // List cases
            HttpURLConnection conn = createAuthenticatedConnection(
                "/cases",
                "GET",
                API_KEY_VALUE,
                ""
            );

            assertEquals(200, conn.getResponseCode(), "Case listing should succeed");

            String response = readResponse(conn);
            assertTrue(response.contains(case1Id), "Response should contain first case ID");
            assertTrue(response.contains(case2Id), "Response should contain second case ID");
            assertTrue(response.contains("\"count\":2"), "Response should indicate 2 cases");

            conn.disconnect();
        } finally {
            // Clean up
            yawlEngine.removeCase(case1Id);
            yawlEngine.removeCase(case2Id);
        }
    }

    @Test
    @DisplayName("Server Health Check")
    void testServerHealthCheck() throws Exception {
        // Health check should be public
        HttpURLConnection conn = createConnection("/health", "GET");
        assertEquals(200, conn.getResponseCode(), "Health check should return 200");

        String response = readResponse(conn);
        assertTrue(response.contains("status"), "Health response should contain status");
        assertTrue(response.contains("healthy"), "Healthy server should return healthy status");
        conn.disconnect();
    }

    @Test
    @DisplayName("Server Info - Public Access")
    void testServerInfoPublic() throws Exception {
        // Server info should be public
        HttpURLConnection conn = createConnection("/info", "GET");
        assertEquals(200, conn.getResponseCode(), "Server info should be public");

        String response = readResponse(conn);
        assertTrue(response.contains("version"), "Response should contain version info");
        assertTrue(response.contains("YAWL"), "Response should mention YAWL");
        conn.disconnect();
    }

    // Helper methods

    private void setupAuthentication() throws Exception {
        // Ensure authentication providers are set up properly
        assertNotNull(createAuthenticationProvider(), "Authentication provider should be created");
    }

    private CompositeAuthenticationProvider createAuthenticationProvider() {
        ApiKeyAuthenticationProvider apiKeyProvider = new ApiKeyAuthenticationProvider(API_KEY_MASTER);
        apiKeyProvider.registerKey("test-agent", "test-user", API_KEY_VALUE,
            Set.of(AuthenticatedPrincipal.PERM_ALL));

        JwtAuthenticationProvider jwtProvider =
            new JwtAuthenticationProvider(JWT_SECRET, null);

        return new CompositeAuthenticationProvider(jwtProvider, apiKeyProvider);
    }

    private String createTestSpecification() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<specification xmlns=\"http://www.yawlfoundation.org/yawl\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.yawlfoundation.org/yawl http://www.yawlfoundation.org/yawl/schema/YAWL_Schema4.0.xsd\">\n" +
            "  <header>\n" +
            "    <specificationName>" + TEST_SPEC_NAME + "</specificationName>\n" +
            "    <specificationVersion>1.0</specificationVersion>\n" +
            "    <creator>YAWL Test Suite</creator>\n" +
            "    <description>A simple test specification for A2A capability testing</description>\n" +
            "    <version>1.0</version>\n" +
            "    <dateCreated>2024-01-01T00:00:00</dateCreated>\n" +
            "  </header>\n" +
            "  <schema>\n" +
            "    <inputParameters></inputParameters>\n" +
            "    <outputParameters></outputParameters>\n" +
            "    <variables></variables>\n" +
            "    <net id=\"net_1\">\n" +
            "      <inputCondition id=\"start\">\n" +
            "        <predicate>\n" +
            "          <expression>true</expression>\n" +
            "        </predicate>\n" +
            "      </inputCondition>\n" +
            "      <edges>\n" +
            "        <flowCondition id=\"flow_1\">\n" +
            "          <sourceRef>start</sourceRef>\n" +
            "          <targetRef>Task_1</targetRef>\n" +
            "        </flowCondition>\n" +
            "      </edges>\n" +
            "      <places>\n" +
            "        <place id=\"p_1\">\n" +
            "          <edges>\n" +
            "            <flowCondition id=\"flow_2\">\n" +
            "              <sourceRef>p_1</sourceRef>\n" +
            "              <targetRef>Task_1</targetRef>\n" +
            "            </flowCondition>\n" +
            "          </edges>\n" +
            "        </place>\n" +
            "        <place id=\"p_2\">\n" +
            "          <edges>\n" +
            "            <flowCondition id=\"flow_3\">\n" +
            "              <sourceRef>Task_1</sourceRef>\n" +
            "              <targetRef>p_2</targetRef>\n" +
            "            </flowCondition>\n" +
            "          </edges>\n" +
            "        </place>\n" +
            "      </places>\n" +
            "      <tasks>\n" +
            "        <task id=\"Task_1\" name=\"Simple Task\">\n" +
            "          <inputCondition id=\"input_1\">\n" +
            "            <predicate>\n" +
            "              <expression>true</expression>\n" +
            "            </predicate>\n" +
            "          </inputCondition>\n" +
            "          <outputCondition id=\"output_1\">\n" +
            "            <predicate>\n" +
            "              <expression>true</expression>\n" +
            "            </predicate>\n" +
            "          </outputCondition>\n" +
            "          <edges>\n" +
            "            <flowCondition id=\"flow_4\">\n" +
            "              <sourceRef>Task_1</sourceRef>\n" +
            "              <targetRef>p_2</targetRef>\n" +
            "            </flowCondition>\n" +
            "          </edges>\n" +
            "        </task>\n" +
            "      </tasks>\n" +
            "      <outputCondition id=\"end\">\n" +
            "        <predicate>\n" +
            "          <expression>true</expression>\n" +
            "        </predicate>\n" +
            "      </outputCondition>\n" +
            "    </net>\n" +
            "  </schema>\n" +
            "</specification>";
    }

    private void uploadTestSpecification() throws Exception {
        String specContent = createTestSpecification();

        HttpURLConnection conn = createAuthenticatedConnection(
            "/workflows",
            "POST",
            API_KEY_VALUE,
            specContent
        );

        assertEquals(200, conn.getResponseCode(), "Test specification upload should succeed");
        conn.disconnect();
    }

    private String createCaseCreationRequest(String caseName) {
        return String.format(
            "{\"specificationId\":\"%s\",\"caseName\":\"%s\"}",
            TEST_SPEC_NAME,
            caseName
        );
    }

    private String createTestCase(String caseName) throws Exception {
        String requestBody = createCaseCreationRequest(caseName);

        HttpURLConnection conn = createAuthenticatedConnection(
            "/cases",
            "POST",
            API_KEY_VALUE,
            requestBody
        );

        assertEquals(201, conn.getResponseCode(), "Test case creation should succeed");

        String response = readResponse(conn);
        String caseId = extractCaseId(response);
        assertNotNull(caseId, "Should extract valid case ID");

        conn.disconnect();
        return caseId;
    }

    private HttpURLConnection createConnection(String path, String method) throws Exception {
        URL url = new URL("http://localhost:" + TEST_PORT + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        return conn;
    }

    private HttpURLConnection createAuthenticatedConnection(String path, String method,
            String apiKey, String body) throws Exception {
        return createAuthenticatedConnection(path, method, apiKey, body, "X-API-Key", apiKey);
    }

    private HttpURLConnection createAuthenticatedConnection(String path, String method,
            String apiKey, String body, String headerName, String headerValue) throws Exception {
        HttpURLConnection conn = createConnection(path, method);

        if (apiKey != null) {
            conn.setRequestProperty(headerName, headerValue);
        }

        if (!body.isEmpty() && !method.equals("GET")) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }

        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        if (conn.getResponseCode() >= 400) {
            return readErrorResponse(conn);
        }

        try (var in = conn.getInputStream();
             var reader = new java.io.BufferedReader(
                 new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining());
        } finally {
            conn.disconnect();
        }
    }

    private String readErrorResponse(HttpURLConnection conn) throws Exception {
        try (var in = conn.getErrorStream();
             var reader = new java.io.BufferedReader(
                 new java.io.InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining());
        } finally {
            conn.disconnect();
        }
    }

    private void assertAgentCardValid(String response) {
        assertTrue(response.contains("\"name\":\"YAWL-A2A-Capability-Test\""),
            "Agent card should contain correct name");
        assertTrue(response.contains("\"version\":\"5.2.0\""),
            "Agent card should contain version");
        assertTrue(response.contains("\"capabilities\""),
            "Agent card should contain capabilities");
        assertTrue(response.contains("\"skills\""),
            "Agent card should contain skills");
    }

    private String extractSpecId(String response) {
        // Simple extraction - in real implementation would use JSON parsing
        int idIndex = response.indexOf("\"id\":\"");
        if (idIndex >= 0) {
            idIndex += 6;
            int endIndex = response.indexOf("\"", idIndex);
            return response.substring(idIndex, endIndex);
        }
        return null;
    }

    private String extractCaseId(String response) {
        // Simple extraction - in real implementation would use JSON parsing
        int idIndex = response.indexOf("\"id\":\"");
        if (idIndex >= 0) {
            idIndex += 6;
            int endIndex = response.indexOf("\"", idIndex);
            return response.substring(idIndex, endIndex);
        }
        return null;
    }
}