package org.yawlfoundation.yawl.graalpy.load;

/**
 * Performance targets and constants for load testing
 */
public final class PerformanceTargets {
    
    // Performance targets
    public static final double MAX_PERFORMANCE_DEGRADATION = 10.0; // 10x performance hit
    public static final double MIN_PARALLEL_SPEEDUP = 0.70; // 70% parallel speedup
    public static final long MAX_ACCEPTABLE_MEMORY_GROWTH_MB = 100; // 100MB under load
    
    // Test configuration
    public static final int MAX_CONCURRENT_WORKFLOWS = 100;
    public static final int WARMUP_ITERATIONS = 10;
    public static final int MEASUREMENT_ITERATIONS = 100;
    public static final int STRESS_TEST_MULTIPLIER = 2;
    
    // Latency targets (milliseconds)
    public static final long TARGET_P50_LATENCY_MS = 50;
    public static final long TARGET_P95_LATENCY_MS = 200;
    public static final long TARGET_P99_LATENCY_MS = 500;
    
    // Throughput targets
    public static final long MIN_THROUGHPUT_OPS_PER_SEC = 100;
    
    // Memory thresholds
    public static final long MAX_HEAP_USAGE_PERCENT = 80;
    public static final long MAX_GC_TIME_PERCENT = 5;
    
    private PerformanceTargets() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Checks if the performance degradation is acceptable
     */
    public static boolean isPerformanceDegradationAcceptable(double degradation) {
        return degradation <= MAX_PERFORMANCE_DEGRADATION;
    }
    
    /**
     * Checks if parallel speedup meets targets
     */
    public static boolean isParallelSpeedupAcceptable(double speedup) {
        return speedup >= MIN_PARALLEL_SPEEDUP;
    }
    
    /**
     * Checks if memory growth is acceptable
     */
    public static boolean isMemoryGrowthAcceptable(long growthMb) {
        return growthMb <= MAX_ACCEPTABLE_MEMORY_GROWTH_MB;
    }
}
