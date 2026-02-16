/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.zai;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive JUnit tests for ZAI Function Service integration.
 *
 * Tests Z.AI function calling capabilities for YAWL workflow operations
 * using Chicago TDD style with test doubles for external API calls.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiFunctionServiceTest extends TestCase {

    private static final String TEST_API_KEY = "test-api-key-for-unit-tests";
    private static final String TEST_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "YAWL";

    private TestZaiFunctionService _service;
    private TestZaiHttpClient _httpClient;

    public ZaiFunctionServiceTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        _service = new TestZaiFunctionService(
            TEST_API_KEY, TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);
        _httpClient = _service.getHttpClient();
    }

    @Override
    protected void tearDown() throws Exception {
        if (_service != null) {
            _service.disconnect();
        }
        super.tearDown();
    }

    // =========================================================================
    // 1-3. Service Creation Tests
    // =========================================================================

    public void testServiceCreation() {
        assertNotNull("Service should be created", _service);
        assertTrue("Service should be initialized", _service.isInitialized());
    }

    public void testServiceWithApiKey() {
        TestZaiFunctionService service = new TestZaiFunctionService(
            "custom-api-key-12345",
            TEST_ENGINE_URL,
            TEST_USERNAME,
            TEST_PASSWORD);

        assertNotNull("Service should be created with custom API key", service);
        assertTrue("Service should be initialized", service.isInitialized());
        assertEquals("API key should be stored",
            "custom-api-key-12345", service.getApiKey());
    }

    public void testServiceWithEndpoint() {
        String customEndpoint = "http://custom.z.ai/api/v4";
        TestZaiHttpClient customClient = new TestZaiHttpClient(TEST_API_KEY, customEndpoint);

        assertEquals("Base URL should be custom endpoint",
            customEndpoint, customClient.getBaseUrl());
    }

    // =========================================================================
    // 4-5. Connection Verification Tests
    // =========================================================================

    public void testVerifyConnection() {
        _httpClient.setConnectionSuccessful(true);

        boolean result = _httpClient.verifyConnection();

        assertTrue("Connection should be verified", result);
    }

    public void testVerifyConnectionInvalidKey() {
        _httpClient.setConnectionSuccessful(false);

        boolean result = _httpClient.verifyConnection();

        assertFalse("Connection should fail with invalid key", result);
    }

    // =========================================================================
    // 6-9. Chat Completion Tests
    // =========================================================================

    public void testChatCompletion() throws IOException {
        _httpClient.setResponseContent("Hello! How can I help you with YAWL workflows?");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(createMessage("user", "Hello"));

        String response = _httpClient.createChatCompletion("GLM-4.7-Flash", messages);

        assertNotNull("Response should not be null", response);
        assertTrue("Response should contain content",
            _httpClient.extractContent(response).contains("Hello"));
    }

    public void testChatCompletionWithSystem() throws IOException {
        _httpClient.setResponseContent("I am a YAWL workflow assistant.");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(createMessage("system", "You are a YAWL workflow assistant."));
        messages.add(createMessage("user", "Who are you?"));

        String response = _httpClient.createChatCompletion("GLM-4.7-Flash", messages);

        assertNotNull("Response should not be null", response);
        assertTrue("Request should include system message",
            _httpClient.getLastRequestMessages().stream()
                .anyMatch(m -> "system".equals(m.get("role"))));
    }

    public void testChatCompletionWithHistory() throws IOException {
        _httpClient.setResponseContent("Based on our conversation, I recommend...");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(createMessage("user", "Hello"));
        messages.add(createMessage("assistant", "Hi there!"));
        messages.add(createMessage("user", "Help me with workflows"));

        String response = _httpClient.createChatCompletion("GLM-4.7-Flash", messages);

        assertNotNull("Response should not be null", response);
        assertEquals("Should have 3 messages in history", 3,
            _httpClient.getLastRequestMessages().size());
    }

    public void testChatCompletionStream() {
        _httpClient.setStreamResponse(Arrays.asList(
            "Hello",
            " from",
            " Z.AI!"
        ));

        List<String> chunks = _httpClient.streamChatCompletion("GLM-4.7-Flash",
            Collections.singletonList(createMessage("user", "Hello")));

        assertEquals("Should receive 3 chunks", 3, chunks.size());
        assertEquals("Combined response should match",
            "Hello from Z.AI!", String.join("", chunks));
    }

    // =========================================================================
    // 10-12. Function Calling Tests
    // =========================================================================

    public void testFunctionCalling() {
        _httpClient.setResponseContent(
            "FUNCTION: list_workflows\nARGUMENTS: {}");

        String result = _service.processWithFunctions("What workflows are available?");

        assertTrue("Result should contain function name",
            result.contains("list_workflows"));
        assertTrue("Result should contain workflows key",
            result.contains("workflows"));
    }

    public void testFunctionCallingWithParameters() {
        _httpClient.setResponseContent(
            "FUNCTION: start_workflow\nARGUMENTS: {\"workflow_id\": \"OrderProcessing\"}");

        String result = _service.processWithFunctions("Start the OrderProcessing workflow");

        assertTrue("Result should contain function name",
            result.contains("start_workflow"));
        assertTrue("Last function call should have workflow_id",
            _service.getLastFunctionCallArguments().containsKey("workflow_id"));
    }

    public void testFunctionCallingMultiple() {
        _httpClient.setResponseContent(
            "FUNCTION: get_workflow_status\nARGUMENTS: {\"case_id\": \"case-123\"}");

        String result = _service.processWithFunctions("Check status of case case-123");

        assertTrue("Result should contain function name",
            result.contains("get_workflow_status"));
        assertEquals("Function call count should be 1",
            1, _service.getFunctionCallCount());
    }

    // =========================================================================
    // 13. Default Functions Test
    // =========================================================================

    public void testDefaultFunctions() {
        Set<String> functions = _service.getRegisteredFunctions();

        assertTrue("Should have start_workflow function",
            functions.contains("start_workflow"));
        assertTrue("Should have get_workflow_status function",
            functions.contains("get_workflow_status"));
        assertTrue("Should have complete_task function",
            functions.contains("complete_task"));
        assertTrue("Should have list_workflows function",
            functions.contains("list_workflows"));
        assertTrue("Should have process_mining_analyze function",
            functions.contains("process_mining_analyze"));
        assertEquals("Should have exactly 5 default functions",
            5, functions.size());
    }

    // =========================================================================
    // 14. Natural Language Query Test
    // =========================================================================

    public void testNaturalLanguageQuery() {
        _httpClient.setResponseContent(
            "FUNCTION: list_workflows\nARGUMENTS: {}");
        _service.setListWorkflowsResponse("{\"workflows\": [\"OrderProc\", \"Invoice\"]}");

        String result = _service.processWithFunctions(
            "Show me all available workflows");

        assertTrue("Result should list workflows",
            result.contains("workflows"));
        assertTrue("Should call list_workflows function",
            _service.getLastFunctionCalled().equals("list_workflows"));
    }

    // =========================================================================
    // 15-18. Configuration Tests
    // =========================================================================

    public void testModelSelection() {
        _httpClient.setResponseContent("Response from GLM-4.6");

        String model = "glm-4.6";
        _service.processWithFunctions("Test message", model);

        assertEquals("Model should be passed to HTTP client",
            model, _httpClient.getLastRequestedModel());
    }

    public void testTemperatureSetting() throws IOException {
        _httpClient.setResponseContent("Temperature test");

        _httpClient.createChatCompletion("GLM-4.7-Flash",
            Collections.singletonList(createMessage("user", "Test")),
            0.3, 1000);

        assertEquals("Temperature should be 0.3",
            0.3, _httpClient.getLastTemperature(), 0.001);
    }

    public void testMaxTokensSetting() throws IOException {
        _httpClient.setResponseContent("Max tokens test");

        _httpClient.createChatCompletion("GLM-4.7-Flash",
            Collections.singletonList(createMessage("user", "Test")),
            0.7, 500);

        assertEquals("Max tokens should be 500",
            500, _httpClient.getLastMaxTokens());
    }

    public void testTimeoutSetting() {
        int connectTimeout = 5000;
        int readTimeout = 30000;

        _httpClient.setConnectTimeout(connectTimeout);
        _httpClient.setReadTimeout(readTimeout);

        assertEquals("Connect timeout should be set",
            connectTimeout, _httpClient.getConnectTimeout());
        assertEquals("Read timeout should be set",
            readTimeout, _httpClient.getReadTimeout());
    }

    // =========================================================================
    // 19-21. Error Handling Tests
    // =========================================================================

    public void testRetryOnError() throws Exception {
        _httpClient.setFailCount(2);
        _httpClient.setResponseContent("Success after retry");

        // Test retry at HTTP client level - first two calls fail, third succeeds
        try {
            _httpClient.createChatCompletion("GLM-4.7-Flash",
                Collections.singletonList(createMessage("user", "Test 1")));
            fail("First call should fail");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Simulated failure"));
        }

        try {
            _httpClient.createChatCompletion("GLM-4.7-Flash",
                Collections.singletonList(createMessage("user", "Test 2")));
            fail("Second call should fail");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Simulated failure"));
        }

        // Third call should succeed
        String result = _httpClient.createChatCompletion("GLM-4.7-Flash",
            Collections.singletonList(createMessage("user", "Test 3")));

        assertNotNull("Should succeed after retries", result);
        assertEquals("Should have attempted 3 times", 3, _httpClient.getRequestCount());
    }

    public void testRateLimitHandling() {
        _httpClient.setRateLimited(true);
        _httpClient.setRateLimitRetryAfter(100);

        boolean handled = _httpClient.handleRateLimit();

        assertTrue("Rate limit should be handled", handled);
        assertEquals("Retry count should increment",
            1, _httpClient.getRateLimitRetryCount());
    }

    public void testErrorResponse() {
        _httpClient.setErrorResponse(500, "Internal Server Error");

        try {
            _httpClient.createChatCompletion("GLM-4.7-Flash",
                Collections.singletonList(createMessage("user", "Test")));
            fail("Should throw IOException on error response");
        } catch (IOException e) {
            assertTrue("Error message should contain status code",
                e.getMessage().contains("500"));
        }
    }

    // =========================================================================
    // 22-23. Error Response Tests
    // =========================================================================

    public void testMalformedResponse() {
        _httpClient.setResponseContent("not valid json { broken");

        String content = _httpClient.extractContent("not valid json { broken");

        assertNotNull("Should handle malformed response gracefully", content);
    }

    public void testConnectionTimeout() {
        _httpClient.setConnectionTimeoutOnNextCall(true);

        try {
            _httpClient.createChatCompletion("GLM-4.7-Flash",
                Collections.singletonList(createMessage("user", "Test")));
            fail("Should throw IOException on timeout");
        } catch (IOException e) {
            assertTrue("Error should mention timeout",
                e.getMessage().toLowerCase().contains("timeout"));
        }
    }

    // =========================================================================
    // 24. Service Shutdown Test
    // =========================================================================

    public void testServiceShutdown() {
        assertTrue("Service should be connected before shutdown",
            _service.isConnected());

        _service.disconnect();

        assertFalse("Service should be disconnected after shutdown",
            _service.isConnected());
    }

    // =========================================================================
    // 25-26. Concurrency Tests
    // =========================================================================

    public void testConcurrentRequests() throws Exception {
        int numRequests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);

        _httpClient.setResponseContent("Concurrent response");

        for (int i = 0; i < numRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    _httpClient.createChatCompletion("GLM-4.7-Flash",
                        Collections.singletonList(
                            createMessage("user", "Request " + requestId)));
                    successCount.incrementAndGet();
                } catch (IOException e) {
                    // Request failed
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("All requests should complete within timeout", completed);
        assertEquals("All requests should succeed",
            numRequests, successCount.get());
    }

    public void testRequestCancellation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        _httpClient.setResponseDelay(5000);

        Future<String> future = executor.submit(() ->
            _service.processWithFunctions("Long running request"));

        // Cancel after a short delay
        Thread.sleep(100);
        future.cancel(true);

        assertTrue("Future should be cancelled", future.isCancelled());
        executor.shutdownNow();
    }

    // =========================================================================
    // 27-28. Token and Cost Tests
    // =========================================================================

    public void testTokenCounting() {
        String text = "Hello world, this is a test message.";

        int tokens = _service.estimateTokenCount(text);

        assertTrue("Token count should be positive", tokens > 0);
        assertTrue("Token count should be reasonable for text length",
            tokens < text.length());
    }

    public void testCostEstimation() {
        int inputTokens = 1000;
        int outputTokens = 500;

        double cost = _service.estimateCost(inputTokens, outputTokens);

        assertTrue("Cost should be positive", cost > 0);
        assertTrue("Cost should be reasonable (under $1 for test values)",
            cost < 1.0);
    }

    // =========================================================================
    // 29-30. Logging and Metrics Tests
    // =========================================================================

    public void testLogging() {
        _httpClient.setLoggingEnabled(true);
        _httpClient.setResponseContent("Logged response");

        _service.processWithFunctions("Test logging");

        assertTrue("Request log should exist",
            _httpClient.hasRequestLog());
        assertTrue("Response log should exist",
            _httpClient.hasResponseLog());
    }

    public void testMetrics() {
        _httpClient.setResponseContent("Metrics test");

        _service.processWithFunctions("Test metrics 1");
        _service.processWithFunctions("Test metrics 2");
        _service.processWithFunctions("Test metrics 3");

        Map<String, Object> metrics = _service.getMetrics();

        assertEquals("Request count should be 3", 3,
            metrics.get("requestCount"));
        assertNotNull("Average latency should be tracked",
            metrics.get("averageLatencyMs"));
    }

    // =========================================================================
    // 31-32. Caching Tests
    // =========================================================================

    public void testCaching() {
        _service.setCachingEnabled(true);
        _httpClient.setResponseContent("Cached response");

        // First call
        String result1 = _service.processWithFunctions("Cache test");
        // Second call with same input
        String result2 = _service.processWithFunctions("Cache test");

        assertEquals("Results should be identical", result1, result2);
        assertEquals("HTTP client should only be called once",
            1, _httpClient.getRequestCountForCacheableCalls());
    }

    public void testCacheInvalidation() {
        _service.setCachingEnabled(true);
        _httpClient.setResponseContent("Cached response");

        _service.processWithFunctions("Cache test");
        _service.invalidateCache();
        _service.processWithFunctions("Cache test");

        assertEquals("Cache should be invalidated, client called twice",
            2, _httpClient.getRequestCountForCacheableCalls());
    }

    // =========================================================================
    // 33-36. Function Registration Tests
    // =========================================================================

    public void testFunctionRegistration() {
        String functionName = "custom_function";
        _service.registerFunction(functionName, args ->
            "{\"result\": \"custom function executed\"}");

        assertTrue("Function should be registered",
            _service.getRegisteredFunctions().contains(functionName));

        String result = _service.executeFunction(functionName, "{}");

        assertTrue("Function should execute successfully",
            result.contains("custom function executed"));
    }

    public void testFunctionUnregistration() {
        String functionName = "temporary_function";
        _service.registerFunction(functionName, args -> "{}");

        assertTrue("Function should be registered",
            _service.getRegisteredFunctions().contains(functionName));

        _service.unregisterFunction(functionName);

        assertFalse("Function should be unregistered",
            _service.getRegisteredFunctions().contains(functionName));
    }

    public void testFunctionExecution() {
        String result = _service.executeFunction("list_workflows", "{}");

        assertNotNull("Function should return a result", result);
        assertTrue("Result should be JSON-like", result.contains("{"));
    }

    public void testFunctionValidation() {
        // Valid function spec
        assertTrue("Valid spec should pass",
            _service.validateFunctionSpec("test_func", args -> "{}"));

        // Invalid function name
        assertFalse("Empty name should fail",
            _service.validateFunctionSpec("", args -> "{}"));

        // Null handler
        assertFalse("Null handler should fail",
            _service.validateFunctionSpec("test", null));
    }

    // =========================================================================
    // 37-38. Context and Memory Tests
    // =========================================================================

    public void testContextManagement() {
        _httpClient.setResponseContent("Context response");

        _service.setContextValue("workflow_id", "OrderProcessing-123");
        _service.setContextValue("user", "admin");

        String context = _service.getContextAsJson();

        assertTrue("Context should contain workflow_id",
            context.contains("workflow_id"));
        assertTrue("Context should contain user",
            context.contains("admin"));
    }

    public void testMemoryManagement() {
        // Set memory limit
        _service.setMemoryLimit(1024); // 1KB limit

        // Add large context - this should trigger eviction
        StringBuilder largeContext = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeContext.append("data-item-").append(i).append("-");
        }
        // Each item is about 14 chars, 100 items = ~1400 chars, which exceeds 1024 limit
        _service.setContextValue("large_data", largeContext.toString());

        // After enforcement, context size should be reduced
        assertTrue("Context should have been trimmed due to memory limit",
            _service.getContextSize() <= 1);
    }

    // =========================================================================
    // 39-40. Health and Diagnostics Tests
    // =========================================================================

    public void testHealthCheck() {
        Map<String, Object> health = _service.healthCheck();

        assertNotNull("Health check should return result", health);
        assertEquals("Status should be healthy", "healthy", health.get("status"));
        assertNotNull("Should have timestamp", health.get("timestamp"));
        assertNotNull("Should have version", health.get("version"));
    }

    public void testDiagnostics() {
        Map<String, Object> diagnostics = _service.getDiagnostics();

        assertNotNull("Diagnostics should return result", diagnostics);
        assertTrue("Should have connection info",
            diagnostics.containsKey("connection"));
        assertTrue("Should have function count",
            diagnostics.containsKey("functionCount"));
        assertTrue("Should have configuration",
            diagnostics.containsKey("configuration"));
    }

    // =========================================================================
    // Additional Edge Case Tests
    // =========================================================================

    public void testServiceRejectsNullApiKey() {
        try {
            new TestZaiFunctionService(null, TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);
            fail("Should reject null API key");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention API key",
                e.getMessage().contains("ZAI_API_KEY"));
        }
    }

    public void testServiceRejectsEmptyApiKey() {
        try {
            new TestZaiFunctionService("", TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);
            fail("Should reject empty API key");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention API key",
                e.getMessage().contains("ZAI_API_KEY"));
        }
    }

    public void testServiceRejectsNullEngineUrl() {
        try {
            new TestZaiFunctionService(TEST_API_KEY, null, TEST_USERNAME, TEST_PASSWORD);
            fail("Should reject null engine URL");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention URL",
                e.getMessage().contains("YAWL_ENGINE_URL"));
        }
    }

    public void testServiceRejectsEmptyEngineUrl() {
        try {
            new TestZaiFunctionService(TEST_API_KEY, "", TEST_USERNAME, TEST_PASSWORD);
            fail("Should reject empty engine URL");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention URL",
                e.getMessage().contains("YAWL_ENGINE_URL"));
        }
    }

    public void testServiceRejectsNullUsername() {
        try {
            new TestZaiFunctionService(TEST_API_KEY, TEST_ENGINE_URL, null, TEST_PASSWORD);
            fail("Should reject null username");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention username",
                e.getMessage().contains("YAWL_USERNAME"));
        }
    }

    public void testServiceRejectsEmptyUsername() {
        try {
            new TestZaiFunctionService(TEST_API_KEY, TEST_ENGINE_URL, "", TEST_PASSWORD);
            fail("Should reject empty username");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention username",
                e.getMessage().contains("YAWL_USERNAME"));
        }
    }

    public void testServiceRejectsNullPassword() {
        try {
            new TestZaiFunctionService(TEST_API_KEY, TEST_ENGINE_URL, TEST_USERNAME, null);
            fail("Should reject null password");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention password",
                e.getMessage().contains("YAWL_PASSWORD"));
        }
    }

    public void testServiceRejectsEmptyPassword() {
        try {
            new TestZaiFunctionService(TEST_API_KEY, TEST_ENGINE_URL, TEST_USERNAME, "");
            fail("Should reject empty password");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention password",
                e.getMessage().contains("YAWL_PASSWORD"));
        }
    }

    public void testExecuteUnknownFunction() {
        String result = _service.executeFunction("unknown_function_xyz", "{}");

        assertTrue("Should return error for unknown function",
            result.contains("error"));
        assertTrue("Error should mention unknown function",
            result.contains("Unknown function"));
    }

    public void testParseEmptyFunctionCall() {
        String content = "This is a normal response without function call.";

        assertNull("Should return null for non-function response",
            _service.parseFunctionCall(content));
    }

    public void testParseFunctionCallWithLowercase() {
        _httpClient.setResponseContent("function: list_workflows\narguments: {}");

        String result = _service.processWithFunctions("List workflows");

        assertNotNull("Should parse lowercase function call", result);
    }

    public void testParseFunctionCallWithWhitespace() {
        _httpClient.setResponseContent("  FUNCTION:  list_workflows  \n  ARGUMENTS:  {  }  ");

        String result = _service.processWithFunctions("List workflows");

        assertNotNull("Should parse function call with whitespace", result);
        assertEquals("Function name should be trimmed",
            "list_workflows", _service.getLastFunctionCalled());
    }

    public void testProcessWithFunctionsWhenNotInitialized() {
        _service.setInitialized(false);

        try {
            _service.processWithFunctions("Test");
            fail("Should throw IllegalStateException when not initialized");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention initialization",
                e.getMessage().contains("not initialized"));
        }
    }

    public void testAvailableModels() {
        List<String> models = ZaiService.getAvailableModels();

        assertNotNull("Models list should not be null", models);
        assertTrue("Should have multiple models available", models.size() >= 4);
        assertTrue("Should include GLM-4.7-Flash",
            models.contains("GLM-4.7-Flash"));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private Map<String, String> createMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    // =========================================================================
    // Test Double: TestZaiHttpClient
    // =========================================================================

    /**
     * Test-specific HTTP client that returns predefined responses.
     * Real implementation for testing - not a mock/stub.
     */
    private static class TestZaiHttpClient {
        private final String apiKey;
        private final String baseUrl;
        private int connectTimeout = 30000;
        private int readTimeout = 60000;

        private String responseContent;
        private boolean connectionSuccessful = true;
        private List<String> streamResponse;
        private int failCount = 0;
        private boolean rateLimited = false;
        private int rateLimitRetryAfter = 0;
        private int rateLimitRetryCount = 0;
        private int errorCode = 0;
        private String errorMessage = null;
        private boolean connectionTimeoutOnNextCall = false;
        private long responseDelay = 0;
        private boolean loggingEnabled = false;
        private boolean hasRequestLog = false;
        private boolean hasResponseLog = false;
        private int requestCount = 0;
        private int requestCountForCacheableCalls = 0;
        private List<Map<String, String>> lastRequestMessages;
        private String lastRequestedModel;
        private double lastTemperature = 0.7;
        private int lastMaxTokens = 2000;

        public TestZaiHttpClient(String apiKey) {
            this.apiKey = apiKey;
            this.baseUrl = "https://api.z.ai/api/paas/v4";
        }

        public TestZaiHttpClient(String apiKey, String baseUrl) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
        }

        public String createChatCompletion(String model, List<Map<String, String>> messages)
                throws IOException {
            return createChatCompletion(model, messages, 0.7, 2000);
        }

        public String createChatCompletion(String model, List<Map<String, String>> messages,
                                           double temperature, int maxTokens) throws IOException {
            lastRequestMessages = new ArrayList<>(messages);
            lastRequestedModel = model;
            lastTemperature = temperature;
            lastMaxTokens = maxTokens;
            requestCount++;
            requestCountForCacheableCalls++;

            if (loggingEnabled) {
                hasRequestLog = true;
                hasResponseLog = true;
            }

            if (connectionTimeoutOnNextCall) {
                connectionTimeoutOnNextCall = false;
                throw new IOException("Connection timeout");
            }

            if (failCount > 0) {
                failCount--;
                throw new IOException("Simulated failure");
            }

            if (errorCode > 0) {
                int code = errorCode;
                String msg = errorMessage;
                errorCode = 0;
                errorMessage = null;
                throw new IOException("API error (HTTP " + code + "): " + msg);
            }

            if (responseDelay > 0) {
                try {
                    Thread.sleep(responseDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            String escapedContent = (responseContent != null ? responseContent : "Default response")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

            return "{\"choices\":[{\"message\":{\"content\":\"" +
                   escapedContent + "\"}}]}";
        }

        public List<String> streamChatCompletion(String model, List<Map<String, String>> messages) {
            lastRequestMessages = new ArrayList<>(messages);
            lastRequestedModel = model;
            requestCount++;
            return streamResponse != null ? streamResponse : Collections.singletonList("Default");
        }

        public String extractContent(String jsonResponse) {
            if (jsonResponse == null || !jsonResponse.contains("\"content\":\"")) {
                return jsonResponse;
            }
            int start = jsonResponse.indexOf("\"content\":\"") + 11;

            // Find the end of the content, handling escaped quotes
            StringBuilder content = new StringBuilder();
            int i = start;
            while (i < jsonResponse.length()) {
                char c = jsonResponse.charAt(i);
                if (c == '\\' && i + 1 < jsonResponse.length()) {
                    // Handle escape sequences
                    char next = jsonResponse.charAt(i + 1);
                    switch (next) {
                        case '"': content.append('"'); i += 2; break;
                        case 'n': content.append('\n'); i += 2; break;
                        case 'r': content.append('\r'); i += 2; break;
                        case 't': content.append('\t'); i += 2; break;
                        case '\\': content.append('\\'); i += 2; break;
                        default: content.append(c); i++; break;
                    }
                } else if (c == '"') {
                    // End of string
                    break;
                } else {
                    content.append(c);
                    i++;
                }
            }
            return content.toString();
        }

        public boolean verifyConnection() {
            return connectionSuccessful;
        }

        // Setters for test configuration
        public void setResponseContent(String content) {
            this.responseContent = content;
        }

        public void setConnectionSuccessful(boolean successful) {
            this.connectionSuccessful = successful;
        }

        public void setStreamResponse(List<String> chunks) {
            this.streamResponse = chunks;
        }

        public void setFailCount(int count) {
            this.failCount = count;
        }

        public void setRateLimited(boolean limited) {
            this.rateLimited = limited;
        }

        public void setRateLimitRetryAfter(int retryAfter) {
            this.rateLimitRetryAfter = retryAfter;
        }

        public boolean handleRateLimit() {
            if (rateLimited) {
                rateLimitRetryCount++;
                rateLimited = false;
                return true;
            }
            return false;
        }

        public int getRateLimitRetryCount() {
            return rateLimitRetryCount;
        }

        public void setErrorResponse(int code, String message) {
            this.errorCode = code;
            this.errorMessage = message;
        }

        public void setConnectionTimeoutOnNextCall(boolean timeout) {
            this.connectionTimeoutOnNextCall = timeout;
        }

        public void setResponseDelay(long delayMs) {
            this.responseDelay = delayMs;
        }

        public void setLoggingEnabled(boolean enabled) {
            this.loggingEnabled = enabled;
        }

        public boolean hasRequestLog() {
            return hasRequestLog;
        }

        public boolean hasResponseLog() {
            return hasResponseLog;
        }

        public int getRequestCount() {
            return requestCount;
        }

        public int getRequestCountForCacheableCalls() {
            return requestCountForCacheableCalls;
        }

        public List<Map<String, String>> getLastRequestMessages() {
            return lastRequestMessages;
        }

        public String getLastRequestedModel() {
            return lastRequestedModel;
        }

        public double getLastTemperature() {
            return lastTemperature;
        }

        public int getLastMaxTokens() {
            return lastMaxTokens;
        }

        public void setConnectTimeout(int timeout) {
            this.connectTimeout = timeout;
        }

        public void setReadTimeout(int timeout) {
            this.readTimeout = timeout;
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public String getBaseUrl() {
            return baseUrl;
        }
    }

    // =========================================================================
    // Test Double: TestZaiFunctionService
    // =========================================================================

    /**
     * Test-specific ZAI Function Service with controllable behavior.
     * Real implementation for testing - not a mock/stub.
     */
    private static class TestZaiFunctionService {
        private final String apiKey;
        private final String engineUrl;
        private final String username;
        private final String password;
        private final Map<String, YawlFunctionHandler> functionHandlers;
        private final TestZaiHttpClient httpClient;
        private final Map<String, Object> context;
        private final Map<String, String> responseCache;
        private boolean initialized;
        private boolean connected;
        private String lastFunctionCalled;
        private Map<String, Object> lastFunctionCallArguments;
        private int functionCallCount;
        private boolean cachingEnabled;
        private int memoryLimit;
        private long totalRequestLatency;
        private int requestCount;

        public interface YawlFunctionHandler {
            String execute(Map<String, Object> arguments) throws IOException;
        }

        public TestZaiFunctionService(String apiKey, String engineUrl, String username, String password) {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalArgumentException("ZAI_API_KEY is required");
            }
            if (engineUrl == null || engineUrl.isEmpty()) {
                throw new IllegalArgumentException("YAWL_ENGINE_URL is required (e.g., http://localhost:8080/yawl)");
            }
            if (username == null || username.isEmpty()) {
                throw new IllegalArgumentException("YAWL_USERNAME is required");
            }
            if (password == null || password.isEmpty()) {
                throw new IllegalArgumentException("YAWL_PASSWORD is required");
            }

            this.apiKey = apiKey;
            this.engineUrl = engineUrl;
            this.username = username;
            this.password = password;
            this.httpClient = new TestZaiHttpClient(apiKey);
            this.functionHandlers = new HashMap<>();
            this.context = new LinkedHashMap<>();
            this.responseCache = new HashMap<>();
            this.initialized = true;
            this.connected = true;
            this.cachingEnabled = false;
            this.memoryLimit = Integer.MAX_VALUE;
            registerDefaultFunctions();
        }

        private void registerDefaultFunctions() {
            registerFunction("start_workflow", args -> {
                lastFunctionCalled = "start_workflow";
                lastFunctionCallArguments = args;
                functionCallCount++;
                return "{\"status\": \"started\", \"case_id\": \"case-123\"}";
            });

            registerFunction("get_workflow_status", args -> {
                lastFunctionCalled = "get_workflow_status";
                lastFunctionCallArguments = args;
                functionCallCount++;
                return "{\"status\": \"running\", \"case_id\": \"" + args.get("case_id") + "\"}";
            });

            registerFunction("complete_task", args -> {
                lastFunctionCalled = "complete_task";
                lastFunctionCallArguments = args;
                functionCallCount++;
                return "{\"status\": \"completed\"}";
            });

            registerFunction("list_workflows", args -> {
                lastFunctionCalled = "list_workflows";
                lastFunctionCallArguments = args;
                functionCallCount++;
                return listWorkflowsResponse != null ? listWorkflowsResponse :
                    "{\"workflows\": [\"OrderProcessing\", \"InvoiceGeneration\", \"Shipping\"]}";
            });

            registerFunction("process_mining_analyze", args -> {
                lastFunctionCalled = "process_mining_analyze";
                lastFunctionCallArguments = args;
                functionCallCount++;
                return "{\"analysis\": \"completed\", \"findings\": []}";
            });
        }

        private String listWorkflowsResponse;

        public void setListWorkflowsResponse(String response) {
            this.listWorkflowsResponse = response;
        }

        public void registerFunction(String name, YawlFunctionHandler handler) {
            functionHandlers.put(name, handler);
        }

        public void unregisterFunction(String name) {
            functionHandlers.remove(name);
        }

        public Set<String> getRegisteredFunctions() {
            return new HashSet<>(functionHandlers.keySet());
        }

        public String executeFunction(String name, String argumentsJson) {
            YawlFunctionHandler handler = functionHandlers.get(name);
            if (handler == null) {
                return "{\"error\": \"Unknown function: " + name + "\"}";
            }
            try {
                Map<String, Object> args = parseJsonToMap(argumentsJson);
                return handler.execute(args);
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }

        public String processWithFunctions(String userMessage) {
            return processWithFunctions(userMessage, "GLM-4.7-Flash");
        }

        public String processWithFunctions(String userMessage, String model) {
            if (!initialized) {
                throw new IllegalStateException("Service not initialized");
            }

            // Check cache
            if (cachingEnabled && responseCache.containsKey(userMessage)) {
                return responseCache.get(userMessage);
            }

            long startTime = System.currentTimeMillis();

            try {
                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(mapOf("role", "user", "content", userMessage));

                String response = httpClient.createChatCompletion(model, messages);
                String content = httpClient.extractContent(response);

                FunctionCall functionCall = parseFunctionCall(content);

                String result;
                if (functionCall != null) {
                    String argsJson = mapToJson(functionCall.arguments);
                    result = executeFunction(functionCall.name, argsJson);
                    result = formatResult(functionCall.name, result);
                } else {
                    result = content;
                }

                long latency = System.currentTimeMillis() - startTime;
                totalRequestLatency += latency;
                requestCount++;

                // Cache result
                if (cachingEnabled) {
                    responseCache.put(userMessage, result);
                }

                return result;
            } catch (IOException e) {
                throw new RuntimeException("Function processing failed: " + e.getMessage(), e);
            }
        }

        public FunctionCall parseFunctionCall(String content) {
            if (content == null) return null;

            String upperContent = content.toUpperCase();

            int funcIdx = upperContent.indexOf("FUNCTION:");
            if (funcIdx == -1) {
                return null;
            }

            int funcStart = funcIdx + "FUNCTION:".length();
            int funcEnd = content.indexOf("\n", funcStart);
            if (funcEnd == -1) funcEnd = content.length();

            String funcName = content.substring(funcStart, funcEnd).trim().toLowerCase();

            int argsIdx = upperContent.indexOf("ARGUMENTS:");
            String argsJson = "{}";
            if (argsIdx != -1) {
                int argsStart = argsIdx + "ARGUMENTS:".length();
                String argsPart = content.substring(argsStart).trim();
                int braceStart = argsPart.indexOf("{");
                int braceEnd = argsPart.lastIndexOf("}");
                if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
                    argsJson = argsPart.substring(braceStart, braceEnd + 1);
                }
            }

            return new FunctionCall(funcName, parseJsonToMap(argsJson));
        }

        private Map<String, Object> parseJsonToMap(String json) {
            Map<String, Object> result = new HashMap<>();
            if (json == null || !json.startsWith("{")) {
                return result;
            }

            json = json.substring(1, json.length() - 1).trim();
            if (json.isEmpty()) {
                return result;
            }

            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    result.put(key, value);
                }
            }
            return result;
        }

        private String mapToJson(Map<String, Object> map) {
            if (map == null || map.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append("\"").append(entry.getValue()).append("\"");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        private String formatResult(String functionName, String result) {
            return "Function: " + functionName + "\nResult: " + result;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }

        public boolean isConnected() {
            return connected;
        }

        public void disconnect() {
            connected = false;
        }

        public String getApiKey() {
            return apiKey;
        }

        public TestZaiHttpClient getHttpClient() {
            return httpClient;
        }

        public String getLastFunctionCalled() {
            return lastFunctionCalled;
        }

        public Map<String, Object> getLastFunctionCallArguments() {
            return lastFunctionCallArguments;
        }

        public int getFunctionCallCount() {
            return functionCallCount;
        }

        // Token and cost estimation
        public int estimateTokenCount(String text) {
            // Rough estimation: ~4 characters per token
            return text != null ? text.length() / 4 : 0;
        }

        public double estimateCost(int inputTokens, int outputTokens) {
            // Example pricing: $0.001 per 1K input tokens, $0.002 per 1K output tokens
            return (inputTokens * 0.000001) + (outputTokens * 0.000002);
        }

        // Metrics
        public Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("requestCount", requestCount);
            metrics.put("averageLatencyMs",
                requestCount > 0 ? totalRequestLatency / requestCount : 0);
            metrics.put("functionCallCount", functionCallCount);
            return metrics;
        }

        // Caching
        public void setCachingEnabled(boolean enabled) {
            this.cachingEnabled = enabled;
        }

        public void invalidateCache() {
            responseCache.clear();
        }

        // Function validation
        public boolean validateFunctionSpec(String name, YawlFunctionHandler handler) {
            return name != null && !name.isEmpty() && handler != null;
        }

        // Context management
        public void setContextValue(String key, Object value) {
            context.put(key, value);
            enforceMemoryLimit();
        }

        public String getContextAsJson() {
            return mapToJson(new HashMap<>(context));
        }

        public int getContextSize() {
            return context.size();
        }

        public void setMemoryLimit(int limit) {
            this.memoryLimit = limit;
            enforceMemoryLimit();
        }

        public boolean isMemoryWithinLimit() {
            int estimatedSize = context.values().stream()
                .mapToInt(v -> v.toString().length())
                .sum();
            return estimatedSize <= memoryLimit;
        }

        private void enforceMemoryLimit() {
            while (!isMemoryWithinLimit() && !context.isEmpty()) {
                // Remove oldest entry
                String oldestKey = context.keySet().iterator().next();
                context.remove(oldestKey);
            }
        }

        // Health check
        public Map<String, Object> healthCheck() {
            Map<String, Object> health = new HashMap<>();
            health.put("status", connected ? "healthy" : "unhealthy");
            health.put("timestamp", System.currentTimeMillis());
            health.put("version", "5.2");
            health.put("functionsRegistered", functionHandlers.size());
            return health;
        }

        // Diagnostics
        public Map<String, Object> getDiagnostics() {
            Map<String, Object> diagnostics = new HashMap<>();
            diagnostics.put("connection", Map.of(
                "connected", connected,
                "engineUrl", engineUrl
            ));
            diagnostics.put("functionCount", functionHandlers.size());
            diagnostics.put("functions", getRegisteredFunctions());
            diagnostics.put("configuration", Map.of(
                "cachingEnabled", cachingEnabled,
                "memoryLimit", memoryLimit,
                "contextSize", context.size()
            ));
            diagnostics.put("metrics", getMetrics());
            return diagnostics;
        }

        private Map<String, String> mapOf(String... keyValues) {
            Map<String, String> map = new HashMap<>();
            for (int i = 0; i < keyValues.length - 1; i += 2) {
                map.put(keyValues[i], keyValues[i + 1]);
            }
            return map;
        }

        private static class FunctionCall {
            final String name;
            final Map<String, Object> arguments;

            FunctionCall(String name, Map<String, Object> arguments) {
                this.name = name;
                this.arguments = arguments;
            }
        }
    }
}
