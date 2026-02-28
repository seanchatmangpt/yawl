/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.datamodelling.queries;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Advanced filtering utilities for data modelling artifacts.
 *
 * <p>Provides predicates and filtering logic for complex query scenarios including
 * owner filtering, infrastructure type filtering, medallion layer filtering,
 * and custom tag filtering with support for boolean combinations (AND, OR, NOT).</p>
 *
 * <p>Thread-safe and stateless — can be used concurrently across multiple queries.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class AdvancedFiltering {

    private AdvancedFiltering() {
        // Utility class
    }

    // ── Owner Filtering ────────────────────────────────────────────────────────

    /**
     * Create a predicate that matches artifacts owned by the given owner.
     *
     * @param owner the owner name/email (case-sensitive)
     * @return a predicate that returns true if artifact.owner == owner
     */
    public static <T extends Owned> Predicate<T> byOwner(String owner) {
        return artifact -> {
            String artifactOwner = artifact.getOwner();
            return artifactOwner != null && artifactOwner.equals(owner);
        };
    }

    /**
     * Create a predicate that matches artifacts owned by any of the given owners.
     *
     * @param owners the set of owners to match against
     * @return a predicate that returns true if artifact.owner is in the set
     */
    public static <T extends Owned> Predicate<T> byOwners(Collection<String> owners) {
        Set<String> ownerSet = new HashSet<>(owners);
        return artifact -> {
            String artifactOwner = artifact.getOwner();
            return artifactOwner != null && ownerSet.contains(artifactOwner);
        };
    }

    // ── Infrastructure Type Filtering ──────────────────────────────────────────

    /**
     * Create a predicate that matches artifacts with the given infrastructure type.
     *
     * @param infrastructureType the infrastructure type (e.g. "postgresql", "warehouse", "lake")
     * @return a predicate that returns true if artifact.infrastructureType == infrastructureType
     */
    public static <T extends InfrastructureTyped> Predicate<T> byInfrastructureType(
            String infrastructureType) {
        return artifact -> {
            String infra = artifact.getInfrastructureType();
            return infra != null && infra.equals(infrastructureType);
        };
    }

    /**
     * Create a predicate that matches artifacts with any of the given infrastructure types.
     *
     * @param types the set of infrastructure types to match
     * @return a predicate that returns true if artifact.infrastructureType is in the set
     */
    public static <T extends InfrastructureTyped> Predicate<T> byInfrastructureTypes(
            Collection<String> types) {
        Set<String> typeSet = new HashSet<>(types);
        return artifact -> {
            String infra = artifact.getInfrastructureType();
            return infra != null && typeSet.contains(infra);
        };
    }

    // ── Medallion Layer Filtering ──────────────────────────────────────────────

    /**
     * Create a predicate that matches tables with the given medallion layer.
     *
     * @param layer the medallion layer (e.g. "bronze", "silver", "gold")
     * @return a predicate that returns true if table.medallionLayer == layer
     */
    public static <T extends MedallionLayered> Predicate<T> byMedallionLayer(String layer) {
        return artifact -> {
            String artLayer = artifact.getMedallionLayer();
            return artLayer != null && artLayer.equals(layer);
        };
    }

    /**
     * Create a predicate that matches tables in any of the given medallion layers.
     *
     * @param layers the set of medallion layers to match
     * @return a predicate that returns true if table.medallionLayer is in the set
     */
    public static <T extends MedallionLayered> Predicate<T> byMedallionLayers(
            Collection<String> layers) {
        Set<String> layerSet = new HashSet<>(layers);
        return artifact -> {
            String artLayer = artifact.getMedallionLayer();
            return artLayer != null && layerSet.contains(artLayer);
        };
    }

    // ── Tag Filtering ──────────────────────────────────────────────────────────

    /**
     * Create a predicate that matches artifacts having a specific tag.
     *
     * @param tagValue the tag value to search for (case-sensitive)
     * @return a predicate that returns true if tags contain tagValue
     */
    public static <T extends Tagged> Predicate<T> byTag(String tagValue) {
        return artifact -> {
            Collection<?> tags = artifact.getTags();
            if (tags == null) return false;
            return tags.stream()
                    .anyMatch(tag -> tagValue.equals(tagToString(tag)));
        };
    }

    /**
     * Create a predicate that matches artifacts having any of the given tags.
     *
     * @param tagValues the set of tag values to match against
     * @return a predicate that returns true if tags contain any tagValue
     */
    public static <T extends Tagged> Predicate<T> byTags(Collection<String> tagValues) {
        Set<String> tagSet = new HashSet<>(tagValues);
        return artifact -> {
            Collection<?> tags = artifact.getTags();
            if (tags == null) return false;
            return tags.stream()
                    .map(AdvancedFiltering::tagToString)
                    .anyMatch(tagSet::contains);
        };
    }

    /**
     * Create a predicate that matches artifacts having all of the given tags.
     *
     * @param requiredTags the set of tags all required in the artifact
     * @return a predicate that returns true if artifact tags contain all requiredTags
     */
    public static <T extends Tagged> Predicate<T> byAllTags(Collection<String> requiredTags) {
        Set<String> tagSet = new HashSet<>(requiredTags);
        return artifact -> {
            Collection<?> tags = artifact.getTags();
            if (tags == null) return tagSet.isEmpty();
            Set<String> artifactTags = new HashSet<>();
            tags.stream()
                    .map(AdvancedFiltering::tagToString)
                    .forEach(artifactTags::add);
            return artifactTags.containsAll(tagSet);
        };
    }

    /**
     * Create a predicate that matches artifacts NOT having the given tag.
     *
     * @param tagValue the tag value to exclude
     * @return a predicate that returns true if artifact does not have tagValue
     */
    public static <T extends Tagged> Predicate<T> byTagNot(String tagValue) {
        return byTag(tagValue).negate();
    }

    // ── Boolean Combinations ───────────────────────────────────────────────────

    /**
     * Create a composite predicate combining two predicates with AND logic.
     *
     * @param p1 the first predicate
     * @param p2 the second predicate
     * @return a predicate that returns true only if both p1 AND p2 return true
     */
    public static <T> Predicate<T> and(Predicate<T> p1, Predicate<T> p2) {
        return p1.and(p2);
    }

    /**
     * Create a composite predicate combining multiple predicates with AND logic.
     *
     * @param predicates the predicates to combine
     * @return a predicate that returns true only if all predicates return true
     */
    @SafeVarargs
    public static <T> Predicate<T> andAll(Predicate<T>... predicates) {
        if (predicates.length == 0) {
            return t -> true;
        }
        Predicate<T> result = predicates[0];
        for (int i = 1; i < predicates.length; i++) {
            result = result.and(predicates[i]);
        }
        return result;
    }

    /**
     * Create a composite predicate combining two predicates with OR logic.
     *
     * @param p1 the first predicate
     * @param p2 the second predicate
     * @return a predicate that returns true if either p1 OR p2 returns true
     */
    public static <T> Predicate<T> or(Predicate<T> p1, Predicate<T> p2) {
        return p1.or(p2);
    }

    /**
     * Create a composite predicate combining multiple predicates with OR logic.
     *
     * @param predicates the predicates to combine
     * @return a predicate that returns true if any predicate returns true
     */
    @SafeVarargs
    public static <T> Predicate<T> orAny(Predicate<T>... predicates) {
        if (predicates.length == 0) {
            return t -> false;
        }
        Predicate<T> result = predicates[0];
        for (int i = 1; i < predicates.length; i++) {
            result = result.or(predicates[i]);
        }
        return result;
    }

    /**
     * Create a predicate that negates the given predicate (NOT logic).
     *
     * @param predicate the predicate to negate
     * @return a predicate that returns true if the original returns false
     */
    public static <T> Predicate<T> not(Predicate<T> predicate) {
        return predicate.negate();
    }

    // ── Helper Methods ─────────────────────────────────────────────────────────

    /**
     * Convert a tag object to its string representation.
     * Handles both String tags and objects with name properties.
     *
     * @param tag the tag object
     * @return the string representation of the tag
     * @throws IllegalArgumentException if tag is null
     */
    private static String tagToString(Object tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Tag cannot be null. " +
                    "All tag objects must be non-null and either String or have a string representation.");
        }
        if (tag instanceof String str) {
            return str;
        }
        // Try to handle tag objects with getName() method
        try {
            Object name = tag.getClass().getMethod("getName")
                    .invoke(tag);
            return name != null ? name.toString() : tag.toString();
        } catch (NoSuchMethodException e) {
            // Fall through to toString()
            return tag.toString();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Unable to extract tag name from " + tag.getClass().getName() +
                    ". Ensure tag objects implement getName() or are Strings.",
                    e);
        }
    }

    // ── Marker Interfaces ──────────────────────────────────────────────────────

    /**
     * Marker interface for objects that have an owner property.
     */
    public interface Owned {
        String getOwner();
    }

    /**
     * Marker interface for objects that have an infrastructure type.
     */
    public interface InfrastructureTyped {
        String getInfrastructureType();
    }

    /**
     * Marker interface for objects that have a medallion layer.
     */
    public interface MedallionLayered {
        String getMedallionLayer();
    }

    /**
     * Marker interface for objects that have tags.
     */
    public interface Tagged {
        Collection<?> getTags();
    }
}
