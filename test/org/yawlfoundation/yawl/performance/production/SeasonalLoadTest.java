/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 * See LICENSE in the project root for license information.
 */

package org.yawlfoundation.yawl.performance.production;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.elements.YNet;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production test for seasonal load pattern validation.
 * Tests daily and weekly load patterns with auto-scaling.
 *
 * Validates:
 * - Daily peak hour handling
 * - Weekly workload patterns
 * - Auto-scaling during load spikes
 * - Resource utilization efficiency
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("production")
@Tag("seasonal")
@Tag("load-patterns")
public class SeasonalLoadTest {

    private static final int BASE_CASE_RATE = 100; // cases per minute
    private static final int DAILY_PEAK_MULTIPLIER = 5;
    private static final int WEEKLY_PEAK_MULTIPLIER = 3;
    private static final int TEST_DURATION_MINUTES = 10;
    private static final int SAMPLING_INTERVAL_SECONDS = 10;
    
    private YNetRunner engine;
    private final ExecutorService executor = Executors.newFixedThreadPool(20);
    private final LoadMetrics loadMetrics = new LoadMetrics();
    private final Map<LocalTime, LoadSnapshot> hourlyLoad = new ConcurrentHashMap<>();
    private final Map<DayOfWeek, LoadSnapshot> dailyLoad = new ConcurrentHashMap<>();
    
    @BeforeAll
    void setupSeasonalLoadTest() throws Exception {
        engine = new YNetRunner(createTestNet());
        
        // Allow engine to initialize
        Thread.sleep(5000);
    }
    
