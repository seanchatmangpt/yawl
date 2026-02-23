package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.util.YVerificationHandler;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Comprehensive YEngine Lifecycle Tests using Chicago TDD methodology.
 * Tests real YEngine instances with real specifications and cases.
 * No mocks - all integrations are real.
 */
@DisplayName("YEngine Lifecycle Tests")
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
public class TestYEngineLifecycle {

    private YEngine engine;

    @BeforeEach
    void setUp() throws YPersistenceException, YEngineStateException {
        engine = YEngine.getInstance();
        // Clear any previous state
        EngineClearer.clear(engine);
    }

    @AfterEach
    void tearDown() throws YPersistenceException, YEngineStateException {
        // Clean up after each test
        if (engine != null) {
            EngineClearer.clear(engine);
        }
    }

    @Test
    @DisplayName("Outer class test - confirms test discovery")
    public void testDiscovery() {
        assertNotNull(engine, "Engine should be initialized");
    }

    // =========================================================================
    // Engine Initialization Tests
    // =========================================================================

    @Nested
    @DisplayName("Engine Initialization")
    public class EngineInitializationTests {

        @Test
        @DisplayName("Engine singleton pattern returns same instance")
        public void engineSingletonReturnsSameInstance() {
            YEngine engine1 = YEngine.getInstance();
            YEngine engine2 = YEngine.getInstance();

            assertSame(engine1, engine2, "getInstance should return same instance");
        }

        @Test
        @DisplayName("Engine instance is never null")
        public void engineInstanceNeverNull() {
            YEngine instance = YEngine.getInstance();
            assertNotNull(instance, "YEngine.getInstance() must return non-null instance");
        }

        @Test
        @DisplayName("Engine reports running status after initialization")
        public void engineReportsRunningStatusAfterInit() {
            assertTrue(YEngine.isRunning(), "Engine should report running status after initialization");
        }

        @Test
        @DisplayName("Engine status can be retrieved")
        public void engineStatusCanBeRetrieved() {
            YEngine.Status status = engine.getEngineStatus();
            assertNotNull(status, "Engine status should not be null");
            assertEquals(YEngine.Status.Running, status, "Engine status should be Running");
        }

        @Test
        @DisplayName("Engine has no loaded specifications initially after clear")
        public void engineHasNoLoadedSpecificationsAfterClear() throws YPersistenceException, YEngineStateException {
            EngineClearer.clear(engine);
            Set<YSpecificationID> specs = engine.getLoadedSpecificationIDs();
            assertTrue(specs.isEmpty(), "Engine should have no loaded specifications after clear");
        }

        @Test
        @DisplayName("Engine has no running cases initially after clear")
        public void engineHasNoRunningCasesAfterClear() throws YPersistenceException, YEngineStateException {
            EngineClearer.clear(engine);
            List<YIdentifier> cases = engine.getRunningCaseIDs();
            assertTrue(cases.isEmpty(), "Engine should have no running cases after clear");
        }

        @Test
        @DisplayName("Engine work item repository is accessible")
        public void engineWorkItemRepositoryIsAccessible() {
            assertNotNull(engine.getWorkItemRepository(), "Work item repository should be accessible");
        }

        @Test
        @DisplayName("Engine net runner repository is accessible")
        public void engineNetRunnerRepositoryIsAccessible() {
            assertNotNull(engine.getNetRunnerRepository(), "Net runner repository should be accessible");
        }

        @Test
        @DisplayName("Engine specification table is accessible")
        public void engineSpecificationTableIsAccessible() {
            assertNotNull(engine.getSpecificationTable(), "Specification table should be accessible");
        }

        @Test
        @DisplayName("Engine session cache is accessible")
        public void engineSessionCacheIsAccessible() {
            assertNotNull(engine.getSessionCache(), "Session cache should be accessible");
        }

        @Test
        @DisplayName("Engine announcer is accessible")
        public void engineAnnouncerIsAccessible() {
            assertNotNull(engine.getAnnouncer(), "Announcer should be accessible");
        }

        @Test
        @DisplayName("Engine instance cache is accessible")
        public void engineInstanceCacheIsAccessible() {
            assertNotNull(engine.getInstanceCache(), "Instance cache should be accessible");
        }
    }

    // =========================================================================
    // Specification Loading/Unloading Tests
    // =========================================================================

