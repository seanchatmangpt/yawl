# Mixed Workload Simulator & TestDataGenerator Usage Examples

**For**: LongRunningStressTest Implementation Team
**Date**: 2026-02-28
**Audience**: Engineers implementing Phase 1 of 1M Case Stress Test

---

## Quick Start

### Import Statements

```java
import org.yawlfoundation.yawl.benchmark.TestDataGenerator;
import org.yawlfoundation.yawl.benchmark.soak.MixedWorkloadSimulator;
import org.yawlfoundation.yawl.benchmark.soak.MixedWorkloadSimulator.WorkloadEvent;

import java.util.List;
import java.util.Map;
```

### Minimal Example (Real-Time Event Stream)

```java
@Test
void testRealisticEventStream() throws InterruptedException {
    // Create simulator: 10 cases/sec, 150ms median task time
    MixedWorkloadSimulator simulator = new MixedWorkloadSimulator(10.0, 150L);

    // Process events for 60 seconds
    long startTime = System.currentTimeMillis();
    long durationMs = 60_000;

    while (System.currentTimeMillis() - startTime < durationMs) {
        WorkloadEvent event = simulator.nextEvent();

        System.out.printf("Event: %s (delay=%dms)%n",
                event.eventType(), event.delayMs());

        // Process event based on type
        switch (event.eventType()) {
            case "case_arrival" -> System.out.println("  → Creating new case");
            case "task_execution" -> System.out.println("  → Executing work item");
            case "case_completion" -> System.out.println("  → Completing case");
        }
    }
}
```

**Output Example**:
```
Event: task_execution (delay=287ms)
  → Executing work item
Event: case_arrival (delay=42ms)
  → Creating new case
Event: case_completion (delay=115ms)
  → Completing case
```

---

## Pattern 1: Real-Time Workload (LongRunningStressTest Variant 1)

### Setup

```java
public class LongRunningStressTest {
    private MixedWorkloadSimulator simulator;
    private TestDataGenerator dataGenerator;
    private YStatelessEngine engine;
    private Map<String, String> workflowSpecs;

    @BeforeEach
    void setUp() {
        // Initialize simulator for realistic load
        simulator = new MixedWorkloadSimulator(
            10.0,    // 10 cases per second target
            150L     // 150ms median task execution time
        );

        // Get diverse workflow specifications
        dataGenerator = new TestDataGenerator();
        workflowSpecs = dataGenerator.newRealisticMixedWorkload(1_000_000, 150);

        // Initialize real engine
        engine = new YStatelessEngine();
    }

    @Test
    @DisplayName("Long-running stress test with real-time event stream")
    void testLongRunningRealTimeWorkload() throws InterruptedException {
        long testDurationSeconds = 3600;  // 1 hour
        long startTime = System.nanoTime();
        int caseCount = 0;
        int taskCount = 0;
        int completionCount = 0;

        while (System.nanoTime() - startTime < testDurationSeconds * 1_000_000_000L) {
            WorkloadEvent event = simulator.nextEvent();

            switch (event.eventType()) {
                case "case_arrival" -> {
                    caseCount++;
                    createNewCase();
                }
                case "task_execution" -> {
                    taskCount++;
                    executeQueuedWorkItem();
                }
                case "case_completion" -> {
                    completionCount++;
                    completeRunningCase();
                }
            }

            // Optional: Log progress every 10000 events
            if ((caseCount + taskCount + completionCount) % 10000 == 0) {
                System.out.printf(
                    "Progress: %d cases, %d tasks, %d completions%n",
                    caseCount, taskCount, completionCount);
            }
        }

        System.out.printf("Test Complete: %d cases, %d tasks, %d completions%n",
            caseCount, taskCount, completionCount);
    }

    private void createNewCase() {
        // Select random workflow pattern
        String workflowKey = selectRandomKey(workflowSpecs.keySet());
        String specXml = workflowSpecs.get(workflowKey);

        try {
            YSpecification spec = engine.unmarshalSpecification(specXml);
            YWorkItem workItem = engine.startCase(spec, null, null);
            // Track case in metrics
        } catch (Exception e) {
            System.err.println("Failed to create case: " + e.getMessage());
        }
    }

    private void executeQueuedWorkItem() {
        // Get next work item from queue
        List<YWorkItem> workItems = engine.getWorkItems();
        if (!workItems.isEmpty()) {
            YWorkItem item = workItems.get(0);
            try {
                engine.completeWorkItem(item, ...);
            } catch (Exception e) {
                System.err.println("Failed to execute work item: " + e.getMessage());
            }
        }
    }

    private void completeRunningCase() {
        // Get active cases and complete one if ready
        // Implementation depends on case tracking mechanism
    }

    private <T> T selectRandomKey(Collection<T> collection) {
        List<T> list = new ArrayList<>(collection);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
```

