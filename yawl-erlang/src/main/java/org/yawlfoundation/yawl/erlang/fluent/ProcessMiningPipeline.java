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
package org.yawlfoundation.yawl.erlang.fluent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fluent API for building supervised process mining pipelines combining
 * OTP-inspired supervision, circuit breaker resilience, and composable
 * mining stages.
 *
 * <p>Pipelines execute named stages sequentially. Each stage receives a
 * {@link PipelineContext} for reading prior stage outputs and produces
 * a result that is stored in the context for downstream stages.
 *
 * <h3>Supervision</h3>
 * <p>When supervision is configured, failed stages are automatically retried
 * according to the {@link RestartStrategy}:
 * <ul>
 *   <li>{@link RestartStrategy#ONE_FOR_ONE} — retry only the failed stage</li>
 *   <li>{@link RestartStrategy#ONE_FOR_ALL} — clear context and re-run all stages</li>
 *   <li>{@link RestartStrategy#REST_FOR_ONE} — re-run the failed stage and all subsequent</li>
 * </ul>
 *
 * <h3>Circuit Breaker</h3>
 * <p>Per-stage circuit breakers track consecutive failures. After
 * {@code failureThreshold} consecutive failures, the circuit opens and
 * the stage fails immediately until {@code resetTimeout} elapses.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PipelineResult result = ProcessMiningPipeline.builder()
 *     .supervision(sup -> sup
 *         .strategy(RestartStrategy.ONE_FOR_ONE)
 *         .maxRestarts(3)
 *         .window(Duration.ofMinutes(5)))
 *     .circuitBreaker(cb -> cb
 *         .failureThreshold(5)
 *         .resetTimeout(Duration.ofSeconds(60)))
 *     .onEvent(event -> logger.info("{}", event))
 *     .stage("parse", ctx -> engine.parseOcel2Json(json))
 *     .stage("discover", ctx -> {
 *         var log = ctx.get("parse", OcelLogHandle.class);
 *         return engine.discoverDfg(log);
 *     })
 *     .stage("conform", ctx -> {
 *         var log = ctx.get("parse", OcelLogHandle.class);
 *         return engine.checkConformance(log, pnmlXml);
 *     })
 *     .build()
 *     .execute();
 *
 * result.stageOutput("conform", ConformanceReport.class)
 *     .ifPresent(r -> System.out.println("Fitness: " + r.fitness()));
 * }</pre>
 *
 * <p>Pipelines are immutable after construction and may be executed
 * multiple times (each execution creates a fresh context).
 */
public final class ProcessMiningPipeline {

    private static final Logger LOG = Logger.getLogger(ProcessMiningPipeline.class.getName());

    /**
     * A pipeline stage that receives a context and produces a result.
     */
    @FunctionalInterface
    public interface Stage {
        Object execute(PipelineContext context) throws Exception;
    }

    private final SupervisionSpec supervisionSpec;
    private final CircuitBreakerSpec circuitBreakerSpec;
    private final List<Consumer<PipelineEvent>> eventListeners;
    private final LinkedHashMap<String, Stage> stages;

    private ProcessMiningPipeline(
            SupervisionSpec supervisionSpec,
            CircuitBreakerSpec circuitBreakerSpec,
            List<Consumer<PipelineEvent>> eventListeners,
            LinkedHashMap<String, Stage> stages) {
        this.supervisionSpec = supervisionSpec;
        this.circuitBreakerSpec = circuitBreakerSpec;
        this.eventListeners = List.copyOf(eventListeners);
        this.stages = new LinkedHashMap<>(stages);
    }

    /** Returns a new pipeline builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes the pipeline synchronously, returning the aggregate result.
     *
     * <p>Creates a fresh {@link PipelineContext} and executes each stage
     * sequentially. Supervision and circuit breaker logic is applied per stage.
     *
     * @return aggregate result containing all stage results
     */
    public PipelineResult execute() {
        Instant pipelineStart = Instant.now();
        PipelineContext context = new PipelineContext();
        List<StageResult> stageResults = new ArrayList<>();
        AtomicInteger totalRestarts = new AtomicInteger(0);
        Map<String, CircuitState> circuitStates = new ConcurrentHashMap<>();

        List<String> stageNames = new ArrayList<>(stages.keySet());
        int stageIndex = 0;

        while (stageIndex < stageNames.size()) {
            String stageName = stageNames.get(stageIndex);
            Stage stage = stages.get(stageName);

            StageResult result = executeStageWithSupervision(
                    stageName, stage, context, circuitStates, totalRestarts);

            stageResults.add(result);

            if (!result.success()) {
                Duration totalDuration = Duration.between(pipelineStart, Instant.now());
                return new PipelineResult(
                        stageResults, totalDuration, false, totalRestarts.get(), context);
            }

            stageIndex++;
        }

        Duration totalDuration = Duration.between(pipelineStart, Instant.now());
        return new PipelineResult(stageResults, totalDuration, true, totalRestarts.get(), context);
    }

    /**
     * Executes the pipeline asynchronously on a virtual thread.
     *
     * @return CompletableFuture that completes with the pipeline result
     */
    public CompletableFuture<PipelineResult> executeAsync() {
        CompletableFuture<PipelineResult> future = new CompletableFuture<>();
        Thread.ofVirtual()
                .name("pipeline-async")
                .start(() -> {
                    try {
                        future.complete(execute());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
        return future;
    }

    // =========================================================================
    // Stage execution with supervision and circuit breaking
    // =========================================================================

    private StageResult executeStageWithSupervision(
            String stageName,
            Stage stage,
            PipelineContext context,
            Map<String, CircuitState> circuitStates,
            AtomicInteger totalRestarts) {

        int maxAttempts = supervisionSpec != null
                ? supervisionSpec.maxRestarts() + 1
                : 1;

        Instant stageStart = Instant.now();
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;

            if (circuitBreakerSpec != null) {
                CircuitState cs = circuitStates.computeIfAbsent(
                        stageName, _ -> new CircuitState(circuitBreakerSpec));
                if (cs.isOpen()) {
                    emitEvent(new PipelineEvent.CircuitOpened(
                            stageName, Instant.now(), cs.consecutiveFailures()));
                    Duration duration = Duration.between(stageStart, Instant.now());
                    return StageResult.failure(stageName, duration,
                            "Circuit breaker OPEN after " + cs.consecutiveFailures() + " failures",
                            attempt);
                }
            }

            emitEvent(new PipelineEvent.StageStarted(stageName, Instant.now()));

            try {
                Object output = stage.execute(context);
                context.put(stageName, output);

                Duration duration = Duration.between(stageStart, Instant.now());
                emitEvent(new PipelineEvent.StageCompleted(stageName, Instant.now(), duration));

                if (circuitBreakerSpec != null) {
                    circuitStates.computeIfAbsent(
                            stageName, _ -> new CircuitState(circuitBreakerSpec)).recordSuccess();
                }

                return StageResult.success(stageName, output, duration, attempt);
            } catch (Exception e) {
                emitEvent(new PipelineEvent.StageFailed(
                        stageName, Instant.now(), e.getMessage(), attempt));

                if (circuitBreakerSpec != null) {
                    circuitStates.computeIfAbsent(
                            stageName, _ -> new CircuitState(circuitBreakerSpec)).recordFailure();
                }

                if (attempt < maxAttempts) {
                    totalRestarts.incrementAndGet();
                    emitEvent(new PipelineEvent.StageRestarted(
                            stageName, Instant.now(), totalRestarts.get()));

                    applyRestartStrategy(stageName, context);
                } else {
                    Duration duration = Duration.between(stageStart, Instant.now());
                    return StageResult.failure(stageName, duration, e.getMessage(), attempt);
                }
            }
        }

        Duration duration = Duration.between(stageStart, Instant.now());
        return StageResult.failure(stageName, duration, "Max attempts exhausted", attempt);
    }

    private void applyRestartStrategy(String stageName, PipelineContext context) {
        if (supervisionSpec == null) return;

        switch (supervisionSpec.strategy()) {
            case ONE_FOR_ONE -> { /* context unchanged, just retry this stage */ }
            case ONE_FOR_ALL -> context.clear();
            case REST_FOR_ONE -> context.clearFrom(stageName);
        }
    }

    private void emitEvent(PipelineEvent event) {
        for (Consumer<PipelineEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Event listener threw for " + event, e);
            }
        }
    }

    // =========================================================================
    // Per-stage circuit breaker state
    // =========================================================================

    private static final class CircuitState {
        private final CircuitBreakerSpec spec;
        private int consecutiveFailures;
        private Instant lastFailureTime;

        CircuitState(CircuitBreakerSpec spec) {
            this.spec = spec;
        }

        synchronized boolean isOpen() {
            if (consecutiveFailures < spec.failureThreshold()) return false;
            if (lastFailureTime == null) return false;

            Duration elapsed = Duration.between(lastFailureTime, Instant.now());
            if (elapsed.compareTo(spec.resetTimeout()) > 0) {
                consecutiveFailures = 0;
                return false;
            }
            return true;
        }

        synchronized void recordFailure() {
            consecutiveFailures++;
            lastFailureTime = Instant.now();
        }

        synchronized void recordSuccess() {
            consecutiveFailures = 0;
        }

        synchronized int consecutiveFailures() {
            return consecutiveFailures;
        }
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Fluent builder for constructing {@link ProcessMiningPipeline} instances.
     *
     * <p>Stages are executed in the order they are added. Stage names must be unique.
     */
    public static final class Builder {
        private SupervisionSpec supervisionSpec;
        private CircuitBreakerSpec circuitBreakerSpec;
        private final List<Consumer<PipelineEvent>> eventListeners = new ArrayList<>();
        private final LinkedHashMap<String, Stage> stages = new LinkedHashMap<>();

        Builder() {}

        /**
         * Configures OTP-style supervision for failed stages.
         *
         * @param config consumer that configures the supervision spec builder
         * @return this builder
         */
        public Builder supervision(Consumer<SupervisionSpec.Builder> config) {
            SupervisionSpec.Builder supBuilder = SupervisionSpec.builder();
            config.accept(supBuilder);
            this.supervisionSpec = supBuilder.build();
            return this;
        }

        /**
         * Configures supervision with a pre-built spec.
         *
         * @param spec the supervision specification
         * @return this builder
         */
        public Builder supervision(SupervisionSpec spec) {
            this.supervisionSpec = Objects.requireNonNull(spec);
            return this;
        }

        /**
         * Configures per-stage circuit breaker protection.
         *
         * @param config consumer that configures the circuit breaker spec builder
         * @return this builder
         */
        public Builder circuitBreaker(Consumer<CircuitBreakerSpec.Builder> config) {
            CircuitBreakerSpec.Builder cbBuilder = CircuitBreakerSpec.builder();
            config.accept(cbBuilder);
            this.circuitBreakerSpec = cbBuilder.build();
            return this;
        }

        /**
         * Configures circuit breaker with a pre-built spec.
         *
         * @param spec the circuit breaker specification
         * @return this builder
         */
        public Builder circuitBreaker(CircuitBreakerSpec spec) {
            this.circuitBreakerSpec = Objects.requireNonNull(spec);
            return this;
        }

        /**
         * Registers an event listener for pipeline lifecycle events.
         * Multiple listeners can be registered; they are called in order.
         *
         * @param listener consumer of pipeline events
         * @return this builder
         */
        public Builder onEvent(Consumer<PipelineEvent> listener) {
            this.eventListeners.add(Objects.requireNonNull(listener));
            return this;
        }

        /**
         * Adds a named stage to the pipeline.
         *
         * <p>Stages execute in the order added. Each stage receives a
         * {@link PipelineContext} containing results from all prior stages.
         *
         * @param name  unique stage name (used as key for result retrieval)
         * @param stage the stage function
         * @return this builder
         * @throws IllegalArgumentException if a stage with this name already exists
         */
        public Builder stage(String name, Stage stage) {
            Objects.requireNonNull(name, "stage name must not be null");
            Objects.requireNonNull(stage, "stage must not be null");
            if (name.isBlank()) throw new IllegalArgumentException("stage name must not be blank");
            if (stages.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate stage name: '" + name + "'");
            }
            stages.put(name, stage);
            return this;
        }

        /**
         * Adds a named stage that transforms a prior stage's output.
         *
         * <p>Convenience method that retrieves the prior stage's result
         * and applies the given function.
         *
         * @param name           unique stage name
         * @param priorStage     name of the stage whose output to transform
         * @param priorType      expected type of prior stage's output
         * @param transformation function to apply to the prior output
         * @param <I>            prior stage output type
         * @param <O>            this stage output type
         * @return this builder
         */
        public <I, O> Builder stage(String name, String priorStage,
                                     Class<I> priorType,
                                     Function<I, O> transformation) {
            return stage(name, ctx -> {
                I input = ctx.get(priorStage, priorType);
                return transformation.apply(input);
            });
        }

        /**
         * Builds an immutable {@link ProcessMiningPipeline}.
         *
         * @return the configured pipeline
         * @throws IllegalStateException if no stages have been added
         */
        public ProcessMiningPipeline build() {
            if (stages.isEmpty()) {
                throw new IllegalStateException("Pipeline must have at least one stage");
            }
            SupervisionSpec sup = supervisionSpec != null ? supervisionSpec : SupervisionSpec.DEFAULT;
            return new ProcessMiningPipeline(sup, circuitBreakerSpec, eventListeners, stages);
        }
    }
}
