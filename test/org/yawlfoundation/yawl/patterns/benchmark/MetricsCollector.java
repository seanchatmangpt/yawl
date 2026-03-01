package org.yawlfoundation.yawl.patterns.benchmark;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Collects and aggregates performance metrics from benchmarks and stress tests.
 * Computes throughput, latency percentiles, and memory statistics.
 */
public class MetricsCollector {
    private final List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> throughputs = Collections.synchronizedList(new ArrayList<>());
    private final String testName;
    private final Instant startTime;
    private long operations = 0;

    public MetricsCollector(String testName) {
        this.testName = testName;
        this.startTime = Instant.now();
    }

    /**
     * Record operation latency in milliseconds
     */
    public void recordLatency(double ms) {
        latencies.add(ms);
    }

    /**
     * Record throughput measurement (ops/sec)
     */
    public void recordThroughput(long opsPerSec) {
        throughputs.add(opsPerSec);
    }

    /**
     * Increment operation count
     */
    public void incrementOperations(long count) {
        operations += count;
    }

    /**
     * Get p50 latency (median)
     */
    public double getP50Latency() {
        if (latencies.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    /**
     * Get p99 latency (99th percentile)
     */
    public double getP99Latency() {
        if (latencies.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int index = (int) (sorted.size() * 0.99);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    /**
     * Get p99.9 latency (99.9th percentile)
     */
    public double getP999Latency() {
        if (latencies.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int index = (int) (sorted.size() * 0.999);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    /**
     * Get max latency
     */
    public double getMaxLatency() {
        if (latencies.isEmpty()) return 0;
        return Collections.max(latencies);
    }

    /**
     * Get min latency
     */
    public double getMinLatency() {
        if (latencies.isEmpty()) return 0;
        return Collections.min(latencies);
    }

    /**
     * Get average latency
     */
    public double getAvgLatency() {
        if (latencies.isEmpty()) return 0;
        return latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /**
     * Get average throughput (ops/sec)
     */
    public long getAvgThroughput() {
        if (throughputs.isEmpty()) return 0;
        return (long) throughputs.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    /**
     * Get total operations executed
     */
    public long getTotalOperations() {
        return operations;
    }

    /**
     * Get test duration in seconds
     */
    public long getDurationSeconds() {
        return Math.max(1, (System.currentTimeMillis() - startTime.toEpochMilli()) / 1000);
    }

    /**
     * Generate summary report
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== METRICS REPORT: ").append(testName).append(" ===\n");
        sb.append(String.format("Duration: %d seconds\n", getDurationSeconds()));
        sb.append(String.format("Operations: %d\n", getTotalOperations()));
        sb.append(String.format("Avg Throughput: %d ops/sec\n", getAvgThroughput()));
        sb.append(String.format("Latency (ms): min=%.2f, avg=%.2f, p50=%.2f, p99=%.2f, p999=%.2f, max=%.2f\n",
            getMinLatency(), getAvgLatency(), getP50Latency(), getP99Latency(), getP999Latency(), getMaxLatency()));
        sb.append("=============================================\n");
        return sb.toString();
    }

    /**
     * Print report to stdout
     */
    public void printReport() {
        System.out.println(generateReport());
    }
}
