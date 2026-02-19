/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.yawlfoundation.yawl.integration.TestConstants.*;

/**
 * Base class for MCP (Model Context Protocol) integration tests.
 *
 * <p>Provides MCP-specific test infrastructure including:</p>
 * <ul>
 *   <li>Mock transport layer for MCP communication</li>
 *   <li>Tool call and result generation</li>
 *   <li>JSON-RPC message handling</li>
 *   <li>Server capability simulation</li>
 *   <li>Protocol lifecycle testing</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * class MyMCPTest extends McpIntegrationTestBase {
 *     @Test
 *     void testToolCall() throws Exception {
 *         McpToolScenario scenario = createLaunchWorkflowScenario("spec-123");
 *         McpResult result = executeToolCall(scenario);
 *         assertSuccess(result);
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class McpIntegrationTestBase extends IntegrationTestBase {

    protected ObjectMapper objectMapper;
    protected TestMcpTransport transport;
    protected TestMcpServer testServer;
    protected TestMcpClient testClient;

    private final AtomicLong callIdCounter = new AtomicLong(0);

    // ============ Sealed MCP Result Types ============

    /**
     * Sealed interface for MCP operation results.
     */
    public sealed interface McpResult permits
        McpResult.ToolSuccess,
        McpResult.ToolError,
        McpResult.ProtocolError {

        /**
         * Successful tool execution result.
         *
         * @param callId matching call identifier
         * @param method method that was called
         * @param result result data
         * @param duration execution duration
         */
        record ToolSuccess(
            String callId,
            String method,
            Map<String, Object> result,
            Duration duration
        ) implements McpResult {}

        /**
         * Tool execution error result.
         *
         * @param callId matching call identifier
         * @param method method that was called
         * @param errorCode JSON-RPC error code
         * @param errorMessage error message
         * @param errorData additional error data
         */
        record ToolError(
            String callId,
            String method,
            int errorCode,
            String errorMessage,
            Map<String, Object> errorData
        ) implements McpResult {}

        /**
         * Protocol-level error result.
         *
         * @param errorCode protocol error code
         * @param errorMessage error message
         * @param recoverable whether error is recoverable
         */
        record ProtocolError(
            int errorCode,
            String errorMessage,
            boolean recoverable
        ) implements McpResult {}
    }

    /**
     * MCP server capabilities record.
     *
     * @param protocolVersion supported protocol version
     * @param supportsTools whether tools are supported
     * @param supportsResources whether resources are supported
     * @param supportsPrompts whether prompts are supported
     * @param supportsCompletions whether completions are supported
     * @param serverInfo server identification
     */
    public record McpServerCapabilities(
        String protocolVersion,
        boolean supportsTools,
        boolean supportsResources,
        boolean supportsPrompts,
        boolean supportsCompletions,
        ServerInfo serverInfo
    ) {
        /**
         * Creates default YAWL MCP server capabilities.
         *
         * @return default capabilities
         */
        public static McpServerCapabilities defaultYawlCapabilities() {
            return new McpServerCapabilities(
                MCP_PROTOCOL_VERSION,
                true,
                true,
                true,
                true,
                new ServerInfo("YAWL-MCP-Server", "6.0.0")
            );
        }
    }

    /**
     * Server information record.
     *
     * @param name server name
     * @param version server version
     */
    public record ServerInfo(String name, String version) {}

    /**
     * JSON-RPC message record.
     *
     * @param jsonrpc JSON-RPC version
     * @param id message identifier
     * @param method method name (for requests)
     * @param params parameters (for requests)
     * @param result result data (for responses)
     * @param error error object (for error responses)
     */
    public record JsonRpcMessage(
        String jsonrpc,
        String id,
        String method,
        Map<String, Object> params,
        Map<String, Object> result,
        JsonRpcError error
    ) {
        /**
         * Creates a request message.
         *
         * @param id message ID
         * @param method method name
         * @param params parameters
         * @return request message
         */
        public static JsonRpcMessage request(String id, String method, Map<String, Object> params) {
            return new JsonRpcMessage(JSON_RPC_VERSION, id, method, params, null, null);
        }

        /**
         * Creates a success response message.
         *
         * @param id matching request ID
         * @param result result data
         * @return response message
         */
        public static JsonRpcMessage response(String id, Map<String, Object> result) {
            return new JsonRpcMessage(JSON_RPC_VERSION, id, null, null, result, null);
        }

        /**
         * Creates an error response message.
         *
         * @param id matching request ID (may be null)
         * @param error error object
         * @return error response message
         */
        public static JsonRpcMessage error(String id, JsonRpcError error) {
            return new JsonRpcMessage(JSON_RPC_VERSION, id, null, null, null, error);
        }

        /**
         * Checks if this is a request message.
         *
         * @return true if request
         */
        public boolean isRequest() {
            return method != null;
        }

        /**
         * Checks if this is a response message.
         *
         * @return true if response
         */
        public boolean isResponse() {
            return method == null && (result != null || error != null);
        }

        /**
         * Converts to JSON string.
         *
         * @param mapper object mapper to use
         * @return JSON string
         */
        public String toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("jsonrpc", jsonrpc);
            if (id != null) {
                node.put("id", id);
            }
            if (method != null) {
                node.put("method", method);
            }
            if (params != null) {
                node.putPOJO("params", params);
            }
            if (result != null) {
                node.putPOJO("result", result);
            }
            if (error != null) {
                ObjectNode errorNode = node.putObject("error");
                errorNode.put("code", error.code());
                errorNode.put("message", error.message());
                if (error.data() != null) {
                    errorNode.putPOJO("data", error.data());
                }
            }
            return node.toString();
        }
    }

    /**
     * JSON-RPC error record.
     *
     * @param code error code
     * @param message error message
     * @param data additional error data
     */
    public record JsonRpcError(int code, String message, Object data) {
        /** Parse error code. */
        public static final int PARSE_ERROR = -32700;
        /** Invalid request error code. */
        public static final int INVALID_REQUEST = -32600;
        /** Method not found error code. */
        public static final int METHOD_NOT_FOUND = -32601;
        /** Invalid params error code. */
        public static final int INVALID_PARAMS = -32602;
        /** Internal error code. */
        public static final int INTERNAL_ERROR = -32603;

        /**
         * Creates a method not found error.
         *
         * @param method method that was not found
         * @return error object
         */
        public static JsonRpcError methodNotFound(String method) {
            return new JsonRpcError(METHOD_NOT_FOUND, "Method not found: " + method, null);
        }

        /**
         * Creates an invalid params error.
         *
         * @param message error message
         * @return error object
         */
        public static JsonRpcError invalidParams(String message) {
            return new JsonRpcError(INVALID_PARAMS, message, null);
        }

        /**
         * Creates an internal error.
         *
         * @param message error message
         * @return error object
         */
        public static JsonRpcError internalError(String message) {
            return new JsonRpcError(INTERNAL_ERROR, message, null);
        }
    }

    // ============ Lifecycle ============

    @Override
    protected void onSetUp() throws Exception {
        objectMapper = new ObjectMapper();

        // Initialize test transport
        transport = new TestMcpTransport();

        // Initialize test server and client
        testServer = new TestMcpServer(transport);
        testClient = new TestMcpClient(transport);

        // Start server
        testServer.start();

        logger.info("MCP test infrastructure initialized");
    }

    @Override
    protected void onTearDown() throws Exception {
        if (testClient != null) {
            testClient.disconnect();
        }
        if (testServer != null) {
            testServer.stop();
        }
        if (transport != null) {
            transport.close();
        }
        logger.info("MCP test infrastructure shutdown");
    }

    // ============ Tool Scenarios ============

    /**
     * Creates a launch workflow tool scenario.
     *
     * @param specId specification identifier
     * @return tool scenario
     */
    protected TestDataGenerator.McpToolScenario createLaunchWorkflowScenario(String specId) {
        return TestDataGenerator.McpToolScenario.launchWorkflow(specId);
    }

    /**
     * Creates a cancel workflow tool scenario.
     *
     * @param caseId case identifier
     * @return tool scenario
     */
    protected TestDataGenerator.McpToolScenario createCancelWorkflowScenario(String caseId) {
        return TestDataGenerator.McpToolScenario.cancelWorkflow(caseId);
    }

    /**
     * Creates a query workflows tool scenario.
     *
     * @param filters optional filters
     * @return tool scenario
     */
    @SuppressWarnings("unchecked")
    protected TestDataGenerator.McpToolScenario createQueryWorkflowsScenario(Map<String, Object> filters) {
        Map<String, Object> params = new HashMap<>();
        params.put("filters", filters != null ? filters : Map.of());

        return new TestDataGenerator.McpToolScenario(
            generateCallId(),
            "query_workflows",
            params,
            true,
            DEFAULT_MCP_TIMEOUT
        );
    }

    /**
     * Creates a manage work items tool scenario.
     *
     * @param workItemId work item identifier
     * @param action action to perform
     * @return tool scenario
     */
    protected TestDataGenerator.McpToolScenario createManageWorkItemsScenario(String workItemId, String action) {
        Map<String, Object> params = new HashMap<>();
        params.put("workItemId", workItemId);
        params.put("action", action);

        return new TestDataGenerator.McpToolScenario(
            generateCallId(),
            "manage_workitems",
            params,
            true,
            DEFAULT_MCP_TIMEOUT
        );
    }

    // ============ Tool Execution ============

    /**
     * Executes a tool call scenario.
     *
     * @param scenario tool scenario
     * @return tool result
     */
    protected McpResult executeToolCall(TestDataGenerator.McpToolScenario scenario) {
        return executeToolCall(scenario, null);
    }

    /**
     * Executes a tool call scenario with custom handler.
     *
     * @param scenario tool scenario
     * @param handler custom response handler
     * @return tool result
     */
    protected McpResult executeToolCall(TestDataGenerator.McpToolScenario scenario,
                                         Consumer<JsonRpcMessage> handler) {
        Instant start = Instant.now();

        try {
            // Create request
            JsonRpcMessage request = JsonRpcMessage.request(
                scenario.callId(),
                scenario.method(),
                scenario.params()
            );

            // Set up response handler if provided
            if (handler != null) {
                testServer.setResponseHandler(scenario.callId(), handler);
            }

            // Send request
            JsonRpcMessage response = testClient.sendRequest(request, scenario.timeout());

            // Process response
            return processResponse(response, scenario, Duration.between(start, Instant.now()));

        } catch (TimeoutException e) {
            return new McpResult.ToolError(
                scenario.callId(),
                scenario.method(),
                JsonRpcError.INTERNAL_ERROR,
                "Request timed out after " + scenario.timeout(),
                Map.of("timeout", scenario.timeout().toString())
            );
        } catch (Exception e) {
            return new McpResult.ProtocolError(
                JsonRpcError.INTERNAL_ERROR,
                e.getMessage(),
                true
            );
        }
    }

    /**
     * Executes multiple tool calls concurrently.
     *
     * @param scenarios list of scenarios
     * @return list of results
     * @throws Exception if execution fails
     */
    protected List<McpResult> executeToolCallsConcurrent(List<TestDataGenerator.McpToolScenario> scenarios)
            throws Exception {
        List<Callable<McpResult>> tasks = scenarios.stream()
            .map(scenario -> (Callable<McpResult>) () -> executeToolCall(scenario))
            .toList();
        return runConcurrent(tasks);
    }

    // ============ Message Utilities ============

    /**
     * Generates a unique call ID.
     *
     * @return unique call ID
     */
    protected String generateCallId() {
        return "call-" + callIdCounter.incrementAndGet() + "-" + System.currentTimeMillis();
    }

    /**
     * Parses a JSON-RPC message.
     *
     * @param json JSON string
     * @return parsed message
     */
    protected JsonRpcMessage parseMessage(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            JsonRpcError error = null;
            if (node.has("error")) {
                JsonNode errorNode = node.get("error");
                error = new JsonRpcError(
                    errorNode.get("code").asInt(),
                    errorNode.get("message").asText(),
                    errorNode.has("data") ? objectMapper.convertValue(errorNode.get("data"), Map.class) : null
                );
            }

            return new JsonRpcMessage(
                node.get("jsonrpc").asText(),
                node.has("id") ? node.get("id").asText() : null,
                node.has("method") ? node.get("method").asText() : null,
                node.has("params") ? objectMapper.convertValue(node.get("params"), Map.class) : null,
                node.has("result") ? objectMapper.convertValue(node.get("result"), Map.class) : null,
                error
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON-RPC message: " + e.getMessage(), e);
        }
    }

    // ============ Assertions ============

    /**
     * Asserts that an MCP result is successful.
     *
     * @param result MCP result
     */
    protected void assertSuccess(McpResult result) {
        assertTrue(result instanceof McpResult.ToolSuccess,
            "Expected successful result but got: " + result.getClass().getSimpleName());
    }

    /**
     * Asserts that an MCP result is an error.
     *
     * @param result MCP result
     */
    protected void assertError(McpResult result) {
        assertTrue(result instanceof McpResult.ToolError || result instanceof McpResult.ProtocolError,
            "Expected error result but got: " + result.getClass().getSimpleName());
    }

    /**
     * Asserts that a result has a specific error code.
     *
     * @param result MCP result
     * @param expectedCode expected error code
     */
    protected void assertErrorCode(McpResult result, int expectedCode) {
        assertError(result);
        int actualCode = switch (result) {
            case McpResult.ToolError te -> te.errorCode();
            case McpResult.ProtocolError pe -> pe.errorCode();
            default -> throw new AssertionError("Not an error result");
        };
        assertEquals(expectedCode, actualCode, "Error code mismatch");
    }

    /**
     * Asserts that a JSON-RPC message is valid.
     *
     * @param message message to validate
     */
    protected void assertValidMessage(JsonRpcMessage message) {
        assertNotNull(message.jsonrpc(), "jsonrpc version is required");
        assertEquals(JSON_RPC_VERSION, message.jsonrpc(), "jsonrpc version must be 2.0");
    }

    /**
     * Asserts that call and result IDs match.
     *
     * @param callId call identifier
     * @param resultId result identifier
     */
    protected void assertIdsMatch(String callId, String resultId) {
        assertEquals(callId, resultId, "Call and result IDs must match");
    }

    // ============ Private Methods ============

    private McpResult processResponse(JsonRpcMessage response, TestDataGenerator.McpToolScenario scenario,
                                       Duration duration) {
        if (response.error() != null) {
            return new McpResult.ToolError(
                response.id(),
                scenario.method(),
                response.error().code(),
                response.error().message(),
                response.error().data() != null ?
                    (Map<String, Object>) response.error().data() : Map.of()
            );
        }

        return new McpResult.ToolSuccess(
            response.id(),
            scenario.method(),
            response.result() != null ? response.result() : Map.of(),
            duration
        );
    }

    // ============ Test Infrastructure Classes ============

    /**
     * Mock MCP transport for testing.
     */
    protected static class TestMcpTransport implements Closeable {
        private final BlockingQueue<JsonRpcMessage> clientQueue = new LinkedBlockingQueue<>();
        private final BlockingQueue<JsonRpcMessage> serverQueue = new LinkedBlockingQueue<>();
        private final Map<String, CompletableFuture<JsonRpcMessage>> pendingRequests = new ConcurrentHashMap<>();
        private volatile boolean closed = false;

        /**
         * Sends a message from client to server.
         *
         * @param message message to send
         */
        public void sendFromClient(JsonRpcMessage message) {
            if (closed) {
                throw new IllegalStateException("Transport is closed");
            }
            clientQueue.offer(message);
        }

        /**
         * Sends a message from server to client.
         *
         * @param message message to send
         */
        public void sendFromServer(JsonRpcMessage message) {
            if (closed) {
                throw new IllegalStateException("Transport is closed");
            }
            serverQueue.offer(message);

            // Complete pending request if response
            if (message.id() != null && message.isResponse()) {
                CompletableFuture<JsonRpcMessage> future = pendingRequests.remove(message.id());
                if (future != null) {
                    future.complete(message);
                }
            }
        }

        /**
         * Receives a message on the server side.
         *
         * @param timeout receive timeout
         * @return received message or null
         * @throws InterruptedException if interrupted
         */
        public JsonRpcMessage receiveOnServer(Duration timeout) throws InterruptedException {
            return clientQueue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        /**
         * Receives a message on the client side.
         *
         * @param timeout receive timeout
         * @return received message or null
         * @throws InterruptedException if interrupted
         */
        public JsonRpcMessage receiveOnClient(Duration timeout) throws InterruptedException {
            return serverQueue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        /**
         * Registers a pending request.
         *
         * @param callId call identifier
         * @param future future to complete
         */
        public void registerPendingRequest(String callId, CompletableFuture<JsonRpcMessage> future) {
            pendingRequests.put(callId, future);
        }

        /**
         * Gets a pending request future.
         *
         * @param callId call identifier
         * @return future or null
         */
        public CompletableFuture<JsonRpcMessage> getPendingRequest(String callId) {
            return pendingRequests.get(callId);
        }

        @Override
        public void close() {
            closed = true;
            clientQueue.clear();
            serverQueue.clear();
            pendingRequests.clear();
        }
    }

    /**
     * Test MCP server implementation.
     */
    protected static class TestMcpServer {
        private final TestMcpTransport transport;
        private final Map<String, Consumer<JsonRpcMessage>> responseHandlers = new ConcurrentHashMap<>();
        private volatile boolean running = false;
        private Thread serverThread;

        public TestMcpServer(TestMcpTransport transport) {
            this.transport = transport;
        }

        /**
         * Starts the server.
         */
        public void start() {
            if (running) {
                return;
            }
            running = true;
            serverThread = Thread.ofVirtual().name("mcp-test-server").start(this::runServer);
        }

        /**
         * Stops the server.
         */
        public void stop() {
            running = false;
            if (serverThread != null) {
                serverThread.interrupt();
            }
        }

        /**
         * Sets a custom response handler for a call ID.
         *
         * @param callId call identifier
         * @param handler response handler
         */
        public void setResponseHandler(String callId, Consumer<JsonRpcMessage> handler) {
            responseHandlers.put(callId, handler);
        }

        private void runServer() {
            while (running) {
                try {
                    JsonRpcMessage request = transport.receiveOnServer(Duration.ofMillis(100));
                    if (request != null && request.isRequest()) {
                        JsonRpcMessage response = handleRequest(request);
                        transport.sendFromServer(response);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private JsonRpcMessage handleRequest(JsonRpcMessage request) {
            // Check for custom handler
            Consumer<JsonRpcMessage> handler = responseHandlers.remove(request.id());
            if (handler != null) {
                handler.accept(request);
            }

            // Default handling for known methods
            return switch (request.method()) {
                case "initialize" -> handleInitialize(request);
                case "tools/list" -> handleToolsList(request);
                case "launch_workflow" -> handleLaunchWorkflow(request);
                case "cancel_workflow" -> handleCancelWorkflow(request);
                case "query_workflows" -> handleQueryWorkflows(request);
                case "manage_workitems" -> handleManageWorkItems(request);
                default -> JsonRpcMessage.error(request.id(), JsonRpcError.methodNotFound(request.method()));
            };
        }

        private JsonRpcMessage handleInitialize(JsonRpcMessage request) {
            Map<String, Object> result = Map.of(
                "protocolVersion", MCP_PROTOCOL_VERSION,
                "capabilities", Map.of(
                    "tools", Map.of(),
                    "resources", Map.of(),
                    "prompts", Map.of()
                ),
                "serverInfo", Map.of(
                    "name", "YAWL-MCP-Server",
                    "version", "6.0.0"
                )
            );
            return JsonRpcMessage.response(request.id(), result);
        }

        private JsonRpcMessage handleToolsList(JsonRpcMessage request) {
            Map<String, Object> result = Map.of(
                "tools", List.of(
                    Map.of("name", "launch_workflow", "description", "Launch a workflow"),
                    Map.of("name", "cancel_workflow", "description", "Cancel a workflow"),
                    Map.of("name", "query_workflows", "description", "Query workflows"),
                    Map.of("name", "manage_workitems", "description", "Manage work items")
                )
            );
            return JsonRpcMessage.response(request.id(), result);
        }

        private JsonRpcMessage handleLaunchWorkflow(JsonRpcMessage request) {
            Map<String, Object> params = request.params();
            String specId = params != null ? (String) params.get("specificationId") : null;

            if (specId == null || specId.isBlank()) {
                return JsonRpcMessage.error(request.id(), JsonRpcError.invalidParams("specificationId is required"));
            }

            Map<String, Object> result = Map.of(
                "caseId", TestDataGenerator.generateCaseId(),
                "status", "launched",
                "specId", specId
            );
            return JsonRpcMessage.response(request.id(), result);
        }

        private JsonRpcMessage handleCancelWorkflow(JsonRpcMessage request) {
            Map<String, Object> params = request.params();
            String caseId = params != null ? (String) params.get("caseId") : null;

            if (caseId == null || caseId.isBlank()) {
                return JsonRpcMessage.error(request.id(), JsonRpcError.invalidParams("caseId is required"));
            }

            Map<String, Object> result = Map.of(
                "caseId", caseId,
                "status", "cancelled"
            );
            return JsonRpcMessage.response(request.id(), result);
        }

        private JsonRpcMessage handleQueryWorkflows(JsonRpcMessage request) {
            Map<String, Object> result = Map.of(
                "workflows", List.of(
                    Map.of("caseId", "case-1", "status", "running"),
                    Map.of("caseId", "case-2", "status", "completed")
                )
            );
            return JsonRpcMessage.response(request.id(), result);
        }

        private JsonRpcMessage handleManageWorkItems(JsonRpcMessage request) {
            Map<String, Object> params = request.params();
            String workItemId = params != null ? (String) params.get("workItemId") : null;

            if (workItemId == null || workItemId.isBlank()) {
                return JsonRpcMessage.error(request.id(), JsonRpcError.invalidParams("workItemId is required"));
            }

            Map<String, Object> result = Map.of(
                "workItemId", workItemId,
                "status", "updated"
            );
            return JsonRpcMessage.response(request.id(), result);
        }
    }

    /**
     * Test MCP client implementation.
     */
    protected static class TestMcpClient {
        private final TestMcpTransport transport;
        private volatile boolean connected = false;

        public TestMcpClient(TestMcpTransport transport) {
            this.transport = transport;
        }

        /**
         * Sends a request and waits for response.
         *
         * @param request request message
         * @param timeout timeout duration
         * @return response message
         * @throws TimeoutException if timeout occurs
         * @throws InterruptedException if interrupted
         */
        public JsonRpcMessage sendRequest(JsonRpcMessage request, Duration timeout)
                throws TimeoutException, InterruptedException {
            CompletableFuture<JsonRpcMessage> future = new CompletableFuture<>();
            transport.registerPendingRequest(request.id(), future);

            transport.sendFromClient(request);

            JsonRpcMessage response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response == null) {
                throw new TimeoutException("No response received within " + timeout);
            }
            return response;
        }

        /**
         * Disconnects the client.
         */
        public void disconnect() {
            connected = false;
        }

        /**
         * Checks if connected.
         *
         * @return true if connected
         */
        public boolean isConnected() {
            return connected;
        }
    }
}
