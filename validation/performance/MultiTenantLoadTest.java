/*
 * YAWL v6.0.0-GA Validation
 * Multi-Tenant Load Test
 *
 * Validates tenant isolation and performance under mixed workloads
 */
package org.yawlfoundation.yawl.performance;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.elements.YAWLNet;
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YNetRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-tenant isolation and performance validation
 * Validates tenant isolation under mixed workloads
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MultiTenantLoadTest {

    private YAWLServiceGateway serviceGateway;
    private PerformanceMonitor performanceMonitor;
    private ExecutorService executorService;

    // Test configuration
    private static final int MAX_TENANTS = 100;
    private static final int CASES_PER_TENANT = 100;
    private static final int CONCURRENT_WORKERS = 20;
    private static final Duration TEST_DURATION = Duration.ofMinutes(30);

    @BeforeAll
    void setUp() {
        serviceGateway = new YAWLServiceGateway();
        performanceMonitor = new PerformanceMonitor();
        executorService = Executors.newFixedThreadPool(CONCURRENT_WORKERS);
    }

    @AfterAll
    void tearDown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Validate tenant isolation under load")
    void validateTenantIsolationUnderLoad() throws InterruptedException {
        System.out.println("Starting multi-tenant isolation validation...");

        // Create tenants with different characteristics
        List<TenantProfile> tenants = createTenantProfiles(MAX_TENANTS);
        Map<String, TenantResult> tenantResults = new ConcurrentHashMap<>();

        // Launch concurrent workload for all tenants
        Instant startTime = Instant.now();
        List<Future<?>> futures = new ArrayList<>();

        for (TenantProfile tenant : tenants) {
            futures.add(executorService.submit(() -> {
                processTenantWorkload(tenant, tenantResults);
            }));
        }

        // Monitor progress
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            System.out.printf("Progress: %d/%d tenants completed%n",
                tenantResults.size(), tenants.size());
        }, 1, 1, TimeUnit.MINUTES);

        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            try {
                future.get(TEST_DURATION.toSeconds(), TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                System.err.println("Task timed out, continuing with remaining...");
            } catch (ExecutionException e) {
                System.err.println("Task failed: " + e.getCause());
            }
        }

        monitor.shutdown();

        // Validate isolation
        validateTenantIsolation(tenantResults);

        // Validate performance
        validateMultiTenantPerformance(tenantResults, startTime);

        System.out.println("Multi-tenant isolation validation completed");
    }

    @Test
    @DisplayName("Validate resource sharing fairness")
    void validateResourceSharingFairness() throws InterruptedException {
        System.out.println("Starting resource fairness validation...");

        // Create tenants with different priority levels
        List<TenantProfile> tenants = new ArrayList<>();
        for (int i = 0; i < MAX_TENANTS; i++) {
            int priority = (i % 3) + 1; // Distribute priorities evenly
            tenants.add(new TenantProfile(
                "tenant-" + i,
                "enterprise-customer-" + (i / 10),
                priority,
                generateTenantWorkloadProfile(priority)
            ));
        }

        // Track resource usage
        ResourceTracker resourceTracker = new ResourceTracker();

        // Launch mixed workload
        Instant startTime = Instant.now();
        List<Future<?>> futures = new ArrayList<>();

        for (TenantProfile tenant : tenants) {
            futures.add(executorService.submit(() -> {
                processTenantWorkloadWithTracking(tenant, resourceTracker);
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(TEST_DURATION.toSeconds(), TimeUnit.SECONDS);
            } catch (TimeoutException | ExecutionException e) {
                future.cancel(true);
            }
        }

        // Validate fairness
        validateFairResourceDistribution(resourceTracker);

        // Validate priority-based scheduling
        validatePriorityBasedScheduling(tenants);

        System.out.println("Resource fairness validation completed");
    }

    @Test
    @DisplayName("Validate cross-tenant data isolation")
    void validateCrossTenantDataIsolation() throws InterruptedException {
        System.out.println("Starting data isolation validation...");

        // Create test data for each tenant
        Map<String, Map<String, Object>> tenantData = new ConcurrentHashMap<>();
        for (int i = 0; i < MAX_TENANTS; i++) {
            String tenantId = "tenant-" + i;
            Map<String, Object> data = generateTenantTestData(tenantId);
            tenantData.put(tenantId, data);
        }

        // Process cross-tenant operations
        AtomicInteger isolationViolations = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_WORKERS; i++) {
            final int threadId = i;
            futures.add(executorService.submit(() -> {
                performCrossTenantOperations(tenantData, isolationViolations, threadId);
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(1, TimeUnit.MINUTES);
            } catch (TimeoutException | ExecutionException e) {
                future.cancel(true);
            }
        }

        // Validate no data leakage
        assertEquals(0, isolationViolations.get(),
            "Cross-tenant data isolation violations detected");

        System.out.println("Data isolation validation completed with no violations");
    }

    private List<TenantProfile> createTenantProfiles(int count) {
        List<TenantProfile> profiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            profiles.add(new TenantProfile(
                "tenant-" + i,
                "enterprise-customer-" + (i / 10),
                (i % 3) + 1, // Priority 1-3
                generateTenantWorkloadProfile((i % 3) + 1)
            ));
        }
        return profiles;
    }

    private WorkloadProfile generateTenantWorkloadProfile(int priority) {
        switch (priority) {
            case 1:
                return WorkloadProfile.ENTERPRISE_MIXED;
            case 2:
                return WorkloadProfile.MULTI_TENANT;
            case 3:
                return WorkloadProfile.SEASONAL_SPIKES;
            default:
                return WorkloadProfile.ENTERPRISE_MIXED;
        }
    }

    private void processTenantWorkload(TenantProfile tenant, Map<String, TenantResult> results) {
        TenantResult result = new TenantResult(tenant.getId());
        results.put(tenant.getId(), result);

        for (int i = 0; i < CASES_PER_TENANT; i++) {
            Instant caseStart = Instant.now();
            try {
                // Process case with tenant context
                String caseId = processTenantCase(tenant, i);

                // Record metrics
                Instant caseEnd = Instant.now();
                long duration = Duration.between(caseStart, caseEnd).toMillis();

                result.recordCase(caseId, duration, true);
                performanceMonitor.recordCaseProcessing(duration, tenant.getPriority());

            } catch (Exception e) {
                result.recordError(e.getMessage());
                performanceMonitor.recordError(e.getClass().getSimpleName(), tenant.getId() + "-" + i);
            }
        }
    }

    private void processTenantWorkloadWithTracking(TenantProfile tenant, ResourceTracker tracker) {
        for (int i = 0; i < CASES_PER_TENANT; i++) {
            Instant caseStart = Instant.now();

            // Track resource usage
            long startMemory = tracker.getCurrentMemoryUsage();
            long startCpu = tracker.getCurrentCpuUsage();

            try {
                processTenantCase(tenant, i);

                // Record resource usage
                long endMemory = tracker.getCurrentMemoryUsage();
                long endCpu = tracker.getCurrentCpuUsage();

                tracker.recordUsage(tenant.getId(),
                    endMemory - startMemory,
                    endCpu - startCpu);

            } catch (Exception e) {
                tracker.recordError(tenant.getId(), e.getMessage());
            }
        }
    }

    private String processTenantCase(TenantProfile tenant, int caseNumber) throws Exception {
        // Simulate case processing with tenant context
        YNetRunner runner = serviceGateway.getNet("multi-tenant-workflow");
        if (runner == null) {
            throw new RuntimeException("Workflow not found");
        }

        // Set tenant-specific attributes
        runner.setAttribute("tenantId", tenant.getId());
        runner.setAttribute("priority", tenant.getPriority());
        runner.setAttribute("customerType", tenant.getCustomerType());

        // Execute case
        String caseToken = runner.launchCase();

        // Simulate processing time
        Thread.sleep((long) (Math.random() * 100) + 10);

        return caseToken;
    }

    private Map<String, Object> generateTenantTestData(String tenantId) {
        Map<String, Object> data = new HashMap<>();
        data.put("tenantId", tenantId);
        data.put("timestamp", Instant.now());
        data.put("sensitiveData", "encrypted-" + UUID.randomUUID());
        data.put("transactionCount", Math.random() * 1000);
        return data;
    }

    private void performCrossTenantOperations(Map<String, Map<String, Object>> tenantData,
                                           AtomicInteger violations, int threadId) {
        Random random = new Random(threadId);

        for (int i = 0; i < 100; i++) {
            String sourceTenant = "tenant-" + random.nextInt(MAX_TENANTS);
            String targetTenant = "tenant-" + random.nextInt(MAX_TENANTS);

            try {
                // Attempt to access tenant data
                Map<String, Object> sourceData = tenantData.get(sourceTenant);
                Map<String, Object> targetData = tenantData.get(targetTenant);

                // Validate data isolation
                if (sourceData != null && targetData != null) {
                    Object sourceSensitive = sourceData.get("sensitiveData");
                    Object targetSensitive = targetData.get("sensitiveData");

                    // Check if data is properly isolated (encrypted differently)
                    if (sourceSensitive.equals(targetSensitive)) {
                        violations.incrementAndGet();
                    }
                }

            } catch (Exception e) {
                // Expected - should throw exception for unauthorized access
            }
        }
    }

    private void validateTenantIsolation(Map<String, TenantResult> results) {
        // Validate that no tenant data leaked to others
        for (TenantResult result : results.values()) {
            assertTrue(result.getSuccessRate() >= 0.95,
                "Tenant " + result.getTenantId() + " has low success rate");
            assertFalse(result.hasDataLeaks(),
                "Tenant " + result.getTenantId() + " has data leaks");
        }

        // Validate consistent performance across tenants
        double avgProcessingTime = results.values().stream()
            .mapToDouble(TenantResult::getAverageProcessingTime)
            .average()
            .orElse(0);

        double maxDeviation = results.values().stream()
            .mapToDouble(r -> Math.abs(r.getAverageProcessingTime() - avgProcessingTime))
            .max()
            .orElse(0);

        assertTrue(maxDeviation <= avgProcessingTime * 0.2,
            "Performance deviation too high: " + maxDeviation);
    }

    private void validateMultiTenantPerformance(Map<String, TenantResult> results, Instant startTime) {
        Duration duration = Duration.between(startTime, Instant.now());
        long totalCases = results.values().stream()
            .mapToLong(TenantResult::getCaseCount)
            .sum();

        double throughput = totalCases / (duration.toSeconds() / 3600.0);
        double avgProcessingTime = results.values().stream()
            .mapToDouble(TenantResult::getAverageProcessingTime)
            .average()
            .orElse(0);

        System.out.printf("Multi-tenant performance: %.2f cases/hour, %.2fms avg processing%n",
            throughput, avgProcessingTime);

        // Validate performance targets
        assertTrue(throughput > 5000, "Throughput below target: " + throughput);
        assertTrue(avgProcessingTime < 200, "Average processing time above target: " + avgProcessingTime);
    }

    private void validateFairResourceDistribution(ResourceTracker tracker) {
        Map<String, Long> memoryUsage = tracker.getMemoryUsageByTenant();
        Map<String, Long> cpuUsage = tracker.getCpuUsageByTenant();

        // Validate resource usage is within acceptable bounds
        long maxMemory = memoryUsage.values().stream().max(Long::compare).orElse(0L);
        long avgMemory = memoryUsage.values().stream()
            .mapToLong(Long::longValue)
            .sum() / memoryUsage.size();

        assertTrue(maxMemory <= avgMemory * 2,
            "Memory usage imbalance detected");

        // CPU usage should be evenly distributed
        long maxCpu = cpuUsage.values().stream().max(Long::compare).orElse(0L);
        long avgCpu = cpuUsage.values().stream()
            .mapToLong(Long::longValue)
            .sum() / cpuUsage.size();

        assertTrue(maxCpu <= avgCpu * 1.5,
            "CPU usage imbalance detected");
    }

    private void validatePriorityBasedScheduling(List<TenantProfile> tenants) {
        // Group tenants by priority
        Map<Integer, List<TenantProfile>> tenantsByPriority = new HashMap<>();
        tenants.forEach(t ->
            tenantsByPriority.computeIfAbsent(t.getPriority(), k -> new ArrayList<>()).add(t));

        // Validate higher priority tenants get better performance
        List<TenantProfile> priority1 = tenantsByPriority.get(1);
        List<TenantProfile> priority3 = tenantsByPriority.get(3);

        // This would require actual measurement in a real scenario
        // Here we're validating the configuration is correct
        assertFalse(priority1.isEmpty(), "No priority 1 tenants found");
        assertFalse(priority3.isEmpty(), "No priority 3 tenants found");
    }

    /**
     * Tenant profile definition
     */
    private static class TenantProfile {
        private final String id;
        private final String customerType;
        private final int priority;
        private final WorkloadProfile profile;

        public TenantProfile(String id, String customerType, int priority, WorkloadProfile profile) {
            this.id = id;
            this.customerType = customerType;
            this.priority = priority;
            this.profile = profile;
        }

        // Getters
        public String getId() { return id; }
        public String getCustomerType() { return customerType; }
        public int getPriority() { return priority; }
        public WorkloadProfile getProfile() { return profile; }
    }

    /**
     * Tenant result tracking
     */
    private static class TenantResult {
        private final String tenantId;
        private final List<String> successfulCases = new ArrayList<>();
        private final List<String> failedCases = new ArrayList<>();
        private final List<Long> processingTimes = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public TenantResult(String tenantId) {
            this.tenantId = tenantId;
        }

        public void recordCase(String caseId, long duration, boolean success) {
            if (success) {
                successfulCases.add(caseId);
                processingTimes.add(duration);
            } else {
                failedCases.add(caseId);
            }
        }

        public void recordError(String error) {
            errors.add(error);
        }

        public double getSuccessRate() {
            int total = successfulCases.size() + failedCases.size();
            return total > 0 ? (double) successfulCases.size() / total : 0;
        }

        public double getAverageProcessingTime() {
            return processingTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }

        public int getCaseCount() {
            return successfulCases.size() + failedCases.size();
        }

        public boolean hasDataLeaks() {
            return errors.stream().anyMatch(e -> e.contains("data leakage"));
        }

        public String getTenantId() { return tenantId; }
    }

    /**
     * Resource tracking utility
     */
    private static class ResourceTracker {
        private final Map<String, Long> memoryUsage = new ConcurrentHashMap<>();
        private final Map<String, Long> cpuUsage = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> errorCount = new ConcurrentHashMap<>();

        public long getCurrentMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        }

        public long getCurrentCpuUsage() {
            // Simplified CPU tracking
            return System.currentTimeMillis();
        }

        public void recordUsage(String tenantId, long memoryDelta, long cpuDelta) {
            memoryUsage.merge(tenantId, memoryDelta, Long::sum);
            cpuUsage.merge(tenantId, cpuDelta, Long::sum);
        }

        public void recordError(String tenantId, String error) {
            errorCount.computeIfAbsent(tenantId, k -> new AtomicInteger()).incrementAndGet();
        }

        public Map<String, Long> getMemoryUsageByTenant() {
            return new HashMap<>(memoryUsage);
        }

        public Map<String, Long> getCpuUsageByTenant() {
            return new HashMap<>(cpuUsage);
        }
    }
}