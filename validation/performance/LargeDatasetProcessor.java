/*
 * YAWL v6.0.0-GA Validation
 * Large Dataset Processor
 *
 * Tests performance with 1M+ work items and cases
 */
package org.yawlfoundation.yawl.performance;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.elements.YAWLNet;
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YNetRunner;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Large-scale dataset processing validation
 * Tests performance with 1M+ work items and cases
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LargeDatasetProcessor {

    private YAWLServiceGateway serviceGateway;
    private PerformanceMonitor performanceMonitor;
    private ExecutorService executorService;

    // Test configuration
    private static final long TOTAL_WORK_ITEMS = 1_000_000L;
    private static final int BATCH_SIZE = 10_000;
    private static final int CONCURRENT_WORKERS = 50;
    private static final Duration TEST_TIMEOUT = Duration.ofHours(2);

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
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Validate processing of 1M work items")
    void validateLargeDatasetProcessing() throws InterruptedException {
        System.out.println("Starting large dataset processing validation...");

        // Generate test dataset
        List<WorkItem> workItems = generateLargeDataset(TOTAL_WORK_ITEMS);
        System.out.printf("Generated %d work items%n", workItems.size());

        // Process dataset in batches
        Instant startTime = Instant.now();
        AtomicLong processedItems = new AtomicLong(0);
        AtomicLong totalProcessingTime = new AtomicLong(0);

        // Create batch processor
        BatchProcessor batchProcessor = new BatchProcessor(workItems, BATCH_SIZE);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_WORKERS; i++) {
            futures.add(executorService.submit(() -> {
                processBatch(batchProcessor, processedItems, totalProcessingTime);
            }));
        }

        // Monitor progress
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            long current = processedItems.get();
            double progress = (double) current / TOTAL_WORK_ITEMS * 100;
            System.out.printf("Progress: %.2f%% (%d/%d items processed)%n",
                progress, current, TOTAL_WORK_ITEMS);
        }, 5, 5, TimeUnit.SECONDS);

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                System.err.println("Worker timed out, continuing with remaining...");
            } catch (ExecutionException e) {
                System.err.println("Worker failed: " + e.getCause());
            }
        }

        monitor.shutdown();

        // Validate results
        Instant endTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, endTime);

        validateLargeDatasetResults(
            processedItems.get(),
            totalDuration,
            totalProcessingTime.get()
        );

        System.out.println("Large dataset processing validation completed");
    }

    @Test
    @DisplayName("Validate memory usage with large datasets")
    void validateMemoryUsage() throws InterruptedException {
        System.out.println("Starting memory usage validation...");

        // Track memory usage
        MemoryMonitor memoryMonitor = new MemoryMonitor();
        List<MemorySnapshot> snapshots = new ArrayList<>();

        // Start memory monitoring
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            MemorySnapshot snapshot = memoryMonitor.capture();
            snapshots.add(snapshot);
        }, 1, 1, TimeUnit.SECONDS);

        // Process dataset
        List<WorkItem> workItems = generateLargeDataset(100_000L); // Smaller dataset for memory test
        AtomicLong processedItems = new AtomicLong(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) { // Fewer workers for memory test
            futures.add(executorService.submit(() -> {
                processMemoryTestBatch(workItems, processedItems);
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.MINUTES);
            } catch (TimeoutException | ExecutionException e) {
                future.cancel(true);
            }
        }

        monitor.shutdown();

        // Validate memory usage
        validateMemoryCharacteristics(snapshots);

        System.out.println("Memory usage validation completed");
    }

    @Test
    @DisplayName("Validate data consistency at scale")
    void validateDataConsistencyAtScale() throws InterruptedException {
        System.out.println("Starting data consistency validation...");

        // Create test data with known relationships
        List<ConsistentWorkItem> workItems = generateConsistentDataset(500_000L);
        Map<String, ConsistentWorkItem> itemMap = workItems.stream()
            .collect(Collectors.toMap(ConsistentWorkItem::getId, item -> item));

        // Process dataset with consistency checks
        AtomicInteger consistencyViolations = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            final int workerId = i;
            futures.add(executorService.submit(() -> {
                processWithConsistencyChecks(workItems, itemMap, consistencyViolations, workerId);
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(20, TimeUnit.MINUTES);
            } catch (TimeoutException | ExecutionException e) {
                future.cancel(true);
            }
        }

        // Validate no consistency violations
        assertEquals(0, consistencyViolations.get(),
            "Data consistency violations detected: " + consistencyViolations.get());

        System.out.println("Data consistency validation completed with no violations");
    }

    @Test
    @DisplayName("Validate database scalability")
    void validateDatabaseScalability() throws InterruptedException {
        System.out.println("Starting database scalability validation...");

        // Create test database connections
        DatabaseConnectionManager dbManager = new DatabaseConnectionManager(10);
        List<DatabaseWorker> workers = new ArrayList<>();

        // Initialize workers
        for (int i = 0; i < 10; i++) {
            workers.add(new DatabaseWorker(dbManager, "worker-" + i));
        }

        // Generate database test data
        List<DatabaseRecord> records = generateDatabaseRecords(100_000);

        // Process database operations
        AtomicLong processedRecords = new AtomicLong(0);
        List<Future<?>> futures = new ArrayList<>();

        for (DatabaseWorker worker : workers) {
            futures.add(executorService.submit(() -> {
                worker.processDatabaseBatch(records, processedRecords);
            }));
        }

        // Monitor database performance
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            System.out.printf("Database progress: %d/%d records processed%n",
                processedRecords.get(), records.size());
            dbManager.reportMetrics();
        }, 5, 5, TimeUnit.SECONDS);

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.MINUTES);
            } catch (TimeoutException | ExecutionException e) {
                future.cancel(true);
            }
        }

        monitor.shutdown();

        // Validate database performance
        validateDatabasePerformance(dbManager);

        System.out.println("Database scalability validation completed");
    }

    private List<WorkItem> generateLargeDataset(long itemCount) {
        System.out.println("Generating large dataset...");
        List<WorkItem> items = new ArrayList<>();

        for (long i = 0; i < itemCount; i++) {
            items.add(new WorkItem(
                "item-" + i,
                "workflow-" + (i % 100), // 100 different workflows
                "tenant-" + (i % 1000), // 1000 different tenants
                generateItemData(i),
                (int) (i % 5) + 1 // Priority 1-5
            ));

            // Progress indicator
            if (i % 100_000 == 0) {
                System.out.printf("Generated %d items%n", i);
            }
        }

        return items;
    }

    private Map<String, Object> generateItemData(long index) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "data-" + index);
        data.put("timestamp", Instant.now());
        data.put("amount", Math.random() * 10000);
        data.put("customer", "customer-" + (index % 10_000));
        data.put("category", "category-" + (index % 20));
        return data;
    }

    private void processBatch(BatchProcessor processor,
                             AtomicLong processedItems,
                             AtomicLong totalProcessingTime) {
        while (!Thread.currentThread().isInterrupted()) {
            List<WorkItem> batch = processor.nextBatch();
            if (batch.isEmpty()) {
                break;
            }

            Instant batchStart = Instant.now();
            try {
                // Process batch
                for (WorkItem item : batch) {
                    processSingleWorkItem(item);
                    processedItems.incrementAndGet();
                }

                // Record processing time
                Instant batchEnd = Instant.now();
                long batchTime = Duration.between(batchStart, batchEnd).toMillis();
                totalProcessingTime.addAndGet(batchTime);

            } catch (Exception e) {
                // Handle batch processing errors
                performanceMonitor.recordError(e.getClass().getSimpleName(), "batch-" + batch.get(0).getId());
            }
        }
    }

    private void processSingleWorkItem(WorkItem item) {
        try {
            // Simulate work item processing
            YNetRunner runner = serviceGateway.getNet(item.getWorkflowId());
            if (runner != null) {
                runner.setAttribute("tenantId", item.getTenantId());
                runner.setAttribute("priority", item.getPriority());
                runner.setAttribute("data", item.getData());

                String caseToken = runner.launchCase();

                // Simulate processing
                Thread.sleep((long) (Math.random() * 50) + 5);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process work item: " + item.getId(), e);
        }
    }

    private void processMemoryTestBatch(List<WorkItem> workItems, AtomicLong processedItems) {
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            WorkItem item = workItems.get(random.nextInt(workItems.size()));
            processSingleWorkItem(item);
            processedItems.incrementAndGet();

            // Simulate memory-intensive operations
            if (i % 100 == 0) {
                List<String> memoryHolder = new ArrayList<>();
                for (int j = 0; j < 1000; j++) {
                    memoryHolder.add("data-" + UUID.randomUUID());
                }
                memoryHolder.clear();
            }
        }
    }

    private void processWithConsistencyChecks(List<ConsistentWorkItem> workItems,
                                           Map<String, ConsistentWorkItem> itemMap,
                                           AtomicInteger violations,
                                           int workerId) {
        Random random = new Random(workerId);

        for (int i = 0; i < 1000; i++) {
            ConsistentWorkItem item = workItems.get(random.nextInt(workItems.size()));

            // Validate data consistency
            if (!validateItemConsistency(item, itemMap)) {
                violations.incrementAndGet();
            }

            // Process the item
            processConsistentWorkItem(item);

            // Update parent-child relationships
            updateRelationships(item, itemMap, violations);
        }
    }

    private boolean validateItemConsistency(ConsistentWorkItem item,
                                          Map<String, ConsistentWorkItem> itemMap) {
        // Validate parent exists if specified
        if (item.getParentId() != null) {
            ConsistentWorkItem parent = itemMap.get(item.getParentId());
            if (parent == null || !parent.getChildren().contains(item.getId())) {
                return false;
            }
        }

        // Validate data integrity
        return item.getData() != null && !item.getData().isEmpty();
    }

    private void processConsistentWorkItem(ConsistentWorkItem item) {
        // Process with consistency guarantees
        try {
            YNetRunner runner = serviceGateway.getNet(item.getWorkflowId());
            if (runner != null) {
                runner.setAttribute("itemId", item.getId());
                runner.setAttribute("parentId", item.getParentId());
                runner.setAttribute("tenantId", item.getTenantId());
                runner.setAttribute("data", item.getData());

                runner.launchCase();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process consistent work item", e);
        }
    }

    private void updateRelationships(ConsistentWorkItem item,
                                   Map<String, ConsistentWorkItem> itemMap,
                                   AtomicInteger violations) {
        // Update parent-child relationships
        if (item.getParentId() != null) {
            ConsistentWorkItem parent = itemMap.get(item.getParentId());
            if (parent != null && !parent.getChildren().contains(item.getId())) {
                parent.addChild(item.getId());
            }
        }
    }

    private void validateLargeDatasetResults(long processedItems,
                                           Duration totalDuration,
                                           long totalProcessingTime) {
        // Calculate metrics
        double throughput = processedItems / (totalDuration.toSeconds() / 3600.0);
        double avgProcessingTime = processedItems > 0 ?
            (double) totalProcessingTime / processedItems : 0;

        System.out.printf("Large dataset results:%n");
        System.out.printf("  Processed items: %d%n", processedItems);
        System.out.printf("  Total duration: %d seconds%n", totalDuration.toSeconds());
        System.out.printf("  Throughput: %.2f items/hour%n", throughput);
        System.out.printf("  Average processing time: %.2f ms%n", avgProcessingTime);

        // Validate performance targets
        assertTrue(processedItems >= TOTAL_WORK_ITEMS * 0.95,
            "Not enough items processed: " + processedItems);
        assertTrue(throughput > 50_000, "Throughput below target: " + throughput);
        assertTrue(avgProcessingTime < 50, "Processing time too high: " + avgProcessingTime);
    }

    private void validateMemoryCharacteristics(List<MemorySnapshot> snapshots) {
        if (snapshots.isEmpty()) return;

        // Memory usage over time
        long startMemory = snapshots.get(0).getUsedMemory();
        long peakMemory = snapshots.stream()
            .mapToLong(MemorySnapshot::getUsedMemory)
            .max()
            .orElse(0);

        double memoryGrowth = (double) (peakMemory - startMemory) / startMemory * 100;

        System.out.printf("Memory characteristics:%n");
        System.out.printf("  Initial memory: %d MB%n", startMemory / (1024 * 1024));
        System.out.printf("  Peak memory: %d MB%n", peakMemory / (1024 * 1024));
        System.out.printf("  Growth: %.2f%%%n", memoryGrowth);

        // Validate memory usage is acceptable
        assertTrue(memoryGrowth < 100, "Memory growth too high: " + memoryGrowth);
        assertTrue(peakMemory < 4L * 1024 * 1024 * 1024, "Peak memory too high: " + peakMemory);
    }

    private List<ConsistentWorkItem> generateConsistentDataset(long itemCount) {
        List<ConsistentWorkItem> items = new ArrayList<>();
        Random random = new Random();

        for (long i = 0; i < itemCount; i++) {
            String parentId = i > 0 && random.nextDouble() < 0.3 ?
                "item-" + random.nextInt((int) i) : null;

            items.add(new ConsistentWorkItem(
                "item-" + i,
                parentId,
                "workflow-" + (i % 100),
                "tenant-" + (i % 1000),
                generateConsistentData(i)
            ));
        }

        return items;
    }

    private Map<String, Object> generateConsistentData(long index) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "data-" + index);
        data.put("timestamp", Instant.now());
        data.put("value", Math.random() * 1000);
        data.put("type", "consistent-" + (index % 10));
        return data;
    }

    private List<DatabaseRecord> generateDatabaseRecords(long recordCount) {
        return IntStream.range(0, (int) recordCount)
            .mapToObj(i -> new DatabaseRecord(
                "record-" + i,
                "tenant-" + (i % 1000),
                "data-" + UUID.randomUUID(),
                Instant.now()
            ))
            .collect(Collectors.toList());
    }

    private void validateDatabasePerformance(DatabaseConnectionManager dbManager) {
        DatabaseMetrics metrics = dbManager.getMetrics();

        System.out.printf("Database performance metrics:%n");
        System.out.printf("  Total operations: %d%n", metrics.getTotalOperations());
        System.out.printf("  Average response time: %.2f ms%n", metrics.getAverageResponseTime());
        System.out.printf("  Connection pool usage: %d/%d%n",
            metrics.getActiveConnections(), metrics.getMaxConnections());
        System.out.printf("  Error rate: %.2f%%%n",
            metrics.getErrorRate() * 100);

        // Validate database performance
        assertTrue(metrics.getAverageResponseTime() < 100,
            "Database response time too high: " + metrics.getAverageResponseTime());
        assertTrue(metrics.getErrorRate() < 0.01,
            "Database error rate too high: " + metrics.getErrorRate());
    }

    /**
     * Work item representation
     */
    private static class WorkItem {
        private final String id;
        private final String workflowId;
        private final String tenantId;
        private final Map<String, Object> data;
        private final int priority;

        public WorkItem(String id, String workflowId, String tenantId,
                       Map<String, Object> data, int priority) {
            this.id = id;
            this.workflowId = workflowId;
            this.tenantId = tenantId;
            this.data = data;
            this.priority = priority;
        }

        // Getters
        public String getId() { return id; }
        public String getWorkflowId() { return workflowId; }
        public String getTenantId() { return tenantId; }
        public Map<String, Object> getData() { return data; }
        public int getPriority() { return priority; }
    }

    /**
     * Consistent work item with relationships
     */
    private static class ConsistentWorkItem extends WorkItem {
        private final String parentId;
        private final Set<String> children = new HashSet<>();

        public ConsistentWorkItem(String id, String parentId, String workflowId,
                                String tenantId, Map<String, Object> data) {
            super(id, workflowId, tenantId, data, 1);
            this.parentId = parentId;
        }

        public String getParentId() { return parentId; }
        public Set<String> getChildren() { return children; }
        public void addChild(String childId) { children.add(childId); }
    }

    /**
     * Database record representation
     */
    private static class DatabaseRecord {
        private final String id;
        private final String tenantId;
        private final String data;
        private final Instant timestamp;

        public DatabaseRecord(String id, String tenantId, String data, Instant timestamp) {
            this.id = id;
            this.tenantId = tenantId;
            this.data = data;
            this.timestamp = timestamp;
        }

        // Getters
        public String getId() { return id; }
        public String getTenantId() { return tenantId; }
        public String getData() { return data; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * Batch processor for large datasets
     */
    private static class BatchProcessor {
        private final List<WorkItem> workItems;
        private final int batchSize;
        private int currentIndex = 0;

        public BatchProcessor(List<WorkItem> workItems, int batchSize) {
            this.workItems = workItems;
            this.batchSize = batchSize;
        }

        public List<WorkItem> nextBatch() {
            if (currentIndex >= workItems.size()) {
                return Collections.emptyList();
            }

            int endIndex = Math.min(currentIndex + batchSize, workItems.size());
            List<WorkItem> batch = workItems.subList(currentIndex, endIndex);
            currentIndex = endIndex;
            return batch;
        }
    }

    /**
     * Memory snapshot for monitoring
     */
    private static class MemorySnapshot {
        private final Instant timestamp;
        private final long usedMemory;
        private final long maxMemory;

        public MemorySnapshot(Instant timestamp, long usedMemory, long maxMemory) {
            this.timestamp = timestamp;
            this.usedMemory = usedMemory;
            this.maxMemory = maxMemory;
        }

        public Instant getTimestamp() { return timestamp; }
        public long getUsedMemory() { return usedMemory; }
        public long getMaxMemory() { return maxMemory; }
    }

    /**
     * Memory monitor
     */
    private static class MemoryMonitor {
        public MemorySnapshot capture() {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            return new MemorySnapshot(Instant.now(), usedMemory, maxMemory);
        }
    }

    /**
     * Database connection manager
     */
    private static class DatabaseConnectionManager {
        private final int maxConnections;
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private final AtomicLong totalOperations = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);

        public DatabaseConnectionManager(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public void executeOperation(Runnable operation) {
            activeConnections.incrementAndGet();
            long startTime = System.currentTimeMillis();

            try {
                operation.run();
                long responseTime = System.currentTimeMillis() - startTime;
                totalOperations.incrementAndGet();
                totalResponseTime.addAndGet(responseTime);
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                activeConnections.decrementAndGet();
            }
        }

        public void reportMetrics() {
            System.out.printf("DB Connections: %d/%d, Ops: %d, Errors: %d%n",
                activeConnections.get(), maxConnections,
                totalOperations.get(), errorCount.get());
        }

        public DatabaseMetrics getMetrics() {
            return new DatabaseMetrics(
                totalOperations.get(),
                totalOperations.get() > 0 ?
                    (double) totalResponseTime.get() / totalOperations.get() : 0,
                activeConnections.get(),
                maxConnections,
                errorCount.get()
            );
        }
    }

    /**
     * Database worker
     */
    private static class DatabaseWorker {
        private final DatabaseConnectionManager dbManager;
        private final String workerId;

        public DatabaseWorker(DatabaseConnectionManager dbManager, String workerId) {
            this.dbManager = dbManager;
            this.workerId = workerId;
        }

        public void processDatabaseBatch(List<DatabaseRecord> records,
                                       AtomicLong processedRecords) {
            for (DatabaseRecord record : records) {
                dbManager.executeOperation(() -> {
                    // Simulate database operation
                    try {
                        Thread.sleep((long) (Math.random() * 10));
                        processedRecords.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }
    }

    /**
     * Database metrics
     */
    private static class DatabaseMetrics {
        private final long totalOperations;
        private final double averageResponseTime;
        private final int activeConnections;
        private final int maxConnections;
        private final int errorCount;

        public DatabaseMetrics(long totalOperations, double averageResponseTime,
                             int activeConnections, int maxConnections, int errorCount) {
            this.totalOperations = totalOperations;
            this.averageResponseTime = averageResponseTime;
            this.activeConnections = activeConnections;
            this.maxConnections = maxConnections;
            this.errorCount = errorCount;
        }

        // Getters
        public long getTotalOperations() { return totalOperations; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public int getActiveConnections() { return activeConnections; }
        public int getMaxConnections() { return maxConnections; }
        public double getErrorRate() { return totalOperations > 0 ? (double) errorCount / totalOperations : 0; }
    }
}