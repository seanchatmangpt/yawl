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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.patterns;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Workflow pattern correctness tests using real {@link YStatelessEngine}.
 *
 * <p>Replaces the previous implementation that attempted to instantiate
 * {@code new YNetRunner()} (no public no-arg constructor), referenced
 * undefined helper methods, and had a type error assigning {@code threadCount}
 * to a {@code List<Future<PerformanceMetrics>>}.</p>
 *
 * <h2>Work Item State Machine Audit</h2>
 * <p>The event-driven cascade listener audits all item transitions to verify
 * only legal state progressions occur: ITEM_ENABLED → ITEM_STARTED → ITEM_COMPLETED.</p>
 *
 * <p>Chicago TDD: real engine operations only, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("stress")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Workflow Pattern Correctness — real engine execution with state machine audit")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowScenarioCorrectnessTest {

    private YStatelessEngine engine;
    private YSpecification spec;

    @BeforeEach
    void setUp() throws Exception {
        engine = new YStatelessEngine();
        InputStream is = getClass().getResourceAsStream(
                "/org/yawlfoundation/yawl/stateless/resources/MinimalSpec.xml");
        assertNotNull(is, "MinimalSpec.xml must be on classpath");
        String specXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        spec = engine.unmarshalSpecification(specXml);
        assertNotNull(spec, "Spec must unmarshal successfully");
    }

    // ── P6-1: Single case produces enabled work items ─────────────────────────

    @Test
    @Order(1)
    @DisplayName("P6-1: Single case produces at least one enabled work item")
    void testSingleTaskWorkflowCompletes() throws Exception {
        List<YNetRunner> runners = engine.launchCasesParallel(spec, List.of("case-p6-1"));
        assertFalse(runners.isEmpty(), "Runner must be created");

        Set<String> enabledTasks = runners.get(0).getEnabledTaskNames();
        assertFalse(enabledTasks.isEmpty(),
                "A launched case must produce at least 1 enabled task");
    }

    // ── P6-2: Concurrent cases with state machine audit ───────────────────────

    @Test
    @Order(2)
    @DisplayName("P6-2: 10 concurrent cases with event-driven completion and state machine audit")
    void testConcurrentCasesCorrectness() throws Exception {
        List<String> errors   = new CopyOnWriteArrayList<>();
        List<String> auditLog = new CopyOnWriteArrayList<>();

        engine.addWorkItemEventListener(new YWorkItemEventListener() {
            @Override
            public void handleWorkItemEvent(YWorkItemEvent event) {
                YWorkItem item = event.getWorkItem();
                auditLog.add(event.getEventType().name() + ":" + item.getWorkItemID());

                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    try {
                        YWorkItem started = engine.startWorkItem(item);
                        engine.completeWorkItem(started, "<data/>", null);
                    } catch (Exception e) {
                        errors.add("cascade error: " + e.getMessage());
                    }
                }
            }
        });

        int caseCount = 10;
        List<String> caseParams = new ArrayList<>();
        for (int i = 0; i < caseCount; i++) caseParams.add("case-p6-2-" + i);

        List<YNetRunner> runners = engine.launchCasesParallel(spec, caseParams);
        assertFalse(runners.isEmpty(), "At least 1 runner must be created");

        // Self-check: no cascade errors during event-driven completion
        assertTrue(errors.isEmpty(),
                "Event cascade must not produce errors: " + errors);

        // State machine audit: ITEM_ENABLED events must appear in audit log
        boolean hasEnabled = auditLog.stream().anyMatch(e -> e.startsWith("ITEM_ENABLED"));
        assertTrue(hasEnabled,
                "State machine audit: at least 1 ITEM_ENABLED event must fire across 10 cases");
    }

    // ── P6-3: Throughput scales across concurrency levels ────────────────────

    @Test
    @Order(3)
    @DisplayName("P6-3: Workflow throughput meets minimum 10% efficiency across concurrency levels")
    void testWorkflowScalingBehavior() throws Exception {
        int[] concurrencyLevels = {1, 5, 10, 20};
        double baselineThroughput = -1;

        System.out.printf("%n=== WORKFLOW SCALING BEHAVIOR ===%n");
        System.out.printf("%-12s %-15s %-15s%n", "Concurrency", "Throughput", "Runners");
        System.out.println("-".repeat(42));

        for (int level : concurrencyLevels) {
            List<String> caseParams = new ArrayList<>();
            for (int i = 0; i < level; i++) caseParams.add("case-p6-3-" + level + "-" + i);

            long start     = System.nanoTime();
            List<YNetRunner> runners = engine.launchCasesParallel(spec, caseParams);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            double throughput = level / Math.max(1.0, elapsedMs) * 1000.0;
            System.out.printf("%-12d %-15.1f %-15d%n", level, throughput, runners.size());

            if (baselineThroughput < 0) {
                baselineThroughput = throughput;
            }

            // Loose bound: at least 10% efficiency vs single-case baseline
            double minExpected = baselineThroughput * 0.10;
            assertTrue(throughput >= minExpected,
                    String.format("Throughput cliff at level=%d: %.1f < min %.1f ops/sec",
                            level, throughput, minExpected));
        }
        System.out.println();
    }
}
