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

import java.io.Serial;

/**
 * Exception thrown when A2A handoff message schema validation fails.
 *
 * <p>This exception indicates that a handoff message does not conform
 * to the expected A2A JSON schema format.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class SchemaValidationError extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String schemaId;
    private final String validationMessage;

    /**
     * Constructs a schema validation error with the specified details.
     *
     * @param schemaId the ID of the schema that failed validation
     * @param message the validation error message
     */
    public SchemaValidationError(String schemaId, String message) {
        super(String.format("Schema validation failed for %s: %s", schemaId, message));
        this.schemaId = schemaId;
        this.validationMessage = message;
    }

    /**
     * Constructs a schema validation error with a cause.
     *
     * @param schemaId the ID of the schema that failed validation
     * @param message the validation error message
     * @param cause the underlying cause
     */
    public SchemaValidationError(String schemaId, String message, Throwable cause) {
        super(String.format("Schema validation failed for %s: %s", schemaId, message), cause);
        this.schemaId = schemaId;
        this.validationMessage = message;
    }

    /**
     * Gets the schema ID that failed validation.
     *
     * @return the schema ID
     */
    public String getSchemaId() {
        return schemaId;
    }

    /**
     * Gets the validation message.
     *
     * @return the validation message
     */
    public String getValidationMessage() {
        return validationMessage;
    }
}
