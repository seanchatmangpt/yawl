/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.exceptions;

import java.io.Serial;

import org.jdom2.Element;

/**
 * Exception thrown when data validation fails against a schema during YAWL execution.
 *
 * <p><b>Common causes:</b>
 * <ul>
 *   <li>Missing required XML element or attribute
 *   <li>Data type mismatch (e.g., non-numeric value for int field)
 *   <li>Value outside allowed range or pattern
 *   <li>Schema constraint violation (unique, length, format)
 *   <li>Malformed XML structure
 * </ul>
 *
 * <p><b>Recovery guidance:</b>
 * <ul>
 *   <li>Review error message for specific element/attribute that failed
 *   <li>Check task output data against workflow specification schema
 *   <li>Validate data format (ensure required fields are present)
 *   <li>Consult workflow specification documentation for field requirements
 * </ul>
 *
 * @author Lachlan Aldred
 * @since 1/09/2005
 */
public class YDataValidationException extends YDataStateException {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Constructs a new data validation exception.
     *
     * @param schema       the schema used for validation
     * @param dataInput    the data element that failed validation
     * @param xercesErrors the validation errors from Xerces
     * @param source       the source task identifier
     * @param message      the detail message
     */
    public YDataValidationException(String schema, Element dataInput, String xercesErrors,
                                    String source, String message) {
        super(null, null, schema, dataInput, xercesErrors, source, message);
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        if (base == null) {
            return "Data validation failed. Review schema constraints and ensure all required fields are present.";
        }
        return base + " See workflow specification for schema definition.";
    }
}
