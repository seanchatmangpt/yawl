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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Comprehensive tests for YCaseExporter covering marshal operations,
 * XML structure validation, and timestamp handling.
 *
 * <p>Chicago TDD: All tests use real YStatelessEngine instances,
 * real YSpecification objects, and real export operations. No mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YCaseExporter Tests")
@Tag("unit")
class TestYCaseExporter {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final long EVENT_TIMEOUT_SEC = 10L;
    private static final Namespace YAWL_NAMESPACE = Namespace.getNamespace("yawl",
            "http://www.yawlfoundation.org/yawlschema");

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
        String xml = StringUtil.streamToString(is);
        return engine.unmarshalSpecification(xml);
    }

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

    // =========================================================================
    // Nested: Marshal Runner/Work Items Tests
    // =========================================================================

    @Nested
    @DisplayName("Marshal Runner/Work Items Tests")
    class MarshalRunnerWorkItemsTests {

        @Test
        @DisplayName("Marshal runner returns non-null XML string")
        void marshalRunnerReturnsNonNullXmlString() throws Exception {
            String caseId = "marshal-basic-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertNotNull(xml, "Marshal result should not be null");
            assertFalse(xml.isEmpty(), "Marshal result should not be empty");
        }

        @Test
        @DisplayName("Marshal output contains case element")
        void marshalOutputContainsCaseElement() throws Exception {
            String caseId = "marshal-case-element";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("<case"), "Should contain case element");
            assertTrue(xml.contains("id=\"" + caseId + "\""),
                    "Case element should have correct id attribute");
        }

        @Test
        @DisplayName("Marshal output contains runners element")
        void marshalOutputContainsRunnersElement() throws Exception {
            String caseId = "marshal-runners-element";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("<runners>"), "Should contain runners element");
            assertTrue(xml.contains("</runners>"), "Should have closing runners tag");
        }

        @Test
        @DisplayName("Marshal output contains specification set")
        void marshalOutputContainsSpecificationSet() throws Exception {
            String caseId = "marshal-spec-set";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("specificationSet"),
                    "Should contain specificationSet element");
        }

        @Test
        @DisplayName("Marshal output contains runner identifier")
        void marshalOutputContainsRunnerIdentifier() throws Exception {
            String caseId = "marshal-identifier";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("<identifier"), "Should contain identifier element");
        }

        @Test
        @DisplayName("Marshal output contains work items")
        void marshalOutputContainsWorkItems() throws Exception {
            String caseId = "marshal-work-items";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("<workitems>"), "Should contain workitems element");
        }

        @Test
        @DisplayName("Marshal work item includes task ID")
        void marshalWorkItemIncludesTaskId() throws Exception {
            String caseId = "marshal-task-id";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("task1"), "Should contain task1 reference");
        }

        @Test
        @DisplayName("Marshal includes net data")
        void marshalIncludesNetData() throws Exception {
            String caseId = "marshal-net-data";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("<netdata>"), "Should contain netdata element");
        }

        @Test
        @DisplayName("Marshal includes timer states")
        void marshalIncludesTimerStates() throws Exception {
            String caseId = "marshal-timer-states";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("<timerstates>"), "Should contain timerstates element");
        }
    }

    // =========================================================================
    // Nested: XML Structure Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("XML Structure Validation Tests")
    class XmlStructureValidationTests {

        @Test
        @DisplayName("Marshal output is valid XML")
        void marshalOutputIsValidXml() throws Exception {
            String caseId = "valid-xml-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertDoesNotThrow(() -> {
                Document doc = JDOMUtil.stringToDocument(xml);
                assertNotNull(doc, "Document should parse successfully");
                assertNotNull(doc.getRootElement(), "Should have root element");
            }, "Marshal output should be valid XML");
        }

        @Test
        @DisplayName("Marshal output has case root element")
        void marshalOutputHasCaseRootElement() throws Exception {
            String caseId = "root-element-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            Document doc = JDOMUtil.stringToDocument(xml);
            Element root = doc.getRootElement();

            assertEquals("case", root.getName(), "Root element should be 'case'");
        }

        @Test
        @DisplayName("Case element has id attribute")
        void caseElementHasIdAttribute() throws Exception {
            String caseId = "case-id-attr-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            Document doc = JDOMUtil.stringToDocument(xml);
            Element root = doc.getRootElement();

            assertEquals(caseId, root.getAttributeValue("id"),
                    "Case id attribute should match");
        }

        @Test
        @DisplayName("Runners element contains runner children")
        void runnersElementContainsRunnerChildren() throws Exception {
            String caseId = "runner-children-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            Document doc = JDOMUtil.stringToDocument(xml);
            Element runnersElement = doc.getRootElement().getChild("runners");

            assertNotNull(runnersElement, "Should have runners element");
            assertFalse(runnersElement.getChildren().isEmpty(),
                    "Runners should have child elements");
        }

        @Test
        @DisplayName("Runner element has all required children")
        void runnerElementHasAllRequiredChildren() throws Exception {
            String caseId = "runner-children-required";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            Document doc = JDOMUtil.stringToDocument(xml);
            Element runnersElement = doc.getRootElement().getChild("runners");
            Element runnerElement = runnersElement.getChild("runner");

            assertNotNull(runnerElement.getChild("identifier"),
                    "Should have identifier child");
            assertNotNull(runnerElement.getChild("netdata"),
                    "Should have netdata child");
            assertNotNull(runnerElement.getChild("starttime"),
                    "Should have starttime child");
        }

        @Test
        @DisplayName("Identifier element has id attribute")
        void identifierElementHasIdAttribute() throws Exception {
            String caseId = "identifier-id-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            Document doc = JDOMUtil.stringToDocument(xml);
            Element identifier = doc.getRootElement()
                    .getChild("runners")
                    .getChild("runner")
                    .getChild("identifier");

            assertNotNull(identifier.getAttributeValue("id"),
                    "Identifier should have id attribute");
        }

        @Test
        @DisplayName("Work item element contains all status fields")
        void workItemElementContainsAllStatusFields() throws Exception {
            String caseId = "workitem-status-fields";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("<id>"), "Should contain id element");
            assertTrue(xml.contains("<status>"), "Should contain status element");
            assertTrue(xml.contains("<enablement>"), "Should contain enablement element");
        }

        @Test
        @DisplayName("Exported XML can be parsed back")
        void exportedXmlCanBeParsedBack() throws Exception {
            String caseId = "parse-back-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            YCaseImporter importer = new YCaseImporter();

            assertDoesNotThrow(() -> {
                Document doc = JDOMUtil.stringToDocument(xml);
                assertNotNull(doc);
            }, "Exported XML should be parseable");
        }
    }

    // =========================================================================
    // Nested: Timestamp Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Timestamp Handling Tests")
    class TimestampHandlingTests {

        @Test
        @DisplayName("Marshal includes start time")
        void marshalIncludesStartTime() throws Exception {
            String caseId = "start-time-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("<starttime>"), "Should contain starttime element");

            Document doc = JDOMUtil.stringToDocument(xml);
            Element starttime = doc.getRootElement()
                    .getChild("runners")
                    .getChild("runner")
                    .getChild("starttime");

            assertNotNull(starttime, "Should have starttime element");
            long timeValue = Long.parseLong(starttime.getText());
            assertTrue(timeValue > 0, "Start time should be positive");
        }

        @Test
        @DisplayName("Marshal includes work item enablement time")
        void marshalIncludesWorkItemEnablementTime() throws Exception {
            String caseId = "enablement-time-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.contains("<enablement>"),
                    "Should contain enablement timestamp");
        }

        @Test
        @DisplayName("Start time matches runner getStartTime")
        void startTimeMatchesRunnerGetStartTime() throws Exception {
            String caseId = "start-time-match";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            long expectedStartTime = runner.getStartTime();

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            Document doc = JDOMUtil.stringToDocument(xml);
            Element starttime = doc.getRootElement()
                    .getChild("runners")
                    .getChild("runner")
                    .getChild("starttime");

            long exportedStartTime = Long.parseLong(starttime.getText());
            assertEquals(expectedStartTime, exportedStartTime,
                    "Exported start time should match runner's start time");
        }

        @Test
        @DisplayName("Enablement time is valid epoch millis")
        void enablementTimeIsValidEpochMillis() throws Exception {
            String caseId = "epoch-millis-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            Document doc = JDOMUtil.stringToDocument(xml);
            Element workitems = doc.getRootElement()
                    .getChild("runners")
                    .getChild("runner")
                    .getChild("workitems");

            if (workitems != null) {
                Element item = workitems.getChild("item");
                if (item != null) {
                    Element enablement = item.getChild("enablement");
                    if (enablement != null && !enablement.getText().equals("0")) {
                        long enablementTime = Long.parseLong(enablement.getText());
                        assertTrue(enablementTime > 0,
                                "Enablement time should be positive epoch millis");
                        assertTrue(enablementTime <= System.currentTimeMillis(),
                                "Enablement time should not be in the future");
                    }
                }
            }
        }

        @Test
        @DisplayName("Timestamps are numeric strings")
        void timestampsAreNumericStrings() throws Exception {
            String caseId = "numeric-timestamps";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            Document doc = JDOMUtil.stringToDocument(xml);
            Element starttime = doc.getRootElement()
                    .getChild("runners")
                    .getChild("runner")
                    .getChild("starttime");

            assertTrue(Pattern.matches("\\d+", starttime.getText()),
                    "Start time should be numeric string");
        }
    }

    // =========================================================================
    // Nested: Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Marshal with empty work items repository")
        void marshalWithEmptyWorkItemsRepository() throws Exception {
            String caseId = "empty-wi-repo";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            // Complete all work items to clear repository
            List<YWorkItem> items = runner.getWorkItemRepository().getEnabledWorkItems();
            for (YWorkItem item : items) {
                runner.getWorkItemRepository().removeWorkItemFamily(item);
            }

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertNotNull(xml, "Should still produce valid XML");
            assertTrue(xml.contains("<workitems>"),
                    "Should have workitems element even if empty");
        }

        @Test
        @DisplayName("Marshal produces consistent output format")
        void marshalProducesConsistentOutputFormat() throws Exception {
            String caseId = "consistent-format";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertTrue(xml.startsWith("<?xml") || xml.startsWith("<case"),
                    "Should start with XML declaration or case element");
        }
    }
}
