package org.yawlfoundation.yawl.ggen.mining.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a place (state) in a Petri net model.
 * Places hold tokens and represent states in the workflow.
 */
public class Place extends PnmlElement {
    private int initialMarking;
    private Set<Arc> incomingArcs;
    private Set<Arc> outgoingArcs;

    public Place(String id, String name) {
        super(id, name);
        this.initialMarking = 0;
        this.incomingArcs = new HashSet<>();
        this.outgoingArcs = new HashSet<>();
    }

    public Place(String id, String name, int initialMarking) {
        this(id, name);
        this.initialMarking = initialMarking;
    }

    public int getInitialMarking() {
        return initialMarking;
    }

    public void setInitialMarking(int marking) {
        this.initialMarking = marking;
    }

    public Set<Arc> getIncomingArcs() {
        return Collections.unmodifiableSet(incomingArcs);
    }

    public Set<Arc> getOutgoingArcs() {
        return Collections.unmodifiableSet(outgoingArcs);
    }

    public void addIncomingArc(Arc arc) {
        incomingArcs.add(arc);
    }

    public void addOutgoingArc(Arc arc) {
        outgoingArcs.add(arc);
    }

    /**
     * Check if this place is an initial place (no incoming arcs, has initial marking).
     */
    public boolean isInitialPlace() {
        return incomingArcs.isEmpty() && initialMarking > 0;
    }

    /**
     * Check if this place is a final place (no outgoing arcs).
     */
    public boolean isFinalPlace() {
        return outgoingArcs.isEmpty();
    }
}
