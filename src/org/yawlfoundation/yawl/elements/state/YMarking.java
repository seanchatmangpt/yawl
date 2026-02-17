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

package org.yawlfoundation.yawl.elements.state;

import java.util.List;
import java.util.Set;

import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YNetElement;
import org.yawlfoundation.yawl.engine.core.marking.YCoreMarking;
import org.yawlfoundation.yawl.engine.core.marking.YCoreSetOfMarkings;
import org.yawlfoundation.yawl.engine.core.marking.IMarkingTask;

import org.yawlfoundation.yawl.elements.*;

/**
 * Stateful-engine thin wrapper around {@link YCoreMarking}.
 *
 * <p>This class was refactored as part of Phase 1 engine deduplication
 * (EngineDedupPlan P1.2).  All marking algorithm logic now lives in
 * {@link YCoreMarking}; this wrapper contributes only the two
 * tree-specific operations:</p>
 * <ul>
 *   <li>{@link #netPostset(Set)} - delegates to the stateful
 *       {@code elements.YNet.getPostset()} static method.</li>
 *   <li>{@link #newInstance(List)} - creates a new {@code YMarking} of this
 *       (stateful) type so that intermediate markings produced during
 *       reachability analysis remain stateful.</li>
 * </ul>
 *
 * <p>The public API is unchanged: existing callers pass {@code YTask} or
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
     * Delegates to {@link YNet#getPostset(Set)} using the stateful element types.
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
     * Creates a new stateful {@code YMarking} instance with the given locations.
     *
     * @param locations the initial token positions for the new marking
     * @return a new {@code YMarking} of the stateful type
     */
    @Override
    protected YMarking newInstance(List<YNetElement> locations) {
        return new YMarking(locations);
    }


    // =========================================================================
    // Covariant return type overrides
    // =========================================================================

    /**
     * Computes all markings reachable from this marking in a single task firing.
     *
     * <p>This override provides a covariant return type of {@link YSetOfMarkings}
     * for backward compatibility with existing stateful-engine callers that expect
     * the stateful wrapper type.</p>
     *
     * @param task   the task to fire
     * @param orJoin the OR-join being evaluated (used to guard OR-join firing)
     * @return the set of markings reachable in one step, or {@code null} if not enabled
     */
    @Override
    public YSetOfMarkings reachableInOneStep(IMarkingTask task, IMarkingTask orJoin) {
        YCoreSetOfMarkings coreResult = super.reachableInOneStep(task, orJoin);
        if (coreResult == null) {
            return null;
        }
        // Wrap in YSetOfMarkings for backward compatibility
        YSetOfMarkings result = new YSetOfMarkings();
        for (YCoreMarking marking : coreResult.getMarkings()) {
            result.addMarking(marking);
        }
        return result;
    }

}
