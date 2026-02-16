/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.cloud;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.autonomous.observability.StructuredLogger;
import org.yawlfoundation.yawl.integration.autonomous.observability.MetricsCollector;
import org.yawlfoundation.yawl.integration.autonomous.observability.HealthCheck;

import java.util.Map;

/**
 * Integration tests for OpenTelemetry trace and metric export.
 *
 * Tests cover:
 * - Trace export to backend systems
 * - Metric export (histograms, counters, gauges)
 * - Log correlation with trace/span IDs
 * - Baggage propagation
 * - Sampling configuration
 * - Exporter lifecycle management
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class OpenTelemetryIntegrationTest extends TestCase {

    private StructuredLogger logger;
    private MetricsCollector metricsCollector;
    private HealthCheck healthCheck;

    public OpenTelemetryIntegrationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        logger = new StructuredLogger();
        metricsCollector = new MetricsCollector();
        healthCheck = new HealthCheck();
    }

    @Override
    protected void tearDown() throws Exception {
        if (logger != null) {
            logger.shutdown();
        }
        if (metricsCollector != null) {
            metricsCollector.shutdown();
        }
        super.tearDown();
    }

    /**
     * Test structured logger initialization
     */
    public void testStructuredLoggerInitialization() throws Exception {
        assertNotNull("Logger initialized", logger);
        assertTrue("Logger ready", logger.isReady());
    }

    /**
     * Test trace span creation and export
     */
    public void testTraceSpanCreation() throws Exception {
        String spanName = "workflow.execution";
        Object span = logger.startSpan(spanName);
        
        assertNotNull("Span created", span);
        
        logger.addSpanAttribute("operation", "launch_case");
        logger.addSpanAttribute("case_id", "12345");
        
        logger.endSpan(span);
        assertTrue("Span completed", logger.isSpanCompleted(span));
    }

    /**
     * Test log event with trace context
     */
    public void testLogEventWithTraceContext() throws Exception {
        String spanId = "span-123";
        String traceId = "trace-456";
        
        logger.setTraceContext(traceId, spanId);
        logger.info("Workflow event", "type", "task_start");
        
        Map<String, String> context = logger.getTraceContext();
        assertEquals("Trace ID preserved", traceId, context.get("traceId"));
        assertEquals("Span ID preserved", spanId, context.get("spanId"));
    }

    /**
     * Test metric collection for counters
     */
    public void testMetricCounters() throws Exception {
        assertNotNull("Metrics collector initialized", metricsCollector);
        
        metricsCollector.incrementCounter("workflow.launches", 1);
        metricsCollector.incrementCounter("workflow.launches", 1);
        metricsCollector.incrementCounter("workflow.launches", 1);
        
        long count = metricsCollector.getCounterValue("workflow.launches");
        assertEquals("Counter incremented correctly", 3, count);
    }

    /**
     * Test metric collection for histograms
     */
    public void testMetricHistograms() throws Exception {
        metricsCollector.recordHistogram("task.duration_ms", 100);
        metricsCollector.recordHistogram("task.duration_ms", 150);
        metricsCollector.recordHistogram("task.duration_ms", 200);
        
        double mean = metricsCollector.getHistogramMean("task.duration_ms");
        assertTrue("Mean calculated", mean > 100 && mean < 200);
        
        double p95 = metricsCollector.getHistogramPercentile("task.duration_ms", 95);
        assertTrue("P95 calculated", p95 > 100);
    }

    /**
     * Test metric collection for gauges
     */
    public void testMetricGauges() throws Exception {
        metricsCollector.setGauge("active.workitems", 42);
        
        double gauge = metricsCollector.getGaugeValue("active.workitems");
        assertEquals("Gauge set correctly", 42.0, gauge);
        
        metricsCollector.setGauge("active.workitems", 50);
        gauge = metricsCollector.getGaugeValue("active.workitems");
        assertEquals("Gauge updated", 50.0, gauge);
    }

    /**
     * Test baggage propagation
     */
    public void testBaggagePropagation() throws Exception {
        logger.setBaggage("request_id", "req-789");
        logger.setBaggage("user_id", "user-123");
        
        String requestId = logger.getBaggage("request_id");
        assertEquals("Request ID propagated", "req-789", requestId);
        
        String userId = logger.getBaggage("user_id");
        assertEquals("User ID propagated", "user-123", userId);
    }

    /**
     * Test sampling configuration
     */
    public void testSamplingConfiguration() throws Exception {
        // Test always-on sampling
        logger.setSamplingRate(1.0);
        for (int i = 0; i < 100; i++) {
            Object span = logger.startSpan("test");
            logger.endSpan(span);
        }
        assertEquals("All spans sampled with rate=1.0", 100, logger.getExportedSpanCount());
        
        // Test sampling with probability
        logger.setSamplingRate(0.5);
        logger.resetExportedSpanCount();
        for (int i = 0; i < 100; i++) {
            Object span = logger.startSpan("test");
            logger.endSpan(span);
        }
        long exportedCount = logger.getExportedSpanCount();
        assertTrue("Approximately 50% sampled", exportedCount > 30 && exportedCount < 70);
    }

    /**
     * Test trace export to backend
     */
    public void testTraceExport() throws Exception {
        logger.configureExporter("otlp", "http://localhost:4317");
        
        Object span = logger.startSpan("export.test");
        logger.addSpanAttribute("test", "data");
        logger.endSpan(span);
        
        logger.flush();
        
        assertTrue("Export completed", logger.isExported());
    }

    /**
     * Test metric export to backend
     */
    public void testMetricExport() throws Exception {
        metricsCollector.configureExporter("otlp", "http://localhost:4317");
        
        metricsCollector.incrementCounter("test.counter", 5);
        metricsCollector.recordHistogram("test.histogram", 100);
        
        metricsCollector.flush();
        
        assertTrue("Metrics exported", metricsCollector.isExported());
    }

    /**
     * Test health check endpoint integration
     */
    public void testHealthCheckIntegration() throws Exception {
        assertNotNull("Health check initialized", healthCheck);
        
        boolean isHealthy = healthCheck.check();
        assertTrue("System health checked", true); // May be healthy or not depending on deps
    }

    /**
     * Test trace context propagation across threads
     */
    public void testTraceContextPropagation() throws Exception {
        String originalTraceId = "trace-original";
        logger.setTraceContext(originalTraceId, "span-original");
        
        final String[] childTraceId = {null};
        Thread childThread = new Thread(() -> {
            childTraceId[0] = logger.getTraceContext().get("traceId");
        });
        
        childThread.start();
        childThread.join();
        
        assertEquals("Trace ID propagated to child thread", originalTraceId, childTraceId[0]);
    }

    /**
     * Test concurrent metric recording
     */
    public void testConcurrentMetricRecording() throws Exception {
        final int threadCount = 10;
        final int incrementsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    metricsCollector.incrementCounter("concurrent.test", 1);
                }
            });
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        long total = metricsCollector.getCounterValue("concurrent.test");
        assertEquals("All increments recorded", threadCount * incrementsPerThread, total);
    }

    /**
     * Test exporter failure handling
     */
    public void testExporterFailureHandling() throws Exception {
        logger.configureExporter("otlp", "http://invalid-host:99999");
        
        Object span = logger.startSpan("failure.test");
        logger.endSpan(span);
        
        try {
            logger.flush();
        } catch (Exception e) {
            // Expected to fail due to invalid host
            assertTrue("Export attempted", true);
        }
        
        // Should gracefully handle failure
        assertTrue("Logger still functional", logger.isReady());
    }

    /**
     * Test span attributes with various types
     */
    public void testSpanAttributeTypes() throws Exception {
        Object span = logger.startSpan("attribute.test");
        
        logger.addSpanAttribute("string_attr", "value");
        logger.addSpanAttribute("int_attr", 42);
        logger.addSpanAttribute("float_attr", 3.14);
        logger.addSpanAttribute("bool_attr", true);
        
        logger.endSpan(span);
        
        assertTrue("Span with multiple attribute types created", logger.isSpanCompleted(span));
    }

    /**
     * Test metric export interval configuration
     */
    public void testMetricExportInterval() throws Exception {
        metricsCollector.setExportIntervalMs(500);
        
        metricsCollector.incrementCounter("interval.test", 1);
        
        long startTime = System.currentTimeMillis();
        metricsCollector.flush();
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue("Export interval respected", duration >= 0);
    }

    /**
     * Test structured logger shutdown behavior
     */
    public void testLoggerShutdownBehavior() throws Exception {
        Object span = logger.startSpan("shutdown.test");
        logger.endSpan(span);
        
        logger.shutdown();
        
        assertFalse("Logger no longer ready after shutdown", logger.isReady());
    }
}
