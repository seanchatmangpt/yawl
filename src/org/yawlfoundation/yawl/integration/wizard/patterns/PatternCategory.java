package org.yawlfoundation.yawl.integration.wizard.patterns;

/**
 * Categories of van der Aalst workflow patterns.
 *
 * <p>Provides classification of workflow patterns into logical groups,
 * facilitating pattern selection and recommendation based on scenario requirements.
 *
 * <p>Based on van der Aalst, W.M.P., ter Hofstede, A.H.M., Kiepuszewski, B.,
 * Barros, A.P. (2003). "Workflow Patterns." Distributed and Parallel Databases, 14, 5–51.
 */
public enum PatternCategory {
    /**
     * Basic control flow patterns (WP-1 to WP-5).
     * Foundation patterns for sequential, parallel, and conditional execution.
     */
    BASIC("Basic Control Flow"),

    /**
     * Advanced branching and synchronization patterns (WP-6 to WP-9).
     * Complex multi-way joins and splits with deferred choice semantics.
     */
    ADVANCED_BRANCHING("Advanced Branching and Synchronization"),

    /**
     * Structural patterns (WP-10 to WP-11).
     * Patterns handling cycles and implicit termination.
     */
    STRUCTURAL("Structural Patterns"),

    /**
     * Multiple instance patterns (WP-12 to WP-15).
     * Patterns for creating and synchronizing multiple instances of tasks.
     */
    MULTIPLE_INSTANCES("Multiple Instance Patterns"),

    /**
     * State-based patterns (WP-16 to WP-18).
     * Patterns based on state observation rather than explicit synchronization.
     */
    STATE_BASED("State-Based Patterns"),

    /**
     * Cancellation patterns (WP-19 to WP-20, WP-25 to WP-27).
     * Patterns for cancelling tasks or entire case instances.
     */
    CANCELLATION("Cancellation Patterns"),

    /**
     * Iteration patterns (WCP-21 to WCP-22).
     * Patterns for structured loops and recursion.
     */
    ITERATION("Iteration Patterns"),

    /**
     * Trigger patterns (WCP-23 to WCP-24).
     * Patterns for transient and persistent triggers.
     */
    TRIGGER("Trigger Patterns"),

    /**
     * Synchronisation patterns (WCP-33).
     * Advanced synchronisation patterns with generalised AND-join semantics.
     */
    SYNCHRONISATION("Synchronisation Patterns");

    private final String displayName;

    PatternCategory(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the human-readable display name for this category.
     *
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }
}
