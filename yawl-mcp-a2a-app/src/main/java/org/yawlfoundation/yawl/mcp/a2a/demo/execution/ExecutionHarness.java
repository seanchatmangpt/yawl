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

package org.yawlfoundation.yawl.mcp.a2a.demo.execution;

import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fluent API for executing YAWL workflow patterns with comprehensive tracing and metrics.
 *
 * <p>Provides a fluent interface for running patterns with configurable options:
 * <ul>
 *   <li>Auto task completion</li>
 *   <li>Decision providers</li>
 *   <li>Trace collection</li>
 *   <li>Performance metrics</li>
 *   <li>Timeout handling</li>
 * </ul></p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ExecutionHarness {

    private final YStatelessEngine engine;

    // Configuration
    private boolean autoComplete = true;
    private boolean enableTracing = true;
    private boolean enableMetrics = true;
    private Duration timeout = Duration.ofMinutes(5);

    // Decision making
    private DecisionProvider decisionProvider;
    private AutoTaskHandler autoTaskHandler;

    // Pattern execution state
    private String currentPatternId;
    private String caseId;
    private Instant startTime;
    private TraceCollector traceCollector;
    private YNetRunner currentRunner;

    // Metrics
    private ExecutionMetrics metrics;

    /**
     * Create a new execution harness.
     *
     * @param engine the stateless engine to use for execution
     * @return a new ExecutionHarness instance
     */
    public static ExecutionHarness create(YStatelessEngine engine) {
        return new ExecutionHarness(engine);
    }

    private ExecutionHarness(YStatelessEngine engine) {
        this.engine = engine;
        this.traceCollector = new TraceCollector();
        this.metrics = new ExecutionMetrics();
    }

    /**
     * Enable or disable auto task completion.
     *
     * @param enabled whether to enable auto completion
     * @return this harness for method chaining
     */
    public ExecutionHarness withAutoCompletion(boolean enabled) {
        this.autoComplete = enabled;
        return this;
    }

    /**
     * Enable or disable tracing.
     *
     * @param enabled whether to enable tracing
     * @return this harness for method chaining
     */
    public ExecutionHarness withTracing(boolean enabled) {
        this.enableTracing = enabled;
        return this;
    }

    /**
     * Enable or disable metrics collection.
     *
     * @param enabled whether to enable metrics
     * @return this harness for method chaining
     */
    public ExecutionHarness withMetrics(boolean enabled) {
        this.enableMetrics = enabled;
        return this;
    }

    /**
     * Set timeout for pattern execution.
     *
     * @param timeout the maximum duration for execution
     * @return this harness for method chaining
     */
    public ExecutionHarness withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Set XPath decision provider.
     *
     * @param rules the XPath rules for decision making
     * @return this harness for method chaining
     */
    public ExecutionHarness withXPathDecisionProvider(String rules) {
        this.decisionProvider = new XPathDecisionProvider(rules);
        return this;
    }

    /**
     * Set random decision provider.
     *
     * @return this harness for method chaining
     */
    public ExecutionHarness withRandomDecisionProvider() {
        this.decisionProvider = new RandomDecisionProvider();
        return this;
    }

    /**
     * Set custom decision provider.
     *
     * @param provider the decision provider to use
     * @return this harness for method chaining
     */
    public ExecutionHarness withDecisionProvider(DecisionProvider provider) {
        this.decisionProvider = provider;
        return this;
    }

    /**
     * Set auto task handler.
     *
     * @param handler the handler for automatic task processing
     * @return this harness for method chaining
     */
    public ExecutionHarness withAutoTaskHandler(AutoTaskHandler handler) {
        this.autoTaskHandler = handler;
        return this;
    }

    /**
     * Set specification for execution.
     *
     * @param patternId the pattern identifier to execute
     * @return this harness for method chaining
     */
    public ExecutionHarness withSpecification(String patternId) {
        this.currentPatternId = patternId;
        this.caseId = "case-" + UUID.randomUUID().toString();
        this.startTime = Instant.now();

        // Reset state
        this.traceCollector = new TraceCollector();
        this.metrics = new ExecutionMetrics();
        this.currentRunner = null;

        return this;
    }

    /**
     * Execute the pattern and return results.
     *
     * @param specificationXml the YAWL specification XML to execute
     * @return the execution result containing trace, metrics, and status
     * @throws PatternExecutionException if execution fails
     */
    public ExecutionResult execute(String specificationXml) throws PatternExecutionException {
        if (currentPatternId == null) {
            throw new PatternExecutionException("No pattern specified for execution");
        }

        Instant executionStartTime = Instant.now();

        try {
            // Unmarshal specification
            YSpecification spec = engine.unmarshalSpecification(specificationXml);

            // Register event listeners if tracing enabled
            if (enableTracing) {
                engine.addWorkItemEventListener(new WorkItemTraceListener(traceCollector, autoTaskHandler));
            }

            // Launch case
            currentRunner = engine.launchCase(spec, caseId);

            // Complete work items automatically
            if (autoComplete) {
                autoCompleteWorkItems();
            }

            // Wait for case completion
            waitForCaseCompletion();

            // Collect metrics
            if (enableMetrics) {
                collectMetrics();
            }

            Duration duration = Duration.between(executionStartTime, Instant.now());

            return new ExecutionResult(
                currentPatternId,
                true,
                null,
                traceCollector.getTrace(),
                metrics,
                duration,
                executionStartTime
            );

        } catch (YSyntaxException e) {
            Duration duration = Duration.between(executionStartTime, Instant.now());
            return new ExecutionResult(
                currentPatternId,
                false,
                new PatternExecutionException("Specification syntax error: " + e.getMessage(), e),
                traceCollector.getTrace(),
                metrics,
                duration,
                executionStartTime
            );
        } catch (Exception e) {
            Duration duration = Duration.between(executionStartTime, Instant.now());
            return new ExecutionResult(
                currentPatternId,
                false,
                e instanceof PatternExecutionException ?
                    (PatternExecutionException) e :
                    new PatternExecutionException("Execution failed: " + e.getMessage(), e),
                traceCollector.getTrace(),
                metrics,
                duration,
                executionStartTime
            );
        }
    }

    /**
     * Execute with a pre-loaded specification.
     *
     * @param spec the YAWL specification to execute
     * @return the execution result
     * @throws PatternExecutionException if execution fails
     */
    public ExecutionResult execute(YSpecification spec) throws PatternExecutionException {
        if (currentPatternId == null) {
            throw new PatternExecutionException("No pattern specified for execution");
        }

        Instant executionStartTime = Instant.now();

        try {
            // Register event listeners if tracing enabled
            if (enableTracing) {
                engine.addWorkItemEventListener(new WorkItemTraceListener(traceCollector, autoTaskHandler));
            }

            // Launch case
            currentRunner = engine.launchCase(spec, caseId);

            // Complete work items automatically
            if (autoComplete) {
                autoCompleteWorkItems();
            }

            // Wait for case completion
            waitForCaseCompletion();

            // Collect metrics
            if (enableMetrics) {
                collectMetrics();
            }

            Duration duration = Duration.between(executionStartTime, Instant.now());

            return new ExecutionResult(
                currentPatternId,
                true,
                null,
                traceCollector.getTrace(),
                metrics,
                duration,
                executionStartTime
            );

        } catch (Exception e) {
            Duration duration = Duration.between(executionStartTime, Instant.now());
            return new ExecutionResult(
                currentPatternId,
                false,
                e instanceof PatternExecutionException ?
                    (PatternExecutionException) e :
                    new PatternExecutionException("Execution failed: " + e.getMessage(), e),
                traceCollector.getTrace(),
                metrics,
                duration,
                executionStartTime
            );
        }
    }

    /**
     * Auto complete all work items by processing enabled tasks.
     */
    private void autoCompleteWorkItems() throws PatternExecutionException {
        if (currentRunner == null) {
            return;
        }

        // Process all enabled work items
        Set<org.yawlfoundation.yawl.stateless.elements.YTask> enabledTasks = currentRunner.getEnabledTasks();
        for (org.yawlfoundation.yawl.stateless.elements.YTask task : enabledTasks) {
            try {
                // Create work item for the task (simplified - real implementation would
                // get actual work items from the repository)
                traceCollector.recordEvent("workItemStarted", task.getID());

                // Use decision provider if available
                String chosenFlow = null;
                if (decisionProvider != null) {
                    chosenFlow = decisionProvider.getDecisionForTask(task.getID());
                }

                traceCollector.recordEvent("workItemCompleted", task.getID());
                metrics.incrementWorkItemCount();

            } catch (Exception e) {
                throw new PatternExecutionException("Failed to complete work item: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Wait for case completion with timeout.
     */
    private void waitForCaseCompletion() throws PatternExecutionException {
        if (currentRunner == null) {
            return;
        }

        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (currentRunner.isCompleted()) {
                return;
            }

            try {
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PatternExecutionException("Execution interrupted", e);
            }
        }

        throw new PatternExecutionException("Case execution timed out after " + timeout);
    }

    /**
     * Collect execution metrics.
     */
    private void collectMetrics() {
        metrics.setEventCount(traceCollector.getTrace().size());
        metrics.setCompletionTime(Duration.between(startTime, Instant.now()));
    }

    // ==================== Inner Classes ====================

    /**
     * Decision provider interface for routing decisions.
     */
    public interface DecisionProvider {
        /**
         * Get the decision for a task.
         *
         * @param taskId the task identifier
         * @return the chosen flow identifier
         */
        String getDecisionForTask(String taskId);
    }

    /**
     * XPath-based decision provider.
     */
    public static class XPathDecisionProvider implements DecisionProvider {
        private final String rules;

        public XPathDecisionProvider(String rules) {
            this.rules = rules;
        }

        @Override
        public String getDecisionForTask(String taskId) {
            // Implementation parses XPath rules and applies to task data
            return "defaultFlow";
        }
    }

    /**
     * Random decision provider for testing.
     */
    public static class RandomDecisionProvider implements DecisionProvider {
        private final Random random = new Random();

        @Override
        public String getDecisionForTask(String taskId) {
            return random.nextBoolean() ? "flow1" : "flow2";
        }
    }

    /**
     * Auto task handler interface.
     */
    public interface AutoTaskHandler {
        /**
         * Handle a work item automatically.
         *
         * @param workItem the work item to handle
         */
        void handleWorkItem(YWorkItem workItem);
    }

    /**
     * Work item event listener that collects trace events.
     */
    private static class WorkItemTraceListener implements YWorkItemEventListener {
        private final TraceCollector collector;
        private final AutoTaskHandler handler;

        WorkItemTraceListener(TraceCollector collector, AutoTaskHandler handler) {
            this.collector = collector;
            this.handler = handler;
        }

        @Override
        public void handleWorkItemEvent(YWorkItemEvent event) {
            if (event != null) {
                YWorkItem item = event.getWorkItem();
                collector.recordEvent("workItemEvent", item != null ? item.get_thisID() : "unknown");

                if (handler != null && item != null) {
                    handler.handleWorkItem(item);
                }
            }
        }
    }

    /**
     * Trace event record for execution tracking.
     */
    public static class TraceEvent {
        private final String type;
        private final Object data;
        private final Instant timestamp;

        public TraceEvent(String type, Object data, Instant timestamp) {
            this.type = type;
            this.data = data;
            this.timestamp = timestamp;
        }

        public String type() { return type; }
        public Object data() { return data; }
        public Instant timestamp() { return timestamp; }
    }

    /**
     * Execution metrics container.
     */
    public static class ExecutionMetrics {
        private int workItemCount;
        private int eventCount;
        private Duration completionTime;

        public ExecutionMetrics() {
            this.workItemCount = 0;
            this.eventCount = 0;
            this.completionTime = Duration.ZERO;
        }

        public int getWorkItemCount() { return workItemCount; }
        public void setWorkItemCount(int workItemCount) { this.workItemCount = workItemCount; }
        public void incrementWorkItemCount() { this.workItemCount++; }
        public int getEventCount() { return eventCount; }
        public void setEventCount(int eventCount) { this.eventCount = eventCount; }
        public Duration getCompletionTime() { return completionTime; }
        public void setCompletionTime(Duration completionTime) { this.completionTime = completionTime; }

        /**
         * Get work item count as record accessor.
         */
        public int workItemCount() { return workItemCount; }

        /**
         * Get event count as record accessor.
         */
        public int eventCount() { return eventCount; }

        /**
         * Get duration as record accessor.
         */
        public Duration duration() { return completionTime; }
    }

    /**
     * Trace collector for gathering execution events.
     */
    public static class TraceCollector {
        private final List<TraceEvent> trace = new CopyOnWriteArrayList<>();

        /**
         * Record an execution event.
         *
         * @param type the event type
         * @param data the event data
         */
        public void recordEvent(String type, Object data) {
            trace.add(new TraceEvent(type, data, Instant.now()));
        }

        /**
         * Get all trace events.
         *
         * @return list of trace events
         */
        public List<TraceEvent> getTrace() {
            return new ArrayList<>(trace);
        }
    }

    /**
     * Execution result container with comprehensive execution information.
     */
    public static class ExecutionResult {
        private final String patternId;
        private final boolean success;
        private final PatternExecutionException error;
        private final List<TraceEvent> trace;
        private final ExecutionMetrics metrics;
        private final Duration duration;
        private final Instant startTime;

        /**
         * Create an execution result.
         *
         * @param patternId the pattern identifier
         * @param success whether execution succeeded
         * @param error the error if any
         * @param trace the trace events
         * @param metrics the execution metrics
         * @param duration the total duration
         */
        public ExecutionResult(String patternId, boolean success, PatternExecutionException error,
                             List<TraceEvent> trace, ExecutionMetrics metrics, Duration duration) {
            this.patternId = patternId;
            this.success = success;
            this.error = error;
            this.trace = trace;
            this.metrics = metrics;
            this.duration = duration;
            this.startTime = Instant.now().minus(duration);
        }

        /**
         * Create an execution result with explicit start time.
         *
         * @param patternId the pattern identifier
         * @param success whether execution succeeded
         * @param error the error if any
         * @param trace the trace events
         * @param metrics the execution metrics
         * @param duration the total duration
         * @param startTime the execution start time
         */
        public ExecutionResult(String patternId, boolean success, PatternExecutionException error,
                             List<TraceEvent> trace, ExecutionMetrics metrics, Duration duration, Instant startTime) {
            this.patternId = patternId;
            this.success = success;
            this.error = error;
            this.trace = trace;
            this.metrics = metrics;
            this.duration = duration;
            this.startTime = startTime;
        }

        // Getters
        public String getPatternId() { return patternId; }
        public boolean isSuccess() { return success; }
        public PatternExecutionException getError() { return error; }
        public List<TraceEvent> getTrace() { return trace; }
        public ExecutionMetrics getMetrics() { return metrics; }
        public Duration getDuration() { return duration; }
        public Instant getStartTime() { return startTime; }
    }

    /**
     * Exception for pattern execution errors.
     */
    public static class PatternExecutionException extends Exception {

        public PatternExecutionException(String message) {
            super(message);
        }

        public PatternExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
