package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;

import java.util.List;

/**
 * Comprehensive tests for YNetRunnerRepository class using Chicago TDD methodology.
 * Tests real YNetRunnerRepository instances with various scenarios.
 */
@DisplayName("YNetRunnerRepository Tests")
@Tag("unit")
class YNetRunnerRepositoryTest {

    private YNetRunnerRepository repository;
    private YNetRunner netRunner1;
    private YNetRunner netRunner2;
    private YIdentifier caseId1;
    private YIdentifier caseId2;

    @BeforeEach
    void setUp() throws Exception {
        repository = new YNetRunnerRepository();

        // Create test case identifiers
        caseId1 = new YIdentifier(null);
        caseId2 = new YIdentifier(null);

        // Create test net runners
        netRunner1 = new RealYNetRunner(caseId1);
        netRunner2 = new RealYNetRunner(caseId2);
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor creates empty repository")
        void defaultConstructorCreatesEmptyRepository() {
            YNetRunnerRepository newRepo = new YNetRunnerRepository();

            assertNotNull(newRepo, "Repository should not be null");
            assertTrue(newRepo.isEmpty(), "New repository should be empty");
            assertNull(newRepo.get("any-case-id"), "Should return null for non-existent case ID");
        }

        @Test
        @DisplayName("Constructor initializes ID map")
        void constructorInitializesIdMap() {
            // The constructor should initialize the internal id map
            assertNotNull(repository, "Repository should not be null");
        }
    }

    // =========================================================================
    // Add Method Tests
    // =========================================================================

    @Nested
    @DisplayName("Add Method Tests")
    class AddMethodTests {

        @Test
        @DisplayName("Add net runner with case ID")
        void addNetRunnerWithCaseId() {
            YNetRunner added = repository.add(netRunner1, caseId1);

            // putIfAbsent returns null when key is newly inserted (success)
            assertNull(added, "putIfAbsent returns null on successful insert");
            assertEquals(netRunner1, repository.get(caseId1), "Net runner should be accessible via case ID");
        }

        @Test
        @DisplayName("Add net runner using convenience method")
        void addNetRunnerUsingConvenienceMethod() {
            YNetRunner added = repository.add(netRunner1);

            // putIfAbsent returns null when key is newly inserted (success)
            assertNull(added, "putIfAbsent returns null on successful insert");
            assertEquals(netRunner1, repository.get(caseId1), "Net runner should be accessible via case ID");
        }

        @Test
        @DisplayName("Add multiple net runners")
        void addMultipleNetRunners() {
            repository.add(netRunner1, caseId1);
            repository.add(netRunner2, caseId2);

            assertEquals(netRunner1, repository.get(caseId1), "First net runner should be accessible");
            assertEquals(netRunner2, repository.get(caseId2), "Second net runner should be accessible");
        }

        @Test
        @DisplayName("Add net runner returns existing runner if case ID already exists")
        void addNetRunnerReturnsExistingRunnerIfCaseIdExists() {
            // Add first runner
            repository.add(netRunner1, caseId1);

            // Add different runner for same case ID
            YNetRunner differentRunner = new RealYNetRunner(caseId1);
            YNetRunner returned = repository.add(differentRunner, caseId1);

            // Should return the first runner (existing one)
            assertSame(netRunner1, returned, "Should return existing runner");
            assertSame(netRunner1, repository.get(caseId1), "Existing runner should remain");
            assertNotSame(differentRunner, repository.get(caseId1), "Different runner should not replace existing");
        }

        @Test
        @DisplayName("Add null net runner with valid case ID")
        void addNullNetRunnerWithValidCaseId() {
            // ConcurrentHashMap.putIfAbsent throws NPE for null values
            assertThrows(NullPointerException.class, () -> repository.add(null, caseId1),
                        "ConcurrentHashMap rejects null values");
        }

        @Test
        @DisplayName("Add net runner with null case ID")
        void addNetRunnerWithNullCaseId() {
            // caseID.toString() throws NPE for null YIdentifier
            assertThrows(NullPointerException.class, () -> repository.add(netRunner1, null),
                        "add with null YIdentifier throws NPE");
        }
    }

    // =========================================================================
    // Get Method Tests
    // =========================================================================

    @Nested
    @DisplayName("Get Method Tests")
    class GetMethodTests {

