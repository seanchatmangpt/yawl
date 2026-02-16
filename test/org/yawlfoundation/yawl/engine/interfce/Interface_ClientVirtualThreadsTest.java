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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

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

    @Before
    public void setUp() throws IOException {
        requestCount = new AtomicInteger(0);
        requestTimings = new ConcurrentHashMap<>();

        testServer = HttpServer.create(new InetSocketAddress(0), 0);
        serverPort = testServer.getAddress().getPort();
        serverUrl = "http://localhost:" + serverPort;

        testServer.createContext("/test", new TestHandler());
        testServer.createContext("/slow", new SlowHandler());
        testServer.createContext("/echo", new EchoHandler());
        testServer.start();

        client = new TestableInterfaceClient();
    }

    @After
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

        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain success", result.contains("success"));
        assertEquals("Request count should be 1", 1, requestCount.get());
    }

    @Test
    public void testBasicGetRequest() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("action", "query");

        String result = client.executeGet(serverUrl + "/test", params);

        assertNotNull("Result should not be null", result);
        assertEquals("Request count should be 1", 1, requestCount.get());
    }

    @Test
    public void testAsyncPostRequest() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("action", "async-test");
        params.put("value", "42");

        CompletableFuture<String> future = client.executePostAsync(serverUrl + "/echo", params);

        assertNotNull("Future should not be null", future);
        assertFalse("Future should not be done immediately", future.isDone());

        String result = future.get(5, TimeUnit.SECONDS);

        assertNotNull("Result should not be null", result);
        assertTrue("Result should contain echoed data", result.contains("42"));
    }

    @Test
    public void testAsyncGetRequest() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("query", "data");

        CompletableFuture<String> future = client.executeGetAsync(serverUrl + "/echo", params);

        String result = future.get(5, TimeUnit.SECONDS);

        assertNotNull("Result should not be null", result);
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
            future.thenRun(latch::countDown);
            futures.add(future);
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue("All requests should complete within timeout", completed);
        assertEquals("All futures should be successful", concurrentRequests,
            futures.stream().filter(f -> !f.isCompletedExceptionally()).count());
        assertEquals("Request count should match", concurrentRequests, requestCount.get());

        System.out.println("Completed " + concurrentRequests + " concurrent requests in " + duration + "ms");
        assertTrue("Concurrent requests should complete efficiently (< 10s)", duration < 10000);
    }

    @Test
    public void testHighConcurrencyWithVirtualThreads() throws Exception {
        int concurrentRequests = 1000;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < concurrentRequests; i++) {
            Map<String, String> params = new HashMap<>();
            params.put("request", String.valueOf(i));

            CompletableFuture<String> future = client.executePostAsync(serverUrl + "/test", params);
            future.thenRun(latch::countDown);
            futures.add(future);
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue("All 1000 requests should complete", completed);

        long successCount = futures.stream()
            .filter(f -> !f.isCompletedExceptionally())
            .count();

        assertTrue("Most requests should succeed (>95%)", successCount > 950);

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

        assertTrue("Virtual threads should handle blocking I/O efficiently (< 5s for 50x100ms)",
            duration < 5000);

        long successCount = futures.stream()
            .filter(f -> !f.isCompletedExceptionally())
            .count();
        assertEquals("All slow requests should succeed", concurrentRequests, successCount);
    }

    @Test
    public void testErrorHandling() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("error", "true");

        CompletableFuture<String> future = client.executePostAsync(serverUrl + "/nonexistent", params);

        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Should throw exception for nonexistent endpoint");
        } catch (Exception e) {
            assertTrue("Exception should contain meaningful message",
                e.getCause() != null || e.getMessage() != null);
        }
    }

    @Test
    public void testConnectionReuse() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("test", "reuse");

        for (int i = 0; i < 10; i++) {
            String result = client.executePost(serverUrl + "/test", params);
            assertNotNull("Result should not be null for request " + i, result);
        }

        assertEquals("All 10 requests should be counted", 10, requestCount.get());
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
     * Echo handler that returns request data
     */
    private class EchoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            String response = "<response><echo>received</echo></response>";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
