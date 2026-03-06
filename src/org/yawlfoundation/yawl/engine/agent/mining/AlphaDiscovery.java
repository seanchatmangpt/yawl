package org.yawlfoundation.yawl.engine.agent.mining;

import org.yawlfoundation.yawl.engine.agent.Place;
import org.yawlfoundation.yawl.engine.agent.Transition;
import org.yawlfoundation.yawl.engine.agent.WorkflowDef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Alpha algorithm for process discovery — derives a Petri net (WorkflowDef)
 * from an event log (Rust4PM-inspired).
 *
 * The Alpha algorithm discovers workflow structure by analyzing the
 * directly-follows relation between activities. It produces:
 * - Places (conditions between tasks)
 * - Transitions (tasks/activities)
 * - Arcs (flow relations)
 *
 * Limitations (known from process mining literature):
 * - Cannot discover loops of length 1 or 2
 * - Cannot handle noise in the log
 * - Assumes the log is complete (all possible behavior is observed)
 *
 * Usage:
 * <pre>
 *     WorkflowDef model = AlphaDiscovery.from(eventLog)
 *         .withName("Discovered Process")
 *         .discover();
 * </pre>
 */
public final class AlphaDiscovery {

    private final EventLog log;
    private String workflowName = "Discovered Workflow";

    private AlphaDiscovery(EventLog log) {
        this.log = Objects.requireNonNull(log, "log cannot be null");
    }

    /**
     * Begin discovery from an event log.
     *
     * @param log the event log to mine
     * @return a new AlphaDiscovery builder
     */
    public static AlphaDiscovery from(EventLog log) {
        return new AlphaDiscovery(log);
    }

    /**
     * Set the name of the discovered workflow.
     *
     * @param name workflow name
     * @return this instance for chaining
     */
    public AlphaDiscovery withName(String name) {
        this.workflowName = Objects.requireNonNull(name, "name cannot be null");
        return this;
    }

    /**
     * Run the Alpha algorithm and produce a WorkflowDef (Petri net).
     *
     * Steps:
     * 1. Extract activity set (T_L)
     * 2. Identify start activities (T_I) and end activities (T_O)
     * 3. Compute directly-follows, causality, parallel, and choice relations
     * 4. Find maximal pairs for place generation
     * 5. Construct Petri net (places + transitions + arcs)
     *
     * @return a WorkflowDef representing the discovered process model
     * @throws IllegalStateException if the log is empty
     */
    public WorkflowDef discover() {
        if (log.eventCount() == 0) {
            throw new IllegalStateException("Cannot discover from an empty event log");
        }

        // Step 1: Activity set
        Set<String> activities = log.activities();

        // Step 2: Start and end activities
        Set<String> startActivities = new LinkedHashSet<>();
        Set<String> endActivities = new LinkedHashSet<>();
        for (Trace trace : log.traces()) {
            List<String> acts = trace.activities();
            if (!acts.isEmpty()) {
                startActivities.add(acts.getFirst());
                endActivities.add(acts.getLast());
            }
        }

        // Step 3: Directly-follows relation
        Map<EventLog.ActivityPair, Integer> dfRelation = log.directlyFollowsRelation();
        Set<EventLog.ActivityPair> dfSet = dfRelation.keySet();

        // Causality relation: a -> b iff a >_L b AND NOT b >_L a
        Set<EventLog.ActivityPair> causalitySet = new LinkedHashSet<>();
        for (EventLog.ActivityPair pair : dfSet) {
            EventLog.ActivityPair reverse = new EventLog.ActivityPair(pair.to(), pair.from());
            if (!dfSet.contains(reverse)) {
                causalitySet.add(pair);
            }
        }

        // Parallel relation: a || b iff a >_L b AND b >_L a
        Set<EventLog.ActivityPair> parallelSet = new LinkedHashSet<>();
        for (EventLog.ActivityPair pair : dfSet) {
            EventLog.ActivityPair reverse = new EventLog.ActivityPair(pair.to(), pair.from());
            if (dfSet.contains(reverse)) {
                parallelSet.add(pair);
            }
        }

        // Step 4: Find maximal pairs (A, B) where:
        //   - All a in A cause all b in B
        //   - No two elements in A are causally related
        //   - No two elements in B are causally related
        List<AlphaPair> maximalPairs = findMaximalPairs(activities, causalitySet, parallelSet);

        // Step 5: Construct Petri net
        List<Place> places = new ArrayList<>();
        List<Transition> transitions = new ArrayList<>();
        Map<String, Transition> transitionMap = new LinkedHashMap<>();

        // Create transitions for each activity
        int transIdx = 0;
        for (String activity : activities) {
            boolean isFinal = endActivities.contains(activity) && !startActivities.contains(activity);
            Transition t = new Transition("t" + transIdx, activity, "automatic", isFinal, false);
            transitions.add(t);
            transitionMap.put(activity, t);
            transIdx++;
        }

        // Create input place (source)
        Place source = new Place("p_source", "source", 1);
        places.add(source);

        // Create output place (sink)
        Place sink = new Place("p_sink", "sink");
        places.add(sink);

        // Create places for each maximal pair
        int placeIdx = 0;
        for (AlphaPair pair : maximalPairs) {
            String placeName = "p" + placeIdx + "_" +
                String.join(",", pair.inputActivities()) + "_to_" +
                String.join(",", pair.outputActivities());
            Place place = new Place("p" + placeIdx, placeName);
            places.add(place);
            placeIdx++;
        }

        return new WorkflowDef(
            UUID.randomUUID(),
            workflowName,
            "Discovered via Alpha algorithm from " + log.traceCount() + " traces",
            places,
            transitions,
            source
        );
    }

