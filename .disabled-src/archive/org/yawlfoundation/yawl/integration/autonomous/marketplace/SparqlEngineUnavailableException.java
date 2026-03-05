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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

/**
 * Thrown when a SPARQL engine operation is attempted but the engine is not reachable.
 *
 * <p>Callers that want to degrade gracefully should catch this subclass specifically
 * and fall back to the pure-Java path.</p>
 *
 * @since YAWL 6.0
 */
public class SparqlEngineUnavailableException extends SparqlEngineException {

    public SparqlEngineUnavailableException(String engineType, String endpoint) {
        super("SPARQL engine '" + engineType + "' is unavailable at " + endpoint);
    }

    public SparqlEngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
