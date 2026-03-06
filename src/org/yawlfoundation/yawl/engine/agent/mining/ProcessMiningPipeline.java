package org.yawlfoundation.yawl.engine.agent.mining;

import org.yawlfoundation.yawl.engine.agent.WorkflowDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Fluent pipeline for composing process mining operations (Rust4PM-inspired).
 *
 * Chains log transformations, discovery, and conformance checking into
 * a single fluent expression. Lazy evaluation — nothing executes until
 * a terminal operation (discover, conformance, footprint) is called.
 *
 * Usage:
 * <pre>
 *     // Discover a model from a log
 *     WorkflowDef model = ProcessMiningPipeline.fromLog(eventLog)
 *         .filterActivities(a -> !a.equals("admin-task"))
 *         .filterCases(caseId -> caseId.startsWith("prod-"))
 *         .discover("My Process");
 *
 *     // Check conformance
 *     ConformanceChecker.ConformanceResult result = ProcessMiningPipeline.fromLog(eventLog)
 *         .filterActivities(a -> !a.equals("admin-task"))
 *         .checkConformance(referenceModel);
 *
 *     // Compute footprint
 *     AlphaDiscovery.FootprintMatrix fp = ProcessMiningPipeline.fromLog(eventLog)
 *         .footprint();
 * </pre>
 */
public final class ProcessMiningPipeline {

    private final EventLog sourceLog;
    private final List<UnaryOperator<EventLog>> transformations;

    private ProcessMiningPipeline(EventLog log) {
        this.sourceLog = Objects.requireNonNull(log, "log cannot be null");
        this.transformations = new ArrayList<>();
    }

    private ProcessMiningPipeline(EventLog log, List<UnaryOperator<EventLog>> transformations) {
        this.sourceLog = log;
        this.transformations = new ArrayList<>(transformations);
    }

    /**
     * Start a pipeline from an event log.
     *
     * @param log the source event log
     * @return a new pipeline
     */
    public static ProcessMiningPipeline fromLog(EventLog log) {
        return new ProcessMiningPipeline(log);
    }

    /**
     * Filter events by activity name.
     * Only events whose activity passes the predicate are kept.
     *
     * @param activityFilter predicate on activity name (true = keep)
     * @return a new pipeline with the filter applied
     */
    public ProcessMiningPipeline filterActivities(Predicate<String> activityFilter) {
        Objects.requireNonNull(activityFilter, "activityFilter cannot be null");
        ProcessMiningPipeline next = new ProcessMiningPipeline(sourceLog, transformations);
        next.transformations.add(log -> {
            List<ProcessEvent> filtered = log.events().stream()
                .filter(e -> activityFilter.test(e.activity()))
                .toList();
            return EventLog.of(filtered);
        });
        return next;
    }

    /**
     * Filter events by case ID.
     * Only events whose case ID passes the predicate are kept.
     *
     * @param caseFilter predicate on case ID (true = keep)
     * @return a new pipeline with the filter applied
     */
    public ProcessMiningPipeline filterCases(Predicate<String> caseFilter) {
        Objects.requireNonNull(caseFilter, "caseFilter cannot be null");
        ProcessMiningPipeline next = new ProcessMiningPipeline(sourceLog, transformations);
        next.transformations.add(log -> {
            List<ProcessEvent> filtered = log.events().stream()
                .filter(e -> caseFilter.test(e.caseId()))
                .toList();
            return EventLog.of(filtered);
        });
        return next;
    }

    /**
     * Filter events by lifecycle type (START, COMPLETE, SKIP).
     *
     * @param lifecycle the lifecycle to keep
     * @return a new pipeline with the filter applied
     */
    public ProcessMiningPipeline filterLifecycle(ProcessEvent.Lifecycle lifecycle) {
        Objects.requireNonNull(lifecycle, "lifecycle cannot be null");
        ProcessMiningPipeline next = new ProcessMiningPipeline(sourceLog, transformations);
        next.transformations.add(log -> {
            List<ProcessEvent> filtered = log.events().stream()
                .filter(e -> e.lifecycle() == lifecycle)
                .toList();
            return EventLog.of(filtered);
        });
        return next;
    }

    /**
     * Apply all transformations and return the resulting event log.
     *
     * @return the transformed event log
     */
    public EventLog materialize() {
        EventLog current = sourceLog;
        for (UnaryOperator<EventLog> transform : transformations) {
            current = transform.apply(current);
        }
        return current;
    }

    /**
     * Terminal operation: discover a process model using the Alpha algorithm.
     *
     * @param processName name for the discovered workflow
     * @return the discovered WorkflowDef
     */
    public WorkflowDef discover(String processName) {
        EventLog transformed = materialize();
        return AlphaDiscovery.from(transformed)
            .withName(processName)
            .discover();
    }

    /**
     * Terminal operation: compute the footprint matrix.
     *
     * @return the footprint matrix
     */
    public AlphaDiscovery.FootprintMatrix footprint() {
        EventLog transformed = materialize();
        return AlphaDiscovery.from(transformed).footprint();
    }

    /**
     * Terminal operation: check conformance against a reference model.
     *
     * @param model the reference workflow model
     * @return conformance checking result
     */
    public ConformanceChecker.ConformanceResult checkConformance(WorkflowDef model) {
        EventLog transformed = materialize();
        return ConformanceChecker.check(transformed)
            .against(model)
            .run();
    }

    /**
     * Terminal operation: get summary statistics for the (transformed) log.
     *
     * @return log summary
     */
    public LogSummary summarize() {
        EventLog transformed = materialize();
        return new LogSummary(
            transformed.eventCount(),
            transformed.traceCount(),
            transformed.activities().size(),
            Collections.unmodifiableList(new ArrayList<>(transformed.activities()))
        );
    }

    /**
     * Summary statistics for an event log.
     *
     * @param eventCount    total number of events
     * @param traceCount    number of distinct traces (cases)
     * @param activityCount number of distinct activities
     * @param activities    list of activity names
     */
    public record LogSummary(
        int eventCount,
        int traceCount,
        int activityCount,
        List<String> activities
    ) {
        public LogSummary {
            activities = List.copyOf(activities);
        }
    }
}
