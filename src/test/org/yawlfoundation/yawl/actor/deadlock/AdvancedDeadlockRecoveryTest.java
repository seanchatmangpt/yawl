package org.yawlfoundation.yawl.actor.deadlock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for AdvancedDeadlockRecovery
 *
 * @author Deadlock Detection Team
 * @version 1.0
 */
@Execution(ExecutionMode.CONCURRENT)
public class AdvancedDeadlockRecoveryTest {

    private AdvancedDeadlockRecovery recovery;
    private TestMemoryRepository memoryRepository;
    private RealStrategySelector strategySelector;

    @BeforeEach
    void setUp() {
        recovery = new AdvancedDeadlockRecovery();
        memoryRepository = new TestMemoryRepository();
        strategySelector = new RealStrategySelector();
        recovery.setMemoryRepository(memoryRepository);
        recovery.setStrategySelector(strategySelector);
        recovery.initialize();
    }

    @Test
    @DisplayName("Selects optimal recovery strategy based on deadlock type")
    void testOptimalStrategySelection() {
        // Test cycle deadlock scenario
        DeadlockAlert cycleAlert = new DeadlockAlert(
            "CIRCULAR_DEPENDENCY",
            Instant.now(),
            "Test deadlock",
            Set.of("A", "B", "C"),
            List.of("smart-rollback", "timeout", "priority-based")
        );

        RecoveryStrategy strategy = recovery.selectOptimalStrategy(cycleAlert);
        assertEquals(RecoveryStrategy.SMART_ROLLBACK, strategy,
                   "Should select smart rollback for cycle deadlocks");

        // Test resource deadlock scenario
        DeadlockAlert resourceAlert = new DeadlockAlert(
            "RESOURCE_DEADLOCK",
            Instant.now(),
            "Resource contention",
            Set.of("actor-A", "actor-B"),
            List.of("timeout", "resource-priority", "process-kill")
        );

        strategy = recovery.selectOptimalStrategy(resourceAlert);
        assertEquals(RecoveryStrategy.RESOURCE_PRIORITY, strategy,
                   "Should select resource priority for resource deadlocks");

        // Test message queue deadlock
        DeadlockAlert messageAlert = new DeadlockAlert(
            "MESSAGE_QUEUE_DEADLOCK",
            Instant.now(),
            "Message queue deadlock",
            Set.of("producer-A", "consumer-B"),
            List.of("priority-based", "timeout", "smart-rollback")
        );

        strategy = recovery.selectOptimalStrategy(messageAlert);
        assertEquals(RecoveryStrategy.PRIORITY_BASED, strategy,
                   "Should select priority-based for message deadlocks");
    }

    @Test
    @DisplayName("Recovers from simple cycle deadlock")
    void testCycleRecovery() {
        // Create cycle deadlock scenario
        DeadlockAlert alert = new DeadlockAlert(
            "CIRCULAR_DEPENDENCY",
            Instant.now(),
            "A -> B -> C -> A",
            Set.of("actor-A", "actor-B", "actor-C"),
            List.of("smart-rollback", "timeout")
        );

        RecoveryResult result = recovery.executeRecovery(alert, RecoveryStrategy.SMART_ROLLBACK);

        assertTrue(result.isSuccess(), "Recovery should succeed");
        assertEquals("Smart rollback completed", result.getMessage());
        assertTrue(memoryRepository.isStrategyLogged(alert, RecoveryStrategy.SMART_ROLLBACK),
                 "Strategy should be logged in memory");
    }

    @Test
    @DisplayName("Recovers from resource deadlock with priority")
    void testResourceDeadlockRecovery() {
        // Create resource deadlock scenario
        DeadlockAlert alert = new DeadlockAlert(
            "RESOURCE_DEADLOCK",
            Instant.now(),
            "Resource deadlock detected",
            Set.of("actor-A", "actor-B"),
            List.of("resource-priority", "timeout")
        );

        RecoveryResult result = recovery.executeRecovery(alert, RecoveryStrategy.RESOURCE_PRIORITY);

        assertTrue(result.isSuccess(), "Recovery should succeed");
        assertEquals("Resource priority recovery completed", result.getMessage());
    }

