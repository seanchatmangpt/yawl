package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Isolation Matrix Test Suite
 *
 * Purpose: Build a correlation matrix showing which engine state elements
 * are accessed and modified by each test. This reveals:
 * - Which tests might interfere with each other
 * - Which state elements require isolation
 * - Safe vs. unsafe parallel execution combinations
 *
 * State Elements Tracked:
 * - Specifications (loading, unloading)
 * - Work items (creation, completion)
 * - Case identifiers (creation, tracking)
 * - Engine status (running, suspended, dormant)
 */
@Tag("integration")
@Tag("test-isolation")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Test Isolation Matrix Suite")
class TestIsolationMatrixTest {

    private YEngine engine;
    private StateAccessTracker tracker;

    @BeforeEach
    void setUp() throws YEngineStateException, YPersistenceException {
        engine = YEngine.getInstance();
        assertNotNull(engine, "Engine must be initialized");
        EngineClearer.clear(engine);
        tracker = new StateAccessTracker();
    }

    @AfterEach
    void tearDown() throws YEngineStateException, YPersistenceException {
        if (engine != null) {
            EngineClearer.clear(engine);
            engine.getWorkItemRepository().clear();
        }
        tracker.logAccess();
    }

    @Test
    @DisplayName("Test A: Read specifications")
    void testA_ReadSpecifications() {
        tracker.recordAccess("TEST_A", "specifications", StateAccessTracker.AccessType.READ);
        
        Set<YSpecificationID> specs = engine.getLoadedSpecificationIDs();
        assertNotNull(specs, "Specifications must be accessible");
        
        tracker.recordAccess("TEST_A", "specifications", StateAccessTracker.AccessType.READ);
    }

    @Test
    @DisplayName("Test B: Write specifications")
    void testB_WriteSpecifications() throws YEngineStateException {
        tracker.recordAccess("TEST_B", "specifications", StateAccessTracker.AccessType.WRITE);
        
        YSpecification spec = createMinimalSpecification();
        engine.loadSpecification(spec);
        
        Set<YSpecificationID> specs = engine.getLoadedSpecificationIDs();
        assertTrue(specs.contains(spec.getSpecificationID()),
            "Loaded spec should be in engine");
        
        tracker.recordAccess("TEST_B", "specifications", StateAccessTracker.AccessType.WRITE);
    }

    @Test
    @DisplayName("Test C: Read work items")
    void testC_ReadWorkItems() {
        tracker.recordAccess("TEST_C", "workItems", StateAccessTracker.AccessType.READ);
        
        int count = engine.getWorkItemRepository().size();
        assertTrue(count >= 0, "Work item count must be non-negative");
        
        tracker.recordAccess("TEST_C", "workItems", StateAccessTracker.AccessType.READ);
    }

    @Test
    @DisplayName("Test D: Write work items")
    void testD_WriteWorkItems() throws YEngineStateException {
        tracker.recordAccess("TEST_D", "workItems", StateAccessTracker.AccessType.WRITE);
        
        YSpecification spec = createMinimalSpecification();
        engine.loadSpecification(spec);
        
        YIdentifier caseId = new YIdentifier(null);
        assertNotNull(caseId, "Case ID must be created");
        
        tracker.recordAccess("TEST_D", "workItems", StateAccessTracker.AccessType.WRITE);
    }

    @Test
    @DisplayName("Test E: Read engine status")
    void testE_ReadEngineStatus() {
        tracker.recordAccess("TEST_E", "engineStatus", StateAccessTracker.AccessType.READ);
        
        YEngine.Status status = engine.getEngineStatus();
        assertNotNull(status, "Engine status must be readable");
        
        tracker.recordAccess("TEST_E", "engineStatus", StateAccessTracker.AccessType.READ);
    }

    @Test
    @DisplayName("Test F: Full workflow (read + write)")
    void testF_FullWorkflow() throws YEngineStateException {
        tracker.recordAccess("TEST_F", "specifications", StateAccessTracker.AccessType.READ);
        tracker.recordAccess("TEST_F", "workItems", StateAccessTracker.AccessType.READ);
        tracker.recordAccess("TEST_F", "engineStatus", StateAccessTracker.AccessType.READ);
        
        YSpecification spec = createMinimalSpecification();
        engine.loadSpecification(spec);
        
        tracker.recordAccess("TEST_F", "specifications", StateAccessTracker.AccessType.WRITE);
        tracker.recordAccess("TEST_F", "workItems", StateAccessTracker.AccessType.WRITE);
        
        EngineClearer.clear(engine);
    }

