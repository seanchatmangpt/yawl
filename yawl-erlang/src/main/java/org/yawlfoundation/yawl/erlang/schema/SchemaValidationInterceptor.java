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
package org.yawlfoundation.yawl.erlang.schema;

import org.yawlfoundation.yawl.erlang.workflow.TaskSchemaViolation;
import org.yawlfoundation.yawl.erlang.workflow.WorkflowEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates JSON input and output at workflow task boundaries against ODCS schema contracts.
 *
 * <p>The interceptor performs structural validation of JSON payloads against the
 * {@link ParsedSchema} registered for each task. Validation checks:</p>
 * <ol>
 *   <li>All required fields are present in the JSON object</li>
 *   <li>Field values match the declared type ({@code string}, {@code integer},
 *       {@code number}, {@code boolean})</li>
 * </ol>
 *
 * <p>When validation fails the interceptor:</p>
 * <ol>
 *   <li>Publishes a {@link TaskSchemaViolation} event to the {@link WorkflowEventBus}</li>
 *   <li>Throws {@link TaskSchemaViolationException} to halt task execution</li>
 * </ol>
 *
 * <p>Tasks with no registered schema contract are passed through without validation.
 * This allows incremental schema adoption without requiring all tasks to be annotated.</p>
 *
 * <p>Usage:
 * <pre>
 *   SchemaValidationInterceptor interceptor =
 *       new SchemaValidationInterceptor(registry, eventBus);
 *
 *   // At task input boundary:
 *   interceptor.validateInput("ValidateOrderTask", inputJson);
 *
 *   // At task output boundary:
 *   interceptor.validateOutput("ValidateOrderTask", outputJson);
 * </pre>
 */
public final class SchemaValidationInterceptor {

    private static final Logger LOG = Logger.getLogger(SchemaValidationInterceptor.class.getName());

