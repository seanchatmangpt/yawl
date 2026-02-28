package org.yawlfoundation.yawl.dspy.performance;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark suite for GEPA optimization latency.
 * 
 * <p>Provides comprehensive benchmarking of optimization operations with
 * target of <5 seconds for typical workflows.</p>
 * 
 * <h3>Benchmark Scenarios</h3>
 * <ul>
 *   <li>Single optimization run</li>
 *   <li>Concurrent optimization runs</li>
 *   <li>Caching effectiveness</li>
 *   <li>Memory usage patterns</li>
 *   <li>Hot path performance</li>
 * </ul>
 * 
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class GepaBenchmark {

    private static final Logger log = LoggerFactory.getLogger(GepaBenchmark.class);
    
    // Target thresholds
    private static final long TARGET_SINGLE_OPTIMIZATION_MS = 5000; // 5 seconds
    private static final long TARGET_CONCURRENT_OPTIMIZATION_MS = 3000; // 3 seconds
    private static final long TARGET_CACHE_HIT_MS = 10; // 10ms
    private static final int TARGET_CONCURRENT_USERS = 100; // 100 concurrent users
    
    // Benchmark configuration
    private final int warmupIterations;
    private final int benchmarkIterations;
    private final int threadCount;
    
    // Results
    private final Map<String, BenchmarkResult> results = new ConcurrentHashMap<>();
    private final AtomicInteger totalRuns = new AtomicInteger(0);
    private final AtomicLong totalTimeMs = new AtomicLong(0);
    
    /**
     * Creates a new benchmark with default settings.
     */
    public GepaBenchmark() {
        this(5, 20, 4); // 5 warmup, 20 benchmark, 4 threads
    }
    
    /**
     * Creates a new benchmark with custom settings.
     */
    public GepaBenchmark(int warmupIterations, int benchmarkIterations, int threadCount) {
        this.warmupIterations = warmupIterations;
        this.benchmarkIterations = benchmarkIterations;
        this.threadCount = threadCount;
    }
    
    /**
     * Runs single optimization benchmark.
     */
    public BenchmarkResult runSingleOptimizationBenchmark(Runnable optimizationTask) {
        String benchmarkId = "single_optimization_" + System.currentTimeMillis();
        BenchmarkResult result = new BenchmarkResult(benchmarkId);
        
        // Warmup
        log.info("Running warmup for single optimization benchmark...");
        for (int i = 0; i < warmupIterations; i++) {
            long start = System.currentTimeMillis();
            optimizationTask.run();
            long duration = System.currentTimeMillis() - start;
            result.recordWarmup(duration);
        }
        
        // Benchmark
        log.info("Running single optimization benchmark...");
        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < benchmarkIterations; i++) {
            long start = System.currentTimeMillis();
            optimizationTask.run();
            long duration = System.currentTimeMillis() - start;
            durations.add(duration);
            result.recordRun(duration);
        }
        
        // Calculate statistics
        result.calculateStatistics();
        results.put(benchmarkId, result);
        
        // Check against targets
        checkSingleOptimizationTarget(result);
        
        log.info("Single optimization benchmark completed: avg={}ms, p95={}ms", 
                result.getAverageTimeMs(), result.getP95TimeMs());
        
        return result;
    }
    
    /**
     * Runs concurrent optimization benchmark.
     */
    public BenchmarkResult runConcurrentOptimizationBenchmark(Runnable optimizationTask, int concurrentUsers) {
        String benchmarkId = "concurrent_optimization_" + System.currentTimeMillis();
        BenchmarkResult result = new BenchmarkResult(benchmarkId);
        
        // Warmup
        log.info("Running warmup for concurrent optimization benchmark...");
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < warmupIterations; i++) {
                executor.submit(() -> {
                    long start = System.currentTimeMillis();
                    optimizationTask.run();
                    long duration = System.currentTimeMillis() - start;
                    result.recordWarmup(duration);
                    return null;
                });
            }
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Warmup interrupted", e);
        }
        
        // Benchmark
        log.info("Running concurrent optimization benchmark with {} users...", concurrentUsers);
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Long> durations = new ArrayList<>();
            
            for (int i = 0; i < benchmarkIterations; i++) {
                final int iteration = i;
                executor.submit(() -> {
                    long start = System.currentTimeMillis();
                    optimizationTask.run();
                    long duration = System.currentTimeMillis() - start;
                    synchronized (durations) {
                        durations.add(duration);
                        result.recordRun(duration);
                    }
                    return null;
                });
            }
            
            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Benchmark interrupted", e);
        }
        
        // Calculate statistics
        result.calculateStatistics();
        results.put(benchmarkId, result);
        
        // Check against targets
        checkConcurrentOptimizationTarget(result, concurrentUsers);
        
        log.info("Concurrent optimization benchmark completed: avg={}ms, p95={}ms", 
                result.getAverageTimeMs(), result.getP95TimeMs());
        
        return result;
    }
    
    /**
     * Runs caching effectiveness benchmark.
     */
    public BenchmarkResult runCacheBenchmark(CacheBenchmarkTask cacheTask) {
        String benchmarkId = "cache_effectiveness_" + System.currentTimeMillis();
        BenchmarkResult result = new BenchmarkResult(benchmarkId);
        
        // Warmup cache
        log.info("Warming up cache...");
        for (int i = 0; i < warmupIterations; i++) {
            cacheTask.warmupCache();
        }
        
        // Benchmark cache hits and misses
        log.info("Running cache benchmark...");
        List<Long> hitDurations = new ArrayList<>();
        List<Long> missDurations = new ArrayList<>();
        
        for (int i = 0; i < benchmarkIterations; i++) {
            // Cache hit test
            long start = System.currentTimeMillis();
            Object resultObj = cacheTask.cacheHit();
            long duration = System.currentTimeMillis() - start;
            hitDurations.add(duration);
            result.recordRun(duration);
            
            // Cache miss test
            start = System.currentTimeMillis();
            resultObj = cacheTask.cacheMiss();
            duration = System.currentTimeMillis() - start;
            missDurations.add(duration);
            result.recordRun(duration);
        }
        
        // Calculate cache statistics
        double hitRate = (double) hitDurations.size() / (hitDurations.size() + missDurations.size());
        long avgHitTime = hitDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        long avgMissTime = missDurations.stream().mapToLong(Long::longValue).average().orElse(0);
        
        result.setCacheStatistics(hitRate, avgHitTime, avgMissTime);
        
        // Check against targets
        checkCacheTarget(result, hitRate, avgHitTime);
        
        log.info("Cache benchmark completed: hitRate={:.2f}%, avgHit={}ms, avgMiss={}ms", 
                hitRate * 100, avgHitTime, avgMissTime);
        
        return result;
    }
    
    /**
     * Gets summary of all benchmark results.
     */
    public Map<String, Object> getBenchmarkSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        summary.put("total_benchmarks", results.size());
        summary.put("total_runs", totalRuns.get());
        summary.put("total_time_ms", totalTimeMs.get());
        
        // Aggregate results
        List<BenchmarkResult> allResults = new ArrayList<>(results.values());
        if (!allResults.isEmpty()) {
            double avgTime = allResults.stream()
                .mapToDouble(BenchmarkResult::getAverageTimeMs)
                .average()
                .orElse(0);
            summary.put("average_time_ms", avgTime);
            
            long p95Time = calculateP95(allResults.stream()
                .mapToLong(BenchmarkResult::getAllDurations)
                .toArray());
            summary.put("p95_time_ms", p95Time);
        }
        
        // Target compliance
        int compliant = (int) allResults.stream()
            .filter(BenchmarkResult::isTargetCompliant)
            .count();
        summary.put("target_compliant", compliant);
        summary.put("target_compliance_rate", allResults.size() > 0 ? 
                (double) compliant / allResults.size() : 0);
        
        return summary;
    }
    
    /**
     * Checks single optimization target compliance.
     */
    private void checkSingleOptimizationTarget(BenchmarkResult result) {
        if (result.getP95TimeMs() > TARGET_SINGLE_OPTIMIZATION_MS) {
            log.warn("SINGLE OPTIMIZATION TARGET VIOLATED: p95={}ms (target={}ms)", 
                    result.getP95TimeMs(), TARGET_SINGLE_OPTIMIZATION_MS);
        } else {
            log.info("Single optimization target met: p95={}ms < {}ms", 
                    result.getP95TimeMs(), TARGET_SINGLE_OPTIMIZATION_MS);
        }
    }
    
    /**
     * Checks concurrent optimization target compliance.
     */
    private void checkConcurrentOptimizationTarget(BenchmarkResult result, int concurrentUsers) {
        if (result.getP95TimeMs() > TARGET_CONCURRENT_OPTIMIZATION_MS) {
            log.warn("CONCURRENT OPTIMIZATION TARGET VIOLATED: p95={}ms (target={}ms for {} users)", 
                    result.getP95TimeMs(), TARGET_CONCURRENT_OPTIMIZATION_MS, concurrentUsers);
        } else {
            log.info("Concurrent optimization target met: p95={}ms < {}ms for {} users", 
                    result.getP95TimeMs(), TARGET_CONCURRENT_OPTIMIZATION_MS, concurrentUsers);
        }
    }
    
    /**
     * Checks cache target compliance.
     */
    private void checkCacheTarget(BenchmarkResult result, double hitRate, long avgHitTime) {
        if (avgHitTime > TARGET_CACHE_HIT_MS) {
            log.warn("CACHE TARGET VIOLATED: avgHit={}ms (target={}ms)", 
                    avgHitTime, TARGET_CACHE_HIT_MS);
        } else {
            log.info("Cache target met: avgHit={}ms < {}ms", 
                    avgHitTime, TARGET_CACHE_HIT_MS);
        }
    }
    
    /**
     * Calculates p95 value from duration array.
     */
    private long calculateP95(long[] durations) {
        if (durations.length == 0) return 0;
        
        java.util.Arrays.sort(durations);
        int index = (int) (durations.length * 0.95);
        return durations[Math.min(index, durations.length - 1)];
    }
    
    /**
     * Functional interface for cache benchmark tasks.
     */
    @FunctionalInterface
    public interface CacheBenchmarkTask {
        void warmupCache();
        Object cacheHit();
        Object cacheMiss();
    }
    
    /**
     * Result container for benchmark runs.
     */
    public static class BenchmarkResult {
        private final String benchmarkId;
        private final List<Long> durations = new ArrayList<>();
        private final List<Long> warmupDurations = new ArrayList<>();
        private double hitRate;
        private long avgHitTime;
        private long avgMissTime;
        
        public BenchmarkResult(String benchmarkId) {
            this.benchmarkId = benchmarkId;
        }
        
        public void recordRun(long durationMs) {
            durations.add(durationMs);
        }
        
        public void recordWarmup(long durationMs) {
            warmupDurations.add(durationMs);
        }
        
        public void calculateStatistics() {
            if (durations.isEmpty()) return;
            
            // Basic statistics
            double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            long min = durations.stream().mapToLong(Long::longValue).min().orElse(0);
            long max = durations.stream().mapToLong(Long::longValue).max().orElse(0);
            
            // Calculate p95
            long[] sortedDurations = durations.stream().mapToLong(Long::longValue).toArray();
            java.util.Arrays.sort(sortedDurations);
            int p95Index = (int) (sortedDurations.length * 0.95);
            long p95 = sortedDurations[Math.min(p95Index, sortedDurations.length - 1)];
            
            // Set statistics
            this.avgTimeMs = (long) avg;
            this.minTimeMs = min;
            this.maxTimeMs = max;
            this.p95TimeMs = p95;
            this.targetCompliant = p95 <= TARGET_SINGLE_OPTIMIZATION_MS;
        }
        
        public void setCacheStatistics(double hitRate, long avgHitTime, long avgMissTime) {
            this.hitRate = hitRate;
            this.avgHitTime = avgHitTime;
            this.avgMissTime = avgMissTime;
        }
        
        // Getters
        public String getBenchmarkId() { return benchmarkId; }
        public List<Long> getAllDurations() { return durations; }
        public long getAverageTimeMs() { return avgTimeMs; }
        public long getMinTimeMs() { return minTimeMs; }
        public long getMaxTimeMs() { return maxTimeMs; }
        public long getP95TimeMs() { return p95TimeMs; }
        public boolean isTargetCompliant() { return targetCompliant; }
        public double getHitRate() { return hitRate; }
        public long getAvgHitTime() { return avgHitTime; }
        public long getAvgMissTime() { return avgMissTime; }
        
        // Statistics
        private long avgTimeMs;
        private long minTimeMs;
        private long maxTimeMs;
        private long p95TimeMs;
        private boolean targetCompliant;
    }
}
