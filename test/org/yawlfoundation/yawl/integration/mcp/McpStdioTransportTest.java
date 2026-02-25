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
 */

package org.yawlfoundation.yawl.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP STDIO Transport Integration Tests for YAWL.
 *
 * Tests real pipe-based communication, newline-delimited JSON-RPC messages,
 * stderr logging capture, graceful shutdown on EOF, and large message handling.
 *
 * Chicago TDD: Uses real PipedInputStream/PipedOutputStream for process-like
 * STDIO simulation without requiring actual process spawning.
 *
 * Tests MCP 2025-11-25 specification compliance (SDK v1 0.18.0+).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@TestMethodOrder(OrderAnnotation.class)
@Execution(ExecutionMode.CONCURRENT)
public class McpStdioTransportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TEST_TIMEOUT_SECONDS = 30;
    private static final int LARGE_MESSAGE_SIZE = 70 * 1024; // 70KB > 64KB default buffer

    private PipedInputStream clientToServerInput;
    private PipedOutputStream clientToServerOutput;
    private PipedInputStream serverToClientInput;
    private PipedOutputStream serverToClientOutput;
    private ByteArrayOutputStream stderrCapture;
    private ExecutorService executor;
    private AtomicBoolean serverRunning;
    private AtomicReference<String> lastErrorResponse;

    @BeforeEach
    void setUp() throws IOException {
        clientToServerOutput = new PipedOutputStream();
        clientToServerInput = new PipedInputStream(clientToServerOutput, 65536);

        serverToClientOutput = new PipedOutputStream();
        serverToClientInput = new PipedInputStream(serverToClientOutput, 65536);

        stderrCapture = new ByteArrayOutputStream();
        executor = Executors.newVirtualThreadPerTaskExecutor();
        serverRunning = new AtomicBoolean(true);
        lastErrorResponse = new AtomicReference<>();
    }

    @AfterEach
    void tearDown() throws IOException {
        serverRunning.set(false);
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeQuietly(clientToServerInput);
        closeQuietly(clientToServerOutput);
        closeQuietly(serverToClientInput);
        closeQuietly(serverToClientOutput);
        closeQuietly(stderrCapture);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // Quiet close
            }
        }
    }

    // =========================================================================
    // Test 1: Process Spawn and Message Exchange
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Test bidirectional message exchange over STDIO pipes")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testProcessSpawnAndMessageExchange() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        // Simulate MCP initialize request
        String requestId = UUID.randomUUID().toString();
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId);
        request.put("method", "initialize");

        ObjectNode params = request.putObject("params");
        params.put("protocolVersion", "2025-11-25");
        params.putObject("capabilities");
        ObjectNode clientInfo = params.putObject("clientInfo");
        clientInfo.put("name", "test-client");
        clientInfo.put("version", "1.0.0");

        // Write request with newline delimiter
        String requestJson = MAPPER.writeValueAsString(request);
        clientWriter.write(requestJson);
        clientWriter.newLine();
        clientWriter.flush();

        // Simulate server processing and response
        simulateServerResponse(requestId, serverToClientOutput);

        // Read response
        String responseLine = clientReader.readLine();
        assertNotNull(responseLine, "Server should send response");

        JsonNode response = MAPPER.readTree(responseLine);
        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(requestId, response.get("id").asText());
        assertTrue(response.has("result"), "Response should have result");

        JsonNode result = response.get("result");
        assertTrue(result.has("protocolVersion"), "Result should have protocolVersion");
        assertTrue(result.has("capabilities"), "Result should have capabilities");
        assertTrue(result.has("serverInfo"), "Result should have serverInfo");
    }

    private void simulateServerResponse(String requestId, OutputStream serverOutput) throws IOException {
        BufferedWriter serverWriter = new BufferedWriter(
            new OutputStreamWriter(serverOutput, StandardCharsets.UTF_8));

        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", requestId);

        ObjectNode result = response.putObject("result");
        result.put("protocolVersion", "2025-11-25");

        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        capabilities.putObject("resources");
        capabilities.putObject("prompts");
        capabilities.putObject("logging");

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "yawl-mcp-server");
        serverInfo.put("version", "6.0.0");

        serverWriter.write(MAPPER.writeValueAsString(response));
        serverWriter.newLine();
        serverWriter.flush();
    }

    // =========================================================================
    // Test 2: Newline-Delimited Messages
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Test multiple newline-delimited JSON-RPC messages")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testNewlineDelimitedMessages() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        int messageCount = 10;
        List<String> requestIds = new ArrayList<>();

        // Send multiple requests with newline delimiters
        for (int i = 0; i < messageCount; i++) {
            String requestId = "req-" + i;
            requestIds.add(requestId);

            ObjectNode request = MAPPER.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", requestId);
            request.put("method", "tools/list");

            clientWriter.write(MAPPER.writeValueAsString(request));
            clientWriter.newLine();
        }
        clientWriter.flush();

        // Simulate server responses
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                for (String requestId : requestIds) {
                    ObjectNode response = MAPPER.createObjectNode();
                    response.put("jsonrpc", "2.0");
                    response.put("id", requestId);

                    ObjectNode result = response.putObject("result");
                    result.putArray("tools");

                    BufferedWriter serverWriter = new BufferedWriter(
                        new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));
                    serverWriter.write(MAPPER.writeValueAsString(response));
                    serverWriter.newLine();
                    serverWriter.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);

        // Read all responses
        int responseCount = 0;
        for (int i = 0; i < messageCount; i++) {
            String responseLine = clientReader.readLine();
            if (responseLine != null && !responseLine.isBlank()) {
                JsonNode response = MAPPER.readTree(responseLine);
                assertEquals("2.0", response.get("jsonrpc").asText());
                assertEquals(requestIds.get(i), response.get("id").asText());
                responseCount++;
            }
        }

        serverTask.get(10, TimeUnit.SECONDS);
        assertEquals(messageCount, responseCount,
            "All " + messageCount + " messages should be received");
    }

    @Test
    @Order(3)
    @DisplayName("Test message with embedded newlines in string values")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testMessagesWithEmbeddedNewlines() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        String requestId = "embedded-newline-test";

        // Create request with embedded newlines in a string value (properly escaped in JSON)
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId);
        request.put("method", "tools/call");

        ObjectNode params = request.putObject("params");
        params.put("name", "test_tool");
        // JSON properly escapes newlines as \n within the string value
        params.put("arguments", MAPPER.createObjectNode()
            .put("description", "Line 1\nLine 2\nLine 3"));

        String requestJson = MAPPER.writeValueAsString(request);

        // Verify the JSON contains escaped newlines, not literal newlines
        assertTrue(requestJson.contains("\\n"), "Embedded newlines should be escaped in JSON");

        // The message should be a single line (one newline delimiter at end)
        clientWriter.write(requestJson);
        clientWriter.newLine();
        clientWriter.flush();

        // Simulate server echo response
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", requestId);
        response.putObject("result").put("status", "received");

        BufferedWriter serverWriter = new BufferedWriter(
            new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));
        serverWriter.write(MAPPER.writeValueAsString(response));
        serverWriter.newLine();
        serverWriter.flush();

        // Read single response line
        String responseLine = clientReader.readLine();
        assertNotNull(responseLine, "Should receive single response");

        JsonNode responseNode = MAPPER.readTree(responseLine);
        assertEquals(requestId, responseNode.get("id").asText());
    }

    // =========================================================================
    // Test 3: Stderr Logging Capture
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Test stderr logging capture during server operation")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testStderrCapture() throws Exception {
        // Simulate server writing to stderr
        String[] logMessages = {
            "[INFO] YAWL MCP Server v6.0.0 started on STDIO transport",
            "[INFO] Connected to YAWL engine (session established)",
            "[DEBUG] Processing initialize request",
            "[INFO] Client capabilities received: tools, resources, prompts"
        };

        // Write logs to stderr capture
        OutputStream stderrStream = stderrCapture;
        BufferedWriter stderrWriter = new BufferedWriter(
            new OutputStreamWriter(stderrStream, StandardCharsets.UTF_8));

        for (String message : logMessages) {
            stderrWriter.write(message);
            stderrWriter.newLine();
        }
        stderrWriter.flush();

        // Verify captured stderr content
        String captured = stderrCapture.toString(StandardCharsets.UTF_8);
        for (String message : logMessages) {
            assertTrue(captured.contains(message),
                "Stderr should contain: " + message);
        }

        // Verify line structure
        String[] lines = captured.split("\n");
        assertEquals(logMessages.length, lines.length,
            "Stderr should have " + logMessages.length + " lines");
    }

    @Test
    @Order(5)
    @DisplayName("Test concurrent stdout responses and stderr logging")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testConcurrentStdoutAndStderr() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));
        BufferedWriter stderrWriter = new BufferedWriter(
            new OutputStreamWriter(stderrCapture, StandardCharsets.UTF_8));

        int requestCount = 5;
        CountDownLatch allReceived = new CountDownLatch(requestCount);
        AtomicInteger receivedCount = new AtomicInteger(0);

        // Send requests
        for (int i = 0; i < requestCount; i++) {
            ObjectNode request = MAPPER.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", "concurrent-" + i);
            request.put("method", "ping");

            clientWriter.write(MAPPER.writeValueAsString(request));
            clientWriter.newLine();
        }
        clientWriter.flush();

        // Server responds on stdout while logging on stderr
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < requestCount; i++) {
                    // Log to stderr
                    stderrWriter.write("[DEBUG] Processing request " + i);
                    stderrWriter.newLine();
                    stderrWriter.flush();

                    // Respond on stdout
                    ObjectNode response = MAPPER.createObjectNode();
                    response.put("jsonrpc", "2.0");
                    response.put("id", "concurrent-" + i);
                    response.putObject("result");

                    BufferedWriter serverWriter = new BufferedWriter(
                        new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));
                    serverWriter.write(MAPPER.writeValueAsString(response));
                    serverWriter.newLine();
                    serverWriter.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);

        // Read responses
        CompletableFuture<Void> readerTask = CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < requestCount; i++) {
                    String line = clientReader.readLine();
                    if (line != null) {
                        JsonNode response = MAPPER.readTree(line);
                        if (response.has("id")) {
                            receivedCount.incrementAndGet();
                            allReceived.countDown();
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);

        assertTrue(allReceived.await(10, TimeUnit.SECONDS),
            "All responses should be received");
        serverTask.get(5, TimeUnit.SECONDS);
        readerTask.get(5, TimeUnit.SECONDS);

        assertEquals(requestCount, receivedCount.get(),
            "Should receive all " + requestCount + " responses");

        // Verify stderr captured logs
        String stderrContent = stderrCapture.toString(StandardCharsets.UTF_8);
        assertTrue(stderrContent.contains("[DEBUG] Processing request"),
            "Stderr should contain debug logs");
    }

    // =========================================================================
    // Test 4: Graceful Shutdown on EOF
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Test graceful shutdown when client closes connection (EOF)")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testGracefulShutdown() throws Exception {
        AtomicBoolean eofDetected = new AtomicBoolean(false);
        AtomicReference<String> shutdownLog = new AtomicReference<>();
        CountDownLatch shutdownComplete = new CountDownLatch(1);

        // Server reader thread that detects EOF
        CompletableFuture<Void> serverReaderTask = CompletableFuture.runAsync(() -> {
            try {
                BufferedReader serverReader = new BufferedReader(
                    new InputStreamReader(clientToServerInput, StandardCharsets.UTF_8));

                String line;
                while ((line = serverReader.readLine()) != null) {
                    // Process incoming messages
                    if (!line.isBlank()) {
                        // Echo back for confirmation
                        BufferedWriter serverWriter = new BufferedWriter(
                            new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));
                        ObjectNode response = MAPPER.createObjectNode();
                        response.put("jsonrpc", "2.0");
                        response.put("id", "echo");
                        response.putObject("result").put("echoed", true);
                        serverWriter.write(MAPPER.writeValueAsString(response));
                        serverWriter.newLine();
                        serverWriter.flush();
                    }
                }

                // EOF detected (readLine returned null)
                eofDetected.set(true);
                shutdownLog.set("[INFO] Client disconnected, initiating graceful shutdown");

                // Log shutdown to stderr
                BufferedWriter stderrWriter = new BufferedWriter(
                    new OutputStreamWriter(stderrCapture, StandardCharsets.UTF_8));
                stderrWriter.write(shutdownLog.get());
                stderrWriter.newLine();
                stderrWriter.flush();

                shutdownComplete.countDown();

            } catch (IOException e) {
                // Pipe closed is expected during shutdown
                shutdownComplete.countDown();
            }
        }, executor);

        // Client sends a message and then closes
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        // Send a test message
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "final-request");
        request.put("method", "ping");
        clientWriter.write(MAPPER.writeValueAsString(request));
        clientWriter.newLine();
        clientWriter.flush();

        // Read the echo response
        String response = clientReader.readLine();
        assertNotNull(response, "Should receive response before shutdown");

        // Close client output (simulates client disconnect)
        clientWriter.close();

        // Wait for server to detect EOF and shutdown
        assertTrue(shutdownComplete.await(10, TimeUnit.SECONDS),
            "Server should complete shutdown within timeout");
        assertTrue(eofDetected.get(), "Server should detect EOF");

        // Verify shutdown was logged
        String stderrContent = stderrCapture.toString(StandardCharsets.UTF_8);
        assertTrue(stderrContent.contains("graceful shutdown") || stderrContent.contains("disconnected"),
            "Stderr should log shutdown reason");
    }

    @Test
    @Order(7)
    @DisplayName("Test server handles multiple client connections with EOF per client")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testMultipleClientEofHandling() throws Exception {
        int clientCount = 3;
        CountDownLatch allClientsHandled = new CountDownLatch(clientCount);
        AtomicInteger eofCount = new AtomicInteger(0);

        // Process each "client" sequentially using the same pipe setup
        for (int c = 0; c < clientCount; c++) {
            final int clientIndex = c;

            // Create fresh pipes for each client
            PipedInputStream cis = new PipedInputStream();
            PipedOutputStream cos = new PipedOutputStream(cis);
            PipedInputStream sis = new PipedInputStream();
            PipedOutputStream sos = new PipedOutputStream(sis);

            // Server handler for this client
            CompletableFuture<Void> serverHandler = CompletableFuture.runAsync(() -> {
                try {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(cis, StandardCharsets.UTF_8));
                    BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(sos, StandardCharsets.UTF_8));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        ObjectNode response = MAPPER.createObjectNode();
                        response.put("jsonrpc", "2.0");
                        response.put("id", "client-" + clientIndex);
                        response.putObject("result").put("processed", true);
                        writer.write(MAPPER.writeValueAsString(response));
                        writer.newLine();
                        writer.flush();
                    }

                    // EOF detected
                    eofCount.incrementAndGet();
                    allClientsHandled.countDown();

                } catch (IOException e) {
                    allClientsHandled.countDown();
                }
            }, executor);

            // Client sends message and closes
            BufferedWriter clientWriter = new BufferedWriter(
                new OutputStreamWriter(cos, StandardCharsets.UTF_8));
            BufferedReader clientReader = new BufferedReader(
                new InputStreamReader(sis, StandardCharsets.UTF_8));

            ObjectNode request = MAPPER.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", "client-" + clientIndex);
            request.put("method", "ping");
            clientWriter.write(MAPPER.writeValueAsString(request));
            clientWriter.newLine();
            clientWriter.flush();

            // Read response
            String response = clientReader.readLine();
            assertNotNull(response, "Client " + clientIndex + " should receive response");

            // Close client connection
            clientWriter.close();
            cos.close();

            // Wait briefly for server to detect EOF
            Thread.sleep(100);
        }

        assertTrue(allClientsHandled.await(10, TimeUnit.SECONDS),
            "All clients should be handled");
        assertEquals(clientCount, eofCount.get(),
            "EOF should be detected for all " + clientCount + " clients");
    }

    // =========================================================================
    // Test 5: Large Message Handling (>64KB)
    // =========================================================================

    @Test
    @Order(8)
    @Tag("slow")
        @DisplayName("Test large message handling exceeding default buffer size")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testLargeMessage() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        // Create a large payload (>64KB)
        StringBuilder largeContent = new StringBuilder(LARGE_MESSAGE_SIZE);
        for (int i = 0; i < LARGE_MESSAGE_SIZE / 100; i++) {
            largeContent.append("data-chunk-").append(i).append("-")
                .append("x".repeat(80)).append(",");
        }

        String largeData = largeContent.toString();
        assertTrue(largeData.length() > 64 * 1024,
            "Large message should exceed 64KB, was " + largeData.length() + " bytes");

        String requestId = "large-message-test";

        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId);
        request.put("method", "tools/call");

        ObjectNode params = request.putObject("params");
        params.put("name", "upload_specification");
        params.putObject("arguments").put("specification_xml", largeData);

        // Send large message
        long sendStart = System.currentTimeMillis();
        clientWriter.write(MAPPER.writeValueAsString(request));
        clientWriter.newLine();
        clientWriter.flush();
        long sendDuration = System.currentTimeMillis() - sendStart;

        // Server echoes back confirmation
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                BufferedReader serverReader = new BufferedReader(
                    new InputStreamReader(clientToServerInput, StandardCharsets.UTF_8));

                String line = serverReader.readLine();
                assertNotNull(line, "Server should receive large message");

                JsonNode received = MAPPER.readTree(line);
                String specXml = received.get("params").get("arguments").get("specification_xml").asText();

                // Verify the large content was received intact
                assertEquals(largeData.length(), specXml.length(),
                    "Large content should be received intact");

                // Send acknowledgment response
                ObjectNode response = MAPPER.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.put("id", requestId);
                response.putObject("result")
                    .put("received_bytes", specXml.length())
                    .put("status", "success");

                BufferedWriter serverWriter = new BufferedWriter(
                    new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));
                serverWriter.write(MAPPER.writeValueAsString(response));
                serverWriter.newLine();
                serverWriter.flush();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);

        // Read confirmation response
        long recvStart = System.currentTimeMillis();
        String responseLine = clientReader.readLine();
        long recvDuration = System.currentTimeMillis() - recvStart;

        serverTask.get(15, TimeUnit.SECONDS);

        assertNotNull(responseLine, "Should receive response for large message");
        JsonNode response = MAPPER.readTree(responseLine);
        assertEquals(requestId, response.get("id").asText());

        JsonNode result = response.get("result");
        assertEquals("success", result.get("status").asText());
        assertTrue(result.get("received_bytes").asInt() > 64 * 1024,
            "Server should confirm receiving >64KB");

        // Log timing information
        System.out.println("Large message send time: " + sendDuration + "ms");
        System.out.println("Large message receive time: " + recvDuration + "ms");
        System.out.println("Total message size: " + largeData.length() + " bytes");
    }

    @Test
    @Order(9)
    @Tag("slow")
        @DisplayName("Test multiple large messages in sequence")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testSequentialLargeMessages() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        int messageCount = 3;
        int messageSize = 50 * 1024; // 50KB each

        for (int m = 0; m < messageCount; m++) {
            // Create large payload
            String payload = "msg-" + m + "-" + "x".repeat(messageSize);

            ObjectNode request = MAPPER.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", "large-seq-" + m);
            request.put("method", "echo");
            request.putObject("params").put("payload", payload);

            clientWriter.write(MAPPER.writeValueAsString(request));
            clientWriter.newLine();
        }
        clientWriter.flush();

        // Server processes and echoes each message
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                BufferedReader serverReader = new BufferedReader(
                    new InputStreamReader(clientToServerInput, StandardCharsets.UTF_8));
                BufferedWriter serverWriter = new BufferedWriter(
                    new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));

                for (int m = 0; m < messageCount; m++) {
                    String line = serverReader.readLine();
                    if (line != null) {
                        JsonNode request = MAPPER.readTree(line);

                        // Echo back the payload length as confirmation
                        ObjectNode response = MAPPER.createObjectNode();
                        response.put("jsonrpc", "2.0");
                        response.put("id", request.get("id").asText());
                        response.putObject("result")
                            .put("payload_length", request.get("params").get("payload").asText().length());

                        serverWriter.write(MAPPER.writeValueAsString(response));
                        serverWriter.newLine();
                        serverWriter.flush();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);

        // Read all responses
        int successCount = 0;
        for (int m = 0; m < messageCount; m++) {
            String responseLine = clientReader.readLine();
            if (responseLine != null) {
                JsonNode response = MAPPER.readTree(responseLine);
                if (response.has("result")) {
                    int payloadLength = response.get("result").get("payload_length").asInt();
                    assertTrue(payloadLength > messageSize,
                        "Payload " + m + " length should exceed " + messageSize);
                    successCount++;
                }
            }
        }

        serverTask.get(20, TimeUnit.SECONDS);
        assertEquals(messageCount, successCount,
            "All " + messageCount + " large messages should be processed successfully");
    }

    // =========================================================================
    // Test 6: Error Response Handling
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("Test JSON-RPC error responses over STDIO")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testErrorResponseHandling() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        String requestId = "error-test";

        // Send request for non-existent method
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId);
        request.put("method", "nonexistent_method");

        clientWriter.write(MAPPER.writeValueAsString(request));
        clientWriter.newLine();
        clientWriter.flush();

        // Server sends error response
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                ObjectNode errorResponse = MAPPER.createObjectNode();
                errorResponse.put("jsonrpc", "2.0");
                errorResponse.put("id", requestId);

                ObjectNode error = errorResponse.putObject("error");
                error.put("code", -32601); // Method not found
                error.put("message", "Method not found: nonexistent_method");

                BufferedWriter serverWriter = new BufferedWriter(
                    new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));
                serverWriter.write(MAPPER.writeValueAsString(errorResponse));
                serverWriter.newLine();
                serverWriter.flush();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);

        // Read error response
        String responseLine = clientReader.readLine();
        serverTask.get(5, TimeUnit.SECONDS);

        assertNotNull(responseLine, "Should receive error response");
        JsonNode response = MAPPER.readTree(responseLine);

        assertTrue(response.has("error"), "Response should have error field");
        assertFalse(response.has("result"), "Error response should not have result field");

        JsonNode error = response.get("error");
        assertEquals(-32601, error.get("code").asInt(), "Error code should be -32601");
        assertTrue(error.get("message").asText().contains("not found"),
            "Error message should indicate method not found");
    }

    // =========================================================================
    // Test 7: Invalid JSON Handling
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("Test handling of malformed JSON messages")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testMalformedJsonHandling() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        // Send malformed JSON
        String malformedJson = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",";
        clientWriter.write(malformedJson);
        clientWriter.newLine();
        clientWriter.flush();

        // Server should respond with parse error
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                BufferedReader serverReader = new BufferedReader(
                    new InputStreamReader(clientToServerInput, StandardCharsets.UTF_8));

                String line = serverReader.readLine();
                if (line != null) {
                    try {
                        MAPPER.readTree(line);
                        // If we got here, the malformed JSON was somehow parsed
                        // This shouldn't happen, but send a valid response anyway
                    } catch (Exception parseError) {
                        // Expected - send parse error response
                        ObjectNode errorResponse = MAPPER.createObjectNode();
                        errorResponse.put("jsonrpc", "2.0");
                        errorResponse.put("id", (String) null);

                        ObjectNode error = errorResponse.putObject("error");
                        error.put("code", -32700); // Parse error
                        error.put("message", "Parse error: Invalid JSON");

                        BufferedWriter serverWriter = new BufferedWriter(
                            new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));
                        serverWriter.write(MAPPER.writeValueAsString(errorResponse));
                        serverWriter.newLine();
                        serverWriter.flush();
                    }
                }
            } catch (IOException e) {
                // Connection closed
            }
        }, executor);

        // Wait for server processing
        serverTask.get(5, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Test 8: Concurrent Request-Response Matching
    // =========================================================================

    @Test
    @Order(12)
    @DisplayName("Test concurrent requests maintain correct ID matching")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testConcurrentRequestIdMatching() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        int requestCount = 20;
        Map<String, String> expectedResponses = new ConcurrentHashMap<>();
        CountDownLatch allResponsesReceived = new CountDownLatch(requestCount);

        // Send requests with unique IDs
        for (int i = 0; i < requestCount; i++) {
            String requestId = "concurrent-id-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            String responseData = "response-data-" + i;

            ObjectNode request = MAPPER.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", requestId);
            request.put("method", "process");
            request.putObject("params").put("data", responseData);

            expectedResponses.put(requestId, responseData);
            clientWriter.write(MAPPER.writeValueAsString(request));
            clientWriter.newLine();
        }
        clientWriter.flush();

        // Server processes requests concurrently
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                BufferedReader serverReader = new BufferedReader(
                    new InputStreamReader(clientToServerInput, StandardCharsets.UTF_8));
                BufferedWriter serverWriter = new BufferedWriter(
                    new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));

                String line;
                while ((line = serverReader.readLine()) != null) {
                    try {
                        JsonNode request = MAPPER.readTree(line);
                        String id = request.get("id").asText();
                        String data = request.get("params").get("data").asText();

                        ObjectNode response = MAPPER.createObjectNode();
                        response.put("jsonrpc", "2.0");
                        response.put("id", id);
                        response.putObject("result").put("echoed_data", data);

                        serverWriter.write(MAPPER.writeValueAsString(response));
                        serverWriter.newLine();
                        serverWriter.flush();
                    } catch (Exception e) {
                        // Skip malformed
                    }
                }
            } catch (IOException e) {
                // Pipe closed
            }
        }, executor);

        // Reader thread collects responses
        AtomicInteger matchedCount = new AtomicInteger(0);
        CompletableFuture<Void> readerTask = CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < requestCount; i++) {
                    String line = clientReader.readLine();
                    if (line != null) {
                        JsonNode response = MAPPER.readTree(line);
                        if (response.has("id") && response.has("result")) {
                            String id = response.get("id").asText();
                            String echoedData = response.get("result").get("echoed_data").asText();

                            String expected = expectedResponses.get(id);
                            if (expected != null && expected.equals(echoedData)) {
                                matchedCount.incrementAndGet();
                                allResponsesReceived.countDown();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // Connection closed
            }
        }, executor);

        assertTrue(allResponsesReceived.await(15, TimeUnit.SECONDS),
            "All responses should be received within timeout");

        serverTask.cancel(true);
        readerTask.get(5, TimeUnit.SECONDS);

        assertEquals(requestCount, matchedCount.get(),
            "All " + requestCount + " request-response pairs should match");
    }

    // =========================================================================
    // Test 9: Protocol Version Negotiation
    // =========================================================================

    @Test
    @Order(13)
    @DisplayName("Test MCP protocol version negotiation over STDIO")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testProtocolVersionNegotiation() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        String requestId = "version-negotiation";

        // Client requests with specific protocol version
        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId);
        request.put("method", "initialize");

        ObjectNode params = request.putObject("params");
        params.put("protocolVersion", "2025-11-25");
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", "version-test-client").put("version", "1.0.0");

        clientWriter.write(MAPPER.writeValueAsString(request));
        clientWriter.newLine();
        clientWriter.flush();

        // Server responds with negotiated version
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                BufferedReader serverReader = new BufferedReader(
                    new InputStreamReader(clientToServerInput, StandardCharsets.UTF_8));

                String line = serverReader.readLine();
                if (line != null) {
                    ObjectNode response = MAPPER.createObjectNode();
                    response.put("jsonrpc", "2.0");
                    response.put("id", requestId);

                    ObjectNode result = response.putObject("result");
                    // Server agrees to client's version
                    result.put("protocolVersion", "2025-11-25");
                    result.putObject("capabilities");
                    ObjectNode serverInfo = result.putObject("serverInfo");
                    serverInfo.put("name", "yawl-mcp-server");
                    serverInfo.put("version", "6.0.0");

                    BufferedWriter serverWriter = new BufferedWriter(
                        new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));
                    serverWriter.write(MAPPER.writeValueAsString(response));
                    serverWriter.newLine();
                    serverWriter.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);

        String responseLine = clientReader.readLine();
        serverTask.get(5, TimeUnit.SECONDS);

        assertNotNull(responseLine, "Should receive version negotiation response");
        JsonNode response = MAPPER.readTree(responseLine);

        assertEquals("2025-11-25", response.get("result").get("protocolVersion").asText(),
            "Server should agree to protocol version 2025-11-25");
    }

    // =========================================================================
    // Test 10: Batch Message Processing
    // =========================================================================

    @Test
    @Order(14)
    @DisplayName("Test JSON-RPC batch message processing")
    @Timeout(value = TEST_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testBatchMessageProcessing() throws Exception {
        BufferedWriter clientWriter = new BufferedWriter(
            new OutputStreamWriter(clientToServerOutput, StandardCharsets.UTF_8));
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(serverToClientInput, StandardCharsets.UTF_8));

        // Create batch of requests as JSON array
        List<ObjectNode> batch = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ObjectNode request = MAPPER.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", "batch-" + i);
            request.put("method", "echo");
            request.putObject("params").put("index", i);
            batch.add(request);
        }

        // Send as JSON array (batch)
        String batchJson = MAPPER.writeValueAsString(batch);
        clientWriter.write(batchJson);
        clientWriter.newLine();
        clientWriter.flush();

        // Server processes batch and returns batch response
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                BufferedReader serverReader = new BufferedReader(
                    new InputStreamReader(clientToServerInput, StandardCharsets.UTF_8));

                String line = serverReader.readLine();
                if (line != null) {
                    JsonNode batchRequest = MAPPER.readTree(line);

                    if (batchRequest.isArray()) {
                        List<ObjectNode> responses = new ArrayList<>();

                        for (JsonNode req : batchRequest) {
                            ObjectNode response = MAPPER.createObjectNode();
                            response.put("jsonrpc", "2.0");
                            response.put("id", req.get("id").asText());
                            response.putObject("result")
                                .put("echoed_index", req.get("params").get("index").asInt());
                            responses.add(response);
                        }

                        BufferedWriter serverWriter = new BufferedWriter(
                            new OutputStreamWriter(serverToClientOutput, StandardCharsets.UTF_8));
                        serverWriter.write(MAPPER.writeValueAsString(responses));
                        serverWriter.newLine();
                        serverWriter.flush();
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executor);

        String responseLine = clientReader.readLine();
        serverTask.get(5, TimeUnit.SECONDS);

        assertNotNull(responseLine, "Should receive batch response");
        JsonNode response = MAPPER.readTree(responseLine);

        assertTrue(response.isArray(), "Batch response should be an array");
        assertEquals(5, response.size(), "Batch response should have 5 items");

        for (int i = 0; i < 5; i++) {
            JsonNode item = response.get(i);
            assertEquals("batch-" + i, item.get("id").asText(),
                "Response " + i + " should have correct ID");
            assertEquals(i, item.get("result").get("echoed_index").asInt(),
                "Response " + i + " should echo correct index");
        }
    }
}