    // Matches a JSON object field: "fieldName": <value>
    private static final Pattern JSON_FIELD_PATTERN =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*([^,}\\]]+)");

    private final SchemaContractRegistry registry;
    private final WorkflowEventBus eventBus;

    /**
     * Creates an interceptor backed by the given registry and event bus.
     *
     * @param registry the schema contract registry
     * @param eventBus bus to publish {@link TaskSchemaViolation} events
     * @throws IllegalArgumentException if either argument is null
     */
    public SchemaValidationInterceptor(SchemaContractRegistry registry, WorkflowEventBus eventBus) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        if (eventBus == null) throw new IllegalArgumentException("eventBus must not be null");
        this.registry = registry;
        this.eventBus = eventBus;
    }

    /**
     * Validates a JSON input payload against the task's input schema contract.
     *
     * <p>If no input schema is registered for {@code taskId}, this method returns
     * immediately without validation. If the primary input schema fails and a fallback
     * schema is registered, the fallback is tried before failing.</p>
     *
     * @param taskId    the task class simple name
     * @param inputJson the JSON string to validate
     * @throws TaskSchemaViolationException if the JSON fails validation against both
     *                                      primary and fallback schemas
     * @throws IllegalArgumentException     if taskId is blank or inputJson is null
     */
    public void validateInput(String taskId, String inputJson) {
        if (taskId == null || taskId.isBlank())
            throw new IllegalArgumentException("taskId must not be blank");
        if (inputJson == null)
            throw new IllegalArgumentException("inputJson must not be null");

        Optional<ParsedSchema> primary = registry.getInputSchema(taskId);
        if (primary.isEmpty()) {
            LOG.fine("No input schema registered for task '" + taskId + "' — skipping validation");
            return;
        }

        List<String> violations = validate(primary.get(), inputJson);
        if (violations.isEmpty()) return;

        // Try fallback schema if primary fails
        Optional<ParsedSchema> fallback = registry.getInputFallbackSchema(taskId);
        if (fallback.isPresent()) {
            List<String> fallbackViolations = validate(fallback.get(), inputJson);
            if (fallbackViolations.isEmpty()) {
                LOG.fine("Task '" + taskId + "' input validated against fallback schema");
                return;
            }
        }

        String diff = String.join("; ", violations);
        String schemaSummary = primary.get().summary();
        publishAndThrow(taskId, schemaSummary, inputJson, diff);
    }

    /**
     * Validates a JSON output payload against the task's output schema contract.
     *
     * <p>If no output schema is registered for {@code taskId}, this method returns
     * immediately without validation.</p>
     *
     * @param taskId     the task class simple name
     * @param outputJson the JSON string to validate
     * @throws TaskSchemaViolationException if the JSON fails validation
     * @throws IllegalArgumentException     if taskId is blank or outputJson is null
     */
    public void validateOutput(String taskId, String outputJson) {
        if (taskId == null || taskId.isBlank())
            throw new IllegalArgumentException("taskId must not be blank");
        if (outputJson == null)
            throw new IllegalArgumentException("outputJson must not be null");

        Optional<ParsedSchema> schema = registry.getOutputSchema(taskId);
        if (schema.isEmpty()) {
            LOG.fine("No output schema registered for task '" + taskId + "' — skipping validation");
            return;
        }

        List<String> violations = validate(schema.get(), outputJson);
        if (violations.isEmpty()) return;

        String diff = String.join("; ", violations);
        String schemaSummary = schema.get().summary();
        publishAndThrow(taskId, schemaSummary, outputJson, diff);
    }

    // -------------------------------------------------------------------------
    // Private validation logic
    // -------------------------------------------------------------------------

    /**
     * Validates {@code json} against the given schema using structural checks.
     *
     * <p>Returns an empty list if valid, or a list of human-readable violation
     * descriptions if invalid.</p>
     */
    private List<String> validate(ParsedSchema schema, String json) {
        List<String> violations = new ArrayList<>();

        // Extract all field name→rawValue pairs from the JSON object
        java.util.Map<String, String> presentFields = extractJsonFields(json);

        for (SchemaField field : schema.fields()) {
            String rawValue = presentFields.get(field.name());

            if (rawValue == null) {
                if (field.required()) {
                    violations.add("missing required field '" + field.name() + "'");
                }
                continue;
            }

            if (!matchesType(rawValue.trim(), field.type())) {
                violations.add("field '" + field.name() + "' expected type '" + field.type()
                        + "' but value was: " + rawValue.trim());
            }
        }

        return violations;
    }

    /**
     * Extracts field name → raw value string from a flat JSON object.
     *
     * <p>This is a structural extractor, not a full JSON parser. It handles
     * well-formed single-depth JSON objects sufficient for workflow task payloads.
     * Nested objects are extracted as raw tokens (not recursed into).</p>
     */
    private java.util.Map<String, String> extractJsonFields(String json) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        Matcher m = JSON_FIELD_PATTERN.matcher(json);
        while (m.find()) {
            String fieldName = m.group(1);
            String rawValue = m.group(2).trim();
            fields.put(fieldName, rawValue);
        }
        return fields;
    }

    /**
     * Returns true if {@code rawValue} (a JSON token) matches the declared ODCS {@code type}.
     *
     * <p>Recognised types: {@code string}, {@code integer}, {@code number},
     * {@code boolean}. Unknown types are treated as valid (pass-through).</p>
     */
    private boolean matchesType(String rawValue, String type) {
        return switch (type) {
            case "string" -> rawValue.startsWith("\"") && rawValue.endsWith("\"");
            case "integer" -> isInteger(rawValue);
            case "number" -> isNumber(rawValue);
            case "boolean" -> rawValue.equals("true") || rawValue.equals("false");
            // Unknown type declarations pass through — forward compatibility
            default -> true;
        };
    }

    private boolean isInteger(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void publishAndThrow(String taskId, String schemaSummary, String json, String diff) {
        // Best-effort publish — do not let bus failures suppress the validation exception
        try {
            eventBus.publish(new TaskSchemaViolation(
                    "unknown-instance", taskId, schemaSummary, json, diff));
        } catch (Exception e) {
            LOG.warning("Failed to publish TaskSchemaViolation for task '" + taskId + "': " + e);
        }

        throw new TaskSchemaViolationException(taskId, schemaSummary, json, diff);
    }
}
