package org.yawlfoundation.yawl.graalpy.load;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Monitors memory usage during load testing
 */
public class MemoryMonitor {
    
    private final MemoryMXBean memoryBean;
    private final AtomicLong lastHeapUsed = new AtomicLong(0);
    private final AtomicLong maxHeapUsed = new AtomicLong(0);
    private final AtomicInteger gcCount = new AtomicInteger(0);
    private final AtomicLong gcTime = new AtomicLong(0);
    
    private long startTime;
    private volatile boolean monitoring = false;
    
    public MemoryMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
    }
    
    /**
     * Starts memory monitoring
     */
    public void startMonitoring() {
        this.startTime = System.currentTimeMillis();
        this.monitoring = true;
        
        // Take initial snapshot
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        lastHeapUsed.set(heapUsage.getUsed());
        maxHeapUsed.set(heapUsage.getUsed());
        gcCount.set(0);
        gcTime.set(0);
    }
    
    /**
     * Stops memory monitoring
     */
    public void stopMonitoring() {
        this.monitoring = false;
    }
    
    /**
     * Takes a memory snapshot
     */
    public MemorySnapshot takeSnapshot() {
        if (!monitoring) {
            throw new IllegalStateException("Memory monitoring is not active");
        }
        
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long committed = heapUsage.getCommitted();
        long max = heapUsage.getMax();
        
        // Update max if current usage is higher
        if (used > maxHeapUsed.get()) {
            maxHeapUsed.set(used);
        }
        
        // Update GC statistics
        gcCount.set(memoryBean.getGarbageCollectorMXBeans().stream()
            .mapToInt(bean -> bean.getCollectionCount())
            .sum());
        
        gcTime.set(memoryBean.getGarbageCollectorMXBeans().stream()
            .mapToLong(bean -> bean.getCollectionTime())
            .sum());
        
        return new MemorySnapshot(used, committed, max, gcCount.get(), gcTime.get());
    }
    
    /**
     * Gets the maximum heap usage observed
     */
    public long getMaxHeapUsed() {
        return maxHeapUsed.get();
    }
    
    /**
     * Calculates memory growth since start
     */
    public long calculateMemoryGrowth() {
        return maxHeapUsed.get() - lastHeapUsed.get();
    }
    
    /**
     * Calculates memory usage percentage
     */
    public double calculateMemoryUsagePercent() {
        long max = memoryBean.getHeapMemoryUsage().getMax();
        if (max <= 0) return 0.0;
        
        return ((double) maxHeapUsed.get() / max) * 100;
    }
    
    /**
     * Calculates GC time percentage
     */
    public double calculateGCTimePercent() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed <= 0) return 0.0;
        
        return ((double) gcTime.get() / elapsed) * 100;
    }
    
    /**
     * Checks if memory usage is within acceptable limits
     */
    public boolean isMemoryUsageAcceptable() {
        double usagePercent = calculateMemoryUsagePercent();
        return usagePercent <= PerformanceTargets.MAX_HEAP_USAGE_PERCENT;
    }
    
    /**
     * Checks if GC time is within acceptable limits
     */
    public boolean isGCTimeAcceptable() {
        double gcPercent = calculateGCTimePercent();
        return gcPercent <= PerformanceTargets.MAX_GC_TIME_PERCENT;
    }
    
    /**
     * Memory snapshot data class
     */
    public static class MemorySnapshot {
        private final long usedBytes;
        private final long committedBytes;
        private final long maxBytes;
        private final long gcCount;
        private final long gcTimeMs;
        
        public MemorySnapshot(long usedBytes, long committedBytes, long maxBytes, 
                            long gcCount, long gcTimeMs) {
            this.usedBytes = usedBytes;
            this.committedBytes = committedBytes;
            this.maxBytes = maxBytes;
            this.gcCount = gcCount;
            this.gcTimeMs = gcTimeMs;
        }
        
        public long getUsedBytes() { return usedBytes; }
        public long getCommittedBytes() { return committedBytes; }
        public long getMaxBytes() { return maxBytes; }
        public long getGcCount() { return gcCount; }
        public long getGcTimeMs() { return gcTimeMs; }
        
        /**
         * Converts memory usage to megabytes
         */
        public long getUsedMB() { return usedBytes / (1024 * 1024); }
        public long getCommittedMB() { return committedBytes / (1024 * 1024); }
        public long getMaxMB() { return maxBytes / (1024 * 1024); }
    }
}