---

## Pattern 2: Precomputed Sequence (LongRunningStressTest Variant 2)

### For Deterministic, Reproducible Testing

```java
public class LongRunningStressTestWithPrecomputedLoad {
    private MixedWorkloadSimulator simulator;
    private List<WorkloadEvent> events;

    @BeforeEach
    void setUp() {
        simulator = new MixedWorkloadSimulator(10.0, 150L);

        // Pre-compute 24-hour arrival sequence for deterministic replay
        long durationSeconds = 24 * 3600;  // 24 hours
        events = simulator.generateArrivalSequence(durationSeconds, 10);

        System.out.printf("Pre-computed %d events for %d seconds%n",
            events.size(), durationSeconds);
    }

    @Test
    @DisplayName("Stress test with precomputed deterministic workload")
    void testWithPrecomputedSequence() {
        int caseCount = 0;
        int taskCount = 0;
        int completionCount = 0;

        for (WorkloadEvent event : events) {
            switch (event.eventType()) {
                case "case_arrival" -> caseCount++;
                case "task_execution" -> taskCount++;
                case "case_completion" -> completionCount++;
            }

            // Sleep to maintain realistic timing
            try {
                Thread.sleep(event.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.printf("Completed: %d cases, %d tasks, %d completions%n",
            caseCount, taskCount, completionCount);
    }

    @Test
    @DisplayName("Run precomputed sequence multiple times")
    void testMultipleRuns() {
        for (int run = 1; run <= 3; run++) {
            System.out.printf("=== Run %d ===%n", run);

            int totalEvents = 0;
            for (WorkloadEvent event : events) {
                totalEvents++;
                // Process event...
            }

            simulator.resetArrivalSequence();
            System.out.printf("Run %d: %d events processed%n", run, totalEvents);
        }
    }
}
```

---

## Pattern 3: Workflow Pattern Selection

### Using TestDataGenerator for Realistic Mix

```java
@Test
void demonstrateWorkflowSelection() {
    TestDataGenerator generator = new TestDataGenerator();
    Map<String, String> workloads = generator.newRealisticMixedWorkload(
        1_000_000,  // Target 1M cases (informational)
        150);       // 150ms baseline task rate (informational)

    System.out.println("Available workflow patterns:");
    for (String key : workloads.keySet()) {
        System.out.println("  - " + key);
    }

    // Expected output:
    // Available workflow patterns:
    //   - sequential_primary
    //   - sequential_2task
    //   - sequential_4task
    //   - parallel_andsplit
    //   - loop_sequential
    //   - complex_multichoice
    //   - complex_exclusive

    // Demonstrate random selection
    Random random = ThreadLocalRandom.current();
    for (int i = 0; i < 5; i++) {
        List<String> keys = new ArrayList<>(workloads.keySet());
        String selected = keys.get(random.nextInt(keys.size()));
        String specXml = workloads.get(selected);

        System.out.printf("Selected: %s (XML length: %d bytes)%n",
            selected, specXml.length());
    }
}

// Output example:
// Available workflow patterns:
//   - sequential_primary
//   - sequential_2task
//   - sequential_4task
//   - parallel_andsplit
//   - loop_sequential
//   - complex_multichoice
//   - complex_exclusive
// Selected: sequential_4task (XML length: 1247 bytes)
// Selected: parallel_andsplit (XML length: 1389 bytes)
// Selected: complex_exclusive (XML length: 1512 bytes)
// Selected: sequential_2task (XML length: 987 bytes)
// Selected: loop_sequential (XML length: 987 bytes)
```

