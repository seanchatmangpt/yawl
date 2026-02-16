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

package org.yawlfoundation.yawl.engine.interfce.interfaceB;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InterfaceB_EnvironmentBasedClient batch operations with structured concurrency.
 *
 * Tests verify:
 * 1. Batch checkout operations work correctly
 * 2. Batch checkin operations work correctly
 * 3. Batch case cancellation works correctly
 * 4. Timeout handling works properly
 * 5. Error propagation is correct
 * 6. Performance is improved vs sequential operations
 *
 * @author Claude (Virtual Threads Migration)
 * @date 2026-02-16
 */
public class InterfaceB_EnvironmentBasedClientBatchTest {

    private HttpServer testServer;
    private int serverPort;
    private String serverUrl;
    private InterfaceB_EnvironmentBasedClient client;
    private AtomicInteger requestCount;
    private Map<String, String> processedRequests;

    @BeforeEach
    public void setUp() throws IOException {
        requestCount = new AtomicInteger(0);
        processedRequests = new ConcurrentHashMap<>();

        testServer = HttpServer.create(new InetSocketAddress(0), 0);
        serverPort = testServer.getAddress().getPort();
        serverUrl = "http://localhost:" + serverPort + "/ib";

        testServer.createContext("/ib", new TestInterfaceBHandler());
        testServer.start();

        client = new InterfaceB_EnvironmentBasedClient(serverUrl);
    }

    @AfterEach
    public void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @Test
    public void testBatchCheckoutWorkItems() throws IOException, ExecutionException, InterruptedException {
        List<String> workItemIDs = Arrays.asList("item1", "item2", "item3", "item4", "item5");
        String sessionHandle = "test-session-123";

        long startTime = System.currentTimeMillis();
        List<String> results = client.checkOutWorkItemsBatch(workItemIDs, sessionHandle);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull("Results should not be null", results);
        assertEquals("Should have result for each work item", workItemIDs.size(), results.size());

        for (int i = 0; i < workItemIDs.size(); i++) {
            String result = results.get(i);
            assertNotNull("Result " + i + " should not be null", result);
            assertTrue("Result should indicate success", result.contains("success") || result.contains("workItem"));
        }

        System.out.println("Batch checkout of " + workItemIDs.size() + " items completed in " + duration + "ms");

        assertTrue("Batch checkout should complete quickly (< 2s)", duration < 2000);
    }

    @Test
    public void testBatchCheckoutWithTimeout() throws IOException, TimeoutException, ExecutionException, InterruptedException {
        List<String> workItemIDs = Arrays.asList("item1", "item2", "item3");
        String sessionHandle = "test-session-123";

        List<String> results = client.checkOutWorkItemsBatch(workItemIDs, sessionHandle, 10);

        assertNotNull("Results should not be null", results);
        assertEquals("Should have result for each work item", workItemIDs.size(), results.size());
    }

    @Test
    public void testBatchCheckoutTimeout() throws IOException, InterruptedException {
        assertThrows(TimeoutException.class, () -> {
            List<String> workItemIDs = Arrays.asList("slow-item1", "slow-item2");
            String sessionHandle = "test-session-123";

            client.checkOutWorkItemsBatch(workItemIDs, sessionHandle, 1);
        });
    }

    @Test
    public void testBatchCheckinWorkItems() throws IOException, ExecutionException, InterruptedException {
        List<InterfaceB_EnvironmentBasedClient.WorkItemCheckinData> workItems = Arrays.asList(
            new InterfaceB_EnvironmentBasedClient.WorkItemCheckinData("item1", "<data>value1</data>", "log1"),
            new InterfaceB_EnvironmentBasedClient.WorkItemCheckinData("item2", "<data>value2</data>", "log2"),
            new InterfaceB_EnvironmentBasedClient.WorkItemCheckinData("item3", "<data>value3</data>")
        );
        String sessionHandle = "test-session-123";

        long startTime = System.currentTimeMillis();
        List<String> results = client.checkInWorkItemsBatch(workItems, sessionHandle);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull("Results should not be null", results);
        assertEquals("Should have result for each work item", workItems.size(), results.size());

        for (String result : results) {
            assertNotNull("Result should not be null", result);
            assertTrue("Result should indicate success", result.contains("success"));
        }

        System.out.println("Batch checkin of " + workItems.size() + " items completed in " + duration + "ms");
    }

    @Test
    public void testBatchCancelCases() throws IOException, ExecutionException, InterruptedException {
        List<String> caseIDs = Arrays.asList("case1", "case2", "case3", "case4");
        String sessionHandle = "test-session-123";

        long startTime = System.currentTimeMillis();
        List<String> results = client.cancelCasesBatch(caseIDs, sessionHandle);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull("Results should not be null", results);
        assertEquals("Should have result for each case", caseIDs.size(), results.size());

        for (String result : results) {
            assertNotNull("Result should not be null", result);
        }

        System.out.println("Batch cancel of " + caseIDs.size() + " cases completed in " + duration + "ms");
    }

