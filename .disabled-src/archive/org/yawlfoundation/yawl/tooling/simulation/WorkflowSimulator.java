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

package org.yawlfoundation.yawl.tooling.simulation;

import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;
import org.yawlfoundation.yawl.stateless.unmarshal.YMarshal;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives a step-by-step token-firing simulation of a YAWL specification
 * using the {@link YStatelessEngine}.
 *
 * The simulator:
 * <ol>
 *   <li>Parses the specification XML using {@link YMarshal}</li>
 *   <li>Starts a single case via {@link YStatelessEngine#launchCase(YSpecification)},
 *       which returns a {@link YNetRunner}</li>
 *   <li>Iterates: obtains enabled work items from the net runner's repository,
 *       starts each item, then completes it with empty output data</li>
 *   <li>Stops when no more enabled or executing items remain, or max steps exceeded</li>
 * </ol>
 *
 * All enabled work items are completed with empty output data (the simulator
 * is designed for control-flow tracing, not data-driven execution).
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class WorkflowSimulator {

    private final PrintStream out;
    private final PrintStream err;

    public WorkflowSimulator(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /**
     * Run the simulation.
     *
     * @param specXml  the YAWL specification XML (stateless engine format)
     * @param maxSteps maximum number of work item firings before aborting
     * @param trace    if true, print task details after each firing
     * @param initData optional initial case parameter XML (may be null)
     * @return a {@link SimulationResult} describing what happened
     */
    public SimulationResult simulate(String specXml, int maxSteps, boolean trace, String initData) {
        YStatelessEngine engine = new YStatelessEngine();

        SimulationTracker tracker = new SimulationTracker(out, trace);
        engine.addWorkItemEventListener(tracker);
        engine.addCaseEventListener(tracker);

        // Parse specification
        List<YSpecification> specs;
        try {
            specs = YMarshal.unmarshalSpecifications(specXml);
        } catch (Exception e) {
            return new SimulationResult(Outcome.ERROR, 0, 0, 0, e.getMessage());
        }

        if (specs.isEmpty()) {
            return new SimulationResult(Outcome.ERROR, 0, 0, 0, "No specifications found in XML.");
        }

        YSpecification spec = specs.get(0);
        out.println("[simulate] Loaded spec: " + spec.getURI() + " v" + spec.getSpecVersion());

        // Start case — returns the root YNetRunner
        YNetRunner runner;
        try {
            runner = engine.launchCase(spec, null, initData);
            out.println("[simulate] Case started: " + runner.getCaseID());
        } catch (Exception e) {
            return new SimulationResult(Outcome.ERROR, 0, 0, 0,
                    "Failed to start case: " + e.getMessage());
        }

        // Main simulation loop: iterate over all runners in the case tree
        int steps = 0;
        while (steps < maxSteps) {
            // Collect enabled work items from the entire case tree
            Set<YWorkItem> enabledItems = runner.getWorkItemRepository().getEnabledWorkItems();

            if (enabledItems.isEmpty()) {
                // Check executing items
                Set<YWorkItem> executingItems = runner.getWorkItemRepository().getExecutingWorkItems();
                if (executingItems.isEmpty()) {
                    out.println("[simulate] No enabled or executing items at step " + steps +
                                " — workflow complete or deadlocked.");
                    break;
                }
                // Complete any executing item to advance
                YWorkItem execItem = executingItems.iterator().next();
                steps++;
                if (trace) {
                    out.printf("[simulate] Step %d: Completing executing task '%s'%n",
                            steps, execItem.getTaskID());
                }
                try {
                    engine.completeWorkItem(execItem, buildOutputData(execItem), null);
                } catch (Exception e) {
                    return new SimulationResult(Outcome.ERROR, steps,
                            tracker.tasksCompleted.get(), tracker.casesCompleted.get(),
                            "Error completing task " + execItem.getTaskID() + ": " + e.getMessage());
                }
                continue;
            }

            // Start and complete the first enabled item
            YWorkItem item = enabledItems.iterator().next();
            steps++;

            if (trace) {
                out.printf("[simulate] Step %d: Firing task '%s' (case: %s)%n",
                        steps, item.getTaskID(), item.getCaseID());
            }

            try {
                YWorkItem startedItem = engine.startWorkItem(item);
                engine.completeWorkItem(startedItem, buildOutputData(startedItem), null);
            } catch (Exception e) {
                return new SimulationResult(Outcome.ERROR, steps,
                        tracker.tasksCompleted.get(), tracker.casesCompleted.get(),
                        "Error at step " + steps + " for task " + item.getTaskID() + ": " + e.getMessage());
            }
        }

        if (tracker.casesCompleted.get() > 0) {
            return new SimulationResult(Outcome.COMPLETED, steps,
                    tracker.tasksCompleted.get(), tracker.casesCompleted.get(), null);
        }
        if (steps >= maxSteps) {
            return new SimulationResult(Outcome.MAX_EXCEEDED, steps,
                    tracker.tasksCompleted.get(), tracker.casesCompleted.get(), null);
        }
        // All items drained — treat as completed
        return new SimulationResult(Outcome.COMPLETED, steps,
                tracker.tasksCompleted.get(), tracker.casesCompleted.get(), null);
    }

    /**
     * Build minimal empty output data XML for a work item so the engine accepts it.
     * Uses the task ID as the root element name per YAWL data conventions.
     */
    private String buildOutputData(YWorkItem item) {
        return "<" + item.getTaskID() + "/>";
    }

    // ---- Inner types ----------------------------------------------------------

    /** Possible simulation outcomes. */
    public enum Outcome {
        COMPLETED,
        MAX_EXCEEDED,
        ERROR
    }

    /**
     * Immutable result record for a simulation run.
     */
    public record SimulationResult(
            Outcome outcome,
            int stepsExecuted,
            int tasksCompleted,
            int casesCompleted,
            String errorMessage
    ) { }

    /**
     * Listens to engine events and tracks simulation metrics.
     */
    private static final class SimulationTracker
            implements YWorkItemEventListener, YCaseEventListener {

        private final PrintStream out;
        private final boolean trace;
        final AtomicInteger tasksCompleted  = new AtomicInteger(0);
        final AtomicInteger casesCompleted  = new AtomicInteger(0);

        SimulationTracker(PrintStream out, boolean trace) {
            this.out   = out;
            this.trace = trace;
        }

        @Override
        public void handleWorkItemEvent(YWorkItemEvent event) {
            if (event.getEventType() == YEventType.ITEM_COMPLETED) {
                int n = tasksCompleted.incrementAndGet();
                if (trace) {
                    out.printf("[simulate]   -> Task completed: %s (total: %d)%n",
                            event.getWorkItem().getTaskID(), n);
                }
            }
        }

        @Override
        public void handleCaseEvent(YCaseEvent event) {
            if (event.getEventType() == YEventType.CASE_COMPLETED) {
                int n = casesCompleted.incrementAndGet();
                out.printf("[simulate] Case COMPLETED: %s (total cases done: %d)%n",
                        event.getCaseID(), n);
            } else if (event.getEventType() == YEventType.CASE_STARTED) {
                out.printf("[simulate] Case STARTED: %s%n", event.getCaseID());
            }
        }
    }
}
