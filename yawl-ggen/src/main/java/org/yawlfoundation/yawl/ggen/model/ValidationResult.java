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

package org.yawlfoundation.yawl.ggen.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Validation result from multi-layer validation pipeline.
 *
 * <p>Provides comprehensive validation feedback across all layers:
 * XSD schema validation, rust4pm soundness checking, and virtual execution.
 *
 * @param valid Overall validation status
 * @param xsdValid XSD schema validation passed
 * @param soundnessValid rust4pm soundness check passed
 * @param executionValid Virtual execution passed
 * @param xsdErrors XSD validation errors (if any)
 * @param deadlocks Detected deadlock nodes (if any)
 * @param lackOfSync Detected lack of synchronization (if any)
 * @param executionErrors Virtual execution errors (if any)
 * @param validatedAt Validation timestamp
 * @param validationTimeMs Total validation time in milliseconds
 * @param metadata Additional metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationResult(
    boolean valid,
    @JsonProperty("xsd_valid") boolean xsdValid,
    @JsonProperty("soundness_valid") boolean soundnessValid,
    @JsonProperty("execution_valid") boolean executionValid,
    @JsonProperty("xsd_errors") List<String> xsdErrors,
    List<String> deadlocks,
    @JsonProperty("lack_of_sync") List<String> lackOfSync,
    @JsonProperty("execution_errors") List<ExecutionError> executionErrors,
    @JsonProperty("validated_at") Instant validatedAt,
    @JsonProperty("validation_time_ms") Long validationTimeMs,
    Map<String, Object> metadata
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ValidationResult {
        xsdErrors = xsdErrors != null ? List.copyOf(xsdErrors) : List.of();
        deadlocks = deadlocks != null ? List.copyOf(deadlocks) : List.of();
        lackOfSync = lackOfSync != null ? List.copyOf(lackOfSync) : List.of();
        executionErrors = executionErrors != null ? List.copyOf(executionErrors) : List.of();
        validatedAt = validatedAt != null ? validatedAt : Instant.now();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a successful validation result.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, true, true, true,
            List.of(), List.of(), List.of(), List.of(),
            Instant.now(), null, Map.of());
    }

    /**
     * Create a failed validation result.
     */
    public static ValidationResult failure(boolean xsdValid, boolean soundnessValid, boolean executionValid,
                                           List<String> xsdErrors, List<String> deadlocks,
                                           List<String> lackOfSync, List<ExecutionError> executionErrors) {
        return new ValidationResult(false, xsdValid, soundnessValid, executionValid,
            xsdErrors, deadlocks, lackOfSync, executionErrors,
            Instant.now(), null, Map.of());
    }

    /**
     * Create an XSD-only validation failure.
     */
    public static ValidationResult xsdFailure(List<String> errors) {
        return new ValidationResult(false, false, true, true,
            errors, List.of(), List.of(), List.of(),
            Instant.now(), null, Map.of());
    }

    /**
     * Create a soundness validation failure.
     */
    public static ValidationResult soundnessFailure(List<String> deadlocks, List<String> lackOfSync) {
        return new ValidationResult(false, true, false, true,
            List.of(), deadlocks, lackOfSync, List.of(),
            Instant.now(), null, Map.of());
    }

    /**
     * Create an execution validation failure.
     */
    public static ValidationResult executionFailure(List<ExecutionError> errors) {
        return new ValidationResult(false, true, true, false,
            List.of(), List.of(), List.of(), errors,
            Instant.now(), null, Map.of());
    }

    /**
     * Convert to JSON.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ValidationResult to JSON", e);
        }
    }

    /**
     * Parse from JSON.
     */
    public static ValidationResult fromJson(String json) {
        try {
            return MAPPER.readValue(json, ValidationResult.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse ValidationResult from JSON", e);
        }
    }

    /**
     * Get summary of validation issues.
     */
    public String getSummary() {
        if (valid) {
            return "Validation passed";
        }

        StringBuilder sb = new StringBuilder("Validation failed: ");

        if (!xsdValid) {
            sb.append(String.format("XSD errors: %d. ", xsdErrors.size()));
        }
        if (!soundnessValid) {
            sb.append(String.format("Deadlocks: %d, Lack of sync: %d. ",
                deadlocks.size(), lackOfSync.size()));
        }
        if (!executionValid) {
            sb.append(String.format("Execution errors: %d. ", executionErrors.size()));
        }

        return sb.toString().trim();
    }

    /**
     * Execution error detail.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExecutionError(
        String task,
        String reason,
        String caseId,
        Map<String, Object> context
    ) {
        public ExecutionError {
            context = context != null ? Map.copyOf(context) : Map.of();
        }

        public static ExecutionError of(String task, String reason) {
            return new ExecutionError(task, reason, null, Map.of());
        }

        public static ExecutionError withCase(String task, String reason, String caseId) {
            return new ExecutionError(task, reason, caseId, Map.of());
        }
    }
}