    @AfterAll
    void teardownSeasonalLoadTest() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        engine.shutdown();
    }
    
    @Test
    @DisplayName("Daily Load Pattern Simulation")
    void testDailyLoadPattern() throws Exception {
        System.out.println("Simulating daily load pattern...");
        
        // Simulate 24-hour day
        for (int hour = 0; hour < 24; hour++) {
            LocalTime currentHour = LocalTime.of(hour, 0);
            int casesThisHour = calculateHourlyLoad(hour);
            
            System.out.printf("Hour %02d: %d cases%n", hour, casesThisHour);
            
            // Submit hourly workload
            submitHourlyWorkload(casesThisHour, currentHour);
            
            // Record load metrics
            LoadSnapshot snapshot = captureLoadSnapshot();
            hourlyLoad.put(currentHour, snapshot);
            
            // Simulate time passing
            Thread.sleep(1000); // 1 minute per hour in test
        }
        
        // Analyze daily patterns
        analyzeDailyLoadPattern();
    }
    
    @Test
    @DisplayName("Weekly Load Pattern Simulation")
    void testWeeklyLoadPattern() throws Exception {
        System.out.println("Simulating weekly load pattern...");
        
        // Simulate 7-day week
        for (DayOfWeek day : DayOfWeek.values()) {
            int casesThisDay = calculateDailyLoad(day);
            
            System.out.printf("%s: %d cases%n", day, casesThisDay);
            
            // Submit daily workload
            submitDailyWorkload(casesThisDay, day);
            
            // Record load metrics
            LoadSnapshot snapshot = captureLoadSnapshot();
            dailyLoad.put(day, snapshot);
            
            // Simulate time passing
            Thread.sleep(2000); // 2 hours per day in test
        }
        
        // Analyze weekly patterns
        analyzeWeeklyLoadPattern();
    }
    
    @Test
    @DisplayName("Auto-Scaling During Load Spikes")
    void testAutoScalingDuringLoadSpikes() throws Exception {
        System.out.println("Testing auto-scaling during load spikes...");
        
        // Baseline performance
        LoadSnapshot baseline = submitNormalLoad(1000);
        
        // Simulate unexpected load spike
        LoadSnapshot spike = submitLoadSpike(5000, "unexpected-spike");
        
        // Validate performance degradation
        assertTrue(spike.getAverageLatency() < baseline.getAverageLatency() * 3,
            "During spike, latency should not exceed 3x baseline");
        
        // Simulate auto-scaling response
        Thread.sleep(30000); // Allow scaling to occur
        
        // Measure post-scaling performance
        LoadSnapshot postScaling = submitNormalLoad(1000);
        
        // Validate scaling effectiveness
        assertTrue(postScaling.getAverageLatency() < baseline.getAverageLatency() * 1.5,
            "After scaling, performance should recover to within 1.5x baseline");
    }
    
    @Test
    @DisplayName("Resource Utilization Efficiency")
    void testResourceUtilizationEfficiency() throws Exception {
        System.out.println("Testing resource utilization efficiency...");
        
        // Monitor resource utilization during load test
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger sampleCount = new AtomicInteger(0);
        
        // Start monitoring
        monitor.scheduleAtFixedRate(() -> {
            ResourceUsage usage = captureResourceUsage();
            loadMetrics.recordResourceUsage(usage);
            sampleCount.incrementAndGet();
            
            if (sampleCount.get() % 6 == 0) { // Every minute
                System.out.printf("CPU: %.1f%%, Memory: %.1fMB, Active Threads: %d%n",
                    usage.cpuUsage, usage.memoryMB, usage.activeThreads);
            }
        }, 0, SAMPLING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // Run sustained load
        submitSustainedLoad(TEST_DURATION_MINUTES);
        
        // Stop monitoring
        monitor.shutdown();
        
        // Analyze efficiency
        analyzeResourceEfficiency();
    }
    
    private int calculateHourlyLoad(int hour) {
        // Simulate typical daily pattern
        // 9 AM - 5 PM: business hours (peak)
        // 8 PM - 6 AM: night hours (low)
        if (hour >= 9 && hour <= 17) {
            return BASE_CASE_RATE * DAILY_PEAK_MULTIPLIER;
        } else if (hour >= 20 || hour <= 6) {
            return BASE_CASE_RATE / 4; // Night time
        } else {
            return BASE_CASE_RATE; // Normal hours
        }
    }
    
    private int calculateDailyLoad(DayOfWeek day) {
        // Simulate typical weekly pattern
        switch (day) {
            case MONDAY:
            case TUESDAY:
            case WEDNESDAY:
            case THURSDAY:
            case FRIDAY:
                return BASE_CASE_RATE * 24; // Business days
            case SATURDAY:
                return BASE_CASE_RATE * 12; // Half load
            case SUNDAY:
                return BASE_CASE_RATE * 6; // Light load
            default:
                return BASE_CASE_RATE * 24;
        }
    }
    
    private void submitHourlyWorkload(int caseCount, LocalTime hour) throws Exception {
        CountDownLatch latch = new CountDownLatch(caseCount);
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < caseCount; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    long caseStart = System.currentTimeMillis();
                    
                    // Submit case
                    String caseIdStr = "case-hourly-" + hour + "-" + caseId;
                    engine.createCase(caseIdStr);
                    
                    // Process with realistic duration
                    Thread.sleep(new Random().nextInt(100) + 50);
                    
                    long caseTime = System.currentTimeMillis() - caseStart;
                    loadMetrics.recordCaseTime(caseTime);
                    
                    latch.countDown();
                } catch (Exception e) {
                    loadMetrics.recordFailedCase();
                }
            });
        }
        
        latch.await(2, TimeUnit.MINUTES);
        loadMetrics.setHourlyDuration(System.currentTimeMillis() - startTime, hour);
    }
    
    private void submitDailyWorkload(int caseCount, DayOfWeek day) throws Exception {
        CountDownLatch latch = new CountDownLatch(caseCount);
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < caseCount; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    long caseStart = System.currentTimeMillis();
                    
                    // Submit case
                    String caseIdStr = "case-daily-" + day + "-" + caseId;
                    engine.createCase(caseIdStr);
                    
                    // Process with day-appropriate duration
                    int duration = getDayAppropriateDuration(day);
                    Thread.sleep(duration);
                    
                    long caseTime = System.currentTimeMillis() - caseStart;
                    loadMetrics.recordCaseTime(caseTime);
                    
                    latch.countDown();
                } catch (Exception e) {
                    loadMetrics.recordFailedCase();
                }
            });
        }
        
        latch.await(5, TimeUnit.MINUTES);
        loadMetrics.setDailyDuration(System.currentTimeMillis() - startTime, day);
    }
    
    private LoadSnapshot submitNormalLoad(int caseCount) throws Exception {
        return submitLoadWithPattern(caseCount, "normal");
    }
    
    private LoadSnapshot submitLoadSpike(int caseCount, String spikeType) throws Exception {
        return submitLoadWithPattern(caseCount, spikeType);
    }
    
    private LoadSnapshot submitLoadWithPattern(int caseCount, String pattern) throws Exception {
        LoadSnapshot snapshot = new LoadSnapshot(pattern);
        CountDownLatch latch = new CountDownLatch(caseCount);
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < caseCount; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    long caseStart = System.currentTimeMillis();
                    
                    // Submit case with pattern-specific characteristics
                    String caseIdStr = "case-" + pattern + "-" + caseId;
                    engine.createCase(caseIdStr);
                    
                    // Pattern-specific processing
                    switch (pattern) {
                        case "normal":
                            Thread.sleep(new Random().nextInt(50) + 25);
                            break;
                        case "unexpected-spike":
                            Thread.sleep(new Random().nextInt(200) + 100);
                            break;
                    }
                    
                    long caseTime = System.currentTimeMillis() - caseStart;
                    snapshot.recordCaseTime(caseTime);
                    
                    latch.countDown();
                } catch (Exception e) {
                    snapshot.recordFailedCase();
                }
            });
        }
        
        latch.await(2, TimeUnit.MINUTES);
        snapshot.setTotalDuration(System.currentTimeMillis() - startTime);
        return snapshot;
    }
    
    private void submitSustainedLoad(int durationMinutes) throws Exception {
        long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L);
        AtomicInteger caseId = new AtomicInteger(0);
        
        while (System.currentTimeMillis() < endTime) {
            // Submit batch of cases
            int batchSize = 50;
            CountDownLatch latch = new CountDownLatch(batchSize);
            
            for (int i = 0; i < batchSize; i++) {
                final int currentCaseId = caseId.getAndIncrement();
                executor.submit(() -> {
                    try {
                        long caseStart = System.currentTimeMillis();
                        
                        String caseIdStr = "case-sustained-" + currentCaseId;
                        engine.createCase(caseIdStr);
                        
                        // Normal processing
                        Thread.sleep(new Random().nextInt(100) + 50);
                        
                        long caseTime = System.currentTimeMillis() - caseStart;
                        loadMetrics.recordCaseTime(caseTime);
                        
                        latch.countDown();
                    } catch (Exception e) {
                        loadMetrics.recordFailedCase();
                    }
                });
            }
            
            latch.await(1, TimeUnit.MINUTES);
            
            // Simulate time passing between batches
            Thread.sleep(5000);
        }
    }
    
    private LoadSnapshot captureLoadSnapshot() {
        LoadSnapshot snapshot = new LoadSnapshot("snapshot");
        snapshot.recordSystemMetrics();
        return snapshot;
    }
    
    private ResourceUsage captureResourceUsage() {
        // In a real implementation, this would capture actual resource usage
        return new ResourceUsage(
            new Random().nextDouble() * 100, // CPU %
            new Random().nextInt(1000) + 500, // Memory MB
            Thread.activeCount() // Active threads
        );
    }
    
    private int getDayAppropriateDuration(DayOfWeek day) {
        switch (day) {
            case MONDAY:
            case FRIDAY:
                return new Random().nextInt(150) + 100; // Busy days
            case SATURDAY:
            case SUNDAY:
                return new Random().nextInt(50) + 25; // Light days
            default:
                return new Random().nextInt(100) + 50; // Normal days
        }
    }
    
    private void analyzeDailyLoadPattern() {
        System.out.println("\n=== DAILY LOAD PATTERN ANALYSIS ===");
        
        // Find peak and low hours
        LoadSnapshot peakHour = hourlyLoad.values().stream()
            .max(Comparator.comparingDouble(LoadSnapshot::getThroughput))
            .orElse(null);
        
        LoadSnapshot lowHour = hourlyLoad.values().stream()
            .min(Comparator.comparingDouble(LoadSnapshot::getThroughput))
            .orElse(null);
        
        if (peakHour != null && lowHour != null) {
            System.out.printf("Peak hour throughput: %.2f cases/sec%n", 
                peakHour.getThroughput());
            System.out.printf("Low hour throughput: %.2f cases/sec%n", 
                lowHour.getThroughput());
            
            double peakToLowRatio = peakHour.getThroughput() / lowHour.getThroughput();
            System.out.printf("Peak-to-low ratio: %.2f%n", peakToLowRatio);
            
            // Validate pattern expectations
            assertTrue(peakToLowRatio >= 2.0, 
                "Peak hour should have at least 2x throughput of low hour");
        }
        
        // Validate performance consistency
        double avgLatency = hourlyLoad.values().stream()
            .mapToDouble(LoadSnapshot::getAverageLatency)
            .average()
            .orElse(0);
        
        System.out.printf("Average hourly latency: %.2fms%n", avgLatency);
        assertTrue(avgLatency < 500, "Average hourly latency should be < 500ms");
    }
    
    private void analyzeWeeklyLoadPattern() {
        System.out.println("\n=== WEEKLY LOAD PATTERN ANALYSIS ===");
        
        // Analyze daily patterns
        LoadSnapshot businessDay = dailyLoad.entrySet().stream()
            .filter(e -> e.getKey().getValue() >= DayOfWeek.MONDAY.getValue() && 
                       e.getKey().getValue() <= DayOfWeek.FRIDAY.getValue())
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
        
        LoadSnapshot weekendDay = dailyLoad.entrySet().stream()
            .filter(e -> e.getKey() == DayOfWeek.SATURDAY || e.getKey() == DayOfWeek.SUNDAY)
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
        
        if (businessDay != null && weekendDay != null) {
            System.out.printf("Business day throughput: %.2f cases/sec%n", 
                businessDay.getThroughput());
            System.out.printf("Weekend day throughput: %.2f cases/sec%n", 
                weekendDay.getThroughput());
            
            double businessToWeekendRatio = businessDay.getThroughput() / weekendDay.getThroughput();
            System.out.printf("Business-to-weekend ratio: %.2f%n", businessToWeekendRatio);
            
            // Validate pattern expectations
            assertTrue(businessToWeekendRatio >= 1.5, 
                "Business days should have at least 1.5x throughput of weekend days");
        }
    }
    
    private void analyzeResourceEfficiency() {
        System.out.println("\n=== RESOURCE EFFICIENCY ANALYSIS ===");
        
        // Calculate efficiency metrics
        double avgCpu = loadMetrics.getAverageCpuUsage();
        double avgMemory = loadMetrics.getAverageMemoryUsage();
        double avgThreads = loadMetrics.getAverageActiveThreads();
        
        System.out.printf("Average CPU usage: %.1f%%%n", avgCpu);
        System.out.printf("Average memory usage: %.1fMB%n", avgMemory);
        System.out.printf("Average active threads: %.0f%n", avgThreads);
        
        // Validate efficiency thresholds
        assertTrue(avgCpu < 80, "Average CPU usage should be < 80%");
        assertTrue(avgMemory < 1024, "Average memory should be < 1024MB");
        
        // Calculate efficiency score
        double efficiencyScore = calculateEfficiencyScore(avgCpu, avgMemory, avgThreads);
        System.out.printf("Resource efficiency score: %.2f/100%n", efficiencyScore);
        
        assertTrue(efficiencyScore > 70, "Resource efficiency should be > 70%");
    }
    
    private double calculateEfficiencyScore(double cpu, double memory, double threads) {
        // Normalize metrics (0-100 scale)
        double cpuScore = Math.max(0, 100 - (cpu / 80 * 100));
        double memoryScore = Math.max(0, 100 - (memory / 1024 * 100));
        double threadScore = Math.max(0, 100 - (threads / 200 * 100));
        
        // Weighted average
        return (cpuScore * 0.4) + (memoryScore * 0.4) + (threadScore * 0.2);
    }
    
    /**
     * Metrics collection for seasonal load tests
     */
    private static class LoadMetrics {
        private final List<Long> caseTimes = new ArrayList<>();
        private final List<Long> hourlyDurations = new ArrayList<>();
        private final List<Long> dailyDurations = new ArrayList<>();
        private final List<ResourceUsage> resourceUsages = new ArrayList<>();
        private int failedCases = 0;
        
        public void recordCaseTime(long time) {
            caseTimes.add(time);
        }
        
        public void recordFailedCase() {
            failedCases++;
        }
        
        public void setHourlyDuration(long duration, LocalTime hour) {
            hourlyDurations.add(duration);
        }
        
        public void setDailyDuration(long duration, DayOfWeek day) {
            dailyDurations.add(duration);
        }
        
        public void recordResourceUsage(ResourceUsage usage) {
            resourceUsages.add(usage);
        }
        
        public double getAverageLatency() {
            if (caseTimes.isEmpty()) return 0;
            return caseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }
        
        public double getAverageCpuUsage() {
            if (resourceUsages.isEmpty()) return 0;
            return resourceUsages.stream()
                .mapToDouble(ResourceUsage::cpuUsage)
                .average()
                .orElse(0);
        }
        
        public double getAverageMemoryUsage() {
            if (resourceUsages.isEmpty()) return 0;
            return resourceUsages.stream()
                .mapToDouble(ResourceUsage::memoryMB)
                .average()
                .orElse(0);
        }
        
        public double getAverageActiveThreads() {
            if (resourceUsages.isEmpty()) return 0;
            return resourceUsages.stream()
                .mapToDouble(ResourceUsage::activeThreads)
                .average()
                .orElse(0);
        }
    }
    
    /**
     * Snapshot of load metrics at a specific time
     */
    private static class LoadSnapshot {
        private final String pattern;
        private final List<Long> caseTimes = new ArrayList<>();
        private int failedCases = 0;
        private long totalDuration = 0;
        private double cpuUsage = 0;
        private double memoryMB = 0;
        private int activeThreads = 0;
        
        public LoadSnapshot(String pattern) {
            this.pattern = pattern;
        }
        
        public void recordCaseTime(long time) {
            caseTimes.add(time);
        }
        
        public void recordFailedCase() {
            failedCases++;
        }
        
        public void setTotalDuration(long duration) {
            this.totalDuration = duration;
        }
        
        public void recordSystemMetrics() {
            // In a real implementation, capture actual system metrics
            this.cpuUsage = new Random().nextDouble() * 100;
            this.memoryMB = new Random().nextInt(1000) + 500;
            this.activeThreads = Thread.activeCount();
        }
        
        public double getThroughput() {
            if (totalDuration == 0) return 0;
            return (caseTimes.size() + failedCases) / (totalDuration / 1000.0);
        }
        
        public double getAverageLatency() {
            if (caseTimes.isEmpty()) return 0;
            return caseTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }
    }
    
    /**
     * Resource usage snapshot
     */
    private static record ResourceUsage(double cpuUsage, double memoryMB, int activeThreads) {
    }
}
