package org.yawlfoundation.yawl.integration.actor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Configuration Management for Actor Validation
 *
 * Provides centralized configuration management for the actor validation system
 * with support for environment variables, property files, and dynamic updates.
 *
 * @since 6.0.0
 */
public class ActorValidationConfig {

    private static final String DEFAULT_CONFIG_FILE = "config/actor-validation.properties";
    private final Properties properties;

    public ActorValidationConfig() {
        this.properties = new Properties();
        loadDefaultConfig();
        loadEnvironmentOverrides();
        loadConfigFile();
    }

    public ActorValidationConfig(String configFilePath) {
        this.properties = new Properties();
        loadDefaultConfig();
        loadEnvironmentOverrides();
        loadConfigFile(configFilePath);
    }

    /**
     * Load default configuration
     */
    private void loadDefaultConfig() {
        // Set default values
        setProperty("actor.validation.enabled", "true");
        setProperty("actor.validation.interval.seconds", "30");
        setProperty("actor.validation.parallel.threads", "512");
        setProperty("actor.validation.memory.threshold.mb", "100");
        setProperty("actor.validation.memory.check.interval.seconds", "60");
        setProperty("actor.validation.memory.alert.threshold.percent", "80");
        setProperty("actor.validation.deadlock.enabled", "true");
        setProperty("actor.validation.deadlock.timeout.seconds", "30");
        setProperty("actor.validation.deadlock.thread.count.threshold", "5");
        setProperty("actor.validation.performance.slow.threshold.ms", "5000");
        setProperty("actor.validation.performance.alert.threshold.percent", "90");
        setProperty("actor.validation.performance.history.size", "1000");
        setProperty("actor.validation.mcp.enabled", "true");
        setProperty("actor.validation.mcp.port", "8080");
        setProperty("actor.validation.mcp.host", "localhost");
        setProperty("actor.validation.mcp.ssl.enabled", "false");
        setProperty("actor.validation.observability.enabled", "true");
        setProperty("actor.validation.observability.metrics.port", "9090");
        setProperty("actor.validation.observability.tracing.enabled", "true");
        setProperty("actor.validation.log.level", "INFO");
        setProperty("actor.validation.log.file", "logs/actor-validation.log");
        setProperty("actor.validation.reports.enabled", "true");
        setProperty("actor.validation.dev.mode", "false");
    }

    /**
     * Load environment variable overrides
     */
    private void loadEnvironmentOverrides() {
        // Map environment variables to properties
        String[][] envMappings = {
            {"ACTOR_VALIDATION_ENABLED", "actor.validation.enabled"},
            {"ACTOR_VALIDATION_INTERVAL_SECONDS", "actor.validation.interval.seconds"},
            {"ACTOR_VALIDATION_PARALLEL_THREADS", "actor.validation.parallel.threads"},
            {"ACTOR_MEMORY_THRESHOLD_MB", "actor.validation.memory.threshold.mb"},
            {"ACTOR_DEADLOCK_ENABLED", "actor.validation.deadlock.enabled"},
            {"ACTOR_MCP_PORT", "actor.validation.mcp.port"},
            {"ACTOR_MCP_HOST", "actor.validation.mcp.host"},
            {"ACTOR_MCP_SSL_ENABLED", "actor.validation.mcp.ssl.enabled"},
            {"ACTOR_OBSERVABILITY_ENABLED", "actor.validation.observability.enabled"},
            {"ACTOR_OBSERVABILITY_METRICS_PORT", "actor.validation.observability.metrics.port"},
            {"ACTOR_LOG_LEVEL", "actor.validation.log.level"},
            {"ACTOR_REPORTS_ENABLED", "actor.validation.reports.enabled"}
        };

        for (String[] mapping : envMappings) {
            String envValue = System.getenv(mapping[0]);
            if (envValue != null) {
                setProperty(mapping[1], envValue);
            }
        }
    }

