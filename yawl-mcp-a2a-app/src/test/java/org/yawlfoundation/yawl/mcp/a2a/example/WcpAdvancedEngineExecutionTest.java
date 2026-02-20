/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.example;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine execution tests for WCP-35 through WCP-39 advanced workflow patterns.
 *
 * <p>Tests verify that the YAWL stateless engine correctly executes patterns defined in
 * YAML, converted to YAWL XML via {@link ExtendedYamlConverter}. Each test:
 * <ol>
 *   <li>Loads the pattern YAML from the classpath resource directory.</li>
 *   <li>Converts the YAML to YAWL XML using {@link ExtendedYamlConverter}.</li>
 *   <li>Unmarshals the XML into a {@link YSpecification}.</li>
 *   <li>Launches a case via {@link YStatelessEngine} and drives work items to completion.</li>
 *   <li>Verifies execution trace, state consistency, and case termination.</li>
 * </ol>
 *
 * <p>Patterns under test:
 * <ul>
 *   <li>WCP-35: Dynamic Partial Join — dynamic threshold multi-instance fork/join.</li>
 *   <li>WCP-36: Discriminator with Complete MI — first-result-wins discriminator gate.</li>
 *   <li>WCP-37: Local Trigger — local event trigger with timeout alternative path.</li>
 *   <li>WCP-38: Global Trigger — global broadcast trigger with AND-join synchronisation.</li>
 *   <li>WCP-39: Reset Trigger — conditional reset/checkpoint loop with XOR routing.</li>
 * </ul>
 *
 * <p>Chicago TDD: all tests use real engine instances, real specifications, and real
 * work item event dispatch. No mocks or stubs.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("integration")
@DisplayName("WCP-35 through WCP-39 Advanced Engine Execution Tests")
class WcpAdvancedEngineExecutionTest {

    /** Maximum seconds to wait for a case to reach completion in the event listener. */
    private static final long CASE_COMPLETION_TIMEOUT_SECS = 15L;

    /** Classpath base for pattern YAML resources relative to the test class. */
    private static final String PATTERN_RESOURCE_BASE = "/patterns/";

    private YStatelessEngine engine;
    private ExtendedYamlConverter converter;

