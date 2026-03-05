package org.yawlfoundation.yawl.integration.mcp.spring;

/**
 * Configuration properties for YAWL MCP Spring integration.
 *
 * <p>Binds to {@code yawl.mcp.*} properties in application.yml or application.properties.
 * Provides type-safe configuration for YAWL engine connection and MCP server settings.</p>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * # application.yml
 * yawl:
 *   mcp:
 *     enabled: true
 *     engine-url: http://localhost:8080/yawl
 *     username: ${YAWL_USERNAME:admin}
 *     password: ${YAWL_PASSWORD}  # required - no default; see SECURITY.md
 *     transport: stdio
 *     http:
 *       enabled: false
 *       port: 8081
 *       path: /mcp
 *     zai:
 *       enabled: true
 *       api-key: ${ZAI_API_KEY}
 *     connection:
 *       retry-attempts: 3
 *       retry-delay-ms: 1000
 *       timeout-ms: 5000
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpProperties {

    /**
     * Enable or disable YAWL MCP integration.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * YAWL engine base URL (required).
     * Example: http://localhost:8080/yawl
     */
    private String engineUrl;

    /**
     * YAWL admin username (required).
     * Default: admin
     * Environment variable: YAWL_USERNAME
     */
    private String username = "admin";

    /**
     * YAWL admin password (required).
     * SECURITY: Must be set via environment variable YAWL_PASSWORD.
     * No default value provided for security reasons.
     * Environment variable: YAWL_PASSWORD
     */
    private String password;

    /**
     * MCP transport type: stdio or http.
     * Default: stdio
     */
    private Transport transport = Transport.STDIO;

    /**
     * HTTP transport configuration (only used if transport=http).
     */
    private HttpConfig http = new HttpConfig();

    /**
     * Z.AI natural language integration configuration.
     */
    private ZaiConfig zai = new ZaiConfig();

    /**
     * Groq Cloud LLM integration configuration.
     */
    private GroqConfig groq = new GroqConfig();

    /**
     * Connection retry and timeout configuration.
     */
    private ConnectionConfig connection = new ConnectionConfig();

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEngineUrl() {
        return engineUrl;
    }

    public void setEngineUrl(String engineUrl) {
        this.engineUrl = engineUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public HttpConfig getHttp() {
        return http;
    }

    public void setHttp(HttpConfig http) {
        this.http = http;
    }

    public ZaiConfig getZai() {
        return zai;
    }

    public void setZai(ZaiConfig zai) {
        this.zai = zai;
    }

    public GroqConfig getGroq() {
        return groq;
    }

    public void setGroq(GroqConfig groq) {
        this.groq = groq;
    }

    public ConnectionConfig getConnection() {
        return connection;
    }

    public void setConnection(ConnectionConfig connection) {
        this.connection = connection;
    }

    /**
     * MCP transport type enumeration.
     */
    public enum Transport {
        /** Standard I/O transport (default) */
        STDIO,
        /** HTTP/REST transport */
        HTTP
    }

    /**
     * HTTP transport configuration.
     */
    public static class HttpConfig {
        /**
         * Enable HTTP transport (in addition to or instead of STDIO).
         * Default: false
         */
        private boolean enabled = false;

        /**
         * HTTP server port.
         * Default: 8081
         */
        private int port = 8081;

        /**
         * HTTP server path for MCP endpoint.
         * Default: /mcp
         */
        private String path = "/mcp";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    /**
     * Z.AI natural language integration configuration.
     */
    public static class ZaiConfig {
        /**
         * Enable Z.AI natural language tool.
         * Default: true if api-key is provided
         */
        private boolean enabled = true;

        /**
         * Z.AI API key (from environment variable ZAI_API_KEY or ZHIPU_API_KEY).
         * Required for natural language tool.
         */
        private String apiKey;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    /**
     * Groq Cloud LLM integration configuration.
     *
     * <p>Example application.yml:
     * <pre>{@code
     * yawl:
     *   mcp:
     *     groq:
     *       enabled: true
     *       api-key: ${GROQ_API_KEY}
     *       model: llama-3.3-70b-versatile
     *       max-concurrency: 30
     * }</pre>
     *
     * <p>When both {@code groq} and {@code zai} are enabled, Groq takes precedence.
     * Free-tier rate limit: 30 RPM per model.
     */
    public static class GroqConfig {
        /**
         * Enable Groq LLM integration.
         * Default: true if api-key is provided.
         */
        private boolean enabled = true;

        /**
         * Groq API key (from environment variable GROQ_API_KEY).
         * Required for Groq integration.
         */
        private String apiKey;

        /**
         * Groq model identifier.
         * Default: llama-3.3-70b-versatile
         */
        private String model = "llama-3.3-70b-versatile";

        /**
         * Maximum concurrent in-flight requests to Groq.
         * Default: 30 (matches free-tier RPM). Increase for paid plans.
         */
        private int maxConcurrency = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }
    }

    /**
     * Connection retry and timeout configuration.
     */
    public static class ConnectionConfig {
        /**
         * Number of retry attempts for YAWL engine connection.
         * Default: 3
         */
        private int retryAttempts = 3;

        /**
         * Delay between retry attempts in milliseconds.
         * Default: 1000ms (1 second)
         */
        private long retryDelayMs = 1000;

        /**
         * Connection timeout in milliseconds.
         * Default: 5000ms (5 seconds)
         */
        private long timeoutMs = 5000;

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public long getRetryDelayMs() {
            return retryDelayMs;
        }

        public void setRetryDelayMs(long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
