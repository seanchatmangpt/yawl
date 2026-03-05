/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
 * Interaction graph model for Proclet Service.
 *
 * <p>This package provides the graph-based model for representing interactions
 * between proclets. Interaction graphs capture the flow of performatives
 * (speech acts) between workflow participants.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.procletService.interactionGraph.InteractionGraph} - Directed graph of interactions</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.interactionGraph.InteractionGraphs} - Collection of interaction graphs</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.interactionGraph.InteractionNode} - Node representing a proclet</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.interactionGraph.InteractionArc} - Arc representing a performative flow</li>
 * </ul>
 */
package org.yawlfoundation.yawl.procletService.interactionGraph;