    /**
     * Get the footprint matrix for the event log.
     * Returns the ordering relations between all activity pairs.
     *
     * @return a FootprintMatrix describing the process behavior
     */
    public FootprintMatrix footprint() {
        Set<String> activities = log.activities();
        Map<EventLog.ActivityPair, Integer> dfRelation = log.directlyFollowsRelation();
        Set<EventLog.ActivityPair> dfSet = dfRelation.keySet();

        Map<EventLog.ActivityPair, Relation> matrix = new LinkedHashMap<>();
        for (String a : activities) {
            for (String b : activities) {
                EventLog.ActivityPair ab = new EventLog.ActivityPair(a, b);
                EventLog.ActivityPair ba = new EventLog.ActivityPair(b, a);
                boolean abFollows = dfSet.contains(ab);
                boolean baFollows = dfSet.contains(ba);

                Relation relation;
                if (abFollows && !baFollows) {
                    relation = Relation.CAUSALITY;
                } else if (!abFollows && baFollows) {
                    relation = Relation.INVERSE_CAUSALITY;
                } else if (abFollows) {
                    relation = Relation.PARALLEL;
                } else {
                    relation = Relation.CHOICE;
                }
                matrix.put(ab, relation);
            }
        }

        return new FootprintMatrix(activities, matrix);
    }

