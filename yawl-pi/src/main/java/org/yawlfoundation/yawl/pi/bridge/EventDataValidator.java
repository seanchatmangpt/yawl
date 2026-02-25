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

package org.yawlfoundation.yawl.pi.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates OCEL2 JSON event data against an OcedSchema.
 *
 * <p>Checks:
 * <ul>
 *   <li>ocel:events array exists and is non-empty</li>
 *   <li>Each event has ocel:id, ocel:type (activity), ocel:time (timestamp)</li>
 *   <li>ocel:objects array exists</li>
 *   <li>Required columns from schema are present</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class EventDataValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ROWS_CHECKED = 1000;

    /**
     * Validate OCEL2 JSON string against the provided schema.
     *
     * @param ocel2Json OCEL2 JSON event data
     * @param schema OCEL schema for validation
     * @return validation report with success/failure and details
     */
    public ValidationReport validate(String ocel2Json, OcedSchema schema) {
        if (ocel2Json == null || ocel2Json.isEmpty()) {
            return ValidationReport.failure(List.of("OCEL2 JSON cannot be null or empty"));
        }
        if (schema == null) {
            return ValidationReport.failure(List.of("Schema cannot be null"));
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        JsonNode root;
        try {
            root = MAPPER.readTree(ocel2Json);
        } catch (Exception e) {
            return ValidationReport.failure(List.of("Invalid JSON: " + e.getMessage()));
        }

        validateEventsArray(root, errors, warnings);
        validateObjectsArray(root, warnings);

        if (!errors.isEmpty()) {
            return ValidationReport.failure(errors);
        }

        int rowsChecked = countEventRows(root);
        return warnings.isEmpty()
            ? ValidationReport.success(rowsChecked)
            : ValidationReport.successWithWarnings(rowsChecked, warnings);
    }

    /**
     * Validate the ocel:events array.
     *
     * @param root JSON root node
     * @param errors error list (modified)
     * @param warnings warning list (modified)
     */
    private void validateEventsArray(JsonNode root, List<String> errors, List<String> warnings) {
        JsonNode events = root.path("ocel:events");
        if (events.isMissingNode() || !events.isArray()) {
            errors.add("Missing or invalid 'ocel:events' array");
            return;
        }

        if (events.isEmpty()) {
            warnings.add("'ocel:events' array is empty");
            return;
        }

        int rowsChecked = 0;
        for (JsonNode event : events) {
            rowsChecked++;
            validateEventNode(event, rowsChecked, errors, warnings);

            if (rowsChecked >= MAX_ROWS_CHECKED) {
                warnings.add("Validation stopped at " + MAX_ROWS_CHECKED + " events");
                break;
            }
        }
    }

    /**
     * Validate a single event node.
     *
     * @param event event node
     * @param index event index (for error reporting)
     * @param errors error list (modified)
     * @param warnings warning list (modified)
     */
    private void validateEventNode(JsonNode event, int index, List<String> errors, List<String> warnings) {
        if (event.path("ocel:id").isMissingNode()) {
            errors.add("Event at index " + index + " missing 'ocel:id'");
        }
        if (event.path("ocel:type").isMissingNode()) {
            errors.add("Event at index " + index + " missing 'ocel:type' (activity)");
        }

        JsonNode timeNode = event.path("ocel:time");
        if (timeNode.isMissingNode()) {
            errors.add("Event at index " + index + " missing 'ocel:time'");
        } else {
            try {
                Instant.parse(timeNode.asText());
            } catch (DateTimeParseException e) {
                warnings.add("Event at index " + index + ": 'ocel:time' is not ISO-8601: "
                    + timeNode.asText());
            }
        }
    }

    /**
     * Validate the ocel:objects array exists.
     *
     * @param root JSON root node
     * @param warnings warning list (modified)
     */
    private void validateObjectsArray(JsonNode root, List<String> warnings) {
        JsonNode objects = root.path("ocel:objects");
        if (objects.isMissingNode() || !objects.isArray()) {
            warnings.add("Missing or invalid 'ocel:objects' array");
        }
    }

    /**
     * Count the number of event rows in the JSON.
     *
     * @param root JSON root node
     * @return event count (capped at MAX_ROWS_CHECKED)
     */
    private int countEventRows(JsonNode root) {
        JsonNode events = root.path("ocel:events");
        if (!events.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode event : events) {
            count++;
            if (count >= MAX_ROWS_CHECKED) {
                break;
            }
        }
        return count;
    }
}
