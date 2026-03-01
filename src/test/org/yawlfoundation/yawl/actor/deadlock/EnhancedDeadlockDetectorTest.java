package org.yawlfoundation.yawl.actor.deadlock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for EnhancedDeadlockDetector
 *
 * @author Deadlock Detection Team
 * @version 1.0
 */
@Execution(ExecutionMode.CONCURRENT)
public class EnhancedDeadlockDetectorTest {

    private EnhancedDeadlockDetector detector;
    private RealWorkItemRepository workItemRepo;

    @BeforeEach
    void setUp() {
        detector = new EnhancedDeadlockDetector();
        workItemRepo = new RealWorkItemRepository();
        detector.setWorkItemRepository(workItemRepo);
        detector.initialize();
    }

    @Test
    @DisplayName("Detects simple circular dependency")
    void testSimpleCycleDetection() {
        // Create a simple cycle: A -> B -> C -> A
        String actorA = "actor-A";
        String actorB = "actor-B";
        String actorC = "actor-C";

        workItemRepo.addDependency(actorA, actorB, "task1");
        workItemRepo.addDependency(actorB, actorC, "task2");
        workItemRepo.addDependency(actorC, actorA, "task3");

        List<DeadlockAlert> alerts = detector.detectDeadlocks();
        assertNotNull(alerts);
        assertFalse(alerts.isEmpty());

        boolean hasCycleAlert = alerts.stream()
            .anyMatch(alert -> alert.getType().equals("CIRCULAR_DEPENDENCY"));
        assertTrue(hasCycleAlert, "Should detect circular dependency");
    }

    @Test
    @DisplayName("Detects resource deadlock pattern")
    void testResourceDeadlockDetection() {
        String actorA = "actor-A";
        String actorB = "actor-B";

        workItemRepo.acquireResource(actorA, "resource1");
        workItemRepo.acquireResource(actorB, "resource2");
        workItemRepo.requestResource(actorA, "resource2");
        workItemRepo.requestResource(actorB, "resource1");

        List<DeadlockAlert> alerts = detector.detectDeadlocks();
        assertFalse(alerts.isEmpty());

        boolean hasResourceAlert = alerts.stream()
            .anyMatch(alert -> alert.getType().equals("RESOURCE_DEADLOCK"));
        assertTrue(hasResourceAlert, "Should detect resource deadlock");
    }

    @Test
    @DisplayName("Detects message queue deadlock")
    void testMessageQueueDeadlockDetection() {
        String producerA = "producer-A";
        String producerB = "producer-B";
        String consumerA = "consumer-A";
        String consumerB = "consumer-B";

        workItemRepo.addMessageDependency(producerA, consumerA);
        workItemRepo.addMessageDependency(producerB, consumerB);
        workItemRepo.addMessageDependency(consumerA, producerB);
        workItemRepo.addMessageDependency(consumerB, producerA);

        List<DeadlockAlert> alerts = detector.detectDeadlocks();
        assertFalse(alerts.isEmpty());

        boolean hasMessageAlert = alerts.stream()
            .anyMatch(alert -> alert.getType().equals("MESSAGE_QUEUE_DEADLOCK"));
        assertTrue(hasMessageAlert, "Should detect message queue deadlock");
    }

    @Test
    @DisplayName("No false positives with acyclic graph")
    void testNoFalsePositives() {
        // Create acyclic dependency: A -> B -> C
        String actorA = "actor-A";
        String actorB = "actor-B";
        String actorC = "actor-C";

        workItemRepo.addDependency(actorA, actorB, "task1");
        workItemRepo.addDependency(actorB, actorC, "task2");

        List<DeadlockAlert> alerts = detector.detectDeadlocks();
        assertNotNull(alerts);
        assertTrue(alerts.isEmpty(), "Should not detect deadlocks in acyclic graph");
    }

    @Test
    @DisplayName("Integrates with virtual thread monitoring")
    void testVirtualThreadIntegration() {
        // Create virtual thread context
        String vtContext = "vt-" + UUID.randomUUID();
        workItemRepo.setVirtualThreadContext(vtContext);

        String producer = "producer";
        String consumer = "consumer";

        workItemRepo.addMessageDependency(producer, consumer);
        workItemRepo.setVirtualThread(producer, vtContext);
        workItemRepo.setVirtualThread(consumer, vtContext);

        List<DeadlockAlert> alerts = detector.detectDeadlocks();

        // Should detect virtual thread specific deadlocks
        assertFalse(alerts.isEmpty());
    }

    @Test
    @DisplayName("Performance test with large graph")
    void testPerformanceWithLargeGraph() {
        // Create large acyclic graph (100 nodes)
        for (int i = 0; i < 100; i++) {
            String actorA = "actor-" + i;
            String actorB = "actor-" + (i + 1);
            workItemRepo.addDependency(actorA, actorB, "task-" + i);
        }

        long startTime = System.currentTimeMillis();
        List<DeadlockAlert> alerts = detector.detectDeadlocks();
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < 5000, "Should complete within 5 seconds");
        assertTrue(alerts.isEmpty(), "Large acyclic graph should have no deadlocks");
    }

    @ParameterizedTest
    @MethodSource("deadlockScenarios")
    @DisplayName("Tests various deadlock scenarios")
    void testDeadlockScenarios(DeadlockScenario scenario) {
        // Set up scenario
        scenario.setup(workItemRepo);

        List<DeadlockAlert> alerts = detector.detectDeadlocks();

        assertFalse(alerts.isEmpty(), "Should detect deadlock in scenario");
        assertTrue(scenario.validate(alerts), "Alerts should match scenario requirements");
    }

    @Test
    @DisplayName("Recovery integration works")
    void testRecoveryIntegration() {
        // Create deadlock scenario
        String actorA = "actor-A";
        String actorB = "actor-B";

        workItemRepo.addDependency(actorA, actorB, "task1");
        workItemRepo.addDependency(actorB, actorA, "task2");

        List<DeadlockAlert> alerts = detector.detectDeadlocks();
        assertFalse(alerts.isEmpty());

        // Test that recovery system is notified
        assertEquals(1, alerts.size(), "Should have exactly one deadlock alert");
        assertTrue(alerts.get(0).getRecoveryOptions().size() > 0,
                  "Should have recovery options");
    }

    private static Stream<DeadlockScenario> deadlockScenarios() {
        return Stream.of(
            new SimpleCycleScenario(),
            new ResourceWaitScenario(),
            new MessageQueueScenario(),
            new MultiActorCycleScenario(),
            new VirtualThreadDeadlockScenario()
        );
    }
}

