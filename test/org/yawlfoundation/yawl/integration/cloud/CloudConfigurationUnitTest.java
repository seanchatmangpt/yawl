package org.yawlfoundation.yawl.integration.cloud;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck;
import org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck.CheckResult;
import org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck.HealthResult;
import org.yawlfoundation.yawl.integration.autonomous.observability.MetricsCollector;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for cloud platform configuration and observability.
 * Tests health check status evaluation, metrics collection, and configuration parsing.
 *
 * Chicago TDD: Real health checks and metrics collectors, no infrastructure dependencies.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class CloudConfigurationUnitTest {

    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() throws Exception {
        metricsCollector = new MetricsCollector();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (metricsCollector != null) {
            metricsCollector.shutdown();
        }
        metricsCollector = null;
    }

    @Test
    void testHealthCheckResultCreation() {
        CheckResult healthyResult = CheckResult.healthy("Service is responding");
        CheckResult unhealthyResult = CheckResult.unhealthy("Service is down");

        assertTrue(healthyResult.isHealthy(), "Healthy result should report as healthy");
        assertFalse(unhealthyResult.isHealthy(), "Unhealthy result should not report as healthy");
        assertEquals("Service is responding", healthyResult.getMessage(), "Healthy message should match");
        assertEquals("Service is down", unhealthyResult.getMessage(), "Unhealthy message should match");
    }

    @Test
    void testHealthCheckAggregation() {
        Map<String, CheckResult> checks = new HashMap<>();
        checks.put("database", CheckResult.healthy("Connected"));
        checks.put("engine", CheckResult.healthy("Running"));

        HealthResult result = new HealthResult("healthy", checks);

        assertTrue(result.isHealthy(), "Overall result should be healthy");
        assertEquals("healthy", result.getStatus(), "Status should be healthy");
        assertEquals(2, result.getChecks().size(), "Should have 2 checks");
    }

    @Test
    void testHealthCheckAggregationWithFailure() {
        Map<String, CheckResult> checks = new HashMap<>();
        checks.put("database", CheckResult.healthy("Connected"));
        checks.put("engine", CheckResult.unhealthy("Not responding"));

        HealthResult result = new HealthResult("unhealthy", checks);

        assertFalse(result.isHealthy(), "Overall result should be unhealthy");
        assertEquals("unhealthy", result.getStatus(), "Status should be unhealthy");
    }

    @Test
    void testMetricsCollectorCounterIncrement() {
        metricsCollector.incrementCounter("tasks_completed");
        metricsCollector.incrementCounter("tasks_completed");
        metricsCollector.incrementCounter("tasks_completed");

        long count = metricsCollector.getCounterValue("tasks_completed", new HashMap<>());
        assertEquals(3L, count, "Counter should be incremented 3 times");
    }

    @Test
    void testMetricsCollectorCounterWithLabels() {
        Map<String, String> labels1 = new HashMap<>();
        labels1.put("agent", "ordering");
        labels1.put("domain", "Ordering");

        Map<String, String> labels2 = new HashMap<>();
        labels2.put("agent", "carrier");
        labels2.put("domain", "Carrier");

        metricsCollector.incrementCounter("tasks_completed", labels1);
        metricsCollector.incrementCounter("tasks_completed", labels1);
        metricsCollector.incrementCounter("tasks_completed", labels2);

        long count1 = metricsCollector.getCounterValue("tasks_completed", labels1);
        long count2 = metricsCollector.getCounterValue("tasks_completed", labels2);

        assertEquals(2L, count1, "Ordering counter should be 2");
        assertEquals(1L, count2, "Carrier counter should be 1");
    }

    @Test
    void testMetricsCollectorDurationRecording() {
        metricsCollector.recordDuration("task_execution_time", 1000);
        metricsCollector.recordDuration("task_execution_time", 2000);
        metricsCollector.recordDuration("task_execution_time", 3000);

        long count = metricsCollector.getHistogramCount("task_execution_time", new HashMap<>());
        double sum = metricsCollector.getHistogramSum("task_execution_time", new HashMap<>());

        assertEquals(3L, count, "Should have 3 observations");
        assertEquals(6.0, sum, 0.001, "Sum should be 6.0 seconds (6000ms)");
    }

    @Test
    void testMetricsCollectorDurationWithLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("task", "approval");

        metricsCollector.recordDuration("task_execution_time", 500, labels);
        metricsCollector.recordDuration("task_execution_time", 1500, labels);

        long count = metricsCollector.getHistogramCount("task_execution_time", labels);
        double sum = metricsCollector.getHistogramSum("task_execution_time", labels);

        assertEquals(2L, count, "Should have 2 observations");
        assertEquals(2.0, sum, 0.001, "Sum should be 2.0 seconds (2000ms)");
    }

    @Test
    void testMetricsCollectorExport() {
        metricsCollector.incrementCounter("requests_total");
        metricsCollector.incrementCounter("requests_total");

        String metrics = metricsCollector.exportMetrics();

        assertNotNull(metrics, "Exported metrics should not be null");
        assertTrue(metrics.contains("requests_total"), "Should contain counter metric");
        assertTrue(metrics.contains("2"), "Should contain value");
    }

    @Test
    void testMetricsCollectorExportWithLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("status", "success");

        metricsCollector.incrementCounter("requests_total", labels);

        String metrics = metricsCollector.exportMetrics();

        assertTrue(metrics.contains("requests_total"), "Should contain metric name");
        assertTrue(metrics.contains("status"), "Should contain label");
        assertTrue(metrics.contains("success"), "Should contain label value");
    }

    @Test
    void testMetricsCollectorValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            metricsCollector.incrementCounter(null);
        }, "Should reject null metric name");

        assertThrows(IllegalArgumentException.class, () -> {
            metricsCollector.incrementCounter("");
        }, "Should reject empty metric name");

        assertThrows(IllegalArgumentException.class, () -> {
            metricsCollector.recordDuration(null, 1000);
        }, "Should reject null metric name for duration");
    }

    @Test
    void testMetricsCollectorValidationMessages() {
        try {
            metricsCollector.incrementCounter(null);
            fail("Should reject null metric name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("name"), "Error should mention metric name");
        }

        try {
            metricsCollector.incrementCounter("");
            fail("Should reject empty metric name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("name"), "Error should mention metric name");
        }

        try {
            metricsCollector.recordDuration(null, 1000);
            fail("Should reject null metric name for duration");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("name"), "Error should mention metric name");
        }
    }

    @Test
    void testMetricsCollectorZeroValues() {
        long count = metricsCollector.getCounterValue("nonexistent", new HashMap<>());
        assertEquals(0L, count, "Nonexistent counter should be 0");

        double sum = metricsCollector.getHistogramSum("nonexistent", new HashMap<>());
        assertEquals(0.0, sum, 0.001, "Nonexistent histogram sum should be 0.0");

        long histogramCount = metricsCollector.getHistogramCount("nonexistent", new HashMap<>());
        assertEquals(0L, histogramCount, "Nonexistent histogram count should be 0");
    }

    @Test
    void testHealthStatusEvaluation() {
        assertTrue(isHealthyStatus(200), "200 should be healthy");
        assertTrue(isHealthyStatus(201), "201 should be healthy");
        assertTrue(isHealthyStatus(204), "204 should be healthy");
        assertTrue(isHealthyStatus(400), "400 should be healthy (client error, service up)");
        assertTrue(isHealthyStatus(404), "404 should be healthy (client error, service up)");
        assertFalse(isHealthyStatus(500), "500 should be unhealthy");
        assertFalse(isHealthyStatus(503), "503 should be unhealthy");
    }

    @Test
    void testConfigurationParsing() {
        String engineUrl = parseEngineUrl("http://localhost:8080/yawl");
        assertEquals("http://localhost:8080/yawl", engineUrl, "Should parse engine URL");

        int port = parsePort("8091");
        assertEquals(8091, port, "Should parse port");

        long timeout = parseTimeout("5000");
        assertEquals(5000L, timeout, "Should parse timeout");
    }

    @Test
    void testConfigurationValidation() {
        assertTrue(isValidUrl("http://localhost:8080"), "Valid URL should pass");
        assertTrue(isValidUrl("https://example.com"), "HTTPS URL should pass");
        assertFalse(isValidUrl(""), "Empty URL should fail");
        assertFalse(isValidUrl(null), "Null URL should fail");
        assertFalse(isValidUrl("not-a-url"), "Invalid URL should fail");

        assertTrue(isValidPort(8080), "Valid port should pass");
        assertTrue(isValidPort(1), "Edge case port 1 should pass");
        assertTrue(isValidPort(65535), "Edge case port 65535 should pass");
        assertFalse(isValidPort(0), "Port 0 should fail");
        assertFalse(isValidPort(-1), "Negative port should fail");
        assertFalse(isValidPort(65536), "Port > 65535 should fail");
    }

    @Test
    void testMetricsPrometheusFormat() {
        Map<String, String> labels = new HashMap<>();
        labels.put("instance", "yawl-1");

        metricsCollector.incrementCounter("yawl_cases_total", labels);
        metricsCollector.recordDuration("yawl_case_duration_seconds", 1500, labels);

        String metrics = metricsCollector.exportMetrics();

        assertTrue(metrics.contains("yawl_cases_total"), "Should export counter");
        assertTrue(metrics.contains("yawl_case_duration_seconds_sum"), "Should export histogram sum");
        assertTrue(metrics.contains("yawl_case_duration_seconds_count"), "Should export histogram count");
    }

    private boolean isHealthyStatus(int httpStatus) {
        return httpStatus >= 200 && httpStatus < 500;
    }

    private String parseEngineUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Engine URL cannot be empty");
        }
        return url.trim();
    }

    private int parsePort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + portStr);
        }
    }

    private long parseTimeout(String timeoutStr) {
        try {
            long timeout = Long.parseLong(timeoutStr);
            if (timeout <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            return timeout;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid timeout: " + timeoutStr);
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }
}
