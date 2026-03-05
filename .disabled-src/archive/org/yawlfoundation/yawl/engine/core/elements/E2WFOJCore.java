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

package org.yawlfoundation.yawl.engine.core.elements;

import org.yawlfoundation.yawl.elements.YNetElement;
import org.yawlfoundation.yawl.elements.e2wfoj.CombinationGenerator;
import org.yawlfoundation.yawl.elements.e2wfoj.RElement;
import org.yawlfoundation.yawl.elements.e2wfoj.RFlow;
import org.yawlfoundation.yawl.elements.e2wfoj.RMarking;
import org.yawlfoundation.yawl.elements.e2wfoj.RPlace;
import org.yawlfoundation.yawl.elements.e2wfoj.RSetOfMarkings;
import org.yawlfoundation.yawl.elements.e2wfoj.RTransition;
import org.yawlfoundation.yawl.engine.core.marking.IMarkingTask;
import org.yawlfoundation.yawl.engine.core.marking.YCoreMarking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Canonical implementation of the E2WFOJ Reset-net algorithm for OR-join analysis.
 *
 * <p>This is the Phase 1 deduplication result for {@code E2WFOJNet}.  Both the
 * stateful {@code org.yawlfoundation.yawl.elements.e2wfoj.E2WFOJNet} and the
 * stateless {@code org.yawlfoundation.yawl.stateless.elements.e2wfoj.E2WFOJNet}
 * are now thin wrappers that delegate to this class.</p>
 *
 * <p>Compared to the original tree-specific implementations, the type substitutions
 * that enable sharing are:
 * <ul>
 *   <li>{@code YTask} parameter types → {@link IMarkingTask}
 *       (both trees' {@code YTask} implement this interface)</li>
 *   <li>{@code instanceof YTask} checks → {@code instanceof IMarkingTask}</li>
 *   <li>{@code YCondition} checks → {@code !(element instanceof IMarkingTask)}
 *       (any net element that is not a task is a condition for Reset-net purposes)</li>
 *   <li>{@code YExternalNetElement} → {@link YNetElement} (shared base type)</li>
 *   <li>{@code YMarking} → {@link YCoreMarking} (shared marking base type)</li>
 *   <li>{@code YNet.getNetElements()} → {@link INetElementProvider#getNetElements()}
 *       (functional interface supplied by the tree-specific wrapper constructor)</li>
 *   <li>{@code YTask._AND/_OR/_XOR} constants → {@link IMarkingTask#_AND/_OR/_XOR}
 *       (same numeric values in both trees)</li>
 * </ul></p>
 *
 * @author YAWL Foundation (original E2WFOJNet)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 * @since 5.2
 */
public final class E2WFOJCore {

    private final Map<String, RTransition> transitions = new HashMap<>(100);
    private final Map<String, RPlace> places = new HashMap<>(100);
    private Map<String, RTransition> orJoinTransitions = new HashMap<>();
    private Map<String, IMarkingTask> yawlOrJoins = new HashMap<>();
    private INetElementProvider net;
    private Set<RMarking> alreadyConsideredMarkings = new HashSet<>(100);
    private Set<YNetElement> conditions = new HashSet<>(100);

    /**
     * Constructs a Reset net from a YAWL net for OR-join analysis.
     *
     * @param net    provider of the YAWL net elements (pass {@code yNet::getNetElements})
     * @param orJoin the OR-join task being analyzed
     */
    public E2WFOJCore(INetElementProvider net, IMarkingTask orJoin) {
        this.net = Objects.requireNonNull(net, "net cannot be null");
        Objects.requireNonNull(orJoin, "OR-join task cannot be null");
        convertToResetNet();
        removeOrJoin(orJoin);
        // Clean up references no longer needed after construction
        this.orJoinTransitions = null;
        this.yawlOrJoins = null;
        this.net = null;
    }

    /**
     * Private constructor for testing purposes.
     */
    private E2WFOJCore() {
        // No-op constructor for testing
    }

    /**
     * Converts a YAWL net into a Reset net representation.
     */
    private void convertToResetNet() {
        Map<String, ? extends YNetElement> netElements = net.getNetElements();

        // Generate places from conditions and tasks
        for (YNetElement element : netElements.values()) {
            if (element instanceof IMarkingTask) {
                // Tasks get an internal place p_<id>
                RPlace place = new RPlace("p_" + element.getID());
                places.put(place.getID(), place);
            } else {
                // Conditions get a place named by their own ID
                RPlace place = new RPlace(element.getID());
                places.put(place.getID(), place);
                conditions.add(element);
            }
        }

        Map<String, RTransition> startTransitions = new HashMap<>();
        Map<String, RTransition> endTransitions = new HashMap<>();

        // Generate transitions based on task join/split types
        for (YNetElement element : netElements.values()) {
            if (element instanceof IMarkingTask task) {
                createStartTransition(task, startTransitions);
                createEndTransition(task, endTransitions);
            }
        }

        transitions.putAll(startTransitions);
        transitions.putAll(endTransitions);
    }

    /**
     * Creates the start transition(s) for a task based on its join type.
     */
    private void createStartTransition(IMarkingTask task,
                                        Map<String, RTransition> startTransitions) {
        int joinType = task.getJoinType();
        String taskId = ((YNetElement) task).getID();

        if (joinType == IMarkingTask._AND) {
            RTransition transition = new RTransition(taskId + "_start");
            startTransitions.put(transition.getID(), transition);

            for (YNetElement preElement : task.getPresetElements()) {
                RFlow inflow = new RFlow(places.get(preElement.getID()), transition);
                transition.setPreset(inflow);

                RFlow outflow = new RFlow(transition, places.get("p_" + taskId));
                transition.setPostset(outflow);
            }
        } else if (joinType == IMarkingTask._XOR) {
            for (YNetElement preElement : task.getPresetElements()) {
                RTransition transition = new RTransition(
                        taskId + "_start^" + preElement.getID());
                startTransitions.put(transition.getID(), transition);

                RFlow inflow = new RFlow(places.get(preElement.getID()), transition);
                transition.setPreset(inflow);

                RFlow outflow = new RFlow(transition, places.get("p_" + taskId));
                transition.setPostset(outflow);
            }
        } else if (joinType == IMarkingTask._OR) {
            RTransition transition = new RTransition(taskId + "_start");
            startTransitions.put(transition.getID(), transition);
            orJoinTransitions.put(transition.getID(), transition);
            yawlOrJoins.put(taskId, task);
        }
    }

    /**
     * Creates the end transition(s) for a task based on its split type.
     */
    private void createEndTransition(IMarkingTask task,
                                      Map<String, RTransition> endTransitions) {
        int splitType = task.getSplitType();

        if (splitType == IMarkingTask._AND) {
            createAndSplitEndTransition(task, endTransitions);
        } else if (splitType == IMarkingTask._XOR) {
            createXorSplitEndTransition(task, endTransitions);
        } else if (splitType == IMarkingTask._OR) {
            createOrSplitEndTransition(task, endTransitions);
        }
    }

    /**
     * Creates AND split end transition with all postset elements.
     */
    private void createAndSplitEndTransition(IMarkingTask task,
                                              Map<String, RTransition> endTransitions) {
        String taskId = ((YNetElement) task).getID();
        RTransition transition = new RTransition(taskId + "_end");
        endTransitions.put(transition.getID(), transition);

        for (YNetElement postElement : task.getPostsetElements()) {
            RFlow inflow = new RFlow(places.get("p_" + taskId), transition);
            transition.setPreset(inflow);

            RFlow outflow = new RFlow(transition, places.get(postElement.getID()));
            transition.setPostset(outflow);
        }

        Set<YNetElement> removeSet = new HashSet<>(task.getRemoveSet());
        if (!removeSet.isEmpty()) {
            addCancelSet(transition, removeSet);
        }
    }

    /**
     * Creates XOR split end transitions - one per postset element.
     */
    private void createXorSplitEndTransition(IMarkingTask task,
                                              Map<String, RTransition> endTransitions) {
        String taskId = ((YNetElement) task).getID();
        for (YNetElement postElement : task.getPostsetElements()) {
            RTransition transition = new RTransition(taskId + "_end^" + postElement.getID());
            endTransitions.put(transition.getID(), transition);

            RFlow inflow = new RFlow(places.get("p_" + taskId), transition);
            transition.setPreset(inflow);

            RFlow outflow = new RFlow(transition, places.get(postElement.getID()));
            transition.setPostset(outflow);

            Set<YNetElement> removeSet = new HashSet<>(task.getRemoveSet());
            if (!removeSet.isEmpty()) {
                addCancelSet(transition, removeSet);
            }
        }
    }

    /**
     * Creates OR split end transitions - generates all non-empty subset combinations.
     */
    private void createOrSplitEndTransition(IMarkingTask task,
                                             Map<String, RTransition> endTransitions) {
        String taskId = ((YNetElement) task).getID();
        Set<? extends YNetElement> postElements = task.getPostsetElements();
        Set<Set<YNetElement>> allSubsets = new HashSet<>();

        // Generate all non-empty subsets of postset
        for (int size = 1; size <= postElements.size(); size++) {
            allSubsets.addAll(generateCombinations(postElements, size));
        }

        for (Set<YNetElement> subset : allSubsets) {
            String transitionId = buildTransitionId(taskId, "_end^{", subset, " ");
            RTransition transition = new RTransition(transitionId);
            endTransitions.put(transition.getID(), transition);

            RFlow inflow = new RFlow(places.get("p_" + taskId), transition);
            transition.setPreset(inflow);

            for (YNetElement postElement : subset) {
                RFlow outflow = new RFlow(transition, places.get(postElement.getID()));
                transition.setPostset(outflow);
            }

            Set<YNetElement> removeSet = new HashSet<>(task.getRemoveSet());
            if (!removeSet.isEmpty()) {
                addCancelSet(transition, removeSet);
            }
        }
    }

    /**
     * Builds a transition ID from task prefix, suffix, and element subset.
     */
    private String buildTransitionId(String prefix, String suffix,
                                     Set<YNetElement> elements, String delimiter) {
        StringBuilder builder = new StringBuilder(prefix);
        builder.append(suffix);
        for (YNetElement element : elements) {
            builder.append(element.getID()).append(delimiter);
        }
        builder.append('}');
        return builder.toString();
    }

    /**
     * Generates all combinations of the specified size from the given set.
     */
    private Set<Set<YNetElement>> generateCombinations(
            Set<? extends YNetElement> netElements, int size) {
        Set<Set<YNetElement>> subsets = new HashSet<>();
        Object[] elements = netElements.toArray();
        CombinationGenerator generator = new CombinationGenerator(elements.length, size);

        while (generator.hasMore()) {
            Set<YNetElement> combination = new HashSet<>();
            int[] indices = generator.getNext();
            for (int index : indices) {
                combination.add((YNetElement) elements[index]);
            }
            subsets.add(combination);
        }
        return subsets;
    }

    /**
     * Associates a cancellation set with a transition.
     */
    private void addCancelSet(RTransition transition, Set<YNetElement> removeSet) {
        Set<RPlace> resetPlaces = new HashSet<>();

        // Process conditions in the remove set
        Set<YNetElement> conditionRemoves = new HashSet<>(removeSet);
        conditionRemoves.retainAll(conditions);
        for (YNetElement condition : conditionRemoves) {
            RPlace place = places.get(condition.getID());
            if (place != null) {
                resetPlaces.add(place);
            }
        }

        // Process tasks in the remove set (use internal place p_<id>)
        Set<YNetElement> taskRemoves = new HashSet<>(removeSet);
        taskRemoves.removeAll(conditions);
        for (YNetElement task : taskRemoves) {
            RPlace place = places.get("p_" + task.getID());
            if (place != null) {
                resetPlaces.add(place);
            }
        }

        transition.setRemoveSet(resetPlaces);
    }

    /**
     * Removes the specified OR-join task and converts other OR-joins to XOR semantics.
     */
    private void removeOrJoin(IMarkingTask orJoin) {
        String orJoinId = ((YNetElement) orJoin).getID();
        yawlOrJoins.remove(orJoinId);

        // Remove OR-join transitions from the net
        for (RTransition rj : orJoinTransitions.values()) {
            transitions.remove(rj.getID());
        }

        // Convert remaining OR-joins to XOR semantics
        for (IMarkingTask otherOrJoin : yawlOrJoins.values()) {
            String otherId = ((YNetElement) otherOrJoin).getID();
            for (YNetElement preElement : otherOrJoin.getPresetElements()) {
                RTransition transition = new RTransition(
                        otherId + "_start^" + preElement.getID());
                transitions.put(transition.getID(), transition);

                RFlow inflow = new RFlow(places.get(preElement.getID()), transition);
                transition.setPreset(inflow);

                RFlow outflow = new RFlow(transition, places.get("p_" + otherId));
                transition.setPostset(outflow);
            }
        }
    }

    /**
     * Performs structural restriction of the reset net based on an OR-join task.
     *
     * @param orJoin the OR-join task for structural restriction
     */
    public void restrictNet(IMarkingTask orJoin) {
        Set<RTransition> restrictedTransitions = new HashSet<>();
        Set<RPlace> restrictedPlaces = new HashSet<>();

        // Convert YAWL preset to Reset net places
        Set<RPlace> presetPlaces = new HashSet<>();
        for (YNetElement element : orJoin.getPresetElements()) {
            if (!(element instanceof IMarkingTask)) {
                // condition
                RPlace place = places.get(element.getID());
                if (place != null) {
                    presetPlaces.add(place);
                }
            }
        }

        // Backward reachability pass
        Set<RTransition> currentTransitions = getPresetPlaces(presetPlaces);
        restrictedTransitions.addAll(currentTransitions);
        restrictedPlaces.addAll(presetPlaces);

        Set<RTransition> previousTransitions = new HashSet<>();
        while (!previousTransitions.equals(restrictedTransitions)) {
            previousTransitions = new HashSet<>(restrictedTransitions);
            presetPlaces = getPreset(currentTransitions);
            currentTransitions = getPresetPlaces(presetPlaces);
            restrictedTransitions.addAll(currentTransitions);
            restrictedPlaces.addAll(presetPlaces);
        }

        performRestriction(restrictedTransitions, restrictedPlaces);
    }

    /**
     * Performs active projection restriction based on a marking.
     *
     * @param marking the current marking for active projection
     */
    public void restrictNet(YCoreMarking marking) {
        Set<RPlace> markedPlaces = new HashSet<>();
        List<YNetElement> yMarked = new ArrayList<>(marking.getLocations());

        // Convert YAWL marking to Reset net places
        for (YNetElement element : yMarked) {
            if (element instanceof IMarkingTask) {
                RPlace place = places.get("p_" + element.getID());
                if (place != null) {
                    markedPlaces.add(place);
                }
            } else {
                // condition
                RPlace place = places.get(element.getID());
                if (place != null) {
                    markedPlaces.add(place);
                }
            }
        }

        // Forward reachability pass
        Set<RTransition> restrictedTransitions = new HashSet<>();
        Set<RPlace> restrictedPlaces = new HashSet<>();
        Set<RTransition> currentTransitions = getPostset(markedPlaces);
        restrictedTransitions.addAll(currentTransitions);
        restrictedPlaces.addAll(markedPlaces);

        Set<RTransition> previousTransitions = new HashSet<>();
        while (!previousTransitions.equals(restrictedTransitions)) {
            previousTransitions = new HashSet<>(restrictedTransitions);
            Set<RPlace> postsetPlaces = getPostsetTransitions(currentTransitions);
            currentTransitions = getPostset(postsetPlaces);
            restrictedTransitions.addAll(currentTransitions);
            restrictedPlaces.addAll(postsetPlaces);
        }

        // Remove transitions whose presets are not fully contained
        Set<RTransition> toRemove = new HashSet<>();
        for (RTransition transition : restrictedTransitions) {
            if (!restrictedPlaces.containsAll(transition.getPresetElements())) {
                toRemove.add(transition);
            }
        }
        restrictedTransitions.removeAll(toRemove);

        performRestriction(restrictedTransitions, restrictedPlaces);
    }

    /**
     * Performs the actual restriction by removing irrelevant elements.
     */
    private void performRestriction(Set<RTransition> restrictedTransitions,
                                    Set<RPlace> restrictedPlaces) {
        // Remove irrelevant transitions
        Set<RTransition> irrelevantTransitions = new HashSet<>(transitions.values());
        irrelevantTransitions.removeAll(restrictedTransitions);
        for (RTransition transition : irrelevantTransitions) {
            transitions.remove(transition.getID());
        }

        // Update remaining transitions
        for (RTransition transition : restrictedTransitions) {
            // Update remove set for cancel transitions
            if (transition.isCancelTransition()) {
                Set<RPlace> removeSet = new HashSet<>(transition.getRemoveSet());
                removeSet.retainAll(restrictedPlaces);
            }

            // Remove postset places not in restricted set
            removeElementsFromFlows(transition, restrictedPlaces,
                    transition.getPostsetElements(), true);

            // Remove preset places not in restricted set
            removeElementsFromFlows(transition, restrictedPlaces,
                    transition.getPresetElements(), false);
        }

        // Remove irrelevant places
        Set<RPlace> irrelevantPlaces = new HashSet<>(places.values());
        irrelevantPlaces.removeAll(restrictedPlaces);
        for (RPlace place : irrelevantPlaces) {
            places.remove(place.getID());

            // Remove preset/postset transitions not in restricted set
            removeElementsFromFlows(place, restrictedTransitions,
                    place.getPresetElements(), false);
            removeElementsFromFlows(place, restrictedTransitions,
                    place.getPostsetElements(), true);
        }
    }

    /**
     * Removes elements from flow relations that are not in the restricted set.
     */
    private <T extends RElement> void removeElementsFromFlows(
            RElement element, Set<? extends RElement> restrictedSet,
            Set<? extends RElement> elementsToRemove, boolean isPostset) {

        Set<RElement> toRemove = new HashSet<>(elementsToRemove);
        toRemove.removeAll(restrictedSet);

        if (!toRemove.isEmpty()) {
            Map<String, RFlow> flows = new HashMap<>(
                    isPostset ? element.getPostsetFlows() : element.getPresetFlows());
            for (RElement removeElement : toRemove) {
                flows.remove(removeElement.getID());
            }
            if (isPostset) {
                element.setPostsetFlows(flows);
            } else {
                element.setPresetFlows(flows);
            }
        }
    }

    /**
     * Gets the postset transitions of a collection of places.
     */
    private static Set<RTransition> getPostset(Set<RPlace> places) {
        return places.stream()
                .map(RElement::getPostsetElements)
                .flatMap(Set::stream)
                .map(e -> (RTransition) e)
                .collect(Collectors.toSet());
    }

    /**
     * Gets the postset places of a collection of transitions.
     */
    private static Set<RPlace> getPostsetTransitions(Set<RTransition> transitions) {
        return transitions.stream()
                .map(RElement::getPostsetElements)
                .flatMap(Set::stream)
                .map(e -> (RPlace) e)
                .collect(Collectors.toSet());
    }

    /**
     * Gets the preset places of a collection of transitions.
     */
    private static Set<RPlace> getPreset(Set<RTransition> transitions) {
        return transitions.stream()
                .map(RElement::getPresetElements)
                .flatMap(Set::stream)
                .map(e -> (RPlace) e)
                .collect(Collectors.toSet());
    }

    /**
     * Gets the preset transitions of a collection of places.
     */
    private static Set<RTransition> getPresetPlaces(Set<RPlace> places) {
        return places.stream()
                .map(RElement::getPresetElements)
                .flatMap(Set::stream)
                .map(e -> (RTransition) e)
                .collect(Collectors.toSet());
    }

    /**
     * Determines if a marking s' less than or equal to s is coverable from the
     * predecessors of marking t.
     */
    private boolean isCoverable(RMarking source, RMarking target) {
        alreadyConsideredMarkings = new HashSet<>();

        RSetOfMarkings targetSet = new RSetOfMarkings();
        targetSet.addMarking(target);

        RSetOfMarkings predecessors = computeFiniteBasisPredecessors(targetSet);

        for (RMarking predecessor : predecessors.getMarkings()) {
            if (predecessor.isLessThanOrEqual(source)) {
                alreadyConsideredMarkings = null;
                return true;
            }
        }

        alreadyConsideredMarkings = null;
        return false;
    }

    /**
     * Computes the finite basis of predecessors for a set of markings.
     */
    private RSetOfMarkings computeFiniteBasisPredecessors(RSetOfMarkings initial) {
        RSetOfMarkings current = new RSetOfMarkings();
        RSetOfMarkings next = new RSetOfMarkings();
        RSetOfMarkings predecessors = new RSetOfMarkings();

        current.addAll(initial);
        predecessors.addAll(current);
        next = getMinimalCoveringSet(computePredecessors(current), predecessors);

        while (!current.equals(next)) {
            current.removeAll();
            current.addAll(next);
            predecessors.removeAll();
            predecessors.addAll(current);
            next = getMinimalCoveringSet(computePredecessors(current), predecessors);
        }

        return current;
    }

    /**
     * Computes the predecessor markings for a set of markings.
     */
    private RSetOfMarkings computePredecessors(RSetOfMarkings markings) {
        RSetOfMarkings result = new RSetOfMarkings();

        for (RMarking marking : markings.getMarkings()) {
            result.addAll(computePredecessors(marking));
        }

        return getMinimalCoveringSet(result);
    }

    /**
     * Computes the predecessor markings for a single marking.
     */
    private RSetOfMarkings computePredecessors(RMarking marking) {
        RSetOfMarkings result = new RSetOfMarkings();

        if (!alreadyConsideredMarkings.contains(marking)) {
            for (RTransition transition : transitions.values()) {
                if (isBackwardsEnabled(marking, transition)) {
                    RMarking predecessor = getPreviousMarking(marking, transition);
                    if (!predecessor.isBiggerThanOrEqual(marking)) {
                        result.addMarking(predecessor);
                    }
                }
            }
            alreadyConsideredMarkings.add(marking);
        }

        return result;
    }

    /**
     * Determines if a transition is backwards enabled at a given marking.
     */
    private boolean isBackwardsEnabled(RMarking marking, RTransition transition) {
        Set<RPlace> postSet = transition.getPostsetPlaces();
        Set<RPlace> removeSet = transition.getRemoveSet();
        Map<String, Integer> markedPlaces = marking.getMarkedPlaces();

        if (removeSet.isEmpty()) {
            return true;
        }

        for (RPlace place : removeSet) {
            String placeName = place.getID();

            if (markedPlaces.containsKey(placeName)) {
                if (postSet.contains(place)) {
                    int count = markedPlaces.get(placeName);
                    if (count > 1) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Computes the marking before a transition fires (backwards firing).
     */
    private RMarking getPreviousMarking(RMarking currentMarking, RTransition transition) {
        Map<String, Integer> previousPlaces = new HashMap<>(currentMarking.getMarkedPlaces());
        Set<RPlace> postSet = new HashSet<>(transition.getPostsetPlaces());
        Set<RPlace> preSet = new HashSet<>(transition.getPresetPlaces());
        Set<RPlace> removeSet = new HashSet<>(transition.getRemoveSet());

        // Remove one token from each postset place (excluding reset places)
        postSet.removeAll(removeSet);
        for (RPlace place : postSet) {
            String placeName = place.getID();
            if (previousPlaces.containsKey(placeName)) {
                int count = previousPlaces.get(placeName);
                if (count == 1) {
                    previousPlaces.remove(placeName);
                } else if (count > 1) {
                    previousPlaces.put(placeName, count - 1);
                }
            }
        }

        // Add one token to each preset place (excluding reset places)
        preSet.removeAll(removeSet);
        for (RPlace place : preSet) {
            String placeName = place.getID();
            int newCount = previousPlaces.getOrDefault(placeName, 0) + 1;
            previousPlaces.put(placeName, newCount);
        }

        // Add one token to reset places that are also in the preset
        removeSet.retainAll(transition.getPresetElements());
        for (RPlace place : removeSet) {
            previousPlaces.put(place.getID(), 1);
        }

        return new RMarking(previousPlaces);
    }

    /**
     * Determines whether an OR-join task should be enabled at a given marking.
     *
     * @param marking the current marking
     * @param orJoin  the OR-join task to check
     * @return true if the OR-join should be enabled, false otherwise
     */
    public boolean orJoinEnabled(YCoreMarking marking, IMarkingTask orJoin) {
        // Convert YAWL marking to Reset net marking
        Map<String, Integer> resetMarking = new HashMap<>();
        Set<IMarkingTask> markedTasks = new HashSet<>();
        List<YNetElement> yLocations = new ArrayList<>(marking.getLocations());

        for (YNetElement element : yLocations) {
            if (element instanceof IMarkingTask task) {
                markedTasks.add(task);
            } else {
                // condition
                RPlace place = places.get(element.getID());
                if (place != null) {
                    String placeName = place.getID();
                    resetMarking.merge(placeName, 1, Integer::sum);
                }
            }
        }

        // Convert active tasks to internal places
        for (IMarkingTask task : markedTasks) {
            String taskId = ((YNetElement) task).getID();
            RPlace place = places.get("p_" + taskId);
            if (place != null) {
                resetMarking.put(place.getID(), 1);
            }
        }

        RMarking resetM = new RMarking(resetMarking);

        // Generate bigger-enabling markings for OR-join analysis
        Set<? extends YNetElement> presetConditions = orJoin.getPresetElements();
        Map<String, Integer> baseMarking = new HashMap<>();
        Set<RPlace> emptyPresetPlaces = new HashSet<>();

        for (YNetElement element : presetConditions) {
            if (!(element instanceof IMarkingTask)) {
                // condition
                RPlace place = places.get(element.getID());
                if (place != null) {
                    if (yLocations.contains(element)) {
                        baseMarking.put(place.getID(), 1);
                    } else {
                        emptyPresetPlaces.add(place);
                    }
                }
            }
        }

        // Check coverability for each bigger-enabling marking
        RSetOfMarkings biggerEnablingMarkings = new RSetOfMarkings();
        for (RPlace emptyPlace : emptyPresetPlaces) {
            Map<String, Integer> biggerMarking = new HashMap<>(baseMarking);
            biggerMarking.put(emptyPlace.getID(), 1);
            biggerEnablingMarkings.addMarking(new RMarking(biggerMarking));
        }

        for (RMarking biggerMarking : biggerEnablingMarkings.getMarkings()) {
            if (isCoverable(resetM, biggerMarking)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Computes the minimal covering set of markings (version without base set).
     */
    private RSetOfMarkings getMinimalCoveringSet(RSetOfMarkings markings) {
        RSetOfMarkings minimal = new RSetOfMarkings();
        minimal.addAll(markings);

        for (RMarking marking : markings.getMarkings()) {
            RSetOfMarkings others = new RSetOfMarkings();
            others.addAll(minimal);
            others.removeMarking(marking);

            for (RMarking other : others.getMarkings()) {
                if (marking.isBiggerThanOrEqual(other)) {
                    minimal.removeMarking(marking);
                    break;
                }
            }
        }

        return minimal;
    }

    /**
     * Computes the minimal covering set of markings with a base set.
     */
    private RSetOfMarkings getMinimalCoveringSet(RSetOfMarkings newMarkings,
                                                  RSetOfMarkings baseSet) {
        RSetOfMarkings minimal = new RSetOfMarkings();
        minimal.addAll(baseSet);
        minimal.addAll(newMarkings);

        for (RMarking newMarking : newMarkings.getMarkings()) {
            RSetOfMarkings others = new RSetOfMarkings();
            others.addAll(minimal);
            others.removeMarking(newMarking);

            for (RMarking other : others.getMarkings()) {
                if (newMarking.isBiggerThanOrEqual(other)) {
                    minimal.removeMarking(newMarking);
                    break;
                } else if (other.isBiggerThanOrEqual(newMarking)) {
                    minimal.removeMarking(other);
                }
            }
        }

        return minimal;
    }
}
