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

/**
 * Data structure for Storage of RMarkings.
 *
 * @author YAWL Foundation
 * @since 2.0
 */
public class RSetOfMarkings {
    private final Set<RMarking> markings = new HashSet<>();

    public void addMarking(RMarking marking) {
        markings.add(marking);
    }

    public Set<RMarking> getMarkings() {
        return new HashSet<>(markings);
    }

    public int size() {
        return markings.size();
    }

    public void removeAll() {
        markings.clear();
    }

    public void removeMarking(RMarking marking) {
        markings.remove(marking);
    }

    public void addAll(RSetOfMarkings newmarkings) {
        markings.addAll(newmarkings.getMarkings());
    }

    public boolean equals(RSetOfMarkings other) {
        if (other == null) {
            return false;
        }
        Set<RMarking> markingsToCompare = other.getMarkings();
        if (markings.size() != markingsToCompare.size()) {
            return false;
        }
        return markings.containsAll(markingsToCompare) &&
               markingsToCompare.containsAll(markings);
    }
}
