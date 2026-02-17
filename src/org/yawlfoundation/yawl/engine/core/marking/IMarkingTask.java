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

package org.yawlfoundation.yawl.engine.core.marking;

import org.yawlfoundation.yawl.elements.YNetElement;

import java.util.Set;

/**
 * Minimal interface exposing the task properties required by marking algorithms
 * (YCoreMarking, reachability analysis, deadlock detection).
 *
 * <p>Both {@code org.yawlfoundation.yawl.elements.YTask} (stateful) and
 * {@code org.yawlfoundation.yawl.stateless.elements.YTask} (stateless) implement
 * this interface, enabling the shared {@link YCoreMarking} to operate on tasks
 * from either engine tree without casting to a tree-specific type.</p>
 *
 * <p>The join and split type constants (_AND, _OR, _XOR) are identical in both
 * engine trees ({@code YTask._AND = 0}, etc.) so they can be compared directly.</p>
 *
 * @since 5.2 (Phase 1 deduplication, EngineDedupPlan P1.2)
 */
public interface IMarkingTask {

    /**
     * Join/split type constant: AND semantics.
     * Value 95 matches {@code YTask._AND} in both engine trees.
     */
    int _AND = 95;

    /**
     * Join/split type constant: OR semantics.
     * Value 103 matches {@code YTask._OR} in both engine trees.
     */
    int _OR = 103;

    /**
     * Join/split type constant: XOR semantics.
     * Value 126 matches {@code YTask._XOR} in both engine trees.
     */
    int _XOR = 126;

    /**
     * Returns the join type of this task.
     * @return one of {@link #_AND}, {@link #_OR}, {@link #_XOR}
     */
    int getJoinType();

    /**
     * Returns the split type of this task.
     * @return one of {@link #_AND}, {@link #_OR}, {@link #_XOR}
     */
    int getSplitType();

    /**
     * Returns the preset elements (input conditions) of this task.
     * The returned set contains {@code YNetElement} instances from whichever
     * engine tree this task belongs to.
     * @return the set of preset net elements
     */
    Set<? extends YNetElement> getPresetElements();

    /**
     * Returns the postset elements (output conditions) of this task.
     * @return the set of postset net elements
     */
    Set<? extends YNetElement> getPostsetElements();

    /**
     * Returns the cancellation (remove) set of this task.
     * @return the set of net elements that are removed when this task fires
     */
    Set<? extends YNetElement> getRemoveSet();

}
