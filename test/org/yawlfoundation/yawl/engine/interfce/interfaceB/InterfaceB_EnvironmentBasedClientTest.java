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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jdom2.Document;
import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.PasswordEncryptor;

import static org.junit.jupiter.api.*;

/**
 * Comprehensive test suite for InterfaceB_EnvironmentBasedClient
 * Tests all public methods, error scenarios, and edge cases
 * Following Chicago TDD principles with real HTTP server and TestContainers
 */
@Tag("integration")
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
public class InterfaceB_EnvironmentBasedClientTest {

    private static final String DEFAULT_BACKEND_URI = "http://localhost:8080/yawl/ib";
    private static final String TEST_USER_ID = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_SESSION_HANDLE = "session123";

    private static final int HTTP_SERVER_PORT = 8080;

    private InterfaceB_EnvironmentBasedClient client;
    private HttpTestServer testServer;

    @BeforeEach
    void setUp() throws IOException {
        testServer = new HttpTestServer(HTTP_SERVER_PORT);
        testServer.start();
        client = new InterfaceB_EnvironmentBasedClient("http://localhost:" + HTTP_SERVER_PORT + "/yawl/ib");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (testServer != null) {
            testServer.stop();
        }
        client = null;
    }

    @Test
    @DisplayName("Constructor with backend URI creates client correctly")
    @Order(1)
    void constructor_withBackendURI_createsClient() {
        // Arrange & Act
        String testUri = "http://test:8080/yawl/ib";
        InterfaceB_EnvironmentBasedClient newClient = new InterfaceB_EnvironmentBasedClient(testUri);

        // Assert
        assertNotNull(newClient);
        assertEquals(testUri, newClient.getBackEndURI());
    }

    @Test
    @DisplayName("getBackEndURI returns correct URI")
    @Order(2)
    void getBackEndURI_returnsCorrectURI() {
        // Act
        String uri = client.getBackEndURI();

        // Assert
        assertEquals("http://localhost:" + HTTP_SERVER_PORT + "/yawl/ib", uri);
    }

    @Test
    @DisplayName("connect with valid credentials returns session handle")
    @Order(3)
    void connect_withValidCredentials_returnsSessionHandle() throws Exception {
        // Arrange: Configure server to return session handle
        testServer.setConnectResponse(TEST_SESSION_HANDLE);

        // Act
        String result = client.connect(TEST_USER_ID, TEST_PASSWORD);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_SESSION_HANDLE, result);