    /**
     * Load configuration from file
     */
    private void loadConfigFile() {
        loadConfigFile(DEFAULT_CONFIG_FILE);
    }

    /**
     * Load configuration from file
     */
    private void loadConfigFile(String configFilePath) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFilePath)) {
            if (input != null) {
                properties.load(input);
            } else {
                // Try to load from file system
                java.io.File configFile = new java.io.File(configFilePath);
                if (configFile.exists()) {
                    try (InputStream fileInput = new java.io.FileInputStream(configFile)) {
                        properties.load(fileInput);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load configuration file: " + configFilePath);
        }
    }

    /**
     * Set property value
     */
    private void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * Get boolean property
     */
    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(properties.getProperty(key));
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    /**
     * Get integer property
     */
    public int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    /**
     * Get long property
     */
    public long getLong(String key) {
        return Long.parseLong(properties.getProperty(key));
    }

    public long getLong(String key, long defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Long.parseLong(value) : defaultValue;
    }

    /**
     * Get double property
     */
    public double getDouble(String key) {
        return Double.parseDouble(properties.getProperty(key));
    }

    public double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Double.parseDouble(value) : defaultValue;
    }

    /**
     * Get string property
     */
    public String getString(String key) {
        return properties.getProperty(key);
    }

    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Get time duration property
     */
    public Duration getDuration(String key) {
        return getDuration(key, Duration.ZERO);
    }

    public Duration getDuration(String key, Duration defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;

        try {
            // Parse format: "30s", "5m", "1h"
            if (value.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
            } else if (value.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
            } else if (value.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(value.substring(0, value.length() - 1)));
            } else {
                // Assume seconds
                return Duration.ofSeconds(Long.parseLong(value));
            }
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get validation configuration
     */
    public ValidationConfig getValidationConfig() {
        return new ValidationConfig();
    }

    /**
     * Get MCP configuration
     */
    public McpConfig getMcpConfig() {
        return new McpConfig();
    }

    /**
     * Get observability configuration
     */
    public ObservabilityConfig getObservabilityConfig() {
        return new ObservabilityConfig();
    }

    /**
     * Get performance configuration
     */
    public PerformanceConfig getPerformanceConfig() {
        return new PerformanceConfig();
    }

    /**
     * Get CI/CD configuration
     */
    public CiCdConfig getCiCdConfig() {
        return new CiCdConfig();
    }

    /**
     * Validation configuration
     */
    public static class ValidationConfig {
        private final ActorValidationConfig config;

        public ValidationConfig() {
            this.config = ActorValidationConfig.this;
        }

        public boolean isEnabled() {
            return config.getBoolean("actor.validation.enabled");
        }

        public int getIntervalSeconds() {
            return config.getInt("actor.validation.interval.seconds");
        }

        public int getParallelThreads() {
            return config.getInt("actor.validation.parallel.threads");
        }

        public MemoryConfig getMemoryConfig() {
            return new MemoryConfig(config);
        }

        public DeadlockConfig getDeadlockConfig() {
            return new DeadlockConfig(config);
        }

        public PerformanceConfig getPerformanceConfig() {
            return new PerformanceConfig(config);
        }
    }

    /**
     * Memory configuration
     */
    public static class MemoryConfig {
        private final ActorValidationConfig config;

        public MemoryConfig(ActorValidationConfig config) {
            this.config = config;
        }

        public double getThresholdMB() {
            return config.getDouble("actor.validation.memory.threshold.mb");
        }

        public int getCheckIntervalSeconds() {
            return config.getInt("actor.validation.memory.check.interval.seconds");
        }

        public double getAlertThresholdPercent() {
            return config.getDouble("actor.validation.memory.alert.threshold.percent");
        }
    }

    /**
     * Deadlock configuration
     */
    public static class DeadlockConfig {
        private final ActorValidationConfig config;

        public DeadlockConfig(ActorValidationConfig config) {
            this.config = config;
        }

        public boolean isEnabled() {
            return config.getBoolean("actor.validation.deadlock.enabled");
        }

        public int getTimeoutSeconds() {
            return config.getInt("actor.validation.deadlock.timeout.seconds");
        }

        public int getThreadCountThreshold() {
            return config.getInt("actor.validation.deadlock.thread.count.threshold");
        }
    }

    /**
     * MCP configuration
     */
    public static class McpConfig {
        private final ActorValidationConfig config;

        public McpConfig() {
            this.config = ActorValidationConfig.this;
        }

        public boolean isEnabled() {
            return config.getBoolean("actor.validation.mcp.enabled");
        }

        public int getPort() {
            return config.getInt("actor.validation.mcp.port");
        }

        public String getHost() {
            return config.getString("actor.validation.mcp.host");
        }

        public boolean isSslEnabled() {
            return config.getBoolean("actor.validation.mcp.ssl.enabled");
        }
    }

    /**
     * Observability configuration
     */
    public static class ObservabilityConfig {
        private final ActorValidationConfig config;

        public ObservabilityConfig() {
            this.config = ActorValidationConfig.this;
        }

        public boolean isEnabled() {
            return config.getBoolean("actor.validation.observability.enabled");
        }

        public int getMetricsPort() {
            return config.getInt("actor.validation.observability.metrics.port");
        }

        public boolean isTracingEnabled() {
            return config.getBoolean("actor.validation.observability.tracing.enabled");
        }

        public String getJaegerEndpoint() {
            return config.getString("actor.validation.observability.jaeger.endpoint");
        }
    }

    /**
     * Performance configuration
     */
    public static class PerformanceConfig {
        private final ActorValidationConfig config;

        public PerformanceConfig() {
            this.config = ActorValidationConfig.this;
        }

        public PerformanceConfig(ActorValidationConfig config) {
            this.config = config;
        }

        public long getSlowThresholdMs() {
            return config.getLong("actor.validation.performance.slow.threshold.ms");
        }

        public double getAlertThresholdPercent() {
            return config.getDouble("actor.validation.performance.alert.threshold.percent");
        }

        public int getHistorySize() {
            return config.getInt("actor.validation.performance.history.size");
        }
    }

    /**
     * CI/CD configuration
     */
    public static class CiCdConfig {
        private final ActorValidationConfig config;

        public CiCdConfig() {
            this.config = ActorValidationConfig.this;
        }

        public boolean isEnabled() {
            return config.getBoolean("actor.validation.ci.enabled");
        }

        public int getArtifactsRetentionDays() {
            return config.getInt("actor.validation.ci.artifacts.retention.days");
        }

        public boolean isPrCommentsEnabled() {
            return config.getBoolean("actor.validation.ci.pr.comments.enabled");
        }
    }

    /**
     * Validate configuration
     */
    public void validate() {
        // Validate required properties
        String[] requiredProperties = {
            "actor.validation.enabled",
            "actor.validation.interval.seconds",
            "actor.validation.memory.threshold.mb"
        };

        for (String property : requiredProperties) {
            if (getProperty(property) == null) {
                throw new IllegalStateException("Required property missing: " + property);
            }
        }

        // Validate ranges
        if (getInt("actor.validation.interval.seconds") < 1) {
            throw new IllegalStateException("Validation interval must be at least 1 second");
        }

        if (getDouble("actor.validation.memory.threshold.mb") < 1) {
            throw new IllegalStateException("Memory threshold must be at least 1 MB");
        }

        if (getInt("actor.validation.parallel.threads") < 1) {
            throw new IllegalStateException("Parallel threads must be at least 1");
        }
    }

    /**
     * Get property value
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Get all properties
     */
    public Properties getProperties() {
        return new Properties(properties);
    }

    /**
     * Reload configuration
     */
    public void reload() {
        properties.clear();
        loadDefaultConfig();
        loadEnvironmentOverrides();
        loadConfigFile();
    }
}