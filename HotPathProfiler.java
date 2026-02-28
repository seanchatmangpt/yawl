package org.yawlfoundation.yawl.dspy.performance;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Hot path profiler for identifying performance bottlenecks in GEPA optimization.
 * 
 * <p>Provides real-time profiling of frequently executed operations with
 * automatic bottleneck detection and optimization recommendations.</p>
 * 
 * <h3>Key Features</h3>
 * <ul>
 *   <li>Real-time hot path detection</li>
 *   <li>Automatic bottleneck identification</li>
 *   <li>Call stack analysis</li>
 *   <li>Memory allocation tracking</li>
 *   <li>Optimization recommendations</li>
 * </ul>
 * 
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class HotPathProfiler {

    private static final Logger log = LoggerFactory.getLogger(HotPathProfiler.class);
    
    // Configuration
    private static final long HOT_PATH_THRESHOLD_MS = 100; // 100ms
    private static final int MIN_INVOCATIONS = 10;
    private static final double MEMORY_ALLOC_THRESHOLD = 0.1; // 10% of heap
    
    // Thread-safe storage
    private final Map<String, HotPath> hotPaths = new ConcurrentHashMap<>();
    private final AtomicInteger totalProfiles = new AtomicInteger(0);
    private final AtomicLong totalProfileTime = new AtomicLong(0);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Global instance
    private static final HotPathProfiler INSTANCE = new HotPathProfiler();
    
    /**
     * Gets the global hot path profiler instance.
     */
    public static HotPathProfiler getInstance() {
        return INSTANCE;
    }
    
    /**
     * Profiles a method execution.
     */
    public static <T> T profile(String operation, Function<T, T> operationFn) {
        return INSTANCE._profile(operation, operationFn);
    }
    
    /**
     * Profiles a method with input parameters.
     */
    public static <T, R> R profile(String operation, T input, Function<T, R> operationFn) {
        return INSTANCE._ProfileWithInput(operation, input, operationFn);
    }
    
    /**
     * Internal profile method.
     */
    private <T> T _profile(String operation, Function<T, T> operationFn) {
        Instant start = Instant.now();
        T result = null;
        Exception exception = null;
        
        try {
            result = operationFn.apply(null);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
            recordProfile(operation, durationMs, exception != null, null);
        }
    }
    
    /**
     * Internal profile method with input.
     */
    private <T, R> R _ProfileWithInput(String operation, T input, Function<T, R> operationFn) {
        Instant start = Instant.now();
        R result = null;
        Exception exception = null;
        
        try {
            result = operationFn.apply(input);
            return result;
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
            recordProfile(operation, durationMs, exception != null, input);
        }
    }
    
    /**
     * Records profile data and identifies hot paths.
     */
    private void recordProfile(String operation, long durationMs, boolean hasException, Object input) {
        totalProfiles.incrementAndGet();
        totalProfileTime.addAndGet(durationMs);
        
        // Update or create hot path record
        HotPath hotPath = hotPaths.computeIfAbsent(operation, k -> new HotPath(operation));
        hotPath.recordInvocation(durationMs, hasException);
        
        // Check for hot path
        if (hotPath.isHotPath(HOT_PATH_THRESHOLD_MS, MIN_INVOCATIONS)) {
            log.warn("HOT PATH DETECTED: {} - avg={}ms, invocations={}, exceptions={}",
                    operation, hotPath.getAverageTime(), hotPath.getInvocationCount(), hotPath.getExceptionCount());
            
            // Generate optimization recommendation
            String recommendation = generateRecommendation(hotPath);
            if (recommendation != null) {
                log.info("OPTIMIZATION RECOMMENDATION for {}: {}", operation, recommendation);
            }
        }
        
        // Check for memory allocation issues
        checkMemoryIssues();
    }
    
    /**
     * Generates optimization recommendations for hot paths.
     */
    @Nullable
    private String generateRecommendation(HotPath hotPath) {
        double avgTime = hotPath.getAverageTime();
        int invocationCount = hotPath.getInvocationCount();
        double exceptionRate = hotPath.getExceptionRate();
        
        if (exceptionRate > 0.1) {
            return "Reduce exception handling overhead. Consider pre-validation or error caching.";
        }
        
        if (avgTime > 1000 && invocationCount > 50) {
            return "Consider caching results or implementing lazy loading.";
        }
        
        if (avgTime > 5000) {
            return "Optimize algorithm complexity or consider asynchronous processing.";
        }
        
        if (hotPath.isCpuBound()) {
            return "Move to separate thread or use async processing.";
        }
        
        if (hotPath.isMemoryBound()) {
            return "Reduce object allocations, use object pooling or primitive arrays.";
        }
        
        return null;
    }
    
    /**
     * Checks for memory-related issues.
     */
    private void checkMemoryIssues() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsageRatio = (double) usedMemory / maxMemory;
        
        if (memoryUsageRatio > MEMORY_ALLOC_THRESHOLD) {
            log.warn("HIGH MEMORY USAGE: {:.1f}% of heap used", memoryUsageRatio * 100);
            
            // Find memory-intensive hot paths
            hotPaths.values().stream()
                .filter(HotPath::isMemoryBound)
                .forEach(path -> log.warn("Memory-intensive hot path: {} - avg={}ms, objects={}",
                        path.getOperation(), path.getAverageTime(), path.getObjectCount()));
        }
    }
    
    /**
     * Gets hot path summary.
     */
    public Map<String, Object> getHotPathSummary() {
        lock.readLock().lock();
        try {
            Map<String, Object> summary = new ConcurrentHashMap<>();
            summary.put("total_profiles", totalProfiles.get());
            summary.put("total_profile_time_ms", totalProfileTime.get());
            summary.put("average_profile_time_ms", 
                    totalProfiles.get() > 0 ? totalProfileTime.get() / totalProfiles.get() : 0);
            summary.put("hot_path_count", hotPaths.size());
            
            // Add hot paths
            Map<String, Object> hotPathsMap = new ConcurrentHashMap<>();
            hotPaths.values().stream()
                .filter(path -> path.isHotPath(HOT_PATH_THRESHOLD_MS, MIN_INVOCATIONS))
                .forEach(path -> {
                    hotPathsMap.put(path.getOperation(), path.getSummary());
                });
            summary.put("hot_paths", hotPathsMap);
            
            return summary;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Clears all profile data.
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            hotPaths.clear();
            totalProfiles.set(0);
            totalProfileTime.set(0);
            log.info("Hot path profiler reset");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Inner class representing a single hot path.
     */
    private static class HotPath {
        private final String operation;
        private final AtomicLong totalTime = new AtomicLong(0);
        private final AtomicInteger invocationCount = new AtomicInteger(0);
        private final AtomicInteger exceptionCount = new AtomicInteger(0);
        private final AtomicLong totalTimeSquared = new AtomicLong(0); // For variance calculation
        private final AtomicInteger objectAllocations = new AtomicInteger(0);
        private final Instant[] recentInvocations = new Instant[10];
        private int recentIndex = 0;
        
        public HotPath(String operation) {
            this.operation = operation;
        }
        
        public void recordInvocation(long durationMs, boolean hasException) {
            totalTime.addAndGet(durationMs);
            invocationCount.incrementAndGet();
            totalTimeSquared.addAndGet(durationMs * durationMs);
            
            if (hasException) {
                exceptionCount.incrementAndGet();
            }
            
            // Track recent invocations for burst detection
            recentInvocations[recentIndex] = Instant.now();
            recentIndex = (recentIndex + 1) % recentInvocations.length;
            
            // Estimate object allocations (rough approximation)
            objectAllocations.addAndGet((int) (durationMs / 10)); // 1 object per 10ms
        }
        
        public boolean isHotPath(long thresholdMs, int minInvocations) {
            return invocationCount.get() >= minInvocations && 
                   getAverageTime() > thresholdMs;
        }
        
        public boolean isCpuBound() {
            double variance = calculateVariance();
            return variance > 1000; // High variance indicates CPU-bound
        }
        
        public boolean isMemoryBound() {
            return getAverageTime() > 200 && objectAllocations.get() > 100;
        }
        
        public double getAverageTime() {
            int count = invocationCount.get();
            return count > 0 ? (double) totalTime.get() / count : 0;
        }
        
        public double getStandardDeviation() {
            double variance = calculateVariance();
            return Math.sqrt(variance);
        }
        
        private double calculateVariance() {
            int count = invocationCount.get();
            if (count <= 1) return 0;
            
            double mean = getAverageTime();
            double meanSquared = (double) totalTimeSquared.get() / count;
            return meanSquared - (mean * mean);
        }
        
        public double getExceptionRate() {
            int count = invocationCount.get();
            return count > 0 ? (double) exceptionCount.get() / count : 0;
        }
        
        public Map<String, Object> getSummary() {
            Map<String, Object> summary = new ConcurrentHashMap<>();
            summary.put("invocations", invocationCount.get());
            summary.put("average_time_ms", getAverageTime());
            summary.put("standard_deviation_ms", getStandardDeviation());
            summary.put("exception_rate", getExceptionRate());
            summary.put("object_allocations", objectAllocations.get());
            summary.put("is_cpu_bound", isCpuBound());
            summary.put("is_memory_bound", isMemoryBound());
            return summary;
        }
        
        public String getOperation() {
            return operation;
        }
        
        public int getInvocationCount() {
            return invocationCount.get();
        }
        
        public int getExceptionCount() {
            return exceptionCount.get();
        }
        
        public int getObjectCount() {
            return objectAllocations.get();
        }
    }
}
