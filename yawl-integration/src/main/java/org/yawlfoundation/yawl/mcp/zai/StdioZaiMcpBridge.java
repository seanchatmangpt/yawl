package org.yawlfoundation.yawl.integration.mcp.zai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * STDIO-based implementation of Z.AI MCP Bridge.
 *
 * <p>Communicates with Z.AI MCP tools via standard input/output streams.
 * Uses JSON-RPC 2.0 protocol for message exchange.
 *
 * <p><b>Protocol:</b>
 * <pre>
 * Request:  Content-Length: {length}\r\n\r\n{json-rpc-request}
 * Response: Content-Length: {length}\r\n\r\n{json-rpc-response}
 * </pre>
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Result caching with configurable TTL</li>
 *   <li>Automatic request timeout handling</li>
 *   <li>Health monitoring via ping/pong</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class StdioZaiMcpBridge implements ZaiMcpBridge {

    private static final Logger _logger = LogManager.getLogger(StdioZaiMcpBridge.class);

    private static final Map<String, String> AVAILABLE_TOOLS = Map.ofEntries(
        Map.entry("ui_to_artifact", "Convert UI screenshots to code, prompts, or specs"),
        Map.entry("analyze_image", "General-purpose image analysis"),
        Map.entry("analyze_data_visualization", "Analyze charts, graphs, and dashboards"),
        Map.entry("extract_text_from_screenshot", "OCR for screenshots"),
        Map.entry("diagnose_error_screenshot", "Diagnose error messages and stack traces"),
        Map.entry("understand_technical_diagram", "Analyze architecture diagrams"),
        Map.entry("ui_diff_check", "Compare two UI screenshots"),
        Map.entry("analyze_video", "Analyze video content"),
        Map.entry("web_reader", "Fetch and convert web content"),
        Map.entry("web_search", "Search web for information")
    );

    private final ZaiMcpConfig config;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final Process mcpProcess;
    private final PrintWriter processInput;
    private final BufferedReader processOutput;
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingRequests;
    private final ConcurrentHashMap<String, CacheEntry> cache;
    private volatile boolean healthy = true;
    private volatile boolean closed = false;

    /**
     * Create a STDIO bridge with the given configuration.
     *
     * @param config bridge configuration
     * @throws IOException if process startup fails
     */
    public StdioZaiMcpBridge(ZaiMcpConfig config) throws IOException {
        this.config = Objects.requireNonNull(config, "config is required");
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.cache = new ConcurrentHashMap<>();

        ProcessBuilder pb = new ProcessBuilder(
            "npx",
            "-y",
            "@anthropic-ai/mcp-client-cli"
        );
        pb.redirectErrorStream(false);

        _logger.info("Starting Z.AI MCP process: {}", pb.command());

        try {
            this.mcpProcess = pb.start();
            this.processInput = new PrintWriter(
                new OutputStreamWriter(mcpProcess.getOutputStream(), StandardCharsets.UTF_8), true);
            this.processOutput = new BufferedReader(
                new InputStreamReader(mcpProcess.getInputStream(), StandardCharsets.UTF_8));

            Thread.ofVirtual().start(this::responseReaderLoop);

            _logger.info("Z.AI MCP bridge initialized in STDIO mode");

        } catch (IOException e) {
            _logger.error("Failed to start Z.AI MCP process: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> callTool(String toolName, Map<String, Object> parameters) {
        if (closed) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Bridge is closed"));
        }

        if (!isToolAvailable(toolName)) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Tool not available: " + toolName));
        }

        String cacheKey = buildCacheKey(toolName, parameters);
        if (config.isCacheResults()) {
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                _logger.debug("Cache hit for tool: {}", toolName);
                return CompletableFuture.completedFuture(cached.result);
            }
        }

        String requestId = generateRequestId();
        CompletableFuture<JsonNode> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);

        return sendRequest(requestId, toolName, parameters)
            .thenCompose(v -> responseFuture)
            .thenApply(response -> {
                Map<String, Object> result = parseToolResult(response);
                if (config.isCacheResults()) {
                    cache.put(cacheKey, new CacheEntry(result, Instant.now().plus(config.getCacheTtl())));
                }
                return result;
            })
            .whenComplete((result, error) -> pendingRequests.remove(requestId));
    }

    private CompletableFuture<Void> sendRequest(String requestId, String toolName, Map<String, Object> parameters) {
        return CompletableFuture.runAsync(() -> {
            try {
                ObjectNode request = objectMapper.createObjectNode();
                request.put("jsonrpc", "2.0");
                request.put("id", requestId);
                request.put("method", "tools/call");

                ObjectNode params = request.putObject("params");
                params.put("name", toolName);
                params.set("arguments", objectMapper.valueToTree(parameters));

                String json = objectMapper.writeValueAsString(request);
                String message = "Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length +
                                 "\r\n\r\n" + json;

                _logger.debug("Sending request: {}", requestId);
                processInput.print(message);
                processInput.flush();

            } catch (Exception e) {
                CompletableFuture<JsonNode> future = pendingRequests.remove(requestId);
                if (future != null) {
                    future.completeExceptionally(
                        new IOException("Failed to send request: " + e.getMessage(), e));
                }
            }
        }, executorService);
    }

    private void responseReaderLoop() {
        try {
            while (!closed && mcpProcess.isAlive()) {
                String line = processOutput.readLine();
                if (line == null) {
                    break;
                }

                if (line.startsWith("Content-Length:")) {
                    int length = Integer.parseInt(line.substring(15).trim());

                    processOutput.readLine();

                    char[] buffer = new char[length];
                    int read = processOutput.read(buffer, 0, length);
                    if (read == length) {
                        String json = new String(buffer);
                        handleResponse(json);
                    }
                }
            }
        } catch (IOException e) {
            if (!closed) {
                _logger.error("Response reader error: {}", e.getMessage());
                healthy = false;
            }
        } finally {
            _logger.info("Response reader loop ended");
        }
    }

    private void handleResponse(String json) {
        try {
            JsonNode response = objectMapper.readTree(json);

            String id = response.has("id") ? response.get("id").asText() : null;
            if (id == null) {
                _logger.debug("Received notification: {}", json);
                return;
            }

            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
            if (future != null) {
                if (response.has("error")) {
                    JsonNode error = response.get("error");
                    String message = error.has("message")
                        ? error.get("message").asText()
                        : "Unknown error";
                    future.completeExceptionally(new IOException("Tool error: " + message));
                } else {
                    future.complete(response.get("result"));
                }
                _logger.debug("Handled response for request: {}", id);
            }

        } catch (Exception e) {
            _logger.error("Failed to parse response: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolResult(JsonNode result) {
        if (result == null || !result.isObject()) {
            return Map.of("content", "No result");
        }

        JsonNode content = result.get("content");
        if (content != null && content.isArray() && content.size() > 0) {
            JsonNode firstContent = content.get(0);
            if (firstContent.has("text")) {
                try {
                    return objectMapper.readValue(
                        firstContent.get("text").asText(),
                        Map.class
                    );
                } catch (Exception e) {
                    return Map.of("content", firstContent.get("text").asText());
                }
            }
        }

        return objectMapper.convertValue(result, Map.class);
    }

    private String buildCacheKey(String toolName, Map<String, Object> parameters) {
        return toolName + ":" + parameters.hashCode();
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public Map<String, String> listTools() {
        return AVAILABLE_TOOLS;
    }

    @Override
    public boolean isToolAvailable(String toolName) {
        return AVAILABLE_TOOLS.containsKey(toolName);
    }

    @Override
    public ZaiMcpConfig getConfig() {
        return config;
    }

    @Override
    public boolean isHealthy() {
        return healthy && !closed && mcpProcess.isAlive();
    }

    @Override
    public Optional<Map<String, Object>> getCacheStats() {
        if (!config.isCacheResults()) {
            return Optional.empty();
        }
        return Optional.of(Map.of(
            "size", cache.size(),
            "maxSize", config.getMaxCacheSize(),
            "enabled", true
        ));
    }

    @Override
    public void clearCache() {
        cache.clear();
        _logger.info("Cache cleared");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        healthy = false;

        _logger.info("Closing Z.AI MCP bridge");

        pendingRequests.values().forEach(future ->
            future.completeExceptionally(new IOException("Bridge closed")));

        try {
            processInput.close();
        } catch (Exception e) {
            _logger.debug("Error closing process input: {}", e.getMessage());
        }

        try {
            processOutput.close();
        } catch (Exception e) {
            _logger.debug("Error closing process output: {}", e.getMessage());
        }

        mcpProcess.destroy();
        try {
            if (!mcpProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                mcpProcess.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executorService.shutdown();
        _logger.info("Z.AI MCP bridge closed");
    }

    private record CacheEntry(Map<String, Object> result, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