---

## Pattern 4: Metrics Collection with Simulator

### Track Performance During Stress Test

```java
public class StressTestWithMetrics {
    private MixedWorkloadSimulator simulator;
    private List<WorkloadEvent> events;
    private BenchmarkMetricsCollector metrics;

    @BeforeEach
    void setUp() {
        simulator = new MixedWorkloadSimulator(10.0, 150L);
        events = simulator.generateArrivalSequence(3600, 10);  // 1 hour
        metrics = new BenchmarkMetricsCollector();
    }

    @Test
    void testWithMetricsCollection() {
        long startTime = System.currentTimeMillis();
        int processedEvents = 0;

        try (var metricsCollector = metrics.startCollection(5)) {  // Collect every 5 sec
            for (WorkloadEvent event : events) {
                processEvent(event);
                processedEvents++;

                // Sample metrics periodically
                if (processedEvents % 100 == 0) {
                    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    double eventsPerSecond = (double) processedEvents / elapsedSeconds;
                    System.out.printf(
                        "Progress: %d events in %ds (%.1f events/sec)%n",
                        processedEvents, elapsedSeconds, eventsPerSecond);
                }

                Thread.sleep(event.delayMs());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Generate report
        long totalDurationSeconds = (System.currentTimeMillis() - startTime) / 1000;
        double throughput = (double) processedEvents / totalDurationSeconds;

        System.out.printf(
            "Test Summary:%n  Events: %d%n  Duration: %ds%n  Throughput: %.1f events/sec%n",
            processedEvents, totalDurationSeconds, throughput);
    }

    private void processEvent(WorkloadEvent event) {
        // Implementation here
    }
}
```

---

## Pattern 5: Distribution Verification

### Validate Poisson/Exponential Properties

```java
@Test
void verifyDistributionProperties() {
    MixedWorkloadSimulator simulator = new MixedWorkloadSimulator(10.0, 150L);
    List<WorkloadEvent> events = simulator.generateArrivalSequence(60, 10);

    // Collect event type counts
    Map<String, Long> eventCounts = new HashMap<>();
    List<Long> taskExecutionTimes = new ArrayList<>();

    for (WorkloadEvent event : events) {
        eventCounts.merge(event.eventType(), 1L, Long::sum);

        if ("task_execution".equals(event.eventType())) {
            taskExecutionTimes.add(event.delayMs());
        }
    }

    // Verify distribution
    long total = eventCounts.values().stream().mapToLong(Long::longValue).sum();
    System.out.println("Event Distribution:");
    for (var entry : eventCounts.entrySet()) {
        double percentage = (double) entry.getValue() / total * 100;
        System.out.printf("  %s: %.1f%% (%d)%n",
            entry.getKey(), percentage, entry.getValue());
    }

    // Verify exponential task times
    if (!taskExecutionTimes.isEmpty()) {
        double mean = taskExecutionTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);

        System.out.printf("Task Execution Time Distribution:%n");
        System.out.printf("  Median configured: 150ms%n");
        System.out.printf("  Mean observed: %.1fms%n", mean);
        System.out.printf("  Expected mean (median/ln(2)): %.1fms%n", 150 / Math.log(2));
        System.out.printf("  Count: %d%n", taskExecutionTimes.size());
    }
}

// Output example:
// Event Distribution:
//   task_execution: 68.2% (239)
//   case_arrival: 21.6% (76)
//   case_completion: 10.2% (36)
// Task Execution Time Distribution:
//   Median configured: 150ms
//   Mean observed: 215.3ms
//   Expected mean (median/ln(2)): 216.5ms
//   Count: 239
```