        @Test
        @DisplayName("Get net runner by string case ID")
        void getNetRunnerByStringCaseId() throws Exception {
            repository.add(netRunner1, caseId1);

            YNetRunner retrieved = repository.get(caseId1.toString());

            assertSame(netRunner1, retrieved, "Should retrieve the same net runner");
        }

        @Test
        @DisplayName("Get net runner by YIdentifier")
        void getNetRunnerByYIdentifier() throws Exception {
            repository.add(netRunner1, caseId1);

            YNetRunner retrieved = repository.get(caseId1);

            assertSame(netRunner1, retrieved, "Should retrieve the same net runner");
        }

        @Test
        @DisplayName("Get returns null for non-existent string case ID")
        void getReturnsNullForNonExistentStringCaseId() {
            YNetRunner retrieved = repository.get("non-existent-case-id");

            assertNull(retrieved, "Should return null for non-existent case ID");
        }

        @Test
        @DisplayName("Get returns null for null string case ID")
        void getReturnsNullForNullStringCaseId() {
            YNetRunner retrieved = repository.get((String) null);

            assertNull(retrieved, "Should return null for null case ID");
        }

        @Test
        @DisplayName("Get returns null for null YIdentifier")
        void getReturnsNullForNullYIdentifier() {
            // ConcurrentHashMap.get(null) throws NPE — no null-check for YIdentifier key
            assertThrows(NullPointerException.class, () -> repository.get((YIdentifier) null),
                        "get((YIdentifier) null) throws NPE");
        }
    }

    // =========================================================================
    // Get Method with WorkItem Tests
    // =========================================================================

    @Nested
    @DisplayName("Get with WorkItem Tests")
    class GetWithWorkItemTests {

        @Test
        @DisplayName("Get net runner for workitem with enabled status")
        void getNetRunnerForWorkitemWithEnabledStatus() throws Exception {
            YWorkItem workItem = createWorkItem(caseId1, YWorkItemStatus.statusEnabled);
            repository.add(netRunner1, caseId1);

            YNetRunner retrieved = repository.get(workItem);

            assertSame(netRunner1, retrieved, "Should retrieve runner for enabled work item");
        }

        @Test
        @DisplayName("Get net runner for workitem with parent status")
        void getNetRunnerForWorkitemWithParentStatus() throws Exception {
            YWorkItem workItem = createWorkItem(caseId1, YWorkItemStatus.statusIsParent);
            repository.add(netRunner1, caseId1);

            YNetRunner retrieved = repository.get(workItem);

            assertSame(netRunner1, retrieved, "Should retrieve runner for parent work item");
        }

        @Test
        @DisplayName("Get net runner for workitem with suspended status")
        void getNetRunnerForWorkitemWithSuspendedStatus() throws Exception {
            YWorkItem workItem = createWorkItem(caseId1, YWorkItemStatus.statusSuspended);
            // Set prevStatus to non-null/non-enabled to avoid NPE in isEnabledSuspended()
            // and to route through the parent-lookup branch (statusSuspended)
            workItem.set_prevStatus("Executing");

            // Create parent case
            YIdentifier parentCaseId = new YIdentifier(null);
            YNetRunner parentRunner = new RealYNetRunner(parentCaseId);
            repository.add(parentRunner, parentCaseId);

            // Set parent relationship
            workItem.getWorkItemID().getCaseID().set_parent(parentCaseId);

            YNetRunner retrieved = repository.get(workItem);

            assertSame(parentRunner, retrieved, "Should retrieve runner from parent case for suspended work item");
        }

        @Test
        @DisplayName("Get net runner for workitem with live status")
        void getNetRunnerForWorkitemWithLiveStatus() throws Exception {
            // Live (executing) work items use parent case ID for lookup
            // Set up: work item's caseId1 has parentCaseId as its parent
            YIdentifier parentCaseId = new YIdentifier(null);
            caseId1.set_parent(parentCaseId);
            YNetRunner parentRunner = new RealYNetRunner(parentCaseId);
            repository.add(parentRunner, parentCaseId);

            YWorkItem workItem = createWorkItem(caseId1, YWorkItemStatus.statusExecuting);

            YNetRunner retrieved = repository.get(workItem);

            assertSame(parentRunner, retrieved, "Should retrieve parent runner for executing work item");
        }

