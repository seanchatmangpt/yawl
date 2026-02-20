/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pact consumer-driven contract test for the YAWL A2A (Agent-to-Agent) protocol.
 *
 * Defines the consumer expectations of the A2A server HTTP interface:
 * - Submit task (POST /a2a/task)
 * - Get task status (GET /a2a/task/{id})
 * - Update task (PUT /a2a/task/{id})
 *
 * Records these as JSON pact files in target/pacts/ for provider verification.
 * Uses real HTTP contracts, not mocks.
 *
 * Chicago TDD: Real Pact contracts, real client behavior validation.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-20
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "YawlA2AServer", port = "0")
@DisplayName("YAWL A2A Protocol - Consumer Contract")
@Tag("contract")
class A2AConsumerContractTest {

    private static final String CONSUMER_NAME = "YawlA2AClient";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_AGENT_ID = "X-Agent-ID";

    /**
     * Contract for submitting a task via A2A protocol.
     * Client sends task specification; server responds with task ID.
     */
    @Pact(consumer = CONSUMER_NAME)
    RequestResponsePact submitTaskPact(PactDslWithProvider builder) {
        return builder
            .given("A2A server is ready to receive tasks")
            .uponReceiving("a request to submit a new task")
                .path("/a2a/task")
                .method("POST")
                .headers(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .headers(HEADER_AUTHORIZATION, "Bearer valid-a2a-token")
                .headers(HEADER_AGENT_ID, "agent-001")
                .body(new PactDslJsonBody()
                    .stringValue("taskName", "ProcessOrder")
                    .stringValue("workflowId", "order-wf-001")
                    .stringValue("caseId", "case-100")
                    .object("payload")
                        .stringType("orderId")
                        .numberType("amount")
                        .closeObject()
                )
            .willRespondWith()
                .status(201)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringType("taskId")
                    .stringValue("status", "RECEIVED")
                    .datetime("createdAt", "yyyy-MM-dd'T'HH:mm:ss'Z'")
                )
            .toPact();
    }

    /**
     * Contract for retrieving task status via A2A protocol.
     * Client requests status of a known task; server returns current state.
     */
    @Pact(consumer = CONSUMER_NAME)
    RequestResponsePact getTaskStatusPact(PactDslWithProvider builder) {
        return builder
            .given("task 'TASK-001' exists in PROCESSING state")
            .uponReceiving("a request to get task status for TASK-001")
                .path("/a2a/task/TASK-001")
                .method("GET")
                .headers(HEADER_AUTHORIZATION, "Bearer valid-a2a-token")
                .headers(HEADER_AGENT_ID, "agent-001")
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringValue("taskId", "TASK-001")
                    .stringValue("status", "PROCESSING")
                    .stringValue("workflowId", "order-wf-001")
                    .stringValue("caseId", "case-100")
                    .datetime("updatedAt", "yyyy-MM-dd'T'HH:mm:ss'Z'")
                )
            .toPact();
    }

    /**
     * Contract for updating a task status via A2A protocol.
     * Client sends completion status and results; server acknowledges.
     */
    @Pact(consumer = CONSUMER_NAME)
    RequestResponsePact updateTaskPact(PactDslWithProvider builder) {
        return builder
            .given("task 'TASK-001' is in PROCESSING state")
            .uponReceiving("a request to update task TASK-001 to COMPLETED")
                .path("/a2a/task/TASK-001")
                .method("PUT")
                .headers(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .headers(HEADER_AUTHORIZATION, "Bearer valid-a2a-token")
                .headers(HEADER_AGENT_ID, "agent-001")
                .body(new PactDslJsonBody()
                    .stringValue("status", "COMPLETED")
                    .object("result")
                        .stringType("output")
                        .numberType("processedItems")
                        .closeObject()
                )
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringValue("taskId", "TASK-001")
                    .stringValue("status", "COMPLETED")
                    .datetime("completedAt", "yyyy-MM-dd'T'HH:mm:ss'Z'")
                )
            .toPact();
    }

    /**
     * Contract for task not found error.
     * Client requests non-existent task; server returns 404.
     */
    @Pact(consumer = CONSUMER_NAME)
    RequestResponsePact taskNotFoundPact(PactDslWithProvider builder) {
        return builder
            .given("task 'INVALID-TASK' does not exist")
            .uponReceiving("a request to get a non-existent task")
                .path("/a2a/task/INVALID-TASK")
                .method("GET")
                .headers(HEADER_AUTHORIZATION, "Bearer valid-a2a-token")
                .headers(HEADER_AGENT_ID, "agent-001")
            .willRespondWith()
                .status(404)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringValue("error", "Task not found")
                    .stringValue("taskId", "INVALID-TASK")
                )
            .toPact();
    }

    // =========================================================================
    // Consumer Tests (verify client behavior against contracts)
    // =========================================================================

    @Test
    @PactTestFor(pactMethod = "submitTaskPact")
    @DisplayName("Consumer correctly submits a task to A2A server")
    void consumerCanSubmitTask(MockServer mockServer) throws IOException, InterruptedException {
        String taskPayload = """{
            "taskName":"ProcessOrder",
            "workflowId":"order-wf-001",
            "caseId":"case-100",
            "payload":{"orderId":"ORD-123","amount":999.99}
        }""";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/a2a/task"))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, "Bearer valid-a2a-token")
                .header(HEADER_AGENT_ID, "agent-001")
                .POST(HttpRequest.BodyPublishers.ofString(taskPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertNotNull(response.body());
        assertTrue(response.body().contains("taskId"));
        assertTrue(response.body().contains("RECEIVED"));
    }

    @Test
    @PactTestFor(pactMethod = "getTaskStatusPact")
    @DisplayName("Consumer correctly retrieves task status from A2A server")
    void consumerCanGetTaskStatus(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/a2a/task/TASK-001"))
                .header(HEADER_AUTHORIZATION, "Bearer valid-a2a-token")
                .header(HEADER_AGENT_ID, "agent-001")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertNotNull(response.body());
        assertTrue(response.body().contains("TASK-001"));
        assertTrue(response.body().contains("PROCESSING"));
    }

    @Test
    @PactTestFor(pactMethod = "updateTaskPact")
    @DisplayName("Consumer correctly updates task status on A2A server")
    void consumerCanUpdateTask(MockServer mockServer) throws IOException, InterruptedException {
        String updatePayload = """{
            "status":"COMPLETED",
            "result":{"output":"Success","processedItems":42}
        }""";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/a2a/task/TASK-001"))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, "Bearer valid-a2a-token")
                .header(HEADER_AGENT_ID, "agent-001")
                .method("PUT", HttpRequest.BodyPublishers.ofString(updatePayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("COMPLETED"));
    }

    @Test
    @PactTestFor(pactMethod = "taskNotFoundPact")
    @DisplayName("Consumer handles task not found error from A2A server")
    void consumerHandlesTaskNotFound(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/a2a/task/INVALID-TASK"))
                .header(HEADER_AUTHORIZATION, "Bearer valid-a2a-token")
                .header(HEADER_AGENT_ID, "agent-001")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Task not found"));
    }
}
