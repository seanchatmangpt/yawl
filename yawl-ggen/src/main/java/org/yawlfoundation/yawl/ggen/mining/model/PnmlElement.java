package org.yawlfoundation.yawl.ggen.mining.model;

import java.util.Objects;

/**
 * Abstract base class for PNML elements (places, transitions, arcs).
 * Represents a Petri net model element with identifier and name.
 */
public abstract class PnmlElement {
    protected String id;
    protected String name;

    public PnmlElement(String id, String name) {
        this.id = Objects.requireNonNull(id, "Element id cannot be null");
        this.name = name != null ? name : id;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PnmlElement that = (PnmlElement) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s(id=%s, name=%s)", getClass().getSimpleName(), id, name);
    }
}
