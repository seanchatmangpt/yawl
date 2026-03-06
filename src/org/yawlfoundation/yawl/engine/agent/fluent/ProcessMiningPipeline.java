package org.yawlfoundation.yawl.engine.agent.fluent;

import org.yawlfoundation.yawl.engine.agent.core.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Fluent builder for actor-based process mining pipelines.
 *
 * <p>Composes OTP-style actors into a staged pipeline where each stage processes
 * data and forwards results to the next stage. Stages are supervised, fault-tolerant,
 * and communicate via the actor mailbox.</p>
 *
 * <p>The pipeline is backend-agnostic: stage functions accept and return plain Java
 * objects, so they can delegate to Rust4PM (native FFM), Erlang/OTP (ErlangBridge),
 * or pure Java implementations.</p>
 *
 * <p>Usage:
 * <pre>
 *   var pipeline = ProcessMiningPipeline.builder()
 *       .runtime(new VirtualThreadRuntime())
 *       .supervisor(s -&gt; s.strategy(ONE_FOR_ONE).maxRestarts(5))
 *       .stage("parse", json -&gt; parseOcel2(json))
 *       .stage("discover", log -&gt; discoverDfg(log))
 *       .stage("conform", dfg -&gt; checkConformance(dfg))
 *       .build();
 *
 *   CompletableFuture&lt;Object&gt; result = pipeline.submit(ocelJson);
 *   Object conformanceReport = result.get(30, TimeUnit.SECONDS);
 *   pipeline.close();
 * </pre>
 *
 * <p>Each stage runs as a supervised actor on a virtual thread. Messages flow
 * through the pipeline stages in order. Failed stages are restarted by the
 * supervisor per the configured strategy.</p>
 */
public final class ProcessMiningPipeline implements AutoCloseable {

    private final ActorRuntime runtime;
    private final Supervisor supervisor;
    private final List<String> stageNames;
    private final Map<String, ActorRef> stageActors;
    private final Map<Long, CompletableFuture<Object>> pending;
    private final AtomicLong correlationSeq;

    private ProcessMiningPipeline(ActorRuntime runtime, Supervisor supervisor,
                                   List<String> stageNames,
                                   Map<String, ActorRef> stageActors,
                                   Map<Long, CompletableFuture<Object>> pending) {
        this.runtime = runtime;
        this.supervisor = supervisor;
        this.stageNames = List.copyOf(stageNames);
        this.stageActors = stageActors;
        this.pending = pending;
        this.correlationSeq = new AtomicLong(System.nanoTime());
    }

    /**
     * Create a new pipeline builder.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Submit data into the first pipeline stage and receive a future for the
     * final stage's output.
     *
     * @param input data to process (fed to the first stage)
     * @return CompletableFuture completed with the final stage's result
     */
    public CompletableFuture<Object> submit(Object input) {
        long correlationId = correlationSeq.getAndIncrement();
        CompletableFuture<Object> future = new CompletableFuture<>();
        pending.put(correlationId, future);

        String firstStage = stageNames.getFirst();
        stageActors.get(firstStage).tell(new PipelineMessage(correlationId, input));

        return future;
    }

