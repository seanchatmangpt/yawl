package org.yawlfoundation.yawl.integration.autonomous.observability;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Health check system for YAWL autonomous integration.
 * Provides HTTP endpoint for readiness and liveness probes.
 *
 * Checks:
 * - YAWL engine connectivity
 * - ZAI API connectivity (if configured)
 * - Custom registered health checks
 *
 * Thread-safe for concurrent health check execution.
 *
 * @author YAWL Production Validator
 * @version 5.2
 */
public class HealthCheck {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheck.class);

    private final String yawlEngineUrl;
    private final String zaiApiUrl;
    private final int timeoutMs;
    private final HttpServer server;
    private final ConcurrentHashMap<String, Check> customChecks;

    /**
     * Create health check without HTTP server.
     *
     * @param yawlEngineUrl YAWL engine base URL (e.g., "http://localhost:8080/yawl")
     */
    public HealthCheck(String yawlEngineUrl) throws IOException {
        this(yawlEngineUrl, null, 5000, 0);
    }

    /**
     * Create health check with HTTP endpoint.
     *
     * @param yawlEngineUrl YAWL engine base URL
     * @param zaiApiUrl ZAI API base URL (null if not using ZAI)
     * @param timeoutMs Connection timeout in milliseconds
     * @param port HTTP server port (0 to disable HTTP server)
     * @throws IOException If HTTP server cannot be started
     */
    public HealthCheck(String yawlEngineUrl, String zaiApiUrl, int timeoutMs, int port) throws IOException {
        if (yawlEngineUrl == null || yawlEngineUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("yawlEngineUrl cannot be null or empty");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be > 0, got: " + timeoutMs);
        }

        this.yawlEngineUrl = yawlEngineUrl;
        this.zaiApiUrl = zaiApiUrl;
        this.timeoutMs = timeoutMs;
        this.customChecks = new ConcurrentHashMap<>();

        if (port > 0) {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/health", new HealthHandler());
            server.createContext("/health/ready", new ReadinessHandler());
            server.createContext("/health/live", new LivenessHandler());
            server.setExecutor(null);
            server.start();

            logger.info("Health check HTTP server started on port {}", port);
        } else {
            this.server = null;
        }
    }

    /**
     * Register custom health check.
     *
     * @param name Check name
     * @param check Check implementation
     */
    public void registerCheck(String name, Check check) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        if (check == null) {
            throw new IllegalArgumentException("check cannot be null");
        }

        customChecks.put(name, check);
        logger.info("Registered custom health check: {}", name);
    }

    /**
     * Unregister custom health check.
     *
     * @param name Check name
     */
    public void unregisterCheck(String name) {
        if (customChecks.remove(name) != null) {
            logger.info("Unregistered custom health check: {}", name);
        }
    }

    /**
     * Execute all health checks.
     *
     * @return Health check result
     */
    public HealthResult checkHealth() {
        Map<String, CheckResult> checks = new HashMap<>();

        CheckResult yawlCheck = checkYawlEngine();
        checks.put("yawl_engine", yawlCheck);

        if (zaiApiUrl != null) {
            CheckResult zaiCheck = checkZaiApi();
            checks.put("zai_api", zaiCheck);
        }

        customChecks.forEach((name, check) -> {
            try {
                CheckResult result = check.execute();
                checks.put(name, result);
            } catch (Exception e) {
                logger.error("Custom health check [{}] threw exception: {}", name, e.getMessage());
                checks.put(name, CheckResult.unhealthy("Exception: " + e.getMessage()));
            }
        });

        boolean allHealthy = checks.values().stream().allMatch(CheckResult::isHealthy);
        String status = allHealthy ? "healthy" : "unhealthy";

        return new HealthResult(status, checks);
    }

    /**
     * Check YAWL engine connectivity.
     * Sends HEAD request to YAWL engine base URL.
     *
     * @return Check result
     */
    private CheckResult checkYawlEngine() {
        try {
            URL url = new URL(yawlEngineUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode >= 200 && responseCode < 500) {
                return CheckResult.healthy("YAWL engine responding (HTTP " + responseCode + ")");
            } else {
                return CheckResult.unhealthy("YAWL engine returned HTTP " + responseCode);
            }

        } catch (IOException e) {
            logger.warn("YAWL engine health check failed: {}", e.getMessage());
            return CheckResult.unhealthy("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Check ZAI API connectivity.
     * Sends HEAD request to ZAI API base URL.
     *
     * @return Check result
     */
    private CheckResult checkZaiApi() {
        if (zaiApiUrl == null) {
            return CheckResult.healthy("ZAI API not configured");
        }

        try {
            URL url = new URL(zaiApiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode >= 200 && responseCode < 500) {
                return CheckResult.healthy("ZAI API responding (HTTP " + responseCode + ")");
            } else {
                return CheckResult.unhealthy("ZAI API returned HTTP " + responseCode);
            }

        } catch (IOException e) {
            logger.warn("ZAI API health check failed: {}", e.getMessage());
            return CheckResult.unhealthy("Connection failed: " + e.getMessage());
        }
    }

    /**
     * Shutdown HTTP server if running.
     */
    public void shutdown() {
        if (server != null) {
            server.stop(0);
            logger.info("Health check HTTP server stopped");
        }
    }

    /**
     * Format health result as JSON.
     *
     * @param result Health result
     * @return JSON string
     */
    private String formatJson(HealthResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"status\": \"").append(result.status).append("\",\n");
        json.append("  \"checks\": {\n");

        List<String> checkEntries = new ArrayList<>();
        result.checks.forEach((name, check) -> {
            StringBuilder entry = new StringBuilder();
            entry.append("    \"").append(name).append("\": {\n");
            entry.append("      \"healthy\": ").append(check.healthy).append(",\n");
            entry.append("      \"message\": \"").append(escapeJson(check.message)).append("\"\n");
            entry.append("    }");
            checkEntries.add(entry.toString());
        });

        json.append(String.join(",\n", checkEntries));
        json.append("\n  }\n");
        json.append("}");

        return json.toString();
    }

    /**
     * Escape special characters for JSON.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "null";
        }

        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * HTTP handler for /health endpoint.
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            HealthResult result = checkHealth();
            String response = formatJson(result);
            byte[] bytes = response.getBytes("UTF-8");

            int statusCode = "healthy".equals(result.status) ? 200 : 503;

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * HTTP handler for /health/ready endpoint (readiness probe).
     */
    private class ReadinessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            HealthResult result = checkHealth();
            int statusCode = "healthy".equals(result.status) ? 200 : 503;

            String response = "{\"ready\": " + ("healthy".equals(result.status)) + "}";
            byte[] bytes = response.getBytes("UTF-8");

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * HTTP handler for /health/live endpoint (liveness probe).
     */
    private class LivenessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String response = "{\"alive\": true}";
            byte[] bytes = response.getBytes("UTF-8");

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Interface for custom health checks.
     */
    public interface Check {
        CheckResult execute();
    }

    /**
     * Result of individual health check.
     */
    public static class CheckResult {
        private final boolean healthy;
        private final String message;

        private CheckResult(boolean healthy, String message) {
            this.healthy = healthy;
            this.message = message;
        }

        public static CheckResult healthy(String message) {
            return new CheckResult(true, message);
        }

        public static CheckResult unhealthy(String message) {
            return new CheckResult(false, message);
        }

        public boolean isHealthy() {
            return healthy;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Overall health check result.
     */
    public static class HealthResult {
        private final String status;
        private final Map<String, CheckResult> checks;

        public HealthResult(String status, Map<String, CheckResult> checks) {
            this.status = status;
            this.checks = checks;
        }

        public String getStatus() {
            return status;
        }

        public Map<String, CheckResult> getChecks() {
            return new HashMap<>(checks);
        }

        public boolean isHealthy() {
            return "healthy".equals(status);
        }
    }
}