    @Test
    @DisplayName("Uses ML-based strategy selection when available")
    void testMLBasedStrategySelection() {
        // Enable ML selection
        strategySelector.enableMLSelection();

        DeadlockAlert complexAlert = new DeadlockAlert(
            "MULTI_TYPE_DEADLOCK",
            Instant.now(),
            "Complex deadlock involving cycles and resources",
            Set.of("actor-1", "actor-2", "resource-A", "resource-B"),
            List.of("ml-optimized", "smart-rollback", "priority-based")
        );

        RecoveryStrategy strategy = recovery.selectOptimalStrategy(complexAlert);
        assertEquals(RecoveryStrategy.ML_OPTIMIZED, strategy,
                   "Should use ML-optimized strategy for complex deadlocks");
    }

    @Test
    @DisplayName("Tracks recovery metrics and performance")
    void testRecoveryMetricsTracking() {
        // Execute multiple recovery operations
        DeadlockAlert alert1 = new DeadlockAlert(
            "CIRCULAR_DEPENDENCY",
            Instant.now(),
            "Test deadlock 1",
            Set.of("actor-A", "actor-B"),
            List.of("smart-rollback", "timeout")
        );

        DeadlockAlert alert2 = new DeadlockAlert(
            "RESOURCE_DEADLOCK",
            Instant.now(),
            "Test deadlock 2",
            Set.of("actor-C", "actor-D"),
            List.of("resource-priority", "timeout")
        );

        RecoveryResult result1 = recovery.executeRecovery(alert1, RecoveryStrategy.SMART_ROLLBACK);
        RecoveryResult result2 = recovery.executeRecovery(alert2, RecoveryStrategy.RESOURCE_PRIORITY);

        // Verify metrics tracking
        assertEquals(2, memoryRepository.getRecoveryCount(),
                   "Should track 2 recovery operations");
        assertTrue(memoryRepository.getAverageRecoveryTime() > 0,
                   "Should track average recovery time");
        assertEquals(2, memoryRepository.getSuccessCount(),
                   "Should track 2 successful recoveries");
    }

    @Test
    @DisplayName("Implements circuit breaker for rapid failures")
    void testCircuitBreakerProtection() {
        // Simulate rapid failures
        DeadlockAlert alert = new DeadlockAlert(
            "CIRCULAR_DEPENDENCY",
            Instant.now(),
            "Test deadlock",
            Set.of("actor-A", "actor-B"),
            List.of("timeout", "smart-rollback")
        );

        // Execute multiple recovery operations that fail
        for (int i = 0; i < 5; i++) {
            memoryRepository.markStrategyAsFailed(RecoveryStrategy.TIMEOUT);
        }

        RecoveryStrategy strategy = recovery.selectOptimalStrategy(alert);
        assertNotEquals(RecoveryStrategy.TIMEOUT, strategy,
                       "Circuit breaker should prevent failed strategy selection");
        assertEquals(RecoveryStrategy.SMART_ROLLBACK, strategy);
    }

    @Test
    @DisplayName("Adapts strategy based on historical success rates")
    void testAdaptiveStrategySelection() {
        // Set up historical data favoring timeout strategy
        memoryRepository.markStrategyAsSuccessful(RecoveryStrategy.TIMEOUT, 10);
        memoryRepository.markStrategyAsSuccessful(RecoveryStrategy.SMART_ROLLBACK, 3);
        memoryRepository.markStrategyAsFailed(RecoveryStrategy.TIMEOUT, 2);
        memoryRepository.markStrategyAsFailed(RecoveryStrategy.SMART_ROLLBACK, 5);

        DeadlockAlert alert = new DeadlockAlert(
            "CIRCULAR_DEPENDENCY",
            Instant.now(),
            "Test deadlock",
            Set.of("actor-A", "actor-B"),
            List.of("timeout", "smart-rollback")
        );

        RecoveryStrategy strategy = recovery.selectOptimalStrategy(alert);
        assertEquals(RecoveryStrategy.TIMEOUT, strategy,
                   "Should select strategy with higher historical success rate");
    }
}

/**
 * Real implementation of MemoryRepository for testing
 */
class TestMemoryRepository implements MemoryRepository {
    private final Map<RecoveryStrategy, Integer> successCounts = new ConcurrentHashMap<>();
    private final Map<RecoveryStrategy, Integer> failureCounts = new ConcurrentHashMap<>();
    private final List<DeadlockEvent> recoveryEvents = new CopyOnWriteArrayList<>();
    private int recoveryCount = 0;
    private long totalRecoveryTime = 0;
    private boolean mlEnabled = false;

