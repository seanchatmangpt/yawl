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

/**
 * Shared Petri-net element abstractions for OR-join analysis and net traversal.
 *
 * <p>This package is part of Phase 1 engine deduplication (EngineDedupPlan P1.3).
 * It contains canonical implementations that are used by both the stateful and
 * stateless engine trees for Reset-net construction and OR-join enablement analysis.</p>
 *
 * <p>Contents:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.elements.E2WFOJCore} - Canonical
 *       implementation of the E2WFOJ Reset-net algorithm for OR-join analysis.
 *       This class converts YAWL nets to Reset nets, performs structural restriction,
 *       and determines OR-join enablement via backwards coverability analysis.</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.core.elements.INetElementProvider} -
 *       Functional interface abstracting net element map retrieval. Both
 *       {@code elements.YNet} and {@code stateless.elements.YNet} are passed
 *       via method reference ({@code yNet::getNetElements}).</li>
 * </ul></p>
 *
 * <p>The E2WFOJ algorithm (originally from the E2WFOJ research project) determines
 * whether an OR-join task should be enabled at a given marking. It constructs a
 * Reset net representation of the YAWL workflow, performs structural restriction
 * based on the OR-join's preset, and uses backwards coverability analysis to
 * determine if any "bigger-enabling" marking is reachable.</p>
 *
 * <p>Type substitutions enabling tree neutrality:
 * <ul>
 *   <li>{@code YTask} parameter types are replaced with
 *       {@link org.yawlfoundation.yawl.engine.core.marking.IMarkingTask}</li>
 *   <li>{@code instanceof YTask} checks use {@code instanceof IMarkingTask}</li>
 *   <li>{@code YExternalNetElement} is replaced with the shared
 *       {@link org.yawlfoundation.yawl.elements.YNetElement} base type</li>
 * </ul></p>
 *
 * @since 5.2 (Phase 1 deduplication)
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.engine.core.elements;