    @BeforeEach
    void setUp() {
        engine = new YStatelessEngine();
        converter = new ExtendedYamlConverter();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.setCaseMonitoringEnabled(false);
        }
    }

    // =========================================================================
    // Shared test infrastructure
    // =========================================================================

    /**
     * Load a pattern YAML file from the classpath using an absolute resource path.
     * The file is read from the yawl-mcp-a2a-app resources directory.
     *
     * @param relativePath path relative to PATTERN_RESOURCE_BASE, e.g.
     *                     "statebased/wcp-35-dynamic-partial-join.yaml"
     * @return YAML content as a string
     */
    private String loadPatternYaml(String relativePath) throws Exception {
        String fullPath = PATTERN_RESOURCE_BASE + relativePath;
        InputStream is = getClass().getResourceAsStream(fullPath);
        assertNotNull(is, "Pattern YAML not found on classpath: " + fullPath);
        byte[] bytes = is.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Convert YAML to a YAWL XML specification, unmarshal it, and return the spec.
     *
     * @param yaml raw YAML text
     * @return an unmarshalled {@link YSpecification}
     */
    private YSpecification yamlToSpec(String yaml) throws YSyntaxException {
        String xml = converter.convertToXml(yaml);
        assertNotNull(xml, "Converter produced null XML");
        assertFalse(xml.isBlank(), "Converter produced blank XML");
        return engine.unmarshalSpecification(xml);
    }

    /**
     * Drive a case to completion by starting and completing each enabled work item
     * as it is announced via the {@link YWorkItemEventListener}.
     *
     * <p>Returns an execution trace of task IDs in the order items were completed,
     * and awaits the {@code CASE_COMPLETED} event up to {@link #CASE_COMPLETION_TIMEOUT_SECS}.</p>
     *
     * @param spec     the specification to launch
     * @param caseId   the case identifier
     * @return execution result containing the trace and completion flag
     */
    private ExecutionResult driveToCompletion(YSpecification spec, String caseId)
            throws YStateException, YDataStateException, YEngineStateException,
            YQueryException, InterruptedException {

        List<String> trace = new CopyOnWriteArrayList<>();
        AtomicBoolean caseCompleted = new AtomicBoolean(false);
        AtomicInteger workItemsStarted = new AtomicBoolean(false) != null ?
                new AtomicInteger(0) : new AtomicInteger(0);
        CountDownLatch completionLatch = new CountDownLatch(1);

        YCaseEventListener caseListener = event -> {
            if (event.getEventType() == YEventType.CASE_COMPLETED) {
                caseCompleted.set(true);
                completionLatch.countDown();
            }
        };

        YWorkItemEventListener workItemListener = event -> {
            if (event.getEventType() == YEventType.ITEM_ENABLED) {
                YWorkItem item = event.getWorkItem();
                try {
                    engine.startWorkItem(item);
                } catch (YStateException | YDataStateException | YQueryException |
                         YEngineStateException e) {
                    // item may have been auto-started or is already executing; proceed
                }
            } else if (event.getEventType() == YEventType.ITEM_STARTED) {
                YWorkItem item = event.getWorkItem();
                trace.add(item.getTaskID());
                workItemsStarted.incrementAndGet();
                try {
                    if (!item.hasCompletedStatus()) {
                        engine.completeWorkItem(item, "<data/>", null);
                    }
                } catch (YStateException | YDataStateException | YQueryException |
                         YEngineStateException e) {
                    // item may already be complete due to concurrent processing; safe to ignore
                }
            }
        };

        engine.addCaseEventListener(caseListener);
        engine.addWorkItemEventListener(workItemListener);

        try {
            engine.launchCase(spec, caseId);
            boolean completed = completionLatch.await(CASE_COMPLETION_TIMEOUT_SECS, TimeUnit.SECONDS);
            return new ExecutionResult(
                    Collections.unmodifiableList(new ArrayList<>(trace)),
                    caseCompleted.get(),
                    completed,
                    workItemsStarted.get());
        } finally {
            engine.removeCaseEventListener(caseListener);
            engine.removeWorkItemEventListener(workItemListener);
        }
    }

    /**
     * Immutable record of a case execution result.
     */
    record ExecutionResult(
            List<String> trace,
            boolean caseCompletedEventFired,
            boolean completionLatchReleasedWithinTimeout,
            int totalWorkItemsStarted) {

        boolean caseTerminatedCleanly() {
            return caseCompletedEventFired && completionLatchReleasedWithinTimeout;
        }
    }

    // =========================================================================
    // WCP-35: Dynamic Partial Join
    // =========================================================================

    @Nested
    @DisplayName("WCP-35: Dynamic Partial Join")
    class Wcp35DynamicPartialJoinTests {

        @Test
        @DisplayName("YAML converts to schema-compliant YAWL XML")
        void yamlConvertsToSchemaCompliantXml() throws Exception {
            String yaml = loadPatternYaml("statebased/wcp-35-dynamic-partial-join.yaml");

            String xml = converter.convertToXml(yaml);

            assertNotNull(xml, "WCP-35 XML must not be null");
            assertTrue(xml.contains("<specificationSet"), "Must have specificationSet root");
            assertTrue(xml.contains("version=\"4.0\""), "Must be YAWL schema 4.0");
            assertTrue(xml.contains("DynamicPartialJoinPattern"),
                    "Must contain the spec name from YAML");
            assertTrue(xml.contains("<inputCondition"), "Must have input condition");
            assertTrue(xml.contains("<outputCondition"), "Must have output condition");
        }

        @Test
        @DisplayName("YAML unmarshals to a valid YSpecification with root net")
        void yamlUnmarshalsToValidSpec() throws Exception {
            String yaml = loadPatternYaml("statebased/wcp-35-dynamic-partial-join.yaml");
            YSpecification spec = yamlToSpec(yaml);

            assertNotNull(spec, "WCP-35 specification must not be null");
            assertNotNull(spec.getRootNet(), "Root net must be present");
            assertFalse(spec.getDecompositions().isEmpty(),
                    "Spec must have at least one decomposition");
        }

        @Test
        @DisplayName("XML contains multi-instance task configuration")
        void xmlContainsMultiInstanceConfiguration() throws Exception {
            String yaml = loadPatternYaml("statebased/wcp-35-dynamic-partial-join.yaml");

            String xml = converter.convertToXml(yaml);

            // ProcessBranches has multiInstance configuration in the YAML
            assertTrue(xml.contains("MultipleInstanceExternalTaskFactsType"),
                    "Must emit MI xsi:type for dynamic ProcessBranches task");
            assertTrue(xml.contains("<minimum>"), "Must have minimum element for MI task");
            assertTrue(xml.contains("<maximum>"), "Must have maximum element for MI task");
            assertTrue(xml.contains("<threshold>"), "Must have threshold element for MI task");
            assertTrue(xml.contains("<miDataInput>"),
                    "Must have required miDataInput element for MI task");
        }

        @Test
        @DisplayName("XML contains variable declarations for branch tracking")
        void xmlContainsVariableDeclarations() throws Exception {
            String yaml = loadPatternYaml("statebased/wcp-35-dynamic-partial-join.yaml");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("branchCount"), "Must declare branchCount variable");
            assertTrue(xml.contains("thresholdPercentage"),
                    "Must declare thresholdPercentage variable");
            assertTrue(xml.contains("dynamicThreshold"),
                    "Must declare dynamicThreshold variable");
            assertTrue(xml.contains("<inputParam>"),
                    "Variables must be declared as inputParam elements");
        }

        @Test
        @DisplayName("Engine executes WCP-35 case to completion")
        void engineExecutesCaseToCorrectionCompletion() throws Exception {
            String yaml = loadPatternYaml("statebased/wcp-35-dynamic-partial-join.yaml");
            YSpecification spec = yamlToSpec(yaml);
            long startNanos = System.nanoTime();

            ExecutionResult result = driveToCompletion(spec, "wcp35-exec-01");

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            assertTrue(result.caseTerminatedCleanly(),
                    "WCP-35 case must complete within " + CASE_COMPLETION_TIMEOUT_SECS + "s");
            assertFalse(result.trace().isEmpty(),
                    "Execution trace must contain at least one completed task");
            assertTrue(result.totalWorkItemsStarted() > 0,
                    "At least one work item must have been started");
            // Performance: engine overhead must not exceed 10 seconds for a simple WCP-35 case
            assertTrue(elapsedMs < 10_000L,
                    "WCP-35 execution must complete within 10s; took " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("Execution trace contains expected task sequence")
        void executionTraceContainsExpectedTasks() throws Exception {
            String yaml = loadPatternYaml("statebased/wcp-35-dynamic-partial-join.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp35-trace-01");

            assertTrue(result.caseTerminatedCleanly(),
                    "WCP-35 case must complete before trace inspection");
            // StartTask must appear first in the trace (the entry point of WCP-35)
            assertFalse(result.trace().isEmpty(), "Trace must not be empty");
            assertEquals("StartTask", result.trace().get(0),
                    "First completed task must be StartTask");
        }
    }

    // =========================================================================
    // WCP-36: Discriminator with Complete MI
    // =========================================================================

    @Nested
    @DisplayName("WCP-36: Discriminator with Complete MI")
    class Wcp36DiscriminatorCompleteMiTests {

        @Test
        @DisplayName("YAML converts to schema-compliant YAWL XML")
        void yamlConvertsToSchemaCompliantXml() throws Exception {
            String yaml = loadPatternYaml("controlflow/wcp-36-discriminator-complete.yaml");

            String xml = converter.convertToXml(yaml);

            assertNotNull(xml, "WCP-36 XML must not be null");
            assertTrue(xml.contains("<specificationSet"), "Must have specificationSet root");
            assertTrue(xml.contains("DiscriminatorCompleteMIPattern"),
                    "Must contain spec name from YAML");
            assertTrue(xml.contains("discriminator"),
                    "Must contain discriminator join code for Discriminator task");
        }

        @Test
        @DisplayName("YAML unmarshals to valid YSpecification")
        void yamlUnmarshalsToValidSpec() throws Exception {
            String yaml = loadPatternYaml("controlflow/wcp-36-discriminator-complete.yaml");
            YSpecification spec = yamlToSpec(yaml);

            assertNotNull(spec, "WCP-36 specification must not be null");
            assertNotNull(spec.getRootNet(), "Root net must be present");
        }

        @Test
        @DisplayName("XML contains discriminator join code on Discriminator task")
        void xmlContainsDiscriminatorJoinCode() throws Exception {
            String yaml = loadPatternYaml("controlflow/wcp-36-discriminator-complete.yaml");

            String xml = converter.convertToXml(yaml);

            // The Discriminator task has join: discriminator in the YAML
            assertTrue(xml.contains("code=\"discriminator\""),
                    "Discriminator task must emit join code='discriminator'");
        }

        @Test
        @DisplayName("XML contains multi-instance task for ProcessItems")
        void xmlContainsMultiInstanceForProcessItems() throws Exception {
            String yaml = loadPatternYaml("controlflow/wcp-36-discriminator-complete.yaml");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("MultipleInstanceExternalTaskFactsType"),
                    "ProcessItems must be emitted as MI task");
            // WCP-36 YAML has min: 3, max: 5
            assertTrue(xml.contains("<minimum>3</minimum>"),
                    "ProcessItems must have minimum 3 instances");
            assertTrue(xml.contains("<maximum>5</maximum>"),
                    "ProcessItems must have maximum 5 instances");
        }

        @Test
        @DisplayName("XML contains conditional flows from Discriminator")
        void xmlContainsConditionalFlowsFromDiscriminator() throws Exception {
            String yaml = loadPatternYaml("controlflow/wcp-36-discriminator-complete.yaml");

            String xml = converter.convertToXml(yaml);

            // Discriminator flows to CompleteRemaining and Continue with condition
            assertTrue(xml.contains("nextElementRef id=\"CompleteRemaining\""),
                    "Must reference CompleteRemaining as flow target");
            assertTrue(xml.contains("nextElementRef id=\"Continue\""),
                    "Must reference Continue as flow target");
            assertTrue(xml.contains("<isDefaultFlow/>"),
                    "Must have isDefaultFlow for the default Continue path");
        }

        @Test
        @DisplayName("XML contains variable declarations for result tracking")
        void xmlContainsVariableDeclarations() throws Exception {
            String yaml = loadPatternYaml("controlflow/wcp-36-discriminator-complete.yaml");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("itemCount"), "Must declare itemCount variable");
            assertTrue(xml.contains("firstResult"), "Must declare firstResult variable");
            assertTrue(xml.contains("completedEarly"), "Must declare completedEarly variable");
        }

        @Test
        @DisplayName("Engine executes WCP-36 case to completion")
        void engineExecutesCaseToCompletion() throws Exception {
            String yaml = loadPatternYaml("controlflow/wcp-36-discriminator-complete.yaml");
            YSpecification spec = yamlToSpec(yaml);
            long startNanos = System.nanoTime();

            ExecutionResult result = driveToCompletion(spec, "wcp36-exec-01");

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            assertTrue(result.caseTerminatedCleanly(),
                    "WCP-36 case must complete within " + CASE_COMPLETION_TIMEOUT_SECS + "s");
            assertTrue(result.totalWorkItemsStarted() > 0,
                    "At least one work item must have been started in WCP-36");
            assertTrue(elapsedMs < 10_000L,
                    "WCP-36 execution must complete within 10s; took " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("Execution trace begins with StartTask")
        void executionTraceStartsWithStartTask() throws Exception {
            String yaml = loadPatternYaml("controlflow/wcp-36-discriminator-complete.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp36-trace-01");

            assertTrue(result.caseTerminatedCleanly(), "WCP-36 case must complete first");
            assertFalse(result.trace().isEmpty(), "Trace must not be empty");
            assertEquals("StartTask", result.trace().get(0),
                    "First completed task must be StartTask");
        }
    }

    // =========================================================================
    // WCP-37: Local Trigger
    // =========================================================================

    @Nested
    @DisplayName("WCP-37: Local Trigger")
    class Wcp37LocalTriggerTests {

        @Test
        @DisplayName("YAML converts to schema-compliant YAWL XML")
        void yamlConvertsToSchemaCompliantXml() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-37-local-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            assertNotNull(xml, "WCP-37 XML must not be null");
            assertTrue(xml.contains("<specificationSet"), "Must have specificationSet root");
            assertTrue(xml.contains("LocalTriggerPattern"),
                    "Must contain spec name from YAML");
        }

        @Test
        @DisplayName("YAML unmarshals to valid YSpecification")
        void yamlUnmarshalsToValidSpec() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-37-local-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            assertNotNull(spec, "WCP-37 specification must not be null");
            assertNotNull(spec.getRootNet(), "Root net must be present");
        }

        @Test
        @DisplayName("XML contains WaitForTrigger task with XOR split for dual paths")
        void xmlContainsWaitForTriggerWithDualPaths() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-37-local-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("id=\"WaitForTrigger\""),
                    "Must have WaitForTrigger task");
            // WaitForTrigger has both ProcessTrigger and HandleTimeout as flows
            assertTrue(xml.contains("nextElementRef id=\"ProcessTrigger\""),
                    "Must reference ProcessTrigger as a flow target");
            assertTrue(xml.contains("nextElementRef id=\"HandleTimeout\""),
                    "Must reference HandleTimeout as alternative flow target");
        }

        @Test
        @DisplayName("XML contains variable declarations for trigger state")
        void xmlContainsVariableDeclarationsForTriggerState() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-37-local-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("triggerReceived"), "Must declare triggerReceived variable");
            assertTrue(xml.contains("triggerData"), "Must declare triggerData variable");
            assertTrue(xml.contains("timeout"), "Must declare timeout variable");
        }

        @Test
        @DisplayName("XML contains all six tasks from WCP-37 YAML")
        void xmlContainsAllSixTasks() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-37-local-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            // WCP-37 defines: StartTask, WaitForTrigger, ProcessTrigger,
            //                  HandleTimeout, HandleNoTrigger, Complete
            assertTrue(xml.contains("id=\"StartTask\""), "Must have StartTask");
            assertTrue(xml.contains("id=\"WaitForTrigger\""), "Must have WaitForTrigger");
            assertTrue(xml.contains("id=\"ProcessTrigger\""), "Must have ProcessTrigger");
            assertTrue(xml.contains("id=\"HandleTimeout\""), "Must have HandleTimeout");
            assertTrue(xml.contains("id=\"HandleNoTrigger\""), "Must have HandleNoTrigger");
            assertTrue(xml.contains("id=\"Complete\""), "Must have Complete");
        }

        @Test
        @DisplayName("Engine executes WCP-37 case to completion")
        void engineExecutesCaseToCompletion() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-37-local-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);
            long startNanos = System.nanoTime();

            ExecutionResult result = driveToCompletion(spec, "wcp37-exec-01");

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            assertTrue(result.caseTerminatedCleanly(),
                    "WCP-37 case must complete within " + CASE_COMPLETION_TIMEOUT_SECS + "s");
            assertTrue(result.totalWorkItemsStarted() > 0,
                    "At least one work item must have been started in WCP-37");
            assertTrue(elapsedMs < 10_000L,
                    "WCP-37 execution must complete within 10s; took " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("Execution trace begins with StartTask and includes at least 3 tasks")
        void executionTraceHasExpectedMinimumLength() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-37-local-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp37-trace-01");

            assertTrue(result.caseTerminatedCleanly(), "WCP-37 case must complete first");
            assertFalse(result.trace().isEmpty(), "Trace must not be empty");
            assertEquals("StartTask", result.trace().get(0),
                    "First completed task must be StartTask");
            // WCP-37 has minimum 3 tasks in any valid path: Start, WaitForTrigger,
            // and at least one of {ProcessTrigger, HandleTimeout/HandleNoTrigger}, then Complete
            assertTrue(result.trace().size() >= 3,
                    "WCP-37 trace must have at least 3 tasks; got: " + result.trace());
        }

        @Test
        @DisplayName("Execution trace ends with Complete task")
        void executionTraceEndsWithComplete() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-37-local-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp37-complete-01");

            assertTrue(result.caseTerminatedCleanly(), "WCP-37 case must complete first");
            assertFalse(result.trace().isEmpty(), "Trace must not be empty");
            String lastTask = result.trace().get(result.trace().size() - 1);
            assertEquals("Complete", lastTask,
                    "Last completed task must be Complete; trace=" + result.trace());
        }
    }

    // =========================================================================
    // WCP-38: Global Trigger
    // =========================================================================

    @Nested
    @DisplayName("WCP-38: Global Trigger")
    class Wcp38GlobalTriggerTests {

        @Test
        @DisplayName("YAML converts to schema-compliant YAWL XML")
        void yamlConvertsToSchemaCompliantXml() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            assertNotNull(xml, "WCP-38 XML must not be null");
            assertTrue(xml.contains("<specificationSet"), "Must have specificationSet root");
            assertTrue(xml.contains("GlobalTriggerPattern"),
                    "Must contain spec name from YAML");
        }

        @Test
        @DisplayName("YAML unmarshals to valid YSpecification")
        void yamlUnmarshalsToValidSpec() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            assertNotNull(spec, "WCP-38 specification must not be null");
            assertNotNull(spec.getRootNet(), "Root net must be present");
        }

        @Test
        @DisplayName("XML contains AND-split on StartTask for parallel branches")
        void xmlContainsAndSplitOnStartTask() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            // StartTask has split: and in WCP-38 YAML, producing SubscribeToEvent and ContinueWork
            assertTrue(xml.contains("id=\"StartTask\""), "Must have StartTask");
            assertTrue(xml.contains("code=\"and\""),
                    "StartTask AND-split must emit code='and'");
            assertTrue(xml.contains("nextElementRef id=\"SubscribeToEvent\""),
                    "StartTask must reference SubscribeToEvent branch");
            assertTrue(xml.contains("nextElementRef id=\"ContinueWork\""),
                    "StartTask must reference ContinueWork branch");
        }

        @Test
        @DisplayName("XML contains AND-join on WaitForGlobalEvent")
        void xmlContainsAndJoinOnWaitForGlobalEvent() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            // WaitForGlobalEvent has join: and in the YAML
            assertTrue(xml.contains("id=\"WaitForGlobalEvent\""),
                    "Must have WaitForGlobalEvent task");
            // The AND join code appears on this task - but we can verify 'and' code is present
            // as the only and-join task in the workflow
            int andCodeCount = countOccurrences(xml, "code=\"and\"");
            assertTrue(andCodeCount >= 2,
                    "Must have at least 2 occurrences of code='and' " +
                    "(one for AND-split on StartTask, one for AND-join on WaitForGlobalEvent)");
        }

        @Test
        @DisplayName("XML contains all seven tasks from WCP-38 YAML")
        void xmlContainsAllSevenTasks() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("id=\"StartTask\""), "Must have StartTask");
            assertTrue(xml.contains("id=\"SubscribeToEvent\""), "Must have SubscribeToEvent");
            assertTrue(xml.contains("id=\"ContinueWork\""), "Must have ContinueWork");
            assertTrue(xml.contains("id=\"WaitForGlobalEvent\""), "Must have WaitForGlobalEvent");
            assertTrue(xml.contains("id=\"ProcessGlobalEvent\""), "Must have ProcessGlobalEvent");
            assertTrue(xml.contains("id=\"HandleTimeout\""), "Must have HandleTimeout");
            assertTrue(xml.contains("id=\"Complete\""), "Must have Complete");
        }

        @Test
        @DisplayName("XML contains variable declarations for global event state")
        void xmlContainsVariableDeclarations() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("globalEventReceived"),
                    "Must declare globalEventReceived variable");
            assertTrue(xml.contains("eventData"), "Must declare eventData variable");
            assertTrue(xml.contains("subscriptionId"), "Must declare subscriptionId variable");
        }

        @Test
        @DisplayName("Engine executes WCP-38 case to completion")
        void engineExecutesCaseToCompletion() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);
            long startNanos = System.nanoTime();

            ExecutionResult result = driveToCompletion(spec, "wcp38-exec-01");

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            assertTrue(result.caseTerminatedCleanly(),
                    "WCP-38 case must complete within " + CASE_COMPLETION_TIMEOUT_SECS + "s");
            assertTrue(result.totalWorkItemsStarted() > 0,
                    "At least one work item must have been started in WCP-38");
            assertTrue(elapsedMs < 10_000L,
                    "WCP-38 execution must complete within 10s; took " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("Execution trace begins with StartTask")
        void executionTraceStartsWithStartTask() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp38-trace-01");

            assertTrue(result.caseTerminatedCleanly(), "WCP-38 case must complete first");
            assertFalse(result.trace().isEmpty(), "Trace must not be empty");
            assertEquals("StartTask", result.trace().get(0),
                    "First completed task must be StartTask");
        }

        @Test
        @DisplayName("Execution trace ends with Complete task")
        void executionTraceEndsWithComplete() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp38-complete-01");

            assertTrue(result.caseTerminatedCleanly(), "WCP-38 case must complete first");
            assertFalse(result.trace().isEmpty(), "Trace must not be empty");
            String lastTask = result.trace().get(result.trace().size() - 1);
            assertEquals("Complete", lastTask,
                    "Last completed task must be Complete; trace=" + result.trace());
        }

        @Test
        @DisplayName("Parallel branches both execute before WaitForGlobalEvent")
        void parallelBranchesBothExecuteBeforeJoin() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-38-global-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp38-parallel-01");

            assertTrue(result.caseTerminatedCleanly(), "WCP-38 case must complete");
            List<String> trace = result.trace();
            // Both SubscribeToEvent and ContinueWork must appear in the trace
            // before WaitForGlobalEvent (AND-join synchronisation point)
            assertTrue(trace.contains("SubscribeToEvent"),
                    "SubscribeToEvent must be in execution trace; trace=" + trace);
            assertTrue(trace.contains("ContinueWork"),
                    "ContinueWork must be in execution trace; trace=" + trace);
            assertTrue(trace.contains("WaitForGlobalEvent"),
                    "WaitForGlobalEvent must be in execution trace; trace=" + trace);
            int subscribeIdx = trace.indexOf("SubscribeToEvent");
            int continueIdx = trace.indexOf("ContinueWork");
            int waitIdx = trace.indexOf("WaitForGlobalEvent");
            assertTrue(subscribeIdx < waitIdx,
                    "SubscribeToEvent must complete before WaitForGlobalEvent");
            assertTrue(continueIdx < waitIdx,
                    "ContinueWork must complete before WaitForGlobalEvent");
        }
    }

    // =========================================================================
    // WCP-39: Reset Trigger
    // =========================================================================

    @Nested
    @DisplayName("WCP-39: Reset Trigger")
    class Wcp39ResetTriggerTests {

        @Test
        @DisplayName("YAML converts to schema-compliant YAWL XML")
        void yamlConvertsToSchemaCompliantXml() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            assertNotNull(xml, "WCP-39 XML must not be null");
            assertTrue(xml.contains("<specificationSet"), "Must have specificationSet root");
            assertTrue(xml.contains("ResetTriggerPattern"),
                    "Must contain spec name from YAML");
        }

        @Test
        @DisplayName("YAML unmarshals to valid YSpecification")
        void yamlUnmarshalsToValidSpec() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            assertNotNull(spec, "WCP-39 specification must not be null");
            assertNotNull(spec.getRootNet(), "Root net must be present");
        }

        @Test
        @DisplayName("XML contains conditional routing on ProcessStep1")
        void xmlContainsConditionalRoutingOnProcessStep1() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            // ProcessStep1 has condition routing in the YAML
            assertTrue(xml.contains("id=\"ProcessStep1\""), "Must have ProcessStep1");
            assertTrue(xml.contains("nextElementRef id=\"CheckReset\""),
                    "Must reference CheckReset as conditional target");
            assertTrue(xml.contains("nextElementRef id=\"ProcessStep2\""),
                    "Must reference ProcessStep2 as default target");
            // The predicate for the reset condition
            assertTrue(xml.contains("<predicate>"),
                    "Must emit predicate for conditional flow from ProcessStep1");
            assertTrue(xml.contains("<isDefaultFlow/>"),
                    "Must emit isDefaultFlow for the ProcessStep2 default path");
        }

        @Test
        @DisplayName("XML contains all eight tasks from WCP-39 YAML")
        void xmlContainsAllEightTasks() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("id=\"StartTask\""), "Must have StartTask");
            assertTrue(xml.contains("id=\"ProcessStep1\""), "Must have ProcessStep1");
            assertTrue(xml.contains("id=\"CheckReset\""), "Must have CheckReset");
            assertTrue(xml.contains("id=\"ResetToCheckpoint\""), "Must have ResetToCheckpoint");
            assertTrue(xml.contains("id=\"ContinueProcessing\""), "Must have ContinueProcessing");
            assertTrue(xml.contains("id=\"ProcessStep2\""), "Must have ProcessStep2");
            assertTrue(xml.contains("id=\"ProcessStep3\""), "Must have ProcessStep3");
            assertTrue(xml.contains("id=\"Complete\""), "Must have Complete");
        }

        @Test
        @DisplayName("XML contains variable declarations for reset state tracking")
        void xmlContainsVariableDeclarations() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("resetRequested"), "Must declare resetRequested variable");
            assertTrue(xml.contains("currentState"), "Must declare currentState variable");
            assertTrue(xml.contains("resetPoint"), "Must declare resetPoint variable");
        }

        @Test
        @DisplayName("XML has XOR splits enabling conditional branching throughout")
        void xmlHasXorSplitsForConditionalBranching() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");

            String xml = converter.convertToXml(yaml);

            // All tasks in WCP-39 use xor split; count the occurrences
            int xorSplitCount = countOccurrences(xml, "<split code=\"xor\"");
            // 8 tasks in WCP-39, each must have a split element
            assertTrue(xorSplitCount >= 8,
                    "All 8 WCP-39 tasks must have split elements; found: " + xorSplitCount);
        }

        @Test
        @DisplayName("Engine executes WCP-39 case to completion on normal path")
        void engineExecutesCaseToCompletionOnNormalPath() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);
            long startNanos = System.nanoTime();

            // Launch with no reset trigger; the engine takes the default (normal) path
            ExecutionResult result = driveToCompletion(spec, "wcp39-exec-01");

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            assertTrue(result.caseTerminatedCleanly(),
                    "WCP-39 case must complete within " + CASE_COMPLETION_TIMEOUT_SECS + "s");
            assertTrue(result.totalWorkItemsStarted() > 0,
                    "At least one work item must have been started in WCP-39");
            assertTrue(elapsedMs < 10_000L,
                    "WCP-39 execution must complete within 10s; took " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("Execution trace begins with StartTask")
        void executionTraceStartsWithStartTask() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp39-trace-01");

            assertTrue(result.caseTerminatedCleanly(), "WCP-39 case must complete first");
            assertFalse(result.trace().isEmpty(), "Trace must not be empty");
            assertEquals("StartTask", result.trace().get(0),
                    "First completed task must be StartTask");
        }

        @Test
        @DisplayName("Execution trace ends with Complete task on normal path")
        void executionTraceEndsWithCompleteOnNormalPath() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp39-complete-01");

            assertTrue(result.caseTerminatedCleanly(), "WCP-39 case must complete first");
            assertFalse(result.trace().isEmpty(), "Trace must not be empty");
            String lastTask = result.trace().get(result.trace().size() - 1);
            assertEquals("Complete", lastTask,
                    "Last completed task must be Complete; trace=" + result.trace());
        }

        @Test
        @DisplayName("Normal path traverses all three process steps in order")
        void normalPathTraversesAllThreeProcessSteps() throws Exception {
            String yaml = loadPatternYaml("eventdriven/wcp-39-reset-trigger.yaml");
            YSpecification spec = yamlToSpec(yaml);

            ExecutionResult result = driveToCompletion(spec, "wcp39-steps-01");

            assertTrue(result.caseTerminatedCleanly(), "WCP-39 case must complete first");
            List<String> trace = result.trace();
            // On the normal (non-reset) path: ProcessStep1 -> ProcessStep2 -> ProcessStep3
            assertTrue(trace.contains("ProcessStep1"), "Trace must include ProcessStep1");
            assertTrue(trace.contains("ProcessStep2"), "Trace must include ProcessStep2");
            assertTrue(trace.contains("ProcessStep3"), "Trace must include ProcessStep3");
            // Verify ordering: step1 before step2, step2 before step3
            assertTrue(trace.indexOf("ProcessStep1") < trace.indexOf("ProcessStep2"),
                    "ProcessStep1 must precede ProcessStep2");
            assertTrue(trace.indexOf("ProcessStep2") < trace.indexOf("ProcessStep3"),
                    "ProcessStep2 must precede ProcessStep3");
        }
    }

    // =========================================================================
    // Cross-pattern: YAML-to-XML conversion consistency
    // =========================================================================

    @Nested
    @DisplayName("Cross-Pattern Conversion Consistency")
    class CrossPatternConversionConsistencyTests {

        @Test
        @DisplayName("All five pattern YAMLs load from classpath without error")
        void allFivePatternYamlsLoadFromClasspath() throws Exception {
            String[] patterns = {
                "statebased/wcp-35-dynamic-partial-join.yaml",
                "controlflow/wcp-36-discriminator-complete.yaml",
                "eventdriven/wcp-37-local-trigger.yaml",
                "eventdriven/wcp-38-global-trigger.yaml",
                "eventdriven/wcp-39-reset-trigger.yaml"
            };
            for (String pattern : patterns) {
                String yaml = loadPatternYaml(pattern);
                assertNotNull(yaml, "YAML for " + pattern + " must not be null");
                assertFalse(yaml.isBlank(), "YAML for " + pattern + " must not be blank");
            }
        }

        @Test
        @DisplayName("All five patterns convert to non-null XML without exception")
        void allFivePatternsConvertToNonNullXml() throws Exception {
            String[] patterns = {
                "statebased/wcp-35-dynamic-partial-join.yaml",
                "controlflow/wcp-36-discriminator-complete.yaml",
                "eventdriven/wcp-37-local-trigger.yaml",
                "eventdriven/wcp-38-global-trigger.yaml",
                "eventdriven/wcp-39-reset-trigger.yaml"
            };
            for (String pattern : patterns) {
                String yaml = loadPatternYaml(pattern);
                String xml = converter.convertToXml(yaml);
                assertNotNull(xml, "XML for " + pattern + " must not be null");
                assertFalse(xml.isBlank(), "XML for " + pattern + " must not be blank");
                assertTrue(xml.contains("<specificationSet"),
                        pattern + " must produce specificationSet root element");
            }
        }

        @Test
        @DisplayName("All five patterns unmarshal to valid YSpecification instances")
        void allFivePatternsUnmarshalToValidSpecifications() throws Exception {
            String[] patterns = {
                "statebased/wcp-35-dynamic-partial-join.yaml",
                "controlflow/wcp-36-discriminator-complete.yaml",
                "eventdriven/wcp-37-local-trigger.yaml",
                "eventdriven/wcp-38-global-trigger.yaml",
                "eventdriven/wcp-39-reset-trigger.yaml"
            };
            for (String pattern : patterns) {
                String yaml = loadPatternYaml(pattern);
                YSpecification spec = yamlToSpec(yaml);
                assertNotNull(spec, "Spec for " + pattern + " must not be null");
                assertNotNull(spec.getRootNet(),
                        "Root net for " + pattern + " must not be null");
            }
        }

        @Test
        @DisplayName("All five patterns produce schema 4.0 namespace declaration")
        void allFivePatternsProduceSchemaFourPointZeroNamespace() throws Exception {
            String[] patterns = {
                "statebased/wcp-35-dynamic-partial-join.yaml",
                "controlflow/wcp-36-discriminator-complete.yaml",
                "eventdriven/wcp-37-local-trigger.yaml",
                "eventdriven/wcp-38-global-trigger.yaml",
                "eventdriven/wcp-39-reset-trigger.yaml"
            };
            String expectedNs = "http://www.yawlfoundation.org/yawlschema";
            for (String pattern : patterns) {
                String yaml = loadPatternYaml(pattern);
                String xml = converter.convertToXml(yaml);
                assertTrue(xml.contains(expectedNs),
                        pattern + " must declare YAWL Schema 4.0 namespace");
                assertTrue(xml.contains("version=\"4.0\""),
                        pattern + " must declare version='4.0'");
            }
        }

        @Test
        @DisplayName("All five patterns produce both input and output conditions")
        void allFivePatternsProduceBothConditions() throws Exception {
            String[] patterns = {
                "statebased/wcp-35-dynamic-partial-join.yaml",
                "controlflow/wcp-36-discriminator-complete.yaml",
                "eventdriven/wcp-37-local-trigger.yaml",
                "eventdriven/wcp-38-global-trigger.yaml",
                "eventdriven/wcp-39-reset-trigger.yaml"
            };
            for (String pattern : patterns) {
                String yaml = loadPatternYaml(pattern);
                String xml = converter.convertToXml(yaml);
                assertTrue(xml.contains("<inputCondition"),
                        pattern + " must have inputCondition");
                assertTrue(xml.contains("<outputCondition"),
                        pattern + " must have outputCondition");
                assertTrue(xml.contains("id=\"i-top\""),
                        pattern + " inputCondition must have id='i-top'");
                assertTrue(xml.contains("id=\"o-top\""),
                        pattern + " outputCondition must have id='o-top'");
            }
        }

        @Test
        @DisplayName("All five patterns execute and complete within time bounds")
        void allFivePatternsExecuteAndCompleteWithinTimeBounds() throws Exception {
            record PatternEntry(String path, String caseIdPrefix) {}
            List<PatternEntry> patterns = List.of(
                new PatternEntry("statebased/wcp-35-dynamic-partial-join.yaml", "wcp35-perf"),
                new PatternEntry("controlflow/wcp-36-discriminator-complete.yaml", "wcp36-perf"),
                new PatternEntry("eventdriven/wcp-37-local-trigger.yaml", "wcp37-perf"),
                new PatternEntry("eventdriven/wcp-38-global-trigger.yaml", "wcp38-perf"),
                new PatternEntry("eventdriven/wcp-39-reset-trigger.yaml", "wcp39-perf")
            );

            long totalStartNanos = System.nanoTime();
            int patternIndex = 0;

            for (PatternEntry entry : patterns) {
                String yaml = loadPatternYaml(entry.path());
                YSpecification spec = yamlToSpec(yaml);

                long caseStartNanos = System.nanoTime();
                ExecutionResult result = driveToCompletion(
                        spec, entry.caseIdPrefix() + "-" + patternIndex);
                long caseElapsedMs = (System.nanoTime() - caseStartNanos) / 1_000_000L;

                assertTrue(result.caseTerminatedCleanly(),
                        entry.path() + " must complete within timeout");
                assertTrue(result.totalWorkItemsStarted() > 0,
                        entry.path() + " must process at least one work item");
                assertTrue(caseElapsedMs < 10_000L,
                        entry.path() + " must complete within 10s; took " + caseElapsedMs + "ms");

                patternIndex++;
            }

            long totalElapsedMs = (System.nanoTime() - totalStartNanos) / 1_000_000L;
            // All five patterns must complete in under 30 seconds total
            assertTrue(totalElapsedMs < 30_000L,
                    "All 5 patterns must complete within 30s; took " + totalElapsedMs + "ms");
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Count occurrences of a literal substring within a string.
     *
     * @param text    the string to search within
     * @param pattern the literal substring to count
     * @return the number of non-overlapping occurrences
     */
    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
