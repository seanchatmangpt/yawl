/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.elements.e2wfoj;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a transition in the Reset net formalization of a YAWL net.
 *
 * @author YAWL Foundation
 * @since 2.0
 */
public class RTransition extends RElement {
    private final Set<RPlace> removeSet = new HashSet<>();

    public RTransition(String id) {
        super(id);
    }

    public void setRemoveSet(Set<RPlace> places) {
        removeSet.addAll(places);
    }

    public void setRemoveSet(RPlace place) {
        removeSet.add(place);
    }

    public Set<RPlace> getRemoveSet() {
        return new HashSet<>(removeSet);
    }

    public boolean isCancelTransition() {
        return !removeSet.isEmpty();
    }

    /**
     * Gets the postset places of this transition.
     * Overrides to return properly typed Set of RPlace.
     */
    public Set<RPlace> getPostsetPlaces() {
        return getPostsetElements().stream()
                .filter(e -> e instanceof RPlace)
                .map(e -> (RPlace) e)
                .collect(Collectors.toSet());
    }

    /**
     * Gets the preset places of this transition.
     * Overrides to return properly typed Set of RPlace.
     */
    public Set<RPlace> getPresetPlaces() {
        return getPresetElements().stream()
                .filter(e -> e instanceof RPlace)
                .map(e -> (RPlace) e)
                .collect(Collectors.toSet());
    }
}
