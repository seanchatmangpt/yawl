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

package org.yawlfoundation.yawl.stateless.elements.marking;

import org.yawlfoundation.yawl.elements.YNetElement;
import org.yawlfoundation.yawl.engine.core.marking.YCoreMarking;
import org.yawlfoundation.yawl.stateless.elements.YExternalNetElement;
import org.yawlfoundation.yawl.stateless.elements.YNet;

import java.util.List;
import java.util.Set;

/**
 * Stateless-engine thin wrapper around {@link YCoreMarking}.
 *
 * <p>This class was refactored as part of Phase 1 engine deduplication
 * (EngineDedupPlan P1.2).  All marking algorithm logic now lives in
 * {@link YCoreMarking}; this wrapper contributes only the two
 * tree-specific operations:</p>
 * <ul>
 *   <li>{@link #netPostset(Set)} - delegates to the stateless
 *       {@code stateless.elements.YNet.getPostset()} static method.</li>
 *   <li>{@link #newInstance(List)} - creates a new {@code YMarking} of this
 *       (stateless) type so that intermediate markings produced during
 *       reachability analysis remain stateless.</li>
 * </ul>
 *
 * <p>The public API is unchanged: existing callers pass stateless {@code YTask} or
 * {@code YIdentifier} arguments which are accepted via the
 * {@code IMarkingTask} and {@code List<YNetElement>} parameters of the
 * super-class methods.</p>
 *
 * @author Lachlan Aldred (original)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 */
public class YMarking extends YCoreMarking {

    /**
     * Constructs a marking from the locations held by the given case identifier.
     *
     * @param identifier the case identifier whose locations define the initial marking
     */
    public YMarking(YIdentifier identifier) {
        super(identifier.getLocations());
    }

    /**
     * Constructs a marking from an explicit list of net element locations.
     *
     * @param locations the initial token positions
     */
    public YMarking(List<YNetElement> locations) {
        super(locations);
    }


    // =========================================================================
    // Abstract method implementations (tree-specific operations)
    // =========================================================================

    /**
     * Delegates to {@link YNet#getPostset(Set)} using the stateless element types.
     *
     * @param elements the set of net elements to compute the postset from
     * @return the union of postsets of all provided elements
     */
    @Override
    @SuppressWarnings("unchecked")
    protected Set<YExternalNetElement> netPostset(Set<? extends YNetElement> elements) {
        return YNet.getPostset((Set<YExternalNetElement>) elements);
    }

    /**
     * Creates a new stateless {@code YMarking} instance with the given locations.
     *
     * @param locations the initial token positions for the new marking
     * @return a new {@code YMarking} of the stateless type
     */
    @Override
    protected YMarking newInstance(List<YNetElement> locations) {
        return new YMarking(locations);
    }

}
