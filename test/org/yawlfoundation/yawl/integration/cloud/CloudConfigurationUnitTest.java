package org.yawlfoundation.yawl.integration.cloud;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck;
import org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck.CheckResult;
import org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck.HealthResult;
import org.yawlfoundation.yawl.integration.autonomous.observability.MetricsCollector;

import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for cloud platform configuration and observability.
 * Tests health check status evaluation, metrics collection, and configuration parsing.
 *
 * Chicago TDD: Real health checks and metrics collectors, no infrastructure dependencies.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class CloudConfigurationUnitTest extends TestCase {

    private MetricsCollector metricsCollector;

    public CloudConfigurationUnitTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        metricsCollector = new MetricsCollector();
    }

    @Override
    protected void tearDown() throws Exception {
        if (metricsCollector != null) {
            metricsCollector.shutdown();
        }
        metricsCollector = null;
        super.tearDown();
    }

    public void testHealthCheckResultCreation() {
        CheckResult healthyResult = CheckResult.healthy("Service is responding");
        CheckResult unhealthyResult = CheckResult.unhealthy("Service is down");

        assertTrue("Healthy result should report as healthy", healthyResult.isHealthy());
        assertFalse("Unhealthy result should not report as healthy", unhealthyResult.isHealthy());
        assertEquals("Healthy message should match", "Service is responding", healthyResult.getMessage());
        assertEquals("Unhealthy message should match", "Service is down", unhealthyResult.getMessage());
    }

    public void testHealthCheckAggregation() {
        Map<String, CheckResult> checks = new HashMap<>();
        checks.put("database", CheckResult.healthy("Connected"));
        checks.put("engine", CheckResult.healthy("Running"));

        HealthResult result = new HealthResult("healthy", checks);

        assertTrue("Overall result should be healthy", result.isHealthy());
        assertEquals("Status should be healthy", "healthy", result.getStatus());
        assertEquals("Should have 2 checks", 2, result.getChecks().size());
    }

    public void testHealthCheckAggregationWithFailure() {
        Map<String, CheckResult> checks = new HashMap<>();
        checks.put("database", CheckResult.healthy("Connected"));
        checks.put("engine", CheckResult.unhealthy("Not responding"));

        HealthResult result = new HealthResult("unhealthy", checks);

        assertFalse("Overall result should be unhealthy", result.isHealthy());
        assertEquals("Status should be unhealthy", "unhealthy", result.getStatus());
    }

    public void testMetricsCollectorCounterIncrement() {
        metricsCollector.incrementCounter("tasks_completed");
        metricsCollector.incrementCounter("tasks_completed");
        metricsCollector.incrementCounter("tasks_completed");

        long count = metricsCollector.getCounterValue("tasks_completed", new HashMap<>());
        assertEquals("Counter should be incremented 3 times", 3L, count);
    }

    public void testMetricsCollectorCounterWithLabels() {
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

        assertEquals("Ordering counter should be 2", 2L, count1);
        assertEquals("Carrier counter should be 1", 1L, count2);
    }

    public void testMetricsCollectorDurationRecording() {
        metricsCollector.recordDuration("task_execution_time", 1000);
        metricsCollector.recordDuration("task_execution_time", 2000);
        metricsCollector.recordDuration("task_execution_time", 3000);

        long count = metricsCollector.getHistogramCount("task_execution_time", new HashMap<>());
        double sum = metricsCollector.getHistogramSum("task_execution_time", new HashMap<>());

        assertEquals("Should have 3 observations", 3L, count);
        assertEquals("Sum should be 6.0 seconds (6000ms)", 6.0, sum, 0.001);
    }

    public void testMetricsCollectorDurationWithLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("task", "approval");

        metricsCollector.recordDuration("task_execution_time", 500, labels);
        metricsCollector.recordDuration("task_execution_time", 1500, labels);

        long count = metricsCollector.getHistogramCount("task_execution_time", labels);
        double sum = metricsCollector.getHistogramSum("task_execution_time", labels);

        assertEquals("Should have 2 observations", 2L, count);
        assertEquals("Sum should be 2.0 seconds (2000ms)", 2.0, sum, 0.001);
    }

    public void testMetricsCollectorExport() {
        metricsCollector.incrementCounter("requests_total");
        metricsCollector.incrementCounter("requests_total");

        String metrics = metricsCollector.exportMetrics();

        assertNotNull("Exported metrics should not be null", metrics);
        assertTrue("Should contain counter metric", metrics.contains("requests_total"));
        assertTrue("Should contain value", metrics.contains("2"));
    }

    public void testMetricsCollectorExportWithLabels() {
        Map<String, String> labels = new HashMap<>();
        labels.put("status", "success");

        metricsCollector.incrementCounter("requests_total", labels);

        String metrics = metricsCollector.exportMetrics();

        assertTrue("Should contain metric name", metrics.contains("requests_total"));
        assertTrue("Should contain label", metrics.contains("status"));
        assertTrue("Should contain label value", metrics.contains("success"));
    }

    public void testMetricsCollectorValidation() {
        try {
            metricsCollector.incrementCounter(null);
            fail("Should reject null metric name");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention metric name", e.getMessage().contains("name"));
        }

        try {
            metricsCollector.incrementCounter("");
            fail("Should reject empty metric name");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention metric name", e.getMessage().contains("name"));
        }

        try {
            metricsCollector.recordDuration(null, 1000);
            fail("Should reject null metric name for duration");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention metric name", e.getMessage().contains("name"));
        }
    }

    public void testMetricsCollectorZeroValues() {
        long count = metricsCollector.getCounterValue("nonexistent", new HashMap<>());
        assertEquals("Nonexistent counter should be 0", 0L, count);

        double sum = metricsCollector.getHistogramSum("nonexistent", new HashMap<>());
        assertEquals("Nonexistent histogram sum should be 0.0", 0.0, sum, 0.001);

        long histogramCount = metricsCollector.getHistogramCount("nonexistent", new HashMap<>());
        assertEquals("Nonexistent histogram count should be 0", 0L, histogramCount);
    }

    public void testHealthStatusEvaluation() {
        assertTrue("200 should be healthy", isHealthyStatus(200));
        assertTrue("201 should be healthy", isHealthyStatus(201));
        assertTrue("204 should be healthy", isHealthyStatus(204));
        assertTrue("400 should be healthy (client error, service up)", isHealthyStatus(400));
        assertTrue("404 should be healthy (client error, service up)", isHealthyStatus(404));
        assertFalse("500 should be unhealthy", isHealthyStatus(500));
        assertFalse("503 should be unhealthy", isHealthyStatus(503));
    }

    public void testConfigurationParsing() {
        String engineUrl = parseEngineUrl("http://localhost:8080/yawl");
        assertEquals("Should parse engine URL", "http://localhost:8080/yawl", engineUrl);

        int port = parsePort("8091");
        assertEquals("Should parse port", 8091, port);

        long timeout = parseTimeout("5000");
        assertEquals("Should parse timeout", 5000L, timeout);
    }

    public void testConfigurationValidation() {
        assertTrue("Valid URL should pass", isValidUrl("http://localhost:8080"));
        assertTrue("HTTPS URL should pass", isValidUrl("https://example.com"));
        assertFalse("Empty URL should fail", isValidUrl(""));
        assertFalse("Null URL should fail", isValidUrl(null));
        assertFalse("Invalid URL should fail", isValidUrl("not-a-url"));

        assertTrue("Valid port should pass", isValidPort(8080));
        assertTrue("Edge case port 1 should pass", isValidPort(1));
        assertTrue("Edge case port 65535 should pass", isValidPort(65535));
        assertFalse("Port 0 should fail", isValidPort(0));
        assertFalse("Negative port should fail", isValidPort(-1));
        assertFalse("Port > 65535 should fail", isValidPort(65536));
    }

    public void testMetricsPrometheusFormat() {
        Map<String, String> labels = new HashMap<>();
        labels.put("instance", "yawl-1");

        metricsCollector.incrementCounter("yawl_cases_total", labels);
        metricsCollector.recordDuration("yawl_case_duration_seconds", 1500, labels);

        String metrics = metricsCollector.exportMetrics();

        assertTrue("Should export counter", metrics.contains("yawl_cases_total"));
        assertTrue("Should export histogram sum", metrics.contains("yawl_case_duration_seconds_sum"));
        assertTrue("Should export histogram count", metrics.contains("yawl_case_duration_seconds_count"));
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

    public static Test suite() {
        TestSuite suite = new TestSuite("Cloud Configuration Unit Tests");
        suite.addTestSuite(CloudConfigurationUnitTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