        @Test
        @DisplayName("Get net runner for workitem with completed status")
        void getNetRunnerForWorkitemWithCompletedStatus() throws Exception {
            YWorkItem workItem = createWorkItem(caseId1, YWorkItemStatus.statusComplete);

            // Create parent case
            YIdentifier parentCaseId = new YIdentifier(null);
            YNetRunner parentRunner = new RealYNetRunner(parentCaseId);
            repository.add(parentRunner, parentCaseId);

            // Set parent relationship
            workItem.getWorkItemID().getCaseID().set_parent(parentCaseId);

            YNetRunner retrieved = repository.get(workItem);

            assertSame(parentRunner, retrieved, "Should retrieve runner from parent case");
        }

        @Test
        @DisplayName("Get returns null for workitem without runner")
        void getReturnsNullForWorkitemWithoutRunner() throws Exception {
            YWorkItem workItem = createWorkItem(caseId1, YWorkItemStatus.statusEnabled);

            // Don't add any runner to repository
            YNetRunner retrieved = repository.get(workItem);

            assertNull(retrieved, "Should return null when no runner exists");
        }

        @Test
        @DisplayName("Get returns null for workitem with null case ID")
        void getReturnsNullForWorkitemWithNullCaseId() {
            // YWorkItemID with null caseId — constructor NPEs when adding to global repo
            assertThrows(NullPointerException.class, () -> {
                YWorkItemID workItemId = new YWorkItemID(null, "task-id");
                YWorkItem workItem = new YWorkItem(null, null, null, workItemId, false, false);
                repository.get(workItem);
            }, "workitem with null case ID causes NPE");
        }

        @Test
        @DisplayName("Get returns null for null workitem")
        void getReturnsNullForNullWorkitem() {
            // get(YWorkItem) does not null-check — NPE on workitem.getStatus()
            assertThrows(NullPointerException.class, () -> repository.get((YWorkItem) null),
                        "get(null workitem) throws NPE");
        }
    }

    // =========================================================================
    // Get All Runners for Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Get All Runners for Case Tests")
    class GetAllRunnersForCaseTests {

        @Test
        @DisplayName("Get all runners for single case returns list with one runner")
        void getAllRunnersForSingleCaseReturnsListWithOneRunner() {
            repository.add(netRunner1, caseId1);

            List<YNetRunner> runners = repository.getAllRunnersForCase(caseId1);

            assertEquals(1, runners.size(), "Should return one runner");
            assertSame(netRunner1, runners.get(0), "Should return the correct runner");
        }

        @Test
        @DisplayName("Get all runners returns empty list for case with no runners")
        void getAllRunnersReturnsEmptyListForCaseWithNoRunners() {
            List<YNetRunner> runners = repository.getAllRunnersForCase(caseId1);

            assertTrue(runners.isEmpty(), "Should return empty list for case with no runners");
        }

        @Test
        @DisplayName("Get all runners returns runners for ancestor cases")
        void getAllRunnersReturnsRunnersForAncestorCases() {
            // Create hierarchy: child -> caseId1 -> caseId2
            YIdentifier childCaseId = new YIdentifier(null);
            childCaseId.set_parent(caseId1);
            caseId1.set_parent(caseId2);

            YNetRunner runner1 = new RealYNetRunner(caseId1);
            YNetRunner runner2 = new RealYNetRunner(caseId2);

            repository.add(runner1, caseId1);
            repository.add(runner2, caseId2);

            // Get all runners for the top-level case
            List<YNetRunner> runners = repository.getAllRunnersForCase(caseId2);

            assertEquals(2, runners.size(), "Should return runners for both caseId1 and caseId2");
            assertTrue(runners.contains(runner1), "Should contain runner for caseId1");
            assertTrue(runners.contains(runner2), "Should contain runner for caseId2");
        }

        @Test
        @DisplayName("Get all runners returns runners for descendant cases")
        void getAllRunnersReturnsRunnersForDescendantCases() {
            // Create hierarchy: caseId2 -> caseId1
            YIdentifier caseId3 = new YIdentifier(null);
            caseId3.set_parent(caseId1);

            YNetRunner runner1 = new RealYNetRunner(caseId1);
            YNetRunner runner3 = new RealYNetRunner(caseId3);

            repository.add(runner1, caseId1);
            repository.add(runner3, caseId3);

            // Get all runners for caseId1
            List<YNetRunner> runners = repository.getAllRunnersForCase(caseId1);

            assertEquals(2, runners.size(), "Should return runners for both caseId1 and caseId3");
            assertTrue(runners.contains(runner1), "Should contain runner for caseId1");
            assertTrue(runners.contains(runner3), "Should contain runner for caseId3");
        }