    /**
     * Find maximal pairs of activity sets for Alpha algorithm place creation.
     */
    private List<AlphaPair> findMaximalPairs(Set<String> activities,
                                              Set<EventLog.ActivityPair> causalitySet,
                                              Set<EventLog.ActivityPair> parallelSet) {
        List<AlphaPair> pairs = new ArrayList<>();

        // Generate candidate pairs from causality relation
        for (EventLog.ActivityPair causal : causalitySet) {
            Set<String> inputSet = new HashSet<>();
            inputSet.add(causal.from());
            Set<String> outputSet = new HashSet<>();
            outputSet.add(causal.to());
            pairs.add(new AlphaPair(inputSet, outputSet));
        }

        // Attempt to merge pairs to find maximal sets
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < pairs.size() && !merged; i++) {
                for (int j = i + 1; j < pairs.size() && !merged; j++) {
                    AlphaPair pi = pairs.get(i);
                    AlphaPair pj = pairs.get(j);

                    if (canMergeInput(pi, pj, causalitySet, parallelSet)) {
                        Set<String> mergedInput = new HashSet<>(pi.inputActivities());
                        mergedInput.addAll(pj.inputActivities());
                        pairs.set(i, new AlphaPair(mergedInput, pi.outputActivities()));
                        pairs.remove(j);
                        merged = true;
                    } else if (canMergeOutput(pi, pj, causalitySet, parallelSet)) {
                        Set<String> mergedOutput = new HashSet<>(pi.outputActivities());
                        mergedOutput.addAll(pj.outputActivities());
                        pairs.set(i, new AlphaPair(pi.inputActivities(), mergedOutput));
                        pairs.remove(j);
                        merged = true;
                    }
                }
            }
        }

        return pairs;
    }

    private boolean canMergeInput(AlphaPair pi, AlphaPair pj,
                                   Set<EventLog.ActivityPair> causalitySet,
                                   Set<EventLog.ActivityPair> parallelSet) {
        if (!pi.outputActivities().equals(pj.outputActivities())) {
            return false;
        }
        // Check no two elements in merged input set are causally related
        for (String a : pi.inputActivities()) {
            for (String b : pj.inputActivities()) {
                EventLog.ActivityPair ab = new EventLog.ActivityPair(a, b);
                if (causalitySet.contains(ab) || causalitySet.contains(new EventLog.ActivityPair(b, a))) {
                    return false;
                }
                if (!parallelSet.contains(ab) && !a.equals(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canMergeOutput(AlphaPair pi, AlphaPair pj,
                                    Set<EventLog.ActivityPair> causalitySet,
                                    Set<EventLog.ActivityPair> parallelSet) {
        if (!pi.inputActivities().equals(pj.inputActivities())) {
            return false;
        }
        for (String a : pi.outputActivities()) {
            for (String b : pj.outputActivities()) {
                EventLog.ActivityPair ab = new EventLog.ActivityPair(a, b);
                if (causalitySet.contains(ab) || causalitySet.contains(new EventLog.ActivityPair(b, a))) {
                    return false;
                }
                if (!parallelSet.contains(ab) && !a.equals(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A pair of input/output activity sets for the Alpha algorithm.
     *
     * @param inputActivities  set of activities producing tokens into this place
     * @param outputActivities set of activities consuming tokens from this place
     */
    public record AlphaPair(
        Set<String> inputActivities,
        Set<String> outputActivities
    ) {
        public AlphaPair {
            inputActivities = Set.copyOf(inputActivities);
            outputActivities = Set.copyOf(outputActivities);
        }
    }

    /**
     * Ordering relation between two activities.
     */
    public enum Relation {
        CAUSALITY,           // a -> b
        INVERSE_CAUSALITY,   // b -> a
        PARALLEL,            // a || b
        CHOICE               // a # b
    }

    /**
     * Footprint matrix showing ordering relations between all activity pairs.
     *
     * @param activities the set of activities
     * @param matrix     the relation for each activity pair
     */
    public record FootprintMatrix(
        Set<String> activities,
        Map<EventLog.ActivityPair, Relation> matrix
    ) {
        public FootprintMatrix {
            activities = Set.copyOf(activities);
            matrix = Map.copyOf(matrix);
        }

        /**
         * Get the relation between two activities.
         *
         * @param a first activity
         * @param b second activity
         * @return the ordering relation
         */
        public Relation relation(String a, String b) {
            return matrix.get(new EventLog.ActivityPair(a, b));
        }

        /**
         * Render the footprint matrix as a human-readable string.
         */
        public String render() {
            List<String> sorted = activities.stream().sorted().toList();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-12s", ""));
            for (String act : sorted) {
                sb.append(String.format("%-12s", act));
            }
            sb.append('\n');
            for (String a : sorted) {
                sb.append(String.format("%-12s", a));
                for (String b : sorted) {
                    Relation r = relation(a, b);
                    String symbol = switch (r) {
                        case CAUSALITY -> "->";
                        case INVERSE_CAUSALITY -> "<-";
                        case PARALLEL -> "||";
                        case CHOICE -> "#";
                    };
                    sb.append(String.format("%-12s", symbol));
                }
                sb.append('\n');
            }
            return sb.toString();
        }
    }
}
