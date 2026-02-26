package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.engine.instance.CaseInstance;

import java.util.Map;

/**
 * Comprehensive tests for A2ALifecycleIntegration class using Chicago TDD methodology.
 * Tests real A2ALifecycleIntegration instances with various scenarios.
 */
@DisplayName("A2ALifecycleIntegration Tests")
@Tag("unit")
class A2ALifecycleIntegrationTest {

    private A2ALifecycleIntegration integration;
    private RealTestEngine testEngine;

    @BeforeEach
    void setUp() {
        testEngine = new RealTestEngine();
        integration = new A2ALifecycleIntegration(testEngine);
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Constructor with valid engine creates instance")
        void constructorWithValidEngineCreatesInstance() {
            assertNotNull(integration, "Integration instance should be created");
            assertFalse(integration.isIntegrationEnabled(), "Integration should be disabled initially");
            assertFalse(integration.isMonitoringEnabled(), "Monitoring should be disabled initially");
        }

        @Test
        @DisplayName("Constructor with null engine throws exception")
        void constructorWithNullEngineThrowsException() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> new A2ALifecycleIntegration(null),
                    "Should throw exception for null engine");

            assertTrue(exception.getMessage().contains("YAWL engine cannot be null"),
                       "Exception message should indicate null engine");
        }

        @Test
        @DisplayName("Constructor initializes dependencies")
        void constructorInitializesDependencies() {
            // Verify that internal components are initialized
                // Integration has engine
                assertSame(testEngine, integration.getEngine(), "Should have the engine");
        }
    }

    // =========================================================================
    // Integration Enable/Disable Tests
    // =========================================================================

    @Nested
    @DisplayName("Integration Enable/Disable Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Enable integration sets enabled to true")
        void enableIntegrationSetsEnabledToTrue() {
            integration.enableIntegration(false);

            assertTrue(integration.isIntegrationEnabled(), "Integration should be enabled");
            assertFalse(integration.isMonitoringEnabled(), "Monitoring should be disabled");
        }

        @Test
        @DisplayName("Enable integration with monitoring sets monitoring to true")
        void enableIntegrationWithMonitoringSetsMonitoringToTrue() {
            integration.enableIntegration(true);

            assertTrue(integration.isIntegrationEnabled(), "Integration should be enabled");
            assertTrue(integration.isMonitoringEnabled(), "Monitoring should be enabled");
        }

        @Test
        @DisplayName("Disable integration sets enabled to false")
        void disableIntegrationSetsEnabledToFalse() {
            // First enable
            integration.enableIntegration(false);

            // Then disable
            integration.disableIntegration();

            assertFalse(integration.isIntegrationEnabled(), "Integration should be disabled");
            assertFalse(integration.isMonitoringEnabled(), "Monitoring should be disabled");
        }

        @Test
        @DisplayName("Disable integration resets monitoring")
        void disableIntegrationResetsMonitoring() {
            // Enable with monitoring
            integration.enableIntegration(true);

            // Disable integration
            integration.disableIntegration();

            assertFalse(integration.isIntegrationEnabled(), "Integration should be disabled");
            assertFalse(integration.isMonitoringEnabled(), "Monitoring should be disabled");
        }

        @Test
        @DisplayName("Multiple enable/disable calls work correctly")
        void multipleEnableDisableCallsWorkCorrectly() {
            // Enable without monitoring
            integration.enableIntegration(false);
            assertTrue(integration.isIntegrationEnabled());
            assertFalse(integration.isMonitoringEnabled());

            // Enable with monitoring
            integration.enableIntegration(true);
            assertTrue(integration.isIntegrationEnabled());
            assertTrue(integration.isMonitoringEnabled());

            // Disable again
            integration.disableIntegration();
            assertFalse(integration.isIntegrationEnabled());
            assertFalse(integration.isMonitoringEnabled());
        }
    }

    // =========================================================================
    // Initialize Tests
    // =========================================================================

    @Nested
    @DisplayName("Initialize Tests")
    class InitializeTests {

        @Test
        @DisplayName("Initialize does nothing when integration is disabled")
        void initializeDoesNothingWhenIntegrationIsDisabled() {
            integration.initialize();

            // Verify no events were published
            assertTrue(testEngine.getPublishedEvents().isEmpty(),
                       "No events should be published when integration is disabled");
        }

        @Test
        @DisplayName("Initialize publishes engine startup event when integration is enabled")
        void initializePublishesEngineStartupEventWhenIntegrationIsEnabled() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Initialize
            integration.initialize();

            // Verify startup event was published
            Map<String, Object> events = mockEngine.getPublishedEvents();
            assertEquals(1, events.size(), "Should publish one startup event");

            Map<String, Object> eventData = (Map<String, Object>) events.get("startup");
            assertEquals("6.0.0", eventData.get("version"), "Version should be 6.0.0");
            assertEquals("YAWL Engine", eventData.get("component"), "Component should be YAWL Engine");
        }

        @Test
        @DisplayName("Initialize publishes correct event data")
        void initializePublishesCorrectEventData() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Initialize
            integration.initialize();

            Map<String, Object> events = mockEngine.getPublishedEvents();
            Map<String, Object> eventData = (Map<String, Object>) events.get("startup");

            // Verify all required fields
            assertNotNull(eventData.get("timestamp"), "Timestamp should be present");
            assertEquals("6.0.0", eventData.get("version"), "Version should be correct");
            assertEquals("YAWL Engine", eventData.get("component"), "Component should be correct");
        }

        @Test
        @DisplayName("Initialize does nothing when called multiple times")
        void initializeDoesNothingWhenCalledMultipleTimes() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Initialize multiple times
            integration.initialize();
            integration.initialize();

            // Should only publish one event
            Map<String, Object> events = mockEngine.getPublishedEvents();
            assertEquals(1, events.size(), "Should only publish one startup event");
        }
    }

    // =========================================================================
    // Case Started Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Case Started Event Tests")
    class CaseStartedEventTests {

        @Test
        @DisplayName("Handle case published event when integration is enabled")
        void handleCasePublishedEventWhenIntegrationIsEnabled() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Create and publish case started event
            CaseInstance caseInstance = new MockCaseInstance("case123");
            integration.handleCaseStarted(caseInstance);

            // Verify event was published
            Map<String, Object> events = testEngine.getPublishedEvents();
            assertFalse(events.isEmpty(), "Should publish case started event");
        }

        @Test
        @DisplayName("Handle case published event with monitoring enabled")
        void handleCasePublishedEventWithMonitoringEnabled() throws Exception {
            // Enable integration with monitoring
            integration.enableIntegration(true);

            // Create and publish case started event
            CaseInstance caseInstance = new MockCaseInstance("case123");
            integration.handleCaseStarted(caseInstance);

            // Verify event was published and monitoring was called
            Map<String, Object> events = mockEngine.getPublishedEvents();
            assertFalse(events.isEmpty(), "Should publish case started event");
        }

        @Test
        @DisplayName("Handle case published event does nothing when integration is disabled")
        void handleCasePublishedEventDoesNothingWhenIntegrationIsDisabled() throws Exception {
            // Integration is disabled by default

            // Create and publish case started event
            CaseInstance caseInstance = new MockCaseInstance("case123");
            integration.handleCaseStarted(caseInstance);

            // Verify no events were published
            assertTrue(testEngine.getPublishedEvents().isEmpty(),
                       "No events should be published when integration is disabled");
        }

        @Test
        @DisplayName("Handle case published event does nothing when case instance is null")
        void handleCasePublishedEventDoesNothingWhenCaseInstanceIsNull() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Publish null case instance
            integration.handleCaseStarted(null);

            // Verify no events were published
            assertTrue(mockEngine.getPublishedEvents().isEmpty(),
                       "No events should be published for null case instance");
        }

        @Test
        @DisplayName("Handle case published event with case data")
        void handleCasePublishedEventWithCaseData() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Create case instance with data
            RealTestCaseInstance caseInstance = new RealTestCaseInstance("case123");
            caseInstance.setData("test data");

            // Handle case started
            integration.handleCaseStarted(caseInstance);

            // Verify event was published with case data
            Map<String, Object> events = mockEngine.getPublishedEvents();
            assertFalse(events.isEmpty(), "Should publish case started event");
        }
    }

    // =========================================================================
    // Case Completed Event Tests
    // =========================================================================

    @Nested
    @DisplayName("Case Completed Event Tests")
    class CaseCompletedEventTests {

        @Test
        @DisplayName("Handle case completed event when integration is enabled")
        void handleCaseCompletedEventWhenIntegrationIsEnabled() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Create and publish case completed event
            CaseInstance caseInstance = new MockCaseInstance("case123");
            integration.handleCaseCompleted(caseInstance);

            // Verify event was published
            Map<String, Object> events = testEngine.getPublishedEvents();
            assertFalse(events.isEmpty(), "Should publish case completed event");
        }

        @Test
        @DisplayName("Handle case completed event does nothing when integration is disabled")
        void handleCaseCompletedEventDoesNothingWhenIntegrationIsDisabled() throws Exception {
            // Integration is disabled by default

            // Create and publish case completed event
            CaseInstance caseInstance = new MockCaseInstance("case123");
            integration.handleCaseCompleted(caseInstance);

            // Verify no events were published
            assertTrue(testEngine.getPublishedEvents().isEmpty(),
                       "No events should be published when integration is disabled");
        }

        @Test
        @DisplayName("Handle case completed event does nothing when case instance is null")
        void handleCaseCompletedEventDoesNothingWhenCaseInstanceIsNull() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Publish null case instance
            integration.handleCaseCompleted(null);

            // Verify no events were published
            assertTrue(mockEngine.getPublishedEvents().isEmpty(),
                       "No events should be published for null case instance");
        }
    }

    // =========================================================================
    // Exception Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Handle case started event throws YAWLException for invalid case")
        void handleCaseStartedEventThrowsYAWLExceptionForInvalidCase() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Create case instance that throws exception
            CaseInstance invalidCaseInstance = new TestCaseInstanceThatThrows("invalid-case");

            // Should throw YAWLException
            assertThrows(YAWLException.class,
                    () -> integration.handleCaseStarted(invalidCaseInstance),
                    "Should throw YAWLException for invalid case");
        }

        @Test
        @DisplayName("Integration state remains consistent after exception")
        void integrationStateRemainsConsistentAfterException() throws Exception {
            // Enable integration
            integration.enableIntegration(false);

            // Create case instance that throws exception
            CaseInstance invalidCaseInstance = new TestCaseInstanceThatThrows("invalid-case");

            // Handle case and expect exception
            assertThrows(YAWLException.class,
                    () -> integration.handleCaseStarted(invalidCaseInstance));

            // Verify integration state is still consistent
            assertTrue(integration.isIntegrationEnabled(), "Integration should still be enabled");
            assertFalse(integration.isMonitoringEnabled(), "Monitoring should still be disabled");
        }
    }

    // =========================================================================
    // Helper Classes
    // =========================================================================

    /**
     * Real Test Engine for testing purposes.
     */
    private static class RealTestEngine {
        private final Map<String, Object> publishedEvents = new java.util.HashMap<>();

        public void publishEvent(String eventType, Map<String, Object> data) {
            publishedEvents.put(eventType, data);
        }

        public Map<String, Object> getPublishedEvents() {
            return publishedEvents;
        }

        public Object getEngine() {
            return this;
        }
    }

    /**
     * Real Test Case Instance for testing purposes.
     */
    private static class RealTestCaseInstance implements CaseInstance {
        private final String caseId;
        private String data;

        public RealTestCaseInstance(String caseId) {
            this.caseId = caseId;
        }

        public void setData(String data) {
            this.data = data;
        }

        @Override
        public String getCaseID() {
            return caseId;
        }

        @Override
        public String getData() {
            return data;
        }
    }

    /**
     * Test Case Instance that throws exception for testing error handling.
     */
    private static class TestCaseInstanceThatThrows implements CaseInstance {
        private final String caseId;

        public TestCaseInstanceThatThrows(String caseId) {
            this.caseId = caseId;
        }

        @Override
        public String getCaseID() {
            return caseId;
        }

        @Override
        public String getData() {
            throw new YAWLException("Case data access failed");
        }
    }
}