---

## Pattern 6: Multi-Threaded Simulation

### Thread-Safe Event Generation

```java
@Test
void testThreadSafeSimulation() throws InterruptedException {
    MixedWorkloadSimulator simulator = new MixedWorkloadSimulator(10.0, 150L);

    int numThreads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    List<Future<Integer>> futures = new ArrayList<>();

    for (int t = 0; t < numThreads; t++) {
        futures.add(executor.submit(() -> {
            int eventsGenerated = 0;
            try {
                for (int i = 0; i < 100; i++) {
                    WorkloadEvent event = simulator.nextEvent();
                    // Process event
                    eventsGenerated++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return eventsGenerated;
        }));
    }

    int totalEvents = 0;
    for (Future<Integer> future : futures) {
        totalEvents += future.get();
    }

    executor.shutdown();

    System.out.printf("Generated %d events from %d threads%n",
        totalEvents, numThreads);
    // Expected: 400 events total (100 × 4)
}
```

---

## Error Handling Examples

### Parameter Validation

```java
@Test
void demonstrateParameterValidation() {
    // Valid configuration
    assertDoesNotThrow(
        () -> new MixedWorkloadSimulator(10.0, 150L),
        "Valid parameters should not throw");

    // Invalid lambda (negative)
    assertThrows(
        IllegalArgumentException.class,
        () -> new MixedWorkloadSimulator(-5.0, 150L),
        "Should reject negative lambda");

    // Invalid median (zero)
    assertThrows(
        IllegalArgumentException.class,
        () -> new MixedWorkloadSimulator(10.0, 0L),
        "Should reject zero median");

    // Invalid event type
    assertThrows(
        IllegalArgumentException.class,
        () -> new MixedWorkloadSimulator.WorkloadEvent(
            Instant.now(), "invalid_type", 100L),
        "Should reject invalid event type");
}
```

### Interrupt Handling

```java
@Test
void demonstrateInterruptHandling() throws InterruptedException {
    MixedWorkloadSimulator simulator = new MixedWorkloadSimulator(10.0, 150L);

    Thread eventThread = new Thread(() -> {
        try {
            for (int i = 0; i < 1000; i++) {
                WorkloadEvent event = simulator.nextEvent();
                System.out.println("Generated: " + event.eventType());
            }
        } catch (InterruptedException e) {
            System.out.println("Event generation interrupted");
            Thread.currentThread().interrupt();
        }
    });

    eventThread.start();
    Thread.sleep(500);  // Let it run a bit
    eventThread.interrupt();
    eventThread.join();

    System.out.println("Test completed with interruption");
}
```

---

## Integration with LongRunningStressTest

### Complete Test Structure

