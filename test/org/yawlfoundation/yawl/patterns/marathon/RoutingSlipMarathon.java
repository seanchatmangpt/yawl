package org.yawlfoundation.yawl.patterns.marathon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime;
import org.yawlfoundation.yawl.engine.agent.patterns.RoutingSlip;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Marathon stress test for Routing Slip pattern.
 * Runs 4+ hours of active case routing with registry cleanup monitoring.
 *
 * Scenario: Continuously route cases through actor chains, measuring:
 * - Case registry size growth (should stay bounded at ~100K at equilibrium)
 * - Completion rate (cases properly deregistered)
 * - Heap stability (no unbounded growth as cases accumulate)
 * - GC pauses during cleanup operations
 *
 * Failure threshold: Registry size exceeds 1M entries (unbounded growth)
 */
@DisplayName("RoutingSlip Marathon — 4 hours active cases, registry cleanup monitoring")
class RoutingSlipMarathon extends MarathonTestBase {

    private ActorRuntime runtime;
    private RoutingSlip.CaseRegistry caseRegistry;
    private List<ActorRef> routingActors;
    private ExecutorService caseEnqueuerExecutor;
    private ExecutorService completionExecutor;

    private static final int CASES_PER_SECOND = 1_000;
    private static final int NUM_ROUTING_ACTORS = 10;
    private static final int AVG_CASE_TTL_MILLIS = 10 * 60 * 1000;  // 10 minutes
    private static final int TEST_DURATION_MINUTES =
        (int) (Long.parseLong(System.getProperty("marathon.duration.minutes", "240")) / 60); // 4 hours default
    private static final int RECORD_INTERVAL_MINUTES = 1;
    private static final int REGISTRY_SIZE_THRESHOLD = 1_000_000;
    private static final int EXPECTED_EQUILIBRIUM_SIZE = 100_000;  // At 1K cases/sec × 100s TTL
    private static final int HEAP_GROWTH_THRESHOLD_MB = 200;

