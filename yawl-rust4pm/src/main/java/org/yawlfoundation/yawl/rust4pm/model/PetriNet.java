package org.yawlfoundation.yawl.rust4pm.model;

import java.util.List;

/**
 * A discovered or loaded Petri net process model.
 *
 * <p>Contains places, transitions, and arc connections representing
 * a formal process model suitable for conformance checking and analysis.
 *
 * @param places      all places in the net
 * @param transitions all transitions in the net
 * @param arcs        all arcs (connections) in the net
 * @param initialMarkings initial tokens by place
 */
public record PetriNet(
    List<Place> places,
    List<Transition> transitions,
    List<Arc> arcs,
    List<PlaceMarking> initialMarkings
) {

    /**
     * A place in a Petri net.
     *
     * @param id    unique identifier
     * @param label human-readable name
     */
    public record Place(String id, String label) {}

    /**
     * A transition in a Petri net.
     *
     * @param id    unique identifier
     * @param label human-readable activity name
     */
    public record Transition(String id, String label) {}

    /**
     * An arc (edge) in a Petri net connecting a place to a transition or vice versa.
     *
     * @param source source place or transition ID
     * @param target target place or transition ID
     * @param weight arc weight (typically 1)
     */
    public record Arc(String source, String target, int weight) {}

    /**
     * Initial token count for a specific place.
     *
     * @param placeId place identifier
     * @param tokens  initial token count
     */
    public record PlaceMarking(String placeId, long tokens) {}
}
