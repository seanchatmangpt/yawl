/*
 * Copyright (c) 2024-2025 YAWL Foundation
 *
 * This file is part of YAWL v6.0.0-GA.
 *
 * YAWL v6.0.0-GA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL v6.0.0-GA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL v6.0.0-GA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.integration.java_python.interoperability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive A2A (Agent-to-Agent) protocol validation tests.
 * Validates compliance with A2A interface requirements and Java-Python interoperability.
 *
 * @author YAWL Foundation
 * @since v6.0.0-GA
 */
@EnabledIfEnvironmentVariable(named = "GRAALPY_AVAILABLE", matches = "true")
public class A2AValidationTest extends ValidationTestBase {

    private static final String A2A_BASE_URL = "http://localhost:8080";
    private static final int TIMEOUT_MS = 10000;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(graalpyAvailable, "GraalPy required for A2A validation");

        // Ensure A2A service is running
        if (!isA2AServiceRunning()) {
            // Start A2A service if not running
            startA2AService();
            Thread.sleep(2000); // Give service time to start
        }
    }

    @Test
    @Order(1)
    @DisplayName("Agent Card Endpoint Returns 200")
    void testAgentCardEndpointReturns200() throws Exception {
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/.well-known/agent.json");
        assertEquals(200, conn.getResponseCode(), "Agent card endpoint should return 200");

        String response = readResponse(conn);
        assertThat(response, not(emptyString()));
        assertThat(response, containsString("@context"));
        assertThat(response, containsString("type"));
        assertThat(response, containsString("name"));
    }

    @Test
    @Order(2)
    @DisplayName("Agent Card Schema Validation")
    void testAgentCardSchemaValidation() throws Exception {
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/.well-known/agent.json");
        assertEquals(200, conn.getResponseCode());

        String response = readResponse(conn);

        // Validate required fields
        assertThat(response, containsString("\"@context\": \"https://schema.yawlfoundation.org/agent-1.0\""));
        assertThat(response, containsString("\"type\": \"YAWLAgent\""));
        assertThat(response, containsString("\"name\": \"YAWL-Service\""));
        assertThat(response, containsString("\"skills\":"));
        assertThat(response, containsString("\"capabilities\":"));
    }

    @Test
    @Order(3)
    @DisplayName("Health Check Endpoint Returns 200")
    void testHealthCheckEndpointReturns200() throws Exception {
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/health");
        assertEquals(200, conn.getResponseCode(), "Health check should return 200");

        String response = readResponse(conn);
        assertThat(response, containsString("status"));
        assertThat(response, containsString("healthy"));
    }

    @Test
    @Order(4)
    @DisplayName("Skills Endpoint Returns 200")
    void testSkillsEndpointReturns200() throws Exception {
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/skills");
        assertEquals(200, conn.getResponseCode(), "Skills endpoint should return 200");

        String response = readResponse(conn);
        assertThat(response, not(emptyString()));
        assertThat(response, containsString("skills"));
        assertThat(response, containsString("description"));
    }

    @Test
    @Order(5)
    @DisplayName("Execute Skill Endpoint Returns 200")
    void testExecuteSkillEndpointReturns200() throws Exception {
        // Create skill input
        String skillInput = "{\"workflow_id\": \"test-workflow\", \"parameters\": {}}";

        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/execute/test-skill");
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(skillInput.getBytes());

        int responseCode = conn.getResponseCode();
        assertThat(responseCode, anyOf(equalTo(200), equalTo(201)));

        String response = readResponse(conn);
        assertThat(response, not(emptyString()));
    }

    @Test
    @Order(6)
    @DisplayName("Event Stream Endpoint Returns 200")
    void testEventStreamEndpointReturns200() throws Exception {
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/events");
        conn.setRequestProperty("Accept", "text/event-stream");

        int responseCode = conn.getResponseCode();
        assertThat(responseCode, anyOf(equalTo(200), equalTo(206)));

        // Read first event
        String line;
        boolean eventReceived = false;
        while ((line = conn.getInputStream().readLine()) != null) {
            if (line.contains("data:")) {
                eventReceived = true;
                break;
            }
        }

        assertTrue(eventReceived, "Should receive SSE events");
    }

    @Test
    @Order(7)
    @DisplayName("Skill Parameter Validation")
    void testSkillParameterValidation() throws Exception {
        // Test with invalid parameters
        String invalidInput = "{\"invalid\": \"data\"}";

        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/execute/test-skill");
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(invalidInput.getBytes());

        int responseCode = conn.getResponseCode();
        assertThat(responseCode, anyOf(equalTo(400), equalTo(422)));

        String response = readResponse(conn);
        assertThat(response, containsString("error"));
    }

    @Test
    @Order(8)
    @DisplayName("CORS Headers Present")
    void testCORSHeadersPresent() throws Exception {
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/.well-known/agent.json");
        conn.setRequestMethod("OPTIONS");

        int responseCode = conn.getResponseCode();
        assertThat(responseCode, equalTo(200));

        String corsHeader = conn.getHeaderField("Access-Control-Allow-Origin");
        assertThat(corsHeader, notNullValue());
        assertThat(corsHeader, anyOf(equalTo("*"), containsString("localhost")));
    }

    @Test
    @Order(9)
    @DisplayName("Rate Limiting Headers")
    void testRateLimitingHeaders() throws Exception {
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/health");

        String rateLimit = conn.getHeaderField("X-RateLimit-Limit");
        String rateRemaining = conn.getHeaderField("X-RateLimit-Remaining");
        String rateReset = conn.getHeaderField("X-RateLimit-Reset");

        assertThat(rateLimit, notNullValue());
        assertThat(rateRemaining, notNullValue());
        assertThat(rateReset, notNullValue());

        // Validate numeric values
        assertDoesNotThrow(() -> Integer.parseInt(rateLimit));
        assertDoesNotThrow(() -> Integer.parseInt(rateRemaining));
        assertDoesNotThrow(() -> Long.parseLong(rateReset));
    }

    @Test
    @Order(10)
    @DisplayName("Content-Type Validation")
    void testContentTypeValidation() throws Exception {
        // Test with valid content types
        String[] contentTypes = {
            "application/json",
            "application/ld+json",
            "text/plain"
        };

        for (String contentType : contentTypes) {
            HttpURLConnection conn = createConnection(A2A_BASE_URL + "/skills");
            conn.setRequestProperty("Accept", contentType);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                // Some content types might not be supported, but service should handle gracefully
                assertThat(responseCode, anyOf(equalTo(200), equalTo(400), equalTo(415)));
            }
        }
    }

    @Test
    @Order(11)
    @DisplayName("Authentication Header Requirement")
    void testAuthenticationHeaderRequirement() throws Exception {
        // Test endpoint without authentication
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/secure-data");
        conn.setRequestProperty("Authorization", "Bearer invalid-token");

        int responseCode = conn.getResponseCode();
        assertThat(responseCode, anyOf(equalTo(401), equalTo(403)));
    }

    @Test
    @Order(12)
    @DisplayName("Performance Baseline: Response Time")
    void testPerformanceBaseline() throws Exception {
        long startTime = System.currentTimeMillis();

        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/health");
        conn.getResponseCode();

        long responseTime = System.currentTimeMillis() - startTime;
        assertThat("Response time should be under 100ms", responseTime, lessThan(100L));
    }

    @Test
    @Order(13)
    @DisplayName("Concurrent Requests Handling")
    void testConcurrentRequests() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Integer>> futures = new ArrayList<>();

        // Submit concurrent requests
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                HttpURLConnection conn = createConnection(A2A_BASE_URL + "/health");
                latch.countDown();
                latch.await(); // Wait for all threads to be ready
                return conn.getResponseCode();
            }));
        }

        // Wait for all requests to complete
        List<Integer> responses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            responses.add(future.get(5, TimeUnit.SECONDS));
        }

        // Validate all requests succeeded
        for (int response : responses) {
            assertThat("Concurrent request should succeed", response, equalTo(200));
        }

        executor.shutdown();
    }

    @Test
    @Order(14)
    @DisplayName("Error Response Format")
    void testErrorResponseFormat() throws Exception {
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/nonexistent-endpoint");

        int responseCode = conn.getResponseCode();
        assertThat(responseCode, anyOf(equalTo(404), equalTo(400)));

        String response = readResponse(conn);
        assertThat(response, containsString("error"));
        assertThat(response, containsString("message"));
    }

    @Test
    @Order(15)
    @DisplayName("Version Information")
    void testVersionInformation() throws Exception {
        HttpURLConnection conn = createConnection(A2A_BASE_URL + "/version");

        int responseCode = conn.getResponseCode();
        assertThat(responseCode, equalTo(200));

        String response = readResponse(conn);
        assertThat(response, containsString("version"));
        assertThat(response, containsString("build"));
        assertThat(response, containsString("timestamp"));
    }

    // Helper methods

    private HttpURLConnection createConnection(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        if (conn.getResponseCode() >= 400) {
            return new String(conn.getErrorStream().readAllBytes());
        } else {
            return new String(conn.getInputStream().readAllBytes());
        }
    }

    private boolean isA2AServiceRunning() throws Exception {
        try {
            HttpURLConnection conn = createConnection(A2A_BASE_URL + "/health");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void startA2AService() throws Exception {
        // This would typically start the A2A service
        // For now, we'll assume it's managed externally
        printWarning("A2A service startup not implemented - assuming service is running externally");
    }

    private void printWarning(String message) {
        System.out.println("[WARNING] " + message);
    }
}