    @Override
    public void logRecoveryAttempt(DeadlockAlert alert, RecoveryStrategy strategy) {
        recoveryEvents.add(new DeadlockEvent(
            "RECOVERY_ATTEMPT",
            Instant.now(),
            "Attempting recovery: " + strategy,
            alert.getActors()
        ));
    }

    @Override
    public void logRecoverySuccess(DeadlockAlert alert, RecoveryStrategy strategy) {
        successCounts.merge(strategy, 1, Integer::sum);
        recoveryEvents.add(new DeadlockEvent(
            "RECOVERY_SUCCESS",
            Instant.now(),
            "Recovery successful: " + strategy,
            alert.getActors()
        ));
    }

    @Override
    public void logRecoveryFailure(DeadlockAlert alert, RecoveryStrategy strategy) {
        failureCounts.merge(strategy, 1, Integer::sum);
        recoveryEvents.add(new DeadlockEvent(
            "RECOVERY_FAILURE",
            Instant.now(),
            "Recovery failed: " + strategy,
            alert.getActors()
        ));
    }

    @Override
    public double getSuccessRate(RecoveryStrategy strategy) {
        int successes = successCounts.getOrDefault(strategy, 0);
        int failures = failureCounts.getOrDefault(strategy, 0);
        int total = successes + failures;
        return total == 0 ? 0.5 : (double) successes / total;
    }

    @Override
    public void markStrategyAsSuccessful(RecoveryStrategy strategy, int count) {
        successCounts.merge(strategy, count, Integer::sum);
    }

    @Override
    public void markStrategyAsFailed(RecoveryStrategy strategy, int count) {
        failureCounts.merge(strategy, count, Integer::sum);
    }

    @Override
    public void enableMLSelection(boolean enabled) {
        this.mlEnabled = enabled;
    }

    public void logRecoveryAttemptWithTime(DeadlockAlert alert, RecoveryStrategy strategy, long duration) {
        logRecoveryAttempt(alert, strategy);
        totalRecoveryTime += duration;
        recoveryCount++;
    }

    public boolean isStrategyLogged(DeadlockAlert alert, RecoveryStrategy strategy) {
        return recoveryEvents.stream()
            .anyMatch(e -> e.getMessage().contains("Recovery: " + strategy.name()));
    }

    public int getRecoveryCount() {
        return recoveryCount;
    }

    public double getAverageRecoveryTime() {
        return recoveryCount == 0 ? 0 : (double) totalRecoveryTime / recoveryCount;
    }

    public int getSuccessCount() {
        return successCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
}

/**
 * Real implementation of StrategySelector for testing
 */
class RealStrategySelector implements StrategySelector {
    private Map<RecoveryStrategy, Double> mlPredictions = new ConcurrentHashMap<>();
    private boolean mlEnabled = false;

    @Override
    public RecoveryStrategy selectStrategy(DeadlockAlert alert, MemoryRepository memoryRepo) {
        if (mlEnabled && mlPredictions.containsKey(alert.getType())) {
            return selectMLStrategy(alert, memoryRepo);
        }
        return selectHistoricalStrategy(alert, memoryRepo);
    }

    @Override
    public void enableMLSelection(boolean enabled) {
        this.mlEnabled = enabled;
    }

    private RecoveryStrategy selectMLStrategy(DeadlockAlert alert, MemoryRepository memoryRepo) {
        // Simulate ML-based strategy selection
        String deadlockType = alert.getType();
        if (deadlockType.equals("MULTI_TYPE_DEADLOCK")) {
            return RecoveryStrategy.ML_OPTIMIZED;
        } else if (deadlockType.equals("RESOURCE_DEADLOCK")) {
            return RecoveryStrategy.RESOURCE_PRIORITY;
        } else if (deadlockType.equals("MESSAGE_QUEUE_DEADLOCK")) {
            return RecoveryStrategy.PRIORITY_BASED;
        }
        return RecoveryStrategy.SMART_ROLLBACK;
    }

    private RecoveryStrategy selectHistoricalStrategy(DeadlockAlert alert, MemoryRepository memoryRepo) {
        // Select strategy based on historical success rates
        RecoveryStrategy bestStrategy = RecoveryStrategy.TIMEOUT;
        double bestRate = 0.0;

        for (RecoveryStrategy strategy : alert.getRecoveryOptions()) {
            double rate = memoryRepo.getSuccessRate(strategy);
            if (rate > bestRate) {
                bestRate = rate;
                bestStrategy = strategy;
            }
        }

        return bestStrategy;
    }
}