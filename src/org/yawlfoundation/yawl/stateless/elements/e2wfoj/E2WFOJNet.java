/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.stateless.elements.e2wfoj;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.yawlfoundation.yawl.elements.YNetElement;
import org.yawlfoundation.yawl.elements.e2wfoj.CombinationGenerator;
import org.yawlfoundation.yawl.elements.e2wfoj.RElement;
import org.yawlfoundation.yawl.elements.e2wfoj.RFlow;
import org.yawlfoundation.yawl.elements.e2wfoj.RMarking;
import org.yawlfoundation.yawl.elements.e2wfoj.RPlace;
import org.yawlfoundation.yawl.elements.e2wfoj.RSetOfMarkings;
import org.yawlfoundation.yawl.elements.e2wfoj.RTransition;
import org.yawlfoundation.yawl.stateless.elements.YCondition;
import org.yawlfoundation.yawl.stateless.elements.YExternalNetElement;
import org.yawlfoundation.yawl.stateless.elements.YNet;
import org.yawlfoundation.yawl.stateless.elements.YTask;
import org.yawlfoundation.yawl.stateless.elements.marking.YMarking;

/**
 * A Reset net formalisation of a YAWL net for OR-join analysis (stateless variant).
 * <p>
 * This class implements the E2WFOJ (Enabling Tree With Fair Or-Join) algorithm
 * for determining when an OR-join task should be enabled in a YAWL workflow.
 * The algorithm converts a YAWL net to a Reset net and uses backwards reachability
 * analysis to determine OR-join enablement.
 * </p>
 * <p>
 * This is the stateless variant that works with the stateless YAWL elements.
 * </p>
 *
 * @author YAWL Foundation
 * @since 2.0
 */
public final class E2WFOJNet {

    private final Map<String, RTransition> transitions = new HashMap<>(100);
    private final Map<String, RPlace> places = new HashMap<>(100);
    private Map<String, RTransition> orJoinTransitions = new HashMap<>();
    private Map<String, YTask> yawlOrJoins = new HashMap<>();
    private YNet yNet;
    private Set<RMarking> alreadyConsideredMarkings = new HashSet<>(100);
    private Set<YExternalNetElement> conditions = new HashSet<>(100);

    /**
     * Constructs a Reset net from a YAWL net for OR-join analysis.
     *
     * @param yNet   the YAWL net to convert
     * @param orJoin the OR-join task being analyzed
     */
    public E2WFOJNet(YNet yNet, YTask orJoin) {
        this.yNet = Objects.requireNonNull(yNet, "YNet cannot be null");
        Objects.requireNonNull(orJoin, "OR-join task cannot be null");
        convertToResetNet();
        removeOrJoin(orJoin);
        // Clean up references no longer needed after construction
        this.orJoinTransitions = null;
        this.yawlOrJoins = null;
        this.yNet = null;
    }

    /**
     * Private constructor for testing purposes.
     */
    private E2WFOJNet() {
        // No-op constructor for testing
    }

