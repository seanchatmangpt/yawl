package org.yawlfoundation.yawl.integration.a2a;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * A2A Server Integration for YAWL
 *
 * Exposes YAWL workflow capabilities through the A2A protocol.
 * Implements a real HTTP server with:
 * - AgentCard endpoint at /.well-known/agent.json
 * - JSON-RPC 2.0 endpoint at /a2a
 * - Skill execution via YawlAgentExecutor
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see <a href="https://a2a-protocol.org">A2A Protocol Specification</a>
 */
public class YawlA2AServer {

    private static final String AGENT_CARD_PATH = "/.well-known/agent.json";
    private static final String A2A_ENDPOINT = "/a2a";
    private static final String HEALTH_PATH = "/health";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final int DEFAULT_PORT = 8082;

    private final int port;
    private final String serverUrl;
    private final YawlAgentExecutor executor;
    private HttpServer httpServer;
    private volatile boolean running = false;
    private ExecutorService executorService;

    /**
     * Create A2A server with environment configuration
     *
     * Uses environment variables:
     * - YAWL_ENGINE_URL: YAWL engine URL
     * - YAWL_USERNAME: YAWL username
     * - YAWL_PASSWORD: YAWL password
     * - A2A_SERVER_PORT: Server port (default 8082)
     */
    public YawlA2AServer() {
        this.port = getPortFromEnvironment();
        this.serverUrl = getServerUrlFromEnvironment();
        this.executor = YawlAgentExecutor.fromEnvironment(serverUrl);
        System.out.println("Initializing YAWL A2A Server on port " + port);
    }

    /**
     * Create A2A server with custom port
     *
     * @param port the port to listen on
     */
    public YawlA2AServer(int port) {
        this.port = port;
        this.serverUrl = "http://localhost:" + port;
        this.executor = YawlAgentExecutor.fromEnvironment(serverUrl);
        System.out.println("Initializing YAWL A2A Server on port " + port);
    }

    /**
     * Create A2A server with full configuration
     *
     * @param port the port to listen on
     * @param engineAdapter the YAWL engine adapter
     */
    public YawlA2AServer(int port, YawlEngineAdapter engineAdapter) {
        this.port = port;
        this.serverUrl = "http://localhost:" + port;
        this.executor = new YawlAgentExecutor(engineAdapter, serverUrl);
        System.out.println("Initializing YAWL A2A Server on port " + port);
    }

