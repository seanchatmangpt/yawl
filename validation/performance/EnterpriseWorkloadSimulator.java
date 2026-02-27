/*
 * YAWL v6.0.0-GA Validation
 * Enterprise Workload Simulator
 *
 * Simulates real enterprise workflows with varying complexity and priority
 * for production validation of YAWL v6.0.0-GA stateful engine
 */
package org.yawlfoundation.yawl.performance;

import org.yawlfoundation.yawl.elements.YAWLTask;
import org.yawlfoundation.yawl.elements.YAWLNet;
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.worklet.WorkletServiceGateway;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production workload simulator for YAWL v6.0.0-GA validation
 * Simulates real enterprise workflows with varying complexity and priority
 */
public class EnterpriseWorkloadSimulator {

    private final YAWLServiceGateway serviceGateway;
    private final WorkletServiceGateway workletService;
    private final PerformanceMonitor performanceMonitor;

    // Workload profiles matching production characteristics
    public enum WorkloadProfile {
        ENTERPRISE_MIXED(60, 30, 10, 1000),      // 60% simple, 30% complex, 10% high-priority
        PEAK_PROCESSING(80, 15, 5, 5000),        // High volume with burst patterns
        MULTI_TENANT(45, 35, 20, 2000),         // Distributed across 100 tenants
        SEASONAL_SPIKES(70, 20, 10, 8000)       // 5x normal load patterns
    }

    private final WorkloadProfile workloadProfile;
    private final TenantContext tenantContext;
    private final ConcurrentLinkedQueue<WorkloadRequest> workloadQueue;
    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final AtomicLong totalCasesProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    public EnterpriseWorkloadSimulator(YAWLServiceGateway serviceGateway,
                                      WorkletServiceGateway workletService,
                                      WorkloadProfile profile) {
        this.serviceGateway = serviceGateway;
        this.workletService = workletService;
        this.workloadProfile = profile;
        this.tenantContext = new TenantContext();
        this.workloadQueue = new ConcurrentLinkedQueue<>();
        this.performanceMonitor = new PerformanceMonitor();
    }

    /**
     * Simulates production workload with realistic patterns
     */
    public void simulateProductionLoad(int durationHours, int targetThroughput) throws InterruptedException {
        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(Duration.ofHours(durationHours));

        // Initialize workload generator threads
        int generatorThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService generatorExecutor = Executors.newFixedThreadPool(generatorThreads);

        // Initialize worker threads
        int workerThreads = targetThroughput / 10; // 10 cases per thread throughput
        ExecutorService workerExecutor = Executors.newFixedThreadPool(workerThreads);

        // Start workload generation
        generatorExecutor.submit(() -> generateWorkloadPattern(targetThroughput, endTime));

        // Start workload processing
        for (int i = 0; i < workerThreads; i++) {
            workerExecutor.submit(this::processWorkload);
        }

        // Monitor progress
        ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor();
        monitorExecutor.scheduleAtFixedRate(this::monitorProgress, 1, 1, TimeUnit.MINUTES);

        // Wait for duration to complete
        while (Instant.now().isBefore(endTime)) {
            Thread.sleep(1000);
        }

        // Shutdown gracefully
        generatorExecutor.shutdown();
        workerExecutor.shutdown();
        monitorExecutor.shutdown();

        // Generate final report
        generateProductionReport(startTime, endTime);
    }

