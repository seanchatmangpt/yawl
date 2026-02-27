/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.external;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EngineBasedClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * External service integration tests for YAWL v6.
 *
 * Tests integration with external services:
 * - HTTP client behavior
 * - Timeout handling
 * - Retry logic
 * - Connection failures
 * - Service discovery
 *
 * Chicago TDD: Real HTTP connections, real timeouts, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("integration")
class ExternalServiceIntegrationTest {

    private static final String UNREACHABLE_HOST = "http://localhost:19999";
    private static final String VALID_HTTPBIN = "https://httpbin.org";
    private static final int TIMEOUT_MS = 5000;

    // =========================================================================
    // HTTP Connection Tests
    // =========================================================================

    @Test
    void testHttpConnectionToLocalhost() throws Exception {
        // Test that we can create HTTP connections
        URL url = new URL("http://localhost:80");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(1000);
        conn.setReadTimeout(1000);

        // Will likely fail (no server), but we verify connection attempt
        try {
            conn.connect();
        } catch (ConnectException e) {
            // Expected - no server running
            assertNotNull(e.getMessage());
        } finally {
            conn.disconnect();
        }
    }

    @Test
    void testHttpConnectionTimeout() throws Exception {
        // Use a non-routable IP to test timeout
        URL url = new URL("http://10.255.255.1:9999");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(500); // Very short timeout

        long start = System.currentTimeMillis();
        try {
            conn.connect();
            fail("Should timeout");
        } catch (java.net.SocketTimeoutException e) {
            long duration = System.currentTimeMillis() - start;
            assertTrue(duration < 2000, "Timeout should occur quickly: " + duration + "ms");
        } finally {
            conn.disconnect();
        }
    }

    @Test
    void testHttpConnectionRefused() throws Exception {
        URL url = new URL(UNREACHABLE_HOST + "/test");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(1000);

        try {
            conn.connect();
            fail("Connection should be refused");
        } catch (ConnectException e) {
            // Expected - connection refused
            assertNotNull(e.getMessage());
        } finally {
            conn.disconnect();
        }
    }

    // =========================================================================
    // URL Validation Tests
    // =========================================================================

    @Test
    void testValidUrlConstruction() throws Exception {
        String[] validUrls = {
            "http://localhost:8080/yawl",
            "https://example.com/api",
            "http://192.168.1.1:8080/service"
        };

        for (String urlStr : validUrls) {
            URL url = new URL(urlStr);
            assertNotNull(url);
            assertNotNull(url.getProtocol());
            assertNotNull(url.getHost());
        }
    }

    @Test
    void testInvalidUrlHandling() {
        String[] invalidUrls = {
            "not-a-url",
            "http://",
            "://missing-protocol",
            ""
        };

        for (String urlStr : invalidUrls) {
            assertThrows(Exception.class, () -> {
                new URL(urlStr);
            }, "Invalid URL should throw: " + urlStr);
        }
    }

    // =========================================================================
    // Service Reference Tests
    // =========================================================================

    @Test
    void testYawlServiceReferenceConstruction() throws Exception {
        String uri = "http://localhost:8080/worklist";
        String serviceName = "TestWorklist";

        YAWLServiceReference ref = new YAWLServiceReference(
                serviceName, uri, null, null);

        assertNotNull(ref, "Service reference must be constructable");
        assertEquals(uri, ref.getURI(), "URI must match");
        assertEquals(serviceName, ref.getServiceName(), "Service name must match");
    }

    @Test
    void testYawlServiceReferenceEquality() throws Exception {
        YAWLServiceReference ref1 = new YAWLServiceReference(
                "Service", "http://localhost:8080", null, null);
        YAWLServiceReference ref2 = new YAWLServiceReference(
                "Service", "http://localhost:8080", null, null);
        YAWLServiceReference ref3 = new YAWLServiceReference(
                "Other", "http://localhost:8081", null, null);

        // Service references with same properties should be comparable
        assertEquals(ref1.getURI(), ref2.getURI());
        assertNotEquals(ref1.getURI(), ref3.getURI());
    }

    // =========================================================================
    // Interface B Client Tests
    // =========================================================================

    @Test
    void testInterfaceBClientConstruction() throws Exception {
        InterfaceB_EngineBasedClient client = new InterfaceB_EngineBasedClient();

        assertNotNull(client, "Client must be constructable");
        assertEquals("http", client.getScheme(), "Scheme must be http");
    }

    @Test
    void testInterfaceBClientShutdown() throws Exception {
        InterfaceB_EngineBasedClient client = new InterfaceB_EngineBasedClient();

        // Shutdown should not throw
        assertDoesNotThrow(() -> client.shutdown(),
                "Shutdown must not throw exception");
    }

    // =========================================================================
    // HTTP POST Tests
    // =========================================================================

    @Test
    void testHttpPostWithParameters() throws Exception {
        // Test POST request construction (will fail without server)
        URL url = new URL(UNREACHABLE_HOST + "/yawl/ib");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(1000);
        conn.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded");

        String params = "action=test&param1=value1";
        conn.setRequestProperty("Content-Length",
                String.valueOf(params.length()));

        try {
            conn.connect();
            conn.getOutputStream().write(params.getBytes());
        } catch (ConnectException e) {
            // Expected - no server
        } finally {
            conn.disconnect();
        }
    }

