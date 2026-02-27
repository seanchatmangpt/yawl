/*
 * Example usage of McpResponseBuilder in an MCP tool specification.
 *
 * This demonstrates how to use the McpResponseBuilder utility class
 * to create consistent MCP tool responses.
 */

import org.yawlfoundation.yawl.integration.mcp.util.McpResponseBuilder;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.HashMap;

public class McpResponseBuilderExample {

    /**
     * Example MCP tool that demonstrates using McpResponseBuilder.
     */
    public McpServerFeatures.SyncToolSpecification createExampleTool() {
        // Define tool schema
        java.util.Map<String, Object> inputProps = new java.util.HashMap<>();
        inputProps.put("operation", Map.of("type", "string",
            "description", "Operation to perform: 'status', 'data', or 'error'"));
        inputProps.put("timeout", Map.of("type", "integer", "description",
            "Timeout in milliseconds (optional)", "default", 5000));

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name("example_tool")
            .description("Example tool demonstrating McpResponseBuilder usage")
            .inputSchema(new McpSchema.JsonSchema("object", inputProps,
                List.of("operation"), false, null, Map.of()))
            .build();

        // Implement tool handler using McpResponseBuilder
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            long start = System.currentTimeMillis();

            try {
                String operation = getString(request.arguments(), "operation", null);
                Integer timeout = getInt(request.arguments(), "timeout", 5000);

                switch (operation) {
                    case "status":
                        // Simple success response
                        long elapsed = System.currentTimeMillis() - start;
                        return McpResponseBuilder.successWithTiming(
                            "System status: OK", "status-check", elapsed);

                    case "data":
                        // Response with structured data
                        Map<String, Object> data = new HashMap<>();
                        data.put("version", "1.0.0");
                        data.put("uptime_ms", System.currentTimeMillis() % 1000000);
                        data.put("available", true);
                        return McpResponseBuilder.successWithData(data, elapsed);

                    case "error":
                        // Simulate an error
                        throw new RuntimeException("Simulated error occurred");

                    default:
                        return McpResponseBuilder.error(
                            "Unknown operation: " + operation);
                }

            } catch (Exception e) {
                // Handle any exceptions
                return McpResponseBuilder.error("example_tool", e);
            }
        });
    }

    // Helper methods for extracting request arguments
    private String getString(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Integer getInt(Map<String, Object> args, String key, Integer defaultValue) {
        Object value = args.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}