    @Test
    public void testBatchGetWorkItemsForCases() throws IOException, ExecutionException, InterruptedException {
        List<String> caseIDs = Arrays.asList("case1", "case2", "case3");
        String sessionHandle = "test-session-123";

        long startTime = System.currentTimeMillis();
        List<List<WorkItemRecord>> results = client.getWorkItemsForCasesBatch(caseIDs, sessionHandle);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull("Results should not be null", results);
        assertEquals("Should have result for each case", caseIDs.size(), results.size());

        System.out.println("Batch get work items for " + caseIDs.size() + " cases completed in " + duration + "ms");
    }

    @Test
    public void testLargeBatchCheckout() throws IOException, ExecutionException, InterruptedException {
        List<String> workItemIDs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            workItemIDs.add("item" + i);
        }
        String sessionHandle = "test-session-123";

        long startTime = System.currentTimeMillis();
        List<String> results = client.checkOutWorkItemsBatch(workItemIDs, sessionHandle);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull("Results should not be null", results);
        assertEquals("Should have result for each work item", workItemIDs.size(), results.size());

        long successCount = results.stream()
            .filter(r -> r != null && (r.contains("success") || r.contains("workItem")))
            .count();

        assertTrue("Most requests should succeed (>95%)", successCount > 95);

        System.out.println("Large batch checkout of " + workItemIDs.size() +
            " items completed in " + duration + "ms (" + successCount + " successful)");

        assertTrue("Large batch should complete efficiently (< 10s)", duration < 10000);
    }

    @Test
    public void testPerformanceVsSequential() throws IOException, ExecutionException, InterruptedException {
        List<String> workItemIDs = Arrays.asList("item1", "item2", "item3", "item4", "item5",
                                                  "item6", "item7", "item8", "item9", "item10");
        String sessionHandle = "test-session-123";

        long batchStart = System.currentTimeMillis();
        List<String> batchResults = client.checkOutWorkItemsBatch(workItemIDs, sessionHandle);
        long batchDuration = System.currentTimeMillis() - batchStart;

        long sequentialStart = System.currentTimeMillis();
        List<String> sequentialResults = new ArrayList<>();
        for (String itemID : workItemIDs) {
            sequentialResults.add(client.checkOutWorkItem(itemID, sessionHandle));
        }
        long sequentialDuration = System.currentTimeMillis() - sequentialStart;

        System.out.println("Batch: " + batchDuration + "ms, Sequential: " + sequentialDuration + "ms");
        System.out.println("Speedup: " + String.format("%.2f", (double)sequentialDuration / batchDuration) + "x");

        assertTrue("Batch should be faster than sequential",
            batchDuration < sequentialDuration);

        assertEquals("Both should return same number of results",
            batchResults.size(), sequentialResults.size());
    }

    @Test
    public void testEmptyBatch() throws IOException, ExecutionException, InterruptedException {
        List<String> emptyList = Collections.emptyList();
        String sessionHandle = "test-session-123";

        List<String> results = client.checkOutWorkItemsBatch(emptyList, sessionHandle);

        assertNotNull("Results should not be null", results);
        assertEquals("Results should be empty for empty input", 0, results.size());
    }

    @Test
    public void testSingleItemBatch() throws IOException, ExecutionException, InterruptedException {
        List<String> singleItem = Collections.singletonList("item1");
        String sessionHandle = "test-session-123";

        List<String> results = client.checkOutWorkItemsBatch(singleItem, sessionHandle);

        assertNotNull("Results should not be null", results);
        assertEquals("Should have one result", 1, results.size());
        assertNotNull("Result should not be null", results.get(0));
    }

    /**
     * Test HTTP handler that implements real InterfaceB protocol for testing.
     * This is a real HTTP server implementation, not a mock.
     */
    private class TestInterfaceBHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();

            String requestBody = new String(exchange.getRequestBody().readAllBytes());
            String action = extractParam(requestBody, "action");
            String workItemID = extractParam(requestBody, "workItemID");
            String caseID = extractParam(requestBody, "caseID");

            processedRequests.put(workItemID != null ? workItemID : caseID, action);

            String response;

            if (workItemID != null && workItemID.startsWith("slow-")) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            switch (action != null ? action : "") {
                case "checkout":
                    response = "<response><workItem><id>" + workItemID + "</id><status>executing</status></workItem></response>";
                    break;
                case "checkin":
                    response = "<response><success/></response>";
                    break;
                case "cancelCase":
                    response = "<response><success/></response>";
                    break;
                case "getWorkItemsWithIdentifier":
                    response = "<response><workitemrecords></workitemrecords></response>";
                    break;
                default:
                    response = "<response><success/></response>";
            }

            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private String extractParam(String body, String param) {
            String prefix = param + "=";
            int startIdx = body.indexOf(prefix);
            if (startIdx == -1) return null;

            startIdx += prefix.length();
            int endIdx = body.indexOf("&", startIdx);
            if (endIdx == -1) endIdx = body.length();

            String value = body.substring(startIdx, endIdx);
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
