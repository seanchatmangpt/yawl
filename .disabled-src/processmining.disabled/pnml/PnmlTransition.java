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
 * Petri Net transition element from PNML.
 * A transition consumes tokens from input places and produces tokens in output places.
 * Can be silent (tau transition) or labeled with an activity name.
 * Immutable record.
 *
 * @param id       Unique identifier
 * @param name     Activity name (human-readable label)
 * @param isSilent true if this is a tau/silent transition (not observable)
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record PnmlTransition(String id, String name, boolean isSilent) {

    /**
     * Validates that id is non-null and non-empty.
     * Name can be empty if silent.
     */
    public PnmlTransition {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Transition id cannot be null or empty");
        }
        if (name == null) {
            throw new IllegalArgumentException("Transition name cannot be null");
        }
    }

    /**
     * Returns the label for this transition.
     * For non-silent transitions, returns the name.
     * For silent transitions, returns empty string.
     *
     * @return the label (activity name) or empty string if silent
     */
    public String label() {
        return isSilent ? "" : name;
    }

    /**
     * Checks if this transition is observable (has a label).
     *
     * @return true if not silent
     */
    public boolean isObservable() {
        return !isSilent;
    }

    /**
     * Creates a labeled (non-silent) transition.
     *
     * @param id   Unique identifier
     * @param name Activity name
     * @return new PnmlTransition with isSilent=false
     */
    public static PnmlTransition of(String id, String name) {
        return new PnmlTransition(id, name, false);
    }

    /**
     * Creates a silent (tau) transition.
     *
     * @param id Unique identifier
     * @return new PnmlTransition with isSilent=true and empty name
     */
    public static PnmlTransition silent(String id) {
        return new PnmlTransition(id, "", true);
    }
}
