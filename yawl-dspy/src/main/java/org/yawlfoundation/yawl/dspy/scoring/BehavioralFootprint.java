/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.dspy.scoring;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the behavioral footprint of a workflow.
 * A behavioral footprint captures control-flow relationships between activities
 * in three dimensions: direct succession, concurrency, and exclusivity.
 *
 * <h2>Structure</h2>
 * <ul>
 *   <li><b>Direct Succession</b>: Set of pairs (A, B) where task A is immediately followed by task B</li>
 *   <li><b>Concurrency</b>: Set of pairs (A, B) where tasks A and B can execute in parallel</li>
 *   <li><b>Exclusivity</b>: Set of pairs (A, B) where tasks A and B are mutually exclusive</li>
 * </ul>
 *
 * <h2>Immutability</h2>
 * <p>This class is immutable. All sets are defensively copied to ensure thread safety.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a behavioral footprint
 * Set<List<String>> directSuccession = Set.of(List.of("A", "B"), List.of("B", "C"));
 * Set<List<String>> concurrency = Set.of(List.of("A", "C"));
 * Set<List<String>> exclusivity = Set.of(List.of("B", "D"));
 *
 * BehavioralFootprint footprint = new BehavioralFootprint(
 *     directSuccession, concurrency, exclusivity
 * );
 *
 * // Get the relationships
 * Set<List<String>> ds = footprint.directSuccession(); // {(A,B), (B,C)}
 * Set<List<String>> conc = footprint.concurrency();   // {(A,C)}
 * Set<List<String>> excl = footprint.exclusivity();    // {(B,D)}
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class BehavioralFootprint {

    private final Set<List<String>> directSuccession;
    private final Set<List<String>> concurrency;
    private final Set<List<String>> exclusivity;

    /**
     * Constructs a BehavioralFootprint with the specified relationships.
     *
     * @param directSuccession direct succession relationships (must not be null)
     * @param concurrency concurrency relationships (must not be null)
     * @param exclusivity exclusivity relationships (must not be null)
     * @throws IllegalArgumentException if any set is null
     */
    public BehavioralFootprint(Set<List<String>> directSuccession,
                              Set<List<String>> concurrency,
                              Set<List<String>> exclusivity) {
        Objects.requireNonNull(directSuccession, "Direct succession must not be null");
        Objects.requireNonNull(concurrency, "Concurrency must not be null");
        Objects.requireNonNull(exclusivity, "Exclusivity must not be null");

        this.directSuccession = Collections.unmodifiableSet(new HashSet<>(directSuccession));
        this.concurrency = Collections.unmodifiableSet(new HashSet<>(concurrency));
        this.exclusivity = Collections.unmodifiableSet(new HashSet<>(exclusivity));
    }

    /**
     * Gets the direct succession relationships.
     * Returns an unmodifiable view of the set.
     *
     * @return set of direct succession pairs (A, B)
     */
    public Set<List<String>> directSuccession() {
        return directSuccession;
    }

    /**
     * Gets the concurrency relationships.
     * Returns an unmodifiable view of the set.
     *
     * @return set of concurrency pairs (A, B)
     */
    public Set<List<String>> concurrency() {
        return concurrency;
    }

    /**
     * Gets the exclusivity relationships.
     * Returns an unmodifiable view of the set.
     *
     * @return set of exclusivity pairs (A, B)
     */
    public Set<List<String>> exclusivity() {
        return exclusivity;
    }

    /**
     * Creates an empty behavioral footprint (no relationships).
     *
     * @return empty BehavioralFootprint
     */
    public static BehavioralFootprint empty() {
        return new BehavioralFootprint(
            Set.of(), Set.of(), Set.of()
        );
    }

    /**
     * Creates a behavioral footprint with only direct succession relationships.
     *
     * @param directSuccession direct succession relationships
     * @return BehavioralFootprint with only direct succession
     */
    public static BehavioralFootprint onlyDirectSuccession(Set<List<String>> directSuccession) {
        return new BehavioralFootprint(
            directSuccession, Set.of(), Set.of()
        );
    }

    /**
     * Gets the total number of relationships across all dimensions.
     *
     * @return sum of relationships in all dimensions
     */
    public int totalRelationships() {
        return directSuccession.size() + concurrency.size() + exclusivity.size();
    }

    /**
     * Checks if this footprint is empty (no relationships in any dimension).
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return directSuccession.isEmpty() && concurrency.isEmpty() && exclusivity.isEmpty();
    }

    /**
     * Checks if this footprint contains the specified direct succession relationship.
     *
     * @param source the source task
     * @param target the target task
     * @return true if the relationship exists, false otherwise
     */
    public boolean hasDirectSuccession(String source, String target) {
        return directSuccession.contains(List.of(source, target));
    }

    /**
     * Checks if this footprint contains the specified concurrency relationship.
     *
     * @param task1 the first task
     * @param task2 the second task
     * @return true if the relationship exists, false otherwise
     */
    public boolean hasConcurrency(String task1, String task2) {
        return concurrency.contains(List.of(task1, task2)) || concurrency.contains(List.of(task2, task1));
    }

    /**
     * Checks if this footprint contains the specified exclusivity relationship.
     *
     * @param task1 the first task
     * @param task2 the second task
     * @return true if the relationship exists, false otherwise
     */
    public boolean hasExclusivity(String task1, String task2) {
        return exclusivity.contains(List.of(task1, task2)) || exclusivity.contains(List.of(task2, task1));
    }

    /**
     * Gets all unique task IDs involved in this footprint.
     *
     * @return set of task IDs
     */
    public Set<String> getAllTaskIds() {
        Set<String> allTasks = new HashSet<>();

        for (List<String> pair : directSuccession) {
            allTasks.add(pair.get(0));
            allTasks.add(pair.get(1));
        }

        for (List<String> pair : concurrency) {
            allTasks.add(pair.get(0));
            allTasks.add(pair.get(1));
        }

        for (List<String> pair : exclusivity) {
            allTasks.add(pair.get(0));
            allTasks.add(pair.get(1));
        }

        return allTasks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BehavioralFootprint that = (BehavioralFootprint) o;
        return directSuccession.equals(that.directSuccession) &&
               concurrency.equals(that.concurrency) &&
               exclusivity.equals(that.exclusivity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(directSuccession, concurrency, exclusivity);
    }

    @Override
    public String toString() {
        return "BehavioralFootprint{" +
               "directSuccession=" + directSuccession +
               ", concurrency=" + concurrency +
               ", exclusivity=" + exclusivity +
               '}';
    }
}