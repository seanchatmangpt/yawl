package org.yawlfoundation.yawl.mcp.a2a.github.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Configuration properties for GitHub integration.
 *
 * <p>This class provides type-safe access to GitHub configuration properties
 * defined in application.yml. It includes validation constraints to ensure
 * required properties are properly configured.</p>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * yawl:
 *   github:
 *     enabled: true
 *     base-url: https://api.github.com
 *     personal-access-token: ${GITHUB_TOKEN:}
 *     default-repository: yawlfoundation/yawl-engine
 *     webhooks:
 *       enabled: true
 *       secret: ${GITHUB_WEBHOOK_SECRET:}
 *       max-payload-size: 1048576
 *     rate-limit:
 *       requests-per-hour: 5000
 *       retry-attempts: 3
 *       retry-delay-ms: 1000
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Validated
@ConfigurationProperties(prefix = "yawl.github")
public class GitHubConfig {

    /**
     * Whether GitHub integration is enabled.
     */
    private boolean enabled = true;

    /**
     * GitHub API base URL.
     * Default: https://api.github.com
     */
    @NotBlank
    @Pattern(regexp = "^https://api\\.github\\.com|https://[^/]+/api/v3$")
    private String baseUrl = "https://api.github.com";

    /**
     * GitHub personal access token for authentication.
     */
    @NotBlank
    private String personalAccessToken;

    /**
     * Default repository in format "owner/repo".
     */
    @NotBlank
    @Pattern(regexp = "^[^/]+/[^/]+$")
    private String defaultRepository;

    /**
     * Webhook configuration.
     */
    private WebhookConfig webhook = new WebhookConfig();

    /**
     * Rate limiting configuration.
     */
    private RateLimitConfig rateLimit = new RateLimitConfig();

    /**
     * Request timeout configuration.
     */
    private RequestConfig request = new RequestConfig();

    /**
     * Whether to enable debug logging for GitHub API calls.
     */
    private boolean debugLogging = false;

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPersonalAccessToken() {
        return personalAccessToken;
    }

    public void setPersonalAccessToken(String personalAccessToken) {
        this.personalAccessToken = personalAccessToken;
    }

    public String getDefaultRepository() {
        return defaultRepository;
    }

    public void setDefaultRepository(String defaultRepository) {
        this.defaultRepository = defaultRepository;
    }

    public WebhookConfig getWebhook() {
        return webhook;
    }

    public void setWebhook(WebhookConfig webhook) {
        this.webhook = webhook;
    }

    public RateLimitConfig getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimitConfig rateLimit) {
        this.rateLimit = rateLimit;
    }

    public RequestConfig getRequest() {
        return request;
    }

    public void setRequest(RequestConfig request) {
        this.request = request;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    /**
     * Builder for creating GitHubConfig instances.
     */
    public static class Builder {
        private final GitHubConfig config;

        public Builder() {
            config = new GitHubConfig();
        }

        public Builder baseUrl(String baseUrl) {
            config.baseUrl = baseUrl;
            return this;
        }

        public Builder personalAccessToken(String token) {
            config.personalAccessToken = token;
            return this;
        }

        public Builder defaultRepository(String repository) {
            config.defaultRepository = repository;
            return this;
        }

        public Builder enabled(boolean enabled) {
            config.enabled = enabled;
            return this;
        }

        public Builder webhook(WebhookConfig webhook) {
            config.webhook = webhook;
            return this;
        }

        public Builder rateLimit(RateLimitConfig rateLimit) {
            config.rateLimit = rateLimit;
            return this;
        }

        public Builder request(RequestConfig request) {
            config.request = request;
            return this;
        }

        public Builder debugLogging(boolean debugLogging) {
            config.debugLogging = debugLogging;
            return this;
        }

        public GitHubConfig build() {
            return config;
        }
    }

    /**
     * Create a new builder for GitHubConfig.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Webhook configuration.
     */
    public static class WebhookConfig {
        /**
         * Whether webhooks are enabled.
         */
        private boolean enabled = true;

        /**
         * Webhook secret for signature verification.
         */
        private String secret;

        /**
         * Maximum payload size in bytes.
         */
        private int maxPayloadSize = 1024 * 1024; // 1MB

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getMaxPayloadSize() {
            return maxPayloadSize;
        }

        public void setMaxPayloadSize(int maxPayloadSize) {
            this.maxPayloadSize = maxPayloadSize;
        }
    }

    /**
     * Rate limiting configuration.
     */
    public static class RateLimitConfig {
        /**
         * Maximum requests per hour.
         */
        private int requestsPerHour = 5000;

        /**
         * Number of retry attempts for rate-limited requests.
         */
        private int retryAttempts = 3;

        /**
         * Delay between retries in milliseconds.
         */
        private int retryDelayMs = 1000;

        public int getRequestsPerHour() {
            return requestsPerHour;
        }

        public void setRequestsPerHour(int requestsPerHour) {
            this.requestsPerHour = requestsPerHour;
        }

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public int getRetryDelayMs() {
            return retryDelayMs;
        }

        public void setRetryDelayMs(int retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
        }
    }

    /**
     * Request configuration.
     */
    public static class RequestConfig {
        /**
         * Connection timeout in milliseconds.
         */
        private int connectionTimeoutMs = 5000;

        /**
         * Read timeout in milliseconds.
         */
        private int readTimeoutMs = 30000;

        /**
         * Maximum number of connections.
         */
        private int maxConnections = 100;

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }
    }
}