```java
@DisplayName("LongRunningStressTest with Mixed Workload")
public class LongRunningStressTest {

    private MixedWorkloadSimulator workloadSimulator;
    private TestDataGenerator testDataGenerator;
    private YStatelessEngine engine;
    private BenchmarkMetricsCollector metrics;
    private Map<String, String> workflowSpecs;

    private int caseCounter = 0;
    private int taskCounter = 0;
    private int completionCounter = 0;

    @BeforeEach
    void setUp() {
        // 1. Initialize simulator
        workloadSimulator = new MixedWorkloadSimulator(
            10.0,    // 10 cases/sec
            150L     // 150ms median task time
        );

        // 2. Load workflow specifications
        testDataGenerator = new TestDataGenerator();
        workflowSpecs = testDataGenerator.newRealisticMixedWorkload(
            1_000_000,  // Target 1M cases
            150);       // 150ms baseline

        // 3. Initialize engine
        engine = new YStatelessEngine();

        // 4. Setup metrics
        metrics = new BenchmarkMetricsCollector();
    }

    @Test
    @DisplayName("Execute 24-hour stress test with realistic workload")
    @Timeout(value = 24, unit = TimeUnit.HOURS)
    void testStress24Hours() throws InterruptedException {
        long testDurationSeconds = 24 * 3600;
        long startTime = System.nanoTime();

        // Pre-compute arrival sequence
        List<WorkloadEvent> events = workloadSimulator.generateArrivalSequence(
            testDurationSeconds, 10);

        System.out.printf("Starting 24h stress test with %d precomputed events%n",
            events.size());

        try (var collector = metrics.startCollection(300)) {  // Collect every 5 min
            for (WorkloadEvent event : events) {
                if (System.nanoTime() - startTime > testDurationSeconds * 1_000_000_000L) {
                    break;
                }

                processWorkloadEvent(event);

                // Log progress every 10K events
                int totalProcessed = caseCounter + taskCounter + completionCounter;
                if (totalProcessed % 10_000 == 0) {
                    double elapsed = (System.nanoTime() - startTime) / 1e9;
                    double throughput = totalProcessed / elapsed;
                    System.out.printf(
                        "Progress: %d events (%.1f events/sec) at %.1f hours%n",
                        totalProcessed, throughput, elapsed / 3600);
                }

                Thread.sleep(Math.min(event.delayMs(), 1));  // Cap sleep to 1ms
            }
        }

        // Report results
        long totalDurationSeconds = (System.nanoTime() - startTime) / 1_000_000_000;
        System.out.printf(
            "Test Complete (%d seconds):%n  Cases: %d%n  Tasks: %d%n  Completions: %d%n",
            totalDurationSeconds, caseCounter, taskCounter, completionCounter);
    }

    private void processWorkloadEvent(WorkloadEvent event) {
        switch (event.eventType()) {
            case "case_arrival" -> {
                caseCounter++;
                createNewCase();
            }
            case "task_execution" -> {
                taskCounter++;
                executeWorkItem();
            }
            case "case_completion" -> {
                completionCounter++;
                completeCase();
            }
        }
    }

    private void createNewCase() {
        // Select random workflow
        List<String> keys = new ArrayList<>(workflowSpecs.keySet());
        String workflowKey = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        String specXml = workflowSpecs.get(workflowKey);

        try {
            YSpecification spec = engine.unmarshalSpecification(specXml);
            engine.startCase(spec, null, null);
        } catch (Exception e) {
            System.err.println("Case creation failed: " + e.getMessage());
        }
    }

    private void executeWorkItem() {
        // Implementation: execute next work item from queue
    }

    private void completeCase() {
        // Implementation: complete a running case
    }
}
```

---

## Performance Tuning Tips

### Adjusting Load Profile

```java
// Light load: 5 cases/sec, 100ms tasks
MixedWorkloadSimulator light = new MixedWorkloadSimulator(5.0, 100L);

// Moderate load: 10 cases/sec, 150ms tasks
MixedWorkloadSimulator moderate = new MixedWorkloadSimulator(10.0, 150L);

// Heavy load: 50 cases/sec, 200ms tasks
MixedWorkloadSimulator heavy = new MixedWorkloadSimulator(50.0, 200L);

// Extreme load: 200 cases/sec, 100ms tasks
MixedWorkloadSimulator extreme = new MixedWorkloadSimulator(200.0, 100L);
```

### Precomputation vs. Real-Time

**Use Precomputation When:**
- You want reproducible testing (same sequence every run)
- You want to avoid runtime randomness variance
- Test duration is known in advance
- You need offline analysis of event patterns

**Use Real-Time When:**
- You want unpredictable load (more realistic)
- Test duration varies
- You want to measure event generation overhead
- You're debugging event processing logic

---

## Summary

Both components are production-ready for immediate integration:

1. **MixedWorkloadSimulator** — Provides realistic Poisson/Exponential distributions
2. **TestDataGenerator.newRealisticMixedWorkload()** — Supplies diverse workflow patterns

Together they enable realistic, heterogeneous stress testing at scale without artificial uniformity.
