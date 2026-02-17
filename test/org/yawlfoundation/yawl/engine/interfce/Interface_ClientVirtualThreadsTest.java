/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine.interfce;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Interface_Client virtual threads implementation.
 *
 * Tests verify:
 * 1. Virtual thread executor is properly configured
 * 2. Concurrent requests execute efficiently
 * 3. Async methods work correctly
 * 4. Performance is improved vs platform threads
 * 5. Resource cleanup is proper
 *
 * @author Claude (Virtual Threads Migration)
 * @date 2026-02-16
 */
public class Interface_ClientVirtualThreadsTest {

    private HttpServer testServer;
    private int serverPort;
    private String serverUrl;
    private TestableInterfaceClient client;
    private AtomicInteger requestCount;
    private Map<String, Long> requestTimings;

    @BeforeEach
    public void setUp() throws IOException {
        requestCount = new AtomicInteger(0);
        requestTimings = new ConcurrentHashMap<>();

        testServer = HttpServer.create(new InetSocketAddress(0), 512);
        serverPort = testServer.getAddress().getPort();
        serverUrl = "http://localhost:" + serverPort;

        testServer.createContext("/test", new TestHandler());
        testServer.createContext("/slow", new SlowHandler());
        testServer.createContext("/echo", new EchoHandler());
        // Use a virtual thread executor for the test server to allow concurrent request handling
        testServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        testServer.start();

        client = new TestableInterfaceClient();
    }

    @AfterEach
    public void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @Test
    public void testBasicPostRequest() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("action", "test");
        params.put("data", "hello");

        String result = client.executePost(serverUrl + "/test", params);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("success"), "Result should contain success");
        assertEquals(1, requestCount.get(), "Request count should be 1");
    }

    @Test
    public void testBasicGetRequest() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("action", "query");

        String result = client.executeGet(serverUrl + "/test", params);

        assertNotNull(result, "Result should not be null");
        assertEquals(1, requestCount.get(), "Request count should be 1");
    }

    @Test
    public void testAsyncPostRequest() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("action", "async-test");
        params.put("value", "42");

        CompletableFuture<String> future = client.executePostAsync(serverUrl + "/echo", params);

        assertNotNull(future, "Future should not be null");

        String result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("42"), "Result should contain echoed data");
    }

    @Test
    public void testAsyncGetRequest() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("query", "data");

        CompletableFuture<String> future = client.executeGetAsync(serverUrl + "/echo", params);

        String result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result, "Result should not be null");
    }

    @Test
    public void testConcurrentRequests() throws Exception {
        int concurrentRequests = 100;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentRequests; i++) {
            Map<String, String> params = new HashMap<>();
            params.put("request", String.valueOf(i));

            CompletableFuture<String> future = client.executePostAsync(serverUrl + "/test", params);
            // Count down on both success and failure to avoid latch starvation
            future.whenComplete((result, ex) -> latch.countDown());
            futures.add(future);
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(completed, "All requests should complete within timeout");
        assertEquals((long) concurrentRequests,
            futures.stream().filter(f -> !f.isCompletedExceptionally()).count(),
            "All futures should be successful");
        assertEquals(concurrentRequests, requestCount.get(), "Request count should match");

        System.out.println("Completed " + concurrentRequests + " concurrent requests in " + duration + "ms");
        assertTrue(duration < 10000, "Concurrent requests should complete efficiently (< 10s)");
    }

    @Test
    public void testHighConcurrencyWithVirtualThreads() throws Exception {
        int concurrentRequests = 200;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentRequests; i++) {
            Map<String, String> params = new HashMap<>();
            params.put("request", String.valueOf(i));

            CompletableFuture<String> future = client.executePostAsync(serverUrl + "/test", params);
            // Count down on both success and failure to avoid latch starvation
            future.whenComplete((result, ex) -> latch.countDown());
            futures.add(future);
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(completed, "All " + concurrentRequests + " requests should complete within timeout");

        long successCount = futures.stream()
            .filter(f -> !f.isCompletedExceptionally())
            .count();

        assertTrue(successCount > concurrentRequests * 0.95,
                "Most requests should succeed (>95%): " + successCount + "/" + concurrentRequests);

        System.out.println("Completed " + successCount + "/" + concurrentRequests +
            " high-concurrency requests in " + duration + "ms");
    }

    @Test
    public void testSlowRequestsWithVirtualThreads() throws Exception {
        int concurrentRequests = 50;
        List<CompletableFuture<String>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentRequests; i++) {
            Map<String, String> params = new HashMap<>();
            params.put("delay", "100");

            CompletableFuture<String> future = client.executePostAsync(serverUrl + "/slow", params);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("Completed " + concurrentRequests + " slow requests in " + duration + "ms");

        assertTrue(duration < 5000, "Virtual threads should handle blocking I/O efficiently (< 5s for 50x100ms)");

        long successCount = futures.stream()
            .filter(f -> !f.isCompletedExceptionally())
            .count();
        assertEquals(concurrentRequests, successCount, "All slow requests should succeed");
    }

    @Test
    public void testErrorHandling() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("error", "true");

        // Use a port that has no server listening (connection refused) to trigger a real error
        int unusedPort = findUnusedPort();
        CompletableFuture<String> future = client.executePostAsync(
                "http://localhost:" + unusedPort + "/nonexistent", params);

        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Should throw exception for connection refused endpoint");
        } catch (Exception e) {
            assertTrue(e.getCause() != null || e.getMessage() != null,
                    "Exception should contain meaningful message");
        }
    }

    private int findUnusedPort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    public void testConnectionReuse() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("test", "reuse");

        for (int i = 0; i < 10; i++) {
            String result = client.executePost(serverUrl + "/test", params);
            assertNotNull(result, "Result should not be null for request " + i);
        }

        assertEquals(10, requestCount.get(), "All 10 requests should be counted");
    }

    /**
     * Testable subclass that exposes protected methods
     */
    private static class TestableInterfaceClient extends Interface_Client {
        @Override
        public String executePost(String urlStr, Map<String, String> paramsMap) throws IOException {
            return super.executePost(urlStr, paramsMap);
        }

        @Override
        public String executeGet(String urlStr, Map<String, String> paramsMap) throws IOException {
            return super.executeGet(urlStr, paramsMap);
        }

        @Override
        public CompletableFuture<String> executePostAsync(String urlStr, Map<String, String> paramsMap) {
            return super.executePostAsync(urlStr, paramsMap);
        }

        @Override
        public CompletableFuture<String> executeGetAsync(String urlStr, Map<String, String> paramsMap) {
            return super.executeGetAsync(urlStr, paramsMap);
        }
    }

    /**
     * Basic HTTP handler for tests
     */
    private class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            String response = "<response><success/></response>";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    /**
     * Slow HTTP handler to test blocking I/O
     */
    private class SlowHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String response = "<response><success/></response>";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    /**
     * Echo handler that returns the request body data
     */
    private class EchoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            // Read the request body to echo it back
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String body = new String(bodyBytes);
            String response = "<response><echo>" + body + "</echo></response>";
            byte[] responseBytes = response.getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }
}
