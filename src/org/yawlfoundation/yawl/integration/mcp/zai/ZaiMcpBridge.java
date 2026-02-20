package org.yawlfoundation.yawl.integration.mcp.zai;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Bridge interface for connecting YAWL MCP server to Z.AI MCP tools.
 *
 * <p>Provides a unified interface for calling Z.AI tools from within
 * the YAWL MCP server. Supports both STDIO and HTTP transports.
 *
 * <p><b>Available Z.AI Tools:</b>
 * <ul>
 *   <li>{@code ui_to_artifact} - Convert UI screenshots to YAWL specifications</li>
 *   <li>{@code analyze_image} - General image analysis</li>
 *   <li>{@code analyze_data_visualization} - Chart/graph analysis</li>
 *   <li>{@code web_reader} - Web content extraction</li>
 *   <li>{@code web_search} - Web search for documentation</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * ZaiMcpBridge bridge = ZaiMcpBridge.create(config);
 *
 * // Generate YAWL spec from UI screenshot
 * Map<String, Object> result = bridge.callTool("ui_to_artifact", Map.of(
 *     "image_source", "/path/to/screenshot.png",
 *     "output_type", "code",
 *     "prompt", "Generate YAWL XML workflow from this UI mockup"
 * )).join();
 *
 * String yawlSpec = (String) result.get("code");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public interface ZaiMcpBridge extends AutoCloseable {

    /**
     * Create a ZAI MCP bridge with the given configuration.
     *
     * @param config bridge configuration
     * @return configured bridge instance
     * @throws IOException if bridge initialization fails
     */
    static ZaiMcpBridge create(ZaiMcpConfig config) throws IOException {
        return switch (config.getMode()) {
            case STDIO -> new StdioZaiMcpBridge(config);
            case HTTP -> new HttpZaiMcpBridge(config);
        };
    }

    /**
     * Call a Z.AI tool with the given parameters.
     *
     * @param toolName   name of the Z.AI tool to call
     * @param parameters parameters to pass to the tool
     * @return future containing the tool result
     */
    CompletableFuture<Map<String, Object>> callTool(String toolName, Map<String, Object> parameters);

    /**
     * Call a Z.AI tool synchronously with timeout.
     *
     * @param toolName   name of the Z.AI tool to call
     * @param parameters parameters to pass to the tool
     * @param timeout    maximum time to wait
     * @return tool result
     * @throws IOException if the call fails or times out
     */
    default Map<String, Object> callToolSync(String toolName,
                                              Map<String, Object> parameters,
                                              Duration timeout) throws IOException {
        try {
            return callTool(toolName, parameters)
                .orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .join();
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IOException("Tool call timed out after " + timeout, e);
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioEx) {
                throw ioEx;
            }
            throw new IOException("Tool call failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * List available Z.AI tools.
     *
     * @return map of tool names to descriptions
     */
    Map<String, String> listTools();

    /**
     * Check if a specific tool is available.
     *
     * @param toolName tool name to check
     * @return true if tool is available
     */
    boolean isToolAvailable(String toolName);

    /**
     * Get the bridge configuration.
     *
     * @return configuration
     */
    ZaiMcpConfig getConfig();

    /**
     * Check if the bridge is healthy and connected.
     *
     * @return true if bridge is operational
     */
    boolean isHealthy();

    /**
     * Get cache statistics.
     *
     * @return map of cache stats (hits, misses, size)
     */
    default Optional<Map<String, Object>> getCacheStats() {
        return Optional.empty();
    }

    /**
     * Clear the result cache.
     */
    default void clearCache() {
        // Default: no-op for bridges without caching
    }

    @Override
    default void close() {
        // Default: no-op for bridges without resources
    }
}
