/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.mcp;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.DumperOptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration loader for Slack MCP Server.
 *
 * Provides type-safe access to configuration values loaded from YAML file.
 */
public class SlackMcpConfig {

    private final ConfigProperties properties;
    private static final SlackMcpConfig INSTANCE = new SlackMcpConfig();

    /**
     * Private constructor to enforce singleton pattern.
     */
    private SlackMcpConfig() {
        this.properties = loadConfiguration();
    }

    /**
     * Gets the singleton instance.
     */
    public static SlackMcpConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Loads configuration from YAML file.
     */
    private ConfigProperties loadConfiguration() {
        try (InputStream input = new FileInputStream("src/main/resources/slack-mcp-server.yml")) {
            Yaml yaml = new Yaml();
            return yaml.loadAs(input, ConfigProperties.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    /**
     * Gets server configuration.
     */
    public ServerConfig getServer() {
        return properties.getServer();
    }

    /**
     * Gets Slack configuration.
     */
    public SlackConfig getSlack() {
        return properties.getSlack();
    }

    /**
     * Gets notification configuration.
     */
    public NotificationConfig getNotifications() {
        return properties.getNotifications();
    }

    /**
     * Gets workflow subscription configuration.
     */
    public SubscriptionConfig getSubscriptions() {
        return properties.getSubscriptions();
    }

    /**
     * Gets performance configuration.
     */
    public PerformanceConfig getPerformance() {
        return properties.getPerformance();
    }

    /**
     * Gets security configuration.
     */
    public SecurityConfig getSecurity() {
        return properties.getSecurity();
    }

    /**
     * Gets logging configuration.
     */
    public LoggingConfig getLogging() {
        return properties.getLogging();
    }

    /**
     * Gets metrics configuration.
     */
    public MetricsConfig getMetrics() {
        return properties.getMetrics();
    }

    /**
     * Gets health check configuration.
     */
    public HealthConfig getHealth() {
        return properties.getHealth();
    }

    /**
     * Root configuration properties.
     */
    public static class ConfigProperties {
        private ServerConfig server;
        private SlackConfig slack;
        private NotificationConfig notifications;
        private SubscriptionConfig subscriptions;
        private PerformanceConfig performance;
        private SecurityConfig security;
        private LoggingConfig logging;
        private MetricsConfig metrics;
        private HealthConfig health;

        // Getters and setters
        public ServerConfig getServer() { return server; }
        public void setServer(ServerConfig server) { this.server = server; }
        public SlackConfig getSlack() { return slack; }
        public void setSlack(SlackConfig slack) { this.slack = slack; }
        public NotificationConfig getNotifications() { return notifications; }
        public void setNotifications(NotificationConfig notifications) { this.notifications = notifications; }
        public SubscriptionConfig getSubscriptions() { return subscriptions; }
        public void setSubscriptions(SubscriptionConfig subscriptions) { this.subscriptions = subscriptions; }
        public PerformanceConfig getPerformance() { return performance; }
        public void setPerformance(PerformanceConfig performance) { this.performance = performance; }
        public SecurityConfig getSecurity() { return security; }
        public void setSecurity(SecurityConfig security) { this.security = security; }
        public LoggingConfig getLogging() { return logging; }
        public void setLogging(LoggingConfig logging) { this.logging = logging; }
        public MetricsConfig getMetrics() { return metrics; }
        public void setMetrics(MetricsConfig metrics) { this.metrics = metrics; }
        public HealthConfig getHealth() { return health; }
        public void setHealth(HealthConfig health) { this.health = health; }
    }

    /**
     * Server configuration.
     */
    public static class ServerConfig {
        private int port = 8085;
        private String name = "yawl-slack";

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * Slack configuration.
     */
    public static class SlackConfig {
        private String botToken;
        private String signingSecret;
        private List<SlackChannel> defaultChannels;

        public String getBotToken() { return botToken; }
        public void setBotToken(String botToken) { this.botToken = botToken; }
        public String getSigningSecret() { return signingSecret; }
        public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }
        public List<SlackChannel> getDefaultChannels() { return defaultChannels; }
        public void setDefaultChannels(List<SlackChannel> defaultChannels) { this.defaultChannels = defaultChannels; }
    }

    /**
     * Slack channel configuration.
     */
    public static class SlackChannel {
        private String name;
        private String purpose;
        private String id;
        private boolean notificationsEnabled = true;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public boolean isNotificationsEnabled() { return notificationsEnabled; }
        public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    }

    /**
     * Notification configuration.
     */
    public static class NotificationConfig {
        private boolean enabled = true;
        private List<String> eventTypes;
        private Map<String, String> templates;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getEventTypes() { return eventTypes; }
        public void setEventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; }
        public Map<String, String> getTemplates() { return templates; }
        public void setTemplates(Map<String, String> templates) { this.templates = templates; }
    }

    /**
     * Subscription configuration.
     */
    public static class SubscriptionConfig {
        private boolean enabled = true;
        private List<String> defaultChannels;
        private int maxSubscriptionsPerChannel = 100;
        private long defaultDuration = 86400000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getDefaultChannels() { return defaultChannels; }
        public void setDefaultChannels(List<String> defaultChannels) { this.defaultChannels = defaultChannels; }
        public int getMaxSubscriptionsPerChannel() { return maxSubscriptionsPerChannel; }
        public void setMaxSubscriptionsPerChannel(int maxSubscriptionsPerChannel) { this.maxSubscriptionsPerChannel = maxSubscriptionsPerChannel; }
        public long getDefaultDuration() { return defaultDuration; }
        public void setDefaultDuration(long defaultDuration) { this.defaultDuration = defaultDuration; }
    }

    /**
     * Performance configuration.
     */
    public static class PerformanceConfig {
        private boolean virtualThreads = true;
        private int queueSize = 1000;
        private int batchSize = 10;
        private int apiTimeout = 30;
        private RetryConfig retry;

        public boolean isVirtualThreads() { return virtualThreads; }
        public void setVirtualThreads(boolean virtualThreads) { this.virtualThreads = virtualThreads; }
        public int getQueueSize() { return queueSize; }
        public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getApiTimeout() { return apiTimeout; }
        public void setApiTimeout(int apiTimeout) { this.apiTimeout = apiTimeout; }
        public RetryConfig getRetry() { return retry; }
        public void setRetry(RetryConfig retry) { this.retry = retry; }
    }

    /**
     * Retry configuration.
     */
    public static class RetryConfig {
        private int maxAttempts = 3;
        private int delayMs = 1000;
        private boolean exponentialBackoff = true;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public int getDelayMs() { return delayMs; }
        public void setDelayMs(int delayMs) { this.delayMs = delayMs; }
        public boolean isExponentialBackoff() { return exponentialBackoff; }
        public void setExponentialBackoff(boolean exponentialBackoff) { this.exponentialBackoff = exponentialBackoff; }
    }

    /**
     * Security configuration.
     */
    public static class SecurityConfig {
        private boolean validateRequests = true;
        private int maxRequestSize = 1048576;
        private List<String> allowedOrigins;
        private RateLimitConfig rateLimit;

        public boolean isValidateRequests() { return validateRequests; }
        public void setValidateRequests(boolean validateRequests) { this.validateRequests = validateRequests; }
        public int getMaxRequestSize() { return maxRequestSize; }
        public void setMaxRequestSize(int maxRequestSize) { this.maxRequestSize = maxRequestSize; }
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        public RateLimitConfig getRateLimit() { return rateLimit; }
        public void setRateLimit(RateLimitConfig rateLimit) { this.rateLimit = rateLimit; }
    }

    /**
     * Rate limiting configuration.
     */
    public static class RateLimitConfig {
        private boolean enabled = true;
        private int requestsPerMinute = 60;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    }

    /**
     * Logging configuration.
     */
    public static class LoggingConfig {
        private String level = "INFO";
        private Map<String, String> loggers;
        private String format = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n";
        private FileConfig file;

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public Map<String, String> getLoggers() { return loggers; }
        public void setLoggers(Map<String, String> loggers) { this.loggers = loggers; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public FileConfig getFile() { return file; }
        public void setFile(FileConfig file) { this.file = file; }
    }

    /**
     * File logging configuration.
     */
    public static class FileConfig {
        private boolean enabled = true;
        private String file = "logs/slack-mcp-server.log";
        private String maxSize = "10MB";
        private int maxFiles = 5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public String getMaxSize() { return maxSize; }
        public void setMaxSize(String maxSize) { this.maxSize = maxSize; }
        public int getMaxFiles() { return maxFiles; }
        public void setMaxFiles(int maxFiles) { this.maxFiles = maxFiles; }
    }

    /**
     * Metrics configuration.
     */
    public static class MetricsConfig {
        private boolean enabled = true;
        private ExporterConfig exporter;
        private List<String> metrics;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public ExporterConfig getExporter() { return exporter; }
        public void setExporter(ExporterConfig exporter) { this.exporter = exporter; }
        public List<String> getMetrics() { return metrics; }
        public void setMetrics(List<String> metrics) { this.metrics = metrics; }
    }

    /**
     * Metrics exporter configuration.
     */
    public static class ExporterConfig {
        private String type = "logging";
        private String interval = "30s";

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getInterval() { return interval; }
        public void setInterval(String interval) { this.interval = interval; }
    }

    /**
     * Health check configuration.
     */
    public static class HealthConfig {
        private boolean enabled = true;
        private List<String> endpoints;
        private int checkInterval = 30;
        private ThresholdConfig thresholds;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<String> getEndpoints() { return endpoints; }
        public void setEndpoints(List<String> endpoints) { this.endpoints = endpoints; }
        public int getCheckInterval() { return checkInterval; }
        public void setCheckInterval(int checkInterval) { this.checkInterval = checkInterval; }
        public ThresholdConfig getThresholds() { return thresholds; }
        public void setThresholds(ThresholdConfig thresholds) { this.thresholds = thresholds; }
    }

    /**
     * Health check thresholds configuration.
     */
    public static class ThresholdConfig {
        private int memoryUsagePercent = 80;
        private int queueSize = 500;
        private int subscriptionCount = 1000;

        public int getMemoryUsagePercent() { return memoryUsagePercent; }
        public void setMemoryUsagePercent(int memoryUsagePercent) { this.memoryUsagePercent = memoryUsagePercent; }
        public int getQueueSize() { return queueSize; }
        public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
        public int getSubscriptionCount() { return subscriptionCount; }
        public void setSubscriptionCount(int subscriptionCount) { this.subscriptionCount = subscriptionCount; }
    }
}