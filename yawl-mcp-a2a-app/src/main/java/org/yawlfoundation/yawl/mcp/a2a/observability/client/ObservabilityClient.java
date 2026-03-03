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

package org.yawlfoundation.yawl.mcp.a2a.observability.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.yawlfoundation.yawl.mcp.a2a.observability.config.ObservabilityConfig;

import java.io.IOException;

import java.time.Instant;
import java.util.*;

/**
 * OpenTelemetry observability client for YAWL integration.
 *
 * <p>This client provides a unified interface to query metrics, traces,
 * and health status from OpenTelemetry collectors and backends like
 * Prometheus, Jaeger, and AlertManager.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><strong>Metrics Query</strong> - Query Prometheus-style metrics</li>
 *   <li><strong>Trace Analysis</strong> - Query distributed traces from Jaeger</li>
 *   <li><strong>Health Status</strong> - Get service health and readiness</li>
 *   <li><strong>Alert Status</strong> - Query current alert states</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see ObservabilityConfig
 */
public class ObservabilityClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ObservabilityConfig config;
    private final RestTemplate restTemplate;

    /**
     * Create a new Observability client with configuration.
     *
     * @param config Observability configuration
     */
    public ObservabilityClient(ObservabilityConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
        this.restTemplate.setConnectTimeout(java.time.Duration.ofMillis(config.getQuery().getTimeoutMs()));
        this.restTemplate.setReadTimeout(java.time.Duration.ofMillis(config.getQuery().getTimeoutMs()));
    }

    /**
     * Query Prometheus metrics.
     *
     * @param query PromQL query string
     * @param time Optional query timestamp (epoch seconds)
     * @return Query result as JSON
     * @throws IOException if query fails
     */
    public JsonNode queryMetrics(String query, Long time) throws IOException {
        if (config.getPrometheusUrl() == null || config.getPrometheusUrl().isBlank()) {
            return buildErrorResponse("Prometheus URL not configured");
        }

        String url = config.getPrometheusUrl() + "/api/v1/query";
        StringBuilder params = new StringBuilder("?query=" + encode(query));
        if (time != null) {
            params.append("&time=").append(time);
        }

        return getRequest(url + params.toString(), buildPrometheusHeaders());
    }

    /**
     * Query Prometheus metrics range.
     *
     * @param query PromQL query string
     * @param start Start timestamp (epoch seconds)
     * @param end End timestamp (epoch seconds)
     * @param step Query step interval (e.g., "15s")
     * @return Query result as JSON
     * @throws IOException if query fails
     */
    public JsonNode queryMetricsRange(String query, long start, long end, String step) throws IOException {
        if (config.getPrometheusUrl() == null || config.getPrometheusUrl().isBlank()) {
            return buildErrorResponse("Prometheus URL not configured");
        }

        String url = config.getPrometheusUrl() + "/api/v1/query_range";
        String params = String.format("?query=%s&start=%d&end=%d&step=%s",
            encode(query), start, end, encode(step));

        return getRequest(url + params, buildPrometheusHeaders());
    }

    /**
     * Query Jaeger traces.
     *
     * @param service Service name to filter by
     * @param operation Operation name to filter by (optional)
     * @param limit Maximum number of traces to return
     * @return Traces as JSON
     * @throws IOException if query fails
     */
    public JsonNode queryTraces(String service, String operation, int limit) throws IOException {
        if (config.getJaegerUrl() == null || config.getJaegerUrl().isBlank()) {
            return buildErrorResponse("Jaeger URL not configured");
        }

        StringBuilder url = new StringBuilder(config.getJaegerUrl() + "/api/traces");
        url.append("?service=").append(encode(service));
        if (operation != null && !operation.isBlank()) {
            url.append("&operation=").append(encode(operation));
        }
        url.append("&limit=").append(limit);

        return getRequest(url.toString(), buildJsonHeaders());
    }

    /**
     * Query Jaeger traces by case ID.
     *
     * @param caseId YAWL case ID to search for
     * @param limit Maximum number of traces to return
     * @return Traces as JSON
     * @throws IOException if query fails
     */
    public JsonNode queryTracesByCaseId(String caseId, int limit) throws IOException {
        if (config.getJaegerUrl() == null || config.getJaegerUrl().isBlank()) {
            return buildErrorResponse("Jaeger URL not configured");
        }

        // Query Jaeger with case-id tag filter
        String url = config.getJaegerUrl() + "/api/traces";
        String params = String.format("?service=%s&tags={\"case-id\":\"%s\"}&limit=%d",
            encode(config.getServiceName()), encode(caseId), limit);

        return getRequest(url + params, buildJsonHeaders());
    }

    /**
     * Get trace by ID.
     *
     * @param traceId Trace ID
     * @return Trace details as JSON
     * @throws IOException if query fails
     */
    public JsonNode getTraceById(String traceId) throws IOException {
        if (config.getJaegerUrl() == null || config.getJaegerUrl().isBlank()) {
            return buildErrorResponse("Jaeger URL not configured");
        }

        String url = config.getJaegerUrl() + "/api/traces/" + traceId;
        return getRequest(url, buildJsonHeaders());
    }

    /**
     * Get health status of all configured services.
     *
     * @return Health status as JSON
     */
    public JsonNode getHealthStatus() {
        ObjectNode health = objectMapper.createObjectNode();
        health.put("timestamp", Instant.now().toString());
        health.put("service", config.getServiceName());

        ObjectNode services = health.putObject("services");

        // Check Prometheus
        services.set("prometheus", checkServiceHealth(config.getPrometheusUrl()));

        // Check Jaeger
        services.set("jaeger", checkServiceHealth(config.getJaegerUrl()));

        // Check OTLP
        services.set("otlp", checkServiceHealth(config.getOtlpEndpoint()));

        // Check AlertManager
        services.set("alertmanager", checkServiceHealth(config.getAlertmanagerUrl()));

        // Overall status
        boolean allHealthy = true;
        for (JsonNode service : services) {
            if (!service.path("healthy").asBoolean(false)) {
                allHealthy = false;
                break;
            }
        }
        health.put("healthy", allHealthy);
        health.put("status", allHealthy ? "UP" : "DEGRADED");

        return health;
    }

    /**
     * Get span summary for a service.
     *
     * @param service Service name
     * @param lookbackMs Lookback period in milliseconds
     * @return Span summary as JSON
     */
    public JsonNode getSpanSummary(String service, long lookbackMs) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("service", service);
        summary.put("lookback_ms", lookbackMs);
        summary.put("timestamp", Instant.now().toString());

        // Build summary from metrics
        ArrayNode operations = summary.putArray("operations");

        try {
            // Query rate of spans by operation
            String rateQuery = String.format(
                "sum by (operation) (rate(yawl_span_duration_seconds_count{service=\"%s\"}[%ds]))",
                service, lookbackMs / 1000);

            JsonNode rateResult = queryMetrics(rateQuery, null);
            if (rateResult.path("status").asText().equals("success")) {
                JsonNode data = rateResult.path("data").path("result");
                if (data.isArray()) {
                    for (JsonNode result : data) {
                        ObjectNode op = objectMapper.createObjectNode();
                        op.put("operation", result.path("metric").path("operation").asText("unknown"));
                        op.put("rate_per_second", result.path("value").get(1).asDouble(0.0));
                        operations.add(op);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to query span summary: {}", e.getMessage());
        }

        // Add latency percentiles
        ObjectNode latency = summary.putObject("latency");
        try {
            String p50Query = String.format(
                "histogram_quantile(0.50, sum by (le) (rate(yawl_span_duration_seconds_bucket{service=\"%s\"}[%ds])))",
                service, lookbackMs / 1000);
            JsonNode p50 = queryMetrics(p50Query, null);
            latency.put("p50_seconds", extractMetricValue(p50));

            String p95Query = String.format(
                "histogram_quantile(0.95, sum by (le) (rate(yawl_span_duration_seconds_bucket{service=\"%s\"}[%ds])))",
                service, lookbackMs / 1000);
            JsonNode p95 = queryMetrics(p95Query, null);
            latency.put("p95_seconds", extractMetricValue(p95));

            String p99Query = String.format(
                "histogram_quantile(0.99, sum by (le) (rate(yawl_span_duration_seconds_bucket{service=\"%s\"}[%ds])))",
                service, lookbackMs / 1000);
            JsonNode p99 = queryMetrics(p99Query, null);
            latency.put("p99_seconds", extractMetricValue(p99));
        } catch (Exception e) {
            LOGGER.warn("Failed to query latency percentiles: {}", e.getMessage());
        }

        return summary;
    }

    /**
     * Get alert status from AlertManager.
     *
     * @param filter Optional filter (e.g., "service=yawl-engine")
     * @return Alert status as JSON
     */
    public JsonNode getAlertStatus(String filter) {
        if (config.getAlertmanagerUrl() == null || config.getAlertmanagerUrl().isBlank()) {
            return buildErrorResponse("AlertManager URL not configured");
        }

        try {
            String url = config.getAlertmanagerUrl() + "/api/v2/alerts";
            if (filter != null && !filter.isBlank()) {
                url += "?filter=" + encode(filter);
            }
            return getRequest(url, buildJsonHeaders());
        } catch (Exception e) {
            LOGGER.warn("Failed to query alert status: {}", e.getMessage());
            return buildErrorResponse("Failed to query alerts: " + e.getMessage());
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private JsonNode checkServiceHealth(String url) {
        ObjectNode status = objectMapper.createObjectNode();
        if (url == null || url.isBlank()) {
            status.put("healthy", false);
            status.put("status", "NOT_CONFIGURED");
            status.put("url", "not configured");
            return status;
        }

        status.put("url", url);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url + "/-/healthy", HttpMethod.GET,
                new HttpEntity<>(buildJsonHeaders()), String.class);

            status.put("healthy", response.getStatusCode() == HttpStatus.OK);
            status.put("status", response.getStatusCode() == HttpStatus.OK ? "UP" : "DOWN");
            status.put("response_time_ms", 0); // Could measure actual time
        } catch (Exception e) {
            status.put("healthy", false);
            status.put("status", "DOWN");
            status.put("error", e.getMessage());
        }

        return status;
    }

    private JsonNode getRequest(String url, HttpHeaders headers) throws IOException {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return objectMapper.readTree(response.getBody());
            } else {
                throw new IOException("Request failed: " + response.getStatusCode());
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new IOException("HTTP error: " + e.getStatusCode() + " - " + e.getMessage(), e);
        } catch (ResourceAccessException e) {
            throw new IOException("Connection error: " + e.getMessage(), e);
        }
    }

    private HttpHeaders buildJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private HttpHeaders buildPrometheusHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String encode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value to encode must not be null");
        }
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private JsonNode buildErrorResponse(String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("status", "error");
        error.put("error", message);
        error.put("timestamp", Instant.now().toString());
        return error;
    }

    private double extractMetricValue(JsonNode result) {
        try {
            JsonNode value = result.path("data").path("result").get(0).path("value").get(1);
            return value.asDouble(0.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Get the observability configuration.
     *
     * @return Configuration
     */
    public ObservabilityConfig getConfig() {
        return config;
    }
}
