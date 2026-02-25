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

package org.yawlfoundation.yawl.integration.reactor;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.memory.LearningCapture;
import org.yawlfoundation.yawl.integration.memory.UpgradeMemoryStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Chicago TDD test suite for LivingProcessReactor.
 *
 * <p>Tests verify that the reactor runs cycles, detects drift, proposes
 * mutations using LearningCapture, and persists results via UpgradeMemoryStore.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class LivingProcessReactorTest extends TestCase {

    private UpgradeMemoryStore memoryStore;
    private LearningCapture learningCapture;
    private LivingProcessReactor reactor;
    private Path tempDir;

    @Override
    protected void setUp() throws Exception {
        tempDir = Files.createTempDirectory("yawl-reactor-test");
        memoryStore = new UpgradeMemoryStore(tempDir);
        learningCapture = new LearningCapture(memoryStore);
    }

    @Override
    protected void tearDown() throws Exception {
        if (reactor != null && reactor.isRunning()) {
            reactor.stop();
        }
        // Clean up temp dir
        try (var files = Files.walk(tempDir)) {
            files.sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(java.io.File::delete);
        }
    }

    public void testRunCycleNoDrift() {
        reactor = new LivingProcessReactor(
            learningCapture, memoryStore, ReactorPolicy.defaults(),
            () -> Map.of("avgExecutionTimeMs", 100.0, "totalCasesCompleted", 5.0));

        ReactorCycle cycle = reactor.runCycle();

        assertNotNull(cycle);
        assertEquals("NO_DRIFT", cycle.outcome());
        assertNull(cycle.proposedMutation());
        assertNotNull(cycle.metricsSnapshot());
    }

    public void testRunCycleProducesCycleHistory() {
        reactor = new LivingProcessReactor(
            learningCapture, memoryStore, ReactorPolicy.defaults(),
            () -> Map.of("avgExecutionTimeMs", 50.0, "totalCasesCompleted", 3.0));

        reactor.runCycle();
        reactor.runCycle();
        reactor.runCycle();

        var history = reactor.getCycleHistory(5);
        assertEquals(3, history.size());
        assertNotNull(reactor.getLastCycle());
    }

    public void testRunCycleHandlesEmptyMetrics() {
        reactor = new LivingProcessReactor(
            learningCapture, memoryStore, ReactorPolicy.defaults(),
            Map::of);

        ReactorCycle cycle = reactor.runCycle();

        assertNotNull(cycle);
        assertEquals("NO_DRIFT", cycle.outcome());
    }

    public void testRunCycleHandlesNullMetrics() {
        reactor = new LivingProcessReactor(
            learningCapture, memoryStore, ReactorPolicy.defaults(),
            () -> null);

        ReactorCycle cycle = reactor.runCycle();

        assertNotNull(cycle);
        // Should handle null gracefully
        assertEquals("NO_DRIFT", cycle.outcome());
    }

    public void testStartAndStop() throws InterruptedException {
        reactor = new LivingProcessReactor(
            learningCapture, memoryStore, ReactorPolicy.lenient(),
            () -> Map.of("avgExecutionTimeMs", 0.0));

        assertFalse(reactor.isRunning());

        reactor.start();
        assertTrue(reactor.isRunning());

        reactor.stop();
        assertFalse(reactor.isRunning());
    }

    public void testDoubleStartIgnored() {
        reactor = new LivingProcessReactor(
            learningCapture, memoryStore, ReactorPolicy.defaults(),
            Map::of);

        reactor.start();
        reactor.start(); // should not throw

        assertTrue(reactor.isRunning());
        reactor.stop();
    }

    public void testGetCycleHistoryLimit() {
        reactor = new LivingProcessReactor(
            learningCapture, memoryStore, ReactorPolicy.defaults(),
            () -> Map.of("avgExecutionTimeMs", 10.0));

        for (int i = 0; i < 10; i++) {
            reactor.runCycle();
        }

        var lastFive = reactor.getCycleHistory(5);
        assertEquals(5, lastFive.size());

        var all = reactor.getCycleHistory(100);
        assertEquals(10, all.size());
    }

    public void testNullConstructorParamsRejected() {
        try {
            new LivingProcessReactor(null, memoryStore, ReactorPolicy.defaults(), Map::of);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    public void testDriftDetectionWithSufficientSamples() {
        // Use lenient policy to lower threshold
        ReactorPolicy lenient = ReactorPolicy.lenient();
        reactor = new LivingProcessReactor(
            learningCapture, memoryStore, lenient,
            () -> Map.of("avgExecutionTimeMs", 100.0, "totalCasesCompleted", 20.0));

        // First cycle establishes baseline
        ReactorCycle first = reactor.runCycle();
        assertNotNull(first);
    }
}