    /**
     * Generates workload patterns based on production characteristics
     */
    private void generateWorkloadPattern(int targetThroughput, Instant endTime) {
        RandomWorkloadGenerator generator = new RandomWorkloadGenerator(workloadProfile);

        while (Instant.now().isBefore(endTime)) {
            int casesToGenerate = generator.generateBatchSize(targetThroughput);
            tenantContext.setCurrentTenant(generator.generateTenantContext());

            for (int i = 0; i < casesToGenerate; i++) {
                WorkloadRequest request = generator.generateWorkloadRequest();
                workloadQueue.offer(request);

                // Simulate burst patterns
                if (generator.isBurstPattern()) {
                    try {
                        Thread.sleep(generator.getBurstInterval());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            try {
                Thread.sleep(100); // Small delay between batches
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Processes workload from the queue
     */
    private void processWorkload() {
        activeWorkers.incrementAndGet();

        while (!Thread.currentThread().isInterrupted()) {
            WorkloadRequest request = workloadQueue.poll();
            if (request == null) {
                try {
                    Thread.sleep(50); // Wait for new work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            Instant caseStart = Instant.now();
            try {
                // Process the workload request
                processSingleCase(request);

                // Update metrics
                Instant caseEnd = Instant.now();
                long processingTime = Duration.between(caseStart, caseEnd).toMillis();
                totalCasesProcessed.incrementAndGet();
                totalProcessingTime.addAndGet(processingTime);

                // Monitor performance
                performanceMonitor.recordCaseProcessing(processingTime, request.getPriority());

            } catch (Exception e) {
                // Handle processing errors
                performanceMonitor.recordError(e.getClass().getSimpleName(), request.getCaseId());
            }
        }

        activeWorkers.decrementAndGet();
    }

    /**
     * Processes a single case
     */
    private void processSingleCase(WorkloadRequest request) throws Exception {
        String caseId = request.getCaseId();
        String workflowId = request.getWorkflowId();
        String tenantId = tenantContext.getCurrentTenant();

        // Create YAWL case with tenant context
        YNetRunner runner = serviceGateway.getNet(workflowId);
        if (runner == null) {
            throw new RuntimeException("Workflow not found: " + workflowId);
        }

        // Set tenant-specific attributes
        runner.setAttribute("tenantId", tenantId);
        runner.setAttribute("priority", request.getPriority());
        runner.setAttribute("workloadType", request.getType());

        // Execute the case
        String caseToken = runner.launchCase();

        // Monitor execution progress
        while (!runner.isCaseComplete(caseToken)) {
            Thread.sleep(10); // Poll for completion
            performanceMonitor.recordQueueLatency(10);
        }

        // Record successful completion
        performanceMonitor.recordSuccessfulCase(caseId);
    }

    /**
     * Monitors and reports progress
     */
    private void monitorProgress() {
        int queueSize = workloadQueue.size();
        int active = activeWorkers.get();
        long processed = totalCasesProcessed.get();
        long avgTime = totalCasesProcessed.get() > 0 ?
            totalProcessingTime.get() / totalCasesProcessed.get() : 0;

        System.out.printf("[%s] Progress: Queue=%d, Active=%d, Processed=%d, AvgTime=%dms%n",
            Instant.now(), queueSize, active, processed, avgTime);

        // Check for performance degradation
        performanceMonitor.checkPerformanceDegradation();
    }

    /**
     * Generates comprehensive production report
     */
    private void generateProductionReport(Instant startTime, Instant endTime) {
        Duration totalDuration = Duration.between(startTime, endTime);
        long totalCases = totalCasesProcessed.get();
        double throughputPerHour = totalCases / (totalDuration.toMinutes() / 60.0);
        double avgProcessingTime = totalCases > 0 ?
            (double) totalProcessingTime.get() / totalCases : 0;

        PerformanceReport report = new PerformanceReport();
        report.setStartTime(startTime);
        report.setEndTime(endTime);
        report.setTotalCases(totalCases);
        report.setThroughputPerHour(throughputPerHour);
        report.setAverageProcessingTime(avgProcessingTime);
        report.setWorkloadProfile(workloadProfile.name());
        report.setTenantCount(tenantContext.getTenantCount());
        report.setPerformanceMetrics(performanceMonitor.getMetrics());

        // Save report for analysis
        report.saveToFile("validation/reports/production-simulation-" +
            startTime.toString().replace(":", "-") + ".json");
    }

    /**
     * Workload Request container
     */
    private static class WorkloadRequest {
        private final String caseId;
        private final String workflowId;
        private final String caseData;
        private final String tenantId;
        private final int priority;
        private final String type;

        public WorkloadRequest(String caseId, String workflowId, String caseData,
                              String tenantId, int priority, String type) {
            this.caseId = caseId;
            this.workflowId = workflowId;
            this.caseData = caseData;
            this.tenantId = tenantId;
            this.priority = priority;
            this.type = type;
        }

        // Getters
        public String getCaseId() { return caseId; }
        public String getWorkflowId() { return workflowId; }
        public String getCaseData() { return caseData; }
        public String getTenantId() { return tenantId; }
        public int getPriority() { return priority; }
        public String getType() { return type; }
    }

    /**
     * Random workload generator
     */
    private static class RandomWorkloadGenerator {
        private final WorkloadProfile profile;
        private final Random random = new SecureRandom();
        private int burstCounter = 0;

        public RandomWorkloadGenerator(WorkloadProfile profile) {
            this.profile = profile;
        }

        public int generateBatchSize(int targetThroughput) {
            double variance = 0.3; // 30% variance around target
            double adjustedThroughput = targetThroughput * (1 + (random.nextDouble() - 0.5) * variance);
            return (int) Math.max(1, adjustedThroughput / 10); // Split into batches
        }

        public WorkloadRequest generateWorkloadRequest() {
            String caseId = "case-" + UUID.randomUUID().toString();
            String workflowId = selectWorkflow();
            String tenantId = "tenant-" + (random.nextInt(100) + 1);
            int priority = selectPriority();
            String type = selectWorkloadType();

            // Generate case data based on type
            String caseData = generateCaseData(type);

            return new WorkloadRequest(caseId, workflowId, caseData, tenantId, priority, type);
        }

        private String selectWorkflow() {
            // Simulate real workflow selection
            String[] workflows = {
                "simple-order-approval",
                "complex-employee-onboarding",
                "high-priority-crisis-response",
                "multi-tenant-document-processing"
            };
            return workflows[random.nextInt(workflows.length)];
        }

        private int selectPriority() {
            switch (profile) {
                case ENTERPRISE_MIXED:
                    return random.nextInt(100) < 60 ? 1 :
                           random.nextInt(100) < 90 ? 2 : 3;
                case PEAK_PROCESSING:
                    return 1; // All low priority during peak
                case MULTI_TENANT:
                    return random.nextInt(100) < 20 ? 2 : 1;
                case SEASONAL_SPIKES:
                    return random.nextInt(100) < 10 ? 3 : 1;
                default:
                    return 1;
            }
        }

        private String selectWorkloadType() {
            switch (profile) {
                case ENTERPRISE_MIXED:
                    return random.nextInt(100) < 60 ? "simple" :
                           random.nextInt(100) < 90 ? "complex" : "high-priority";
                case PEAK_PROCESSING:
                    return "simple";
                case MULTI_TENANT:
                    return "multi-tenant";
                case SEASONAL_SPIKES:
                    return "burst";
                default:
                    return "simple";
            }
        }

        private String generateCaseData(String type) {
            switch (type) {
                case "simple":
                    return "{\"customer\": \"ACME Corp\", \"amount\": 1000}";
                case "complex":
                    return "{\"employee\": \"John Doe\", \"department\": \"IT\", \"skills\": [\"java\", \"sql\"], \"certifications\": [\"AWS\"]}";
                case "high-priority":
                    return \"{\"incident\": \"server-down\", \"severity\": \"critical\", \"impact\": \"production\"}";
                case "multi-tenant":
                    return \"{\"tenant\": \"enterprise-customer\", \"data\": \"sensitive\", \"encryption\": \"aes256\"}";
                default:
                    return "{}";
            }
        }

        public boolean isBurstPattern() {
            burstCounter++;
            return burstCounter % 10 == 0; // Burst every 10 requests
        }

        public long getBurstInterval() {
            return random.nextInt(50) + 10; // 10-60ms bursts
        }
    }

    /**
     * Tenant context management
     */
    private static class TenantContext {
        private final Map<String, String> tenantAttributes = new ConcurrentHashMap<>();
        private final AtomicInteger tenantCounter = new AtomicInteger(0);

        public void setCurrentTenant(String tenantId) {
            tenantAttributes.put("currentTenant", tenantId);
        }

        public String getCurrentTenant() {
            return tenantAttributes.get("currentTenant");
        }

        public int getTenantCount() {
            return tenantCounter.get();
        }
    }
}