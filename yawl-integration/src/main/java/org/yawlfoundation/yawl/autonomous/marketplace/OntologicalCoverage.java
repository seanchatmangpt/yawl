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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import java.util.List;
import java.util.Objects;

/**
 * Dimension 2: Ontological coverage declaration for an agent marketplace entry.
 *
 * <p>Formally declares which slice of the shared ontology O an agent understands
 * and can correctly operate on. This is the machine-readable, verifiable contract
 * that distinguishes the CONSTRUCT-native marketplace from string-based capability
 * registries.</p>
 *
 * <p>An agent publishes:</p>
 * <ul>
 *   <li><b>Declared namespaces</b> — the RDF IRI prefixes this agent formally
 *       comprehends (e.g. {@code http://www.yawlfoundation.org/yawlschema#}).
 *       Marketplace queries can perform set-containment matching: which agents
 *       cover ALL namespaces required by my workflow?</li>
 *   <li><b>SPARQL patterns</b> — Basic Graph Patterns (BGPs) this agent can
 *       match against the coordination graph. Expressed as triple pattern
 *       templates (e.g. {@code ?task yawls:hasDecomposition ?decomp}).</li>
 *   <li><b>CONSTRUCT templates</b> — the CONSTRUCT query shapes this agent
 *       produces as output, enabling downstream agents to determine whether
 *       this agent's output satisfies their input pattern requirements.</li>
 * </ul>
 *
 * <p>All lists are immutable defensive copies. Null elements are rejected.</p>
 *
 * <p>This maps to the {@code [ontology]} section of {@code ggen.toml}, making
 * ggen.toml the formal publication artifact for marketplace registration.</p>
 *
 * @param declaredNamespaces   RDF namespace IRIs this agent formally understands;
 *                             never null, never contains null
 * @param sparqlPatterns       BGP triple patterns this agent can match against the
 *                             coordination graph; never null, never contains null
 * @param constructTemplates   CONSTRUCT output templates this agent produces;
 *                             never null, never contains null
 * @since YAWL 6.0
 */
public record OntologicalCoverage(
        List<String> declaredNamespaces,
        List<String> sparqlPatterns,
        List<String> constructTemplates) {

    /** Compact constructor: validates and creates immutable defensive copies. */
    public OntologicalCoverage {
        Objects.requireNonNull(declaredNamespaces, "declaredNamespaces is required");
        Objects.requireNonNull(sparqlPatterns, "sparqlPatterns is required");
        Objects.requireNonNull(constructTemplates, "constructTemplates is required");

        if (declaredNamespaces.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("declaredNamespaces must not contain null elements");
        }
        if (sparqlPatterns.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sparqlPatterns must not contain null elements");
        }
        if (constructTemplates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("constructTemplates must not contain null elements");
        }

        declaredNamespaces = List.copyOf(declaredNamespaces);
        sparqlPatterns = List.copyOf(sparqlPatterns);
        constructTemplates = List.copyOf(constructTemplates);
    }

    /**
     * Returns true if this coverage declares all namespaces in {@code required}.
     *
     * <p>This is the primary ontological containment predicate used in marketplace
     * queries: an agent must formally understand every namespace a buyer requires.</p>
     *
     * @param required the namespaces a buyer requires
     * @return true iff {@code declaredNamespaces} contains every element of {@code required}
     */
    public boolean coversAllNamespaces(List<String> required) {
        Objects.requireNonNull(required, "required must not be null");
        return declaredNamespaces.containsAll(required);
    }

    /**
     * Returns true if this coverage can match all patterns in {@code required}.
     *
     * <p>Used in transition-slot matching: an agent must be able to match every
     * SPARQL BGP that a workflow transition requires it to handle.</p>
     *
     * @param required the SPARQL BGP patterns a buyer requires
     * @return true iff {@code sparqlPatterns} contains every element of {@code required}
     */
    public boolean matchesAllPatterns(List<String> required) {
        Objects.requireNonNull(required, "required must not be null");
        return sparqlPatterns.containsAll(required);
    }

    /**
     * Returns an empty {@code OntologicalCoverage} with no declared coverage.
     *
     * <p>Useful for agents that operate purely on inference without a formal
     * ontological declaration. Such agents will never match namespace or pattern
     * predicates in marketplace queries.</p>
     *
     * @return an empty coverage instance
     */
    public static OntologicalCoverage empty() {
        return new OntologicalCoverage(List.of(), List.of(), List.of());
    }

    /**
     * Returns a builder for constructing an {@code OntologicalCoverage} incrementally.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@code OntologicalCoverage}. */
    public static final class Builder {
        private final java.util.ArrayList<String> namespaces = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> patterns = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> templates = new java.util.ArrayList<>();

        private Builder() {}

        /** Adds a declared RDF namespace IRI. */
        public Builder namespace(String iri) {
            namespaces.add(Objects.requireNonNull(iri, "iri must not be null"));
            return this;
        }

        /** Adds a SPARQL BGP triple pattern. */
        public Builder sparqlPattern(String pattern) {
            patterns.add(Objects.requireNonNull(pattern, "pattern must not be null"));
            return this;
        }

        /** Adds a CONSTRUCT output template. */
        public Builder constructTemplate(String template) {
            templates.add(Objects.requireNonNull(template, "template must not be null"));
            return this;
        }

        /** Builds the {@code OntologicalCoverage}. */
        public OntologicalCoverage build() {
            return new OntologicalCoverage(namespaces, patterns, templates);
        }
    }
}
