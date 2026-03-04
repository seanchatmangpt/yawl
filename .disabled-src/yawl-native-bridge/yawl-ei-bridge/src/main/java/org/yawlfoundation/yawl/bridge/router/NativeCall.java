/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.bridge.router;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a native call ontology triple for routing through the bridge.
 * Immutable data carrier for subject-predicate-object triples with execution metadata.
 *
 * <p>This record encapsulates the fundamental unit of native bridge operations,
 * containing the RDF triple components and routing information needed for
 * domain-specific execution (JVM, BEAM, or DIRECT).</p>
 */
public final record NativeCall(
    /**
     * Subject URI of the ontology triple.
     * Must be a valid URI string (absolute or relative).
     */
    String subject,

    /**
     * Predicate URI of the ontology triple.
     * Must be a valid URI string defining the relationship.
     */
    String predicate,

    /**
     * Object URI or literal value of the ontology triple.
     * Can be URI, literal, or blank node identifier.
     */
    String object,

    /**
     * Call pattern specifying the execution domain.
     * Determines how the triple should be processed.
     */
    CallPattern callPattern,

    /**
     * Optional correlation ID for tracing across execution domains.
     * If null, a random UUID will be generated during execution.
     */
    UUID correlationId
) {
    public NativeCall {
        Objects.requireNonNull(subject, "Subject cannot be null");
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        Objects.requireNonNull(object, "Object cannot be null");
        Objects.requireNonNull(callPattern, "CallPattern cannot be null");

        validateSubject(subject);
        validatePredicate(predicate);
        validateObject(object);
    }

    /**
     * Validates that the subject is a valid URI or blank node.
     *
     * @param subject to validate
     * @throws IllegalArgumentException if subject is invalid
     */
    private static void validateSubject(String subject) {
        if (subject.isBlank()) {
            throw new IllegalArgumentException("Subject cannot be blank");
        }

        // Allow blank nodes (_:id) or URIs (containing :, /, #)
        if (!(subject.startsWith("_:") || subject.contains(":") ||
              subject.contains("/") || subject.contains("#"))) {
            throw new IllegalArgumentException(
                "Subject must be a URI or blank node: " + subject
            );
        }
    }

    /**
     * Validates that the predicate is a valid URI.
     *
     * @param predicate to validate
     * @throws IllegalArgumentException if predicate is invalid
     */
    private static void validatePredicate(String predicate) {
        if (predicate.isBlank()) {
            throw new IllegalArgumentException("Predicate cannot be blank");
        }

        // Predicates must be URIs
        if (!(predicate.contains(":") || predicate.contains("/") ||
              predicate.contains("#"))) {
            throw new IllegalArgumentException(
                "Predicate must be a URI: " + predicate
            );
        }
    }

    /**
     * Validates that the object is valid.
     *
     * @param object to validate
     * @throws IllegalArgumentException if object is invalid
     */
    private static void validateObject(String object) {
        if (object.isBlank()) {
            throw new IllegalArgumentException("Object cannot be blank");
        }

        // Allow blank nodes, URIs, or literals
        if (!(object.startsWith("_:") || object.contains(":") ||
              object.contains("/") || object.contains("#") ||
              isLiteral(object))) {
            throw new IllegalArgumentException(
                "Object must be a URI, blank node, or literal: " + object
            );
        }
    }

    /**
     * Checks if a string appears to be a literal value.
     *
     * @param value to check
     * @return true if it appears to be a literal
     */
    private static boolean isLiteral(String value) {
        // Literals often have quotes or don't have URI-like patterns
        return value.startsWith("\"") || value.startsWith("'") ||
               !value.matches("^[^:/?#\\s]+$");
    }

    /**
     * Creates a NativeCall with an automatically generated correlation ID.
     *
     * @param subject URI of the triple subject
     * @param predicate URI of the triple predicate
     * @param object URI or literal value of the triple object
     * @param callPattern execution domain for the call
     * @return new NativeCall instance
     */
    public static NativeCall of(String subject, String predicate, String object,
                                CallPattern callPattern) {
        return new NativeCall(subject, predicate, object, callPattern, UUID.randomUUID());
    }

    /**
     * Creates a NativeCall with specified correlation ID.
     *
     * @param subject URI of the triple subject
     * @param predicate URI of the triple predicate
     * @param object URI or literal value of the triple object
     * @param callPattern execution domain for the call
     * @param correlationId tracing correlation ID
     * @return new NativeCall instance
     */
    public static NativeCall withId(String subject, String predicate, String object,
                                   CallPattern callPattern, UUID correlationId) {
        return new NativeCall(subject, predicate, object, callPattern, correlationId);
    }

    /**
     * Gets a string representation of the triple.
     * Format: "subject predicate object"
     *
     * @return N-triple formatted string
     */
    public String toNtriple() {
        return String.format("%s %s %s .", subject, predicate, object);
    }

    /**
     * Checks if this triple represents a literal object.
     *
     * @return true if object appears to be a literal
     */
    public boolean isLiteralObject() {
        return isLiteral(object);
    }

    /**
     * Gets the execution domain description.
     *
     * @return human-readable description of where this call will execute
     */
    public String getExecutionDomain() {
        return callPattern.getDescription();
    }

    /**
     * Checks if this call can be executed in the specified domain.
     *
     * @param pattern to check against
     * @return true if the call can be executed in this domain
     */
    public boolean canExecuteIn(CallPattern pattern) {
        return callPattern == pattern && pattern.isExecutable();
    }
}