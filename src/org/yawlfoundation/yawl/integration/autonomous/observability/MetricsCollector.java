package org.yawlfoundation.yawl.integration.autonomous.observability;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Simple metrics collection system for YAWL autonomous operations.
 * Provides Prometheus-style text format metrics via HTTP endpoint.
 *
 * Thread-safe for concurrent metric recording.
 *
 * @author YAWL Production Validator
 * @version 5.2
 */
public class MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    private final ConcurrentHashMap<String, Counter> counters;
    private final ConcurrentHashMap<String, Histogram> histograms;
    private final HttpServer server;

    /**
     * Create metrics collector without HTTP server.
     */
    public MetricsCollector() {
        this.counters = new ConcurrentHashMap<>();
        this.histograms = new ConcurrentHashMap<>();
        this.server = null;
    }

    /**
     * Create metrics collector with HTTP endpoint.
     *
     * @param port Port for metrics HTTP server
     * @throws IOException If server cannot be started
     */
    public MetricsCollector(int port) throws IOException {
        this.counters = new ConcurrentHashMap<>();
        this.histograms = new ConcurrentHashMap<>();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/metrics", new MetricsHandler());
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        logger.info("Metrics collector started on port {}", port);
    }

    /**
     * Increment counter with labels.
     *
     * @param name Metric name (e.g., "tasks_completed_total")
     * @param labels Label map (e.g., {"agent": "ordering", "domain": "Ordering"})
     */
    public void incrementCounter(String name, Map<String, String> labels) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Metric name cannot be null or empty");
        }

        String key = buildKey(name, labels);
        counters.computeIfAbsent(key, k -> new Counter(name, labels)).increment();
    }

    /**
     * Increment counter without labels.
     *
     * @param name Metric name
     */
    public void incrementCounter(String name) {
        incrementCounter(name, new HashMap<>());
    }

    /**
     * Record duration measurement in milliseconds.
     *
     * @param name Metric name (e.g., "task_completion_duration_seconds")
     * @param durationMs Duration in milliseconds
     * @param labels Label map (optional)
     */
    public void recordDuration(String name, long durationMs, Map<String, String> labels) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Metric name cannot be null or empty");
        }

        String key = buildKey(name, labels);
        histograms.computeIfAbsent(key, k -> new Histogram(name, labels))
                  .record(durationMs);
    }

    /**
     * Record duration without labels.
     *
     * @param name Metric name
     * @param durationMs Duration in milliseconds
     */
    public void recordDuration(String name, long durationMs) {
        recordDuration(name, durationMs, new HashMap<>());
    }

    /**
     * Get counter value.
     *
     * @param name Metric name
     * @param labels Label map
     * @return Counter value or 0 if not found
     */
    public long getCounterValue(String name, Map<String, String> labels) {
        String key = buildKey(name, labels);
        Counter counter = counters.get(key);
        return counter != null ? counter.getValue() : 0;
    }

    /**
     * Get histogram sum.
     *
     * @param name Metric name
     * @param labels Label map
     * @return Sum of all recorded values in seconds (converted from ms)
     */
    public double getHistogramSum(String name, Map<String, String> labels) {
        String key = buildKey(name, labels);
        Histogram histogram = histograms.get(key);
        return histogram != null ? histogram.getSum() : 0.0;
    }

    /**
     * Get histogram count.
     *
     * @param name Metric name
     * @param labels Label map
     * @return Number of recorded observations
     */
    public long getHistogramCount(String name, Map<String, String> labels) {
        String key = buildKey(name, labels);
        Histogram histogram = histograms.get(key);
        return histogram != null ? histogram.getCount() : 0;
    }

    /**
     * Build unique key for metric with labels.
     */
    private String buildKey(String name, Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return name;
        }

        StringBuilder key = new StringBuilder(name);
        key.append("{");

        List<String> labelPairs = new ArrayList<>();
        labels.forEach((k, v) -> labelPairs.add(k + "=" + v));
        labelPairs.sort(String::compareTo);

        key.append(String.join(",", labelPairs));
        key.append("}");

        return key.toString();
    }

    /**
     * Format labels for Prometheus text format.
     * Returns empty string (no braces) when no labels present, per Prometheus spec.
     * Returns {label1="value1",label2="value2"} format when labels present.
     */
    private String formatLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            // Prometheus text format: metrics without labels have no braces
            // Example: "metric_name 42" not "metric_name{} 42"
            return new String();
        }

        StringBuilder formatted = new StringBuilder("{");

        List<String> labelPairs = new ArrayList<>();
        labels.forEach((k, v) -> labelPairs.add(k + "=\"" + v + "\""));
        labelPairs.sort(String::compareTo);

        formatted.append(String.join(",", labelPairs));
        formatted.append("}");

        return formatted.toString();
    }

    /**
     * Generate Prometheus-style text format metrics.
     *
     * @return Metrics in text format
     */
    public String exportMetrics() {
        StringBuilder output = new StringBuilder();

        counters.values().forEach(counter -> {
            String labels = formatLabels(counter.labels);
            output.append(counter.name)
                  .append(labels)
                  .append(" ")
                  .append(counter.getValue())
                  .append("\n");
        });

        histograms.values().forEach(histogram -> {
            String labels = formatLabels(histogram.labels);

            output.append(histogram.name)
                  .append("_sum")
                  .append(labels)
                  .append(" ")
                  .append(String.format("%.3f", histogram.getSum()))
                  .append("\n");

            output.append(histogram.name)
                  .append("_count")
                  .append(labels)
                  .append(" ")
                  .append(histogram.getCount())
                  .append("\n");
        });

        return output.toString();
    }

    /**
     * Shutdown HTTP server if running.
     */
    public void shutdown() {
        if (server != null) {
            server.stop(0);
            logger.info("Metrics collector HTTP server stopped");
        }
    }

    /**
     * HTTP handler for /metrics endpoint.
     */
    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String response = exportMetrics();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Thread-safe counter.
     */
    private static class Counter {
        private final String name;
        private final Map<String, String> labels;
        private final AtomicLong value;

        Counter(String name, Map<String, String> labels) {
            this.name = name;
            this.labels = labels != null ? new HashMap<>(labels) : new HashMap<>();
            this.value = new AtomicLong(0);
        }

        void increment() {
            value.incrementAndGet();
        }

        long getValue() {
            return value.get();
        }
    }

    /**
     * Thread-safe histogram for duration measurements.
     */
    private static class Histogram {
        private final String name;
        private final Map<String, String> labels;
        private final DoubleAdder sum;
        private final AtomicLong count;

        Histogram(String name, Map<String, String> labels) {
            this.name = name;
            this.labels = labels != null ? new HashMap<>(labels) : new HashMap<>();
            this.sum = new DoubleAdder();
            this.count = new AtomicLong(0);
        }

        void record(long durationMs) {
            sum.add(durationMs / 1000.0);
            count.incrementAndGet();
        }

        double getSum() {
            return sum.sum();
        }

        long getCount() {
            return count.get();
        }
    }
}
