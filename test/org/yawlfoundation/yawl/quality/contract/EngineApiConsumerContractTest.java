package org.yawlfoundation.yawl.quality.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pact consumer-driven contract test for the YAWL Engine REST API.
 *
 * Defines the consumer expectations of the engine HTTP interface and records
 * them as a JSON pact file in target/pacts/ for provider verification.
 *
 * @version 6.0.0
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "YawlEngineApi", port = "0")
@DisplayName("YAWL Engine API - Consumer Contract")
@Tag("unit")
public class EngineApiConsumerContractTest {

    private static final String CONSUMER_NAME = "YawlControlPanel";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact getActiveCasesPact(PactDslWithProvider builder) {
        return builder
            .given("engine has two active cases")
            .uponReceiving("a request to list active cases")
                .path("/engine/cases")
                .method("GET")
                .headers(HEADER_AUTHORIZATION, "Bearer valid-session-token")
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .minArrayLike("cases", 2)
                        .stringType("caseId")
                        .stringType("specificationId")
                        .stringMatcher("status", "ACTIVE|SUSPENDED", "ACTIVE")
                        .closeObject()
                    .closeArray()
                )
            .toPact();
    }

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact launchCasePact(PactDslWithProvider builder) {
        return builder
            .given("specification 'OrderFulfillment:1.0' is loaded")
            .uponReceiving("a request to launch a new case")
                .path("/engine/cases")
                .method("POST")
                .headers(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .headers(HEADER_AUTHORIZATION, "Bearer valid-session-token")
                .body(new PactDslJsonBody()
                    .stringValue("specificationId", "OrderFulfillment")
                    .stringValue("specVersion", "1.0")
                    .object("caseData")
                        .stringType("orderId")
                        .closeObject()
                )
            .willRespondWith()
                .status(201)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringType("caseId")
                    .stringValue("specificationId", "OrderFulfillment")
                    .stringValue("status", "ACTIVE")
                    .datetime("startedAt", "yyyy-MM-dd'T'HH:mm:ss'Z'")
                )
            .toPact();
    }

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact getWorkItemsPact(PactDslWithProvider builder) {
        return builder
            .given("case '100' has enabled work items")
            .uponReceiving("a request to retrieve work items for case 100")
                .path("/engine/workitems")
                .query("caseId=100")
                .method("GET")
                .headers(HEADER_AUTHORIZATION, "Bearer valid-session-token")
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .minArrayLike("workItems", 1)
                        .stringType("workItemId")
                        .stringValue("caseId", "100")
                        .stringType("taskId")
                        .stringMatcher("status", "ENABLED|EXECUTING|SUSPENDED", "ENABLED")
                        .closeObject()
                    .closeArray()
                )
            .toPact();
    }

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact completeWorkItemPact(PactDslWithProvider builder) {
        return builder
            .given("work item 'WI-100-A1' is in EXECUTING state")
            .uponReceiving("a request to complete work item WI-100-A1")
                .path("/engine/workitems/WI-100-A1/complete")
                .method("POST")
                .headers(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .headers(HEADER_AUTHORIZATION, "Bearer valid-session-token")
                .body(new PactDslJsonBody()
                    .object("outputData")
                        .stringType("result")
                        .closeObject()
                )
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringValue("workItemId", "WI-100-A1")
                    .stringValue("status", "COMPLETED")
                    .datetime("completedAt", "yyyy-MM-dd'T'HH:mm:ss'Z'")
                )
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getActiveCasesPact")
    @DisplayName("Consumer correctly requests active cases list")
    void consumerCanGetActiveCases(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/engine/cases"))
                .header(HEADER_AUTHORIZATION, "Bearer valid-session-token")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertNotNull(response.body());
        assertTrue(response.body().contains("cases"));
    }

    @Test
    @PactTestFor(pactMethod = "launchCasePact")
    @DisplayName("Consumer correctly launches a new workflow case")
    void consumerCanLaunchCase(MockServer mockServer) throws IOException, InterruptedException {
        String body = "{\"specificationId\":\"OrderFulfillment\",\"specVersion\":\"1.0\","
                    + "\"caseData\":{\"orderId\":\"ORD-001\"}}";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/engine/cases"))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, "Bearer valid-session-token")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("caseId"));
        assertTrue(response.body().contains("ACTIVE"));
    }

    @Test
    @PactTestFor(pactMethod = "getWorkItemsPact")
    @DisplayName("Consumer correctly retrieves work items for a case")
    void consumerCanGetWorkItems(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/engine/workitems?caseId=100"))
                .header(HEADER_AUTHORIZATION, "Bearer valid-session-token")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("workItems"));
    }

    @Test
    @PactTestFor(pactMethod = "completeWorkItemPact")
    @DisplayName("Consumer correctly completes a work item")
    void consumerCanCompleteWorkItem(MockServer mockServer) throws IOException, InterruptedException {
        String body = "{\"outputData\":{\"result\":\"approved\"}}";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/engine/workitems/WI-100-A1/complete"))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, "Bearer valid-session-token")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("COMPLETED"));
    }
}
