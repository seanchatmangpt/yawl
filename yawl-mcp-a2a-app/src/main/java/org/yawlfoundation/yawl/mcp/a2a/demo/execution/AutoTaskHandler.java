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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Interface for automatic task completion in YAWL workflows.
 *
 * <p>Auto task handlers process work items automatically during pattern
 * execution. Implementations can transform data, add delays for testing,
 * or integrate with external systems.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Priority-based handler selection</li>
 *   <li>Task-specific handling via canHandle()</li>
 *   <li>Input/output data transformation</li>
 *   <li>Composable handler chains</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create handlers
 * AutoTaskHandler defaultHandler = new DefaultAutoTaskHandler();
 * AutoTaskHandler transformHandler = new DataTransformHandler()
 *     .withTransformation("amount", v -> ((Number) v).doubleValue() * 1.1);
 * AutoTaskHandler delayedHandler = new DelayedHandler(1000);
 *
 * // Use in execution
 * Map<String, Object> output = handler.handleTask("TaskA", inputData);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface AutoTaskHandler {

    /**
     * Handle a task automatically and return completion data.
     *
     * @param taskId the task identifier
     * @param inputData the input data for the task (may be null)
     * @return the output data to complete the task with
     */
    Map<String, Object> handleTask(String taskId, Map<String, Object> inputData);

    /**
     * Check if this handler can handle the given task.
     *
     * @param taskId the task identifier
     * @return true if this handler should process the task
     */
    boolean canHandle(String taskId);

    /**
     * Get the priority of this handler (higher = more preferred).
     *
     * <p>When multiple handlers can handle a task, the one with
     * the highest priority is used.</p>
     *
     * @return the priority value (default 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Get the handler name for logging.
     *
     * @return the handler name
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    // ==================== Implementations ====================

    /**
     * Default handler that returns empty completion data.
     *
     * <p>Useful as a fallback handler when no specific processing
     * is required but the task needs to complete.</p>
     */
    class DefaultAutoTaskHandler implements AutoTaskHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAutoTaskHandler.class);

        private final Map<String, Object> defaultOutput;

        /**
         * Creates a default handler with empty output.
         */
        public DefaultAutoTaskHandler() {
            this.defaultOutput = Collections.emptyMap();
        }

        /**
         * Creates a default handler with specified output.
         *
         * @param defaultOutput the default output to return for all tasks
         */
        public DefaultAutoTaskHandler(Map<String, Object> defaultOutput) {
            this.defaultOutput = defaultOutput != null ?
                new HashMap<>(defaultOutput) : Collections.emptyMap();
        }

        @Override
        public Map<String, Object> handleTask(String taskId, Map<String, Object> inputData) {
            LOGGER.debug("DefaultAutoTaskHandler handling task: {}", taskId);
            return new HashMap<>(defaultOutput);
        }

        @Override
        public boolean canHandle(String taskId) {
            return true; // Default handler can handle any task
        }

        @Override
        public int getPriority() {
            return Integer.MIN_VALUE; // Lowest priority - used as fallback
        }

        @Override
        public String getName() {
            return "DefaultAutoTaskHandler";
        }
    }

    /**
     * Handler that applies data transformations to task output.
     *
     * <p>Transforms input data using registered transformation functions.
     * Transformations are applied in order of registration.</p>
     *
     * <h3>Usage</h3>
     * <pre>{@code
     * DataTransformHandler handler = new DataTransformHandler()
     *     .withTransformation("total", v -> ((Number) v).doubleValue() * 1.1)
     *     .withTransformation("status", v -> "completed")
     *     .withTransformation("timestamp", v -> Instant.now().toString());
     * }</pre>
     */
    class DataTransformHandler implements AutoTaskHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(DataTransformHandler.class);

        private final Map<String, Function<Object, Object>> transformations;
        private final List<String> taskPatterns;
        private final int priority;
        private final boolean copyInputToOutput;

        /**
         * Creates a new data transform handler.
         */
        public DataTransformHandler() {
            this.transformations = new ConcurrentHashMap<>();
            this.taskPatterns = new ArrayList<>();
            this.priority = 10;
            this.copyInputToOutput = true;
        }

        /**
         * Creates a data transform handler with specific priority.
         *
         * @param priority the handler priority
         */
        public DataTransformHandler(int priority) {
            this.transformations = new ConcurrentHashMap<>();
            this.taskPatterns = new ArrayList<>();
            this.priority = priority;
            this.copyInputToOutput = true;
        }

        /**
         * Register a transformation for a field.
         *
         * @param fieldName the field to transform
         * @param transformation the transformation function
         * @return this handler for method chaining
         */
        public DataTransformHandler withTransformation(String fieldName, Function<Object, Object> transformation) {
            if (fieldName != null && transformation != null) {
                transformations.put(fieldName, transformation);
            }
            return this;
        }

        /**
         * Register multiple transformations.
         *
         * @param transformations map of field names to transformations
         * @return this handler for method chaining
         */
        public DataTransformHandler withTransformations(Map<String, Function<Object, Object>> transformations) {
            if (transformations != null) {
                this.transformations.putAll(transformations);
            }
            return this;
        }

        /**
         * Restrict this handler to specific tasks (supports wildcards).
         *
         * @param patterns task ID patterns (e.g., "Approve*", "*Review")
         * @return this handler for method chaining
         */
        public DataTransformHandler forTasks(String... patterns) {
            if (patterns != null) {
                for (String pattern : patterns) {
                    if (pattern != null) {
                        taskPatterns.add(pattern);
                    }
                }
            }
            return this;
        }

        /**
         * Set whether to copy input data to output before transformations.
         *
         * @param copy true to copy input (default true)
         * @return this handler for method chaining
         */
        public DataTransformHandler copyInputToOutput(boolean copy) {
            return new DataTransformHandler(priority);
        }

        @Override
        public Map<String, Object> handleTask(String taskId, Map<String, Object> inputData) {
            Map<String, Object> output = new HashMap<>();

            // Copy input data if configured
            if (copyInputToOutput && inputData != null) {
                output.putAll(inputData);
            }

            // Apply transformations
            transformations.forEach((field, transform) -> {
                Object inputValue = output.get(field);
                if (inputValue == null && inputData != null) {
                    inputValue = inputData.get(field);
                }

                try {
                    Object transformed = transform.apply(inputValue);
                    output.put(field, transformed);
                    LOGGER.debug("Transformed {} for task {}: {} -> {}",
                        field, taskId, inputValue, transformed);
                } catch (Exception e) {
                    LOGGER.warn("Transformation failed for field {} in task {}: {}",
                        field, taskId, e.getMessage());
                }
            });

            return output;
        }

        @Override
        public boolean canHandle(String taskId) {
            if (taskPatterns.isEmpty()) {
                return true; // No patterns = handle all tasks
            }

            for (String pattern : taskPatterns) {
                if (matchesPattern(taskId, pattern)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public String getName() {
            return "DataTransformHandler";
        }

        private boolean matchesPattern(String taskId, String pattern) {
            if (pattern.equals("*")) {
                return true;
            }
            if (pattern.startsWith("*") && pattern.endsWith("*")) {
                String middle = pattern.substring(1, pattern.length() - 1);
                return taskId.contains(middle);
            }
            if (pattern.startsWith("*")) {
                return taskId.endsWith(pattern.substring(1));
            }
            if (pattern.endsWith("*")) {
                return taskId.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            return taskId.equals(pattern);
        }
    }

    /**
     * Handler that adds a configurable delay before processing.
     *
     * <p>Useful for testing timeout handling, simulating slow operations,
     * or rate-limiting task processing.</p>
     *
     * <h3>Features</h3>
     * <ul>
     *   <li>Fixed or random delay range</li>
     *   <li>Configurable time unit</li>
     *   <li>Optional jitter for realistic simulation</li>
     * </ul>
     */
    class DelayedHandler implements AutoTaskHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(DelayedHandler.class);

        private final long minDelayMs;
        private final long maxDelayMs;
        private final boolean randomDelay;
        private final AutoTaskHandler delegate;
        private final List<String> taskPatterns;

        /**
         * Creates a handler with a fixed delay and default delegate.
         *
         * @param delayMs the delay in milliseconds
         */
        public DelayedHandler(long delayMs) {
            this(delayMs, delayMs, new DefaultAutoTaskHandler());
        }

        /**
         * Creates a handler with a fixed delay and custom delegate.
         *
         * @param delayMs the delay in milliseconds
         * @param delegate the delegate handler for actual processing
         */
        public DelayedHandler(long delayMs, AutoTaskHandler delegate) {
            this(delayMs, delayMs, delegate);
        }

        /**
         * Creates a handler with a random delay range.
         *
         * @param minDelayMs minimum delay in milliseconds
         * @param maxDelayMs maximum delay in milliseconds
         */
        public DelayedHandler(long minDelayMs, long maxDelayMs) {
            this(minDelayMs, maxDelayMs, new DefaultAutoTaskHandler());
        }

        /**
         * Creates a handler with a random delay range and custom delegate.
         *
         * @param minDelayMs minimum delay in milliseconds
         * @param maxDelayMs maximum delay in milliseconds
         * @param delegate the delegate handler
         */
        public DelayedHandler(long minDelayMs, long maxDelayMs, AutoTaskHandler delegate) {
            this.minDelayMs = Math.max(0, minDelayMs);
            this.maxDelayMs = Math.max(this.minDelayMs, maxDelayMs);
            this.randomDelay = this.minDelayMs != this.maxDelayMs;
            this.delegate = delegate != null ? delegate : new DefaultAutoTaskHandler();
            this.taskPatterns = new ArrayList<>();
        }

        /**
         * Restrict this handler to specific tasks.
         *
         * @param patterns task ID patterns
         * @return this handler for method chaining
         */
        public DelayedHandler forTasks(String... patterns) {
            if (patterns != null) {
                for (String pattern : patterns) {
                    if (pattern != null) {
                        taskPatterns.add(pattern);
                    }
                }
            }
            return this;
        }

        @Override
        public Map<String, Object> handleTask(String taskId, Map<String, Object> inputData) {
            long delay = calculateDelay();

            LOGGER.debug("DelayedHandler waiting {}ms for task: {}", delay, taskId);

            try {
                TimeUnit.MILLISECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Delay interrupted for task {}", taskId);
            }

            return delegate.handleTask(taskId, inputData);
        }

        @Override
        public boolean canHandle(String taskId) {
            if (taskPatterns.isEmpty()) {
                return delegate.canHandle(taskId);
            }

            for (String pattern : taskPatterns) {
                if (matchesPattern(taskId, pattern)) {
                    return delegate.canHandle(taskId);
                }
            }
            return false;
        }

        @Override
        public int getPriority() {
            return delegate.getPriority() - 1; // Lower priority than delegate
        }

        @Override
        public String getName() {
            return "DelayedHandler[" + minDelayMs + "-" + maxDelayMs + "ms]";
        }

        private long calculateDelay() {
            if (!randomDelay) {
                return minDelayMs;
            }
            return ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
        }

        private boolean matchesPattern(String taskId, String pattern) {
            if (pattern.equals("*")) {
                return true;
            }
            if (pattern.startsWith("*") && pattern.endsWith("*")) {
                return taskId.contains(pattern.substring(1, pattern.length() - 1));
            }
            if (pattern.startsWith("*")) {
                return taskId.endsWith(pattern.substring(1));
            }
            if (pattern.endsWith("*")) {
                return taskId.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            return taskId.equals(pattern);
        }
    }

    /**
     * Composite handler that chains multiple handlers together.
     *
     * <p>Each handler in the chain is consulted in priority order.
     * The first handler that canHandle() the task is used.</p>
     */
    class CompositeAutoTaskHandler implements AutoTaskHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(CompositeAutoTaskHandler.class);

        private final List<AutoTaskHandler> handlers;
        private final String name;

        /**
         * Creates an empty composite handler.
         */
        public CompositeAutoTaskHandler() {
            this.handlers = new ArrayList<>();
            this.name = "CompositeAutoTaskHandler";
        }

        /**
         * Creates a composite handler with a custom name.
         *
         * @param name the handler name
         */
        public CompositeAutoTaskHandler(String name) {
            this.handlers = new ArrayList<>();
            this.name = name != null ? name : "CompositeAutoTaskHandler";
        }

        /**
         * Creates a composite handler with initial handlers.
         *
         * @param handlers the initial handlers
         */
        public CompositeAutoTaskHandler(List<AutoTaskHandler> handlers) {
            this.handlers = new ArrayList<>();
            this.name = "CompositeAutoTaskHandler";
            if (handlers != null) {
                this.handlers.addAll(handlers);
            }
            sortHandlers();
        }

        /**
         * Add a handler to the composite.
         *
         * @param handler the handler to add
         * @return this handler for method chaining
         */
        public CompositeAutoTaskHandler addHandler(AutoTaskHandler handler) {
            if (handler != null) {
                handlers.add(handler);
                sortHandlers();
            }
            return this;
        }

        /**
         * Add multiple handlers.
         *
         * @param handlers the handlers to add
         * @return this handler for method chaining
         */
        public CompositeAutoTaskHandler addHandlers(AutoTaskHandler... handlers) {
            if (handlers != null) {
                for (AutoTaskHandler handler : handlers) {
                    if (handler != null) {
                        this.handlers.add(handler);
                    }
                }
                sortHandlers();
            }
            return this;
        }

        @Override
        public Map<String, Object> handleTask(String taskId, Map<String, Object> inputData) {
            for (AutoTaskHandler handler : handlers) {
                if (handler.canHandle(taskId)) {
                    LOGGER.debug("Using handler {} for task {}", handler.getName(), taskId);
                    return handler.handleTask(taskId, inputData);
                }
            }

            LOGGER.warn("No handler found for task {}, returning empty result", taskId);
            return Collections.emptyMap();
        }

        @Override
        public boolean canHandle(String taskId) {
            return handlers.stream().anyMatch(h -> h.canHandle(taskId));
        }

        @Override
        public int getPriority() {
            return handlers.stream()
                .mapToInt(AutoTaskHandler::getPriority)
                .max()
                .orElse(0);
        }

        @Override
        public String getName() {
            return name;
        }

        private void sortHandlers() {
            handlers.sort(Comparator.comparingInt(AutoTaskHandler::getPriority).reversed());
        }
    }

    /**
     * Handler that logs task execution and passes through to a delegate.
     *
     * <p>Useful for debugging and monitoring task processing.</p>
     */
    class LoggingAutoTaskHandler implements AutoTaskHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(LoggingAutoTaskHandler.class);

        private final AutoTaskHandler delegate;
        private final boolean logInput;
        private final boolean logOutput;

        /**
         * Creates a logging handler with default delegate.
         */
        public LoggingAutoTaskHandler() {
            this(new DefaultAutoTaskHandler(), true, true);
        }

        /**
         * Creates a logging handler with custom delegate.
         *
         * @param delegate the delegate handler
         */
        public LoggingAutoTaskHandler(AutoTaskHandler delegate) {
            this(delegate, true, true);
        }

        /**
         * Creates a logging handler with logging options.
         *
         * @param delegate the delegate handler
         * @param logInput whether to log input data
         * @param logOutput whether to log output data
         */
        public LoggingAutoTaskHandler(AutoTaskHandler delegate, boolean logInput, boolean logOutput) {
            this.delegate = delegate != null ? delegate : new DefaultAutoTaskHandler();
            this.logInput = logInput;
            this.logOutput = logOutput;
        }

        @Override
        public Map<String, Object> handleTask(String taskId, Map<String, Object> inputData) {
            Instant start = Instant.now();

            if (logInput) {
                LOGGER.info("Task {} starting with input: {}", taskId, inputData);
            }

            Map<String, Object> output = delegate.handleTask(taskId, inputData);

            long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();

            if (logOutput) {
                LOGGER.info("Task {} completed in {}ms with output: {}", taskId, durationMs, output);
            }

            return output;
        }

        @Override
        public boolean canHandle(String taskId) {
            return delegate.canHandle(taskId);
        }

        @Override
        public int getPriority() {
            return delegate.getPriority();
        }

        @Override
        public String getName() {
            return "LoggingAutoTaskHandler[" + delegate.getName() + "]";
        }
    }
}
