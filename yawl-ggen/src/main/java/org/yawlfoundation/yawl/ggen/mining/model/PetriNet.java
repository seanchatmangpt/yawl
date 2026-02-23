package org.yawlfoundation.yawl.ggen.mining.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a complete Petri net model parsed from PNML.
 * Contains all places, transitions, and arcs in the workflow.
 */
public class PetriNet {
    private String id;
    private String name;
    private Map<String, Place> places;
    private Map<String, Transition> transitions;
    private Set<Arc> arcs;

    public PetriNet(String id, String name) {
        this.id = id;
        this.name = name;
        this.places = new HashMap<>();
        this.transitions = new HashMap<>();
        this.arcs = new HashSet<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Place> getPlaces() {
        return places;
    }

    public Map<String, Transition> getTransitions() {
        return transitions;
    }

    public Set<Arc> getArcs() {
        return arcs;
    }

    /**
     * Add a place to the net.
     */
    public void addPlace(Place place) {
        places.put(place.getId(), place);
    }

    /**
     * Add a transition to the net.
     */
    public void addTransition(Transition transition) {
        transitions.put(transition.getId(), transition);
    }

    /**
     * Add an arc to the net and link it to source and target elements.
     */
    public void addArc(Arc arc) {
        arcs.add(arc);
        if (arc.getSource() instanceof Place place) {
            place.addOutgoingArc(arc);
        }
        if (arc.getSource() instanceof Transition transition) {
            transition.addOutgoingArc(arc);
        }
        if (arc.getTarget() instanceof Place place) {
            place.addIncomingArc(arc);
        }
        if (arc.getTarget() instanceof Transition transition) {
            transition.addIncomingArc(arc);
        }
    }

    /**
     * Get a place by ID.
     */
    public Place getPlace(String id) {
        return places.get(id);
    }

    /**
     * Get a transition by ID.
     */
    public Transition getTransition(String id) {
        return transitions.get(id);
    }

    /**
     * Get all start transitions (no incoming arcs).
     */
    public Set<Transition> getStartTransitions() {
        return transitions.values().stream()
            .filter(Transition::isStartTransition)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all end transitions (no outgoing arcs).
     */
    public Set<Transition> getEndTransitions() {
        return transitions.values().stream()
            .filter(Transition::isEndTransition)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all gateway transitions (multiple outgoing arcs).
     */
    public Set<Transition> getGateways() {
        return transitions.values().stream()
            .filter(Transition::isGateway)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Validate the Petri net structure.
     * @return true if the net is valid (all arcs connect valid elements)
     */
    public boolean isValid() {
        for (Arc arc : arcs) {
            PnmlElement source = arc.getSource();
            PnmlElement target = arc.getTarget();
            boolean sourceValid = (source instanceof Place p && places.containsValue(p))
                || (source instanceof Transition t && transitions.containsValue(t));
            boolean targetValid = (target instanceof Place p && places.containsValue(p))
                || (target instanceof Transition t && transitions.containsValue(t));
            if (!sourceValid || !targetValid) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("PetriNet(id=%s, name=%s, places=%d, transitions=%d, arcs=%d)",
            id, name, places.size(), transitions.size(), arcs.size());
    }
}
