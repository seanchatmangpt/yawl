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

import org.jdom2.Element;

import java.io.Serial;

/**
 * Exception thrown when a data query fails during YAWL task execution.
 *
 * @author Lachlan Aldred
 * @since 1/09/2005
 */
public class YDataQueryException extends YDataStateException {
    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Constructs a new data query exception.
     *
     * @param queryString the XQuery expression that failed
     * @param data        the data element being queried
     * @param source      the source task identifier
     * @param message     the detail message
     */
    public YDataQueryException(String queryString, Element data, String source, String message) {
        super(queryString, data, null, null, null, source, message);
    }

    @Override
    public String getMessage() {
        var base = "The MI data accessing query (%s) for the task (%s) was applied over some data. It failed to execute as expected"
                .formatted(getQueryString(), getSource());
        var cause = super.getMessage();
        return cause != null ? base + ": " + cause : base;
    }

    /**
     * Gets the query string that caused this exception.
     *
     * @return the XQuery expression
     */
    public String getQueryString() {
        return _queryString;
    }

    /**
     * Gets the data element that was being queried.
     *
     * @return the queried data element
     */
    public Element getData() {
        return _queriedData;
    }
}
