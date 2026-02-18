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
 * Pact consumer contract test for the YAWL Integration API (MCP and A2A endpoints).
 *
 * Consumers: external agent frameworks, MCP-compatible tools, A2A agent-to-agent
 * communication infrastructure.
 *
 * @version 6.0.0
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "YawlIntegrationApi", port = "0")
@DisplayName("YAWL Integration API - Consumer Contract")
@Tag("unit")
public class IntegrationApiConsumerContractTest {

    private static final String CONSUMER_NAME = "YawlExternalAgent";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact getMcpCapabilitiesPact(PactDslWithProvider builder) {
        return builder
            .given("MCP server is running and engine is connected")
            .uponReceiving("a request for MCP server capabilities")
                .path("/mcp/capabilities")
                .method("GET")
                .headers(HEADER_AUTHORIZATION, "Bearer valid-jwt-token")
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringType("serverName")
                    .stringType("serverVersion")
                    .object("capabilities")
                        .booleanValue("tools", true)
                        .booleanValue("resources", true)
                        .closeObject()
                    .minArrayLike("tools", 1)
                        .stringType("name")
                        .stringType("description")
                        .closeObject()
                    .closeArray()
                )
            .toPact();
    }

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact invokeMcpToolPact(PactDslWithProvider builder) {
        return builder
            .given("MCP server is running and 'list-cases' tool is registered")
            .uponReceiving("a request to invoke the list-cases MCP tool")
                .path("/mcp/tools/invoke")
                .method("POST")
                .headers(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .headers(HEADER_AUTHORIZATION, "Bearer valid-jwt-token")
                .body(new PactDslJsonBody()
                    .stringValue("toolName", "list-cases")
                    .object("parameters")
                        .closeObject()
                )
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .booleanValue("success", true)
                    .object("result")
                        .minArrayLike("cases", 0)
                            .stringType("caseId")
                            .closeObject()
                        .closeArray()
                        .closeObject()
                )
            .toPact();
    }

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact getAgentCardPact(PactDslWithProvider builder) {
        return builder
            .given("A2A server is running")
            .uponReceiving("a request for the A2A agent card")
                .path("/.well-known/agent.json")
                .method("GET")
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringType("name")
                    .stringType("description")
                    .stringType("url")
                    .stringType("version")
                    .object("capabilities")
                        .booleanValue("streaming", false)
                        .booleanValue("pushNotifications", false)
                        .closeObject()
                    .minArrayLike("skills", 1)
                        .stringType("id")
                        .stringType("name")
                        .closeObject()
                    .closeArray()
                )
            .toPact();
    }

    @Pact(consumer = CONSUMER_NAME)
    public RequestResponsePact sendA2ATaskPact(PactDslWithProvider builder) {
        return builder
            .given("A2A server is running and engine is healthy")
            .uponReceiving("a request to dispatch a task to the YAWL agent")
                .path("/a2a/tasks/send")
                .method("POST")
                .headers(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .headers(HEADER_AUTHORIZATION, "Bearer valid-jwt-token")
                .body(new PactDslJsonBody()
                    .stringType("taskId")
                    .stringValue("skill", "launch-workflow")
                    .object("message")
                        .stringType("role")
                        .minArrayLike("parts", 1)
                            .stringType("text")
                            .closeObject()
                        .closeArray()
                        .closeObject()
                )
            .willRespondWith()
                .status(200)
                .headers(Map.of(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON))
                .body(new PactDslJsonBody()
                    .stringType("taskId")
                    .stringMatcher("state", "submitted|working|completed|failed", "submitted")
                    .object("result")
                        .closeObject()
                )
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getMcpCapabilitiesPact")
    @DisplayName("Consumer correctly requests MCP server capabilities")
    void consumerCanGetMcpCapabilities(MockServer mockServer)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/mcp/capabilities"))
                .header(HEADER_AUTHORIZATION, "Bearer valid-jwt-token")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("capabilities"));
        assertTrue(response.body().contains("tools"));
    }

    @Test
    @PactTestFor(pactMethod = "invokeMcpToolPact")
    @DisplayName("Consumer correctly invokes an MCP tool")
    void consumerCanInvokeMcpTool(MockServer mockServer)
            throws IOException, InterruptedException {
        String body = "{\"toolName\":\"list-cases\",\"parameters\":{}}";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/mcp/tools/invoke"))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, "Bearer valid-jwt-token")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("success"));
    }

    @Test
    @PactTestFor(pactMethod = "getAgentCardPact")
    @DisplayName("Consumer correctly reads the A2A agent card")
    void consumerCanGetAgentCard(MockServer mockServer)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/.well-known/agent.json"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("skills"));
        assertTrue(response.body().contains("capabilities"));
    }

    @Test
    @PactTestFor(pactMethod = "sendA2ATaskPact")
    @DisplayName("Consumer correctly dispatches a task to the YAWL A2A agent")
    void consumerCanSendA2ATask(MockServer mockServer)
            throws IOException, InterruptedException {
        String body = "{\"taskId\":\"t-001\",\"skill\":\"launch-workflow\","
                    + "\"message\":{\"role\":\"user\","
                    + "\"parts\":[{\"text\":\"launch order process\"}]}}";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/a2a/tasks/send"))
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(HEADER_AUTHORIZATION, "Bearer valid-jwt-token")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("taskId"));
        assertTrue(response.body().contains("state"));
    }
}
