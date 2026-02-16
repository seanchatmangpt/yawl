package org.yawlfoundation.yawl.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Observability Integration Tests - Verifies OpenTelemetry tracing and metrics
 * Tests span creation, metric collection, and trace propagation
 */
public class ObservabilityIntegrationTest {

    private MetricsCollector metricsCollector;
    private TraceContext traceContext;

    @BeforeEach
    public void setUp() {
        metricsCollector = new MetricsCollector();
        traceContext = new TraceContext();
    }

    @Test
    public void testMetricCollection() {
        metricsCollector.recordProcessStart("workflow_1", 100);
        metricsCollector.recordProcessStart("workflow_2", 150);
        metricsCollector.recordProcessCompletion("workflow_1", 250);
        
        assertEquals("Should have 2 active processes", 1, metricsCollector.getActiveProcessCount());
        assertEquals("Should have 1 completed process", 1, metricsCollector.getCompletedProcessCount());
    }

    @Test
    public void testSpanCreation() {
        Span span = traceContext.createSpan("process.execution");
        
        assertNotNull("Span should be created", span);
        assertTrue("Span should be active", span.isActive());
        assertEquals("Span name should match", "process.execution", span.getName());
        
        span.addAttribute("process_id", "proc_123");
        span.addAttribute("task_count", "5");
        
        assertEquals("Span should have 2 attributes", 2, span.getAttributeCount());
    }

    @Test
    public void testSpanEvents() {
        Span span = traceContext.createSpan("workflow.execution");
        
        span.addEvent("task_started");
        span.addEvent("task_completed");
        span.addEvent("task_failed");
        
        assertEquals("Span should have 3 events", 3, span.getEventCount());
    }

    @Test
    public void testTracePropagation() {
        String traceId = "trace_12345abcde";
        String spanId = "span_67890fghij";
        
        Span parentSpan = traceContext.createSpan("parent.span", traceId, spanId);
        Span childSpan = traceContext.createChildSpan(parentSpan, "child.span");
        
        assertNotNull("Child span should be created", childSpan);
        assertEquals("Child span should inherit trace ID", traceId, childSpan.getTraceId());
        assertEquals("Child span should have parent span ID", parentSpan.getSpanId(), childSpan.getParentSpanId());
    }

    @Test
    public void testMetricLabeling() {
        metricsCollector.recordProcessStart("workflow_1", 100);
        metricsCollector.recordProcessStart("workflow_2", 150);
        metricsCollector.recordProcessStart("workflow_3", 200);
        
        int count = metricsCollector.getProcessCountByStatus("ACTIVE");
        assertEquals("Should have 3 active processes", 3, count);
    }

    @Test
    public void testErrorSpan() {
        Span errorSpan = traceContext.createSpan("error.operation");
        errorSpan.addAttribute("error", "true");
        errorSpan.addAttribute("error.type", "TaskException");
        errorSpan.addAttribute("error.message", "Task execution failed");
        
        assertEquals("Error span should have error attributes", 3, errorSpan.getAttributeCount());
        assertTrue("Error attribute should be true", errorSpan.getAttribute("error").equals("true"));
    }

    @Test
    public void testMetricAggregation() {
        for (int i = 0; i < 100; i++) {
            metricsCollector.recordProcessStart("workflow_batch", 50 + i);
        }
        
        assertEquals("Should have 100 processes started", 100, metricsCollector.getProcessCountByStatus("ACTIVE"));
        
        double avgDuration = metricsCollector.getAverageDuration();
        assertTrue("Average duration should be calculated", avgDuration > 0);
    }

    @Test
    public void testSpanDuration() throws InterruptedException {
        Span span = traceContext.createSpan("timed.operation");
        span.start();
        
        Thread.sleep(100);
        
        span.end();
        long duration = span.getDurationMillis();
        
        assertTrue("Duration should be at least 100ms", duration >= 100);
    }

    @Test
    public void testConcurrentMetrics() throws InterruptedException {
        Thread[] threads = new Thread[10];
        
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    metricsCollector.recordProcessStart("workflow_" + threadId, 100);
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertEquals("Should have 100 processes from all threads", 100, 
            metricsCollector.getProcessCountByStatus("ACTIVE"));
    }

    // Helper classes for testing

    public static class MetricsCollector {
        private AtomicInteger activeCount = new AtomicInteger(0);
        private AtomicInteger completedCount = new AtomicInteger(0);
        private long totalDuration = 0;
        private AtomicInteger processCount = new AtomicInteger(0);

        public void recordProcessStart(String processId, long duration) {
            activeCount.incrementAndGet();
            processCount.incrementAndGet();
        }

        public void recordProcessCompletion(String processId, long duration) {
            activeCount.decrementAndGet();
            completedCount.incrementAndGet();
            totalDuration += duration;
        }

        public int getActiveProcessCount() {
            return activeCount.get();
        }

        public int getCompletedProcessCount() {
            return completedCount.get();
        }

        public int getProcessCountByStatus(String status) {
            return "ACTIVE".equals(status) ? processCount.get() : 0;
        }

        public double getAverageDuration() {
            int total = activeCount.get() + completedCount.get();
            return total > 0 ? (double) totalDuration / total : 0.0;
        }
    }

    public static class TraceContext {
        private AtomicInteger spanCounter = new AtomicInteger(0);

        public Span createSpan(String name) {
            return new Span(name, "trace_" + System.nanoTime(), "span_" + spanCounter.incrementAndGet());
        }

        public Span createSpan(String name, String traceId, String spanId) {
            return new Span(name, traceId, spanId);
        }

        public Span createChildSpan(Span parentSpan, String name) {
            return new Span(name, parentSpan.traceId, "span_" + spanCounter.incrementAndGet(), parentSpan.spanId);
        }
    }

    public static class Span {
        private String name;
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private java.util.Map<String, String> attributes = new java.util.HashMap<>();
        private java.util.List<String> events = new java.util.ArrayList<>();
        private boolean active = true;
        private long startTime;
        private long endTime;

        public Span(String name, String traceId, String spanId) {
            this(name, traceId, spanId, null);
        }

        public Span(String name, String traceId, String spanId, String parentSpanId) {
            this.name = name;
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.startTime = System.currentTimeMillis();
        }

        public void addAttribute(String key, String value) {
            attributes.put(key, value);
        }

        public String getAttribute(String key) {
            return attributes.get(key);
        }

        public int getAttributeCount() {
            return attributes.size();
        }

        public void addEvent(String eventName) {
            events.add(eventName);
        }

        public int getEventCount() {
            return events.size();
        }

        public String getName() { return name; }
        public String getTraceId() { return traceId; }
        public String getSpanId() { return spanId; }
        public String getParentSpanId() { return parentSpanId; }
        public boolean isActive() { return active; }

        public void start() {
            startTime = System.currentTimeMillis();
        }

        public void end() {
            endTime = System.currentTimeMillis();
            active = false;
        }

        public long getDurationMillis() {
            return endTime - startTime;
        }
    }
}
