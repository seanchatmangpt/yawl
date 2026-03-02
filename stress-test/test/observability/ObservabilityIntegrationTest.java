package org.yawlfoundation.yawl.observability;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.observability.mcp.SimpleObservabilityMcpServer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for YAWL Observability MCP Server.
 *
 * Tests the complete functionality including:
 * - HTTP server startup and shutdown
 * - MCP protocol endpoints
 * - Health monitoring
 * - Metrics collection
 */
public class ObservabilityIntegrationTest {

    private static SimpleObservabilityMcpServer server;
    private static HttpClient httpClient;

    @BeforeAll
    static void setup() throws IOException {
        // Start the MCP server
        server = new SimpleObservabilityMcpServer(0, "integration-test");
        server.start();

        // Create HTTP client
        httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void cleanup() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testServerStartup() {
        assertNotNull(server, "Server should be created");
    }

    @Test
    void testRootEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/"))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("server"));
        assertTrue(response.body().contains("status"));
    }

    @Test
    void testHealthEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/health"))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("status"));
        assertTrue(response.body().contains("timestamp"));
    }

    @Test
    void testMetricsEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/metrics"))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("cases_active"));
        assertTrue(response.body().contains("queue_depth"));
    }

    @Test
    void testMcpInitialize() throws IOException, InterruptedException {
        String requestBody = """
            {
                "method": "initialize",
                "params": {}
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("protocolVersion"));
        assertTrue(response.body().contains("capabilities"));
    }

    @Test
    void testMcpToolsList() throws IOException, InterruptedException {
        String requestBody = """
            {
                "method": "tools/list",
                "params": {}
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("get_metrics"));
        assertTrue(response.body().contains("monitor_workflow"));
        assertTrue(response.body().contains("health_check"));
    }

    @Test
    void testMcpGetMetrics() throws IOException, InterruptedException {
        String requestBody = """
            {
                "method": "tools/call",
                "params": {
                    "name": "get_metrics",
                    "arguments": {}
                }
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("cases_active"));
        assertTrue(response.body().contains("queue_depth"));
        assertTrue(response.body().contains("active_threads"));
    }

    @Test
    void testMcpMonitorWorkflow() throws IOException, InterruptedException {
        String requestBody = """
            {
                "method": "tools/call",
                "params": {
                    "name": "monitor_workflow",
                    "arguments": {
                        "workflowId": "test-case-123",
                        "maxDuration": 300000
                    }
                }
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("workflow_id"));
        assertTrue(response.body().contains("monitoring_started"));
        assertTrue(response.body().contains("max_duration_ms"));
    }

    @Test
    void testMcpHealthCheck() throws IOException, InterruptedException {
        String requestBody = """
            {
                "method": "tools/call",
                "params": {
                    "name": "health_check",
                    "arguments": {}
                }
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("memory_free"));
        assertTrue(response.body().contains("memory_total"));
        assertTrue(response.body().contains("memory_max"));
        assertTrue(response.body().contains("memory_usage_percent"));
        assertTrue(response.body().contains("thread_count"));
    }

    @Test
    void testTraceCorrelation() throws IOException, InterruptedException {
        // First monitor a workflow
        String requestBody = """
            {
                "method": "tools/call",
                "params": {
                    "name": "monitor_workflow",
                    "arguments": {
                        "workflowId": "trace-test-case",
                        "maxDuration": 300000
                    }
                }
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Now get the trace
        String traceRequestBody = """
            {
                "method": "tools/call",
                "params": {
                    "name": "correlate_trace",
                    "arguments": {
                        "workflowId": "trace-test-case"
                    }
                }
            }
            """;

        HttpRequest traceRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + server.getPort() + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(traceRequestBody))
            .build();

        HttpResponse<String> traceResponse = httpClient.send(traceRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, traceResponse.statusCode());
        assertTrue(traceResponse.body().contains("workflow_id"));
        assertTrue(traceResponse.body().contains("trace_id"));
        assertTrue(traceResponse.body().contains("span_id"));
    }
}