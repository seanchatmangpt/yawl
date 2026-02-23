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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pact consumer contract test for the YAWL Stateless Engine API.
 *
 * The stateless engine accepts a workflow specification and case data then
 * executes a complete case in memory without database persistence.  Consumers
 * of this API are: batch processing pipelines, test harnesses, and embedded
 * workflow evaluators.
 *
 * @version 6.0.0
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "YawlStatelessApi", port = "0")
@DisplayName("YAWL Stateless Engine API - Consumer Contract")
@Tag("unit")
public class StatelessApiConsumerContractTest {

    private static final String CONSUMER_NAME = "YawlBatchProcessor";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact executeSpecificationPact(PactDslWithProvider builder) {
        return builder
            .given("a valid YAWL specification document")
            .uponReceiving("a request to execute a workflow specification in stateless mode")
                .path("/stateless/execute")
                .method("POST")
                .headers(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .headers(HEADER_AUTHORIZATION, "Bearer valid-api-key")
                .body(new PactDslJsonBody()
                    .stringType("specificationXml")
                    .object("caseData")
                        .stringType("inputParam")
                        .closeObject()
                    .booleanValue("awaitCompletion", true)
                    .integerType("timeoutSeconds")
                )
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringType("caseId")
                    .stringMatcher("outcome", "COMPLETED|CANCELLED|TIMEOUT", "COMPLETED")
                    .object("outputData")
                        .closeObject()
                    .integerType("executionTimeMs")
                )
            .toPact();
    }

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact exportCaseStatePact(PactDslWithProvider builder) {
        return builder
            .given("stateless case 'SL-42' is suspended mid-execution")
            .uponReceiving("a request to export case state for external persistence")
                .path("/stateless/cases/SL-42/export")
                .method("GET")
                .headers(HEADER_AUTHORIZATION, "Bearer valid-api-key")
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringValue("caseId", "SL-42")
                    .stringType("specificationId")
                    .stringType("stateSnapshot")
                    .datetime("exportedAt", "yyyy-MM-dd'T'HH:mm:ss'Z'")
                )
            .toPact();
    }

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact importCaseStatePact(PactDslWithProvider builder) {
        return builder
            .given("a valid case state snapshot is available")
            .uponReceiving("a request to restore a case from an exported state")
                .path("/stateless/cases/import")
                .method("POST")
                .headers(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .headers(HEADER_AUTHORIZATION, "Bearer valid-api-key")
                .body(new PactDslJsonBody()
                    .stringType("stateSnapshot")
                    .stringType("specificationXml")
                )
            .willRespondWith()
                .status(201)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringType("caseId")
                    .stringValue("status", "ACTIVE")
                    .datetime("restoredAt", "yyyy-MM-dd'T'HH:mm:ss'Z'")
                )
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "executeSpecificationPact")
    @DisplayName("Consumer correctly submits a stateless execution request")
    void consumerCanExecuteSpecification(MockServer mockServer)
            throws IOException, InterruptedException {
        String body = "{\"specificationXml\":\"<spec/>\","
                    + "\"caseData\":{\"inputParam\":\"value\"},"
                    + "\"awaitCompletion\":true,\"timeoutSeconds\":30}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/stateless/execute"))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, "Bearer valid-api-key")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("caseId"));
        assertTrue(response.body().contains("outcome"));
        assertTrue(response.body().contains("executionTimeMs"));
    }

    @Test
    @PactTestFor(pactMethod = "exportCaseStatePact")
    @DisplayName("Consumer correctly exports case state snapshot")
    void consumerCanExportCaseState(MockServer mockServer)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/stateless/cases/SL-42/export"))
                .header(HEADER_AUTHORIZATION, "Bearer valid-api-key")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("stateSnapshot"));
        assertTrue(response.body().contains("SL-42"));
    }

    @Test
    @PactTestFor(pactMethod = "importCaseStatePact")
    @DisplayName("Consumer correctly restores a case from state snapshot")
    void consumerCanImportCaseState(MockServer mockServer)
            throws IOException, InterruptedException {
        String body = "{\"stateSnapshot\":\"...\",\"specificationXml\":\"<spec/>\"}";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/stateless/cases/import"))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, "Bearer valid-api-key")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("caseId"));
        assertTrue(response.body().contains("ACTIVE"));
    }
}