    /**
     * Converts a YAWL net into a Reset net representation.
     * <p>
     * This method creates RPlace and RTransition elements corresponding to
     * the conditions and tasks in the YAWL net, establishing proper flow
     * relationships based on task join and split types.
     * </p>
     */
    private void convertToResetNet() {
        Map<String, ? extends YExternalNetElement> netElements = yNet.getNetElements();

        // Generate places from conditions and tasks
        for (YExternalNetElement element : netElements.values()) {
            switch (element) {
                case YCondition condition -> {
                    RPlace place = new RPlace(condition.getID());
                    places.put(place.getID(), place);
                    conditions.add(condition);
                }
                case YTask task -> {
                    RPlace place = new RPlace("p_" + task.getID());
                    places.put(place.getID(), place);
                }
                default -> {
                    // Other element types are not converted to places
                }
            }
        }

        Map<String, RTransition> startTransitions = new HashMap<>();
        Map<String, RTransition> endTransitions = new HashMap<>();

        // Generate transitions based on task join/split types
        for (YExternalNetElement element : netElements.values()) {
            if (element instanceof YTask task) {
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
    private void createStartTransition(YTask task, Map<String, RTransition> startTransitions) {
        int joinType = task.getJoinType();

        if (joinType == YTask._AND) {
            RTransition transition = new RTransition(task.getID() + "_start");
            startTransitions.put(transition.getID(), transition);

            for (YExternalNetElement preElement : task.getPresetElements()) {
                RFlow inflow = new RFlow(places.get(preElement.getID()), transition);
                transition.setPreset(inflow);

                RFlow outflow = new RFlow(transition, places.get("p_" + task.getID()));
                transition.setPostset(outflow);
            }
        } else if (joinType == YTask._XOR) {
            for (YExternalNetElement preElement : task.getPresetElements()) {
                RTransition transition = new RTransition(
                        task.getID() + "_start^" + preElement.getID());
                startTransitions.put(transition.getID(), transition);

                RFlow inflow = new RFlow(places.get(preElement.getID()), transition);
                transition.setPreset(inflow);

                RFlow outflow = new RFlow(transition, places.get("p_" + task.getID()));
                transition.setPostset(outflow);
            }
        } else if (joinType == YTask._OR) {
            RTransition transition = new RTransition(task.getID() + "_start");
            startTransitions.put(transition.getID(), transition);
            orJoinTransitions.put(transition.getID(), transition);
            yawlOrJoins.put(task.getID(), task);
        }
    }

    /**
     * Creates the end transition(s) for a task based on its split type.
     */
    private void createEndTransition(YTask task, Map<String, RTransition> endTransitions) {
        int splitType = task.getSplitType();

        if (splitType == YTask._AND) {
            createAndSplitEndTransition(task, endTransitions);
        } else if (splitType == YTask._XOR) {
            createXorSplitEndTransition(task, endTransitions);
        } else if (splitType == YTask._OR) {
            createOrSplitEndTransition(task, endTransitions);
        }
    }

    /**
     * Creates AND split end transition with all postset elements.
     */
    private void createAndSplitEndTransition(YTask task, Map<String, RTransition> endTransitions) {
        RTransition transition = new RTransition(task.getID() + "_end");
        endTransitions.put(transition.getID(), transition);

        for (YExternalNetElement postElement : task.getPostsetElements()) {
            RFlow inflow = new RFlow(places.get("p_" + task.getID()), transition);
            transition.setPreset(inflow);

            RFlow outflow = new RFlow(transition, places.get(postElement.getID()));
            transition.setPostset(outflow);
        }

        Set<YExternalNetElement> removeSet = new HashSet<>(task.getRemoveSet());
        if (!removeSet.isEmpty()) {
            addCancelSet(transition, removeSet);
        }
    }

    /**
     * Creates XOR split end transitions - one per postset element.
     */
    private void createXorSplitEndTransition(YTask task, Map<String, RTransition> endTransitions) {
        for (YExternalNetElement postElement : task.getPostsetElements()) {
            RTransition transition = new RTransition(
                    task.getID() + "_end^" + postElement.getID());
            endTransitions.put(transition.getID(), transition);

            RFlow inflow = new RFlow(places.get("p_" + task.getID()), transition);
            transition.setPreset(inflow);

            RFlow outflow = new RFlow(transition, places.get(postElement.getID()));
            transition.setPostset(outflow);

            Set<YExternalNetElement> removeSet = new HashSet<>(task.getRemoveSet());
            if (!removeSet.isEmpty()) {
                addCancelSet(transition, removeSet);
            }
        }
    }

    /**
     * Creates OR split end transitions - generates all non-empty subset combinations.
     */
    private void createOrSplitEndTransition(YTask task, Map<String, RTransition> endTransitions) {
        Set<YExternalNetElement> postElements = task.getPostsetElements();
        Set<Set<YExternalNetElement>> allSubsets = new HashSet<>();

        // Generate all non-empty subsets of postset
        for (int size = 1; size <= postElements.size(); size++) {
            allSubsets.addAll(generateCombinations(postElements, size));
        }

        for (Set<YExternalNetElement> subset : allSubsets) {
            String transitionId = buildTransitionId(task.getID(), "_end^{", subset, " ");
            RTransition transition = new RTransition(transitionId);
            endTransitions.put(transition.getID(), transition);

            RFlow inflow = new RFlow(places.get("p_" + task.getID()), transition);
            transition.setPreset(inflow);

            for (YExternalNetElement postElement : subset) {
                RFlow outflow = new RFlow(transition, places.get(postElement.getID()));
                transition.setPostset(outflow);
            }

            Set<YExternalNetElement> removeSet = new HashSet<>(task.getRemoveSet());
            if (!removeSet.isEmpty()) {
                addCancelSet(transition, removeSet);
            }
        }
    }

    /**
     * Builds a transition ID from task prefix, suffix, and element subset.
     */
    private String buildTransitionId(String prefix, String suffix,
                                     Set<YExternalNetElement> elements, String delimiter) {
        StringBuilder builder = new StringBuilder(prefix);
        builder.append(suffix);
        for (YExternalNetElement element : elements) {
            builder.append(element.getID()).append(delimiter);
        }
        builder.append('}');
        return builder.toString();
    }

    /**
     * Generates all combinations of the specified size from the given set.
     *
     * @param netElements the set of elements to combine
     * @param size        the size of each combination
     * @return a set of all combinations of the specified size
     */
    private Set<Set<YExternalNetElement>> generateCombinations(
            Set<YExternalNetElement> netElements, int size) {
        Set<Set<YExternalNetElement>> subsets = new HashSet<>();
        Object[] elements = netElements.toArray();
        CombinationGenerator generator = new CombinationGenerator(elements.length, size);

        while (generator.hasMore()) {
            Set<YExternalNetElement> combination = new HashSet<>();
            int[] indices = generator.getNext();
            for (int index : indices) {
                combination.add((YExternalNetElement) elements[index]);
            }
            subsets.add(combination);
        }
        return subsets;
    }

    /**
     * Associates a cancellation set with a transition.
     * <p>
     * This implements the reset arc functionality in the Reset net.
     * When a transition fires, all tokens in its cancellation set are removed.
     * </p>
     *
     * @param transition the transition to add the cancellation set to
     * @param removeSet  the set of YAWL elements to be cancelled
     */
    private void addCancelSet(RTransition transition, Set<YExternalNetElement> removeSet) {
        Set<RPlace> resetPlaces = new HashSet<>();

        // Process conditions in the remove set
        Set<YExternalNetElement> conditionRemoves = new HashSet<>(removeSet);
        conditionRemoves.retainAll(conditions);
        for (YExternalNetElement condition : conditionRemoves) {
            RPlace place = places.get(condition.getID());
            if (place != null) {
                resetPlaces.add(place);
            }
        }

        // Process tasks in the remove set (use internal place p_t)
        Set<YExternalNetElement> taskRemoves = new HashSet<>(removeSet);
        taskRemoves.removeAll(conditions);
        for (YExternalNetElement task : taskRemoves) {
            RPlace place = places.get("p_" + task.getID());
            if (place != null) {
                resetPlaces.add(place);
            }
        }

        transition.setRemoveSet(resetPlaces);
    }

    /**
     * Removes the specified OR-join task and converts other OR-joins to XOR semantics.
     * <p>
     * This is part of the OR-join analysis algorithm where the OR-join being
     * analyzed is removed, and other OR-joins are treated as XOR joins to
     * enable the coverability analysis.
     * </p>
     *
     * @param orJoin the OR-join task being analyzed
     */
    private void removeOrJoin(YTask orJoin) {
        yawlOrJoins.remove(orJoin.getID());

        // Remove OR-join transitions from the net
        for (RTransition rj : orJoinTransitions.values()) {
            transitions.remove(rj.getID());
        }

        // Convert remaining OR-joins to XOR semantics
        for (YExternalNetElement otherOrJoin : yawlOrJoins.values()) {
            for (YExternalNetElement preElement : otherOrJoin.getPresetElements()) {
                RTransition transition = new RTransition(
                        otherOrJoin.getID() + "_start^" + preElement.getID());
                transitions.put(transition.getID(), transition);

                RFlow inflow = new RFlow(places.get(preElement.getID()), transition);
                transition.setPreset(inflow);

                RFlow outflow = new RFlow(transition, places.get("p_" + otherOrJoin.getID()));
                transition.setPostset(outflow);
            }
        }
    }

    /**
     * Performs structural restriction of the reset net based on an OR-join task.
     * <p>
     * This restricts the net to only those elements that can reach the OR-join,
     * improving the efficiency of the coverability analysis.
     * </p>
     *
     * @param orJoin the OR-join task for structural restriction
     */
    public void restrictNet(YTask orJoin) {
        Set<RTransition> restrictedTransitions = new HashSet<>();
        Set<RPlace> restrictedPlaces = new HashSet<>();

        // Convert YAWL preset to Reset net places
        Set<RPlace> presetPlaces = new HashSet<>();
        for (YExternalNetElement element : orJoin.getPresetElements()) {
            if (element instanceof YCondition condition) {
                RPlace place = places.get(condition.getID());
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
     * <p>
     * This restricts the net to only those elements reachable from the
     * currently marked places, using forward reachability analysis.
     * </p>
     *
     * @param marking the current marking for active projection
     */
    public void restrictNet(YMarking marking) {
        Set<RPlace> markedPlaces = new HashSet<>();
        Set<YNetElement> yMarked = new HashSet<>(marking.getLocations());

        // Convert YAWL marking to Reset net places
        for (YNetElement element : yMarked) {
            switch (element) {
                case YCondition condition -> {
                    RPlace place = places.get(condition.getID());
                    if (place != null) {
                        markedPlaces.add(place);
                    }
                }
                case YTask task -> {
                    RPlace place = places.get("p_" + task.getID());
                    if (place != null) {
                        markedPlaces.add(place);
                    }
                }
                default -> {
                    // Other element types are not converted
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
     * Performs the actual restriction by removing irrelevant elements and
     * updating flow relations.
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
     *
     * @param source the marking to check for coverability
     * @param target the target marking to find predecessors from
     * @return true if a marking s' less than or equal to source is coverable
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
     * <p>
     * This implements the iterative fixpoint computation for predecessor markings.
     * </p>
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
     * <p>
     * For optimization, this method tracks which markings have already been
     * considered to avoid redundant computation.
     * </p>
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
     * <p>
     * A transition is not backwards enabled if there is a token in its remove set
     * that violates the backwards firing rule.
     * </p>
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
                    // Reset place is marked and in postset
                    int count = markedPlaces.get(placeName);
                    if (count > 1) {
                        return false;
                    }
                } else {
                    // Reset place is marked but not in postset
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Computes the marking before a transition fires (backwards firing).
     *
     * @param currentMarking the current marking
     * @param transition     the transition to fire backwards
     * @return the marking before the transition fired
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
     * <p>
     * This is the main entry point for OR-join enablement analysis. The OR-join
     * is enabled if none of the "bigger enabling" markings are coverable from
     * the current marking's predecessors.
     * </p>
     *
     * @param marking the current marking
     * @param orJoin  the OR-join task to check
     * @return true if the OR-join should be enabled, false otherwise
     */
    public boolean orJoinEnabled(YMarking marking, YTask orJoin) {
        // Convert YAWL marking to Reset net marking
        Map<String, Integer> resetMarking = new HashMap<>();
        Set<YTask> markedTasks = new HashSet<>();
        List<YNetElement> yLocations = new java.util.ArrayList<>(marking.getLocations());

        for (YNetElement element : yLocations) {
            if (element instanceof YCondition condition) {
                RPlace place = places.get(condition.getID());
                if (place != null) {
                    String placeName = place.getID();
                    resetMarking.merge(placeName, 1, Integer::sum);
                }
            } else if (element instanceof YTask task) {
                markedTasks.add(task);
            }
        }

        // Convert active tasks to internal places
        for (YTask task : markedTasks) {
            RPlace place = places.get("p_" + task.getID());
            if (place != null) {
                resetMarking.put(place.getID(), 1);
            }
        }

        RMarking resetM = new RMarking(resetMarking);

        // Generate bigger-enabling markings for OR-join analysis
        Set<YExternalNetElement> presetConditions = orJoin.getPresetElements();
        Map<String, Integer> baseMarking = new HashMap<>();
        Set<RPlace> emptyPresetPlaces = new HashSet<>();

        for (YExternalNetElement element : presetConditions) {
            if (element instanceof YCondition condition) {
                RPlace place = places.get(condition.getID());
                if (place != null) {
                    if (yLocations.contains(condition)) {
                        // Add one token for each marked preset place
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
