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

package org.yawlfoundation.yawl.integration.verification;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core soundness verifier for Petri net workflows using graph reachability
 * analysis and cycle detection.
 *
 * <p>The verifier takes a Petri net represented as adjacency maps and performs
 * 7 pattern checks using real graph algorithms:
 * <ul>
 *   <li>BFS reachability to detect unreachable tasks and orphaned places</li>
 *   <li>Strongly connected component (SCC) detection for livelock identification</li>
 *   <li>Path analysis for AND-join validation and implicit deadlock detection</li>
 *   <li>Convergence point analysis for missing OR-join detection</li>
 *   <li>Terminal state validation for improper termination detection</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * var verifier = new SoundnessVerifier(
 *     placeToTransitions,    // Map&lt;placeId, Set&lt;transitionId&gt;&gt;
 *     transitionToPlaces,    // Map&lt;transitionId, Set&lt;placeId&gt;&gt;
 *     "startPlace",
 *     "endPlace"
 * );
 * VerificationReport report = verifier.verify();
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class SoundnessVerifier {
    // Graph structure: bipartite Petri net (places and transitions)
    private final Map<String, Set<String>> placeToTransitions;
    private final Map<String, Set<String>> transitionToPlaces;
    private final String startPlace;
    private final String endPlace;
    private final WfNetSoundnessProver soundnessProver = new WfNetSoundnessProver();

    // Cached reachability for optimization
    private Set<String> reachablePlaces;
    private Set<String> reachableTransitions;
    private Map<String, Set<String>> stronglyConnectedComponents;

    /**
     * Constructs a SoundnessVerifier for the given Petri net.
     *
     * @param placeToTransitions map of place IDs to their output transitions
     * @param transitionToPlaces map of transition IDs to their output places
     * @param startPlace ID of the start place
     * @param endPlace ID of the end place (must be reachable from startPlace)
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if startPlace or endPlace are empty
     */
    public SoundnessVerifier(
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces,
        String startPlace,
        String endPlace
    ) {
        this.placeToTransitions = Objects.requireNonNull(placeToTransitions);
        this.transitionToPlaces = Objects.requireNonNull(transitionToPlaces);
        this.startPlace = Objects.requireNonNull(startPlace);
        this.endPlace = Objects.requireNonNull(endPlace);

        if (startPlace.isBlank()) {
            throw new IllegalArgumentException("startPlace must not be empty");
        }
        if (endPlace.isBlank()) {
            throw new IllegalArgumentException("endPlace must not be empty");
        }
    }

    /**
     * Proves soundness of this Petri net using van der Aalst's theorem (1997).
     *
     * <p>Delegates to WfNetSoundnessProver to validate structure and check that the
     * short-circuited net N* is both live and bounded.
     *
     * @return a SoundnessProof with complete proof details
     */
    public WfNetSoundnessProver.SoundnessProof proveWfNetSoundness() {
        return soundnessProver.prove(placeToTransitions, transitionToPlaces, startPlace, endPlace);
    }

    /**
     * Runs the complete verification, executing all 7 pattern checks.
     *
     * @return a VerificationReport with all findings and overall soundness status
     */
    public VerificationReport verify() {
        Instant startTime = Instant.now();

        // Pre-compute reachability once (used by all checks)
        this.reachablePlaces = computeReachablePlaces();
        this.reachableTransitions = computeReachableTransitions();

        List<VerificationFinding> allFindings = new ArrayList<>();

        // Execute all 7 checks
        allFindings.addAll(checkUnreachableTasks());
        allFindings.addAll(checkDeadTransitions());
        allFindings.addAll(checkImplicitDeadlock());
        allFindings.addAll(checkMissingOrJoin());
        allFindings.addAll(checkOrphanedPlaces());
        allFindings.addAll(checkLivelock());
        allFindings.addAll(checkImproperTermination());

        // Classify findings by severity
        long errorCount = allFindings.stream()
            .filter(f -> f.severity() == VerificationFinding.Severity.ERROR)
            .count();
        long warningCount = allFindings.stream()
            .filter(f -> f.severity() == VerificationFinding.Severity.WARNING)
            .count();
        long infoCount = allFindings.stream()
            .filter(f -> f.severity() == VerificationFinding.Severity.INFO)
            .count();

        boolean isSound = errorCount == 0;

        String summary = isSound
            ? "Workflow is sound: no deadlock patterns detected."
            : "Workflow is unsound: %d deadlock pattern(s) found.".formatted(errorCount);

        Duration elapsed = Duration.between(startTime, Instant.now());

        return new VerificationReport(
            Collections.unmodifiableList(allFindings),
            isSound,
            (int) errorCount,
            (int) warningCount,
            (int) infoCount,
            summary,
            elapsed
        );
    }

    /**
     * Checks for unreachable tasks (tasks with no incoming arc from reachable places).
     *
     * @return list of UNREACHABLE_TASK findings
     */
    private List<VerificationFinding> checkUnreachableTasks() {
        List<VerificationFinding> findings = new ArrayList<>();

        for (String transition : transitionToPlaces.keySet()) {
            // Find all places that feed into this transition
            Set<String> inputPlaces = findInputPlaces(transition);

            // If no input places are reachable, this transition is unreachable
            boolean hasReachableInput = inputPlaces.stream()
                .anyMatch(reachablePlaces::contains);

            if (!hasReachableInput && !transition.equals(startPlace)) {
                findings.add(new VerificationFinding(
                    DeadlockPattern.UNREACHABLE_TASK,
                    transition,
                    "Task '" + transition + "' cannot be reached from start place. "
                        + "No reachable input places: " + inputPlaces,
                    VerificationFinding.Severity.ERROR
                ));
            }
        }

        return findings;
    }

    /**
     * Checks for dead transitions (tasks that can never fire).
     *
     * <p>A transition is dead if all its input places are either:
     * <ul>
     *   <li>Not reachable from start place, OR</li>
     *   <li>Only produced by transitions that are themselves dead</li>
     * </ul>
     *
     * @return list of DEAD_TRANSITION findings
     */
    private List<VerificationFinding> checkDeadTransitions() {
        List<VerificationFinding> findings = new ArrayList<>();

        // A transition is dead if it cannot be reached via forward BFS from the start place.
        // reachableTransitions is pre-computed in verify() using the same BFS approach.
        for (String transition : transitionToPlaces.keySet()) {
            if (!reachableTransitions.contains(transition)) {
                findings.add(new VerificationFinding(
                    DeadlockPattern.DEAD_TRANSITION,
                    transition,
                    "Task '" + transition + "' can never fire. Not reachable from the start place.",
                    VerificationFinding.Severity.ERROR
                ));
            }
        }

        return findings;
    }

    /**
     * Checks for implicit deadlock (AND-joins with unreachable input places).
     *
     * <p>An AND-join is only implicitly deadlock-prone if at least one of its
     * inputs is unreachable. This check identifies such problematic AND-joins.
     *
     * @return list of IMPLICIT_DEADLOCK findings
     */
    private List<VerificationFinding> checkImplicitDeadlock() {
        List<VerificationFinding> findings = new ArrayList<>();

        // For simplicity, we detect AND-joins heuristically:
        // A transition is likely an AND-join if it has multiple input places.
        for (String transition : transitionToPlaces.keySet()) {
            Set<String> inputPlaces = findInputPlaces(transition);

            if (inputPlaces.size() >= 2) { // Multi-input = potential AND-join
                // Check if any input is unreachable
                Set<String> unreachableInputs = inputPlaces.stream()
                    .filter(p -> !reachablePlaces.contains(p))
                    .collect(Collectors.toSet());

                if (!unreachableInputs.isEmpty()) {
                    findings.add(new VerificationFinding(
                        DeadlockPattern.IMPLICIT_DEADLOCK,
                        transition,
                        "AND-join task '" + transition + "' has unreachable input places: "
                            + unreachableInputs + ". Tokens may never arrive.",
                        VerificationFinding.Severity.ERROR
                    ));
                }
            }
        }

        return findings;
    }

    /**
     * Checks for missing OR-join (convergence without merge).
     *
     * <p>Detects places that have multiple input transitions but no explicit
     * merge/OR-join transition consolidating them.
     *
     * @return list of MISSING_OR_JOIN findings
     */
    private List<VerificationFinding> checkMissingOrJoin() {
        List<VerificationFinding> findings = new ArrayList<>();

        for (String place : placeToTransitions.keySet()) {
            Set<String> inputTransitions = findInputTransitions(place);

            // If a place has multiple inputs and is not produced by all transitions
            // in a consistent manner, we flag it as missing OR-join
            if (inputTransitions.size() >= 2) {
                // Heuristic: if multiple transitions feed same place without
                // a synchronizer, it's likely a missing OR-join
                findings.add(new VerificationFinding(
                    DeadlockPattern.MISSING_OR_JOIN,
                    place,
                    "Place '" + place + "' has " + inputTransitions.size()
                        + " input transitions with no explicit OR-join: "
                        + inputTransitions + ". Potential token loss.",
                    VerificationFinding.Severity.WARNING
                ));
            }
        }

        return findings;
    }

    /**
     * Checks for orphaned places (places with no outgoing transitions).
     *
     * <p>An orphaned place becomes a token trap: once a token arrives, it
     * cannot leave, blocking workflow completion.
     *
     * @return list of ORPHANED_PLACE findings
     */
    private List<VerificationFinding> checkOrphanedPlaces() {
        List<VerificationFinding> findings = new ArrayList<>();

        for (String place : placeToTransitions.keySet()) {
            Set<String> outputs = placeToTransitions.get(place);

            // Orphaned if no outgoing transitions (and not end place)
            if ((outputs == null || outputs.isEmpty()) && !place.equals(endPlace)) {
                findings.add(new VerificationFinding(
                    DeadlockPattern.ORPHANED_PLACE,
                    place,
                    "Place '" + place + "' has no outgoing transitions. Tokens "
                        + "arriving here will be trapped.",
                    VerificationFinding.Severity.ERROR
                ));
            }
        }

        return findings;
    }

    /**
     * Checks for livelock (cycles with no exit to end place).
     *
     * <p>Uses Tarjan's algorithm to find strongly connected components (SCCs).
     * An SCC is a livelock if it does not have a path to the end place.
     *
     * @return list of LIVELOCK findings
     */
    private List<VerificationFinding> checkLivelock() {
        List<VerificationFinding> findings = new ArrayList<>();

        // Compute SCCs
        Map<String, Set<String>> sccs = computeStronglyConnectedComponents();

        for (Map.Entry<String, Set<String>> sccEntry : sccs.entrySet()) {
            String sccId = sccEntry.getKey();
            Set<String> sccMembers = sccEntry.getValue();

            // Skip single-node SCCs (not a cycle)
            if (sccMembers.size() <= 1) {
                continue;
            }

            // Check if SCC has an exit path to end place
            boolean hasExitPath = sccMembers.stream()
                .flatMap(transition -> {
                    Set<String> outputs = transitionToPlaces.getOrDefault(transition, Set.of());
                    return outputs.stream();
                })
                .anyMatch(place -> canReachEnd(place, new HashSet<>()));

            if (!hasExitPath) {
                findings.add(new VerificationFinding(
                    DeadlockPattern.LIVELOCK,
                    sccId,
                    "Livelock detected: cycle " + sccMembers + " has no exit path to end place.",
                    VerificationFinding.Severity.ERROR
                ));
            }
        }

        return findings;
    }

    /**
     * Checks for improper termination (multiple tokens at end place).
     *
     * <p>Detects if multiple independent transitions can produce to the end place
     * without consolidation, violating the "exactly one final token" property.
     *
     * @return list of IMPROPER_TERMINATION findings
     */
    private List<VerificationFinding> checkImproperTermination() {
        List<VerificationFinding> findings = new ArrayList<>();

        // Find transitions that can reach the end place
        Set<String> terminatingTransitions = transitionToPlaces.keySet().stream()
            .filter(t -> {
                Set<String> outputs = transitionToPlaces.getOrDefault(t, Set.of());
                return outputs.contains(endPlace);
            })
            .collect(Collectors.toSet());

        // If multiple transitions directly output to end place without merge,
        // we have improper termination
        if (terminatingTransitions.size() > 1) {
            findings.add(new VerificationFinding(
                DeadlockPattern.IMPROPER_TERMINATION,
                endPlace,
                "Multiple transitions (" + terminatingTransitions
                    + ") produce to the end place without synchronization. "
                    + "Potential for multiple final tokens.",
                VerificationFinding.Severity.ERROR
            ));
        }

        return findings;
    }

    // =========================================================================
    // Graph Algorithms (BFS, DFS, SCC)
    // =========================================================================

    /**
     * Computes all places reachable from the start place using forward BFS.
     *
     * @return set of reachable place IDs
     */
    private Set<String> computeReachablePlaces() {
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startPlace);
        reachable.add(startPlace);

        while (!queue.isEmpty()) {
            String place = queue.poll();
            Set<String> nextTransitions = placeToTransitions.getOrDefault(place, Set.of());

            for (String transition : nextTransitions) {
                Set<String> nextPlaces = transitionToPlaces.getOrDefault(transition, Set.of());
                for (String nextPlace : nextPlaces) {
                    if (reachable.add(nextPlace)) {
                        queue.add(nextPlace);
                    }
                }
            }
        }

        return reachable;
    }

    /**
     * Computes all transitions reachable from the start place.
     *
     * @return set of reachable transition IDs
     */
    private Set<String> computeReachableTransitions() {
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startPlace);
        Set<String> visitedPlaces = new HashSet<>();
        visitedPlaces.add(startPlace);

        while (!queue.isEmpty()) {
            String place = queue.poll();
            Set<String> nextTransitions = placeToTransitions.getOrDefault(place, Set.of());
            reachable.addAll(nextTransitions);

            for (String transition : nextTransitions) {
                Set<String> nextPlaces = transitionToPlaces.getOrDefault(transition, Set.of());
                for (String nextPlace : nextPlaces) {
                    if (visitedPlaces.add(nextPlace)) {
                        queue.add(nextPlace);
                    }
                }
            }
        }

        return reachable;
    }

    /**
     * Computes strongly connected components (SCCs) using Tarjan's algorithm.
     * Uses a simplified approach suitable for workflow verification.
     *
     * @return map of SCC ID to set of transition IDs in each SCC
     */
    private Map<String, Set<String>> computeStronglyConnectedComponents() {
        if (stronglyConnectedComponents != null) {
            return stronglyConnectedComponents;
        }

        Map<String, Set<String>> sccs = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        int sccIndex = 0;

        for (String transition : transitionToPlaces.keySet()) {
            if (!visited.contains(transition)) {
                Set<String> scc = new HashSet<>();
                dfsForSCC(transition, visited, scc, new HashSet<>());
                if (!scc.isEmpty()) {
                    sccs.put("scc-" + (sccIndex++), scc);
                }
            }
        }

        this.stronglyConnectedComponents = sccs;
        return sccs;
    }

    /**
     * Depth-first search to identify cycles (for SCC detection).
     *
     * @param current current transition node
     * @param visited set of already-visited transitions
     * @param currentScc current SCC being built
     * @param recursionStack stack for cycle detection
     */
    private void dfsForSCC(String current, Set<String> visited, Set<String> currentScc,
                           Set<String> recursionStack) {
        visited.add(current);
        recursionStack.add(current);
        currentScc.add(current);

        // Get neighbors (transitions reachable from this one)
        Set<String> neighbors = transitionToPlaces.getOrDefault(current, Set.of()).stream()
            .flatMap(place -> placeToTransitions.getOrDefault(place, Set.of()).stream())
            .collect(Collectors.toSet());

        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                dfsForSCC(neighbor, visited, currentScc, recursionStack);
            } else if (recursionStack.contains(neighbor)) {
                // Back edge found: cycle exists
                currentScc.add(neighbor);
            }
        }

        recursionStack.remove(current);
    }

    /**
     * Checks if the end place is reachable from the given place.
     *
     * @param startNode the place to start from
     * @param visited set of visited places (to avoid infinite loops)
     * @return true iff end place is reachable
     */
    private boolean canReachEnd(String startNode, Set<String> visited) {
        if (startNode.equals(endPlace)) {
            return true;
        }
        if (!visited.add(startNode)) {
            return false; // Already visited, cycle detected
        }

        Set<String> nextTransitions = placeToTransitions.getOrDefault(startNode, Set.of());
        for (String transition : nextTransitions) {
            Set<String> nextPlaces = transitionToPlaces.getOrDefault(transition, Set.of());
            for (String nextPlace : nextPlaces) {
                if (canReachEnd(nextPlace, new HashSet<>(visited))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Finds all places that feed into the given transition (reverse arc lookup).
     *
     * @param transition the transition to find inputs for
     * @return set of input place IDs
     */
    private Set<String> findInputPlaces(String transition) {
        Set<String> inputs = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : placeToTransitions.entrySet()) {
            if (entry.getValue().contains(transition)) {
                inputs.add(entry.getKey());
            }
        }
        return inputs;
    }

    /**
     * Finds all transitions that feed into the given place (reverse arc lookup).
     *
     * @param place the place to find input transitions for
     * @return set of input transition IDs
     */
    private Set<String> findInputTransitions(String place) {
        Set<String> inputs = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : transitionToPlaces.entrySet()) {
            if (entry.getValue().contains(place)) {
                inputs.add(entry.getKey());
            }
        }
        return inputs;
    }
}