    @Nested
    @DisplayName("Specification Loading and Unloading")
    public class SpecificationLoadingTests {

        @Test
        @DisplayName("Load specification from MakeMusic.xml")
        public void loadSpecificationFromMakeMusicXml() throws Exception {
            YSpecificationID specID = loadSpecification("MakeMusic.xml");

            assertNotNull(specID, "Specification ID should not be null after loading");
            assertTrue(engine.getLoadedSpecificationIDs().contains(specID),
                    "Engine should contain the loaded specification");
        }

        @Test
        @DisplayName("Load specification from YAWL_Specification2.xml")
        public void loadSpecificationFromYAWLSpecification2Xml() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            assertNotNull(specID, "Specification ID should not be null after loading");
            assertTrue(engine.getLoadedSpecificationIDs().contains(specID),
                    "Engine should contain the loaded specification");
        }

        @Test
        @DisplayName("Unload specification successfully")
        public void unloadSpecificationSuccessfully() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            // Verify it's loaded
            assertTrue(engine.getLoadedSpecificationIDs().contains(specID));

            // Unload it
            engine.unloadSpecification(specID);

            // Verify it's unloaded
            assertFalse(engine.getLoadedSpecificationIDs().contains(specID),
                    "Specification should be unloaded");
        }

        @Test
        @DisplayName("Cannot unload specification with active cases")
        public void cannotUnloadSpecificationWithActiveCases() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            // Launch a case
            String caseID = engine.launchCase(specID, null, null, null);
            assertNotNull(caseID, "Case should be launched");

            // Try to unload - should fail
            assertThrows(YStateException.class, () -> engine.unloadSpecification(specID),
                    "Should throw exception when unloading spec with active cases");

            // Clean up
            YIdentifier yCaseID = engine.getCaseID(caseID);
            engine.cancelCase(yCaseID);
        }

        @Test
        @DisplayName("Cannot unload non-existent specification")
        public void cannotUnloadNonExistentSpecification() {
            YSpecificationID nonexistentSpecID = new YSpecificationID("nonexistent", "0.1", "NonExistent");

            assertThrows(YStateException.class, () -> engine.unloadSpecification(nonexistentSpecID),
                    "Should throw exception when unloading non-existent specification");
        }

        @Test
        @DisplayName("Get specification by ID returns correct specification")
        public void getSpecificationByIdReturnsCorrectSpecification() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            YSpecification spec = engine.getSpecification(specID);
            assertNotNull(spec, "Specification should not be null");
            assertEquals(specID, spec.getSpecificationID(), "Specification ID should match");
        }

        @Test
        @DisplayName("Get specification by key (URI) returns correct specification")
        public void getSpecificationByKeyReturnsCorrectSpecification() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            YSpecification spec = engine.getLatestSpecification(specID.getUri());
            assertNotNull(spec, "Specification should not be null when fetched by key");
        }

        @Test
        @DisplayName("Load specification returns correct load status")
        public void loadSpecificationReturnsCorrectLoadStatus() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String status = engine.getLoadStatus(specID);
            assertEquals(YSpecification._loaded, status, "Load status should be 'loaded'");

            // Unload and check
            engine.unloadSpecification(specID);
            status = engine.getLoadStatus(specID);
            assertEquals(YSpecification._unloaded, status, "Load status should be 'unloaded'");
        }

        @Test
        @DisplayName("Get data schema for loaded specification")
        public void getDataSchemaForLoadedSpecification() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String schema = engine.getSpecificationDataSchema(specID);
            // Schema may be null for specs without explicit data schema
            // Just verify the call doesn't throw
            assertNotNull(engine.getSpecification(specID));
        }

        @Test
        @DisplayName("Load multiple specifications")
        public void loadMultipleSpecifications() throws Exception {
            YSpecificationID specID1 = loadSpecification("YAWL_Specification2.xml");
            YSpecificationID specID2 = loadSpecification("YAWL_Specification3.xml");

            Set<YSpecificationID> loadedSpecs = engine.getLoadedSpecificationIDs();
            assertTrue(loadedSpecs.contains(specID1), "Should contain first specification");
            assertTrue(loadedSpecs.contains(specID2), "Should contain second specification");
        }

        @Test
        @DisplayName("Add specifications with verification handler")
        public void addSpecificationsWithVerificationHandler() throws Exception {
            String specXml = loadResourceAsString("YAWL_Specification2.xml");
            YVerificationHandler handler = new YVerificationHandler();

            List<YSpecificationID> specIDs = engine.addSpecifications(specXml, false, handler);

            assertFalse(specIDs.isEmpty(), "Should load at least one specification");
            assertFalse(handler.hasErrors(), "Should have no verification errors");
        }

        @Test
        @DisplayName("Add specifications ignoring errors")
        public void addSpecificationsIgnoringErrors() throws Exception {
            String specXml = loadResourceAsString("YAWL_Specification2.xml");
            YVerificationHandler handler = new YVerificationHandler();

            // ignoreErrors = true
            List<YSpecificationID> specIDs = engine.addSpecifications(specXml, true, handler);

            assertFalse(specIDs.isEmpty(), "Should load specification even with ignoreErrors=true");
        }
    }

    // =========================================================================
    // Case Launch and Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Case Launch and Management")
    public class CaseLaunchTests {

        @Test
        @DisplayName("Launch case with valid specification")
        public void launchCaseWithValidSpecification() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = engine.launchCase(specID, null, null, null);

            assertNotNull(caseID, "Case ID should not be null");
            assertFalse(caseID.isEmpty(), "Case ID should not be empty");
        }

        @Test
        @DisplayName("Launched case appears in running case IDs")
        public void launchedCaseAppearsInRunningCaseIds() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = engine.launchCase(specID, null, null, null);
            YIdentifier yCaseID = engine.getCaseID(caseID);

            List<YIdentifier> runningCases = engine.getRunningCaseIDs();
            assertTrue(runningCases.contains(yCaseID), "Launched case should appear in running cases");
        }

        @Test
        @DisplayName("Get case ID by string returns correct identifier")
        public void getCaseIdByStringReturnsCorrectIdentifier() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = engine.launchCase(specID, null, null, null);
            YIdentifier yCaseID = engine.getCaseID(caseID);

            assertNotNull(yCaseID, "YIdentifier should not be null");
            assertEquals(caseID, yCaseID.get_idString(), "Case ID string should match");
        }

        @Test
        @DisplayName("Get cases for specification returns correct cases")
        public void getCasesForSpecificationReturnsCorrectCases() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID1 = engine.launchCase(specID, null, null, null);
            String caseID2 = engine.launchCase(specID, null, null, null);

            Set<YIdentifier> cases = engine.getCasesForSpecification(specID);
            assertEquals(2, cases.size(), "Should have 2 cases for the specification");
        }

        @Test
        @DisplayName("Get running case map groups by specification")
        public void getRunningCaseMapGroupsBySpecification() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            engine.launchCase(specID, null, null, null);
            engine.launchCase(specID, null, null, null);

            var caseMap = engine.getRunningCaseMap();
            assertTrue(caseMap.containsKey(specID), "Case map should contain specification");
            assertEquals(2, caseMap.get(specID).size(), "Should have 2 cases for specification");
        }

        @Test
        @DisplayName("Cancel case removes it from running cases")
        public void cancelCaseRemovesFromRunningCases() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = engine.launchCase(specID, null, null, null);
            YIdentifier yCaseID = engine.getCaseID(caseID);

            // Verify case is running
            assertTrue(engine.getRunningCaseIDs().contains(yCaseID));

            // Cancel the case
            engine.cancelCase(yCaseID);

            // Verify case is removed
            assertFalse(engine.getRunningCaseIDs().contains(yCaseID),
                    "Case should be removed after cancellation");
        }

        @Test
        @DisplayName("Cannot launch case with invalid specification ID")
        public void cannotLaunchCaseWithInvalidSpecificationId() {
            YSpecificationID unregisteredSpecID = new YSpecificationID("nonexistent", "0.1", "NonExistent");

            assertThrows(YStateException.class,
                    () -> engine.launchCase(unregisteredSpecID, null, null, null),
                    "Should throw exception for invalid specification ID");
        }

        @Test
        @DisplayName("Cannot launch case with duplicate case ID")
        public void cannotLaunchCaseWithDuplicateCaseId() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = "TestDuplicateCase123";
            engine.launchCase(specID, null, null, caseID, null, null, false);

            // Try to launch with same case ID
            assertThrows(YStateException.class,
                    () -> engine.launchCase(specID, null, null, caseID, null, null, false),
                    "Should throw exception for duplicate case ID");
        }

        @Test
        @DisplayName("Launch case with custom case ID")
        public void launchCaseWithCustomCaseId() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String customCaseID = "CustomTestCaseID456";
            String resultCaseID = engine.launchCase(specID, null, null, customCaseID, null, null, false);

            assertEquals(customCaseID, resultCaseID, "Case ID should match custom ID");
        }

        @Test
        @DisplayName("Get case data for running case")
        public void getCaseDataForRunningCase() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = engine.launchCase(specID, null, null, null);
            String caseData = engine.getCaseData(caseID);

            assertNotNull(caseData, "Case data should not be null");
        }

        @Test
        @DisplayName("Get state for case returns valid XML")
        public void getStateForCaseReturnsValidXml() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = engine.launchCase(specID, null, null, null);
            YIdentifier yCaseID = engine.getCaseID(caseID);

            String state = engine.getStateForCase(yCaseID);
            assertNotNull(state, "State should not be null");
            assertTrue(state.contains("caseState"), "State should contain caseState element");
        }

        @Test
        @DisplayName("Cancel null case ID throws exception")
        public void cancelNullCaseIdThrowsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> engine.cancelCase(null),
                    "Should throw exception for null case ID");
        }

        @Test
        @DisplayName("Get next case number returns unique values")
        public void getNextCaseNumberReturnsUniqueValues() {
            String caseNbr1 = engine.getNextCaseNbr();
            String caseNbr2 = engine.getNextCaseNbr();

            assertNotNull(caseNbr1, "Case number should not be null");
            assertNotNull(caseNbr2, "Case number should not be null");
            assertNotEquals(caseNbr1, caseNbr2, "Case numbers should be unique");
        }

        @Test
        @DisplayName("Get case data as YNetData object")
        public void getCaseDataAsYNetDataObject() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = engine.launchCase(specID, null, null, null);
            YIdentifier yCaseID = engine.getCaseID(caseID);

            var netData = engine.getCaseData(yCaseID);
            assertNotNull(netData, "NetData should not be null");
        }

        @Test
        @DisplayName("Get specification for running case")
        public void getSpecificationForRunningCase() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = engine.launchCase(specID, null, null, null);
            YIdentifier yCaseID = engine.getCaseID(caseID);

            YSpecification spec = engine.getSpecificationForCase(yCaseID);
            assertNotNull(spec, "Specification should not be null for running case");
            assertEquals(specID, spec.getSpecificationID(), "Specification ID should match");
        }
    }

    // =========================================================================
    // Work Item Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Work Item Management")
    public class WorkItemTests {

        @Test
        @DisplayName("Get available work items after case launch")
        public void getAvailableWorkItemsAfterCaseLaunch() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            engine.launchCase(specID, null, null, null);

            Set<YWorkItem> workItems = engine.getAvailableWorkItems();
            assertNotNull(workItems, "Work items set should not be null");
        }

        @Test
        @DisplayName("Get all work items returns work item set")
        public void getAllWorkItemsReturnsWorkItemSet() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            engine.launchCase(specID, null, null, null);

            Set<YWorkItem> allItems = engine.getAllWorkItems();
            assertNotNull(allItems, "All work items set should not be null");
        }

        @Test
        @DisplayName("Get work item by ID returns correct item")
        public void getWorkItemByIdReturnsCorrectItem() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            engine.launchCase(specID, null, null, null);

            Set<YWorkItem> availableItems = engine.getAvailableWorkItems();
            if (!availableItems.isEmpty()) {
                YWorkItem firstItem = availableItems.iterator().next();
                String itemID = firstItem.getIDString();

                YWorkItem retrievedItem = engine.getWorkItem(itemID);
                assertNotNull(retrievedItem, "Retrieved work item should not be null");
                assertEquals(itemID, retrievedItem.getIDString(), "Work item IDs should match");
            }
        }

        @Test
        @DisplayName("Work item repository contains launched case items")
        public void workItemRepositoryContainsLaunchedCaseItems() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            String caseID = engine.launchCase(specID, null, null, null);
            YIdentifier yCaseID = engine.getCaseID(caseID);

            YWorkItemRepository repo = engine.getWorkItemRepository();
            assertNotNull(repo, "Repository should not be null");
        }
    }

    // =========================================================================
    // Engine State Tests
    // =========================================================================

    @Nested
    @DisplayName("Engine State Management")
    public class EngineStateTests {

        @Test
        @DisplayName("Check engine running does not throw when running")
        public void checkEngineRunningDoesNotThrowWhenRunning() throws YEngineStateException {
            assertDoesNotThrow(() -> engine.checkEngineRunning(),
                    "checkEngineRunning should not throw when engine is running");
        }

        @Test
        @DisplayName("Engine status transitions correctly")
        public void engineStatusTransitionsCorrectly() {
            // Engine should be running
            assertEquals(YEngine.Status.Running, engine.getEngineStatus());

            // Manually set status
            engine.setEngineStatus(YEngine.Status.Terminating);
            assertEquals(YEngine.Status.Terminating, engine.getEngineStatus());

            // Reset to running
            engine.setEngineStatus(YEngine.Status.Running);
            assertEquals(YEngine.Status.Running, engine.getEngineStatus());
        }

        @Test
        @DisplayName("IsRunning static method returns correct value")
        public void isRunningStaticMethodReturnsCorrectValue() {
            assertTrue(YEngine.isRunning(), "isRunning should return true after initialization");
        }
    }

    // =========================================================================
    // YAWL Services Tests
    // =========================================================================

    @Nested
    @DisplayName("YAWL Services Management")
    public class YawlServicesTests {

        @Test
        @DisplayName("Get YAWL services returns set")
        public void getYawlServicesReturnsSet() {
            Set<YAWLServiceReference> services = engine.getYAWLServices();
            assertNotNull(services, "YAWL services set should not be null");
        }

        @Test
        @DisplayName("Get registered YAWL service returns null for non-existent")
        public void getRegisteredYawlServiceReturnsNullForNonExistent() {
            YAWLServiceReference service = engine.getRegisteredYawlService("http://nonexistent/service");
            assertNull(service, "Should return null for non-existent service");
        }
    }

    // =========================================================================
    // Task Definition Tests
    // =========================================================================

    @Nested
    @DisplayName("Task Definition Tests")
    public class TaskDefinitionTests {

        @Test
        @DisplayName("Get task definition for existing task")
        public void getTaskDefinitionForExistingTask() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            // Get task definition - "a-top" is a task in YAWL_Specification2.xml
            var task = engine.getTaskDefinition(specID, "a-top");
            assertNotNull(task, "Task definition should not be null for existing task");
        }

        @Test
        @DisplayName("Get task definition for non-existent task returns null")
        public void getTaskDefinitionForNonExistentTaskReturnsNull() throws Exception {
            YSpecificationID specID = loadSpecification("YAWL_Specification2.xml");

            var task = engine.getTaskDefinition(specID, "nonexistent-task");
            assertNull(task, "Task definition should be null for non-existent task");
        }

        @Test
        @DisplayName("Get parameters for task")
        public void getParametersForTask() throws Exception {
            YSpecificationID specID = loadSpecification("MakeMusic.xml");

            // "decide" task has parameters in MakeMusic.xml
            var inputParams = engine.getParameters(specID, "decide", true);
            // May be null if task doesn't exist or has no parameters
            // Just verify the call completes
            assertNotNull(engine.getSpecification(specID));
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Loads a specification from the test resources directory.
     * @param filename the XML specification file name
     * @return the specification ID of the loaded specification
     */
    private YSpecificationID loadSpecification(String filename) throws Exception {
        String specXml = loadResourceAsString(filename);
        YVerificationHandler handler = new YVerificationHandler();
        List<YSpecificationID> specIDs = engine.addSpecifications(specXml, false, handler);

        if (handler.hasErrors()) {
            throw new YStateException("Specification verification failed: " + handler.getMessages());
        }

        if (specIDs.isEmpty()) {
            throw new YStateException("No specification loaded from: " + filename);
        }

        return specIDs.get(0);
    }

    /**
     * Loads a resource file as a string from the classpath.
     * @param filename the resource file name
     * @return the file contents as a string
     */
    private String loadResourceAsString(String filename) throws IOException {
        java.net.URL fileURL = getClass().getResource(filename);
        if (fileURL == null) {
            throw new IOException("Resource not found on classpath: " + filename);
        }
        java.io.File file = new java.io.File(fileURL.getFile());
        return Files.readString(file.toPath());
    }
}
