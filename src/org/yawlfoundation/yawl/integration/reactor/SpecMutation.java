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

package org.yawlfoundation.yawl.integration.reactor;

import java.util.Objects;

/**
 * Proposed mutation to a workflow specification.
 *
 * <p>Represents a suggested structural change to improve workflow performance or correctness.
 * Each mutation includes a type, target element, XML fragment, rationale, and risk assessment.</p>
 *
 * @param mutationType type of mutation (e.g., "ADD_PARALLEL_SPLIT", "REMOVE_REDUNDANT_TASK")
 * @param targetElement ID of the task/place to modify
 * @param mutationXml XML fragment describing the change (may be empty)
 * @param rationale human-readable explanation for the mutation
 * @param riskLevel risk assessment (LOW, MEDIUM, HIGH, CRITICAL)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record SpecMutation(
    String mutationType,
    String targetElement,
    String mutationXml,
    String rationale,
    RiskLevel riskLevel
) {
    /**
     * Risk level enumeration for mutations.
     */
    public enum RiskLevel {
        /** Safe to auto-commit; minimal side effects */
        LOW,
        /** Manual review recommended before commit */
        MEDIUM,
        /** Requires expert review; may affect stability */
        HIGH,
        /** Do not commit without extensive testing */
        CRITICAL
    }

    /**
     * Constructs a SpecMutation with validation.
     *
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if mutationType, targetElement, or rationale are blank
     */
    public SpecMutation {
        Objects.requireNonNull(mutationType, "mutationType must not be null");
        Objects.requireNonNull(targetElement, "targetElement must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");

        if (mutationType.isBlank()) {
            throw new IllegalArgumentException("mutationType must not be blank");
        }
        if (targetElement.isBlank()) {
            throw new IllegalArgumentException("targetElement must not be blank");
        }
        if (rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }

        if (mutationXml == null) {
            mutationXml = "";
        }
    }
}
