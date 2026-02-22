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
 * Petri Net arc element from PNML.
 * An arc connects a place to a transition or vice versa.
 * Direction: source → target.
 * Immutable record.
 *
 * @param id       Unique identifier
 * @param sourceId ID of source node (place or transition)
 * @param targetId ID of target node (place or transition)
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record PnmlArc(String id, String sourceId, String targetId) {

    /**
     * Validates that id, sourceId, and targetId are non-null and non-empty.
     */
    public PnmlArc {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Arc id cannot be null or empty");
        }
        if (sourceId == null || sourceId.isEmpty()) {
            throw new IllegalArgumentException("Arc sourceId cannot be null or empty");
        }
        if (targetId == null || targetId.isEmpty()) {
            throw new IllegalArgumentException("Arc targetId cannot be null or empty");
        }
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Arc source and target cannot be the same node");
        }
    }

    /**
     * Creates an arc with auto-generated ID.
     *
     * @param source Source node ID
     * @param target Target node ID
     * @return new PnmlArc with id="arc_source_target"
     */
    public static PnmlArc of(String source, String target) {
        String id = "arc_" + source + "_" + target;
        return new PnmlArc(id, source, target);
    }

    /**
     * Checks if this arc is place-to-transition (normal arc).
     * Requires external knowledge of node types; this is a structural hint.
     *
     * @param isSourcePlace true if source is a place
     * @return true if source is place and target is transition
     */
    public boolean isPlaceToTransition(boolean isSourcePlace) {
        return isSourcePlace;
    }

    /**
     * Checks if this arc is transition-to-place (output arc).
     * Requires external knowledge of node types; this is a structural hint.
     *
     * @param isSourceTransition true if source is a transition
     * @return true if source is transition and target is place
     */
    public boolean isTransitionToPlace(boolean isSourceTransition) {
        return isSourceTransition;
    }
}
