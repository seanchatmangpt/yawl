package org.yawlfoundation.yawl.engine.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent builder for constructing WorkflowDef (Petri net models).
 *
 * Provides a chainable API to define places, transitions, and their relationships
 * without manually constructing lists and UUIDs.
 *
 * Usage:
 * <pre>
 *     WorkflowDef workflow = WorkflowBuilder.named("Order Processing")
 *         .description("Handles customer order lifecycle")
 *         .place("start", "Start", 1)
 *         .place("approved", "Approved")
 *         .place("shipped", "Shipped")
 *         .place("end", "End")
 *         .transition("review", "Review Order", "manual")
 *         .transition("approve", "Approve Order", "automatic")
 *         .transition("ship", "Ship Order", "service")
 *         .finalTransition("complete", "Complete Order", "automatic")
 *         .startAt("start")
 *         .build();
 * </pre>
 */
public final class WorkflowBuilder {

    private final String name;
    private String description = "";
    private UUID workflowId;
    private final Map<String, Place> places = new LinkedHashMap<>();
    private final List<Transition> transitions = new ArrayList<>();
    private String initialPlaceId;

    private WorkflowBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.workflowId = UUID.randomUUID();
    }

    /**
     * Create a new workflow builder with a name.
     *
     * @param name workflow name
     * @return a new builder
     */
    public static WorkflowBuilder named(String name) {
        return new WorkflowBuilder(name);
    }

    /**
     * Set the workflow description.
     *
     * @param description human-readable description
     * @return this builder
     */
    public WorkflowBuilder description(String description) {
        this.description = Objects.requireNonNull(description);
        return this;
    }

    /**
     * Set a specific workflow ID (default is random UUID).
     *
     * @param id the workflow ID
     * @return this builder
     */
    public WorkflowBuilder id(UUID id) {
        this.workflowId = Objects.requireNonNull(id);
        return this;
    }

    /**
     * Add a place with initial tokens.
     *
     * @param id           place identifier
     * @param name         human-readable name
     * @param initialTokens number of initial tokens
     * @return this builder
     */
    public WorkflowBuilder place(String id, String name, int initialTokens) {
        places.put(id, new Place(id, name, initialTokens));
        return this;
    }

    /**
     * Add a place with zero initial tokens.
     *
     * @param id   place identifier
     * @param name human-readable name
     * @return this builder
     */
    public WorkflowBuilder place(String id, String name) {
        return place(id, name, 0);
    }

    /**
     * Add a regular transition.
     *
     * @param id       transition identifier
     * @param name     human-readable name
     * @param taskType task type (manual, automatic, service)
     * @return this builder
     */
    public WorkflowBuilder transition(String id, String name, String taskType) {
        transitions.add(new Transition(id, name, taskType, false, false));
        return this;
    }

    /**
     * Add a final transition (marks end of workflow).
     *
     * @param id       transition identifier
     * @param name     human-readable name
     * @param taskType task type
     * @return this builder
     */
    public WorkflowBuilder finalTransition(String id, String name, String taskType) {
        transitions.add(new Transition(id, name, taskType, true, false));
        return this;
    }

    /**
     * Add a multi-instance transition (can fire multiple times).
     *
     * @param id       transition identifier
     * @param name     human-readable name
     * @param taskType task type
     * @return this builder
     */
    public WorkflowBuilder multiInstanceTransition(String id, String name, String taskType) {
        transitions.add(new Transition(id, name, taskType, false, true));
        return this;
    }

    /**
     * Set the initial place where workflow execution begins.
     *
     * @param placeId the ID of the initial place (must have been added via place())
     * @return this builder
     */
    public WorkflowBuilder startAt(String placeId) {
        this.initialPlaceId = Objects.requireNonNull(placeId);
        return this;
    }

    /**
     * Build the immutable WorkflowDef.
     *
     * @return a new WorkflowDef
     * @throws IllegalStateException if no places, no transitions, or no initial place set
     */
    public WorkflowDef build() {
        if (places.isEmpty()) {
            throw new IllegalStateException("At least one place must be defined");
        }
        if (transitions.isEmpty()) {
            throw new IllegalStateException("At least one transition must be defined");
        }
        if (initialPlaceId == null) {
            throw new IllegalStateException("Initial place must be set via startAt()");
        }

        Place initialPlace = places.get(initialPlaceId);
        if (initialPlace == null) {
            throw new IllegalStateException(
                "Initial place '" + initialPlaceId + "' not found in defined places");
        }

        return new WorkflowDef(
            workflowId,
            name,
            description,
            new ArrayList<>(places.values()),
            transitions,
            initialPlace
        );
    }
}
