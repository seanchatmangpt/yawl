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
 * Proclet Service for inter-workflow communication and coordination.
 *
 * <p>This package provides the Proclet Service, which extends YAWL with support
 * for proclets - lightweight, interacting workflow processes that communicate
 * via performatives (speech acts). Proclets enable complex multi-party workflow
 * interactions and conversations.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.procletService.ProcletService} - Main service extending InterfaceB</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.SingleInstanceClass} - Singleton pattern support</li>
 * </ul>
 *
 * <p>Subpackages:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.procletService.blockType} - Block type implementations</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.connect} - CPN/Java communication layer</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.editor} - Visual editor components</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.interactionGraph} - Interaction graph model</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.models} - Proclet model definitions</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence} - Hibernate persistence</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.selectionProcess} - Process selection logic</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.state} - Performative state management</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.util} - Utility classes</li>
 * </ul>
 */
package org.yawlfoundation.yawl.procletService;
