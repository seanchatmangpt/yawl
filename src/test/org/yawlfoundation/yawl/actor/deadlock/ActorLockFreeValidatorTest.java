package org.yawlfoundation.yawl.actor.deadlock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ActorLockFreeValidator
 *
 * @author Deadlock Detection Team
 * @version 1.0
 */
@Execution(ExecutionMode.CONCURRENT)
public class ActorLockFreeValidatorTest {

    private ActorLockFreeValidator validator;
    private AtomicTestStorage atomicStorage;
    private MemoryBarrierTestManager barrierManager;

    @BeforeEach
    void setUp() {
        validator = new ActorLockFreeValidator();
        atomicStorage = new AtomicTestStorage();
        barrierManager = new MemoryBarrierTestManager();
        validator.setAtomicStorage(atomicStorage);
        validator.setBarrierManager(barrierManager);
        validator.initialize();
    }

    @Test
    @DisplayName("Validates atomic operations without race conditions")
    void testAtomicOperationValidation() {
        // Create multiple concurrent operations on the same actor
        String actorId = "test-actor";
        String operation1 = "op1";
        String operation2 = "op2";

        // Simulate concurrent operations
        validator.validateAtomicOperation(actorId, operation1);
        validator.validateAtomicOperation(actorId, operation2);

        // Validate no race conditions occurred
        assertTrue(validator.validateActorState(actorId),
                  "Actor should maintain consistent state after atomic operations");
        assertTrue(atomicStorage.validateIntegrity(actorId),
                  "Atomic storage should maintain integrity");
    }

    @Test
    @DisplayName("Detects memory visibility issues")
    void testMemoryVisibilityDetection() {
        String actorA = "actor-A";
        String actorB = "actor-B";

        // Create memory snapshots
        MemorySnapshot snapshot1 = createSnapshot(actorA, "state1");
        MemorySnapshot snapshot2 = createSnapshot(actorA, "state2");
        MemorySnapshot snapshot3 = createSnapshot(actorB, "state1");

        // Simulate visibility issue
        barrierManager.validateMemoryVisibility(snapshot2, snapshot1);

        // Should detect visibility issue
        List<LockFreeViolation> violations = validator.detectViolations();
        assertFalse(violations.isEmpty(), "Should detect memory visibility violations");
        assertTrue(violations.stream().anyMatch(v -> v.getType().equals("MEMORY_VISIBILITY")),
                   "Should detect memory visibility violation");
    }

    @Test
    @DisplayName("Validates lock-free concurrent data structures")
    void testLockFreeDataStructureValidation() {
        String actorId = "test-actor";
        String queueId = "work-queue";

        // Test concurrent queue operations
        validator.validateConcurrentQueueOperation(actorId, queueId, "enqueue");
        validator.validateConcurrentQueueOperation(actorId, queueId, "dequeue");
        validator.validateConcurrentQueueOperation(actorId, queueId, "enqueue");

        // Validate queue consistency
        assertTrue(validator.validateDataStructureConsistency(actorId, queueId),
                  "Queue should maintain consistency after concurrent operations");
    }

    @Test
    @DisplayName("Detects starvation in fair scheduling")
    void testStarvationDetection() {
        String actorA = "actor-A";
        String actorB = "actor-B";
        String sharedResource = "resource-X";

        // Simulate unfair scheduling
        validator.recordExecutionStart(actorA, sharedResource, Instant.now());
        validator.recordExecutionStart(actorB, sharedResource, Instant.now().plusMillis(1000));

        // Simulate long execution
        validator.recordExecutionEnd(actorA, sharedResource,
                                    Instant.now().plusMillis(5000));

        // Check for starvation
        List<StarvationAlert> alerts = validator.detectStarvation();
        assertFalse(alerts.isEmpty(), "Should detect starvation");
        assertTrue(alerts.stream().anyMatch(a -> a.getStarvedActor().equals(actorB)),
                   "Should detect actor B as starved");
    }

    @Test
    @DisplayName("Validates virtual thread coordination")
    void testVirtualThreadValidation() {
        String vt1 = "virtual-thread-1";
        String vt2 = "virtual-thread-2";
        String sharedResource = "vt-resource";

        // Simulate virtual thread coordination
        validator.recordVirtualThreadOperation(vt1, sharedResource, "acquire");
        validator.recordVirtualThreadOperation(vt2, sharedResource, "acquire");

        // Validate coordination
        List<LockFreeViolation> violations = validator.detectViolations();
        // Should detect virtual thread coordination issues
        assertFalse(violations.isEmpty(), "Should detect virtual thread coordination violations");
    }