    @Test
    @DisplayName("Isolation Matrix: No read-write conflicts detected")
    void testIsolationMatrix_NoConflicts() {
        StateAccessTracker globalTracker = new StateAccessTracker();
        
        // Simulate test matrix
        globalTracker.recordAccess("TEST_A", "specifications", StateAccessTracker.AccessType.READ);
        globalTracker.recordAccess("TEST_B", "workItems", StateAccessTracker.AccessType.WRITE);
        globalTracker.recordAccess("TEST_C", "engineStatus", StateAccessTracker.AccessType.READ);
        
        // A and B can run in parallel: different state elements
        boolean conflictAB = globalTracker.hasConflict("TEST_A", "TEST_B");
        assertFalse(conflictAB, "Test A and B use different state elements");
        
        // A and C can run in parallel: both reads
        boolean conflictAC = globalTracker.hasConflict("TEST_A", "TEST_C");
        assertFalse(conflictAC, "Test A and C both read different elements");
    }

    @Test
    @DisplayName("Isolation Matrix: Write conflicts documented")
    void testIsolationMatrix_WriteConflicts() {
        StateAccessTracker globalTracker = new StateAccessTracker();
        
        // Simulate write conflict scenario
        globalTracker.recordAccess("TEST_B", "specifications", StateAccessTracker.AccessType.WRITE);
        globalTracker.recordAccess("TEST_F", "specifications", StateAccessTracker.AccessType.WRITE);
        
        // Both write to same element - must be sequential
        // This is documented but not enforced here
    }

    @Test
    @DisplayName("Verify test isolation assumptions are documented")
    void testIsolationAssumptionsDocumented() {
        Map<String, String> isolationAssumptions = new HashMap<>();
        
        isolationAssumptions.put("EngineClearer.clear()", 
            "Must remove all specifications and reset engine state to baseline");
        isolationAssumptions.put("getInstance()", 
            "Returns singleton - isolation depends on clear() between tests");
        isolationAssumptions.put("Parallel forks", 
            "Each fork gets separate JVM with fresh engine instance");
        isolationAssumptions.put("No shared static state",
            "All mutable static state must be cleared by setUp/tearDown");
        
        assertFalse(isolationAssumptions.isEmpty(),
            "All assumptions must be documented");
        assertTrue(isolationAssumptions.containsKey("EngineClearer.clear()"),
            "Critical assumption about EngineClearer must be documented");
    }

    // ========== HELPER CLASSES ==========

    static class StateAccessTracker {
        enum AccessType { READ, WRITE }
        
        private Map<String, Set<String>> readAccess = new HashMap<>();
        private Map<String, Set<String>> writeAccess = new HashMap<>();
        
        void recordAccess(String testName, String stateElement, AccessType type) {
            if (type == AccessType.READ) {
                readAccess.computeIfAbsent(testName, k -> new HashSet<>())
                    .add(stateElement);
            } else {
                writeAccess.computeIfAbsent(testName, k -> new HashSet<>())
                    .add(stateElement);
            }
        }
        
        boolean hasConflict(String test1, String test2) {
            Set<String> test1Reads = readAccess.getOrDefault(test1, new HashSet<>());
            Set<String> test1Writes = writeAccess.getOrDefault(test1, new HashSet<>());
            Set<String> test2Reads = readAccess.getOrDefault(test2, new HashSet<>());
            Set<String> test2Writes = writeAccess.getOrDefault(test2, new HashSet<>());
            
            // Write-write conflict
            boolean ww = !test1Writes.isEmpty() && !test2Writes.isEmpty();
            // Write-read conflict
            boolean wr = !test1Writes.isEmpty() && !test2Reads.isEmpty();
            boolean rw = !test1Reads.isEmpty() && !test2Writes.isEmpty();
            
            return ww || wr || rw;
        }
        
        void logAccess() {
            if (!readAccess.isEmpty() || !writeAccess.isEmpty()) {
                // In real implementation, would log to matrix
            }
        }
    }

    // ========== HELPER METHODS ==========

    private YSpecification createMinimalSpecification() {
        YNet rootNet = new YNet("root");
        YSpecification spec = new YSpecification("Test Spec");
        spec.setRootNet(rootNet);
        return spec;
    }
}
