/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.compliance.shacl;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YNetRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of a SHACL validation operation.
 *
 * <p>This record encapsulates the validation result for compliance checking
 * against SOX, GDPR, and HIPAA regulations. It provides detailed information
 * about validation status, errors, and performance metrics.</p>
 *
 * @param valid Whether the validation passed
 * @param complianceDomain The compliance domain (SOX, GDPR, HIPAA)
 * @param target The validated target (specification or engine)
 * @param violations List of validation violations
 * @param validationTime Time taken for validation in milliseconds
 * @param timestamp When the validation was performed
 * @param metadata Additional metadata about the validation
 */
public record ShaclValidationResult(
    boolean valid,
    ComplianceDomain complianceDomain,
    String target,
    List<ShaclViolation> violations,
    long validationTime,
    Instant timestamp,
    Map<String, Object> metadata
) {

    /**
     * Creates a successful validation result.
     */
    public static ShaclValidationResult success(
        ComplianceDomain complianceDomain,
        String target,
        long validationTime,
        Map<String, Object> metadata
    ) {
        return new ShaclValidationResult(
            true,
            complianceDomain,
            target,
            Collections.emptyList(),
            validationTime,
            Instant.now(),
            metadata
        );
    }

    /**
     * Creates a failed validation result with violations.
     */
    public static ShaclValidationResult failure(
        ComplianceDomain complianceDomain,
        String target,
        List<ShaclViolation> violations,
        long validationTime,
        Map<String, Object> metadata
    ) {
        return new ShaclValidationResult(
            false,
            complianceDomain,
            target,
            new ArrayList<>(violations),
            validationTime,
            Instant.now(),
            metadata
        );
    }

    /**
     * Gets the violation count.
     */
    public int getViolationCount() {
        return violations.size();
    }

    /**
     * Gets the severity level of the highest priority violation.
     */
    public ViolationSeverity getHighestSeverity() {
        if (violations.isEmpty()) {
            return ViolationSeverity.NONE;
        }
        return violations.stream()
            .map(ShaclViolation::severity)
            .max(ViolationSeverity::compareTo)
            .get();
    }

    /**
     * Gets violations filtered by severity.
     */
    public List<ShaclViolation> getViolationsBySeverity(ViolationSeverity severity) {
        return violations.stream()
            .filter(v -> v.severity() == severity)
            .toList();
    }

    /**
     * Gets violations filtered by focus node.
     */
    public List<ShaclViolation> getViolationsByFocusNode(String focusNode) {
        return violations.stream()
            .filter(v -> v.focusNode().equals(focusNode))
            .toList();
    }

    /**
     * Converts to a JSON string representation.
     */
    public String toJson() {
        return String.format(
            "{\"valid\":%b,\"domain\":\"%s\",\"target\":\"%s\",\"violations\":%d,\"validationTime\":%d,\"timestamp\":\"%s\"}",
            valid,
            complianceDomain,
            target,
            violations.size(),
            validationTime,
            timestamp.toString()
        );
    }
}