    /**
     * Start the A2A server
     *
     * @throws A2AException if server cannot be started
     */
    public void start() throws A2AException {
        if (running) {
            System.out.println("Server already running");
            return;
        }

        System.out.println("Starting YAWL A2A Server on port " + port + "...");

        try {
            // Connect to YAWL engine first
            executor.connect();
            System.out.println("Connected to YAWL engine");

            // Create HTTP server
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            executorService = Executors.newFixedThreadPool(10);
            httpServer.setExecutor(executorService);

            // Register handlers
            httpServer.createContext(AGENT_CARD_PATH, new AgentCardHandler());
            httpServer.createContext(A2A_ENDPOINT, new A2AEndpointHandler());
            httpServer.createContext(HEALTH_PATH, new HealthHandler());

            // Start server
            httpServer.start();
            running = true;

            System.out.println("YAWL A2A Server started successfully");
            System.out.println("  Agent Card: " + serverUrl + AGENT_CARD_PATH);
            System.out.println("  A2A Endpoint: " + serverUrl + A2A_ENDPOINT);
            System.out.println("  Health Check: " + serverUrl + HEALTH_PATH);

        } catch (java.net.BindException e) {
            throw new A2AException(
                A2AException.ErrorCode.SERVER_ERROR,
                "Port " + port + " is already in use",
                "Choose a different port or stop the process using port " + port,
                e
            );
        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.SERVER_ERROR,
                "Failed to start HTTP server: " + e.getMessage(),
                "Check network configuration and port availability",
                e
            );
        } catch (A2AException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to connect to YAWL engine: " + e.getMessage(),
                "Ensure YAWL engine is running and environment variables are set:\n" +
                "  YAWL_ENGINE_URL=http://localhost:8080/yawl\n" +
                "  YAWL_USERNAME=admin\n" +
                "  YAWL_PASSWORD=YAWL",
                e
            );
        }
    }

    /**
     * Stop the A2A server
     */
    public void stop() {
        if (!running) {
            System.out.println("Server not running");
            return;
        }

        System.out.println("Stopping YAWL A2A Server...");

        running = false;

        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }

        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }

        executor.disconnect();

        System.out.println("YAWL A2A Server stopped");
    }

    /**
     * Check if server is running
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the server port
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the server URL
     *
     * @return the server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * Get the agent card
     *
     * @return the agent card
     */
    public A2ATypes.AgentCard getAgentCard() {
        return executor.getAgentCard();
    }

    /**
     * Get the executor
     *
     * @return the agent executor
     */
    public YawlAgentExecutor getExecutor() {
        return executor;
    }

    // ==================== HTTP Handlers ====================

    /**
     * Handler for /.well-known/agent.json endpoint
     */
    private class AgentCardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method Not Allowed");
                    return;
                }

                A2ATypes.AgentCard card = executor.getAgentCard();
                String response = card.toJson();

                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
    }

    /**
     * Handler for /a2a JSON-RPC endpoint
     */
    private class A2AEndpointHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method Not Allowed");
                    return;
                }

                // Read request body
                String requestBody = readRequestBody(exchange);

                // Parse JSON-RPC request
                A2ATypes.A2ARequest request = parseRequest(requestBody);
                if (request == null) {
                    A2ATypes.A2AResponse errorResponse = new A2ATypes.A2AResponse(
                        A2ATypes.A2AError.parseError(), "unknown"
                    );
                    sendJsonResponse(exchange, 200, errorResponse.toJson());
                    return;
                }

                // Execute request
                A2ATypes.A2AResponse response = executor.execute(request);

                // Send response
                sendJsonResponse(exchange, 200, response.toJson());

            } catch (Exception e) {
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
    }

    /**
     * Handler for /health endpoint
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method Not Allowed");
                    return;
                }

                Map<String, Object> health = new HashMap<>();
                health.put("status", running ? "healthy" : "unhealthy");
                health.put("server", "YAWL A2A Server");
                health.put("version", "5.2");
                health.put("port", port);
                health.put("yawlConnected", executor.isConnected());

                String response = mapToJson(health);
                sendJsonResponse(exchange, 200, response);

            } catch (Exception e) {
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
    }

    // ==================== Helper Methods ====================

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE_JSON);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String response = "{\"error\":\"" + escapeJson(message) + "\"}";
        sendJsonResponse(exchange, statusCode, response);
    }

    private A2ATypes.A2ARequest parseRequest(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            String jsonrpc = extractJsonString(json, "jsonrpc");
            if (!"2.0".equals(jsonrpc)) {
                return null;
            }

            String method = extractJsonString(json, "method");
            String id = extractJsonString(json, "id");

            if (method == null) {
                return null;
            }

            Map<String, Object> params = parseParams(json);

            return new A2ATypes.A2ARequest(method, params, id);

        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseParams(String json) {
        Map<String, Object> params = new HashMap<>();

        int paramsStart = json.indexOf("\"params\":");
        if (paramsStart == -1) {
            return params;
        }

        int braceStart = json.indexOf("{", paramsStart);
        if (braceStart == -1) {
            return params;
        }

        int braceEnd = findMatchingBrace(json, braceStart);
        if (braceEnd == -1) {
            return params;
        }

        String paramsJson = json.substring(braceStart + 1, braceEnd);

        // Simple key-value extraction
        int pos = 0;
        while (pos < paramsJson.length()) {
            int keyStart = paramsJson.indexOf("\"", pos);
            if (keyStart == -1) break;

            int keyEnd = paramsJson.indexOf("\"", keyStart + 1);
            if (keyEnd == -1) break;

            String key = paramsJson.substring(keyStart + 1, keyEnd);

            int colonPos = paramsJson.indexOf(":", keyEnd);
            if (colonPos == -1) break;

            // Find value
            int valueStart = -1;
            for (int i = colonPos + 1; i < paramsJson.length(); i++) {
                char c = paramsJson.charAt(i);
                if (!Character.isWhitespace(c)) {
                    valueStart = i;
                    break;
                }
            }

            if (valueStart == -1) break;

            char firstChar = paramsJson.charAt(valueStart);
            String value;

            if (firstChar == '"') {
                int valueEnd = paramsJson.indexOf("\"", valueStart + 1);
                if (valueEnd == -1) break;
                value = paramsJson.substring(valueStart + 1, valueEnd);
                pos = valueEnd + 1;
            } else if (firstChar == '{') {
                int valueEnd = findMatchingBrace(paramsJson, valueStart);
                if (valueEnd == -1) break;
                value = paramsJson.substring(valueStart, valueEnd + 1);
                pos = valueEnd + 1;
            } else if (firstChar == '[') {
                int valueEnd = findMatchingBracket(paramsJson, valueStart);
                if (valueEnd == -1) break;
                value = paramsJson.substring(valueStart, valueEnd + 1);
                pos = valueEnd + 1;
            } else {
                // Number or boolean
                int valueEnd = valueStart;
                while (valueEnd < paramsJson.length()) {
                    char c = paramsJson.charAt(valueEnd);
                    if (c == ',' || c == '}' || Character.isWhitespace(c)) {
                        break;
                    }
                    valueEnd++;
                }
                value = paramsJson.substring(valueStart, valueEnd);
                pos = valueEnd;
            }

            params.put(key, unescapeJson(value));

            // Find next comma
            int commaPos = paramsJson.indexOf(",", pos);
            if (commaPos == -1) break;
            pos = commaPos + 1;
        }

        return params;
    }

    private int findMatchingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private int findMatchingBracket(String s, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start == -1) {
            return null;
        }
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end == -1) {
            return null;
        }
        return unescapeJson(json.substring(start, end));
    }

    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(value))).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static int getPortFromEnvironment() {
        String portStr = System.getenv("A2A_SERVER_PORT");
        if (portStr != null && !portStr.isEmpty()) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return DEFAULT_PORT;
    }

    private static String getServerUrlFromEnvironment() {
        String url = System.getenv("A2A_SERVER_URL");
        if (url != null && !url.isEmpty()) {
            return url;
        }
        return "http://localhost:" + getPortFromEnvironment();
    }

    /**
     * Main method for standalone server
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }

        System.out.println("YAWL A2A Server");
        System.out.println("===============");
        System.out.println();

        final YawlA2AServer server = new YawlA2AServer(port);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down YAWL A2A Server...");
            server.stop();
        }));

        try {
            server.start();

            System.out.println("\nYAWL A2A Server is ready to accept agent requests");
            System.out.println("Press Ctrl+C to stop");

            // Keep server running
            Thread.currentThread().join();

        } catch (A2AException e) {
            System.err.println("Failed to start server: " + e.getFullReport());
            System.exit(1);
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
