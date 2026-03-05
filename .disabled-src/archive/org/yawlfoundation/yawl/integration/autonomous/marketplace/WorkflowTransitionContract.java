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
 * Dimension 3: YAWL workflow transition compatibility contract for a marketplace agent.
 *
 * <p>An agent in the CONSTRUCT-native marketplace does not merely offer a capability —
 * it offers a capability that fits a formally specified process position. This record
 * captures what a transition consumes and produces in terms of typed tokens, and which
 * van der Aalst Workflow Control-flow Patterns (WCPs) it is compatible with.</p>
 *
 * <p>This enables the marketplace to answer a class of questions that no current
 * agent registry can approach:</p>
 * <blockquote>
 *   Given my YAWL workflow model and my current ontology O, which agents can fill
 *   this transition slot while preserving soundness?
 * </blockquote>
 *
 * <p>Soundness here means the Petri-net theoretic property: the workflow cannot
 * deadlock, every task can eventually complete, and no work item is lost. An agent
 * that consumes the wrong token types or produces unexpected token types breaks
 * soundness; the marketplace filters these out before they can be deployed.</p>
 *
 * <p>WCP codes reference the van der Aalst et al. pattern catalogue
 * (WCP-1 through WCP-43 in the extended classification). Agents declare only the
 * patterns they are explicitly designed to handle — not patterns they might muddle
 * through with inference.</p>
 *
 * @param wcpPatterns          WCP codes this agent explicitly supports
 *                             (e.g. {@code ["WCP-1", "WCP-5", "WCP-17"]});
 *                             never null, never contains null
 * @param inputPlaceTokenTypes RDF types of tokens this agent consumes from input
 *                             places (e.g. {@code ["yawls:WorkItem", "yawls:Task"]});
 *                             never null, never contains null
 * @param outputPlaceTokenTypes RDF types of tokens this agent deposits into output
 *                              places; never null, never contains null
 * @param maintainsSoundness   whether this agent is declared to preserve YAWL
 *                             workflow soundness for its declared WCP patterns
 * @since YAWL 6.0
 */
public record WorkflowTransitionContract(
        List<String> wcpPatterns,
        List<String> inputPlaceTokenTypes,
        List<String> outputPlaceTokenTypes,
        boolean maintainsSoundness) {

    /** Compact constructor: validates and creates immutable defensive copies. */
    public WorkflowTransitionContract {
        Objects.requireNonNull(wcpPatterns, "wcpPatterns is required");
        Objects.requireNonNull(inputPlaceTokenTypes, "inputPlaceTokenTypes is required");
        Objects.requireNonNull(outputPlaceTokenTypes, "outputPlaceTokenTypes is required");

        if (wcpPatterns.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("wcpPatterns must not contain null elements");
        }
        if (inputPlaceTokenTypes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("inputPlaceTokenTypes must not contain null elements");
        }
        if (outputPlaceTokenTypes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("outputPlaceTokenTypes must not contain null elements");
        }

        wcpPatterns = List.copyOf(wcpPatterns);
        inputPlaceTokenTypes = List.copyOf(inputPlaceTokenTypes);
        outputPlaceTokenTypes = List.copyOf(outputPlaceTokenTypes);
    }

    /**
     * Returns true if this contract is compatible with the requested WCP pattern.
     *
     * <p>An agent is compatible with a pattern if it explicitly declares support —
     * implicit support via inference is not accepted. Marketplace buyers specify
     * the exact WCP code(s) their transition requires.</p>
     *
     * @param wcpCode the WCP code to check (e.g. {@code "WCP-17"})
     * @return true iff this contract declares the pattern
     */
    public boolean supportsPattern(String wcpCode) {
        Objects.requireNonNull(wcpCode, "wcpCode must not be null");
        return wcpPatterns.contains(wcpCode);
    }

    /**
     * Returns true if this contract can consume all required input token types.
     *
     * <p>Used in transition-slot matching to verify token type compatibility:
     * an agent must be able to accept every token type that the workflow places
     * on its input arc.</p>
     *
     * @param required the token types the slot requires as input
     * @return true iff {@code inputPlaceTokenTypes} contains every required type
     */
    public boolean acceptsInputTypes(List<String> required) {
        Objects.requireNonNull(required, "required must not be null");
        return inputPlaceTokenTypes.containsAll(required);
    }

    /**
     * Returns true if this contract produces all required output token types.
     *
     * <p>Used in transition-slot matching to verify that the agent's output is
     * consumable by subsequent transitions in the workflow.</p>
     *
     * @param required the token types the slot requires as output
     * @return true iff {@code outputPlaceTokenTypes} contains every required type
     */
    public boolean producesOutputTypes(List<String> required) {
        Objects.requireNonNull(required, "required must not be null");
        return outputPlaceTokenTypes.containsAll(required);
    }

    /**
     * Returns a no-op contract that makes no claims about workflow compatibility.
     *
     * <p>Agents using this contract will not match any WCP-specific or
     * token-type-specific marketplace queries.</p>
     *
     * @return an empty contract with {@code maintainsSoundness = false}
     */
    public static WorkflowTransitionContract unconstrained() {
        return new WorkflowTransitionContract(List.of(), List.of(), List.of(), false);
    }

    /**
     * Returns a builder for constructing a {@code WorkflowTransitionContract} incrementally.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@code WorkflowTransitionContract}. */
    public static final class Builder {
        private final java.util.ArrayList<String> wcps = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> inputs = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> outputs = new java.util.ArrayList<>();
        private boolean soundness = false;

        private Builder() {}

        /** Adds a supported WCP pattern code. */
        public Builder wcp(String wcpCode) {
            wcps.add(Objects.requireNonNull(wcpCode, "wcpCode must not be null"));
            return this;
        }

        /** Adds an input place token type. */
        public Builder inputType(String type) {
            inputs.add(Objects.requireNonNull(type, "type must not be null"));
            return this;
        }

        /** Adds an output place token type. */
        public Builder outputType(String type) {
            outputs.add(Objects.requireNonNull(type, "type must not be null"));
            return this;
        }

        /** Declares that this contract preserves YAWL soundness. */
        public Builder maintainsSoundness() {
            this.soundness = true;
            return this;
        }

        /** Builds the {@code WorkflowTransitionContract}. */
        public WorkflowTransitionContract build() {
            return new WorkflowTransitionContract(wcps, inputs, outputs, soundness);
        }
    }
}
