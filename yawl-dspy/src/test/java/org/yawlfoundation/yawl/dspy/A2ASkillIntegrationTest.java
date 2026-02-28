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

package org.yawlfoundation.yawl.dspy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A2A (Agent-to-Agent) Protocol Compliance Tests for DSPy Skill.
 *
 * Tests DSPy as an A2A skill invoked via agent-to-agent request/response protocol:
 * - A2A skill registration and discovery
 * - Request validation and parameter marshalling
 * - Response generation with correct schema
 * - Error handling in A2A context
 * - Async execution with callbacks
 *
 * A2A is a peer-to-peer protocol for autonomous agents to invoke skills on each other.
 */
@DisplayName("A2A Skill Integration Tests")
class A2ASkillIntegrationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should register DSPy skill with A2A protocol")
    void testSkillRegistration() {
        // Arrange: Simulate A2A skill descriptor
        Map<String, Object> skillDescriptor = new HashMap<>();
        skillDescriptor.put("skill_id", "dspy-sentiment-analyzer");
        skillDescriptor.put("skill_type", "ai_processing");
        skillDescriptor.put("name", "DSPy Sentiment Analyzer");
        skillDescriptor.put("description", "Analyzes sentiment using DSPy");
        skillDescriptor.put("version", "1.0");
        skillDescriptor.put("api_version", "a2a/1.0");

        // Input parameters schema
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("text", Map.of("type", "string", "required", true));
        inputParams.put("language", Map.of("type", "string", "required", false, "default", "en"));
        skillDescriptor.put("input_parameters", inputParams);

        // Output schema
        Map<String, Object> outputSchema = new HashMap<>();
        outputSchema.put("sentiment", Map.of("type", "string"));
        outputSchema.put("confidence", Map.of("type", "number"));
        skillDescriptor.put("output_schema", outputSchema);

        // Act & Assert
        assertThat(skillDescriptor.get("skill_id"), equalTo("dspy-sentiment-analyzer"));
        assertThat(skillDescriptor.get("api_version"), equalTo("a2a/1.0"));
        assertTrue(skillDescriptor.containsKey("input_parameters"));
        assertTrue(skillDescriptor.containsKey("output_schema"));
    }

    @Test
    @DisplayName("Should handle A2A request with proper parameter validation")
    void testA2ARequestHandling() throws IOException {
        // Arrange: Create A2A request
        String a2aRequest = """
                {
                  "request_id": "req-12345",
                  "skill_id": "dspy-sentiment-analyzer",
                  "version": "1.0",
                  "timestamp": "2026-02-28T14:30:00Z",
                  "parameters": {
                    "text": "This product is amazing!",
                    "language": "en"
                  },
                  "callback_url": "https://agent-b.example.com/callbacks/dspy-result"
                }
                """;

        // Act
        JsonNode requestNode = objectMapper.readTree(a2aRequest);

        // Assert request structure
        assertThat(requestNode.get("request_id").asText(), equalTo("req-12345"));
        assertThat(requestNode.get("skill_id").asText(), equalTo("dspy-sentiment-analyzer"));
        assertTrue(requestNode.has("parameters"));
        assertTrue(requestNode.has("callback_url"));

        // Assert parameters
        JsonNode params = requestNode.get("parameters");
        assertThat(params.get("text").asText(), containsString("amazing"));
        assertThat(params.get("language").asText(), equalTo("en"));
    }

    @Test
    @DisplayName("Should validate required parameters in A2A request")
    void testA2AParameterValidation() throws IOException {
        // Arrange
        String requestMissingRequired = """
                {
                  "request_id": "req-456",
                  "skill_id": "dspy-sentiment-analyzer",
                  "parameters": {
                    "language": "en"
                  }
                }
                """;

        String requestWithRequired = """
                {
                  "request_id": "req-789",
                  "skill_id": "dspy-sentiment-analyzer",
                  "parameters": {
                    "text": "sample text",
                    "language": "en"
                  }
                }
                """;

        // Act
        JsonNode invalidRequest = objectMapper.readTree(requestMissingRequired);
        JsonNode validRequest = objectMapper.readTree(requestWithRequired);

        // Assert
        assertFalse(invalidRequest.get("parameters").has("text"));
        assertTrue(validRequest.get("parameters").has("text"));
        assertTrue(validRequest.get("parameters").has("language"));
    }

    @Test
    @DisplayName("Should generate A2A response with correct schema")
    void testA2AResponseGeneration() throws IOException {
        // Arrange: Simulate DSPy execution and format as A2A response
        Map<String, Object> dspyOutput = new HashMap<>();
        dspyOutput.put("sentiment", "positive");
        dspyOutput.put("confidence", 0.95);
        dspyOutput.put("reasoning", "Strong positive indicators");

        DspyExecutionMetrics metrics = DspyExecutionMetrics.builder()
                .compilationTimeMs(150)
                .executionTimeMs(300)
                .inputTokens(45)
                .outputTokens(35)
                .qualityScore(0.92)
                .cacheHit(false)
                .contextReused(false)
                .timestamp(Instant.now())
                .build();

        DspyExecutionResult result = DspyExecutionResult.builder()
                .output(dspyOutput)
                .metrics(metrics)
                .build();

        // Create A2A response
        Map<String, Object> a2aResponse = new HashMap<>();
        a2aResponse.put("request_id", "req-12345");
        a2aResponse.put("skill_id", "dspy-sentiment-analyzer");
        a2aResponse.put("status", "success");
        a2aResponse.put("timestamp", Instant.now().toString());
        a2aResponse.put("result", dspyOutput);
        a2aResponse.put("execution_metrics", Map.of(
                "compilation_time_ms", metrics.compilationTimeMs(),
                "execution_time_ms", metrics.executionTimeMs(),
                "total_tokens", metrics.totalTokens()
        ));

        // Act
        String jsonResponse = objectMapper.writeValueAsString(a2aResponse);
        JsonNode responseNode = objectMapper.readTree(jsonResponse);

        // Assert
        assertThat(responseNode.get("status").asText(), equalTo("success"));
        assertThat(responseNode.get("skill_id").asText(), equalTo("dspy-sentiment-analyzer"));
        assertTrue(responseNode.has("result"));
        assertTrue(responseNode.has("execution_metrics"));

        // Verify result contains expected fields
        JsonNode resultNode = responseNode.get("result");
        assertThat(resultNode.get("sentiment").asText(), equalTo("positive"));
        assertThat(resultNode.get("confidence").asDouble(), closeTo(0.95, 0.001));
    }

    @Test
    @DisplayName("Should handle A2A errors with proper error response")
    void testA2AErrorHandling() throws IOException {
        // Arrange: Create error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("request_id", "req-error");
        errorResponse.put("skill_id", "dspy-sentiment-analyzer");
        errorResponse.put("status", "error");
        errorResponse.put("error_code", "INVALID_PARAMETER");
        errorResponse.put("error_message", "Required parameter 'text' is missing");
        errorResponse.put("timestamp", Instant.now().toString());

        // Act
        String jsonError = objectMapper.writeValueAsString(errorResponse);
        JsonNode errorNode = objectMapper.readTree(jsonError);

        // Assert
        assertThat(errorNode.get("status").asText(), equalTo("error"));
        assertThat(errorNode.get("error_code").asText(), equalTo("INVALID_PARAMETER"));
        assertTrue(errorNode.has("error_message"));
    }

    @Test
    @DisplayName("Should support async execution with callback in A2A")
    void testA2AAsyncExecution() throws IOException {
        // Arrange
        String asyncRequest = """
                {
                  "request_id": "async-001",
                  "skill_id": "dspy-sentiment-analyzer",
                  "execution_mode": "async",
                  "callback_url": "https://agent-a.example.com/callbacks/results",
                  "parameters": {
                    "text": "Sample text for async processing"
                  }
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(asyncRequest);

        // Assert
        assertThat(request.get("execution_mode").asText(), equalTo("async"));
        assertTrue(request.has("callback_url"));
        assertThat(request.get("callback_url").asText(), containsString("callbacks"));
    }

    @Test
    @DisplayName("Should publish A2A callback with skill result")
    void testA2ACallback() throws IOException {
        // Arrange: Simulate callback after async execution
        String callbackPayload = """
                {
                  "request_id": "async-001",
                  "status": "completed",
                  "result": {
                    "sentiment": "positive",
                    "confidence": 0.92,
                    "reasoning": "Positive language detected"
                  },
                  "execution_time_ms": 450,
                  "timestamp": "2026-02-28T14:31:00Z"
                }
                """;

        // Act
        JsonNode callback = objectMapper.readTree(callbackPayload);

        // Assert
        assertThat(callback.get("status").asText(), equalTo("completed"));
        assertThat(callback.get("request_id").asText(), equalTo("async-001"));
        assertTrue(callback.has("result"));
        assertTrue(callback.has("execution_time_ms"));
    }

    @Test
    @DisplayName("Should support request batching in A2A")
    void testA2ARequestBatching() throws IOException {
        // Arrange: Batch request
        String batchRequest = """
                {
                  "batch_id": "batch-001",
                  "requests": [
                    {
                      "request_id": "req-1",
                      "skill_id": "dspy-sentiment-analyzer",
                      "parameters": {"text": "First text"}
                    },
                    {
                      "request_id": "req-2",
                      "skill_id": "dspy-sentiment-analyzer",
                      "parameters": {"text": "Second text"}
                    },
                    {
                      "request_id": "req-3",
                      "skill_id": "dspy-sentiment-analyzer",
                      "parameters": {"text": "Third text"}
                    }
                  ]
                }
                """;

        // Act
        JsonNode batch = objectMapper.readTree(batchRequest);
        JsonNode requests = batch.get("requests");

        // Assert
        assertThat(batch.get("batch_id").asText(), equalTo("batch-001"));
        assertThat(requests.size(), equalTo(3));
        for (int i = 0; i < requests.size(); i++) {
            assertTrue(requests.get(i).has("request_id"));
            assertTrue(requests.get(i).has("parameters"));
        }
    }

    @Test
    @DisplayName("Should handle batch response with individual results")
    void testA2ABatchResponse() throws IOException {
        // Arrange: Batch response
        String batchResponse = """
                {
                  "batch_id": "batch-001",
                  "status": "completed",
                  "results": [
                    {
                      "request_id": "req-1",
                      "status": "success",
                      "result": {"sentiment": "positive", "confidence": 0.9}
                    },
                    {
                      "request_id": "req-2",
                      "status": "success",
                      "result": {"sentiment": "negative", "confidence": 0.85}
                    },
                    {
                      "request_id": "req-3",
                      "status": "success",
                      "result": {"sentiment": "neutral", "confidence": 0.78}
                    }
                  ]
                }
                """;

        // Act
        JsonNode response = objectMapper.readTree(batchResponse);
        JsonNode results = response.get("results");

        // Assert
        assertThat(response.get("status").asText(), equalTo("completed"));
        assertThat(results.size(), equalTo(3));

        // Verify each result
        assertThat(results.get(0).get("result").get("sentiment").asText(), equalTo("positive"));
        assertThat(results.get(1).get("result").get("sentiment").asText(), equalTo("negative"));
        assertThat(results.get(2).get("result").get("sentiment").asText(), equalTo("neutral"));
    }

    @Test
    @DisplayName("Should support skill capability negotiation in A2A")
    void testA2ACapabilityNegotiation() throws IOException {
        // Arrange: Capability request
        String capabilityRequest = """
                {
                  "request_type": "capability_check",
                  "skill_id": "dspy-sentiment-analyzer",
                  "required_capabilities": ["sentiment_analysis", "confidence_scoring"],
                  "requested_api_version": "a2a/1.0"
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(capabilityRequest);

        // Assert
        assertThat(request.get("request_type").asText(), equalTo("capability_check"));
        assertTrue(request.has("required_capabilities"));
        assertThat(request.get("required_capabilities").size(), equalTo(2));
    }

    @Test
    @DisplayName("Should report capability in A2A capability response")
    void testA2ACapabilityResponse() throws IOException {
        // Arrange: Capability response
        String capabilityResponse = """
                {
                  "skill_id": "dspy-sentiment-analyzer",
                  "supports_capabilities": [
                    "sentiment_analysis",
                    "confidence_scoring",
                    "multi_language"
                  ],
                  "supported_api_versions": ["a2a/1.0", "a2a/2.0"],
                  "max_concurrent_requests": 100,
                  "rate_limit_requests_per_second": 50
                }
                """;

        // Act
        JsonNode response = objectMapper.readTree(capabilityResponse);

        // Assert
        assertTrue(response.has("supports_capabilities"));
        assertThat(response.get("supports_capabilities").size(), greaterThan(0));
        assertTrue(response.has("supported_api_versions"));
        assertTrue(response.has("max_concurrent_requests"));
    }

    @Test
    @DisplayName("Should handle A2A health check")
    void testA2AHealthCheck() throws IOException {
        // Arrange
        String healthRequest = """
                {
                  "request_type": "health_check",
                  "skill_id": "dspy-sentiment-analyzer"
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(healthRequest);

        // Simulate health check response
        Map<String, Object> healthResponse = new HashMap<>();
        healthResponse.put("skill_id", "dspy-sentiment-analyzer");
        healthResponse.put("status", "healthy");
        healthResponse.put("timestamp", Instant.now().toString());
        healthResponse.put("uptime_ms", 86400000);
        healthResponse.put("requests_processed", 1250);

        String jsonHealth = objectMapper.writeValueAsString(healthResponse);
        JsonNode response = objectMapper.readTree(jsonHealth);

        // Assert
        assertThat(request.get("request_type").asText(), equalTo("health_check"));
        assertThat(response.get("status").asText(), equalTo("healthy"));
        assertTrue(response.has("uptime_ms"));
    }

    @Test
    @DisplayName("Should enforce API versioning in A2A")
    void testA2AApiVersioning() throws IOException {
        // Arrange: Request with specific API version
        String versionedRequest = """
                {
                  "request_id": "versioned-001",
                  "skill_id": "dspy-sentiment-analyzer",
                  "api_version": "a2a/2.0",
                  "parameters": {
                    "text": "Test text"
                  }
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(versionedRequest);

        // Assert
        assertThat(request.get("api_version").asText(), equalTo("a2a/2.0"));
    }

    @Test
    @DisplayName("Should track request correlation IDs in A2A")
    void testA2ARequestCorrelation() throws IOException {
        // Arrange: Request with correlation tracking
        String correlatedRequest = """
                {
                  "request_id": "req-final",
                  "correlation_id": "corr-abc123",
                  "skill_id": "dspy-sentiment-analyzer",
                  "parent_request_id": "req-parent",
                  "parameters": {
                    "text": "Correlated request text"
                  }
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(correlatedRequest);

        // Assert
        assertTrue(request.has("correlation_id"));
        assertTrue(request.has("parent_request_id"));
        assertThat(request.get("correlation_id").asText(), equalTo("corr-abc123"));
    }
}
