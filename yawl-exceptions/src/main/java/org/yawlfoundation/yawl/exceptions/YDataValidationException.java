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
        return super.getMessage();
    }
}
