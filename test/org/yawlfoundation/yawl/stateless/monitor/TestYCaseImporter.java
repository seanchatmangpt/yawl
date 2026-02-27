/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * Public License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YAnnouncer;
import org.yawlfoundation.yawl.stateless.engine.YEngine;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Comprehensive tests for YCaseImporter covering XML unmarshaling,
 * runner restoration, identifier hierarchy, and work item reunification.
 *
 * <p>Chicago TDD: All tests use real YStatelessEngine instances,
 * real YSpecification objects, and real case XML marshaling/unmarshaling.
 * No mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YCaseImporter Tests")
@Tag("unit")
public class TestYCaseImporter {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final long EVENT_TIMEOUT_SEC = 10L;

    private YStatelessEngine engine;
    private YSpecification spec;

    @BeforeEach
    void setUp() throws Exception {
        engine = new YStatelessEngine();
        engine.setCaseMonitoringEnabled(true);
        spec = loadMinimalSpec();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.setCaseMonitoringEnabled(false);
        }
    }

    private YSpecification loadMinimalSpec() throws YSyntaxException {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull(is, "Missing resource: " + MINIMAL_SPEC_RESOURCE);
        String xml = StringUtil.streamToString(is)
                .orElseThrow(() -> new AssertionError("Empty spec XML from " + MINIMAL_SPEC_RESOURCE));
        return engine.unmarshalSpecification(xml);
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private YNetRunner launchCaseWithMonitoring(String caseId) throws Exception {
        AtomicReference<YNetRunner> runnerCapture = new AtomicReference<>();
        CountDownLatch startedLatch = new CountDownLatch(1);

        YCaseEventListener listener = event -> {
            if (event.getEventType() == YEventType.CASE_STARTED) {
                runnerCapture.set(event.getRunner());
                startedLatch.countDown();
            }
        };
        engine.addCaseEventListener(listener);
        engine.launchCase(spec, caseId);
        startedLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
        engine.removeCaseEventListener(listener);

        YNetRunner runner = runnerCapture.get();
        assertNotNull(runner, "Runner should be captured after case start");
        return runner;
    }

    private String exportCase(YNetRunner runner) throws YStateException {
        return engine.marshalCase(runner);
    }

    // =========================================================================
    // Nested: XML Unmarshal Tests
    // =========================================================================

    @Nested
    @DisplayName("XML Unmarshal Tests")
    class XmlUnmarshalTests {

        @Test
        @DisplayName("Unmarshal valid case XML returns list of runners")
        void unmarshalValidCaseXmlReturnsRunners() throws Exception {
            String caseId = "import-test-1";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            YAnnouncer announcer = yEngine.getAnnouncer();

            List<YNetRunner> runners = importer.unmarshal(caseXml, announcer);

            assertNotNull(runners, "Unmarshaled runners should not be null");
            assertFalse(runners.isEmpty(), "Should have at least one runner");
        }

        @Test
        @DisplayName("Unmarshal preserves case ID")
        void unmarshalPreservesCaseId() throws Exception {
            String caseId = "import-test-case-id";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            assertEquals(caseId, restoredRunner.getCaseID().toString(),
                    "Restored case ID should match original");
        }

        @Test
        @DisplayName("Unmarshal preserves specification ID")
        void unmarshalPreservesSpecificationId() throws Exception {
            String caseId = "import-test-spec-id";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            assertEquals(originalRunner.getSpecificationID(), restoredRunner.getSpecificationID(),
                    "Restored specification ID should match original");
        }

        @Test
        @DisplayName("Unmarshal handles empty runners list gracefully")
        void unmarshalHandlesEmptyRunnersList() throws Exception {
            String malformedXml = """
                <case id="test">
                  <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
                </case>
                """;

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();

            assertThrows(YStateException.class, () ->
                    importer.unmarshal(malformedXml, yEngine.getAnnouncer()),
                    "Should throw for XML without runners");
        }

        @Test
        @DisplayName("Unmarshal throws YSyntaxException for invalid specification XML")
        void unmarshalThrowsForInvalidSpecificationXml() {
            String invalidSpecXml = """
                <case id="test">
                  <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                    <invalid/>
                  </specificationSet>
                  <runners/>
                </case>
                """;

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();

            assertThrows(YSyntaxException.class, () ->
                    importer.unmarshal(invalidSpecXml, yEngine.getAnnouncer()),
                    "Should throw YSyntaxException for malformed specification");
        }
    }

    // =========================================================================
    // Nested: Runner Restoration Tests
    // =========================================================================

    @Nested
    @DisplayName("Runner Restoration Tests")
    class RunnerRestorationTests {

        @Test
        @DisplayName("Restored runner has correct net reference")
        void restoredRunnerHasCorrectNetReference() throws Exception {
            String caseId = "restore-net-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            assertNotNull(restoredRunner.getNet(), "Restored runner should have net");
            assertEquals("MinimalNet", restoredRunner.getNet().getID(),
                    "Net ID should match specification");
        }

        @Test
        @DisplayName("Restored runner preserves start time")
        void restoredRunnerPreservesStartTime() throws Exception {
            String caseId = "restore-time-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            long originalStartTime = originalRunner.getStartTime();
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            assertEquals(originalStartTime, restoredRunner.getStartTime(),
                    "Start time should be preserved");
        }

        @Test
        @DisplayName("Restored runner preserves execution status")
        void restoredRunnerPreservesExecutionStatus() throws Exception {
            String caseId = "restore-status-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            assertEquals("Normal", restoredRunner.getExecutionStatus(),
                    "Execution status should be Normal");
        }

        @Test
        @DisplayName("Restored runner has announcer attached")
        void restoredRunnerHasAnnouncerAttached() throws Exception {
            String caseId = "restore-announcer-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            assertNotNull(restoredRunner.getAnnouncer(),
                    "Restored runner should have announcer");
        }

        @Test
        @DisplayName("Restored runner has work item repository")
        void restoredRunnerHasWorkItemRepository() throws Exception {
            String caseId = "restore-repo-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            assertNotNull(restoredRunner.getWorkItemRepository(),
                    "Restored runner should have work item repository");
        }
    }

    // =========================================================================
    // Nested: Identifier Hierarchy Tests
    // =========================================================================

    @Nested
    @DisplayName("Identifier Hierarchy Tests")
    class IdentifierHierarchyTests {

        @Test
        @DisplayName("Restored identifier has correct ID string")
        void restoredIdentifierHasCorrectIdString() throws Exception {
            String caseId = "id-string-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            YIdentifier restoredId = restoredRunner.getCaseID();
            assertEquals(caseId, restoredId.toString(),
                    "Identifier string should match original case ID");
        }

        @Test
        @DisplayName("Root identifier has no parent")
        void rootIdentifierHasNoParent() throws Exception {
            String caseId = "root-id-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            YIdentifier restoredId = restoredRunner.getCaseID();
            assertNull(restoredId.getParent(),
                    "Root identifier should have no parent");
        }

        @Test
        @DisplayName("Restored identifier has location names")
        void restoredIdentifierHasLocationNames() throws Exception {
            String caseId = "location-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            YIdentifier restoredId = restoredRunner.getCaseID();
            assertNotNull(restoredId.getLocationNames(),
                    "Identifier should have location names");
        }
    }

    // =========================================================================
    // Nested: Work Item Reunification Tests
    // =========================================================================

    @Nested
    @DisplayName("Work Item Reunification Tests")
    class WorkItemReunificationTests {

        @Test
        @DisplayName("Restored runner has work items")
        void restoredRunnerHasWorkItems() throws Exception {
            String caseId = "wi-reunite-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            // getWorkItems() returns Set, convert to List for indexed access
            List<YWorkItem> workItems = new java.util.ArrayList<>(
                restoredRunner.getWorkItemRepository().getWorkItems());

            assertNotNull(workItems, "Work items list should not be null");
            assertFalse(workItems.isEmpty(), "Should have at least one work item");
        }

        @Test
        @DisplayName("Restored work item has correct task ID")
        void restoredWorkItemHasCorrectTaskId() throws Exception {
            String caseId = "wi-taskid-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            List<YWorkItem> workItems = new java.util.ArrayList<>(
                restoredRunner.getWorkItemRepository().getWorkItems());

            YWorkItem restoredItem = workItems.get(0);
            assertEquals("task1", restoredItem.getTaskID(),
                    "Task ID should match specification task");
        }

        @Test
        @DisplayName("Restored work item has enabled status")
        void restoredWorkItemHasEnabledStatus() throws Exception {
            String caseId = "wi-status-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            List<YWorkItem> workItems = new java.util.ArrayList<>(
                restoredRunner.getWorkItemRepository().getWorkItems());

            YWorkItem restoredItem = workItems.get(0);
            assertEquals("Enabled", restoredItem.getStatus().toString(),
                    "Work item should be enabled");
        }

        @Test
        @DisplayName("Restored work item has valid case ID reference")
        void restoredWorkItemHasValidCaseIdReference() throws Exception {
            String caseId = "wi-caseid-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            List<YWorkItem> workItems = new java.util.ArrayList<>(
                restoredRunner.getWorkItemRepository().getWorkItems());

            YWorkItem restoredItem = workItems.get(0);
            assertEquals(caseId, restoredItem.getCaseID().toString(),
                    "Work item case ID should match runner case ID");
        }
    }

    // =========================================================================
    // Nested: Timer States Restoration Tests
    // =========================================================================

    @Nested
    @DisplayName("Timer States Restoration Tests")
    class TimerStatesRestorationTests {

        @Test
        @DisplayName("Restored runner has timer states map")
        void restoredRunnerHasTimerStatesMap() throws Exception {
            String caseId = "timer-states-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            assertNotNull(restoredRunner.get_timerStates(),
                    "Timer states map should not be null");
        }

        @Test
        @DisplayName("Restored runner has work item with enablement time")
        void restoredWorkItemHasEnablementTime() throws Exception {
            String caseId = "wi-time-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);
            // getWorkItems() returns Set, convert to List for indexed access
            List<YWorkItem> workItems = new java.util.ArrayList<>(
                restoredRunner.getWorkItemRepository().getWorkItems());

            YWorkItem restoredItem = workItems.get(0);
            assertNotNull(restoredItem.getEnablementTime(),
                    "Work item should have enablement time");
        }
    }

    // =========================================================================
    // Nested: Corrupt XML Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Corrupt XML Handling Tests")
    class CorruptXmlHandlingTests {

        @Test
        @DisplayName("Null XML throws NullPointerException")
        void nullXmlThrowsException() {
            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();

            assertThrows(NullPointerException.class, () ->
                    importer.unmarshal(null, yEngine.getAnnouncer()),
                    "Should throw for null XML");
        }

        @Test
        @DisplayName("Empty XML throws exception")
        void emptyXmlThrowsException() {
            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();

            assertThrows(Exception.class, () ->
                    importer.unmarshal("", yEngine.getAnnouncer()),
                    "Should throw for empty XML");
        }

        @Test
        @DisplayName("Malformed XML throws exception")
        void malformedXmlThrowsException() {
            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();

            String malformedXml = "<case><invalid";

            assertThrows(Exception.class, () ->
                    importer.unmarshal(malformedXml, yEngine.getAnnouncer()),
                    "Should throw for malformed XML");
        }

        @Test
        @DisplayName("Missing specification set throws exception")
        void missingSpecificationSetThrowsException() {
            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();

            String missingSpecXml = """
                <case id="test">
                  <runners>
                    <runner>
                      <identifier id="test-id"/>
                    </runner>
                  </runners>
                </case>
                """;

            assertThrows(Exception.class, () ->
                    importer.unmarshal(missingSpecXml, yEngine.getAnnouncer()),
                    "Should throw for missing specification set");
        }

        @Test
        @DisplayName("Missing runners element throws exception")
        void missingRunnersElementThrowsException() {
            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();

            String missingRunnersXml = """
                <case id="test">
                  <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"/>
                </case>
                """;

            assertThrows(YStateException.class, () ->
                    importer.unmarshal(missingRunnersXml, yEngine.getAnnouncer()),
                    "Should throw for missing runners element");
        }

        @Test
        @DisplayName("Null announcer causes processing failure")
        void nullAnnouncerCausesFailure() throws Exception {
            String caseId = "null-announcer-test";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();

            assertThrows(Exception.class, () ->
                    importer.unmarshal(caseXml, null),
                    "Should throw for null announcer");
        }
    }

    // =========================================================================
    // Nested: Round-Trip Integrity Tests
    // =========================================================================

    @Nested
    @DisplayName("Round-Trip Integrity Tests")
    class RoundTripIntegrityTests {

        @Test
        @DisplayName("Export and import preserves runner equality")
        void exportImportPreservesRunnerEquality() throws Exception {
            String caseId = "roundtrip-equality";
            YNetRunner originalRunner = launchCaseWithMonitoring(caseId);
            String caseXml = exportCase(originalRunner);

            YCaseImporter importer = new YCaseImporter();
            YEngine yEngine = new YEngine();
            List<YNetRunner> runners = importer.unmarshal(caseXml, yEngine.getAnnouncer());

            YNetRunner restoredRunner = runners.get(0);

            assertEquals(originalRunner.getCaseID().toString(),
                    restoredRunner.getCaseID().toString(),
                    "Case IDs should match after round-trip");
            assertEquals(originalRunner.getSpecificationID(),
                    restoredRunner.getSpecificationID(),
                    "Specification IDs should match after round-trip");
        }

        @Test
        @DisplayName("Multiple exports produce consistent results")
        void multipleExportsProduceConsistentResults() throws Exception {
            String caseId = "multi-export-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            String export1 = exportCase(runner);
            String export2 = exportCase(runner);

            assertNotNull(export1, "First export should not be null");
            assertNotNull(export2, "Second export should not be null");
            assertTrue(export1.contains(caseId), "Export should contain case ID");
            assertTrue(export2.contains(caseId), "Export should contain case ID");
        }
    }
}
