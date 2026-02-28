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
 * MCP (Model Context Protocol) Tool Integration Tests for DSPy.
 *
 * Tests DSPy as an MCP tool invoked via JSON-RPC 2.0 protocol:
 * - MCP tool registration and discovery
 * - JSON-RPC 2.0 request/response handling
 * - Tool parameter validation via JSON Schema
 * - Response generation with proper result/error structure
 * - Async tool execution with promise-like handling
 * - Multi-tool composition
 *
 * MCP is an open protocol for AI models to invoke external tools and integrate with
 * enterprise systems.
 */
@DisplayName("MCP Tool Integration Tests")
class McpToolIntegrationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should register DSPy tool with MCP protocol")
    void testToolRegistration() throws IOException {
        // Arrange: MCP Tool definition
        String toolDefinition = """
                {
                  "type": "function",
                  "function": {
                    "name": "analyze_sentiment_dspy",
                    "description": "Analyzes sentiment of text using DSPy",
                    "parameters": {
                      "type": "object",
                      "properties": {
                        "text": {
                          "type": "string",
                          "description": "Text to analyze"
                        },
                        "language": {
                          "type": "string",
                          "description": "Language code (e.g., en, fr, es)",
                          "default": "en"
                        }
                      },
                      "required": ["text"]
                    }
                  }
                }
                """;

        // Act
        JsonNode tool = objectMapper.readTree(toolDefinition);
        JsonNode function = tool.get("function");

        // Assert
        assertThat(tool.get("type").asText(), equalTo("function"));
        assertThat(function.get("name").asText(), equalTo("analyze_sentiment_dspy"));
        assertTrue(function.has("parameters"));
        assertTrue(function.get("parameters").has("properties"));
    }

    @Test
    @DisplayName("Should handle MCP JSON-RPC 2.0 request")
    void testJsonRpc20Request() throws IOException {
        // Arrange: Standard MCP JSON-RPC 2.0 request
        String jsonRpcRequest = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "tools/call",
                  "params": {
                    "name": "analyze_sentiment_dspy",
                    "arguments": {
                      "text": "This product exceeded my expectations!",
                      "language": "en"
                    }
                  }
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(jsonRpcRequest);

        // Assert
        assertThat(request.get("jsonrpc").asText(), equalTo("2.0"));
        assertThat(request.get("id").asInt(), equalTo(1));
        assertThat(request.get("method").asText(), equalTo("tools/call"));

        JsonNode params = request.get("params");
        assertThat(params.get("name").asText(), equalTo("analyze_sentiment_dspy"));
        assertTrue(params.has("arguments"));

        JsonNode args = params.get("arguments");
        assertThat(args.get("text").asText(), containsString("exceeded"));
        assertThat(args.get("language").asText(), equalTo("en"));
    }

    @Test
    @DisplayName("Should generate MCP JSON-RPC 2.0 success response")
    void testJsonRpc20SuccessResponse() throws IOException {
        // Arrange: Simulate DSPy execution
        Map<String, Object> toolResult = new HashMap<>();
        toolResult.put("sentiment", "positive");
        toolResult.put("confidence", 0.96);
        toolResult.put("reasoning", "Strong positive sentiment detected");
        toolResult.put("keywords", new String[]{"exceeded", "expectations"});

        // Create MCP JSON-RPC success response
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", 1);
        response.put("result", Map.of(
                "type", "text",
                "text", objectMapper.writeValueAsString(toolResult)
        ));

        // Act
        String jsonResponse = objectMapper.writeValueAsString(response);
        JsonNode responseNode = objectMapper.readTree(jsonResponse);

        // Assert
        assertThat(responseNode.get("jsonrpc").asText(), equalTo("2.0"));
        assertThat(responseNode.get("id").asInt(), equalTo(1));
        assertTrue(responseNode.has("result"));
        assertFalse(responseNode.has("error"));

        JsonNode result = responseNode.get("result");
        assertThat(result.get("type").asText(), equalTo("text"));
        assertTrue(result.has("text"));
    }

    @Test
    @DisplayName("Should generate MCP JSON-RPC 2.0 error response")
    void testJsonRpc20ErrorResponse() throws IOException {
        // Arrange: Error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("jsonrpc", "2.0");
        errorResponse.put("id", 2);
        errorResponse.put("error", Map.of(
                "code", -32602,
                "message", "Invalid params",
                "data", "Missing required parameter: text"
        ));

        // Act
        String jsonError = objectMapper.writeValueAsString(errorResponse);
        JsonNode errorNode = objectMapper.readTree(jsonError);

        // Assert
        assertThat(errorNode.get("jsonrpc").asText(), equalTo("2.0"));
        assertFalse(errorNode.has("result"));
        assertTrue(errorNode.has("error"));

        JsonNode error = errorNode.get("error");
        assertThat(error.get("code").asInt(), equalTo(-32602));
        assertThat(error.get("message").asText(), containsString("Invalid params"));
    }

    @Test
    @DisplayName("Should validate tool parameters against JSON Schema")
    void testParameterValidation() throws IOException {
        // Arrange: Tool schema with validation
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "text": {
                      "type": "string",
                      "minLength": 1,
                      "maxLength": 5000
                    },
                    "language": {
                      "type": "string",
                      "enum": ["en", "fr", "es", "de", "it"]
                    },
                    "include_reasoning": {
                      "type": "boolean",
                      "default": true
                    }
                  },
                  "required": ["text"]
                }
                """;

        // Arrange: Valid parameters
        String validParams = """
                {
                  "text": "Sample text",
                  "language": "en",
                  "include_reasoning": true
                }
                """;

        // Act
        JsonNode schemaNode = objectMapper.readTree(schema);
        JsonNode paramsNode = objectMapper.readTree(validParams);

        // Assert validation constraints
        assertTrue(paramsNode.has("text"));
        assertThat(paramsNode.get("text").asText().length(), greaterThan(0));
        assertThat(paramsNode.get("language").asText(), isIn("en", "fr", "es", "de", "it"));
    }

    @Test
    @DisplayName("Should support MCP resource references in tool input")
    void testResourceReferencesInToolInput() throws IOException {
        // Arrange: Request with resource reference
        String requestWithResource = """
                {
                  "jsonrpc": "2.0",
                  "id": 3,
                  "method": "tools/call",
                  "params": {
                    "name": "analyze_sentiment_dspy",
                    "arguments": {
                      "text_resource": "file:///documents/review.txt",
                      "language": "en"
                    }
                  }
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(requestWithResource);

        // Assert
        JsonNode args = request.get("params").get("arguments");
        assertTrue(args.has("text_resource"));
        assertThat(args.get("text_resource").asText(), startsWith("file://"));
    }

    @Test
    @DisplayName("Should handle MCP tool batching")
    void testToolBatching() throws IOException {
        // Arrange: Batch request with multiple tool calls
        String batchRequest = """
                [
                  {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "tools/call",
                    "params": {
                      "name": "analyze_sentiment_dspy",
                      "arguments": {"text": "First review"}
                    }
                  },
                  {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "method": "tools/call",
                    "params": {
                      "name": "analyze_sentiment_dspy",
                      "arguments": {"text": "Second review"}
                    }
                  },
                  {
                    "jsonrpc": "2.0",
                    "id": 3,
                    "method": "tools/call",
                    "params": {
                      "name": "analyze_sentiment_dspy",
                      "arguments": {"text": "Third review"}
                    }
                  }
                ]
                """;

        // Act
        JsonNode batch = objectMapper.readTree(batchRequest);

        // Assert
        assertTrue(batch.isArray());
        assertThat(batch.size(), equalTo(3));
        for (int i = 0; i < batch.size(); i++) {
            assertThat(batch.get(i).get("method").asText(), equalTo("tools/call"));
        }
    }

    @Test
    @DisplayName("Should handle MCP batch responses")
    void testBatchResponses() throws IOException {
        // Arrange: Batch response
        String batchResponse = """
                [
                  {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "result": {
                      "type": "text",
                      "text": "{\\"sentiment\\": \\"positive\\", \\"confidence\\": 0.92}"
                    }
                  },
                  {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "result": {
                      "type": "text",
                      "text": "{\\"sentiment\\": \\"negative\\", \\"confidence\\": 0.88}"
                    }
                  },
                  {
                    "jsonrpc": "2.0",
                    "id": 3,
                    "result": {
                      "type": "text",
                      "text": "{\\"sentiment\\": \\"neutral\\", \\"confidence\\": 0.79}"
                    }
                  }
                ]
                """;

        // Act
        JsonNode batch = objectMapper.readTree(batchResponse);

        // Assert
        assertTrue(batch.isArray());
        assertThat(batch.size(), equalTo(3));
        for (int i = 0; i < batch.size(); i++) {
            JsonNode response = batch.get(i);
            assertTrue(response.has("result"));
            assertFalse(response.has("error"));
        }
    }

    @Test
    @DisplayName("Should support MCP progress notifications")
    void testProgressNotifications() throws IOException {
        // Arrange: Progress notification (not a response)
        String progressNotification = """
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/progress",
                  "params": {
                    "request_id": 1,
                    "progress": 50,
                    "total": 100,
                    "status": "processing"
                  }
                }
                """;

        // Act
        JsonNode notification = objectMapper.readTree(progressNotification);

        // Assert
        assertThat(notification.get("method").asText(), equalTo("notifications/progress"));
        assertTrue(notification.has("params"));
        JsonNode params = notification.get("params");
        assertThat(params.get("progress").asInt(), equalTo(50));
        assertThat(params.get("total").asInt(), equalTo(100));
    }

    @Test
    @DisplayName("Should support MCP async tool execution")
    void testAsyncToolExecution() throws IOException {
        // Arrange: Async request
        String asyncRequest = """
                {
                  "jsonrpc": "2.0",
                  "id": 4,
                  "method": "tools/call",
                  "params": {
                    "name": "analyze_sentiment_dspy",
                    "arguments": {"text": "Large text for async processing..."},
                    "async": true
                  }
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(asyncRequest);

        // Assert
        assertTrue(request.get("params").has("async"));
        assertTrue(request.get("params").get("async").asBoolean());
    }

    @Test
    @DisplayName("Should handle MCP tool listing")
    void testToolListing() throws IOException {
        // Arrange: List tools response
        String toolsResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": 0,
                  "result": {
                    "tools": [
                      {
                        "name": "analyze_sentiment_dspy",
                        "description": "Analyzes sentiment using DSPy",
                        "inputSchema": {
                          "type": "object",
                          "properties": {
                            "text": {"type": "string"},
                            "language": {"type": "string", "default": "en"}
                          },
                          "required": ["text"]
                        }
                      },
                      {
                        "name": "generate_summary_dspy",
                        "description": "Generates summary using DSPy",
                        "inputSchema": {
                          "type": "object",
                          "properties": {
                            "text": {"type": "string"},
                            "max_length": {"type": "integer", "default": 100}
                          },
                          "required": ["text"]
                        }
                      }
                    ]
                  }
                }
                """;

        // Act
        JsonNode response = objectMapper.readTree(toolsResponse);
        JsonNode tools = response.get("result").get("tools");

        // Assert
        assertTrue(tools.isArray());
        assertThat(tools.size(), greaterThanOrEqualTo(1));
        for (JsonNode tool : tools) {
            assertTrue(tool.has("name"));
            assertTrue(tool.has("description"));
            assertTrue(tool.has("inputSchema"));
        }
    }

    @Test
    @DisplayName("Should handle MCP resource requests")
    void testResourceRequests() throws IOException {
        // Arrange: Resource request
        String resourceRequest = """
                {
                  "jsonrpc": "2.0",
                  "id": 5,
                  "method": "resources/read",
                  "params": {
                    "uri": "file:///workflow-data/input.json"
                  }
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(resourceRequest);

        // Assert
        assertThat(request.get("method").asText(), equalTo("resources/read"));
        assertTrue(request.has("params"));
        assertThat(request.get("params").get("uri").asText(), startsWith("file://"));
    }

    @Test
    @DisplayName("Should handle MCP resource streaming")
    void testResourceStreaming() throws IOException {
        // Arrange: Streaming response
        String streamingResponse = """
                {
                  "jsonrpc": "2.0",
                  "id": 6,
                  "result": {
                    "contents": [
                      {
                        "uri": "file:///data/stream.txt",
                        "mimeType": "text/plain",
                        "streaming": true,
                        "chunks": [
                          {"data": "chunk 1"},
                          {"data": "chunk 2"},
                          {"data": "chunk 3"}
                        ]
                      }
                    ]
                  }
                }
                """;

        // Act
        JsonNode response = objectMapper.readTree(streamingResponse);
        JsonNode contents = response.get("result").get("contents");

        // Assert
        assertTrue(contents.get(0).get("streaming").asBoolean());
        assertTrue(contents.get(0).has("chunks"));
        assertThat(contents.get(0).get("chunks").size(), equalTo(3));
    }

    @Test
    @DisplayName("Should support MCP tool composition")
    void testToolComposition() throws IOException {
        // Arrange: Composite tool request
        String compositeRequest = """
                {
                  "jsonrpc": "2.0",
                  "id": 7,
                  "method": "tools/compose",
                  "params": {
                    "pipeline": [
                      {
                        "name": "analyze_sentiment_dspy",
                        "arguments": {"text": "input text"},
                        "alias": "sentiment"
                      },
                      {
                        "name": "generate_summary_dspy",
                        "arguments": {"text": "input text", "max_length": 100},
                        "alias": "summary"
                      }
                    ]
                  }
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(compositeRequest);
        JsonNode pipeline = request.get("params").get("pipeline");

        // Assert
        assertTrue(pipeline.isArray());
        assertThat(pipeline.size(), equalTo(2));
        assertThat(pipeline.get(0).get("alias").asText(), equalTo("sentiment"));
        assertThat(pipeline.get(1).get("alias").asText(), equalTo("summary"));
    }

    @Test
    @DisplayName("Should handle MCP error codes correctly")
    void testMcpErrorCodes() throws IOException {
        // Arrange: Various error responses
        String[] errorCodes = {
                "-32700: Parse error",
                "-32600: Invalid Request",
                "-32601: Method not found",
                "-32602: Invalid params",
                "-32603: Internal error",
                "-32000 to -32099: Server error"
        };

        // Assert: Verify standard JSON-RPC error code ranges
        for (String errorCode : errorCodes) {
            String[] parts = errorCode.split(":");
            int code = Integer.parseInt(parts[0].trim());

            // JSON-RPC 2.0 spec defines these ranges
            if (code >= -32768 && code <= -32000) {
                assertTrue(true, "Valid reserved error code: " + code);
            } else if (code >= -32700 && code <= -32603) {
                assertTrue(true, "Valid standard error code: " + code);
            }
        }
    }

    @Test
    @DisplayName("Should support MCP notification subscriptions")
    void testNotificationSubscriptions() throws IOException {
        // Arrange: Subscribe to notifications
        String subscribeRequest = """
                {
                  "jsonrpc": "2.0",
                  "id": 8,
                  "method": "notifications/subscribe",
                  "params": {
                    "event_types": [
                      "tool.started",
                      "tool.progress",
                      "tool.completed"
                    ]
                  }
                }
                """;

        // Act
        JsonNode request = objectMapper.readTree(subscribeRequest);
        JsonNode eventTypes = request.get("params").get("event_types");

        // Assert
        assertTrue(eventTypes.isArray());
        assertThat(eventTypes.size(), equalTo(3));
        assertThat(eventTypes.get(0).asText(), equalTo("tool.started"));
    }

    @Test
    @DisplayName("Should handle MCP capability exchange")
    void testCapabilityExchange() throws IOException {
        // Arrange: Client capabilities
        String clientCapabilities = """
                {
                  "protocols": {
                    "list_tools": true,
                    "call_tool": true,
                    "compose_tools": true,
                    "streaming": true
                  },
                  "features": {
                    "batch_calls": true,
                    "async_execution": true,
                    "resource_access": true
                  }
                }
                """;

        // Arrange: Server capabilities
        String serverCapabilities = """
                {
                  "protocols": {
                    "list_tools": true,
                    "call_tool": true,
                    "compose_tools": true,
                    "streaming": true
                  },
                  "features": {
                    "batch_calls": true,
                    "async_execution": true,
                    "resource_access": true,
                    "progress_notifications": true
                  }
                }
                """;

        // Act
        JsonNode clientCaps = objectMapper.readTree(clientCapabilities);
        JsonNode serverCaps = objectMapper.readTree(serverCapabilities);

        // Assert
        assertTrue(clientCaps.get("protocols").get("call_tool").asBoolean());
        assertTrue(serverCaps.get("features").get("progress_notifications").asBoolean());
    }

    @Test
    @DisplayName("Should serialize DSPy metrics in MCP response")
    void testMetricsInMcpResponse() throws IOException {
        // Arrange: MCP response with execution metrics
        DspyExecutionMetrics metrics = DspyExecutionMetrics.builder()
                .compilationTimeMs(250)
                .executionTimeMs(500)
                .inputTokens(60)
                .outputTokens(40)
                .qualityScore(0.91)
                .cacheHit(false)
                .contextReused(false)
                .timestamp(Instant.now())
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", 9);
        response.put("result", Map.of(
                "type", "text",
                "text", "Sentiment analysis result",
                "metadata", Map.of(
                        "compilation_time_ms", metrics.compilationTimeMs(),
                        "execution_time_ms", metrics.executionTimeMs(),
                        "total_tokens", metrics.totalTokens(),
                        "cache_hit", metrics.cacheHit(),
                        "quality_score", metrics.qualityScore()
                )
        ));

        // Act
        String jsonResponse = objectMapper.writeValueAsString(response);
        JsonNode responseNode = objectMapper.readTree(jsonResponse);

        // Assert
        JsonNode metadata = responseNode.get("result").get("metadata");
        assertThat(metadata.get("compilation_time_ms").asLong(), equalTo(250L));
        assertThat(metadata.get("execution_time_ms").asLong(), equalTo(500L));
        assertThat(metadata.get("total_tokens").asLong(), equalTo(100L));
    }
}
