package org.yawlfoundation.yawl.integration.mcp.zai;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for Z.AI MCP Bridge.
 *
 * <p>Immutable configuration record with builder pattern for
 * constructing bridge settings.
 *
 * <p><b>Environment Variables:</b>
 * <ul>
 *   <li>{@code ZAI_MCP_ENABLED} - Enable bridge (default: true)</li>
 *   <li>{@code ZAI_MCP_MODE} - Transport mode: stdio, http (default: stdio)</li>
 *   <li>{@code ZAI_MCP_TIMEOUT_MS} - Timeout in milliseconds (default: 30000)</li>
 *   <li>{@code ZAI_MCP_CACHE_RESULTS} - Enable result caching (default: true)</li>
 *   <li>{@code ZAI_MCP_CACHE_TTL_MS} - Cache TTL in milliseconds (default: 3600000)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class ZaiMcpConfig {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(1);
    private static final int DEFAULT_MAX_CACHE_SIZE = 100;

    private final TransportMode mode;
    private final Duration timeout;
    private final boolean cacheResults;
    private final Duration cacheTtl;
    private final int maxCacheSize;
    private final String httpEndpoint;
    private final String apiKey;
    private final Map<String, String> extraHeaders;
    private final boolean enabled;

    /**
     * Transport mode for Z.AI MCP bridge.
     */
    public enum TransportMode {
        STDIO,
        HTTP
    }

    private ZaiMcpConfig(Builder builder) {
        this.mode = builder.mode;
        this.timeout = builder.timeout;
        this.cacheResults = builder.cacheResults;
        this.cacheTtl = builder.cacheTtl;
        this.maxCacheSize = builder.maxCacheSize;
        this.httpEndpoint = builder.httpEndpoint;
        this.apiKey = builder.apiKey;
        this.extraHeaders = builder.extraHeaders != null
            ? Map.copyOf(builder.extraHeaders)
            : Map.of();
        this.enabled = builder.enabled;
    }

    /**
     * Create configuration from environment variables.
     *
     * @return configuration from environment
     */
    public static ZaiMcpConfig fromEnvironment() {
        Builder builder = builder();

        String enabled = System.getenv("ZAI_MCP_ENABLED");
        if (enabled != null) {
            builder.enabled(Boolean.parseBoolean(enabled));
        }

        String mode = System.getenv("ZAI_MCP_MODE");
        if (mode != null) {
            builder.mode(TransportMode.valueOf(mode.toUpperCase()));
        }

        String timeoutMs = System.getenv("ZAI_MCP_TIMEOUT_MS");
        if (timeoutMs != null) {
            builder.timeout(Duration.ofMillis(Long.parseLong(timeoutMs)));
        }

        String cacheResults = System.getenv("ZAI_MCP_CACHE_RESULTS");
        if (cacheResults != null) {
            builder.cacheResults(Boolean.parseBoolean(cacheResults));
        }

        String cacheTtlMs = System.getenv("ZAI_MCP_CACHE_TTL_MS");
        if (cacheTtlMs != null) {
            builder.cacheTtl(Duration.ofMillis(Long.parseLong(cacheTtlMs)));
        }

        String endpoint = System.getenv("ZAI_MCP_HTTP_ENDPOINT");
        if (endpoint != null) {
            builder.httpEndpoint(endpoint);
        }

        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey != null) {
            builder.apiKey(apiKey);
        }

        return builder.build();
    }

    /**
     * Create a new builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a builder from this configuration.
     *
     * @return builder pre-populated with this config
     */
    public Builder toBuilder() {
        return new Builder()
            .mode(mode)
            .timeout(timeout)
            .cacheResults(cacheResults)
            .cacheTtl(cacheTtl)
            .maxCacheSize(maxCacheSize)
            .httpEndpoint(httpEndpoint)
            .apiKey(apiKey)
            .extraHeaders(extraHeaders)
            .enabled(enabled);
    }

    // Getters

    public TransportMode getMode() {
        return mode;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isCacheResults() {
        return cacheResults;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public int getMaxCacheSize() {
        return maxCacheSize;
    }

    public String getHttpEndpoint() {
        return httpEndpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Map<String, String> getExtraHeaders() {
        return extraHeaders;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "ZaiMcpConfig{" +
               "mode=" + mode +
               ", timeout=" + timeout +
               ", cacheResults=" + cacheResults +
               ", enabled=" + enabled +
               '}';
    }

    /**
     * Builder for ZaiMcpConfig.
     */
    public static final class Builder {
        private TransportMode mode = TransportMode.STDIO;
        private Duration timeout = DEFAULT_TIMEOUT;
        private boolean cacheResults = true;
        private Duration cacheTtl = DEFAULT_CACHE_TTL;
        private int maxCacheSize = DEFAULT_MAX_CACHE_SIZE;
        private String httpEndpoint;
        private String apiKey;
        private Map<String, String> extraHeaders;
        private boolean enabled = true;

        private Builder() {}

        public Builder mode(TransportMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode is required");
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout is required");
            return this;
        }

        public Builder cacheResults(boolean cacheResults) {
            this.cacheResults = cacheResults;
            return this;
        }

        public Builder cacheTtl(Duration cacheTtl) {
            this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl is required");
            return this;
        }

        public Builder maxCacheSize(int maxCacheSize) {
            if (maxCacheSize < 0) {
                throw new IllegalArgumentException("maxCacheSize must be >= 0");
            }
            this.maxCacheSize = maxCacheSize;
            return this;
        }

        public Builder httpEndpoint(String httpEndpoint) {
            this.httpEndpoint = httpEndpoint;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder extraHeaders(Map<String, String> extraHeaders) {
            this.extraHeaders = extraHeaders;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ZaiMcpConfig build() {
            Objects.requireNonNull(mode, "mode is required");
            Objects.requireNonNull(timeout, "timeout is required");
            return new ZaiMcpConfig(this);
        }
    }
}