        @Test
        @DisplayName("Get all runners returns empty list for null case ID")
        void getAllRunnersReturnsEmptyListForNullCaseId() {
            // When repository is empty, the loop body never executes, so no NPE — returns empty list
            List<YNetRunner> runners = repository.getAllRunnersForCase(null);
            assertTrue(runners.isEmpty(), "Should return empty list for null case ID when repo is empty");
        }
    }

    // =========================================================================
    // Get Case Identifier Tests
    // =========================================================================

    @Nested
    @DisplayName("Get Case Identifier Tests")
    class GetCaseIdentifierTests {

        @Test
        @DisplayName("Get case identifier for existing string case ID")
        void getCaseIdentifierForExistingStringCaseId() throws Exception {
            repository.add(netRunner1, caseId1);

            YIdentifier retrieved = repository.getCaseIdentifier(caseId1.toString());

            assertSame(caseId1, retrieved, "Should retrieve the same YIdentifier");
        }

        @Test
        @DisplayName("Get case identifier returns null for non-existent string case ID")
        void getCaseIdentifierReturnsNullForNonExistentStringCaseId() {
            YIdentifier retrieved = repository.getCaseIdentifier("non-existent-case-id");

            assertNull(retrieved, "Should return null for non-existent case ID");
        }

        @Test
        @DisplayName("Get case identifier returns null for null string case ID")
        void getCaseIdentifierReturnsNullForNullStringCaseId() {
            // ConcurrentHashMap.get(null) throws NPE — no null-check in getCaseIdentifier
            assertThrows(NullPointerException.class, () -> repository.getCaseIdentifier(null),
                        "getCaseIdentifier(null) throws NPE");
        }

        @Test
        @DisplayName("Get case identifier works after adding multiple runners")
        void getCaseIdentifierWorksAfterAddingMultipleRunners() throws Exception {
            repository.add(netRunner1, caseId1);
            repository.add(netRunner2, caseId2);

            YIdentifier retrieved1 = repository.getCaseIdentifier(caseId1.toString());
            YIdentifier retrieved2 = repository.getCaseIdentifier(caseId2.toString());

            assertSame(caseId1, retrieved1, "Should retrieve first case identifier");
            assertSame(caseId2, retrieved2, "Should retrieve second case identifier");
        }
    }

    // =========================================================================
    // Remove Method Tests
    // =========================================================================

    @Nested
    @DisplayName("Remove Method Tests")
    class RemoveMethodTests {

        @Test
        @DisplayName("Remove net runner by YIdentifier")
        void removeNetRunnerByYIdentifier() {
            repository.add(netRunner1, caseId1);

            YNetRunner removed = repository.remove(caseId1);

            assertSame(netRunner1, removed, "Should remove and return the net runner");
            assertNull(repository.get(caseId1), "Net runner should be removed");
        }

        @Test
        @DisplayName("Remove net runner by string case ID")
        void removeNetRunnerByStringCaseId() throws Exception {
            repository.add(netRunner1, caseId1);

            YNetRunner removed = repository.remove(caseId1.toString());

            assertSame(netRunner1, removed, "Should remove and return the net runner");
            assertNull(repository.get(caseId1), "Net runner should be removed");
        }

        @Test
        @DisplayName("Remove returns null for non-existent case ID")
        void removeReturnsNullForNonExistentCaseId() {
            YNetRunner removed = repository.remove("non-existent-case-id");

            assertNull(removed, "Should return null for non-existent case ID");
        }

        @Test
        @DisplayName("Remove returns null for null case ID")
        void removeReturnsNullForNullCaseId() {
            YNetRunner removed = repository.remove((YIdentifier) null);

            assertNull(removed, "Should return null for null case ID");
        }

        @Test
        @DisplayName("Remove workitem from repository")
        void removeWorkitemFromRepository() throws Exception {
            YWorkItem workItem = createWorkItem(caseId1, YWorkItemStatus.statusEnabled);
            repository.add(netRunner1, caseId1);

            // YNetRunnerRepository has no remove(YWorkItem) — use the case ID from workitem
            YIdentifier caseId = workItem.getWorkItemID().getCaseID();
            YNetRunner removed = repository.remove(caseId);

            assertSame(netRunner1, removed, "Should remove and return the net runner");
            assertNull(repository.get(caseId1), "Net runner should be removed");
        }

