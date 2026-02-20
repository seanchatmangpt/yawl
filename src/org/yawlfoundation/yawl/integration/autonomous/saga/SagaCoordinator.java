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

package org.yawlfoundation.yawl.integration.autonomous.saga;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Saga coordinator for distributed transactions across autonomous agents.
 *
 * <p>Implements the Saga pattern with compensating transactions for
 * multi-agent workflow operations. Supports sequential steps and
 * parallel step groups using Java 25 {@link StructuredTaskScope}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SagaResult result = SagaCoordinator.saga("order-fulfillment")
 *     .step("reserve-inventory", () -> inventoryAgent.reserve(items),
 *           r -> inventoryAgent.release(items))
 *     .step("charge-payment", () -> paymentAgent.charge(amount),
 *           r -> paymentAgent.refund(amount))
 *     .step("ship-order", () -> shippingAgent.ship(order),
 *           r -> shippingAgent.cancelShipment(order))
 *     .build()
 *     .execute();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class SagaCoordinator {

    private static final Logger LOG = LogManager.getLogger(SagaCoordinator.class);

    /**
     * Scoped value carrying the saga correlation ID.
     */
    public static final ScopedValue<String> SAGA_ID = ScopedValue.newInstance();

    /**
     * Result of executing a single saga step.
     *
     * @param stepName the step name
     * @param success  whether the step succeeded
     * @param output   key-value output data
     * @param elapsed  execution duration
     */
    public record StepResult(
        String stepName,
        boolean success,
        Map<String, Object> output,
        Duration elapsed
    ) {
        public StepResult {
            Objects.requireNonNull(stepName);
            output = output != null ? Map.copyOf(output) : Map.of();
        }
    }

    /**
     * Saga state machine.
     */
    public sealed interface SagaState {
        record Running(int completedSteps, int totalSteps) implements SagaState {}
        record Compensating(int compensatedSteps, String failureReason) implements SagaState {}
        record Completed(Duration totalDuration, List<StepResult> results) implements SagaState {}
        record Failed(String reason, List<StepResult> results, List<String> compensationErrors) implements SagaState {}
    }

    /**
     * Aggregate saga result.
     *
     * @param sagaId   unique saga identifier
     * @param state    final state
     * @param results  results of all steps attempted
     * @param elapsed  total elapsed time
     */
    public record SagaResult(
        String sagaId,
        SagaState state,
        List<StepResult> results,
        Duration elapsed
    ) {
        public boolean succeeded() {
            return state instanceof SagaState.Completed;
        }
    }

    /**
     * A saga step definition.
     */
    private record SagaStep(
        String name,
        Callable<Map<String, Object>> action,
        Consumer<StepResult> compensation,
        Duration timeout
    ) {}

    /**
     * A group of saga steps to execute in parallel.
     */
    private record ParallelGroup(List<SagaStep> steps) {}

    /**
     * An entry in the saga plan: either a single step or a parallel group.
     */
    private sealed interface SagaEntry {
        record Single(SagaStep step) implements SagaEntry {}
        record Parallel(ParallelGroup group) implements SagaEntry {}
    }

    private final String sagaName;
    private final List<SagaEntry> entries;
    private final Duration defaultTimeout;

    private SagaCoordinator(String sagaName, List<SagaEntry> entries, Duration defaultTimeout) {
        this.sagaName = sagaName;
        this.entries = List.copyOf(entries);
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Creates a saga builder.
     *
     * @param sagaName descriptive name for the saga
     * @return a new builder
     */
    public static Builder saga(String sagaName) {
        return new Builder(sagaName);
    }

    /**
     * Executes the saga with compensation on failure.
     *
     * @return the saga result
     */
    public SagaResult execute() {
        String sagaId = UUID.randomUUID().toString().substring(0, 12);
        Instant start = Instant.now();

        LOG.info("Starting saga '{}' [{}] with {} entries", sagaName, sagaId, entries.size());

        return ScopedValue
            .where(SAGA_ID, sagaId)
            .call(() -> executeInternal(sagaId, start));
    }

    private SagaResult executeInternal(String sagaId, Instant start) throws Exception {
        List<StepResult> results = new ArrayList<>();
        List<SagaStep> completedSteps = new ArrayList<>(); // For compensation

        for (SagaEntry entry : entries) {
            switch (entry) {
                case SagaEntry.Single(SagaStep step) -> {
                    StepResult result = executeStep(step);
                    results.add(result);
                    if (result.success()) {
                        completedSteps.add(step);
                    } else {
                        return compensateAndReturn(sagaId, start, results, completedSteps,
                            "Step '" + step.name() + "' failed");
                    }
                }
                case SagaEntry.Parallel(ParallelGroup group) -> {
                    List<StepResult> groupResults = executeParallel(group);
                    results.addAll(groupResults);
                    boolean allSuccess = groupResults.stream().allMatch(StepResult::success);
                    if (allSuccess) {
                        completedSteps.addAll(group.steps());
                    } else {
                        return compensateAndReturn(sagaId, start, results, completedSteps,
                            "Parallel group failed");
                    }
                }
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        LOG.info("Saga '{}' [{}] completed successfully in {}", sagaName, sagaId, elapsed);
        return new SagaResult(sagaId, new SagaState.Completed(elapsed, List.copyOf(results)),
                              List.copyOf(results), elapsed);
    }

    private StepResult executeStep(SagaStep step) {
        Instant stepStart = Instant.now();
        try {
            Map<String, Object> output = step.action().call();
            Duration elapsed = Duration.between(stepStart, Instant.now());
            LOG.debug("Step '{}' succeeded in {}", step.name(), elapsed);
            return new StepResult(step.name(), true, output, elapsed);
        } catch (Exception e) {
            Duration elapsed = Duration.between(stepStart, Instant.now());
            LOG.warn("Step '{}' failed: {}", step.name(), e.getMessage());
            return new StepResult(step.name(), false, Map.of("error", e.getMessage()), elapsed);
        }
    }

    private List<StepResult> executeParallel(ParallelGroup group) {
        List<StepResult> results = new ArrayList<>(group.steps().size());

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<StepResult>> subtasks = new ArrayList<>();

            for (SagaStep step : group.steps()) {
                subtasks.add(scope.fork(() -> executeStep(step)));
            }

            scope.joinUntil(Instant.now().plus(defaultTimeout));

            for (StructuredTaskScope.Subtask<StepResult> subtask : subtasks) {
                if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                    results.add(subtask.get());
                } else {
                    results.add(new StepResult("parallel-step", false,
                        Map.of("error", "Scope cancelled"), Duration.ZERO));
                }
            }
        } catch (Exception e) {
            LOG.warn("Parallel group execution error: {}", e.getMessage());
            for (SagaStep step : group.steps()) {
                boolean alreadyRecorded = results.stream()
                    .anyMatch(r -> r.stepName().equals(step.name()));
                if (!alreadyRecorded) {
                    results.add(new StepResult(step.name(), false,
                        Map.of("error", e.getMessage()), Duration.ZERO));
                }
            }
        }

        return results;
    }

    private SagaResult compensateAndReturn(String sagaId, Instant start,
            List<StepResult> results, List<SagaStep> completed, String reason) {

        LOG.warn("Saga '{}' [{}] compensating: {}", sagaName, sagaId, reason);

        List<String> compensationErrors = new ArrayList<>();
        List<SagaStep> reversed = new ArrayList<>(completed);
        Collections.reverse(reversed);

        for (SagaStep step : reversed) {
            if (step.compensation() != null) {
                try {
                    StepResult stepResult = results.stream()
                        .filter(r -> r.stepName().equals(step.name()))
                        .findFirst()
                        .orElse(new StepResult(step.name(), false, Map.of(), Duration.ZERO));
                    step.compensation().accept(stepResult);
                    LOG.debug("Compensated step '{}'", step.name());
                } catch (Exception e) {
                    String err = "Compensation failed for '" + step.name() + "': " + e.getMessage();
                    compensationErrors.add(err);
                    LOG.error(err, e);
                }
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        SagaState finalState = compensationErrors.isEmpty()
            ? new SagaState.Completed(elapsed, List.copyOf(results))
            : new SagaState.Failed(reason, List.copyOf(results), List.copyOf(compensationErrors));

        return new SagaResult(sagaId, finalState, List.copyOf(results), elapsed);
    }

    /**
     * Fluent builder for saga definitions.
     */
    public static final class Builder {
        private final String sagaName;
        private final List<SagaEntry> entries = new ArrayList<>();
        private Duration defaultTimeout = Duration.ofMinutes(5);

        private Builder(String sagaName) {
            this.sagaName = Objects.requireNonNull(sagaName);
        }

        /**
         * Adds a sequential step with compensation.
         */
        public Builder step(String name, Callable<Map<String, Object>> action,
                           Consumer<StepResult> compensation) {
            entries.add(new SagaEntry.Single(
                new SagaStep(name, action, compensation, defaultTimeout)));
            return this;
        }

        /**
         * Adds a sequential step without compensation.
         */
        public Builder step(String name, Callable<Map<String, Object>> action) {
            return step(name, action, null);
        }

        /**
         * Adds a group of steps to execute in parallel.
         */
        public Builder parallelSteps(SagaStep... steps) {
            entries.add(new SagaEntry.Parallel(new ParallelGroup(List.of(steps))));
            return this;
        }

        /**
         * Sets the default timeout for all steps.
         */
        public Builder withTimeout(Duration timeout) {
            this.defaultTimeout = Objects.requireNonNull(timeout);
            return this;
        }

        /**
         * Builds the saga coordinator.
         */
        public SagaCoordinator build() {
            if (entries.isEmpty()) {
                throw new IllegalStateException("Saga must have at least one step");
            }
            return new SagaCoordinator(sagaName, entries, defaultTimeout);
        }
    }
}
