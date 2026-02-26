/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.powl;

import org.yawlfoundation.yawl.ggen.mining.model.Arc;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts a POWL model to a Petri net representation.
 *
 * <p>Conversion algorithm:
 * <ul>
 *   <li>Each POWL subtree maps to a subgraph with an entry place and exit place</li>
 *   <li>SEQUENCE: chains subtrees so exit_i → entry_{i+1}</li>
 *   <li>XOR: shared entry and exit; one transition per child for exclusive choice</li>
 *   <li>PARALLEL: AND-split from entry, AND-join to exit</li>
 *   <li>LOOP: create back-edge from exit to entry (cycle)</li>
 *   <li>ACTIVITY: single transition with input/output places</li>
 * </ul>
 */
public class PowlToYawlConverter {

    /**
     * Represents the entry and exit points of a converted POWL subtree in the Petri net.
     * Used internally to thread the conversion through recursive calls.
     */
    private static class SubgraphBoundaries {
        final Place entryPlace;
        final Place exitPlace;

        SubgraphBoundaries(Place entryPlace, Place exitPlace) {
            this.entryPlace = Objects.requireNonNull(entryPlace);
            this.exitPlace = Objects.requireNonNull(exitPlace);
        }
    }

    private final PetriNet net;
    private final Map<String, Object> nodeMap;

    /**
     * Constructs a PowlToYawlConverter with a new Petri net.
     */
    public PowlToYawlConverter() {
        this.net = new PetriNet(UUID.randomUUID().toString(), "Converted POWL Model");
        this.nodeMap = new HashMap<>();
    }

    /**
     * Converts a POWL model to a Petri net.
     *
     * @param model the POWL model to convert (must not be null)
     * @return a PetriNet representation of the POWL model
     * @throws IllegalArgumentException if model is null
     */
    public PetriNet convert(PowlModel model) {
        Objects.requireNonNull(model, "model must not be null");

        // Convert the root node; its boundaries span the entire net
        SubgraphBoundaries boundaries = convertNode(model.root());

        // Ensure the entry place is marked as initial (token count = 1)
        boundaries.entryPlace.setInitialMarking(1);

        return net;
    }

    /**
     * Recursively converts a POWL node to Petri net elements.
     * Returns the entry and exit places of the resulting subgraph.
     *
     * @param node the POWL node to convert
     * @return SubgraphBoundaries with entry and exit places
     */
    private SubgraphBoundaries convertNode(PowlNode node) {
        return switch (node) {
            case PowlActivity activity -> convertActivity(activity);
            case PowlOperatorNode operator -> convertOperator(operator);
        };
    }

    /**
     * Converts a PowlActivity to Petri net: input place → transition → output place.
     */
    private SubgraphBoundaries convertActivity(PowlActivity activity) {
        String id = activity.id();

        Place entryPlace = new Place(genId("p_in", id), "Entry to " + activity.label());
        Place exitPlace = new Place(genId("p_out", id), "Exit from " + activity.label());

        Transition transition = new Transition(genId("t", id), activity.label());

        // Add elements to net
        net.addPlace(entryPlace);
        net.addPlace(exitPlace);
        net.addTransition(transition);

        // Create arcs: entryPlace → transition → exitPlace
        Arc inArc = new Arc(genId("arc_in", id), entryPlace, transition);
        Arc outArc = new Arc(genId("arc_out", id), transition, exitPlace);

        net.addArc(inArc);
        net.addArc(outArc);

        return new SubgraphBoundaries(entryPlace, exitPlace);
    }

    /**
     * Converts a PowlOperatorNode based on its operator type.
     */
    private SubgraphBoundaries convertOperator(PowlOperatorNode operator) {
        return switch (operator.type()) {
            case SEQUENCE -> convertSequence(operator);
            case XOR -> convertXor(operator);
            case PARALLEL -> convertParallel(operator);
            case LOOP -> convertLoop(operator);
        };
    }

    /**
     * SEQUENCE: chain children so exit_i connects to entry_{i+1}.
     */
    private SubgraphBoundaries convertSequence(PowlOperatorNode operator) {
        List<PowlNode> children = operator.children();
        SubgraphBoundaries first = convertNode(children.get(0));

        for (int i = 1; i < children.size(); i++) {
            SubgraphBoundaries next = convertNode(children.get(i));
            // Connect exit of previous to entry of next via a transition
            Transition connector = new Transition(
                genId("t_seq", operator.id() + "_" + i),
                "Sequence " + i
            );
            net.addTransition(connector);

            Arc exitToConnector = new Arc(
                genId("arc_seq_out", operator.id() + "_" + i),
                first.exitPlace,
                connector
            );
            Arc connectorToEntry = new Arc(
                genId("arc_seq_in", operator.id() + "_" + i),
                connector,
                next.entryPlace
            );

            net.addArc(exitToConnector);
            net.addArc(connectorToEntry);

            first = new SubgraphBoundaries(first.entryPlace, next.exitPlace);
        }

        return first;
    }