        // Verify request was properly formed
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        assertEquals("POST", recordedRequest.method);
        assertEquals("/yawl/ib", recordedRequest.path);
        assertTrue(recordedRequest.parameters.containsKey("userid"));
        assertTrue(recordedRequest.parameters.containsKey("password"));
        assertEquals(TEST_USER_ID, recordedRequest.parameters.get("userid"));
    }

    @Test
    @DisplayName("connect encrypts password before sending")
    @Order(4)
    void connect_encryptsPasswordBeforeSending() throws Exception {
        // Arrange: Configure server to capture and return encrypted password
        testServer.setConnectResponse(TEST_SESSION_HANDLE);

        // Act
        client.connect(TEST_USER_ID, TEST_PASSWORD);

        // Assert that the encrypted password was sent
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        String sentPassword = recordedRequest.parameters.get("password");
        assertNotNull(sentPassword);
        assertNotEquals(TEST_PASSWORD, sentPassword); // Should be encrypted

        // Verify it's a valid encryption (not empty, different from original)
        assertFalse(sentPassword.isEmpty());
        assertFalse(sentPassword.equals(TEST_PASSWORD));
    }

    @Test
    @DisplayName("connect throws IOException on connection failure")
    @Order(5)
    void connect_throwsIOExceptionOnConnectionFailure() throws Exception {
        // Arrange: Stop server to simulate connection failure
        testServer.stop();

        // Act & Assert
        assertThrows(IOException.class, () -> {
            client.connect(TEST_USER_ID, TEST_PASSWORD);
        });
    }

    @Test
    @DisplayName("disconnect returns success message")
    @Order(6)
    void disconnect_returnsSuccessMessage() throws Exception {
        // Arrange: Configure server with disconnect response
        testServer.setDisconnectResponse("Disconnected successfully");

        // Act
        String result = client.disconnect(TEST_SESSION_HANDLE);

        // Assert
        assertEquals("Disconnected successfully", result);

        // Verify request was properly formed
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        assertEquals("POST", recordedRequest.method);
        assertEquals("/yawl/ib", recordedRequest.path);
        assertEquals("disconnect", recordedRequest.parameters.get("action"));
    }

    @Test
    @DisplayName("disconnect throws IOException on failure")
    @Order(7)
    void disconnect_throwsIOExceptionOnFailure() throws Exception {
        // Arrange: Stop server to simulate connection failure
        testServer.stop();

        // Act & Assert
        assertThrows(IOException.class, () -> {
            client.disconnect(TEST_SESSION_HANDLE);
        });
    }

    @Test
    @DisplayName("getCompleteListOfLiveWorkItems returns empty list when no work items")
    @Order(8)
    void getCompleteListOfLiveWorkItems_returnsEmptyList() throws Exception {
        // Arrange: Configure server with empty work item list
        testServer.setWorkItemsResponse("<workItemList/>");

        // Act
        List<WorkItemRecord> result = client.getCompleteListOfLiveWorkItems(TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify request was properly formed
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        assertEquals("getCompleteListOfLiveWorkItems", recordedRequest.parameters.get("action"));
    }

    @Test
    @DisplayName("getCompleteListOfLiveWorkItems throws IOException on connection failure")
    @Order(9)
    void getCompleteListOfLiveWorkItems_throwsIOExceptionOnConnectionFailure() throws Exception {
        // Arrange: Stop server to simulate connection failure
        testServer.stop();

        // Act & Assert
        assertThrows(IOException.class, () -> {
            client.getCompleteListOfLiveWorkItems(TEST_SESSION_HANDLE);
        });
    }

    @Test
    @DisplayName("getCompleteListOfLiveWorkItemsAsXML returns XML string")
    @Order(10)
    void getCompleteListOfLiveWorkItemsAsXML_returnsXmlString() throws Exception {
        // Arrange: Configure server with work items XML
        String expectedXml = "<workItemList><workItem id=\"item1\"/></workItemList>";
        testServer.setWorkItemsResponse(expectedXml);

        // Act
        String result = client.getCompleteListOfLiveWorkItemsAsXML(TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        assertEquals(expectedXml, result);

        // Verify request was properly formed
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        assertEquals("getCompleteListOfLiveWorkItems", recordedRequest.parameters.get("action"));
    }

    @Test
    @DisplayName("getWorkItem returns work item XML")
    @Order(11)
    void getWorkItem_returnsWorkItemXml() throws Exception {
        // Arrange: Configure server with specific work item
        String workItemId = "workItem123";
        String expectedXml = "<workItem id=\"" + workItemId + "\"/>";
        testServer.setWorkItemResponse(workItemId, expectedXml);

        // Act
        String result = client.getWorkItem(workItemId, TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        assertEquals(expectedXml, result);

        // Verify request was properly formed
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        assertEquals("getWorkItem", recordedRequest.parameters.get("action"));
        assertEquals(workItemId, recordedRequest.parameters.get("workItemID"));
    }

    @Test
    @DisplayName("getWorkItemExpiryTime returns expiry time")
    @Order(12)
    void getWorkItemExpiryTime_returnsExpiryTime() throws Exception {
        // Arrange: Configure server with expiry time response
        String workItemId = "workItem123";
        long expectedExpiry = System.currentTimeMillis() + 3600000; // 1 hour from now;
        testServer.setExpiryTimeResponse(workItemId, String.valueOf(expectedExpiry));

        // Act
        long result = client.getWorkItemExpiryTime(workItemId, TEST_SESSION_HANDLE);

        // Assert
        assertEquals(expectedExpiry, result);

        // Verify request was properly formed
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        assertEquals("getWorkItemExpiryTime", recordedRequest.parameters.get("action"));
        assertEquals(workItemId, recordedRequest.parameters.get("workItemID"));
    }

    @Test
    @DisplayName("getWorkItemExpiryTime returns 0 when work item not found")
    @Order(13)
    void getWorkItemExpiryTime_returnsZeroWhenNotFound() throws Exception {
        // Arrange: Configure server with not found response
        String workItemId = "nonexistentItem";
        testServer.setExpiryTimeResponse(workItemId, "null");

        // Act
        long result = client.getWorkItemExpiryTime(workItemId, TEST_SESSION_HANDLE);

        // Assert
        assertEquals(0, result);
    }

    @Test
    @DisplayName("getWorkItemsForCase returns work items for specific case")
    @Order(14)
    void getWorkItemsForCase_returnsWorkItemsForCase() throws Exception {
        // Arrange: Configure server with case-specific work items
        String caseId = "case123";
        String xmlResponse = "<workItemList><workItem id=\"item1\" caseId=\"" + caseId + "\"/></workItemList>";
        testServer.setWorkItemsForCaseResponse(caseId, xmlResponse);

        // Act
        List<WorkItemRecord> result = client.getWorkItemsForCase(caseId, TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify request was properly formed
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        assertEquals("getWorkItemsWithIdentifier", recordedRequest.parameters.get("action"));
        assertEquals(caseId, recordedRequest.parameters.get("id"));
        assertEquals("case", recordedRequest.parameters.get("idType"));
    }

    @Test
    @DisplayName("getWorkItemsForSpecification returns work items for specification")
    @Order(15)
    void getWorkItemsForSpecification_returnsWorkItemsForSpecification() throws Exception {
        // Arrange: Configure server with specification-specific work items
        String specName = "TestSpecification";
        String xmlResponse = "<workItemList><workItem id=\"item1\" specName=\"" + specName + "\"/></workItemList>";
        testServer.setWorkItemsForSpecificationResponse(specName, xmlResponse);

        // Act
        List<WorkItemRecord> result = client.getWorkItemsForSpecification(specName, TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify request was properly formed
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        assertEquals("getWorkItemsWithIdentifier", recordedRequest.parameters.get("action"));
        assertEquals(specName, recordedRequest.parameters.get("id"));
        assertEquals("spec", recordedRequest.parameters.get("idType"));
    }

    @Test
    @DisplayName("getWorkItemsForTask returns work items for task")
    @Order(16)
    void getWorkItemsForTask_returnsWorkItemsForTask() throws Exception {
        // Arrange: Configure server with task-specific work items
        String taskId = "task123";
        String xmlResponse = "<workItemList><workItem id=\"item1\" taskId=\"" + taskId + "\"/></workItemList>";
        testServer.setWorkItemsForTaskResponse(taskId, xmlResponse);

        // Act
        List<WorkItemRecord> result = client.getWorkItemsForTask(taskId, TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify request was properly formed
        HttpTestServer.HttpRequestRecord recordedRequest = testServer.getRecordedRequest();
        assertNotNull(recordedRequest);
        assertEquals("getWorkItemsWithIdentifier", recordedRequest.parameters.get("action"));
        assertEquals(taskId, recordedRequest.parameters.get("id"));
        assertEquals("task", recordedRequest.parameters.get("idType"));
    }

    @Test
    @DisplayName("handleIOException converts IO exception correctly")
    @Order(17)
    void handleIOException_convertsIoExceptionCorrectly() throws Exception {
        // Arrange: Stop server to simulate IO exception
        testServer.stop();

        // Act & Assert
        assertThrows(IOException.class, () -> {
            client.getWorkItem("test", TEST_SESSION_HANDLE);
        });
    }

    @Test
    @DisplayName("concurrentConnectionsHandleMultipleRequests")
    @Order(18)
    void concurrentConnectionsHandleMultipleRequests() throws Exception {
        // Arrange: Configure server to handle multiple connections
        testServer.setConnectResponse("session123");

        int requestCount = 10;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Act - create concurrent connections
        for (int i = 0; i < requestCount; i++) {
            final int threadNum = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return client.connect("user" + threadNum, "password" + threadNum);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }

        // Wait for all connections to complete
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            try {
                results.add(future.get(5, TimeUnit.SECONDS));
            } catch (TimeoutException | ExecutionException e) {
                fail("Connection timed out or failed: " + e.getMessage());
            }
        }

        // Assert
        assertEquals(requestCount, results.size());
        assertTrue(results.stream().allMatch(s -> s.equals("session123")));

        executor.shutdown();
    }

    @Test
    @DisplayName("nullInputParametersThrowNullPointerException")
    @Order(19)
    void nullInputParametersThrowNullPointerException() {
        // Test null parameters for critical methods

        // connect with null userId
        assertThrows(NullPointerException.class, () -> {
            client.connect(null, TEST_PASSWORD);
        });

        // connect with null password
        assertThrows(NullPointerException.class, () -> {
            client.connect(TEST_USER_ID, null);
        });

        // disconnect with null handle
        assertThrows(NullPointerException.class, () -> {
            client.disconnect(null);
        });

        // getWorkItem with null item ID
        assertThrows(NullPointerException.class, () -> {
            client.getWorkItem(null, TEST_SESSION_HANDLE);
        });
    }

    @Test
    @DisplayName("emptyStringParametersAreHandledCorrectly")
    @Order(20)
    void emptyStringParametersAreHandledCorrectly() throws Exception {
        // Test empty string parameters
        testServer.setWorkItemsResponse("<workItemList/>");

        // Empty work item ID should work (returns empty list)
        List<WorkItemRecord> result = client.getWorkItemsForCase("", TEST_SESSION_HANDLE);
        assertNotNull(result);

        // Empty session handle should work
        testServer.setWorkItemResponse("test", "<workItem id=\"test\"/>");
        String workItemXml = client.getWorkItem("test", "");
        assertNotNull(workItemXml);
    }
}

/**
 * Real HTTP test server for testing InterfaceB_EnvironmentBasedClient
 * Implements the YAWL InterfaceB protocol for real HTTP communication
 * No mocks - actual HTTP server implementation
 */
class HttpTestServer {
    private final int port;
    private com.sun.net.httpserver.HttpServer server;
    private volatile boolean running = false;
    private HttpRequestRecord lastRequest;

    // Response configurations
    private String connectResponse = "session123";
    private String disconnectResponse = "Disconnected successfully";
    private String workItemsResponse = "<workItemList/>";
    private Map<String, String> workItemResponses = new HashMap<>();
    private Map<String, String> expiryTimeResponses = new HashMap<>();
    private Map<String, String> caseResponses = new HashMap<>();
    private Map<String, String> specResponses = new HashMap<>();
    private Map<String, String> taskResponses = new HashMap<>();

    public static class HttpRequestRecord {
        public String method;
        public String path;
        public Map<String, String> parameters = new HashMap<>();
        public String body;
    }

    public HttpTestServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running) return;

        server = com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress.createUnresolved("localhost", port), 0);

        server.createContext("/yawl/ib", exchange -> {
            try {
                lastRequest = new HttpRequestRecord();
                lastRequest.method = exchange.getRequestMethod();
                lastRequest.path = exchange.getRequestURI().getPath();

                // Parse query parameters
                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    parseParams(query, lastRequest.parameters);
                }

                // Parse POST body
                if ("POST".equals(lastRequest.method)) {
                    lastRequest.body = new String(exchange.getRequestBody().readAllBytes());
                    parseParams(lastRequest.body, lastRequest.parameters);
                }

                // Handle the request
                String response = handleRequest(lastRequest);

                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
                e.printStackTrace();
            } finally {
                exchange.close();
            }
        });

        server.start();
        running = true;
    }

    public void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
        }
    }

    private String handleRequest(HttpRequestRecord request) {
        String action = request.parameters.get("action");

        switch (action) {
            case "connect":
                return connectResponse;
            case "disconnect":
                return disconnectResponse;
            case "getCompleteListOfLiveWorkItems":
                return workItemsResponse;
            case "getWorkItem":
                String workItemId = request.parameters.get("workItemID");
                return workItemResponses.getOrDefault(workItemId, "<workItem/>");
            case "getWorkItemExpiryTime":
                String expiryItemId = request.parameters.get("workItemID");
                return expiryTimeResponses.getOrDefault(expiryItemId, "0");
            case "getWorkItemsWithIdentifier":
                String id = request.parameters.get("id");
                String idType = request.parameters.get("idType");

                if ("case".equals(idType)) {
                    return caseResponses.getOrDefault(id, "<workItemList/>");
                } else if ("spec".equals(idType)) {
                    return specResponses.getOrDefault(id, "<workItemList/>");
                } else if ("task".equals(idType)) {
                    return taskResponses.getOrDefault(id, "<workItemList/>");
                }
                return "<workItemList/>";
            default:
                throw new UnsupportedOperationException("Unknown action: " + action);
        }
    }

    private void parseParams(String queryString, Map<String, String> params) {
        if (queryString == null || queryString.isEmpty()) {
            return;
        }

        for (String pair : queryString.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }
    }

    // Configuration methods for setting responses
    public void setConnectResponse(String response) {
        this.connectResponse = response;
    }

    public void setDisconnectResponse(String response) {
        this.disconnectResponse = response;
    }

    public void setWorkItemsResponse(String response) {
        this.workItemsResponse = response;
    }

    public void setWorkItemResponse(String workItemId, String response) {
        this.workItemResponses.put(workItemId, response);
    }

    public void setExpiryTimeResponse(String workItemId, String response) {
        this.expiryTimeResponses.put(workItemId, response);
    }

    public void setWorkItemsForCaseResponse(String caseId, String response) {
        this.caseResponses.put(caseId, response);
    }

    public void setWorkItemsForSpecificationResponse(String specName, String response) {
        this.specResponses.put(specName, response);
    }

    public void setWorkItemsForTaskResponse(String taskId, String response) {
        this.taskResponses.put(taskId, response);
    }

    public HttpRequestRecord getRecordedRequest() {
        return lastRequest;
    }
}