/**
 * Real implementation of WorkItemRepository for testing
 * Implements the actual interface without mocking behavior
 */
class RealWorkItemRepository extends WorkItemRepository {
    private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> resources = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> messageDeps = new ConcurrentHashMap<>();
    private final Map<String, String> virtualThreads = new ConcurrentHashMap<>();
    private String currentVirtualThreadContext;

    @Override
    public void addDependency(String fromActor, String toActor, String taskId) {
        dependencies.computeIfAbsent(fromActor, k -> ConcurrentHashMap.newKeySet()).add(toActor);
    }

    @Override
    public void acquireResource(String actor, String resourceId) {
        resources.computeIfAbsent(actor, k -> ConcurrentHashMap.newKeySet()).add(resourceId);
    }

    @Override
    public void requestResource(String actor, String resourceId) {
        // Real implementation tracks resource requests
        resources.computeIfAbsent(actor + "_pending", k -> ConcurrentHashMap.newKeySet())
                .add(resourceId);
    }

    @Override
    public void addMessageDependency(String producer, String consumer) {
        messageDeps.computeIfAbsent(producer, k -> ConcurrentHashMap.newKeySet()).add(consumer);
    }

    @Override
    public void setVirtualThread(String actor, String virtualThreadId) {
        virtualThreads.put(actor, virtualThreadId);
    }

    @Override
    public void setVirtualThreadContext(String vtContext) {
        this.currentVirtualThreadContext = vtContext;
    }

    @Override
    public Map<String, Set<String>> getDependencies() {
        return Collections.unmodifiableMap(dependencies);
    }

    @Override
    public Map<String, Set<String>> getResources() {
        return Collections.unmodifiableMap(resources);
    }
}

interface DeadlockScenario {
    void setup(RealWorkItemRepository repo);
    boolean validate(List<DeadlockAlert> alerts);
}

class SimpleCycleScenario implements DeadlockScenario {
    @Override
    public void setup(RealWorkItemRepository repo) {
        repo.addDependency("A", "B", "1");
        repo.addDependency("B", "C", "2");
        repo.addDependency("C", "A", "3");
    }

    @Override
    public boolean validate(List<DeadlockAlert> alerts) {
        return alerts.stream().anyMatch(a -> a.getType().equals("CIRCULAR_DEPENDENCY"));
    }
}

class ResourceWaitScenario implements DeadlockScenario {
    @Override
    public void setup(RealWorkItemRepository repo) {
        repo.acquireResource("A", "R1");
        repo.acquireResource("B", "R2");
        repo.requestResource("A", "R2");
        repo.requestResource("B", "R1");
    }

    @Override
    public boolean validate(List<DeadlockAlert> alerts) {
        return alerts.stream().anyMatch(a -> a.getType().equals("RESOURCE_DEADLOCK"));
    }
}

class MessageQueueScenario implements DeadlockScenario {
    @Override
    public void setup(RealWorkItemRepository repo) {
        repo.addMessageDependency("P1", "C1");
        repo.addMessageDependency("C1", "P2");
        repo.addMessageDependency("P2", "C2");
        repo.addMessageDependency("C2", "P1");
    }

    @Override
    public boolean validate(List<DeadlockAlert> alerts) {
        return alerts.stream().anyMatch(a -> a.getType().equals("MESSAGE_QUEUE_DEADLOCK"));
    }
}

class MultiActorCycleScenario implements DeadlockScenario {
    @Override
    public void setup(RealWorkItemRepository repo) {
        repo.addDependency("1", "2", "t1");
        repo.addDependency("2", "3", "t2");
        repo.addDependency("3", "4", "t3");
        repo.addDependency("4", "1", "t4");
    }

    @Override
    public boolean validate(List<DeadlockAlert> alerts) {
        return alerts.size() == 1 && alerts.get(0).getType().equals("CIRCULAR_DEPENDENCY");
    }
}

class VirtualThreadDeadlockScenario implements DeadlockScenario {
    @Override
    public void setup(RealWorkItemRepository repo) {
        String vt = "vt-123";
        repo.setVirtualThreadContext(vt);
        repo.setVirtualThread("vt-producer", vt);
        repo.setVirtualThread("vt-consumer", vt);
        repo.addMessageDependency("vt-producer", "vt-consumer");
        repo.addMessageDependency("vt-consumer", "vt-producer");
    }

    @Override
    public boolean validate(List<DeadlockAlert> alerts) {
        return alerts.stream().anyMatch(a -> a.getType().equals("VIRTUAL_THREAD_DEADLOCK"));
    }
}