    /**
     * Submit data and block until the pipeline completes or times out.
     *
     * @param input   data to process
     * @param timeout maximum wait time
     * @return the final stage's result
     * @throws Exception if the pipeline fails or times out
     */
    public Object submitAndWait(Object input, Duration timeout) throws Exception {
        return submit(input).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Get the names of all pipeline stages in order.
     *
     * @return unmodifiable list of stage names
     */
    public List<String> stageNames() {
        return stageNames;
    }

    /**
     * Get the number of pending (in-flight) pipeline executions.
     *
     * @return pending count
     */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * Get the ActorRef for a named stage.
     *
     * @param stageName stage name
     * @return the actor reference, or empty if not found
     */
    public Optional<ActorRef> lookupStage(String stageName) {
        return Optional.ofNullable(stageActors.get(stageName));
    }

    @Override
    public void close() {
        pending.forEach((id, future) ->
            future.completeExceptionally(new IllegalStateException("Pipeline closed")));
        pending.clear();
        supervisor.stop(false);
        runtime.close();
    }

    /**
     * Internal message flowing between pipeline stages.
     * Carries a correlation ID for routing the final result back to the caller.
     *
     * @param correlationId unique ID linking this message to a pending future
     * @param payload       the data being processed
     */
    record PipelineMessage(long correlationId, Object payload) {}

    /**
     * Fluent builder for {@link ProcessMiningPipeline}.
     */
    public static final class Builder {

        private ActorRuntime runtime;
        private Supervisor.SupervisorStrategy strategy =
            Supervisor.SupervisorStrategy.ONE_FOR_ONE;
        private Duration restartDelay = Duration.ofSeconds(1);
        private int maxRestarts = 3;
        private Duration restartWindow = Duration.ofMinutes(1);
        private final List<StageEntry> stages = new ArrayList<>();

        private Builder() {
        }

        /**
         * Set the actor runtime for the pipeline.
         *
         * @param runtime the runtime (e.g., {@code new VirtualThreadRuntime()})
         * @return this builder
         */
        public Builder runtime(ActorRuntime runtime) {
            this.runtime = Objects.requireNonNull(runtime, "runtime");
            return this;
        }

        /**
         * Configure the supervisor for pipeline actors via a fluent lambda.
         *
         * @param configurer lambda that configures supervisor settings
         * @return this builder
         */
        public Builder supervisor(
                java.util.function.Consumer<SupervisorConfig> configurer) {
            SupervisorConfig config = new SupervisorConfig();
            configurer.accept(config);
            this.strategy = config.strategy;
            this.restartDelay = config.restartDelay;
            this.maxRestarts = config.maxRestarts;
            this.restartWindow = config.restartWindow;
            return this;
        }

        /**
         * Add a named processing stage to the pipeline.
         *
         * <p>Stages execute in the order they are added. Each stage's output
         * becomes the next stage's input.</p>
         *
         * @param name      unique stage name (used as actor name)
         * @param transform function that processes input and returns output
         * @return this builder
         */
        public Builder stage(String name, Function<Object, Object> transform) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(transform, "transform");
            stages.add(new StageEntry(name, transform));
            return this;
        }

        /**
         * Build the pipeline, creating a supervised actor system with
         * one actor per stage, wired in sequence.
         *
         * <p>Stages are spawned in reverse order so each stage captures
         * the next stage's {@link ActorRef} directly — no fragile ID arithmetic.</p>
         *
         * @return a ready-to-use {@link ProcessMiningPipeline}
         * @throws IllegalStateException if no stages are defined
         */
        public ProcessMiningPipeline build() {
            if (stages.isEmpty()) {
                throw new IllegalStateException("Pipeline must have at least one stage");
            }

            if (runtime == null) {
                runtime = new VirtualThreadRuntime();
            }

            Map<Long, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

            Supervisor sup = new Supervisor(
                runtime, strategy, restartDelay, maxRestarts, restartWindow);

            Map<String, ActorRef> stageActors = new LinkedHashMap<>();
            List<String> stageNames = stages.stream()
                .map(StageEntry::name)
                .toList();

            // Spawn stages in reverse order so each captures the next stage's ActorRef
            ActorRef nextRef = null;
            for (int i = stages.size() - 1; i >= 0; i--) {
                StageEntry stage = stages.get(i);
                boolean isLast = (i == stages.size() - 1);
                ActorRef downstream = nextRef;

                ActorBehavior behavior = createStageBehavior(
                    stage.transform(), downstream, isLast, pending);

                ActorRef ref = sup.spawn(stage.name(), behavior);
                stageActors.put(stage.name(), ref);
                nextRef = ref;
            }

            sup.start();

            return new ProcessMiningPipeline(
                runtime, sup, stageNames, stageActors, pending);
        }

        private static ActorBehavior createStageBehavior(
                Function<Object, Object> transform,
                ActorRef downstream,
                boolean isLastStage,
                Map<Long, CompletableFuture<Object>> pending) {

            return self -> {
                while (true) {
                    Object raw = self.recv();
                    if (raw instanceof PipelineMessage msg) {
                        try {
                            Object result = transform.apply(msg.payload());
                            if (isLastStage) {
                                CompletableFuture<Object> future =
                                    pending.remove(msg.correlationId());
                                if (future != null) {
                                    future.complete(result);
                                }
                            } else {
                                downstream.tell(
                                    new PipelineMessage(msg.correlationId(), result));
                            }
                        } catch (Exception e) {
                            CompletableFuture<Object> future =
                                pending.remove(msg.correlationId());
                            if (future != null) {
                                future.completeExceptionally(e);
                            }
                        }
                    }
                }
            };
        }

        private record StageEntry(String name, Function<Object, Object> transform) {}
    }

    /**
     * Mutable configuration holder for supervisor settings, used via fluent lambda.
     */
    public static final class SupervisorConfig {
        private Supervisor.SupervisorStrategy strategy =
            Supervisor.SupervisorStrategy.ONE_FOR_ONE;
        private Duration restartDelay = Duration.ofSeconds(1);
        private int maxRestarts = 3;
        private Duration restartWindow = Duration.ofMinutes(1);

        /**
         * Set the restart strategy.
         *
         * @param strategy ONE_FOR_ONE, ONE_FOR_ALL, or REST_FOR_ONE
         * @return this config
         */
        public SupervisorConfig strategy(Supervisor.SupervisorStrategy strategy) {
            this.strategy = Objects.requireNonNull(strategy, "strategy");
            return this;
        }

        /**
         * Set the delay before restarting a failed actor.
         *
         * @param delay restart delay
         * @return this config
         */
        public SupervisorConfig restartDelay(Duration delay) {
            this.restartDelay = Objects.requireNonNull(delay, "delay");
            return this;
        }

        /**
         * Set the maximum restarts within the restart window.
         *
         * @param maxRestarts max restarts (must be &gt;= 1)
         * @return this config
         */
        public SupervisorConfig maxRestarts(int maxRestarts) {
            if (maxRestarts < 1) {
                throw new IllegalArgumentException("maxRestarts must be >= 1");
            }
            this.maxRestarts = maxRestarts;
            return this;
        }

        /**
         * Set the time window for counting restarts.
         *
         * @param window restart window duration
         * @return this config
         */
        public SupervisorConfig within(Duration window) {
            this.restartWindow = Objects.requireNonNull(window, "window");
            return this;
        }
    }
}
