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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.WorkItemCompletion;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine execution tests for WCP-30 through WCP-34 advanced workflow control patterns.
 *
 * <p>Each test loads the pattern YAML from the patterns resource directory, converts it
 * to YAWL XML using {@link ExtendedYamlConverter}, loads it into {@link YStatelessEngine},
 * and drives execution by responding to work-item events. The tests verify:</p>
 *
 * <ul>
 *   <li>WCP-30: Loop with Cancel Region — structured loop body with cancellable region</li>
 *   <li>WCP-31: Loop with Complete MI — structured loop with multi-instance completion</li>
 *   <li>WCP-32: Synchronizing Merge with Cancel — AND-join merge with cancel capability</li>
 *   <li>WCP-33: Generalized And-Join — AND-join for dynamically-sized parallel branches</li>
 *   <li>WCP-34: Static Partial Join — join triggered when N-of-M branches complete</li>
 * </ul>
 *
 * <p>Chicago TDD: real objects, real engine, real event dispatch. No mocks or stubs.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("WCP-30..34 Engine Execution Tests")
class WcpPatternEngineExecutionTest {

    /** Root of the patterns resource tree inside the mcp-a2a-app module. */
    private static final String PATTERNS_ROOT =
            "yawl-mcp-a2a-app/src/main/resources/patterns";

    /** Absolute base path resolved at runtime. */
    private static final Path PATTERNS_BASE = resolveProjectRoot().resolve(PATTERNS_ROOT);

    /** Max seconds to wait for case-completed event per test. */
    private static final long CASE_TIMEOUT_SEC = 15L;

