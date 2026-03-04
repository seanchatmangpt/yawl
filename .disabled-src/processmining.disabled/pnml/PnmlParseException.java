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

package org.yawlfoundation.yawl.integration.processmining.pnml;

/**
 * Checked exception thrown when PNML XML parsing fails.
 * Indicates malformed XML, missing required elements, or invalid structure.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class PnmlParseException extends Exception {

    /**
     * Creates an exception with a message.
     *
     * @param message Detailed error message
     */
    public PnmlParseException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and underlying cause.
     *
     * @param message Detailed error message
     * @param cause   Root cause exception
     */
    public PnmlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