    @Test
    @DisplayName("Performance test with high concurrency")
    void testHighConcurrencyPerformance() {
        int actorCount = 50;
        int operationsPerActor = 100;

        long startTime = System.currentTimeMillis();

        // Simulate high concurrency
        for (int i = 0; i < actorCount; i++) {
            String actorId = "actor-" + i;
            for (int j = 0; j < operationsPerActor; j++) {
                validator.validateAtomicOperation(actorId, "operation-" + j);
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Performance assertion
        assertTrue(duration < 10000, "Should complete within 10 seconds");

        // Validate no violations occurred
        List<LockFreeViolation> violations = validator.detectViolations();
        assertTrue(violations.isEmpty() || violations.size() < 5,
                   "Should have minimal violations with high concurrency");
    }

    @ParameterizedTest
    @MethodSource("lockFreeScenarios")
    @DisplayName("Tests various lock-free validation scenarios")
    void testLockFreeScenarios(LockFreeScenario scenario) {
        // Set up scenario
        scenario.setup(validator);

        // Validate scenario
        List<LockFreeViolation> violations = scenario.validate(validator);

        // Scenario-specific assertions
        scenario.assertResults(violations);
    }

    @Test
    @DisplayName("Integrates with existing YAWL engine")
    void testYawlIntegration() {
        // Test integration points with YAWL engine
        String netId = "test-net";
        String caseId = "case-123";

        // Simulate YAWL workflow execution
        validator.recordWorkflowExecution(netId, caseId);
        validator.recordActorExecution(netId, caseId, "actor-A");

        // Validate integration
        assertTrue(validator.validateWorkflowConsistency(netId, caseId),
                  "Workflow should remain consistent");
    }

    @Test
    @DisplayName("Recovers from lock-free violations")
    void testViolationRecovery() {
        // Create a violation
        LockFreeViolation violation = new LockFreeViolation(
            "MEMORY_VISIBILITY",
            Instant.now(),
            "Visibility issue detected",
            Set.of("actor-A"),
            List.of("rollback", "restart")
        );

        // Execute recovery
        RecoveryResult result = validator.recoverFromViolation(violation);

        assertTrue(result.isSuccess(), "Recovery should succeed");
        assertTrue(atomicStorage.isRecoveryLogged(violation),
                   "Recovery should be logged in storage");
    }

    private static Stream<LockFreeScenario> lockFreeScenarios() {
        return Stream.of(
            new ConcurrentQueueScenario(),
            new MemoryVisibilityScenario(),
            new VirtualThreadCoordinationScenario(),
            new StarvationScenario(),
            new MultiActorRaceScenario()
        );
    }

    private MemorySnapshot createSnapshot(String actorId, String state) {
        MemorySnapshot snapshot = new MemorySnapshot(Instant.now());
        snapshot.setActorState(actorId, new ActorValidationState(
            actorId, state, Instant.now()
        ));
        return snapshot;
    }
}

/**
 * Real implementation of AtomicStorage for testing
 */
class AtomicTestStorage implements AtomicStorage {
    private final Map<String, List<AtomicOperation>> operations = new ConcurrentHashMap<>();
    private final Map<String, Boolean> integrityFlags = new ConcurrentHashMap<>();
    private final List<LockFreeViolation> recoveryLog = new CopyOnWriteArrayList<>();

    @Override
    public void recordOperation(String actorId, AtomicOperation operation) {
        operations.computeIfAbsent(actorId, k -> new CopyOnWriteArrayList<>()).add(operation);
    }

    @Override
    public List<AtomicOperation> getOperations(String actorId) {
        return Collections.unmodifiableList(operations.getOrDefault(actorId, Collections.emptyList()));
    }

    @Override
    public void validateIntegrity(String actorId) {
        // Validate atomic storage integrity
        List<AtomicOperation> ops = operations.get(actorId);
        if (ops != null) {
            boolean isConsistent = ops.stream()
                .map(AtomicOperation::isConsistent)
                .allMatch(Boolean.TRUE::equals);
            integrityFlags.put(actorId, isConsistent);
        }
    }

    @Override
    public boolean validateIntegrity(String actorId) {
        return integrityFlags.getOrDefault(actorId, true);
    }

    @Override
    public void logRecovery(LockFreeViolation violation) {
        recoveryLog.add(violation);
    }

    public boolean isRecoveryLogged(LockFreeViolation violation) {
        return recoveryLog.contains(violation);
    }
}

/**
 * Real implementation of MemoryBarrierTestManager for testing
 */
class MemoryBarrierTestManager implements MemoryBarrierTestManager {
    private final Map<String, MemorySnapshot> snapshots = new ConcurrentHashMap<>();
    private final List<LockFreeViolation> violations = new CopyOnWriteArrayList<>();

    @Override
    public void validateMemoryVisibility(MemorySnapshot current, MemorySnapshot last) {
        snapshots.put(current.getTimestamp().toString(), current);

        // Check for visibility issues
        for (String actorId : current.getActorStates().keySet()) {
            if (last.getActorStates().containsKey(actorId)) {
                ActorValidationState currentActor = current.getActorStates().get(actorId);
                ActorValidationState lastActor = last.getActorStates().get(actorId);

                if (currentActor.getLastUpdate().isBefore(lastActor.getLastUpdate())) {
                    violations.add(new LockFreeViolation(
                        "MEMORY_VISIBILITY",
                        Instant.now(),
                        "Visibility issue for actor: " + actorId,
                        Set.of(actorId),
                        List.of("restart", "rollback")
                    ));
                }
            }
        }
    }

    @Override
    public List<LockFreeViolation> getViolations() {
        return Collections.unmodifiableList(violations);
    }
}

/**
 * Test scenarios for lock-free validation
 */
interface LockFreeScenario {
    void setup(ActorLockFreeValidator validator);
    List<LockFreeViolation> validate(ActorLockFreeValidator validator);
    void assertResults(List<LockFreeViolation> violations);
}

class ConcurrentQueueScenario implements LockFreeScenario {
    @Override
    public void setup(ActorLockFreeValidator validator) {
        String actor = "queue-actor";
        validator.validateConcurrentQueueOperation(actor, "queue1", "enqueue");
        validator.validateConcurrentQueueOperation(actor, "queue1", "dequeue");
        validator.validateConcurrentQueueOperation(actor, "queue1", "enqueue");
    }

    @Override
    public List<LockFreeViolation> validate(ActorLockFreeValidator validator) {
        return validator.detectViolations();
    }

    @Override
    public void assertResults(List<LockFreeViolation> violations) {
        // Queue operations should not cause violations
        assertTrue(violations.isEmpty() ||
                  violations.stream().noneMatch(v -> v.getType().equals("QUEUE_INCONSISTENCY")));
    }
}

class MemoryVisibilityScenario implements LockFreeScenario {
    @Override
    public void setup(ActorLockFreeValidator validator) {
        // Create visibility issue
        MemorySnapshot snapshot1 = new MemorySnapshot(Instant.now());
        MemorySnapshot snapshot2 = new MemorySnapshot(Instant.now().plusMillis(100));

        ActorValidationState state1 = new ActorValidationState("actor-A", "state1", Instant.now());
        ActorValidationState state2 = new ActorValidationState("actor-A", "state2", Instant.now().plusMillis(50));

        snapshot1.setActorState("actor-A", state1);
        snapshot2.setActorState("actor-A", state2);

        // This should trigger visibility detection
        validator.validateMemoryVisibility(snapshot2, snapshot1);
    }

    @Override
    public List<LockFreeViolation> validate(ActorLockFreeValidator validator) {
        return validator.detectViolations();
    }

    @Override
    public void assertResults(List<LockFreeViolation> violations) {
        assertFalse(violations.isEmpty(), "Should detect memory visibility violations");
    }
}

class VirtualThreadCoordinationScenario implements LockFreeScenario {
    @Override
    public void setup(ActorLockFreeValidator validator) {
        String vt1 = "vt-1";
        String vt2 = "vt-2";
        String resource = "shared-resource";

        validator.recordVirtualThreadOperation(vt1, resource, "acquire");
        validator.recordVirtualThreadOperation(vt2, resource, "acquire");
    }

    @Override
    public List<LockFreeViolation> validate(ActorLockFreeValidator validator) {
        return validator.detectViolations();
    }

    @Override
    public void assertResults(List<LockFreeViolation> violations) {
        assertFalse(violations.isEmpty(), "Should detect virtual thread coordination violations");
    }
}

class StarvationScenario implements LockFreeScenario {
    @Override
    public void setup(ActorLockFreeValidator validator) {
        String actorA = "actor-A";
        String actorB = "actor-B";
        String resource = "resource";

        validator.recordExecutionStart(actorA, resource, Instant.now());
        validator.recordExecutionStart(actorB, resource, Instant.now().plusMillis(5000));
    }

    @Override
    public List<LockFreeViolation> validate(ActorLockFreeValidator validator) {
        return validator.detectViolations();
    }

    @Override
    public void assertResults(List<LockFreeViolation> violations) {
        assertFalse(violations.isEmpty(), "Should detect starvation");
    }
}

class MultiActorRaceScenario implements LockFreeScenario {
    @Override
    public void setup(ActorLockFreeValidator validator) {
        // Simulate race condition between multiple actors
        for (int i = 0; i < 10; i++) {
            validator.validateAtomicOperation("actor-" + i, "shared-op");
        }
    }

    @Override
    public List<LockFreeViolation> validate(ActorLockFreeValidator validator) {
        return validator.detectViolations();
    }

    @Override
    public void assertResults(List<LockFreeViolation> violations) {
        // May have some violations but should be minimal
        assertTrue(violations.size() < 3, "Should have minimal race condition violations");
    }
}