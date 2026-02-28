package org.yawlfoundation.yawl.engine.agent;

import java.util.Objects;

/**
 * Represents a Transition in a Petri net model within the YAWL workflow engine.
 * A transition represents a workflow task or routing step that consumes tokens
 * from input places and produces tokens in output places.
 *
 * Thread-safe: All fields are immutable and final.
 *
 * @since Java 21
 */
public final class Transition {

    private final String id;
    private final String name;
    private final String taskType;
    private final boolean isFinal;
    private final boolean isMultiInstance;

    /**
     * Create a new transition with full specification.
     *
     * @param id Unique identifier for this transition
     * @param name Human-readable name for this transition
     * @param taskType Type of task (e.g., "manual", "automatic", "service")
     * @param isFinal true if this is a final transition (end of workflow)
     * @param isMultiInstance true if this transition fires multiple times (loop)
     */
    public Transition(String id, String name, String taskType, boolean isFinal, boolean isMultiInstance) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.taskType = Objects.requireNonNull(taskType, "taskType cannot be null");
        this.isFinal = isFinal;
        this.isMultiInstance = isMultiInstance;
    }

    /**
     * Create a new regular (non-final, non-multi-instance) transition.
     *
     * @param id Unique identifier for this transition
     * @param name Human-readable name
     * @param taskType Type of task
     */
    public Transition(String id, String name, String taskType) {
        this(id, name, taskType, false, false);
    }

    /**
     * Get the unique identifier for this transition.
     *
     * @return transition ID
     */
    public String id() {
        return id;
    }

    /**
     * Get the human-readable name of this transition.
     *
     * @return transition name
     */
    public String name() {
        return name;
    }

    /**
     * Get the task type (e.g., "manual", "automatic", "service").
     *
     * @return task type string
     */
    public String taskType() {
        return taskType;
    }

    /**
     * Check if this is a final transition (last step in workflow).
     *
     * @return true if final
     */
    public boolean isFinal() {
        return isFinal;
    }

    /**
     * Check if this transition is part of a multi-instance loop.
     *
     * @return true if multi-instance
     */
    public boolean isMultiInstance() {
        return isMultiInstance;
    }

    /**
     * Check if this transition can fire given the input place state.
     * A transition can fire if its input place has at least one token.
     *
     * @param inputPlace place providing input tokens
     * @return true if place has at least one token
     */
    public boolean canFire(Place inputPlace) {
        Objects.requireNonNull(inputPlace, "inputPlace cannot be null");
        return inputPlace.hasTokens();
    }

    /**
     * Check if this transition can fire given multiple input places.
     * A transition can fire if ALL input places have sufficient tokens.
     *
     * @param inputPlaces array of input places
     * @return true if all places have at least one token
     */
    public boolean canFire(Place... inputPlaces) {
        Objects.requireNonNull(inputPlaces, "inputPlaces cannot be null");
        if (inputPlaces.length == 0) {
            return false; // No inputs means cannot fire
        }
        for (Place place : inputPlaces) {
            if (!place.hasTokens()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Execute this transition by consuming tokens from input place and
     * producing tokens in output place.
     *
     * @param inputPlace place from which tokens are consumed
     * @param outputPlace place where tokens are produced
     * @return true if transition fired successfully, false if insufficient tokens
     */
    public boolean fire(Place inputPlace, Place outputPlace) {
        Objects.requireNonNull(inputPlace, "inputPlace cannot be null");
        Objects.requireNonNull(outputPlace, "outputPlace cannot be null");

        if (!canFire(inputPlace)) {
            return false; // Cannot fire without tokens
        }

        // Atomically consume and produce tokens
        if (inputPlace.removeToken() != -1) {
            outputPlace.addToken();
            return true;
        }
        return false; // Token removal failed (race condition)
    }

    /**
     * Execute this transition by consuming multiple tokens.
     * Supports multi-instance patterns where one transition fires to multiple outputs.
     *
     * @param inputPlace place from which tokens are consumed
     * @param outputPlaces array of output places that each receive one token
     * @return true if transition fired successfully, false if insufficient tokens
     */
    public boolean fire(Place inputPlace, Place... outputPlaces) {
        Objects.requireNonNull(inputPlace, "inputPlace cannot be null");
        Objects.requireNonNull(outputPlaces, "outputPlaces cannot be null");

        if (!canFire(inputPlace)) {
            return false;
        }

        // Atomically consume from input and produce to all outputs
        if (inputPlace.removeToken() != -1) {
            for (Place outputPlace : outputPlaces) {
                outputPlace.addToken();
            }
            return true;
        }
        return false;
    }

    /**
     * Check equality based on transition ID and name (structural identity).
     *
     * @param obj object to compare with
     * @return true if same id and name
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Transition other)) return false;
        return id.equals(other.id) && name.equals(other.name);
    }

    /**
     * Hash based on id and name (consistent with equals).
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    /**
     * Human-readable representation including task type and final status.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Transition{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", taskType='" + taskType + '\'' +
                ", final=" + isFinal +
                ", multiInstance=" + isMultiInstance +
                '}';
    }
}
