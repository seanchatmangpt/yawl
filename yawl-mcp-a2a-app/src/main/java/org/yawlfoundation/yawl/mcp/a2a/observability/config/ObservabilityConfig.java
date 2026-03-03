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

package org.yawlfoundation.yawl.mcp.a2a.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Configuration properties for OpenTelemetry observability integration.
 *
 * <p>This class provides type-safe access to observability configuration properties
 * defined in application.yml. It includes validation constraints to ensure
 * required properties are properly configured.</p>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * yawl:
 *   observability:
 *     enabled: true
 *     prometheus-url: http://localhost:9090
 *     jaeger-url: http://localhost:16686
 *     otlp-endpoint: http://localhost:4317
 *     alertmanager-url: http://localhost:9093
 *     query:
 *       timeout-ms: 30000
 *       max-results: 1000
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Validated
@ConfigurationProperties(prefix = "yawl.observability")
public class ObservabilityConfig {

    /**
     * Whether observability integration is enabled.
     */
    private boolean enabled = true;

    /**
     * Prometheus server URL for metrics queries.
     */
    private String prometheusUrl = "http://localhost:9090";

    /**
     * Jaeger UI URL for trace queries.
     */
    private String jaegerUrl = "http://localhost:16686";

    /**
     * OTLP collector endpoint.
     */
    private String otlpEndpoint = "http://localhost:4317";

    /**
     * AlertManager URL for alert status queries.
     */
    private String alertmanagerUrl = "http://localhost:9093";

    /**
     * Query configuration.
     */
    private QueryConfig query = new QueryConfig();

    /**
     * Service name for identifying this YAWL instance in traces.
     */
    private String serviceName = "yawl-engine";

    /**
     * Whether to include workflow case IDs in trace spans.
     */
    private boolean includeCaseIds = true;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrometheusUrl() {
        return prometheusUrl;
    }

    public void setPrometheusUrl(String prometheusUrl) {
        this.prometheusUrl = prometheusUrl;
    }

    public String getJaegerUrl() {
        return jaegerUrl;
    }

    public void setJaegerUrl(String jaegerUrl) {
        this.jaegerUrl = jaegerUrl;
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    public void setOtlpEndpoint(String otlpEndpoint) {
        this.otlpEndpoint = otlpEndpoint;
    }

    public String getAlertmanagerUrl() {
        return alertmanagerUrl;
    }

    public void setAlertmanagerUrl(String alertmanagerUrl) {
        this.alertmanagerUrl = alertmanagerUrl;
    }

    public QueryConfig getQuery() {
        return query;
    }

    public void setQuery(QueryConfig query) {
        this.query = query;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public boolean isIncludeCaseIds() {
        return includeCaseIds;
    }

    public void setIncludeCaseIds(boolean includeCaseIds) {
        this.includeCaseIds = includeCaseIds;
    }

    /**
     * Builder for creating ObservabilityConfig instances.
     */
    public static class Builder {
        private final ObservabilityConfig config;

        public Builder() {
            config = new ObservabilityConfig();
        }

        public Builder prometheusUrl(String prometheusUrl) {
            config.prometheusUrl = prometheusUrl;
            return this;
        }

        public Builder jaegerUrl(String jaegerUrl) {
            config.jaegerUrl = jaegerUrl;
            return this;
        }

        public Builder otlpEndpoint(String otlpEndpoint) {
            config.otlpEndpoint = otlpEndpoint;
            return this;
        }

        public Builder alertmanagerUrl(String alertmanagerUrl) {
            config.alertmanagerUrl = alertmanagerUrl;
            return this;
        }

        public Builder serviceName(String serviceName) {
            config.serviceName = serviceName;
            return this;
        }

        public Builder enabled(boolean enabled) {
            config.enabled = enabled;
            return this;
        }

        public Builder includeCaseIds(boolean includeCaseIds) {
            config.includeCaseIds = includeCaseIds;
            return this;
        }

        public Builder query(QueryConfig query) {
            config.query = query;
            return this;
        }

        public ObservabilityConfig build() {
            return config;
        }
    }

    /**
     * Create a new builder for ObservabilityConfig.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Query configuration.
     */
    public static class QueryConfig {
        /**
         * Query timeout in milliseconds.
         */
        private int timeoutMs = 30000;

        /**
         * Maximum number of results to return.
         */
        private int maxResults = 1000;

        /**
         * Whether to cache query results.
         */
        private boolean cacheEnabled = true;

        /**
         * Cache TTL in seconds.
         */
        private int cacheTtlSeconds = 60;

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public boolean isCacheEnabled() {
            return cacheEnabled;
        }

        public void setCacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
        }

        public int getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(int cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }
    }
}
