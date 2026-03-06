package org.yawlfoundation.yawl.rust4pm.fluent;

import org.yawlfoundation.yawl.rust4pm.bridge.OcelLogHandle;
import org.yawlfoundation.yawl.rust4pm.bridge.Rust4pmBridge;
import org.yawlfoundation.yawl.rust4pm.error.ProcessMiningException;
import org.yawlfoundation.yawl.rust4pm.model.ConformanceReport;
import org.yawlfoundation.yawl.rust4pm.model.DirectlyFollowsGraph;
import org.yawlfoundation.yawl.rust4pm.model.PerformanceStats;
import org.yawlfoundation.yawl.processmining.ProcessMiningEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fluent API for composing process mining pipelines with OTP-inspired supervision.
 *
 * <p>Combines Erlang/OTP fault-tolerance patterns (supervision strategies,
 * let-it-crash with retry) with Rust4PM's high-performance process mining
 * via Java 25 Panama FFM.
 *
 * <p>Usage:
 * <pre>{@code
 * try (var bridge = new Rust4pmBridge()) {
 *     PipelineResult result = ProcessMiningPipeline.create(bridge)
 *         .supervised(SupervisionStrategy.RETRY_DEFAULT)
 *         .parse(ocelJson)
 *         .discoverDfg()
 *         .checkConformance(pnmlXml)
 *         .execute();
 *
 *     result.dfg().ifPresent(dfg ->
 *         System.out.println("Nodes: " + dfg.nodes().size()));
 *     result.conformance().ifPresent(report ->
 *         System.out.printf("Fitness: %.2f%n", report.fitness()));
 * }
 * }</pre>
 *
 * <p>Supervision strategies control failure handling:
 * <ul>
 *   <li>{@link SupervisionStrategy#FAIL_FAST} — Abort on first failure (default)</li>
 *   <li>{@link SupervisionStrategy#RETRY_DEFAULT} — Retry 3x with 100ms delay</li>
 *   <li>{@link SupervisionStrategy#SKIP_ON_FAILURE} — Skip failed stages</li>
 * </ul>
 */
public final class ProcessMiningPipeline {

    private final Rust4pmBridge bridge;
    private SupervisionStrategy supervision = SupervisionStrategy.FAIL_FAST;
    private final List<PipelineStage> stages = new ArrayList<>();

    private ProcessMiningPipeline(Rust4pmBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
    }

    /**
     * Create a new pipeline builder backed by the given bridge.
     *
     * @param bridge open Rust4pmBridge (caller manages lifecycle)
     * @return pipeline builder
     */
    public static ProcessMiningPipeline create(Rust4pmBridge bridge) {
        return new ProcessMiningPipeline(bridge);
    }

    /**
     * Set the supervision strategy for this pipeline.
     *
     * @param strategy OTP-inspired supervision strategy
     * @return this builder
     */
    public ProcessMiningPipeline supervised(SupervisionStrategy strategy) {
        this.supervision = Objects.requireNonNull(strategy, "strategy must not be null");
        return this;
    }

    /**
     * Add a parse stage: parse OCEL2 JSON into a native log handle.
     *
     * @param ocelJson OCEL2 format JSON string
     * @return this builder
     */
    public ProcessMiningPipeline parse(String ocelJson) {
        stages.add(new PipelineStage.ParseOcel2(ocelJson));
        return this;
    }

    /**
     * Add a DFG discovery stage.
     * Requires a preceding {@link PipelineStage.ParseOcel2} stage.
     *
     * @return this builder
     */
    public ProcessMiningPipeline discoverDfg() {
        stages.add(new PipelineStage.DiscoverDfg());
        return this;
    }

    /**
     * Add a conformance checking stage.
     * Requires a preceding {@link PipelineStage.ParseOcel2} stage.
     *
     * @param pnmlXml Petri net in PNML XML format
     * @return this builder
     */
    public ProcessMiningPipeline checkConformance(String pnmlXml) {
        stages.add(new PipelineStage.CheckConformance(pnmlXml));
        return this;
    }

    /**
     * Add a performance statistics stage.
     * Requires a preceding {@link PipelineStage.ParseOcel2} stage.
     *
     * @return this builder
     */
    public ProcessMiningPipeline computeStats() {
        stages.add(new PipelineStage.ComputeStats());
        return this;
    }

    /**
     * Execute the pipeline, running all stages in order.
     *
     * <p>Applies the configured {@link SupervisionStrategy} to handle failures:
     * <ul>
     *   <li>FAIL_FAST: throws on first failure</li>
     *   <li>RETRY: retries failed stage up to maxRetries times</li>
     *   <li>SKIP: records failure and continues to next stage</li>
     * </ul>
     *
     * @return pipeline result containing all artifacts and stage outcomes
     * @throws ProcessMiningException if a stage fails and supervision is FAIL_FAST
     * @throws IllegalStateException  if pipeline has no stages or no parse stage
     */
    public PipelineResult execute() throws ProcessMiningException {
        if (stages.isEmpty()) {
            throw new IllegalStateException("Pipeline has no stages. Add at least a parse() stage.");
        }

        boolean hasParse = stages.stream().anyMatch(s -> s instanceof PipelineStage.ParseOcel2);
        if (!hasParse) {
            throw new IllegalStateException("Pipeline requires a parse() stage before other operations.");
        }

        Instant startedAt = Instant.now();
        List<PipelineResult.StageOutcome> outcomes = new ArrayList<>();
        DirectlyFollowsGraph dfg = null;
        ConformanceReport conformance = null;
        PerformanceStats stats = null;
        OcelLogHandle logHandle = null;

        try (ProcessMiningEngine engine = new ProcessMiningEngine(bridge)) {
            for (PipelineStage stage : stages) {
                Instant stageStart = Instant.now();
                int retries = 0;

                while (true) {
                    try {
                        switch (stage) {
                            case PipelineStage.ParseOcel2 parse -> {
                                if (logHandle != null) {
                                    logHandle.close();
                                }
                                logHandle = engine.parseOcel2Json(parse.json());
                            }
                            case PipelineStage.DiscoverDfg ignored -> {
                                requireLog(logHandle);
                                dfg = engine.discoverDfg(logHandle);
                            }
                            case PipelineStage.CheckConformance check -> {
                                requireLog(logHandle);
                                conformance = engine.checkConformance(logHandle, check.pnmlXml());
                            }
                            case PipelineStage.ComputeStats ignored -> {
                                requireLog(logHandle);
                                stats = engine.computePerformanceStats(logHandle);
                            }
                        }

                        outcomes.add(new PipelineResult.StageOutcome(
                            stage, PipelineResult.StageStatus.SUCCESS,
                            Duration.between(stageStart, Instant.now()), null, retries));
                        break;

                    } catch (ProcessMiningException | UnsupportedOperationException e) {
                        boolean handled = handleFailure(stage, e, stageStart, retries, outcomes);
                        if (!handled) {
                            throw wrapException(e);
                        }
                        if (supervision.policy() == SupervisionStrategy.Policy.SKIP) {
                            break;
                        }
                        retries++;
                        sleepBeforeRetry();
                    }
                }
            }
        } finally {
            if (logHandle != null) {
                logHandle.close();
            }
        }

        return new PipelineResult(
            Optional.ofNullable(dfg),
            Optional.ofNullable(conformance),
            Optional.ofNullable(stats),
            outcomes,
            Duration.between(startedAt, Instant.now()),
            startedAt
        );
    }

    private void requireLog(OcelLogHandle logHandle) {
        if (logHandle == null) {
            throw new IllegalStateException(
                "No parsed log available. Ensure parse() stage precedes this stage.");
        }
    }

    /**
     * Handle a stage failure according to the supervision strategy.
     *
     * @return true if the failure was handled (skip or retry), false if should propagate
     */
    private boolean handleFailure(PipelineStage stage, Exception e,
                                  Instant stageStart, int retries,
                                  List<PipelineResult.StageOutcome> outcomes) {
        switch (supervision.policy()) {
            case FAIL_FAST -> {
                outcomes.add(new PipelineResult.StageOutcome(
                    stage, PipelineResult.StageStatus.FAILED,
                    Duration.between(stageStart, Instant.now()), e, retries));
                return false;
            }
            case RETRY -> {
                if (retries >= supervision.maxRetries()) {
                    outcomes.add(new PipelineResult.StageOutcome(
                        stage, PipelineResult.StageStatus.FAILED,
                        Duration.between(stageStart, Instant.now()), e, retries));
                    return false;
                }
                return true;
            }
            case SKIP -> {
                outcomes.add(new PipelineResult.StageOutcome(
                    stage, PipelineResult.StageStatus.SKIPPED,
                    Duration.between(stageStart, Instant.now()), e, retries));
                return true;
            }
        }
        return false;
    }

    private void sleepBeforeRetry() {
        if (!supervision.retryDelay().isZero()) {
            try {
                Thread.sleep(supervision.retryDelay());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ProcessMiningException wrapException(Exception e) {
        if (e instanceof ProcessMiningException pme) {
            return pme;
        }
        return new ProcessMiningException("Pipeline stage failed: " + e.getMessage(), e);
    }
}