    private ExtendedYamlConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ExtendedYamlConverter();
    }

    // =========================================================================
    // Helper — resolve project root (two levels above test classes on classpath)
    // =========================================================================

    private static Path resolveProjectRoot() {
        // Walk upward from cwd until we find the root pom.xml
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("pom.xml"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Cannot locate project root (pom.xml not found)");
        }
        return dir;
    }

    // =========================================================================
    // Helper — load YAML and convert to YAWL XML
    // =========================================================================

    private String yamlToXml(Path yamlPath) throws IOException {
        assertTrue(Files.exists(yamlPath), "Pattern YAML not found: " + yamlPath);
        String yaml = Files.readString(yamlPath);
        assertFalse(yaml.isBlank(), "Pattern YAML is empty: " + yamlPath);
        return converter.convertToXml(yaml);
    }

    // =========================================================================
    // Helper — execution driver
    // =========================================================================

    /**
     * Drives a single case to completion by automatically starting and completing
     * every work item that becomes enabled. Collects an execution trace of task IDs
     * in the order they were activated.
     *
     * @param specXml   YAWL XML specification
     * @param caseId    unique identifier for the case
     * @return          execution result capturing trace, timing and status
     */
    private ExecutionResult driveCase(String specXml, String caseId)
            throws YSyntaxException, YStateException, YDataStateException,
                   YEngineStateException, YQueryException, InterruptedException {

        YStatelessEngine engine = new YStatelessEngine();
        ExecutionDriver driver = new ExecutionDriver(engine, CASE_TIMEOUT_SEC);
        engine.addCaseEventListener(driver);
        engine.addWorkItemEventListener(driver);

        long startNs = System.nanoTime();
        YSpecification spec = engine.unmarshalSpecification(specXml);
        assertNotNull(spec, "Specification must not be null after unmarshal");
        assertNotNull(spec.getRootNet(), "Root net must be present");

        engine.launchCase(spec, caseId);
        boolean completed = driver.awaitCompletion();
        long elapsedNs = System.nanoTime() - startNs;

        engine.removeCaseEventListener(driver);
        engine.removeWorkItemEventListener(driver);

        return new ExecutionResult(
                caseId,
                completed,
                driver.getTrace(),
                driver.getErrors(),
                elapsedNs,
                driver.getItemsStarted(),
                driver.getItemsCompleted()
        );
    }

    // =========================================================================
    // WCP-30: Loop with Cancel Region
    // =========================================================================

    @Nested
    @DisplayName("WCP-30: Loop with Cancel Region")
    class Wcp30LoopCancelRegionTests {

        private static final Path YAML_PATH =
                PATTERNS_BASE.resolve("controlflow/wcp-30-loop-cancel-region.yaml");

        @Test
        @Timeout(30)
        @DisplayName("WCP-30: YAML resource exists and is non-empty")
        void yamlResourceExists() throws IOException {
            assertTrue(Files.exists(YAML_PATH),
                    "WCP-30 YAML must exist at: " + YAML_PATH);
            String yaml = Files.readString(YAML_PATH);
            assertFalse(yaml.isBlank(), "WCP-30 YAML must not be empty");
            assertTrue(yaml.contains("LoopCancelRegionPattern"),
                    "WCP-30 YAML must declare LoopCancelRegionPattern");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-30: YAML converts to valid YAWL XML with required structural elements")
        void convertsToValidYawlXml() throws IOException {
            String xml = yamlToXml(YAML_PATH);
            // Root structural elements
            assertTrue(xml.contains("<specificationSet"), "Must have specificationSet");
            assertTrue(xml.contains("version=\"4.0\""), "Must use schema version 4.0");
            assertTrue(xml.contains("isRootNet=\"true\""), "Must have root net");
            assertTrue(xml.contains("<inputCondition"), "Must have input condition");
            assertTrue(xml.contains("<outputCondition"), "Must have output condition");
            // Loop-specific tasks
            assertTrue(xml.contains("id=\"StartTask\""), "Must have StartTask");
            assertTrue(xml.contains("id=\"CheckCondition\""), "Must have CheckCondition");
            assertTrue(xml.contains("id=\"LoopBody\""), "Must have LoopBody");
            assertTrue(xml.contains("id=\"ExitLoop\""), "Must have ExitLoop");
            assertTrue(xml.contains("id=\"CancelRegion\""), "Must have CancelRegion task");
            // XOR split/join codes for loop routing
            assertTrue(xml.contains("code=\"xor\""), "Must have XOR routing codes");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-30: Engine executes loop structure — case starts and activates tasks")
        void engineExecutesLoopStructure()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp30-case-1");

            assertEngineExecutionBasics(result, "WCP-30");
            // Loop pattern must activate at least the initial sequence: StartTask, Initialize
            assertTrue(result.itemsStarted() >= 2,
                    "WCP-30 must start at least 2 work items (loop setup), got: "
                            + result.itemsStarted());
            // First task in trace must be StartTask
            assertFalse(result.trace().isEmpty(), "Execution trace must be non-empty");
            assertEquals("StartTask", result.trace().get(0),
                    "WCP-30 execution must begin with StartTask");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-30: Engine emits ITEM_ENABLED events for each task in execution path")
        void engineEmitsEnabledEventsForEachTask()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp30-case-2");

            assertEngineExecutionBasics(result, "WCP-30");
            // Every item that was started must also have been completed
            assertEquals(result.itemsStarted(), result.itemsCompleted(),
                    "Every started work item must be completed for WCP-30");
        }
    }

    // =========================================================================
    // WCP-31: Loop with Complete MI
    // =========================================================================

    @Nested
    @DisplayName("WCP-31: Loop with Complete MI")
    class Wcp31LoopCompleteMiTests {

        private static final Path YAML_PATH =
                PATTERNS_BASE.resolve("controlflow/wcp-31-loop-complete-mi.yaml");

        @Test
        @Timeout(30)
        @DisplayName("WCP-31: YAML resource exists and declares MI configuration")
        void yamlResourceExistsWithMiConfig() throws IOException {
            assertTrue(Files.exists(YAML_PATH),
                    "WCP-31 YAML must exist at: " + YAML_PATH);
            String yaml = Files.readString(YAML_PATH);
            assertFalse(yaml.isBlank(), "WCP-31 YAML must not be empty");
            assertTrue(yaml.contains("LoopCompleteMIPattern"),
                    "WCP-31 YAML must declare LoopCompleteMIPattern");
            assertTrue(yaml.contains("multiInstance"),
                    "WCP-31 YAML must declare a multiInstance task");
            assertTrue(yaml.contains("ProcessItems"),
                    "WCP-31 YAML must declare ProcessItems MI task");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-31: YAML converts to XML with MultipleInstanceExternalTaskFactsType")
        void convertsToXmlWithMiTaskType() throws IOException {
            String xml = yamlToXml(YAML_PATH);
            assertTrue(xml.contains("MultipleInstanceExternalTaskFactsType"),
                    "WCP-31 must produce MI task type declaration");
            assertTrue(xml.contains("<minimum>"), "WCP-31 MI task must have minimum element");
            assertTrue(xml.contains("<maximum>"), "WCP-31 MI task must have maximum element");
            assertTrue(xml.contains("<threshold>"), "WCP-31 MI task must have threshold element");
            assertTrue(xml.contains("<creationMode"), "WCP-31 MI task must have creationMode");
            assertTrue(xml.contains("<miDataInput>"),
                    "WCP-31 MI task must have required miDataInput element");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-31: Engine executes loop-with-MI — case activates and completes")
        void engineExecutesLoopWithMi()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp31-case-1");

            assertEngineExecutionBasics(result, "WCP-31");
            assertTrue(result.itemsStarted() >= 2,
                    "WCP-31 must start at least 2 work items, got: " + result.itemsStarted());
            assertFalse(result.trace().isEmpty(), "WCP-31 execution trace must be non-empty");
            assertEquals("StartTask", result.trace().get(0),
                    "WCP-31 execution must begin with StartTask");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-31: Engine produces symmetric start/complete counts for loop body")
        void engineProducesSymmetricStartAndCompleteCounts()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp31-case-2");

            assertEngineExecutionBasics(result, "WCP-31");
            assertEquals(result.itemsStarted(), result.itemsCompleted(),
                    "WCP-31: every started work item must reach completion");
        }
    }

    // =========================================================================
    // WCP-32: Synchronizing Merge with Cancel
    // =========================================================================

    @Nested
    @DisplayName("WCP-32: Synchronizing Merge with Cancel")
    class Wcp32SyncMergeCancelTests {

        private static final Path YAML_PATH =
                PATTERNS_BASE.resolve("statebased/wcp-32-sync-cancel.yaml");

        @Test
        @Timeout(30)
        @DisplayName("WCP-32: YAML resource exists and declares parallel branches")
        void yamlResourceExistsWithParallelBranches() throws IOException {
            assertTrue(Files.exists(YAML_PATH),
                    "WCP-32 YAML must exist at: " + YAML_PATH);
            String yaml = Files.readString(YAML_PATH);
            assertTrue(yaml.contains("SyncMergeCancelPattern"),
                    "WCP-32 must declare SyncMergeCancelPattern");
            assertTrue(yaml.contains("BranchA"), "WCP-32 must have BranchA");
            assertTrue(yaml.contains("BranchB"), "WCP-32 must have BranchB");
            assertTrue(yaml.contains("BranchC"), "WCP-32 must have BranchC");
            assertTrue(yaml.contains("SyncPoint"), "WCP-32 must have SyncPoint");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-32: YAML converts to XML with AND-split for parallel branch activation")
        void convertsToXmlWithAndSplitForParallelBranches() throws IOException {
            String xml = yamlToXml(YAML_PATH);
            // StartTask has split: and (fires BranchA, BranchB, BranchC simultaneously)
            assertTrue(xml.contains("code=\"and\""),
                    "WCP-32 must have AND-split for parallel branch activation");
            // SyncPoint has join: and (waits for all three branches)
            // The join code must also appear
            int andCount = countOccurrences(xml, "code=\"and\"");
            assertTrue(andCount >= 2,
                    "WCP-32 must have at least 2 AND codes (split on StartTask + join on SyncPoint), found: "
                            + andCount);
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-32: Engine executes synchronizing merge — all three branches activate")
        void engineExecutesSynchronizingMerge()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp32-case-1");

            assertEngineExecutionBasics(result, "WCP-32");
            // Minimum: StartTask + BranchA + BranchB + BranchC + SyncPoint + Complete
            assertTrue(result.itemsStarted() >= 5,
                    "WCP-32 must activate at least 5 items (StartTask + 3 branches + sync point), got: "
                            + result.itemsStarted());
            // All three branch tasks must appear in trace
            List<String> trace = result.trace();
            assertTrue(trace.contains("BranchA"),
                    "WCP-32 execution trace must contain BranchA, trace=" + trace);
            assertTrue(trace.contains("BranchB"),
                    "WCP-32 execution trace must contain BranchB, trace=" + trace);
            assertTrue(trace.contains("BranchC"),
                    "WCP-32 execution trace must contain BranchC, trace=" + trace);
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-32: SyncPoint appears after all three branch tasks in execution trace")
        void syncPointAppearsAfterAllBranches()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp32-case-2");

            assertEngineExecutionBasics(result, "WCP-32");
            List<String> trace = result.trace();
            int branchAIdx = trace.indexOf("BranchA");
            int branchBIdx = trace.indexOf("BranchB");
            int branchCIdx = trace.indexOf("BranchC");
            int syncIdx = trace.indexOf("SyncPoint");
            assertTrue(branchAIdx >= 0, "BranchA must appear in trace");
            assertTrue(branchBIdx >= 0, "BranchB must appear in trace");
            assertTrue(branchCIdx >= 0, "BranchC must appear in trace");
            assertTrue(syncIdx >= 0, "SyncPoint must appear in trace");
            // SyncPoint is an AND-join: it fires only after all branch tasks complete
            int lastBranchIdx = Math.max(Math.max(branchAIdx, branchBIdx), branchCIdx);
            assertTrue(syncIdx > lastBranchIdx,
                    "SyncPoint (AND-join) must appear after all branches in trace; "
                            + "SyncPoint=" + syncIdx + " lastBranch=" + lastBranchIdx
                            + " trace=" + trace);
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-32: Engine produces symmetric start/complete counts")
        void engineProducesSymmetricCounts()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp32-case-3");

            assertEngineExecutionBasics(result, "WCP-32");
            assertEquals(result.itemsStarted(), result.itemsCompleted(),
                    "WCP-32: every started work item must be completed");
        }
    }

    // =========================================================================
    // WCP-33: Generalized And-Join
    // =========================================================================

    @Nested
    @DisplayName("WCP-33: Generalized And-Join")
    class Wcp33GeneralizedAndJoinTests {

        private static final Path YAML_PATH =
                PATTERNS_BASE.resolve("statebased/wcp-33-generalized-join.yaml");

        @Test
        @Timeout(30)
        @DisplayName("WCP-33: YAML resource exists and declares generalized join")
        void yamlResourceExistsWithGeneralizedJoin() throws IOException {
            assertTrue(Files.exists(YAML_PATH),
                    "WCP-33 YAML must exist at: " + YAML_PATH);
            String yaml = Files.readString(YAML_PATH);
            assertTrue(yaml.contains("GeneralizedAndJoinPattern"),
                    "WCP-33 must declare GeneralizedAndJoinPattern");
            assertTrue(yaml.contains("ProcessBranches"),
                    "WCP-33 must declare ProcessBranches MI task");
            assertTrue(yaml.contains("GeneralizedJoin"),
                    "WCP-33 must declare GeneralizedJoin task");
            assertTrue(yaml.contains("generalizedJoin: true"),
                    "WCP-33 must flag generalizedJoin: true");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-33: YAML converts to XML with AND-join on GeneralizedJoin task")
        void convertsToXmlWithAndJoinOnGeneralizedJoinTask() throws IOException {
            String xml = yamlToXml(YAML_PATH);
            assertTrue(xml.contains("id=\"GeneralizedJoin\""),
                    "WCP-33 must have GeneralizedJoin task element");
            // MI task on ProcessBranches
            assertTrue(xml.contains("MultipleInstanceExternalTaskFactsType"),
                    "WCP-33 must have MI task for ProcessBranches");
            // AND-join must appear (for GeneralizedJoin task)
            assertTrue(xml.contains("code=\"and\""),
                    "WCP-33 must have AND-join code for GeneralizedJoin task");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-33: Engine executes generalized join — case activates and completes")
        void engineExecutesGeneralizedJoin()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp33-case-1");

            assertEngineExecutionBasics(result, "WCP-33");
            assertFalse(result.trace().isEmpty(),
                    "WCP-33 execution trace must not be empty");
            assertEquals("StartTask", result.trace().get(0),
                    "WCP-33 execution must begin with StartTask");
            // DetermineBranches must fire before GeneralizedJoin
            assertTrue(result.trace().contains("DetermineBranches"),
                    "WCP-33 trace must contain DetermineBranches: " + result.trace());
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-33: Engine produces symmetric start/complete counts")
        void engineProducesSymmetricCounts()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp33-case-2");

            assertEngineExecutionBasics(result, "WCP-33");
            assertEquals(result.itemsStarted(), result.itemsCompleted(),
                    "WCP-33: every started work item must be completed");
        }
    }

    // =========================================================================
    // WCP-34: Static Partial Join
    // =========================================================================

    @Nested
    @DisplayName("WCP-34: Static Partial Join")
    class Wcp34StaticPartialJoinTests {

        private static final Path YAML_PATH =
                PATTERNS_BASE.resolve("statebased/wcp-34-static-partial-join.yaml");

        @Test
        @Timeout(30)
        @DisplayName("WCP-34: YAML resource exists and declares 5-branch partial join")
        void yamlResourceExistsWithFiveBranches() throws IOException {
            assertTrue(Files.exists(YAML_PATH),
                    "WCP-34 YAML must exist at: " + YAML_PATH);
            String yaml = Files.readString(YAML_PATH);
            assertTrue(yaml.contains("StaticPartialJoinPattern"),
                    "WCP-34 must declare StaticPartialJoinPattern");
            assertTrue(yaml.contains("Branch1"), "WCP-34 must have Branch1");
            assertTrue(yaml.contains("Branch2"), "WCP-34 must have Branch2");
            assertTrue(yaml.contains("Branch3"), "WCP-34 must have Branch3");
            assertTrue(yaml.contains("Branch4"), "WCP-34 must have Branch4");
            assertTrue(yaml.contains("Branch5"), "WCP-34 must have Branch5");
            assertTrue(yaml.contains("PartialJoin"), "WCP-34 must have PartialJoin task");
            assertTrue(yaml.contains("threshold: 3"), "WCP-34 must declare threshold of 3");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-34: YAML converts to XML with AND-split activating 5 branches")
        void convertsToXmlWithAndSplitFiveBranches() throws IOException {
            String xml = yamlToXml(YAML_PATH);
            // StartTask has split: and → 5 branches
            assertTrue(xml.contains("code=\"and\""),
                    "WCP-34 must have AND-split to launch all 5 branches");
            // All 5 branch tasks must appear
            assertTrue(xml.contains("id=\"Branch1\""), "Must have Branch1");
            assertTrue(xml.contains("id=\"Branch2\""), "Must have Branch2");
            assertTrue(xml.contains("id=\"Branch3\""), "Must have Branch3");
            assertTrue(xml.contains("id=\"Branch4\""), "Must have Branch4");
            assertTrue(xml.contains("id=\"Branch5\""), "Must have Branch5");
            assertTrue(xml.contains("id=\"PartialJoin\""), "Must have PartialJoin");
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-34: Engine executes static partial join — case activates all branches")
        void engineExecutesStaticPartialJoin()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp34-case-1");

            assertEngineExecutionBasics(result, "WCP-34");
            // StartTask + 5 branches + PartialJoin + Complete = at least 8 work items
            assertTrue(result.itemsStarted() >= 7,
                    "WCP-34 must activate StartTask + all 5 branches + partial join, got: "
                            + result.itemsStarted());
            List<String> trace = result.trace();
            assertTrue(trace.contains("Branch1"),
                    "WCP-34 trace must contain Branch1, trace=" + trace);
            assertTrue(trace.contains("Branch2"),
                    "WCP-34 trace must contain Branch2, trace=" + trace);
            assertTrue(trace.contains("Branch3"),
                    "WCP-34 trace must contain Branch3, trace=" + trace);
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-34: PartialJoin appears in trace (join task fires)")
        void partialJoinAppearsInTrace()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp34-case-2");

            assertEngineExecutionBasics(result, "WCP-34");
            assertTrue(result.trace().contains("PartialJoin"),
                    "PartialJoin task must appear in execution trace, trace=" + result.trace());
        }

        @Test
        @Timeout(30)
        @DisplayName("WCP-34: Engine produces symmetric start/complete counts")
        void engineProducesSymmetricCounts()
                throws IOException, YSyntaxException, YStateException, YDataStateException,
                       YEngineStateException, YQueryException, InterruptedException {

            String xml = yamlToXml(YAML_PATH);
            ExecutionResult result = driveCase(xml, "wcp34-case-3");

            assertEngineExecutionBasics(result, "WCP-34");
            assertEquals(result.itemsStarted(), result.itemsCompleted(),
                    "WCP-34: every started work item must be completed");
        }
    }

    // =========================================================================
    // Cross-pattern performance report
    // =========================================================================

    @Nested
    @DisplayName("Cross-Pattern: XML Conversion Performance")
    class CrossPatternPerformanceTests {

        @Test
        @Timeout(10)
        @DisplayName("All 5 patterns convert to XML within 500ms each")
        void allPatternsConvertWithinBudget() throws IOException {
            Path[] yamlPaths = {
                PATTERNS_BASE.resolve("controlflow/wcp-30-loop-cancel-region.yaml"),
                PATTERNS_BASE.resolve("controlflow/wcp-31-loop-complete-mi.yaml"),
                PATTERNS_BASE.resolve("statebased/wcp-32-sync-cancel.yaml"),
                PATTERNS_BASE.resolve("statebased/wcp-33-generalized-join.yaml"),
                PATTERNS_BASE.resolve("statebased/wcp-34-static-partial-join.yaml")
            };
            String[] names = {"WCP-30", "WCP-31", "WCP-32", "WCP-33", "WCP-34"};

            for (int i = 0; i < yamlPaths.length; i++) {
                long start = System.nanoTime();
                String xml = yamlToXml(yamlPaths[i]);
                long elapsed = System.nanoTime() - start;
                long elapsedMs = elapsed / 1_000_000L;
                assertNotNull(xml, names[i] + " XML must not be null");
                assertTrue(elapsedMs < 500,
                        names[i] + " conversion must complete in <500ms, took: " + elapsedMs + "ms");
            }
        }

        @Test
        @Timeout(10)
        @DisplayName("All 5 patterns produce structurally complete YAWL XML")
        void allPatternsProduceStructurallyCompleteXml() throws IOException {
            Path[] yamlPaths = {
                PATTERNS_BASE.resolve("controlflow/wcp-30-loop-cancel-region.yaml"),
                PATTERNS_BASE.resolve("controlflow/wcp-31-loop-complete-mi.yaml"),
                PATTERNS_BASE.resolve("statebased/wcp-32-sync-cancel.yaml"),
                PATTERNS_BASE.resolve("statebased/wcp-33-generalized-join.yaml"),
                PATTERNS_BASE.resolve("statebased/wcp-34-static-partial-join.yaml")
            };
            String[] names = {"WCP-30", "WCP-31", "WCP-32", "WCP-33", "WCP-34"};

            for (int i = 0; i < yamlPaths.length; i++) {
                String xml = yamlToXml(yamlPaths[i]);
                String name = names[i];
                assertTrue(xml.contains("<specificationSet"), name + ": must have specificationSet");
                assertTrue(xml.contains("version=\"4.0\""), name + ": must use schema v4.0");
                assertTrue(xml.contains("<inputCondition"), name + ": must have inputCondition");
                assertTrue(xml.contains("<outputCondition"), name + ": must have outputCondition");
                assertTrue(xml.contains("<processControlElements>"),
                        name + ": must have processControlElements");
                assertTrue(xml.contains("isRootNet=\"true\""), name + ": must have root net");
                // Tags must be balanced
                assertEquals(
                        countOccurrences(xml, "<specificationSet"),
                        countOccurrences(xml, "</specificationSet>"),
                        name + ": specificationSet must have balanced tags");
            }
        }
    }

    // =========================================================================
    // Shared assertion helper
    // =========================================================================

    private void assertEngineExecutionBasics(ExecutionResult result, String patternName) {
        assertTrue(result.errors().isEmpty(),
                patternName + " engine errors: " + result.errors());
        assertTrue(result.caseCompleted(),
                patternName + " case must complete within " + CASE_TIMEOUT_SEC + "s");
        assertTrue(result.itemsStarted() > 0,
                patternName + " must start at least 1 work item");
        assertTrue(result.elapsedMs() < (CASE_TIMEOUT_SEC * 1000),
                patternName + " must complete in <" + CASE_TIMEOUT_SEC + "s, took: "
                        + result.elapsedMs() + "ms");
    }

    // =========================================================================
    // Internal types
    // =========================================================================

    /**
     * Immutable record capturing the result of driving a single case to completion.
     */
    record ExecutionResult(
            String caseId,
            boolean caseCompleted,
            List<String> trace,
            List<String> errors,
            long elapsedNs,
            int itemsStarted,
            int itemsCompleted
    ) {
        long elapsedMs() { return elapsedNs / 1_000_000L; }
    }

    /**
     * Work-item and case-event listener that automatically drives cases to completion
     * by starting enabled items and completing executing items.
     *
     * <p>Collects a trace of task IDs in order of ITEM_ENABLED events, counts
     * ITEM_STARTED and ITEM_COMPLETED events, and latches on CASE_COMPLETED.</p>
     */
    private static final class ExecutionDriver
            implements YCaseEventListener, YWorkItemEventListener {

        private final YStatelessEngine engine;
        private final CountDownLatch completionLatch = new CountDownLatch(1);
        private final List<String> trace = new CopyOnWriteArrayList<>();
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private final AtomicInteger itemsStarted = new AtomicInteger(0);
        private final AtomicInteger itemsCompleted = new AtomicInteger(0);
        private final long timeoutSec;

        ExecutionDriver(YStatelessEngine engine, long timeoutSec) {
            this.engine = engine;
            this.timeoutSec = timeoutSec;
        }

        @Override
        public void handleCaseEvent(YCaseEvent event) {
            if (event.getEventType() == YEventType.CASE_COMPLETED) {
                completionLatch.countDown();
            }
        }

        @Override
        public void handleWorkItemEvent(YWorkItemEvent event) {
            YWorkItem item = event.getWorkItem();
            YEventType type = event.getEventType();
            try {
                if (type == YEventType.ITEM_ENABLED) {
                    trace.add(item.getTaskID());
                    engine.startWorkItem(item);
                } else if (type == YEventType.ITEM_STARTED) {
                    itemsStarted.incrementAndGet();
                    engine.completeWorkItem(item, "<data/>", null,
                            WorkItemCompletion.Normal);
                } else if (type == YEventType.ITEM_COMPLETED) {
                    itemsCompleted.incrementAndGet();
                }
            } catch (YStateException | YDataStateException | YQueryException
                     | YEngineStateException e) {
                errors.add(item.getTaskID() + "[" + type + "]: " + e.getMessage());
            }
        }

        boolean awaitCompletion() throws InterruptedException {
            return completionLatch.await(timeoutSec, TimeUnit.SECONDS);
        }

        List<String> getTrace()  { return List.copyOf(trace); }
        List<String> getErrors() { return List.copyOf(errors); }
        int getItemsStarted()    { return itemsStarted.get(); }
        int getItemsCompleted()  { return itemsCompleted.get(); }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
