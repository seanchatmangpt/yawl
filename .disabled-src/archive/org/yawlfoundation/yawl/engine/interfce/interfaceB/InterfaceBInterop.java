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

package org.yawlfoundation.yawl.engine.interfce.interfaceB;


/**
 * Defines the 'B' interface into the YAWL Engine corresponding to WfMC Interface 4 - Workflow engine interoperability.
 *
 * <p>This interface is a placeholder for WfMC Interface 4 compatibility. YAWL implements
 * its own engine interoperability through custom web service interfaces and event-driven
 * architecture rather than implementing WfMC-specific interoperability protocols.</p>
 *
 * <p>For engine interoperability operations, use:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EngineBasedServer}</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.interfaceE.InterfaceE_Service}</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.interfaceX.InterfaceX_Service}</li>
 * </ul>
 * </p>
 *
 * @author Andrew Hastie
 * @date 10-Jun-2005
 * @see org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EngineBasedServer
 * @see org.yawlfoundation.yawl.engine.interfce.interfaceE.InterfaceE_Service
 */
public interface InterfaceBInterop {
    // Intentionally empty - YAWL uses custom web service interfaces and event-driven
    // architecture for engine interoperability rather than implementing WfMC Interface 4
}
