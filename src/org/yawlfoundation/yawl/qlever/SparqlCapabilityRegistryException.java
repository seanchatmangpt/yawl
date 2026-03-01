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

package org.yawlfoundation.yawl.qlever;

/**
 * Exception thrown when SparqlCapability registry validation fails.
 * Indicates incomplete capability mappings or testing coverage.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
public final class SparqlCapabilityRegistryException extends IllegalStateException {
    /**
     * Creates a new exception with the given message.
     *
     * @param message the detail message
     */
    public SparqlCapabilityRegistryException(String message) {
        super(message);
    }
}
