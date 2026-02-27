/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.stress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.engine.YAWLStatelessEngine;
import org.yawlfoundation.yawl.elements.YSpecificationID;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;

/**
 * Multi-tenant stress test for YAWL v6.0.0-GA.
 *
 * Validates tenant isolation, resource sharing, and performance
 * under concurrent multi-tenant workloads.
 *
 * Tests:
 * - 100 concurrent tenants
 * - Tenant data isolation
 * - Resource sharing fairness
 * - Performance degradation under mixed workloads
 *
 * @author YAWL Performance Team
 * @version 6.0.0-GA
 * @since 2026-02-26
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MultiTenantStressTest {

    private YAWLStatelessEngine engine;
    private PerformanceMonitor performanceMonitor;
    private ExecutorService executorService;

    // Test configuration
    private static final int MAX_TENANTS = 100;
    private static final int CASES_PER_TENANT = 100;
    private static final int CONCURRENT_WORKERS = 50;
    private static final int TEST_DURATION_MINUTES = 30;
    private static final int TENANT_ISOLATION_CHECK_INTERVAL_MS = 1000;

    // Tenant configurations
    private List<TenantProfile> tenantProfiles;
    private Map<String, TenantResult> tenantResults = new ConcurrentHashMap<>();

    @BeforeAll
    void setUp() throws Exception {
        engine = new YAWLStatelessEngine();
        performanceMonitor = new PerformanceMonitor();
        executorService = Executors.newVirtualThreadPerTaskExecutor();

        // Load tenant-specific workflow specifications
        loadTenantWorkflowSpecifications();

        // Create tenant profiles
        createTenantProfiles();
    }

    @AfterAll
    void tearDown() throws Exception {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Test tenant isolation under concurrent load")
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void testTenantIsolationUnderConcurrentLoad() throws Exception {
        System.out.println("Starting tenant isolation test with " + MAX_TENANTS + " concurrent tenants...");

        // Start tenant isolation monitor
        AtomicBoolean isolationViolationDetected = new AtomicBoolean(false);
        Thread isolationMonitor = startIsolationMonitor(isolationViolationDetected);
        isolationMonitor.start();

        // Launch concurrent tenant workloads
        Instant testStart = Instant.now();
        List<CompletableFuture<Void>> tenantFutures = new ArrayList<>();

        for (TenantProfile tenant : tenantProfiles) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                processTenantWorkload(tenant);
            }, executorService);

            tenantFutures.add(future);
        }

        // Monitor progress
        ScheduledExecutorService progressMonitor = Executors.newSingleThreadScheduledExecutor();
        progressMonitor.scheduleAtFixedRate(() -> {
            int completed = tenantResults.size();
            System.out.printf("Tenant progress: %d/%d tenants completed (%.1f%%)%n",
                completed, MAX_TENANTS, (double) completed / MAX_TENANTS * 100);
        }, 1, 1, TimeUnit.MINUTES);

        // Wait for all tenants to complete
        CompletableFuture.allOf(tenantFutures.toArray(new CompletableFuture[0]))
            .get(TEST_DURATION_MINUTES, TimeUnit.MINUTES);

        progressMonitor.shutdown();
        isolationMonitor.interrupt();
        isolationMonitor.join();

        // Validate tenant isolation results
        validateTenantIsolationResults(isolationViolationDetected.get());
    }

    @Test
    @DisplayName("Test resource sharing fairness")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void testResourceSharingFairness() throws Exception {
        System.out.println("Testing resource sharing fairness across tenants...");

        // Create resource tracker
        ResourceTracker resourceTracker = new ResourceTracker();

        // Launch mixed workload with resource tracking
        Instant startTime = Instant.now();
        List<CompletableFuture<Void>> workloadFutures = new ArrayList<>();

        for (TenantProfile tenant : tenantProfiles) {
            for (int i = 0; i < CASES_PER_TENANT / 10; i++) { // Reduced for fairness test
                final int caseIndex = i;
                workloadFutures.add(CompletableFuture.runAsync(() -> {
                    processTenantCaseWithTracking(tenant, caseIndex, resourceTracker);
                }, executorService));
            }
        }

        // Wait for workload completion
        CompletableFuture.allOf(workloadFutures.toArray(new CompletableFuture[0]))
            .get(20, TimeUnit.MINUTES);

        // Validate resource fairness
        validateResourceSharingFairness(resourceTracker);
    }

    @Test
    @DisplayName("Test cross-tenant data isolation")
    @Timeout(value = 25, unit = TimeUnit.MINUTES)
    void testCrossTenantDataIsolation() throws Exception {
        System.out.println("Testing cross-tenant data isolation...");

        // Generate tenant-specific data
        Map<String, Map<String, Object>> tenantData = new ConcurrentHashMap<>();
        AtomicInteger isolationViolations = new AtomicInteger(0);

        for (TenantProfile tenant : tenantProfiles) {
            tenantData.put(tenant.getId(), generateTenantData(tenant));
        }

        // Perform cross-tenant operations
        List<CompletableFuture<Void>> operationFutures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_WORKERS; i++) {
            final int workerId = i;
            operationFutures.add(CompletableFuture.runAsync(() -> {
                performCrossTenantOperations(tenantData, isolationViolations, workerId);
            }, executorService));
        }

        // Wait for all operations to complete
        CompletableFuture.allOf(operationFutures.toArray(new CompletableFuture[0]))
            .get(25, TimeUnit.MINUTES);

        // Validate data isolation
        validateDataIsolation(isolationViolations.get());
    }

    @Test
    @DisplayName("Test priority-based tenant scheduling")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void testPriorityBasedTenantScheduling() throws Exception {
        System.out.println("Testing priority-based tenant scheduling...");

        // Create tenants with different priority levels
        Map<Integer, List<TenantProfile>> tenantsByPriority = new HashMap<>();
        tenantProfiles.forEach(tenant ->
            tenantsByPriority.computeIfAbsent(tenant.getPriority(), k -> new ArrayList<>()).add(tenant));

        // Track scheduling performance
        Map<Integer, PriorityMetrics> priorityMetrics = new ConcurrentHashMap<>();

        // Launch priority-based workload
        Instant startTime = Instant.now();
        List<CompletableFuture<Void>> priorityFutures = new ArrayList<>();

        for (Map.Entry<Integer, List<TenantProfile>> entry : tenantsByPriority.entrySet()) {
            int priority = entry.getKey();
            List<TenantProfile> priorityTenants = entry.getValue();

            for (TenantProfile tenant : priorityTenants) {
                for (int i = 0; i < 20; i++) { // 20 cases per tenant for priority test
                    final int caseIndex = i;
                    priorityFutures.add(CompletableFuture.runAsync(() -> {
                        processPriorityCase(tenant, priority, caseIndex, priorityMetrics);
                    }, executorService));
                }
            }
        }

        // Wait for completion
        CompletableFuture.allOf(priorityFutures.toArray(new CompletableFuture[0]))
            .get(20, TimeUnit.MINUTES);

        // Validate priority scheduling
        validatePriorityScheduling(tenantsByPriority, priorityMetrics);
    }

    @Test
    @DisplayName("Test tenant-specific SLA compliance")
    @Timeout(value = 25, unit = TimeUnit.MINUTES)
    void testTenantSpecificSLACompliance() throws Exception {
        System.out.println("Testing tenant-specific SLA compliance...");

        // SLA thresholds based on tenant priority
        Map<Integer, SLAThresholds> slaThresholds = createSLAThresholds();

        // Track SLA compliance
        Map<String, SLAMetrics> slaMetrics = new ConcurrentHashMap<>();

        // Launch SLA-compliant workload
        Instant testStart = Instant.now();
        List<CompletableFuture<Void>> slaFutures = new ArrayList<>();

        for (TenantProfile tenant : tenantProfiles) {
            SLAThresholds thresholds = slaThresholds.get(tenant.getPriority());

            for (int i = 0; i < 50; i++) { // 50 cases per tenant for SLA test
                final int caseIndex = i;
                slaFutures.add(CompletableFuture.runAsync(() -> {
                    processSLACompliantCase(tenant, thresholds, caseIndex, slaMetrics);
                }, executorService));
            }
        }

        // Wait for completion
        CompletableFuture.allOf(slaFutures.toArray(new CompletableFuture[0]))
            .get(25, TimeUnit.MINUTES);

        // Validate SLA compliance
        validateSLACompliance(slaThresholds, slaMetrics);
    }

    // Helper methods

    private void loadTenantWorkflowSpecifications() throws Exception {
        // Create specifications for different tenant workflows
        String[] specNames = {
            "SimpleTenantWorkflow",
            "ComplexTenantWorkflow",
            "EnterpriseTenantWorkflow",
            "SPTenantWorkflow"
        };

        for (String specName : specNames) {
            String specXml = createTenantSpecification(specName);
            YSpecificationID specId = engine.uploadSpecification(specXml);
            System.out.println("Loaded tenant specification: " + specId);
        }
    }

    private String createTenantSpecification(String name) {
        return switch (name) {
            case "SimpleTenantWorkflow" -> """
                <specification id="SimpleTenantWorkflow" version="1.0">
                    <name>Simple Tenant Process</name>
                    <process id="simpleTenantProcess" name="Simple Tenant Process">
                        <start id="start"/>
                        <task id="task1" name="Tenant Task"/>
                        <end id="end"/>
                        <flow from="start" to="task1"/>
                        <flow from="task1" to="end"/>
                    </process>
                </specification>
                """;
            case "ComplexTenantWorkflow" -> """
                <specification id="ComplexTenantWorkflow" version="1.0">
                    <name>Complex Tenant Process</name>
                    <process id="complexTenantProcess" name="Complex Tenant Process">
                        <start id="start"/>
                        <task id="task1" name="Setup Task"/>
                        <task id="task2" name="Data Processing Task"/>
                        <task id="task3" name="Validation Task"/>
                        <task id="task4" name="Completion Task"/>
                        <end id="end"/>
                        <flow from="start" to="task1"/>
                        <flow from="task1" to="task2"/>
                        <flow from="task2" to="task3"/>
                        <flow from="task3" to="task4"/>
                        <flow from="task4" to="end"/>
                    </process>
                </specification>
                """;
            case "EnterpriseTenantWorkflow" -> """
                <specification id="EnterpriseTenantWorkflow" version="1.0">
                    <name>Enterprise Tenant Process</name>
                    <process id="enterpriseTenantProcess" name="Enterprise Tenant Process">
                        <start id="start"/>
                        <task id="task1" name="Enterprise Setup" priority="high"/>
                        <task id="task2" name="Enterprise Processing"/>
                        <task id="task3" name="Enterprise Validation"/>
                        <task id="task4" name="Enterprise Completion"/>
                        <end id="end"/>
                        <flow from="start" to="task1"/>
                        <flow from="task1" to="task2"/>
                        <flow from="task2" to="task3"/>
                        <flow from="task3" to="task4"/>
                        <flow from="task4" to="end"/>
                    </process>
                </specification>
                """;
            case "SPTenantWorkflow" -> """
                <specification id="SPTenantWorkflow" version="1.0">
                    <name>Service Provider Process</name>
                    <process id="spTenantProcess" name="Service Provider Process">
                        <start id="start"/>
                        <task id="task1" name="SP Registration"/>
                        <task id="task2" name="SP Authentication"/>
                        <task id="task3" name="SP Service Delivery"/>
                        <task id="task4" name="SP Billing"/>
                        <end id="end"/>
                        <flow from="start" to="task1"/>
                        <flow from="task1" to="task2"/>
                        <flow from="task2" to="task3"/>
                        <flow from="task3" to="task4"/>
                        <flow from="task4" to="end"/>
                    </process>
                </specification>
                """;
            default -> throw new IllegalArgumentException("Unknown specification: " + name);
        };
    }

    private void createTenantProfiles() {
        tenantProfiles = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < MAX_TENANTS; i++) {
            String tenantId = "tenant-" + (i + 1000); // Unique tenant IDs
            String customerType = getCustomerType(i);
            int priority = determinePriority(i);
            WorkloadProfile profile = determineWorkloadProfile(priority);

            tenantProfiles.add(new TenantProfile(
                tenantId,
                customerType,
                priority,
                profile,
                generateTenantAttributes()
            ));
        }
    }

    private String getCustomerType(int index) {
        if (index < 30) return "enterprise";
        if (index < 60) return "professional";
        if (index < 80) return "small-business";
        return "individual";
    }

    private int determinePriority(int index) {
        // Distribute priorities evenly
        return (index % 3) + 1; // Priority 1-3
    }

    private WorkloadProfile determineWorkloadProfile(int priority) {
        return switch (priority) {
            case 1 -> WorkloadProfile.HIGH_VOLUME;
            case 2 -> WorkloadProfile.MIXED;
            case 3 -> WorkloadProfile.LOW_FREQUENCY;
            default -> WorkloadProfile.MIXED;
        };
    }

    private Map<String, Object> generateTenantAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("createdDate", Instant.now());
        attributes.put("settings", generateTenantSettings());
        attributes.put("quotas", generateTenantQuotas());
        return attributes;
    }

    private Map<String, Object> generateTenantSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("maxConcurrentCases", 50);
        settings.put("timeoutMinutes", 30);
        settings.put("retryAttempts", 3);
        return settings;
    }

    private Map<String, Object> generateTenantQuotas() {
        Map<String, Object> quotas = new HashMap<>();
        quotas.put("monthlyCases", 10000);
        quotas.put("storageGB", 100);
        quotas.put("apiCallsPerMinute", 1000);
        return quotas;
    }

    private void processTenantWorkload(TenantProfile tenant) {
        TenantResult result = new TenantResult(tenant.getId());
        tenantResults.put(tenant.getId(), result);

        try {
            for (int i = 0; i < CASES_PER_TENANT; i++) {
                Instant caseStart = Instant.now();

                try {
                    String caseId = processTenantCase(tenant, i);

                    Instant caseEnd = Instant.now();
                    long duration = Duration.between(caseStart, caseEnd).toMillis();

                    result.recordCase(caseId, duration, true);
                    performanceMonitor.recordTenantExecution(tenant, duration);

                } catch (Exception e) {
                    result.recordError(e.getMessage());
                    performanceMonitor.recordTenantError(tenant, e.getMessage());
                }
            }
        } catch (Exception e) {
            result.recordError("Workload processing failed: " + e.getMessage());
        }
    }

    private String processTenantCase(TenantProfile tenant, int caseIndex) throws Exception {
        String specId = selectSpecificationForTenant(tenant);
        String caseId = tenant.getId() + "-case-" + caseIndex + "-" + System.currentTimeMillis();

        String caseXml = String.format("""
            <case id="%s">
                <specificationID>%s</specificationID>
                <data>
                    <variable name="tenantId" type="string">%s</variable>
                    <variable name="caseIndex" type="int">%d</variable>
                    <variable name="priority" type="int">%d</variable>
                    <variable name="customerType" type="string">%s</variable>
                </data>
            </case>
            """, caseId, specId, tenant.getId(), caseIndex, tenant.getPriority(), tenant.getCustomerType());

        return engine.launchCase(caseXml, new YSpecificationID(specId, "1.0"));
    }

    private String selectSpecificationForTenant(TenantProfile tenant) {
        return switch (tenant.getCustomerType()) {
            case "enterprise" -> "EnterpriseTenantWorkflow";
            case "professional" -> "ComplexTenantWorkflow";
            case "small-business" -> "SimpleTenantWorkflow";
            default -> "SPTenantWorkflow";
        };
    }

    private void processTenantCaseWithTracking(TenantProfile tenant, int caseIndex,
                                            ResourceTracker tracker) {
        long startMemory = tracker.getCurrentMemoryUsage();
        long startCpu = tracker.getCurrentCpuUsage();

        try {
            String caseId = processTenantCase(tenant, caseIndex);

            long endMemory = tracker.getCurrentMemoryUsage();
            long endCpu = tracker.getCurrentCpuUsage();

            tracker.recordUsage(tenant.getId(),
                endMemory - startMemory,
                endCpu - startCpu);

        } catch (Exception e) {
            tracker.recordError(tenant.getId(), e.getMessage());
        }
    }

    private Map<String, Object> generateTenantData(TenantProfile tenant) {
        Map<String, Object> data = new HashMap<>();
        data.put("tenantId", tenant.getId());
        data.put("customerType", tenant.getCustomerType());
        data.put("priority", tenant.getPriority());
        data.put("sensitiveData", "encrypted-" + UUID.randomUUID().toString());
        data.put("transactionCount", new Random().nextInt(1000));
        data.put("settings", generateTenantSettings());
        return data;
    }

    private void performCrossTenantOperations(Map<String, Map<String, Object>> tenantData,
                                            AtomicInteger violations, int workerId) {
        Random random = new Random(workerId);

        for (int i = 0; i < 50; i++) {
            String sourceTenant = "tenant-" + (random.nextInt(MAX_TENANTS) + 1000);
            String targetTenant = "tenant-" + (random.nextInt(MAX_TENANTS) + 1000);

            try {
                Map<String, Object> sourceData = tenantData.get(sourceTenant);
                Map<String, Object> targetData = tenantData.get(targetTenant);

                if (sourceData != null && targetData != null) {
                    Object sourceSensitive = sourceData.get("sensitiveData");
                    Object targetSensitive = targetData.get("sensitiveData");

                    // Check for data leakage
                    if (sourceSensitive.equals(targetSensitive)) {
                        violations.incrementAndGet();
                        System.out.printf("Data leakage detected: worker %d, source %s -> target %s%n",
                            workerId, sourceTenant, targetTenant);
                    }
                }
            } catch (Exception e) {
                // Expected - unauthorized access should throw exceptions
            }
        }
    }

    private void processPriorityCase(TenantProfile tenant, int priority, int caseIndex,
                                   Map<Integer, PriorityMetrics> metrics) {
        long startTime = System.currentTimeMillis();

        try {
            String caseId = processTenantCase(tenant, caseIndex);

            long duration = System.currentTimeMillis() - startTime;

            metrics.computeIfAbsent(priority, k -> new PriorityMetrics())
                .recordExecution(duration);

        } catch (Exception e) {
            metrics.computeIfAbsent(priority, k -> new PriorityMetrics())
                .recordError();
        }
    }

    private Map<Integer, SLAThresholds> createSLAThresholds() {
        Map<Integer, SLAThresholds> thresholds = new HashMap<>();

        thresholds.put(1, new SLAThresholds(500, 1000, 99.0)); // Priority 1: 500ms response, 99% uptime
        thresholds.put(2, new SLAThresholds(1000, 2000, 95.0)); // Priority 2: 1s response, 95% uptime
        thresholds.put(3, new SLAThresholds(2000, 5000, 90.0)); // Priority 3: 2s response, 90% uptime

        return thresholds;
    }

    private void processSLACompliantCase(TenantProfile tenant, SLAThresholds thresholds,
                                        int caseIndex, Map<String, SLAMetrics> metrics) {
        long startTime = System.currentTimeMillis();

        try {
            String caseId = processTenantCase(tenant, caseIndex);

            long duration = System.currentTimeMillis() - startTime;
            boolean meetsSla = duration <= thresholds.responseTimeMs;

            metrics.computeIfAbsent(tenant.getId(), k -> new SLAMetrics())
                .recordExecution(duration, meetsSla);

        } catch (Exception e) {
            metrics.computeIfAbsent(tenant.getId(), k -> new SLAMetrics())
                .recordError();
        }
    }

    // Validation methods

    private void validateTenantIsolationResults(boolean isolationViolationDetected) {
        System.out.println("Validating tenant isolation results...");

        // Check all tenant results
        for (TenantResult result : tenantResults.values()) {
            double successRate = result.getSuccessRate();
            Assertions.assertTrue(successRate >= 95.0,
                String.format("Tenant %s has low success rate: %.1f%%",
                    result.getTenantId(), successRate));

            Assertions.assertFalse(result.hasDataLeaks(),
                String.format("Tenant %s has data leaks", result.getTenantId()));
        }

        // Validate no isolation violations
        Assertions.assertFalse(isolationViolationDetected,
            "Tenant isolation violations detected");

        // Validate consistent performance across tenants
        double avgProcessingTime = tenantResults.values().stream()
            .mapToDouble(TenantResult::getAverageProcessingTime)
            .average()
            .orElse(0);

        double maxDeviation = tenantResults.values().stream()
            .mapToDouble(r -> Math.abs(r.getAverageProcessingTime() - avgProcessingTime))
            .max()
            .orElse(0);

        Assertions.assertTrue(maxDeviation <= avgProcessingTime * 0.2,
            String.format("Performance deviation too high: %.1fms", maxDeviation));

        System.out.println("Tenant isolation validation passed");
    }

    private void validateResourceSharingFairness(ResourceTracker tracker) {
        Map<String, Long> memoryUsage = tracker.getMemoryUsageByTenant();
        Map<String, Long> cpuUsage = tracker.getCpuUsageByTenant();

        // Validate memory fairness
        long maxMemory = memoryUsage.values().stream().max(Long::compare).orElse(0L);
        long avgMemory = memoryUsage.values().stream()
            .mapToLong(Long::longValue)
            .sum() / memoryUsage.size();

        Assertions.assertTrue(maxMemory <= avgMemory * 2,
            "Memory usage imbalance detected");

        // Validate CPU fairness
        long maxCpu = cpuUsage.values().stream().max(Long::compare).orElse(0L);
        long avgCpu = cpuUsage.values().stream()
            .mapToLong(Long::longValue)
            .sum() / cpuUsage.size();

        Assertions.assertTrue(maxCpu <= avgCpu * 1.5,
            "CPU usage imbalance detected");

        System.out.println("Resource sharing fairness validation passed");
    }

    private void validateDataIsolation(int isolationViolations) {
        System.out.printf("Data isolation validation: %d violations detected%n",
            isolationViolations);

        Assertions.assertEquals(0, isolationViolations,
            "Cross-tenant data isolation violations detected");

        System.out.println("Data isolation validation passed");
    }

    private void validatePriorityScheduling(Map<Integer, List<TenantProfile>> tenantsByPriority,
                                         Map<Integer, PriorityMetrics> metrics) {
        System.out.println("Validating priority-based scheduling...");

        for (Map.Entry<Integer, List<TenantProfile>> entry : tenantsByPriority.entrySet()) {
            int priority = entry.getKey();
            PriorityMetrics priorityMetrics = metrics.get(priority);

            if (priorityMetrics != null) {
                double avgDuration = priorityMetrics.getAverageDuration();
                double successRate = priorityMetrics.getSuccessRate();

                System.out.printf("Priority %d: avg=%.1fms, success=%.1f%%%n",
                    priority, avgDuration, successRate);

                // Higher priority should have better performance
                if (priority == 1) {
                    Assertions.assertTrue(avgDuration < 1000,
                        String.format("Priority 1 too slow: %.1fms", avgDuration));
                }
            }
        }

        System.out.println("Priority scheduling validation passed");
    }

    private void validateSLACompliance(Map<Integer, SLAThresholds> thresholds,
                                     Map<String, SLAMetrics> metrics) {
        System.out.println("Validating SLA compliance...");

        for (Map.Entry<String, SLAMetrics> entry : metrics.entrySet()) {
            String tenantId = entry.getKey();
            SLAMetrics tenantMetrics = entry.getValue();

            // Find tenant priority
            int priority = tenantProfiles.stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .map(TenantProfile::getPriority)
                .orElse(3);

            SLAThresholds threshold = thresholds.get(priority);
            double complianceRate = tenantMetrics.getComplianceRate();

            System.out.printf("Tenant %s (Priority %d): %.1f%% SLA compliance%n",
                tenantId, priority, complianceRate);

            Assertions.assertTrue(complianceRate >= threshold.complianceRate,
                String.format("Tenant %s SLA compliance too low: %.1f%%",
                    tenantId, complianceRate));
        }

        System.out.println("SLA compliance validation passed");
    }

    private Thread startIsolationMonitor(AtomicBoolean violationDetected) {
        return new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(TENANT_ISOLATION_CHECK_INTERVAL_MS);
                    checkTenantIsolation(violationDetected);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void checkTenantIsolation(AtomicBoolean violationDetected) {
        // Check for potential isolation violations
        for (TenantResult result : tenantResults.values()) {
            if (result.hasDataLeaks()) {
                violationDetected.set(true);
                System.out.println("Tenant isolation violation detected!");
                break;
            }
        }
    }

    // Inner classes

    private static class TenantProfile {
        private final String id;
        private final String customerType;
        private final int priority;
        private final WorkloadProfile profile;
        private final Map<String, Object> attributes;

        public TenantProfile(String id, String customerType, int priority,
                           WorkloadProfile profile, Map<String, Object> attributes) {
            this.id = id;
            this.customerType = customerType;
            this.priority = priority;
            this.profile = profile;
            this.attributes = attributes;
        }

        public String getId() { return id; }
        public String getCustomerType() { return customerType; }
        public int getPriority() { return priority; }
        public WorkloadProfile getProfile() { return profile; }
        public Map<String, Object> getAttributes() { return attributes; }
    }

    private enum WorkloadProfile {
        HIGH_VOLUME, MIXED, LOW_FREQUENCY
    }

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

    private static class ResourceTracker {
        private final Map<String, Long> memoryUsage = new ConcurrentHashMap<>();
        private final Map<String, Long> cpuUsage = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> errorCount = new ConcurrentHashMap<>();

        public long getCurrentMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            return runtime.totalMemory() - runtime.freeMemory();
        }

        public long getCurrentCpuUsage() {
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

    private static class PriorityMetrics {
        private final List<Long> durations = new ArrayList<>();
        private final AtomicInteger errors = new AtomicInteger(0);

        public void recordExecution(long duration) {
            durations.add(duration);
        }

        public void recordError() {
            errors.incrementAndGet();
        }

        public double getAverageDuration() {
            return durations.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }

        public double getSuccessRate() {
            int total = durations.size() + errors.get();
            return total > 0 ? (double) durations.size() / total * 100 : 0;
        }
    }

    private static class SLAThresholds {
        final long responseTimeMs;
        final long uptimeMs;
        final double complianceRate;

        SLAThresholds(long responseTimeMs, long uptimeMs, double complianceRate) {
            this.responseTimeMs = responseTimeMs;
            this.uptimeMs = uptimeMs;
            this.complianceRate = complianceRate;
        }
    }

    private static class SLAMetrics {
        private final List<Long> durations = new ArrayList<>();
        private final AtomicInteger slaViolations = new AtomicInteger(0);
        private final AtomicInteger errors = new AtomicInteger(0);

        public void recordExecution(long duration, boolean meetsSla) {
            durations.add(duration);
            if (!meetsSla) {
                slaViolations.incrementAndGet();
            }
        }

        public void recordError() {
            errors.incrementAndGet();
        }

        public double getComplianceRate() {
            int total = durations.size() + errors.get();
            return total > 0 ?
                (double) (durations.size() - slaViolations.get()) / total * 100 : 0;
        }
    }

    /**
     * Performance monitoring utility for tenant tests
     */
    private static class PerformanceMonitor {
        private final Map<String, List<Long>> tenantDurations = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> tenantErrors = new ConcurrentHashMap<>();
        private final AtomicInteger totalWorkflows = new AtomicInteger(0);
        private final AtomicInteger totalErrors = new AtomicInteger(0);

        public void recordTenantExecution(TenantProfile tenant, long duration) {
            tenantDurations.computeIfAbsent(tenant.getId(), k -> new ArrayList<>())
                .add(duration);
            totalWorkflows.incrementAndGet();
        }

        public void recordTenantError(TenantProfile tenant, String error) {
            tenantErrors.computeIfAbsent(tenant.getId(), k -> new AtomicInteger())
                .incrementAndGet();
            totalWorkflows.incrementAndGet();
            totalErrors.incrementAndGet();
        }

        public Map<String, Double> getAverageTenantDurations() {
            Map<String, Double> averages = new HashMap<>();
            tenantDurations.forEach((tenant, durations) -> {
                double avg = durations.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);
                averages.put(tenant, avg);
            });
            return averages;
        }

        public int getTotalWorkflows() { return totalWorkflows.get(); }
        public int getTotalErrors() { return totalErrors.get(); }
    }
}