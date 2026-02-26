package org.yawlfoundation.yawl.graalpy.load;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Collects and aggregates performance metrics during load testing
 */
public class MetricsCollector {
    
    private final List<Long> executionTimes = new CopyOnWriteArrayList<>();
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicInteger successfulOperations = new AtomicInteger(0);
    private final AtomicInteger failedOperations = new AtomicInteger(0);
    private final AtomicLongArray memorySnapshots;
    
    public MetricsCollector(int snapshotCount) {
        this.memorySnapshots = new AtomicLongArray(snapshotCount);
    }
    
    /**
     * Records an execution time
     */
    public void recordExecutionTime(long durationMs) {
        executionTimes.add(durationMs);
    }
    
    /**
     * Records a successful operation
     */
    public void recordSuccess() {
        successfulOperations.incrementAndGet();
        totalOperations.incrementAndGet();
    }
    
    /**
     * Records a failed operation
     */
    public void recordFailure() {
        failedOperations.incrementAndGet();
        totalOperations.incrementAndGet();
    }
    
    /**
     * Records a memory snapshot
     */
    public void recordMemorySnapshot(int index, long bytesUsed) {
        if (index >= 0 && index < memorySnapshots.length()) {
            memorySnapshots.set(index, bytesUsed);
        }
    }
    
    /**
     * Calculates P50 latency
     */
    public long calculateP50Latency() {
        if (executionTimes.isEmpty()) return 0;
        return calculatePercentile(50.0);
    }
    
    /**
     * Calculates P95 latency
     */
    public long calculateP95Latency() {
        if (executionTimes.isEmpty()) return 0;
        return calculatePercentile(95.0);
    }
    
    /**
     * Calculates P99 latency
     */
    public long calculateP99Latency() {
        if (executionTimes.isEmpty()) return 0;
        return calculatePercentile(99.0);
    }
    
    /**
     * Calculates average latency
     */
    public double calculateAverageLatency() {
        if (executionTimes.isEmpty()) return 0.0;
        
        long sum = 0;
        for (long time : executionTimes) {
            sum += time;
        }
        return (double) sum / executionTimes.size();
    }
    
    /**
     * Calculates throughput (operations per second)
     */
    public double calculateThroughput(long durationMs) {
        if (durationMs <= 0) return 0.0;
        return (totalOperations.get() * 1000.0) / durationMs;
    }
    
    /**
     * Calculates success rate
     */
    public double calculateSuccessRate() {
        if (totalOperations.get() == 0) return 0.0;
        return (double) successfulOperations.get() / totalOperations.get();
    }
    
    /**
     * Calculates memory growth (bytes)
     */
    public long calculateMemoryGrowth() {
        if (memorySnapshots.length() < 2) return 0;
        
        long first = memorySnapshots.get(0);
        long last = memorySnapshots.get(memorySnapshots.length() - 1);
        return last - first;
    }
    
    /**
     * Gets maximum memory usage
     */
    public long getMaximumMemoryUsage() {
        long max = 0;
        for (int i = 0; i < memorySnapshots.length(); i++) {
            long value = memorySnapshots.get(i);
            if (value > max) max = value;
        }
        return max;
    }
    
    /**
     * Gets total operations
     */
    public long getTotalOperations() {
        return totalOperations.get();
    }
    
    /**
     * Gets failed operations count
     */
    public int getFailedOperations() {
        return failedOperations.get();
    }
    
    /**
     * Gets successful operations count
     */
    public int getSuccessfulOperations() {
        return successfulOperations.get();
    }
    
    /**
     * Resets all metrics
     */
    public void reset() {
        executionTimes.clear();
        totalOperations.set(0);
        successfulOperations.set(0);
        failedOperations.set(0);
        for (int i = 0; i < memorySnapshots.length(); i++) {
            memorySnapshots.set(i, 0);
        }
    }
    
    /**
     * Helper method to calculate percentile
     */
    private long calculatePercentile(double percentile) {
        if (executionTimes.isEmpty()) return 0;
        
        List<Long> sorted = new CopyOnWriteArrayList<>(executionTimes);
        sorted.sort(Long::compareTo);
        
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        
        return sorted.get(index);
    }
}
