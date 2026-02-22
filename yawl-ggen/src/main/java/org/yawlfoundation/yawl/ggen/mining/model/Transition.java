package org.yawlfoundation.yawl.ggen.mining.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a transition (activity/event) in a Petri net model.
 * Transitions fire when all incoming places have tokens.
 */
public class Transition extends PnmlElement {
    private String guard;
    private Set<Arc> incomingArcs;
    private Set<Arc> outgoingArcs;

    public Transition(String id, String name) {
        super(id, name);
        this.guard = null;
        this.incomingArcs = new HashSet<>();
        this.outgoingArcs = new HashSet<>();
    }

    public String getGuard() {
        return guard;
    }

    public void setGuard(String guard) {
        this.guard = guard;
    }

    public Set<Arc> getIncomingArcs() {
        return incomingArcs;
    }

    public Set<Arc> getOutgoingArcs() {
        return outgoingArcs;
    }

    public void addIncomingArc(Arc arc) {
        incomingArcs.add(arc);
    }

    public void addOutgoingArc(Arc arc) {
        outgoingArcs.add(arc);
    }

    /**
     * Check if this is a start transition.
     * A transition is a start transition if it has no incoming arcs, OR if all its
     * incoming places are initial places (have initial marking and no incoming arcs).
     * This handles both pure-source transitions and workflow-start transitions
     * preceded by the initial place.
     */
    public boolean isStartTransition() {
        if (incomingArcs.isEmpty()) return true;
        return incomingArcs.stream()
            .allMatch(arc -> arc.getSource() instanceof Place p && p.isInitialPlace());
    }

    /**
     * Check if this is an end transition (no outgoing arcs).
     */
    public boolean isEndTransition() {
        return outgoingArcs.isEmpty();
    }

    /**
     * Get number of outgoing arcs (branch count for gateways).
     */
    public int getBranchCount() {
        return outgoingArcs.size();
    }

    /**
     * Check if this transition represents a gateway (multiple branches).
     */
    public boolean isGateway() {
        return getBranchCount() > 1;
    }
}
