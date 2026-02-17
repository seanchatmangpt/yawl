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

package org.yawlfoundation.yawl.stateless.elements.e2wfoj;

import org.yawlfoundation.yawl.engine.core.elements.E2WFOJCore;
import org.yawlfoundation.yawl.stateless.elements.YNet;
import org.yawlfoundation.yawl.stateless.elements.YTask;
import org.yawlfoundation.yawl.stateless.elements.marking.YMarking;

/**
 * Stateless-engine thin wrapper around {@link E2WFOJCore}.
 *
 * <p>This class was refactored as part of Phase 1 engine deduplication
 * (EngineDedupPlan P1.3).  All Reset-net and OR-join analysis logic now lives in
 * {@link E2WFOJCore}; this wrapper:
 * <ul>
 *   <li>Adapts the stateless {@code YNet} to the {@code INetElementProvider} interface
 *       by passing a method reference ({@code yNet::getNetElements}).</li>
 *   <li>Accepts the stateless {@code YTask} for the OR-join (which implements
 *       {@code IMarkingTask}) and the {@code YMarking} (which extends
 *       {@code YCoreMarking}).</li>
 *   <li>Preserves the original public API for full backward compatibility.</li>
 * </ul></p>
 *
 * @author YAWL Foundation (original)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 */
public final class E2WFOJNet {

    /** The canonical core implementation. */
    private final E2WFOJCore _core;

    /**
     * Constructs a Reset net from a stateless YAWL net for OR-join analysis.
     *
     * @param yNet   the YAWL net
     * @param orJoin the OR-join task being analyzed
     */
    public E2WFOJNet(YNet yNet, YTask orJoin) {
        _core = new E2WFOJCore(yNet::getNetElements, orJoin);
    }


    // =========================================================================
    // Delegation to E2WFOJCore
    // =========================================================================

    /**
     * Performs structural restriction of the reset net based on an OR-join task.
     *
     * @param orJoin the OR-join task for structural restriction
     */
    public void restrictNet(YTask orJoin) {
        _core.restrictNet(orJoin);
    }

    /**
     * Performs active projection restriction based on a marking.
     *
     * @param marking the current marking for active projection
     */
    public void restrictNet(YMarking marking) {
        _core.restrictNet(marking);
    }

    /**
     * Determines whether an OR-join task should be enabled at a given marking.
     *
     * @param marking the current marking
     * @param orJoin  the OR-join task to check
     * @return true if the OR-join should be enabled, false otherwise
     */
    public boolean orJoinEnabled(YMarking marking, YTask orJoin) {
        return _core.orJoinEnabled(marking, orJoin);
    }

}
