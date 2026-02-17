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
 * Minimal interface abstracting the net-level post-set query required by
 * deadlock detection in {@link YCoreMarking#deadLock(IMarkingTask)}.
 *
 * <p>Both {@code org.yawlfoundation.yawl.elements.YNet} (stateful) and
 * {@code org.yawlfoundation.yawl.stateless.elements.YNet} (stateless) implement
 * this interface via their static {@code getPostset} utility, exposed here as
 * an instance method so it can be passed polymorphically to the shared marking
 * algorithm.</p>
 *
 * @since 5.2 (Phase 1 deduplication, EngineDedupPlan P1.2)
 */
public interface IMarkingNet {

    /**
     * Returns all direct successor elements of the given set of net elements.
     *
     * <p>This is the instance-method bridge to the static {@code YNet.getPostset}
     * utility present in both engine trees. The implementation in each tree's
     * {@code YNet} simply delegates to its own static method.</p>
     *
     * @param elements the set of net elements whose postset is to be computed
     * @return the union of the postsets of all provided elements
     */
    Set<? extends YNetElement> getPostset(Set<? extends YNetElement> elements);

}