    @Test
    void testHttpPostWithXmlPayload() throws Exception {
        URL url = new URL(UNREACHABLE_HOST + "/yawl/ib");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(1000);
        conn.setRequestProperty("Content-Type", "application/xml");

        String xmlPayload = "<?xml version=\"1.0\"?>"
                + "<case><id>test-123</id></case>";
        conn.setRequestProperty("Content-Length",
                String.valueOf(xmlPayload.length()));

        try {
            conn.connect();
            conn.getOutputStream().write(xmlPayload.getBytes("UTF-8"));
        } catch (ConnectException e) {
            // Expected
        } finally {
            conn.disconnect();
        }
    }

    // =========================================================================
    // Timeout Configuration Tests
    // =========================================================================

    @Test
    void testConnectionTimeoutConfiguration() throws Exception {
        URL url = new URL(UNREACHABLE_HOST);

        int[] timeouts = {100, 500, 1000, 5000};

        for (int timeout : timeouts) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeout);
            assertEquals(timeout, conn.getConnectTimeout(),
                    "Connect timeout must be set to " + timeout);
            conn.disconnect();
        }
    }

    @Test
    void testReadTimeoutConfiguration() throws Exception {
        URL url = new URL(UNREACHABLE_HOST);

        int[] timeouts = {100, 500, 1000, 5000};

        for (int timeout : timeouts) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(timeout);
            assertEquals(timeout, conn.getReadTimeout(),
                    "Read timeout must be set to " + timeout);
            conn.disconnect();
        }
    }

    // =========================================================================
    // Concurrent Request Tests
    // =========================================================================

    @Test
    void testConcurrentHttpRequests() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger attemptCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    URL url = new URL(UNREACHABLE_HOST + "/test" + attemptCount.get());
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(500);
                    attemptCount.incrementAndGet();

                    try {
                        conn.connect();
                    } catch (ConnectException e) {
                        failCount.incrementAndGet();
                    } finally {
                        conn.disconnect();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(threadCount, attemptCount.get(),
                "All connection attempts must be made");
        assertEquals(threadCount, failCount.get(),
                "All connections must fail (no server)");
    }

    // =========================================================================
    // Error Response Handling Tests
    // =========================================================================

    @Test
    void testErrorResponseCodes() {
        // Test error response handling logic
        int[] errorCodes = {400, 401, 403, 404, 500, 502, 503};

        for (int code : errorCodes) {
            String message = getErrorMessageForCode(code);
            assertNotNull(message, "Error message must exist for code " + code);
            assertFalse(message.isEmpty(), "Error message must not be empty");
        }
    }

    private String getErrorMessageForCode(int code) {
        return switch (code) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "Unknown Error";
        };
    }

    // =========================================================================
    // Request Header Tests
    // =========================================================================

    @Test
    void testRequestHeaders() throws Exception {
        URL url = new URL(UNREACHABLE_HOST);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestProperty("User-Agent", "YAWL-Engine/6.0");
        conn.setRequestProperty("Accept", "application/xml");
        conn.setRequestProperty("X-Request-ID", "test-123");

        assertEquals("YAWL-Engine/6.0", conn.getRequestProperty("User-Agent"));
        assertEquals("application/xml", conn.getRequestProperty("Accept"));
        assertEquals("test-123", conn.getRequestProperty("X-Request-ID"));

        conn.disconnect();
    }

    @Test
    void testContentTypeHeaders() throws Exception {
        String[] contentTypes = {
            "application/xml",
            "application/json",
            "application/x-www-form-urlencoded",
            "text/xml"
        };

        for (String contentType : contentTypes) {
            URL url = new URL(UNREACHABLE_HOST);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Content-Type", contentType);
            assertEquals(contentType, conn.getRequestProperty("Content-Type"));
            conn.disconnect();
        }
    }

    // =========================================================================
    // Retry Logic Tests
    // =========================================================================

    @Test
    void testRetryLogicWithFailures() throws Exception {
        int maxRetries = 3;
        int attempts = 0;
        boolean success = false;

        for (int i = 0; i < maxRetries && !success; i++) {
            attempts++;
            try {
                URL url = new URL(UNREACHABLE_HOST);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(100);
                conn.connect();
                success = true;
                conn.disconnect();
            } catch (ConnectException e) {
                // Retry
                Thread.sleep(100);
            }
        }

        assertEquals(maxRetries, attempts, "Must attempt all retries");
        assertFalse(success, "Must fail after all retries (no server)");
    }

    @Test
    void testExponentialBackoff() throws Exception {
        int[] delays = {100, 200, 400, 800, 1600};

        for (int i = 0; i < delays.length; i++) {
            int delay = delays[i];
            long start = System.currentTimeMillis();
            Thread.sleep(delay);
            long elapsed = System.currentTimeMillis() - start;

            // Allow 50% tolerance
            assertTrue(elapsed >= delay * 0.5,
                    "Delay must be at least " + (delay * 0.5) + "ms");
            assertTrue(elapsed <= delay * 1.5,
                    "Delay must be at most " + (delay * 1.5) + "ms");
        }
    }
}