    /**
     * XOR: shared entry and exit places; one transition per child for choice.
     */
    private SubgraphBoundaries convertXor(PowlOperatorNode operator) {
        Place sharedEntry = new Place(
            genId("p_xor_in", operator.id()),
            "XOR Entry"
        );
        Place sharedExit = new Place(
            genId("p_xor_out", operator.id()),
            "XOR Exit"
        );

        net.addPlace(sharedEntry);
        net.addPlace(sharedExit);

        for (int i = 0; i < operator.children().size(); i++) {
            PowlNode child = operator.children().get(i);
            SubgraphBoundaries childBounds = convertNode(child);

            // Choice transition: sharedEntry → choiceTransition → childEntry
            Transition choiceTransition = new Transition(
                genId("t_xor_choice", operator.id() + "_" + i),
                "Choice " + i
            );
            net.addTransition(choiceTransition);

            Arc entryToChoice = new Arc(
                genId("arc_xor_choice_in", operator.id() + "_" + i),
                sharedEntry,
                choiceTransition
            );
            Arc choiceToChild = new Arc(
                genId("arc_xor_choice_out", operator.id() + "_" + i),
                choiceTransition,
                childBounds.entryPlace
            );

            net.addArc(entryToChoice);
            net.addArc(choiceToChild);

            // Merge transition: childExit → mergeTransition → sharedExit
            Transition mergeTransition = new Transition(
                genId("t_xor_merge", operator.id() + "_" + i),
                "Merge " + i
            );
            net.addTransition(mergeTransition);

            Arc childToMerge = new Arc(
                genId("arc_xor_merge_in", operator.id() + "_" + i),
                childBounds.exitPlace,
                mergeTransition
            );
            Arc mergeToExit = new Arc(
                genId("arc_xor_merge_out", operator.id() + "_" + i),
                mergeTransition,
                sharedExit
            );

            net.addArc(childToMerge);
            net.addArc(mergeToExit);
        }

        return new SubgraphBoundaries(sharedEntry, sharedExit);
    }

    /**
     * PARALLEL: AND-split from entry to all children, AND-join from all children to exit.
     */
    private SubgraphBoundaries convertParallel(PowlOperatorNode operator) {
        Place sharedEntry = new Place(
            genId("p_par_in", operator.id()),
            "AND-Split Entry"
        );
        Place sharedExit = new Place(
            genId("p_par_out", operator.id()),
            "AND-Join Exit"
        );

        net.addPlace(sharedEntry);
        net.addPlace(sharedExit);

        // AND-split transition
        Transition andSplit = new Transition(
            genId("t_and_split", operator.id()),
            "AND-Split"
        );
        net.addTransition(andSplit);

        Arc entryToSplit = new Arc(
            genId("arc_par_entry", operator.id()),
            sharedEntry,
            andSplit
        );
        net.addArc(entryToSplit);

        // AND-join transition
        Transition andJoin = new Transition(
            genId("t_and_join", operator.id()),
            "AND-Join"
        );
        net.addTransition(andJoin);

        Arc joinToExit = new Arc(
            genId("arc_par_exit", operator.id()),
            andJoin,
            sharedExit
        );
        net.addArc(joinToExit);

        // Convert all children and connect them
        for (int i = 0; i < operator.children().size(); i++) {
            PowlNode child = operator.children().get(i);
            SubgraphBoundaries childBounds = convertNode(child);

            Arc splitToChild = new Arc(
                genId("arc_par_split_out", operator.id() + "_" + i),
                andSplit,
                childBounds.entryPlace
            );
            Arc childToJoin = new Arc(
                genId("arc_par_join_in", operator.id() + "_" + i),
                childBounds.exitPlace,
                andJoin
            );

            net.addArc(splitToChild);
            net.addArc(childToJoin);
        }

        return new SubgraphBoundaries(sharedEntry, sharedExit);
    }

    /**
     * LOOP: convert do-child and redo-child, then create back-edge from redo-exit to do-entry.
     */
    private SubgraphBoundaries convertLoop(PowlOperatorNode operator) {
        List<PowlNode> children = operator.children();
        if (children.size() != 2) {
            throw new IllegalStateException(
                "LOOP must have exactly 2 children, got: " + children.size()
            );
        }

        // Convert do-child
        SubgraphBoundaries doBounds = convertNode(children.get(0));

        // Convert redo-child
        SubgraphBoundaries redoBounds = convertNode(children.get(1));

        // Create back-edge: redo-exit → loop-back transition → do-entry
        Transition loopBack = new Transition(
            genId("t_loop_back", operator.id()),
            "Loop Back"
        );
        net.addTransition(loopBack);

        Arc redoToBack = new Arc(
            genId("arc_loop_back_in", operator.id()),
            redoBounds.exitPlace,
            loopBack
        );
        Arc backToDo = new Arc(
            genId("arc_loop_back_out", operator.id()),
            loopBack,
            doBounds.entryPlace
        );

        net.addArc(redoToBack);
        net.addArc(backToDo);

        // The LOOP entry is the do-entry, exit is the redo-exit
        return new SubgraphBoundaries(doBounds.entryPlace, redoBounds.exitPlace);
    }

    /**
     * Generates a unique, deterministic ID for Petri net elements.
     *
     * @param prefix the element type prefix (e.g., "p_in", "t")
     * @param nodeId the original POWL node ID
     * @return a unique ID string
     */
    private String genId(String prefix, String nodeId) {
        return prefix + "_" + nodeId.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
