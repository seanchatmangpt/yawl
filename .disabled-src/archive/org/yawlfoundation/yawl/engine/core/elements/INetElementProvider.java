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

package org.yawlfoundation.yawl.engine.core.elements;

import org.yawlfoundation.yawl.elements.YNetElement;

import java.util.Map;

/**
 * Functional interface abstracting the net-element map retrieval needed by
 * {@code E2WFOJCore} during Reset-net construction.
 *
 * <p>Both the stateful {@code org.yawlfoundation.yawl.elements.YNet} and the
 * stateless {@code org.yawlfoundation.yawl.stateless.elements.YNet} are passed to
 * {@code E2WFOJCore} via a method reference: {@code yNet::getNetElements}.</p>
 *
 * <p>The map values are {@code YNetElement} (the shared base type); callers may
 * use {@code instanceof org.yawlfoundation.yawl.engine.core.marking.IMarkingTask}
 * to distinguish tasks from conditions without depending on the tree-specific type.</p>
 *
 * @since 5.2 (Phase 1 deduplication, EngineDedupPlan P1.3)
 */
@FunctionalInterface
public interface INetElementProvider {

    /**
     * Returns the map of net elements keyed by element ID.
     *
     * @return unmodifiable or live map of net elements
     */
    Map<String, ? extends YNetElement> getNetElements();

}