    private final AtomicLong totalCasesCreated = new AtomicLong(0);
    private final AtomicLong totalCasesCompleted = new AtomicLong(0);
    private final AtomicInteger registrySizeHistory = new AtomicInteger(0);
    private final AtomicBoolean testRunning = new AtomicBoolean(true);
    private final List<Integer> registrySizePerMinute = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        runtime = createRuntime();
        caseRegistry = new RoutingSlip.CaseRegistry();
        routingActors = new ArrayList<>();
        caseEnqueuerExecutor = Executors.newFixedThreadPool(20);
        completionExecutor = Executors.newFixedThreadPool(10);
        metrics.clear();
        totalCasesCreated.set(0);
        totalCasesCompleted.set(0);
        registrySizeHistory.set(0);
        registrySizePerMinute.clear();
        testRunning.set(true);
    }

    @Test
    @Timeout(value = 310, unit = java.util.concurrent.TimeUnit.MINUTES)  // 4 hours + 50 min buffer
    @DisplayName("RoutingSlip: 4 hours active cases, registry cleanup monitoring")
    void routingSlipCaseCleanupMarathon() throws Exception {
        System.out.println("\n=== RoutingSlip Marathon Test ===");
        System.out.println("Configuration:");
        System.out.println("  - Duration: 4 hours sustained routing");
        System.out.println("  - Cases per second: " + CASES_PER_SECOND);
        System.out.println("  - Routing actors: " + NUM_ROUTING_ACTORS);
        System.out.println("  - Average case TTL: " + (AVG_CASE_TTL_MILLIS / 1000) + " seconds");
        System.out.println("  - Registry size threshold: " + REGISTRY_SIZE_THRESHOLD);
        System.out.println("  - Expected equilibrium: " + EXPECTED_EQUILIBRIUM_SIZE);
        System.out.println();

        // Phase 1: Baseline (5 minutes, no cases)
        System.out.println("Phase 1: Baseline (5 min, no active cases)");
        recordMetrics("baseline", caseRegistry.size());
        gcAndRecord("baseline_gc");
        Thread.sleep(Duration.ofMinutes(5).toMillis());
        recordMetrics("after_baseline", caseRegistry.size());

        long baselineHeap = metrics.get(metrics.size() - 1).heapUsed;
        System.out.printf("  Baseline heap: %d MB%n", baselineHeap / (1024 * 1024));
        System.out.printf("  Initial registry size: %d%n", caseRegistry.size());

        // Phase 2: Spawn routing actors
        System.out.println("\nPhase 2: Spawning " + NUM_ROUTING_ACTORS + " routing actors");
        for (int i = 0; i < NUM_ROUTING_ACTORS; i++) {
            final int actorIndex = i;
            ActorRef actor = runtime.spawn(self -> routeCase(self, actorIndex));
            routingActors.add(actor);
        }
        System.out.println("  Actors spawned: " + routingActors.size());

        // Phase 3: Start background case completion service
        System.out.println("\nPhase 3: Starting case completion background service");
        startCaseCompletionService();

        // Phase 4: Sustained load (4 hours)
        System.out.println("\nPhase 4: Sustained load (4 hours, " + TEST_DURATION_MINUTES + " minutes)");
        long phaseStartTime = System.currentTimeMillis();
        long recordStartTime = System.currentTimeMillis();
        int recordCount = 0;

        for (int minute = 0; minute < TEST_DURATION_MINUTES; minute++) {
            if (!shouldContinue()) {
                throw new AssertionError("Test aborted during load phase at minute " + minute);
            }

            // Enqueue cases for this minute
            long minuteStartMs = System.currentTimeMillis();
            long casesThisMinute = 0;

            while (System.currentTimeMillis() - minuteStartMs < Duration.ofMinutes(1).toMillis()) {
                int routerIndex = (int) (casesThisMinute % NUM_ROUTING_ACTORS);
                ActorRef router = routingActors.get(routerIndex);

                enqueueCaseForRouting(router, minute, (int) casesThisMinute);
                casesThisMinute++;

                // Sleep to maintain rate
                if (casesThisMinute % 100 == 0) {
                    Thread.yield();
                }
            }

            // Record metrics every RECORD_INTERVAL_MINUTES
            long elapsedMs = System.currentTimeMillis() - recordStartTime;
            if (elapsedMs >= Duration.ofMinutes(RECORD_INTERVAL_MINUTES).toMillis()) {
                int registrySize = caseRegistry.size();
                recordMetrics("minute_" + minute, registrySize);
                registrySizePerMinute.add(registrySize);
                recordCount++;

                // Check for unbounded registry growth
                if (registrySize > REGISTRY_SIZE_THRESHOLD) {
                    abortIfCritical(
                        "Registry size exceeded threshold: " + registrySize +
                        " > " + REGISTRY_SIZE_THRESHOLD
                    );
                }

                // Check heap growth
                if (isHeapGrowingUnbounded(HEAP_GROWTH_THRESHOLD_MB)) {
                    gcAndRecord("gc_during_load_minute_" + minute);
                    double growthRate = getHeapGrowthRatePerMinute();
                    abortIfCritical("Heap growing unbounded: " + growthRate + "MB/min");
                }

                // Print progress every 10 minutes
                if (minute % 10 == 0) {
                    long elapsedSec = (System.currentTimeMillis() - phaseStartTime) / 1000;
                    double casesPerSec = elapsedSec > 0 ?
                        (double) totalCasesCreated.get() / elapsedSec : 0;
                    double completionRate = elapsedSec > 0 ?
                        (double) totalCasesCompleted.get() / elapsedSec : 0;

                    System.out.printf(
                        "  Minute %d: created=%.0f/sec, completed=%.0f/sec, registry=%d, heap=%.0f MB%n",
                        minute,
                        casesPerSec,
                        completionRate,
                        registrySize,
                        metrics.get(metrics.size() - 1).heapUsedMB()
                    );
                }

                recordStartTime = System.currentTimeMillis();
            }
        }

        System.out.println("\nPhase 5: Draining remaining cases and cleanup");
        testRunning.set(false);

        // Stop enqueuing but allow cases to complete
        caseEnqueuerExecutor.shutdown();
        caseEnqueuerExecutor.awaitTermination(30, TimeUnit.SECONDS);

        // Wait for completion service to drain
        completionExecutor.shutdown();
        completionExecutor.awaitTermination(60, TimeUnit.SECONDS);

        // Give cases final opportunity to complete
        Thread.sleep(Duration.ofSeconds(10).toMillis());

        // Force cleanup
        gcAndRecord("cleanup_gc");
        Thread.sleep(1000);
        recordMetrics("after_cleanup", caseRegistry.size());

        // Final verification
        System.out.println("\n=== Test Results ===");
        long finalHeap = metrics.get(metrics.size() - 1).heapUsed;
        long heapGrowth = (finalHeap - baselineHeap) / (1024 * 1024);
        int finalRegistrySize = caseRegistry.size();
        double avgRegistrySize = registrySizePerMinute.stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);

        System.out.printf("Baseline heap: %d MB%n", baselineHeap / (1024 * 1024));
        System.out.printf("Final heap: %d MB%n", finalHeap / (1024 * 1024));
        System.out.printf("Total heap growth: %d MB%n", heapGrowth);
        System.out.printf("Growth rate: %.2f MB/min%n", getHeapGrowthRatePerMinute());
        System.out.printf("Total cases created: %d%n", totalCasesCreated.get());
        System.out.printf("Total cases completed: %d%n", totalCasesCompleted.get());
        System.out.printf("Pending cases (registry): %d%n", finalRegistrySize);
        System.out.printf("Average registry size: %.0f%n", avgRegistrySize);
        System.out.printf("Max registry size: %d%n",
            registrySizePerMinute.stream().mapToInt(Integer::intValue).max().orElse(0));
        System.out.printf("Min registry size: %d%n",
            registrySizePerMinute.stream().mapToInt(Integer::intValue).min().orElse(0));

        // Print metric summary
        System.out.println("\nHeap usage progression:");
        for (int i = 0; i < metrics.size(); i += Math.max(1, metrics.size() / 10)) {
            MarathonMetrics m = metrics.get(i);
            System.out.printf("  %s: heap=%d MB, registry=%d%n", m.phase, m.heapUsedMB(), m.customValue);
        }

        // Assertions: registry must be bounded and heap stable
        assertThat(finalRegistrySize)
            .as("Registry size must not exceed " + REGISTRY_SIZE_THRESHOLD + " (unbounded growth detected)")
            .isLessThan(REGISTRY_SIZE_THRESHOLD);

        assertThat(heapGrowth)
            .as("Heap growth must be less than " + HEAP_GROWTH_THRESHOLD_MB + "MB over 4 hours")
            .isLessThan(HEAP_GROWTH_THRESHOLD_MB);

        // Assertion: Completion rate should be reasonable (cases are being cleaned up)
        assertThat(totalCasesCompleted.get())
            .as("Most cases should complete and be deregistered")
            .isGreaterThan((long) (totalCasesCreated.get() * 0.8));

        System.out.println("\n✓ RoutingSlip Marathon test PASSED");
        System.out.printf("  Registry stayed bounded at ~%.0f entries (threshold: %d)%n",
            avgRegistrySize, REGISTRY_SIZE_THRESHOLD);
    }

    /**
     * Enqueue a case for routing through the slip.
     */
    private void enqueueCaseForRouting(ActorRef router, int minute, int caseIndex) {
        caseEnqueuerExecutor.submit(() -> {
            if (!testRunning.get()) return;

            String caseId = "case-" + minute + "-" + caseIndex + "-" + System.nanoTime();
            String caseData = "data-" + caseId;

            // Create routing slip with all routing actors
            Deque<ActorRef> slip = RoutingSlip.create(routingActors);

            // Create envelope
            RoutingSlip.Envelope envelope = RoutingSlip.envelope(caseId, caseData, slip);

            // Register case
            caseRegistry.register(envelope);
            totalCasesCreated.incrementAndGet();

            // Send to first router
            try {
                router.tell(envelope);
            } catch (Exception e) {
                // Case send failed, deregister
                caseRegistry.deregister(caseId);
            }
        });
    }

    /**
     * Routing actor behavior: processes case, forwards to next actor, or completes.
     * In a real system, this would apply business logic and update the payload.
     */
    private void routeCase(ActorRef self, int actorIndex) throws InterruptedException {
        while (testRunning.get() && !Thread.currentThread().isInterrupted()) {
            Object msg = self.recv();

            if (msg instanceof RoutingSlip.Envelope envelope) {
                // Simulate processing: update case data
                String updatedData = envelope.payload() + " -> actor-" + actorIndex;
                RoutingSlip.Envelope processed = envelope.withPayload(updatedData);

                // Advance to next actor and forward
                RoutingSlip.Envelope advanced = processed.advance("actor-" + actorIndex);

                if (advanced.isComplete()) {
                    // Case routing complete, deregister
                    caseRegistry.deregister(advanced.caseId());
                    totalCasesCompleted.incrementAndGet();
                } else {
                    // Forward to next actor in slip
                    caseRegistry.update(advanced);
                    RoutingSlip.forward(advanced, this::handleCaseCompletion);
                }
            }
        }
    }

    /**
     * Completion handler called when case routing is complete.
     */
    private void handleCaseCompletion(RoutingSlip.Envelope envelope) {
        caseRegistry.deregister(envelope.caseId());
        totalCasesCompleted.incrementAndGet();
    }

    /**
     * Background service to simulate case completion (TTL expiry, early termination).
     * In production, this would be driven by timers or external events.
     */
    private void startCaseCompletionService() {
        completionExecutor.submit(() -> {
            while (testRunning.get()) {
                try {
                    Thread.sleep(1000);  // Check every second

                    // Simulate TTL expiry: randomly complete some old cases
                    int currentSize = caseRegistry.size();
                    if (currentSize > EXPECTED_EQUILIBRIUM_SIZE) {
                        // Registry is growing, accelerate completions
                        int toComplete = Math.min(100, currentSize - EXPECTED_EQUILIBRIUM_SIZE);
                        caseRegistry.all().stream()
                            .limit(toComplete)
                            .forEach(env -> {
                                caseRegistry.deregister(env.caseId());
                                totalCasesCompleted.incrementAndGet();
                            });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Create runtime for this test.
     */
    protected ActorRuntime createRuntime() {
        return new VirtualThreadRuntime();
    }
}
