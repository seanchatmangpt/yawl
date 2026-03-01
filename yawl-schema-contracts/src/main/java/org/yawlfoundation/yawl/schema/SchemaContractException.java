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

package org.yawlfoundation.yawl.schema;

/**
 * Thrown when a schema contract file cannot be found, read, or parsed at startup.
 *
 * <p>This is a <em>configuration error</em> (wrong classpath path, missing YAML file,
 * malformed ODCS YAML), not a data validation failure.
 * {@link TaskSchemaViolationException} is thrown for actual field-level violations at
 * task execution time.</p>
 *
 * @since 6.0.0
 */
public final class SchemaContractException extends RuntimeException {

    public SchemaContractException(String message) {
        super(message);
    }

    public SchemaContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
