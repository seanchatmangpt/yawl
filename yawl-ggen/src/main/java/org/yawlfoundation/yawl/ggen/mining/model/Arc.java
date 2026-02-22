package org.yawlfoundation.yawl.ggen.mining.model;

import java.util.Objects;

/**
 * Represents an arc (flow) connecting places and transitions in a Petri net.
 * Arcs define the control flow between states and activities.
 */
public class Arc extends PnmlElement {
    private PnmlElement source;
    private PnmlElement target;
    private int multiplicity;

    public Arc(String id, PnmlElement source, PnmlElement target) {
        super(id, String.format("%s → %s", source.getId(), target.getId()));
        this.source = Objects.requireNonNull(source, "Source cannot be null");
        this.target = Objects.requireNonNull(target, "Target cannot be null");
        this.multiplicity = 1;
    }

    public Arc(String id, PnmlElement source, PnmlElement target, int multiplicity) {
        this(id, source, target);
        this.multiplicity = multiplicity;
    }

    public PnmlElement getSource() {
        return source;
    }

    public PnmlElement getTarget() {
        return target;
    }

    public int getMultiplicity() {
        return multiplicity;
    }

    public void setMultiplicity(int multiplicity) {
        this.multiplicity = multiplicity;
    }

    /**
     * Check if this arc is from a place to a transition (normal flow).
     */
    public boolean isP2T() {
        return source instanceof Place && target instanceof Transition;
    }

    /**
     * Check if this arc is from a transition to a place (normal flow).
     */
    public boolean isT2P() {
        return source instanceof Transition && target instanceof Place;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Arc arc = (Arc) o;
        return Objects.equals(source.getId(), arc.source.getId()) &&
               Objects.equals(target.getId(), arc.target.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source.getId(), target.getId());
    }
}
