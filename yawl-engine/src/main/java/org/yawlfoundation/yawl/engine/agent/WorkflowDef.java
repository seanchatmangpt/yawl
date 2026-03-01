package org.yawlfoundation.yawl.engine.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a workflow definition using a minimal Petri-net model.
 * Defines the structure of a workflow with places (states) and transitions (tasks).
 *
 * Immutable: Provides unmodifiable lists of places and transitions.
 * The workflow structure is fixed at creation time.
 *
 * Thread-safe: Uses immutable collections and final fields.
 *
 * @since Java 21
 */
public final class WorkflowDef {

    private final UUID workflowId;
    private final String name;
    private final String description;
    private final List<Place> places;
    private final List<Transition> transitions;
    private final Place initialPlace;

    /**
     * Create a new workflow definition with full specification.
     *
     * @param workflowId Unique identifier for this workflow
     * @param name Human-readable name for this workflow
     * @param description Optional workflow description
     * @param places List of Petri-net places defining workflow states
     * @param transitions List of Petri-net transitions defining workflow tasks
     * @param initialPlace The place where workflow execution starts
     */
    public WorkflowDef(UUID workflowId, String name, String description,
                       List<Place> places, List<Transition> transitions,
                       Place initialPlace) {
        this.workflowId = Objects.requireNonNull(workflowId, "workflowId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = description != null ? description : "";
        this.places = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(places, "places cannot be null"))
        );
        this.transitions = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(transitions, "transitions cannot be null"))
        );
        this.initialPlace = Objects.requireNonNull(initialPlace, "initialPlace cannot be null");

        // Verify that initialPlace is in the places list
        if (!this.places.contains(initialPlace)) {
            throw new IllegalArgumentException("initialPlace must be in the places list");
        }
    }

    /**
     * Create a new workflow definition without description.
     *
     * @param workflowId Unique identifier
     * @param name Workflow name
     * @param places List of places
     * @param transitions List of transitions
     * @param initialPlace Starting place
     */
    public WorkflowDef(UUID workflowId, String name,
                       List<Place> places, List<Transition> transitions,
                       Place initialPlace) {
        this(workflowId, name, "", places, transitions, initialPlace);
    }

    /**
     * Get the unique identifier for this workflow.
     *
     * @return workflow ID
     */
    public UUID workflowId() {
        return workflowId;
    }

    /**
     * Get the human-readable name of this workflow.
     *
     * @return workflow name
     */
    public String name() {
        return name;
    }

    /**
     * Get the description of this workflow.
     *
     * @return workflow description (empty string if not set)
     */
    public String description() {
        return description;
    }

    /**
     * Get the initial place where workflow execution begins.
     *
     * @return the initial place
     */
    public Place getInitialPlace() {
        return initialPlace;
    }

    /**
     * Get an unmodifiable list of all places in this workflow.
     *
     * @return unmodifiable list of places
     */
    public List<Place> places() {
        return places;
    }

    /**
     * Get an unmodifiable list of all transitions in this workflow.
     *
     * @return unmodifiable list of transitions
     */
    public List<Transition> transitions() {
        return transitions;
    }

    /**
     * Find a place by its ID.
     *
     * @param placeId the place ID to find
     * @return the Place with matching ID, or null if not found
     */
    public Place findPlace(String placeId) {
        Objects.requireNonNull(placeId, "placeId cannot be null");
        return places.stream()
                .filter(p -> p.id().equals(placeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Find a transition by its ID.
     *
     * @param transitionId the transition ID to find
     * @return the Transition with matching ID, or null if not found
     */
    public Transition findTransition(String transitionId) {
        Objects.requireNonNull(transitionId, "transitionId cannot be null");
        return transitions.stream()
                .filter(t -> t.id().equals(transitionId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all transitions that can consume tokens from a specific place.
     * These are transitions that have the given place as input.
     *
     * @param place the input place
     * @return list of transitions that can fire from this place
     */
    public List<Transition> getTransitions(Place place) {
        Objects.requireNonNull(place, "place cannot be null");
        // In a full implementation, this would check transition input places.
        // For now, return all transitions that can fire from the place.
        List<Transition> result = new ArrayList<>();
        for (Transition t : transitions) {
            if (t.canFire(place)) {
                result.add(t);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Check if this workflow has any final transitions.
     *
     * @return true if at least one transition is marked as final
     */
    public boolean hasFinalTransition() {
        return transitions.stream().anyMatch(Transition::isFinal);
    }

    /**
     * Get the count of places in this workflow.
     *
     * @return number of places
     */
    public int placeCount() {
        return places.size();
    }

    /**
     * Get the count of transitions in this workflow.
     *
     * @return number of transitions
     */
    public int transitionCount() {
        return transitions.size();
    }

    /**
     * Validate the workflow structure.
     * Checks for common issues like dangling places or transitions.
     *
     * @return true if workflow is structurally valid
     */
    public boolean isValid() {
        // All places should have at least one incoming or outgoing transition
        // (except initial place which may have no incoming)
        // This is a basic check - full validation would be more complex
        return !places.isEmpty() && !transitions.isEmpty() &&
               places.contains(initialPlace);
    }

    /**
     * Check equality based on workflow ID (workflows are identified by their ID).
     *
     * @param obj object to compare
     * @return true if same workflowId
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof WorkflowDef other)) return false;
        return workflowId.equals(other.workflowId);
    }

    /**
     * Hash based on workflowId.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(workflowId);
    }

    /**
     * Human-readable representation including workflow structure summary.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "WorkflowDef{" +
                "workflowId=" + workflowId +
                ", name='" + name + '\'' +
                ", places=" + places.size() +
                ", transitions=" + transitions.size() +
                ", initial=" + initialPlace.name() +
                '}';
    }
}
