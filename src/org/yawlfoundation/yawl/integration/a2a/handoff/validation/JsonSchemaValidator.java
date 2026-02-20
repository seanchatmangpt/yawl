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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.handoff.validation;

import java.util.List;
import java.util.Collections;

/**
 * JSON Schema validator for A2A handoff messages.
 *
 * <p>This validator provides schema validation capabilities for handoff
 * messages conforming to the A2A protocol specification.</p>
 *
 * <p><strong>Note:</strong> This implementation currently throws
 * {@link UnsupportedOperationException} for validation methods.
 * Full implementation requires additional JSON schema library dependencies.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class JsonSchemaValidator {

    private final String schemaId;

    /**
     * Creates a new JSON schema validator.
     *
     * @param schemaId the schema identifier to validate against
     */
    public JsonSchemaValidator(String schemaId) {
        this.schemaId = schemaId;
    }

    /**
     * Gets the schema ID.
     *
     * @return the schema identifier
     */
    public String getSchemaId() {
        return schemaId;
    }

    /**
     * Validates JSON content against the schema.
     *
     * @param jsonContent the JSON content to validate
     * @return the validation result
     * @throws UnsupportedOperationException always, as this is not yet implemented
     */
    public ValidationResult validate(String jsonContent) {
        SchemaValidator delegate = new SchemaValidator(schemaId);
        SchemaValidator.ValidationResult result = delegate.validate(jsonContent, schemaId);
        return new ValidationResult(
            result.isValid(),
            result.getErrors().stream().map(e -> e.getMessage()).toList(),
            Collections.emptyList()
        );
    }

    /**
     * Represents the result of a JSON schema validation operation.
     */
    public static class ValidationResult {

        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        /**
         * Creates a successful validation result.
         *
         * @return a valid result
         */
        public static ValidationResult success() {
            return new ValidationResult(true, Collections.emptyList(), Collections.emptyList());
        }

        /**
         * Creates a failed validation result.
         *
         * @param errors the list of validation errors
         * @return an invalid result
         */
        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, Collections.emptyList());
        }

        /**
         * Creates a validation result with warnings.
         *
         * @param valid whether validation passed
         * @param errors the list of errors
         * @param warnings the list of warnings
         */
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors != null ? List.copyOf(errors) : Collections.emptyList();
            this.warnings = warnings != null ? List.copyOf(warnings) : Collections.emptyList();
        }

        /**
         * Returns whether validation passed.
         *
         * @return true if valid, false otherwise
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Returns the list of validation errors.
         *
         * @return the errors
         */
        public List<String> getErrors() {
            return errors;
        }

        /**
         * Returns the list of validation warnings.
         *
         * @return the warnings
         */
        public List<String> getWarnings() {
            return warnings;
        }
    }
}