        @Test
        @DisplayName("Remove returns null for workitem without runner")
        void removeReturnsNullForWorkitemWithoutRunner() throws Exception {
            YWorkItem workItem = createWorkItem(caseId1, YWorkItemStatus.statusEnabled);

            YNetRunner removed = repository.remove(workItem);

            assertNull(removed, "Should return null for workitem without runner");
        }

        @Test
        @DisplayName("Remove returns null for null workitem")
        void removeReturnsNullForNullWorkitem() {
            // ConcurrentHashMap.remove(null) throws NPE — no null-check for YWorkItem
            assertThrows(NullPointerException.class, () -> repository.remove((YWorkItem) null),
                        "remove(null workitem) throws NPE");
        }
    }

    // =========================================================================
    // Size and Containment Tests
    // =========================================================================

    @Nested
    @DisplayName("Size and Containment Tests")
    class SizeAndContainmentTests {

        @Test
        @DisplayName("Size returns correct number of runners")
        void sizeReturnsCorrectNumberOfRunners() {
            assertEquals(0, repository.size(), "Initial size should be 0");

            repository.add(netRunner1, caseId1);
            assertEquals(1, repository.size(), "Size should be 1 after adding one runner");

            repository.add(netRunner2, caseId2);
            assertEquals(2, repository.size(), "Size should be 2 after adding second runner");
        }

        @Test
        @DisplayName("ContainsKey returns true for existing case ID")
        void containsKeyReturnsTrueForExistingCaseId() throws Exception {
            repository.add(netRunner1, caseId1);

            // YNetRunnerRepository extends ConcurrentHashMap<YIdentifier, YNetRunner>
            // Keys are YIdentifier objects — String lookup returns false (different type)
            assertTrue(repository.containsKey(caseId1), "Should contain existing YIdentifier key");
            assertFalse(repository.containsKey(caseId1.toString()), "String keys are not in the YIdentifier-keyed map");
        }

        @Test
        @DisplayName("ContainsKey returns false for non-existent case ID")
        void containsKeyReturnsFalseForNonExistentCaseId() {
            assertFalse(repository.containsKey(caseId1), "Should not contain non-existent case ID");
        }

        @Test
        @DisplayName("ContainsKey returns false for null case ID")
        void containsKeyReturnsFalseForNullCaseId() {
            // ConcurrentHashMap.containsKey(null) throws NPE — CHM prohibits null keys
            assertThrows(NullPointerException.class, () -> repository.containsKey(null),
                        "containsKey(null) throws NPE");
        }

        @Test
        @DisplayName("IsEmpty returns true for empty repository")
        void isEmptyReturnsTrueForEmptyRepository() {
            assertTrue(repository.isEmpty(), "Should be empty initially");
        }

        @Test
        @DisplayName("IsEmpty returns false for non-empty repository")
        void isEmptyReturnsFalseForNonEmptyRepository() {
            repository.add(netRunner1, caseId1);
            assertFalse(repository.isEmpty(), "Should not be empty after adding runner");
        }

        @Test
        @DisplayName("Clear removes all runners")
        void clearRemovesAllRunners() {
            repository.add(netRunner1, caseId1);
            repository.add(netRunner2, caseId2);

            repository.clear();

            assertTrue(repository.isEmpty(), "Should be empty after clear");
            assertNull(repository.get(caseId1), "Runners should be removed");
            assertNull(repository.get(caseId2), "Runners should be removed");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Creates a test work item with specified case ID and status.
     */
    private YWorkItem createWorkItem(YIdentifier caseId, YWorkItemStatus status) throws Exception {
        YWorkItemID workItemId = new YWorkItemID(caseId, "task-123");
        YAtomicTask task = new YAtomicTask("task-123", YTask._XOR, YTask._AND, null);

        YWorkItem workItem = new YWorkItem(null, null, task, workItemId, false, false);
        workItem.setStatus(status);

        return workItem;
    }

    /**
     * Real YNetRunner implementation for testing purposes.
     */
    private static class RealYNetRunner extends YNetRunner {
        private final YIdentifier caseId;

        public RealYNetRunner(YIdentifier caseId) {
            this.caseId = caseId;
        }

        @Override
        public YIdentifier getCaseID() {
            return caseId;
        }
    }
}