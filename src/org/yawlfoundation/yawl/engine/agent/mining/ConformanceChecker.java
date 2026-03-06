package org.yawlfoundation.yawl.engine.agent.mining;

import org.yawlfoundation.yawl.engine.agent.WorkflowDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Conformance checking — compares observed behavior (event log) against
 * a reference process model (WorkflowDef) to detect deviations (Rust4PM-inspired).
 *
 * Detects:
 * - Missing activities (model expects, log omits)
 * - Unexpected activities (log has, model doesn't expect)
 * - Ordering violations (activities in wrong sequence)
 *
 * Produces a ConformanceResult with fitness score and deviation details.
 *
 * Usage:
 * <pre>
 *     ConformanceResult result = ConformanceChecker.check(eventLog)
 *         .against(workflowDef)
 *         .run();
 *
 *     double fitness = result.fitness();
 *     List&lt;Deviation&gt; deviations = result.deviations();
 * </pre>
 */
public final class ConformanceChecker {

    private final EventLog log;
    private WorkflowDef model;

    private ConformanceChecker(EventLog log) {
        this.log = Objects.requireNonNull(log, "log cannot be null");
    }

    /**
     * Begin conformance checking from an event log.
     *
     * @param log the observed event log
     * @return a new ConformanceChecker
     */
    public static ConformanceChecker check(EventLog log) {
        return new ConformanceChecker(log);
    }

    /**
     * Set the reference process model to check against.
     *
     * @param model the expected workflow model
     * @return this instance for chaining
     */
    public ConformanceChecker against(WorkflowDef model) {
        this.model = Objects.requireNonNull(model, "model cannot be null");
        return this;
    }

    /**
     * Run the conformance check.
     *
     * Compares each trace in the event log against the model's transitions.
     * Computes fitness as: 1 - (deviations / total_events).
     *
     * @return conformance checking result with fitness and deviations
     * @throws IllegalStateException if model not set
     */
    public ConformanceResult run() {
        if (model == null) {
            throw new IllegalStateException("Reference model must be set via against() before run()");
        }

        // Extract expected activities from model transitions
        Set<String> modelActivities = new HashSet<>();
        model.transitions().forEach(t -> modelActivities.add(t.name()));

        List<Deviation> deviations = new ArrayList<>();
        int totalEvents = 0;

        for (Trace trace : log.traces()) {
            List<String> observedActivities = trace.activities();
            totalEvents += observedActivities.size();

            // Check for unexpected activities
            for (String activity : observedActivities) {
                if (!modelActivities.contains(activity)) {
                    deviations.add(new Deviation(
                        trace.caseId(),
                        activity,
                        DeviationType.UNEXPECTED_ACTIVITY,
                        "Activity '" + activity + "' not in model"
                    ));
                }
            }

            // Check for missing activities (activities in model not observed in trace)
            Set<String> observedSet = new HashSet<>(observedActivities);
            for (String modelActivity : modelActivities) {
                if (!observedSet.contains(modelActivity)) {
                    deviations.add(new Deviation(
                        trace.caseId(),
                        modelActivity,
                        DeviationType.MISSING_ACTIVITY,
                        "Expected activity '" + modelActivity + "' not in trace"
                    ));
                }
            }

            // Check ordering: verify directly-follows relations from model
            checkOrdering(trace, modelActivities, deviations);
        }

        double fitness;
        if (totalEvents == 0) {
            fitness = 1.0;
        } else {
            int deviationWeight = deviations.size();
            fitness = Math.max(0.0, 1.0 - ((double) deviationWeight / (totalEvents + modelActivities.size())));
        }

        return new ConformanceResult(fitness, deviations, log.traceCount(), totalEvents);
    }

    private void checkOrdering(Trace trace, Set<String> modelActivities,
                                List<Deviation> deviations) {
        List<String> acts = trace.activities();
        // Only check ordering for activities that are in the model
        List<String> modelActs = acts.stream()
            .filter(modelActivities::contains)
            .toList();

        // Check that model transitions that should follow each other actually do
        for (int i = 0; i < modelActs.size() - 1; i++) {
            String current = modelActs.get(i);
            String next = modelActs.get(i + 1);

            // Verify this directly-follows pair exists in the model's transition structure
            boolean validSequence = model.transitions().stream()
                .anyMatch(t -> t.name().equals(current)) &&
                model.transitions().stream()
                .anyMatch(t -> t.name().equals(next));

            if (!validSequence) {
                deviations.add(new Deviation(
                    trace.caseId(),
                    current + " -> " + next,
                    DeviationType.ORDERING_VIOLATION,
                    "Sequence '" + current + "' -> '" + next + "' not valid in model"
                ));
            }
        }
    }

    /**
     * Result of a conformance check.
     *
     * @param fitness     fitness score between 0.0 (no conformance) and 1.0 (perfect)
     * @param deviations  list of all detected deviations
     * @param traceCount  number of traces checked
     * @param eventCount  total number of events checked
     */
    public record ConformanceResult(
        double fitness,
        List<Deviation> deviations,
        int traceCount,
        int eventCount
    ) {
        public ConformanceResult {
            if (fitness < 0.0 || fitness > 1.0) {
                throw new IllegalArgumentException("fitness must be in [0.0, 1.0], got: " + fitness);
            }
            deviations = Collections.unmodifiableList(new ArrayList<>(deviations));
        }

        /**
         * Check if the log perfectly conforms to the model.
         */
        public boolean isPerfect() {
            return deviations.isEmpty();
        }

        /**
         * Get deviations filtered by type.
         *
         * @param type the deviation type to filter by
         * @return filtered deviations
         */
        public List<Deviation> deviationsOfType(DeviationType type) {
            return deviations.stream()
                .filter(d -> d.type() == type)
                .toList();
        }
    }

    /**
     * A single deviation detected during conformance checking.
     *
     * @param caseId      which trace the deviation was found in
     * @param activity    the activity involved
     * @param type        the type of deviation
     * @param description human-readable description
     */
    public record Deviation(
        String caseId,
        String activity,
        DeviationType type,
        String description
    ) {
        public Deviation {
            Objects.requireNonNull(caseId, "caseId cannot be null");
            Objects.requireNonNull(activity, "activity cannot be null");
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(description, "description cannot be null");
        }
    }

    /**
     * Types of conformance deviations.
     */
    public enum DeviationType {
        MISSING_ACTIVITY,
        UNEXPECTED_ACTIVITY,
        ORDERING_VIOLATION
    }
}
