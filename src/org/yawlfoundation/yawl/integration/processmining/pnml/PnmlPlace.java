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
 * Petri Net place element from PNML.
 * A place holds tokens (marked with an initial marking).
 * Immutable record.
 *
 * @param id          Unique identifier
 * @param name        Human-readable name
 * @param initialMarking Number of tokens initially in this place
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record PnmlPlace(String id, String name, int initialMarking) {

    /**
     * Validates that id and name are non-null and non-empty.
     */
    public PnmlPlace {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Place id cannot be null or empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Place name cannot be null or empty");
        }
        if (initialMarking < 0) {
            throw new IllegalArgumentException("Initial marking cannot be negative");
        }
    }

    /**
     * Checks if this place is a start place (has initial marking).
     *
     * @return true if initialMarking > 0
     */
    public boolean isStartPlace() {
        return initialMarking > 0;
    }

    /**
     * Checks if this place is an end place (typically no outgoing arcs).
     * This is a structural hint; actual end determination requires arc analysis.
     *
     * @return true if initialMarking == 0 (not a source place)
     */
    public boolean couldBeEndPlace() {
        return initialMarking == 0;
    }

    /**
     * Creates a place with zero initial marking.
     *
     * @param id   Unique identifier
     * @param name Human-readable name
     * @return new PnmlPlace with initialMarking=0
     */
    public static PnmlPlace of(String id, String name) {
        return new PnmlPlace(id, name, 0);
    }

    /**
     * Creates a start place with initial marking of 1.
     *
     * @param id Unique identifier
     * @return new PnmlPlace with name="start" and initialMarking=1
     */
    public static PnmlPlace start(String id) {
        return new PnmlPlace(id, "start", 1);
    }

    /**
     * Creates an end place with zero initial marking.
     *
     * @param id Unique identifier
     * @return new PnmlPlace with name="end" and initialMarking=0
     */
    public static PnmlPlace end(String id) {
        return new PnmlPlace(id, "end", 0);
    }
}
