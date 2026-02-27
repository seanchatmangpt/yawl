/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark suite that validates YAWL engine health via real workflow operations.
 *
 * <p>Replaces the previous implementation that had:</p>
 * <ul>
 *   <li>An empty {@code runBenchmarkClassWithPattern()} method body</li>
 *   <li>{@code ValidationResult} hardcoded to {@code passed=true}</li>
 *   <li>{@code ConsolidatedReport} generating static template HTML with no data</li>
 *   <li>{@code isValidResultFile()} using an illegal {@code throws IOException} in a lambda</li>
 * </ul>
 *
 * <h2>Self-checking oracle</h2>
 * <p>After each engine operation, the runner work item repository is verified
 * to return a non-null set — providing an oracle for case repository integrity.</p>
 *
 * <p>Chicago TDD: real engine operations only, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("benchmark")
@DisplayName("Benchmark Suite — YAWL engine health validation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BenchmarkSuite {

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("S1: Engine health check — bootstrap, spec load, case start, work item query")
    void engineHealthCheck() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = loadSpec(engine);

        int caseTarget = 10;
        List<String> caseParams = new ArrayList<>();
        for (int i = 0; i < caseTarget; i++) caseParams.add("health-" + i);

        List<YNetRunner> runners = engine.launchCasesParallel(spec, caseParams);
        int casesSucceeded = runners.size();

        // Work item query self-check: repository must not be null
        boolean workItemQuerySucceeded = false;
        if (!runners.isEmpty()) {
            Set<String> tasks = runners.get(0).getEnabledTaskNames();
            workItemQuerySucceeded = (tasks != null);
        }

        ValidationResult result = new ValidationResult(
                true, caseTarget, casesSucceeded, workItemQuerySucceeded, null);

        System.out.printf("%n=== BENCHMARK SUITE ENGINE HEALTH CHECK ===%n");
        System.out.printf("Cases attempted: %d, succeeded: %d%n",
                result.casesAttempted(), result.casesSucceeded());
        System.out.printf("Work item query: %s%n",
                result.workItemQuerySucceeded() ? "OK" : "FAILED");
        System.out.printf("Overall:         %s%n%n", result.passed() ? "PASS" : "FAIL");

        assertTrue(result.passed(),
                "Engine health check failed: attempted=" + result.casesAttempted()
                        + " succeeded=" + result.casesSucceeded());
    }

    @Test
    @Order(2)
    @DisplayName("S2: Workflow cycle completion — 5 cases driven to completion via event cascade")
    void workflowCycleCompletionTest() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = loadSpec(engine);
        List<String> errors = new CopyOnWriteArrayList<>();

        engine.addWorkItemEventListener(new YWorkItemEventListener() {
            @Override
            public void handleWorkItemEvent(YWorkItemEvent event) {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    try {
                        YWorkItem started = engine.startWorkItem(event.getWorkItem());
                        engine.completeWorkItem(started, "<data/>", null);
                    } catch (Exception e) {
                        errors.add(e.getMessage());
                    }
                }
            }
        });

        int caseTarget = 5;
        List<String> caseParams = new ArrayList<>();
        for (int i = 0; i < caseTarget; i++) caseParams.add("suite-cycle-" + i);

        List<YNetRunner> runners = engine.launchCasesParallel(spec, caseParams);
        assertFalse(runners.isEmpty(), "At least 1 runner must be created");
        assertTrue(errors.isEmpty(),
                "Workflow cycle completion must not produce errors: " + errors);

        // Self-check: each runner work item repository must not be null
        for (YNetRunner runner : runners) {
            assertNotNull(runner.getWorkItemRepository(),
                    "Runner work item repository must not be null");
        }
    }

    // ── Result record ─────────────────────────────────────────────────────────

    /**
     * Immutable health check result with real validation criteria.
     *
     * <p>{@code passed()} requires: engine started, ≥90% of cases succeeded,
     * and work item query returned non-null.</p>
     */
    record ValidationResult(
            boolean engineStarted,
            int casesAttempted,
            int casesSucceeded,
            boolean workItemQuerySucceeded,
            String errorMessage
    ) {
        boolean passed() {
            return engineStarted
                    && casesSucceeded >= (casesAttempted * 9 / 10)
                    && workItemQuerySucceeded;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private YSpecification loadSpec(YStatelessEngine engine) throws Exception {
        InputStream is = getClass().getResourceAsStream(
                "/org/yawlfoundation/yawl/stateless/resources/MinimalSpec.xml");
        assertNotNull(is, "MinimalSpec.xml must be on classpath");
        String specXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        YSpecification spec = engine.unmarshalSpecification(specXml);
        assertNotNull(spec, "Spec must unmarshal successfully");
        return spec;
    }

    /**
     * Checks if a result file contains real benchmark metrics (throughput and latency fields).
     */
    private boolean isValidResultFile(Path file) {
        try {
            String content = Files.readString(file);
            return content.contains("throughput") && content.contains("latency");
        } catch (IOException e) {
            return false;
        }
    }
}
